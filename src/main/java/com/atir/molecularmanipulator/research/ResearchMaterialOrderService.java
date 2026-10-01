package com.atir.molecularmanipulator.research;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Queues missing research materials through the connected AE2 crafting service. */
public final class ResearchMaterialOrderService implements ICraftingRequester {
    private static final Comparator<AEKey> DISPLAY_ORDER = Comparator
            .comparing((AEKey key) -> key.getClass().getName())
            .thenComparing(key -> key.getId().toString())
            .thenComparing(AEKey::toString);
    private final MatterFabricationBlockEntity machine;
    private final MachineSource source;
    private final Map<AEItemKey, Long> requested = new LinkedHashMap<>();
    private final Map<AEItemKey, Diagnostic> diagnostics = new LinkedHashMap<>();
    private final Set<ICraftingLink> links = new LinkedHashSet<>();
    /** Amounts submitted to a CPU; re-queued if the controller is saved before completion. */
    private final Map<ICraftingLink, Request> inFlight = new LinkedHashMap<>();
    private Future<ICraftingPlan> calculation;
    private AEItemKey calculating;
    private long calculatingAmount;
    private long retryAt;
    private String status = "idle";

    public ResearchMaterialOrderService(MatterFabricationBlockEntity machine) {
        this.machine = machine;
        this.source = new MachineSource(machine);
    }

    public void request(Map<AEItemKey, Long> missing) {
        if (missing == null) return;
        missing.forEach((key, amount) -> {
            if (key != null && amount != null && amount > 0) {
                requested.merge(key, amount, ResearchMaterialOrderService::saturatedAdd);
                diagnostics.remove(key);
            }
        });
        if (!requested.isEmpty()) status = "queued";
    }

    public boolean hasPending() {
        return calculation != null || !requested.isEmpty() || !links.isEmpty() || !inFlight.isEmpty();
    }

    public long pendingAmount(AEItemKey key) {
        if (key == null) return 0;
        long total = requested.getOrDefault(key, 0L);
        for (var request : inFlight.values()) {
            if (key.equals(request.key())) total = saturatedAdd(total, request.amount());
        }
        return total;
    }

    public String status() { return status; }
    public int queuedTypes() { return requested.size(); }
    public int activeJobs() { return links.size() + (calculation == null ? 0 : 1); }

    public void reportUncraftable(AEItemKey key, long amount) {
        if (key != null && amount > 0) {
            diagnostics.put(key, new Diagnostic("not_craftable", amount, List.of()));
            status = "not_craftable";
        }
    }

    public JsonObject clientState(HolderLookup.Provider registries) {
        var result = new JsonObject();
        result.addProperty("status", status);
        result.addProperty("queued", queuedTypes());
        result.addProperty("active", activeJobs());
        var entries = new JsonArray();
        var amounts = new LinkedHashMap<AEItemKey, Long>(requested);
        var activeAmounts = new LinkedHashMap<AEItemKey, Long>();
        inFlight.values().forEach(request -> {
            if (request.amount() > 0) activeAmounts.merge(request.key(), request.amount(),
                    ResearchMaterialOrderService::saturatedAdd);
        });
        activeAmounts.forEach((key, amount) -> amounts.merge(key, amount,
                ResearchMaterialOrderService::saturatedAdd));
        diagnostics.forEach((key, diagnostic) -> amounts.putIfAbsent(key, diagnostic.amount()));
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        displayOrder(amounts.keySet()).forEach(key -> {
            long amount = amounts.get(key);
            var encoded = GenericStack.CODEC.encodeStart(ops, new GenericStack(key, Math.max(1, amount))).result();
            if (encoded.isEmpty()) return;
            var entry = new JsonObject();
            entry.add("target", encoded.get());
            entry.addProperty("queued", requested.getOrDefault(key, 0L));
            entry.addProperty("active", activeAmounts.getOrDefault(key, 0L));
            var diagnostic = diagnostics.get(key);
            entry.addProperty("status", diagnostic != null ? diagnostic.status()
                    : key.equals(calculating) ? "calculating"
                    : requested.containsKey(key) ? "queued" : "submitted");
            var missing = new JsonArray();
            if (diagnostic != null) diagnostic.missing().forEach(stack -> GenericStack.CODEC
                    .encodeStart(ops, stack).result().ifPresent(missing::add));
            entry.add("missing", missing);
            entries.add(entry);
        });
        result.add("items", entries);
        return result;
    }

