package com.atir.molecularmanipulator.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** A centered pixel icon, independent of font glyph metrics and text replacements. */
final class ResearchBookmarkButton extends OmniButton {
    private static final String[] STAR = {
            "    ##    ", "    ##    ", "   ####   ", "##########", " ######## ",
            "  ######  ", "  ######  ", " ###  ### ", " ##    ## ", "##      ##"};

    ResearchBookmarkButton(int x, int y, OnPress onPress) {
        super(x, y, 20, 16, Component.translatable("gui.molecularmanipulator.research.bookmark_hint"), onPress);
    }

    @Override protected void renderButtonText(GuiGraphics graphics, Font font, int padding, int color, int yOffset) {
        int left = getX() + (width - 10) / 2, top = getY() + (height - 10) / 2;
        for (int y = 0; y < STAR.length; y++) for (int x = 0; x < STAR[y].length(); x++) {
            if (STAR[y].charAt(x) == '#') graphics.fill(left + x, top + y, left + x + 1, top + y + 1, color);
        }
    }
}
