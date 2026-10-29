package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityChunkLoadingGameTests {
    private static final TicketType<ChunkPos> FIXTURE = TicketType.create(
            "singularity_unload_fixture", java.util.Comparator.comparingLong(ChunkPos::toLong));
    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_chunk_loading", timeoutTicks = 400)
    public static void collectionStartsRunsAndDocksWithoutForcedTickets(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var anchor = new BlockPos(12288, 150, 12288);
        var chunks = MultiblockChunkLoading.rectangle(
                SingularityStructure.worldPos(anchor, Direction.NORTH, new BlockPos(-50, 0, -50)),
                SingularityStructure.worldPos(anchor, Direction.NORTH, new BlockPos(50, 128, 50)));
        for (var chunk : chunks) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        for (var part : SingularityStructure.parts()) level.setBlock(
                SingularityStructure.worldPos(anchor, Direction.NORTH, part), SingularityStructure.state(part, Direction.NORTH), 2);
        var machine = (SingularityBlockEntity) level.getBlockEntity(anchor);
        var power = Arrays.stream(Direction.values()).map(anchor::relative)
                .filter(pos -> SingularityStructure.partAt(anchor, Direction.NORTH, pos) == null).findFirst().orElseThrow();
        level.setBlock(power, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var player = operator(level, anchor, "NoChunkTickets");
        var players = onlinePlayers(level);
        players.put(player.getUUID(), player);
        machine.setRecoveryOwner(player);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.networkOnline() && machine.formed(),
                "Complete hub must form and connect to a real powered grid")).thenExecute(() -> {
            boolean previous = ModConfig.FORCE_LOAD_CHUNKS.get();
            var service = machine.getMainNode().getGrid().getStorageService();
            var stock = new KeyCounter();
            IStorageProvider provider = mounts -> mounts.mount(new MEStorage() {
                @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                    if (mode == Actionable.MODULATE) stock.add(key, amount);
                    return amount;
                }
                @Override public void getAvailableStacks(KeyCounter out) { out.addAll(stock); }
                @Override public Component getDescription() { return Component.literal("Chunk-loading regression storage"); }
            }, 0);
            service.addGlobalStorageProvider(provider);
            var current = new SingularityBlockEntity[]{machine};
            var dockingPlayer = operator(level, anchor, "NoChunkTicketsReloaded");
            Runnable cleanup = () -> {
                service.removeGlobalStorageProvider(provider);
                current[0].motion().clear();
                level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(power, Blocks.AIR.defaultBlockState(), 3);
                players.remove(player.getUUID());
                players.remove(dockingPlayer.getUUID());
                for (var chunk : chunks) {
                    level.setChunkForced(chunk.x, chunk.z, false);
                    level.getChunkSource().chunkMap.getDistanceManager().removeTicket(FIXTURE, chunk, 33, chunk);
                }
                ModConfig.FORCE_LOAD_CHUNKS.set(previous);
            };
            boolean deferred = false;
            long startedAt = level.getGameTime();
            try {
                ModConfig.FORCE_LOAD_CHUNKS.set(false);
                MultiblockChunkLoading.maintain(machine);
                helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, anchor).isEmpty(), "Disabled option must release all hub tickets");
                machine.toggleCollection(player);
                helper.assertTrue(machine.collectionActive() && machine.motion().mode() == SingularityMotionState.RUNNING,
                        "Public collection action must start with force-loading disabled: " + machine.motion().message());
                for (int tick = 0; tick < 100; tick++) advance(level, machine);
                helper.assertTrue(machine.collectionActive() && machine.motion().mode() == SingularityMotionState.RUNNING,
                        "Collection must keep running without controller-owned tickets");
                helper.assertTrue(!stock.isEmpty(), "Actual collection cycles must deliver resources into ME storage");
                helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, anchor).isEmpty(), "Running cannot silently reacquire tickets");

                // The fake operator is a real server entity and its simulation
                // ticket would legitimately keep nearby footprint chunks loaded.
                // Remove that unrelated player ticket before isolating the motion
                // chunk; the collection session itself does not require the owner
                // to remain present while it is running or paused.
                players.remove(player.getUUID());
                level.removePlayerImmediately(player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                for (int tick = 0; tick < 3; tick++) level.getChunkSource().tick(() -> true, true);

                // Remove one real, previously loaded motion-footprint chunk while
                // the collection session is running. Release the low-level forced
                // tickets first: neighboring forced tickets propagate one chunk
                // of loading and would otherwise keep the target at BLOCK_TICKING.
                // Keep body chunks entity-ticking. Explicit level-33 fixture
                // tickets keep every other non-target chunk available without
                // propagating FULL status into the isolated target.
                var ownerChunk = new ChunkPos(anchor);
                var bodyChunks = new java.util.HashSet<ChunkPos>();
                bodyChunks.add(ownerChunk);
                for (var body : SingularityMotionWorld.bodies(level)) bodyChunks.add(new ChunkPos(body.blockPosition()));
                for (var chunk : chunks) {
                    level.getChunkSource().chunkMap.getDistanceManager().addTicket(FIXTURE, chunk, 33, chunk);
                    level.setChunkForced(chunk.x, chunk.z, bodyChunks.contains(chunk));
                }
                var dropped = chunks.stream().filter(chunk -> !bodyChunks.contains(chunk))
                        .max(java.util.Comparator.comparingInt(chunk -> bodyChunks.stream()
                                .mapToInt(bodyChunk -> Math.max(Math.abs(chunk.x - bodyChunk.x), Math.abs(chunk.z - bodyChunk.z)))
                                .min().orElse(0)))
                        .orElseThrow();
                level.getChunkSource().chunkMap.getDistanceManager().removeTicket(FIXTURE, dropped, 33, dropped);
                unload(level, dropped);
                helper.assertTrue(!level.hasChunk(dropped.x, dropped.z), "The isolated target chunk must really unload");
                helper.assertTrue(chunks.stream().filter(chunk -> !chunk.equals(dropped))
                                .allMatch(chunk -> level.hasChunk(chunk.x, chunk.z)),
                        "Every non-target footprint chunk must stay loaded");
                int[] pausedOwned = machine.motion().portableCounts();
                advance(level, machine);
                helper.assertTrue(machine.collectionActive()
                                && machine.motion().mode() == SingularityMotionState.PAUSED
                                && machine.motion().message().equals("unloaded"),
                        "A running collection session must pause instead of docking when one footprint chunk unloads: active="
                                + machine.collectionActive() + " mode=" + machine.motion().mode()
                                + " message=" + machine.motion().message());
                long pausedPhase = machine.motion().save().getLong("phase");
                for (int tick = 0; tick < 40; tick++) advance(level, machine);
                helper.assertTrue(machine.motion().save().getLong("phase") == pausedPhase
                                && Arrays.equals(pausedOwned, machine.motion().portableCounts())
                                && bodyCount(level, anchor) == 11,
                        "Missing chunks must freeze motion without losing bodies or owned materials");

                helper.assertTrue(machine.collectionActive() && stock.size() > 0,
                        "The waiting session must remain active through a complete inspection interval");
                load(level, dropped);
                helper.assertTrue(chunks.stream().allMatch(chunk -> level.hasChunk(chunk.x, chunk.z)),
                        "The full swept footprint must be available before recovery");
                for (int tick = 0; tick < 3; tick++) advance(level, machine);
                helper.assertTrue(machine.collectionActive() && machine.motion().mode() == SingularityMotionState.RUNNING,
                        "Reloading the missing chunk must resume the existing collection session: active="
                                + machine.collectionActive() + " mode=" + machine.motion().mode()
                                + " message=" + machine.motion().message()
                                + " targetLoaded=" + level.hasChunk(dropped.x, dropped.z));
                players.put(dockingPlayer.getUUID(), dockingPlayer);
                current[0] = reload(level, machine, anchor);
                var restored = current[0];
                advance(level, restored);
                helper.assertTrue(restored.collectionActive() && !restored.getMainNode().isReady(),
                        "A restored session must survive the first tick before AE2 node initialization");
                // Let normal server events initialize the restored node and boot
                // its real network rather than faking active grid state.
                helper.runAfterDelay(level.getGameTime() - startedAt + 40, () -> {
                    try {
                        helper.assertTrue(restored.networkOnline() && restored.collectionActive()
                                        && restored.motion().mode() == SingularityMotionState.RUNNING
                                        && bodyCount(level, anchor) == 11
                                        && Arrays.equals(pausedOwned, restored.motion().portableCounts()),
                                "Controller recovery: active=" + restored.collectionActive() + " network=" + restored.networkOnline()
                                        + " ready=" + restored.getMainNode().isReady() + " booted=" + restored.getMainNode().hasGridBooted()
                                        + " powered=" + restored.getMainNode().isPowered() + " online=" + restored.getMainNode().isOnline()
                                        + " installed=" + (level.getBlockEntity(anchor) == restored)
                                        + " power=" + level.getBlockState(power) + " powerBE=" + level.getBlockEntity(power)
                                        + " formed=" + restored.formed() + " mode=" + restored.motion().mode()
                                        + " message=" + restored.motion().message() + " bodies=" + bodyCount(level, anchor)
                                        + " conserved=" + Arrays.equals(pausedOwned, restored.motion().portableCounts()));
                        helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, anchor).isEmpty(),
                                "Resumed motion cannot create controller tickets");
                        int[] owned = restored.motion().portableCounts();
                        restored.toggleCollection(dockingPlayer);
                        helper.assertTrue(!restored.collectionActive() && restored.motion().mode() == SingularityMotionState.DOCKING,
                                "Public collection stop must begin docking with force-loading disabled");
                        for (int tick = 0; tick < 1000 && restored.motion().hasBodies(); tick++) advance(level, restored);
                        helper.assertTrue(!restored.motion().hasBodies(), "Docking must complete without force-loading");
                        helper.assertTrue(SingularityStructure.inspect(level, anchor, Direction.NORTH).formed(),
                                "Docking must restore every authored block exactly");
                        helper.assertTrue(Arrays.stream(owned).sum() > 0 && Arrays.stream(restored.motion().portableCounts()).sum() == 0,
                                "Restored blocks cannot remain owned as portable materials");
                        helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, anchor).isEmpty(), "Docking cannot reacquire hub tickets");
                        LogUtils.getLogger().info("SINGULARITY_NO_FORCE_LOAD_PASS publicCollection=true produced=true isolatedUnload=true pauseResume=true controllerReload=true docking=true tickets=0");
                        helper.succeed();
                    } finally {
                        cleanup.run();
                    }
                });
                deferred = true;
            } finally {
                if (!deferred) cleanup.run();
            }
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_unloaded_motion", timeoutTicks = 200)
    public static void unavailableChunksBlockActivationAndFreezeSavedMotion(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var anchor = new BlockPos(18000, 150, 18000);
        var ownerChunk = new ChunkPos(anchor);
        level.setChunkForced(ownerChunk.x, ownerChunk.z, true);
        level.getChunk(ownerChunk.x, ownerChunk.z);
        level.setBlock(anchor, SingularityContent.CONTROLLER.get().defaultBlockState(), 3);
        var machine = (SingularityBlockEntity) level.getBlockEntity(anchor);
        var player = operator(level, anchor, "MissingMotionChunk");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(level.areEntitiesLoaded(ownerChunk.toLong()),
                "Controller chunk entity storage must be ready before restoring saved bodies")).thenExecute(() -> {
            boolean previous = ModConfig.FORCE_LOAD_CHUNKS.get();
            try {
                ModConfig.FORCE_LOAD_CHUNKS.set(false);
                var absent = machine.getMotionChunks().stream().filter(chunk -> !level.hasChunk(chunk.x, chunk.z)).findFirst().orElseThrow();
                helper.assertTrue(!machine.motion().startForCollection(player) && !machine.motion().hasBodies()
                                && machine.motion().message().equals("unloaded"),
                        "Activation must reject unavailable swept chunks before converting any blocks");
                // Restore an owned moving structure as on controller/chunk reload. Only
                // the controller's chunk is loaded, so no motion may advance its clock.
                var tag = machine.motion().save();
                tag.putInt("mask", SingularityMotionGeometry.ALL_GROUPS);
                tag.putInt("mode", SingularityMotionState.RUNNING);
                tag.putLong("phase", 180);
                tag.putUUID("operator", player.getUUID());
                machine.motion().load(tag);
                int[] owned = machine.motion().portableCounts();
                machine.motion().serverTick();
                helper.assertTrue(machine.motion().mode() == SingularityMotionState.PAUSED && machine.motion().message().equals("unloaded"),
                        "Saved running motion must pause for actual missing chunks");
                helper.assertTrue(!machine.motion().startForCollection(player), "Missing chunks must also prevent a manual resume");
                advanceClock(level, 50); machine.motion().serverTick();
                helper.assertTrue(machine.motion().save().getLong("phase") == 180, "Unavailable running motion cannot accumulate phase");
                tag.putInt("mode", SingularityMotionState.DOCKING); tag.putLong("dockingElapsed", 17);
                machine.motion().load(tag); machine.motion().serverTick();
                helper.assertTrue(machine.motion().save().getBoolean("dockingFrozen") && machine.motion().message().equals("unloaded"),
                        "Saved docking must freeze when required chunks are unavailable");
                advanceClock(level, 100); machine.motion().serverTick();
                helper.assertTrue(machine.motion().save().getLong("dockingElapsed") == 17,
                        "Unavailable docking cannot advance its restoration clock");
                helper.assertTrue(Arrays.equals(owned, machine.motion().portableCounts()), "Missing chunks must preserve every owned material");
                helper.assertTrue(!level.hasChunk(absent.x, absent.z), "Motion checks must not synchronously load missing chunks");
                helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, anchor).isEmpty(), "Missing chunks cannot implicitly enable tickets");
                LogUtils.getLogger().info("SINGULARITY_UNLOADED_MOTION_PASS activationBlocked=true resumeBlocked=true runningPaused=true dockingFrozen=true conserved=true noSynchronousLoad=true");
            } finally {
                machine.motion().clear();
                level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
                level.setChunkForced(ownerChunk.x, ownerChunk.z, false);
                ModConfig.FORCE_LOAD_CHUNKS.set(previous);
            }
        }).thenSucceed();
    }

    private static ServerPlayer operator(ServerLevel level, BlockPos anchor, String name) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(anchor.getX() + .5, anchor.getY() + 1, anchor.getZ() - 2);
        return player;
    }
    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> onlinePlayers(ServerLevel level) throws Exception {
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        return (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
    }
    private static long bodyCount(ServerLevel level, BlockPos anchor) {
        return SingularityMotionWorld.bodies(level).stream()
                .filter(body -> body.configured() && !body.isRemoved() && body.controller().equals(anchor)).count();
    }
    private static void advance(ServerLevel level, SingularityBlockEntity machine) {
        advanceClock(level, 1); machine.serverTick();
    }
    private static void unload(ServerLevel level, ChunkPos chunk) {
        level.setChunkForced(chunk.x, chunk.z, false);
        // Level#getChunk installs a persistent UNKNOWN ticket. Remove the fixture's
        // direct load ticket as well; otherwise the forced-ticket toggle cannot
        // exercise the real unload path.
        level.getChunkSource().chunkMap.getDistanceManager().removeTicket(
                TicketType.UNKNOWN, chunk, net.minecraft.server.level.ChunkLevel.byStatus(ChunkStatus.FULL), chunk);
        level.getChunkSource().save(true);
        for (int attempt = 0; attempt < 10 && level.hasChunk(chunk.x, chunk.z); attempt++) {
            level.getChunkSource().tick(() -> true, true);
            forceProcessUnloads(level);
            level.getChunkSource().tick(() -> true, true);
        }
    }
    private static void forceProcessUnloads(ServerLevel level) {
        try {
            var process = level.getChunkSource().chunkMap.getClass().getDeclaredMethod(
                    "processUnloads", java.util.function.BooleanSupplier.class);
            process.setAccessible(true);
            process.invoke(level.getChunkSource().chunkMap, (java.util.function.BooleanSupplier) () -> true);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot exercise the real chunk unload path", error);
        }
    }
    private static void load(ServerLevel level, ChunkPos chunk) {
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunk(chunk.x, chunk.z);
        level.getChunkSource().tick(() -> true, true);
    }
    private static SingularityBlockEntity reload(ServerLevel level, SingularityBlockEntity machine, BlockPos anchor) {
        var saved = machine.saveWithFullMetadata(level.registryAccess());
        var state = machine.getBlockState();
        machine.motion().discardBodies();
        level.removeBlockEntity(anchor);
        var restored = (SingularityBlockEntity) BlockEntity.loadStatic(anchor, state, saved, level.registryAccess());
        level.setBlockEntity(restored);
        return restored;
    }
    private static void advanceClock(ServerLevel level, long ticks) {
        ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + ticks);
    }
}