    static List<AEItemKey> displayOrder(Set<AEItemKey> keys) {
        return keys.stream().sorted(DISPLAY_ORDER).toList();
    }

    public void clear() {
        requested.clear();
        diagnostics.clear();
        links.clear();
        inFlight.clear();
        calculation = null;
        calculating = null;
        calculatingAmount = 0;
        status = "idle";
    }

    public void tick() {
        var level = machine.getLevel();
        var grid = machine.getMainNode().getGrid();
        if (level == null || grid == null || !machine.getMainNode().isActive()) return;
        cleanupLinks();
        var available = grid.getStorageService().getInventory().getAvailableStacks();
        diagnostics.entrySet().removeIf(entry -> !requested.containsKey(entry.getKey())
                && pendingAmount(entry.getKey()) == 0
                && available.get(entry.getKey()) >= entry.getValue().amount());
        if (requested.isEmpty() && calculation == null && links.isEmpty() && diagnostics.isEmpty()) {
            status = "idle";
        }
        if (calculation != null) {
            status = "calculating";
            if (!calculation.isDone()) return;
            try {
                var plan = calculation.get();
                if (plan == null) {
                    fail("plan_failed", List.of());
                } else if (plan.simulation()) {
                    fail("incomplete_plan", missingItems(plan));
                } else {
                    var result = grid.getCraftingService().submitJob(plan, this, null, false, source);
                    if (result.successful() && result.link() != null) {
                        long submittedAmount = plan.finalOutput().amount();
                        links.add(result.link());
                        inFlight.put(result.link(), new Request(calculating, submittedAmount));
                        diagnostics.remove(calculating);
                        status = "submitted";
                        requested.computeIfPresent(calculating, (key, old) -> {
                            long left = old - Math.min(old, submittedAmount);
                            return left > 0 ? left : null;
                        });
                        machine.saveChanges();
                    } else {
                        var missing = new ArrayList<>(missingItems(plan));
                        if (result.errorDetail() instanceof GenericStack stack && stack.amount() > 0) {
                            missing.add(stack);
                        }
                        fail(result.errorCode() == null ? "submit_failed"
                                : result.errorCode().name().toLowerCase(java.util.Locale.ROOT), missing);
                    }
                }
            } catch (Exception error) {
                var previous = diagnostics.get(calculating);
                if (previous == null || !previous.status().equals("plan_failed")) {
                    com.atir.molecularmanipulator.MolecularManipulator.LOGGER.warn(
                            "Research crafting order failed at {} for {}", machine.getBlockPos(), calculating, error);
                }
                fail("plan_failed", List.of());
            } finally {
                if (calculating != null && requested.containsKey(calculating)) {
                    long amount = requested.remove(calculating);
                    requested.put(calculating, amount);
                }
                calculation = null;
                calculating = null;
                calculatingAmount = 0;
                retryAt = level.getGameTime() + 20;
            }
            return;
        }
        if (requested.isEmpty() || level.getGameTime() < retryAt) return;
        var crafting = grid.getCraftingService();
        var entry = requested.entrySet().iterator().next();
        if (!crafting.isCraftable(entry.getKey())) {
            var key = entry.getKey();
            long amount = requested.remove(key);
            requested.put(key, amount);
            diagnostics.put(key, new Diagnostic("not_craftable", amount, List.of()));
            status = "not_craftable";
            retryAt = level.getGameTime() + 100;
            return;
        }
        status = "calculating";
        long amount = Math.min(entry.getValue(), Long.MAX_VALUE);
        calculating = entry.getKey();
        calculatingAmount = amount;
        try {
            calculation = crafting.beginCraftingCalculation(level, new ICraftingSimulationRequester() {
                @Override public IActionSource getActionSource() { return source; }
                @Override public IGridNode getGridNode() { return machine.getMainNode().getNode(); }
            }, calculating, amount, CalculationStrategy.CRAFT_LESS);
            if (calculation == null) {
                fail("plan_failed", List.of());
                calculating = null;
                calculatingAmount = 0;
                retryAt = level.getGameTime() + 100;
            }
        } catch (RuntimeException error) {
            var previous = diagnostics.get(calculating);
            fail("plan_failed", List.of());
            if (previous == null || !previous.status().equals("plan_failed")) {
                com.atir.molecularmanipulator.MolecularManipulator.LOGGER.warn(
                        "Could not start research crafting calculation at {} for {}",
                        machine.getBlockPos(), calculating, error);
            }
            calculation = null;
            calculating = null;
            calculatingAmount = 0;
            retryAt = level.getGameTime() + 100;
        }
    }

