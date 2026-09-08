package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Texture-free console surfaces based on AE2 19's cool-grey GUI materials. */
public final class OmniUiTheme {
    public static final int CANVAS = 0xFFCBCCD4;
    public static final int PANEL = 0xFFCBCCD4;
    public static final int PANEL_INSET = 0xFFADB0C4;
    public static final int CONTROL = 0xFF9A9FB4;
    public static final int PRIMARY_TEXT = 0xFF302F44;
    public static final int MUTED_TEXT = 0xFF34354B;
    public static final int ACCENT = 0xFF2F4267;
    public static final int SOFT_ACCENT = 0xFF9CD3FF;
    public static final int CYAN = 0xFF0E495B;
    public static final int SLOT = 0xFFADB0C4;
    public static final int TRACK = 0xFF9A9FB4;
    public static final int HIGHLIGHT = 0xFFF2F2F2;
    public static final int SHADOW = 0xFF413F54;
    public static final int BORDER = 0xFF878FA5;
    public static final int BUTTON_SHADE = 0xFF696D88;
    public static final int BUTTON_HOVER_SHADE = 0xFF708CBA;
    public static final int BUTTON_HIGHLIGHT = 0xFFDAFFFF;
    public static final int FIELD_BACKGROUND = 0xFF4D4D67;
    public static final int FIELD_TEXT = 0xFFF2F2F2;
    public static final int FIELD_HINT = 0xFFDEDFE3;
    public static final int QUANTITY_BACKGROUND = 0xFF343347;
    public static final int SUCCESS = 0xFF164C30;
    public static final int WARNING = 0xFF5E3B0C;
    public static final int ERROR = 0xFF792034;
    public static final int DISABLED_TEXT = 0xFF413F54;

    private OmniUiTheme() { }

    static OmniButton button(int x, int y, int width, int height, Component label, Button.OnPress action) {
        return new OmniButton(x, y, width, height, label, action);
    }

    static EditBox textField(ScreenStyle style, Font font, int x, int y, int width, int height,
            Component hint, Component tooltip) {
        return tallTextField(style, font, x, y, width, height, hint, tooltip);
    }

    static EditBox tallTextField(ScreenStyle style, Font font, int x, int y, int width, int height,
            Component hint, Component tooltip) {
        var field = new ConsoleTextField(font, x, y, width, height);
        field.setHint(hint.copy().withStyle(textStyle -> textStyle.withColor(FIELD_HINT & 0xFFFFFF)));
        if (tooltip != null) field.setTooltip(Tooltip.create(tooltip));
        return field;
    }

