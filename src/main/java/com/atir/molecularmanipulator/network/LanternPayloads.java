package com.atir.molecularmanipulator.network;

import com.appliedenhancements.network.PacketCodec;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.world.LanternSimulation;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Actions contain no claimed positions or simulation flag; the server resolves both. */
public final class LanternPayloads {
    public static final int CLOSE = 0, OPEN = 1, CANCEL = 2, PLACE = 3, FIRE = 4;
    private static Consumer<State> clientHandler = state -> {};
    private static final String PROTOCOL = "1-forge-1";
    private static final class Channel {
        static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                MolecularManipulator.id("lantern"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }
    private LanternPayloads() {}
    public record Action(int action) {
        public static final PacketCodec<FriendlyByteBuf, Action> CODEC = PacketCodec.of(
                (buf, payload) -> buf.writeByte(payload.action), buf -> new Action(buf.readUnsignedByte()));
    }
    public record State(boolean active) {
        public static final PacketCodec<FriendlyByteBuf, State> CODEC = PacketCodec.of(
                (buf, payload) -> buf.writeBoolean(payload.active), buf -> new State(buf.readBoolean()));
    }
    public static void setClientHandler(Consumer<State> handler) { clientHandler = handler; }
    public static void register() {
        Channel.INSTANCE.messageBuilder(Action.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> Action.CODEC.encode(buffer, payload)).decoder(Action.CODEC::decode)
                .consumerMainThread((payload, context) -> {
                    var player = context.get().getSender();
                    if (player != null) LanternSimulation.action(player, payload.action());
                }).add();
        Channel.INSTANCE.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> State.CODEC.encode(buffer, payload)).decoder(State.CODEC::decode)
                .consumerMainThread((payload, context) -> clientHandler.accept(payload)).add();
    }
    public static void sendToServer(Action payload) {
        Channel.INSTANCE.sendToServer(payload);
    }
    public static void sendToPlayer(ServerPlayer player, State payload) {
        Channel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
