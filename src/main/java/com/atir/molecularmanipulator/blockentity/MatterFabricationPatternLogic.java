package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.atir.molecularmanipulator.api.crafting.*;
import com.atir.molecularmanipulator.crafting.MolecularExternalScaledPattern;
import com.atir.molecularmanipulator.crafting.MolecularScaledPattern;
import com.github.appliedenhancements.integration.ae2.AelisScaledPattern;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MatterFabricationPatternLogic extends PatternProviderLogic
        implements OmniBatchCraftingProvider {
    private final MatterFabricationPatternAssemblyBlockEntity assembly;
    private final AppEngInternalInventory patternInventory;
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private final Set<IPatternDetails> patternSet = new HashSet<>();
    private final Set<AEItemKey> definitions = new HashSet<>();

    MatterFabricationPatternLogic(MatterFabricationPatternAssemblyBlockEntity assembly) {
        super(assembly.getMainNode(), assembly,
                MatterFabricationPatternAssemblyBlockEntity.PATTERN_SLOTS);
        this.assembly = assembly;
        this.patternInventory = (AppEngInternalInventory) super.getPatternInv();
        this.patternInventory.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inventory, int slot,
                    ItemStack stack) {
                return isSupportedPattern(stack);
            }
        });
    }

    public static boolean isSupportedPattern(ItemStack stack) {
        return stack.is(AEItems.PROCESSING_PATTERN.asItem()) && PatternDetailsHelper.isEncodedPattern(stack);
    }

    @Override
    public void updatePatterns() {
        patterns.clear();
        patternSet.clear();
        definitions.clear();
        var level = assembly.getLevel();
        if (level != null) {
            for (var stack : patternInventory) {
                if (!isSupportedPattern(stack)) {
                    continue;
                }
                var details = PatternDetailsHelper.decodePattern(stack, level);
                if (details != null) {
                    patterns.add(details);
                    patternSet.add(details);
                    definitions.add(details.getDefinition());
                }
            }
        }
        ICraftingProvider.requestUpdate(assembly.getMainNode());
        assembly.patternsChanged();
    }

    Set<AEItemKey> patternDefinitions() { return definitions; }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return Collections.unmodifiableList(patterns);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        var normalized = normalizeSmartPattern(patternDetails);
        var basePattern = normalized.pattern();
        var registered = registeredPattern(basePattern);
        if (isBlocking() && assembly.getBuffer().hasContents()) return false;
        if (!assembly.isOperational() || registered == null) return false;
        return acceptSmartPattern(registered, inputHolder, normalized.crafts());
    }

    @Override
    public boolean isBusy() {
        return !assembly.isOperational() || super.isBusy();
    }

    private IPatternDetails registeredPattern(IPatternDetails candidate) {
        if (candidate == null) return null;
        if (patternSet.contains(candidate)) return candidate;
        // Smart-doubling wrappers retain the encoded definition but are different
        // runtime objects. Resolve them back to the exact registered details before
        // the durable buffer records the job.
        for (var pattern : patterns) {
            try {
                if (pattern.getDefinition().equals(candidate.getDefinition())) return pattern;
            } catch (RuntimeException ignored) { }
        }
        return null;
    }

    private static SmartPattern normalizeSmartPattern(IPatternDetails pattern) {
        if (pattern instanceof AelisScaledPattern scaled
                && scaled.appliedenhancements$operationsPerPush() > 1) {
            return new SmartPattern(scaled.appliedenhancements$originalPattern(),
                    scaled.appliedenhancements$operationsPerPush());
        }
        if (pattern instanceof MolecularScaledPattern scaled) {
            return new SmartPattern(scaled.base(), scaled.multiplier());
        }
        try {
            var external = MolecularExternalScaledPattern.unwrapSmartDoubling(pattern);
            return new SmartPattern(external.patternDetails(), external.multiplier());
        } catch (RuntimeException ignored) {
            // Unknown optional wrappers remain ordinary one-craft patterns.
            return new SmartPattern(pattern, 1);
        }
    }

    private record SmartPattern(IPatternDetails pattern, long crafts) {
        private SmartPattern {
            if (pattern == null || crafts <= 0) throw new IllegalArgumentException("Invalid smart pattern");
        }
    }

    /**
     * Infers the exact craft count from the complete material vector supplied by
     * an ordinary CPU. The buffer still validates the candidate against the
     * recipe, so substitutions and multi-input recipes cannot be partially
     * accepted.
     */
    private boolean acceptSmartPattern(IPatternDetails pattern, KeyCounter[] inputHolder,
            long wrapperCrafts) {
        var supplied = new java.util.LinkedHashMap<AEKey, Long>();
        try {
            for (var counter : inputHolder) for (var entry : counter) {
                if (entry.getLongValue() <= 0) return false;
                supplied.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
        } catch (ArithmeticException error) { return false; }
        var candidates = new java.util.TreeSet<Long>(java.util.Comparator.reverseOrder());
        if (wrapperCrafts > 0) candidates.add(wrapperCrafts);
        try {
            for (var entry : supplied.entrySet()) {
                for (var input : pattern.getInputs()) {
                    for (var possible : input.getPossibleInputs()) {
                        if (!entry.getKey().equals(possible.what()) || possible.amount() <= 0) continue;
                        long perCraft = Math.multiplyExact(possible.amount(), input.getMultiplier());
                        if (perCraft > 0 && entry.getValue() % perCraft == 0) {
                            long candidate = entry.getValue() / perCraft;
                            if (candidate > 0) candidates.add(candidate);
                        }
                    }
                }
            }
        } catch (ArithmeticException error) { return false; }
        if (candidates.isEmpty()) candidates.add(1L);
        for (long crafts : candidates) {
            if (assembly.getBuffer().enqueue(pattern, supplied, crafts)) {
                for (var counter : inputHolder) counter.clear();
                return true;
            }
        }
        return false;
    }

    @Override
    public OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe) {
        var oneCraft = new LinkedHashMap<AEKey, Long>();
        try {
            for (var input : probe.oneCraftInputs()) {
                oneCraft.merge(input.key(), input.amount(), Math::addExact);
            }
        }
        catch (ArithmeticException error) { return null; }
        var batch = prepareCountedBatch(probe.pattern(), oneCraft, probe.requestedMaxCrafts());
        if (batch == null || batch.maxCrafts() < 2) return null;
        return new OmniBatchAdmission() {
            @Override public long maxCrafts() { return batch.maxCrafts(); }
            @Override public void commit(OmniBatchDelivery delivery) {
                var request = delivery.request();
                var inputs = new LinkedHashMap<AEKey, Long>();
                boolean accepted = false;
                try {
                    for (var input : request.inputs()) {
                        inputs.merge(input.key(), input.amount(), Math::addExact);
                    }
                    accepted = batch.matches(request.pattern())
                            && batch.commit(inputs, batchInputs(request.expectedOutputs()), request.craftCount());
                } catch (ArithmeticException | IllegalArgumentException ignored) { }
                if (accepted) delivery.accept(new OmniBatchDelivery.Receipt(OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE, OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
                else delivery.reject(OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.CAPACITY_CHANGED));
            }
        };
    }

    /** Native CPUs own scaling. Inputs here describe one CPU operation, including any external wrapper. */
    public CountedBatch prepareCountedBatch(IPatternDetails pattern, Map<AEKey, Long> unitInputs,
            long requestedCrafts) {
        if (requestedCrafts <= 0 || !canDispatchBatch()) return null;
        try {
            var normalized = normalizeSmartPattern(pattern);
            var registered = registeredPattern(normalized.pattern());
            if (registered == null || unitInputs == null || unitInputs.isEmpty()) return null;
            var baseInputs = new LinkedHashMap<AEKey, Long>();
            for (var entry : unitInputs.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0
                        || entry.getValue() % normalized.crafts() != 0) return null;
                baseInputs.put(entry.getKey(), entry.getValue() / normalized.crafts());
            }
            var outputs = MatterPatternBuffer.scaled(MatterFabricationBatch.patternOutputs(registered),
                    normalized.crafts());
            if (outputs.isEmpty() || !outputs.equals(MatterFabricationBatch.patternOutputs(pattern))) return null;
            long capacity = assembly.getBuffer().capacity(registered, baseInputs) / normalized.crafts();
            long limit = Math.min(requestedCrafts, capacity);
            return limit > 0 ? new CountedBatch(registered, normalized.crafts(), Map.copyOf(unitInputs),
                    outputs, limit, assembly.getController()) : null;
        } catch (ArithmeticException | IllegalArgumentException error) { return null; }
    }

    public long countedBatchCapacity(IPatternDetails pattern) {
        try {
            var unit = new LinkedHashMap<AEKey, Long>();
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length == 0) return 0;
                unit.merge(possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier()), Math::addExact);
            }
            var batch = prepareCountedBatch(pattern, unit, Long.MAX_VALUE);
            return batch == null ? 0 : batch.maxCrafts();
        } catch (ArithmeticException | IllegalArgumentException error) { return 0; }
    }

    /** Report missing prerequisites even when the CPU still owns the rejected delivery. */
    public boolean hasMissingCatalysts() {
        var controller = assembly.getController();
        if (controller == null) return false;
        for (var pattern : patterns) {
            try {
                var unit = new LinkedHashMap<AEKey, Long>();
                for (var input : pattern.getInputs()) {
                    var possible = input.getPossibleInputs();
                    if (possible.length == 0) continue;
                    unit.merge(possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier()), Math::addExact);
                }
                var recipe = MatterFabricationBatch.match(controller, pattern, unit, 1);
                if (recipe != null && !controller.hasCatalysts(recipe.value())) return true;
            } catch (ArithmeticException | IllegalArgumentException ignored) { }
        }
        return false;
    }

    private boolean canDispatchBatch() {
        var level = assembly.getLevel();
        return level != null && !level.isClientSide() && level.getServer() != null
                && level.getServer().isSameThread() && !assembly.isRemoved()
                && assembly.isOperational() && (!isBlocking() || !assembly.getBuffer().hasContents());
    }

    /** Checked snapshots avoid KeyCounter's saturating arithmetic at the admission boundary. */
    public static Map<AEKey, Long> batchInputs(KeyCounter[] counters) {
        if (counters == null) throw new IllegalArgumentException("Missing batch inputs");
        var result = new LinkedHashMap<AEKey, Long>();
        for (var counter : counters) {
            if (counter == null) throw new IllegalArgumentException("Missing input slot");
            for (var entry : counter) {
                if (entry.getKey() == null || entry.getLongValue() <= 0) throw new IllegalArgumentException("Invalid batch input");
                result.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
        }
        return result;
    }

    public static Map<AEKey, Long> batchInputs(List<appeng.api.stacks.GenericStack> stacks) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid batch stack");
            result.merge(stack.what(), stack.amount(), Math::addExact);
        }
        return result;
    }

    public final class CountedBatch {
        private final IPatternDetails pattern;
        private final long multiplier, limit;
        private final Map<AEKey, Long> unitInputs, unitOutputs;
        private final MatterFabricationBlockEntity controller;
        private boolean used;

        private CountedBatch(IPatternDetails pattern, long multiplier, Map<AEKey, Long> unitInputs,
                Map<AEKey, Long> unitOutputs, long limit, MatterFabricationBlockEntity controller) {
            this.pattern = pattern;
            this.multiplier = multiplier;
            this.unitInputs = unitInputs;
            this.unitOutputs = Map.copyOf(unitOutputs);
            this.limit = limit;
            this.controller = controller;
        }

        public long maxCrafts() { return limit; }

        private boolean matches(IPatternDetails candidate) {
            var normalized = normalizeSmartPattern(candidate);
            return normalized.crafts() == multiplier && pattern.equals(registeredPattern(normalized.pattern()));
        }

        /** ECO supplies the full vector; ownership transfers only after durable enqueue succeeds. */
        public boolean commit(Map<AEKey, Long> totalInputs, Map<AEKey, Long> totalOutputs, long crafts) {
            if (used) return false;
            used = true;
            try {
                return crafts > 0 && crafts <= limit && canDispatchBatch()
                        && assembly.getController() == controller && patternSet.contains(pattern)
                        && MatterPatternBuffer.scaled(unitOutputs, crafts).equals(totalOutputs)
                        && assembly.getBuffer().enqueue(pattern, totalInputs, Math.multiplyExact(crafts, multiplier));
            } catch (ArithmeticException | IllegalArgumentException error) { return false; }
        }

        /** Trinity/Thunderbolt pass a reusable single-operation template. Never clear that template. */
        public boolean commitPrototype(KeyCounter[] prototype, long crafts) {
            try {
                var actual = batchInputs(prototype);
                if (!actual.equals(unitInputs)) return false;
                return commit(MatterPatternBuffer.scaled(actual, crafts),
                        MatterPatternBuffer.scaled(unitOutputs, crafts), crafts);
            } catch (ArithmeticException | IllegalArgumentException error) { return false; }
        }
    }
}
