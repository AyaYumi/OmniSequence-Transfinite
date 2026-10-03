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
import net.minecraft.resources.ResourceLocation;

/** Queues missing research materials through the connected AE2 crafting service. */
public final class ResearchMaterialOrderService implements ICraftingRequester {
    private static final Comparator<AEKey> DISPLAY_ORDER = Comparator
            .comparing((AEKey key) -> key.getClass().getName())
            .thenComparing(key -> key.getId().toString())
            .thenComparing(AEKey::toString);
    private final MatterFabricationBlockEntity machine;
    private final MachineSource source;
    private final Map<Target, Long> requested = new LinkedHashMap<>();
    private final Map<Target, Diagnostic> diagnostics = new LinkedHashMap<>();
    private final Set<ICraftingLink> links = new LinkedHashSet<>();
    /** Remaining CPU deliveries; native links reconnect after world reload. */
    private final Map<ICraftingLink, Request> inFlight = new LinkedHashMap<>();
    private Future<ICraftingPlan> calculation;
    private Target calculating;
    private long calculatingAmount;
    private long retryAt;
    private String status = "idle";

    public ResearchMaterialOrderService(MatterFabricationBlockEntity machine) {
        this.machine = machine;
        this.source = new MachineSource(machine);
    }

    public void request(Map<AEItemKey, Long> missing) { request(null, missing); }

    public void request(ResourceLocation owner, Map<AEItemKey, Long> missing) {
        if (missing == null) return;
        missing.forEach((key, amount) -> {
            if (key != null && amount != null && amount > 0) {
                var target = new Target(owner, key);
                requested.merge(target, amount, ResearchMaterialOrderService::saturatedAdd);
                diagnostics.remove(target);
            }
        });
        if (!requested.isEmpty()) status = "queued";
    }

    /** Replace outstanding needs using current stock; submitted outputs are counted only once. */
    public void reconcile(ResourceLocation owner, Map<AEItemKey, Long> deficits) {
        var next = new LinkedHashMap<Target, Long>();
        deficits.forEach((key, amount) -> {
            long remaining = Math.max(0L, amount - Math.min(amount, activeAmount(owner, key)));
            if (remaining > 0) next.put(new Target(owner, key), remaining);
        });
        boolean changed = requested.entrySet().removeIf(entry -> java.util.Objects.equals(owner, entry.getKey().owner())
                && !next.containsKey(entry.getKey()));
        for (var entry : next.entrySet()) {
            var previous = requested.put(entry.getKey(), entry.getValue());
            changed |= !java.util.Objects.equals(previous, entry.getValue());
        }
        diagnostics.keySet().removeIf(target -> java.util.Objects.equals(owner, target.owner())
                && !deficits.containsKey(target.key()));
        if (!requested.isEmpty()) status = "queued";
        if (changed) machine.saveChanges();
    }

    public boolean hasPending() {
        return calculation != null || !requested.isEmpty() || !links.isEmpty() || !inFlight.isEmpty();
    }

    public long pendingAmount(AEItemKey key) {
        long total = 0;
        for (var entry : requested.entrySet()) if (entry.getKey().key().equals(key)) total = saturatedAdd(total, entry.getValue());
        for (var request : inFlight.values()) if (request.key().equals(key)) total = saturatedAdd(total, request.amount());
        return total;
    }

    private long activeAmount(ResourceLocation owner, AEItemKey key) {
        long total = 0;
        for (var request : inFlight.values()) if (java.util.Objects.equals(owner, request.owner()) && key.equals(request.key()))
            total = saturatedAdd(total, request.amount());
        return total;
    }

    /** Cancel only this research's CPU requests, leaving other research orders intact. */
    public void cancel(ResourceLocation owner) {
        requested.keySet().removeIf(target -> java.util.Objects.equals(owner, target.owner()));
        diagnostics.keySet().removeIf(target -> java.util.Objects.equals(owner, target.owner()));
        if (calculating != null && java.util.Objects.equals(owner, calculating.owner())) {
            if (calculation != null) calculation.cancel(true);
            calculation = null; calculating = null; calculatingAmount = 0;
        }
        var canceled = inFlight.entrySet().stream().filter(entry -> java.util.Objects.equals(owner, entry.getValue().owner()))
                .map(Map.Entry::getKey).toList();
        for (var link : canceled) { inFlight.remove(link); links.remove(link); link.cancel(); }
        if (!hasPending()) status = "idle";
        machine.saveChanges();
    }

