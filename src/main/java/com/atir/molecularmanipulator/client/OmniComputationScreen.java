package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.menu.OmniComputationMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class OmniComputationScreen extends ResponsiveContainerScreen<OmniComputationMenu> {
    private static final int PURPLE = AeUiTheme.ACCENT;
    private static final int CYAN = AeUiTheme.CYAN;
    private OmniComputationLdUi modularView;

    public OmniComputationScreen(OmniComputationMenu menu, Inventory playerInventory, Component title,
            ScreenStyle style) {
        super(menu, playerInventory, title, style);
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
    public void containerTick() {
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
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY,
            float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        AeUiTheme.area(graphics, x + 10, y + 28, x + 154, y + 190);
        AeUiTheme.area(graphics, x + 162, y + 28, x + 322, y + 190);
        AeUiTheme.insetArea(graphics, x + 10, y + 194, x + 322, y + 226);
        AeUiTheme.area(graphics, x + 75, y + 260, x + 257, y + 360);
        AeUiTheme.slot(graphics, x + 292, y + 200);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                AeUiTheme.slot(graphics, x + 84 + column * 18, y + 281 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            AeUiTheme.slot(graphics, x + 84 + column * 18, y + 339);
        }

        int centerX = x + 82;
        int centerY = y + 82;
        int pulse = (int) ((minecraft.level == null ? 0 : minecraft.level.getGameTime()) % 20);
        int radius = 29 + Math.min(pulse, 20 - pulse) / 5;
        diamond(graphics, centerX, centerY, radius, 0xFF8F94A8);
        diamond(graphics, centerX, centerY, radius - 7, PURPLE);
        diamond(graphics, centerX, centerY, radius - 14,
                menu.formed && menu.networkOnline ? CYAN : AeUiTheme.SHADOW);
        graphics.fill(centerX - 3, centerY - 3, centerX + 4, centerY + 4,
                menu.formed ? AeUiTheme.HIGHLIGHT : AeUiTheme.MUTED_TEXT);

    }

    private static void diamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int half = radius - Math.abs(row);
            graphics.fill(centerX - half, centerY + row, centerX + half + 1, centerY + row + 1, color);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        // Labels and action controls are rendered by the transparent LDLib2 overlay.
    }
}
