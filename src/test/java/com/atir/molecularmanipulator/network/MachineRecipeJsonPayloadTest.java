package com.atir.molecularmanipulator.network;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MachineRecipeJsonPayloadTest {
    @Test
    void roundTripPreservesMappingsAndSnapshotBoundaries() {
        var payload = new MachineRecipeJsonPayload(true, true, Map.of(
                new ResourceLocation("kubejs", "recipe"), "{\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:diamond\"}"));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MachineRecipeJsonPayload.encode(payload, buffer);
            assertEquals(payload, MachineRecipeJsonPayload.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test
    void rejectsUnboundedEntryCountsBeforeAllocating() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(true);
            buffer.writeBoolean(true);
            buffer.writeVarInt(33);
            assertThrows(DecoderException.class, () -> MachineRecipeJsonPayload.decode(buffer));
        } finally { buffer.release(); }
    }

    @Test
    void rejectsAggregateJsonExceedingChunkBudget() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeBoolean(true);
            buffer.writeBoolean(true);
            buffer.writeVarInt(2);
            for (int i = 0; i < 2; i++) {
                buffer.writeResourceLocation(new ResourceLocation("test", "recipe_" + i));
                buffer.writeUtf("x".repeat(130_000), 250_000);
            }
            assertThrows(DecoderException.class, () -> MachineRecipeJsonPayload.decode(buffer));
        } finally { buffer.release(); }
    }
}