    public void adoptLegacyOwner(ResourceLocation owner) {
        var old=new LinkedHashMap<>(requested);requested.clear();
        old.forEach((target,amount)->requested.merge(target.owner()==null?new Target(owner,target.key()):target,amount,ResearchMaterialOrderService::saturatedAdd));
        inFlight.replaceAll((link,request)->request.owner()==null?new Request(owner,request.key(),request.amount()):request);
    }

    public String status() { return status; }
    public int queuedTypes() { return requested.size(); }
    public int activeJobs() { return links.size() + (calculation == null ? 0 : 1); }

    public void reportUncraftable(AEItemKey key, long amount) { reportUncraftable(null, key, amount); }
    public void reportUncraftable(ResourceLocation owner, AEItemKey key, long amount) {
        if (key != null && amount > 0) { diagnostics.put(new Target(owner, key), new Diagnostic("not_craftable", amount, List.of())); status = "not_craftable"; }
    }

    public JsonObject clientState(HolderLookup.Provider registries) {
        var result = new JsonObject();
        result.addProperty("status", status); result.addProperty("queued", queuedTypes()); result.addProperty("active", activeJobs());
        var entries = new JsonArray(); var targets = new LinkedHashSet<Target>(requested.keySet());
        inFlight.values().forEach(request -> targets.add(request.target())); targets.addAll(diagnostics.keySet());
        var ops = JsonOps.INSTANCE;
        targets.stream().sorted(Comparator.comparing(Target::key, DISPLAY_ORDER)
                .thenComparing(target -> String.valueOf(target.owner()))).forEach(target -> {
            long active = activeAmount(target.owner(), target.key());
            long amount = saturatedAdd(requested.getOrDefault(target,0L), active);
            var diagnostic = diagnostics.get(target); if (amount == 0 && diagnostic != null) amount = diagnostic.amount();
            var encoded = com.atir.molecularmanipulator.crafting.ForgeRecipeCodecs.GENERIC_STACK.encodeStart(ops,new GenericStack(target.key(),Math.max(1,amount))).result();
            if (encoded.isEmpty()) return;
            var entry = new JsonObject(); entry.add("target",encoded.get());
            if (target.owner() != null) entry.addProperty("owner",target.owner().toString());
            entry.addProperty("queued", requested.getOrDefault(target,0L)); entry.addProperty("active",active);
            entry.addProperty("status", diagnostic != null ? diagnostic.status() : target.equals(calculating) ? "calculating" : requested.containsKey(target) ? "queued" : "submitted");
            var missing = new JsonArray(); if (diagnostic != null) diagnostic.missing().forEach(stack -> com.atir.molecularmanipulator.crafting.ForgeRecipeCodecs.GENERIC_STACK.encodeStart(ops,stack).result().ifPresent(missing::add));
            entry.add("missing",missing); entries.add(entry);
        });
        result.add("items",entries); return result;
    }

    static List<AEItemKey> displayOrder(Set<AEItemKey> keys) {
        return keys.stream().sorted(DISPLAY_ORDER).toList();
    }

