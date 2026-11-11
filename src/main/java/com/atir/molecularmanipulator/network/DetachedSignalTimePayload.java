package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The server chooses the dimension's rate; clients use the same rate for prediction and animation. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public record DetachedSignalTimePayload(boolean accelerated) implements CustomPacketPayload {
    public static final Type<DetachedSignalTimePayload> TYPE = new Type<>(MolecularManipulator.id("detached_signal_time"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DetachedSignalTimePayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBoolean(payload.accelerated()), buf -> new DetachedSignalTimePayload(buf.readBoolean()));
    private static Consumer<DetachedSignalTimePayload> clientHandler = payload -> {};

    @Override public Type<DetachedSignalTimePayload> type() { return TYPE; }

    public static void setClientHandler(Consumer<DetachedSignalTimePayload> handler) {
        clientHandler = handler;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC,
                (payload, context) -> context.enqueueWork(() -> clientHandler.accept(payload)));
    }
}
