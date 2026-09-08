package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;

import java.util.HashSet;
import java.util.Set;

final class MolecularManipulatorLogic extends PatternProviderLogic implements MolecularBatchCraftingProvider {
    private final MolecularManipulatorBlockEntity machine;
    private final Set<IPatternDetails> availablePatterns = new HashSet<>();
    private boolean patternRebuildScheduled;
    private int patternRevision;

    MolecularManipulatorLogic(IManagedGridNode mainNode, MolecularManipulatorBlockEntity machine) {
        super(mainNode, machine, MolecularManipulatorBlockEntity.PATTERN_SLOTS);
        this.machine = machine;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!machine.getMainNode().isActive() || !availablePatterns.contains(patternDetails)) {
            return false;
        }
        return machine.acceptPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return machine.hasActiveReusableBatch() || super.isBusy();
    }

    @Override
    public boolean molecularmanipulator$supportsBatching(IPatternDetails patternDetails) {
        return machine.getMainNode().isActive()
                && !machine.hasActiveReusableBatch()
                && availablePatterns.contains(patternDetails)
                && patternDetails instanceof IMolecularAssemblerSupportedPattern;
    }

    @Override
    public long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails) {
        return MolecularManipulatorBlockEntity.VIRTUAL_PARALLEL_LIMIT;
    }

    @Override
    public boolean molecularmanipulator$supportsReusableBatching(
            IPatternDetails patternDetails) {
        return molecularmanipulator$supportsBatching(patternDetails);
    }

    @Override
    public void updatePatterns() {
        super.updatePatterns();
        availablePatterns.clear();
        availablePatterns.addAll(getAvailablePatterns());
    }

    @Override
    public void onChangeInventory(appeng.api.inventories.InternalInventory inventory, int slot) {
        patternRevision++;
        saveChanges();
        if (isClientSide() || patternRebuildScheduled) {
            return;
        }

        patternRebuildScheduled = true;
        if (!machine.schedulePatternRebuild(() -> {
            patternRebuildScheduled = false;
            if (!machine.isRemoved() && machine.getLevel() != null) {
                updatePatterns();
            }
        })) {
            patternRebuildScheduled = false;
        }
    }

    int getPatternRevision() {
        return patternRevision;
    }
}
