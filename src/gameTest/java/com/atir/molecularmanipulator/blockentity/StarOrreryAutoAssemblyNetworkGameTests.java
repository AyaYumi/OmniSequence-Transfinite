package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.PlayerSource;
import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.building.BuildingType;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real powered AE2 grid, finite physical item cell and the actual upgrade menu action. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class StarOrreryAutoAssemblyNetworkGameTests {
    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 400, batch = "orrery_auto_assembly_network")
    public static void fourthRingSpendsBackpackFirstAndOnlyTheRemainderFromAFiniteMeCell(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        for (var entry : BuildingType.STAR_ORRERY_L3.template()) {
            fixture.place(fixture.origin.offset(entry.offset()), entry.state());
        }
        // Register every production-written position for loaded chunks and fixture-owned cleanup.
        for (int index = 0; index < StarOrreryLayout.slotCount(3); index++) {
            fixture.place(StarOrreryLayout.slot(fixture.origin, Direction.NORTH, 3, index),
                    Blocks.AIR.defaultBlockState());
        }
        var core = (StarComputationEngineCoreBlockEntity) fixture.level.getBlockEntity(fixture.origin);
        helper.assertTrue(core != null, "The L3 building supplies a real core block entity");
        var power = fixture.place(fixture.origin.east(64), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),
                AENetworkedBlockEntity.class);
        var drive = fixture.place(fixture.origin.east(68), AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        var player = fixture.player("orrery-me-assembly", fixture.origin);
        var item = ModContent.QUANTUM_STAR_POSITION_ANCHOR.get().asItem();
        var key = AEItemKey.of(new ItemStack(item));
        var source = new PlayerSource(player);
        var menu = new StarComputationEngineMenu[1];

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(core.getMainNode().getNode() != null && power.getMainNode().getNode() != null
                            && drive.getMainNode().getNode() != null,
                    "All three real AE2 nodes initialize before connecting");
        }).thenExecute(() -> {
            GridHelper.createConnection(core.getMainNode().getNode(), power.getMainNode().getNode());
            GridHelper.createConnection(power.getMainNode().getNode(), drive.getMainNode().getNode());
        }).thenWaitUntil(() -> {
            core.refreshStructureNow();
            helper.assertTrue(core.isFormed() && core.getLitRings() == 3 && core.getCertifiedLevel() == 3,
                    "The live L3 blueprint is formed with its original L3 certification");
            helper.assertTrue(core.getMainNode().isActive() && power.getMainNode().isActive()
                            && drive.getMainNode().isActive()
                            && core.getMainNode().getGrid() == drive.getMainNode().getGrid()
                            && core.getMainNode().getGrid() == power.getMainNode().getGrid()
                            && drive.getOriginalCellInventory(0) != null,
                    "The core, creative power and finite 1K disk belong to one powered, active AE2 grid");
        }).thenExecute(() -> {
            var rule = StarComputationUpgrades.rule(fixture.level, 4);
            helper.assertTrue(rule != null && rule.recipe().autoBuild() && rule.recipe().energy() == 0,
                    "Exercise the shipped immediate automatic L4 rule without replacing recipes");
            player.getInventory().setItem(0, new ItemStack(item, 11));
            var storage = core.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(storage.insert(key, 64, Actionable.MODULATE, source) == 64,
                    "Seed 64 physical quantum anchors through the actual ME storage service");
            helper.assertTrue(drive.getOriginalCellInventory(0).getAvailableStacks().get(key) == 64,
                    "All seeded ME anchors physically reside in the finite disk mounted in the real drive");
            for (int preview = 0; preview < 3; preview++) {
                var plan = StarOrreryAutoAssembly.plan(core, 4, player);
                var supply = plan.supplies().get(1);
                helper.assertTrue(plan.blocked() == null && supply.tier() == 1 && supply.placed() == 0
                                && supply.required() == 48 && supply.available() == 75,
                        "Preview combines exactly 11 backpack anchors and 64 extractable anchors on this ME grid");
                helper.assertTrue(player.getInventory().countItem(item) == 11
                                && storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == 64
                                && drive.getOriginalCellInventory(0).getAvailableStacks().get(key) == 64,
                        "Repeated preview and storage simulation never consume either material source");
            }
            core.openMenu(player);
            helper.assertTrue(player.containerMenu instanceof StarComputationEngineMenu,
                    "Open the real server menu for the nearby survival player");
            menu[0] = (StarComputationEngineMenu) player.containerMenu;
            menu[0].broadcastChanges();
            helper.assertTrue(menu[0].canConfigure && menu[0].upgrades.entry(4).conditionsMet(),
                    "The synchronized upgrade conditions see the combined backpack and ME supply");
            menu[0].receiveClientAction("orrery_upgrade_start", "4");
            helper.assertTrue(menu[0].actionResult == StarComputationEngineMenu.ActionResult.UPGRADE_STARTED,
                    "The actual menu action accepts the fourth-ring construction request");
            helper.assertTrue(player.getInventory().countItem(item) == 0,
                    "Construction consumes all 11 backpack anchors before taking any ME shortfall");
            helper.assertTrue(storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == 27
                            && drive.getOriginalCellInventory(0).getAvailableStacks().get(key) == 27,
                    "Only the 37 remaining sockets consume ME items: 64 minus 37 leaves 27 in the real disk");
            helper.assertTrue(core.getLitRings() == 4 && core.getCertifiedLevel() == 4
                            && core.getAnchorCount(1) == 48 && core.getUpgradeTarget() == 0
                            && !core.isUpgradeRunning() && core.getUpgradeEnergy() == 0,
                    "The same action places all 48 quantum anchors and immediately certifies L4 without a payment task");
            assertFourthRing(helper, fixture, core);
        }).thenIdle(5).thenExecute(() -> {
            var storage = core.getMainNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(player.getInventory().countItem(item) == 0
                            && storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == 27
                            && drive.getOriginalCellInventory(0).getAvailableStacks().get(key) == 27,
                    "Subsequent native ticks neither debit the disk again nor synthesize a material refund");
            var plan = StarOrreryAutoAssembly.plan(core, 4, player);
            var quantum = plan.supplies().get(1);
            helper.assertTrue(quantum.placed() == 48 && quantum.required() == 48 && quantum.available() == 27,
                    "The completed preview credits 48 installed anchors while leaving the remaining disk inventory untouched");
            assertFourthRing(helper, fixture, core);
            helper.assertTrue(fixture.level.getEntitiesOfClass(ItemEntity.class, new AABB(fixture.origin).inflate(100)).isEmpty(),
                    "The successful transaction leaves no spilled or refunded material entities");
        }).thenSucceed();
    }

    private static void assertFourthRing(GameTestHelper helper, ServerRegressionFixture fixture,
            StarComputationEngineCoreBlockEntity core) {
        for (int index = 0; index < StarOrreryLayout.slotCount(3); index++) {
            var pos = StarOrreryLayout.slot(fixture.origin, Direction.NORTH, 3, index);
            var state = fixture.level.getBlockState(pos);
            helper.assertTrue(state.is(ModContent.QUANTUM_STAR_POSITION_ANCHOR.get())
                            && state.getValue(StarPositionAnchorBlock.FORMED)
                            && state.getValue(StarPositionAnchorBlock.STAR_ORRERY)
                            && state.getValue(StarPositionAnchorBlock.ANCHOR_POS) == 3
                            && state.getValue(HorizontalDirectionalBlock.FACING) == Direction.NORTH
                            && core.isStarOrreryMember(pos),
                    "Every fourth-ring position contains a correctly formed, owned physical quantum anchor");
        }
    }
}
