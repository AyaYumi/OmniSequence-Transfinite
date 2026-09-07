package com.atir.molecularmanipulator.verification;

import com.atir.molecularmanipulator.blockentity.DismantlePlan;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.StructureLayout;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real-engine dismantle verification, loaded only by the optional verification mod. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularDismantleGameTests {
    private static final BlockPos ORIGIN = new BlockPos(160, 80, 160);
    private static final String PLAN_TAG = "molecular_center_dismantle_plan";

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 600)
    public static void molecularDismantleSnapshotsInWorldOrder(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        for (int x = 6; x <= 14; x++) for (int z = 6; z <= 14; z++) {
            level.setChunkForced(x, z, true);
            level.getChunk(x, z);
        }
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("de449f22-b34a-4262-8f42-e57a3705844b"), "MolecularDismantle"));
        player.setGameMode(GameType.CREATIVE);
        var playerMapField = PlayerList.class.getDeclaredField("playersByUUID");
        playerMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var players = (Map<UUID, ServerPlayer>) playerMapField.get(level.getServer().getPlayerList());
        var previous = players.put(player.getUUID(), player);
        int previousBudget = ModConfig.BUILD_BLOCKS_PER_TICK.get();
        var touched = new HashSet<BlockPos>();
        try {
            ModConfig.BUILD_BLOCKS_PER_TICK.set(1);
            for (var layout : List.of(StructureLayout.CURRENT, StructureLayout.LEGACY_1_3_9)) {
                var facing = layout == StructureLayout.CURRENT ? Direction.NORTH : Direction.EAST;
                var center = seed(helper, level, facing, layout, touched);
                var entries = entries(MolecularCenterStructure.createDismantlePlan(level, ORIGIN, facing, layout, false));
                assertSnake(helper, entries);
                center.startDismantle(player);
                helper.assertTrue(center.getBuildTotal() == entries.size() && center.getBuildProgress() == 0,
                        "Initial total must contain real blocks only");
                finishInOrder(helper, level, center, player, entries, 0);
                helper.assertTrue(level.getBlockEntity(ORIGIN) == center, "Controller must stay in place");
                clear(level, touched);
            }
            sparsePauseAndReload(helper, level, player, touched);
            oldCounterReload(helper, level, player, touched);
            pausedUpgrade(helper, level, player, touched);
            System.out.println("MOLECULAR_DISMANTLE_PASS: current/rim, y-z-snake ordering, sparse ownership, stale skips, full inventory hold, NBT resume, legacy counter rebuild, paused upgrade union");
            helper.succeed();
        } finally {
            clear(level, touched);
            ModConfig.BUILD_BLOCKS_PER_TICK.set(previousBudget);
            if (previous == null) players.remove(player.getUUID()); else players.put(player.getUUID(), previous);
        }
    }

    private static void sparsePauseAndReload(GameTestHelper helper, ServerLevel level, ServerPlayer player,
            Set<BlockPos> touched) {
        var center = seed(helper, level, Direction.NORTH, StructureLayout.CURRENT, touched);
        var all = entries(MolecularCenterStructure.createDismantlePlan(level, ORIGIN, Direction.NORTH,
                StructureLayout.CURRENT, false));
        var keep = sparse(all);
        retain(level, all, keep);
        center.refreshStructure();
        helper.assertTrue(!center.isFormed(), "Sparse fixture must rely on remembered layout, not a complete match");
        center.getMatterInventory().setItemDirect(0, new ItemStack(Items.DIAMOND, 7));
        var decoration = ORIGIN.offset(45, 8, 0);
        touched.add(decoration);
        level.setBlock(decoration, ModContent.MOLECULAR_CENTER_CASING.get().defaultBlockState(), 2);
        center.startDismantle(player);
        helper.assertTrue(center.getBuildTotal() == keep.size(), "AIR and outside decoration must not enter the plan");
        level.removeBlockEntity(keep.get(0).pos());
        level.setBlock(keep.get(0).pos(), Blocks.AIR.defaultBlockState(), 2);
        level.removeBlockEntity(keep.get(1).pos());
        level.setBlock(keep.get(1).pos(), Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
        level.removeBlockEntity(keep.get(2).pos());
        level.setBlock(keep.get(2).pos(), Blocks.CHEST.defaultBlockState(), 2);
        ((ChestBlockEntity) level.getBlockEntity(keep.get(2).pos())).setItem(0, new ItemStack(Items.EMERALD, 17));
        player.setGameMode(GameType.SURVIVAL);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().items.set(slot, new ItemStack(Items.BARRIER, 64));
        }
        center.serverTick();
        helper.assertTrue(center.getBuildProgress() == 3 && center.isDismantling(),
                "Stale entries must skip immediately, then full inventory must hold the highest live entry");
        center.serverTick();
        helper.assertTrue(center.getBuildProgress() == 3 && keep.get(3).matches(level.getBlockState(keep.get(3).pos())),
                "Blocked entry must not fall through to a lower layer");
        center = reload(level, center.saveWithFullMetadata(level.registryAccess()));
        helper.assertTrue(center.getBuildProgress() == 3 && center.getBuildTotal() == keep.size(),
                "Reload must preserve the full original snapshot and forward cursor");
        player.getInventory().clearContent();
        finishInOrder(helper, level, center, player, keep, 3);
        helper.assertTrue(level.getBlockState(keep.get(1).pos()).is(Blocks.DIAMOND_BLOCK), "Replacement block must survive");
        helper.assertTrue(((ChestBlockEntity) level.getBlockEntity(keep.get(2).pos())).getItem(0).getCount() == 17,
                "Foreign BE and contents must survive");
        helper.assertTrue(level.getBlockState(decoration).is(ModContent.MOLECULAR_CENTER_CASING.get()),
                "Nearby same-material decoration must survive");
        helper.assertTrue(center.getMatterInventory().getStackInSlot(0).getCount() == 7,
                "Retained controller inventory must stay untouched");
        clear(level, touched);
    }

    private static void oldCounterReload(GameTestHelper helper, ServerLevel level, ServerPlayer player,
            Set<BlockPos> touched) {
        var center = seed(helper, level, Direction.EAST, StructureLayout.LEGACY_1_3_9, touched);
        var all = entries(MolecularCenterStructure.createDismantlePlan(level, ORIGIN, Direction.EAST,
                StructureLayout.LEGACY_1_3_9, false));
        var keep = sparse(all);
        retain(level, all, keep);
        center.refreshStructure();
        center.startDismantle(player);
        player.getInventory().clearContent();
        center.serverTick();
        center.serverTick();
        var data = center.saveWithFullMetadata(level.registryAccess());
        data.remove(PLAN_TAG);
        data.putInt("molecular_center_work_cursor", 987654);
        data.putInt("molecular_center_work_total", 987655);
        center = reload(level, data);
        helper.assertTrue(center.getBuildProgress() == 0, "Old reverse cursor must be discarded");
        var remaining = entries(MolecularCenterStructure.createDismantlePlan(level, ORIGIN, Direction.EAST,
                StructureLayout.LEGACY_1_3_9, false));
        helper.assertTrue(remaining.size() == keep.size() - 2, "Legacy rebuild must exclude blocks already removed");
        assertSnake(helper, remaining);
        finishInOrder(helper, level, center, player, remaining, 0);
        helper.assertTrue(center.getBuildTotal() == remaining.size(), "Old save rebuild must count only remaining real blocks");
        clear(level, touched);
    }

    private static void pausedUpgrade(GameTestHelper helper, ServerLevel level, ServerPlayer player,
            Set<BlockPos> touched) {
        player.setGameMode(GameType.CREATIVE);
        var source = StructureLayout.LEGACY_1_3_9;
        var center = seed(helper, level, Direction.NORTH, source, touched);
        center.startStructureUpdate(player);
        helper.assertTrue(center.isBuilding(), "Upgrade fixture must have a recorded source");
        var sourceEntries = entries(MolecularCenterStructure.createDismantlePlan(level, ORIGIN, Direction.NORTH, source, false));
        retain(level, sourceEntries, sparse(sourceEntries));
        int added = 0;
        for (var part : MolecularCenterStructure.parts()) {
            if (part.partType() == PartType.AIR || MolecularCenterStructure.isController(part)
                    || MolecularCenterStructure.isController(part, source)) continue;
            var pos = MolecularCenterStructure.worldPos(ORIGIN, Direction.NORTH, part, source);
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, MolecularCenterStructure.partState(part.partType()), 2);
                touched.add(pos);
                if (++added == 5) break;
            }
        }
        var data = center.saveWithFullMetadata(level.registryAccess());
        data.putBoolean("molecular_center_building", false);
        center = reload(level, data);
        var expected = entries(MolecularCenterStructure.createDismantlePlan(level, ORIGIN, Direction.NORTH, source, true));
        center.startDismantle(player);
        helper.assertTrue(center.getBuildTotal() == expected.size(), "Paused upgrade must use only its source-target union");
        finishInOrder(helper, level, center, player, expected, 0);
        clear(level, touched);
    }

    private static MolecularCenterBlockEntity seed(GameTestHelper helper, ServerLevel level, Direction facing,
            StructureLayout layout, Set<BlockPos> touched) {
        for (var part : MolecularCenterStructure.parts(layout)) {
            if (part.partType() == PartType.AIR || MolecularCenterStructure.isController(part, layout)) continue;
            var pos = MolecularCenterStructure.worldPos(ORIGIN, facing, part, layout);
            touched.add(pos);
            level.setBlock(pos, MolecularCenterStructure.partState(part.partType()), 2);
        }
        touched.add(ORIGIN);
        level.setBlock(ORIGIN, ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing), 3);
        var center = (MolecularCenterBlockEntity) level.getBlockEntity(ORIGIN);
        center.refreshStructure();
        helper.assertTrue(center.getStructureLayout() == layout, "Fixture must identify its exact layout: " + layout);
        return center;
    }

    private static List<DismantlePlan.Entry> entries(DismantlePlan plan) {
        if (plan == null) throw new AssertionError("Fixture chunks were not loaded");
        var result = new ArrayList<DismantlePlan.Entry>();
        while (!plan.isComplete()) { result.add(plan.current()); plan.advance(); }
        return List.copyOf(result);
    }

    private static List<DismantlePlan.Entry> sparse(List<DismantlePlan.Entry> all) {
        var result = new ArrayList<DismantlePlan.Entry>();
        result.addAll(all.subList(0, 7));
        result.addAll(all.subList(all.size() / 2, all.size() / 2 + 6));
        result.addAll(all.subList(all.size() - 5, all.size()));
        return entries(DismantlePlan.create(result));
    }

    private static void retain(ServerLevel level, List<DismantlePlan.Entry> all, List<DismantlePlan.Entry> keep) {
        var positions = new HashSet<BlockPos>();
        for (var entry : keep) positions.add(entry.pos());
        for (var entry : all) if (!positions.contains(entry.pos())) {
            level.removeBlockEntity(entry.pos());
            level.setBlock(entry.pos(), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void finishInOrder(GameTestHelper helper, ServerLevel level, MolecularCenterBlockEntity center,
            ServerPlayer player, List<DismantlePlan.Entry> expected, int begin) {
        for (int index = begin; index < expected.size(); index++) {
            player.getInventory().clearContent();
            var entry = expected.get(index);
            helper.assertTrue(entry.matches(level.getBlockState(entry.pos())), "Next snapshot block must still exist");
            center.serverTick();
            helper.assertTrue(level.getBlockState(entry.pos()).isAir(), "Budget-one tick must remove exactly the next world-ordered block");
            helper.assertTrue(center.getBuildProgress() == index + 1, "Completed progress must move forward by actual work");
            if (index + 1 < expected.size()) {
                var next = expected.get(index + 1);
                helper.assertTrue(next.matches(level.getBlockState(next.pos())), "No lower/next block may be removed early");
            }
        }
        helper.assertTrue(!center.isDismantling() && center.getBuildProgress() == center.getBuildTotal(),
                "Dismantle must finish at total without scanning an AIR tail");
    }

    private static void assertSnake(GameTestHelper helper, List<DismantlePlan.Entry> entries) {
        int lastY = Integer.MAX_VALUE, lastZ = Integer.MIN_VALUE, lastX = 0, row = 0;
        for (var entry : entries) {
            var pos = entry.pos();
            helper.assertTrue(pos.getY() <= lastY, "World Y must never increase");
            if (pos.getY() != lastY) { lastZ = Integer.MIN_VALUE; row = -1; }
            helper.assertTrue(pos.getZ() >= lastZ, "Every Y layer must finish each Z row before advancing");
            if (pos.getZ() != lastZ) row++;
            else helper.assertTrue(row % 2 == 0 ? pos.getX() > lastX : pos.getX() < lastX,
                    "Actual rows must alternate X direction without crossing half a layer");
            lastY = pos.getY(); lastZ = pos.getZ(); lastX = pos.getX();
        }
    }

    private static MolecularCenterBlockEntity reload(ServerLevel level, CompoundTag data) {
        var state = level.getBlockState(ORIGIN);
        level.removeBlockEntity(ORIGIN);
        var loaded = BlockEntity.loadStatic(ORIGIN, state, data.copy(), level.registryAccess());
        if (!(loaded instanceof MolecularCenterBlockEntity center)) throw new AssertionError("Controller NBT failed to decode");
        level.setBlockEntity(center);
        return center;
    }

    private static void clear(ServerLevel level, Set<BlockPos> touched) {
        for (var pos : touched) { level.removeBlockEntity(pos); level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); }
        touched.clear();
    }
}
