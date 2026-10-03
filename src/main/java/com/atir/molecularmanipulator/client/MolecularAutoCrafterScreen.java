package com.atir.molecularmanipulator.client;

import appeng.client.gui.Icon;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Compact AE2 pattern controls, a two-row output buffer, and the player inventory. */
public final class MolecularAutoCrafterScreen extends ResponsiveContainerScreen<MolecularAutoCrafterMenu> {
    private static final int CONTROL_SIZE = 16;
    private static final int CONTROL_ICON_SIZE = 16;
    private final MolecularAutoCrafterIconButton[] configureButtons =
            new MolecularAutoCrafterIconButton[MolecularAutoCrafter.PATTERN_SLOTS];
    private final MolecularAutoCrafterIconButton[] startButtons =
            new MolecularAutoCrafterIconButton[MolecularAutoCrafter.PATTERN_SLOTS];
    private MolecularAutoCrafterIconButton outputModeButton;
    private MolecularAutoCrafterIconButton outputDirectionButton;
    private MolecularAutoCrafterIconButton previousOutputPage;
    private MolecularAutoCrafterIconButton nextOutputPage;
    private DirectionPopup directionPopup;
    private boolean directionPopupOpen;

    public MolecularAutoCrafterScreen(MolecularAutoCrafterMenu menu, Inventory inventory,
            Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int mouseButton, ClickType clickType) {
        int index = menu.getPatternSlots().indexOf(slot);
        if (index >= 0 && mouseButton == 1 && clickType == ClickType.PICKUP) {
            menu.requestConfig(index);
            return;
        }
        super.slotClicked(slot, slotId, mouseButton, clickType);
    }

    @Override
    protected void init() {
        if (outputModeButton == null) {
            outputModeButton = addToLeftToolbar(new MolecularAutoCrafterIconButton(
                    0, 0, Icon.AUTO_EXPORT_ON, Component.empty(), menu::requestOutputModeToggle));
            outputDirectionButton = addToLeftToolbar(new MolecularAutoCrafterIconButton(
                    0, 0, Icon.SCHEDULING_ROUND_ROBIN, Component.empty(), this::toggleDirectionPopup));
        }
        super.init();
        directionPopup = new DirectionPopup(leftPos + 82, topPos + 82);
        addScreenWidget(directionPopup);
        previousOutputPage = addScreenWidget(new MolecularAutoCrafterIconButton(
                leftPos + 120, topPos + 74, 12, 10, Icon.ARROW_LEFT,
                Component.translatable("gui.molecularmanipulator.auto_craft_previous_output_page"),
                () -> menu.requestOutputPage(menu.outputPage - 1)));
        nextOutputPage = addScreenWidget(new MolecularAutoCrafterIconButton(
                leftPos + 158, topPos + 74, 12, 10, Icon.ARROW_RIGHT,
                Component.translatable("gui.molecularmanipulator.auto_craft_next_output_page"),
                () -> menu.requestOutputPage(menu.outputPage + 1)));
        var slots = menu.getPatternSlots();
        for (int index = 0; index < slots.size(); index++) {
            int selected = index;
            Slot slot = slots.get(index);
            int x = leftPos + slot.x + (16 - CONTROL_SIZE) / 2;
            configureButtons[index] = addScreenWidget(new MolecularAutoCrafterIconButton(
                    x, topPos + slot.y - 20, CONTROL_SIZE, CONTROL_ICON_SIZE, Icon.WRENCH,
                    Component.translatable("gui.molecularmanipulator.auto_craft_select_slot", index + 1),
                    () -> menu.requestConfig(selected)));
            startButtons[index] = addScreenWidget(new MolecularAutoCrafterIconButton(
                    x, topPos + slot.y + 20, CONTROL_SIZE, CONTROL_ICON_SIZE, Icon.ENTER,
                    Component.empty(),
                    () -> menu.requestToggle(selected)));
        }
        refreshControls();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshControls();
    }

