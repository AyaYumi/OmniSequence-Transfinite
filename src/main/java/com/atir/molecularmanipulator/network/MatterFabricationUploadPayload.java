package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.integration.MatterFabricationPatternUpload;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only the recipe identity and displayed alternatives cross the wire, never a destination or quantity. */
public record MatterFabricationUploadPayload(int containerId, ResourceLocation recipeId, List<ItemStack> selections)
        implements CustomPacketPayload {
    public static final Type<MatterFabricationUploadPayload> TYPE = new Type<>(MolecularManipulator.id("fabrication_pattern_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatterFabricationUploadPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                buffer.writeResourceLocation(payload.recipeId);
                buffer.writeVarInt(payload.selections.size());
                for (var stack : payload.selections) ItemStack.STREAM_CODEC.encode(buffer, stack);
            }, buffer -> {
                int menu = buffer.readVarInt();
                var recipe = buffer.readResourceLocation();
                int count = buffer.readVarInt();
                if (menu < 0 || count < 0 || count > MatterFabricationRecipe.MAX_INPUTS) {
                    throw new DecoderException("Invalid fabrication upload request");
                }
                var items = new ArrayList<ItemStack>(count);
                for (int i = 0; i < count; i++) items.add(ItemStack.STREAM_CODEC.decode(buffer));
                return new MatterFabricationUploadPayload(menu, recipe, items);
            });

    public MatterFabricationUploadPayload {
        if (containerId < 0 || recipeId == null || selections.size() > MatterFabricationRecipe.MAX_INPUTS) {
            throw new IllegalArgumentException("Invalid fabrication upload request");
        }
        selections = selections.stream().map(stack -> stack.copyWithCount(1)).toList();
    }

    @Override public Type<MatterFabricationUploadPayload> type() { return TYPE; }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                MatterFabricationPatternUpload.upload(player, payload.containerId, payload.recipeId, payload.selections);
            }
        });
    }
}
