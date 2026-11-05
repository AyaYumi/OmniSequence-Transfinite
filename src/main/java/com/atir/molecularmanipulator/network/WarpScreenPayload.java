package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only the traveller receives a screen transition; nearby players see the pad's BE animation. */
public record WarpScreenPayload(int phase, long startedAt) implements CustomPacketPayload {
    public static final Type<WarpScreenPayload> TYPE = new Type<>(MolecularManipulator.id("warp_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WarpScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> { buffer.writeByte(payload.phase); buffer.writeVarLong(payload.startedAt); },
            buffer -> new WarpScreenPayload(buffer.readUnsignedByte(), buffer.readVarLong()));
    private static Consumer<WarpScreenPayload> clientHandler = payload -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> clientHandler.accept(payload)));
    }

    public static void setClientHandler(Consumer<WarpScreenPayload> handler) {
        clientHandler = handler;
    }

    @Override
    public Type<WarpScreenPayload> type() { return TYPE; }
}
