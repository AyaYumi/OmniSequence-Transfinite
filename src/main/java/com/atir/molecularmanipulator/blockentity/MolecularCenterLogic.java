package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import net.minecraft.world.item.ItemStack;
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
        this.fullPatternInventory.setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inventory, int slot,
                    ItemStack stack) {
                return isSupportedPattern(stack);
            }
        });
    }

    public AppEngInternalInventory getFullPatternInventory() {
        return fullPatternInventory;
    }

    public static boolean isSupportedPattern(ItemStack stack) {
        return (AEItems.CRAFTING_PATTERN.isSameAs(stack)
                || AEItems.SMITHING_TABLE_PATTERN.isSameAs(stack)
                || AEItems.STONECUTTING_PATTERN.isSameAs(stack))
                && PatternDetailsHelper.isEncodedPattern(stack);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, appeng.api.stacks.KeyCounter[] inputHolder) {
        if (!machine.isOperational() || !availablePatternSet.contains(patternDetails)) {
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
        return machine.isOperational() && !machine.hasActiveReusableBatch()
                && availablePatternSet.contains(patternDetails)
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
                var stack = fullPatternInventory.getStackInSlot(slot);
                if (!isSupportedPattern(stack)) {
                    continue;
                }
                var details = PatternDetailsHelper.decodePattern(stack, level);
                if (details instanceof IMolecularAssemblerSupportedPattern) {
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
    public void onChangeInventory(InternalInventory inventory, int slot) {
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