    public static void console(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SHADOW);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, HIGHLIGHT);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 3, CANVAS);
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, BORDER);
    }

    public static void panel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL);
        outline(graphics, left, top, right, bottom, BUTTON_SHADE);
    }

    public static void area(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL);
        outline(graphics, left, top, right, bottom, HIGHLIGHT);
    }

    public static void insetPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL_INSET);
        outline(graphics, left, top, right, bottom, BUTTON_SHADE);
    }

    public static void insetArea(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL_INSET);
        outline(graphics, left, top, right, bottom, HIGHLIGHT);
    }

    public static void slot(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + 18, top + 18, HIGHLIGHT);
        graphics.fill(left + 1, top + 1, left + 17, top + 17, SLOT);
    }

    public static void outputSlot(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + 18, top + 18, ACCENT);
        graphics.fill(left + 1, top + 1, left + 17, top + 17, SOFT_ACCENT);
    }

    public static void slotGrid(GuiGraphics graphics, int left, int top, int columns, int rows) {
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            slot(graphics, left + column * 18, top + row * 18);
        }
        slotGridOutline(graphics, left, top, columns, rows);
    }

    public static void slotGridOutline(GuiGraphics graphics, int left, int top, int columns, int rows) {
        outline(graphics, left, top, left + columns * 18, top + rows * 18, HIGHLIGHT);
    }

    private static void outline(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    public static void progress(GuiGraphics graphics, int left, int top, int width, int height,
            float progress, int color) {
        insetPanel(graphics, left, top, left + width, top + height);
        int filled = Math.round(Math.max(0, width - 2) * Mth.clamp(progress, 0, 1));
        if (filled > 0 && height > 2) graphics.fill(left + 1, top + 1, left + 1 + filled, top + height - 1, color);
    }

    public static void buttonFace(GuiGraphics graphics, int left, int top, int right, int bottom, boolean hovered) {
        buttonFace(graphics, left, top, right, bottom, hovered, true);
    }

    public static void buttonFace(GuiGraphics graphics, int left, int top, int right, int bottom,
            boolean hovered, boolean active) {
        buttonSurface(graphics, left, top, right, bottom, hovered, active, false, false);
    }

    public static void buttonSurface(GuiGraphics graphics, int left, int top, int right, int bottom,
            boolean hovered, boolean active, boolean selected, boolean danger) {
        boolean highlighted = selected || hovered && active;
        int fill = !active && !selected ? BUTTON_SHADE : danger ? 0xFFE6BCC5 : highlighted ? SOFT_ACCENT : CONTROL;
        int edge = highlighted ? BUTTON_HIGHLIGHT : SLOT;
        int shade = highlighted ? BUTTON_HOVER_SHADE : BUTTON_SHADE;
        graphics.fill(left, top, right, bottom, danger ? ERROR : SHADOW);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, shade);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 3, edge);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 4, fill);
    }

    public static int buttonText(boolean active, boolean selected, boolean danger) {
        return !active && !selected ? DISABLED_TEXT : danger ? ERROR : PRIMARY_TEXT;
    }

    public static void settingsIcon(GuiGraphics graphics, int x, int y, int color) {
        // Pixel gear, matching the configuration control used on the 1.21.1 branch.
        graphics.fill(x + 2, y, x + 6, y + 2, color);
        graphics.fill(x + 2, y + 6, x + 6, y + 8, color);
        graphics.fill(x, y + 2, x + 2, y + 6, color);
        graphics.fill(x + 6, y + 2, x + 8, y + 6, color);
        graphics.fill(x + 1, y + 1, x + 3, y + 3, color);
        graphics.fill(x + 5, y + 1, x + 7, y + 3, color);
        graphics.fill(x + 1, y + 5, x + 3, y + 7, color);
        graphics.fill(x + 5, y + 5, x + 7, y + 7, color);
    }

    public static void computationCore(GuiGraphics graphics, int x, int y, int radius, boolean formed, boolean online) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(45));
            // Three quads replace the reference's per-scanline diamonds.
            for (int layer = 0; layer < 3; layer++) {
                int half = Math.round((radius - layer * 7) / 1.41421356F);
                int color = layer == 0 ? BORDER : layer == 1 ? ACCENT : formed && online ? CYAN : BUTTON_SHADE;
                graphics.fill(-half, -half, half, half, color);
            }
        } finally {
            pose.popPose();
        }
        graphics.fill(x - 3, y - 3, x + 4, y + 4, formed ? HIGHLIGHT : MUTED_TEXT);
    }

    public static void recipePanel(GuiGraphics graphics, int left, int top, int width, int height) {
        console(graphics, left, top, width, height);
    }

    public static void label(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        float scale = Math.min(1, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** Vanilla editing/IME behavior on AE2-style dark fields, with a cyan focus indicator. */
    private static final class ConsoleTextField extends EditBox {
        private final int visualX, visualY, visualWidth, visualHeight;
        private final ConsoleTextRenderer.FieldGraphics textGraphics = new ConsoleTextRenderer.FieldGraphics();

        ConsoleTextField(Font font, int x, int y, int width, int height) {
            super(font, x + 3, y + Math.max(1, (height - 8) / 2),
                    Math.max(1, width - 6 - font.width("_")), font.lineHeight, Component.empty());
            visualX = x; visualY = y; visualWidth = width; visualHeight = height;
            setBordered(false);
            setTextColor(FIELD_TEXT);
            setTextColorUneditable(FIELD_HINT);
        }

        @Override public boolean isMouseOver(double x, double y) {
            return x >= visualX && x < visualX + visualWidth && y >= visualY && y < visualY + visualHeight;
        }

        @Override public boolean mouseClicked(double x, double y, int button) {
            if (isMouseOver(x, y)) {
                x = Mth.clamp(x, getX(), getX() + getWidth() - 1);
                y = Mth.clamp(y, getY(), getY() + getHeight() - 1);
            }
            return super.mouseClicked(x, y, button);
        }

        @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(visualX, visualY, visualX + visualWidth, visualY + visualHeight,
                    isFocused() ? SOFT_ACCENT : HIGHLIGHT);
            graphics.fill(visualX + 1, visualY + 1, visualX + visualWidth - 1, visualY + visualHeight - 1, FIELD_BACKGROUND);
            if (isFocused()) graphics.fill(visualX + 2, visualY + visualHeight - 2,
                    visualX + visualWidth - 2, visualY + visualHeight - 1, SOFT_ACCENT);
            textGraphics.bind(graphics);
            try {
                super.renderWidget(textGraphics, mouseX, mouseY, partialTick);
            } finally {
                textGraphics.clear();
            }
        }
    }
}
