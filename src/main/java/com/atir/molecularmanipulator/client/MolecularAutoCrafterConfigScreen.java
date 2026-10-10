package com.atir.molecularmanipulator.client;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.AETextField;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafter;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterConfigMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Compact AE2 detail page that grows only to fit the required ingredient rows. */
public final class MolecularAutoCrafterConfigScreen
        extends ResponsiveContainerScreen<MolecularAutoCrafterConfigMenu> {
    private static final int PAGE_SIZE = 5;
    private static final int SLOT_X = 10;
    private static final int NAME_X = 32;
    private static final int OUTPUT_Y = 44;
    private static final int MATERIAL_Y = 92;
    private static final int ROW_HEIGHT = 22;
    private static final int FIELD_X = 124;
    private static final int FIELD_WIDTH = 120;
    private static final int APPLY_X = 250;
    private static final int NAME_WIDTH = FIELD_X - NAME_X - 6;

    private final AutoCrafterAmountDraft limitDraft = new AutoCrafterAmountDraft();
    private final AutoCrafterAmountDraft[] reserveDrafts = IntStream.range(0, MolecularAutoCrafter.MAX_INPUTS)
            .mapToObj(index -> new AutoCrafterAmountDraft()).toArray(AutoCrafterAmountDraft[]::new);
    private AETextField outputLimit;
    private final AETextField[] reserves = new AETextField[MolecularAutoCrafter.MAX_INPUTS];
    private OmniButton limitApply;
    private OmniButton outputLimitMode;
    private final OmniButton[] reserveApply = new OmniButton[MolecularAutoCrafter.MAX_INPUTS];
    private OmniButton saveAll;
    private MolecularAutoCrafterIconButton previousPage;
    private MolecularAutoCrafterIconButton nextPage;
    private int page;
    private int layoutRows = 1;
    private ItemStack decodedPattern = ItemStack.EMPTY;
    private IPatternDetails details;

    public MolecularAutoCrafterConfigScreen(MolecularAutoCrafterConfigMenu menu, Inventory inventory,
            Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        layoutRows = materialRows();
        imageHeight = footerY() + 26;
        super.init();
        syncDrafts();
        outputLimit = amountField(OUTPUT_Y, limitDraft,
                Component.translatable("gui.molecularmanipulator.auto_craft_output_limit"),
                Component.translatable("gui.molecularmanipulator.auto_craft_output_limit_tooltip"));
        limitApply = applyButton(OUTPUT_Y, this::applyLimit);
        outputLimitMode = addScreenWidget(AeUiTheme.button(leftPos + SLOT_X, topPos + 61,
                imageWidth - SLOT_X * 2, 18,
                Component.empty(), button -> menu.requestOutputLimitModeToggle()));
        for (int input = 0; input < reserves.length; input++) {
            int selected = input;
            int rowY = rowY(input);
            reserves[input] = amountField(rowY, reserveDrafts[input],
                    Component.translatable("gui.molecularmanipulator.auto_craft_input_reserve_index", input + 1),
                    Component.translatable("gui.molecularmanipulator.auto_craft_input_reserve_tooltip"));
            reserveApply[input] = applyButton(rowY, () -> applyReserve(selected));
        }
        int footerY = topPos + footerY();
        addScreenWidget(OmniUiTheme.button(leftPos + SLOT_X, footerY, 38, 18,
                Component.translatable("gui.back"), button -> menu.requestBack()));
        saveAll = addScreenWidget(OmniUiTheme.button(leftPos + 54, footerY, 64, 18,
                Component.translatable("gui.molecularmanipulator.auto_craft_save_all"), button -> applyAll()));
        previousPage = addScreenWidget(new MolecularAutoCrafterIconButton(leftPos + 210, footerY + 1,
                16, 10, Icon.ARROW_LEFT, Component.translatable("gui.molecularmanipulator.auto_craft_previous_page"),
                () -> changePage(-1)));
        nextPage = addScreenWidget(new MolecularAutoCrafterIconButton(leftPos + 262, footerY + 1,
                16, 10, Icon.ARROW_RIGHT, Component.translatable("gui.molecularmanipulator.auto_craft_next_page"),
                () -> changePage(1)));
        refreshFields();
    }

    private AETextField amountField(int rowY, AutoCrafterAmountDraft draft, Component label, Component tooltip) {
        // AETextField's native texture is 12px tall and supports widths up to 128px.
        var field = new AETextField(style, font, leftPos + FIELD_X, topPos + rowY + 3, FIELD_WIDTH, 12);
        field.setBordered(false);
        field.setMessage(label);
        field.setMaxLength(19);
        field.setFilter(AutoCrafterAmountDraft::accepts);
        field.setValue(draft.text());
        field.setTooltipMessage(List.of(label, tooltip,
                Component.translatable("gui.molecularmanipulator.auto_craft_amount_range")));
        field.setResponder(value -> {
            draft.edit(value);
            refreshButtons();
        });
        return addScreenWidget(field);
    }

    private OmniButton applyButton(int rowY, Runnable action) {
        return addScreenWidget(OmniUiTheme.button(leftPos + APPLY_X, topPos + rowY - 2, 28, 20,
                Component.translatable("gui.molecularmanipulator.auto_craft_apply"), button -> action.run()));
    }

    private void applyLimit() {
        if (selectedDetails() != null) limitDraft.value().ifPresent(menu::requestLimit);
    }

    private void applyReserve(int input) {
        if (selectedDetails() != null && input < inputCount()) {
            reserveDrafts[input].value().ifPresent(value -> menu.requestReserve(input, value));
        }
    }

    private void applyAll() {
        if (!allValid() || selectedDetails() == null) return;
        if (limitDraft.isDirty()) applyLimit();
        for (int input = 0; input < inputCount(); input++) {
            if (reserveDrafts[input].isDirty()) applyReserve(input);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (outputLimit != null && outputLimit.isFocused()) {
                applyLimit();
                return true;
            }
            for (int input = 0; input < inputCount(); input++) {
                if (reserves[input] != null && reserves[input].visible && reserves[input].isFocused()) {
                    applyReserve(input);
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        syncDrafts();
        if (layoutRows != materialRows()) {
            // Ingredient counts arrive through menu sync. Recenter the panel and
            // all hit areas together; the drafts survive rebuilding the widgets.
            rebuildWidgets();
            return;
        }
        refreshFields();
    }

    private void syncDrafts() {
        limitDraft.sync(menu.outputLimit);
        for (int input = 0; input < reserveDrafts.length; input++) {
            reserveDrafts[input].sync(menu.getReserve(input));
        }
    }

    private void refreshFields() {
        if (outputLimit == null) return;
        page = Math.min(page, pageCount() - 1);
        boolean ready = selectedDetails() != null;
        refreshField(outputLimit, limitDraft, ready);
        boolean destination = menu.outputLimitMode
                == MolecularAutoCrafter.OutputLimitMode.DESTINATION;
        outputLimitMode.active = ready;
        outputLimitMode.setMessage(Component.translatable(destination
                ? "gui.molecularmanipulator.auto_craft_limit_source_destination"
                : "gui.molecularmanipulator.auto_craft_limit_source_network"));
        outputLimitMode.setTooltip(Tooltip.create(Component.translatable(destination
                ? "gui.molecularmanipulator.auto_craft_limit_source_destination_hint"
                : "gui.molecularmanipulator.auto_craft_limit_source_network_hint")));
        for (int input = 0; input < reserves.length; input++) {
            boolean visible = input < inputCount() && input / PAGE_SIZE == page;
            reserves[input].visible = visible;
            reserveApply[input].visible = visible;
            refreshField(reserves[input], reserveDrafts[input], ready && visible);
        }
        previousPage.active = page > 0;
        nextPage.active = page + 1 < pageCount();
        previousPage.visible = nextPage.visible = pageCount() > 1;
        refreshButtons();
    }

    private void refreshField(AETextField field, AutoCrafterAmountDraft draft, boolean editable) {
        field.setEditable(editable);
        field.active = editable;
        if (!editable) field.setFocused(false);
        if (!field.getValue().equals(draft.text())) field.setValue(draft.text());
        field.setTextColor(color(draft.value().isPresent() ? PaletteColor.TEXTFIELD_TEXT : PaletteColor.TEXTFIELD_ERROR));
    }

    private void refreshButtons() {
        if (saveAll == null) return;
        boolean ready = selectedDetails() != null;
        limitApply.active = ready && limitDraft.isDirty() && limitDraft.value().isPresent();
        for (int input = 0; input < reserves.length; input++) {
            var draft = reserveDrafts[input];
            reserveApply[input].active = ready && input < inputCount() && draft.isDirty() && draft.value().isPresent();
            reserves[input].setTextColor(color(draft.value().isPresent()
                    ? PaletteColor.TEXTFIELD_TEXT : PaletteColor.TEXTFIELD_ERROR));
        }
        outputLimit.setTextColor(color(limitDraft.value().isPresent()
                ? PaletteColor.TEXTFIELD_TEXT : PaletteColor.TEXTFIELD_ERROR));
        saveAll.active = ready && allValid() && hasChanges();
    }

    private boolean allValid() {
        if (limitDraft.value().isEmpty()) return false;
        for (int input = 0; input < inputCount(); input++) {
            if (reserveDrafts[input].value().isEmpty()) return false;
        }
        return true;
    }

    private boolean hasChanges() {
        if (limitDraft.isDirty()) return true;
        for (int input = 0; input < inputCount(); input++) {
            if (reserveDrafts[input].isDirty()) return true;
        }
        return false;
    }

    private void changePage(int direction) {
        page = net.minecraft.util.Mth.clamp(page + direction, 0, pageCount() - 1);
        setFocused(null);
        refreshFields();
    }

    private int inputCount() {
        return net.minecraft.util.Mth.clamp(menu.inputCount, 0, reserves.length);
    }

    private int pageCount() {
        return Math.max(1, (inputCount() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int materialRows() {
        // Keep the same height across ingredient pages so the navigation stays put.
        return net.minecraft.util.Mth.clamp(inputCount(), 1, PAGE_SIZE);
    }

    private int footerY() {
        return MATERIAL_Y + layoutRows * ROW_HEIGHT + 4;
    }

    private static int rowY(int input) {
        return MATERIAL_Y + (input % PAGE_SIZE) * ROW_HEIGHT;
    }

    @Override
    public void drawBG(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        // Use the actual height without changing the shared screen-style resource.
        BackgroundGenerator.draw(imageWidth, imageHeight, graphics, x, y);
        for (var slot : menu.slots) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(x + slot.x - 1, y + slot.y - 1).blit(graphics);
        }
        Icon.SLOT_BACKGROUND.getBlitter().dest(x + SLOT_X - 1, y + OUTPUT_Y - 1).blit(graphics);
        for (int input = page * PAGE_SIZE; input < Math.min(inputCount(), (page + 1) * PAGE_SIZE); input++) {
            Icon.SLOT_BACKGROUND.getBlitter().dest(x + SLOT_X - 1, y + rowY(input) - 1).blit(graphics);
        }
    }

    @Override
    public void drawFG(GuiGraphics graphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.auto_craft_config_title",
                menu.selectedSlot + 1), NAME_X, 6, imageWidth - NAME_X - 10, color(PaletteColor.DEFAULT_TEXT_COLOR));
        drawFittedString(graphics, Component.translatable("gui.molecularmanipulator.auto_craft_state."
                + menu.state.name().toLowerCase(Locale.ROOT)), NAME_X, 18,
                imageWidth - NAME_X - 10, color(PaletteColor.MUTED_TEXT_COLOR));
        label(graphics, "gui.molecularmanipulator.auto_craft_product", SLOT_X, 32, FIELD_X - SLOT_X - 6);
        label(graphics, "gui.molecularmanipulator.auto_craft_output_limit_label", FIELD_X, 32, FIELD_WIDTH);
        label(graphics, "gui.molecularmanipulator.auto_craft_materials", SLOT_X, 80, FIELD_X - SLOT_X - 6);
        label(graphics, "gui.molecularmanipulator.auto_craft_reserve_label", FIELD_X, 80, FIELD_WIDTH);
        var pattern = selectedDetails();
        if (pattern != null) {
            drawStack(graphics, pattern.getPrimaryOutput(), OUTPUT_Y, mouseX - offsetX, mouseY - offsetY);
            for (int input = page * PAGE_SIZE; input < Math.min(inputCount(), (page + 1) * PAGE_SIZE); input++) {
                drawStack(graphics, selectedInput(pattern, input), rowY(input), mouseX - offsetX, mouseY - offsetY);
            }
        } else {
            label(graphics, "gui.molecularmanipulator.auto_craft_invalid_pattern", NAME_X, OUTPUT_Y + 4, NAME_WIDTH);
        }
        if (inputCount() == 0) {
            label(graphics, "gui.molecularmanipulator.auto_craft_no_materials", SLOT_X,
                    MATERIAL_Y + 4, imageWidth - 20);
        }
        var footer = Component.translatable(!allValid() ? "gui.molecularmanipulator.auto_craft_invalid_amount"
                : hasChanges() ? "gui.molecularmanipulator.auto_craft_unsaved"
                : "gui.molecularmanipulator.auto_craft_saved");
        drawFittedString(graphics, footer, 126, footerY() + 5, pageCount() > 1 ? 76 : imageWidth - 136,
                color(allValid() ? PaletteColor.MUTED_TEXT_COLOR : PaletteColor.ERROR));
        if (pageCount() > 1) {
            drawCenteredFittedString(graphics, Component.literal((page + 1) + " / " + pageCount()),
                    244, footerY() + 5, 30, color(PaletteColor.DEFAULT_TEXT_COLOR));
        }
    }

    private void label(GuiGraphics graphics, String key, int x, int y, int width) {
        drawFittedString(graphics, Component.translatable(key), x, y, width, color(PaletteColor.DEFAULT_TEXT_COLOR));
    }

    private void drawStack(GuiGraphics graphics, GenericStack stack, int y, int mouseX, int mouseY) {
        if (stack == null) return;
        AEStackIcon.draw(graphics, stack, SLOT_X, y);
        Component name = stack.what().getDisplayName();
        Component visibleName = font.width(name) <= NAME_WIDTH ? name
                : Component.literal(font.plainSubstrByWidth(name.getString(), NAME_WIDTH - font.width("…")) + "…");
        graphics.drawString(font, visibleName, NAME_X, y + 4, color(PaletteColor.DEFAULT_TEXT_COLOR), false);
        if (mouseX >= SLOT_X - 1 && mouseX < FIELD_X - 6 && mouseY >= y - 1 && mouseY < y + 17) {
            setTooltipForNextRenderPass(List.of(name.getVisualOrderText()), DefaultTooltipPositioner.INSTANCE, true);
        }
    }

    private int color(PaletteColor color) {
        return style.getColor(color).toARGB();
    }

    private IPatternDetails selectedDetails() {
        var stack = menu.getSelectedPattern();
        if (minecraft == null || minecraft.level == null) return null;
        if (!ItemStack.matches(decodedPattern, stack)) {
            decodedPattern = stack.copy();
            try {
                details = stack.isEmpty() ? null : PatternDetailsHelper.decodePattern(stack, minecraft.level);
            } catch (RuntimeException | LinkageError ignored) {
                details = null;
            }
        }
        return details;
    }

    private static GenericStack selectedInput(IPatternDetails details, int index) {
        if (index < 0 || index >= details.getInputs().length) return null;
        var input = details.getInputs()[index];
        return input == null || input.getPossibleInputs().length == 0 ? null : input.getPossibleInputs()[0];
    }
}
