package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.AE2Button;
import appeng.client.gui.widgets.AETextField;
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/** Shared visual language for screens that live beside AE2's native terminals. */
public final class AeUiTheme {
    private static final ResourceLocation AE_BUTTON = ResourceLocation.fromNamespaceAndPath("ae2", "button");
    private static final ResourceLocation AE_BUTTON_HIGHLIGHTED =
            ResourceLocation.fromNamespaceAndPath("ae2", "button_highlighted");
    private static final Blitter AE_SLOT = Blitter.texture("guis/terminal.png", 256, 256)
            .src(7, 17, 18, 18);
    public static final int PRIMARY_TEXT = 0xFF413F54;
    public static final int MUTED_TEXT = 0xFF878FA5;
    public static final int ACCENT = 0xFF517497;
    public static final int ACCENT_HOVER = 0xFFACE9FF;
    public static final int PANEL = 0xFFCBCCD4;
    public static final int PANEL_INSET = 0xFFADB0C4;
    public static final int SLOT = 0xFFADB0C4;
    public static final int TRACK = 0xFF9A9FB4;
    public static final int HIGHLIGHT = 0xFFF2F2F2;
    public static final int SHADOW = 0xFF696D88;
    public static final int SUCCESS = 0xFF2F7D4F;
    public static final int WARNING = 0xFFA65F14;
    public static final int ERROR = 0xFFCE2401;
    public static final int CYAN = 0xFF367D9D;

    private AeUiTheme() {
    }

    static AE2Button button(int x, int y, int width, int height, Component label,
            Button.OnPress onPress) {
        return new AE2Button(x, y, width, height, label, onPress);
    }

    static AETextField textField(ScreenStyle style, Font font, int x, int y,
            int width, int height, Component placeholder, Component tooltip) {
        var field = new AETextField(style, font, x, y, width, height);
        field.setBordered(false);
        field.setPlaceholder(placeholder);
        if (tooltip != null) {
            field.setTooltipMessage(List.of(tooltip));
        }
        return field;
    }

    static EditBox tallTextField(ScreenStyle style, Font font, int x, int y,
            int width, int height, Component placeholder, Component tooltip) {
        var field = new TallAeTextField(font, x, y, width, height,
                style.getColor(appeng.client.gui.style.PaletteColor.TEXTFIELD_TEXT).toARGB(),
                style.getColor(appeng.client.gui.style.PaletteColor.TEXTFIELD_PLACEHOLDER).toARGB());
        field.setHint(placeholder);
        if (tooltip != null) {
            field.setTooltip(Tooltip.create(tooltip));
        }
        return field;
    }

    static void styleLdButton(com.lowdragmc.lowdraglib2.gui.ui.elements.Button button) {
        button.buttonStyle(style -> style
                .baseTexture(VanillaSpriteTexture.of("ae2:button"))
                .hoverTexture(VanillaSpriteTexture.of("ae2:button_highlighted"))
                .pressedTexture(VanillaSpriteTexture.of("ae2:button_highlighted")));
        button.textStyle(style -> style.textColor(0xFFF2F2F2).textShadow(false));
        button.addEventListener(UIEvents.MOUSE_ENTER, event -> {
            if (button.isActive()) {
                button.textStyle(style -> style.textColor(ACCENT));
            }
        });
        button.addEventListener(UIEvents.MOUSE_LEAVE, event ->
                button.textStyle(style -> style.textColor(button.isActive() ? HIGHLIGHT : PRIMARY_TEXT)));
    }

