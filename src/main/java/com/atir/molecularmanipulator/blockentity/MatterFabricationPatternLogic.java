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
import com.atir.molecularmanipulator.integration.ae2.OmniSmartDoublingProvider;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MatterFabricationPatternLogic extends PatternProviderLogic
        implements OmniBatchCraftingProvider, OmniSmartDoublingProvider {
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
        return AEItems.PROCESSING_PATTERN.is(stack) && PatternDetailsHelper.isEncodedPattern(stack);
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
        var controller = assembly.getController();
        var normalized = normalizeSmartPattern(probe.pattern());
        var registered = registeredPattern(normalized.pattern());
        if (isBlocking() || !assembly.isOperational() || controller == null || registered == null) return null;
        var oneCraft = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var input : probe.oneCraftInputs()) {
                if (input.amount() % normalized.crafts() != 0) return null;
                oneCraft.merge(input.key(), input.amount() / normalized.crafts(), Math::addExact);
            }
        }
        catch (ArithmeticException error) { return null; }
        final long limit;
        try {
            long baseLimit = assembly.getBuffer().capacity(registered, oneCraft);
            limit = Math.min(probe.requestedMaxCrafts(), baseLimit / normalized.crafts());
        }
        catch (ArithmeticException error) { return null; }
        if (limit < 2) return null;
        return new OmniBatchAdmission() {
            @Override public long maxCrafts() { return limit; }
            @Override public void commit(OmniBatchDelivery delivery) {
                var request = delivery.request();
                var requestPattern = normalizeSmartPattern(request.pattern());
                var requestRegistered = registeredPattern(requestPattern.pattern());
                var inputs = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
                var outputs = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
                boolean accepted = false;
                try {
                    boolean validInputs = true;
                    for (var input : request.inputs()) {
                        if (input.amount() % requestPattern.crafts() != 0) { validInputs = false; break; }
                        inputs.merge(input.key(), input.amount() / requestPattern.crafts(), Math::addExact);
                    }
                    for (var output : request.expectedOutputs()) outputs.merge(output.what(), output.amount(), Math::addExact);
                    long baseCrafts = Math.multiplyExact(request.craftCount(), requestPattern.crafts());
                    accepted = validInputs && assembly.isOperational() && assembly.getController() == controller && request.craftCount() <= limit
                            && requestRegistered != null && requestRegistered.equals(registered)
                            && outputs.equals(MatterPatternBuffer.scaled(MatterFabricationBatch.patternOutputs(requestRegistered), baseCrafts))
                            && assembly.getBuffer().enqueue(requestRegistered, inputs, baseCrafts);
                } catch (ArithmeticException ignored) { }
                if (accepted) delivery.accept(new OmniBatchDelivery.Receipt(OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE, OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
                else delivery.reject(OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.CAPACITY_CHANGED));
            }
        };
    }
}
