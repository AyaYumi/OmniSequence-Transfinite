package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.world.LanternSimulation;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Actions contain no claimed positions or simulation flag; the server resolves both. */
public final class LanternPayloads {
    public static final int CLOSE = 0, OPEN = 1, CANCEL = 2, PLACE = 3, FIRE = 4;
    private static Consumer<State> clientHandler = state -> {};
    private LanternPayloads() {}
    public record Action(int action) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(MolecularManipulator.id("lantern_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeByte(payload.action), buf -> new Action(buf.readUnsignedByte()));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record State(boolean active) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(MolecularManipulator.id("lantern_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBoolean(payload.active), buf -> new State(buf.readBoolean()));
        @Override public Type<State> type() { return TYPE; }
    }
    public static void setClientHandler(Consumer<State> handler) { clientHandler = handler; }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) LanternSimulation.action(player, payload.action());
        }));
        registrar.playToClient(State.TYPE, State.CODEC,
                (payload, context) -> context.enqueueWork(() -> clientHandler.accept(payload)));
    }
}