    public static void panel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL);
        flatOutline(graphics, left, top, right, bottom, SHADOW);
    }

    public static void label(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color) {
        float scale = Math.min(1, width / (float) Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    public static void area(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL);
        flatOutline(graphics, left, top, right, bottom, HIGHLIGHT);
    }

    public static void insetPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL_INSET);
        flatOutline(graphics, left, top, right, bottom, SHADOW);
    }

    public static void insetArea(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, PANEL_INSET);
        flatOutline(graphics, left, top, right, bottom, HIGHLIGHT);
    }

    public static void slot(GuiGraphics graphics, int left, int top) {
        AE_SLOT.copy().dest(left, top).blit(graphics);
    }

    public static void slotGrid(GuiGraphics graphics, int left, int top, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                slot(graphics, left + column * 18, top + row * 18);
            }
        }
        slotGridOutline(graphics, left, top, columns, rows);
    }

    public static void slotGridOutline(GuiGraphics graphics, int left, int top,
            int columns, int rows) {
        flatOutline(graphics, left, top,
                left + columns * 18, top + rows * 18, HIGHLIGHT);
    }

    public static void progress(GuiGraphics graphics, int left, int top, int width, int height,
            float progress, int color) {
        insetPanel(graphics, left, top, left + width, top + height);
        int innerWidth = Math.max(0, width - 2);
        int filled = Math.round(innerWidth * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) {
            graphics.fill(left + 1, top + 1, left + 1 + filled, top + height - 1, color);
        }
    }

    public static void buttonFace(GuiGraphics graphics, int left, int top,
            int right, int bottom, boolean hovered) {
        graphics.blitSprite(hovered ? AE_BUTTON_HIGHLIGHTED : AE_BUTTON,
                left, top, right - left, bottom - top);
    }

    private static void flatOutline(GuiGraphics graphics, int left, int top,
            int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    private static final class TallAeTextField extends EditBox {
        private final int visualLeft;
        private final int visualTop;
        private final int visualWidth;
        private final int visualHeight;
        private final int actualTextColor;
        private final int placeholderTextColor;

        private TallAeTextField(Font font, int x, int y, int width, int height,
                int actualTextColor, int placeholderTextColor) {
            super(font, x + 2, y + (height - 8) / 2,
                    width - 4 - font.width("_"), font.lineHeight,
                    Component.empty());
            visualLeft = x;
            visualTop = y;
            visualWidth = width;
            visualHeight = height;
            this.actualTextColor = actualTextColor;
            this.placeholderTextColor = placeholderTextColor;
            setBordered(false);
            setTextColor(actualTextColor);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= visualLeft && mouseX < visualLeft + visualWidth
                    && mouseY >= visualTop && mouseY < visualTop + visualHeight;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOver(mouseX, mouseY)) {
                mouseX = Mth.clamp(mouseX, getX(), getX() + getWidth() - 1);
                mouseY = Mth.clamp(mouseY, getY(), getY() + getHeight() - 1);
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int sourceY = isFocused() ? 24 : 0;
            drawTextFieldSlice(graphics, sourceY);
            setTextColor(getValue().isEmpty() && !isFocused()
                    ? placeholderTextColor
                    : actualTextColor);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        private void drawTextFieldSlice(GuiGraphics graphics, int sourceY) {
            int middleHeight = Math.max(1, visualHeight - 4);
            drawSlice(graphics, 0, sourceY, 1, 3,
                    visualLeft, visualTop, 1, 3);
            drawSlice(graphics, 1, sourceY, 126, 3,
                    visualLeft + 1, visualTop, visualWidth - 2, 3);
            drawSlice(graphics, 127, sourceY, 1, 3,
                    visualLeft + visualWidth - 1, visualTop, 1, 3);

            drawSlice(graphics, 0, sourceY + 3, 1, 8,
                    visualLeft, visualTop + 3, 1, middleHeight);
            drawSlice(graphics, 1, sourceY + 3, 126, 8,
                    visualLeft + 1, visualTop + 3, visualWidth - 2, middleHeight);
            drawSlice(graphics, 127, sourceY + 3, 1, 8,
                    visualLeft + visualWidth - 1, visualTop + 3, 1, middleHeight);

            int bottom = visualTop + visualHeight - 1;
            drawSlice(graphics, 0, sourceY + 11, 1, 1,
                    visualLeft, bottom, 1, 1);
            drawSlice(graphics, 1, sourceY + 11, 126, 1,
                    visualLeft + 1, bottom, visualWidth - 2, 1);
            drawSlice(graphics, 127, sourceY + 11, 1, 1,
                    visualLeft + visualWidth - 1, bottom, 1, 1);
        }

        private static void drawSlice(GuiGraphics graphics,
                int sourceX, int sourceY, int sourceWidth, int sourceHeight,
                int destX, int destY, int destWidth, int destHeight) {
            Blitter.texture("guis/text_field.png", 128, 128)
                    .src(sourceX, sourceY, sourceWidth, sourceHeight)
                    .dest(destX, destY, destWidth, destHeight)
                    .blit(graphics);
        }
    }
}
