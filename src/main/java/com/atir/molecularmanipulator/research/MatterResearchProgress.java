package com.atir.molecularmanipulator.research;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;

/** Repeated research with live-stock, all-material admission and durable rollback ownership. */
public final class MatterResearchProgress {
    private final Map<ResourceLocation, Integer> completions = new LinkedHashMap<>();
    private final Map<ResourceLocation, Task> tasks = new LinkedHashMap<>();
    private final Map<ResourceLocation, CompoundTag> unavailableTasks = new LinkedHashMap<>();
    private final Map<AEItemKey, Long> refunds = new LinkedHashMap<>();
    private final ListTag unavailableRefunds = new ListTag();
    private HolderLookup.Provider registries;
    private String lastError = "";
    private long visualCompletionSerial;
    private ResourceLocation lastVisualCompletion;
    private int lastVisualRound;

    public Set<ResourceLocation> completed() { return Set.copyOf(completions.keySet()); }
    public int completionCount(ResourceLocation id) { return completions.getOrDefault(id, 0); }
    public boolean hasProgress() { return !completions.isEmpty() || !tasks.isEmpty() || !unavailableTasks.isEmpty() || hasRefunds(); }
    public boolean hasStoredMaterials() { return !tasks.isEmpty() || !unavailableTasks.isEmpty() || hasRefunds(); }
    /** Clears material ownership after it has been packed into a dismantled controller. */
    public void clearStoredMaterials() {
        tasks.clear(); unavailableTasks.clear(); refunds.clear(); unavailableRefunds.clear(); lastError = "";
    }
    private boolean hasRefunds() { return !refunds.isEmpty() || !unavailableRefunds.isEmpty(); }
    public boolean hasTask(ResourceLocation id) { return tasks.containsKey(id); }
    public boolean isPaused(ResourceLocation id) { return tasks.containsKey(id) && tasks.get(id).paused; }

    public boolean start(MatterFabricationBlockEntity machine, ResourceLocation id) {
        if (tasks.containsKey(id)) return resume(machine, id);
        if (!ready(machine) || unavailableTasks.containsKey(id) || hasRefunds()) return false;
        var definition = MatterResearchApi.definitions(machine.getLevel()).stream().filter(holder -> holder.id().equals(id)).findFirst().orElse(null);
        if (definition == null || !prerequisitesMet(machine, definition.value())) return false;
        int count = completionCount(id);
        if (count >= definition.value().depths().size()) return false;
        try {
            registries = machine.getLevel().registryAccess();
            var task = new Task(definition.value(), encode(definition.value(), registries), count + 1);
            if (!pay(machine, task)) return false;
            tasks.put(id, task);
            refunds.clear();
            lastError = "";
            machine.saveChanges();
            return true;
        } catch (ArithmeticException error) { lastError = "cost_overflow"; return false; }
    }

    public boolean resume(MatterFabricationBlockEntity machine, ResourceLocation id) {
        var task = tasks.get(id);
        if (task == null || !task.paused || !ready(machine) || hasRefunds()) return false;
        var definition = MatterResearchApi.definitions(machine.getLevel()).stream().filter(holder -> holder.id().equals(id)).findFirst().orElse(null);
        if (definition == null || !prerequisitesMet(machine, definition.value())) return false;
        if (!task.supplied() && !pay(machine, task)) return false;
        refunds.clear(); task.paused = false; lastError = ""; machine.saveChanges(); return true;
    }

    public boolean setPaused(ResourceLocation id, boolean paused) {
        var task = tasks.get(id);
        if (task == null || task.paused == paused || !paused && !task.supplied()) return false;
        task.paused = paused; return true;
    }

    private boolean ready(MatterFabricationBlockEntity machine) {
        return machine.getLevel() != null && !machine.getLevel().isClientSide() && machine.isStructureFormed()
                && !machine.isBuilding() && !machine.isDismantling() && machine.getMainNode().isActive();
    }
    private boolean prerequisitesMet(MatterFabricationBlockEntity machine, MatterResearchRecipe definition) {
        return MatterResearchApi.prerequisitesMet(definition, MatterResearchApi.definitions(machine.getLevel()), this::completionCount);
    }

