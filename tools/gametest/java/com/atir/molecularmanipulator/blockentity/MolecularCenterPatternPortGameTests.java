package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterPatternPortGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void storageBusExposesOnlySupportedMainPatterns(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = new BlockPos(400_008 + (int) (level.getGameTime() % 100_000) * 16, 100, 408);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        level.getChunkAt(pos);
        level.setBlock(pos, ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState(), 3);
        var center = (MolecularCenterBlockEntity) level.getBlockEntity(pos);
        var handler = center.getExternalPatternInventory();
        var patterns = patterns(helper);
        var pattern = patterns.get(0);
        var key = AEItemKey.of(pattern);
        level.setBlock(pos.east(), AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
        var cable = (CableBusBlockEntity) level.getBlockEntity(pos.east());
        cable.addPart(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT), null, null);
        var bus = cable.addPart(AEParts.STORAGE_BUS.get(), Direction.WEST, null);
        level.setBlock(pos.east(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var source = new MachineSource(center);
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(center.canAccessExternalPatterns(), "New controller inventory must initialize without a multiblock");
            helper.assertTrue(bus.getMainNode().isActive(), "Real storage bus must be powered with a channel");
            helper.assertTrue(bus.getInternalHandler().insert(key, 1, Actionable.SIMULATE, source) == 1,
                    "Storage bus must discover the pattern item capability");
        }).thenExecute(() -> {
            for (var side : Direction.values()) helper.assertTrue(
                    level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side) == handler,
                    "All faces expose the same restricted inventory");
            helper.assertTrue(handler.getSlots() == ModConfig.activePatternSlots(), "Expose configured pages only");
            var processing = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)), List.of(new GenericStack(AEItemKey.of(Items.DIRT), 1)));
            for (var rejected : List.of(new ItemStack(Items.DIAMOND), AEItems.BLANK_PATTERN.stack(),
                    AEItems.CRAFTING_PATTERN.stack(), processing)) {
                helper.assertTrue(!handler.isItemValid(0, rejected) && ItemStack.matches(rejected, handler.insertItem(0, rejected, false)),
                        "Ordinary, blank, processing and invalid patterns must be rejected");
                helper.assertTrue(bus.getInternalHandler().insert(AEItemKey.of(rejected), 1, Actionable.MODULATE, source) == 0,
                        "Storage bus must enforce the same input restriction");
            }
            int revision = center.getLogic().getPatternRevision();
            helper.assertTrue(handler.insertItem(35, pattern, true).isEmpty() && handler.getStackInSlot(35).isEmpty(),
                    "Simulated insertion cannot store patterns");
            helper.assertTrue(center.getLogic().getPatternRevision() == revision, "Simulation cannot rebuild patterns");
            for (int i = 0; i < patterns.size(); i++) {
                int slot = i == 2 ? handler.getSlots() - 1 : 35 + i;
                helper.assertTrue(handler.insertItem(slot, patterns.get(i), false).isEmpty(), "Supported patterns cross page boundaries");
                var copy = handler.getStackInSlot(slot); copy.setCount(0);
                helper.assertTrue(!handler.getStackInSlot(slot).isEmpty(), "Inventory inspection cannot mutate the source");
                helper.assertTrue(ItemStack.matches(patterns.get(i), handler.extractItem(slot, 1, true))
                                && !handler.getStackInSlot(slot).isEmpty(), "Simulated extraction preserves the slot");
                helper.assertTrue(ItemStack.matches(patterns.get(i), handler.extractItem(slot, 1, false)), "Extraction preserves encoded data");
            }
            var pair = pattern.copyWithCount(2);
            helper.assertTrue(handler.insertItem(0, pair, false).getCount() == 1 && handler.getStackInSlot(0).getCount() == 1,
                    "One pattern per slot, with exact rejected remainder");
            helper.assertTrue(!handler.insertItem(0, pattern, false).isEmpty(), "Occupied slot must not stack more patterns");
            handler.extractItem(0, 1, false);
            helper.assertTrue(!handler.insertItem(handler.getSlots(), pattern, false).isEmpty(), "Configured capacity cannot be exceeded");
            int pages = ModConfig.PATTERN_PAGES.get();
            try {
                ModConfig.PATTERN_PAGES.set(MolecularCenterBlockEntity.MAX_PATTERN_PAGES);
                int last = handler.getSlots() - 1;
                helper.assertTrue(last == 10_799 && handler.insertItem(last, pattern, false).isEmpty(), "Maximum page 300 accepts patterns");
                helper.assertTrue(ItemStack.matches(pattern, handler.extractItem(last, 1, false)), "Maximum-page extraction is exact");
            } finally {
                ModConfig.PATTERN_PAGES.set(pages);
            }
            center.getMatterInventory().setItemDirect(1, new ItemStack(Items.DIAMOND));
            center.getAutoCrafter().getPatternInventory().setItemDirect(0, patterns.get(1));
            var storage = bus.getInternalHandler();
            helper.assertTrue(storage.extract(AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE, source) == 0,
                    "Material/quantum/upgrade inventories are not exposed");
            helper.assertTrue(storage.extract(AEItemKey.of(patterns.get(1)), 1, Actionable.MODULATE, source) == 0,
                    "Nine passive crafting slots are not exposed");
            helper.assertTrue(storage.insert(key, 2, Actionable.MODULATE, source) == 2, "Real storage bus inserts across two slots");
            helper.assertTrue(storage.extract(key, 2, Actionable.MODULATE, source) == 2, "Real storage bus can move patterns back out");
            helper.assertTrue(center.getLogic().getFullPatternInventory().isEmpty(), "Bus extraction leaves the library empty");
            // A supplied controller can keep its patterns through a normal world save before construction.
            handler.insertItem(36, pattern, false);
            var saved = center.saveWithFullMetadata(level.registryAccess());
            var restored = (MolecularCenterBlockEntity) BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
            helper.assertTrue(restored != null && ItemStack.matches(pattern, restored.getLogic().getFullPatternInventory().getStackInSlot(36)),
                    "Unformed local patterns survive save/load");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(handler.extractItem(36, 1, false).isEmpty(), "A cached capability cannot extract from a removed controller");
            System.out.println("PATTERN_STORAGE_BUS_PASS: real ME storage bus, all faces, input restrictions, simulation, page boundaries, exact transfers and local NBT");
        }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void externalTransfersKeepCrystalOwnershipAcrossEmptyReloadAndDismantling(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = new BlockPos(-400_009 - (int) (level.getGameTime() % 100_000) * 128, 100, -409);
        for (var chunk : MolecularCenterStructure.chunkFootprint(pos, Direction.NORTH,
                MolecularCenterStructure.StructureLayout.CURRENT, MolecularCenterStructure.StructureLayout.CURRENT)) {
            level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z);
        }
        // A repeated isolated run can revisit coordinates; remove old crystal inventories first.
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (var part : MolecularCenterPatternShards.crystals()) {
            level.setBlock(MolecularCenterStructure.worldPos(pos, Direction.NORTH, part), Blocks.AIR.defaultBlockState(), 3);
        }
        for (var part : MolecularCenterStructure.parts()) if (!MolecularCenterStructure.isController(part)) {
            level.setBlock(MolecularCenterStructure.worldPos(pos, Direction.NORTH, part), MolecularCenterStructure.partState(part.partType()), 3);
        }
        level.setBlock(pos, ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState(), 3);
        var center = (MolecularCenterBlockEntity) level.getBlockEntity(pos);
        var pattern = patterns(helper).get(0);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(center.canAccessExternalPatterns() && center.isFormed(),
                "Formed library must finish crystal restoration before automation")).thenExecute(() -> {
            helper.assertTrue(center.getLogic().getFullPatternInventory().isEmpty(), "Fresh fixture must have no inherited patterns");
            var handler = center.getExternalPatternInventory();
            int slot = Math.min(MolecularCenterPatternShards.shardSize(), handler.getSlots() - 1);
            var part = MolecularCenterPatternShards.crystals().get(slot / MolecularCenterPatternShards.shardSize());
            var crystalPos = MolecularCenterStructure.worldPos(pos, Direction.NORTH, part);
            var crystal = (MolecularCenterCrystalBlockEntity) level.getBlockEntity(crystalPos);
            var remainder = handler.insertItem(slot, pattern, false);
            helper.assertTrue(remainder.isEmpty(), "Formed insertion rejected: accessible=" + center.canAccessExternalPatterns()
                    + ", mutable=" + center.canMutateExternalPatterns() + ", valid=" + handler.isItemValid(slot, pattern));
            helper.assertTrue(crystal.patterns().size() == 1
                            && crystal.patterns().getCompound(0).getInt("Slot") == slot % MolecularCenterPatternShards.shardSize(),
                    "External insertion must update crystal immediately: stored=" + handler.getStackInSlot(slot)
                            + ", shard=" + crystal.patterns());
            var drop = crystal.createDrop();
            helper.assertTrue(MolecularCenterCrystalBlockEntity.patternsFromItem(drop).size() == 1,
                    "A same-tick crystal drop retains the inserted pattern");
            handler.extractItem(slot, 1, false);
            helper.assertTrue(crystal.patterns().isEmpty(), "Last extraction immediately clears the crystal copy");
            var saved = center.saveWithFullMetadata(level.registryAccess());
            helper.assertTrue(!saved.contains(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_LIST),
                    "Formed controller save never duplicates the crystal library");
            center.onChunkUnloaded();
            level.removeBlockEntity(pos);
            var restored = BlockEntity.loadStatic(pos, level.getBlockState(pos), saved, level.registryAccess());
            helper.assertTrue(restored != null, "World save must restore the controller entity");
            level.setBlockEntity(restored);
        }).thenWaitUntil(() -> helper.assertTrue(((MolecularCenterBlockEntity) level.getBlockEntity(pos)).canAccessExternalPatterns(),
                "Reloaded controller must initialize its node and restore crystals")).thenExecute(() -> {
            var reloaded = (MolecularCenterBlockEntity) level.getBlockEntity(pos);
            var handler = reloaded.getExternalPatternInventory();
            int slot = Math.min(MolecularCenterPatternShards.shardSize(), handler.getSlots() - 1);
            var part = MolecularCenterPatternShards.crystals().get(slot / MolecularCenterPatternShards.shardSize());
            var crystalPos = MolecularCenterStructure.worldPos(pos, Direction.NORTH, part);
            var crystal = (MolecularCenterCrystalBlockEntity) level.getBlockEntity(crystalPos);
            helper.assertTrue(reloaded.getLogic().getFullPatternInventory().isEmpty(), "Empty library stays empty after reload");
            handler.insertItem(slot, pattern, false);
            var drop = crystal.createDrop();
            level.setBlock(crystalPos, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(handler.extractItem(slot, 1, false).isEmpty(), "Broken crystal blocks a second extraction immediately");
            reloaded.refreshStructure();
            helper.assertTrue(reloaded.getLogic().getFullPatternInventory().isEmpty(), "Damaged controller discards its non-owning mirror");
            var saved = reloaded.saveWithFullMetadata(level.registryAccess());
            helper.assertTrue(!saved.contains(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_LIST), "Damage cannot transfer crystal-owned patterns into controller NBT");
            level.setBlock(crystalPos, MolecularCenterStructure.partState(part.partType()), 3);
            reloaded.refreshStructure();
            helper.assertTrue(reloaded.getLogic().getFullPatternInventory().isEmpty(), "Blank replacement cannot resurrect the removed crystal's patterns");
            // Restore the recovered crystal then reform; exactly one pattern returns.
            level.setBlock(crystalPos, Blocks.AIR.defaultBlockState(), 3); reloaded.refreshStructure();
            level.setBlock(crystalPos, MolecularCenterStructure.partState(part.partType()), 3);
            var replacement = (MolecularCenterCrystalBlockEntity) level.getBlockEntity(crystalPos);
            replacement.loadWithComponents(drop.get(DataComponents.BLOCK_ENTITY_DATA).copyTag(), level.registryAccess());
            helper.assertTrue(replacement.patterns().size() == 1, "Recovered crystal must load its carried pattern: " + drop);
            reloaded.refreshStructure();
            var extracted = handler.extractItem(slot, 1, false);
            helper.assertTrue(ItemStack.matches(pattern, extracted), "Recovered crystal extract=" + extracted
                    + ", slot=" + handler.getStackInSlot(slot) + ", shard=" + replacement.patterns()
                    + ", ready=" + reloaded.canAccessExternalPatterns() + ", mutable=" + reloaded.canMutateExternalPatterns());
            helper.assertTrue(handler.extractItem(slot, 1, false).isEmpty(), "Recovered pattern cannot be extracted twice");
            handler.insertItem(0, pattern, false);
            reloaded.startDismantle(FakePlayerFactory.getMinecraft(level));
            helper.assertTrue(reloaded.isDismantling(), "Dismantle must start");
            helper.assertTrue(handler.extractItem(0, 1, false).isEmpty() && !handler.insertItem(1, pattern, false).isEmpty(),
                    "Cached external inventory cannot mutate while dismantling");
            var first = (MolecularCenterCrystalBlockEntity) level.getBlockEntity(
                    MolecularCenterStructure.worldPos(pos, Direction.NORTH, MolecularCenterPatternShards.crystals().get(0)));
            helper.assertTrue(first.patterns().size() == 1, "Dismantling leaves ownership in crystals");
            reloaded.clearContent();
            helper.assertTrue(first.patterns().size() == 1, "Clearing a departing controller cannot erase crystal-owned contents");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            System.out.println("PATTERN_CRYSTAL_TRANSFER_PASS: immediate shard updates, empty reload, break/repair, one-owner drops and dismantle lock");
        }).thenSucceed();
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> patterns(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var planks = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        var inputs = new ItemStack[9]; java.util.Arrays.fill(inputs, ItemStack.EMPTY); inputs[0] = new ItemStack(Items.OAK_LOG);
        var craft = PatternDetailsHelper.encodeCraftingPattern(planks, inputs, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var slab = (RecipeHolder<StonecutterRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("stone_slab_from_stone_stonecutting")).orElseThrow();
        var stonecut = PatternDetailsHelper.encodeStonecuttingPattern(slab, AEItemKey.of(Items.STONE), AEItemKey.of(Items.STONE_SLAB), false);
        var sword = (RecipeHolder<SmithingRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("netherite_sword_smithing")).orElseThrow();
        var smithing = PatternDetailsHelper.encodeSmithingTablePattern(sword, AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                AEItemKey.of(Items.DIAMOND_SWORD), AEItemKey.of(Items.NETHERITE_INGOT), AEItemKey.of(Items.NETHERITE_SWORD), false);
        return List.of(craft, stonecut, smithing);
    }
}
