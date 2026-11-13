package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.SequenceWirelessHubBlock;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.menu.BuildingMenu;
import com.atir.molecularmanipulator.menu.SequenceWirelessMenuState;
import com.atir.molecularmanipulator.menu.SequenceWirelessMenuState.Entry;
import com.atir.molecularmanipulator.registry.WirelessContent;
import com.mojang.blaze3d.platform.Lighting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Shared modal: native button input, OSQ sizes and a server-authorized three-row page. */
final class SequenceWirelessPanel extends AbstractContainerEventHandler implements Renderable, NarratableEntry {
    static final int WIDTH = 280, HEIGHT = 288, EDGE_WIDTH = 22, EDGE_GAP = 4;
    private final OsqUi ui = loadUi();
    private final ItemStack hubIcon = new ItemStack(WirelessContent.HUB_ITEM.get());
    private final BuildingMenu menu;
    private final Font font;
    private final Runnable close;
    private final List<PanelButton> buttons = new ArrayList<>();
    private final List<PanelButton> rows = new ArrayList<>();
    private final PanelButton connect, disconnect, previous, next;
    private String selected;
    private int x, y;

    SequenceWirelessPanel(BuildingMenu menu, Font font, Runnable close) {
        this.menu = menu;
        this.font = font;
        this.close = close;
        ui.hide(entry -> "sidebar".equals(entry.id) || "sidebar".equals(entry.ownerId));
        disconnect = add("disconnect", tr("disconnect"), menu::requestWirelessDisconnect);
        for (int i = 0; i < SequenceWirelessMenuState.PAGE_SIZE; i++) {
            final int row = i;
            var button = add("row" + i, Component.empty(), () -> select(row));
            button.row = i;
            rows.add(button);
        }
        previous = add("prev", Component.translatable("gui.molecularmanipulator.previous_page"), () -> changePage(-1));
        next = add("next", Component.translatable("gui.molecularmanipulator.next_page"), () -> changePage(1));
        add("cancel", tr("cancel"), close);
        connect = add("connect", tr("connect"), this::connect);
    }

    private static OsqUi loadUi() {
        return new OsqUi(OsqLayout.load(MolecularManipulator.id("osq_light/popup/wireless_selector.json")));
    }

