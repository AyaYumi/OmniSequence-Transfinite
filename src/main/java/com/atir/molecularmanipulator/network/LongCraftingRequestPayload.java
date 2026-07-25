package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.integration.ae2.LongCraftingAmountMenuBridge;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LongCraftingRequestPayload(long amount, boolean craftMissingAmount, boolean autoStart)
        implements CustomPacketPayload {
    public static final Type<LongCraftingRequestPayload> TYPE =
            new Type<>(MolecularManipulator.id("long_crafting_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LongCraftingRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeLong(payload.amount);
                        buffer.writeBoolean(payload.craftMissingAmount);
                        buffer.writeBoolean(payload.autoStart);
                    },
                    buffer -> new LongCraftingRequestPayload(
                            buffer.readLong(),
                            buffer.readBoolean(),
                            buffer.readBoolean()));

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, LongCraftingRequestPayload::handle);
    }

    private static void handle(LongCraftingRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player
                && player.containerMenu instanceof LongCraftingAmountMenuBridge bridge) {
            bridge.molecularmanipulator$confirmLong(
                    payload.amount,
                    payload.craftMissingAmount,
                    payload.autoStart);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
