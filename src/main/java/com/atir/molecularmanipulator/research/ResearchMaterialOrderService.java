package com.atir.molecularmanipulator.research;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.google.common.collect.ImmutableSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import net.minecraft.network.chat.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Queues missing research materials through the connected AE2 crafting service. */
public final class ResearchMaterialOrderService implements ICraftingRequester {
    private final MatterFabricationBlockEntity machine;
    private final MachineSource source;
    private final Map<AEItemKey, Long> requested = new LinkedHashMap<>();
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

    public void clear() {
        requested.clear();
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
        if (calculation != null) {
            status = "calculating";
            if (!calculation.isDone()) return;
            try {
                var plan = calculation.get();
                var cpu = grid.getCraftingService().getCpus().stream()
                        .filter(candidate -> !candidate.isBusy()).findFirst().orElse(null);
                if (plan == null) {
                    status = "plan_failed";
                } else if (cpu == null) {
                    status = "waiting_cpu";
                } else {
                    var result = grid.getCraftingService().submitJob(plan, this, cpu, false, source);
                    if (result.successful() && result.link() != null) {
                        links.add(result.link());
                        inFlight.put(result.link(), new Request(calculating, calculatingAmount));
                        status = "submitted";
                        requested.computeIfPresent(calculating, (key, old) -> {
                            long left = old - calculatingAmount;
                            return left > 0 ? left : null;
                        });
                    } else {
                        status = "submit_failed";
                    }
                }
            } catch (Exception ignored) {
                // Keep the request for a later CPU/network retry.
                status = "submit_failed";
            } finally {
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
            requested.remove(entry.getKey());
            status = requested.isEmpty() ? "not_craftable" : "queued";
            return;
        }
        status = "calculating";
        long amount = Math.min(entry.getValue(), Long.MAX_VALUE);
        calculation = crafting.beginCraftingCalculation(level, new ICraftingSimulationRequester() {
            @Override public IActionSource getActionSource() { return source; }
            @Override public IGridNode getGridNode() { return machine.getMainNode().getNode(); }
        }, entry.getKey(), amount, CalculationStrategy.CRAFT_LESS);
        calculating = entry.getKey();
        calculatingAmount = amount;
    }

    private void cleanupLinks() {
        for (var iterator = links.iterator(); iterator.hasNext();) {
            var link = iterator.next();
            if (link.isCanceled() || link.isDone()) {
                iterator.remove();
                inFlight.remove(link);
            }
        }
        if (requested.isEmpty() && calculation == null && links.isEmpty()) status = "idle";
    }

    @Override public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.copyOf(links);
    }

    @Override public long insertCraftedItems(ICraftingLink link, appeng.api.stacks.AEKey key,
                                             long amount, Actionable mode) {
        var grid = machine.getMainNode().getGrid();
        if (grid == null || amount <= 0) return 0;
        return grid.getStorageService().getInventory().insert(key, amount, mode, source);
    }

    @Override public void jobStateChange(ICraftingLink link) {
        if (link != null && (link.isDone() || link.isCanceled())) {
            links.remove(link);
            inFlight.remove(link);
        }
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
            var value = GenericStack.writeTag(registries, new GenericStack(request.key(), request.amount()));
            submitted.add(value);
        });
        tag.put("submitted", submitted);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        requested.clear();
        links.clear();
        inFlight.clear();
        calculation = null;
        calculating = null;
        calculatingAmount = 0;
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
}
