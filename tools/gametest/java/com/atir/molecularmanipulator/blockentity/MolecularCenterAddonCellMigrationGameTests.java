package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cells.BasicCellInventory;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Opt-in actual addon StorageCell implementations, never a fake handler that bypasses discovery. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterAddonCellMigrationGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void publicDisksMigrateThroughStorageCellContract(GameTestHelper helper) {
        var fixture = MolecularCenterPatternMigrationGameTests.fixture(helper, false, 42);
        var drive = MolecularCenterPatternMigrationGameTests.place(helper, fixture.center().getBlockPos().east(84),
                AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
        fixture.nodes().add(drive.getMainNode()); fixture.cellHosts().add(drive);
        var supported = MolecularCenterPatternMigrationGameTests.patterns(helper);
        var processing = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIRT), 1)));
        var unsupported = List.of(processing, AEItems.CRAFTING_PATTERN.stack(), AEItems.BLANK_PATTERN.stack());
        var diskIds = List.of("omni_cell_1k", "complex_omni_cell_1k");
        for (int slot = 0; slot < diskIds.size(); slot++) {
            var id = ResourceLocation.fromNamespaceAndPath("ae2omnicells", diskIds.get(slot));
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The opted-in real addon must register " + id);
            var disk = new ItemStack(BuiltInRegistries.ITEM.get(id));
            var cell = StorageCells.getCellInventory(disk, null);
            helper.assertTrue(cell != null && !(cell instanceof BasicCellInventory),
                    "Exercise actual third-party StorageCell implementations excluded by the old exact-class policy");
            for (var stack : supported) helper.assertTrue(cell.insert(AEItemKey.of(stack), 2, Actionable.MODULATE, IActionSource.empty()) == 2,
                    "Each addon cell physically owns two of each supported category");
            for (var stack : unsupported) helper.assertTrue(cell.insert(AEItemKey.of(stack), 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                    "Keep unsupported pattern types as exact persistence sentinels");
            cell.persist();
            drive.getInternalInventory().setItemDirect(slot, disk);
        }
        helper.startSequence().thenWaitUntil(() -> MolecularCenterPatternMigrationGameTests.nodesReady(helper, fixture))
                .thenExecute(() -> MolecularCenterPatternMigrationGameTests.connect(fixture))
                .thenWaitUntil(() -> {
                    MolecularCenterPatternMigrationGameTests.online(helper, fixture);
                    helper.assertTrue(drive.getOriginalCellInventory(1) != null, "Both real addon disks are mounted");
                }).thenExecute(() -> {
                    var grid = fixture.center().getMainNode().getGrid();
                    // Prime the actual ME cache, then require migration to invalidate it after direct-cell extraction.
                    for (var stack : supported) helper.assertTrue(grid.getStorageService().getCachedInventory().get(AEItemKey.of(stack)) == 4,
                            "Network initially advertises all four physical copies");
                    var result = MolecularCenterPatternMigration.migrate(fixture.center(), FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(result.fromProviders() == 0 && result.fromStorageCells() == 12 && result.moved() == 12
                                    && !result.interrupted() && !result.destinationFull(),
                            "The common StorageCell path must move every actual addon-disk item: " + result);
                    var counts = new KeyCounter();
                    int occupied = 0;
                    for (var stack : fixture.center().getLogic().getFullPatternInventory()) {
                        helper.assertTrue(stack.getCount() == 1, "Each physical item has a separate target slot");
                        counts.add(AEItemKey.of(stack), stack.getCount()); occupied++;
                    }
                    helper.assertTrue(occupied == 12, "No re-encoding or duplicate collapse");
                    for (var stack : supported) {
                        var key = AEItemKey.of(stack);
                        helper.assertTrue(counts.get(key) == 4, "Exact components and four physical copies survive");
                        helper.assertTrue(grid.getStorageService().getCachedInventory().get(key) == 0, "ME cache no longer advertises removed items");
                    }
                    for (int slot = 0; slot < diskIds.size(); slot++) {
                        var mounted = drive.getOriginalCellInventory(slot).getAvailableStacks();
                        var disk = drive.getInternalInventory().getStackInSlot(slot);
                        var persisted = StorageCells.getCellInventory(disk.copy(), null).getAvailableStacks();
                        for (var stack : supported) helper.assertTrue(mounted.get(AEItemKey.of(stack)) == 0 && persisted.get(AEItemKey.of(stack)) == 0,
                                "Removal affects the mounted inventory and the real saved addon-cell codec");
                        for (var stack : unsupported) helper.assertTrue(mounted.get(AEItemKey.of(stack)) == 1 && persisted.get(AEItemKey.of(stack)) == 1,
                                "Processing, invalid and blank patterns retain their exact quantity/components");
                    }
                    helper.assertTrue(MolecularCenterPatternMigration.migrate(fixture.center(), FakePlayerFactory.getMinecraft(helper.getLevel())).moved() == 0,
                            "A repeated request cannot invent addon-disk patterns");
                    org.slf4j.LoggerFactory.getLogger(MolecularCenterAddonCellMigrationGameTests.class).info("PATTERN_MIGRATION_ADDON_CELL_PASS: public StorageCell implementations, twelve physical items, cache invalidation and persisted exact removal");
                }).thenSucceed();
    }
}
