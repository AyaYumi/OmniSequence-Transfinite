package com.atir.molecularmanipulator.network;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.building.*;
import com.atir.molecularmanipulator.registry.BuildingContent;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Only survey geometry crosses the network; archive contents always stay on the server. */
public final class BuildingPayloads {
    public static final int PREVIEW = 0, DEPLOY = 1, SEAL = 2, DETAILS_PAGE_ENTRIES = 11;
    private static Consumer<Open> openHandler = payload -> {};
    private static Consumer<Preview> previewHandler = payload -> {};
    private static Consumer<Details> detailsHandler = payload -> {};
    private BuildingPayloads() {}
    public record Open(BlockPos base, BlockPos target, boolean seal) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(MolecularManipulator.id("star_chart_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeBlockPos(data.base); buf.writeBlockPos(data.target); buf.writeBoolean(data.seal);
        }, buf -> new Open(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean()));
        @Override public Type<Open> type() { return TYPE; }
    }
    public record Action(UUID request, int action, int slot, BlockPos base, Direction facing, BlockPos target) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(MolecularManipulator.id("star_chart_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeByte(data.action); buf.writeVarInt(data.slot); buf.writeBlockPos(data.base);
            buf.writeEnum(data.facing); buf.writeBlockPos(data.target);
        }, buf -> new Action(buf.readUUID(), buf.readUnsignedByte(), buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBlockPos()));
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Placement(BlockPos position, int state, boolean conflict) {}
    public record Preview(UUID request, int page, int totalPages, int conflicts, List<Placement> placements, String error) implements CustomPacketPayload {
        public static final Type<Preview> TYPE = new Type<>(MolecularManipulator.id("star_chart_preview"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Preview> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeVarInt(data.page); buf.writeVarInt(data.totalPages); buf.writeVarInt(data.conflicts);
            buf.writeUtf(data.error, 512); buf.writeVarInt(data.placements.size());
            for (var entry : data.placements) { buf.writeBlockPos(entry.position); buf.writeVarInt(entry.state); buf.writeBoolean(entry.conflict); }
        }, buf -> {
            var request = buf.readUUID(); int page = buf.readVarInt(), total = buf.readVarInt(), conflicts = buf.readVarInt();
            var error = buf.readUtf(512); int size = buf.readVarInt();
            if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid survey page size");
            var entries = new ArrayList<Placement>(size);
            for (int i = 0; i < size; i++) entries.add(new Placement(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean()));
            return new Preview(request, page, total, conflicts, entries, error);
        });
        @Override public Type<Preview> type() { return TYPE; }
    }
    public record DetailEntry(BlockPos offset, int state, long nativeBytes) {}
    public record DetailsQuery(UUID request, int slot, int page) implements CustomPacketPayload {
        public static final Type<DetailsQuery> TYPE = new Type<>(MolecularManipulator.id("folding_details_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DetailsQuery> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeVarInt(data.slot); buf.writeVarInt(data.page);
        }, buf -> new DetailsQuery(buf.readUUID(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<DetailsQuery> type() { return TYPE; }
    }
    public record Details(UUID request, int page, int pages, int blocks, long nativeBytes, List<DetailEntry> entries, boolean unavailable) implements CustomPacketPayload {
        public static final Type<Details> TYPE = new Type<>(MolecularManipulator.id("folding_details"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Details> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeVarInt(data.page); buf.writeVarInt(data.pages); buf.writeVarInt(data.blocks);
            buf.writeVarLong(data.nativeBytes); buf.writeBoolean(data.unavailable); buf.writeVarInt(data.entries.size());
            for (var entry : data.entries) { buf.writeBlockPos(entry.offset); buf.writeVarInt(entry.state); buf.writeVarLong(entry.nativeBytes); }
        }, buf -> {
            var request = buf.readUUID(); int page = buf.readVarInt(), pages = buf.readVarInt(), blocks = buf.readVarInt();
            long bytes = buf.readVarLong(); boolean unavailable = buf.readBoolean(); int size = buf.readVarInt();
            if (size < 0 || size > DETAILS_PAGE_ENTRIES) throw new IllegalArgumentException("Invalid archive detail page size");
            var entries = new ArrayList<DetailEntry>(size);
            for (int i = 0; i < size; i++) entries.add(new DetailEntry(buf.readBlockPos(), buf.readVarInt(), buf.readVarLong()));
            return new Details(request, page, pages, blocks, bytes, entries, unavailable);
        });
        @Override public Type<Details> type() { return TYPE; }
    }
    public static void handlers(Consumer<Open> open, Consumer<Preview> preview, Consumer<Details> details) {
        openHandler = open; previewHandler = preview; detailsHandler = details;
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, context) -> context.enqueueWork(() -> openHandler.accept(payload)));
        registrar.playToClient(Preview.TYPE, Preview.CODEC, (payload, context) -> context.enqueueWork(() -> previewHandler.accept(payload)));
        registrar.playToClient(Details.TYPE, Details.CODEC, (payload, context) -> context.enqueueWork(() -> detailsHandler.accept(payload)));
        registrar.playToServer(DetailsQuery.TYPE, DetailsQuery.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !player.isHolding(BuildingContent.CALIBRATOR.get())
                    || payload.slot < 0 || payload.slot >= player.getInventory().items.size()) return;
            try {
                var stack = player.getInventory().getItem(payload.slot); var id = BuildingPackage.archive(stack);
                if (id == null) throw new java.io.IOException("No archive");
                var store = BuildingArchiveStore.forServer(player.server); var manifest = store.readManifest(id);
                var index = store.readIndex(id); var registry = player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
                var rows = index.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(value -> (net.minecraft.nbt.CompoundTag) value)
                        .filter(value -> value.getInt("chunks") > 0).toList();
                int pages = Math.max(1, Math.ceilDiv(rows.size(), DETAILS_PAGE_ENTRIES)), page = Math.clamp(payload.page, 0, pages - 1);
                var entries = new ArrayList<DetailEntry>();
                for (int i = page * DETAILS_PAGE_ENTRIES; i < Math.min(rows.size(), (page + 1) * DETAILS_PAGE_ENTRIES); i++) {
                    var row = rows.get(i); entries.add(new DetailEntry(BlockPos.of(row.getLong("offset")),
                            Block.getId(net.minecraft.nbt.NbtUtils.readBlockState(registry, row.getCompound("state"))), row.getLong("bytes")));
                }
                PacketDistributor.sendToPlayer(player, new Details(payload.request, page, pages, manifest.getInt("blocks"), index.getLong("native_bytes"), entries, false));
            } catch (Exception error) { PacketDistributor.sendToPlayer(player, new Details(payload.request, 0, 1, 0, 0, List.of(), true)); }
        }));
        registrar.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) action(player, payload);
        }));
    }
    private static void action(ServerPlayer player, Action action) {
        if (!player.isHolding(BuildingContent.CALIBRATOR.get()) || !player.mayBuild() || player.isSpectator()
                || action.facing.getAxis().isVertical() || action.base.distSqr(player.blockPosition()) > 256D * 256D) return;
        if (action.action == SEAL) {
            if (action.target.distSqr(player.blockPosition()) > 64 || !player.level().mayInteract(player, action.target)) return;
            var controller = player.level().getBlockEntity(action.target);
            if (controller != null) BuildingTransfer.seal(player, controller);
            return;
        }
        if (action.slot < 0 || action.slot >= player.getInventory().items.size()) return;
        if (action.action == DEPLOY) { BuildingTransfer.deploy(player, action.slot, action.base, action.facing); return; }
        if (action.action != PREVIEW) return;
        try {
            var stack = player.getInventory().getItem(action.slot); var layout = BuildingTransfer.layout(player, stack);
            var controller = BuildingTransfer.controllerAtBase(layout, action.base); var rotation = BuildingType.rotation(action.facing);
            var conflicts = new HashSet<>(BuildingTransfer.conflicts(player, layout, controller, action.facing));
            int pages = Math.ceilDiv(layout.size(), 256);
            for (int page = 0; page < pages; page++) {
                var placements = new ArrayList<Placement>();
                for (int i = page * 256; i < Math.min(layout.size(), (page + 1) * 256); i++) {
                    var entry = layout.get(i); var pos = controller.offset(entry.offset().rotate(rotation));
                    placements.add(new Placement(pos, Block.getId(BuildingType.rotateState(entry.state(), rotation)), conflicts.contains(pos)));
                }
                PacketDistributor.sendToPlayer(player, new Preview(action.request, page, pages, conflicts.size(), placements, ""));
            }
        } catch (Exception error) {
            PacketDistributor.sendToPlayer(player, new Preview(action.request, 0, 1, 1, List.of(),
                    error.getMessage() == null ? "Archive unavailable" : error.getMessage().substring(0, Math.min(error.getMessage().length(), 512))));
        }
    }
}
