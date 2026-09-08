package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.ResearchMaterialAllocator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Durable AE-owned batch. Work is proportional to distinct materials, never to the number of crafts. */
final class MatterFabricationBatch {
    private ResourceLocation recipeId;
    private long crafts;
    private int progress, duration;
    private double powerPerCraft;
    private final Map<AEKey, Long> inputs = new LinkedHashMap<>();
    private final Map<AEKey, Long> outputsPerCraft = new LinkedHashMap<>();
    private final Map<AEKey, Long> outputDebt = new LinkedHashMap<>();
    private CompoundTag unavailable;

    boolean hasWork() { return crafts > 0 || !outputDebt.isEmpty() || unavailable != null; }
    long crafts() { return crafts; }
    Map<AEKey, Long> storedInputs() { return Map.copyOf(inputs); }
    long storedInputAmount(AEKey key) { return inputs.getOrDefault(key, 0L); }
    Map<AEKey, Long> storedOutputs() { return Map.copyOf(outputDebt); }
    boolean unavailable() { return unavailable != null; }

    static Map<AEKey, Long> outputs(MatterFabricationRecipe recipe) {
        return MatterRecipeIndex.outputAmounts(recipe);
    }

    static Map<AEKey, Long> patternOutputs(IPatternDetails pattern) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var stack : pattern.getOutputs()) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) return Map.of();
            result.merge(stack.what(), stack.amount(), Math::addExact);
        }
        return result;
    }

    static RecipeHolder<MatterFabricationRecipe> match(MatterFabricationBlockEntity host, IPatternDetails pattern,
            Map<AEKey, Long> supplied, long count) {
        if (count <= 0) return null;
        return match(host, candidates(host, pattern), supplied, count);
    }

    static List<RecipeHolder<MatterFabricationRecipe>> candidates(MatterFabricationBlockEntity host, IPatternDetails pattern) {
        return MatterRecipeIndex.get(host.getLevel()).candidates(patternOutputs(pattern));
    }

    static RecipeHolder<MatterFabricationRecipe> match(MatterFabricationBlockEntity host,
            List<RecipeHolder<MatterFabricationRecipe>> candidates, Map<AEKey, Long> supplied, long count) {
        if (count <= 0) return null;
        for (var holder : candidates) {
            if (MatterResearchApi.canUseRecipe(host, holder)
                    && validInputs(holder.value(), supplied, count)) return holder;
        }
        return null;
    }

    private static boolean validInputs(MatterFabricationRecipe recipe, Map<AEKey, Long> supplied, long count) {
        try {
            for (var entry : supplied.entrySet()) {
                if (entry.getKey() == null || entry.getValue() <= 0) return false;
            }
            var allocated = ResearchMaterialAllocator.plan(inputAmounts(recipe, count), supplied,
                    (index, key) -> matchesInput(recipe, index, key));
            return allocated != null && allocated.equals(supplied);
        } catch (ArithmeticException error) { return false; }
    }

    static Map<AEKey, Long> takeInputs(MatterFabricationRecipe recipe, Map<AEKey, Long> supplied, long total, long crafts) {
        return ResearchMaterialAllocator.planPortion(inputAmounts(recipe, total), inputAmounts(recipe, crafts), supplied,
                (index, key) -> matchesInput(recipe, index, key));
    }

    private static List<Long> inputAmounts(MatterFabricationRecipe recipe, long crafts) {
        var amounts = new ArrayList<Long>();
        for (var ingredient : recipe.ingredients()) amounts.add(Math.multiplyExact((long) ingredient.count(), crafts));
        for (var input : recipe.aeInputs()) amounts.add(Math.multiplyExact(input.amount(), crafts));
        if (!recipe.fluidInput().isEmpty()) amounts.add(Math.multiplyExact((long) recipe.fluidInput().getAmount(), crafts));
        return amounts;
    }

    private static boolean matchesInput(MatterFabricationRecipe recipe, int index, AEKey key) {
        if (index < recipe.ingredients().size()) {
            return key instanceof AEItemKey item && recipe.ingredients().get(index).ingredient().test(item.toStack());
        }
        index -= recipe.ingredients().size();
        if (index < recipe.aeInputs().size()) return recipe.aeInputs().get(index).what().equals(key);
        return AEFluidKey.of(recipe.fluidInput()).equals(key);
    }

    static long inputCapacity(MatterFabricationRecipe recipe) {
        long limit = Long.MAX_VALUE;
        for (long amount : inputAmounts(recipe, 1)) limit = Math.min(limit, Long.MAX_VALUE / amount);
        return limit;
    }

    long capacity(MatterFabricationBlockEntity host, RecipeHolder<MatterFabricationRecipe> holder, Map<AEKey, Long> oneCraft) {
        if (unavailable != null || !outputDebt.isEmpty() || progress > 0) return 0;
        var recipe = holder.value(); var profile = MatterResearchApi.productionProfile(host, holder);
        var output = outputs(recipe);
        if (crafts > 0 && (!holder.id().equals(recipeId) || !output.equals(outputsPerCraft)
                || duration != profile.ticks() || Double.compare(powerPerCraft, recipe.aePerTick()) != 0)) return 0;
        long limit = profile.parallel();
        limit = Math.min(limit, inputCapacity(recipe));
        for (long amount : output.values()) limit = Math.min(limit, Long.MAX_VALUE / amount);
        limit = powerCapacity(host, recipe.aePerTick(), limit);
        long remaining = Math.max(0, limit - crafts);
        for (var entry : oneCraft.entrySet()) {
            if (entry.getValue() <= 0) return 0;
            remaining = Math.min(remaining, (Long.MAX_VALUE - inputs.getOrDefault(entry.getKey(), 0L)) / entry.getValue());
        }
        return remaining;
    }

    boolean accept(MatterFabricationBlockEntity host, IPatternDetails pattern, Map<AEKey, Long> supplied,
            long count, Map<AEKey, Long> expectedOutputs) {
        try {
            var holder = match(host, pattern, supplied, count);
            if (holder == null || count > capacity(host, holder, Map.of())) return false;
            var output = outputs(holder.value());
            var scaled = new LinkedHashMap<AEKey, Long>(); output.forEach((key, amount) -> scaled.put(key, Math.multiplyExact(amount, count)));
            if (expectedOutputs != null && !scaled.equals(expectedOutputs)) return false;
            var nextInputs = new LinkedHashMap<>(inputs);
            supplied.forEach((key, amount) -> nextInputs.merge(key, amount, Math::addExact));
            long total = Math.addExact(crafts, count);
            if (!Double.isFinite(holder.value().aePerTick() * total)) return false;
            var profile = MatterResearchApi.productionProfile(host, holder);
            recipeId = holder.id(); duration = profile.ticks(); powerPerCraft = holder.value().aePerTick();
            crafts = total; inputs.clear(); inputs.putAll(nextInputs); outputsPerCraft.clear(); outputsPerCraft.putAll(output);
            host.saveChanges(); return true;
        } catch (ArithmeticException error) { return false; }
    }

    record Update(MatterFabricationBlockEntity.ProcessingState state, int progress, int duration, double power, boolean completed) {}

    static long powerCapacity(MatterFabricationBlockEntity host, double power, long limit) {
        if (power <= 0 || limit <= 0) return limit;
        double wanted = power * limit;
        double available = host.simulateBatchPower(Double.isFinite(wanted) ? wanted : Double.MAX_VALUE);
        if (!(available > 0)) return 0;
        if (Double.isFinite(wanted) && available >= wanted) return limit;
        return java.math.BigDecimal.valueOf(available).divide(java.math.BigDecimal.valueOf(power), 0, java.math.RoundingMode.FLOOR)
                .min(java.math.BigDecimal.valueOf(limit)).longValue();
    }
    Update tick(MatterFabricationBlockEntity host) {
        return tick(host, (key, amount) -> {
            var storage = host.getMainNode().getGrid().getStorageService().getInventory();
            long inserted = storage.insert(key, amount, Actionable.MODULATE, new MachineSource(host));
            if (inserted < amount) inserted += host.offerBatchOutputToPort(key, amount - inserted);
            return inserted;
        });
    }

    Update tick(MatterFabricationBlockEntity host, com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler.Inserter output) {
        if (unavailable != null) return update(MatterFabricationBlockEntity.ProcessingState.NO_RECIPE, false);
        if (!host.getMainNode().isActive()) return update(MatterFabricationBlockEntity.ProcessingState.NETWORK_OFFLINE, false);
        if (!outputDebt.isEmpty()) {
            flush(host, output);
            return update(outputDebt.isEmpty() ? MatterFabricationBlockEntity.ProcessingState.IDLE : MatterFabricationBlockEntity.ProcessingState.OUTPUT_BLOCKED, false);
        }
        if (!host.isStructureFormed()) return update(MatterFabricationBlockEntity.ProcessingState.STRUCTURE_INCOMPLETE, false);
        double power = powerPerCraft * crafts;
        if (!Double.isFinite(power) || !host.consumeResearchPower(power)) return update(MatterFabricationBlockEntity.ProcessingState.WAITING_POWER, false);
        progress++; host.saveChanges();
        if (progress < duration) return update(MatterFabricationBlockEntity.ProcessingState.RUNNING, false);
        outputsPerCraft.forEach((key, amount) -> outputDebt.put(key, Math.multiplyExact(amount, crafts)));
        inputs.clear(); crafts = 0; progress = 0; host.saveChanges();
        flush(host, output);
        return update(outputDebt.isEmpty() ? MatterFabricationBlockEntity.ProcessingState.IDLE : MatterFabricationBlockEntity.ProcessingState.OUTPUT_BLOCKED, true);
    }

    private Update update(MatterFabricationBlockEntity.ProcessingState state, boolean completed) {
        return new Update(state, progress, duration, powerPerCraft * crafts, completed);
    }
    private void flush(MatterFabricationBlockEntity host, com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler.Inserter output) {
        var iterator = outputDebt.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next(); long inserted = Math.clamp(output.insert(entry.getKey(), entry.getValue()), 0, entry.getValue());
            if (inserted > 0) {
                long remaining = entry.getValue() - inserted;
                if (remaining == 0) iterator.remove(); else entry.setValue(remaining);
                host.saveChanges();
            }
        }
        if (outputDebt.isEmpty()) clear();
    }
    void clear() { recipeId = null; crafts = 0; progress = 0; duration = 0; powerPerCraft = 0; inputs.clear(); outputsPerCraft.clear(); outputDebt.clear(); unavailable = null; }

    CompoundTag save(HolderLookup.Provider registries) {
        if (unavailable != null) return unavailable.copy();
        var tag = new CompoundTag();
        if (recipeId != null) tag.putString("recipe", recipeId.toString());
        tag.putLong("crafts", crafts); tag.putInt("progress", progress); tag.putInt("duration", duration); tag.putDouble("power", powerPerCraft);
        tag.put("inputs", saveMap(inputs, registries)); tag.put("outputs", saveMap(outputsPerCraft, registries)); tag.put("debt", saveMap(outputDebt, registries)); return tag;
    }
    void load(CompoundTag tag, HolderLookup.Provider registries) {
        clear(); if (tag.isEmpty()) return;
        try {
            recipeId = ResourceLocation.tryParse(tag.getString("recipe")); crafts = tag.getLong("crafts");
            duration = tag.getInt("duration"); progress = tag.getInt("progress"); powerPerCraft = tag.getDouble("power");
            loadMap(inputs, tag.getList("inputs", 10), registries); loadMap(outputsPerCraft, tag.getList("outputs", 10), registries); loadMap(outputDebt, tag.getList("debt", 10), registries);
            if (crafts == 0 && !inputs.isEmpty() || crafts > 0 && !outputDebt.isEmpty()) throw new IllegalArgumentException("Conflicting batch ownership state");
            for (long amount : outputsPerCraft.values()) Math.multiplyExact(amount, crafts);
            if (crafts < 0 || !Double.isFinite(powerPerCraft) || powerPerCraft < 0
                    || crafts > 0 && (recipeId == null || inputs.isEmpty() || outputsPerCraft.isEmpty() || duration < 1 || progress < 0 || progress >= duration)) throw new IllegalArgumentException("Invalid batch state");
        } catch (RuntimeException error) {
            clear(); unavailable = tag.copy(); MolecularManipulator.LOGGER.warn("Preserving unavailable fabrication batch: {}", error.getMessage());
        }
    }
    private static ListTag saveMap(Map<AEKey, Long> map, HolderLookup.Provider registries) {
        var list = new ListTag(); map.forEach((key, amount) -> list.add(GenericStack.writeTag(registries, new GenericStack(key, amount)))); return list;
    }
    private static void loadMap(Map<AEKey, Long> map, ListTag list, HolderLookup.Provider registries) {
        for (int i = 0; i < list.size(); i++) {
            var stack = GenericStack.readTag(registries, list.getCompound(i));
            if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid stored material");
            map.merge(stack.what(), stack.amount(), Math::addExact);
        }
    }
}
