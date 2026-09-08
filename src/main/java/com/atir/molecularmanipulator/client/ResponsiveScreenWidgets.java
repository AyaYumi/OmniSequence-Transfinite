package com.atir.molecularmanipulator.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;

/** Keeps injected screen-space controls out of the machine's local transform. */
final class ResponsiveScreenWidgets {
    private final Set<Object> owned = Collections.newSetFromMap(new IdentityHashMap<>());

    void clear() { owned.clear(); }
    void own(Object widget) { owned.add(widget); }
    boolean owns(Object widget) { return owned.contains(widget); }

    void render(GuiGraphics graphics, List<Renderable> renderables,
            int mouseX, int mouseY, float partialTick, Runnable renderMachine) {
        var original = List.copyOf(renderables);
        var external = original.stream().filter(renderer -> !owns(renderer)).toList();
        renderables.removeIf(renderer -> !owns(renderer));
        try {
            renderMachine.run();
        } finally {
            renderables.clear();
            renderables.addAll(original);
        }
        for (var renderer : external) renderer.render(graphics, mouseX, mouseY, partialTick);
    }
}
