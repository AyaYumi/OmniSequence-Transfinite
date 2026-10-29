package com.atir.molecularmanipulator.integration.eco;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderProgress;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderState;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.execution.bigorder.ECOBigCraftingOrder;
import cn.dancingsnow.neoecoae.crafting.planner.ECOBigOrderPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Omni ownership and persistence around ECO's public order state machine and asynchronous planner. */
public final class EcoOmniParentOrder {
    public static final String NBT_KEY = "molecularmanipulator:ecoParentOrder";
    private static final ThreadLocal<ICraftingPlan> CARRIER_SUBMISSION = new ThreadLocal<>();
    private final ECOBigCraftingOrder order;
    private final AEKey goal;
    private final ECOPlannerOptions options;
    private Map<AEKey, BigInteger> preview;
    private final EcoOmniParentOrderSource source;
    private final Planner planner;
    private Future<ECOBigOrderPlanner.Answer> planning;
    private IGrid planningGrid;

    public EcoOmniParentOrder(UUID id, ECOBigOrderRequest request, IActionSource source) {
        this(new ECOBigCraftingOrder(id, request.requested(), request.forced()), request.goal(), request.options(),
                request.pendingPreview(), EcoOmniParentOrderSource.capture(source), ECOBigOrderPlanner::begin);
    }

    EcoOmniParentOrder(UUID id, ECOBigOrderRequest request, IActionSource source, Planner planner) {
        this(new ECOBigCraftingOrder(id, request.requested(), request.forced()), request.goal(), request.options(),
                request.pendingPreview(), EcoOmniParentOrderSource.capture(source), planner);
    }

