package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.GridHelper;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.building.BuildingType;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe.Condition;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe.Target;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** One isolated recipe-manager owner, exercising native ticks, AE power, menus and loot. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class StarComputationUpgradeGameTests {
    private static final String UPGRADE_TAG = "star_orrery_upgrade";
    private static final double PRICE = 4096;
    private static final double RATE = 32;

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 600, batch = "orrery_upgrade")
    public static void fourthRingUpgradePreservesCreditAndCertification(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var level = fixture.level;
        for (var entry : BuildingType.STAR_ORRERY_L3.template())
            fixture.place(fixture.origin.offset(entry.offset()), entry.state());
        for (int slot = 0; slot < StarOrreryLayout.slotCount(3); slot++)
            fixture.place(StarOrreryLayout.slot(fixture.origin, Direction.NORTH, 3, slot),
                    ModContent.QUANTUM_STAR_POSITION_ANCHOR.get().defaultBlockState());
        var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(fixture.origin);
        var energy = fixture.place(fixture.origin.east(64), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),
                AENetworkedBlockEntity.class);
        var player = fixture.player("orrery-upgrade", fixture.origin);
        var menu = new StarComputationEngineMenu[1];
        double[] credit = {0};
        var manager = level.getRecipeManager();
        var originalRecipes = List.copyOf(manager.getRecipes());
        fixture.onCleanup(() -> manager.replaceRecipes(originalRecipes));
        replaceLevelFourRule(helper, core, originalRecipes, 1, PRICE, RATE);

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(core.getMainNode().getNode() != null && energy.getMainNode().getNode() != null,
                    "Both physical main nodes initialize normally before connecting");
        }).thenExecute(() -> GridHelper.createConnection(core.getMainNode().getNode(), energy.getMainNode().getNode()))
                .thenWaitUntil(() -> {
                    helper.assertTrue(core.isFormed() && core.getLitRings() == 4,
                            "The L3 blueprint plus a fourth quantum ring forms physical L4");
                    helper.assertTrue(core.getMainNode().isActive() && energy.getMainNode().isActive()
                                    && core.getMainNode().getGrid() == energy.getMainNode().getGrid(),
                            "A real AE connection powers the active core on the creative cell's grid");
                }).thenExecute(() -> {
                    assertState(helper, core, 4, 3, 0, 0, false);
                    core.openMenu(player);
                    helper.assertTrue(player.containerMenu instanceof StarComputationEngineMenu,
                            "Open the real server menu using the fixture's socket-free real player");
                    menu[0] = (StarComputationEngineMenu) player.containerMenu;
                    assertReadOnly(helper, core, menu[0]);
                    helper.assertTrue(menu[0].isValidMenu() && menu[0].stillValid(player) && menu[0].canConfigure
                                    && core.getBoundTeam() != null,
                            "The current nearby menu is permitted and binds an owner");
                    helper.assertTrue(menu[0].upgrades.entry(4).configured() && menu[0].upgrades.entry(4).conditionsMet(),
                            "The machines condition recognizes this same active, formed core");
                }).thenExecuteFor(8, () -> {
                    assertState(helper, core, 4, 3, 0, 0, false);
                    assertReadOnly(helper, core, menu[0]);
                }).thenExecute(() -> {
                    menu[0].receiveClientAction("orrery_upgrade_start", "5");
                    helper.assertTrue(menu[0].actionResult == StarComputationEngineMenu.ActionResult.REJECTED,
                            "The server rejects a client request that skips certification to L5");
                    assertState(helper, core, 4, 3, 0, 0, false);
                    menu[0].receiveClientAction("orrery_upgrade_start", "4");
                    helper.assertTrue(menu[0].actionResult == StarComputationEngineMenu.ActionResult.UPGRADE_STARTED,
                            "The real menu action explicitly starts permitted L4 calibration");
                    assertState(helper, core, 4, 3, 4, 0, true);
                    assertReadOnly(helper, core, menu[0]);
                    // Compare inside one callback, not against an assumed ordering of background BE/AE ticks.
                    double before = core.getUpgradeEnergy();
                    core.serverTick();
                    helper.assertTrue(core.getUpgradeRate() > 0 && core.getUpgradeRate() <= RATE
                                    && core.getUpgradeEnergy() == before + core.getUpgradeRate(),
                            "Only an actual serverTick buys bounded, real AE energy credit");
                }).thenWaitUntil(() -> helper.assertTrue(core.getUpgradeEnergy() >= 128,
                        "Normal background ticks accumulate a partial payment"))
                .thenExecute(() -> {
                    credit[0] = core.getUpgradeEnergy();
                    helper.assertTrue(credit[0] > 0 && credit[0] < PRICE, "The calibration is partially paid");
                    menu[0].receiveClientAction("orrery_upgrade_pause", "");
                    helper.assertTrue(menu[0].actionResult == StarComputationEngineMenu.ActionResult.UPGRADE_PAUSED,
                            "The real menu action manually pauses an unfinished paid upgrade");
                    core.serverTick();
                    assertState(helper, core, 4, 3, 4, credit[0], false);
                    helper.assertTrue(core.getUpgradeRate() == 0 && core.getUpgradeStatus().equals("paused"),
                            "Pausing stops payment, not the retained target or credit");
                    assertPersistence(helper, core, player, 3, 4, credit[0], false);
                }).thenExecuteFor(8, () -> {
                    assertState(helper, core, 4, 3, 4, credit[0], false);
                    helper.assertTrue(core.getUpgradeRate() == 0 && core.getUpgradeStatus().equals("paused"),
                            "Background ticks cannot implicitly resume a manual pause");
                    assertReadOnly(helper, core, menu[0]);
                }).thenExecute(() -> {
                    menu[0].receiveClientAction("orrery_upgrade_start", "5");
                    helper.assertTrue(menu[0].actionResult == StarComputationEngineMenu.ActionResult.REJECTED,
                            "A paused L4 payment cannot be redirected to L5");
                    menu[0].receiveClientAction("orrery_upgrade_start", "4");
                    helper.assertTrue(menu[0].actionResult == StarComputationEngineMenu.ActionResult.UPGRADE_STARTED,
                            "The real menu action resumes the retained L4 payment explicitly");
                    assertState(helper, core, 4, 3, 4, credit[0], true);
                    assertReadOnly(helper, core, menu[0]);
                }).thenWaitUntil(() -> helper.assertTrue(core.getUpgradeEnergy() > credit[0],
                        "The explicit resume allows further background payments"))
                .thenExecute(() -> {
                    credit[0] = core.getUpgradeEnergy();
                    replaceLevelFourRule(helper, core, originalRecipes, 2, PRICE, RATE);
                    var rule = StarComputationUpgrades.rule(level, 4);
                    helper.assertTrue(rule != null && !StarComputationUpgrades.evaluate(core, rule).passed(),
                            "Two required cores cannot be satisfied by this one physical owner");
                    assertReadOnly(helper, core, menu[0]);
                    helper.assertTrue(!menu[0].upgrades.entry(4).conditionsMet(), "The menu sees the reloaded failing rule");
                    core.serverTick();
                    assertState(helper, core, 4, 3, 4, credit[0], true);
                    helper.assertTrue(core.getUpgradeStatus().equals("conditions") && core.getUpgradeRate() == 0,
                            "A live rule change blocks payment without clearing run intent");
                    assertPersistence(helper, core, player, 3, 4, credit[0], true);
                }).thenExecuteFor(8, () -> {
                    assertState(helper, core, 4, 3, 4, credit[0], true);
                    helper.assertTrue(core.getUpgradeStatus().equals("conditions") && core.getUpgradeRate() == 0,
                            "Blocked background ticks retain every paid AE and the player's run intent");
                    assertReadOnly(helper, core, menu[0]);
                }).thenExecute(() -> {
                    // Leave only 17 AE due: the lower rate must be observed before completion, without repaying old credit.
                    double lowerPrice = credit[0] + 17;
                    helper.assertTrue(lowerPrice < PRICE, "Reload a genuinely lower price");
                    replaceLevelFourRule(helper, core, originalRecipes, 1, lowerPrice, 7);
                    assertState(helper, core, 4, 3, 4, credit[0], true);
                    assertReadOnly(helper, core, menu[0]);
                    var displayed = menu[0].upgrades.entry(4);
                    helper.assertTrue(displayed.conditionsMet() && displayed.energy() == lowerPrice && displayed.aePerTick() == 7,
                            "The same recipe manager and menu observe the new valid price and rate");
                    double before = core.getUpgradeEnergy();
                    core.serverTick();
                    helper.assertTrue(core.getUpgradeRate() > 0 && core.getUpgradeRate() <= 7,
                            "The resumed tick uses the reloaded rate, not the original 32 AE/t");
                    assertState(helper, core, 4, 3, 4, before + core.getUpgradeRate(), true);
                }).thenWaitUntil(() -> helper.assertTrue(core.getCertifiedLevel() == 4,
                        "The valid lower-price rule completes using retained credit, without another start"))
                .thenExecute(() -> {
                    assertState(helper, core, 4, 4, 0, 0, false);
                    helper.assertTrue(core.getUpgradeStatus().equals("complete"), "Completion clears only this finished target");
                    assertReadOnly(helper, core, menu[0]);
                    assertPersistence(helper, core, player, 4, 0, 0, false);
                }).thenExecuteFor(8, () -> {
                    assertState(helper, core, 4, 4, 0, 0, false);
                    assertReadOnly(helper, core, menu[0]);
                }).thenExecute(() -> {
                    helper.assertTrue(core.getUpgradeRate() == 0, "Idle ticks do not automatically start or pay for L5");
                    var anchor = StarOrreryLayout.slot(fixture.origin, Direction.NORTH, 3, 0);
                    fixture.place(anchor, Blocks.AIR.defaultBlockState());
                    core.refreshStructureNow();
                    assertState(helper, core, 3, 4, 0, 0, false);
                }).thenExecuteFor(4, () -> {
                    assertState(helper, core, 3, 4, 0, 0, false);
                    assertReadOnly(helper, core, menu[0]);
                }).thenExecute(() -> {
                    var anchor = StarOrreryLayout.slot(fixture.origin, Direction.NORTH, 3, 0);
                    fixture.place(anchor, ModContent.QUANTUM_STAR_POSITION_ANCHOR.get().defaultBlockState());
                    core.refreshStructureNow();
                    assertState(helper, core, 4, 4, 0, 0, false);
                }).thenExecuteFor(4, () -> {
                    assertState(helper, core, 4, 4, 0, 0, false);
                    assertReadOnly(helper, core, menu[0]);
                }).thenExecute(() -> assertPersistence(helper, core, player, 4, 0, 0, false)).thenSucceed();
    }

    private static void replaceLevelFourRule(GameTestHelper helper, StarComputationEngineCoreBlockEntity core,
            List<RecipeHolder<?>> original, int requiredCores, double price, double rate) {
        var recipes = new ArrayList<RecipeHolder<?>>();
        var id = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "gametest/orrery_upgrade_l4");
        for (var holder : original) {
            if (holder.value() instanceof StarComputationUpgradeRecipe recipe && recipe.level() == 4) id = holder.id();
            else recipes.add(holder);
        }
        var target = new Target(BuiltInRegistries.BLOCK.getKey(core.getBlockState().getBlock()).toString(), requiredCores, "");
        var condition = new Condition("machines", List.of(), List.of(target), "all", 1, true, true, "");
        var replacement = new StarComputationUpgradeRecipe(4, price, rate, condition);
        helper.assertTrue(replacement.error() == null, "Use a valid formed-machines upgrade rule");
        recipes.add(new RecipeHolder<>(id, replacement));
        var manager = core.getLevel().getRecipeManager();
        manager.replaceRecipes(recipes);
        helper.assertTrue(manager.getRecipes().size() == recipes.size() && manager.getRecipes().containsAll(recipes),
                "Replace only L4 upgrade recipes, preserving every other recipe holder");
        var live = StarComputationUpgrades.rule(core.getLevel(), 4);
        helper.assertTrue(live != null && live.recipe() == replacement, "Resolve exactly the current L4 rule, not a cached definition");
    }

    private static void assertState(GameTestHelper helper, StarComputationEngineCoreBlockEntity core,
            int physical, int certified, int target, double paid, boolean running) {
        helper.assertTrue(core.isFormed() && core.getLitRings() == physical && core.getCertifiedLevel() == certified
                        && core.getEffectiveLevel() == Math.min(physical, certified)
                        && core.getEffectiveEntropy() == StarOrreryLayout.entropyForRings(Math.min(physical, certified)),
                "Physical L" + physical + " retains certification L" + certified + " and only the lesser effective level");
        helper.assertTrue(core.getUpgradeTarget() == target && core.getUpgradeEnergy() == paid && core.isUpgradeRunning() == running,
                "Expected target=" + target + ", paid=" + paid + ", running=" + running
                        + "; got target=" + core.getUpgradeTarget() + ", paid=" + core.getUpgradeEnergy()
                        + ", running=" + core.isUpgradeRunning());
    }

    private static void assertReadOnly(GameTestHelper helper, StarComputationEngineCoreBlockEntity core,
            StarComputationEngineMenu menu) {
        var before = core.saveWithFullMetadata(helper.getLevel().registryAccess()).getCompound(UPGRADE_TAG).copy();
        double rate = core.getUpgradeRate();
        String status = core.getUpgradeStatus();
        // Enough broadcasts to refresh the real menu's ten-broadcast catalog cache, without advancing world time.
        for (int repeat = 0; repeat < 12; repeat++) {
            menu.broadcastChanges();
            helper.assertTrue(menu.litRings == core.getLitRings() && menu.benefitLevel == core.getEffectiveLevel()
                            && menu.certifiedLevel == core.getCertifiedLevel() && menu.upgradeTarget == core.getUpgradeTarget()
                            && menu.upgradeEnergy == core.getUpgradeEnergy() && menu.upgradeRunning == core.isUpgradeRunning()
                            && menu.upgradeRate == core.getUpgradeRate() && menu.upgradeStatus.equals(core.getUpgradeStatus()),
                    "The real menu broadcasts the core's current public telemetry");
        }
        helper.assertTrue(before.equals(core.saveWithFullMetadata(helper.getLevel().registryAccess()).getCompound(UPGRADE_TAG))
                        && rate == core.getUpgradeRate() && status.equals(core.getUpgradeStatus()),
                "Menu broadcasts and repeated runtime getters neither pay nor mutate upgrade progress/rate/status");
    }

    private static void assertPersistence(GameTestHelper helper, StarComputationEngineCoreBlockEntity core,
            ServerPlayer player, int certified, int target, double paid, boolean running) {
        var expected = new CompoundTag();
        expected.putInt("version", 1);
        expected.putInt("certified_level", certified);
        expected.putInt("target_level", target);
        expected.putDouble("paid_ae", paid);
        expected.putBoolean("running", running);
        expected.putBoolean("migration_pending", false);
        var level = helper.getLevel();
        var saved = core.saveWithFullMetadata(level.registryAccess());
        helper.assertTrue(saved.contains(UPGRADE_TAG, Tag.TAG_COMPOUND) && saved.getCompound(UPGRADE_TAG).equals(expected),
                "saveWithFullMetadata stores all upgrade fields in the root star_orrery_upgrade compound: " + saved.getCompound(UPGRADE_TAG));
        helper.assertTrue(core.getBoundTeam() != null && saved.hasUUID("entropy_team")
                        && core.getBoundTeam().equals(saved.getUUID("entropy_team")),
                "The initiating owner's bound team persists alongside per-core progress");

        // Ordinary block loot, not AE exportSettings or a manufactured custom-data item.
        var drops = Block.getDrops(core.getBlockState(), level, core.getBlockPos(), core, player, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.assertTrue(drops.size() == 1 && drops.getFirst().getCount() == 1
                        && drops.getFirst().is(ModContent.STAR_COMPUTATION_ENGINE_CORE.get().asItem()),
                "The normal core loot table returns one ordinary controller item");
        var itemData = drops.getFirst().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        helper.assertTrue(itemData.contains(UPGRADE_TAG, Tag.TAG_COMPOUND) && itemData.getCompound(UPGRADE_TAG).equals(expected)
                        && itemData.hasUUID("entropy_team") && core.getBoundTeam().equals(itemData.getUUID("entropy_team")),
                "Ordinary drops retain the nested upgrade tag and bound team in custom_data");

        var original = saved.copy();
        var destination = core.getBlockPos().east(128).above(3);
        var relocated = BuildingTransfer.relocated(saved, destination, destination, 2400);
        helper.assertTrue(relocated.getInt("x") == destination.getX() && relocated.getInt("y") == destination.getY()
                        && relocated.getInt("z") == destination.getZ() && saved.equals(original),
                "Relocation moves infrastructure coordinates without mutating the saved source");
        helper.assertTrue(relocated.contains(UPGRADE_TAG, Tag.TAG_COMPOUND) && relocated.getCompound(UPGRADE_TAG).equals(expected)
                        && relocated.hasUUID("entropy_team") && core.getBoundTeam().equals(relocated.getUUID("entropy_team")),
                "BuildingTransfer.relocated preserves certification, partial credit, run intent and bound team verbatim");
    }
}
