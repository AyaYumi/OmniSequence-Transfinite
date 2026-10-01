package com.atir.molecularmanipulator.verification;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.client.Point;
import appeng.client.gui.style.StyleManager;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.Scrollbar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class Ae2CompatibilityProbe {
    public static final List<String> amounts = new ArrayList<>();
    public static final List<Component> names = new ArrayList<>();
    public static boolean capturing;
    private static int ticks;
    private static int frames;
    private static boolean started;
    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        var mc = Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) onboarding.onClose();
        if (ticks % 200 == 0) System.out.println("AE2_COMPAT_SCREEN " + mc.screen);
        if (++ticks > 1200) throw new IllegalStateException("AE2 compatibility client timed out: " + mc.screen);
        if (started || mc.player == null || mc.getSingleplayerServer() == null) return;
        started = true;
        mc.options.guiScale().set(3);
        mc.resizeDisplay();
        var menu = new CraftingStatusMenu(0, mc.player.getInventory(), null);
        menu.cpuList = new CraftingStatusMenu.CraftingCpuList(List.of(
                entry(1, 4 * 1024 * 1024L, 128, Component.literal("Ordinary CPU"), null),
                entry(2, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Component.translatable("gui.molecularmanipulator.omni.cpu_name", 2), null),
                entry(3, Long.MAX_VALUE, Integer.MAX_VALUE,
                        Component.translatable("gui.molecularmanipulator.nexus.cpu_name", 3), null),
                entry(4, Long.MAX_VALUE, Integer.MAX_VALUE, Component.literal("Busy CPU"),
                        new GenericStack(AEItemKey.of(Items.DIAMOND), 123))));
        mc.setScreen(new CpuScreen(menu));
    }

    private static CraftingStatusMenu.CraftingCpuListEntry entry(int id, long storage, int processors,
            Component name, GenericStack job) {
        return new CraftingStatusMenu.CraftingCpuListEntry(id, storage, processors, name,
                CpuSelectionMode.ANY, job, 0.5f, 1_000_000_000L);
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Post event) throws Exception {
        var mc = Minecraft.getInstance();
        if (finished || !(mc.screen instanceof CpuScreen) || ++frames < 20) return;
        finished = true;
        var output = Path.of(System.getProperty("ae2.compat.output"));
        Files.createDirectories(output);
        try (var screenshot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            screenshot.writeToFile(output.resolve("cpu-list.png"));
        }
        String versions = "ae2=" + ModList.get().getModContainerById("ae2").orElseThrow().getModInfo().getVersion()
                + " appliedenhancements=" + ModList.get().getModContainerById("appliedenhancements").orElseThrow().getModInfo().getVersion()
                + " omni=" + ModList.get().getModContainerById("molecularmanipulator").orElseThrow().getModInfo().getVersion();
        String report = "AE2_COMPAT_CLIENT_PASS " + versions + " amounts=" + amounts + " names="
                + names.stream().map(Component::getString).toList();
        Files.writeString(output.resolve("client-result.txt"), report + System.lineSeparator());
        System.out.println(report);
        mc.stop();
    }

    private static final class CpuScreen extends Screen {
        private final CPUSelectionList widget;

        private CpuScreen(CraftingStatusMenu menu) {
            super(Component.literal("Crafting CPUs"));
            widget = new CPUSelectionList(menu, new Scrollbar(Scrollbar.SMALL),
                    StyleManager.loadStyleDoc("/screens/crafting_status.json"));
            widget.setSize(94, 164);
        }

        @Override
        public boolean isPauseScreen() { return false; }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF24282C);
            widget.setPosition(new Point((width - 94) / 2, (height - 164) / 2));
            widget.updateBeforeRender();
            names.clear();
            amounts.clear();
            capturing = true;
            try {
                widget.drawBackgroundLayer(graphics, new Rect2i(0, 0, width, height), new Point(0, 0));
            } finally {
                capturing = false;
            }
            check(amounts.equals(List.of("4M", "128", "9.2E", "9.2E", "9.2E", "9.2E", "123")),
                    "Unexpected CPU display amounts: " + amounts);
            check(names.size() == 4 && names.get(0).getString().equals("Ordinary CPU"), "Ordinary CPU name changed");
            check(names.get(1).getContents() instanceof TranslatableContents omni
                    && omni.getKey().equals("gui.molecularmanipulator.omni.cpu_name_short"), "Omni CPU name not shortened");
            check(names.get(2).getContents() instanceof TranslatableContents nexus
                    && nexus.getKey().equals("gui.molecularmanipulator.nexus.cpu_name_short"), "Nexus CPU name not shortened");
            var bounds = widget.getBounds();
            for (int row = 1; row <= 2; row++) {
                var tooltip = widget.getTooltip(bounds.getX() + 10, bounds.getY() + 20 + row * 23);
                check(tooltip != null && tooltip.getContent().stream()
                        .filter(line -> line.getString().contains("9.2E")).count() == 2,
                        "Infinite CPU tooltip does not contain both sentinels");
            }
            graphics.flush();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
