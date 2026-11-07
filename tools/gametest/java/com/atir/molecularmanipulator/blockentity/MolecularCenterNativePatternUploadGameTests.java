package com.atir.molecularmanipulator.blockentity;

import static com.atir.molecularmanipulator.blockentity.MolecularCenterPatternMigrationGameTests.place;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real AE2 encode actions, physical item accounting, BE persistence and BE update streams. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterNativePatternUploadGameTests {
    private static final String LEGACY_CORE = "matrix_upload_core_inventory";
    private static final String UPLOAD = "automatic_pattern_upload";
    private static final String PRIMARY_ESCROW = "auto_craft_primary_escrow";
    private static final ResourceLocation STONE_SLAB = ResourceLocation.withDefaultNamespace("stone_slab_from_stone_stonecutting");

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void realMenuEncodeUploadsAllThreeSupportedKinds(GameTestHelper helper) {
        withEncodingFixture(helper, 3, true, fixture -> {
            var center = fixture.array.center();
            var library = center.getLogic().getFullPatternInventory();
            helper.assertTrue(!center.isAutomaticPatternUploadEnabled(), "A new Array starts with upload disabled and no installed core");
            center.setAutomaticPatternUploadEnabled(true);
            var blanks = fixture.terminal.getLogic().getBlankPatternInv();
            var encoded = fixture.terminal.getLogic().getEncodedPatternInv();
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(3));
            var modes = List.of(EncodingMode.CRAFTING, EncodingMode.STONECUTTING, EncodingMode.SMITHING_TABLE);
            for (int index = 0; index < modes.size(); index++) {
                var mode = modes.get(index);
                var expected = expectedPattern(helper, mode);
                prepareEncoding(fixture, mode);
                helper.assertTrue(encoded.isEmpty(), "No pre-encoded pattern may hide a missing encode hook");

                // The transformed encode method must trigger the upload without calling the upload helper here.
                fixture.menu.encode();

                helper.assertTrue(encoded.isEmpty() && amount(blanks) == 2 - index,
                        mode + ": encode consumes exactly one blank and moves the actual output out of the terminal");
                helper.assertTrue(amount(library) == index + 1 && ItemStack.matches(expected, library.getStackInSlot(index)),
                        mode + ": one exact encoded item reaches the real main library, not a synthesized copy");
                helper.assertTrue(PatternDetailsHelper.decodePattern(library.getStackInSlot(index), helper.getLevel()) != null,
                        mode + ": uploaded item must decode against the live server recipe manager");
                helper.assertTrue(crystalPatternCount(helper, center) == index + 1,
                        mode + ": owning crystals must be updated before encode returns");
                helper.assertTrue(center.getAutoCrafter().getPatternInventory().isEmpty()
                                && fixture.array.providers().getFirst().getTerminalPatternInventory().isEmpty(),
                        "Upload must not route into the nine private auto-crafting slots or an ordinary provider");
            }
            prepareEncoding(fixture, EncodingMode.CRAFTING);
            fixture.menu.encode();
            helper.assertTrue(encoded.isEmpty() && blanks.isEmpty() && amount(library) == 3,
                    "An encode click with no blank/output left cannot invent a pattern or a duplicate refund");
            try {
                var restore = MolecularCenterBlockEntity.class.getDeclaredMethod("loadPatternShards");
                restore.setAccessible(true);
                restore.invoke(center);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Unable to reload the real crystal-owned library", exception);
            }
            for (var mode : modes) helper.assertTrue(center.getLogic().containsUploadPattern(expectedPattern(helper, mode)),
                    "Raw crystal NBT restoration must invalidate and rebuild the upload index: " + mode);
            helper.assertTrue(center.getLogic().firstUploadSlot() == 3, "Restored index knows all occupied slots");
            System.out.println("NATIVE_PATTERN_ENCODE_PASS: real PatternEncodingTermMenu.encode, crafting+stonecutting+smithing, blanks=3->0 library=3 crystals=3, no-blank rejection, index crystal reload");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void buttonToggleAndDuplicateRecycleConservePhysicalPatterns(GameTestHelper helper) {
        withEncodingFixture(helper, 4, true, fixture -> {
            var center = fixture.array.center();
            var library = center.getLogic().getFullPatternInventory();
            var blanks = fixture.terminal.getLogic().getBlankPatternInv();
            var encoded = fixture.terminal.getLogic().getEncodedPatternInv();
            var expected = expectedPattern(helper, EncodingMode.CRAFTING);
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(2));
            prepareEncoding(fixture, EncodingMode.CRAFTING);
            fixture.menu.encode();
            assertRetained(helper, fixture, expected, 1, 0, "Disabled upload");

            setUploadThroughButton(helper, fixture, true);
            fixture.menu.broadcastChanges();
            helper.assertTrue(ItemStack.matches(expected, encoded.getStackInSlot(0)) && library.isEmpty(),
                    "Enabling/broadcasting must not itself consume a retained pattern; upload is encode-event driven");
            fixture.menu.encode(); // Re-encoding the occupied output must not spend another blank.
            helper.assertTrue(encoded.isEmpty() && amount(blanks) == 1 && amount(library) == 1,
                    "Enabled re-encode transfers the existing physical pattern without another blank charge");
            var stored = library.getStackInSlot(0).copy();
            var encoderData = new CompoundTag();
            encoderData.putString("encodePlayer", "Another encoder from an older terminal");
            stored.set(DataComponents.CUSTOM_DATA, CustomData.of(encoderData));
            library.setItemDirect(0, stored.copy());
            for (int repeat = 0; repeat < 5; repeat++) {
                fixture.menu.encode();
                helper.assertTrue(encoded.isEmpty() && amount(blanks) == 1 && amount(library) == 1
                                && ItemStack.matches(stored, library.getStackInSlot(0)) && crystalPatternCount(helper, center) == 1,
                        "Repeated duplicate encode returns one blank and never copies or changes the owned pattern");
            }
            setUploadThroughButton(helper, fixture, false);
            fixture.menu.encode();
            assertRetained(helper, fixture, expected, 0, 1, "Disabled duplicate is NOT recycled");

            // Recycle an occupied output while the blank slot is already full: the real player inventory owns the refund.
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(64));
            helper.assertTrue(playerBlanks(fixture.player) == 0, "Refund destination starts empty");
            setUploadThroughButton(helper, fixture, true);
            fixture.menu.encode();
            helper.assertTrue(encoded.isEmpty() && amount(blanks) == 64 && playerBlanks(fixture.player) == 1
                            && amount(library) == 1 && ItemStack.matches(stored, library.getStackInSlot(0)),
                    "Full local blank slot refunds exactly one real blank to the player, with no new library item");
            System.out.println("NATIVE_PATTERN_TOGGLE_DUPLICATE_PASS: actual Array button action, disabled retention, occupied-output re-encode, repeated recycling, full-blank-slot player refund");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void unsupportedAndFullLibraryKeepEncodedItemsAtTerminal(GameTestHelper helper) {
        withEncodingFixture(helper, 5, true, fixture -> {
            var center = fixture.array.center();
            center.setAutomaticPatternUploadEnabled(true);
            var library = center.getLogic().getFullPatternInventory();
            var blanks = fixture.terminal.getLogic().getBlankPatternInv();
            var encoded = fixture.terminal.getLogic().getEncodedPatternInv();
            blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(3));
            prepareEncoding(fixture, EncodingMode.PROCESSING);
            fixture.menu.encode();
            // Native terminal encoding preserves the full sparse input/output lists.
            var processingInputs = new GenericStack[fixture.terminal.getLogic().getEncodedInputInv().size()];
            var processingOutputs = new GenericStack[fixture.terminal.getLogic().getEncodedOutputInv().size()];
            processingInputs[0] = new GenericStack(AEItemKey.of(Items.STONE), 1);
            processingOutputs[0] = new GenericStack(AEItemKey.of(Items.DIRT), 1);
            var processing = PatternDetailsHelper.encodeProcessingPattern(
                    java.util.Arrays.asList(processingInputs), java.util.Arrays.asList(processingOutputs));
            assertRetained(helper, fixture, processing, 2, 0, "Unsupported processing pattern");
            fixture.menu.encode();
            assertRetained(helper, fixture, processing, 2, 0, "Repeated unsupported re-encode");
            helper.assertTrue(ItemStack.matches(processing, encoded.extractItem(0, 1, false)),
                    "The unsupported physical pattern remains retrievable by the user");
            int pages = ModConfig.PATTERN_PAGES.get();
            try {
                // No ticks elapse with this process-global config override; other fixtures cannot observe it.
                ModConfig.PATTERN_PAGES.set(1);
                var filler = expectedPattern(helper, EncodingMode.SMITHING_TABLE);
                for (int slot = 0; slot < 36; slot++) library.setItemDirect(slot, filler.copy());
                prepareEncoding(fixture, EncodingMode.SMITHING_TABLE);
                fixture.menu.encode();
                helper.assertTrue(encoded.isEmpty() && amount(blanks) == 2 && amount(library) == 36,
                        "A duplicate still recycles its one blank when every active library slot is already full");
                var expected = expectedPattern(helper, EncodingMode.CRAFTING);
                prepareEncoding(fixture, EncodingMode.CRAFTING);
                fixture.menu.encode();
                assertRetained(helper, fixture, expected, 1, 36, "Full active library");
                fixture.menu.encode();
                assertRetained(helper, fixture, expected, 1, 36, "Repeated full-library re-encode");
                for (int slot = 0; slot < 36; slot++) helper.assertTrue(ItemStack.matches(filler, library.getStackInSlot(slot)),
                        "A failed upload cannot overwrite any existing library item");
                for (int slot = 36; slot < library.size(); slot++) helper.assertTrue(library.getStackInSlot(slot).isEmpty(),
                        "Upload cannot bypass configured capacity by using inactive pages");
                helper.assertTrue(ItemStack.matches(filler, library.extractItem(35, 1, false)), "Free exactly one real library slot");
                fixture.menu.encode();
                helper.assertTrue(encoded.isEmpty() && amount(blanks) == 1 && amount(library) == 36
                                && ItemStack.matches(expected, library.getStackInSlot(35)) && crystalPatternCount(helper, center) == 36,
                        "The retained pattern transfers when room returns, without spending another blank or duplicating it");
            } finally {
                ModConfig.PATTERN_PAGES.set(pages);
            }
            System.out.println("NATIVE_PATTERN_CAPACITY_PASS: real processing encode retained/retrievable, full/hidden pages unchanged, one-slot recovery without another blank");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void foreignNetworkDoesNotUploadOrRecycleDuplicates(GameTestHelper helper) {
        withEncodingFixture(helper, 6, false, fixture -> {
            var center = fixture.array.center();
            center.setAutomaticPatternUploadEnabled(true);
            var library = center.getLogic().getFullPatternInventory();
            var craft = expectedPattern(helper, EncodingMode.CRAFTING);
            library.setItemDirect(0, craft.copy());
            fixture.terminal.getLogic().getBlankPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack(2));
            prepareEncoding(fixture, EncodingMode.CRAFTING);
            fixture.menu.encode();
            assertRetained(helper, fixture, craft, 1, 1, "Matching pattern on another powered grid must NOT cause recycling");
            var removed = fixture.terminal.getLogic().getEncodedPatternInv().extractItem(0, 1, false);
            helper.assertTrue(ItemStack.matches(craft, removed), "Foreign-network duplicate remains physically retrievable");
            prepareEncoding(fixture, EncodingMode.STONECUTTING);
            fixture.menu.encode();
            assertRetained(helper, fixture, expectedPattern(helper, EncodingMode.STONECUTTING), 0, 1,
                    "Enabled, operational Array on a different grid must NOT receive a new supported pattern");
            helper.assertTrue(ItemStack.matches(craft, library.getStackInSlot(0)), "Foreign Array contents remain unchanged");
            System.out.println("NATIVE_PATTERN_NETWORK_SCOPE_PASS: two genuinely separate powered grids, supported and duplicate patterns retained");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void poweredButUnformedArrayCannotUploadOrRecycleOwnedPatterns(GameTestHelper helper) {
        withEncodingFixture(helper, 9, true, true, fixture -> {
            var inactive = fixture.unformed;
            inactive.setAutomaticPatternUploadEnabled(true);
            helper.assertTrue(inactive.getMainNode().isActive() && !inactive.isFormed() && !inactive.isOperational()
                            && inactive.canMutateExternalPatterns() && !inactive.canAcceptExternalPatternUpload(),
                    "Use a genuinely powered, channelled, unformed Array with accessible controller-owned inventory, not a locked mock");
            helper.assertTrue(!fixture.array.center().isAutomaticPatternUploadEnabled(), "The formed control Array must remain opted out");
            var library = inactive.getLogic().getFullPatternInventory();
            var craft = expectedPattern(helper, EncodingMode.CRAFTING);
            library.setItemDirect(0, craft.copy());
            fixture.terminal.getLogic().getBlankPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack(2));
            prepareEncoding(fixture, EncodingMode.CRAFTING);
            fixture.menu.encode();
            assertRetained(helper, fixture, craft, 1, 0, "Unformed same-grid Array cannot recycle its owned duplicate");
            helper.assertTrue(ItemStack.matches(craft, fixture.terminal.getLogic().getEncodedPatternInv().extractItem(0, 1, false)),
                    "The physical output stays retrievable while the opted-in destination is not operational");
            prepareEncoding(fixture, EncodingMode.STONECUTTING);
            fixture.menu.encode();
            assertRetained(helper, fixture, expectedPattern(helper, EncodingMode.STONECUTTING), 0, 0,
                    "Unformed same-grid Array cannot accept a new supported pattern");
            helper.assertTrue(amount(library) == 1 && ItemStack.matches(craft, library.getStackInSlot(0)),
                    "The inactive Array's own library must remain unchanged");
            System.out.println("NATIVE_PATTERN_OPERATIONAL_GATE_PASS: real powered/channelled but unformed same-grid Array, writable owned inventory, supported and duplicate outputs retained");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void sameTickCrystalDamageBlocksUploadAndDuplicateRecycle(GameTestHelper helper) {
        withEncodingFixture(helper, 7, true, fixture -> {
            var center = fixture.array.center();
            center.setAutomaticPatternUploadEnabled(true);
            var craft = expectedPattern(helper, EncodingMode.CRAFTING);
            center.getLogic().getFullPatternInventory().setItemDirect(0, craft.copy());
            fixture.terminal.getLogic().getBlankPatternInv().setItemDirect(0, AEItems.BLANK_PATTERN.stack(2));
            var crystal = MolecularCenterPatternShards.crystals().getFirst();
            var pos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, crystal);
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(!center.canAcceptExternalPatternUpload(), "Actual same-tick crystal damage must lock the destination");
            prepareEncoding(fixture, EncodingMode.CRAFTING);
            fixture.menu.encode();
            helper.assertTrue(ItemStack.matches(craft, fixture.terminal.getLogic().getEncodedPatternInv().getStackInSlot(0))
                            && amount(fixture.terminal.getLogic().getBlankPatternInv()) == 1,
                    "A stale mirrored duplicate must not spend/recycle the encoded pattern against a broken destination");
            helper.assertTrue(ItemStack.matches(craft, fixture.terminal.getLogic().getEncodedPatternInv().extractItem(0, 1, false)),
                    "The first physical pattern remains recoverable");
            prepareEncoding(fixture, EncodingMode.STONECUTTING);
            fixture.menu.encode();
            helper.assertTrue(ItemStack.matches(expectedPattern(helper, EncodingMode.STONECUTTING),
                            fixture.terminal.getLogic().getEncodedPatternInv().getStackInSlot(0))
                            && fixture.terminal.getLogic().getBlankPatternInv().isEmpty(),
                    "New supported patterns also stay in the terminal while crystal ownership is invalid");
            System.out.println("NATIVE_PATTERN_OWNERSHIP_PASS: real same-tick crystal removal blocks both insertion and duplicate recycling");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void retiredCoreReturnsExactlyOnceAcrossActualBeNbtRoundTrips(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        // The old slot was serialized with AE2's real inventory codec. A registered fallback permits the native/no-EAEP run.
        var coreItem = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("extendedae_plus:assembler_matrix_upload_core"))
                .orElse(AEItems.FORMATION_CORE.asItem());
        var core = new ItemStack(coreItem);
        core.set(DataComponents.CUSTOM_NAME, Component.literal("Installed legacy upload core"));
        var custom = new CompoundTag();
        custom.putUUID("legacy_owner", UUID.randomUUID());
        custom.putInt("legacy_payload", 431);
        core.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        var coreKey = AEItemKey.of(core);
        var otherKey = AEItemKey.of(Items.DIAMOND);
        var legacy = new CompoundTag();
        var oldSlot = new AppEngInternalInventory(1);
        oldSlot.setMaxStackSize(0, 1);
        oldSlot.setItemDirect(0, core.copy());
        oldSlot.writeToNBT(legacy, LEGACY_CORE, registries);
        var existing = new ListTag();
        existing.add(GenericStack.writeTag(registries, new GenericStack(coreKey, 2)));
        existing.add(GenericStack.writeTag(registries, new GenericStack(otherKey, 7)));
        legacy.put(PRIMARY_ESCROW, existing);
        var original = legacy.copy();
        var center = detachedCenter(helper);
        for (int load = 0; load < 2; load++) {
            center.loadTag(legacy.copy(), registries);
            helper.assertTrue(center.isAutomaticPatternUploadEnabled(), "An old installed core preserves the formerly enabled behavior");
            assertEscrow(helper, center, coreKey, otherKey);
        }
        for (int reload = 0; reload < 5; reload++) {
            var saved = center.saveWithoutMetadata(registries);
            helper.assertTrue(!saved.contains(LEGACY_CORE) && saved.contains(UPLOAD, Tag.TAG_BYTE) && saved.getBoolean(UPLOAD),
                    "New saves replace the retired physical slot with an explicit enabled setting");
            var savedCore = saved.getList(PRIMARY_ESCROW, Tag.TAG_COMPOUND).stream()
                    .map(tag -> GenericStack.readTag(registries, (CompoundTag) tag))
                    .filter(stack -> stack != null && stack.what().equals(coreKey)).toList();
            helper.assertTrue(savedCore.size() == 1 && savedCore.getFirst().amount() == 3,
                    "Exactly one merged escrow entry retains the installed core's complete components and count");
            center = detachedCenter(helper);
            center.loadTag(saved, registries);
            assertEscrow(helper, center, coreKey, otherKey);
        }
        helper.assertTrue(original.equals(legacy), "Migration must not rewrite the caller-owned legacy payload");
        legacy.putBoolean(UPLOAD, false);
        center.loadTag(legacy, registries);
        helper.assertTrue(!center.isAutomaticPatternUploadEnabled(), "An explicit new disabled setting overrides legacy-core inference");
        assertEscrow(helper, center, coreKey, otherKey);
        System.out.println("NATIVE_PATTERN_LEGACY_CORE_NBT_PASS: real AE2 legacy inventory codec, exact-component output escrow, enabled inference, explicit false, five non-duplicating reloads");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void migratedCoreReallyReturnsToFiniteMeDiskAndCannotReplay(GameTestHelper helper) {
        var array = MolecularCenterPatternMigrationGameTests.fixture(helper, false, 8);
        var center = array.center();
        var drive = place(helper, center.getBlockPos().east(84), AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        array.nodes().add(drive.getMainNode());
        var registries = helper.getLevel().registryAccess();
        var core = new ItemStack(BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("extendedae_plus:assembler_matrix_upload_core"))
                .orElse(AEItems.FORMATION_CORE.asItem()));
        core.set(DataComponents.CUSTOM_NAME, Component.literal("Legacy core returned through actual ME storage"));
        var key = AEItemKey.of(core);
        var legacy = center.saveWithoutMetadata(registries);
        legacy.remove(UPLOAD);
        var oldSlot = new AppEngInternalInventory(1);
        oldSlot.setItemDirect(0, core.copy());
        oldSlot.writeToNBT(legacy, LEGACY_CORE, registries);
        center.loadTag(legacy, registries);
        helper.assertTrue(center.isAutomaticPatternUploadEnabled() && center.getBufferedAutoCraftAmount(key) == 1,
                "Actual placed BE loads one retired core into durable output escrow before its grid starts");
        helper.startSequence().thenWaitUntil(() -> MolecularCenterPatternMigrationGameTests.nodesReady(helper, array))
                .thenExecute(() -> MolecularCenterPatternMigrationGameTests.connect(array))
                .thenWaitUntil(() -> {
                    MolecularCenterPatternMigrationGameTests.online(helper, array);
                    helper.assertTrue(drive.getMainNode().isActive() && drive.getCellInventory(0) != null,
                            "Finite ME destination is genuinely mounted and online");
                    helper.assertTrue(center.getBufferedAutoCraftAmount(key) == 0 && center.getPendingOutputAmount() == 0
                                    && drive.getCellInventory(0).getAvailableStacks().get(key) == 1,
                            "Normal server ticks must move the migrated core out of escrow into the actual finite disk exactly once");
                }).thenExecute(() -> {
                    var saved = center.saveWithoutMetadata(registries);
                    helper.assertTrue(!saved.contains(LEGACY_CORE) && saved.getBoolean(UPLOAD),
                            "Post-refund save has only the explicit setting, not a replayable retired slot");
                    center.loadTag(saved, registries);
                }).thenIdle(40).thenExecute(() -> {
                    helper.assertTrue(center.isAutomaticPatternUploadEnabled() && center.getPendingOutputAmount() == 0
                                    && drive.getCellInventory(0).getAvailableStacks().get(key) == 1,
                            "Reloading the actual placed BE cannot return the core a second time");
                    var persisted = appeng.me.cells.BasicCellInventory.createInventory(drive.getInternalInventory().getStackInSlot(0).copy(), null);
                    helper.assertTrue(persisted != null && persisted.getAvailableStacks().get(key) == 1,
                            "The returned core and its custom components are persisted in the physical disk, not merely a transient monitor");
                    System.out.println("NATIVE_PATTERN_LEGACY_CORE_RETURN_PASS: placed BE migration, actual server-tick escrow flush into finite ME disk, post-refund reload, no replay after 40 ticks");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void nativeUploadSettingIsExplicitAndIdempotentInNewSaves(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var center = detachedCenter(helper);
        center.setAutomaticPatternUploadEnabled(true);
        center.loadTag(new CompoundTag(), registries);
        helper.assertTrue(!center.isAutomaticPatternUploadEnabled() && center.getPendingOutputAmount() == 0,
                "Absent setting and absent legacy core reset to disabled without creating a refund");
        var emptyLegacy = new CompoundTag();
        // AE2 normally omits an empty inventory key; also accept an explicitly empty legacy list.
        emptyLegacy.put(LEGACY_CORE, new ListTag());
        center.loadTag(emptyLegacy, registries);
        helper.assertTrue(!center.isAutomaticPatternUploadEnabled() && center.getPendingOutputAmount() == 0,
                "An explicitly empty legacy slot must not enable upload or synthesize a core");
        for (boolean enabled : List.of(true, true, false, false, true)) {
            center.setAutomaticPatternUploadEnabled(enabled);
            center.setAutomaticPatternUploadEnabled(enabled);
            var saved = center.saveWithoutMetadata(registries);
            helper.assertTrue(saved.contains(UPLOAD, Tag.TAG_BYTE) && saved.getBoolean(UPLOAD) == enabled && !saved.contains(LEGACY_CORE),
                    "Both true and false are explicitly persisted; repeated setter calls do not revive the retired inventory");
            var restored = detachedCenter(helper);
            for (int reload = 0; reload < 2; reload++) restored.loadTag(saved.copy(), registries);
            var again = restored.saveWithoutMetadata(registries);
            helper.assertTrue(restored.isAutomaticPatternUploadEnabled() == enabled && restored.getPendingOutputAmount() == 0
                            && saved.equals(again),
                    "Actual load/save is idempotent for the new boolean and cannot invent old core refunds");
            center = restored;
        }
        System.out.println("NATIVE_PATTERN_SETTING_NBT_PASS: missing/empty legacy defaults, true+false persistence, repeated setters and identical re-saved BE payloads");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void threeEffectTiersRoundTripThroughFourActualBlockEntities(GameTestHelper helper) {
        var pos = helper.absolutePos(BlockPos.ZERO);
        var center = detachedCenter(helper);
        var centerCopy = detachedCenter(helper);
        assertEffectRoundTrips(helper, center, centerCopy, center::writeToStream, centerCopy::readFromStream);
        var wellState = ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState();
        var well = new MatterFabricationBlockEntity(pos, wellState);
        var wellCopy = new MatterFabricationBlockEntity(pos, wellState);
        assertEffectRoundTrips(helper, well, wellCopy, well::writeToStream, wellCopy::readFromStream);
        var nexusState = ModContent.OMNI_COMPUTATION_CONTROLLER.get().defaultBlockState();
        var nexus = new OmniComputationCoreBlockEntity(pos, nexusState);
        var nexusCopy = new OmniComputationCoreBlockEntity(pos, nexusState);
        assertEffectRoundTrips(helper, nexus, nexusCopy, nexus::writeToStream, nexusCopy::readFromStream);
        var hubState = SingularityContent.CONTROLLER.get().defaultBlockState();
        var hub = new SingularityBlockEntity(pos, hubState);
        var hubCopy = new SingularityBlockEntity(pos, hubState);
        assertEffectRoundTrips(helper, hub, hubCopy, hub::writeToStream, hubCopy::readFromStream);
        System.out.println("MACHINE_EFFECT_BE_ROUNDTRIP_PASS: actual Array+well+Nexus+Hub NBT and full BE update streams, inherit/off/reduced/full, changed flags and trailing sentinel");
        helper.succeed();
    }

    private record EncodingFixture(MolecularCenterPatternMigrationGameTests.Fixture array,
            PatternEncodingTerminalPart terminal, ServerPlayer player, PatternEncodingTermMenu menu, MolecularCenterBlockEntity unformed) {}

    private static void withEncodingFixture(GameTestHelper helper, int index, boolean sameGrid, Consumer<EncodingFixture> verification) {
        withEncodingFixture(helper, index, sameGrid, false, verification);
    }

    private static void withEncodingFixture(GameTestHelper helper, int index, boolean sameGrid, boolean includeUnformed,
            Consumer<EncodingFixture> verification) {
        var array = MolecularCenterPatternMigrationGameTests.fixture(helper, false, index);
        var unformed = includeUnformed ? place(helper, array.center().getBlockPos().east(96),
                ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState(), MolecularCenterBlockEntity.class) : null;
        if (unformed != null) array.nodes().add(unformed.getMainNode());
        var terminalPos = array.center().getBlockPos().east(80);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "NativeEncode" + index));
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        player.setPos(terminalPos.getX() + .5, terminalPos.getY(), terminalPos.getZ() - 1);
        var cable = place(helper, terminalPos, AEBlocks.CABLE_BUS.block().defaultBlockState(), CableBusBlockEntity.class);
        cable.addPart(AEParts.SMART_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), null, player);
        var terminal = (PatternEncodingTerminalPart) cable.addPart(AEParts.PATTERN_ENCODING_TERMINAL.asItem(), Direction.NORTH, player);
        var power = place(helper, terminalPos.east(4), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), AENetworkedBlockEntity.class);
        helper.startSequence().thenWaitUntil(() -> {
                    MolecularCenterPatternMigrationGameTests.nodesReady(helper, array);
                    helper.assertTrue(terminal.getMainNode().getNode() != null && power.getMainNode().getNode() != null,
                            "Actual terminal and independent power nodes initialized");
                }).thenExecute(() -> {
                    MolecularCenterPatternMigrationGameTests.connect(array);
                    GridHelper.createConnection(power.getMainNode().getNode(), terminal.getMainNode().getNode());
                    if (sameGrid) GridHelper.createConnection(array.controller().getNode(), terminal.getMainNode().getNode());
                }).thenWaitUntil(() -> {
                    MolecularCenterPatternMigrationGameTests.online(helper, array);
                    helper.assertTrue(array.center().canAcceptExternalPatternUpload() && terminal.getMainNode().isActive(),
                            "Real operational Array and powered terminal must be ready");
                    helper.assertTrue((terminal.getMainNode().getGrid() == array.center().getMainNode().getGrid()) == sameGrid,
                            "Fixture must have genuinely " + (sameGrid ? "shared" : "separate") + " AE2 grids");
                    if (unformed != null) helper.assertTrue(unformed.getMainNode().isActive()
                                    && unformed.getMainNode().getGrid() == terminal.getMainNode().getGrid(),
                            "Unformed control is powered/channelled on the terminal's actual grid");
                }).thenExecute(() -> {
                    var previousMenu = player.containerMenu;
                    try {
                        var menu = new PatternEncodingTermMenu(300 + index, player.getInventory(), terminal);
                        player.containerMenu = menu;
                        menu.broadcastChanges();
                        helper.assertTrue(menu.isValidMenu() && menu.stillValid(player) && menu.getTarget() == terminal,
                                "Exercise a valid, open, server-side AE2 encoding menu with its actual part host");
                        helper.assertTrue(terminal.getLogic().getBlankPatternInv().isEmpty() && terminal.getLogic().getEncodedPatternInv().isEmpty(),
                                "No terminal seed items or optional constructor autofill may hide blank accounting");
                        verification.accept(new EncodingFixture(array, terminal, player, menu, unformed));
                    } finally {
                        player.containerMenu = previousMenu;
                    }
                }).thenSucceed();
    }

    private static void prepareEncoding(EncodingFixture fixture, EncodingMode mode) {
        var logic = fixture.terminal.getLogic();
        fixture.menu.clear(); // Clear ghost inputs only; never discard a physical blank or encoded output.
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
        fixture.menu.broadcastChanges();
    }

    private static ItemStack expectedPattern(GameTestHelper helper, EncodingMode mode) {
        int index = switch (mode) {
            case CRAFTING -> 0;
            case STONECUTTING -> 1;
            case SMITHING_TABLE -> 2;
            default -> throw new IllegalArgumentException("Not an Array-supported encoding mode: " + mode);
        };
        var result = MolecularCenterPatternMigrationGameTests.patterns(helper).get(index).copy();
        result.remove(DataComponents.CUSTOM_NAME); // The migration fixture names its patterns; fresh AE2 encode does not.
        return result;
    }

    private static void setUploadThroughButton(GameTestHelper helper, EncodingFixture fixture, boolean enabled) {
        var player = fixture.player;
        var previousMenu = player.containerMenu;
        var previousPos = player.position();
        try {
            player.setPos(fixture.array.center().getBlockPos().getCenter());
            // The actual menu constructor is private because AE2 normally constructs it via its menu type.
            var constructor = MolecularCenterMenu.class.getDeclaredConstructor(int.class, Inventory.class, PatternProviderLogicHost.class);
            constructor.setAccessible(true);
            var menu = constructor.newInstance(400, player.getInventory(), fixture.array.center());
            player.containerMenu = menu;
            helper.assertTrue(menu.isValidMenu() && menu.stillValid(player), "Actual Array button action uses a valid nearby open menu");
            menu.receiveClientAction("set_pattern_upload", Boolean.toString(enabled));
            helper.assertTrue(fixture.array.center().isAutomaticPatternUploadEnabled() == enabled && menu.automaticPatternUpload == enabled,
                    "Registered server button handler changes the BE and synchronized menu state");
            player.containerMenu = previousMenu;
            menu.receiveClientAction("set_pattern_upload", Boolean.toString(!enabled));
            helper.assertTrue(fixture.array.center().isAutomaticPatternUploadEnabled() == enabled,
                    "A stale Array menu must not toggle the setting after the player switches back to the terminal");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct the actual Array button menu", exception);
        } finally {
            player.containerMenu = previousMenu;
            player.setPos(previousPos);
        }
    }

    private static void assertRetained(GameTestHelper helper, EncodingFixture fixture, ItemStack expected,
            int blanks, int libraryItems, String reason) {
        helper.assertTrue(ItemStack.matches(expected, fixture.terminal.getLogic().getEncodedPatternInv().getStackInSlot(0))
                        && amount(fixture.terminal.getLogic().getBlankPatternInv()) == blanks
                        && amount(fixture.array.center().getLogic().getFullPatternInventory()) == libraryItems,
                reason + ": retain exactly the encoded item, charge no extra blank and leave the library unchanged");
    }

    private static int amount(InternalInventory inventory) {
        int result = 0;
        for (var stack : inventory) result += stack.getCount();
        return result;
    }

    private static int playerBlanks(ServerPlayer player) {
        int result = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            if (AEItems.BLANK_PATTERN.is(stack)) result += stack.getCount();
        }
        return result;
    }

    private static int crystalPatternCount(GameTestHelper helper, MolecularCenterBlockEntity center) {
        int result = 0;
        for (var part : MolecularCenterPatternShards.crystals()) {
            var pos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, part);
            result += ((MolecularCenterCrystalBlockEntity) helper.getLevel().getBlockEntity(pos)).patterns().size();
        }
        return result;
    }

    private static MolecularCenterBlockEntity detachedCenter(GameTestHelper helper) {
        var center = new MolecularCenterBlockEntity(helper.absolutePos(BlockPos.ZERO), ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState());
        center.setLevel(helper.getLevel()); // Real registries/world for codecs; do not tick or create an unrelated grid.
        return center;
    }

    private static void assertEscrow(GameTestHelper helper, MolecularCenterBlockEntity center, AEItemKey core, AEItemKey other) {
        helper.assertTrue(center.getBufferedAutoCraftAmount(core) == 3 && center.getBufferedAutoCraftAmount(other) == 7
                        && center.getPendingOutputAmount() == 10,
                "One installed legacy core merges with the existing two; seven unrelated escrow items remain intact");
    }

    private static void assertEffectRoundTrips(GameTestHelper helper, AEBaseBlockEntity source, AEBaseBlockEntity target,
            Consumer<RegistryFriendlyByteBuf> write, Function<RegistryFriendlyByteBuf, Boolean> read) {
        source.setLevel(helper.getLevel());
        target.setLevel(helper.getLevel());
        var original = ((MachineEffectHost) source).getMachineEffects();
        var restored = ((MachineEffectHost) target).getMachineEffects();
        String name = source.getClass().getSimpleName();
        for (int tier : new int[]{-1, 0, 1, 2, -1}) {
            original.setOverride(tier);
            if (source instanceof MatterFabricationBlockEntity) {
                var progress = source.saveWithoutMetadata(helper.getLevel().registryAccess());
                progress.putInt("fabrication_progress", tier + 2);
                source.loadTag(progress, helper.getLevel().registryAccess());
            }
            var saved = source.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(saved.contains(MachineEffectSettings.NBT_KEY, Tag.TAG_INT) == (tier != -1),
                    name + ": inheritance is absence; each explicit effect tier is persisted");
            restored.setOverride(tier == 0 ? 2 : 0);
            target.loadTag(saved, helper.getLevel().registryAccess());
            helper.assertTrue(restored.getOverride() == tier, name + ": actual BE NBT reload retains tier " + tier);
            // Also diverge an earlier visual field, so a short-circuiting read cannot accidentally pass.
            var differentVisualState = saved.copy();
            if (target instanceof MolecularCenterBlockEntity) {
                differentVisualState.putInt("visual_field_color", saved.getInt("visual_field_color") ^ 1);
            } else if (target instanceof OmniComputationCoreBlockEntity) {
                differentVisualState.putBoolean("omni_structure_formed", true);
            } else if (target instanceof SingularityBlockEntity) {
                differentVisualState.putBoolean("formed", !saved.getBoolean("formed"));
            }
            target.loadTag(differentVisualState, helper.getLevel().registryAccess());
            restored.setOverride(tier == 0 ? 2 : 0);
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            try {
                write.accept(buffer); // Entire BE update, including superclass and all fields before the effect byte.
                buffer.writeInt(0x4315A7E);
                helper.assertTrue(read.apply(buffer) && restored.getOverride() == tier,
                        name + ": actual BE stream reads tier " + tier + " and reports a change");
                helper.assertTrue(buffer.readInt() == 0x4315A7E && buffer.readableBytes() == 0,
                        name + ": effect decode must not skip bytes when another BE field already changed");
                buffer.clear();
                write.accept(buffer);
                buffer.writeInt(0x4315A7E);
                helper.assertTrue(!read.apply(buffer) && restored.getOverride() == tier,
                        name + ": repeated full stream is idempotent");
                helper.assertTrue(buffer.readInt() == 0x4315A7E && buffer.readableBytes() == 0,
                        name + ": unchanged full stream is still fully consumed");
            } finally {
                buffer.release();
            }
        }
    }
}
