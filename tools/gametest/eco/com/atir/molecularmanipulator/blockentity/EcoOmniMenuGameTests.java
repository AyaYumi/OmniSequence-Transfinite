package com.atir.molecularmanipulator.blockentity;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ISubMenuHost;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.menu.ISubMenu;
import appeng.menu.guisync.GuiSync;
import appeng.menu.locator.MenuLocators;
import appeng.menu.me.crafting.CraftConfirmMenu;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.menu.ECOCraftConfirmMenuMode;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanMenuAccess;
import com.atir.molecularmanipulator.integration.eco.EcoOmniParentOrderCpu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loads the actual transformed menu and executes its public ECO entry point on a powered Omni CPU. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class EcoOmniMenuGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void ecoAndOmniMenuMixinsMergeAgainstInstalledApi(GameTestHelper helper) throws ReflectiveOperationException {
        var menu = CraftConfirmMenu.class;
        helper.assertTrue(ECOCraftConfirmMenuMode.class.isAssignableFrom(menu), "ECO menu API must be present");
        helper.assertTrue(menu.getDeclaredField("result").getType() == ICraftingPlan.class
                        && menu.getDeclaredField("selectedCpu").getType() == ICraftingCPU.class
                        && field("neoecoae$confirmedPlanningResult").getType() == ECOPlanningResult.class
                        && field("neoecoae$originalOptions").getType() == ECOPlannerOptions.class,
                "Original AE2 fields and ECO fields must be accessible on the actual transformed class");
        helper.assertTrue(field("neoecoae$bigOrderCpu").getAnnotation(GuiSync.class).value() == 108,
                "Omni availability must use ECO's real synchronized field");
        helper.assertTrue(menu.getDeclaredMethod("neoecoae$startBigOrder", boolean.class).getReturnType() == void.class,
                "Omni must inject into ECO's public menu interface method");
        for (var handler : new String[] { "molecularmanipulator$syncOmniBigOrderCpu",
                "molecularmanipulator$includeOmniBigOrderCpu", "molecularmanipulator$startOmniBigOrder" }) {
            helper.assertTrue(Arrays.stream(menu.getDeclaredMethods()).anyMatch(method -> method.getName().endsWith(handler)),
                    "Missing transformed Omni menu injection: " + handler);
        }

        var level = helper.getLevel();
        var pos = new BlockPos(985_192, 100, 960);
        for (var at : List.of(pos, pos.east())) {
            level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
            level.getChunkAt(at);
            if (level.getBlockEntity(at) instanceof AEBaseBlockEntity old) old.clearContent();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        level.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        helper.startSequence().thenWaitUntil(() -> {
            var core = (OmniComputationCoreBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(core != null && core.isNetworkOnline() && core.getCluster() != null
                            && core.getCluster().isActive()
                            && core.getCluster().getGrid().getCraftingService().getCpus().contains(core.getCluster()),
                    "A real powered Omni CPU must be registered before opening its confirmation menu");
        }).thenExecute(() -> {
            try {
                exerciseMenu(helper, (OmniComputationCoreBlockEntity) level.getBlockEntity(pos));
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Transformed menu field or original AE2 selector changed", failure);
            }
        });
    }

    private static void exerciseMenu(GameTestHelper helper, OmniComputationCoreBlockEntity core) throws ReflectiveOperationException {
        var cpu = core.getCluster();
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EcoMenuVerifier"));
        player.getInventory().setItem(0, new ItemStack(Items.PAPER));
        var host = new MenuHost(player, core.getActionableNode());
        var menu = new CraftConfirmMenu(91, player.getInventory(), host);
        EcoOmniPlanMenuAccess.of(menu).requireReady();
        var mode = (ECOCraftConfirmMenuMode) menu;
        menu.broadcastChanges();
        menu.cycleSelectedCPU(true);
        menu.broadcastChanges();
        helper.assertTrue(field("selectedCpu").get(menu) == cpu && mode.neoecoae$bigOrderCpuAvailable(),
                "Real CPU cycling and broadcast must advertise the selected Omni CPU through ECO's synchronized API");

        menu.setAutoStart(true);
        mode.neoecoae$startBigOrder(true);
        helper.assertTrue(!menu.isAutoStart() && !menu.submitError.result().successful() && !cpu.isBusy()
                        && mode.neoecoae$getPlanningDiagnostic().equals("Omni: NO_PLAN") && !host.returned,
                "Missing-plan rejection must update AE2's public error and ECO's diagnostic without a private ECO call");

        ICraftingPlan preview = new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 123), 0,
                true, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        var confirmed = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, (CraftingPlan) preview,
                new ECOPlanTrace(), List.of(), List.of(), List.of(), 0);
        ((ECOCraftingPlanDiagnostics) preview).neoecoae$setPlanningResult(confirmed);
        field("result").set(menu, preview);
        field("neoecoae$confirmedPlanningResult").set(menu, confirmed);
        menu.setAutoStart(true);
        mode.neoecoae$startBigOrder(true);
        helper.assertTrue(!menu.isAutoStart() && !cpu.isBusy()
                        && mode.neoecoae$getPlanningDiagnostic().equals("Omni: NO_PLANNER_OPTIONS"),
                "Omni must read the actual ECO original-options field and reject missing options");

        field("neoecoae$originalOptions").set(menu, new ECOPlannerOptions(true, true, Set.of()));
        field("neoecoae$confirmedPlanningResult").set(menu, new ECOPlanningResult(PlanningStatus.MISSING_ITEMS,
                (CraftingPlan) preview, new ECOPlanTrace(), List.of(), List.of(), List.of(), 0));
        mode.neoecoae$startBigOrder(true);
        helper.assertTrue(!cpu.isBusy() && mode.neoecoae$getPlanningDiagnostic().equals("Omni: CONFIRMED_RESULT_CHANGED")
                        && field("result").get(menu) == preview,
                "Public ownership resolution must reject a replaced confirmation while preserving the preview");

        field("neoecoae$confirmedPlanningResult").set(menu, confirmed);
        var matches = CraftConfirmMenu.class.getDeclaredMethod("cpuMatches", ICraftingCPU.class);
        matches.setAccessible(true);
        helper.assertTrue(Boolean.TRUE.equals(matches.invoke(menu, cpu)), "The actual CPU filter must admit Omni for the confirmed ECO result");
        menu.clearError();
        menu.setAutoStart(true);
        mode.neoecoae$startBigOrder(true);
        var bridge = (EcoOmniParentOrderCpu) cpu.craftingLogic;
        helper.assertTrue(cpu.isBusy() && cpu.craftingLogic.hasJob() && bridge.ecoOmni$parentProgress() != null
                        && bridge.ecoOmni$parentProgress().requested().equals(BigInteger.valueOf(123))
                        && cpu.craftingLogic.getInventory().list.isEmpty() && host.returned && !menu.isAutoStart()
                        && field("result").get(menu) == null && field("neoecoae$confirmedPlanningResult").get(menu) == null
                        && mode.neoecoae$getPlanningDiagnostic().isEmpty(),
                "Public menu submission must create the real parent, consume no preview stock and clear the actual confirmed result");
        menu.broadcastChanges();
        helper.assertTrue(!mode.neoecoae$bigOrderCpuAvailable(), "A busy Omni CPU must clear the previous availability flag");
        cpu.cancelJob();
        System.out.println("ECO_OMNI_MENU_PASS transformed_menu_api");
        System.out.println("ECO_OMNI_MENU_PASS public_menu_reject_ownership_parent_submit");
        helper.succeed();
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Class<?> expected = switch (name) {
            case "neoecoae$confirmedPlanningResult" -> ECOPlanningResult.class;
            case "neoecoae$originalOptions" -> ECOPlannerOptions.class;
            case "neoecoae$bigOrderCpu" -> boolean.class;
            case "neoecoae$planningDiagnostic" -> String.class;
            default -> null;
        };
        if (expected != null) return EcoOmniPlanMenuAccess.resolveField(CraftConfirmMenu.class, name, expected);
        var field = CraftConfirmMenu.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class MenuHost extends ItemMenuHost<Item> implements ISubMenuHost, IActionHost {
        private final IGridNode node;
        boolean returned;
        MenuHost(Player player, IGridNode node) {
            super(Items.PAPER, player, MenuLocators.forInventorySlot(0));
            this.node = node;
        }
        @Override public IGridNode getActionableNode() { return node; }
        @Override public void returnToMainMenu(Player player, ISubMenu menu) { returned = true; }
        @Override public ItemStack getMainMenuIcon() { return new ItemStack(Items.PAPER); }
    }
}
