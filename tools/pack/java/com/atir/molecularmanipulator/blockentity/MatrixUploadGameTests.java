package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises EAEP's installed, transformed encoding hooks rather than our bridge alone. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatrixUploadGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 700)
    public static void matrixUploadFromRealEncodingMenu(GameTestHelper h) {
        var level = h.getLevel();
        var pos = new BlockPos(24000, 100, 24000);
        for (var chunk : MolecularCenterStructure.chunkFootprint(pos, Direction.NORTH,
                MolecularCenterStructure.StructureLayout.CURRENT, MolecularCenterStructure.StructureLayout.CURRENT)) {
            level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunk(chunk.x, chunk.z);
        }
        for (var part : MolecularCenterStructure.parts()) if (!MolecularCenterStructure.isController(part)) {
            level.setBlock(MolecularCenterStructure.worldPos(pos, Direction.NORTH, part),
                    MolecularCenterStructure.partState(part.partType()), 3);
        }
        level.setBlockAndUpdate(pos, ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState());
        var center = (MolecularCenterBlockEntity) level.getBlockEntity(pos);
        var powerPos = pos.east(80);
        var cablePos = powerPos.east();
        level.setChunkForced(cablePos.getX() >> 4, cablePos.getZ() >> 4, true);
        level.getChunkAt(cablePos);
        level.setBlockAndUpdate(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(cablePos, AEBlocks.CABLE_BUS.block().defaultBlockState());
        var power = (appeng.blockentity.networking.CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        var cable = (CableBusBlockEntity) level.getBlockEntity(cablePos);
        cable.addPart(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT), null, null);
        var terminal = (PatternEncodingTerminalPart) cable.addPart(AEParts.PATTERN_ENCODING_TERMINAL.asItem(), Direction.NORTH, null);
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        var core = BuiltInRegistries.ITEM.get(new ResourceLocation("extendedae_plus:assembler_matrix_upload_core"));
        h.assertTrue(core != Items.AIR, "Installed EAEP upload core must exist");
        final PatternEncodingTermMenu[] menu = new PatternEncodingTermMenu[1];
        h.startSequence().thenWaitUntil(() -> h.assertTrue(center.getMainNode().isReady()
                        && power.getMainNode().isActive() && terminal.getMainNode().isActive(), "Wait for real nodes"))
                .thenExecute(() -> GridHelper.createConnection(center.getMainNode().getNode(), power.getMainNode().getNode()))
                .thenWaitUntil(() -> h.assertTrue(center.canAcceptMatrixUpload(), "Wait for powered, restored crystal library"))
                .thenExecute(() -> {
                    h.assertTrue(center.getLogic().getFullPatternInventory().isEmpty(), "Fresh fixture library");
                    center.getMatrixUploadCoreInventory().setItemDirect(0, new ItemStack(core));
                    menu[0] = new PatternEncodingTermMenu(91, player.getInventory(), terminal);
                    terminal.getLogic().getBlankPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack(4));
                    menu[0].setMode(EncodingMode.CRAFTING);
                    menu[0].getCraftingMatrix().setItemDirect(0, new ItemStack(Items.OAK_LOG));
                    // EAEP's real encode() RETURN hook schedules its upload utility.
                    menu[0].encode();
                }).thenIdle(3).thenExecute(() -> {
                    h.assertTrue(terminal.getLogic().getEncodedPatternInv().isEmpty(), "Encoded source slot must clear after automatic upload");
                    h.assertTrue(count(center) == 1, "One real encoded pattern must reach the Sequence Array");
                    h.assertTrue(terminal.getLogic().getBlankPatternInv().getStackInSlot(0).getCount() == 3, "One blank consumed");
                    menu[0].encode();
                }).thenIdle(3).thenExecute(() -> {
                    h.assertTrue(count(center) == 1 && terminal.getLogic().getEncodedPatternInv().isEmpty(), "Duplicate encoding adds no pattern");
                    h.assertTrue(terminal.getLogic().getBlankPatternInv().getStackInSlot(0).getCount() == 3, "Duplicate returns its blank");
                    var stored = center.getLogic().getFullPatternInventory().getStackInSlot(0);
                    var direct = stored.copy();
                    direct.getOrCreateTag().putString("encodePlayer", "Different encoder");
                    try {
                        var api = Class.forName("com.extendedae_plus.util.uploadPattern.MatrixUploadUtil");
                        var basic = api.getMethod("uploadPatternToMatrix", net.minecraft.server.level.ServerPlayer.class,
                                ItemStack.class, appeng.api.networking.IGrid.class);
                        var silent = api.getMethod("uploadPatternToMatrix", net.minecraft.server.level.ServerPlayer.class,
                                ItemStack.class, appeng.api.networking.IGrid.class, boolean.class);
                        h.assertTrue(!(Boolean) basic.invoke(null, player, direct, center.getMainNode().getGrid()), "Direct duplicate rejected");
                        h.assertTrue(!(Boolean) silent.invoke(null, player, direct, center.getMainNode().getGrid(), true), "Silent duplicate rejected");
                        h.assertTrue(direct.getCount() == 1 && count(center) == 1, "Direct rejection preserves caller ownership");
                        // Both overloads must upload a fresh pattern without consuming the caller's source.
                        menu[0].setMode(EncodingMode.STONECUTTING);
                        var recipe = (net.minecraft.world.item.crafting.StonecutterRecipe) level.getRecipeManager()
                                .byKey(new ResourceLocation("minecraft:stone_slab_from_stone_stonecutting")).orElseThrow();
                        var cut = PatternDetailsHelper.encodeStonecuttingPattern(recipe, AEItemKey.of(Items.STONE), AEItemKey.of(Items.STONE_SLAB), false);
                        h.assertTrue((Boolean) basic.invoke(null, player, cut, center.getMainNode().getGrid()) && count(center) == 2,
                                "Three-argument upload reaches the library");
                        var smith = (net.minecraft.world.item.crafting.SmithingRecipe) level.getRecipeManager()
                                .byKey(new ResourceLocation("minecraft:netherite_sword_smithing")).orElseThrow();
                        var upgrade = PatternDetailsHelper.encodeSmithingTablePattern(smith, AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                                AEItemKey.of(Items.DIAMOND_SWORD), AEItemKey.of(Items.NETHERITE_INGOT), AEItemKey.of(Items.NETHERITE_SWORD), false);
                        h.assertTrue((Boolean) silent.invoke(null, player, upgrade, center.getMainNode().getGrid(), true) && count(center) == 3,
                                "Four-argument upload reaches the library");
                    } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                    // No core: leave the pattern in the terminal for EAEP's native fallback.
                    center.getMatrixUploadCoreInventory().setItemDirect(0, ItemStack.EMPTY);
                    menu[0].setMode(EncodingMode.CRAFTING);
                    menu[0].getCraftingMatrix().setItemDirect(0, new ItemStack(Items.BIRCH_LOG));
                    menu[0].encode();
                }).thenIdle(3).thenExecute(() -> {
                    h.assertTrue(count(center) == 3 && !terminal.getLogic().getEncodedPatternInv().isEmpty(), "Missing core keeps source pattern");
                    terminal.getLogic().getEncodedPatternInv().setItemDirect(0, ItemStack.EMPTY);
                    center.getMatrixUploadCoreInventory().setItemDirect(0, new ItemStack(core));
                    menu[0].setMode(EncodingMode.PROCESSING);
                    terminal.getLogic().getEncodedInputInv().setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 1));
                    terminal.getLogic().getEncodedOutputInv().setStack(0, new GenericStack(AEItemKey.of(Items.DIRT), 1));
                    menu[0].encode();
                }).thenIdle(3).thenExecute(() -> {
                    h.assertTrue(count(center) == 3 && !terminal.getLogic().getEncodedPatternInv().isEmpty(), "Processing patterns retain native behavior");
                    System.out.println("MATRIX_UPLOAD_PASS realEncode=true sourceConsumedOnce=true duplicateBlankRefund=true"
                            + " bothDirectOverloads=true encoderIgnored=true noCoreFallback=true processingRetained=true");
                }).thenSucceed();
    }

    private static int count(MolecularCenterBlockEntity center) {
        int count = 0;
        for (var stack : center.getLogic().getFullPatternInventory()) count += stack.getCount();
        return count;
    }
}
