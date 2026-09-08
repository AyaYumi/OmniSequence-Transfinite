package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.OmniUiTheme;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.gui.GuiGraphics;

/** Shared slot geometry keeps JEI recipes aligned with the machine consoles. */
final class ConsoleSlotBackground implements IDrawable {
    static final IDrawable INPUT = new ConsoleSlotBackground(false);
    static final IDrawable OUTPUT = new ConsoleSlotBackground(true);
    private final boolean output;

    private ConsoleSlotBackground(boolean output) { this.output = output; }
    @Override public int getWidth() { return 18; }
    @Override public int getHeight() { return 18; }
    @Override public void draw(GuiGraphics graphics, int x, int y) {
        if (output) OmniUiTheme.outputSlot(graphics, x, y);
        else OmniUiTheme.slot(graphics, x, y);
    }
}
