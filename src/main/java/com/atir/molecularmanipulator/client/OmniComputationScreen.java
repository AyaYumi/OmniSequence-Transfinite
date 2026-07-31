package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OmniComputationScreen extends ResponsiveContainerScreen<OmniComputationMenu> {
    private static final int PURPLE = 0xFFB56CFF;
    private static final int CYAN = 0xFF69DBFF;
    private OmniComputationLdUi modularView;

    public OmniComputationScreen(OmniComputationMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 332;
        imageHeight = 364;
    }

    @Override
    protected void init() {
        if (modularView != null) {
            modularView.close();
        }
        super.init();
        modularView = new OmniComputationLdUi(menu);
        modularView.attach(this);
        addResponsiveModularWidget(modularView.widget());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (modularView != null) {
            modularView.tick();
        }
    }

    @Override
    public void removed() {
        if (modularView != null) {
            modularView.close();
            modularView = null;
        }
        super.removed();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF171126, 0xFF080D17);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, PURPLE);
        panel(graphics, x + 10, y + 28, x + 154, y + 190, 0xFF392650);
        panel(graphics, x + 162, y + 28, x + 322, y + 190, 0xFF234352);
        panel(graphics, x + 10, y + 194, x + 322, y + 226, 0xFF69408F);
        panel(graphics, x + 75, y + 260, x + 257, y + 360, 0xFF29334A);
        slotFrame(graphics, x + 292, y + 200, 0xFF9B67CC);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slotFrame(graphics, x + 84 + column * 18, y + 281 + row * 18, 0xFF526079);
            }
        }
        for (int column = 0; column < 9; column++) {
            slotFrame(graphics, x + 84 + column * 18, y + 339, 0xFF526079);
        }

        int centerX = x + 82;
        int centerY = y + 82;
        int pulse = (int) ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) % 20);
        int radius = 29 + Math.min(pulse, 20 - pulse) / 5;
        diamond(graphics, centerX, centerY, radius, 0xFF563378);
        diamond(graphics, centerX, centerY, radius - 7, 0xFF9252D0);
        diamond(graphics, centerX, centerY, radius - 14,
                menu.formed && menu.networkOnline ? CYAN : 0xFF544866);
        graphics.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4,
                menu.formed ? 0xFFE9D6FF : 0xFF6A5E78);

    }

    private static void panel(GuiGraphics graphics, int left, int top, int right, int bottom, int border) {
        graphics.fill(left, top, right, bottom, 0xD9141928);
        graphics.fill(left, top, right, top + 1, border);
        graphics.fill(left, bottom - 1, right, bottom, border);
        graphics.fill(left, top, left + 1, bottom, border);
        graphics.fill(right - 1, top, right, bottom, border);
    }

    private static void slotFrame(GuiGraphics graphics, int left, int top, int border) {
        graphics.fill(left, top, left + 18, top + 18, border);
        graphics.fill(left + 1, top + 1, left + 17, top + 17, 0xFF0C101A);
    }

    private static void diamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = radius - Math.abs(row);
            graphics.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels and action controls are rendered by the transparent LDLib2 overlay.
    }
}
