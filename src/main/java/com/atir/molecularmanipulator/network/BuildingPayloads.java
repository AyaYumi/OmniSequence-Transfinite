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
    private static Consumer<Deployment> deploymentHandler = payload -> {};
    private static Consumer<DeploymentCancelled> cancellationHandler = payload -> {};
    private static Consumer<TransferStatus> transferHandler = payload -> {};
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
    public record Deployment(UUID request, boolean accepted) implements CustomPacketPayload {
        public static final Type<Deployment> TYPE = new Type<>(MolecularManipulator.id("star_chart_deployment"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Deployment> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeBoolean(data.accepted);
        }, buf -> new Deployment(buf.readUUID(), buf.readBoolean()));
        @Override public Type<Deployment> type() { return TYPE; }
    }
    public record DeploymentCancelled(net.minecraft.resources.ResourceLocation dimension, BlockPos controller) implements CustomPacketPayload {
        public static final Type<DeploymentCancelled> TYPE = new Type<>(MolecularManipulator.id("star_chart_cancelled"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeploymentCancelled> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeResourceLocation(data.dimension); buf.writeBlockPos(data.controller);
        }, buf -> new DeploymentCancelled(buf.readResourceLocation(), buf.readBlockPos()));
        @Override public Type<DeploymentCancelled> type() { return TYPE; }
    }
    public record TransferControl(UUID request, UUID operation, int slot, BlockPos target, boolean interrupt) implements CustomPacketPayload {
        public static final Type<TransferControl> TYPE = new Type<>(MolecularManipulator.id("star_chart_transfer_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TransferControl> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeBoolean(data.operation != null); if (data.operation != null) buf.writeUUID(data.operation);
            buf.writeVarInt(data.slot); buf.writeBlockPos(data.target); buf.writeBoolean(data.interrupt);
        }, buf -> new TransferControl(buf.readUUID(), buf.readBoolean() ? buf.readUUID() : null, buf.readVarInt(), buf.readBlockPos(), buf.readBoolean()));
        @Override public Type<TransferControl> type() { return TYPE; }
    }
    public record TransferStatus(UUID request, UUID operation, String state) implements CustomPacketPayload {
        public static final Type<TransferStatus> TYPE = new Type<>(MolecularManipulator.id("star_chart_transfer_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TransferStatus> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeBoolean(data.operation != null); if (data.operation != null) buf.writeUUID(data.operation); buf.writeUtf(data.state, 32);
        }, buf -> new TransferStatus(buf.readUUID(), buf.readBoolean() ? buf.readUUID() : null, buf.readUtf(32)));
        @Override public Type<TransferStatus> type() { return TYPE; }
    }
    public record Placement(BlockPos position, int state, boolean conflict) {}
    public record Preview(UUID request, int page, int totalPages, int conflicts, List<Placement> placements, String error,
            Component summary, Component detail) implements CustomPacketPayload {
        public Preview(UUID request, int page, int totalPages, int conflicts, List<Placement> placements, String error) {
            this(request, page, totalPages, conflicts, placements, error, Component.empty(), Component.empty());
        }
        public static final Type<Preview> TYPE = new Type<>(MolecularManipulator.id("star_chart_preview"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Preview> CODEC = StreamCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeVarInt(data.page); buf.writeVarInt(data.totalPages); buf.writeVarInt(data.conflicts);
            buf.writeUtf(data.error, 512);
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, data.summary);
            net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buf, data.detail);
            buf.writeVarInt(data.placements.size());
            for (var entry : data.placements) { buf.writeBlockPos(entry.position); buf.writeVarInt(entry.state); buf.writeBoolean(entry.conflict); }
        }, buf -> {
            var request = buf.readUUID(); int page = buf.readVarInt(), total = buf.readVarInt(), conflicts = buf.readVarInt();
            var error = buf.readUtf(512);
            var summary = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf);
            var detail = net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buf);
            int size = buf.readVarInt();
            if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid survey page size");
            var entries = new ArrayList<Placement>(size);
            for (int i = 0; i < size; i++) entries.add(new Placement(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean()));
            return new Preview(request, page, total, conflicts, entries, error, summary, detail);
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
    public static void handlers(Consumer<Open> open, Consumer<Preview> preview, Consumer<Details> details,
            Consumer<Deployment> deployment, Consumer<DeploymentCancelled> cancellation, Consumer<TransferStatus> transfer) {
        openHandler = open; previewHandler = preview; detailsHandler = details; deploymentHandler = deployment; cancellationHandler = cancellation; transferHandler = transfer;
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("3");
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, context) -> context.enqueueWork(() -> openHandler.accept(payload)));
        registrar.playToClient(Preview.TYPE, Preview.CODEC, (payload, context) -> context.enqueueWork(() -> previewHandler.accept(payload)));
        registrar.playToClient(Details.TYPE, Details.CODEC, (payload, context) -> context.enqueueWork(() -> detailsHandler.accept(payload)));
        registrar.playToClient(Deployment.TYPE, Deployment.CODEC, (payload, context) -> context.enqueueWork(() -> deploymentHandler.accept(payload)));
        registrar.playToClient(DeploymentCancelled.TYPE, DeploymentCancelled.CODEC, (payload, context) -> context.enqueueWork(() -> cancellationHandler.accept(payload)));
        registrar.playToClient(TransferStatus.TYPE, TransferStatus.CODEC, (payload, context) -> context.enqueueWork(() -> transferHandler.accept(payload)));
        registrar.playToServer(TransferControl.TYPE, TransferControl.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) transferControl(player, payload);
        }));
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
    public static void transferControl(ServerPlayer player, TransferControl control) {
        try {
            // Held items can change while a panel is open. Always acknowledge an owned status query,
            // but only a held calibrator and a build-capable player may authorize a withdrawal.
            var state = BuildingTransfer.transferState(player, control.operation, control.slot, control.target);
            if (control.interrupt && state.id() != null) {
                if (!player.isHolding(BuildingContent.CALIBRATOR.get()))
                    player.displayClientMessage(Component.translatable("star_chart.molecularmanipulator.hold_calibrator"), false);
                else if (player.mayBuild() && !player.isSpectator()) BuildingTransfer.cancelDeployment(player, state.id());
                state = BuildingTransfer.transferState(player, state.id(), control.slot, control.target);
            }
            PacketDistributor.sendToPlayer(player, new TransferStatus(control.request, state.id(), state.state()));
        } catch (java.io.IOException error) {
            PacketDistributor.sendToPlayer(player, new TransferStatus(control.request, control.operation, "UNAVAILABLE"));
        }
    }
    private static void action(ServerPlayer player, Action action) {
        if (!player.isHolding(BuildingContent.CALIBRATOR.get()) || !player.mayBuild() || player.isSpectator()
                || action.facing.getAxis().isVertical() || action.base.distSqr(player.blockPosition()) > 256D * 256D) {
            if (action.action == DEPLOY) PacketDistributor.sendToPlayer(player, new Deployment(action.request, false));
            return;
        }
        if (action.action == SEAL) {
            if (action.target.distSqr(player.blockPosition()) > 64 || !player.level().mayInteract(player, action.target)) return;
            var controller = player.level().getBlockEntity(action.target);
            if (controller != null) BuildingTransfer.seal(player, controller);
            return;
        }
        if (action.slot < 0 || action.slot >= player.getInventory().items.size()) {
            if (action.action == DEPLOY) PacketDistributor.sendToPlayer(player, new Deployment(action.request, false));
            return;
        }
        if (action.action == DEPLOY) {
            PacketDistributor.sendToPlayer(player, new Deployment(action.request,
                    BuildingTransfer.deploy(player, action.slot, action.base, action.facing)));
            return;
        }
        if (action.action != PREVIEW) return;
        try {
            var stack = player.getInventory().getItem(action.slot); var layout = BuildingTransfer.layout(player, stack);
            var controller = BuildingTransfer.controllerAtBase(layout, action.base); var rotation = BuildingType.rotation(action.facing);
            var site = BuildingTransfer.survey(player, layout, controller, action.facing);
            var conflicts = new HashSet<>(site.conflicts());
            int pages = Math.ceilDiv(layout.size(), 256);
            for (int page = 0; page < pages; page++) {
                var placements = new ArrayList<Placement>();
                for (int i = page * 256; i < Math.min(layout.size(), (page + 1) * 256); i++) {
                    var entry = layout.get(i); var pos = controller.offset(entry.offset().rotate(rotation));
                    placements.add(new Placement(pos, Block.getId(BuildingType.rotateState(entry.state(), rotation)), conflicts.contains(pos)));
                }
                PacketDistributor.sendToPlayer(player, new Preview(action.request, page, pages, conflicts.size(), placements, "", site.summary(), site.detail()));
            }
        } catch (Exception error) {
            PacketDistributor.sendToPlayer(player, new Preview(action.request, 0, 1, 1, List.of(),
                    error.getMessage() == null ? "Archive unavailable" : error.getMessage().substring(0, Math.min(error.getMessage().length(), 512))));
        }
    }
}
