package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MolecularCenterCrystalBlockEntity extends BlockEntity {
    private ListTag patterns = new ListTag();
    private long patternRevision;

    public MolecularCenterCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MOLECULAR_CENTER_CRYSTAL_BE.get(), pos, state);
    }

    public ListTag patterns() {
        return patterns;
    }

    public void setPatterns(ListTag patterns) {
        this.patterns = patterns == null ? new ListTag() : patterns.copy();
        patternRevision++;
        setChanged();
    }

    public long patternRevision() {
        return patternRevision;
    }

    /** Update only the changed slot, before a same-tick save or crystal drop. */
    public void setPattern(int slot, ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag saved = null;
        if (!stack.isEmpty()) {
            saved = (CompoundTag) stack.save(registries);
            saved.putInt("Slot", slot);
        }
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.getCompound(i).getInt("Slot") != slot) continue;
            if (saved == null) patterns.remove(i);
            else {
                if (saved.equals(patterns.getCompound(i))) return;
                patterns.set(i, saved);
            }
            patternRevision++;
            setChanged();
            return;
        }
        if (saved != null) {
            patterns.add(saved);
            patternRevision++;
            setChanged();
        }
    }

    public ItemStack createDrop() {
        var stack = new ItemStack(ModContent.MOLECULAR_CENTER_COIL_ITEM.get());
        if (patterns.isEmpty()) return stack;
        var tag = new CompoundTag();
        saveAdditional(tag, level != null ? level.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY);
        BlockItem.setBlockEntityData(stack, getType(), tag);
        return stack;
    }

    public static ListTag patternsFromItem(ItemStack stack) {
        var data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data == null) return new ListTag();
        var tag = data.copyTag();
        return tag.contains(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_LIST)
                ? tag.getList(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_COMPOUND).copy()
                : new ListTag();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!patterns.isEmpty()) tag.put(MolecularCenterPatternShards.PATTERNS_TAG, patterns.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        patterns = tag.contains(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_LIST)
                ? tag.getList(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_COMPOUND).copy()
                : new ListTag();
        patternRevision++;
    }
}
