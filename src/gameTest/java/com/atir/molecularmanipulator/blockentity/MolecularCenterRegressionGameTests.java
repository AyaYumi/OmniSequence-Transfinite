package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.cells.BasicCellInventory;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Selected legacy migration/native-encode regressions, with real finite cells, shards and AE2 mixins. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterRegressionGameTests {
    private static final ResourceLocation STONE_SLAB = ResourceLocation.withDefaultNamespace("stone_slab_from_stone_stonecutting");

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 800)
    public static void menuMigratesPhysicalProvidersSegmentsAndFiniteCellExactlyOnce(GameTestHelper helper) {
        var fixture = new ArrayNetwork(helper, true);
        var supported = patterns(helper, true);
        var processing = processing();
        var disk = AEItems.ITEM_CELL_1K.stack();
        var cell = BasicCellInventory.createInventory(disk, null);
        helper.assertTrue(cell != null, "Use a real finite AE2 item cell");
        for (var stack : supported) helper.assertTrue(cell.insert(AEItemKey.of(stack), 1,
                Actionable.MODULATE, IActionSource.empty()) == 1, "Seed one physical pattern per category");
        for (var stack : List.of(processing, AEItems.BLANK_PATTERN.stack(), AEItems.CRAFTING_PATTERN.stack()))
            cell.insert(AEItemKey.of(stack), 1, Actionable.MODULATE, IActionSource.empty());
        cell.persist();
        fixture.drive.getInternalInventory().setItemDirect(0, disk);
        fixture.ready().thenExecute(() -> {
            var center = fixture.center;
            var ordinary = fixture.provider.getTerminalPatternInventory();
            var segmented = fixture.single.getTerminalPatternInventory();
            int[] slots = {0, 128, 359};
            for (int index = 0; index < 3; index++) {
                ordinary.setItemDirect(index, supported.get(index).copy());
                segmented.setItemDirect(slots[index], supported.get(index).copy());
            }
            ordinary.setItemDirect(3, processing.copy());
            ordinary.setItemDirect(4, AEItems.CRAFTING_PATTERN.stack());
            ordinary.setItemDirect(5, AEItems.BLANK_PATTERN.stack());
            center.getAutoCrafter().getPatternInventory().setItemDirect(0, supported.getFirst().copy());
            var grid = center.getMainNode().getGrid();
            int segments = 0;
            for (Object candidate : grid.getActiveMachines(MolecularManipulatorBlockEntity.class)) {
                helper.assertTrue(candidate instanceof appeng.helpers.patternprovider.PatternContainer container
                                && SegmentedPatternContainers.unwrap(container) == fixture.single,
                        "The actual active-machine query exposes terminal segments of one physical source");
                segments++;
            }
            helper.assertTrue(segments == 3, "Exercise the real 128/128/104 terminal expansion");
            var trap = new AggregateTrap(AEItemKey.of(supported.getFirst()));
            IStorageProvider proxy = mounts -> mounts.mount(trap, 100_000);
            grid.getStorageService().addGlobalStorageProvider(proxy);
            try {
                fixture.player.setPos(center.getBlockPos().getCenter());
                var menu = arrayMenu(fixture.player, center);
                menu.receiveClientAction("migrate_patterns", "");
                helper.assertTrue(center.getLogic().getFullPatternInventory().isEmpty(), "A stale menu cannot migrate");
                fixture.player.containerMenu = menu;
                helper.assertTrue(menu.isValidMenu() && menu.stillValid(fixture.player), "Use the current nearby real server menu");
                menu.receiveClientAction("migrate_patterns", "");
                var library = center.getLogic().getFullPatternInventory();
                var counts = new KeyCounter();
                for (var stack : library) if (!stack.isEmpty()) counts.add(AEItemKey.of(stack), stack.getCount());
                helper.assertTrue(amount(library) == 9 && crystalCount(fixture) == 9,
                        "Six provider items and three disk items commit to the owning crystals immediately");
                for (var stack : supported) helper.assertTrue(counts.get(AEItemKey.of(stack)) == 3,
                        "Exact components and duplicate physical items are conserved, not deduplicated");
                for (int index = 0; index < 3; index++) helper.assertTrue(ordinary.getStackInSlot(index).isEmpty()
                                && segmented.getStackInSlot(slots[index]).isEmpty(),
                        "Every physical source, including the last terminal segment, is deducted");
                helper.assertTrue(ItemStack.matches(processing, ordinary.getStackInSlot(3))
                                && AEItems.CRAFTING_PATTERN.is(ordinary.getStackInSlot(4))
                                && AEItems.BLANK_PATTERN.is(ordinary.getStackInSlot(5)),
                        "Unsupported processing, invalid and blank provider patterns stay in their slots");
                var persisted = BasicCellInventory.createInventory(disk.copy(), null).getAvailableStacks();
                for (var stack : supported) helper.assertTrue(persisted.get(AEItemKey.of(stack)) == 0,
                        "Finite-disk deductions are persisted, not just changed in a temporary handler");
                helper.assertTrue(persisted.get(AEItemKey.of(processing)) == 1
                                && persisted.get(AEItemKey.of(AEItems.BLANK_PATTERN.stack())) == 1
                                && persisted.get(AEItemKey.of(AEItems.CRAFTING_PATTERN.stack())) == 1,
                        "Unsupported disk contents remain physically owned by the disk");
                helper.assertTrue(trap.extractions == 0, "Never extract from aggregate/external-machine storage proxies");
                helper.assertTrue(ItemStack.matches(supported.getFirst(), center.getAutoCrafter().getPatternInventory().getStackInSlot(0)),
                        "The nine private automatic-crafting slots are not migration sources or destinations");
                for (var host : List.of(fixture.provider, fixture.single))
                    for (var details : host.getLogic().getAvailablePatterns())
                        for (var stack : supported) helper.assertTrue(!details.getDefinition().equals(AEItemKey.of(stack)),
                                "Normal extraction notifications revoke the source's recipes");
                menu.receiveClientAction("migrate_patterns", "");
                helper.assertTrue(amount(library) == 9 && crystalCount(fixture) == 9,
                        "Repeated menu requests cannot copy segments, the target library or the finite disk");
            } finally {
                grid.getStorageService().removeGlobalStorageProvider(proxy);
            }
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 800)
    public static void partialMigrationAndSameTickCrystalLossDoNotSpendRemainingSource(GameTestHelper helper) {
        var fixture = new ArrayNetwork(helper, true);
        fixture.ready().thenExecute(() -> {
            var center = fixture.center;
            var pattern = patterns(helper, true).getFirst();
            var source = fixture.provider.getTerminalPatternInventory();
            for (int slot = 0; slot < 3; slot++) source.setItemDirect(slot, pattern.copy());
            int pages = ModConfig.PATTERN_PAGES.get();
            try {
                // Process-global overrides never span a server tick or another fixture callback.
                ModConfig.PATTERN_PAGES.set(1);
                var library = center.getLogic().getFullPatternInventory();
                for (int slot = 0; slot < 35; slot++) library.setItemDirect(slot, pattern.copy());
                var moved = MolecularCenterPatternMigration.migrate(center, fixture.player);
                helper.assertTrue(moved.moved() == 1 && moved.destinationFull() && amount(source) == 2,
                        "One free active slot accepts one real item; the other two stay at source: " + moved);
                helper.assertTrue(MolecularCenterPatternMigration.migrate(center, fixture.player).moved() == 0
                                && amount(source) == 2, "A repeated full-target request cannot consume a source item");
                var crystal = MolecularCenterPatternShards.crystals().getFirst();
                fixture.level.setBlock(MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, crystal),
                        Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(!center.canMutateExternalPatterns(), "Actual same-tick crystal loss invalidates mirror ownership");
                var blocked = MolecularCenterPatternMigration.migrate(center, fixture.player);
                helper.assertTrue(blocked.moved() == 0 && blocked.interrupted() && amount(source) == 2,
                        "Stale target ownership never spends the remaining physical patterns: " + blocked);
            } finally {
                ModConfig.PATTERN_PAGES.set(pages);
            }
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 800)
    public static void nativeEncodingActionUploadsCraftingStonecuttingAndSmithing(GameTestHelper helper) {
        var fixture = new ArrayNetwork(helper, true);
        fixture.ready().thenExecute(() -> {
            var menu = fixture.openEncoder();
            helper.assertTrue(!fixture.center.isAutomaticPatternUploadEnabled(), "A new Array starts opted out, without an optional upload core");
            setUpload(fixture, true);
            var blanks = fixture.terminal.getLogic().getBlankPatternInv();
            var encoded = fixture.terminal.getLogic().getEncodedPatternInv();
            var library = fixture.center.getLogic().getFullPatternInventory();
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(3));
            var modes = List.of(EncodingMode.CRAFTING, EncodingMode.STONECUTTING, EncodingMode.SMITHING_TABLE);
            for (int index = 0; index < modes.size(); index++) {
                prepare(fixture, menu, modes.get(index));
                helper.assertTrue(encoded.isEmpty(), "No pre-encoded seed may hide a missing encode mixin");
                menu.encode(); // Deliberately do not call SequenceArrayPatternUpload directly.
                helper.assertTrue(encoded.isEmpty() && amount(blanks) == 2 - index
                                && amount(library) == index + 1 && crystalCount(fixture) == index + 1
                                && ItemStack.matches(patterns(helper, false).get(index), library.getStackInSlot(index)),
                        modes.get(index) + ": one blank becomes one exact, live-recipe-decodable crystal-owned item");
                helper.assertTrue(PatternDetailsHelper.decodePattern(library.getStackInSlot(index), fixture.level) != null,
                        "Decode against the real server recipe manager");
            }
            menu.encode();
            helper.assertTrue(encoded.isEmpty() && blanks.isEmpty() && amount(library) == 3,
                    "No-blank encode cannot invent another pattern");
            helper.assertTrue(fixture.center.getAutoCrafter().getPatternInventory().isEmpty()
                            && fixture.provider.getTerminalPatternInventory().isEmpty(),
                    "Uploads go only to the main Array library");
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 800)
    public static void nativeUploadToggleDuplicatesCapacityAndCrystalOwnershipConserveItems(GameTestHelper helper) {
        var fixture = new ArrayNetwork(helper, true);
        fixture.ready().thenExecute(() -> {
            var menu = fixture.openEncoder();
            var blanks = fixture.terminal.getLogic().getBlankPatternInv();
            var encoded = fixture.terminal.getLogic().getEncodedPatternInv();
            var library = fixture.center.getLogic().getFullPatternInventory();
            var craft = patterns(helper, false).getFirst();
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(5));
            prepare(fixture, menu, EncodingMode.CRAFTING);
            menu.encode();
            helper.assertTrue(ItemStack.matches(craft, encoded.getStackInSlot(0)) && amount(blanks) == 4 && library.isEmpty(),
                    "Disabled upload retains the real encoded output");
            setUpload(fixture, true);
            helper.assertTrue(ItemStack.matches(craft, encoded.getStackInSlot(0)) && library.isEmpty(),
                    "Toggling/broadcasting alone must not consume an existing terminal output");
            menu.encode();
            helper.assertTrue(encoded.isEmpty() && amount(blanks) == 4 && amount(library) == 1,
                    "Re-encoding an occupied output transfers that item without spending another blank");
            var stored = library.getStackInSlot(0).copy();
            var metadata = new CompoundTag();
            metadata.putString("encodePlayer", "legacy encoder metadata");
            stored.set(DataComponents.CUSTOM_DATA, CustomData.of(metadata));
            library.setItemDirect(0, stored.copy());
            for (int repeat = 0; repeat < 3; repeat++) {
                menu.encode();
                helper.assertTrue(encoded.isEmpty() && amount(blanks) == 4 && amount(library) == 1
                                && ItemStack.matches(stored, library.getStackInSlot(0)),
                        "Duplicate encoding refunds exactly one blank and preserves the owned item's metadata");
            }
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(64));
            menu.encode();
            helper.assertTrue(fixture.player.getInventory().countItem(AEItems.BLANK_PATTERN.asItem()) == 0
                            && amount(blanks) == 64 && encoded.isEmpty() && amount(library) == 1,
                    "Fresh native encoding spends one blank before recycling it into the now-free input space");

            // Reach the overflow branch with a real retained output: native encode while opted out,
            // then explicitly add one blank to refill the input. Do not seed an encoded pattern.
            setUpload(fixture, false);
            menu.encode();
            helper.assertTrue(ItemStack.matches(craft, encoded.getStackInSlot(0)) && amount(blanks) == 63
                            && amount(library) == 1,
                    "Opted-out native encoding retains one physical duplicate and spends exactly one blank");
            helper.assertTrue(blanks.insertItem(0, AEItems.BLANK_PATTERN.stack(), false).isEmpty()
                            && amount(blanks) == 64, "One explicitly supplied blank refills the input around the retained output");
            setUpload(fixture, true);
            helper.assertTrue(ItemStack.matches(craft, encoded.getStackInSlot(0)) && amount(blanks) == 64,
                    "Re-enabling upload alone leaves the retained duplicate and full input untouched");
            int physicalBefore = amount(blanks) + amount(encoded) + amount(library)
                    + fixture.player.getInventory().countItem(AEItems.BLANK_PATTERN.asItem());
            menu.encode();
            int refund = fixture.player.getInventory().countItem(AEItems.BLANK_PATTERN.asItem());
            helper.assertTrue(refund == 1 && amount(blanks) == 64 && encoded.isEmpty() && amount(library) == 1
                            && crystalCount(fixture) == 1 && ItemStack.matches(stored, library.getStackInSlot(0))
                            && amount(blanks) + amount(encoded) + amount(library) + refund == physicalBefore,
                    "Re-encoding a retained duplicate with full input refunds exactly one real blank to the player, conserving every item");
            menu.encode();
            helper.assertTrue(fixture.player.getInventory().countItem(AEItems.BLANK_PATTERN.asItem()) == 1
                            && amount(blanks) == 64 && encoded.isEmpty() && amount(library) == 1,
                    "A subsequent fresh duplicate recycles its own blank, not another refund to the player");
            prepare(fixture, menu, EncodingMode.PROCESSING);
            menu.encode();
            helper.assertTrue(!encoded.isEmpty() && !fixture.center.isSupportedExternalPattern(encoded.getStackInSlot(0))
                            && amount(blanks) == 63 && amount(library) == 1,
                    "Unsupported processing output stays retrievable in the terminal");
            encoded.extractItem(0, 1, false);
            int pages = ModConfig.PATTERN_PAGES.get();
            try {
                ModConfig.PATTERN_PAGES.set(1);
                for (int slot = 1; slot < 36; slot++) library.setItemDirect(slot, craft.copy());
                prepare(fixture, menu, EncodingMode.STONECUTTING);
                menu.encode();
                var cut = patterns(helper, false).get(1);
                helper.assertTrue(ItemStack.matches(cut, encoded.getStackInSlot(0)) && amount(blanks) == 62
                                && amount(library) == 36 && fixture.center.getLogic().firstUploadSlot() < 0,
                        "Full configured capacity retains the encoded item, rather than using inactive pages");
                menu.encode();
                helper.assertTrue(ItemStack.matches(cut, encoded.getStackInSlot(0)) && amount(blanks) == 62,
                        "Repeated full-library encode spends no extra blank");
                library.extractItem(35, 1, false);
                menu.encode();
                helper.assertTrue(encoded.isEmpty() && amount(blanks) == 62 && amount(library) == 36
                                && ItemStack.matches(cut, library.getStackInSlot(35)) && crystalCount(fixture) == 36,
                        "One freed slot accepts the retained physical item without another blank charge");
            } finally {
                ModConfig.PATTERN_PAGES.set(pages);
            }
            fixture.level.setBlock(MolecularCenterStructure.worldPos(fixture.center.getBlockPos(), Direction.NORTH,
                    MolecularCenterPatternShards.crystals().getFirst()), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!fixture.center.canAcceptExternalPatternUpload(), "Same-tick physical crystal loss locks upload");
            prepare(fixture, menu, EncodingMode.CRAFTING);
            menu.encode();
            helper.assertTrue(ItemStack.matches(craft, encoded.getStackInSlot(0)) && amount(blanks) == 61,
                    "A stale mirrored duplicate must not be recycled after crystal ownership is lost");
            encoded.extractItem(0, 1, false);
            prepare(fixture, menu, EncodingMode.SMITHING_TABLE);
            menu.encode();
            helper.assertTrue(ItemStack.matches(patterns(helper, false).get(2), encoded.getStackInSlot(0)) && amount(blanks) == 60,
                    "A new supported item also remains at the terminal while destination ownership is invalid");
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 800)
    public static void nativeUploadCannotUseOrRecycleAgainstAnotherPoweredGrid(GameTestHelper helper) {
        var fixture = new ArrayNetwork(helper, false);
        fixture.ready().thenExecute(() -> {
            var menu = fixture.openEncoder();
            fixture.center.setAutomaticPatternUploadEnabled(true);
            var library = fixture.center.getLogic().getFullPatternInventory();
            var supported = patterns(helper, false);
            library.setItemDirect(0, supported.getFirst().copy());
            var blanks = fixture.terminal.getLogic().getBlankPatternInv();
            var encoded = fixture.terminal.getLogic().getEncodedPatternInv();
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(2));
            prepare(fixture, menu, EncodingMode.CRAFTING);
            menu.encode();
            helper.assertTrue(ItemStack.matches(supported.getFirst(), encoded.extractItem(0, 1, false))
                            && amount(blanks) == 1 && amount(library) == 1,
                    "A duplicate on a different genuinely powered grid cannot trigger recycling");
            prepare(fixture, menu, EncodingMode.STONECUTTING);
            menu.encode();
            helper.assertTrue(ItemStack.matches(supported.get(1), encoded.getStackInSlot(0)) && blanks.isEmpty()
                            && amount(library) == 1 && ItemStack.matches(supported.getFirst(), library.getStackInSlot(0)),
                    "A new supported pattern cannot cross grid boundaries either");
        }).thenSucceed();
    }

    private static final class ArrayNetwork extends ServerRegressionFixture {
        final MolecularCenterBlockEntity center;
        final PatternProviderBlockEntity provider;
        final MolecularManipulatorBlockEntity single;
        final DriveBlockEntity drive;
        final PatternEncodingTerminalPart terminal;
        final ServerPlayer player;
        final IManagedGridNode controller, terminalPower;
        final List<IManagedGridNode> nodes;
        final boolean sameGrid;

        ArrayNetwork(GameTestHelper helper, boolean sameGrid) {
            super(helper);
            this.sameGrid = sameGrid;
            for (var chunk : MolecularCenterStructure.chunkFootprint(origin, Direction.NORTH,
                    MolecularCenterStructure.StructureLayout.CURRENT, MolecularCenterStructure.StructureLayout.CURRENT)) force(chunk);
            for (var part : MolecularCenterStructure.parts()) if (!MolecularCenterStructure.isController(part))
                place(MolecularCenterStructure.worldPos(origin, Direction.NORTH, part), MolecularCenterStructure.partState(part.partType()));
            center = place(origin, ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState(), MolecularCenterBlockEntity.class);
            controller = place(origin.east(64), AEBlocks.CONTROLLER.block().defaultBlockState(), ControllerBlockEntity.class).getMainNode();
            var energy = place(origin.east(68), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), AENetworkedBlockEntity.class);
            provider = place(origin.east(72), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(), PatternProviderBlockEntity.class);
            drive = place(origin.east(76), AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            single = place(origin.east(80), ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState(), MolecularManipulatorBlockEntity.class);
            player = player("array-regression", origin);
            var cable = place(origin.east(84), AEBlocks.CABLE_BUS.block().defaultBlockState(), CableBusBlockEntity.class);
            cable.addPart(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT), null, player);
            terminal = (PatternEncodingTerminalPart) cable.addPart(AEParts.PATTERN_ENCODING_TERMINAL.asItem(), Direction.NORTH, player);
            terminalPower = place(origin.east(88), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), AENetworkedBlockEntity.class).getMainNode();
            nodes = new ArrayList<>(List.of(center.getMainNode(), energy.getMainNode(), provider.getMainNode(),
                    drive.getMainNode(), single.getMainNode()));
        }

        GameTestSequence ready() {
            return helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(controller.getNode() != null && terminalPower.getNode() != null
                                && terminal.getMainNode().getNode() != null, "Real controller/terminal nodes initialize normally");
                for (var node : nodes) helper.assertTrue(node.getNode() != null, "Every physical fixture node initializes");
            }).thenExecute(() -> {
                for (var node : nodes) GridHelper.createConnection(controller.getNode(), node.getNode());
                GridHelper.createConnection(terminalPower.getNode(), terminal.getMainNode().getNode());
                if (sameGrid) GridHelper.createConnection(controller.getNode(), terminal.getMainNode().getNode());
            }).thenWaitUntil(() -> {
                helper.assertTrue(center.isFormed() && center.canAcceptExternalPatternUpload(), "Current blueprint forms a real operational crystal-owned Array");
                helper.assertTrue(provider.getMainNode().isActive() && single.getMainNode().isActive()
                                && drive.getMainNode().isActive() && drive.getOriginalCellInventory(0) != null
                                && terminal.getMainNode().isActive(), "Providers, finite cell and terminal have real power/channels");
                helper.assertTrue((terminal.getMainNode().getGrid() == center.getMainNode().getGrid()) == sameGrid,
                        "The fixture must use genuinely shared/separate AE2 grids");
            });
        }

        PatternEncodingTermMenu openEncoder() {
            player.setPos(terminal.getBlockEntity().getBlockPos().getCenter());
            var menu = new PatternEncodingTermMenu(310, player.getInventory(), terminal);
            player.containerMenu = menu;
            menu.broadcastChanges();
            helper.assertTrue(menu.isValidMenu() && menu.stillValid(player) && menu.getTarget() == terminal
                            && terminal.getLogic().getBlankPatternInv().isEmpty() && terminal.getLogic().getEncodedPatternInv().isEmpty(),
                    "Valid current native encoding menu, initially containing no physical seed items");
            return menu;
        }
    }

    private static MolecularCenterMenu arrayMenu(ServerPlayer player, MolecularCenterBlockEntity center) {
        try {
            var constructor = MolecularCenterMenu.class.getDeclaredConstructor(int.class, Inventory.class, PatternProviderLogicHost.class);
            constructor.setAccessible(true);
            return constructor.newInstance(410, player.getInventory(), center);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot construct the real Array menu", error);
        }
    }

    private static void setUpload(ArrayNetwork fixture, boolean enabled) {
        var player = fixture.player;
        var previousMenu = player.containerMenu;
        var previousPos = player.position();
        try {
            player.setPos(fixture.center.getBlockPos().getCenter());
            var menu = arrayMenu(player, fixture.center);
            player.containerMenu = menu;
            fixture.helper.assertTrue(menu.isValidMenu() && menu.stillValid(player), "Current nearby Array menu");
            menu.receiveClientAction("set_pattern_upload", Boolean.toString(enabled));
            fixture.helper.assertTrue(fixture.center.isAutomaticPatternUploadEnabled() == enabled && menu.automaticPatternUpload == enabled,
                    "Actual registered server button action changes and synchronizes upload state");
            player.containerMenu = previousMenu;
            menu.receiveClientAction("set_pattern_upload", Boolean.toString(!enabled));
            fixture.helper.assertTrue(fixture.center.isAutomaticPatternUploadEnabled() == enabled, "Stale menu cannot toggle upload");
        } finally {
            player.containerMenu = previousMenu;
            player.setPos(previousPos);
        }
    }

    private static void prepare(ArrayNetwork fixture, PatternEncodingTermMenu menu, EncodingMode mode) {
        menu.clear(); // Ghost inputs only; never delete a physical blank or encoded output.
        var logic = fixture.terminal.getLogic();
        logic.setMode(mode);
        logic.setSubstitution(false);
        logic.setFluidSubstitution(false);
        var inputs = logic.getEncodedInputInv();
        switch (mode) {
            case CRAFTING -> inputs.setStack(0, new GenericStack(AEItemKey.of(Items.OAK_LOG), 1));
            case STONECUTTING -> {
                inputs.setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 1));
                logic.setStonecuttingRecipeId(STONE_SLAB);
            }
            case SMITHING_TABLE -> {
                inputs.setStack(0, new GenericStack(AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), 1));
                inputs.setStack(1, new GenericStack(AEItemKey.of(Items.DIAMOND_SWORD), 1));
                inputs.setStack(2, new GenericStack(AEItemKey.of(Items.NETHERITE_INGOT), 1));
            }
            case PROCESSING -> {
                inputs.setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 1));
                logic.getEncodedOutputInv().setStack(0, new GenericStack(AEItemKey.of(Items.DIRT), 1));
            }
        }
        menu.broadcastChanges();
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> patterns(GameTestHelper helper, boolean named) {
        var recipes = helper.getLevel().getRecipeManager();
        var planks = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        var inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);
        inputs[0] = new ItemStack(Items.OAK_LOG);
        var craft = PatternDetailsHelper.encodeCraftingPattern(planks, inputs, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        var slab = (RecipeHolder<StonecutterRecipe>) (RecipeHolder<?>) recipes.byKey(STONE_SLAB).orElseThrow();
        var cut = PatternDetailsHelper.encodeStonecuttingPattern(slab, AEItemKey.of(Items.STONE), AEItemKey.of(Items.STONE_SLAB), false);
        var sword = (RecipeHolder<SmithingRecipe>) (RecipeHolder<?>) recipes.byKey(ResourceLocation.withDefaultNamespace("netherite_sword_smithing")).orElseThrow();
        var smith = PatternDetailsHelper.encodeSmithingTablePattern(sword, AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                AEItemKey.of(Items.DIAMOND_SWORD), AEItemKey.of(Items.NETHERITE_INGOT), AEItemKey.of(Items.NETHERITE_SWORD), false);
        var result = List.of(craft, cut, smith);
        if (named) for (int index = 0; index < result.size(); index++)
            result.get(index).set(DataComponents.CUSTOM_NAME, Component.literal("Physical migration category " + index));
        return result;
    }

    private static ItemStack processing() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.STONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIRT), 1)));
    }

    private static int amount(InternalInventory inventory) {
        int result = 0;
        for (var stack : inventory) result += stack.getCount();
        return result;
    }

    private static int crystalCount(ArrayNetwork fixture) {
        int result = 0;
        for (var part : MolecularCenterPatternShards.crystals()) result += ((MolecularCenterCrystalBlockEntity)
                fixture.level.getBlockEntity(MolecularCenterStructure.worldPos(fixture.center.getBlockPos(), Direction.NORTH, part))).patterns().size();
        return result;
    }

    private static final class AggregateTrap implements MEStorage {
        final AEItemKey pattern;
        int extractions;
        AggregateTrap(AEItemKey pattern) { this.pattern = pattern; }
        @Override public void getAvailableStacks(KeyCounter out) { out.add(pattern, 1); }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) { extractions++; return 0; }
        @Override public Component getDescription() { return Component.literal("Not a physical migration source"); }
    }
}
