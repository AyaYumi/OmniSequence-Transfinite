package com.atir.molecularmanipulator.network;

import com.appliedenhancements.network.PacketCodec;
import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Only the traveller receives a screen transition; nearby players see the pad's BE animation. */
public record WarpScreenPayload(int phase, long startedAt) {
    public static final PacketCodec<FriendlyByteBuf, WarpScreenPayload> STREAM_CODEC = PacketCodec.of(
            (buffer, payload) -> { buffer.writeByte(payload.phase); buffer.writeVarLong(payload.startedAt); },
            buffer -> new WarpScreenPayload(buffer.readUnsignedByte(), buffer.readVarLong()));
    private static Consumer<WarpScreenPayload> clientHandler = payload -> {};
    private static final String PROTOCOL = "1-forge-1";
    private static final class Channel {
        static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                MolecularManipulator.id("warp_screen"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }

    public static void register() {
        Channel.INSTANCE.messageBuilder(WarpScreenPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> STREAM_CODEC.encode(buffer, payload)).decoder(STREAM_CODEC::decode)
                .consumerMainThread((payload, context) -> clientHandler.accept(payload)).add();
    }

    public static void setClientHandler(Consumer<WarpScreenPayload> handler) {
        clientHandler = handler;
    }

    public static void sendToPlayer(ServerPlayer player, WarpScreenPayload payload) {
        Channel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
