package com.atir.molecularmanipulator.verification;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import appeng.menu.slot.AppEngSlot;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import com.atir.molecularmanipulator.client.MatterFabricationPatternAssemblyScreen;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Uses the real screen and network action/slot packets, without reopening the menu. */
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class MatterAssemblyMenuClientChecks {
    private static final BlockPos POSITION = new BlockPos(1, 150, 1);
    private static final int[] VIEWS = {1, 0, 2, 0, 3, 0, 1, 3, 2, 0, 3, 0};
    private static int stage, ticks, viewIndex, viewTicks, containerId;
    private static CompletableFuture<?> operation;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("omnisequence.fabrication_menu_check")) return;
        var mc = Minecraft.getInstance();
        if (++ticks > 2400) throw new AssertionError("Fabrication menu client check timed out at stage " + stage);
        if (stage == 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardingAccessibilityFinished(); mc.setScreen(new TitleScreen());
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            stage = 1;
            mc.createWorldOpenFlows().createFreshLevel("assembly-menu-" + System.currentTimeMillis(),
                    new LevelSettings("Assembly Menu Check", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(91487, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), null);
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (stage == 1) {
            mc.setScreen(null);
            var id = mc.player.getUUID();
            operation = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var level = player.serverLevel();
                level.setChunkForced(0, 0, true); level.getChunkAt(POSITION);
                level.setBlock(POSITION, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
                var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(POSITION);
                var encoded = PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)),
                        List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 1)));
                for (int i = 0; i < 36; i++) {
                    assembly.getLogic().getPatternInv().setItemDirect(i, encoded.copy());
                    assembly.getCatalystInventory().setItemDirect(i, new ItemStack(Items.IRON_INGOT, i + 1));
                }
                player.connection.teleport(1, 150, 3, 0, 0);
            });
            stage = 2; return;
        }
        if (operation != null) {
            if (!operation.isDone()) return;
            operation.join(); operation = null;
        }
        if (stage == 2) {
            var id = mc.player.getUUID();
            operation = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var assembly = (MatterFabricationPatternAssemblyBlockEntity) player.serverLevel().getBlockEntity(POSITION);
                assembly.openAssemblyMenu(player, MenuLocators.forBlockEntity(assembly));
            });
            stage = 3; return;
        }
        if (!(mc.screen instanceof MatterFabricationPatternAssemblyScreen)
                || !(mc.player.containerMenu instanceof MatterFabricationPatternAssemblyMenu menu)) return;
        if (stage == 3) {
            if (++viewTicks < 12) return;
            checkSlots(menu);
            containerId = menu.containerId;
            viewTicks = 0;
            menu.requestView(VIEWS[viewIndex]);
            stage = 4; return;
        }
        if (stage == 4) {
            if (menu.view != VIEWS[viewIndex]) { viewTicks = 0; return; }
            if (++viewTicks < 12) return;
            check(menu.containerId == containerId, "All page switches must use the original open menu");
            checkSlots(menu);
            LogUtils.getLogger().info("MATTER_ASSEMBLY_MENU_CLIENT_PAGE_PASS page={} iteration={} occupied={}", menu.view, viewIndex, menu.occupied);
            if (viewIndex == 5) {
                var output = mc.gameDirectory.toPath().resolve("screenshots");
                java.nio.file.Files.createDirectories(output);
                try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { shot.writeToFile(output.resolve("patterns-after-switch.png")); }
            }
            if (++viewIndex < VIEWS.length) { viewTicks = 0; menu.requestView(VIEWS[viewIndex]); return; }
            LogUtils.getLogger().info("MATTER_ASSEMBLY_MENU_CLIENT_PASS: 36 patterns and 36 catalysts, 12 real network page changes, one open screen");
            stage = 5;
            mc.stop();
        }
    }

    private static void checkSlots(MatterFabricationPatternAssemblyMenu menu) {
        check(menu.occupied == 36, "Server must still own every pattern");
        for (var semantic : List.of(SlotSemantics.ENCODED_PATTERN, SlotSemantics.MACHINE_INPUT)) {
            var slots = menu.getSlots(semantic);
            check(slots.size() == 36, "Each inventory must have 36 slots");
            for (int i = 0; i < slots.size(); i++) {
                var slot = (AppEngSlot) slots.get(i);
                check(!slot.getItem().isEmpty(), "Slot sync must retain content on page " + menu.view + ": " + semantic + "/" + i);
                check(slot.isActive() == (semantic == SlotSemantics.ENCODED_PATTERN ? menu.view == 0 : menu.view == 3),
                        "Only the current page's slots may render or interact");
                if (semantic == SlotSemantics.MACHINE_INPUT) check(slot.getItem().is(Items.IRON_INGOT) && slot.getItem().getCount() == i + 1,
                        "Catalyst sync must preserve the exact slot count");
            }
        }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
