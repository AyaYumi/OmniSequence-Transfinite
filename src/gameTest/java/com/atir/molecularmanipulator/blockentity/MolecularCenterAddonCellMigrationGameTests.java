package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cells.BasicCellInventory;
import appeng.util.inv.AppEngInternalInventory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Opt-in actual addon StorageCells, discovered and migrated through AE2's public contract. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterAddonCellMigrationGameTests {
    private static final List<String> DISK_IDS = List.of("omni_cell_1k", "complex_omni_cell_1k");
    private static final String CELL_CLASS = "com.wintercogs.ae2omnicells.common.me.AEUniversalCellInventory";

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void publicDisksMigrateThroughStorageCellContract(GameTestHelper helper) {
        var addonCellType = loadAddonCell();
        var fixture = MolecularCenterPatternMigrationGameTests.fixture(helper, false, 42);
        var drive = MolecularCenterPatternMigrationGameTests.place(helper, fixture.center().getBlockPos().east(84),
                AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
        fixture.nodes().add(drive.getMainNode());
        fixture.cellHosts().add(drive);
        var supported = MolecularCenterPatternMigrationGameTests.patterns(helper);
        for (var stack : supported) {
            helper.assertTrue(stack.hasTag() && PatternDetailsHelper.decodePattern(stack, helper.getLevel())
                            instanceof IMolecularAssemblerSupportedPattern && fixture.center().isSupportedExternalPattern(stack),
                    "Use real recipe-decoded crafting, stonecutting and smithing patterns carrying exact NBT");
        }
        var processing = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.STONE), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIRT), 1)});
        var unsupported = List.of(processing, AEItems.CRAFTING_PATTERN.stack(), AEItems.BLANK_PATTERN.stack());
        var expectedRetained = new HashMap<AEKey, Long>();
        for (var stack : unsupported) expectedRetained.merge(AEItemKey.of(stack), 1L, Long::sum);
        for (int slot = 0; slot < DISK_IDS.size(); slot++) {
            var id = new ResourceLocation("ae2omnicells", DISK_IDS.get(slot));
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "The opted-in real addon registers " + id);
            var disk = new ItemStack(BuiltInRegistries.ITEM.get(id));
            var cell = StorageCells.getCellInventory(disk, null);
            helper.assertTrue(addonCellType.isInstance(cell) && !(cell instanceof BasicCellInventory),
                    "Exercise the real third-party StorageCell excluded by the old exact-class policy: " + id);
            for (var stack : supported) {
                helper.assertTrue(cell.insert(AEItemKey.of(stack), 2, Actionable.MODULATE, IActionSource.empty()) == 2,
                        "Each addon cell physically owns two of each supported category");
            }
            for (var stack : unsupported) {
                helper.assertTrue(cell.insert(AEItemKey.of(stack), 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                        "Keep unsupported pattern types as exact persistence sentinels");
            }
            cell.persist();
            assertRollback(helper, cell, disk, supported.get(0));
            drive.getInternalInventory().setItemDirect(slot, disk);
        }
        helper.startSequence().thenWaitUntil(() -> MolecularCenterPatternMigrationGameTests.nodesReady(helper, fixture))
                .thenExecute(() -> MolecularCenterPatternMigrationGameTests.connect(fixture))
                .thenWaitUntil(() -> {
                    MolecularCenterPatternMigrationGameTests.online(helper, fixture);
                    for (int slot = 0; slot < DISK_IDS.size(); slot++) {
                        helper.assertTrue(addonCellType.isInstance(drive.getOriginalCellInventory(slot)),
                                "Both real addon disks are mounted through the normal drive lifecycle");
                    }
                    // Prime the actual ME cache before migration; readiness must include both mounts.
                    var cache = fixture.center().getGrid().getStorageService().getCachedInventory();
                    for (var stack : supported) helper.assertTrue(cache.get(AEItemKey.of(stack)) == 4,
                            "Network initially advertises all four physical copies");
                }).thenExecute(() -> {
                    var center = fixture.center();
                    helper.assertTrue(center.getLogic().getFullPatternInventory().isEmpty()
                                    && center.getTerminalPatternInventory().size() >= 12,
                            "The destination starts empty with space for twelve physical items");
                    var result = MolecularCenterPatternMigration.migrate(center, FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(result.fromProviders() == 0 && result.fromStorageCells() == 12 && result.moved() == 12
                                    && !result.interrupted() && !result.destinationFull(),
                            "The common StorageCell path moves every actual addon-disk item: " + result);
                    physicalState(helper, fixture, drive, supported, expectedRetained);
                }).thenWaitUntil(() -> {
                    var center = fixture.center();
                    var service = center.getGrid().getCraftingService();
                    for (var stack : supported) {
                        var details = PatternDetailsHelper.decodePattern(stack, helper.getLevel());
                        helper.assertTrue(service.getCraftingFor(details.getPrimaryOutput().what()).stream()
                                        .anyMatch(published -> published.getDefinition().equals(AEItemKey.of(stack))),
                                "Actual grid crafting service publishes each migrated recipe");
                    }
                }).thenExecute(() -> {
                    var again = MolecularCenterPatternMigration.migrate(fixture.center(), FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(again.fromProviders() == 0 && again.fromStorageCells() == 0 && again.moved() == 0
                                    && !again.interrupted() && !again.destinationFull(),
                            "A repeated request cannot invent addon-disk patterns: " + again);
                    physicalState(helper, fixture, drive, supported, expectedRetained);
                    org.slf4j.LoggerFactory.getLogger(MolecularCenterAddonCellMigrationGameTests.class).info(
                            "PATTERN_MIGRATION_ADDON_CELL_PASS: real public StorageCells, target rollback, twelve exact NBT items, "
                                    + "ME cache invalidation, persisted removal, unsupported retention, owning crystals and idempotence");
                }).thenSucceed();
    }

    private static void assertRollback(GameTestHelper helper, StorageCell cell, ItemStack disk, ItemStack pattern) {
        var before = storageCounts(cell);
        for (boolean throwing : List.of(false, true)) {
            // Only the failing destination is controlled; the source is the actual addon cell and codec.
            var target = new AppEngInternalInventory(1) {
                @Override
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (!simulate && !throwing) return stack;
                    var remainder = super.insertItem(slot, stack, simulate);
                    if (!simulate) throw new IllegalStateException("Target callback failed after writing an addon-cell pattern");
                    return remainder;
                }
            };
            helper.assertTrue(!MolecularCenterPatternTransfer.fromCell(cell, cell, AEItemKey.of(pattern), IActionSource.empty(),
                            target, 0, () -> true),
                    "Rejecting or throwing target cannot spend real addon-cell patterns");
            cell.persist();
            var persisted = StorageCells.getCellInventory(ItemStack.of(disk.save(new CompoundTag())), null);
            helper.assertTrue(target.isEmpty() && storageCounts(cell).equals(before)
                            && persisted != null && storageCounts(persisted).equals(before),
                    "Addon cell/target rollback conserves every physical item and its persisted NBT");
        }
    }

    private static void physicalState(GameTestHelper helper, MolecularCenterPatternMigrationGameTests.Fixture fixture,
            DriveBlockEntity drive, List<ItemStack> supported, Map<AEKey, Long> expectedRetained) {
        var center = fixture.center();
        var inventory = center.getLogic().getFullPatternInventory();
        var counts = new KeyCounter();
        int occupied = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            helper.assertTrue(stack.getCount() == 1, "Each physical item has a separate target slot");
            counts.add(AEItemKey.of(stack), stack.getCount());
            occupied++;
        }
        helper.assertTrue(occupied == 12, "Exactly twelve physical items survive without re-encoding or duplicate collapse");
        var cache = center.getGrid().getStorageService().getCachedInventory();
        for (var stack : supported) {
            var key = AEItemKey.of(stack);
            helper.assertTrue(counts.get(key) == 4, "Exact NBT and four physical copies of each supported recipe survive");
            helper.assertTrue(cache.get(key) == 0, "ME cache no longer advertises removed items");
        }
        for (var entry : expectedRetained.entrySet()) {
            helper.assertTrue(cache.get(entry.getKey()) == entry.getValue() * DISK_IDS.size(),
                    "ME cache retains both disks' unsupported items with their exact NBT and quantity");
        }
        for (int slot = 0; slot < DISK_IDS.size(); slot++) {
            var mounted = drive.getOriginalCellInventory(slot);
            var disk = drive.getInternalInventory().getStackInSlot(slot);
            var savedDisk = disk.save(new CompoundTag());
            var roundTripped = ItemStack.of(savedDisk);
            var persisted = StorageCells.getCellInventory(roundTripped, null);
            helper.assertTrue(ItemStack.matches(disk, roundTripped) && persisted != null,
                    "The actual addon disk ItemStack and NBT survive their native save/load codec");
            helper.assertTrue(storageCounts(mounted).equals(expectedRetained) && storageCounts(persisted).equals(expectedRetained),
                    "Mounted and saved disks retain only the exact processing, invalid and blank pattern sentinels");
        }
        var shards = MolecularCenterPatternShards.split(MolecularCenterPatternShards.inventoryList(inventory));
        int crystalItems = 0;
        for (int index = 0; index < MolecularCenterPatternShards.crystals().size(); index++) {
            var part = MolecularCenterPatternShards.crystals().get(index);
            var pos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, part);
            var crystal = (MolecularCenterCrystalBlockEntity) helper.getLevel().getBlockEntity(pos);
            var expected = shards.get(index);
            helper.assertTrue(crystal != null && crystal.patterns().equals(expected),
                    "Owning crystal stores the exact slot-local NBT for shard " + index);
            helper.assertTrue(crystal.saveWithFullMetadata()
                            .getList(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_COMPOUND).equals(expected),
                    "Owning crystal's real BE codec preserves its committed shard");
            var drop = ItemStack.of(crystal.createDrop().save(new CompoundTag()));
            helper.assertTrue(!drop.isEmpty() && MolecularCenterCrystalBlockEntity.patternsFromItem(drop).equals(expected),
                    "Real crystal drop preserves every owned pattern and its exact NBT");
            crystalItems += crystal.patterns().size();
        }
        helper.assertTrue(crystalItems == 12, "All twelve migrated addon-cell items reach their owning crystals immediately");
    }

    private static Map<AEKey, Long> storageCounts(MEStorage storage) {
        var counts = new HashMap<AEKey, Long>();
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getLongValue() > 0) counts.put(entry.getKey(), entry.getLongValue());
        }
        return counts;
    }

    private static Class<?> loadAddonCell() {
        try {
            return Class.forName(CELL_CLASS);
        } catch (ClassNotFoundException | LinkageError failure) {
            throw new AssertionError("Requires -PgameTestPatternStorageJar=<real ae2omnicells Forge 1.1.6 jar>: " + CELL_CLASS, failure);
        }
    }
}
