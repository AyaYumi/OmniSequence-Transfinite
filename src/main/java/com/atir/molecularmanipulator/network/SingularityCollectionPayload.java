package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.menu.SingularityMenu;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A bounded piece of the Hub resource list. Item registry ids avoid unbounded UTF strings entirely. */
public record SingularityCollectionPayload(int containerId, long generation, int offset, int total, List<Item> items)
        implements CustomPacketPayload {
    public static final int MAX_ITEMS_PER_CHUNK = 128;
    public static final Type<SingularityCollectionPayload> TYPE =
            new Type<>(MolecularManipulator.id("singularity_collection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SingularityCollectionPayload> STREAM_CODEC =
            StreamCodec.of(SingularityCollectionPayload::encode, SingularityCollectionPayload::decode);

    public SingularityCollectionPayload {
        java.util.Objects.requireNonNull(items, "items");
        if (containerId < 0 || generation < 0 || offset < 0 || total < 0
                || total > BuiltInRegistries.ITEM.size() || offset > total
                || items.size() > MAX_ITEMS_PER_CHUNK || items.size() > total - offset
                || items.isEmpty() && (total != 0 || offset != 0)) {
            throw new IllegalArgumentException("Invalid singularity collection chunk bounds");
        }
        items = List.copyOf(items);
        for (var item : items) {
            int id = BuiltInRegistries.ITEM.getId(item);
            if (id < 0 || BuiltInRegistries.ITEM.byId(id) != item) {
                throw new IllegalArgumentException("Unregistered collection item");
            }
        }
    }

    public static List<SingularityCollectionPayload> chunks(int containerId, long generation, List<Item> items) {
        if (items.isEmpty()) return List.of(new SingularityCollectionPayload(containerId, generation, 0, 0, List.of()));
        var result = new ArrayList<SingularityCollectionPayload>();
        for (int offset = 0; offset < items.size(); offset += MAX_ITEMS_PER_CHUNK) {
            result.add(new SingularityCollectionPayload(containerId, generation, offset, items.size(),
                    items.subList(offset, Math.min(items.size(), offset + MAX_ITEMS_PER_CHUNK))));
        }
        return List.copyOf(result);
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, SingularityCollectionPayload::handle);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SingularityCollectionPayload payload) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeLong(payload.generation);
        buffer.writeVarInt(payload.offset);
        buffer.writeVarInt(payload.total);
        buffer.writeVarInt(payload.items.size());
        for (var item : payload.items) buffer.writeVarInt(BuiltInRegistries.ITEM.getId(item));
    }

    private static SingularityCollectionPayload decode(RegistryFriendlyByteBuf buffer) {
        int menu = buffer.readVarInt();
        long generation = buffer.readLong();
        int offset = buffer.readVarInt();
        int total = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (menu < 0 || generation < 0 || total < 0 || total > BuiltInRegistries.ITEM.size()
                || offset < 0 || offset > total || count < 0 || count > MAX_ITEMS_PER_CHUNK
                || count > total - offset || count == 0 && (total != 0 || offset != 0)) {
            throw new DecoderException("Invalid singularity collection chunk bounds");
        }
        var items = new ArrayList<Item>(count);
        for (int index = 0; index < count; index++) {
            int id = buffer.readVarInt();
            var item = id < 0 ? null : BuiltInRegistries.ITEM.byId(id);
            if (item == null || BuiltInRegistries.ITEM.getId(item) != id) {
                throw new DecoderException("Unknown singularity collection item id: " + id);
            }
            items.add(item);
        }
        return new SingularityCollectionPayload(menu, generation, offset, total, items);
    }

    private static void handle(SingularityCollectionPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SingularityMenu menu) menu.acceptCollectionResources(payload);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
