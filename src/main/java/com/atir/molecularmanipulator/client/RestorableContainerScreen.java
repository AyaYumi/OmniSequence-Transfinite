package com.atir.molecularmanipulator.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.AEBaseMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Keeps this screen's controls attached when a recipe viewer returns the same screen instance. */
abstract class RestorableContainerScreen<T extends AEBaseMenu> extends AEBaseScreen<T> {
    private final List<Registration<?>> screenWidgets = new ArrayList<>();
    protected final ResponsiveScreenWidgets screenContent = new ResponsiveScreenWidgets();
    private boolean addingNativeWidgets;
    private boolean detached;
    private boolean recoveryAttempted;

    protected RestorableContainerScreen(T menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        screenWidgets.clear();
        screenContent.clear();
        detached = false;
        recoveryAttempted = false;
        addingNativeWidgets = true;
        try {
            super.init();
        } finally {
            addingNativeWidgets = false;
        }
    }

    @Override
    protected <W extends GuiEventListener & Renderable & NarratableEntry> W addRenderableWidget(W widget) {
        if (addingNativeWidgets) screenContent.own(widget);
        return super.addRenderableWidget(widget);
    }

    protected final <W extends GuiEventListener & Renderable & NarratableEntry> W addScreenWidget(W widget) {
        return addScreenWidget(widget, widget);
    }

    /** Allows LDLib to render with raw mouse coordinates while receiving transformed container input. */
    protected final <W extends GuiEventListener & NarratableEntry> W addScreenWidget(W widget, Renderable renderer) {
        screenWidgets.add(new Registration<>(widget, renderer));
        screenContent.own(widget);
        screenContent.own(renderer);
        addWidget(widget);
        addRenderableOnly(renderer);
        return widget;
    }

    @Override
    public void removed() {
        detached = true;
        recoveryAttempted = false;
        super.removed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        restoreScreenWidgets();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        restoreScreenWidgets();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        restoreScreenWidgets();
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected final void restoreScreenWidgets() {
        if (minecraft == null || minecraft.screen != this) return;
        if (detached) {
            // A removed LDLib view has been disposed. It must go through init again,
            // even when a viewer resumes the screen without vanilla's usual rebuild.
            if (!recoveryAttempted) {
                rebuildWidgets();
                recoveryAttempted = true; // Respect a cancelled Init event; never rebuild every frame.
            }
            return;
        }
        for (var registration : screenWidgets) {
            // Restore only controls owned by this screen. Keep other mods' controls
            // and existing drafts; do not append duplicates or rebuild the whole UI.
            if (!children().contains(registration.listener())) addWidget(registration.listener());
            if (!renderables.contains(registration.renderer())) addRenderableOnly(registration.renderer());
        }
    }

    private record Registration<W extends GuiEventListener & NarratableEntry>(W listener, Renderable renderer) {}
}
