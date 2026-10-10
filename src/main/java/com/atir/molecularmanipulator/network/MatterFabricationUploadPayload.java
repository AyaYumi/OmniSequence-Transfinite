package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.integration.MatterFabricationPatternUpload;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Only recipe identity and displayed alternatives cross the wire, never a destination or quantity. */
public record MatterFabricationUploadPayload(int containerId, ResourceLocation recipeId, List<ItemStack> selections) {
    public static final com.appliedenhancements.network.PacketCodec<FriendlyByteBuf, MatterFabricationUploadPayload> STREAM_CODEC =
            com.appliedenhancements.network.PacketCodec.of((buffer, payload) -> encode(payload, buffer), MatterFabricationUploadPayload::decode);
    private static final String PROTOCOL = "2.0.8-forge-1";
    private static final class Channel {
        static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                MolecularManipulator.id("fabrication_pattern_upload"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }

    public MatterFabricationUploadPayload {
        if (containerId < 0 || recipeId == null || selections == null || selections.size() > MatterFabricationRecipe.MAX_INPUTS)
            throw new IllegalArgumentException("Invalid fabrication upload request");
        selections = selections.stream().map(stack -> stack.copyWithCount(1)).toList();
    }

    public static void register() {
        Channel.INSTANCE.messageBuilder(MatterFabricationUploadPayload.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MatterFabricationUploadPayload::encode).decoder(MatterFabricationUploadPayload::decode)
                .consumerMainThread((payload, context) -> {
                    var player = context.get().getSender();
                    if (player != null) MatterFabricationPatternUpload.upload(player, payload.containerId, payload.recipeId, payload.selections);
                    context.get().setPacketHandled(true);
                }).add();
    }

    public static void sendToServer(MatterFabricationUploadPayload payload) { Channel.INSTANCE.sendToServer(payload); }

    static void encode(MatterFabricationUploadPayload payload, FriendlyByteBuf buffer) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeResourceLocation(payload.recipeId);
        buffer.writeVarInt(payload.selections.size());
        for (var stack : payload.selections) buffer.writeItem(stack);
    }

    static MatterFabricationUploadPayload decode(FriendlyByteBuf buffer) {
        int menu = buffer.readVarInt();
        var recipe = buffer.readResourceLocation();
        int count = buffer.readVarInt();
        if (menu < 0 || count < 0 || count > MatterFabricationRecipe.MAX_INPUTS)
            throw new DecoderException("Invalid fabrication upload request");
        var items = new ArrayList<ItemStack>(count);
        for (int i = 0; i < count; i++) items.add(buffer.readItem());
        return new MatterFabricationUploadPayload(menu, recipe, items);
    }
}
