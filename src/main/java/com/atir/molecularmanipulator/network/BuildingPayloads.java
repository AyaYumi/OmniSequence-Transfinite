package com.atir.molecularmanipulator.network;

import com.appliedenhancements.network.PacketCodec;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.building.*;
import com.atir.molecularmanipulator.registry.BuildingContent;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Only survey geometry crosses the network; archive contents always stay on the server. */
public final class BuildingPayloads {
    public static final int PREVIEW = 0, DEPLOY = 1, SEAL = 2, DETAILS_PAGE_ENTRIES = 11;
    private static Consumer<Open> openHandler = payload -> {};
    private static Consumer<Preview> previewHandler = payload -> {};
    private static Consumer<Details> detailsHandler = payload -> {};
    private static Consumer<Deployment> deploymentHandler = payload -> {};
    private static Consumer<DeploymentCancelled> cancellationHandler = payload -> {};
    private static Consumer<TransferStatus> transferHandler = payload -> {};
    private static final String PROTOCOL = "3-forge-1";
    private static final class Channel {
        static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
                MolecularManipulator.id("building"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    }
    private BuildingPayloads() {}
    public record Open(BlockPos base, BlockPos target, boolean seal) {
        public static final PacketCodec<FriendlyByteBuf, Open> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeBlockPos(data.base); buf.writeBlockPos(data.target); buf.writeBoolean(data.seal);
        }, buf -> new Open(buf.readBlockPos(), buf.readBlockPos(), buf.readBoolean()));
    }
    public record Action(UUID request, int action, int slot, BlockPos base, Direction facing, BlockPos target) {
        public static final PacketCodec<FriendlyByteBuf, Action> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeByte(data.action); buf.writeVarInt(data.slot); buf.writeBlockPos(data.base);
            buf.writeEnum(data.facing); buf.writeBlockPos(data.target);
        }, buf -> new Action(buf.readUUID(), buf.readUnsignedByte(), buf.readVarInt(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readBlockPos()));
    }
    public record Deployment(UUID request, boolean accepted) {
        public static final PacketCodec<FriendlyByteBuf, Deployment> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeBoolean(data.accepted);
        }, buf -> new Deployment(buf.readUUID(), buf.readBoolean()));
    }
    public record DeploymentCancelled(net.minecraft.resources.ResourceLocation dimension, BlockPos controller) {
        public static final PacketCodec<FriendlyByteBuf, DeploymentCancelled> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeResourceLocation(data.dimension); buf.writeBlockPos(data.controller);
        }, buf -> new DeploymentCancelled(buf.readResourceLocation(), buf.readBlockPos()));
    }
    public record TransferControl(UUID request, UUID operation, int slot, BlockPos target, boolean interrupt) {
        public static final PacketCodec<FriendlyByteBuf, TransferControl> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeBoolean(data.operation != null); if (data.operation != null) buf.writeUUID(data.operation);
            buf.writeVarInt(data.slot); buf.writeBlockPos(data.target); buf.writeBoolean(data.interrupt);
        }, buf -> new TransferControl(buf.readUUID(), buf.readBoolean() ? buf.readUUID() : null, buf.readVarInt(), buf.readBlockPos(), buf.readBoolean()));
    }
    public record TransferStatus(UUID request, UUID operation, String state) {
        public static final PacketCodec<FriendlyByteBuf, TransferStatus> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeBoolean(data.operation != null); if (data.operation != null) buf.writeUUID(data.operation); buf.writeUtf(data.state, 32);
        }, buf -> new TransferStatus(buf.readUUID(), buf.readBoolean() ? buf.readUUID() : null, buf.readUtf(32)));
    }
    public record Placement(BlockPos position, int state, boolean conflict) {}
    public record Preview(UUID request, int page, int totalPages, int conflicts, List<Placement> placements, String error,
            Component summary, Component detail) {
        public Preview(UUID request, int page, int totalPages, int conflicts, List<Placement> placements, String error) {
            this(request, page, totalPages, conflicts, placements, error, Component.empty(), Component.empty());
        }
        public static final PacketCodec<FriendlyByteBuf, Preview> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeVarInt(data.page); buf.writeVarInt(data.totalPages); buf.writeVarInt(data.conflicts);
            buf.writeUtf(data.error, 512);
            buf.writeComponent(data.summary);
            buf.writeComponent(data.detail);
            buf.writeVarInt(data.placements.size());
            for (var entry : data.placements) { buf.writeBlockPos(entry.position); buf.writeVarInt(entry.state); buf.writeBoolean(entry.conflict); }
        }, buf -> {
            var request = buf.readUUID(); int page = buf.readVarInt(), total = buf.readVarInt(), conflicts = buf.readVarInt();
            var error = buf.readUtf(512);
            var summary = buf.readComponent();
            var detail = buf.readComponent();
            int size = buf.readVarInt();
            if (size < 0 || size > 256) throw new IllegalArgumentException("Invalid survey page size");
            var entries = new ArrayList<Placement>(size);
            for (int i = 0; i < size; i++) entries.add(new Placement(buf.readBlockPos(), buf.readVarInt(), buf.readBoolean()));
            return new Preview(request, page, total, conflicts, entries, error, summary, detail);
        });
    }
    public record DetailEntry(BlockPos offset, int state, long nativeBytes) {}
    public record DetailsQuery(UUID request, int slot, int page) {
        public static final PacketCodec<FriendlyByteBuf, DetailsQuery> CODEC = PacketCodec.of((buf, data) -> {
            buf.writeUUID(data.request); buf.writeVarInt(data.slot); buf.writeVarInt(data.page);
        }, buf -> new DetailsQuery(buf.readUUID(), buf.readVarInt(), buf.readVarInt()));
    }
    public record Details(UUID request, int page, int pages, int blocks, long nativeBytes, List<DetailEntry> entries, boolean unavailable) {
        public static final PacketCodec<FriendlyByteBuf, Details> CODEC = PacketCodec.of((buf, data) -> {
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
    }
    public static void handlers(Consumer<Open> open, Consumer<Preview> preview, Consumer<Details> details,
            Consumer<Deployment> deployment, Consumer<DeploymentCancelled> cancellation, Consumer<TransferStatus> transfer) {
        openHandler = open; previewHandler = preview; detailsHandler = details; deploymentHandler = deployment; cancellationHandler = cancellation; transferHandler = transfer;
    }
    public static void register() {
        registerClient(0, Open.class, Open.CODEC, payload -> openHandler.accept(payload));
        registerClient(1, Preview.class, Preview.CODEC, payload -> previewHandler.accept(payload));
        registerClient(2, Details.class, Details.CODEC, payload -> detailsHandler.accept(payload));
        registerClient(3, Deployment.class, Deployment.CODEC, payload -> deploymentHandler.accept(payload));
        registerClient(4, DeploymentCancelled.class, DeploymentCancelled.CODEC, payload -> cancellationHandler.accept(payload));
        registerClient(5, TransferStatus.class, TransferStatus.CODEC, payload -> transferHandler.accept(payload));
        registerServer(6, TransferControl.class, TransferControl.CODEC, BuildingPayloads::transferControl);
        registerServer(7, DetailsQuery.class, DetailsQuery.CODEC, BuildingPayloads::details);
        registerServer(8, Action.class, Action.CODEC, BuildingPayloads::action);
    }
    private static <T> void registerClient(int id, Class<T> type, PacketCodec<FriendlyByteBuf, T> codec, Consumer<T> handler) {
        Channel.INSTANCE.messageBuilder(type, id, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((payload, buffer) -> codec.encode(buffer, payload)).decoder(codec::decode)
                .consumerMainThread((payload, context) -> handler.accept(payload)).add();
    }
    private static <T> void registerServer(int id, Class<T> type, PacketCodec<FriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {
        Channel.INSTANCE.messageBuilder(type, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder((payload, buffer) -> codec.encode(buffer, payload)).decoder(codec::decode)
                .consumerMainThread((payload, context) -> {
                    var player = context.get().getSender();
                    if (player != null) handler.accept(player, payload);
                }).add();
    }
    public static void sendToPlayer(ServerPlayer player, Object payload) {
        Channel.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
    public static void sendToServer(Object payload) {
        Channel.INSTANCE.sendToServer(payload);
    }
    private static void details(ServerPlayer player, DetailsQuery payload) {
        if (!player.isHolding(BuildingContent.CALIBRATOR.get())
                || payload.slot < 0 || payload.slot >= player.getInventory().items.size()) return;
        try {
            var stack = player.getInventory().getItem(payload.slot); var id = BuildingPackage.archive(stack);
            if (id == null) throw new java.io.IOException("No archive");
            var store = BuildingArchiveStore.forServer(player.server); var manifest = store.readManifest(id);
            var index = store.readIndex(id); var registry = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
            var rows = index.getList("entries", net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(value -> (net.minecraft.nbt.CompoundTag) value)
                    .filter(value -> value.getInt("chunks") > 0).toList();
            int pages = Math.max(1, com.atir.molecularmanipulator.util.MathCompat.ceilDiv(rows.size(), DETAILS_PAGE_ENTRIES)), page = com.atir.molecularmanipulator.util.MathCompat.clamp(payload.page, 0, pages - 1);
            var entries = new ArrayList<DetailEntry>();
            for (int i = page * DETAILS_PAGE_ENTRIES; i < Math.min(rows.size(), (page + 1) * DETAILS_PAGE_ENTRIES); i++) {
                var row = rows.get(i); entries.add(new DetailEntry(BlockPos.of(row.getLong("offset")),
                        Block.getId(net.minecraft.nbt.NbtUtils.readBlockState(registry, row.getCompound("state"))), row.getLong("bytes")));
            }
            sendToPlayer(player, new Details(payload.request, page, pages, manifest.getInt("blocks"), index.getLong("native_bytes"), entries, false));
        } catch (Exception error) { sendToPlayer(player, new Details(payload.request, 0, 1, 0, 0, List.of(), true)); }
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
            sendToPlayer(player, new TransferStatus(control.request, state.id(), state.state()));
        } catch (java.io.IOException error) {
            sendToPlayer(player, new TransferStatus(control.request, control.operation, "UNAVAILABLE"));
        }
    }
    private static void action(ServerPlayer player, Action action) {
        if (!player.isHolding(BuildingContent.CALIBRATOR.get()) || !player.mayBuild() || player.isSpectator()
                || action.facing.getAxis().isVertical() || action.base.distSqr(player.blockPosition()) > 256D * 256D) {
            if (action.action == DEPLOY) sendToPlayer(player, new Deployment(action.request, false));
            return;
        }
        if (action.action == SEAL) {
            if (action.target.distSqr(player.blockPosition()) > 64 || !player.level().mayInteract(player, action.target)) return;
            var controller = player.level().getBlockEntity(action.target);
            if (controller != null) BuildingTransfer.seal(player, controller);
            return;
        }
        if (action.slot < 0 || action.slot >= player.getInventory().items.size()) {
            if (action.action == DEPLOY) sendToPlayer(player, new Deployment(action.request, false));
            return;
        }
        if (action.action == DEPLOY) {
            sendToPlayer(player, new Deployment(action.request,
                    BuildingTransfer.deploy(player, action.slot, action.base, action.facing)));
            return;
        }
        if (action.action != PREVIEW) return;
        try {
            var stack = player.getInventory().getItem(action.slot); var layout = BuildingTransfer.layout(player, stack);
            var controller = BuildingTransfer.controllerAtBase(layout, action.base); var rotation = BuildingType.rotation(action.facing);
            var site = BuildingTransfer.survey(player, layout, controller, action.facing);
            var conflicts = new HashSet<>(site.conflicts());
            int pages = com.atir.molecularmanipulator.util.MathCompat.ceilDiv(layout.size(), 256);
            for (int page = 0; page < pages; page++) {
                var placements = new ArrayList<Placement>();
                for (int i = page * 256; i < Math.min(layout.size(), (page + 1) * 256); i++) {
                    var entry = layout.get(i); var pos = controller.offset(entry.offset().rotate(rotation));
                    placements.add(new Placement(pos, Block.getId(BuildingType.rotateState(entry.state(), rotation)), conflicts.contains(pos)));
                }
                sendToPlayer(player, new Preview(action.request, page, pages, conflicts.size(), placements, "", site.summary(), site.detail()));
            }
        } catch (Exception error) {
            sendToPlayer(player, new Preview(action.request, 0, 1, 1, List.of(),
                    error.getMessage() == null ? "Archive unavailable" : error.getMessage().substring(0, Math.min(error.getMessage().length(), 512))));
        }
    }
}
