package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.entity.SingularityAssemblyEntity;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityMotionGameTests {
    private static final BlockPos ANCHOR = new BlockPos(2400, 120, 2400);
    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_motion_migration", timeoutTicks = 1000)
    public static void oldMotionReturnsFixedJointsWithoutMaterialLoss(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var anchor = new BlockPos(8192, 146, 5632);
        var chunks = SingularityStructure.chunks(anchor, Direction.NORTH);
        for (var chunk : chunks) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MotionMigration"));
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        players.put(player.getUUID(), player);
        SingularityBlockEntity machine = null;
        try {
            for (var part : SingularityStructure.parts()) level.setBlock(SingularityStructure.worldPos(anchor, Direction.NORTH, part), SingularityStructure.state(part, Direction.NORTH), 2);
            machine = (SingularityBlockEntity) level.getBlockEntity(anchor);
            player.setPos(anchor.getX() + .5, anchor.getY() + 1, anchor.getZ() - 2);
            player.setGameMode(GameType.SURVIVAL); machine.setRecoveryOwner(player);
            var old = SingularityMotionGeometry.groups(4, 2).stream().flatMap(g -> g.parts().stream()).toList();
            var fixed = old.stream().filter(p -> SingularityMotionGeometry.groupOf(p) < 0).toList();
            var tag = machine.motion().save(); tag.putInt("version", 2); tag.putInt("mask", SingularityMotionGeometry.ALL_GROUPS);
            tag.putInt("mode", SingularityMotionState.RUNNING); tag.putUUID("operator", player.getUUID()); tag.putLong("phase", 180);
            for (var part : old) level.setBlock(machine.worldPos(part), Blocks.AIR.defaultBlockState(), 2);
            machine.motion().load(tag);
            int[] before = machine.motion().portableCounts();
            var conflict = machine.worldPos(fixed.getFirst()); level.setBlock(conflict, Blocks.STONE.defaultBlockState(), 2);
            stepMotion(machine);
            helper.assertTrue(machine.motion().save().getInt("version") == 2 && Arrays.equals(before, machine.motion().portableCounts()), "Blocked migration must retain old ownership");
            level.setBlock(conflict, Blocks.AIR.defaultBlockState(), 2);
            var denied = machine.worldPos(fixed.getLast());
            Consumer<BlockEvent.EntityPlaceEvent> deny = e -> { if (e.getPos().equals(denied)) e.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(deny);
            try {
                stepMotion(machine);
                for (var part : fixed) helper.assertTrue(level.getBlockState(machine.worldPos(part)).isAir(), "Denied migration must roll back the entire platform");
                helper.assertTrue(Arrays.equals(before, machine.motion().portableCounts()), "Denied migration cannot lose material");
            } finally { NeoForge.EVENT_BUS.unregister(deny); }
            var saved = machine.saveWithFullMetadata(level.registryAccess()); level.removeBlockEntity(anchor);
            machine = (SingularityBlockEntity) BlockEntity.loadStatic(anchor, SingularityContent.CONTROLLER.get().defaultBlockState(), saved, level.registryAccess());
            level.setBlockEntity(machine); stepMotion(machine);
            int[] accounted = machine.motion().portableCounts();
            for (var part : fixed) {
                helper.assertTrue(SingularityStructure.matches(level.getBlockState(machine.worldPos(part)), part, Direction.NORTH), "Old access joints must return to their stationary positions");
                accounted[part.type().ordinal()]++;
            }
            helper.assertTrue(Arrays.equals(before, accounted), "Migration must conserve every material exactly once");
            helper.assertTrue(machine.motion().save().getInt("version") == 3 && machine.motion().mode() == SingularityMotionState.RUNNING, "Successful migration must resume corrected bodies");
            var after = machine.motion().portableCounts(); stepMotion(machine);
            helper.assertTrue(Arrays.equals(after, machine.motion().portableCounts()), "Migration must not restore a second copy");
            helper.assertTrue(SingularityStructure.inspect(level, anchor, Direction.NORTH, machine.motion()::owns).formed(), "Migrated structure must still form");
            System.out.println("SINGULARITY_MOTION_MIGRATION_PASS fixed=" + fixed.size() + " blocked=true permissionRollback=true reload=true conserved=true");
            helper.succeed();
        } finally {
            if (machine != null) machine.motion().clear(); level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
            players.remove(player.getUUID()); for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_motion", timeoutTicks = 1000)
    public static void physicalCarriageDockingAndOwnership(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var footprint = com.atir.molecularmanipulator.world.MultiblockChunkLoading.rectangle(
                SingularityStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(-64, 0, -64)),
                SingularityStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(64, 128, 64)));
        for (var chunk : footprint) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        // A central controller is 25 blocks higher within its blueprint. Clear the
        // complete disposable motion volume so old fixtures and terrain cannot
        // become accidental obstacles when the test keeps the same world anchor.
        for (var probe : BlockPos.betweenClosed(SingularityStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(-64, 0, -64)),
                SingularityStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(64, 128, 64))))
            if (!level.getBlockState(probe).isAir()) level.setBlock(probe, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(ANCHOR, Blocks.AIR.defaultBlockState(), 3);
        for (var part : SingularityStructure.parts(2)) level.setBlock(SingularityStructure.worldPos(ANCHOR, Direction.NORTH, part, 2), Blocks.AIR.defaultBlockState(), 2);
        for (var part : SingularityStructure.parts()) level.setBlock(SingularityStructure.worldPos(ANCHOR, Direction.NORTH, part), SingularityStructure.state(part, Direction.NORTH), 2);
        for (var air : SingularityStructure.requiredAir()) level.setBlock(SingularityStructure.worldPos(ANCHOR, Direction.NORTH, air), Blocks.AIR.defaultBlockState(), 2);
        var machine = (SingularityBlockEntity) level.getBlockEntity(ANCHOR);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("cc6f0f85-c3f9-4a09-b398-ecadbf66fabc"), "SingularityRider"));
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        players.put(player.getUUID(), player);
        try {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); nearController(player);
            machine.requestInspection(player); machine.serverTick();
            helper.assertTrue(machine.formed(), "Motion fixture must form before conversion");
            machine.motion().startForCollection(player);
            helper.assertTrue(machine.motion().hasBodies(), "Activation must convert complete groups");
            helper.assertTrue(bodies(level).size() == 11, "Three rings and eight towers must be separate entities");
            assertPositionUpdatesPreserveBounds(helper, bodies(level));
            for (var group : SingularityMotionGeometry.groups()) for (var part : group.parts())
                helper.assertTrue(level.getBlockState(SingularityStructure.worldPos(ANCHOR, Direction.NORTH, part)).isAir(), "Moving geometry cannot leave duplicate static blocks behind");
            var ring = bodies(level).stream().filter(b -> b.groupId() == 0).findFirst().orElseThrow();
            var localFeet = new Vec3(.5, 37, 28.5);
            player.setPos(ring.toWorld(localFeet, ring.pose(0))); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
            helper.assertTrue(!level.getEntityCollisions(player, player.getBoundingBox().move(0, -.1, 0)).isEmpty(), "Vanilla entity collision must see the ring surface");
            player.move(MoverType.SELF, new Vec3(0, -.25, 0));
            helper.assertTrue(Math.abs(player.getY() - ring.toWorld(localFeet, ring.pose(0)).y) < .01, "Gravity must stop on the moving ring");
            var start = player.position();
            for (int tick = 0; tick < 160; tick++) {
                advance(level); stepMotion(machine); bodies(level).forEach(SingularityAssemblyEntity::tick);
                SingularityMotionWorld.carry(new EntityTickEvent.Pre(player));
            }
            helper.assertTrue(machine.motion().mode() == SingularityMotionState.RUNNING, "Clear motion path must stay active: " + machine.motion().message());
            assertPositionUpdatesPreserveBounds(helper, bodies(level));
            helper.assertTrue(start.distanceTo(player.position()) > 3, "Standing players must travel with the rotating ring");
            helper.assertTrue(player.position().distanceTo(ring.toWorld(localFeet, ring.pose(0))) < .2, "Ring carriage must not accumulate slip");
            var tower = bodies(level).stream().filter(b -> b.groupId() == 3).findFirst().orElseThrow();
            var towerFeet = new Vec3(SingularityMotionGeometry.towerX(0) + 3.5, 41.5, SingularityMotionGeometry.towerZ(0) + .5);
            player.setPos(tower.toWorld(towerFeet, tower.pose(0))); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
            for (int tick = 0; tick < 200; tick++) {
                advance(level); stepMotion(machine); bodies(level).forEach(SingularityAssemblyEntity::tick);
                SingularityMotionWorld.carry(new EntityTickEvent.Pre(player));
            }
            helper.assertTrue(Math.abs(player.getY() - tower.toWorld(towerFeet, tower.pose(0)).y) < .08, "Ascending and descending towers must carry the rider");
            player.setDeltaMovement(0, .42, 0);
            var beforeJump = player.position(); advance(level);
            SingularityMotionWorld.carry(new EntityTickEvent.Pre(player));
            helper.assertTrue(player.position().equals(beforeJump), "Jumping away must release the rider from carriage");
            player.setDeltaMovement(Vec3.ZERO); nearController(player);
            var obstacle = BlockPos.containing(ring.toWorld(new Vec3(.5, 36.5, 28.5), ring.pose(0)));
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 2);
            for (int tick = 0; tick < 10; tick++) { advance(level); stepMotion(machine); }
            helper.assertTrue(machine.motion().mode() == SingularityMotionState.PAUSED && machine.motion().message().equals("obstacle"), "Foreign blocks must stop running motion");
            helper.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE), "Obstacle detection cannot overwrite foreign blocks");
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 2); machine.motion().startForCollection(player);
            int[] counts = machine.motion().portableCounts();
            machine = reload(level, machine); stepMotion(machine);
            helper.assertTrue(Arrays.equals(counts, machine.motion().portableCounts()) && bodies(level).size() == 11, "Reload must preserve every owned material and recreate exactly eleven bodies");
            nearController(player); machine.motion().dock(player, false);
            for (int tick = 0; tick < 10; tick++) { advance(level); stepMotion(machine); }
            ring = bodies(level).stream().filter(b -> b.groupId() == 0).findFirst().orElseThrow();
            obstacle = BlockPos.containing(ring.toWorld(new Vec3(.5, 36.5, 28.5), ring.pose(0)));
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 2);
            for (int tick = 0; tick < 10; tick++) { advance(level); stepMotion(machine); }
            helper.assertTrue(machine.motion().message().equals("dock_obstacle"), "Docking must freeze when obstructed");
            var frozen = ring.pose(0);
            for (int tick = 0; tick < 30; tick++) { advance(level); stepMotion(machine); }
            helper.assertTrue(ring.pose(0).equals(frozen), "An obstructed dock must retain its exact pose");
            machine = reload(level, machine); stepMotion(machine);
            ring = bodies(level).stream().filter(b -> b.groupId() == 0).findFirst().orElseThrow();
            helper.assertTrue(ring.pose(0).equals(frozen), "Frozen docking must survive save/reload without a jump");
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 2);
            var deniedPart = SingularityMotionGeometry.group(0).parts().get(20);
            var deniedPos = SingularityStructure.worldPos(ANCHOR, Direction.NORTH, deniedPart);
            Consumer<BlockEvent.EntityPlaceEvent> deny = event -> { if (event.getPos().equals(deniedPos)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(deny);
            try {
                for (int tick = 0; tick < 250; tick++) { advance(level); stepMotion(machine); }
                helper.assertTrue(machine.motion().hasBodies() && machine.motion().message().equals("protected"), "Denied docking must retain material ownership");
                for (var group : SingularityMotionGeometry.groups()) for (var part : group.parts())
                    helper.assertTrue(level.getBlockState(SingularityStructure.worldPos(ANCHOR, Direction.NORTH, part)).isAir(), "Denied docking must roll back all earlier placements");
            } finally { NeoForge.EVENT_BUS.unregister(deny); }
            for (int tick = 0; tick < 1000 && machine.motion().hasBodies(); tick++) { advance(level); stepMotion(machine); }
            helper.assertTrue(!machine.motion().hasBodies() && bodies(level).isEmpty(), "Docking must remove derived bodies");
            helper.assertTrue(SingularityStructure.inspect(level, ANCHOR, Direction.NORTH).formed(), "Docking must restore the original blocks and orientations exactly");
            machine.requestInspection(player); machine.serverTick(); machine.motion().startForCollection(player);
            helper.assertTrue(machine.motion().hasBodies(), "Second activation must own materials before removal");
            for (var old : level.getEntitiesOfClass(ItemEntity.class, new AABB(ANCHOR).inflate(4))) old.discard();
            level.destroyBlock(ANCHOR, true, player);
            bodies(level).forEach(SingularityAssemblyEntity::tick);
            helper.assertTrue(bodies(level).isEmpty(), "Removing the controller must remove all derived bodies");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(ANCHOR).inflate(4));
            helper.assertTrue(drops.size() == 1, "Breaking an active controller must pack owned materials in one recoverable item");
            var packed = drops.getFirst().getItem().copy(); drops.getFirst().discard();
            helper.assertTrue(packed.is(SingularityContent.CONTROLLER.get().asItem()) && packed.getCount() == 1, "Packed drop must contain exactly one controller");
            level.setBlock(ANCHOR, SingularityContent.CONTROLLER.get().defaultBlockState(), 3);
            machine = (SingularityBlockEntity) level.getBlockEntity(ANCHOR);
            machine.loadWithComponents(packed.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(), level.registryAccess());
            helper.assertTrue(!machine.motion().hasBodies() && Arrays.equals(counts, machine.motion().portableCounts()), "Replacing a packed controller must restore all owned materials without duplicating geometry");
            machine = reload(level, machine); machine.setRecoveryOwner(player);
            int[] recovered = new int[counts.length];
            for (int tick = 0; tick < 1000 && (machine.motion().hasPortable() || !machine.getInternalInventory().isEmpty()); tick++) {
                machine.serverTick();
                for (var stack : player.getInventory().items) if (!stack.isEmpty()) {
                    for (var type : SingularityStructure.Type.values()) if (stack.is(SingularityStructure.block(type).asItem())) recovered[type.ordinal()] += stack.getCount();
                }
                player.getInventory().clearContent();
            }
            helper.assertTrue(Arrays.equals(counts, recovered), "Portable recovery must return every material exactly once after another reload");
            System.out.println("SINGULARITY_MOTION_PASS bodies=11 ringCarriage=true towerCarriage=true jump=true collision=true resume=true docking=true obstacleFreeze=true permissionRollback=true packedRecovery=true");
            helper.succeed();
        } finally {
            var existing = level.getBlockEntity(ANCHOR);
            if (existing instanceof SingularityBlockEntity controller) { controller.clearContent(); controller.motion().discardBodies(); }
            level.setBlock(ANCHOR, Blocks.AIR.defaultBlockState(), 3); players.remove(player.getUUID());
            for (var chunk : footprint) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
    private static SingularityBlockEntity reload(ServerLevel level, SingularityBlockEntity machine) {
        var saved = machine.saveWithFullMetadata(level.registryAccess()); machine.motion().discardBodies(); level.removeBlockEntity(ANCHOR);
        var restored = (SingularityBlockEntity) BlockEntity.loadStatic(ANCHOR, SingularityContent.CONTROLLER.get().defaultBlockState(), saved, level.registryAccess());
        level.setBlockEntity(restored); return restored;
    }
    private static List<SingularityAssemblyEntity> bodies(ServerLevel level) { return SingularityMotionWorld.bodies(level).stream().filter(b -> b.configured() && b.controller().equals(ANCHOR) && !b.isRemoved()).toList(); }
    private static void assertPositionUpdatesPreserveBounds(GameTestHelper helper, List<SingularityAssemblyEntity> bodies) {
        for (var body : bodies) {
            var expected = body.getBoundingBox();
            body.setPos(body.position());
            helper.assertTrue(body.getBoundingBox().equals(expected), "setPos must retain physical bounds before another tick: group=" + body.groupId());
            body.lerpTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot(), 3);
            helper.assertTrue(body.getBoundingBox().equals(expected), "Position packet interpolation must not shrink rings or towers: group=" + body.groupId());
        }
    }
    private static void nearController(ServerPlayer player) { player.setPos(ANCHOR.getX() + .5, ANCHOR.getY() + 1, ANCHOR.getZ() - 2); }
    private static void advance(ServerLevel level) { ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1); }
    // Exercise assembly geometry directly; production starts/stops it through collection.
    private static void stepMotion(SingularityBlockEntity machine) {
        machine.motion().serverTick();
    }
}
