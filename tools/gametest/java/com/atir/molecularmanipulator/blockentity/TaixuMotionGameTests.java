package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.entity.TaixuAssemblyEntity;
import com.atir.molecularmanipulator.registry.TaixuContent;
import com.atir.molecularmanipulator.world.TaixuMotionWorld;
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
public final class TaixuMotionGameTests {
    private static final BlockPos ANCHOR = new BlockPos(2400, 120, 2400);
    @GameTest(template = "multiblock_dismantle_empty", batch = "taixu_motion", timeoutTicks = 1000)
    public static void physicalCarriageDockingAndOwnership(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var footprint = com.atir.molecularmanipulator.world.MultiblockChunkLoading.rectangle(
                TaixuStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(-64, 0, -64)),
                TaixuStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(64, 128, 64)));
        for (var chunk : footprint) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        // A central controller is 25 blocks higher within its blueprint. Clear the
        // complete disposable motion volume so old fixtures and terrain cannot
        // become accidental obstacles when the test keeps the same world anchor.
        for (var probe : BlockPos.betweenClosed(TaixuStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(-64, 0, -64)),
                TaixuStructure.worldPos(ANCHOR, Direction.NORTH, new BlockPos(64, 128, 64))))
            if (!level.getBlockState(probe).isAir()) level.setBlock(probe, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(ANCHOR, Blocks.AIR.defaultBlockState(), 3);
        for (var part : TaixuStructure.parts(2)) level.setBlock(TaixuStructure.worldPos(ANCHOR, Direction.NORTH, part, 2), Blocks.AIR.defaultBlockState(), 2);
        for (var part : TaixuStructure.parts()) level.setBlock(TaixuStructure.worldPos(ANCHOR, Direction.NORTH, part), TaixuStructure.state(part, Direction.NORTH), 2);
        for (var air : TaixuStructure.requiredAir()) level.setBlock(TaixuStructure.worldPos(ANCHOR, Direction.NORTH, air), Blocks.AIR.defaultBlockState(), 2);
        var machine = (TaixuBlockEntity) level.getBlockEntity(ANCHOR);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("cc6f0f85-c3f9-4a09-b398-ecadbf66fabc"), "TaixuRider"));
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        players.put(player.getUUID(), player);
        try {
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); nearController(player);
            machine.requestInspection(player); machine.serverTick();
            helper.assertTrue(machine.formed(), "Motion fixture must form before conversion");
            machine.motion().toggle(player);
            helper.assertTrue(machine.motion().hasBodies(), "Activation must convert complete groups");
            helper.assertTrue(bodies(level).size() == 11, "Three rings and eight towers must be separate entities");
            assertPositionUpdatesPreserveBounds(helper, bodies(level));
            for (var group : TaixuMotionGeometry.groups()) for (var part : group.parts())
                helper.assertTrue(level.getBlockState(TaixuStructure.worldPos(ANCHOR, Direction.NORTH, part)).isAir(), "Moving geometry cannot leave duplicate static blocks behind");
            var ring = bodies(level).stream().filter(b -> b.groupId() == 0).findFirst().orElseThrow();
            var localFeet = new Vec3(.5, 37, 28.5);
            player.setPos(ring.toWorld(localFeet, ring.pose(0))); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
            helper.assertTrue(!level.getEntityCollisions(player, player.getBoundingBox().move(0, -.1, 0)).isEmpty(), "Vanilla entity collision must see the ring surface");
            player.move(MoverType.SELF, new Vec3(0, -.25, 0));
            helper.assertTrue(Math.abs(player.getY() - ring.toWorld(localFeet, ring.pose(0)).y) < .01, "Gravity must stop on the moving ring");
            var start = player.position();
            for (int tick = 0; tick < 160; tick++) {
                advance(level); machine.serverTick(); bodies(level).forEach(TaixuAssemblyEntity::tick);
                TaixuMotionWorld.carry(new EntityTickEvent.Pre(player));
            }
            helper.assertTrue(machine.motion().mode() == TaixuMotionState.RUNNING, "Clear motion path must stay active: " + machine.motion().message());
            assertPositionUpdatesPreserveBounds(helper, bodies(level));
            helper.assertTrue(start.distanceTo(player.position()) > 3, "Standing players must travel with the rotating ring");
            helper.assertTrue(player.position().distanceTo(ring.toWorld(localFeet, ring.pose(0))) < .2, "Ring carriage must not accumulate slip");
            var tower = bodies(level).stream().filter(b -> b.groupId() == 3).findFirst().orElseThrow();
            var towerFeet = new Vec3(TaixuMotionGeometry.towerX(0) + .5, 65, TaixuMotionGeometry.towerZ(0) + .5);
            player.setPos(tower.toWorld(towerFeet, tower.pose(0))); player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
            for (int tick = 0; tick < 200; tick++) {
                advance(level); machine.serverTick(); bodies(level).forEach(TaixuAssemblyEntity::tick);
                TaixuMotionWorld.carry(new EntityTickEvent.Pre(player));
            }
            helper.assertTrue(Math.abs(player.getY() - tower.toWorld(towerFeet, tower.pose(0)).y) < .08, "Ascending and descending towers must carry the rider");
            player.setDeltaMovement(0, .42, 0);
            var beforeJump = player.position(); advance(level);
            TaixuMotionWorld.carry(new EntityTickEvent.Pre(player));
            helper.assertTrue(player.position().equals(beforeJump), "Jumping away must release the rider from carriage");
            player.setDeltaMovement(Vec3.ZERO); nearController(player);
            var obstacle = BlockPos.containing(ring.toWorld(new Vec3(.5, 36.5, 28.5), ring.pose(0)));
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 2);
            for (int tick = 0; tick < 10; tick++) { advance(level); machine.serverTick(); }
            helper.assertTrue(machine.motion().mode() == TaixuMotionState.PAUSED && machine.motion().message().equals("obstacle"), "Foreign blocks must stop running motion");
            helper.assertTrue(level.getBlockState(obstacle).is(Blocks.STONE), "Obstacle detection cannot overwrite foreign blocks");
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 2); machine.motion().toggle(player);
            int[] counts = machine.motion().portableCounts();
            machine = reload(level, machine); machine.serverTick();
            helper.assertTrue(Arrays.equals(counts, machine.motion().portableCounts()) && bodies(level).size() == 11, "Reload must preserve every owned material and recreate exactly eleven bodies");
            nearController(player); machine.motion().dock(player, false);
            for (int tick = 0; tick < 10; tick++) { advance(level); machine.serverTick(); }
            ring = bodies(level).stream().filter(b -> b.groupId() == 0).findFirst().orElseThrow();
            obstacle = BlockPos.containing(ring.toWorld(new Vec3(.5, 36.5, 28.5), ring.pose(0)));
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 2);
            for (int tick = 0; tick < 10; tick++) { advance(level); machine.serverTick(); }
            helper.assertTrue(machine.motion().message().equals("dock_obstacle"), "Docking must freeze when obstructed");
            var frozen = ring.pose(0);
            for (int tick = 0; tick < 30; tick++) { advance(level); machine.serverTick(); }
            helper.assertTrue(ring.pose(0).equals(frozen), "An obstructed dock must retain its exact pose");
            machine = reload(level, machine); machine.serverTick();
            ring = bodies(level).stream().filter(b -> b.groupId() == 0).findFirst().orElseThrow();
            helper.assertTrue(ring.pose(0).equals(frozen), "Frozen docking must survive save/reload without a jump");
            level.setBlock(obstacle, Blocks.AIR.defaultBlockState(), 2);
            var deniedPart = TaixuMotionGeometry.group(0).parts().get(20);
            var deniedPos = TaixuStructure.worldPos(ANCHOR, Direction.NORTH, deniedPart);
            Consumer<BlockEvent.EntityPlaceEvent> deny = event -> { if (event.getPos().equals(deniedPos)) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(deny);
            try {
                for (int tick = 0; tick < 250; tick++) { advance(level); machine.serverTick(); }
                helper.assertTrue(machine.motion().hasBodies() && machine.motion().message().equals("protected"), "Denied docking must retain material ownership");
                for (var group : TaixuMotionGeometry.groups()) for (var part : group.parts())
                    helper.assertTrue(level.getBlockState(TaixuStructure.worldPos(ANCHOR, Direction.NORTH, part)).isAir(), "Denied docking must roll back all earlier placements");
            } finally { NeoForge.EVENT_BUS.unregister(deny); }
            for (int tick = 0; tick < 1000 && machine.motion().hasBodies(); tick++) { advance(level); machine.serverTick(); }
            helper.assertTrue(!machine.motion().hasBodies() && bodies(level).isEmpty(), "Docking must remove derived bodies");
            helper.assertTrue(TaixuStructure.inspect(level, ANCHOR, Direction.NORTH).formed(), "Docking must restore the original blocks and orientations exactly");
            machine.requestInspection(player); machine.serverTick(); machine.motion().toggle(player);
            helper.assertTrue(machine.motion().hasBodies(), "Second activation must own materials before removal");
            for (var old : level.getEntitiesOfClass(ItemEntity.class, new AABB(ANCHOR).inflate(4))) old.discard();
            level.destroyBlock(ANCHOR, true, player);
            bodies(level).forEach(TaixuAssemblyEntity::tick);
            helper.assertTrue(bodies(level).isEmpty(), "Removing the controller must remove all derived bodies");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(ANCHOR).inflate(4));
            helper.assertTrue(drops.size() == 1, "Breaking an active controller must pack owned materials in one recoverable item");
            var packed = drops.getFirst().getItem().copy(); drops.getFirst().discard();
            helper.assertTrue(packed.is(TaixuContent.CONTROLLER.get().asItem()) && packed.getCount() == 1, "Packed drop must contain exactly one controller");
            level.setBlock(ANCHOR, TaixuContent.CONTROLLER.get().defaultBlockState(), 3);
            machine = (TaixuBlockEntity) level.getBlockEntity(ANCHOR);
            machine.loadWithComponents(packed.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(), level.registryAccess());
            helper.assertTrue(!machine.motion().hasBodies() && Arrays.equals(counts, machine.motion().portableCounts()), "Replacing a packed controller must restore all owned materials without duplicating geometry");
            machine = reload(level, machine); machine.setRecoveryOwner(player);
            int[] recovered = new int[counts.length];
            for (int tick = 0; tick < 1000 && (machine.motion().hasPortable() || !machine.getInternalInventory().isEmpty()); tick++) {
                machine.serverTick();
                for (var stack : player.getInventory().items) if (!stack.isEmpty()) {
                    for (var type : TaixuStructure.Type.values()) if (stack.is(TaixuStructure.block(type).asItem())) recovered[type.ordinal()] += stack.getCount();
                }
                player.getInventory().clearContent();
            }
            helper.assertTrue(Arrays.equals(counts, recovered), "Portable recovery must return every material exactly once after another reload");
            System.out.println("TAIXU_MOTION_PASS bodies=11 ringCarriage=true towerCarriage=true jump=true collision=true resume=true docking=true obstacleFreeze=true permissionRollback=true packedRecovery=true");
            helper.succeed();
        } finally {
            var existing = level.getBlockEntity(ANCHOR);
            if (existing instanceof TaixuBlockEntity controller) { controller.clearContent(); controller.motion().discardBodies(); }
            level.setBlock(ANCHOR, Blocks.AIR.defaultBlockState(), 3); players.remove(player.getUUID());
            for (var chunk : footprint) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
    private static TaixuBlockEntity reload(ServerLevel level, TaixuBlockEntity machine) {
        var saved = machine.saveWithFullMetadata(level.registryAccess()); machine.motion().discardBodies(); level.removeBlockEntity(ANCHOR);
        var restored = (TaixuBlockEntity) BlockEntity.loadStatic(ANCHOR, TaixuContent.CONTROLLER.get().defaultBlockState(), saved, level.registryAccess());
        level.setBlockEntity(restored); return restored;
    }
    private static List<TaixuAssemblyEntity> bodies(ServerLevel level) { return TaixuMotionWorld.bodies(level).stream().filter(b -> b.configured() && b.controller().equals(ANCHOR) && !b.isRemoved()).toList(); }
    private static void assertPositionUpdatesPreserveBounds(GameTestHelper helper, List<TaixuAssemblyEntity> bodies) {
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
}
