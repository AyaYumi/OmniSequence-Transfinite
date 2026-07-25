package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MolecularCenterLogic extends PatternProviderLogic implements MolecularBatchCraftingProvider {
    private final MolecularCenterBlockEntity machine;
    private final IManagedGridNode node;
    private final AppEngInternalInventory fullPatternInventory;
    private final List<IPatternDetails> availablePatterns = new ArrayList<>();
    private final Set<IPatternDetails> availablePatternSet = new HashSet<>();
    private boolean rebuildScheduled;

    MolecularCenterLogic(MolecularCenterBlockEntity machine) {
        super(machine.getMainNode(), machine, MolecularCenterBlockEntity.MAX_PATTERN_SLOTS);
        this.machine = machine;
        this.node = machine.getMainNode();
        this.fullPatternInventory = (AppEngInternalInventory) super.getPatternInv();
    }

    public AppEngInternalInventory getFullPatternInventory() {
        return fullPatternInventory;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, appeng.api.stacks.KeyCounter[] inputHolder) {
        if (!machine.isOperational() || !availablePatternSet.contains(patternDetails)) {
            return false;
        }
        return machine.acceptPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean molecularmanipulator$supportsBatching(IPatternDetails patternDetails) {
        return machine.isOperational() && availablePatternSet.contains(patternDetails)
                && patternDetails instanceof IMolecularAssemblerSupportedPattern;
    }

    @Override
    public long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails) {
        return MolecularCenterBlockEntity.VIRTUAL_PARALLEL_LIMIT;
    }

    @Override
    public void updatePatterns() {
        availablePatterns.clear();
        availablePatternSet.clear();
        if (!machine.isFormed()) {
            ICraftingProvider.requestUpdate(node);
            return;
        }
        Level level = machine.getLevel();
        if (level != null) {
            int activeSlots = Math.min(ModConfig.activePatternSlots(), fullPatternInventory.size());
            for (int slot = 0; slot < activeSlots; slot++) {
                var details = PatternDetailsHelper.decodePattern(fullPatternInventory.getStackInSlot(slot), level);
                if (details != null) {
                    availablePatterns.add(details);
                    availablePatternSet.add(details);
                }
            }
        }
        ICraftingProvider.requestUpdate(node);
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return Collections.unmodifiableList(availablePatterns);
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        saveChanges();
        if (isClientSide() || rebuildScheduled) {
            return;
        }
        rebuildScheduled = true;
        if (!machine.schedulePatternRebuild(() -> {
            rebuildScheduled = false;
            if (!machine.isRemoved()) {
                updatePatterns();
            }
        })) {
            rebuildScheduled = false;
        }
    }
}
