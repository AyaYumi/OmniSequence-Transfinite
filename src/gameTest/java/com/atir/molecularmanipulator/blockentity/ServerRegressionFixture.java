package com.atir.molecularmanipulator.blockentity;

import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

/** Disposable real-world sites; nothing here is bound to the main source set or published JAR. */
class ServerRegressionFixture implements AutoCloseable {
    static final String TEMPLATE = "legacy_regression_empty";
    private static final AtomicInteger NEXT_SITE = new AtomicInteger();
    final GameTestHelper helper;
    final ServerLevel level;
    // Small templates only host the test marker. Separate high-altitude sites keep large blueprints,
    // remote bridges and deliberately unloaded chunks away from other tests and player tickets.
    final BlockPos origin = new BlockPos(2_000_008 + NEXT_SITE.getAndIncrement() * 512, 140, 2_000_008);
    private final Set<BlockPos> blocks = new LinkedHashSet<>();
    private final Map<ChunkPos, Boolean> chunks = new LinkedHashMap<>();
    private final Deque<Runnable> cleanup = new ArrayDeque<>();
    private boolean closed;

    ServerRegressionFixture(GameTestHelper helper) {
        this(helper, helper.getLevel());
    }

    ServerRegressionFixture(GameTestHelper helper, ServerLevel level) {
        this.helper = helper;
        this.level = level;
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {}
        });
    }

    final void onCleanup(Runnable action) { cleanup.push(action); }

    final void force(ChunkPos chunk) {
        chunks.putIfAbsent(chunk, level.getForcedChunks().contains(chunk.toLong()));
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunk(chunk.x, chunk.z);
    }

    final BlockEntity place(BlockPos pos, BlockState state) {
        force(new ChunkPos(pos));
        blocks.add(pos.immutable());
        level.setBlock(pos, state, 3);
        return level.getBlockEntity(pos);
    }

    final <T> T place(BlockPos pos, BlockState state, Class<T> type) {
        return type.cast(place(pos, state));
    }

    final SingularityBlockEntity hub(BlockPos pos, boolean complete) {
        if (complete) {
            for (var chunk : SingularityStructure.chunks(pos, Direction.NORTH)) force(chunk);
            for (var part : SingularityStructure.parts()) {
                if (!SingularityStructure.isController(part)) place(SingularityStructure.worldPos(pos, Direction.NORTH, part),
                        SingularityStructure.state(part, Direction.NORTH));
            }
            for (var air : SingularityStructure.requiredAir())
                place(SingularityStructure.worldPos(pos, Direction.NORTH, air), Blocks.AIR.defaultBlockState());
        }
        return place(pos, SingularityContent.CONTROLLER.get().defaultBlockState(), SingularityBlockEntity.class);
    }

    final QuantumBridgeBlockEntity bridge(BlockPos pos) {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
            place(pos.offset(x, y, 0), (x == 0 && y == 0 ? AEBlocks.QUANTUM_LINK : AEBlocks.QUANTUM_RING)
                    .block().defaultBlockState());
        place(pos.east(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        return (QuantumBridgeBlockEntity) level.getBlockEntity(pos);
    }

    final ServerPlayer player(String name, BlockPos pos) { return player(name, pos, new ArrayList<>()); }

    @SuppressWarnings("unchecked")
    final ServerPlayer player(String name, BlockPos pos, List<Packet<?>> packets) {
        var server = level.getServer();
        var profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        // As in BuildingTransferGameTests: a real player retains AE2/NeoForge menu semantics.
        // Only transport is silent. FakePlayer has no usable channel and openMenu is a no-op.
        player.connection = new ServerGamePacketListenerImpl(server, connection, player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { packets.add(packet); }
            @Override public void tick() {}
        };
        try {
            var listField = PlayerList.class.getDeclaredField("players");
            listField.setAccessible(true);
            var list = (List<ServerPlayer>) listField.get(server.getPlayerList());
            var mapField = PlayerList.class.getDeclaredField("playersByUUID");
            mapField.setAccessible(true);
            var map = (Map<UUID, ServerPlayer>) mapField.get(server.getPlayerList());
            list.add(player);
            map.put(player.getUUID(), player);
            onCleanup(() -> {
                player.containerMenu = player.inventoryMenu;
                list.remove(player);
                map.remove(player.getUUID(), player);
                channel.finishAndReleaseAll();
            });
        } catch (ReflectiveOperationException error) {
            channel.finishAndReleaseAll();
            throw new IllegalStateException("Cannot register a real regression player", error);
        }
        // Do not add a world entity: these tests need an online operator, not simulation tickets
        // that would prevent the deliberately isolated motion chunk from unloading.
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getCenter());
        return player;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            // Clear live quantum registrations and derived bodies before taking nodes apart.
            for (var pos : blocks) {
                if (!level.hasChunkAt(pos)) continue;
                var entity = level.getBlockEntity(pos);
                if (entity instanceof SingularityBlockEntity hub) {
                    hub.getQuantumInventory().setItemDirect(0, ItemStack.EMPTY);
                    hub.motion().clear();
                }
                if (entity instanceof QuantumBridgeBlockEntity bridge)
                    bridge.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            }
            while (!cleanup.isEmpty()) cleanup.pop().run();
        } finally {
            try {
                var reversed = new ArrayList<>(blocks);
                Collections.reverse(reversed);
                for (var pos : reversed) {
                    if (!level.hasChunkAt(pos)) force(new ChunkPos(pos));
                    MultiblockChunkLoading.release(level, pos);
                    // AE2 drops inventories even for setBlock. Remove the disposable BE first,
                    // so cleanup cannot scatter encoded patterns or packed controllers.
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                // Some assertions intentionally break a populated crystal through the real removal
                // hook. Remove its test-owned drops too, not just the blocks/nodes/tickets.
                level.getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(224)).forEach(ItemEntity::discard);
            } finally {
                for (var entry : chunks.entrySet()) {
                    var chunk = entry.getKey();
                    level.setChunkForced(chunk.x, chunk.z, entry.getValue());
                    if (!entry.getValue()) level.getChunkSource().chunkMap.getDistanceManager().removeTicket(
                            TicketType.UNKNOWN, chunk, ChunkLevel.byStatus(ChunkStatus.FULL), chunk);
                }
            }
        }
    }
}
