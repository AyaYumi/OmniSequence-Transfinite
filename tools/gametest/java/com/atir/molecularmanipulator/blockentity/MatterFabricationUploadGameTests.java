package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.*;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.integration.MatterFabricationPatternUpload;
import com.atir.molecularmanipulator.network.MatterFabricationUploadPayload;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.verification.VerificationAEKey;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterFabricationUploadGameTests {
    private static final AEKey GAS = new VerificationAEKey("upload_gas", "hot");
    private static final ResourceLocation RECIPE = ResourceLocation.parse("molecularmanipulator:verification_upload");

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void completeQuantitiesAndTransactions(GameTestHelper helper) {
        var recipe = recipe();
        var selected = List.of(new ItemStack(Items.DIAMOND, 1));
        var pattern = MatterFabricationPatternEncoding.encode(recipe, selected);
        var decoded = PatternDetailsHelper.decodePattern(pattern, helper.getLevel());
        var inputs = new LinkedHashMap<AEKey, Long>();
        for (var input : decoded.getInputs()) {
            var stack = input.getPossibleInputs()[0];
            inputs.merge(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()), Math::addExact);
        }
        helper.assertTrue(inputs.equals(Map.of(AEItemKey.of(Items.DIAMOND), 10_000_000L,
                        AEFluidKey.of(Fluids.WATER), 1500L, GAS, 3_000_000_000L)),
                "Encoding must preserve large long resources and merge duplicate fluid keys");
        var outputs = new LinkedHashMap<AEKey, Long>();
        for (var stack : decoded.getOutputs()) outputs.merge(stack.what(), stack.amount(), Math::addExact);
        helper.assertTrue(outputs.equals(Map.of(AEItemKey.of(Items.EMERALD), 3L, AEItemKey.of(Items.IRON_INGOT), 7L,
                        AEFluidKey.of(Fluids.LAVA), 500L, GAS, 4_000_000_001L)),
                "All ordinary, fluid and third-party outputs must survive encoding");
        boolean rejected = false;
        try { MatterFabricationPatternEncoding.encode(recipe, List.of(new ItemStack(Items.DIRT))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "Forged ingredient choices must fail");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var request = new MatterFabricationUploadPayload(42, RECIPE, List.of(new ItemStack(Items.DIAMOND, 999)));
            MatterFabricationUploadPayload.STREAM_CODEC.encode(buffer, request);
            var restored = MatterFabricationUploadPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(restored.containerId() == 42 && restored.recipeId().equals(RECIPE)
                    && restored.selections().getFirst().getCount() == 1, "Payload must not transmit client-controlled recipe counts");
            buffer.clear(); buffer.writeVarInt(42); buffer.writeResourceLocation(RECIPE); buffer.writeVarInt(13);
            boolean bounded = false;
            try { MatterFabricationUploadPayload.STREAM_CODEC.decode(buffer); }
            catch (io.netty.handler.codec.DecoderException expected) { bounded = true; }
            helper.assertTrue(bounded, "Oversized packet ingredient lists must be rejected");
        } finally { buffer.release(); }
        var blanks = new AppEngInternalInventory(1);
        var destination = new AppEngInternalInventory(1);
        blanks.setItemDirect(0, AEItems.BLANK_PATTERN.stack(3));
        helper.assertTrue(MatterFabricationPatternUpload.insertConsumingBlank(blanks, destination, 0, pattern)
                && blanks.getStackInSlot(0).getCount() == 2, "Successful upload must consume exactly one blank");
        helper.assertTrue(!MatterFabricationPatternUpload.insertConsumingBlank(blanks, destination, 0, pattern)
                && blanks.getStackInSlot(0).getCount() == 2, "Occupied destination must retain blanks");
        destination.clear(); blanks.clear();
        helper.assertTrue(!MatterFabricationPatternUpload.insertConsumingBlank(blanks, destination, 0, pattern)
                && destination.isEmpty(), "Missing blanks must never manufacture free patterns");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 260)
    public static void placementBindingAndServerUpload(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var origin = new BlockPos(4320, 100, 4320);
        var owner = player(helper, "WellOwner");
        var member = player(helper, "WellMember");
        var foreign = player(helper, "OtherWellTeam");
        boolean ftb = ModList.get().isLoaded("ftbteams");
        var team = ftb ? FtbFixture.sharedParty(owner, member) : owner.getUUID();
        if (!ftb) member = owner;
        var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            var chunk = new net.minecraft.world.level.ChunkPos(pos);
            if (chunks.add(chunk)) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunkAt(pos); }
            level.setBlock(pos, MatterFabricationStructure.partState(part.type()), 3);
        }
        // Exercise BlockItem placement, including AE2's inherited placer ownership.
        level.setBlock(origin, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        owner.setYRot(0);
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get()));
        var hit = new BlockHitResult(origin.getCenter().add(0, -.5, 0), Direction.UP, origin.below(), false);
        var context = new BlockPlaceContext(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(((BlockItem) ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get()).place(context).consumesAction(),
                "Controller item must place successfully");
        level.setBlock(origin, level.getBlockState(origin).setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        helper.assertTrue(team.equals(controller.getBoundTeam()), "Actual placement must bind the controller to its current team");
        var saved = controller.saveWithFullMetadata(level.registryAccess());
        var restored = (MatterFabricationBlockEntity) BlockEntity.loadStatic(origin, controller.getBlockState(), saved, level.registryAccess());
        helper.assertTrue(restored != null && team.equals(restored.getBoundTeam()), "Controller team must survive saving and reloading");
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().getFirst());
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var terminalPos = origin.offset(50, 0, 0);
        var terminalChunk = new net.minecraft.world.level.ChunkPos(terminalPos);
        level.setChunkForced(terminalChunk.x, terminalChunk.z, true); level.getChunkAt(terminalPos); chunks.add(terminalChunk);
        level.setBlock(terminalPos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
        var cable = (CableBusBlockEntity) level.getBlockEntity(terminalPos);
        cable.addPart(AEParts.SMART_CABLE.item(appeng.api.util.AEColor.TRANSPARENT), null, owner);
        var terminal = cable.addPart(AEParts.PATTERN_ENCODING_TERMINAL.asItem(), Direction.NORTH, owner);
        helper.assertTrue(terminal != null, "A real AE2 encoding terminal must be available");
        level.setBlock(terminalPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (appeng.blockentity.networking.CreativeEnergyCellBlockEntity) level.getBlockEntity(terminalPos.east());
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(terminal.getMainNode().getNode() != null && power.getMainNode().getNode() != null,
                    "Encoding terminal and power nodes must be initialized");
            GridHelper.createConnection(terminal.getMainNode().getNode(), power.getMainNode().getNode());
        });
        var uploader = member;
        helper.runAfterDelay(80, () -> {
            // Repair the support block overwritten by the actual placement fixture.
            for (var part : MatterFabricationStructure.parts()) {
                var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
                if (!pos.equals(origin) && !pos.equals(bay)) level.setBlock(pos, MatterFabricationStructure.partState(part.type()), 3);
            }
            controller.refreshStructure(); assembly.setControllerPos(origin);
            helper.assertTrue(controller.isStructureFormed() && assembly.getController() == controller,
                    "Upload target must be a real formed well");
            helper.assertTrue(assembly.getMainNode().getNode().getOwningPlayerProfileId() == null,
                    "Assembly ownership must come from the controller even when the assembly has no placer");
            var recipesBefore = List.copyOf(level.getRecipeManager().getRecipes());
            var recipes = new ArrayList<RecipeHolder<?>>(recipesBefore);
            recipes.add(new RecipeHolder<>(RECIPE, recipe())); level.getRecipeManager().replaceRecipes(recipes);
            var previousMenu = uploader.containerMenu;
            var menu = new PatternEncodingTermMenu(42, uploader.getInventory(), terminal);
            uploader.containerMenu = menu;
            var blank = terminal.getLogic().getBlankPatternInv();
            blank.setItemDirect(0, AEItems.BLANK_PATTERN.stack(12));
            var choices = List.of(new ItemStack(Items.DIAMOND));
            var inventory = assembly.getLogic().getPatternInv();
            try {
                helper.assertTrue(MatterFabricationPatternEncoding.isEncodingMenu(menu)
                        && !MatterFabricationPatternEncoding.isEncodingMenu(previousMenu), "Only pattern encoding menus may upload");
                MatterFabricationPatternUpload.upload(uploader, 41, RECIPE, choices);
                helper.assertTrue(inventory.isEmpty() && blank.getStackInSlot(0).getCount() == 12, "Stale menu packets must have no effects");
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, List.of(new ItemStack(Items.DIRT)));
                helper.assertTrue(inventory.isEmpty() && blank.getStackInSlot(0).getCount() == 12, "Forged choices must retain blanks");
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, choices);
                helper.assertTrue(!inventory.isEmpty() && blank.getStackInSlot(0).getCount() == 11,
                        "Same-team teammate must upload even though they did not place either block");
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, choices);
                helper.assertTrue(blank.getStackInSlot(0).getCount() == 11 && filled(inventory) == 1, "Repeated click must not duplicate or consume blanks");
                inventory.clear();
                controller.bindToPlayerTeam(foreign);
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, choices);
                helper.assertTrue(inventory.isEmpty() && blank.getStackInSlot(0).getCount() == 11, "Foreign-team wells must be invisible to uploads");
                controller.bindToPlayerTeam(owner);
                var filler = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.DIRT), 1)),
                        List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)));
                for (int slot = 0; slot < inventory.size(); slot++) inventory.setItemDirect(slot, filler.copy());
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, choices);
                helper.assertTrue(blank.getStackInSlot(0).getCount() == 11, "Full assemblies must not consume blanks");
                inventory.clear(); blank.clear();
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, choices);
                helper.assertTrue(inventory.isEmpty(), "No blank means no uploaded pattern");
                blank.setItemDirect(0, AEItems.BLANK_PATTERN.stack(11));
                assembly.setControllerPos(null);
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, choices);
                helper.assertTrue(blank.getStackInSlot(0).getCount() == 11 && inventory.isEmpty(), "Detached assemblies must not receive patterns");
                assembly.setControllerPos(origin);
                MatterFabricationPatternUpload.unregister(assembly);
                MatterFabricationPatternUpload.upload(uploader, 42, RECIPE, choices);
                helper.assertTrue(blank.getStackInSlot(0).getCount() == 11, "Unloaded assemblies must be removed from the index");
                MatterFabricationPatternUpload.register(assembly);
                // Legacy controllers migrate once from AE2 placer data; initialized bindings do not follow team changes.
                var legacy = controller.saveWithFullMetadata(level.registryAccess());
                legacy.remove("fabrication_bound_team"); legacy.remove("fabrication_team_bound");
                controller.loadWithComponents(legacy, level.registryAccess());
                helper.assertTrue(team.equals(controller.getBoundTeam()), "Legacy controller must migrate from its saved AE2 placer");
                if (ftb) {
                    FtbFixture.leaveParty(uploader);
                    helper.assertTrue(!team.equals(FtbTeamOwnership.forPlayer(uploader)) && team.equals(controller.getBoundTeam()),
                            "Leaving the team must not change the well's placement binding");
                }
            } finally {
                uploader.containerMenu = previousMenu;
                level.getRecipeManager().replaceRecipes(recipesBefore);
                inventory.clear();
                for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
            }
            helper.succeed();
        });
    }

    private static int filled(appeng.api.inventories.InternalInventory inventory) {
        int result = 0; for (var stack : inventory) if (!stack.isEmpty()) result++; return result;
    }

    private static MatterFabricationRecipe recipe() {
        return new MatterFabricationRecipe(List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 10_000_000)),
                List.of(new ItemStack(Items.EMERALD, 3), new ItemStack(Items.IRON_INGOT, 7)),
                new FluidStack(Fluids.WATER, 1000), new FluidStack(Fluids.LAVA, 500),
                List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 500), new GenericStack(GAS, 3_000_000_000L)),
                List.of(new GenericStack(GAS, 4_000_000_001L)), 20, 1, false);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.CREATIVE); return player;
    }

    private static final class FtbFixture {
        private static UUID sharedParty(ServerPlayer owner, ServerPlayer member) throws Exception {
            var manager = (dev.ftb.mods.ftbteams.data.TeamManagerImpl) dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
            manager.playerLoggedIn(null, owner.getUUID(), owner.getGameProfile().getName());
            manager.playerLoggedIn(null, member.getUUID(), member.getGameProfile().getName());
            var party = manager.createParty(owner.getUUID(), null, "WellUploadFixture", null, null);
            party.join(null, member.getGameProfile()); return party.getTeamId();
        }

        private static void leaveParty(ServerPlayer player) {
            var manager = (dev.ftb.mods.ftbteams.data.TeamManagerImpl) dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
            try {
                ((dev.ftb.mods.ftbteams.data.PartyTeam) manager.getTeamForPlayer(player).orElseThrow()).leave(player.getUUID());
            } catch (Exception exception) { throw new AssertionError(exception); }
        }
    }
}
