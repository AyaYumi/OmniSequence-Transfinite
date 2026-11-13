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
    private List<Renderable> original = List.of(), external = List.of();
    private boolean dirty = true;

    void clear() { owned.clear(); original = external = List.of(); dirty = true; }
    void own(Object widget) { if (owned.add(widget)) dirty = true; }
    boolean owns(Object widget) { return owned.contains(widget); }

    void render(GuiGraphics graphics, List<Renderable> renderables,
            int mouseX, int mouseY, float partialTick, Runnable renderMachine) {
        boolean changed = dirty || original.size() != renderables.size();
        if (!changed) for (int i = 0; i < original.size(); i++) {
            if (original.get(i) != renderables.get(i)) { changed = true; break; }
        }
        if (changed) {
            original = List.copyOf(renderables);
            external = original.stream().filter(renderer -> !owns(renderer)).toList();
            dirty = false;
        }
        // Preserve the original finally restoration, including widgets added/removed during render.
        var before = original;
        var overlays = external;
        renderables.removeIf(renderer -> !owns(renderer));
        try {
            renderMachine.run();
        } finally {
            renderables.clear();
            renderables.addAll(before);
        }
        for (var renderer : overlays) renderer.render(graphics, mouseX, mouseY, partialTick);
    }
}
