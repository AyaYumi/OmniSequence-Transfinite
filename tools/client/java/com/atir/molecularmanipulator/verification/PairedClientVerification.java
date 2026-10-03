package com.atir.molecularmanipulator.verification;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.menu.SlotSemantics;
import appeng.core.definitions.AEItems;
import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import com.atir.molecularmanipulator.client.SingularityScreen;
import com.atir.molecularmanipulator.menu.SingularityMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.SingularityContent;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Loads an isolated world, real menu packets, native screens, shaders and resources. */
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class PairedClientVerification {
    private static int ticks, step, wait;
    private static boolean started, ready, done;
    private static volatile boolean hubSlotsPassed;
    private static volatile Throwable serverFailure;
    private static BlockPos pendingPos;
    private static Block pendingBlock;
    private static MenuType<?> pendingType;
    private static final String[] NAMES = {"hub-overview", "hub-duplication", "hub-resources",
            "well", "well-research", "sequence", "molecular", "omni"};

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        var mc = Minecraft.getInstance();
        try {
            mc.options.pauseOnLostFocus = false;
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            if (++ticks > 7200) throw new IllegalStateException("Client verification timed out");
            if (ticks == 120) {
                com.mojang.logging.LogUtils.getLogger().info("PAIRED_CLIENT_INITIAL_SCREEN={}", mc.screen);
                Screenshot.grab(mc.gameDirectory, "initial.png", mc.getMainRenderTarget(), message -> {});
            }
            if (!started && mc.screen != null
                    && mc.screen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
                mc.setScreen(new TitleScreen());
            }
            if (!started && mc.screen instanceof TitleScreen) {
                started = true;
                var settings = new net.minecraft.world.level.LevelSettings("Paired client verification",
                        net.minecraft.world.level.GameType.CREATIVE, false,
                        net.minecraft.world.Difficulty.PEACEFUL, true,
                        new net.minecraft.world.level.GameRules(),
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("verification-" + java.util.UUID.randomUUID(), settings,
                        new net.minecraft.world.level.levelgen.WorldOptions(1L, false, false),
                        registries -> registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT)
                                .value().createWorldDimensions());
                return;
            }
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
            if (serverFailure != null) throw new IllegalStateException("Server menu verification failed", serverFailure);
            if (!ready) { ready = true; wait = 120; return; }
            if (pendingPos != null) {
                // Forge's custom opening packet can overtake vanilla block updates.
                // Open only after the fixture host has reached the actual client.
                if (!mc.level.getBlockState(pendingPos).is(pendingBlock)
                        || mc.level.getBlockEntity(pendingPos) == null) return;
                var pos = pendingPos; var type = pendingType; var uuid = mc.player.getUUID();
                pendingPos = null;
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                        if (!MenuOpener.open(type, player, MenuLocators.forBlockEntity(player.serverLevel().getBlockEntity(pos))))
                            throw new IllegalStateException("Could not open " + type);
                        if (player.containerMenu instanceof SingularityMenu hub) validateHubSlots(hub, player);
                    } catch (Throwable failure) { serverFailure = failure; }
                });
                wait = 60;
            }
            if (wait > 0) { wait--; return; }
            if (step % 2 == 0) {
                int page = step / 2;
                if (page == NAMES.length) {
                    if (!hubSlotsPassed) throw new IllegalStateException("Server menu checks did not pass");
                    if (Boolean.getBoolean("omni.craftingPacketVerification") && !ForgeCraftingPacketVerification.tick(mc)) return;
                    if (Boolean.getBoolean("omni.visualVerification") && !Boolean.getBoolean("omni.menusOnlyVerification")) {
                        if (!ForgeVisualVerification.tick(mc)) return;
                    }
                    com.mojang.logging.LogUtils.getLogger().info("PAIRED_CLIENT_ALL_PASS: shaders/resources, 8 real screen pages, menu packets");
                    done = true;
                    if (Boolean.getBoolean("omni.packVerification") && !Boolean.getBoolean("omni.visualVerification")
                            && !Boolean.getBoolean("omni.menusOnlyVerification")) {
                        Class.forName("com.atir.molecularmanipulator.verification.PackFlowVerification")
                                .getField("menusFinished").setBoolean(null, true);
                    } else mc.stop();
                    return;
                }
                switch (page) {
                    case 0 -> open(mc, SingularityContent.CONTROLLER.get(), SingularityMenu.TYPE);
                    case 1 -> invoke(mc.screen, "showDuplication");
                    case 2 -> invoke(mc.screen, "showCollection");
                    case 3 -> open(mc, ModContent.MATTER_FABRICATION_CONTROLLER.get(), ModContent.MATTER_FABRICATION_MENU.get());
                    case 4 -> {
                        Method method = mc.screen.getClass().getDeclaredMethod("setResearchPage", boolean.class);
                        method.setAccessible(true); method.invoke(mc.screen, true);
                    }
                    case 5 -> open(mc, ModContent.MOLECULAR_CENTER_CONTROLLER.get(), ModContent.MOLECULAR_CENTER_MENU.get());
                    case 6 -> open(mc, ModContent.MOLECULAR_MANIPULATOR.get(), ModContent.MOLECULAR_MANIPULATOR_MENU.get());
                    case 7 -> open(mc, ModContent.OMNI_COMPUTATION_CONTROLLER.get(), ModContent.OMNI_COMPUTATION_MENU.get());
                }
                step++; wait = 60;
            } else {
                if (!(mc.screen instanceof ResponsiveContainerScreen<?> screen))
                    throw new IllegalStateException("Expected real controller screen: " + mc.screen
                            + " menu=" + mc.player.containerMenu);
                if (step / 2 == 1) {
                    var menu = (SingularityMenu) mc.player.containerMenu;
                    if (!menu.getSlots(SingularityMenu.DUPLICATION_SAMPLE).get(0).isActive()
                            || !menu.getSlots(SingularityMenu.BLACK_HOLE_INPUT).get(0).isActive()
                            || menu.getSlots(SingularityMenu.QUANTUM_INPUT).get(0).isActive())
                        throw new IllegalStateException("Duplication page slot visibility is wrong");
                }
                if (step / 2 == 4 && Boolean.getBoolean("omni.visualVerification")) ForgeVisualVerification.bookmark(mc, screen);
                Screenshot.grab(mc.gameDirectory, NAMES[step / 2] + ".png", mc.getMainRenderTarget(), message -> {});
                System.out.println("PAIRED_CLIENT_PAGE_PASS=" + NAMES[step / 2] + " " + screen.getClass().getSimpleName());
                step++; wait = 20;
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.out.println("PAIRED_CLIENT_FAILED=" + failure);
            done = true; mc.stop();
        }
    }

    private static void invoke(Object screen, String name) throws Exception {
        Method method = screen.getClass().getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(screen);
    }

    private static void open(Minecraft mc, Block block, MenuType<?> type) {
        var uuid = mc.player.getUUID();
        BlockPos pos = new BlockPos(2, 100, step);
        pendingPos = pos; pendingBlock = block; pendingType = type;
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            var level = player.serverLevel();
            player.teleportTo(0.5, 100, pos.getZ() + 0.5);
            player.setNoGravity(true);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.setBlockAndUpdate(new BlockPos(0, 99, pos.getZ()), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        });
    }

    private static void validateHubSlots(SingularityMenu menu, net.minecraft.server.level.ServerPlayer player) {
        var quantum = menu.getSlots(SingularityMenu.QUANTUM_INPUT).get(0);
        var sample = menu.getSlots(SingularityMenu.DUPLICATION_SAMPLE).get(0);
        var black = menu.getSlots(SingularityMenu.BLACK_HOLE_INPUT).get(0);
        quantum.set(ItemStack.EMPTY); sample.set(ItemStack.EMPTY); black.set(ItemStack.EMPTY);
        if (quantum.mayPlace(new ItemStack(Items.STONE))
                || quantum.mayPlace(new ItemStack(ModContent.BLACK_HOLE.get()))
                || !quantum.mayPlace(AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack()))
            throw new IllegalStateException("Quantum slot item filter is wrong");
        var source = menu.getSlots(SlotSemantics.PLAYER_INVENTORY).get(0);
        source.set(new ItemStack(ModContent.BLACK_HOLE.get(), 64));
        menu.quickMoveStack(player, source.index);
        if (black.getItem().getCount() != 64 || !quantum.getItem().isEmpty())
            throw new IllegalStateException("Shift black holes must enter only the black-hole slot");
        menu.quickMoveStack(player, black.index);
        if (!black.getItem().isEmpty()) throw new IllegalStateException("Black holes cannot be removed");
        menu.setCarried(new ItemStack(ModContent.BLACK_HOLE.get(), 64));
        menu.clicked(black.index, 0, ClickType.PICKUP, player);
        if (black.getItem().getCount() != 64 || !menu.getCarried().isEmpty())
            throw new IllegalStateException("Normal black-hole insertion failed");
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(sample.index, 0, ClickType.PICKUP, player);
        if (!sample.getItem().is(Items.DIAMOND) || !menu.getCarried().isEmpty())
            throw new IllegalStateException("Normal sample insertion failed");
        menu.clicked(sample.index, 0, ClickType.PICKUP, player);
        if (!sample.getItem().isEmpty() || !menu.getCarried().is(Items.DIAMOND))
            throw new IllegalStateException("Normal sample removal failed");
        menu.clicked(sample.index, 0, ClickType.PICKUP, player);
        menu.broadcastChanges();
        hubSlotsPassed = true;
        com.mojang.logging.LogUtils.getLogger().info("PAIRED_CLIENT_SLOTS_PASS: filtered quantum, shift black 64, normal insertion/removal");
    }
}
