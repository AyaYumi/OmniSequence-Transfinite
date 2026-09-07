package com.atir.molecularmanipulator.verification;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.AEBaseBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.StructureLayout;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real engine regressions in the isolated dismantle-test world. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterDismantleGameTests {
    private static final BlockPos ORIGIN = new BlockPos(672, 80, 160);
    private static final Direction FACING = Direction.NORTH;

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 1000)
    public static void dismantleOnlyActualOwnedBlocksAndPreserveServices(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        for (int x = 38; x <= 46; x++) for (int z = 6; z <= 16; z++) {
            level.setChunkForced(x, z, true);
            level.getChunk(x, z);
        }
        var player = FakePlayerFactory.get(level,
                new GameProfile(UUID.fromString("25f1f1a3-b9ee-49a9-b5f6-e34901dc0986"), "MatterDismantleVerifier"));
        var field = PlayerList.class.getDeclaredField("playersByUUID");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        var previous = players.put(player.getUUID(), player);
        try {
            verifyActualQueue(helper, level, player);

            verifyServiceRecovery(helper, level, player);

            System.out.println("MATTER_DISMANTLE_PASS: actual queue, complete layers, sparse holes, changed blocks, external decoration, NBT resume, packed items/fluids/patterns");
            helper.succeed();
        } finally {
            if (previous == null) players.remove(player.getUUID());
            else players.put(player.getUUID(), previous);
        }
    }

    private static void verifyActualQueue(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
        clearSite(level);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        var controller = placeLayout(level, StructureLayout.CURRENT);
        controller.refreshStructure();
        var original = actualParts(level, StructureLayout.CURRENT);
        var ordered = spatialOrder(original.keySet());
        BlockPos initialHole = ordered.get(2);
        level.setBlock(initialHole, Blocks.AIR.defaultBlockState(), 3);
        original.remove(initialHole);
        var foreign = ordered.stream().filter(pos -> !pos.equals(initialHole)
                && !level.getBlockState(pos).is(ModContent.MATTER_FABRICATION_CASING.get())).findFirst().orElseThrow();
        level.setBlock(foreign, ModContent.MATTER_FABRICATION_CASING.get().defaultBlockState(), 3);
        original.remove(foreign);
        var decoration = ORIGIN.offset(25, 5, 25);
        level.setBlock(decoration, ModContent.MATTER_FABRICATION_CASING.get().defaultBlockState(), 3);
        controller.startDismantle(player);
        helper.assertTrue(controller.getOperationTotal() == original.size(), "Only actual present blocks count toward progress");
        ordered = spatialOrder(original.keySet());
        // A hundred stale entries must not consume the sixty-four real removals in the next tick.
        for (int index = 0; index < 100; index++) level.setBlock(ordered.get(index), Blocks.AIR.defaultBlockState(), 3);
        BlockPos changed = ordered.get(101);
        level.setBlock(changed, Blocks.DIRT.defaultBlockState(), 3);
        controller.serverTick();
        helper.assertTrue(controller.getOperationProgress() == 165, "100 missing plus one changed block must not spend the 64-removal budget");
        helper.assertTrue(level.getBlockState(changed).is(Blocks.DIRT), "A changed target must remain untouched");
        assertPrefix(helper, level, ordered, controller.getOperationProgress(), changed);
        int total = controller.getOperationTotal();
        controller = reload(level, controller.saveWithFullMetadata(level.registryAccess()));
        helper.assertTrue(controller.isDismantling() && controller.getOperationTotal() == total
                && controller.getOperationProgress() == 165, "NBT must retain actual total and forward cursor");
        for (int tick = 0; tick < 2000 && controller.isDismantling(); tick++) {
            controller.serverTick();
            if (controller.isDismantling()) assertPrefix(helper, level, ordered, controller.getOperationProgress(), changed);
        }
        helper.assertTrue(!controller.isDismantling(), "Current dismantle must finish");
        helper.assertTrue(level.getBlockEntity(ORIGIN) == controller, "The controller must stay in place");
        helper.assertTrue(level.getBlockState(decoration).is(ModContent.MATTER_FABRICATION_CASING.get()), "Nearby historical-scan decoration must survive");
        helper.assertTrue(level.getBlockState(foreign).is(ModContent.MATTER_FABRICATION_CASING.get()), "A dedicated machine block in an illegal blueprint slot must survive");
        helper.assertTrue(level.getBlockState(changed).is(Blocks.DIRT), "Changed block must survive the entire operation");
        level.setBlock(decoration, Blocks.AIR.defaultBlockState(), 3);
    }



    private static void verifyServiceRecovery(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
        clearSite(level);
        player.getInventory().clearContent();
        player.setGameMode(GameType.CREATIVE);
        var controller = placeController(level);
        var knowledge = controller.saveWithFullMetadata(level.registryAccess());
        knowledge.putString("fabrication_known_layout", StructureLayout.CURRENT.name());
        controller = reload(level, knowledge);
        var bays = MatterFabricationStructure.patternAssemblyBays();
        var itemPos = world(bays.get(0));
        var fluidPos = world(bays.get(1));
        var patternPos = world(bays.get(2));
        level.setBlock(itemPos, ModContent.MATTER_FABRICATION_ITEM_INPUT.get().defaultBlockState(), 3);
        level.setBlock(fluidPos, ModContent.MATTER_FABRICATION_FLUID_INPUT.get().defaultBlockState(), 3);
        level.setBlock(patternPos, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        ((MatterFabricationPortBlockEntity) level.getBlockEntity(itemPos)).getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 11));
        ((MatterFabricationPortBlockEntity) level.getBlockEntity(fluidPos)).getTank(0).setFluid(new FluidStack(Fluids.WATER, 1234));
        ((MatterFabricationPortBlockEntity) level.getBlockEntity(fluidPos)).getTank(3).setFluid(new FluidStack(Fluids.LAVA, 567));
        var pattern = encodedPattern();
        ((MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(patternPos)).getLogic().getPatternInv().setItemDirect(0, pattern.copy());
        var lower = MatterFabricationStructure.parts().stream().filter(p -> p.y() == 0).findFirst().orElseThrow();
        level.setBlock(world(lower), MatterFabricationStructure.partState(lower.type()), 3);
        int drops = droppedItems(level);
        player.setGameMode(GameType.SURVIVAL);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        controller.startDismantle(player);
        helper.assertTrue(controller.getOperationTotal() == 4, "Three legal service replacements and one lower frame block are the entire queue");
        controller.serverTick();
        helper.assertTrue(controller.isDismantling() && controller.getOperationProgress() == 0, "Full inventory must pause at the first actual block");
        helper.assertTrue(!level.getBlockState(world(lower)).isAir(), "A blocked higher layer must not allow a lower removal");
        controller = reload(level, controller.saveWithFullMetadata(level.registryAccess()));
        player.setGameMode(GameType.ADVENTURE);
        controller.serverTick();
        helper.assertTrue(controller.getOperationProgress() == 0, "Missing build permission must preserve the cursor");
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        finish(helper, controller);
        helper.assertTrue(droppedItems(level) == drops, "Packed service contents must not also be emitted by AE onRemove");
        var item = findItem(player, ModContent.MATTER_FABRICATION_ITEM_INPUT_ITEM.get());
        var fluid = findItem(player, ModContent.MATTER_FABRICATION_FLUID_INPUT_ITEM.get());
        var assembly = findItem(player, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_ITEM.get());
        var restoredItem = (MatterFabricationPortBlockEntity) restore(level, ORIGIN.offset(0, 5, 3), item, player);
        var restoredFluid = (MatterFabricationPortBlockEntity) restore(level, ORIGIN.offset(2, 5, 3), fluid, player);
        var restoredAssembly = (MatterFabricationPatternAssemblyBlockEntity) restore(level, ORIGIN.offset(4, 5, 3), assembly, player);
        helper.assertTrue(restoredItem.getInventory().getStackInSlot(0).is(Items.DIAMOND)
                && restoredItem.getInventory().getStackInSlot(0).getCount() == 11, "Port inventory must survive in its recovered item");
        helper.assertTrue(restoredFluid.getTank(0).getFluid().getFluid() == Fluids.WATER
                && restoredFluid.getTank(0).getFluidAmount() == 1234, "All stored fluid must survive in the recovered port");
        helper.assertTrue(restoredFluid.getTank(3).getFluid().getFluid() == Fluids.LAVA
                && restoredFluid.getTank(3).getFluidAmount() == 567, "Additional fluid tanks must survive too");
        helper.assertTrue(ItemStack.matches(pattern, restoredAssembly.getLogic().getPatternInv().getStackInSlot(0)), "Encoded patterns must survive exactly once");
        for (var entity : List.of(restoredItem, restoredFluid, restoredAssembly)) {
            entity.clearContent();
            level.setBlock(entity.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        }
    }



    private static List<BlockPos> spatialOrder(java.util.Collection<BlockPos> positions) {
        var layers = new TreeMap<Integer, TreeMap<Integer, List<BlockPos>>>(Comparator.reverseOrder());
        for (var pos : positions) layers.computeIfAbsent(pos.getY(), ignored -> new TreeMap<>())
                .computeIfAbsent(pos.getZ(), ignored -> new ArrayList<>()).add(pos);
        var result = new ArrayList<BlockPos>();
        for (var rows : layers.values()) {
            boolean reverse = false;
            for (var row : rows.values()) {
                row.sort(reverse ? Comparator.<BlockPos>comparingInt(BlockPos::getX).reversed()
                        : Comparator.comparingInt(BlockPos::getX));
                result.addAll(row);
                reverse = !reverse;
            }
        }
        return result;
    }

    private static void assertPrefix(GameTestHelper helper, ServerLevel level, List<BlockPos> ordered, int cursor, BlockPos changed) {
        for (int index = 0; index < ordered.size(); index++) {
            var pos = ordered.get(index);
            if (pos.equals(changed)) continue;
            helper.assertTrue(level.getBlockState(pos).isAir() == (index < cursor), "Removal must be a top-down serpentine prefix: index=" + index + " cursor=" + cursor + " position=" + pos);
        }
    }

    private static Map<BlockPos, BlockState> actualParts(ServerLevel level, StructureLayout layout) {
        var result = new HashMap<BlockPos, BlockState>();
        for (var part : MatterFabricationStructure.parts(layout)) {
            if (MatterFabricationStructure.isController(part)) continue;
            var state = level.getBlockState(world(part));
            if (!state.isAir() && MatterFabricationStructure.matchesLayoutPart(state, part, layout)) result.put(world(part), state);
        }
        return result;
    }

    private static ItemStack encodedPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)));
    }

    private static void finish(GameTestHelper helper, MatterFabricationBlockEntity controller) {
        for (int tick = 0; tick < 3000 && controller.isDismantling(); tick++) controller.serverTick();
        helper.assertTrue(!controller.isDismantling(), "Dismantle must finish after the obstruction is removed");
    }

    private static ItemStack findItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        return player.getInventory().items.stream().filter(stack -> stack.is(item)).findFirst().orElseThrow().copy();
    }

    private static BlockEntity restore(ServerLevel level, BlockPos pos, ItemStack stack, ServerPlayer player) {
        level.setBlock(pos, ((BlockItem) stack.getItem()).getBlock().defaultBlockState(), 3);
        BlockItem.updateCustomBlockEntityTag(level, player, pos, stack);
        var entity = level.getBlockEntity(pos);
        entity.applyComponentsFromItemStack(stack);
        return entity;
    }

    private static MatterFabricationBlockEntity reload(ServerLevel level, CompoundTag data) {
        var state = level.getBlockState(ORIGIN);
        level.removeBlockEntity(ORIGIN);
        var entity = BlockEntity.loadStatic(ORIGIN, state, data, level.registryAccess());
        level.setBlockEntity(entity);
        return (MatterFabricationBlockEntity) entity;
    }

    private static MatterFabricationBlockEntity placeController(ServerLevel level) {
        level.setBlock(ORIGIN, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, FACING), 3);
        return (MatterFabricationBlockEntity) level.getBlockEntity(ORIGIN);
    }

    private static MatterFabricationBlockEntity placeLayout(ServerLevel level, StructureLayout layout) {
        for (var part : MatterFabricationStructure.parts(layout)) {
            if (!MatterFabricationStructure.isController(part)) level.setBlock(world(part), MatterFabricationStructure.partState(part.type()), 3);
        }
        return placeController(level);
    }

    private static BlockPos world(MatterFabricationStructure.Part part) {
        return MatterFabricationStructure.worldPos(ORIGIN, FACING, part);
    }

    private static void clearSite(ServerLevel level) {
        var all = new HashSet<BlockPos>();
        for (var layout : StructureLayout.values()) for (var part : MatterFabricationStructure.parts(layout)) all.add(world(part));
        all.add(ORIGIN);
        for (var pos : all) {
            if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity entity) entity.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static int droppedItems(ServerLevel level) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(70)).size();
    }
}
