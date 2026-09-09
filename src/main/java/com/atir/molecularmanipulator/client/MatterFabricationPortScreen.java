package com.atir.molecularmanipulator.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.client.gui.style.ScreenStyle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity;
import com.atir.molecularmanipulator.menu.MatterFabricationPortMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Material cache at left, network return / adjacent-output controls at right. */
public final class MatterFabricationPortScreen extends ResponsiveContainerScreen<MatterFabricationPortMenu> {
    private static final Direction[] SIDES = {Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private final List<OutputSideButton> sideButtons = new ArrayList<>();
    private Button returnButton, autoButton;

    public MatterFabricationPortScreen(MatterFabricationPortMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }
    @Override protected void init() {
        super.init(); sideButtons.clear();
        if (menu.getPort().getPortType().isInput()) {
            returnButton = addScreenWidget(AeUiTheme.button(leftPos + 130, topPos + 74, 98, 20,
                    text("return_ae"), button -> menu.returnInputs()));
            returnButton.setTooltip(Tooltip.create(text("return_hint")));
        } else {
            autoButton = addScreenWidget(AeUiTheme.button(leftPos + 130, topPos + 50, 98, 20,
                    text("auto_off"), button -> menu.toggleAutoOutput()));
            autoButton.setTooltip(Tooltip.create(text("auto_hint")));
            for (int i = 0; i < SIDES.length; i++) {
                var side = SIDES[i];
                var button = addScreenWidget(new OutputSideButton(leftPos + 130 + i % 2 * 52,
                        topPos + 94 + i / 2 * 24, side));
                sideButtons.add(button);
            }
        }
        updateControls();
    }
    @Override public void containerTick() { super.containerTick(); updateControls(); }
    private void updateControls() {
        if (returnButton != null) returnButton.active = menu.networkOnline && menu.totalAmount > 0;
        if (autoButton != null) autoButton.setMessage(text(menu.autoOutput ? "auto_on" : "auto_off"));
        for (int i = 0; i < sideButtons.size(); i++) {
            var side = SIDES[i]; boolean enabled = (menu.outputSides & 1 << side.get3DDataValue()) != 0;
            var label = text("side_short." + side.getName());
            sideButtons.get(i).setMessage(enabled ? Component.literal("[").append(label).append("]") : label);
            sideButtons.get(i).refreshNeighbor(enabled);
        }
    }

    /** Keep AE's native face and height; reserve the left half for the adjacent block icon. */
    private final class OutputSideButton extends ResponsiveAE2Button {
        private final Direction side;
        private ItemStack icon = ItemStack.EMPTY;
        private AEFluidKey fluidIcon;
        private boolean occupied;
        private boolean outputEnabled;

        OutputSideButton(int x, int y, Direction side) {
            super(x, y, 46, 20, Component.empty(), button -> menu.toggleOutputSide(side.get3DDataValue()));
            this.side = side;
        }

        void refreshNeighbor(boolean enabled) {
            outputEnabled = enabled;
            var level = menu.getPort().getLevel();
            var pos = menu.getPort().getBlockPos().relative(side);
            icon = ItemStack.EMPTY; fluidIcon = null; occupied = false;
            Component name = text("neighbor_unloaded");
            if (level != null && level.hasChunkAt(pos)) {
                var state = level.getBlockState(pos);
                occupied = !state.isAir();
                name = occupied ? Component.translatable(state.getBlock().getDescriptionId()) : text("neighbor_empty");
                if (occupied) {
                    icon = new ItemStack(state.getBlock());
                    if (icon.isEmpty() && !state.getFluidState().isEmpty()) {
                        fluidIcon = AEFluidKey.of(state.getFluidState().getType());
                        name = fluidIcon.getDisplayName();
                    }
                }
            }
            setTooltip(Tooltip.create(text("side_hint", text("side." + side.getName()), text(enabled ? "enabled" : "disabled"))
                    .copy().append("\n").append(text("neighbor", name))));
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHovered();
            // AE's original light-blue sprite represents the saved selection. Mint tint is hover feedback only.
            graphics.setColor(hovered ? 0.78F : 1, 1, hovered ? 0.76F : 1, alpha);
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            graphics.blitSprite(SPRITES.get(active, outputEnabled || hovered), getX(), getY(), getWidth(), getHeight());
            graphics.setColor(1, 1, 1, 1);
            int color = hovered ? AeUiTheme.PRIMARY_TEXT : outputEnabled ? AeUiTheme.ACCENT : AeUiTheme.HIGHLIGHT;
            renderButtonText(graphics, net.minecraft.client.Minecraft.getInstance().font, 2,
                    color & 0xFFFFFF | Mth.ceil(alpha * 255) << 24, outputEnabled || hovered ? 0 : 1);
            if (!icon.isEmpty()) graphics.renderItem(icon, getX() + 3, getY() + 1);
            else if (fluidIcon != null) AEKeyRendering.drawInGui(net.minecraft.client.Minecraft.getInstance(), graphics,
                    getX() + 3, getY() + 1, fluidIcon);
            else graphics.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, occupied ? "?" : "–",
                    getX() + 11, getY() + 5, AeUiTheme.MUTED_TEXT);
        }

        @Override protected void renderButtonText(GuiGraphics graphics, Font font, int padding, int color, int yOffset) {
            ResponsiveAE2Button.renderButtonText(graphics, font, getMessage(), getX() + 22, getY(),
                    getX() + getWidth() - 3, getY() + getHeight(), yOffset, color);
        }
    }
    @Override public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        graphics.fill(x + 12, y + 38, x + 236, y + 39, AeUiTheme.SHADOW);
        AeUiTheme.panel(graphics, x + 12, y + 44, x + 112, y + 178);
        AeUiTheme.panel(graphics, x + 122, y + 44, x + 236, y + 178);
        if (menu.getPort().getPortType().isItem()) AeUiTheme.slotGrid(graphics, x + 26, y + 69, 4, 4);
        else for (int tank = 0; tank < 4; tank++) renderTank(graphics, tank, x + tankX(tank), y + tankY(tank));
        AeUiTheme.slotGrid(graphics, x + 43, y + 197, 9, 3);
        AeUiTheme.slotGrid(graphics, x + 43, y + 255, 9, 1);
    }
    @Override public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var displayTitle = title.getString().isBlank()
                ? Component.translatable(menu.getPort().getBlockState().getBlock().getDescriptionId()) : title;
        label(graphics, displayTitle, 14, 10, 220, AeUiTheme.PRIMARY_TEXT);
        graphics.fill(14, 26, 18, 30, menu.networkOnline ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        label(graphics, text(menu.networkOnline ? "network_online" : "network_offline"), 23, 24, 211, AeUiTheme.PRIMARY_TEXT);
        label(graphics, text("cache"), 20, 51, 84, AeUiTheme.PRIMARY_TEXT);
        label(graphics, text("occupied", menu.occupied, menu.getPort().getPortType().isItem() ? 16 : 4), 20, 150, 84, AeUiTheme.MUTED_TEXT);
        label(graphics, text("amount", number(menu.totalAmount), unit()), 20, 163, 84, AeUiTheme.PRIMARY_TEXT);
        if (menu.getPort().getPortType().isInput()) {
            label(graphics, text("recovery"), 130, 51, 98, AeUiTheme.PRIMARY_TEXT);
            int y = 105;
            for (var line : font.split(text("return_description"), 98)) {
                graphics.drawString(font, line, 130, y, AeUiTheme.MUTED_TEXT, false); y += 10;
            }
            if (menu.lastReturned >= 0) {
                label(graphics, text("returned", number(menu.lastReturned), unit()), 130, 149, 98, menu.lastReturned > 0 ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
                label(graphics, text(menu.totalAmount > 0 ? "remainder" : "cache_empty"), 130, 163, 98, AeUiTheme.MUTED_TEXT);
            }
        } else label(graphics, text("directions"), 130, 79, 98, AeUiTheme.PRIMARY_TEXT);
        label(graphics, Component.translatable("container.inventory"), 44, 184, 162, AeUiTheme.PRIMARY_TEXT);
    }
    private void renderTank(GuiGraphics graphics, int index, int x, int y) {
        AeUiTheme.insetPanel(graphics, x, y, x + 40, y + 36);
        var stack = menu.getTankStack(index);
        if (stack != null) AEKeyRendering.drawInGui(net.minecraft.client.Minecraft.getInstance(), graphics, x + 12, y + 3, stack.what());
        else graphics.drawCenteredString(font, "–", x + 20, y + 7, AeUiTheme.MUTED_TEXT);
        var amount = Component.literal(stack == null ? "0" : number(stack.amount()));
        int width = Math.min(36, font.width(amount));
        label(graphics, amount, x + (40 - width) / 2, y + 23, 36, AeUiTheme.PRIMARY_TEXT);
        if (stack != null) {
            int fill = Math.max(2, (int) (36 * stack.amount() / (double) MatterFabricationPortBlockEntity.FLUID_CAPACITY));
            graphics.fill(x + 2, y + 33, x + 2 + fill, y + 35, AeUiTheme.CYAN);
        }
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.getPort().getPortType().isFluid() && (button == 0 || button == 1)) {
            int tank = tankAt(logicalMouseX(mouseX) - leftPos, logicalMouseY(mouseY) - topPos);
            if (tank >= 0) {
                if (button == 1) menu.interactFluid(tank); else menu.requestFluidTransfer(tank);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!menu.getPort().getPortType().isFluid()) return;
        int tank = tankAt(logicalMouseX(mouseX) - leftPos, logicalMouseY(mouseY) - topPos);
        if (tank < 0) return;
        var stack = menu.getTankStack(tank);
        graphics.renderTooltip(font, List.of(stack == null ? text("empty_tank") : stack.what().getDisplayName(),
                Component.literal(String.format(Locale.ROOT, "%,d / %,d mB", stack == null ? 0 : stack.amount(), MatterFabricationPortBlockEntity.FLUID_CAPACITY)),
                text("fluid_click")), java.util.Optional.empty(), mouseX, mouseY);
    }
    private Component unit() { return menu.getPort().getPortType().isItem() ? text("items_unit") : Component.literal("mB"); }
    private void label(GuiGraphics graphics, Component value, int x, int y, int width, int color) { AeUiTheme.label(graphics, font, value, x, y, width, color); }
    private static Component text(String key, Object... args) { return Component.translatable("gui.molecularmanipulator.fabrication.port." + key, args); }
    private static int tankX(int index) { return 20 + index % 2 * 46; }
    private static int tankY(int index) { return 68 + index / 2 * 40; }
    private static int tankAt(double x, double y) {
        for (int i = 0; i < 4; i++) if (x >= tankX(i) && x < tankX(i) + 40 && y >= tankY(i) && y < tankY(i) + 36) return i;
        return -1;
    }
    private static String number(long value) {
        if (value < 1000) return Long.toString(value);
        double unit = value >= 1_000_000_000 ? 1e9 : value >= 1_000_000 ? 1e6 : 1e3;
        return String.format(Locale.ROOT, "%.2f", value / unit).replaceAll("0+$", "").replaceAll("\\.$", "") + (unit == 1e9 ? "G" : unit == 1e6 ? "M" : "k");
    }
}
