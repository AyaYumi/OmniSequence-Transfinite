package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.integration.MatterFabricationPatternUpload;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real upload entry point, formed well, AE2 terminal and a drive containing exactly one blank. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterFabricationNetworkUploadGameTests {
    static final ResourceLocation RECIPE = ResourceLocation.parse("molecularmanipulator:verification_upload_network");
    static final List<ItemStack> CHOICES = List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHER_STAR));

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void defaultAssemblyUploadsOneNetworkBlank(GameTestHelper helper) {
        withFixture(helper, 0, fixture -> verifyUpload(helper, fixture, false));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 180)
    public static void renamedAssemblyUploadsOneNetworkBlank(GameTestHelper helper) {
        withFixture(helper, 1, fixture -> verifyUpload(helper, fixture, true));
    }

    private static void verifyUpload(GameTestHelper helper, Fixture fixture, boolean renamed) {
        var assembly = fixture.assembly;
        if (renamed) {
            // The actual screen/menu rename endpoint, not a stubbed display name.
            assembly.rename("My custom well / 自定义总成 #42");
            helper.assertTrue(assembly.getDisplayName().getString().equals("My custom well / 自定义总成 #42"),
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
        helper.assertTrue(assembly.getController() == fixture.controller && fixture.controller.isStructureFormed()
                        && FtbTeamOwnership.forPlayer(fixture.player).equals(fixture.controller.getBoundTeam()),
                "Destination must be a loaded, same-team, actually formed well");
        helper.assertTrue(blanks.isEmpty() && encoded.isEmpty() && inventory.isEmpty(),
                "No local blank, pre-encoded pattern or pre-existing destination pattern may hide a failure");
        helper.assertTrue(network.getAvailableStacks().get(blankKey) == 1
                        && fixture.drive.getCellInventory(0).getAvailableStacks().get(blankKey) == 1,
                "The real ME drive must contain exactly one blank, not one per ingredient or output");
        helper.assertTrue(menu.isValidMenu() && menu.stillValid(fixture.player), "Use a valid real encoding menu");
        var recipe = MatterRecipeIndex.get(helper.getLevel()).fabrication(RECIPE);
        helper.assertTrue(recipe != null, "The fixture recipe must be loaded from the server datapack");
        var expected = MatterFabricationPatternEncoding.encode(recipe.value(), CHOICES);

        // This is precisely the server entry used by our JEI custom button's payload.
        MatterFabricationPatternUpload.upload(fixture.player, menu.containerId, RECIPE, CHOICES);

        int filled = 0;
        for (var stack : inventory) {
            if (stack.isEmpty()) continue;
            filled++;
            helper.assertTrue(stack.getCount() == 1 && stack.is(expected.getItem())
                            && expected.get(AEComponents.ENCODED_PROCESSING_PATTERN)
                                    .equals(stack.get(AEComponents.ENCODED_PROCESSING_PATTERN)),
                    "Uploaded pattern must retain the complete server recipe, not client display counts");
            helper.assertTrue(PatternDetailsHelper.decodePattern(stack, helper.getLevel()) != null,
                    "The inserted pattern must decode in the actual game");
        }
        helper.assertTrue(filled == 1, (renamed ? "Renamed" : "Default") + " assembly must receive exactly one pattern");
        helper.assertTrue(network.getAvailableStacks().get(blankKey) == 0
                        && fixture.drive.getCellInventory(0).getAvailableStacks().get(blankKey) == 0
                        && blanks.isEmpty() && encoded.isEmpty(),
                "One upload must consume the only ME blank and leave both terminal slots empty");
        MatterFabricationPatternUpload.upload(fixture.player, menu.containerId, RECIPE, CHOICES);
        int afterDuplicate = 0;
        for (var stack : inventory) afterDuplicate += stack.getCount();
        helper.assertTrue(afterDuplicate == 1 && network.getAvailableStacks().get(blankKey) == 0,
                "A duplicate click with no blanks left must not duplicate a pattern");
        System.out.println("MATTER_UPLOAD_NETWORK_PASS " + (renamed ? "renamed" : "default") + " local=0 me=1->0 patterns=1");
    }

    static void withFixture(GameTestHelper helper, int index, Consumer<Fixture> verification) {
        withFixture(helper, index, false, verification);
    }

    static void withFixture(GameTestHelper helper, int index, boolean connectWell, Consumer<Fixture> verification) {
        var level = helper.getLevel();
        var origin = new BlockPos(5120 + index * 384, 100, 5120);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadFixture" + index));
        player.setGameMode(GameType.CREATIVE);
        var chunks = new HashSet<ChunkPos>();
        var placed = new ArrayList<BlockPos>();
        Consumer<BlockPos> load = pos -> {
            var chunk = new ChunkPos(pos);
            if (chunks.add(chunk)) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunkAt(pos); }
        };
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            load.accept(pos); placed.add(pos);
            level.setBlock(pos, MatterFabricationStructure.partState(part.type()), 3);
        }
        level.setBlock(origin, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        if (ModList.get().isLoaded("ftbteams")) {
            var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "UploadOwner" + index));
            FtbFixture.bindParty(helper, controller, owner, player, index);
        } else {
            controller.bindToPlayerTeam(player);
        }
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH,
                MatterFabricationStructure.patternAssemblyBays().getFirst());
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var terminalPos = origin.offset(50, 0, 0);
        for (var pos : List.of(terminalPos, terminalPos.east(), terminalPos.east(2))) {
            load.accept(pos); placed.add(pos);
        }
        player.setPos(terminalPos.getX() + .5, terminalPos.getY(), terminalPos.getZ() - 1);
        level.setBlock(terminalPos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
        var cable = (CableBusBlockEntity) level.getBlockEntity(terminalPos);
        cable.addPart(AEParts.SMART_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), null, player);
        var terminal = (PatternEncodingTerminalPart) cable.addPart(AEParts.PATTERN_ENCODING_TERMINAL.asItem(), Direction.NORTH, player);
        level.setBlock(terminalPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(terminalPos.east());
        level.setBlock(terminalPos.east(2), AEBlocks.DRIVE.block().defaultBlockState(), 3);
        var drive = (DriveBlockEntity) level.getBlockEntity(terminalPos.east(2));
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        helper.runAfterDelay(40, () -> {
            controller.refreshStructure(); assembly.setControllerPos(origin);
            helper.assertTrue(controller.isStructureFormed(), "Fixture blueprint must form without mocking controller state");
            if (terminal.getMainNode().getGrid() != power.getMainNode().getGrid()) {
                GridHelper.createConnection(terminal.getMainNode().getNode(), power.getMainNode().getNode());
            }
            if (power.getMainNode().getGrid() != drive.getMainNode().getGrid()) {
                GridHelper.createConnection(power.getMainNode().getNode(), drive.getMainNode().getNode());
            }
            if (connectWell) GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.runAfterDelay(80, () -> {
            var previousMenu = player.containerMenu;
            try {
                helper.assertTrue(terminal.getMainNode().isActive() && drive.getMainNode().isActive(),
                        "Actual terminal and drive must be powered and active");
                // Open first, seed second: EAEP's optional constructor autofill must not turn this into a local-blank test.
                var menu = new PatternEncodingTermMenu(240 + index, player.getInventory(), terminal);
                player.containerMenu = menu;
                var blankKey = AEItemKey.of(AEItems.BLANK_PATTERN.asItem());
                helper.assertTrue(terminal.getInventory().insert(blankKey, 1, Actionable.MODULATE, menu.getActionSource()) == 1,
                        "Real ME storage must accept the single fixture blank");
                verification.accept(new Fixture(controller, assembly, terminal, drive, player, menu));
            } finally {
                player.containerMenu = previousMenu;
                assembly.getLogic().getPatternInv().clear();
                // Remove while the chunks are still loaded, so the production upload index unregisters naturally.
                for (var pos : placed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
            }
            helper.succeed();
        });
    }

    record Fixture(MatterFabricationBlockEntity controller, MatterFabricationPatternAssemblyBlockEntity assembly,
            PatternEncodingTerminalPart terminal, DriveBlockEntity drive, ServerPlayer player, PatternEncodingTermMenu menu) {}

    private static final class FtbFixture {
        private static void bindParty(GameTestHelper helper, MatterFabricationBlockEntity controller,
                ServerPlayer owner, ServerPlayer uploader, int index) {
            try {
                var manager = (dev.ftb.mods.ftbteams.data.TeamManagerImpl) dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
                manager.playerLoggedIn(null, owner.getUUID(), owner.getGameProfile().getName());
                manager.playerLoggedIn(null, uploader.getUUID(), uploader.getGameProfile().getName());
                var party = manager.createParty(owner.getUUID(), null, "NetworkUploadFixture" + index, null, null);
                party.join(null, uploader.getGameProfile());
                controller.bindToPlayerTeam(owner);
                helper.assertTrue(!owner.getUUID().equals(uploader.getUUID())
                                && party.getTeamId().equals(FtbTeamOwnership.forPlayer(uploader))
                                && party.getTeamId().equals(controller.getBoundTeam()),
                        "Actual FTB party member must upload to a well bound by another player");
            } catch (Exception exception) {
                throw new AssertionError("Unable to create real FTB upload party", exception);
            }
        }
    }
}
