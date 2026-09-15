package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.atir.molecularmanipulator.registry.ModContent;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import java.util.Arrays;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real powered providers; inputs are supplied directly, without terminal planning. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularLongBatchGameTests {
    private static final long CRAFTS = 3_000_000_000L;

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void bothProvidersAcceptLongBatchesAndPreserveOutputCounts(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = new BlockPos(700_008 + (int) (level.getGameTime() % 100_000) * 16, 100, 808);
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        level.getChunkAt(origin);
        // 3 x 3 x 4 matrix: two inner functions, a pattern bank and the rewrite core.
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 4; z++) {
            int edges = (x == 0 || x == 2 ? 1 : 0) + (y == 0 || y == 2 ? 1 : 0) + (z == 0 || z == 3 ? 1 : 0);
            var block = edges == 0 && z == 1 ? ModContent.ASSEMBLER_MATRIX_MOLECULAR_CORE.get()
                    : BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("extendedae",
                            edges >= 2 ? "assembler_matrix_frame" : edges == 1 ? "assembler_matrix_wall" : "assembler_matrix_pattern"));
            helper.assertTrue(block != net.minecraft.world.level.block.Blocks.AIR, "ExtendedAE matrix block must exist");
            level.setBlock(origin.offset(x, y, z), block.defaultBlockState(), 3);
        }
        level.setBlock(origin.offset(1, 1, -1), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var singlePos = origin.offset(7, 0, 0);
        level.setBlock(singlePos, ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState(), 3);
        level.setBlock(singlePos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var single = (MolecularManipulatorBlockEntity) level.getBlockEntity(singlePos);
        var core = (AssemblerMatrixMolecularCoreBlockEntity) level.getBlockEntity(origin.offset(1, 1, 1));
        var bank = (TileAssemblerMatrixPattern) level.getBlockEntity(origin.offset(1, 1, 2));
        var encoded = pattern(helper);
        single.getLogic().getPatternInv().setItemDirect(0, encoded.copy());
        bank.getPatternInventory().setItemDirect(0, encoded.copy());
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(single.getMainNode().isActive(), "Single block must be powered");
            helper.assertTrue(core.isFormed() && core.getMainNode().isActive(), "Real matrix must form and power its core");
            helper.assertTrue(!single.getLogic().getAvailablePatterns().isEmpty() && !bank.getAvailablePatterns().isEmpty(),
                    "Both providers must publish the encoded recipe");
        }).thenExecute(() -> {
            var singlePattern = single.getLogic().getAvailablePatterns().get(0);
            var matrixPattern = bank.getAvailablePatterns().get(0);
            helper.assertTrue(MolecularBatchCraftingProvider.getBatchLimit(single.getLogic(), singlePattern) == Long.MAX_VALUE,
                    "Single block must advertise long batch capacity");
            helper.assertTrue(MolecularBatchCraftingProvider.getBatchLimit(bank, matrixPattern) == Long.MAX_VALUE,
                    "Matrix pattern bank must advertise its core's long capacity");
            checkProvider(helper, singlePattern, inputs -> single.getLogic().pushPattern(singlePattern, inputs), single);
            checkProvider(helper, matrixPattern, inputs -> bank.pushPattern(matrixPattern, inputs), core);
            var batcher = new MolecularCraftingBatcher();
            helper.assertTrue(batcher.prepare(singlePattern, inputs(Long.MAX_VALUE / 4), level, Long.MAX_VALUE)
                            && batcher.getOutputAmounts().getLong(AEItemKey.of(Items.OAK_PLANKS)) == Long.MAX_VALUE / 4 * 4,
                    "Largest representable four-output batch must stay exact");
            helper.assertTrue(!batcher.prepare(singlePattern, inputs(Long.MAX_VALUE / 4 + 1), level, Long.MAX_VALUE),
                    "Output multiplication overflow must reject the batch");
            System.out.println("MOLECULAR_LONG_BATCH_PASS: both real providers accept 3 billion crafts, persist 12 billion outputs, reject overflow without consuming inputs");
        }).thenSucceed();
    }

    private static void checkProvider(GameTestHelper helper, IPatternDetails pattern,
            Function<KeyCounter[], Boolean> push, BlockEntity machine) {
        var overflowing = inputs(Long.MAX_VALUE / 4 + 1);
        helper.assertTrue(!push.apply(overflowing) && overflowing[0].get(AEItemKey.of(Items.OAK_LOG)) == Long.MAX_VALUE / 4 + 1,
                "Overflow rejection must leave all inputs with the caller");
        var supplied = inputs(CRAFTS);
        helper.assertTrue(push.apply(supplied) && supplied[0].isEmpty(), "Accept and consume more than int-max crafts atomically");
        var level = helper.getLevel();
        var saved = machine.saveWithFullMetadata(level.registryAccess());
        assertOutput(helper, saved, CRAFTS * 4);
        var restored = BlockEntity.loadStatic(machine.getBlockPos(), machine.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(restored != null, "Machine must deserialize");
        assertOutput(helper, restored.saveWithFullMetadata(level.registryAccess()), CRAFTS * 4);
        // Individually valid outputs must still fit together with the existing buffer.
        var full = inputs(Long.MAX_VALUE / 4);
        helper.assertTrue(!push.apply(full) && full[0].get(AEItemKey.of(Items.OAK_LOG)) == Long.MAX_VALUE / 4,
                "Combined buffer overflow must preserve caller ownership");
        assertOutput(helper, machine.saveWithFullMetadata(level.registryAccess()), CRAFTS * 4);
    }

    private static void assertOutput(GameTestHelper helper, CompoundTag saved, long amount) {
        var list = saved.getList("output_buffer", Tag.TAG_COMPOUND);
        helper.assertTrue(list.size() == 1, "Exactly one buffered output key");
        var stack = GenericStack.readTag(helper.getLevel().registryAccess(), list.getCompound(0));
        helper.assertTrue(stack != null && stack.what().equals(AEItemKey.of(Items.OAK_PLANKS)) && stack.amount() == amount,
                "Persistent output count must not truncate or wrap");
    }

    private static KeyCounter[] inputs(long count) {
        var holder = new KeyCounter(); holder.add(AEItemKey.of(Items.OAK_LOG), count);
        return new KeyCounter[] {holder};
    }

    @SuppressWarnings("unchecked")
    private static ItemStack pattern(GameTestHelper helper) {
        var recipe = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        var grid = new ItemStack[9]; Arrays.fill(grid, ItemStack.EMPTY); grid[0] = new ItemStack(Items.OAK_LOG);
        return PatternDetailsHelper.encodeCraftingPattern(recipe, grid, new ItemStack(Items.OAK_PLANKS, 4), false, false);
    }
}
