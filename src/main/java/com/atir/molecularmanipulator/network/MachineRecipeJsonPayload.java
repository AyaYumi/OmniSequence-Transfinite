package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.ForgeMachineRecipeJson;
import com.atir.molecularmanipulator.crafting.MatterRecipeBridge;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Keeps Forge client import mappings on the same JSON schema as the server. */
public record MachineRecipeJsonPayload(boolean reset, boolean complete, Map<ResourceLocation, String> entries) {
    private static final int MAX_CHARS = 250_000;
    private static final int MAX_ENTRIES = 32;
    private static final String PROTOCOL = "2.0.7-forge-1";
    private static final class Channel {
        static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                MolecularManipulator.id("machine_recipes"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }

    public static void register() {
        Channel.INSTANCE.messageBuilder(MachineRecipeJsonPayload.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MachineRecipeJsonPayload::encode).decoder(MachineRecipeJsonPayload::decode)
                .consumerMainThread((payload, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.accept(payload));
                    context.get().setPacketHandled(true);
                }).add();
        MinecraftForge.EVENT_BUS.addListener(MachineRecipeJsonPayload::sync);
    }

    private static void sync(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        var level = server.overworld();
        var recipes = server.getRecipeManager().getRecipes();
        var serializers = MatterRecipeBridge.machines(recipes).stream()
                .flatMap(machine -> machine.recipeTypes().stream()).collect(Collectors.toSet());
        var chunks = new ArrayList<Map<ResourceLocation, String>>();
        var current = new LinkedHashMap<ResourceLocation, String>();
        int chars = 0;
        for (var recipe : recipes) {
            var serializer = net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
            if (serializer == null || !serializers.contains(serializer.toString())) continue;
            var json = ForgeMachineRecipeJson.encode(level, recipe);
            if (json == null) continue;
            String encoded = json.toString();
            if (encoded.length() > MAX_CHARS) {
                com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Machine recipe JSON exceeds sync limit: {}", recipe.getId());
                continue;
            }
            if (!current.isEmpty() && (current.size() == MAX_ENTRIES || chars + encoded.length() > MAX_CHARS)) {
                chunks.add(Map.copyOf(current));
                current.clear();
                chars = 0;
            }
            current.put(recipe.getId(), encoded);
            chars += encoded.length();
        }
        if (!current.isEmpty() || chunks.isEmpty()) chunks.add(Map.copyOf(current));
        List<ServerPlayer> players = event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer());
        for (int index = 0; index < chunks.size(); index++) {
            var payload = new MachineRecipeJsonPayload(index == 0, index == chunks.size() - 1, chunks.get(index));
            for (var player : players) Channel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
        }
    }

    static void encode(MachineRecipeJsonPayload payload, FriendlyByteBuf buffer) {
        buffer.writeBoolean(payload.reset);
        buffer.writeBoolean(payload.complete);
        buffer.writeVarInt(payload.entries.size());
        payload.entries.forEach((id, json) -> {
            buffer.writeResourceLocation(id);
            buffer.writeUtf(json, MAX_CHARS);
        });
    }

    static MachineRecipeJsonPayload decode(FriendlyByteBuf buffer) {
        boolean reset = buffer.readBoolean();
        boolean complete = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new io.netty.handler.codec.DecoderException("Invalid recipe count");
        var entries = new LinkedHashMap<ResourceLocation, String>();
        int chars = 0;
        for (int index = 0; index < count; index++) {
            var id = buffer.readResourceLocation();
            var json = buffer.readUtf(MAX_CHARS);
            chars += json.length();
            if (chars > MAX_CHARS) throw new io.netty.handler.codec.DecoderException("Machine recipe chunk is too large");
            entries.put(id, json);
        }
        return new MachineRecipeJsonPayload(reset, complete, Map.copyOf(entries));
    }

    private static final class ClientHandler {
        private static final Map<ResourceLocation, JsonElement> pending = new LinkedHashMap<>();

        static void accept(MachineRecipeJsonPayload payload) {
            if (payload.reset) pending.clear();
            payload.entries.forEach((id, json) -> pending.put(id, JsonParser.parseString(json)));
            if (!payload.complete) return;
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
            if (connection != null) ForgeMachineRecipeJson.receive(connection.getRecipeManager(), pending);
            pending.clear();
        }
    }
}
