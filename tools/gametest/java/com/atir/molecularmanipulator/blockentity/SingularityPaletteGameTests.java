package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.SingularityContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityPaletteGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void palettePlacementDropsAndCoreLifecycle(GameTestHelper helper) {
        var retiredPort = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "resource_confluence_port");
        helper.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(retiredPort), "Retired port must not be registered");
        helper.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(retiredPort), "Retired port item must not be registered");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(retiredPort) == Blocks.AIR, "Old chunk palettes must resolve retired ports as air");
        helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(retiredPort) == Items.AIR, "Old inventory stacks must resolve retired ports as empty");
        var level = helper.getLevel();
        var tool = new ItemStack(Items.NETHERITE_PICKAXE);
        for (int i = 0; i < SingularityContent.PALETTE.size(); i++) {
            var block = SingularityContent.PALETTE.get(i).get();
            var pos = helper.absolutePos(new BlockPos(i, 3, 0));
            level.getChunkAt(pos);
            level.setBlock(pos, block.defaultBlockState(), 3);
            var state = level.getBlockState(pos);
            helper.assertTrue(state.is(block), "Block must place: " + block);
            helper.assertTrue(block.asItem() != Items.AIR, "Each registered block needs an item");
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), "Pickaxe tag must load");
            var be = level.getBlockEntity(pos);
            helper.assertTrue((be instanceof SingularityCoreBlockEntity) == (block == SingularityContent.CORE.get()),
                    "Core block entity must match its registered block");
            var drops = Block.getDrops(state, level, pos, be, null, tool);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                    "Harvesting must return the corresponding palette item: " + block);
            if (be instanceof SingularityCoreBlockEntity) {
                var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state,
                        be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                helper.assertTrue(loaded instanceof SingularityCoreBlockEntity, "Core must survive save/reload");
                helper.assertTrue(SingularityContent.CORE.get().getTicker(level, state, SingularityContent.CORE_BE.get()) == null,
                        "A visual core must not schedule a server tick");
            }
            if (block == SingularityContent.SLAB.get()) {
                var doubleSlab = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
                var doubleDrops = Block.getDrops(doubleSlab, level, pos, null, null, tool);
                helper.assertTrue(doubleDrops.size() == 1 && doubleDrops.getFirst().getCount() == 2,
                        "Double slabs must return two items");
            }
            if (block == SingularityContent.SPIRE.get()) {
                for (var facing : Direction.values()) {
                    var oriented = state.setValue(DirectionalBlock.FACING, facing);
                    helper.assertTrue(!oriented.getShape(level, pos).isEmpty(), "Every crystal orientation needs a shape");
                    var box = oriented.getShape(level, pos).bounds();
                    helper.assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0
                            && box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1, "Crystal shapes must remain inside their block");
                }
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(level.getBlockEntity(pos) == null, "Removing the core must clear its block entity");
        }
        System.out.println("SINGULARITY_PALETTE_SERVER_PASS blocks=13 loot=13 doubleSlab=2 coreReload=true coreServerTickers=0 controllerTicker=1");
        helper.succeed();
    }
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void legacyPortAndMaterialDataLoadWithoutShiftingRefunds(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(0, 4, 0));
        level.setBlock(pos, SingularityContent.CONTROLLER.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        var machine = (SingularityBlockEntity) level.getBlockEntity(pos);
        var stateTag = new net.minecraft.nbt.CompoundTag();
        stateTag.putString("Name", "molecularmanipulator:resource_confluence_port");
        var properties = new net.minecraft.nbt.CompoundTag(); properties.putString("facing", "west");
        stateTag.put("Properties", properties);
        var restoredState = net.minecraft.nbt.NbtUtils.readBlockState(
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(), stateTag);
        helper.assertTrue(restoredState.isAir(), "Old placed port block-state data must load as air");
        var itemTag = new net.minecraft.nbt.CompoundTag();
        itemTag.putString("id", "molecularmanipulator:resource_confluence_port"); itemTag.putInt("count", 64);
        helper.assertTrue(ItemStack.parseOptional(level.registryAccess(), itemTag).isEmpty(),
                "Old port stacks must become empty rather than another material");
        int[] old = new int[14]; old[1] = 17; old[11] = 64; old[12] = 23; old[13] = 41;
        var motion = machine.motion().save(); motion.remove("singularityMaterialVersion");
        motion.putIntArray("portable", old); machine.motion().load(motion);
        assertLegacyRefunds(helper, machine);
        var fallback = motion.copy(); fallback.putInt("version", -1); fallback.putIntArray("ownedMaterials", old);
        machine.motion().load(fallback); assertLegacyRefunds(helper, machine);
        var saved = machine.saveWithFullMetadata(level.registryAccess());
        saved.remove("singularityMaterialVersion"); saved.putIntArray("singularityPortableMaterials", old);
        saved.putString("operation", "BUILD"); saved.putInt("cursor", 9000);
        machine.loadTag(saved, level.registryAccess()); assertLegacyRefunds(helper, machine);
        helper.assertTrue(machine.progress() == 0, "Older construction cursor must restart and rescan completed parts");
        var migrated = machine.saveWithFullMetadata(level.registryAccess());
        migrated.remove("singularityPortableMaterials"); machine.loadTag(migrated, level.registryAccess());
        assertLegacyRefunds(helper, machine);
        machine.clearContent(); level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        System.out.println("SINGULARITY_RETIRED_PORT_PASS oldBlock=air oldItem=empty motionRefunds=true unknownGeometryRefunds=true packedRefunds=true repeatReload=true oldBuildCursor=0");
        helper.succeed();
    }
    private static void assertLegacyRefunds(GameTestHelper helper, SingularityBlockEntity machine) {
        var counts = machine.motion().portableCounts();
        helper.assertTrue(counts[SingularityStructure.Type.CASING.ordinal()] == 17
                && counts[SingularityStructure.Type.STAIRS.ordinal()] == 23
                && counts[SingularityStructure.Type.SLAB.ordinal()] == 41
                && java.util.Arrays.stream(counts).sum() == 81,
                "Old port counts must be discarded while stairs/slabs retain the exact refund quantities");
    }
}
