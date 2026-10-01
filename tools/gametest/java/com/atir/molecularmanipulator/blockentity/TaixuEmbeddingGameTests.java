package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.TaixuContent;
import com.atir.molecularmanipulator.entity.TaixuAssemblyEntity;
import com.atir.molecularmanipulator.world.TaixuMotionWorld;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class TaixuEmbeddingGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "taixu_embedding", timeoutTicks = 1000)
    public static void protectedMigrationRefundsAndFourFacings(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        int index = 0;
        for (var facing : Direction.Plane.HORIZONTAL) {
            var anchor = new BlockPos(3200 + index++ * 256, 120, 3200);
            var chunks = TaixuStructure.chunks(anchor, facing, 2);
            for (var chunk : chunks) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TaixuEmbed" + index));
            players.put(player.getUUID(), player);
            try {
                var machine = legacy(level, anchor, facing);
                near(player, anchor); player.setGameMode(GameType.SURVIVAL);
                for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                machine.getInternalInventory().setItemDirect(0, new ItemStack(Items.DIAMOND, 7));
                machine.requestInspection(player); machine.serverTick();
                helper.assertTrue(machine.formed() && machine.structureVersion() == 2, "Saved v2 building must retain its legacy layout");
                var target = machine.worldPos(TaixuStructure.CONTROLLER);
                var occupied = machine.worldPos(new BlockPos(-2, 65, -6));
                level.setBlock(occupied, Blocks.STONE.defaultBlockState(), 2);
                machine.requestEmbedding(player); machine.serverTick();
                helper.assertTrue(machine.status() == TaixuBlockEntity.Status.CONFLICT && level.getBlockState(occupied).is(Blocks.STONE), "Foreign blocks in new socket positions must prevent migration without being overwritten");
                assertLegacy(helper, machine); level.setBlock(occupied, Blocks.AIR.defaultBlockState(), 2);
                Consumer<BlockEvent.BreakEvent> denyBreak = event -> { if (event.getPos().equals(target)) event.setCanceled(true); };
                NeoForge.EVENT_BUS.addListener(denyBreak);
                try {
                    machine.requestEmbedding(player); machine.serverTick();
                    helper.assertTrue(machine.status() == TaixuBlockEntity.Status.PROTECTED, "Protected terminal must prevent migration: status=" + machine.status() + " pending=" + machine.embedRequested() + " mode=" + machine.motion().mode() + " portable=" + machine.motion().hasPortable() + " canManage=" + machine.canManage(player));
                    assertLegacy(helper, machine);
                } finally { NeoForge.EVENT_BUS.unregister(denyBreak); }
                Consumer<BlockEvent.EntityPlaceEvent> denyPlace = event -> { if (event.getPos().equals(target)) event.setCanceled(true); };
                NeoForge.EVENT_BUS.addListener(denyPlace);
                try {
                    machine.requestEmbedding(player); machine.serverTick();
                    machine = (TaixuBlockEntity) level.getBlockEntity(anchor);
                    helper.assertTrue(machine.status() == TaixuBlockEntity.Status.PROTECTED, "Late placement rejection must roll back the entire migration");
                    assertLegacy(helper, machine);
                    helper.assertTrue(machine.getInternalInventory().getStackInSlot(0).getCount() == 7 && !machine.motion().hasPortable(), "Rollback must preserve inventory without issuing refunds");
                } finally { NeoForge.EVENT_BUS.unregister(denyPlace); }
                int[] refunds = expectedRefunds();
                machine.requestEmbedding(player); machine.serverTick();
                var embedded = (TaixuBlockEntity) level.getBlockEntity(target);
                helper.assertTrue(level.getBlockState(anchor).isAir() && embedded != null && embedded.structureVersion() == 3 && embedded.formed(), "Migration must move the controller and form the central layout");
                for (var part : TaixuStructure.parts(3)) helper.assertTrue(TaixuStructure.matches(level.getBlockState(embedded.worldPos(part)), part, facing), "Every central-layout block must stay at its original building position");
                var newLocals = new HashSet<BlockPos>(); TaixuStructure.parts(3).forEach(p -> newLocals.add(p.pos()));
                for (var part : TaixuStructure.parts(2)) if (!newLocals.contains(part.pos())) helper.assertTrue(level.getBlockState(TaixuStructure.worldPos(anchor, facing, part, 2)).isAir(), "External platform and old terminals must disappear");
                helper.assertTrue(Arrays.equals(refunds, embedded.motion().portableCounts()), "Refunds must exactly match displaced materials");
                helper.assertTrue(embedded.getInternalInventory().getStackInSlot(0).getCount() == 7, "Migration must transfer the existing recovery inventory");
                embedded = reload(level, embedded); embedded.serverTick();
                helper.assertTrue(Arrays.equals(refunds, embedded.motion().portableCounts()) && embedded.getInternalInventory().getStackInSlot(0).getCount() == 7, "Full backpack must preserve all refunds across reload");
                player.getInventory().clearContent(); embedded.setRecoveryOwner(player);
                for (int tick = 0; tick < 50; tick++) embedded.serverTick();
                helper.assertTrue(embedded.getInternalInventory().isEmpty() && !embedded.motion().hasPortable(), "Refunds must drain when inventory has room");
                helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7, "Original inventory must return exactly once");
                for (var type : TaixuStructure.Type.values()) helper.assertTrue(player.getInventory().countItem(TaixuStructure.block(type).asItem()) == refunds[type.ordinal()], "Refund quantity mismatch for " + type);
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            } finally {
                players.remove(player.getUUID()); level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
                for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
            }
        }
        System.out.println("TAIXU_EMBEDDING_PASS facings=4 foreignBlocksPreserved=true breakProtection=true latePlacementRollback=true inventoryTransfer=true exactRefunds=" + Arrays.stream(expectedRefunds()).sum() + " fullInventoryReload=true");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "taixu_embedding_motion", timeoutTicks = 1000)
    public static void movingLegacyBuildingDocksBeforeEmbedding(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var anchor = new BlockPos(4608, 120, 3200); var facing = Direction.NORTH;
        var chunks = TaixuStructure.chunks(anchor, facing, 2);
        for (var chunk : chunks) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "TaixuEmbedMotion"));
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList()); players.put(player.getUUID(), player);
        TaixuBlockEntity embedded = null;
        try {
            var machine = legacy(level, anchor, facing); near(player, anchor); player.setGameMode(GameType.CREATIVE);
            machine.requestInspection(player); machine.serverTick(); machine.motion().toggle(player);
            helper.assertTrue(machine.motion().hasBodies(), "Legacy building must activate correctly");
            for (int tick = 0; tick < 80; tick++) { advance(level); machine.serverTick(); bodies(level, anchor).forEach(TaixuAssemblyEntity::tick); }
            var origin = TaixuMotionGeometry.origin(anchor, facing, 2); var target = machine.worldPos(TaixuStructure.CONTROLLER);
            helper.assertTrue(bodies(level, anchor).size() == 11 && machine.formed(), "Old entities must retain their geometry and ownership");
            machine.requestEmbedding(player);
            helper.assertTrue(machine.embedRequested() && machine.motion().mode() == TaixuMotionState.DOCKING && level.getBlockEntity(anchor) == machine, "Running migration must first enter docking without moving the controller");
            machine = reload(level, machine);
            helper.assertTrue(machine.embedRequested(), "Queued embedding must survive save/reload");
            for (int tick = 0; tick < 400 && level.getBlockEntity(anchor) != null; tick++) { advance(level); machine.serverTick(); bodies(level, anchor).forEach(TaixuAssemblyEntity::tick); }
            embedded = (TaixuBlockEntity) level.getBlockEntity(target);
            helper.assertTrue(embedded != null && embedded.formed() && !embedded.motion().hasBodies() && bodies(level, anchor).isEmpty(), "Docked migration must remove every old moving entity: status=" + machine.status() + " pending=" + machine.embedRequested() + " mode=" + machine.motion().mode() + " message=" + machine.motion().message() + " portable=" + machine.motion().hasPortable() + " canManage=" + machine.canManage(player));
            helper.assertTrue(origin.equals(TaixuMotionGeometry.origin(target, facing, 3)), "Embedding must preserve the building world origin");
            helper.assertTrue(!embedded.motion().hasPortable(), "Creative migration should not issue materials");
            near(player, target); embedded.motion().toggle(player);
            var newBodies = bodies(level, target);
            helper.assertTrue(newBodies.size() == 11 && embedded.motion().mode() == TaixuMotionState.RUNNING, "Embedded controller must restart all eleven moving components");
            for (var body : newBodies) {
                var bounds = body.getBoundingBox(); body.setPos(body.getX(), body.getY(), body.getZ());
                helper.assertTrue(body.getBoundingBox().equals(bounds), "Position updates must preserve the large assembly bounds");
            }
            System.out.println("TAIXU_EMBEDDING_MOTION_PASS legacyEntities=true queuedReload=true dockFirst=true originPreserved=true restartBodies=11 bounds=true");
            helper.succeed();
        } finally {
            if (embedded != null) embedded.motion().clear(); players.remove(player.getUUID()); level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
    private static List<TaixuAssemblyEntity> bodies(ServerLevel level, BlockPos anchor) { return TaixuMotionWorld.bodies(level).stream().filter(b -> b.controller().equals(anchor)).toList(); }
    private static void near(ServerPlayer player, BlockPos pos) { player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 2); }
    private static void advance(ServerLevel level) { ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1); }
    private static TaixuBlockEntity legacy(ServerLevel level, BlockPos anchor, Direction facing) {
        // Start with a fresh terminal; previous disposable runs may leave a queued
        // controller or refund buffer in the same world position.
        level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
        var oldPositions = new HashSet<BlockPos>(); TaixuStructure.parts(2).forEach(p -> oldPositions.add(p.pos()));
        for (var part : TaixuStructure.parts(3)) if (!oldPositions.contains(part.pos()))
            level.setBlock(TaixuStructure.worldPos(anchor, facing, part, 2), Blocks.AIR.defaultBlockState(), 2);
        for (var part : TaixuStructure.parts(2)) level.setBlock(TaixuStructure.worldPos(anchor, facing, part, 2), TaixuStructure.state(part, facing), 2);
        for (var air : TaixuStructure.requiredAir()) level.setBlock(TaixuStructure.worldPos(anchor, facing, air, 2), Blocks.AIR.defaultBlockState(), 2);
        var machine = (TaixuBlockEntity) level.getBlockEntity(anchor);
        var tag = machine.saveWithFullMetadata(level.registryAccess()); tag.remove("taixuLayout"); tag.putInt("taixuVersion", 2); machine.loadTag(tag, level.registryAccess());
        return machine;
    }
    private static void assertLegacy(GameTestHelper helper, TaixuBlockEntity machine) {
        helper.assertTrue(machine.structureVersion() == 2, "Legacy version must remain after rejection");
        for (var part : machine.structureParts()) helper.assertTrue(TaixuStructure.matches(machine.getLevel().getBlockState(machine.worldPos(part)), part, machine.facing()), "Rollback must restore each legacy block");
    }
    private static int[] expectedRefunds() {
        var newParts = new HashMap<BlockPos, TaixuStructure.Part>(); TaixuStructure.parts(3).forEach(p -> newParts.put(p.pos(), p));
        int[] counts = new int[TaixuStructure.Type.values().length];
        for (var part : TaixuStructure.parts(2)) if (!part.equals(newParts.get(part.pos())) && part.type() != TaixuStructure.Type.CONTROLLER && part.type() != TaixuStructure.Type.RESOURCE_PORT) counts[part.type().ordinal()]++;
        return counts;
    }
    private static TaixuBlockEntity reload(ServerLevel level, TaixuBlockEntity machine) {
        var pos = machine.getBlockPos(); var tag = machine.saveWithFullMetadata(level.registryAccess()); var state = machine.getBlockState(); level.removeBlockEntity(pos);
        var restored = (TaixuBlockEntity) BlockEntity.loadStatic(pos, state, tag, level.registryAccess()); level.setBlockEntity(restored); return restored;
    }
}
