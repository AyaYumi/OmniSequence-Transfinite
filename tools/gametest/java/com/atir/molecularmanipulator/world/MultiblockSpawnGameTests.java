package com.atir.molecularmanipulator.world;

import appeng.blockentity.AEBaseBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MultiblockSpawnGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void threeStructuresBlockAllNaturalMobsAcrossTheirFullChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        int offset = (int) (level.getGameTime() % 100_000) * 256;
        var directions = new Direction[] {Direction.NORTH, Direction.WEST, Direction.SOUTH};
        for (int kind = 0; kind < 3; kind++) {
            var origin = new BlockPos(-70_009 - offset - kind * 160, 100, -8007);
            var layout = blueprint(kind, origin, directions[kind]);
            var expected = footprint(layout);
            loadChunks(level, expected);
            for (var entry : layout.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            var machine = level.getBlockEntity(origin);
            refresh(machine);
            helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, origin).equals(expected),
                    "Real formed structure must protect exactly its blueprint footprint: " + kind);
            verifyFootprint(helper, expected);
            var point = expected.iterator().next().getBlockAt(0, 250, 0);
            verifySpawnReasons(helper, point);

            // World-save/unload retains controller-owned chunk tickets and their protection.
            var saved = machine.saveWithFullMetadata(level.registryAccess());
            ((AEBaseBlockEntity) machine).onChunkUnloaded();
            level.removeBlockEntity(origin);
            helper.assertTrue(MultiblockChunkLoading.isOccupiedChunk(level, point), "Chunk unload keeps saved protection");
            var restored = BlockEntity.loadStatic(origin, level.getBlockState(origin), saved, level.registryAccess());
            helper.assertTrue(restored != null, "Controller must restore from its world NBT");
            level.setBlockEntity(restored);
            refresh(restored);
            helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, origin).equals(expected), "Reload restores the same area");

            var part = layout.entrySet().stream().filter(e -> !e.getKey().equals(origin) && !e.getValue().isAir())
                    .findFirst().orElseThrow();
            level.setBlock(part.getKey(), Blocks.AIR.defaultBlockState(), 3);
            refresh(restored);
            helper.assertTrue(!MultiblockChunkLoading.isOccupiedChunk(level, point), "Idle damaged structure releases protection");
            level.setBlock(part.getKey(), part.getValue(), 3);
            refresh(restored);
            helper.assertTrue(MultiblockChunkLoading.isOccupiedChunk(level, point), "Repair restores protection without AE power");
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!MultiblockChunkLoading.isOccupiedChunk(level, point), "Removing controller releases protection");
        }
        System.out.println("MULTIBLOCK_SPAWN_FOOTPRINT_PASS: three formed layouts, full-height chunks, all mob categories, allowed spawners/eggs/commands, unload/reload, damage/repair and removal");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void constructionOverlapAndStandaloneNexusUseTheCorrectOwnership(GameTestHelper helper) {
        var level = helper.getLevel();
        int offset = (int) (level.getGameTime() % 100_000) * 256;
        var player = FakePlayerFactory.getMinecraft(level);
        for (int kind = 0; kind < 3; kind++) {
            var origin = new BlockPos(80_003 + offset + kind * 160, 100, -9001);
            var layout = blueprint(kind, origin, Direction.EAST);
            var expected = footprint(layout);
            loadChunks(level, expected);
            level.setBlock(origin, layout.get(origin), 3);
            var machine = level.getBlockEntity(origin);
            helper.assertTrue(!MultiblockChunkLoading.isOccupiedChunk(level, origin), "Lone controller does not protect an unbuilt structure");
            if (machine instanceof MolecularCenterBlockEntity center) center.startBuild(player);
            else if (machine instanceof OmniComputationCoreBlockEntity omni) omni.startBuild(player);
            else if (machine instanceof MatterFabricationBlockEntity well) well.startBuild(player);
            // Exercise the tick's ownership update without executing player-owned construction:
            // Fake players are not listed as connected server players.
            MultiblockChunkLoading.maintain(machine);
            helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, origin).equals(expected), "Construction protects the target footprint: " + kind);
            level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!MultiblockChunkLoading.isOccupiedChunk(level, origin), "Removing construction controller releases protection");
        }

        var lower = new BlockPos(90_007 + offset, 90, 7001);
        var upper = lower.above(80);
        for (var origin : List.of(lower, upper)) {
            var layout = blueprint(2, origin, Direction.NORTH);
            loadChunks(level, footprint(layout));
            for (var entry : layout.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            refresh(level.getBlockEntity(origin));
        }
        level.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(MultiblockChunkLoading.isOccupiedChunk(level, lower), "An overlapping controller keeps the shared chunk protected");
        var well = (MatterFabricationBlockEntity) level.getBlockEntity(upper);
        well.startDismantle(player);
        MultiblockChunkLoading.maintain(well);
        helper.assertTrue(MultiblockChunkLoading.isOccupiedChunk(level, upper), "Dismantling remains protected");
        level.setBlock(upper, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!MultiblockChunkLoading.isOccupiedChunk(level, lower), "Last controller releases the shared chunk");
        level.setBlock(lower, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        var nexus = (OmniComputationCoreBlockEntity) level.getBlockEntity(lower);
        nexus.refreshStructureNow();
        MultiblockChunkLoading.maintain(nexus);
        helper.assertTrue(!MultiblockChunkLoading.isOccupiedChunk(level, lower), "Standalone nexus has no protected multiblock area");
        level.setBlock(lower, Blocks.AIR.defaultBlockState(), 3);
        System.out.println("MULTIBLOCK_SPAWN_OWNERSHIP_PASS: construction, dismantling, overlapping controllers and standalone nexus exclusion");
        helper.succeed();
    }

    private static void verifyFootprint(GameTestHelper helper, Set<ChunkPos> chunks) {
        var level = helper.getLevel();
        var types = List.of(EntityType.ZOMBIE, EntityType.BAT, EntityType.COW, EntityType.COD,
                EntityType.SQUID, EntityType.AXOLOTL, EntityType.VILLAGER);
        for (var chunk : chunks) for (int height : new int[] {level.getMinBuildHeight(), level.getMaxBuildHeight() - 1}) {
            for (int corner : new int[] {0, 15}) {
                var pos = chunk.getBlockAt(corner, height, corner);
                for (var type : types) helper.assertTrue(!placement(level, pos, type, MobSpawnType.NATURAL),
                        "Natural spawn must be denied at every occupied chunk edge/height: " + type + " " + pos);
            }
            for (var direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
                var outside = new ChunkPos(chunk.x + direction.getStepX(), chunk.z + direction.getStepZ());
                if (!chunks.contains(outside)) helper.assertTrue(placement(level, outside.getBlockAt(0, height, 0), EntityType.BAT, MobSpawnType.NATURAL),
                        "Adjacent unoccupied chunk must retain natural spawning");
            }
        }
        var other = level.getServer().getLevel(Level.NETHER);
        helper.assertTrue(other != null && placement(other, chunks.iterator().next().getBlockAt(0, 80, 0), EntityType.ZOMBIE, MobSpawnType.NATURAL),
                "Protection must remain dimension-specific");
    }

    private static void verifySpawnReasons(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        for (var reason : MobSpawnType.values()) {
            boolean denied = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
                    || reason == MobSpawnType.PATROL || reason == MobSpawnType.REINFORCEMENT;
            helper.assertTrue(placement(level, pos, EntityType.BAT, reason) != denied, "Placement policy for " + reason);
            var mob = EntityType.BAT.create(level);
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), reason, null);
            helper.assertTrue(mob.isSpawnCancelled() == denied, "Finalize policy for " + reason);
            if (denied) helper.assertTrue(!level.addFreshEntity(mob), "Cancelled natural mob must never enter the level");
            else if (reason == MobSpawnType.COMMAND) {
                helper.assertTrue(level.addFreshEntity(mob), "Command mob may enter protected chunk");
                helper.assertTrue(!mob.isRemoved(), "Existing mobs are not removed by protection");
                mob.discard();
            }
        }
    }

    private static boolean placement(ServerLevel level, BlockPos pos, EntityType<?> type, MobSpawnType reason) {
        return EventHooks.checkSpawnPlacements(type, level, reason, pos, level.random, true);
    }

    private static void refresh(BlockEntity machine) {
        if (machine instanceof MolecularCenterBlockEntity center) center.refreshStructure();
        else if (machine instanceof OmniComputationCoreBlockEntity omni) omni.refreshStructureNow();
        else if (machine instanceof MatterFabricationBlockEntity well) well.refreshStructure();
        else throw new AssertionError("Missing multiblock controller");
    }

    private static Map<BlockPos, BlockState> blueprint(int kind, BlockPos origin, Direction facing) {
        var result = new LinkedHashMap<BlockPos, BlockState>();
        if (kind == 0) {
            for (var part : MolecularCenterStructure.parts()) result.put(MolecularCenterStructure.worldPos(origin, facing, part),
                    MolecularCenterStructure.isController(part) ? ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState()
                            : MolecularCenterStructure.partState(part.partType()));
        } else if (kind == 1) {
            for (var part : OmniComputationStructure.parts()) result.put(OmniComputationStructure.worldPos(origin, facing, part),
                    OmniComputationStructure.block(part.type()).defaultBlockState());
        } else {
            for (var part : MatterFabricationStructure.parts()) result.put(MatterFabricationStructure.worldPos(origin, facing, part),
                    MatterFabricationStructure.partState(part.type()));
        }
        var controller = result.remove(origin).setValue(HorizontalDirectionalBlock.FACING, facing);
        result.put(origin, controller); // The controller is placed last, after all other blocks.
        return result;
    }

    private static Set<ChunkPos> footprint(Map<BlockPos, BlockState> parts) {
        int minX = parts.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow() >> 4;
        int maxX = parts.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow() >> 4;
        int minZ = parts.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow() >> 4;
        int maxZ = parts.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow() >> 4;
        var chunks = new HashSet<ChunkPos>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) chunks.add(new ChunkPos(x, z));
        return chunks;
    }

    private static void loadChunks(ServerLevel level, Set<ChunkPos> chunks) {
        for (var chunk : chunks) level.getChunk(chunk.x, chunk.z);
    }
}
