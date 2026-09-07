package com.atir.molecularmanipulator.verification;

import appeng.api.ids.AEComponents;
import appeng.core.definitions.AEItems;
import com.atir.molecularmanipulator.blockentity.DismantlePlan;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.PartType;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.StructureLayout;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class OmniDismantleGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 600)
    public static void actualBlocksDescendLayerByLayerAndResumeFromSavedPositions(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var origin = new BlockPos(416, 80, 160);
        for (int x = 24; x <= 29; x++) for (int z = 8; z <= 13; z++) {
            level.setChunkForced(x, z, true);
            level.getChunk(x, z);
        }
        var player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("bb640ace-12c8-4b60-a710-0a94177c0193"), "OmniDismantle"));
        player.setGameMode(GameType.CREATIVE);
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        var previous = players.put(player.getUUID(), player);
        try {
            for (var layout : List.of(StructureLayout.CURRENT, StructureLayout.LEGACY_1_3_9)) {
                for (var facing : List.of(Direction.NORTH, Direction.EAST)) {
                    verifyLayout(helper, level, origin, layout, facing, player);
                }
            }
            unknownPartialLayoutIsNotADeletionLicense(helper, level, origin, player);
            System.out.println("OMNI_DISMANTLE_ENGINE_PASS: current/legacy, two facings, real-only queue, world-layer snake order, free stale skips, full-capacity pause, NBT resume, decoration protected");
            helper.succeed();
        } finally {
            if (previous == null) players.remove(player.getUUID()); else players.put(player.getUUID(), previous);
        }
    }

    private static void verifyLayout(GameTestHelper helper, ServerLevel level, BlockPos origin,
            StructureLayout layout, Direction facing, ServerPlayer player) {
        player.getInventory().clearContent();
        place(level, origin, facing, layout);
        var core = controller(level, origin, facing);
        core.refreshStructureNow();
        helper.assertTrue(core.getInspection().formed() && core.getInspection().layout() == layout,
                "The source layout must be identified before testing a partial dismantle: " + layout);
        var quantum = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        quantum.set(AEComponents.ENTANGLED_SINGULARITY_ID, 9_104_441L);
        core.getQuantumInventory().setItemDirect(0, quantum.copy());

        var required = OmniComputationStructure.dismantleParts(layout);
        var initiallyMissing = OmniComputationStructure.worldPos(origin, facing, required.getLast(), layout);
        level.setBlock(initiallyMissing, Blocks.AIR.defaultBlockState(), 2);
        var physical = new HashSet<BlockPos>();
        required.forEach(p -> physical.add(OmniComputationStructure.worldPos(origin, facing, p, layout)));
        // This lies inside a historical search area, but outside the selected
        // blueprint's real blocks. The old union scan would wrongly remove it.
        var decoration = OmniComputationStructure.parts(StructureLayout.LEGACY_1_3_9).stream()
                .filter(p -> p.type() != PartType.AIR && p.type() != PartType.CONTROLLER)
                .map(p -> OmniComputationStructure.worldPos(origin, facing, p, layout))
                .filter(p -> !physical.contains(p) && !p.equals(origin))
                .findFirst().orElse(origin.offset(25, 1, 25));
        level.setBlock(decoration, ModContent.OMNI_COMPUTATION_CASING.get().defaultBlockState(), 2);
        core.startDismantle(player);
        helper.assertTrue(core.isDismantling(), "A saved, previously formed layout must remain dismantlable after one part disappears");
        var plan = snapshotPlan(core, level);
        var ordered = entries(plan);
        helper.assertTrue(ordered.size() == required.size() - 1 && core.getBuildTotal() == ordered.size(),
                "Queue/progress totals must count only the actual source blocks, excluding AIR, controller and foreign decoration");
        helper.assertTrue(ordered.size() > 130, "Fixture must exercise multiple dismantle ticks");
        helper.assertTrue(ordered.stream().noneMatch(e -> e.pos().equals(initiallyMissing) || e.pos().equals(decoration)),
                "An absent part or same-type decoration must never enter the plan");
        assertLayerOrder(helper, ordered);

        fillInventory(player);
        core.serverTick();
        helper.assertTrue(core.isDismantling() && core.getBuildProgress() == 0
                        && ordered.getFirst().matches(level.getBlockState(ordered.getFirst().pos())),
                "Full recovery capacity must wait on the highest first block, without dropping to another layer");
        player.getInventory().clearContent();
        level.setBlock(ordered.get(0).pos(), Blocks.AIR.defaultBlockState(), 2);
        var replacement = ordered.get(1).block() == ModContent.OMNI_COMPUTATION_GLASS.get()
                ? ModContent.OMNI_COMPUTATION_CASING.get() : ModContent.OMNI_COMPUTATION_GLASS.get();
        level.setBlock(ordered.get(1).pos(), replacement.defaultBlockState(), 2);
        core.serverTick();
        helper.assertTrue(core.getBuildProgress() == 130,
                "Two live stale entries must skip for free before removing 128 actual blocks");
        assertProcessedPrefix(helper, level, ordered, core.getBuildProgress());
        helper.assertTrue(level.getBlockState(ordered.get(1).pos()).is(replacement),
                "A target replaced by another structure material must not be dismantled");

        fillInventory(player);
        int pausedAt = core.getBuildProgress();
        core.serverTick();
        helper.assertTrue(core.getBuildProgress() == pausedAt && core.isDismantling(),
                "A later full inventory must preserve the exact forward queue index");
        var beforeReload = snapshotPlan(core, level).save();
        var data = core.saveWithFullMetadata(level.registryAccess());
        var state = level.getBlockState(origin);
        level.removeBlockEntity(origin);
        var loaded = BlockEntity.loadStatic(origin, state, data, level.registryAccess());
        helper.assertTrue(loaded instanceof OmniComputationCoreBlockEntity, "Saved Omni controller must load");
        level.setBlockEntity(loaded);
        core = (OmniComputationCoreBlockEntity) loaded;
        helper.assertTrue(core.isDismantling() && core.getBuildProgress() == pausedAt
                        && snapshotPlan(core, level).save().equals(beforeReload),
                "NBT reload must preserve the exact ordered absolute positions and completed prefix");
        helper.assertTrue(ItemStack.matches(quantum, core.getQuantumInventory().getStackInSlot(0)),
                "Dismantle-state persistence must not alter the controller's quantum inventory/components");
        core.serverTick();
        helper.assertTrue(core.getBuildProgress() == pausedAt, "Reload must not bypass a still-full recovery inventory");
        for (int tick = 0; tick < 250 && core.isDismantling(); tick++) {
            player.getInventory().clearContent();
            int prior = core.getBuildProgress();
            core.serverTick();
            helper.assertTrue(core.getBuildProgress() >= prior, "The resumed cursor must never reverse");
            assertProcessedPrefix(helper, level, ordered, core.getBuildProgress());
        }
        helper.assertTrue(!core.isDismantling() && core.getBuildProgress() == ordered.size(),
                "Dismantling must finish after the actual queue, without scanning historical air positions");
        helper.assertTrue(level.getBlockEntity(origin) == core && level.getBlockState(decoration).is(ModContent.OMNI_COMPUTATION_CASING.get())
                        && level.getBlockState(ordered.get(1).pos()).is(replacement),
                "The controller, external same-type decoration and changed live target must remain");
        helper.assertTrue(ItemStack.matches(quantum, core.getQuantumInventory().getStackInSlot(0)),
                "Completing a body dismantle must preserve its controller's quantum item");
        core.getQuantumInventory().setItemDirect(0, ItemStack.EMPTY);
        level.setBlock(decoration, Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(ordered.get(1).pos(), Blocks.AIR.defaultBlockState(), 2);
        level.removeBlockEntity(origin);
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2);
        System.out.println("OMNI_DISMANTLE_LAYOUT_PASS=" + layout + ":" + facing + " queued=" + ordered.size());
    }

    private static void unknownPartialLayoutIsNotADeletionLicense(GameTestHelper helper, ServerLevel level,
            BlockPos origin, ServerPlayer player) {
        var core = controller(level, origin, Direction.NORTH);
        var part = OmniComputationStructure.dismantleParts(StructureLayout.CURRENT).getFirst();
        var residualPos = OmniComputationStructure.worldPos(origin, Direction.NORTH, part);
        level.setBlock(residualPos, OmniComputationStructure.block(part.type()).defaultBlockState(), 2);
        core.startDismantle(player);
        helper.assertTrue(!core.isDismantling() && level.getBlockState(residualPos).is(OmniComputationStructure.block(part.type())),
                "An unknown partial structure must not authorize deleting arbitrary historical candidates");
        level.setBlock(residualPos, Blocks.AIR.defaultBlockState(), 2);
        level.removeBlockEntity(origin);
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2);
    }

    private static void assertLayerOrder(GameTestHelper helper, List<DismantlePlan.Entry> entries) {
        int row = 0;
        for (int i = 1; i < entries.size(); i++) {
            var previous = entries.get(i - 1).pos();
            var next = entries.get(i).pos();
            helper.assertTrue(next.getY() <= previous.getY(), "Every higher world layer must precede every lower layer");
            if (next.getY() != previous.getY()) { row = 0; continue; }
            helper.assertTrue(next.getZ() >= previous.getZ(), "Rows must proceed continuously in ascending world Z");
            if (next.getZ() != previous.getZ()) { row++; continue; }
            helper.assertTrue((row & 1) == 0 ? next.getX() > previous.getX() : next.getX() < previous.getX(),
                    "Adjacent occupied rows must alternate the world-X direction");
        }
    }
    private static void assertProcessedPrefix(GameTestHelper helper, ServerLevel level, List<DismantlePlan.Entry> entries, int completed) {
        for (int i = 0; i < entries.size(); i++) {
            boolean stillMatches = entries.get(i).matches(level.getBlockState(entries.get(i).pos()));
            helper.assertTrue(i < completed ? !stillMatches : stillMatches,
                    "Actual removal must follow a single ordered prefix, never skip into a lower row: " + i + "/" + completed);
        }
    }
    private static DismantlePlan snapshotPlan(OmniComputationCoreBlockEntity core, ServerLevel level) {
        return DismantlePlan.load(core.saveWithFullMetadata(level.registryAccess()).getCompound("omni_dismantle_plan"));
    }
    private static List<DismantlePlan.Entry> entries(DismantlePlan plan) {
        var result = new ArrayList<DismantlePlan.Entry>();
        while (!plan.isComplete()) { result.add(plan.current()); plan.advance(); }
        return result;
    }
    private static void fillInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) player.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
    }
    private static OmniComputationCoreBlockEntity controller(ServerLevel level, BlockPos pos, Direction facing) {
        level.setBlock(pos, ModContent.OMNI_COMPUTATION_CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), 3);
        return (OmniComputationCoreBlockEntity) level.getBlockEntity(pos);
    }
    private static void place(ServerLevel level, BlockPos controller, Direction facing, StructureLayout layout) {
        for (var part : OmniComputationStructure.parts(layout)) if (part.type() != PartType.CONTROLLER) {
            var state = OmniComputationStructure.block(part.type()).defaultBlockState();
            if (state.hasProperty(HorizontalDirectionalBlock.FACING)) state = state.setValue(HorizontalDirectionalBlock.FACING, facing);
            level.setBlock(OmniComputationStructure.worldPos(controller, facing, part, layout), state, 2);
        }
    }
}
