package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.GridHelper;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional tickets are not an activation requirement; missing chunks pause ownership, not the session. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityMotionRegressionGameTests {
    private static final TicketType<ChunkPos> FULL_FIXTURE = TicketType.create(
            "omnisequence_regression_full", Comparator.comparingLong(ChunkPos::toLong));

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 800)
    public static void collectionRunsWithTicketsDisabledAndResumesAfterAnActualChunkUnload(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var machine = fixture.hub(fixture.origin, true);
        var chunks = Set.copyOf(machine.getMotionChunks());
        for (var chunk : chunks) fixture.force(chunk);
        var power = Arrays.stream(Direction.values()).map(fixture.origin::relative)
                .filter(pos -> SingularityStructure.partAt(fixture.origin, Direction.NORTH, pos) == null).findFirst().orElseThrow();
        fixture.place(power, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var drive = fixture.place(fixture.origin.east(100), AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var player = fixture.player("motion-regression", fixture.origin);
        fixture.onCleanup(() -> {
            for (var chunk : chunks) fixture.level.getChunkSource().chunkMap.getDistanceManager()
                    .removeTicket(FULL_FIXTURE, chunk, 33, chunk);
        });
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.getMainNode().getNode() != null
                        && drive.getMainNode().getNode() != null, "Real grid nodes initialize"))
                .thenExecute(() -> GridHelper.createConnection(machine.getMainNode().getNode(), drive.getMainNode().getNode()))
                .thenWaitUntil(() -> helper.assertTrue(machine.formed() && machine.networkOnline() && drive.getMainNode().isActive()
                                && drive.getOriginalCellInventory(0) != null
                                && chunks.stream().allMatch(chunk -> fixture.level.areEntitiesLoaded(chunk.toLong())),
                        "The actual blueprint, powered grid, finite disk and entity storage are ready"))
                .thenExecute(() -> {
                    var level = fixture.level;
                    boolean force = ModConfig.FORCE_LOAD_CHUNKS.get();
                    var tags = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get());
                    var blacklist = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
                    int batch = ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.get();
                    int interval = ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get();
                    long time = level.getGameTime();
                    try {
                        // All config/clock changes and manual machine ticks are contained in one callback.
                        ModConfig.FORCE_LOAD_CHUNKS.set(false);
                        ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of("minecraft:oak_logs"));
                        ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of());
                        ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(1);
                        ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(2);
                        MultiblockChunkLoading.maintain(machine);
                        helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, fixture.origin).isEmpty(), "Disabled option releases Hub tickets");
                        machine.toggleCollection(player); // Public production lifecycle, not direct mask/formed writes.
                        helper.assertTrue(machine.collectionActive() && machine.motion().mode() == SingularityMotionState.RUNNING,
                                "Already-loaded geometry starts collection with force-loading disabled: " + machine.motion().message());
                        advance(level, machine, 80);
                        helper.assertTrue(machine.collectionActive() && machine.motion().mode() == SingularityMotionState.RUNNING
                                        && bodyCount(level, fixture.origin) == SingularityMotionGeometry.GROUP_COUNT,
                                "The real collection session runs with exactly eleven derived bodies");
                        helper.assertTrue(!drive.getOriginalCellInventory(0).getAvailableStacks().isEmpty(),
                                "Actual collection cycles deliver registered tag resources into a finite ME disk");
                        ModConfig.FORCE_LOAD_CHUNKS.set(true);
                        MultiblockChunkLoading.maintain(machine);
                        helper.assertTrue(!MultiblockChunkLoading.ownedChunks(level, fixture.origin).isEmpty(), "Acquire real controller-owned tickets first");
                        ModConfig.FORCE_LOAD_CHUNKS.set(false);
                        MultiblockChunkLoading.maintain(machine);
                        long runningPhase = machine.motion().save().getLong("phase");
                        advance(level, machine, 10);
                        helper.assertTrue(machine.motion().mode() == SingularityMotionState.RUNNING
                                        && machine.motion().save().getLong("phase") > runningPhase
                                        && MultiblockChunkLoading.ownedChunks(level, fixture.origin).isEmpty(),
                                "Turning tickets off while moving releases them without pausing already-loaded motion");

                        // FULL (33) tickets do not propagate FULL status into a neighbor. Only body/owner
                        // chunks stay forced/entity-ticking, so a distant swept chunk can really unload.
                        var bodyChunks = new HashSet<ChunkPos>();
                        bodyChunks.add(new ChunkPos(fixture.origin));
                        for (var body : SingularityMotionWorld.bodies(level))
                            if (body.configured() && !body.isRemoved() && body.controller().equals(fixture.origin))
                                bodyChunks.add(new ChunkPos(body.blockPosition()));
                        for (var chunk : chunks) {
                            level.getChunkSource().chunkMap.getDistanceManager().addTicket(FULL_FIXTURE, chunk, 33, chunk);
                            level.setChunkForced(chunk.x, chunk.z, bodyChunks.contains(chunk));
                        }
                        var dropped = chunks.stream().filter(chunk -> !bodyChunks.contains(chunk))
                                .max(Comparator.comparingInt(chunk -> distance(chunk, bodyChunks))).orElseThrow();
                        helper.assertTrue(distance(dropped, bodyChunks) > 1, "The isolated chunk lies beyond forced-neighbor propagation");
                        level.getChunkSource().chunkMap.getDistanceManager().removeTicket(FULL_FIXTURE, dropped, 33, dropped);
                        unload(level, dropped);
                        helper.assertTrue(!level.hasChunk(dropped.x, dropped.z)
                                        && chunks.stream().filter(chunk -> !chunk.equals(dropped)).allMatch(chunk -> level.hasChunk(chunk.x, chunk.z)),
                                "Exactly one previously loaded swept chunk really unloads");
                        int[] owned = machine.motion().portableCounts();
                        advance(level, machine, 1);
                        helper.assertTrue(machine.collectionActive() && machine.motion().mode() == SingularityMotionState.PAUSED
                                        && machine.motion().message().equals("unloaded"),
                                "Missing chunks pause the existing collection session instead of silently docking/cancelling it");
                        long frozen = machine.motion().save().getLong("phase");
                        advance(level, machine, 40);
                        helper.assertTrue(machine.collectionActive() && machine.motion().save().getLong("phase") == frozen
                                        && Arrays.equals(owned, machine.motion().portableCounts())
                                        && bodyCount(level, fixture.origin) == SingularityMotionGeometry.GROUP_COUNT
                                        && !level.hasChunk(dropped.x, dropped.z),
                                "A full inspection interval preserves phase, bodies and every material, without synchronous chunk loading");
                        fixture.force(dropped);
                        level.getChunkSource().tick(() -> true, true);
                        advance(level, machine, 3);
                        helper.assertTrue(machine.collectionActive() && machine.motion().mode() == SingularityMotionState.RUNNING
                                        && machine.motion().save().getLong("phase") > frozen
                                        && Arrays.equals(owned, machine.motion().portableCounts()),
                                "Reloading the actual chunk resumes the same session automatically");
                        var legacyPause = machine.motion().save();
                        legacyPause.putInt("mode", SingularityMotionState.PAUSED);
                        legacyPause.putString("message", "chunks_required");
                        machine.motion().load(legacyPause);
                        machine.motion().serverTick();
                        helper.assertTrue(machine.motion().mode() == SingularityMotionState.RUNNING,
                                "An old chunks_required save also resumes while force-loading remains disabled");
                        machine.toggleCollection(player);
                        helper.assertTrue(!machine.collectionActive() && machine.motion().mode() == SingularityMotionState.DOCKING,
                                "The public stop action begins docking without forced tickets");
                        // Advance to a completed, ten-tick-aligned docking pose without a thousand expensive scans.
                        clock(level, level.getGameTime() + 1_000 - Math.floorMod(level.getGameTime(), 10));
                        machine.serverTick();
                        helper.assertTrue(!machine.motion().hasBodies() && bodyCount(level, fixture.origin) == 0
                                        && SingularityStructure.inspect(level, fixture.origin, Direction.NORTH).formed()
                                        && Arrays.stream(machine.motion().portableCounts()).sum() == 0,
                                "Docking restores every authored block exactly once and relinquishes material ownership");
                        helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, fixture.origin).isEmpty(),
                                "Neither recovery nor docking may secretly reacquire controller tickets");
                    } finally {
                        clock(level, time);
                        ModConfig.FORCE_LOAD_CHUNKS.set(force);
                        ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(tags);
                        ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(blacklist);
                        ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(batch);
                        ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(interval);
                    }
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void missingChunksRejectActivationAndFreezeRestoredRunningAndDockingClocks(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var machine = fixture.hub(fixture.origin, false);
        var player = fixture.player("saved-motion", fixture.origin);
        var ownerChunk = new ChunkPos(fixture.origin);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(fixture.level.areEntitiesLoaded(ownerChunk.toLong()),
                "Owner entity storage is ready before saved bodies are restored")).thenExecute(() -> {
            boolean force = ModConfig.FORCE_LOAD_CHUNKS.get();
            long time = fixture.level.getGameTime();
            try {
                ModConfig.FORCE_LOAD_CHUNKS.set(false);
                MultiblockChunkLoading.maintain(machine);
                var absent = machine.getMotionChunks().stream().filter(chunk -> !fixture.level.hasChunk(chunk.x, chunk.z)).findFirst().orElseThrow();
                helper.assertTrue(!machine.motion().startForCollection(player) && !machine.motion().hasBodies()
                                && machine.motion().message().equals("unloaded"),
                        "Missing swept chunks reject activation before any conversion of blocks");
                // Native save restoration, with ownership already recorded as it is after a controller reload.
                var tag = machine.motion().save();
                tag.putInt("mask", SingularityMotionGeometry.ALL_GROUPS);
                tag.putInt("mode", SingularityMotionState.RUNNING);
                tag.putUUID("operator", player.getUUID());
                tag.putLong("phase", 180);
                machine.motion().load(tag);
                int[] owned = machine.motion().portableCounts();
                machine.motion().serverTick();
                helper.assertTrue(machine.motion().mode() == SingularityMotionState.PAUSED && machine.motion().message().equals("unloaded")
                                && !machine.motion().startForCollection(player),
                        "Restored running motion pauses and manual resume is rejected while chunks are absent");
                clock(fixture.level, fixture.level.getGameTime() + 50);
                machine.motion().serverTick();
                helper.assertTrue(machine.motion().save().getLong("phase") == 180, "Missing chunks cannot accumulate running phase");
                tag.putInt("mode", SingularityMotionState.DOCKING);
                tag.putLong("dockingElapsed", 17);
                machine.motion().load(tag);
                machine.motion().serverTick();
                clock(fixture.level, fixture.level.getGameTime() + 100);
                machine.motion().serverTick();
                helper.assertTrue(machine.motion().save().getBoolean("dockingFrozen")
                                && machine.motion().save().getLong("dockingElapsed") == 17
                                && Arrays.equals(owned, machine.motion().portableCounts()),
                        "Restored docking freezes its exact restoration clock and retains every owned material");
                helper.assertTrue(!fixture.level.hasChunk(absent.x, absent.z)
                                && MultiblockChunkLoading.ownedChunks(fixture.level, fixture.origin).isEmpty(),
                        "Motion checks neither synchronously load missing chunks nor enable tickets");
            } finally {
                clock(fixture.level, time);
                ModConfig.FORCE_LOAD_CHUNKS.set(force);
            }
        }).thenSucceed();
    }

    private static int distance(ChunkPos chunk, Set<ChunkPos> others) {
        return others.stream().mapToInt(other -> Math.max(Math.abs(chunk.x - other.x), Math.abs(chunk.z - other.z))).min().orElse(0);
    }

    private static long bodyCount(ServerLevel level, net.minecraft.core.BlockPos controller) {
        return SingularityMotionWorld.bodies(level).stream()
                .filter(body -> body.configured() && !body.isRemoved() && body.controller().equals(controller)).count();
    }

    private static void advance(ServerLevel level, SingularityBlockEntity machine, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            clock(level, level.getGameTime() + 1);
            machine.serverTick();
        }
    }

    private static void clock(ServerLevel level, long time) { ((ServerLevelData) level.getLevelData()).setGameTime(time); }

    private static void unload(ServerLevel level, ChunkPos chunk) {
        level.setChunkForced(chunk.x, chunk.z, false);
        // Level#getChunk leaves an UNKNOWN ticket; releasing just the forced ticket is not an unload.
        level.getChunkSource().chunkMap.getDistanceManager().removeTicket(
                TicketType.UNKNOWN, chunk, ChunkLevel.byStatus(ChunkStatus.FULL), chunk);
        level.getChunkSource().save(true);
        try {
            var process = level.getChunkSource().chunkMap.getClass().getDeclaredMethod("processUnloads", BooleanSupplier.class);
            process.setAccessible(true);
            for (int attempt = 0; attempt < 20 && level.hasChunk(chunk.x, chunk.z); attempt++) {
                level.getChunkSource().tick(() -> true, true);
                process.invoke(level.getChunkSource().chunkMap, (BooleanSupplier) () -> true);
                level.getChunkSource().tick(() -> true, true);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot exercise the actual chunk unload path", error);
        }
    }
}
