package com.atir.molecularmanipulator.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The single, NBT-free key used by the negentropy information cell.
 */
public final class NegativeEntropyInfoKey extends AEKey {
    public static final ResourceLocation ID =
            MolecularManipulator.id("negentropy_information");
    public static final NegativeEntropyInfoKey INSTANCE = new NegativeEntropyInfoKey();
    public static final MapCodec<NegativeEntropyInfoKey> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final Codec<NegativeEntropyInfoKey> CODEC = MAP_CODEC.codec();

    private NegativeEntropyInfoKey() {
    }

    @Override
    public AEKeyType getType() {
        return NegativeEntropyInfoKeyType.INSTANCE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        return new CompoundTag();
    }

    @Override
    public Object getPrimaryKey() {
        return ID;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        // The key type itself identifies this singleton key.
    }

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("key.molecularmanipulator.information_flow");
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        if (amount > 0) {
            drops.add(GenericStack.wrapInItemStack(this, amount));
        }
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NegativeEntropyInfoKey;
    }

    @Override
    public int hashCode() {
        return ID.hashCode();
    }

    @Override
    public String toString() {
        return "NegativeEntropyInfoKey{}";
    }
}
