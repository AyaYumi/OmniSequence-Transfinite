package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MolecularCenterCrystalBlockEntity extends BlockEntity {
    private ListTag patterns = new ListTag();

    public MolecularCenterCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MOLECULAR_CENTER_CRYSTAL_BE.get(), pos, state);
    }

    public ListTag patterns() {
        return patterns;
    }

    public void setPatterns(ListTag patterns) {
        this.patterns = patterns == null ? new ListTag() : patterns.copy();
        setChanged();
    }

    public ItemStack createDrop() {
        var stack = new ItemStack(ModContent.MOLECULAR_CENTER_COIL_ITEM.get());
        if (patterns.isEmpty()) return stack;
        var tag = new CompoundTag();
        saveAdditional(tag);
        BlockItem.setBlockEntityData(stack, getType(), tag);
        return stack;
    }

    public static ListTag patternsFromItem(ItemStack stack) {
        var tag = BlockItem.getBlockEntityData(stack);
        if (tag == null) return new ListTag();
        return tag.contains(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_LIST)
                ? tag.getList(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_COMPOUND).copy()
                : new ListTag();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!patterns.isEmpty()) tag.put(MolecularCenterPatternShards.PATTERNS_TAG, patterns.copy());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        patterns = tag.contains(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_LIST)
                ? tag.getList(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_COMPOUND).copy()
                : new ListTag();
    }
}
