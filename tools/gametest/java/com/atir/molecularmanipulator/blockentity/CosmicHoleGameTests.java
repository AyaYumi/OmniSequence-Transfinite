package com.atir.molecularmanipulator.blockentity;

import appeng.api.stacks.AEItemKey;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.world.WhiteHoleRegistry;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class CosmicHoleGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void placementTeamRoutingRemovalAndPersistence(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var owner = player(helper, "HoleOwner");
        var teammate = player(helper, "HoleMember");
        var foreign = player(helper, "HoleForeign");
        boolean ftb = ModList.get().isLoaded("ftbteams");
        UUID team = ftb ? FtbFixture.sharedParty(owner, teammate) : owner.getUUID();
        if (!ftb) teammate = owner;

        var entrance = helper.absolutePos(new BlockPos(0, 5, 0));
        var near = entrance.offset(20, 0, 0);
        var far = entrance.offset(40, 0, 0);
        var otherTeam = entrance.offset(5, 0, 0);
        var positions = List.of(entrance, near, far, otherTeam);
        var chunks = new java.util.HashSet<net.minecraft.world.level.ChunkPos>();
        for (var pos : positions) {
            var chunk = new net.minecraft.world.level.ChunkPos(pos);
            chunks.add(chunk);
            level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunkAt(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        var items = new ArrayList<ItemEntity>();
        try {
            var black = place(helper, entrance, owner, false);
            var whiteFar = place(helper, far, teammate, true);
            var whiteNear = place(helper, near, teammate, true);
            place(helper, otherTeam, foreign, true);
            whiteNear.serverTick();
            whiteFar.serverTick();
            helper.assertTrue(!MultiblockChunkLoading.ownedChunks(level, near).isEmpty()
                    && !MultiblockChunkLoading.ownedChunks(level, far).isEmpty(),
                    "Every placed white hole must own its own ticking ticket");
            helper.assertTrue(team.equals(black.getBoundTeam())
                    && team.equals(whiteFar.getBoundTeam()) && team.equals(whiteNear.getBoundTeam()),
                    "Real placement must bind both holes to the effective team");
            helper.assertTrue(near.equals(WhiteHoleRegistry.nearest(level, entrance, team)),
                    "Multiple exits must place; nearer foreign-team exit must be ignored");

            var item = capture(helper, black, Items.DIAMOND, 23);
            items.add(item);
            assertExit(helper, item, near);
            helper.assertTrue(item.getItem().getCount() == 23 && item.getItem().is(Items.DIAMOND),
                    "Teleport must preserve the actual item entity and complete stack");
            helper.assertTrue(whiteNear.getBoundTeam().equals(
                    whiteNear.saveWithFullMetadata(level.registryAccess()).getUUID("bound_team")),
                    "Team ownership must be persisted in the block entity");

            var restored = (CosmicSingularityBlockEntity) BlockEntity.loadStatic(
                    entrance, black.getBlockState(), black.saveWithFullMetadata(level.registryAccess()),
                    level.registryAccess());
            helper.assertTrue(restored != null && team.equals(restored.getBoundTeam()),
                    "Black-hole binding must survive block-entity reload");
            level.removeBlockEntity(entrance);
            level.setBlockEntity(restored);
            black = restored;
            var restoredWhite = (CosmicSingularityBlockEntity) BlockEntity.loadStatic(
                    far, whiteFar.getBlockState(), whiteFar.saveWithFullMetadata(level.registryAccess()),
                    level.registryAccess());
            level.removeBlockEntity(far);
            level.setBlockEntity(restoredWhite);
            restoredWhite.serverTick();
            helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, far)
                    .contains(new net.minecraft.world.level.ChunkPos(far)),
                    "Reloaded white hole must keep its own ticking chunk ticket");

            level.setBlock(near, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(MultiblockChunkLoading.ownedChunks(level, near).isEmpty(),
                    "Removing one exit must release that exit's tickets");
            var second = capture(helper, black, Items.EMERALD, 7);
            items.add(second);
            assertExit(helper, second, far);
            level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(WhiteHoleRegistry.nearest(level, entrance, team) == null,
                    "With no same-team exit, a foreign white hole must never be used");
            var idle = capture(helper, black, Items.IRON_INGOT, 3);
            items.add(idle);
            helper.assertTrue(idle.position().distanceTo(entrance.getCenter()) < .01,
                    "A black hole without its own team's exit must remain idle");

            // A legacy hole has no placement owner. It must not become a global route.
            var legacy = black.saveWithFullMetadata(level.registryAccess());
            legacy.remove("bound_team");
            black.loadWithComponents(legacy, level.registryAccess());
            helper.assertTrue(black.getBoundTeam() == null
                    && WhiteHoleRegistry.nearest(level, entrance, null) == null,
                    "Old unbound holes must not route through arbitrary teams");
            System.out.println("COSMIC_HOLES_ROUTING_PASS ftb=" + ftb
                    + " multipleExits=true nearestSameTeam=true reload=true removalFallback=true items=preserved");
            helper.succeed();
        } finally {
            items.forEach(ItemEntity::discard);
            for (var pos : positions) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), 3);
            }
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void whiteHoleConsumesTenBlackHoles(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var white = (MatterFabricationRecipe) recipes.byKey(id("white_hole")).orElseThrow().value();
        var black = (MatterFabricationRecipe) recipes.byKey(id("black_hole")).orElseThrow().value();
        helper.assertTrue(white.aeInputs().size() == 1
                && white.aeInputs().getFirst().what().equals(AEItemKey.of(ModContent.BLACK_HOLE.get()))
                && white.aeInputs().getFirst().amount() == 10
                && white.results().size() == 1
                && white.results().getFirst().is(ModContent.WHITE_HOLE.get())
                && white.results().getFirst().getCount() == 1,
                "Loaded white-hole recipe must consume ten black holes for one white hole");
        helper.assertTrue(black.aeInputs().size() == 1
                && black.aeInputs().getFirst().what().equals(
                        AEItemKey.of(appeng.core.definitions.AEItems.SINGULARITY.stack()))
                && black.aeInputs().getFirst().amount() == 10_000,
                "Black-hole input must be 10,000 AE2 singularities");
        helper.assertTrue(white.processingTime() == 600 && white.aePerTick() == 4096
                && white.requiresResearch(), "White-hole time, energy and unlock must stay unchanged");
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    private static CosmicSingularityBlockEntity place(GameTestHelper helper, BlockPos pos,
            ServerPlayer player, boolean white) {
        var level = helper.getLevel();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var item = white ? ModContent.WHITE_HOLE.get() : ModContent.BLACK_HOLE.get();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        var hit = new BlockHitResult(pos.getCenter().add(0, -.5, 0), Direction.UP, pos.below(), false);
        var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(((BlockItem) item).place(context).consumesAction(),
                "Actual hole item placement must succeed at " + pos);
        return (CosmicSingularityBlockEntity) level.getBlockEntity(pos);
    }

    private static ItemEntity capture(GameTestHelper helper, CosmicSingularityBlockEntity black,
            net.minecraft.world.item.Item item, int count) {
        var center = black.getBlockPos().getCenter();
        var entity = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, new ItemStack(item, count));
        helper.getLevel().addFreshEntity(entity);
        black.serverTick();
        return entity;
    }

    private static void assertExit(GameTestHelper helper, ItemEntity entity, BlockPos exit) {
        helper.assertTrue(entity.position().distanceTo(exit.getCenter().add(0, .85, 0)) < .01,
                "Captured entity must arrive at the nearest same-team exit");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", path);
    }

    // Test fixture only: seed real FTB player/party data without needing networked clients.
    private static final class FtbFixture {
        private static UUID sharedParty(ServerPlayer owner, ServerPlayer member) throws Exception {
            var manager = (dev.ftb.mods.ftbteams.data.TeamManagerImpl)
                    dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api().getManager();
            manager.playerLoggedIn(null, owner.getUUID(), owner.getGameProfile().getName());
            manager.playerLoggedIn(null, member.getUUID(), member.getGameProfile().getName());
            var party = manager.createParty(owner.getUUID(), null, "CosmicHoleFixture", null, null);
            party.join(null, member.getGameProfile());
            return party.getTeamId();
        }
    }
}