    public void clear() {
        if(calculation!=null)calculation.cancel(true);
        var active=List.copyOf(links);inFlight.clear();links.clear();
        active.forEach(ICraftingLink::cancel);
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
        if(!hasPending() && diagnostics.isEmpty()) {status="idle";return;}
        var level = machine.getLevel();
        var grid = machine.getMainNode().getGrid();
        if (level == null || grid == null || !machine.getMainNode().isActive()) return;
        cleanupLinks();
        if (!diagnostics.isEmpty() && level.getGameTime() % 20 == 0) {
            var storage = grid.getStorageService().getInventory();
            diagnostics.entrySet().removeIf(entry -> !requested.containsKey(entry.getKey())
                    && activeAmount(entry.getKey().owner(), entry.getKey().key()) == 0
                    && storage.extract(entry.getKey().key(), entry.getValue().amount(), Actionable.SIMULATE, source)
                            >= entry.getValue().amount());
        }
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
                } else if (plan.finalOutput().amount() > requested.getOrDefault(calculating, 0L)) {
                    status = "queued";
                } else if (plan.simulation()) {
                    fail("incomplete_plan", missingItems(plan));
                } else {
                    var result = grid.getCraftingService().submitJob(plan, this, null, false, source);
                    if (result.successful() && result.link() != null) {
                        long submittedAmount = plan.finalOutput().amount();
                        links.add(result.link());
                        inFlight.put(result.link(), new Request(calculating.owner(), calculating.key(), submittedAmount));
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
                    com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
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
        if (!crafting.isCraftable(entry.getKey().key())) {
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
            }, calculating.key(), amount, CalculationStrategy.CRAFT_LESS);
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
                com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
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
                    requested.merge(request.target(), request.amount(), ResearchMaterialOrderService::saturatedAdd);
                    diagnostics.put(request.target(), new Diagnostic("canceled", request.amount(), List.of()));
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
        var request = inFlight.get(link);
        long inserted = request != null && request.owner() != null && key instanceof AEItemKey item
                && machine.getResearch().isPreparing(request.owner())
                ? machine.getResearch().acceptOrderedMaterial(machine, request.owner(), item, amount, mode)
                : grid.getStorageService().getInventory().insert(key, amount, mode, source);
        if (mode == Actionable.MODULATE && inserted > 0 && link != null) {
            inFlight.computeIfPresent(link, (ignored, current) -> key.equals(current.key())
                    ? new Request(current.owner(),current.key(),Math.max(0,current.amount()-inserted)) : current);
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
                requested.merge(request.target(), request.amount(), ResearchMaterialOrderService::saturatedAdd);
                diagnostics.put(request.target(), new Diagnostic("canceled", request.amount(), List.of()));
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

    public CompoundTag save() { return save(true); }

    /** Portable controllers queue unfinished amounts because their old CPU links are canceled. */
    public CompoundTag save(boolean reconnect) {
        var tag = new CompoundTag(); var queued = new ListTag();
        requested.forEach((target,amount) -> queued.add(encode(new Request(target.owner(),target.key(),amount))));
        tag.put("requested",queued);
        var submitted = new ListTag();
        inFlight.forEach((link, request) -> {
            if (request.amount() <= 0) return;
            var value = encode(request);
            if (reconnect) {
                var savedLink = new CompoundTag(); link.writeToNBT(savedLink); value.put("link", savedLink);
            }
            submitted.add(value);
        });
        tag.put("submitted",submitted); return tag;
    }

    public void load(CompoundTag tag) {
        requested.clear(); diagnostics.clear(); links.clear(); inFlight.clear();
        calculation = null; calculating = null; calculatingAmount = 0; status = "queued"; retryAt = 0;
        for (var field : List.of("requested","submitted")) for (var value : tag.getList(field,10)) {
            try {
                var raw = (CompoundTag)value; var stack = GenericStack.readTag(raw);
                ResourceLocation owner = raw.contains("owner") ? ResourceLocation.tryParse(raw.getString("owner")) : null;
                if (stack != null && stack.what() instanceof AEItemKey key && stack.amount() > 0) {
                    var request = new Request(owner, key, stack.amount());
                    if (field.equals("submitted") && raw.contains("link")) {
                        var link = new appeng.crafting.CraftingLink(raw.getCompound("link"), this);
                        if (!link.isDone() && !link.isCanceled()) { links.add(link); inFlight.put(link, request); }
                        else if (link.isCanceled()) requested.merge(request.target(), request.amount(), ResearchMaterialOrderService::saturatedAdd);
                    } else requested.merge(request.target(), request.amount(), ResearchMaterialOrderService::saturatedAdd);
                }
            } catch(RuntimeException ignored) { }
        }
        var grid = machine.getMainNode().getGrid();
        if (grid != null && grid.getCraftingService() instanceof appeng.me.service.CraftingService crafting)
            for (var link : links) if (link instanceof appeng.crafting.CraftingLink nativeLink) crafting.addLink(nativeLink);
    }

    private static CompoundTag encode(Request request) {
        var tag = GenericStack.writeTag(new GenericStack(request.key(),request.amount()));
        if(request.owner()!=null)tag.putString("owner",request.owner().toString());
        return tag;
    }
    private record Target(ResourceLocation owner, AEItemKey key) { }
    private record Request(ResourceLocation owner, AEItemKey key, long amount) {
        Target target() { return new Target(owner,key); }
    }
    private record Diagnostic(String status, long amount, List<GenericStack> missing) { }
}
