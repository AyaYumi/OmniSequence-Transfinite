package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.atir.molecularmanipulator.api.crafting.*;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MatterFabricationPatternLogic extends PatternProviderLogic implements OmniBatchCraftingProvider {
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
        if (isBlocking() && assembly.getBuffer().hasContents()) return false;
        return assembly.isOperational() && patternSet.contains(patternDetails)
                && assembly.acceptPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return !assembly.isOperational() || super.isBusy();
    }

    @Override
    public OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe) {
        var controller = assembly.getController();
        if (isBlocking() || !assembly.isOperational() || controller == null || !patternSet.contains(probe.pattern())) return null;
        var oneCraft = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        try { for (var input : probe.oneCraftInputs()) oneCraft.merge(input.key(), input.amount(), Math::addExact); }
        catch (ArithmeticException error) { return null; }
        final long limit;
        try { limit = Math.min(probe.requestedMaxCrafts(), assembly.getBuffer().capacity(probe.pattern(), oneCraft)); }
        catch (ArithmeticException error) { return null; }
        if (limit < 2) return null;
        return new OmniBatchAdmission() {
            @Override public long maxCrafts() { return limit; }
            @Override public void commit(OmniBatchDelivery delivery) {
                var request = delivery.request();
                var inputs = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
                var outputs = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
                boolean accepted = false;
                try {
                    for (var input : request.inputs()) inputs.merge(input.key(), input.amount(), Math::addExact);
                    for (var output : request.expectedOutputs()) outputs.merge(output.what(), output.amount(), Math::addExact);
                    accepted = assembly.isOperational() && assembly.getController() == controller && request.craftCount() <= limit
                            && patternSet.contains(request.pattern()) && request.pattern().equals(probe.pattern())
                            && outputs.equals(MatterPatternBuffer.scaled(MatterFabricationBatch.patternOutputs(request.pattern()), request.craftCount()))
                            && assembly.getBuffer().enqueue(request.pattern(), inputs, request.craftCount());
                } catch (ArithmeticException ignored) { }
                if (accepted) delivery.accept(new OmniBatchDelivery.Receipt(OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE, OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
                else delivery.reject(OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.CAPACITY_CHANGED));
            }
        };
    }
}