    private void refreshControls() {
        previousOutputPage.visible = nextOutputPage.visible = menu.outputPages > 1;
        previousOutputPage.active = menu.outputPage > 0;
        nextOutputPage.active = menu.outputPage + 1 < menu.outputPages;
        boolean adjacent = menu.outputMode == MolecularAutoCrafterBlockEntity.OutputMode.ADJACENT;
        outputModeButton.setIcon(adjacent ? Icon.AUTO_EXPORT_OFF : Icon.AUTO_EXPORT_ON);
        outputModeButton.setMessage(Component.translatable(adjacent
                ? "gui.molecularmanipulator.auto_craft_output_adjacent"
                : "gui.molecularmanipulator.auto_craft_output_network"));
        outputModeButton.setTooltip(Tooltip.create(Component.translatable(adjacent
                ? "gui.molecularmanipulator.auto_craft_output_adjacent"
                : "gui.molecularmanipulator.auto_craft_output_network").copy().append("\n")
                .append(Component.translatable(adjacent
                        ? "gui.molecularmanipulator.auto_craft_output_adjacent_hint"
                        : "gui.molecularmanipulator.auto_craft_output_network_hint"))));
        outputDirectionButton.visible = adjacent;
        outputDirectionButton.active = adjacent;
        outputDirectionButton.setMessage(Component.translatable(
                "gui.molecularmanipulator.auto_craft_output_directions"));
        outputDirectionButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.molecularmanipulator.auto_craft_output_directions_hint")));
        if (!adjacent) directionPopupOpen = false;
        outputDirectionButton.setSelected(adjacent && directionPopupOpen);
        if (directionPopup != null) {
            directionPopup.visible = directionPopupOpen && adjacent;
            if (directionPopup.visible && !directionPopup.dragged) positionPopupUnderButton();
        }

        var slots = menu.getPatternSlots();
        for (int index = 0; index < slots.size(); index++) {
            boolean occupied = !slots.get(index).getItem().isEmpty();
            boolean enabled = (menu.enabledMask & (1 << index)) != 0;
            configureButtons[index].active = occupied;
            startButtons[index].active = occupied;
            startButtons[index].setSelected(enabled);
            startButtons[index].setIcon(enabled ? Icon.CLEAR : Icon.ENTER);
            startButtons[index].setMessage(Component.translatable(
                    enabled ? "gui.molecularmanipulator.auto_craft_stop_slot"
                            : "gui.molecularmanipulator.auto_craft_start_slot", index + 1));
            startButtons[index].setTooltip(Tooltip.create(startButtons[index].getMessage()));
        }
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, x, y, mouseX, mouseY, partialTick);
        // Slot coordinates describe the 16px item interior, not the 18px frame.
        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                Icon.SLOT_BACKGROUND.getBlitter().dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
            }
        }
        int gridX = x + MolecularAutoCrafterMenu.GRID_X - 1;
        OmniUiTheme.slotGridOutline(graphics, gridX, y + MolecularAutoCrafterMenu.PATTERN_Y - 1, 9, 1);
        OmniUiTheme.slotGridOutline(graphics, gridX, y + MolecularAutoCrafterMenu.OUTPUT_Y - 1, 9, 2);
        OmniUiTheme.slotGridOutline(graphics, gridX, y + MolecularAutoCrafterMenu.INVENTORY_Y - 1, 9, 3);
        OmniUiTheme.slotGridOutline(graphics, gridX, y + MolecularAutoCrafterMenu.HOTBAR_Y - 1, 9, 1);
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        var displayName = getGuiDisplayName(title);
        drawFittedString(graphics, displayName.getString().isBlank()
                        ? Component.translatable("gui.ae2.Patterns") : displayName,
                MolecularAutoCrafterMenu.GRID_X, 6, 160,
                style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB());
        if (menu.outputPages > 1) {
            drawCenteredFittedString(graphics,
                    Component.literal((menu.outputPage + 1) + "/" + menu.outputPages),
                    145, 76, 22, style.getColor(PaletteColor.MUTED_TEXT_COLOR).toARGB());
        }
    }

    private void toggleDirectionPopup() {
        if (menu.outputMode == MolecularAutoCrafterBlockEntity.OutputMode.ADJACENT) {
            directionPopupOpen = !directionPopupOpen;
            if (directionPopup != null) {
                directionPopup.visible = directionPopupOpen;
                if (directionPopupOpen && !directionPopup.dragged) positionPopupUnderButton();
            }
        }
    }

    private void positionPopupUnderButton() {
        directionPopup.setX(outputDirectionButton.getX() + outputDirectionButton.getWidth()
                - directionPopup.getWidth());
        directionPopup.setY(outputDirectionButton.getY() + outputDirectionButton.getHeight() + 4);
    }

    private final class DirectionPopup extends AbstractButton {
        private static final int CELL_SIZE = 18;
        private static final int CELL_STEP = 20;
        private static final int GRID_X = 8;
        private static final int GRID_Y = 24;
        private static final int POPUP_WIDTH = 106;
        private static final int POPUP_HEIGHT = 92;
        private static final int[][] CELLS = {
                {1, 0}, {1, 2}, {0, 1}, {2, 1}, {1, 1}, {2, 2}
        };
        private boolean dragging;
        private int dragOffsetX;
        private int dragOffsetY;
        private boolean dragged;

        private DirectionPopup(int x, int y) {
            super(x, y, POPUP_WIDTH, POPUP_HEIGHT, Component.empty());
            visible = false;
        }

        @Override
        public void onPress() {
            // Direction cells are handled by mouseClicked below.
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
            int localX = (int) mouseX - getX();
            int localY = (int) mouseY - getY();
            if (localX >= POPUP_WIDTH - 22 && localY >= POPUP_HEIGHT - 22) {
                directionPopupOpen = false;
                visible = false;
                return true;
            }
            if (localY < 18) {
                dragging = true;
                dragOffsetX = localX;
                dragOffsetY = localY;
                return true;
            }
            for (int index = 0; index < CELLS.length; index++) {
                int cellLeft = GRID_X + CELLS[index][0] * CELL_STEP;
                int cellTop = GRID_Y + CELLS[index][1] * CELL_STEP;
                if (localX >= cellLeft && localX < cellLeft + CELL_SIZE
                        && localY >= cellTop && localY < cellTop + CELL_SIZE) {
                    menu.requestOutputSideToggle(directions()[index].get3DDataValue());
                    return true;
                }
            }
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button,
                double dragX, double dragY) {
            if (!dragging || button != 0) return false;
            setX((int) mouseX - dragOffsetX);
            setY((int) mouseY - dragOffsetY);
            dragged = true;
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button != 0) return false;
            dragging = false;
            return true;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible) return;
            OmniUiTheme.panel(graphics, getX(), getY(), getX() + POPUP_WIDTH, getY() + POPUP_HEIGHT);
            drawFittedString(graphics, Component.translatable(
                    "gui.molecularmanipulator.auto_craft_output_directions"),
                    getX() + 8, getY() + 5, POPUP_WIDTH - 16, OmniUiTheme.PRIMARY_TEXT);
            int sides = menu.outputSides;
            var directions = directions();
            for (int index = 0; index < CELLS.length; index++) {
                int cellLeft = getX() + GRID_X + CELLS[index][0] * CELL_STEP;
                int cellTop = getY() + GRID_Y + CELLS[index][1] * CELL_STEP;
                boolean selected = (sides & 1 << directions[index].get3DDataValue()) != 0;
                boolean hovered = mouseX >= cellLeft && mouseX < cellLeft + CELL_SIZE
                        && mouseY >= cellTop && mouseY < cellTop + CELL_SIZE;
                Icon background = Icon.TOOLBAR_BUTTON_BACKGROUND;
                if (selected || hovered) graphics.fill(cellLeft, cellTop, cellLeft + CELL_SIZE,
                        cellTop + CELL_SIZE, OmniUiTheme.BUTTON_HOVER_SHADE);
                background.getBlitter().dest(cellLeft, cellTop, CELL_SIZE, CELL_SIZE)
                        .blit(graphics);
                var neighbor = neighborStack(directions[index]);
                if (!neighbor.isEmpty()) graphics.renderItem(neighbor, cellLeft + 1, cellTop + 1);
            }
            int closeLeft = getX() + POPUP_WIDTH - 20;
            int closeTop = getY() + POPUP_HEIGHT - 20;
            boolean closeHovered = mouseX >= closeLeft && mouseX < closeLeft + 18
                    && mouseY >= closeTop && mouseY < closeTop + 18;
            if (closeHovered) graphics.fill(closeLeft, closeTop, closeLeft + 18,
                    closeTop + 18, OmniUiTheme.BUTTON_HOVER_SHADE);
            Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter()
                    .dest(closeLeft, closeTop, 18, 18).blit(graphics);
            Icon.CLEAR.getBlitter().dest(closeLeft + 3, closeTop + 3, 12, 12)
                    .blit(graphics);
        }

        private ItemStack neighborStack(Direction direction) {
            var machine = menu.getMachine();
            var level = machine.getLevel();
            if (level == null) return ItemStack.EMPTY;
            var state = level.getBlockState(machine.getBlockPos().relative(direction));
            return state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
        }

        private Direction[] directions() {
            var facing = menu.getMachine().getBlockState()
                    .getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
            // The panel is viewed from the machine's front, looking against its facing.
            return new Direction[] {
                    Direction.UP, Direction.DOWN, facing.getClockWise(),
                    facing.getCounterClockWise(), facing, facing.getOpposite()
            };
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }
}
