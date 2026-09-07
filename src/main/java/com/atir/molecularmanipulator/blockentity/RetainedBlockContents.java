package com.atir.molecularmanipulator.blockentity;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.SettingsFrom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Packs owned contents without serializing grid identity, controller links or construction queues. */
final class RetainedBlockContents {
    private static final String CONTENTS_TAG = "omnisequence_stored_contents";
    private RetainedBlockContents() {}

    static ItemStack createDrop(AEBaseBlockEntity blockEntity, CompoundTag contents) {
        var stack = new ItemStack(blockEntity.getBlockState().getBlock());
        stack.applyComponents(blockEntity.exportSettings(SettingsFrom.DISMANTLE_ITEM, null));
        // A full 36,000-pattern inventory exceeds the item packet's 2 MiB NBT
        // quota. Compress only the portable contents; world saves stay unchanged.
        var portable = new CompoundTag();
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(contents, bytes);
            portable.putByteArray(CONTENTS_TAG, bytes.toByteArray());
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to pack stored block contents", error);
        }
        BlockItem.setBlockEntityData(stack, blockEntity.getType(), portable);
        return stack;
    }

    static CompoundTag unpack(CompoundTag tag) {
        if (!tag.contains(CONTENTS_TAG, Tag.TAG_BYTE_ARRAY)) return tag;
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(tag.getByteArray(CONTENTS_TAG)),
                    NbtAccounter.create(64L * 1024 * 1024));
        } catch (IOException error) {
            throw new UncheckedIOException("Unable to unpack stored block contents", error);
        }
    }

    static boolean hasPatternContents(PatternProviderBlockEntity blockEntity) {
        var logic = blockEntity.getLogic();
        if (!logic.getPatternInv().isEmpty() || !logic.getReturnInv().isEmpty()) return true;
        if (blockEntity.getLevel() == null) return false;
        // isBusy() also means "controller offline" for some hosts. Inspect the
        // actual send/refund queues instead, without materializing giant drops.
        var tag = new CompoundTag();
        logic.writeToNBT(tag, blockEntity.getLevel().registryAccess());
        return !tag.getList(PatternProviderLogic.NBT_SEND_LIST, Tag.TAG_COMPOUND).isEmpty()
                || !tag.getList("molecularmanipulatorLegacyBatchRefund", Tag.TAG_COMPOUND).isEmpty();
    }
}