    static Button sideButton(int left, int top, BooleanSupplier open, Runnable action) {
        var ui = loadUi();
        var button = ui.button("sidebar", left, top, () -> tr("title"), action);
        ui.state(button, () -> open.getAsBoolean() ? "active" : "normal");
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr("tooltip")));
        return button;
    }

    void position(int left, int top) {
        x = left;
        y = top;
        for (var button : buttons) button.setPosition(x + button.localX, y + button.localY);
    }

    private PanelButton add(String id, Component label, Runnable action) {
        var button = new PanelButton(id, label, action);
        buttons.add(button);
        return button;
    }

    @Override public List<? extends GuiEventListener> children() { return buttons; }

    private void select(int row) {
        if (row < menu.wirelessState.entries().size()) {
            selected = menu.wirelessState.entries().get(row).binding().hubId().toString();
        }
    }

    private void connect() {
        if (selected != null && connect.active) menu.requestWirelessConnect(selected);
    }

    private void changePage(int offset) {
        var state = menu.wirelessState;
        int page = com.atir.molecularmanipulator.util.MathCompat.clamp(state.page() + offset, 0, state.pages() - 1);
        if (page != state.page()) {
            selected = null;
            menu.requestWirelessPage(page);
        }
    }

    private boolean selected(Entry entry) { return entry.binding().hubId().toString().equals(selected); }
    private boolean current(Entry entry) {
        var current = menu.wirelessState.current();
        return current != null && current.binding().equals(entry.binding());
    }

    private void updateControls() {
        var state = menu.wirelessState;
        if (state.entries().stream().noneMatch(this::selected)) selected = null;
        disconnect.active = state.canConfigure() && state.bound();
        previous.active = state.canConfigure() && state.page() > 0;
        next.active = state.canConfigure() && state.page() + 1 < state.pages();
        connect.active = state.canConfigure() && state.entries().stream().anyMatch(entry -> selected(entry) && !current(entry));
        for (int i = 0; i < rows.size(); i++) {
            var button = rows.get(i);
            button.visible = i < state.entries().size();
            button.active = button.visible && state.canConfigure();
            if (button.visible) {
                var entry = state.entries().get(i);
                button.setMessage(Component.empty().append(entry.name()).append(" ").append(detail(entry))
                        .append(" ").append(status(entry)));
            }
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateControls();
        var state = menu.wirelessState;
        ui.background(g, font, x, y);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        ui.foreground(g, font);
        g.pose().popPose();
        renderHubThumbnail(g, x + 12, y + 10);
        text(g, menu.getBlockEntity() == null ? Component.empty() : menu.getBlockEntity().getBlockState().getBlock().getName(),
                35, 30, 196, 7.5f, ui.palette.ink3);
        Component name = state.current() != null ? state.current().name() : tr(state.bound() ? "unavailable_target" : "unbound");
        text(g, name, 20, 75, 174, 9, ui.palette.ink);
        right(g, state.bound() ? Component.translatable("gui.molecularmanipulator.quantum_state."
                        + state.linkState().name().toLowerCase(Locale.ROOT)) : Component.empty(),
                260, 54, 148, 7.5f, connected(state.linkState()) ? ui.palette.ok : ui.palette.ink3);
        if (state.entries().isEmpty()) {
            text(g, tr(state.canConfigure() ? "empty" : state.result().isEmpty() ? "loading" : "denied"),
                    22, 153, 236, 9, ui.palette.ink2);
            text(g, tr(state.canConfigure() ? "empty_hint" : "ownership_hint"), 22, 172, 236, 7.5f, ui.palette.ink3);
        }
        centered(g, tr("page", state.page() + 1, state.pages()), 38, 235, 204, 7.5f, ui.palette.ink3);
        text(g, tr(state.result().isEmpty() ? "hint" : "result." + state.result()), 12, 252, 256, 7.5f, ui.palette.ink3);
        for (var button : buttons) button.render(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < state.entries().size(); i++) {
            var entry = state.entries().get(i);
            if (rows.get(i).isHoveredOrFocused()) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 20);
                g.renderComponentTooltip(font, List.of(entry.name(), detail(entry), status(entry)), mouseX, mouseY);
                g.pose().popPose();
                break;
            }
        }
    }

    private void renderRow(GuiGraphics g, PanelButton button, Entry entry) {
        int top = button.localY;
        String state = !button.active ? "disabled" : selected(entry) ? "active"
                : button.isHoveredOrFocused() ? "hover" : "normal";
        var surface = button.surface;
        surface.sprite = ui.sprite("row_" + state);
        surface.x = button.getX(); surface.y = button.getY();
        surface.w = button.getWidth(); surface.h = button.getHeight();
        OsqLayout.drawSprite(g, surface, 0, 0);
        int ink = ui.palette.controlText("row", state, button.active);
        text(g, Component.literal(Integer.toString(menu.wirelessState.page() * SequenceWirelessMenuState.PAGE_SIZE + button.row + 1)),
                20, top + 17, 17, 9, ink);
        renderHubThumbnail(g, x + 42, y + top + 10);
        text(g, entry.name(), 66, top + 13, 148, 9, ink);
        text(g, detail(entry), 66, top + 28, 190, 7.5f, ink);
        right(g, status(entry), 258, top + 13, 39, 7.5f, ink);
    }

    private Component status(Entry entry) {
        return tr(selected(entry) ? "selected" : current(entry) ? "connected" : entry.online() ? "online" : "offline");
    }

    private static Component detail(Entry entry) {
        var location = entry.binding().target();
        var id = location.dimension().location();
        Component dimension = switch (id.toString()) {
            case "minecraft:overworld" -> tr("overworld");
            case "minecraft:the_nether" -> tr("nether");
            case "minecraft:the_end" -> tr("end");
            default -> Component.literal(id.toString());
        };
        return Component.empty().append(dimension).append("  " + location.pos().toShortString());
    }

    /** Selector thumbnails use the active model with the native inventory perspective. */
    private void renderHubThumbnail(GuiGraphics g, int x, int y) {
        var minecraft = Minecraft.getInstance();
        var state = WirelessContent.HUB.get().defaultBlockState().setValue(SequenceWirelessHubBlock.ONLINE, true);
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        g.flush();
        g.pose().pushPose();
        try {
            g.pose().translate(x + 8, y + 8, 150);
            g.pose().scale(16, -16, 16);
            if (model.usesBlockLight()) Lighting.setupFor3DItems();
            else Lighting.setupForFlatItems();
            minecraft.getItemRenderer().render(hubIcon, ItemDisplayContext.GUI, false,
                    g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, model);
            g.flush();
        } finally {
            g.pose().popPose();
            Lighting.setupFor3DItems();
        }
    }

    private static boolean connected(QuantumLinkState state) {
        return state == QuantumLinkState.CONNECTED || state == QuantumLinkState.CONNECTED_BUILD_ONLY;
    }

    private void text(GuiGraphics g, Component value, float left, float centerY, float width, float size, int color) {
        text(g, value, left, centerY, width, size, color, "left");
    }

    private void centered(GuiGraphics g, Component value, float left, float centerY, float width, float size, int color) {
        text(g, value, left, centerY, width, size, color, "center");
    }

    private void right(GuiGraphics g, Component value, float right, float centerY, float width, float size, int color) {
        text(g, value, right - width, centerY, width, size, color, "right");
    }

    private void text(GuiGraphics g, Component value, float left, float centerY, float width, float size, int color, String align) {
        var entry = new OsqLayout.Entry();
        entry.x = left; entry.centerY = centerY; entry.w = width; entry.size = size;
        entry.fontId = Style.DEFAULT_FONT.toString(); entry.color = String.format("#%08X", color); entry.alignment = align;
        ui.layout.renderSingleLine(g, font, entry, value, x, y);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateControls();
        super.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (vertical != 0) changePage(vertical > 0 ? -1 : 1);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close.run(); return true; }
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            changePage(key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            var available = buttons.stream().filter(button -> button.visible && button.active).toList();
            if (!available.isEmpty()) {
                int index = available.indexOf(getFocused());
                int step = key == GLFW.GLFW_KEY_UP || (key == GLFW.GLFW_KEY_TAB && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) ? -1 : 1;
                setFocused(available.get(Math.floorMod(index + step, available.size())));
            }
            return true;
        }
        super.keyPressed(key, scan, modifiers);
        return true;
    }

    @Override public NarrationPriority narrationPriority() { return NarrationPriority.FOCUSED; }
    @Override public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, tr("title"));
        output.add(NarratedElementType.HINT, tr("hint"));
        if (getFocused() instanceof PanelButton button) button.updateNarration(output.nest());
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.molecularmanipulator.wireless_selector." + key, args);
    }

    private final class PanelButton extends OsqUi.SkinButton {
        private final int localX, localY;
        private final OsqLayout.Entry surface = new OsqLayout.Entry();
        private int row = -1;

        private PanelButton(String id, Component label, Runnable action) {
            ui.super(ui.layout.findControl(id), 0, 0, () -> label, action);
            var box = ui.layout.findControl(id);
            localX = Math.round(box.x);
            localY = Math.round(box.y);
            ui.hide(entry -> ui.layout.owns(box, entry));
        }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (row >= 0) {
                if (row < menu.wirelessState.entries().size()) renderRow(g, this, menu.wirelessState.entries().get(row));
                return;
            }
            super.renderWidget(g, mouseX, mouseY, partialTick);
        }
    }
}
