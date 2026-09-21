package com.atir.molecularmanipulator.block;

import appeng.block.crafting.ICraftingUnitType;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.world.item.Item;

/**
 * AE2 type contract for the Omni compute blocks.
 *
 * <p>{@code CraftingBlockEntity.getUnitBlock()} casts the block at its position to
 * {@code AbstractCraftingUnitBlock} with an unchecked {@code checkcast}, so any
 * {@code CraftingBlockEntity} must sit on such a block. Our cores are self-contained
 * 1x1x1 clusters that supply storage and parallelism through their own overrides, so
 * this type only exists to satisfy that contract and deliberately reports nothing.
 */
public final class TransfiniteCraftingUnitType implements ICraftingUnitType {
    public static final TransfiniteCraftingUnitType INSTANCE = new TransfiniteCraftingUnitType();

    private TransfiniteCraftingUnitType() {
    }

    @Override
    public long getStorageBytes() {
        // Real capacity comes from OmniComputationCoreBlockEntity's own overrides.
        return 0L;
    }

    @Override
    public int getAcceleratorThreads() {
        // Real parallelism comes from OmniComputationCoreBlockEntity's own overrides.
        return 0;
    }

    @Override
    public Item getItemFromType() {
        // Only reached if CraftingBlockEntity#getItemFromBlockEntity were not
        // overridden; ours is, and returns the item matching the actual block.
        return ModContent.TRANSFINITE_COMPUTE_NEXUS_ITEM.get();
    }
}
