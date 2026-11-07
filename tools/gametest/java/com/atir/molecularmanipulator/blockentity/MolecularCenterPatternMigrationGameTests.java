package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.cells.BasicCellInventory;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.glodblock.github.extendedae.common.parts.PartExPatternProvider;
import com.glodblock.github.extendedae.common.tileentities.TileExDrive;
import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Physical migration regressions and shared fixture for tools/gametest/pattern-migration.init.gradle. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterPatternMigrationGameTests {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MolecularCenterPatternMigrationGameTests.class);
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void publicProvidersAndFiniteDisks(GameTestHelper helper) {
        var fixture = fixture(helper, true, 1);
        var supported = patterns(helper);
        var processing = processing();
        var source = IActionSource.empty();
        for (var host : fixture.cellHosts) {
            var disk = AEItems.ITEM_CELL_1K.stack();
            var cell = BasicCellInventory.createInventory(disk, null);
            helper.assertTrue(cell != null, "Real finite item cell");
            for (var stack : supported) helper.assertTrue(cell.insert(AEItemKey.of(stack), 1, Actionable.MODULATE, source) == 1,
                    "Seed encoded supported pattern in disk");
            for (var stack : List.of(processing, AEItems.BLANK_PATTERN.stack(), AEItems.CRAFTING_PATTERN.stack())) {
                cell.insert(AEItemKey.of(stack), 1, Actionable.MODULATE, source);
            }
            if (host instanceof DriveBlockEntity drive) drive.getInternalInventory().setItemDirect(0, disk);
            else ((MEChestBlockEntity) host).setCell(disk);
        }
        helper.startSequence().thenWaitUntil(() -> nodesReady(helper, fixture))
                .thenExecute(() -> connect(fixture))
                .thenWaitUntil(() -> online(helper, fixture))
                .thenExecute(() -> {
                    var center = fixture.center;
                    for (var host : fixture.providers) {
                        var inventory = host.getTerminalPatternInventory();
                        for (int slot = 0; slot < supported.size(); slot++) inventory.setItemDirect(slot, supported.get(slot).copy());
                        inventory.setItemDirect(3, processing.copy());
                        inventory.setItemDirect(4, AEItems.CRAFTING_PATTERN.stack());
                        inventory.setItemDirect(5, AEItems.BLANK_PATTERN.stack());
                    }
                    // Hidden terminal rows are still existing ordinary providers on the same grid.
                    fixture.providers.getFirst().getConfigManager().putSetting(Settings.PATTERN_ACCESS_TERMINAL, YesNo.NO);
                    fixture.automatic.getAutoCraftPatternInventory().setItemDirect(0, supported.getFirst().copy());
                    center.getAutoCrafter().getPatternInventory().setItemDirect(0, supported.get(1).copy());
                    fixture.matrix.getPatternInventory().setItemDirect(0, processing.copy());
                    fixture.assembly.getLogic().getPatternInv().setItemDirect(0, processing.copy());
                    fixture.disconnected.getLogic().getPatternInv().setItemDirect(0, supported.getFirst().copy());
                    var proxy = new AggregateTrap(AEItemKey.of(named(supported.getFirst(), "private machine via external storage")));
                    IStorageProvider proxyProvider = mounts -> mounts.mount(proxy, 100_000);
                    var grid = center.getMainNode().getGrid();
                    var storageService = grid.getStorageService();
                    storageService.addGlobalStorageProvider(proxyProvider);
                    try {
                        var result = MolecularCenterPatternMigration.migrate(center, FakePlayerFactory.getMinecraft(helper.getLevel()));
                        helper.assertTrue(result.fromProviders() == fixture.providers.size() * 3,
                                "Every ordinary block/part provider contributes three physical patterns: " + result);
                        helper.assertTrue(result.fromStorageCells() == fixture.cellHosts.size() * 3,
                                "Drive and ME chest hosts contribute their actual stored patterns: " + result);
                        helper.assertTrue(result.moved() == 21 && !result.interrupted(), "Complete synchronous migration: " + result);
                        helper.assertTrue(proxy.extractions == 0, "Never extract from aggregate network storage/external machine proxies");
                        var counts = new KeyCounter();
                        int occupied = 0;
                        for (var stack : center.getLogic().getFullPatternInventory()) {
                            counts.add(AEItemKey.of(stack), stack.getCount()); occupied++;
                        }
                        helper.assertTrue(occupied == 21, "One physical item in each new library slot");
                        for (var stack : supported) helper.assertTrue(counts.get(AEItemKey.of(stack)) == 7,
                                "All three supported categories retain exact components and physical multiplicity");
                        for (var host : fixture.providers) {
                            var inventory = host.getTerminalPatternInventory();
                            for (int slot = 0; slot < 3; slot++) helper.assertTrue(inventory.getStackInSlot(slot).isEmpty(), "Provider source removed");
                            helper.assertTrue(ItemStack.matches(processing, inventory.getStackInSlot(3))
                                            && AEItems.CRAFTING_PATTERN.is(inventory.getStackInSlot(4))
                                            && AEItems.BLANK_PATTERN.is(inventory.getStackInSlot(5)),
                                    "Processing, invalid and blank patterns stay at their original provider slots");
                            for (var details : host.getLogic().getAvailablePatterns()) {
                                for (var moved : supported) helper.assertTrue(!details.getDefinition().equals(AEItemKey.of(moved)),
                                        "Removed provider patterns must be unpublished before returning");
                            }
                        }
                        for (var host : fixture.cellHosts) {
                            var cell = host.getOriginalCellInventory(0);
                            var available = cell.getAvailableStacks();
                            for (var stack : supported) helper.assertTrue(available.get(AEItemKey.of(stack)) == 0, "Disk source removed");
                            helper.assertTrue(available.get(AEItemKey.of(processing)) == 1
                                            && available.get(AEItemKey.of(AEItems.BLANK_PATTERN.stack())) == 1
                                            && available.get(AEItemKey.of(AEItems.CRAFTING_PATTERN.stack())) == 1,
                                    "Unsupported disk contents remain unchanged");
                            var disk = host instanceof DriveBlockEntity drive ? drive.getInternalInventory().getStackInSlot(0)
                                    : ((MEChestBlockEntity) host).getCell();
                            var persisted = BasicCellInventory.createInventory(disk.copy(), null);
                            for (var stack : supported) helper.assertTrue(persisted.getAvailableStacks().get(AEItemKey.of(stack)) == 0,
                                    "Cell changes are persisted, not merely changed in a transient handler");
                        }
                        helper.assertTrue(ItemStack.matches(supported.getFirst(), fixture.automatic.getAutoCraftPatternInventory().getStackInSlot(0)),
                                "Quantum automatic crafter's private inventory is never a source");
                        helper.assertTrue(ItemStack.matches(supported.get(1), center.getAutoCrafter().getPatternInventory().getStackInSlot(0)),
                                "Target's nine automatic-crafting slots are never sources or targets");
                        helper.assertTrue(ItemStack.matches(processing, fixture.matrix.getPatternInventory().getStackInSlot(0)),
                                "Unsupported processing patterns stay in public matrix containers too");
                        helper.assertTrue(ItemStack.matches(processing, fixture.assembly.getLogic().getPatternInv().getStackInSlot(0)),
                                "Network fabrication pattern assembly only supports processing; leave it intact");
                        helper.assertTrue(ItemStack.matches(supported.getFirst(), fixture.disconnected.getLogic().getPatternInv().getStackInSlot(0)),
                                "Disconnected providers are outside this network");
                        int crystalPatterns = 0;
                        for (var part : MolecularCenterPatternShards.crystals()) {
                            var pos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, part);
                            crystalPatterns += ((MolecularCenterCrystalBlockEntity) helper.getLevel().getBlockEntity(pos)).patterns().size();
                        }
                        helper.assertTrue(crystalPatterns == 21, "Committed target patterns reach the owning crystals immediately");
                        var again = MolecularCenterPatternMigration.migrate(center, FakePlayerFactory.getMinecraft(helper.getLevel()));
                        helper.assertTrue(again.moved() == 0, "Repeated migration neither copies nor invents patterns: " + again);
                    } finally {
                        storageService.removeGlobalStorageProvider(proxyProvider);
                    }
                    LOGGER.info("PATTERN_MIGRATION_NETWORK_PASS: public containers+parts, mounted disks+ME chest, exact data, unsupported retention, private-slot exclusion, processing retention, publication and crystals");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void menuButtonMigratesSegmentedSingleBlockAndDisk(GameTestHelper helper) {
        var fixture = fixture(helper, false, 41);
        var center = fixture.center;
        var single = place(helper, center.getBlockPos().east(80),
                ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState(), MolecularManipulatorBlockEntity.class);
        var drive = place(helper, center.getBlockPos().east(84), AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
        fixture.nodes.add(single.getMainNode()); fixture.nodes.add(drive.getMainNode()); fixture.cellHosts.add(drive);
        var supported = patterns(helper);
        var disk = AEItems.ITEM_CELL_1K.stack();
        var cell = BasicCellInventory.createInventory(disk, null);
        for (var stack : supported) cell.insert(AEItemKey.of(stack), 1, Actionable.MODULATE, IActionSource.empty());
        drive.getInternalInventory().setItemDirect(0, disk);
        helper.startSequence().thenWaitUntil(() -> nodesReady(helper, fixture))
                .thenExecute(() -> connect(fixture)).thenWaitUntil(() -> {
                    online(helper, fixture);
                    helper.assertTrue(single.getMainNode().isActive(), "Single-block source is active on the same grid");
                }).thenExecute(() -> {
                    var source = single.getTerminalPatternInventory();
                    var slots = List.of(0, 128, 359);
                    for (int index = 0; index < 3; index++) source.setItemDirect(slots.get(index), supported.get(index).copy());
                    source.setItemDirect(1, processing());
                    source.setItemDirect(2, AEItems.CRAFTING_PATTERN.stack());
                    source.setItemDirect(3, AEItems.BLANK_PATTERN.stack());
                    int segments = 0;
                    for (Object candidate : center.getMainNode().getGrid().getActiveMachines(MolecularManipulatorBlockEntity.class)) {
                        helper.assertTrue(candidate instanceof appeng.helpers.patternprovider.PatternContainer container
                                        && SegmentedPatternContainers.unwrap(container) == single,
                                "Actual active-machine query returns terminal segments for the physical source");
                        segments++;
                    }
                    helper.assertTrue(segments == 3, "Exercise actual 128/128/104 segment expansion");
                    var player = FakePlayerFactory.getMinecraft(helper.getLevel());
                    var previousMenu = player.containerMenu;
                    var previousPos = player.position();
                    try {
                        player.setPos(center.getBlockPos().getCenter());
                        var constructor = MolecularCenterMenu.class.getDeclaredConstructor(int.class,
                                net.minecraft.world.entity.player.Inventory.class, PatternProviderLogicHost.class);
                        constructor.setAccessible(true);
                        var menu = constructor.newInstance(401, player.getInventory(), center);
                        // A stale menu must not migrate either source.
                        menu.receiveClientAction("migrate_patterns", "");
                        helper.assertTrue(center.getLogic().getFullPatternInventory().isEmpty(), "Stale button request is rejected");
                        player.containerMenu = menu;
                        helper.assertTrue(menu.isValidMenu() && menu.stillValid(player), "The real migration menu is current and nearby");
                        menu.receiveClientAction("migrate_patterns", "");
                        var counts = new KeyCounter();
                        int occupied = 0;
                        for (var stack : center.getLogic().getFullPatternInventory()) {
                            counts.add(AEItemKey.of(stack), stack.getCount()); occupied++;
                        }
                        helper.assertTrue(occupied == 6, "One button moves three single-block and three disk patterns");
                        for (var stack : supported) helper.assertTrue(counts.get(AEItemKey.of(stack)) == 2,
                                "Exact components and duplicate physical items are retained");
                        for (int slot : slots) helper.assertTrue(source.getStackInSlot(slot).isEmpty(), "Even last/segmented source slots are deducted");
                        helper.assertTrue(ItemStack.matches(source.getStackInSlot(1), processing())
                                        && AEItems.CRAFTING_PATTERN.is(source.getStackInSlot(2))
                                        && AEItems.BLANK_PATTERN.is(source.getStackInSlot(3)), "Unsupported rows remain at source");
                        helper.assertTrue(drive.getOriginalCellInventory(0).getAvailableStacks().isEmpty(), "Mounted disk was deducted");
                        helper.assertTrue(BasicCellInventory.createInventory(disk.copy(), null).getAvailableStacks().isEmpty(), "Disk removal persisted");
                        menu.receiveClientAction("migrate_patterns", "");
                        helper.assertTrue(center.getLogic().getFullPatternInventory().getStackInSlot(6).isEmpty(), "Repeated button cannot copy the target");
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError("Cannot open the real migration menu", exception);
                    } finally {
                        player.containerMenu = previousMenu; player.setPos(previousPos);
                    }
                    LOGGER.info("PATTERN_MIGRATION_BUTTON_PASS: real menu action, stale rejection, single-block slots 0/128/359, disk, unsupported retention and exact six items");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void capacityAndSameTickCrystalDamagePreserveOwnership(GameTestHelper helper) {
        var fixture = fixture(helper, false, 2);
        var pattern = patterns(helper).getFirst();
        helper.startSequence().thenWaitUntil(() -> nodesReady(helper, fixture))
                .thenExecute(() -> connect(fixture))
                .thenWaitUntil(() -> online(helper, fixture))
                .thenExecute(() -> {
                    var center = fixture.center;
                    var source = fixture.providers.getFirst().getTerminalPatternInventory();
                    for (int slot = 0; slot < 3; slot++) source.setItemDirect(slot, pattern.copy());
                    int pages = ModConfig.PATTERN_PAGES.get();
                    try {
                        // Temporary config changes and the whole transfer occur in one server callback, not across ticks.
                        ModConfig.PATTERN_PAGES.set(1);
                        var inventory = center.getLogic().getFullPatternInventory();
                        for (int slot = 0; slot < 35; slot++) inventory.setItemDirect(slot, pattern.copy());
                        var result = MolecularCenterPatternMigration.migrate(center, FakePlayerFactory.getMinecraft(helper.getLevel()));
                        helper.assertTrue(result.moved() == 1 && result.destinationFull(), "A single free slot accepts a partial migration: " + result);
                        int left = 0;
                        for (var stack : source) left += stack.getCount();
                        helper.assertTrue(left == 2 && ItemStack.matches(pattern, inventory.getStackInSlot(35)),
                                "Full target leaves the remaining two patterns in the source without loss or copies");
                        helper.assertTrue(MolecularCenterPatternMigration.migrate(center, FakePlayerFactory.getMinecraft(helper.getLevel())).moved() == 0,
                                "A repeated full-target request cannot consume another source pattern");
                        // Raw source pattern must also survive a same-tick loss of target crystal ownership.
                        var part = MolecularCenterPatternShards.crystals().getFirst();
                        var crystalPos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, part);
                        helper.getLevel().setBlock(crystalPos, Blocks.AIR.defaultBlockState(), 3);
                        helper.assertTrue(!center.canMutateExternalPatterns(), "Broken target crystal immediately invalidates the mirror");
                        var blocked = MolecularCenterPatternMigration.migrate(center, FakePlayerFactory.getMinecraft(helper.getLevel()));
                        helper.assertTrue(blocked.moved() == 0 && blocked.interrupted(), "No migration against stale crystal ownership: " + blocked);
                        int after = 0;
                        for (var stack : source) after += stack.getCount();
                        helper.assertTrue(after == 2, "Target ownership failure never spends source patterns");
                    } finally {
                        ModConfig.PATTERN_PAGES.set(pages);
                    }
                    LOGGER.info("PATTERN_MIGRATION_CAPACITY_PASS: partial/full/idempotent transfer and same-tick target ownership lock");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void finiteCellDoesNotNeedAnUnsafeRefundPath(GameTestHelper helper) {
        var pattern = patterns(helper).getFirst();
        var key = AEItemKey.of(pattern);
        var cell = BasicCellInventory.createInventory(AEItems.ITEM_CELL_1K.stack(), null);
        var source = IActionSource.empty();
        cell.insert(key, 3, Actionable.MODULATE, source);
        for (boolean throwing : List.of(false, true)) {
            var target = new AppEngInternalInventory(1) {
                @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (!simulate && !throwing) return stack;
                    var remainder = super.insertItem(slot, stack, simulate);
                    if (!simulate) throw new IllegalStateException("Target callback failed after writing");
                    return remainder;
                }
            };
            helper.assertTrue(!MolecularCenterPatternTransfer.fromCell(cell, cell, key, source, target, 0, () -> true),
                    "Rejecting or throwing target cannot spend finite-cell patterns");
            helper.assertTrue(target.isEmpty() && cell.getAvailableStacks().get(key) == 3, "Cell/target rollback is exact");
        }
        var target = new AppEngInternalInventory(1);
        var mounted = new MEStorage() {
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource actionSource) {
                var removed = cell.extract(what, amount, mode, actionSource);
                if (mode == Actionable.MODULATE) throw new IllegalStateException("Cell save/LED callback threw after removal");
                return removed;
            }
            @Override public Component getDescription() { return Component.literal("Native cell with a throwing monitor"); }
        };
        helper.assertTrue(MolecularCenterPatternTransfer.fromCell(cell, mounted, key, source, target, 0, () -> true),
                "Observe the real backing decrement when a callback throws after extraction");
        helper.assertTrue(ItemStack.matches(pattern, target.getStackInSlot(0)) && cell.getAvailableStacks().get(key) == 2,
                "Exactly one item committed without refund insertion, loss or duplication");
        var denied = new MEStorage() {
            @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource actionSource) {
                return mode == Actionable.SIMULATE ? 1 : 0;
            }
            @Override public Component getDescription() { return Component.literal("Stale simulation"); }
        };
        var empty = new AppEngInternalInventory(1);
        helper.assertTrue(!MolecularCenterPatternTransfer.fromCell(cell, denied, key, source, empty, 0, () -> true)
                        && empty.isEmpty() && cell.getAvailableStacks().get(key) == 2,
                "A stale successful simulation is not a license to create a pattern");
        LOGGER.info("PATTERN_MIGRATION_CELL_TRANSACTION_PASS: actual finite AE2 cell, rejected/throwing target, post-extraction callback and stale simulation");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void realInventoryHostFailureDoesNotPoisonTheUploadIndex(GameTestHelper helper) {
        var fixture = fixture(helper, false, 15);
        var pattern = patterns(helper).getFirst();
        helper.startSequence().thenWaitUntil(() -> nodesReady(helper, fixture))
                .thenExecute(() -> connect(fixture))
                .thenWaitUntil(() -> online(helper, fixture))
                .thenExecute(() -> {
                    var center = fixture.center;
                    var logic = center.getLogic();
                    var source = fixture.providers.getFirst().getTerminalPatternInventory();
                    source.setItemDirect(0, pattern.copy());
                    helper.assertTrue(!logic.containsUploadPattern(pattern) && logic.firstUploadSlot() == 0,
                            "Initialize the real controller-owned index before the failing transaction");
                    var pos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH,
                            MolecularCenterPatternShards.crystals().getFirst());
                    var crystal = (MolecularCenterCrystalBlockEntity) helper.getLevel().getBlockEntity(pos);
                    var saved = crystal.patterns();
                    try {
                        // Inject inside the real inventory HOST, not after super.insertItem returns.
                        // AE2's notification guard must be released even when shard persistence throws.
                        var contents = MolecularCenterCrystalBlockEntity.class.getDeclaredField("patterns");
                        contents.setAccessible(true);
                        try {
                            contents.set(crystal, null);
                            var failed = MolecularCenterPatternMigration.migrate(center,
                                    FakePlayerFactory.getMinecraft(helper.getLevel()));
                            helper.assertTrue(failed.moved() == 0 && failed.interrupted(),
                                    "A real host callback failure is reported without spending the source: " + failed);
                        } finally {
                            contents.set(crystal, saved);
                        }
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError("Unable to inject the real shard persistence failure", exception);
                    }
                    helper.assertTrue(ItemStack.matches(pattern, source.getStackInSlot(0))
                                    && logic.getFullPatternInventory().isEmpty() && crystal.patterns().isEmpty(),
                            "Rollback retains the one physical pattern only at its source");
                    helper.assertTrue(!logic.containsUploadPattern(pattern) && logic.firstUploadSlot() == 0,
                            "A rolled-back pattern cannot remain indexed as a duplicate or occupy its empty slot");
                    var recovered = MolecularCenterPatternMigration.migrate(center,
                            FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(recovered.moved() == 1 && !recovered.interrupted()
                                    && logic.containsUploadPattern(pattern) && crystal.patterns().size() == 1,
                            "Notifications, index and owning crystal must recover on the next real write");
                    logic.getFullPatternInventory().extractItem(0, 1, false);
                    helper.assertTrue(!logic.containsUploadPattern(pattern) && logic.firstUploadSlot() == 0
                                    && crystal.patterns().isEmpty(),
                            "Subsequent removal still notifies; a released latch must not be a one-time repair");
                    LOGGER.info("PATTERN_MIGRATION_HOST_RECOVERY_PASS: actual MolecularCenterLogic callback failure, exact rollback, live index/crystal recovery and subsequent removal");
                }).thenSucceed();
    }

    // Package-private so the native encoding-menu suite uses this same real multiblock/grid fixture.
    record Fixture(MolecularCenterBlockEntity center, List<PatternProviderLogicHost> providers,
            List<IChestOrDrive> cellHosts, List<IManagedGridNode> nodes, IManagedGridNode controller,
            MolecularAutoCrafterBlockEntity automatic, TileAssemblerMatrixPattern matrix,
            MatterFabricationPatternAssemblyBlockEntity assembly, PatternProviderBlockEntity disconnected) {}

    static Fixture fixture(GameTestHelper helper, boolean comprehensive, int index) {
        var pos = new BlockPos(1_600_000 + index * 512, 100, 16_000 + index * 512);
        var level = helper.getLevel();
        for (var chunk : MolecularCenterStructure.chunkFootprint(pos, Direction.NORTH,
                MolecularCenterStructure.StructureLayout.CURRENT, MolecularCenterStructure.StructureLayout.CURRENT)) {
            level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        for (var part : MolecularCenterPatternShards.crystals()) {
            level.setBlock(MolecularCenterStructure.worldPos(pos, Direction.NORTH, part), Blocks.AIR.defaultBlockState(), 3);
        }
        for (var part : MolecularCenterStructure.parts()) if (!MolecularCenterStructure.isController(part)) {
            level.setBlock(MolecularCenterStructure.worldPos(pos, Direction.NORTH, part), MolecularCenterStructure.partState(part.partType()), 3);
        }
        var center = place(helper, pos, ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState(), MolecularCenterBlockEntity.class);
        var controller = place(helper, pos.east(64), AEBlocks.CONTROLLER.block().defaultBlockState(), ControllerBlockEntity.class);
        var energy = place(helper, pos.east(68), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), AENetworkedBlockEntity.class);
        var nativeProvider = place(helper, pos.east(72), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), PatternProviderBlockEntity.class);
        var providers = new ArrayList<PatternProviderLogicHost>(); providers.add(nativeProvider);
        var cells = new ArrayList<IChestOrDrive>();
        var nodes = new ArrayList<IManagedGridNode>(List.of(center.getMainNode(), energy.getMainNode(), nativeProvider.getMainNode()));
        MolecularAutoCrafterBlockEntity automatic = null;
        TileAssemblerMatrixPattern matrix = null;
        MatterFabricationPatternAssemblyBlockEntity assembly = null;
        PatternProviderBlockEntity disconnected = null;
        if (comprehensive) {
            var extended = place(helper, pos.east(76), extendedBlock("ex_pattern_provider"), TileExPatternProvider.class);
            providers.add(extended); nodes.add(extended.getMainNode());
            var cable = place(helper, pos.east(80), AEBlocks.CABLE_BUS.block().defaultBlockState(), CableBusBlockEntity.class);
            cable.addPart(AEParts.SMART_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), null, null);
            var nativePart = cable.addPart(AEParts.PATTERN_PROVIDER.get(), Direction.NORTH, null);
            var item = (IPartItem<?>) BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("extendedae", "ex_pattern_provider_part"));
            var extendedPart = (PartExPatternProvider) cable.addPart(item, Direction.SOUTH, null);
            providers.add(nativePart); providers.add(extendedPart);
            nodes.add(nativePart.getMainNode()); nodes.add(extendedPart.getMainNode());
            var drive = place(helper, pos.east(84), AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
            var exDrive = place(helper, pos.east(88), extendedBlock("ex_drive"), TileExDrive.class);
            var chest = place(helper, pos.east(92), AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
            cells.add(drive); cells.add(exDrive); cells.add(chest);
            nodes.add(drive.getMainNode()); nodes.add(exDrive.getMainNode()); nodes.add(chest.getMainNode());
            automatic = place(helper, pos.east(96), ModContent.MOLECULAR_AUTO_CRAFTER.get().defaultBlockState(), MolecularAutoCrafterBlockEntity.class);
            matrix = place(helper, pos.east(100), extendedBlock("assembler_matrix_pattern"), TileAssemblerMatrixPattern.class);
            assembly = place(helper, pos.east(104), ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), MatterFabricationPatternAssemblyBlockEntity.class);
            nodes.add(automatic.getMainNode()); nodes.add(matrix.getMainNode()); nodes.add(assembly.getMainNode());
            disconnected = place(helper, pos.west(64), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), PatternProviderBlockEntity.class);
        }
        return new Fixture(center, providers, cells, nodes, controller.getMainNode(), automatic, matrix, assembly, disconnected);
    }

    static <T> T place(GameTestHelper helper, BlockPos pos, BlockState state, Class<T> type) {
        var level = helper.getLevel();
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true); level.getChunkAt(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos, state, 3);
        return type.cast(level.getBlockEntity(pos));
    }

    private static BlockState extendedBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("extendedae", path)).defaultBlockState();
    }

    static void nodesReady(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.controller.getNode() != null, "Controller node initialized");
        for (var node : fixture.nodes) helper.assertTrue(node.getNode() != null, "Fixture grid node initialized");
    }

    static void connect(Fixture fixture) {
        for (var node : fixture.nodes) GridHelper.createConnection(fixture.controller.getNode(), node.getNode());
    }

    static void online(GameTestHelper helper, Fixture fixture) {
        helper.assertTrue(fixture.center.isFormed() && fixture.center.canMutateExternalPatterns(), "Formed, owned crystal library initialized");
        var grid = fixture.center.getMainNode().getGrid();
        helper.assertTrue(fixture.center.getMainNode().isActive(), "Destination has power and a channel");
        for (var provider : fixture.providers) helper.assertTrue(provider.getGrid() == grid
                        && grid.getActiveMachines(provider.getClass()).contains(provider),
                "Ordinary provider is active on the destination network");
        for (var host : fixture.cellHosts) helper.assertTrue(host.getActionableNode() != null && host.getActionableNode().isActive()
                        && host.getActionableNode().getGrid() == grid && host.getOriginalCellInventory(0) != null,
                "Cell host is online and has initialized its mounted disk");
        if (fixture.automatic != null) {
            helper.assertTrue(fixture.automatic.getMainNode().isActive(), "Excluded automatic machine is also online");
            helper.assertTrue(fixture.matrix.getMainNode().getGrid() == grid, "Excluded AssemblerMatrix is attached to this same grid");
        }
    }

    private static ItemStack named(ItemStack original, String name) {
        var copy = original.copy(); copy.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return copy;
    }

    private static ItemStack processing() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIRT), 1)));
    }

    @SuppressWarnings("unchecked")
    static List<ItemStack> patterns(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var planks = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        var inputs = new ItemStack[9]; Arrays.fill(inputs, ItemStack.EMPTY); inputs[0] = new ItemStack(Items.OAK_LOG);
        var craft = PatternDetailsHelper.encodeCraftingPattern(planks, inputs, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var slab = (RecipeHolder<StonecutterRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("stone_slab_from_stone_stonecutting")).orElseThrow();
        var stonecut = PatternDetailsHelper.encodeStonecuttingPattern(slab, AEItemKey.of(Items.STONE), AEItemKey.of(Items.STONE_SLAB), false);
        var sword = (RecipeHolder<SmithingRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("netherite_sword_smithing")).orElseThrow();
        var smithing = PatternDetailsHelper.encodeSmithingTablePattern(sword, AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                AEItemKey.of(Items.DIAMOND_SWORD), AEItemKey.of(Items.NETHERITE_INGOT), AEItemKey.of(Items.NETHERITE_SWORD), false);
        return List.of(named(craft, "Migration crafting"), named(stonecut, "Migration stonecutting"), named(smithing, "Migration smithing"));
    }

    private static final class AggregateTrap implements MEStorage {
        private final AEItemKey key;
        private int extractions;
        AggregateTrap(AEItemKey key) { this.key = key; }
        @Override public void getAvailableStacks(KeyCounter out) { out.add(key, 1); }
        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) { extractions++; return 0; }
        @Override public Component getDescription() { return Component.literal("Forbidden aggregate/external-machine pattern source"); }
    }
}
