package com.atir.molecularmanipulator.network;

import com.appliedenhancements.network.PacketCodec;
import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** The server chooses the dimension's rate; clients use the same rate for prediction and animation. */
public record DetachedSignalTimePayload(boolean accelerated) {
    public static final PacketCodec<FriendlyByteBuf, DetachedSignalTimePayload> CODEC = PacketCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.accelerated()), buf -> new DetachedSignalTimePayload(buf.readBoolean()));
    private static Consumer<DetachedSignalTimePayload> clientHandler = payload -> {};
    private static final String PROTOCOL = "1-forge-1";
    private static final class Channel {
        static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                MolecularManipulator.id("detached_signal_time"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }

    public static void setClientHandler(Consumer<DetachedSignalTimePayload> handler) {
        clientHandler = handler;
    }

    public static void register() {
        Channel.INSTANCE.messageBuilder(DetachedSignalTimePayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> CODEC.encode(buffer, payload)).decoder(CODEC::decode)
                .consumerMainThread((payload, context) -> clientHandler.accept(payload)).add();
    }

    public static void sendToPlayer(ServerPlayer player, DetachedSignalTimePayload payload) {
        Channel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToPlayersInDimension(ServerLevel level, DetachedSignalTimePayload payload) {
        Channel.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), payload);
    }
}