    private EcoOmniParentOrder(ECOBigCraftingOrder order, AEKey goal, ECOPlannerOptions options,
            Map<AEKey, BigInteger> preview, EcoOmniParentOrderSource source, Planner planner) {
        this.order = order;
        this.goal = java.util.Objects.requireNonNull(goal);
        this.options = java.util.Objects.requireNonNull(options);
        this.preview = Map.copyOf(preview);
        this.source = source;
        this.planner = java.util.Objects.requireNonNull(planner);
        preview.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Invalid parent preview");
        });
    }

    /** Identifies an empty carrier only inside validated admission on a real Omni CPU. */
    public static boolean submittingCarrier(ICraftingPlan plan) { return CARRIER_SUBMISSION.get() == plan && plan != null; }
    public static <T> T submitCarrier(ICraftingPlan plan, java.util.function.Supplier<T> submission) {
        var previous = CARRIER_SUBMISSION.get();
        CARRIER_SUBMISSION.set(plan);
        try { return submission.get(); }
        finally { if (previous == null) CARRIER_SUBMISSION.remove(); else CARRIER_SUBMISSION.set(previous); }
    }

    /** Bind validated admission to the UUID created by AE2, before any planning starts. */
    public EcoOmniParentOrder bind(UUID id) {
        if (planning != null || order.state() != ECOBigOrderState.PLANNING || order.completed().signum() != 0)
            throw new IllegalStateException("Only a new parent can be bound");
        return new EcoOmniParentOrder(new ECOBigCraftingOrder(id, order.requested(), order.forced()),
                goal, options, preview, source, planner);
    }

    public UUID id() { return order.id(); }
    public boolean running() { return order.state() == ECOBigOrderState.RUNNING_CHILD; }
    public boolean terminal() { return order.terminal(); }
    public boolean lastChild() { return running() && order.remaining().equals(BigInteger.valueOf(order.childTarget())); }
    public ECOBigOrderProgress progress(long childRemaining) { return order.progress(childRemaining); }
    public GenericStack output() { return new GenericStack(goal, bounded(order.remaining())); }
    public Map<AEKey, BigInteger> pending() {
        if (running() || terminal()) return Map.of();
        var pending = new LinkedHashMap<>(preview);
        pending.merge(goal, order.remaining(), BigInteger::max);
        return Map.copyOf(pending);
    }
    public static long bounded(BigInteger amount) { return amount.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact(); }
    public IActionSource actionSource(MinecraftServer server) { return source.resolve(server); }

    /** No extraction occurs here. The native child admission performs the physical reservation. */
    public void tick(Host host) {
        if (running() || terminal()) return;
        var grid = host.ecoParent$grid();
        var action = actionSource(host.ecoParent$server());
        if (planning != null && (planningGrid != grid || action == null)) stopPlanning();
        if (planning == null) {
            if (!order.tickRetry()) return;
            if (grid == null || action == null) { waitFor(false, "SOURCE_UNAVAILABLE"); host.ecoParent$dirty(); return; }
            if (!host.ecoParent$flushInventory()) { waitFor(false, "OUTPUT_STORAGE"); host.ecoParent$dirty(); return; }
            order.planning();
            try {
                // Candidate and search policy belong to ECO. Do not add a throughput ceiling or cache a smaller segment.
                planning = planner.begin(grid, goal, order.candidate(), host.ecoParent$storage(), options);
                planningGrid = grid;
            } catch (RejectedExecutionException busy) {
                waitFor(false, "PLANNER_BUSY");
            }
            host.ecoParent$dirty();
            return;
        }
        if (!planning.isDone()) return;
        try {
            var answer = planning.get();
            planning = null;
            planningGrid = null;
            var result = answer.result();
            if (answer.fatal()) order.fail(result.status().name());
            else if (answer.capacity() || result.status() != PlanningStatus.SUCCESS) {
                waitFor(!answer.capacity(), answer.capacity() ? "CAPACITY" : "MATERIALS");
            } else if (!executable(result, grid)) {
                // A changed provider graph or a blocked contract never becomes executable by changing its status.
                waitFor(true, "PLAN_CHANGED_OR_BLOCKED");
            } else {
                var plan = result.plan();
                long target = plan.finalOutput().amount();
                if (!goal.equals(plan.finalOutput().what()) || target <= 0
                        || BigInteger.valueOf(target).compareTo(order.remaining()) > 0) {
                    order.fail("INVALID_CHILD_TARGET");
                } else {
                    ECOPlanningResultRegistry.register(plan, result);
                    var submitted = ECOPlanningResultRegistry.withSubmissionAlias(plan, result,
                            () -> host.ecoParent$submitChild(plan, action));
                    if (submitted.successful()) {
                        order.resetRetry();
                        order.startChild(target);
                        preview = Map.of();
                    }
                    else waitFor(true, "CHILD_" + submitted.errorCode().name());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            stopPlanning();
            order.fail("PLANNING_INTERRUPTED");
        } catch (Exception | LinkageError failure) {
            stopPlanning();
            order.fail("PLANNING_FAILED");
            RateLimitedLog.warn("ECO Omni parent planning failed", failure);
        }
        host.ecoParent$dirty();
    }

    static boolean executable(ECOPlanningResult result, IGrid grid) {
        if (result == null || result.status() != PlanningStatus.SUCCESS || result.plan() == null
                || result.plan().simulation() || !result.executionContract().executable()
                || result.executionRequirement() == cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement.BLOCKED
                || result.components().stream().anyMatch(component -> switch (component.status()) {
                    case UNRESOLVED, UNSUPPORTED, SOLVED_NOT_EMITTED -> true;
                    default -> false;
                })) return false;
        return result.plan().patternTimes().keySet().stream().allMatch(pattern -> !pattern.getOutputs().isEmpty()
                && grid.getCraftingService().getCraftingFor(pattern.getOutputs().getFirst().what()).contains(pattern));
    }

    private void waitFor(boolean materials, String reason) {
        order.waitFor(materials ? ECOBigOrderState.WAITING_MATERIALS : ECOBigOrderState.WAITING_CAPACITY, reason);
    }
    /** Called only after native finish succeeded with zero *delivered* output remaining. */
    public boolean childDelivered() { return order.completeChild(); }
    public void stopPlanning() {
        if (planning != null) planning.cancel(true);
        planning = null;
        planningGrid = null;
    }
    public void cancel() { stopPlanning(); order.cancel(); }

    public CompoundTag write(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putUUID("id", id());
        tag.putString("requested", order.requested().toString());
        tag.putString("completed", order.completed().toString());
        tag.putBoolean("forced", order.forced());
        tag.putString("state", order.state().name());
        tag.putLong("child", order.childTarget());
        tag.putLong("retryTicks", order.retryTicks());
        tag.putInt("retryDelay", order.retryDelay());
        tag.putString("reason", order.reason());
        tag.put("goal", GenericStack.writeTag(registries, new GenericStack(goal, 1)));
        tag.putBoolean("cycles", options.cyclePlanningEnabled());
        tag.putBoolean("ignoreSubstitutions", options.ignorePatternSubstitutions());
        tag.putBoolean("planningLog", options.planningLogEnabled());
        var fuzzy = new ListTag();
        options.fuzzyPlanningItemIds().forEach(id -> fuzzy.add(StringTag.valueOf(id.toString())));
        tag.put("fuzzy", fuzzy);
        var pending = new ListTag();
        preview.forEach((key, amount) -> {
            var entry = new CompoundTag();
            entry.put("key", GenericStack.writeTag(registries, new GenericStack(key, 1)));
            entry.putString("amount", amount.toString());
            pending.add(entry);
        });
        tag.put("preview", pending);
        tag.put("source", source.write());
        return tag;
    }

    public static EcoOmniParentOrder read(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("version") != 1) throw new IllegalArgumentException("Unsupported ECO Omni parent version");
        var order = ECOBigCraftingOrder.restore(tag.getUUID("id"), ECOBigCraftingOrder.decode(tag.getString("requested")),
                ECOBigCraftingOrder.decode(tag.getString("completed")), tag.getBoolean("forced"),
                ECOBigOrderState.valueOf(tag.getString("state")), tag.getLong("child"), tag.getLong("retryTicks"),
                tag.getInt("retryDelay"), tag.getString("reason"));
        var goal = GenericStack.readTag(registries, tag.getCompound("goal"));
        if (goal == null) throw new IllegalArgumentException("Missing parent goal");
        var fuzzy = new LinkedHashSet<ResourceLocation>();
        tag.getList("fuzzy", Tag.TAG_STRING).forEach(value -> fuzzy.add(ResourceLocation.parse(value.getAsString())));
        var options = new ECOPlannerOptions(tag.getBoolean("cycles"), tag.getBoolean("ignoreSubstitutions"),
                Set.copyOf(fuzzy), tag.getBoolean("planningLog"));
        var preview = new LinkedHashMap<AEKey, BigInteger>();
        for (var value : tag.getList("preview", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            var key = GenericStack.readTag(registries, entry.getCompound("key"));
            if (key == null || preview.putIfAbsent(key.what(), ECOBigCraftingOrder.decode(entry.getString("amount"))) != null)
                throw new IllegalArgumentException("Invalid parent preview key");
        }
        return new EcoOmniParentOrder(order, goal.what(), options, preview,
                EcoOmniParentOrderSource.read(tag.getCompound("source")), ECOBigOrderPlanner::begin);
    }

    @FunctionalInterface interface Planner {
        Future<ECOBigOrderPlanner.Answer> begin(IGrid grid, AEKey goal, long candidate, long storage, ECOPlannerOptions options);
    }

    public interface Host {
        IGrid ecoParent$grid();
        MinecraftServer ecoParent$server();
        long ecoParent$storage();
        boolean ecoParent$flushInventory();
        ICraftingSubmitResult ecoParent$submitChild(ICraftingPlan plan, IActionSource source);
        void ecoParent$dirty();
    }

}
