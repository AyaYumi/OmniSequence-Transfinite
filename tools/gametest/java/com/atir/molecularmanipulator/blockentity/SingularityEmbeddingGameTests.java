package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.entity.SingularityAssemblyEntity;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
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
public final class SingularityEmbeddingGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_embedding", timeoutTicks = 1000)
    public static void protectedMigrationRefundsAndFourFacings(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        int index = 0;
        for (var facing : Direction.Plane.HORIZONTAL) {
            var anchor = new BlockPos(3200 + index++ * 256, 120, 3200);
            var chunks = SingularityStructure.chunks(anchor, facing, 2);
            for (var chunk : chunks) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SingularityEmbed" + index));
            players.put(player.getUUID(), player);
            try {
                var machine = legacy(level, anchor, facing);
                near(player, anchor); player.setGameMode(GameType.SURVIVAL);
                for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                machine.getInternalInventory().setItemDirect(0, new ItemStack(Items.DIAMOND, 7));
                machine.requestInspection(player); machine.serverTick();
                helper.assertTrue(machine.formed() && machine.structureVersion() == 2, "Saved v2 building must retain its legacy layout");
                var target = machine.worldPos(SingularityStructure.CONTROLLER);
                var occupied = machine.worldPos(SingularityStructure.CONTROLLER);
                var originalSocket = level.getBlockState(occupied);
                level.setBlock(occupied, Blocks.STONE.defaultBlockState(), 2);
                machine.requestEmbedding(player); machine.serverTick();
                helper.assertTrue(machine.status() == SingularityBlockEntity.Status.CONFLICT && level.getBlockState(occupied).is(Blocks.STONE), "Foreign blocks in new socket positions must prevent migration without being overwritten");
                level.setBlock(occupied, originalSocket, 2); assertLegacy(helper, machine);
                Consumer<BlockEvent.BreakEvent> denyBreak = event -> { if (event.getPos().equals(target)) event.setCanceled(true); };
                NeoForge.EVENT_BUS.addListener(denyBreak);
                try {
                    machine.requestEmbedding(player); machine.serverTick();
                    helper.assertTrue(machine.status() == SingularityBlockEntity.Status.PROTECTED, "Protected terminal must prevent migration: status=" + machine.status() + " pending=" + machine.embedRequested() + " mode=" + machine.motion().mode() + " portable=" + machine.motion().hasPortable() + " canManage=" + machine.canManage(player));
                    assertLegacy(helper, machine);
                } finally { NeoForge.EVENT_BUS.unregister(denyBreak); }
                Consumer<BlockEvent.EntityPlaceEvent> denyPlace = event -> { if (event.getPos().equals(target)) event.setCanceled(true); };
                NeoForge.EVENT_BUS.addListener(denyPlace);
                try {
                    machine.requestEmbedding(player); machine.serverTick();
                    machine = (SingularityBlockEntity) level.getBlockEntity(anchor);
                    helper.assertTrue(machine.status() == SingularityBlockEntity.Status.PROTECTED, "Late placement rejection must roll back the entire migration");
                    assertLegacy(helper, machine);
                    helper.assertTrue(machine.getInternalInventory().getStackInSlot(0).getCount() == 7 && !machine.motion().hasPortable(), "Rollback must preserve inventory without issuing refunds");
                } finally { NeoForge.EVENT_BUS.unregister(denyPlace); }
                int[] refunds = expectedRefunds();
                machine.requestEmbedding(player); machine.serverTick();
                var embedded = (SingularityBlockEntity) level.getBlockEntity(target);
                helper.assertTrue(level.getBlockState(anchor).isAir() && embedded != null && embedded.structureVersion() == 3 && embedded.formed(), "Migration must move the controller and form the central layout");
                for (var part : SingularityStructure.parts(3)) helper.assertTrue(SingularityStructure.matches(level.getBlockState(embedded.worldPos(part)), part, facing), "Every central-layout block must stay at its original building position");
                var newLocals = new HashSet<BlockPos>(); SingularityStructure.parts(3).forEach(p -> newLocals.add(p.pos()));
                for (var part : SingularityStructure.parts(2)) if (!newLocals.contains(part.pos())) helper.assertTrue(level.getBlockState(SingularityStructure.worldPos(anchor, facing, part, 2)).isAir(), "External platform and old terminals must disappear");
                helper.assertTrue(Arrays.equals(refunds, embedded.motion().portableCounts()), "Refunds must exactly match displaced materials");
                helper.assertTrue(embedded.getInternalInventory().getStackInSlot(0).getCount() == 7, "Migration must transfer the existing recovery inventory");
                embedded = reload(level, embedded); embedded.serverTick();
                helper.assertTrue(Arrays.equals(refunds, embedded.motion().portableCounts()) && embedded.getInternalInventory().getStackInSlot(0).getCount() == 7, "Full backpack must preserve all refunds across reload");
                player.getInventory().clearContent(); embedded.setRecoveryOwner(player);
                for (int tick = 0; tick < 50; tick++) embedded.serverTick();
                helper.assertTrue(embedded.getInternalInventory().isEmpty() && !embedded.motion().hasPortable(), "Refunds must drain when inventory has room");
                helper.assertTrue(player.getInventory().countItem(Items.DIAMOND) == 7, "Original inventory must return exactly once");
                for (var type : SingularityStructure.Type.values()) helper.assertTrue(player.getInventory().countItem(SingularityStructure.block(type).asItem()) == refunds[type.ordinal()], "Refund quantity mismatch for " + type);
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            } finally {
                players.remove(player.getUUID()); level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
                for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
            }
        }
        System.out.println("SINGULARITY_EMBEDDING_PASS facings=4 foreignBlocksPreserved=true breakProtection=true latePlacementRollback=true inventoryTransfer=true exactRefunds=" + Arrays.stream(expectedRefunds()).sum() + " fullInventoryReload=true");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_embedding_motion", timeoutTicks = 1000)
    public static void movingLegacyBuildingDocksBeforeEmbedding(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var anchor = new BlockPos(4608, 120, 3200); var facing = Direction.NORTH;
        var chunks = SingularityStructure.chunks(anchor, facing, 2);
        for (var chunk : chunks) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SingularityEmbedMotion"));
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList()); players.put(player.getUUID(), player);
        SingularityBlockEntity embedded = null;
        try {
            var machine = legacy(level, anchor, facing); near(player, anchor); player.setGameMode(GameType.CREATIVE);
            machine.requestInspection(player); machine.serverTick(); machine.motion().startForCollection(player);
            helper.assertTrue(machine.motion().hasBodies(), "Legacy building must activate correctly");
            for (int tick = 0; tick < 80; tick++) { advance(level); machine.motion().serverTick(); bodies(level, anchor).forEach(SingularityAssemblyEntity::tick); }
            var origin = SingularityMotionGeometry.origin(anchor, facing, 2); var target = machine.worldPos(SingularityStructure.CONTROLLER);
            helper.assertTrue(bodies(level, anchor).size() == 11 && machine.formed(), "Old entities must retain their geometry and ownership");
            machine.requestEmbedding(player);
            helper.assertTrue(machine.embedRequested() && machine.motion().mode() == SingularityMotionState.DOCKING && level.getBlockEntity(anchor) == machine, "Running migration must first enter docking without moving the controller");
            machine = reload(level, machine);
            helper.assertTrue(machine.embedRequested(), "Queued embedding must survive save/reload");
            for (int tick = 0; tick < 400 && level.getBlockEntity(anchor) != null; tick++) { advance(level); machine.serverTick(); bodies(level, anchor).forEach(SingularityAssemblyEntity::tick); }
            embedded = (SingularityBlockEntity) level.getBlockEntity(target);
            helper.assertTrue(embedded != null && embedded.formed() && !embedded.motion().hasBodies() && bodies(level, anchor).isEmpty(), "Docked migration must remove every old moving entity: status=" + machine.status() + " pending=" + machine.embedRequested() + " mode=" + machine.motion().mode() + " message=" + machine.motion().message() + " portable=" + machine.motion().hasPortable() + " canManage=" + machine.canManage(player));
            helper.assertTrue(origin.equals(SingularityMotionGeometry.origin(target, facing, 3)), "Embedding must preserve the building world origin");
            helper.assertTrue(!embedded.motion().hasPortable(), "Creative migration should not issue materials");
            near(player, target); embedded.motion().startForCollection(player);
            var newBodies = bodies(level, target);
            helper.assertTrue(newBodies.size() == 11 && embedded.motion().mode() == SingularityMotionState.RUNNING, "Embedded controller must restart all eleven moving components");
            for (var body : newBodies) {
                var bounds = body.getBoundingBox(); body.setPos(body.getX(), body.getY(), body.getZ());
                helper.assertTrue(body.getBoundingBox().equals(bounds), "Position updates must preserve the large assembly bounds");
            }
            System.out.println("SINGULARITY_EMBEDDING_MOTION_PASS legacyEntities=true queuedReload=true dockFirst=true originPreserved=true restartBodies=11 bounds=true");
            helper.succeed();
        } finally {
            if (embedded != null) embedded.motion().clear(); players.remove(player.getUUID()); level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
    private static List<SingularityAssemblyEntity> bodies(ServerLevel level, BlockPos anchor) { return SingularityMotionWorld.bodies(level).stream().filter(b -> b.controller().equals(anchor)).toList(); }
    private static void near(ServerPlayer player, BlockPos pos) { player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() - 2); }
    private static void advance(ServerLevel level) { ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + 1); }
    private static SingularityBlockEntity legacy(ServerLevel level, BlockPos anchor, Direction facing) {
        // Start with a fresh terminal; previous disposable runs may leave a queued
        // controller or refund buffer in the same world position.
        level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
        var oldPositions = new HashSet<BlockPos>(); SingularityStructure.parts(2).forEach(p -> oldPositions.add(p.pos()));
        for (var part : SingularityStructure.parts(3)) if (!oldPositions.contains(part.pos()))
            level.setBlock(SingularityStructure.worldPos(anchor, facing, part, 2), Blocks.AIR.defaultBlockState(), 2);
        for (var part : SingularityStructure.parts(2)) level.setBlock(SingularityStructure.worldPos(anchor, facing, part, 2), SingularityStructure.state(part, facing), 2);
        for (var air : SingularityStructure.requiredAir()) level.setBlock(SingularityStructure.worldPos(anchor, facing, air, 2), Blocks.AIR.defaultBlockState(), 2);
        var machine = (SingularityBlockEntity) level.getBlockEntity(anchor);
        var tag = machine.saveWithFullMetadata(level.registryAccess()); tag.remove("singularityLayout"); tag.putInt("singularityVersion", 2); machine.loadTag(tag, level.registryAccess());
        return machine;
    }
    private static void assertLegacy(GameTestHelper helper, SingularityBlockEntity machine) {
        helper.assertTrue(machine.structureVersion() == 2, "Legacy version must remain after rejection");
        for (var part : machine.structureParts()) helper.assertTrue(SingularityStructure.matches(machine.getLevel().getBlockState(machine.worldPos(part)), part, machine.facing()), "Rollback must restore each legacy block: " + part + " actual=" + machine.getLevel().getBlockState(machine.worldPos(part)));
    }
    private static int[] expectedRefunds() {
        var newParts = new HashMap<BlockPos, SingularityStructure.Part>(); SingularityStructure.parts(3).forEach(p -> newParts.put(p.pos(), p));
        int[] counts = new int[SingularityStructure.Type.values().length];
        for (var part : SingularityStructure.parts(2)) if (!part.equals(newParts.get(part.pos())) && part.type() != SingularityStructure.Type.CONTROLLER) counts[part.type().ordinal()]++;
        return counts;
    }
    private static SingularityBlockEntity reload(ServerLevel level, SingularityBlockEntity machine) {
        var pos = machine.getBlockPos(); var tag = machine.saveWithFullMetadata(level.registryAccess()); var state = machine.getBlockState(); level.removeBlockEntity(pos);
        var restored = (SingularityBlockEntity) BlockEntity.loadStatic(pos, state, tag, level.registryAccess()); level.setBlockEntity(restored); return restored;
    }
}
