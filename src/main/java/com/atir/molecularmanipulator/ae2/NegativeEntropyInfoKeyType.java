package com.atir.molecularmanipulator.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2 registry type for the single negentropy information-flow key.
 */
public final class NegativeEntropyInfoKeyType extends AEKeyType {
    public static final ResourceLocation TYPE_ID =
            MolecularManipulator.id("information_flow");
    public static final NegativeEntropyInfoKeyType INSTANCE = new NegativeEntropyInfoKeyType();

    private NegativeEntropyInfoKeyType() {
        super(
                TYPE_ID,
                NegativeEntropyInfoKey.class,
                Component.translatable("key_type.molecularmanipulator.information_flow")
        );
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return NegativeEntropyInfoKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return NegativeEntropyInfoKey.INSTANCE;
    }

    @Override
    public int getAmountPerByte() {
        return 8;
    }

    @Override
    public int getAmountPerOperation() {
        return 1;
    }

    @Override
    public String getUnitSymbol() {
        return "bit";
    }
}
