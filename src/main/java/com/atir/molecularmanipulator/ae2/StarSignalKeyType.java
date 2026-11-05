package com.atir.molecularmanipulator.ae2;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2 registry type for the single star signal flow key.
 */
public final class StarSignalKeyType extends AEKeyType {
    public static final ResourceLocation TYPE_ID =
            MolecularManipulator.id("star_signal_flow");
    public static final StarSignalKeyType INSTANCE = new StarSignalKeyType();

    private StarSignalKeyType() {
        super(
                TYPE_ID,
                StarSignalKey.class,
                Component.translatable("key_type.molecularmanipulator.star_signal_flow")
        );
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return StarSignalKey.MAP_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
        return StarSignalKey.INSTANCE;
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
