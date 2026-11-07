package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.core.sync.packets.GuiDataSyncPacket;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.integration.MatterFabricationPatternUpload;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real JEI upload server entry, formed well, AE2 terminal and a finite drive containing one blank. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterFabricationNetworkUploadGameTests {
    private static final ResourceLocation RECIPE = new ResourceLocation("molecularmanipulator", "verification_upload_network");
    private static final List<ItemStack> CHOICES = List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR));

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void defaultAssemblyUploadsOneNetworkBlank(GameTestHelper helper) {
        withFixture(helper, 0, fixture -> verifyUpload(helper, fixture, false));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void renamedAssemblyUploadsOneNetworkBlank(GameTestHelper helper) {
        withFixture(helper, 1, fixture -> verifyUpload(helper, fixture, true));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void legacyBuilderOwnershipUploadsOneNetworkBlank(GameTestHelper helper) {
        withFixture(helper, 2, true, fixture -> {
            helper.assertTrue(fixture.controller.getMainNode().getNode().getOwningPlayerProfileId() == null,
                    "The reloaded legacy well must have no AE2 placer, just its persisted builder");
            verifyUpload(helper, fixture, false);
            var saved = fixture.controller.saveWithFullMetadata();
            var expectedTeam = FtbTeamOwnership.forPlayer(fixture.player);
            helper.assertTrue(saved.getBoolean("fabrication_team_bound")
                            && saved.hasUUID("fabrication_bound_team")
                            && expectedTeam.equals(saved.getUUID("fabrication_bound_team")),
                    "Recovered builder ownership must be saved as the normal team binding");
            var restored = (MatterFabricationBlockEntity) BlockEntity.loadStatic(fixture.controller.getBlockPos(),
                    fixture.controller.getBlockState(), saved);
            helper.assertTrue(restored != null && expectedTeam.equals(restored.getBoundTeam()),
                    "A subsequent NBT reload must preserve the migrated team without resolving a new owner");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void builderMigrationCannotReplaceOrInventOwnership(GameTestHelper helper) {
        withFixture(helper, 3, fixture -> {
            var controller = fixture.controller;
            var expectedTeam = controller.getBoundTeam();
            var tag = controller.saveWithoutMetadata();
            tag.putUUID("fabrication_build_owner", UUID.randomUUID());
            controller.loadTag(tag);
            helper.assertTrue(expectedTeam != null && expectedTeam.equals(controller.getBoundTeam()),
                    "An existing team binding must not follow a different builder");
            tag.remove("fabrication_bound_team");
            tag.putBoolean("fabrication_team_bound", true);
            controller.loadTag(tag);
            helper.assertTrue(controller.getBoundTeam() == null,
                    "An explicitly initialized empty binding must not be claimed through builder fallback");
            assertUnownedUploadRejected(helper, fixture);
            tag.putBoolean("fabrication_team_bound", false);
            tag.remove("fabrication_build_owner");
            controller.loadTag(tag);
            helper.assertTrue(controller.getMainNode().getNode().getOwningPlayerProfileId() == null
                            && controller.getBoundTeam() == null,
                    "A controller with neither placer nor builder must remain unowned");
            assertUnownedUploadRejected(helper, fixture);
        });
    }

    private static void assertUnownedUploadRejected(GameTestHelper helper, Fixture fixture) {
        MatterFabricationPatternUpload.upload(fixture.player, fixture.menu.containerId, RECIPE, CHOICES);
        var blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        helper.assertTrue(fixture.assembly.getLogic().getPatternInv().isEmpty()
                        && fixture.terminal.getInventory().getAvailableStacks().get(blankKey) == 1
                        && fixture.drive.getCellInventory(0).getAvailableStacks().get(blankKey) == 1
                        && fixture.terminal.getLogic().getBlankPatternInv().isEmpty()
                        && fixture.terminal.getLogic().getEncodedPatternInv().isEmpty()
                        && fixture.player.getInventory().isEmpty(),
                "The uploading player must not claim an unowned well or consume its network blank");
    }

    private static void verifyUpload(GameTestHelper helper, Fixture fixture, boolean renamed) {
        var assembly = fixture.assembly;
        if (renamed) {
            var name = "My custom well / 自定义总成 #42";
            renameThroughMenu(helper, fixture, name);
            helper.assertTrue(assembly.hasCustomName() && assembly.getDisplayName().getString().equals(name),
                    "The live assembly must really have its custom name");
        } else {
            helper.assertTrue(!assembly.hasCustomName(), "Default-name control must not have a custom name");
        }
        var menu = fixture.menu;
        var blanks = fixture.terminal.getLogic().getBlankPatternInv();
        var encoded = fixture.terminal.getLogic().getEncodedPatternInv();
        var inventory = assembly.getLogic().getPatternInv();
        var network = fixture.terminal.getInventory();
        var blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
        var team = FtbTeamOwnership.forPlayer(fixture.player);
        helper.assertTrue(assembly.getController() == fixture.controller && fixture.controller.isStructureFormed()
                        && team != null && team.equals(fixture.controller.getBoundTeam()),
                "Destination must be a loaded, same-team, actually formed well");
        helper.assertTrue(fixture.controller.getMainNode().getGrid() != fixture.terminal.getMainNode().getGrid(),
                "The well is on another real grid: the mod's JEI route uses team/type, not a provider name or shared grid");
        helper.assertTrue(blanks.isEmpty() && encoded.isEmpty() && inventory.isEmpty()
                        && fixture.player.getInventory().isEmpty(),
                "No local blank, player blank, pre-encoded pattern or destination pattern may hide a failure");
        helper.assertTrue(network.getAvailableStacks().get(blankKey) == 1
                        && fixture.drive.getCellInventory(0).getAvailableStacks().get(blankKey) == 1,
                "The real ME drive must contain exactly one blank, not one per ingredient or output");
        helper.assertTrue(menu.isValidMenu() && menu.stillValid(fixture.player) && menu.getTarget() == fixture.terminal,
                "Use a valid, open, real encoding menu with its actual terminal host");
        var recipe = MatterRecipeIndex.get(helper.getLevel()).fabrication(RECIPE);
        helper.assertTrue(recipe != null && recipe.getId().equals(RECIPE)
                        && helper.getLevel().getRecipeManager().byKey(RECIPE).isPresent(),
                "The fixture recipe must be loaded from the server datapack");
        var expected = MatterFabricationPatternEncoding.encode(recipe, CHOICES);
        var fullQuantities = PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIAMOND), 10_000_000),
                        new GenericStack(AEItemKey.of(Items.NETHER_STAR), 7)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.EMERALD), 3),
                        new GenericStack(AEItemKey.of(Items.IRON_INGOT), 7)});
        helper.assertTrue(expected.hasTag() && ItemStack.matches(fullQuantities, expected),
                "The server fixture must encode all full quantities despite count-one client selections");

        // Exactly the server entry called by MatterFabricationUploadPayload from our JEI button.
        // No target name, target position or client quantity is supplied.
        MatterFabricationPatternUpload.upload(fixture.player, menu.containerId, RECIPE, CHOICES);

        int filled = 0;
        for (var stack : inventory) {
            if (stack.isEmpty()) continue;
            filled++;
            // Forge 1.20.1 stores the entire processing pattern in item NBT, not AEComponents.
            helper.assertTrue(stack.getCount() == 1 && ItemStack.matches(expected, stack),
                    "Uploaded pattern must retain the complete server recipe NBT, not client display counts");
            helper.assertTrue(PatternDetailsHelper.decodePattern(stack, helper.getLevel()) != null,
                    "The inserted pattern must decode in the actual game");
        }
        helper.assertTrue(filled == 1, (renamed ? "Renamed" : "Default") + " assembly must receive exactly one pattern");
        helper.assertTrue(network.getAvailableStacks().get(blankKey) == 0
                        && fixture.drive.getCellInventory(0).getAvailableStacks().get(blankKey) == 0
                        && blanks.isEmpty() && encoded.isEmpty() && fixture.player.getInventory().isEmpty(),
                "One upload must consume the only ME blank and leave terminal/player inventories empty");
        MatterFabricationPatternUpload.upload(fixture.player, menu.containerId, RECIPE, CHOICES);
        int afterDuplicate = 0;
        for (var stack : inventory) {
            afterDuplicate += stack.getCount();
            helper.assertTrue(stack.isEmpty() || ItemStack.matches(expected, stack),
                    "A duplicate click must not alter the stored pattern NBT");
        }
        helper.assertTrue(afterDuplicate == 1 && network.getAvailableStacks().get(blankKey) == 0
                        && fixture.drive.getCellInventory(0).getAvailableStacks().get(blankKey) == 0
                        && blanks.isEmpty() && encoded.isEmpty() && fixture.player.getInventory().isEmpty(),
                "A duplicate click with no blanks left must not duplicate a pattern or invent a refund");
        System.out.println("MATTER_UPLOAD_NETWORK_PASS " + (renamed ? "renamed" : "default") + " local=0 me=1->0 patterns=1");
    }

    private static void renameThroughMenu(GameTestHelper helper, Fixture fixture, String name) {
        var player = fixture.player;
        var previousMenu = player.containerMenu;
        var previousPos = player.position();
        try {
            player.setPos(fixture.assembly.getBlockPos().getCenter());
            var constructor = MatterFabricationPatternAssemblyMenu.class.getDeclaredConstructor(
                    int.class, Inventory.class, MatterFabricationPatternAssemblyBlockEntity.class);
            constructor.setAccessible(true);
            var menu = constructor.newInstance(340, player.getInventory(), fixture.assembly);
            player.containerMenu = menu;
            helper.assertTrue(menu.isValidMenu() && menu.stillValid(player), "Rename uses the actual nearby assembly menu");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeVarInt(menu.containerId).writeUtf("rename_assembly").writeUtf(new JsonPrimitive(name).toString());
                var packet = new GuiDataSyncPacket(buffer);
                try {
                    packet.serverPacketData(player);
                } finally {
                    packet.getData().release();
                }
            } finally {
                buffer.release();
            }
            menu.broadcastChanges();
            helper.assertTrue(name.equals(menu.assemblyName), "The real rename action must synchronize the new assembly name");
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct the actual assembly rename menu", exception);
        } finally {
            player.containerMenu = previousMenu;
            player.setPos(previousPos);
        }
    }

    private static void withFixture(GameTestHelper helper, int index, Consumer<Fixture> verification) {
        withFixture(helper, index, false, verification);
    }

    private static void withFixture(GameTestHelper helper, int index, boolean legacyBuilder, Consumer<Fixture> verification) {
        var level = helper.getLevel();
        var origin = new BlockPos(5120 + index * 384, 100, 5120);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadFixture" + index));
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        var chunks = new HashSet<ChunkPos>();
        var placed = new ArrayList<BlockPos>();
        var cleaned = new boolean[1];
        Runnable[] releaseTeam = {() -> {}};
        Runnable cleanup = () -> {
            if (cleaned[0]) return;
            cleaned[0] = true;
            try {
                for (var pos : placed) {
                    if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity entity) entity.clearContent();
                }
                // Remove while loaded so production setRemoved unregisters the upload destination naturally.
                for (var pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } finally {
                try {
                    releaseTeam[0].run();
                } finally {
                    for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
                }
            }
        };
        Consumer<BlockPos> load = pos -> {
            var chunk = new ChunkPos(pos);
            if (chunks.add(chunk)) {
                level.setChunkForced(chunk.x, chunk.z, true);
                level.getChunkAt(pos);
            }
            placed.add(pos);
            if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity old) old.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        };
        try {
            for (var part : MatterFabricationStructure.parts()) {
                var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
                load.accept(pos);
                level.setBlock(pos, MatterFabricationStructure.partState(part.type()), 3);
            }
            level.setBlock(origin, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
            var placedController = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
            var builder = player;
            if (ModList.get().isLoaded("ftbteams")) {
                builder = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadOwner" + index));
                releaseTeam[0] = FtbFixture.bindParty(helper, placedController, builder, player, index);
            } else {
                placedController.bindToPlayerTeam(player);
            }
            var controller = legacyBuilder ? reloadLegacyController(helper, placedController, builder.getUUID()) : placedController;
            var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH,
                    MatterFabricationStructure.patternAssemblyBays().get(0));
            level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
            var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
            var terminalPos = origin.offset(50, 0, 0);
            for (var pos : List.of(terminalPos, terminalPos.east(), terminalPos.east(2))) load.accept(pos);
            player.setPos(terminalPos.getX() + .5, terminalPos.getY(), terminalPos.getZ() - 1);
            level.setBlock(terminalPos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
            var cable = (CableBusBlockEntity) level.getBlockEntity(terminalPos);
            cable.addPart(AEParts.SMART_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), null, player);
            var terminal = cable.addPart(AEParts.PATTERN_ENCODING_TERMINAL.asItem(), Direction.NORTH, player);
            level.setBlock(terminalPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
            var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(terminalPos.east());
            level.setBlock(terminalPos.east(2), AEBlocks.DRIVE.block().defaultBlockState(), 3);
            var drive = (DriveBlockEntity) level.getBlockEntity(terminalPos.east(2));
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            helper.runAfterDelay(40, () -> {
                try {
                    // Formation itself discovers and links the service bay; never write a formed flag/controller NBT.
                    controller.refreshStructure();
                    helper.assertTrue(controller.isStructureFormed() && assembly.getController() == controller,
                            "Fixture blueprint must really form and link its assembly through production discovery");
                    helper.assertTrue(terminal.getMainNode().getNode() != null && power.getMainNode().getNode() != null
                                    && drive.getMainNode().getNode() != null,
                            "Real AE2 nodes must have initialized");
                    if (terminal.getMainNode().getGrid() != power.getMainNode().getGrid()) {
                        GridHelper.createConnection(terminal.getMainNode().getNode(), power.getMainNode().getNode());
                    }
                    if (power.getMainNode().getGrid() != drive.getMainNode().getGrid()) {
                        GridHelper.createConnection(power.getMainNode().getNode(), drive.getMainNode().getNode());
                    }
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
            });
            helper.runAfterDelay(80, () -> {
                var previousMenu = player.containerMenu;
                try {
                    helper.assertTrue(terminal.getMainNode().isActive() && drive.getMainNode().isActive()
                                    && terminal.getMainNode().getGrid() == drive.getMainNode().getGrid()
                                    && drive.getCellInventory(0) != null,
                            "Actual terminal and finite drive must be powered, channelled and on the same grid");
                    // Open first, seed second: optional EAEP constructor autofill cannot supply a local blank.
                    var menu = new PatternEncodingTermMenu(240 + index, player.getInventory(), terminal);
                    player.containerMenu = menu;
                    menu.broadcastChanges();
                    var blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
                    helper.assertTrue(terminal.getInventory().insert(blankKey, 1, Actionable.MODULATE, menu.getActionSource()) == 1,
                            "Real ME storage must accept the single fixture blank");
                    verification.accept(new Fixture(controller, assembly, terminal, drive, player, menu));
                } finally {
                    player.containerMenu = previousMenu;
                    cleanup.run();
                }
                helper.succeed();
            });
        } catch (RuntimeException | Error failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static MatterFabricationBlockEntity reloadLegacyController(GameTestHelper helper,
            MatterFabricationBlockEntity controller, UUID builder) {
        var tag = controller.saveWithFullMetadata();
        tag.remove("fabrication_bound_team");
        tag.putBoolean("fabrication_team_bound", false);
        tag.putUUID("fabrication_build_owner", builder);
        var level = helper.getLevel();
        var pos = controller.getBlockPos();
        var state = controller.getBlockState();
        level.removeBlockEntity(pos);
        var restored = (MatterFabricationBlockEntity) BlockEntity.loadStatic(pos, state, tag);
        helper.assertTrue(restored != null, "The old controller NBT must restore through the real block entity loader");
        level.setBlockEntity(restored);
        helper.assertTrue(!restored.saveWithoutMetadata().getBoolean("fabrication_team_bound"),
                "The persisted legacy fixture must start unbound, before normal AE2 readiness");
        return restored;
    }

    private record Fixture(MatterFabricationBlockEntity controller, MatterFabricationPatternAssemblyBlockEntity assembly,
            PatternEncodingTerminalPart terminal, DriveBlockEntity drive, ServerPlayer player, PatternEncodingTermMenu menu) {}

    /** Reflection keeps FTB optional, but invokes the real Forge 2001.3.2 manager/party API when installed. */
    private static final class FtbFixture {
        private static Runnable bindParty(GameTestHelper helper, MatterFabricationBlockEntity controller,
                ServerPlayer owner, ServerPlayer uploader, int index) {
            try {
                var api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api").invoke(null);
                var contract = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
                helper.assertTrue((boolean) contract.getMethod("isManagerLoaded").invoke(api), "Real FTB team manager must be loaded");
                var manager = contract.getMethod("getManager").invoke(api);
                var managerClass = Class.forName("dev.ftb.mods.ftbteams.data.TeamManagerImpl");
                var login = managerClass.getMethod("playerLoggedIn", ServerPlayer.class, UUID.class, String.class);
                login.invoke(manager, null, owner.getUUID(), owner.getGameProfile().getName());
                login.invoke(manager, null, uploader.getUUID(), uploader.getGameProfile().getName());
                var party = managerClass.getMethod("createParty", UUID.class, ServerPlayer.class, String.class,
                                String.class, Class.forName("dev.ftb.mods.ftblibrary.icon.Color4I"))
                        .invoke(manager, owner.getUUID(), null, "NetworkUploadFixture" + index, null, null);
                var partyClass = Class.forName("dev.ftb.mods.ftbteams.data.PartyTeam");
                // Forge 2001.3.2 takes ServerPlayer, unlike the legacy version's join(null, GameProfile).
                partyClass.getMethod("join", ServerPlayer.class).invoke(party, uploader);
                controller.bindToPlayerTeam(owner);
                var team = (UUID) Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("getTeamId").invoke(party);
                helper.assertTrue(!owner.getUUID().equals(uploader.getUUID())
                                && team.equals(FtbTeamOwnership.forPlayer(uploader)) && team.equals(controller.getBoundTeam()),
                        "An actual FTB party member must upload to a well bound by another player");
                return () -> {
                    try {
                        var leave = partyClass.getMethod("leave", UUID.class);
                        leave.invoke(party, uploader.getUUID());
                        leave.invoke(party, owner.getUUID());
                        var logout = managerClass.getMethod("playerLoggedOut", ServerPlayer.class);
                        logout.invoke(manager, uploader);
                        logout.invoke(manager, owner);
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError("Unable to clean up real FTB upload party", exception);
                    }
                };
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("Unable to create real FTB upload party", exception);
            }
        }
    }
}
