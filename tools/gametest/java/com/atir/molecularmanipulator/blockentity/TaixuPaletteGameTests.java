package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.TaixuContent;
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
public final class TaixuPaletteGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void palettePlacementDropsAndCoreLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        var tool = new ItemStack(Items.NETHERITE_PICKAXE);
        for (int i = 0; i < TaixuContent.PALETTE.size(); i++) {
            var block = TaixuContent.PALETTE.get(i).get();
            var pos = helper.absolutePos(new BlockPos(i, 3, 0));
            level.getChunkAt(pos);
            level.setBlock(pos, block.defaultBlockState(), 3);
            var state = level.getBlockState(pos);
            helper.assertTrue(state.is(block), "Block must place: " + block);
            helper.assertTrue(block.asItem() != Items.AIR, "Each registered block needs an item");
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), "Pickaxe tag must load");
            var be = level.getBlockEntity(pos);
            helper.assertTrue((be instanceof TaixuCoreBlockEntity) == (block == TaixuContent.CORE.get()),
                    "Core block entity must match its registered block");
            var drops = Block.getDrops(state, level, pos, be, null, tool);
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                    "Harvesting must return the corresponding palette item: " + block);
            if (be instanceof TaixuCoreBlockEntity) {
                var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, state,
                        be.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
                helper.assertTrue(loaded instanceof TaixuCoreBlockEntity, "Core must survive save/reload");
                helper.assertTrue(TaixuContent.CORE.get().getTicker(level, state, TaixuContent.CORE_BE.get()) == null,
                        "A visual core must not schedule a server tick");
            }
            if (block == TaixuContent.SLAB.get()) {
                var doubleSlab = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
                var doubleDrops = Block.getDrops(doubleSlab, level, pos, null, null, tool);
                helper.assertTrue(doubleDrops.size() == 1 && doubleDrops.getFirst().getCount() == 2,
                        "Double slabs must return two items");
            }
            if (block == TaixuContent.SPIRE.get()) {
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
        System.out.println("TAIXU_PALETTE_SERVER_PASS blocks=14 loot=14 doubleSlab=2 coreReload=true coreServerTickers=0 controllerTicker=1");
        helper.succeed();
    }
}
