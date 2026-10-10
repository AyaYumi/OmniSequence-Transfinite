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
import java.util.ArrayList;
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
    private final Map<ResourceLocation, CompoundTag> unavailablePreparations = new LinkedHashMap<>();
    private final Map<ResourceLocation, CompoundTag> unavailableTasks = new LinkedHashMap<>();
    private final Map<AEItemKey, Long> refunds = new LinkedHashMap<>();
    private final Set<ResourceLocation> autoStart = new java.util.LinkedHashSet<>();
    private final Map<ResourceLocation, Preparation> preparations = new LinkedHashMap<>();
    private final ListTag unavailableRefunds = new ListTag();
    private HolderLookup.Provider registries;
    private String lastError = "";
    private long visualCompletionSerial;
    private ResourceLocation lastVisualCompletion;
    private int lastVisualRound;
    private long permissionRevision;
    private JsonObject cachedLive;
    private ResourceLocation cachedLiveId;
    private Task cachedLiveTask;
    private Object cachedLiveIndex, cachedLiveGrid;
    private int cachedLiveRound;
    private long cachedLiveTick = Long.MIN_VALUE;
    private Map<AEItemKey,Long> preparationStock;
    private Map<net.minecraft.world.item.Item, List<AEItemKey>> preparationKeyIndex;
    private long preparationStockTick=Long.MIN_VALUE;
    private Object preparationStockGrid,preparationStockIndex;
    public long permissionRevision() { return permissionRevision; }

    public Set<ResourceLocation> completed() { return Set.copyOf(completions.keySet()); }
    public int completionCount(ResourceLocation id) { return completions.getOrDefault(id, 0); }
    public boolean hasProgress() { return !completions.isEmpty() || !tasks.isEmpty() || !unavailableTasks.isEmpty()
            || !autoStart.isEmpty() || !preparations.isEmpty() || !unavailablePreparations.isEmpty() || hasRefunds(); }
    public boolean hasStoredMaterials() { return !tasks.isEmpty() || !unavailableTasks.isEmpty() || !autoStart.isEmpty() || !preparations.isEmpty() || !unavailablePreparations.isEmpty() || hasRefunds(); }
    /** Clears material ownership after it has been packed into a dismantled controller. */
    public void clearStoredMaterials() {
        tasks.clear(); unavailableTasks.clear(); refunds.clear(); unavailableRefunds.clear(); autoStart.clear(); preparations.clear(); unavailablePreparations.clear(); lastError = "";cachedLive=null;preparationStock=null;
    }
    private boolean hasRefunds() { return !refunds.isEmpty() || !unavailableRefunds.isEmpty(); }
    public boolean hasTask(ResourceLocation id) { return tasks.containsKey(id); }
    public boolean isPaused(ResourceLocation id) { return tasks.containsKey(id) && tasks.get(id).paused; }

    public boolean isPreparing(ResourceLocation id) { return preparations.containsKey(id) || autoStart.contains(id) || unavailablePreparations.containsKey(id); }
    public boolean isOrderedResearch(ResourceLocation id) { var task = tasks.get(id); return task != null && task.ordered; }

    /** Keep one durable target while repeatedly re-evaluating live stock and CPU requests. */
    public boolean orderMissing(MatterFabricationBlockEntity machine, ResourceLocation id) { return orderMissing(machine,id,false); }
    public boolean orderMissing(MatterFabricationBlockEntity machine, ResourceLocation id, boolean toMaximum) {
        if (!ready(machine) || tasks.containsKey(id) || unavailableTasks.containsKey(id) || unavailablePreparations.containsKey(id) || hasRefunds()) return false;
        var holder = com.atir.molecularmanipulator.crafting.MatterRecipeIndex.get(machine.getLevel()).research(id);
        if (holder == null || !prerequisitesMet(machine, holder.value())) return false;
        int completed = completionCount(id);
        if (completed >= holder.value().depths().size()) return false;
        try {
            registries = machine.getLevel().registryAccess();
            if (!preparations.containsKey(id)) {
                var prep = new Preparation(holder.value(),encode(holder.value()),completed+1,
                        toMaximum ? holder.value().depths().size() : completed+1);
                preparations.put(id,prep); autoStart.add(id); cachedLive = null; machine.saveChanges();
            }
            replenish(machine,id);
            return true;
        } catch(ArithmeticException error) { lastError = "cost_overflow"; return false; }
    }

    private void replenish(MatterFabricationBlockEntity machine, ResourceLocation id) {
        replenish(machine,id,livePreparationStock(machine));
    }
    private Map<AEItemKey,Long> livePreparationStock(MatterFabricationBlockEntity machine) {
        long tick=machine.getLevel().getGameTime();var grid=machine.getMainNode().getGrid();
        var index=com.atir.molecularmanipulator.crafting.MatterRecipeIndex.get(machine.getLevel());
        if(preparationStock==null || tick!=preparationStockTick || grid!=preparationStockGrid || index!=preparationStockIndex) {
            preparationStock=liveInventory(machine);preparationKeyIndex=ResearchIngredientIndex.indexStock(preparationStock);preparationStockTick=tick;preparationStockGrid=grid;preparationStockIndex=index;
        }
        return preparationStock;
    }
    private void replenish(MatterFabricationBlockEntity machine, ResourceLocation id, Map<AEItemKey,Long> sharedStock) {
        var prep = preparations.get(id);
        if (prep == null || !ready(machine) || hasRefunds()) return;
        var costs = prep.costs;
        var matches = prep.matcher.matches(sharedStock, preparationKeyIndex);
        var stock = new LinkedHashMap<AEItemKey, Long>();
        matches.keySet().forEach(key -> stock.put(key, sharedStock.get(key)));
        var reservedMatches = prep.matcher.matches(prep.reserved);
        reservedMatches.forEach((key, rows) -> {
            matches.put(key, rows); stock.merge(key, prep.reserved.get(key), MatterResearchProgress::saturatedAdd);
        });
        var deficits = ResearchMaterialAllocator.deficits(costs.stream().map(MatterResearchRecipe.Cost::count).toList(),
                stock, (row, key) -> matches.get(key).get(row));
        var requested = new LinkedHashMap<AEItemKey,Long>();
        var failures=new LinkedHashMap<AEItemKey,Long>();
        var crafting=machine.getMainNode().getGrid().getCraftingService();boolean uncraftable=false;
        for(int index=0;index<costs.size();index++) {
            if(deficits.get(index)<=0)continue;
            AEItemKey selected=null;
            for(var example:costs.get(index).ingredient().getItems()) {
                var key=AEItemKey.of(example);if(key!=null && crafting.isCraftable(key)){selected=key;break;}
            }
            if(selected==null) {
                var examples=costs.get(index).ingredient().getItems();
                if(examples.length>0)failures.merge(AEItemKey.of(examples[0]),deficits.get(index),Math::addExact);
                uncraftable=true;
            } else requested.merge(selected,deficits.get(index),Math::addExact);
        }
        machine.getResearchOrders().reconcile(id,requested);
        failures.forEach((key,amount)->machine.getResearchOrders().reportUncraftable(id,key,amount));
        lastError=uncraftable ? "not_craftable" : "orders_submitted";
        // Pay only after all requirements can be allocated simultaneously, including reserved output.
        if (deficits.stream().allMatch(amount -> amount == 0)) start(machine, id, sharedStock);
    }

    public long acceptOrderedMaterial(MatterFabricationBlockEntity machine, ResourceLocation id, AEItemKey key,
            long amount, Actionable mode) {
        var prep=preparations.get(id);
        if(prep==null || amount<=0)return 0;
        boolean matches=prep.costs.stream().anyMatch(cost -> cost.ingredient().test(key.toStack()));
        if(!matches)return 0;
        long accepted=Math.min(amount,Long.MAX_VALUE-prep.reserved.getOrDefault(key,0L));
        if(mode==Actionable.MODULATE && accepted>0) {
            prep.reserved.merge(key,accepted,Math::addExact); cachedLive=null;machine.saveChanges();
        }
        return accepted;
    }

    public boolean stopPreparation(MatterFabricationBlockEntity machine, ResourceLocation id) {
        if(tasks.containsKey(id) || !isPreparing(id))return false;
        autoStart.remove(id);var prep=preparations.remove(id);
        var unavailable = unavailablePreparations.remove(id);
        if (unavailable != null) unavailable.getList("reserved", 10).forEach(value -> unavailableRefunds.add(value.copy()));
        machine.getResearchOrders().cancel(id);
        if(prep!=null)prep.reserved.forEach((key,amount)->refunds.merge(key,amount,Math::addExact));
        lastError="";cachedLive=null;preparationStock=null;refund(machine);machine.saveChanges();return true;
    }

    public boolean start(MatterFabricationBlockEntity machine, ResourceLocation id) {
        return start(machine, id, null);
    }

    private boolean start(MatterFabricationBlockEntity machine, ResourceLocation id, Map<AEItemKey, Long> sharedStock) {
        if (tasks.containsKey(id)) return resume(machine, id);
        if (!ready(machine) || unavailableTasks.containsKey(id) || unavailablePreparations.containsKey(id) || hasRefunds()) return false;
        var definition = com.atir.molecularmanipulator.crafting.MatterRecipeIndex.get(machine.getLevel()).research(id);
        if (definition == null || !prerequisitesMet(machine, definition.value())) return false;
        int count = completionCount(id);
        if (count >= definition.value().depths().size()) return false;
        try {
            registries = machine.getLevel().registryAccess();
            var prep=preparations.get(id);
            var task=prep==null ? new Task(definition.value(),encode(definition.value()),count+1)
                    :new Task(prep.definition,prep.terms,prep.firstRound,prep.round,true);
            if (!pay(machine, task, prep, sharedStock)) return false;
            tasks.put(id, task);
            autoStart.remove(id);
            preparations.remove(id);
            if(prep!=null)machine.getResearchOrders().cancel(id);
            cachedLive = null;
            if (sharedStock == null) preparationStock = null;
            lastError = "";
            machine.saveChanges();
            return true;
        } catch (ArithmeticException error) { lastError = "cost_overflow"; return false; }
    }

    public boolean resume(MatterFabricationBlockEntity machine, ResourceLocation id) {
        var task = tasks.get(id);
        if (task == null || task.ordered || !task.paused || !ready(machine) || hasRefunds()) return false;
        var definition = MatterResearchApi.definitions(machine.getLevel()).stream().filter(holder -> holder.id().equals(id)).findFirst().orElse(null);
        if (definition == null || !prerequisitesMet(machine, definition.value())) return false;
        if (!task.supplied() && !pay(machine, task)) return false;
        refunds.clear(); task.paused = false; lastError = ""; machine.saveChanges(); return true;
    }

    public boolean setPaused(ResourceLocation id, boolean paused) {
        var task = tasks.get(id);
        if (task == null || task.ordered || task.paused == paused || !paused && !task.supplied()) return false;
        task.paused = paused; return true;
    }

    private boolean ready(MatterFabricationBlockEntity machine) {
        return machine.getLevel() != null && !machine.getLevel().isClientSide() && machine.isStructureFormed()
                && !machine.isBuilding() && !machine.isDismantling() && machine.getMainNode().isActive();
    }
    private boolean prerequisitesMet(MatterFabricationBlockEntity machine, MatterResearchRecipe definition) {
        var index = com.atir.molecularmanipulator.crafting.MatterRecipeIndex.get(machine.getLevel());
        for (var id : definition.prerequisites()) {
            var prerequisite = index.research(id);
            if (prerequisite == null || completionCount(id) < MatterResearchApi.requiredPrerequisiteLevel(definition, prerequisite)) return false;
        }
        return true;
    }

    /** Administrative replacement: end this research's active attempt so it cannot overwrite the assigned count. */
    void setCompletionCount(MatterFabricationBlockEntity machine, ResourceLocation id, int count) {
        if (count == 0) completions.remove(id); else completions.put(id, count);
        permissionRevision++;
        cachedLive = null;
        machine.getResearchOrders().cancel(id);
        preparationStock = null;
        tasks.remove(id);
        autoStart.remove(id);var prep=preparations.remove(id);
        if(prep!=null)prep.reserved.forEach((key,amount)->refunds.merge(key,amount,Math::addExact));
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
        var matches = new ResearchIngredientIndex(costs).matches(stock);
        var relevant = new LinkedHashMap<AEItemKey, Long>();
        matches.keySet().forEach(key -> relevant.put(key, stock.get(key)));
        return ResearchMaterialAllocator.plan(required, relevant, (index, key) -> matches.get(key).get(index));
    }

    private boolean pay(MatterFabricationBlockEntity machine, Task task) { return pay(machine, task, null, null); }
    private boolean pay(MatterFabricationBlockEntity machine, Task task, Preparation prep, Map<AEItemKey, Long> sharedStock) {
        registries = machine.getLevel().registryAccess();
        // Preparation already read the providers this tick. Reuse its snapshot for planning;
        // exact SIMULATE/MODULATE calls below still validate every withdrawal against live stock.
        var stock = sharedStock == null ? liveInventory(machine) : new LinkedHashMap<>(sharedStock);
        if(prep!=null)prep.reserved.forEach((key,amount)->stock.merge(key,amount,MatterResearchProgress::saturatedAdd));
        var allocation = plan(task.costs, task.paid, stock);
        var requested = allocation == null ? null : new LinkedHashMap<>(allocation);
        if(prep!=null && requested!=null)requested.replaceAll((key,amount)->amount-Math.min(amount,prep.reserved.getOrDefault(key,0L)));
        if (requested == null) { lastError = "insufficient"; return false; }
        var storage = machine.getMainNode().getGrid().getStorageService().getInventory();
        var source = new MachineSource(machine);
        try {
            // A partial extraction or callback may change stock even if admission later fails.
            preparationStock = null;
            for (var entry : requested.entrySet()) {
                if (entry.getValue() == 0) continue;
                if (storage.extract(entry.getKey(), entry.getValue(), Actionable.SIMULATE, source) != entry.getValue()) {
                    lastError = "stock_changed"; return false;
                }
            }
            for (var entry : requested.entrySet()) {
                if (entry.getValue() == 0) continue;
                long taken = storage.extract(entry.getKey(), entry.getValue(), Actionable.MODULATE, source);
                if (taken > 0) { refunds.put(entry.getKey(), taken); machine.saveChanges(); }
                if (taken != entry.getValue()) { lastError = "stock_changed"; refund(machine); return false; }
            }
        } catch (RuntimeException error) {
            lastError = "stock_changed";
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Research inventory changed during admission at {}", machine.getBlockPos(), error);
            refund(machine); return false;
        }
        refunds.clear();
        if (sharedStock != null) {
            requested.forEach((key, amount) -> sharedStock.computeIfPresent(key,
                    (ignored, available) -> available - Math.min(available, amount)));
            // Keys stay in the index until the next live read; zero amounts no longer match.
            preparationStock = sharedStock;
        }
        if(prep!=null) {
            prep.reserved.forEach((key,amount)-> {long extra=amount-Math.min(amount,allocation.getOrDefault(key,0L));if(extra>0)refunds.merge(key,extra,Math::addExact);});
            prep.reserved.clear();
        }
        for (int i = 0; i < task.paid.length; i++) task.paid[i] = task.costs.get(i).count();
        return true;
    }

    private void refund(MatterFabricationBlockEntity machine) {
        if (refunds.isEmpty()) return;
        var grid = machine.getMainNode().getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory(); var source = new MachineSource(machine);
        var iterator = refunds.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            long returned;
            try { returned = storage.insert(entry.getKey(), entry.getValue(), Actionable.MODULATE, source); }
            catch (RuntimeException error) { continue; } // Keep ownership until a compatible provider accepts it.
            if (returned > 0) {
                preparationStock = null;
                cachedLive = null;
                long left = entry.getValue() - returned;
                if (left == 0) iterator.remove(); else entry.setValue(left);
                machine.saveChanges();
            }
        }
    }

    public void tick(MatterFabricationBlockEntity machine) {
        if (machine.getLevel().getGameTime() % 20 == 0) refund(machine);
        if (!autoStart.isEmpty() && machine.getLevel().getGameTime()%20==0 && ready(machine)) {
            for (var id : List.copyOf(autoStart)) {
                if (machine.getLevel().getGameTime() % 20 == 0) {
                    if(!preparations.containsKey(id)) {
                        if (autoStart.size() == 1) machine.getResearchOrders().adoptLegacyOwner(id);
                        else machine.getResearchOrders().cancel(null);
                        orderMissing(machine,id,false);
                    }
                    else replenish(machine, id);
                }
            }
        }
        if (tasks.isEmpty()) return;
        var available = com.atir.molecularmanipulator.crafting.MatterRecipeIndex.get(machine.getLevel());
        var iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); var task = entry.getValue();
            var holder = available.research(entry.getKey());
            if (holder == null) { task.status = "unavailable"; continue; }
            if (!task.supplied()) { task.paused = true; task.status = "legacy_materials"; continue; }
            if (task.paused) { task.status = "paused"; continue; }
            if (!prerequisitesMet(machine, holder.value())) { task.status = "prerequisite"; continue; }
            if (!machine.isStructureFormed() || machine.isBuilding() || machine.isDismantling()) { task.status = "structure"; continue; }
            if (!machine.getMainNode().isActive()) { task.status = "network"; continue; }
            if (!machine.consumeResearchPower(task.definition.aePerTick())) { task.status = "power"; continue; }
            task.status = "running"; task.progress++; machine.saveChanges();
            if (task.progress >= task.definition.duration()) {
                completions.merge(entry.getKey(), task.round, Math::max); iterator.remove();
                permissionRevision++;
                cachedLive = null;
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
            value.putInt("round", task.round);value.putInt("first_round",task.firstRound);value.putBoolean("ordered",task.ordered); active.add(value);
        });
        unavailableTasks.values().forEach(value -> active.add(value.copy())); tag.put("tasks", active);
        var pending = new ListTag(); refunds.forEach((key, amount) -> pending.add(GenericStack.writeTag( new GenericStack(key, amount))));
        unavailableRefunds.forEach(value -> pending.add(value.copy()));
        tag.put("refunds", pending);
        var queued = new ListTag();
        autoStart.forEach(id -> queued.add(StringTag.valueOf(id.toString())));
        tag.put("auto_start", queued);
        var preparing=new ListTag();
        preparations.forEach((id,prep)-> {
            var value=new CompoundTag();value.putString("id",id.toString());value.putString("terms",prep.terms);
            value.putInt("first_round",prep.firstRound);value.putInt("round",prep.round);
            var reserved=new ListTag();prep.reserved.forEach((key,amount)->reserved.add(GenericStack.writeTag(new GenericStack(key,amount))));
            value.put("reserved",reserved);preparing.add(value);
        });
        unavailablePreparations.values().forEach(value->preparing.add(value.copy()));tag.put("preparations",preparing);
        return tag;
    }

    public void load(CompoundTag tag) {
        completions.clear(); tasks.clear(); unavailableTasks.clear(); unavailablePreparations.clear(); preparations.clear(); refunds.clear(); unavailableRefunds.clear(); autoStart.clear(); lastError = "";
        preparationStock=null;preparationStockTick=Long.MIN_VALUE;
        permissionRevision++;
        cachedLive = null;
        var counts = tag.getCompound("completions");
        for (var key : counts.getAllKeys()) {
            var id = ResourceLocation.tryParse(key); int count = counts.getInt(key);
            if (id != null && count > 0) completions.put(id, count);
        }
        for (var value : tag.getList("completed", 8)) {
            var id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) completions.putIfAbsent(id, 1);
        }
        for (var value : tag.getList("auto_start", 8)) {
            var id = ResourceLocation.tryParse(value.getAsString());
            if (id != null) autoStart.add(id);
        }
        for(var raw:tag.getList("preparations",10)) {
            var value=(CompoundTag)raw;var id=ResourceLocation.tryParse(value.getString("id"));if(id==null)continue;
            try {
                String terms=value.getString("terms");
                var definition=MatterResearchRecipe.CODEC.codec().parse(JsonOps.INSTANCE,JsonParser.parseString(terms)).getOrThrow(false, message -> {});
                var prep=new Preparation(definition,terms,value.getInt("first_round"),value.getInt("round"));
                for(var stored:value.getList("reserved",10)) {var stack=GenericStack.readTag((CompoundTag)stored);if(stack!=null && stack.what() instanceof AEItemKey key && stack.amount()>0)prep.reserved.merge(key,stack.amount(),Math::addExact);}
                preparations.put(id,prep);autoStart.add(id);
            } catch(RuntimeException error) {
                unavailablePreparations.put(id,value.copy());autoStart.remove(id);
                com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Keeping unavailable research preparation {}: {}",id,error.getMessage());
            }
        }
        var active = tag.getList("tasks", 10);
        for (int i = 0; i < active.size(); i++) {
            var value = active.getCompound(i); var id = ResourceLocation.tryParse(value.getString("id"));
            int round = value.contains("round") ? value.getInt("round") : 1;
            if (id == null || completionCount(id) >= round) continue;
            try {
                String terms = value.getString("terms");
                var definition = MatterResearchRecipe.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(terms)).getOrThrow(false, message -> {});
                var task = new Task(definition, terms,value.contains("first_round")?value.getInt("first_round"):round, round,value.getBoolean("ordered")); var paid = value.getLongArray("paid");
                if (paid.length != task.paid.length) throw new IllegalArgumentException("Research cost snapshot length changed");
                for (int j = 0; j < paid.length; j++) task.paid[j] = Math.max(0, Math.min(paid[j], task.costs.get(j).count()));
                task.progress = Math.max(0, Math.min(value.getInt("progress"), definition.duration() - 1));
                task.paused = value.getBoolean("paused") || !task.supplied(); tasks.put(id, task);
            } catch (RuntimeException error) {
                unavailableTasks.put(id, value.copy());
                com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Keeping unavailable research task {} for a later compatible load: {}", id, error.getMessage());
            }
        }
        var pending = tag.getList("refunds", 10);
        for (int i = 0; i < pending.size(); i++) {
            var raw = pending.getCompound(i);
            try {
                var stack = GenericStack.readTag( raw);
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
        root.add("orders", machine.getResearchOrders().clientState(machine.getLevel().registryAccess()));
        if (selected == null) return root.toString();
        var holder = com.atir.molecularmanipulator.crafting.MatterRecipeIndex.get(machine.getLevel()).research(selected);
        if (holder == null) return root.toString();
        var live = new JsonObject(); live.addProperty("id", selected.toString());
        live.addProperty("online", machine.getMainNode().isActive());
        int count = completionCount(selected); var task = tasks.get(selected);
        var preparation=preparations.get(selected);
        int round = task != null ? task.round : preparation!=null ? preparation.round : count >= holder.value().depths().size() ? holder.value().depths().size() : count + 1;
        long now = machine.getLevel().getGameTime();
        var index = com.atir.molecularmanipulator.crafting.MatterRecipeIndex.get(machine.getLevel());
        var grid = machine.getMainNode().getGrid();
        // Progress still syncs every five ticks; the stock-only presentation is sampled once a second.
        // Explicit admission reads providers afresh; automatic admission reuses only this tick's
        // preparation snapshot and validates exact withdrawals against the providers.
        if (cachedLive != null && selected.equals(cachedLiveId) && cachedLiveTask == task
                && cachedLiveRound == round && cachedLiveIndex == index && cachedLiveGrid == grid
                && now >= cachedLiveTick && now - cachedLiveTick < 20) {
            live = cachedLive.deepCopy();
            live.addProperty("online", machine.getMainNode().isActive());
            if (hasRefunds()) live.addProperty("affordable", false);
            root.add("live", live); return root.toString();
        }
        try {
            var costs = task != null ? task.costs : preparation!=null ? preparation.costs : holder.value().costsFor(round);
            long[] paid = task == null ? new long[costs.size()] : task.paid;
            var stock = preparationStock!=null && now-preparationStockTick<20 && preparationStockGrid==grid
                    ? new LinkedHashMap<>(preparationStock) : liveInventory(machine);
            var matching = new ResearchIngredientIndex(costs).matches(stock);
            var amounts = new JsonArray();
            for (int row = 0; row < costs.size(); row++) {
                long amount = 0;
                for (var entry : matching.entrySet()) if (entry.getValue().get(row)) {
                    amount = saturatedAdd(amount, stock.get(entry.getKey()));
                }
                amounts.add(amount);
            }
            live.add("available", amounts);
            live.add("batch_available",amounts.deepCopy());
            if(preparation!=null) {
                preparation.reserved.forEach((key,amount)->stock.merge(key,amount,MatterResearchProgress::saturatedAdd));
                var reserved=new JsonArray();
                for(var cost:costs) {long amount=0;for(var entry:preparation.reserved.entrySet())if(cost.ingredient().test(entry.getKey().toStack()))amount=saturatedAdd(amount,entry.getValue());reserved.add(amount);}
                live.add("reserved",reserved);
            } else if(task==null && count<holder.value().depths().size()) {
                var batchCosts = ResearchBatch.costs(holder.value(), count + 1, holder.value().depths().size());
                var batchMatches = new ResearchIngredientIndex(batchCosts).matches(stock);
                var batchAvailable = new JsonArray();
                for (int row = 0; row < batchCosts.size(); row++) {
                    long amount = 0;
                    for (var entry : batchMatches.entrySet()) if (entry.getValue().get(row)) amount = saturatedAdd(amount, stock.get(entry.getKey()));
                    batchAvailable.add(amount);
                }
                live.add("batch_available", batchAvailable);
            }
            live.addProperty("affordable", !hasRefunds() && plan(costs, paid, stock) != null);
        } catch (ArithmeticException error) { live.addProperty("affordable", false); live.addProperty("error", "cost_overflow"); }
        cachedLive = live.deepCopy(); cachedLiveId = selected; cachedLiveTask = task;
        cachedLiveRound = round; cachedLiveIndex = index; cachedLiveGrid = grid; cachedLiveTick = now;
        root.add("live", live); return root.toString();
    }

    private JsonObject state() {
        var root = new JsonObject(); var done = new JsonArray(); var counts = new JsonObject();
        completions.forEach((id, count) -> { done.add(id.toString()); counts.addProperty(id.toString(), count); });
        root.add("completed", done); root.add("counts", counts); root.addProperty("notice", hasRefunds() ? "refund_pending" : lastError);
        var active = new JsonObject();
        tasks.forEach((id, task) -> {
            var value = new JsonObject(); value.addProperty("progress", task.progress); value.addProperty("round", task.round);
            value.addProperty("first_round",task.firstRound);value.addProperty("ordered",task.ordered);
            value.addProperty("paused", task.paused); value.addProperty("status", !task.supplied() ? "legacy_materials" : task.paused ? "paused" : task.status);
            var paid = new JsonArray(); for (long amount : task.paid) paid.add(amount); value.add("paid", paid);
            value.add("terms", JsonParser.parseString(task.terms)); active.add(id.toString(), value);
        });
        root.add("tasks", active);
        var preparing=new JsonObject();preparations.forEach((id,prep)-> {var value=new JsonObject();
            value.addProperty("first_round",prep.firstRound);value.addProperty("round",prep.round);
            value.add("terms",JsonParser.parseString(prep.terms));preparing.add(id.toString(),value);});
        root.add("preparations",preparing);return root;
    }

    private static String encode(MatterResearchRecipe recipe) {
        var ops = JsonOps.INSTANCE;
        var json = MatterResearchRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow(false, message -> {}).getAsJsonObject();
        json.add("depths", ResearchDepth.CODEC.listOf().encodeStart(ops, recipe.depths()).getOrThrow(false, message -> {}));
        return json.toString();
    }
    private static long saturatedAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) return Long.MAX_VALUE;
        return first + second;
    }
    private static final class Preparation {
        final MatterResearchRecipe definition; final String terms; final int firstRound,round;
        final List<MatterResearchRecipe.Cost> costs;
        final ResearchIngredientIndex matcher;
        final Map<AEItemKey,Long> reserved=new LinkedHashMap<>();
        Preparation(MatterResearchRecipe definition,String terms,int firstRound,int round) {
            this.definition=definition;this.terms=terms;this.firstRound=firstRound;this.round=round;
            this.costs=ResearchBatch.costs(definition,firstRound,round);
            this.matcher = new ResearchIngredientIndex(costs);
        }
    }
    private static final class Task {
        final MatterResearchRecipe definition; final String terms; final int firstRound,round; final boolean ordered;
        final List<MatterResearchRecipe.Cost> costs; final long[] paid;
        int progress; boolean paused; String status = "running";
        Task(MatterResearchRecipe definition,String terms,int round) {this(definition,terms,round,round,false);}
        Task(MatterResearchRecipe definition,String terms,int firstRound,int round,boolean ordered) {
            this.definition=definition;this.terms=terms;this.firstRound=firstRound;this.round=round;this.ordered=ordered;
            this.costs=ordered ? ResearchBatch.costs(definition,firstRound,round) : definition.costsFor(round);
            this.paid=new long[costs.size()];
        }
        boolean supplied() {for(int i=0;i<paid.length;i++)if(paid[i]<costs.get(i).count())return false;return true;}
    }
}