    private void cleanupLinks() {
        for (var iterator = links.iterator(); iterator.hasNext();) {
            var link = iterator.next();
            if (link.isCanceled() || link.isDone()) {
                iterator.remove();
                var request = inFlight.remove(link);
                machine.saveChanges();
                if (link.isCanceled() && request != null && request.amount() > 0) {
                    requested.merge(request.key(), request.amount(), ResearchMaterialOrderService::saturatedAdd);
                    diagnostics.put(request.key(), new Diagnostic("canceled", request.amount(), List.of()));
                }
            }
        }
    }

    @Override public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.copyOf(links);
    }

    @Override public long insertCraftedItems(ICraftingLink link, appeng.api.stacks.AEKey key,
                                             long amount, Actionable mode) {
        var grid = machine.getMainNode().getGrid();
        if (grid == null || amount <= 0) return 0;
        long inserted = grid.getStorageService().getInventory().insert(key, amount, mode, source);
        if (mode == Actionable.MODULATE && inserted > 0 && link != null) {
            inFlight.computeIfPresent(link, (ignored, request) -> key.equals(request.key())
                    ? new Request(request.key(), Math.max(0, request.amount() - inserted)) : request);
            machine.saveChanges();
        }
        return inserted;
    }

    @Override public void jobStateChange(ICraftingLink link) {
        if (link != null && (link.isDone() || link.isCanceled())) {
            links.remove(link);
            var request = inFlight.remove(link);
            machine.saveChanges();
            if (link.isCanceled() && request != null && request.amount() > 0) {
                requested.merge(request.key(), request.amount(), ResearchMaterialOrderService::saturatedAdd);
                diagnostics.put(request.key(), new Diagnostic("canceled", request.amount(), List.of()));
            }
        }
    }

    private void fail(String reason, List<GenericStack> missing) {
        status = reason;
        if (calculating != null) diagnostics.put(calculating,
                new Diagnostic(reason, calculatingAmount, List.copyOf(missing)));
    }

    static List<GenericStack> missingItems(ICraftingPlan plan) {
        var missing = new ArrayList<GenericStack>();
        for (var entry : plan.missingItems()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                missing.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        missing.sort(Comparator.comparing(GenericStack::what, DISPLAY_ORDER));
        return missing;
    }

    @Override public IGridNode getActionableNode() {
        return machine.getMainNode().getNode();
    }

    private static long saturatedAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.put("requested", encode(requested, registries));
        var submitted = new ListTag();
        inFlight.values().forEach(request -> {
            if (request.amount() > 0) {
                submitted.add(GenericStack.writeTag(registries, new GenericStack(request.key(), request.amount())));
            }
        });
        tag.put("submitted", submitted);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        requested.clear();
        diagnostics.clear();
        links.clear();
        inFlight.clear();
        calculation = null;
        calculating = null;
        calculatingAmount = 0;
        status = "queued";
        retryAt = 0;
        for (var stack : decode(tag.getList("requested", 10), registries)) {
            requested.merge(stack.key(), stack.amount(), ResearchMaterialOrderService::saturatedAdd);
        }
        for (var stack : decode(tag.getList("submitted", 10), registries)) {
            requested.merge(stack.key(), stack.amount(), ResearchMaterialOrderService::saturatedAdd);
        }
    }

    private static ListTag encode(Map<AEItemKey, Long> values, HolderLookup.Provider registries) {
        var list = new ListTag();
        values.forEach((key, amount) -> {
            if (amount > 0) list.add(GenericStack.writeTag(registries, new GenericStack(key, amount)));
        });
        return list;
    }

    private static List<Request> decode(ListTag list, HolderLookup.Provider registries) {
        var values = new java.util.ArrayList<Request>();
        for (int i = 0; i < list.size(); i++) {
            try {
                var stack = GenericStack.readTag(registries, list.getCompound(i));
                if (stack != null && stack.what() instanceof AEItemKey key && stack.amount() > 0) {
                    values.add(new Request(key, stack.amount()));
                }
            } catch (RuntimeException ignored) { }
        }
        return values;
    }

    private record Request(AEItemKey key, long amount) { }
    private record Diagnostic(String status, long amount, List<GenericStack> missing) { }
}
