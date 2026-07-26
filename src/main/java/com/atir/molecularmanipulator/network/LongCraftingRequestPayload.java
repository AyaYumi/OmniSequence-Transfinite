package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.integration.ae2.LongCraftingAmountMenuBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public record LongCraftingRequestPayload(long amount, boolean craftMissingAmount, boolean autoStart) {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MolecularManipulator.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(
                0,
                LongCraftingRequestPayload.class,
                LongCraftingRequestPayload::encode,
                LongCraftingRequestPayload::decode,
                LongCraftingRequestPayload::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(LongCraftingRequestPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    private static void encode(LongCraftingRequestPayload payload, FriendlyByteBuf buffer) {
        buffer.writeLong(payload.amount);
        buffer.writeBoolean(payload.craftMissingAmount);
        buffer.writeBoolean(payload.autoStart);
    }

    private static LongCraftingRequestPayload decode(FriendlyByteBuf buffer) {
        return new LongCraftingRequestPayload(
                buffer.readLong(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    private static void handle(LongCraftingRequestPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null
                    && player.containerMenu instanceof LongCraftingAmountMenuBridge bridge) {
                bridge.molecularmanipulator$confirmLong(
                        payload.amount,
                        payload.craftMissingAmount,
                        payload.autoStart);
            }
        });
        context.setPacketHandled(true);
    }
}