    /** Administrative replacement: end this research's active attempt so it cannot overwrite the assigned count. */
    void setCompletionCount(ResourceLocation id, int count) {
        if (count == 0) completions.remove(id); else completions.put(id, count);
        tasks.remove(id);
        unavailableTasks.remove(id);
        lastError = "";
    }

    /** Reads providers now, not IStorageService.getCachedInventory(). */
    private static Map<AEItemKey, Long> liveInventory(MatterFabricationBlockEntity machine) {
        var result = new LinkedHashMap<AEItemKey, Long>();
        var grid = machine.getMainNode().getGrid();
        if (grid != null) for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey item && entry.getLongValue() > 0) result.put(item, entry.getLongValue());
        }
        return result;
    }
    private static Map<AEItemKey, Long> plan(List<MatterResearchRecipe.Cost> costs, long[] paid, Map<AEItemKey, Long> stock) {
        var required = new java.util.ArrayList<Long>();
        for (int i = 0; i < costs.size(); i++) required.add(costs.get(i).count() - paid[i]);
        return ResearchMaterialAllocator.plan(required, stock, (index, key) -> costs.get(index).ingredient().test(key.toStack()));
    }

    private boolean pay(MatterFabricationBlockEntity machine, Task task) {
        registries = machine.getLevel().registryAccess();
        var requested = plan(task.costs, task.paid, liveInventory(machine));
        if (requested == null) { lastError = "insufficient"; return false; }
        var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
        var source = new MachineSource(machine);
        for (var entry : requested.entrySet()) {
            if (storage.extract(entry.getKey(), entry.getValue(), Actionable.SIMULATE, source) != entry.getValue()) {
                lastError = "stock_changed"; return false;
            }
        }
        try {
            for (var entry : requested.entrySet()) {
                long taken = storage.extract(entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
                if (taken > 0) { refunds.put(entry.getKey(), taken); machine.saveChanges(); }
                if (taken != entry.getValue()) { lastError = "stock_changed"; refund(machine); return false; }
            }
        } catch (RuntimeException error) {
            lastError = "stock_changed";
            MolecularManipulator.LOGGER.warn("Research inventory changed during admission at {}", machine.getBlockPos(), error);
            refund(machine); return false;
        }
        for (int i = 0; i < task.paid.length; i++) task.paid[i] = task.costs.get(i).count();
        return true;
    }

    private void refund(MatterFabricationBlockEntity machine) {
        var grid = machine.getMainNode().getGrid();
        if (grid == null || refunds.isEmpty()) return;
        var storage = grid.getStorageService().getInventory(); var source = new MachineSource(machine);
        var iterator = refunds.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long returned;
            try { returned = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, source); }
            catch (RuntimeException error) { continue; } // Keep ownership until a compatible provider accepts it.
            if (returned > 0) {
                long left = entry.getValue() - returned;
                if (left == 0) iterator.remove(); else entry.setValue(left);
                machine.saveChanges();
            }
        }
    }

    public void tick(MatterFabricationBlockEntity machine) {
        refund(machine);
        if (tasks.isEmpty()) return;
        var available = new LinkedHashMap<ResourceLocation, MatterResearchRecipe>();
        for (var holder : MatterResearchApi.definitions(machine.getLevel())) available.put(holder.id(), holder.value());
        var iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var task = entry.getValue();
            if (!available.containsKey(entry.getKey())) { task.status = "unavailable"; continue; }
            if (!task.supplied()) { task.paused = true; task.status = "legacy_materials"; continue; }
            if (task.paused) { task.status = "paused"; continue; }
            if (!prerequisitesMet(machine, available.get(entry.getKey()))) { task.status = "prerequisite"; continue; }
            if (!machine.isStructureFormed() || machine.isBuilding() || machine.isDismantling()) { task.status = "structure"; continue; }
            if (!machine.getMainNode().isActive()) { task.status = "network"; continue; }
            if (!machine.consumeResearchPower(task.definition.aePerTick())) { task.status = "power"; continue; }
            task.status = "running"; task.progress++; machine.saveChanges();
            if (task.progress >= task.definition.duration()) {
                completions.merge(entry.getKey(), task.round, Math::max); iterator.remove();
                visualCompletionSerial++;
                lastVisualCompletion = entry.getKey();
                lastVisualRound = task.round;
            }
        }
    }

    public CompoundTag save() {
        var tag = new CompoundTag(); var done = new ListTag(); var counts = new CompoundTag();
        completions.forEach((id, count) -> { done.add(StringTag.valueOf(id.toString())); counts.putInt(id.toString(), count); });
        tag.put("completed", done); tag.put("completions", counts);
        var active = new ListTag();
        tasks.forEach((id, task) -> {
            var value = new CompoundTag(); value.putString("id", id.toString()); value.putString("terms", task.terms);
            value.putLongArray("paid", task.paid); value.putInt("progress", task.progress); value.putBoolean("paused", task.paused);
            value.putInt("round", task.round); active.add(value);
        });
        unavailableTasks.values().forEach(value -> active.add(value.copy())); tag.put("tasks", active);
        var pending = new ListTag(); refunds.forEach((key, amount) -> pending.add(GenericStack.writeTag(registries, new GenericStack(key, amount))));
        unavailableRefunds.forEach(value -> pending.add(value.copy()));
        tag.put("refunds", pending); return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        this.registries = registries;
        completions.clear(); tasks.clear(); unavailableTasks.clear(); refunds.clear(); unavailableRefunds.clear(); lastError = "";
        var counts = tag.getCompound("completions");
        for (var key : counts.getAllKeys()) {
            var id = ResourceLocation.tryParse(key); int count = counts.getInt(key);
            if (id != null && count > 0) completions.put(id, count);
        }
        for (var value : tag.getList("completed", 8)) {
            var id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) completions.putIfAbsent(id, 1);
        }
        var active = tag.getList("tasks", 10);
        for (int i = 0; i < active.size(); i++) {
            var value = active.getCompound(i); var id = ResourceLocation.tryParse(value.getString("id"));
            int round = value.contains("round") ? value.getInt("round") : 1;
            if (id == null || completionCount(id) >= round) continue;
            try {
                String terms = value.getString("terms");
                var definition = MatterResearchRecipe.CODEC.codec().parse(registries.createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(terms)).getOrThrow();
                var task = new Task(definition, terms, round); var paid = value.getLongArray("paid");
                if (paid.length != task.paid.length) throw new IllegalArgumentException("Research cost snapshot length changed");
                for (int j = 0; j < paid.length; j++) task.paid[j] = Math.max(0, Math.min(paid[j], task.costs.get(j).count()));
                task.progress = Math.max(0, Math.min(value.getInt("progress"), definition.duration() - 1));
                task.paused = value.getBoolean("paused") || !task.supplied(); tasks.put(id, task);
            } catch (RuntimeException error) {
                unavailableTasks.put(id, value.copy());
                MolecularManipulator.LOGGER.warn("Keeping unavailable research task {} for a later compatible load: {}", id, error.getMessage());
            }
        }
        var pending = tag.getList("refunds", 10);
        for (int i = 0; i < pending.size(); i++) {
            var raw = pending.getCompound(i);
            try {
                var stack = GenericStack.readTag(registries, raw);
                if (stack == null || !(stack.what() instanceof AEItemKey key) || stack.amount() <= 0)
                    throw new IllegalArgumentException("Unavailable research refund");
                refunds.merge(key, stack.amount(), Math::addExact);
            } catch (RuntimeException error) { unavailableRefunds.add(raw.copy()); }
        }
    }

    public String clientState() { return state().toString(); }

    /** Bounded presentation snapshot, independent of whether a controller menu is open. */
    public ResearchVisualState visualState(long machineSalt) {
        var visible = tasks.entrySet().stream()
                .sorted(java.util.Comparator.<Map.Entry<ResourceLocation, Task>, Boolean>comparing(
                        entry -> !entry.getValue().paused && entry.getValue().status.equals("running"))
                        .reversed().thenComparing(entry -> entry.getKey().toString()))
                .limit(ResearchVisualState.MAX_VISIBLE_TASKS)
                .map(entry -> {
                    var task = entry.getValue();
                    long identity = ResearchVisualState.identity(entry.getKey(), task.round, machineSalt);
                    return new ResearchVisualState.Task(identity, ResearchVisualState.style(entry.getKey(), identity),
                            task.round, task.progress, task.definition.duration(),
                            !task.paused && task.supplied() && task.status.equals("running"));
                }).toList();
        int completionStyle = lastVisualCompletion == null ? 0 : ResearchVisualState.style(lastVisualCompletion,
                ResearchVisualState.identity(lastVisualCompletion, lastVisualRound, machineSalt));
        return new ResearchVisualState(visible, visualCompletionSerial, completionStyle);
    }

    public String clientState(MatterFabricationBlockEntity machine, ResourceLocation selected) {
        var root = state();
        if (selected == null) return root.toString();
        var holder = MatterResearchApi.definitions(machine.getLevel()).stream().filter(r -> r.id().equals(selected)).findFirst().orElse(null);
        if (holder == null) return root.toString();
        var live = new JsonObject(); live.addProperty("id", selected.toString());
        live.addProperty("online", machine.getMainNode().isActive());
        int count = completionCount(selected); var task = tasks.get(selected);
        int round = task != null ? task.round : count >= holder.value().depths().size() ? holder.value().depths().size() : count + 1;
        try {
            var costs = task != null ? task.costs : holder.value().costsFor(round);
            long[] paid = task == null ? new long[costs.size()] : task.paid;
            var stock = liveInventory(machine); var amounts = new JsonArray();
            for (var cost : costs) {
                long amount = 0;
                for (var entry : stock.entrySet()) if (cost.ingredient().test(entry.getKey().toStack())) {
                    amount = Long.MAX_VALUE - amount < entry.getValue() ? Long.MAX_VALUE : amount + entry.getValue();
                }
                amounts.add(amount);
            }
            live.add("available", amounts);
            live.addProperty("affordable", !hasRefunds() && plan(costs, paid, stock) != null);
        } catch (ArithmeticException error) { live.addProperty("affordable", false); live.addProperty("error", "cost_overflow"); }
        root.add("live", live); return root.toString();
    }

    private JsonObject state() {
        var root = new JsonObject(); var done = new JsonArray(); var counts = new JsonObject();
        completions.forEach((id, count) -> { done.add(id.toString()); counts.addProperty(id.toString(), count); });
        root.add("completed", done); root.add("counts", counts); root.addProperty("notice", hasRefunds() ? "refund_pending" : lastError);
        var active = new JsonObject();
        tasks.forEach((id, task) -> {
            var value = new JsonObject(); value.addProperty("progress", task.progress); value.addProperty("round", task.round);
            value.addProperty("paused", task.paused); value.addProperty("status", !task.supplied() ? "legacy_materials" : task.paused ? "paused" : task.status);
            var paid = new JsonArray(); for (long amount : task.paid) paid.add(amount); value.add("paid", paid);
            value.add("terms", JsonParser.parseString(task.terms)); active.add(id.toString(), value);
        });
        root.add("tasks", active); return root;
    }

    private static String encode(MatterResearchRecipe recipe, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var json = MatterResearchRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow().getAsJsonObject();
        json.add("depths", ResearchDepth.CODEC.listOf().encodeStart(ops, recipe.depths()).getOrThrow());
        return json.toString();
    }
    private static final class Task {
        final MatterResearchRecipe definition; final String terms; final int round;
        final List<MatterResearchRecipe.Cost> costs; final long[] paid;
        int progress; boolean paused; String status = "running";
        Task(MatterResearchRecipe definition, String terms, int round) {
            this.definition = definition; this.terms = terms; this.round = round;
            this.costs = definition.costsFor(round); this.paid = new long[costs.size()];
        }
        boolean supplied() { for (int i = 0; i < paid.length; i++) if (paid[i] < costs.get(i).count()) return false; return true; }
    }
}
