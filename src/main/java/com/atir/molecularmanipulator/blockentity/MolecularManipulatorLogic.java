package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class MolecularManipulatorLogic extends PatternProviderLogic implements MolecularBatchCraftingProvider {
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
        var context = MolecularBatchDispatchContext.current(patternDetails, inputHolder);
        if (context != null && context.requiresReusableProof() && (patternRebuildScheduled || isBlocking())) return false;
        return machine.acceptPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return machine.hasActiveReusableBatch()
                || machine.getRemainingAssemblerBudget() <= 0 || super.isBusy();
    }

    @Override
    public boolean molecularmanipulator$supportsBatching(IPatternDetails patternDetails) {
        return machine.getMainNode().isActive()
                && !machine.hasActiveReusableBatch()
                && machine.getRemainingAssemblerBudget() > 0
                && availablePatterns.contains(patternDetails)
                && patternDetails instanceof IMolecularAssemblerSupportedPattern;
    }

    @Override
    public long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails) {
        return machine.getRemainingAssemblerBudget();
    }

    @Override
    public long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails, KeyCounter unitOutputs) {
        return machine.getMainNode().isActive() && availablePatterns.contains(patternDetails)
                ? machine.getBatchLimit(patternDetails, unitOutputs) : 0;
    }

    @Override
    public boolean molecularmanipulator$supportsReusableBatching(
            IPatternDetails patternDetails) {
        return molecularmanipulator$supportsBatching(patternDetails);
    }

    @Override
    public ReusableValidation molecularmanipulator$validateReusableBatch(
            IPatternDetails pattern, MolecularReusableBatchPlan plan, UUID jobId) {
        if (patternRebuildScheduled) return ReusableValidation.PENDING;
        return !isBlocking() && molecularmanipulator$supportsReusableBatching(pattern)
                ? machine.validateReusableBatch(pattern, plan, jobId) : ReusableValidation.REJECTED;
    }

    @Override
    public void molecularmanipulator$flushOutputsAfterCpuAccounting() {
        machine.flushOutputsAfterCpuAccounting();
    }

    @Override
    public void updatePatterns() {
        super.updatePatterns();
        availablePatterns.clear();
        availablePatterns.addAll(getAvailablePatterns());
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
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
