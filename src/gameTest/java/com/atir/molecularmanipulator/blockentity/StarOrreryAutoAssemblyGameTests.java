package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.building.BuildingType;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Default data-pack rules and the real player-triggered transaction, without a powered ME grid. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class StarOrreryAutoAssemblyGameTests {
    private record Site(ServerRegressionFixture fixture, StarComputationEngineCoreBlockEntity core,
                        ServerPlayer player, int initialRings, int target) {}

    // One serial test in its own batch: the paid-upgrade regression's temporary recipe replacement
    // must never hide this test's ordinary, data-pack-provided zero-energy L4 rule.
    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 300, batch = "orrery_auto_assembly")
    public static void automaticAssemblyUsesRealSlotsAndRollsBackFailures(GameTestHelper helper) {
        var full = site(helper, "auto-full", 3, 4);
        var shortStock = site(helper, "auto-short", 3, 4);
        var blocked = site(helper, "auto-blocked", 3, 4);
        var partial = site(helper, "auto-partial", 3, 4);
        var canceled = site(helper, "auto-canceled", 3, 4);
        var firstRing = site(helper, "auto-first", 0, 1);
        var sites = List.of(full, shortStock, blocked, partial, canceled, firstRing);

        helper.startSequence().thenWaitUntil(() -> {
            for (var site : sites) {
                site.core().refreshStructureNow();
                helper.assertTrue(site.core().isFormed() && site.core().getLitRings() == site.initialRings()
                                && site.core().getCertifiedLevel() == 3,
                        "The real blueprint forms exactly its existing rings before the explicit action");
            }
        }).thenExecute(() -> {
            var rule = StarComputationUpgrades.rule(full.fixture().level, 4);
            helper.assertTrue(rule != null && rule.recipe().autoBuild() && rule.recipe().energy() == 0
                            && rule.recipe().aePerTick() == 1 && rule.recipe().conditions().type().equals("anchors")
                            && rule.recipe().conditions().match().equals("all"),
                    "Use the actual default zero-energy automatic L4 recipe, not a manufactured test rule");
            helper.assertTrue(rule.recipe().conditions().targets().size() == 2
                            && rule.recipe().conditions().targets().get(0).id().equals("reference")
                            && rule.recipe().conditions().targets().get(0).count() == 96
                            && rule.recipe().conditions().targets().get(1).id().equals("quantum")
                            && rule.recipe().conditions().targets().get(1).count() == 48,
                    "Default L4 requires all 96 existing reference anchors and all 48 quantum sockets");
            completeFourthRing(helper, full);
            insufficientMaterialsDoNotDebitOrPlace(helper, shortStock);
            obstructionDoesNotDebitOrReplace(helper, blocked);
            partialRingSpendsOnlyTheMissingAnchors(helper, partial);
            canceledPlacementRestoresAllEarlierWritesAndCanRetry(helper, canceled);
            firstRingBuildRetainsExistingLowLevelCertification(helper, firstRing);
        }).thenIdle(5).thenExecute(() -> {
            for (var site : List.of(full, partial, canceled)) assertComplete(helper, site, 4, 4);
            assertComplete(helper, firstRing, 1, 3);
            assertUnstarted(helper, shortStock);
            assertUnstarted(helper, blocked);
            assertRingAir(helper, shortStock, 3, null);
            var obstruction = slot(blocked, 3, StarOrreryLayout.slotCount(3) - 1);
            assertRingAir(helper, blocked, 3, obstruction);
            helper.assertTrue(blocked.fixture().level.getBlockState(obstruction).is(Blocks.STONE),
                    "A failed transaction leaves the player's obstructing block untouched");
            for (var site : sites) assertNoDrops(helper, site);
        }).thenSucceed();
    }

    private static Site site(GameTestHelper helper, String name, int initialRings, int target) {
        var fixture = new ServerRegressionFixture(helper);
        var template = initialRings == 0 ? BuildingType.STAR_ORRERY_L1 : BuildingType.STAR_ORRERY_L3;
        for (var entry : template.template()) fixture.place(fixture.origin.offset(entry.offset()), entry.state());
        // Register every missing socket with the fixture before the transaction: this loads the exact
        // touched chunks and lets fixture cleanup remove anchors created by production code too.
        for (int ring = initialRings; ring < target; ring++) {
            for (int index = 0; index < StarOrreryLayout.slotCount(ring); index++) {
                fixture.place(StarOrreryLayout.slot(fixture.origin, Direction.NORTH, ring, index),
                        Blocks.AIR.defaultBlockState());
            }
        }
        var core = (StarComputationEngineCoreBlockEntity) fixture.level.getBlockEntity(fixture.origin);
        helper.assertTrue(core != null, "The real building blueprint supplies a live core block entity");
        return new Site(fixture, core, fixture.player(name, fixture.origin), initialRings, target);
    }

    private static void completeFourthRing(GameTestHelper helper, Site site) {
        quantumStock(site, 48);
        var plan = StarOrreryAutoAssembly.plan(site.core(), 4, site.player());
        helper.assertTrue(plan.blocked() == null, "Every fourth-ring socket is loaded, free and permitted");
        assertSupply(helper, plan, 0, 96, 96, 0);
        assertSupply(helper, plan, 1, 0, 48, 48);
        helper.assertTrue(quantumCount(site) == 48, "The plan previews inventory without consuming it");
        assertRingAir(helper, site, 3, null);
        helper.assertTrue(!site.core().getMainNode().isActive(), "No creative cell or powered AE grid is supplied");
        helper.assertTrue(site.core().startUpgrade(4, site.player()), "The real server action builds and upgrades L4");
        helper.assertTrue(quantumCount(site) == 0, "Exactly 48 real quantum anchors leave the survival inventory");
        assertComplete(helper, site, 4, 4);
        var completed = StarOrreryAutoAssembly.plan(site.core(), 4, site.player());
        assertSupply(helper, completed, 0, 96, 96, 0);
        assertSupply(helper, completed, 1, 48, 48, 0);
    }

    private static void insufficientMaterialsDoNotDebitOrPlace(GameTestHelper helper, Site site) {
        quantumStock(site, 47);
        var plan = StarOrreryAutoAssembly.plan(site.core(), 4, site.player());
        helper.assertTrue(plan.blocked() == null, "Insufficient stock is not a world obstruction");
        assertSupply(helper, plan, 1, 0, 48, 47);
        helper.assertTrue(!site.core().startUpgrade(4, site.player()), "One missing material rejects the whole transaction");
        helper.assertTrue(quantumCount(site) == 47 && site.core().getUpgradeStatus().equals("anchor_materials"),
                "The material preflight reports the shortage without spending the other 47 anchors");
        assertRingAir(helper, site, 3, null);
        assertUnstarted(helper, site);
    }

    private static void obstructionDoesNotDebitOrReplace(GameTestHelper helper, Site site) {
        quantumStock(site, 48);
        // Deliberately obstruct the final socket: a broken incremental implementation would already
        // have consumed items and built 47 anchors before discovering this block.
        var obstruction = slot(site, 3, StarOrreryLayout.slotCount(3) - 1);
        site.fixture().place(obstruction, Blocks.STONE.defaultBlockState());
        var plan = StarOrreryAutoAssembly.plan(site.core(), 4, site.player());
        helper.assertTrue("anchor_space".equals(plan.blocked()), "The preview identifies an occupied real socket");
        helper.assertTrue(!site.core().startUpgrade(4, site.player()), "The real start refuses an occupied socket");
        helper.assertTrue(quantumCount(site) == 48 && site.fixture().level.getBlockState(obstruction).is(Blocks.STONE),
                "An obstructed build neither debits materials nor replaces the obstructing block");
        assertRingAir(helper, site, 3, obstruction);
        assertUnstarted(helper, site);
    }

    private static void partialRingSpendsOnlyTheMissingAnchors(GameTestHelper helper, Site site) {
        quantumStock(site, 48);
        for (int index = 0; index < 11; index++) {
            site.fixture().place(slot(site, 3, index), ModContent.QUANTUM_STAR_POSITION_ANCHOR.get().defaultBlockState());
        }
        var plan = StarOrreryAutoAssembly.plan(site.core(), 4, site.player());
        helper.assertTrue(plan.blocked() == null, "Correct preplaced anchors are accepted as existing sockets");
        assertSupply(helper, plan, 1, 11, 48, 48);
        helper.assertTrue(site.core().startUpgrade(4, site.player()), "The real action completes a partially built ring");
        helper.assertTrue(quantumCount(site) == 11, "Only the 37 missing sockets consume inventory materials");
        assertComplete(helper, site, 4, 4);
    }

    private static void canceledPlacementRestoresAllEarlierWritesAndCanRetry(GameTestHelper helper, Site site) {
        quantumStock(site, 48);
        var events = new ArrayList<BlockPos>();
        Consumer<BlockEvent.EntityPlaceEvent> cancelSeventh = event -> {
            if (event.getEntity() != site.player() || event.getLevel() != site.fixture().level) return;
            events.add(event.getPos().immutable());
            if (events.size() == 7) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, cancelSeventh);
        site.fixture().onCleanup(() -> NeoForge.EVENT_BUS.unregister(cancelSeventh));
        boolean started;
        try {
            started = site.core().startUpgrade(4, site.player());
        } finally {
            NeoForge.EVENT_BUS.unregister(cancelSeventh);
        }
        helper.assertTrue(!started && events.size() == 7 && events.stream().distinct().count() == 7,
                "A real NeoForge place event cancels the seventh distinct slot after six successful writes");
        helper.assertTrue(quantumCount(site) == 48 && site.core().getUpgradeStatus().equals("anchor_permission"),
                "Cancellation refunds all withdrawn items and reports the placement permission failure");
        assertRingAir(helper, site, 3, null);
        assertUnstarted(helper, site);
        assertNoDrops(helper, site);
        helper.assertTrue(site.core().startUpgrade(4, site.player()), "The unchanged stock can retry after the listener is removed");
        helper.assertTrue(quantumCount(site) == 0 && events.size() == 7,
                "A successful retry spends each anchor once and leaves no event listener behind");
        assertComplete(helper, site, 4, 4);
    }

    private static void firstRingBuildRetainsExistingLowLevelCertification(GameTestHelper helper, Site site) {
        var item = ModContent.REFERENCE_STAR_POSITION_ANCHOR.get().asItem();
        site.player().getInventory().setItem(0, new ItemStack(item, 24));
        var plan = StarOrreryAutoAssembly.plan(site.core(), 1, site.player());
        helper.assertTrue(plan.blocked() == null, "A complete L0 base has 24 empty first-ring sockets");
        assertSupply(helper, plan, 0, 0, 24, 24);
        helper.assertTrue(site.core().startUpgrade(1, site.player()), "The same explicit action supports physical L1 auto-assembly");
        helper.assertTrue(site.player().getInventory().countItem(item) == 0, "The first ring spends exactly 24 reference anchors");
        assertComplete(helper, site, 1, 3);
        helper.assertTrue(site.core().getEffectiveLevel() == 1,
                "L1 benefits still use min(physical, existing L3 certification), not a changed low-level certification rule");
    }

    private static void assertSupply(GameTestHelper helper, StarOrreryAutoAssembly.Plan plan,
                                     int tier, int placed, int required, long available) {
        helper.assertTrue(plan.supplies().size() == 3, "Plans expose exactly the three real anchor tiers");
        var supply = plan.supplies().get(tier);
        helper.assertTrue(supply.tier() == tier && supply.placed() == placed && supply.required() == required
                        && supply.available() == available,
                "Expected tier " + tier + " placed=" + placed + ", required=" + required + ", stock=" + available
                        + "; got " + supply);
    }

    private static void assertComplete(GameTestHelper helper, Site site, int physical, int certified) {
        helper.assertTrue(site.core().isFormed() && site.core().getLitRings() == physical
                        && site.core().getCertifiedLevel() == certified && site.core().getUpgradeTarget() == 0
                        && site.core().getUpgradeEnergy() == 0 && site.core().getUpgradeRate() == 0
                        && !site.core().isUpgradeRunning(),
                "Explicit construction immediately reaches physical L" + physical + " with certification L" + certified
                        + ", no AE payment and no retained running target");
        for (int ring = 0; ring < site.target(); ring++) {
            var block = ring < 3 ? ModContent.REFERENCE_STAR_POSITION_ANCHOR.get()
                    : ModContent.QUANTUM_STAR_POSITION_ANCHOR.get();
            for (int index = 0; index < StarOrreryLayout.slotCount(ring); index++) {
                var pos = slot(site, ring, index);
                var state = site.fixture().level.getBlockState(pos);
                helper.assertTrue(state.is(block) && state.getValue(StarPositionAnchorBlock.FORMED)
                                && state.getValue(StarPositionAnchorBlock.STAR_ORRERY)
                                && state.getValue(StarPositionAnchorBlock.ANCHOR_POS) == ring
                                && state.getValue(HorizontalDirectionalBlock.FACING) == Direction.NORTH
                                && site.core().getBlockPos().equals(StarOrreryOwnership.owner(site.fixture().level, pos)),
                        "Each exact world socket must be a correctly tiered, formed and owned ring member: " + pos);
            }
        }
    }

    private static void assertUnstarted(GameTestHelper helper, Site site) {
        helper.assertTrue(site.core().getLitRings() == site.initialRings() && site.core().getCertifiedLevel() == 3
                        && site.core().getUpgradeTarget() == 0 && site.core().getUpgradeEnergy() == 0
                        && !site.core().isUpgradeRunning(),
                "A rejected transaction changes neither physical rings, certification nor paid progress");
    }

    private static void assertRingAir(GameTestHelper helper, Site site, int ring, BlockPos except) {
        for (int index = 0; index < StarOrreryLayout.slotCount(ring); index++) {
            var pos = slot(site, ring, index);
            if (!pos.equals(except)) helper.assertTrue(site.fixture().level.getBlockState(pos).isAir(),
                    "An unbuilt or rolled-back socket stays exact air: " + pos);
        }
    }

    private static void assertNoDrops(GameTestHelper helper, Site site) {
        helper.assertTrue(site.fixture().level.getEntitiesOfClass(ItemEntity.class,
                        new AABB(site.fixture().origin).inflate(40)).isEmpty(),
                "Construction and rollback must not scatter duplicate anchor-item drops");
    }

    private static BlockPos slot(Site site, int ring, int index) {
        return StarOrreryLayout.slot(site.fixture().origin, Direction.NORTH, ring, index);
    }

    private static void quantumStock(Site site, int amount) {
        site.player().getInventory().setItem(0,
                new ItemStack(ModContent.QUANTUM_STAR_POSITION_ANCHOR.get(), amount));
    }

    private static int quantumCount(Site site) {
        return site.player().getInventory().countItem(ModContent.QUANTUM_STAR_POSITION_ANCHOR.get().asItem());
    }
}
