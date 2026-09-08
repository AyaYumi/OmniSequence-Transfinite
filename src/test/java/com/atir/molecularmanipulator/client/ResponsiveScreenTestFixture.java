package com.atir.molecularmanipulator.client;

import appeng.client.gui.WidgetContainer;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import sun.misc.Unsafe;

/** Exercises screen coordinate and event code without creating a Minecraft window or a live menu. */
public final class ResponsiveScreenTestFixture {
    public static ProbeScreen create(int width, int height, int imageWidth, int imageHeight) throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var screen = (ProbeScreen) ((Unsafe) field.get(null)).allocateInstance(ProbeScreen.class);
        set(screen, "children", new ArrayList<>());
        set(screen, "renderables", new ArrayList<>());
        set(screen, "narratables", new ArrayList<>());
        set(screen, "screenWidgets", new ArrayList<>());
        set(screen, "screenContent", new ResponsiveScreenWidgets());
        set(screen, "drag_click", new HashSet<>());
        set(screen, "widgets", new WidgetContainer(new ScreenStyle()));
        screen.viewport(width, height, imageWidth, imageHeight);
        return screen;
    }

    public static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    public static final class ProbeScreen extends ResponsiveContainerScreen<AEBaseMenu> {
        private ProbeScreen() { super(null, null, Component.empty(), null); }

        public void viewport(int width, int height, int imageWidth, int imageHeight) {
            this.width = width;
            this.height = height;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            leftPos = (width - imageWidth) / 2;
            topPos = (height - imageHeight) / 2;
        }

        <W extends GuiEventListener & Renderable & NarratableEntry> void own(W widget) { addScreenWidget(widget); }
        <W extends GuiEventListener & Renderable & NarratableEntry> void inject(W widget) { addRenderableWidget(widget); }
    }
}
