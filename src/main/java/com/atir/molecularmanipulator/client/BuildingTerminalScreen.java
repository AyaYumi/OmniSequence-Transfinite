package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.client.gui.theme.OsqFonts;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.client.gui.theme.OsqPalette;
import com.atir.molecularmanipulator.client.gui.theme.OsqScrollList;
import com.atir.molecularmanipulator.menu.BuildingTerminalMenu;
import com.atir.molecularmanipulator.menu.BuildingTerminalMenu.Entry;
import com.atir.molecularmanipulator.menu.BuildingTerminalMenu.Status;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.terminal.BuildingTerminal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.glfw.GLFW;

/** OSQ building directory. Access status and actions remain owned by the real server menu. */
public final class BuildingTerminalScreen extends ResponsiveContainerScreen<BuildingTerminalMenu> {
    private static final String KEY = "gui.molecularmanipulator.building_terminal.";
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 49;
    private static final int ROW_HEADER_CENTER = 13;
    private static final int LIST_HEIGHT = VISIBLE_ROWS * ROW_HEIGHT;
    private static final ResourceLocation ROW_FONT = Style.DEFAULT_FONT;
    private static final ResourceLocation DETAIL_FONT = Style.DEFAULT_FONT;
    private final List<RowView> rows = new ArrayList<>();
    private final Button[] openButtons = new Button[VISIBLE_ROWS];
    private final Button[] removeButtons = new Button[VISIBLE_ROWS];
    private List<Entry> lastEntries = List.of();
    private DirectoryScrollbar scrollbar;
    private OsqUi ui;
    private ItemStack terminalIcon;
    private int scroll;

    public BuildingTerminalScreen(BuildingTerminalMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    @Override
    protected void init() {
        ui = new OsqUi("building_access_terminal");
        imageWidth = ui.layout.width;
        imageHeight = ui.layout.height;
        super.init();
        terminalIcon = ModContent.BUILDING_ACCESS_TERMINAL.get().getDefaultInstance();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            final int row = i;
            var box = ui.layout.findControl("row." + i);
            openButtons[i] = addScreenWidget(new RowButton(box, () -> openRow(row)));
            removeButtons[i] = addScreenWidget(ui.button("remove." + i, leftPos, topPos,
                    () -> tr("action.remove"), () -> removeRow(row)));
            ui.state(removeButtons[i], () -> "danger");
        }
        scrollbar = addScreenWidget(new DirectoryScrollbar(ui.layout.findControl("scrollbar")));
        // Resizing/reopening creates new widgets, even if synchronized data is unchanged.
        lastEntries = List.of();
        rows.clear();
        syncRows();
        rebuildRows();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        syncRows();
    }

    private void syncRows() {
        var incoming = menu.directory.entries();
        if (incoming.equals(lastEntries)) return;
        lastEntries = List.copyOf(incoming);
        rows.clear();
        for (var entry : lastEntries) rows.add(RowView.of(entry));
        rebuildRows();
    }

    private int firstRow() { return scroll / ROW_HEIGHT; }
    private int maxScroll() { return OsqScrollList.maxScroll(rows.size(), LIST_HEIGHT, ROW_HEIGHT); }
    private int visibleRowCount() { return Math.min(VISIBLE_ROWS, rows.size() - firstRow()); }

    private void setScroll(double value) {
        int clamped = OsqScrollList.clamp(value, rows.size(), LIST_HEIGHT, ROW_HEIGHT);
        if (scroll == clamped) return;
        scroll = clamped;
        rebuildRows();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = logicalMouseX(mouseX) - leftPos, y = logicalMouseY(mouseY) - topPos;
        var panel = ui == null ? null : ui.layout.find("panel.3");
        if (panel != null && scrollY != 0 && x >= panel.x && x < panel.x + panel.w
                && y >= panel.y && y < panel.y + panel.h) {
            setScroll(OsqScrollList.scroll(scroll, scrollY, rows.size(), LIST_HEIGHT, ROW_HEIGHT));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // ContainerScreen consumes drags without forwarding them to its focused native widget.
        if (button == 0 && isDragging() && getFocused() == scrollbar) {
            return scrollbar.mouseDragged(logicalMouseX(mouseX), logicalMouseY(mouseY), button,
                    logicalMouseX(mouseX) - logicalMouseX(mouseX - dragX),
                    logicalMouseY(mouseY) - logicalMouseY(mouseY - dragY));
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void openRow(int row) {
        int index = firstRow() + row;
        if (index < rows.size()) menu.requestOpen(index);
    }

    private void removeRow(int row) {
        int index = firstRow() + row;
        if (index < rows.size()) menu.requestRemove(index);
    }

    private void rebuildRows() {
        scroll = OsqScrollList.clamp(scroll, rows.size(), LIST_HEIGHT, ROW_HEIGHT);
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            var open = openButtons[i];
            var remove = removeButtons[i];
            boolean visible = i < visibleRowCount();
            open.visible = open.active = remove.visible = remove.active = visible;
            if (!visible) {
                open.setTooltip(null);
                remove.setTooltip(null);
                continue;
            }
            var row = rows.get(firstRow() + i);
            open.setMessage(row.name());
            open.setTooltip(Tooltip.create(tr("tooltip.row", row.name(), statusLabel(row.entry().status()),
                    row.detail(), tr("action.open_hint"))));
            remove.setTooltip(Tooltip.create(tr("tooltip.remove", row.name())));
        }
        scrollbar.active = maxScroll() > 0;
    }

    @Override
    public void drawBG(GuiGraphics g, int x, int y, int mx, int my, float pt) {
        if (ui == null) return;
        ui.background(g, font, x, y);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        for (int i = 0; i < visibleRowCount(); i++) {
            var box = ui.layout.find("card." + i);
            boolean hovered = openButtons[i].isHoveredOrFocused();
            OsqLayout.drawSprite(g, box, 0, 0);
            if (hovered) {
                fill(g, box.x + 1, box.y + 1, box.w - 2, box.h - 2,
                        OsqPalette.withAlpha(color("accent"), .12f));
                g.renderOutline(Math.round(box.x), Math.round(box.y), Math.round(box.w), Math.round(box.h), color("accent"));
            }
            ui.slotBackground(g, box.x + 8, box.y + ROW_HEADER_CENTER - 9);
        }
        fill(g, 285, 98, 98, 2, color("feature_track"));
        fill(g, 285, 98, 98f * rows.size() / BuildingTerminal.MAX_BINDINGS, 2, color("level"));
        g.pose().popPose();
    }

    @Override
    public void drawFG(GuiGraphics g, int x, int y, int mx, int my) {
        if (ui == null) return;
        // Item rendering changes GUI depth/buffer state. Finish every icon before drawing any labels.
        drawIcons(g);
        g.flush();
        text(g, tr("title"), 12, 19, 304, 13, "ink", "left");
        small(g, tr("subtitle"), 12, 30, 304, "muted", "left");
        label(g, tr("list"), 17, 47, 110, "ink");
        small(g, tr("action.open_hint"), 155, 47, 104, "muted", "right");
        if (rows.isEmpty()) drawEmpty(g); else drawRows(g);
        drawSummary(g);
        drawHelp(g);
        centered(g, tr("scroll_hint"), 17, 329, 366, "ink2");
        centered(g, tr("remove_note"), 17, 340, 366, "muted");
    }

    private void drawIcons(GuiGraphics g) {
        if (rows.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(122, 111, 0);
            g.pose().scale(2, 2, 1);
            g.renderItem(terminalIcon, 0, 0);
            g.pose().popPose();
            return;
        }
        for (int i = 0; i < visibleRowCount(); i++) {
            var row = rows.get(firstRow() + i);
            var box = ui.layout.find("card." + i);
            if (!row.icon().isEmpty()) g.renderItem(row.icon(), Math.round(box.x) + 9,
                    Math.round(box.y) + ROW_HEADER_CENTER - 8);
        }
    }

    private void drawRows(GuiGraphics g) {
        for (int i = 0; i < visibleRowCount(); i++) {
            var row = rows.get(firstRow() + i);
            var box = ui.layout.find("card." + i);
            int x = Math.round(box.x), y = Math.round(box.y);
            if (row.icon().isEmpty()) {
                centered(g, Component.literal("?"), x + 9, y + ROW_HEADER_CENTER, 16, "muted");
            }
            var remove = ui.layout.findControl("remove." + i);
            String tint = statusColor(row.entry().status());
            var status = statusLabel(row.entry().status());
            float statusWidth = OsqFonts.widthF(font, status.getString(), DETAIL_FONT);
            float statusX = remove.x - 6 - statusWidth;
            float actionCenter = remove.y + OsqUi.controlCenter("button", "danger", remove.h, false);
            // The icon and name share the header; position, status and Remove share the lower line.
            float nameWidth = box.w - 40;
            float detailWidth = statusX - 12 - (x + 8);
            centered(g, ellipsize(row.name(), ROW_FONT, nameWidth), x + 32,
                    y + ROW_HEADER_CENTER, nameWidth, "ink2", "left");
            centered(g, ellipsize(row.detail(), DETAIL_FONT, detailWidth), x + 8,
                    actionCenter, detailWidth, "muted", "left");
            fill(g, statusX - 6, actionCenter - 1.5f, 2, 2, color(tint));
            centered(g, status, statusX, actionCenter, statusWidth, tint, "left");
        }
    }

    private void drawSummary(GuiGraphics g) {
        long ready = lastEntries.stream().filter(entry -> entry.status() == Status.READY).count();
        label(g, tr("summary"), 285, 48, 98, "feature_muted");
        text(g, Component.literal(Integer.toString(rows.size())), 284, 78, 60,
                26, "feature_ink", "left");
        text(g, Component.literal("/ " + BuildingTerminal.MAX_BINDINGS), 345, 76, 38,
                9, "feature_muted", "right");
        small(g, tr("list"), 285, 90, 98, "feature_muted", "left");
        small(g, tr("availability", ready, rows.size() - ready), 285, 113, 98, "feature_ink", "left");
    }

    private void drawHelp(GuiGraphics g) {
        label(g, tr("help.title"), 285, 141, 98, "ink");
        label(g, tr("help.bind"), 285, 160, 98, "accent");
        float y=helpBody(g,tr("help.bind_click"),173);
        y=helpBody(g,tr("help.bind_item"),y);
        label(g,tr("help.access"),285,y+5,98,"accent");
        y=helpBody(g,tr("help.dimension"),y+18);
        y=helpBody(g,tr("help.chunk"),y);
        helpBody(g,tr("help.no_force_load"),y);
    }

    private float helpBody(GuiGraphics g,Component value,float baseline){
        var lines=OsqFonts.wrap(font,value.getString(),DETAIL_FONT,98);
        for(var line:lines){small(g,Component.literal(line),285,baseline,98,"ink2","left");baseline+=11;}
        return baseline+3;
    }

    private void drawEmpty(GuiGraphics g) {
        text(g, tr("empty.title"), 25, 166, 226, 9, "ink2", "center");
        wrapped(g, tr("empty.hint.bind"), 35, 187, 206);
        wrapped(g, tr("empty.hint.open"), 35, 220, 206);
    }

    private int color(String name) { return OsqLayout.argb(ui.layout.find("color." + name).color); }

    private static String statusColor(Status status) {
        return switch (status) {
            case READY -> "accent";
            case UNLOADED, WRONG_DIMENSION -> "warning";
            case MISSING, DENIED -> "error";
            case INCOMPLETE -> "muted";
        };
    }

    private static Component tr(String suffix, Object... args) { return Component.translatable(KEY + suffix, args); }
    private static Component statusLabel(Status status) { return tr("status." + status.name().toLowerCase(Locale.ROOT)); }

    private static void fill(GuiGraphics g, float x, float y, float w, float h, int tint) {
        g.fill(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), tint);
    }

    private void small(GuiGraphics g, Component value, float x, float y, float width, String tint, String alignment) {
        text(g, value, x, y, width, 7.5f, tint, alignment);
    }

    private void label(GuiGraphics g, Component value, float x, float y, float width, String tint) {
        text(g, value, x, y, width, 7.5f, tint, "left");
    }

    private void text(GuiGraphics g, Component value, float x, float baseline, float width,
            float size, String tint, String alignment) {
        var entry = new OsqLayout.Entry();
        entry.x = x; entry.baseline = baseline; entry.w = width; entry.size = size;
        entry.fontId = "minecraft:default";
        entry.color = ui.layout.find("color." + tint).color;
        entry.alignment = alignment;
        ui.layout.renderSingleLine(g, font, entry, value, 0, 0);
    }

    private void centered(GuiGraphics g, Component value, float x, float centerY, float width, String tint) {
        centered(g,value,x,centerY,width,tint,"center");
    }

    private void centered(GuiGraphics g, Component value, float x, float centerY, float width,
            String tint, String alignment) {
        var entry = new OsqLayout.Entry();
        entry.x = x; entry.centerY = centerY; entry.w = width; entry.size = 7.5f;
        entry.fontId = "minecraft:default";
        entry.color = ui.layout.find("color." + tint).color;
        entry.alignment = alignment;
        ui.layout.renderSingleLine(g, font, entry, value, 0, 0);
    }

    private void wrapped(GuiGraphics g, Component value, float x, float baseline, float width) {
        for (String line : OsqFonts.wrap(font, value.getString(), DETAIL_FONT, width)) {
            small(g, Component.literal(line), x, baseline, width, "muted", "center");
            baseline += 10;
        }
    }

    private Component ellipsize(Component value, ResourceLocation face, float width) {
        String text = value.getString();
        if (OsqFonts.widthF(font, text, face) <= width) return value;
        int low = 0, high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String shortened = text.substring(0, text.offsetByCodePoints(0, middle)) + "…";
            if (OsqFonts.widthF(font, shortened, face) <= width) low = middle;
            else high = middle - 1;
        }
        return Component.literal(text.substring(0, text.offsetByCodePoints(0, low)) + "…");
    }

    private record RowView(Entry entry, ItemStack icon, Component name, Component detail) {
        static RowView of(Entry entry) {
            var block = BuiltInRegistries.BLOCK.getOptional(entry.block()).orElse(Blocks.AIR);
            var icon = block == Blocks.AIR ? ItemStack.EMPTY : new ItemStack(block);
            Component name = icon.isEmpty() ? Component.literal(entry.block().toString()) : icon.getHoverName();
            var dimensionId = entry.target().dimension().location();
            String fallback = dimensionId.getNamespace().equals("minecraft") ? dimensionId.getPath() : dimensionId.toString();
            var dimension = Component.translatableWithFallback(dimensionId.toLanguageKey("dimension"), fallback);
            var pos = entry.target().pos();
            var coords = Component.literal("(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")");
            return new RowView(entry, icon, name, tr("position", dimension, coords));
        }
    }

    /** Card art and labels are painted by the screen; this is only its native input/tooltip target. */
    private final class RowButton extends Button {
        RowButton(OsqLayout.Entry box, Runnable action) {
            super(leftPos + Math.round(box.x), topPos + Math.round(box.y), Math.round(box.w), Math.round(box.h),
                    Component.empty(), button -> action.run(), DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) { }
    }

    /** Uses native focus/capture; the screen forwards container drags in logical GUI coordinates. */
    private final class DirectoryScrollbar extends AbstractWidget {
        private double grabOffset;

        DirectoryScrollbar(OsqLayout.Entry box) {
            super(leftPos + Math.round(box.x), topPos + Math.round(box.y), Math.round(box.w), Math.round(box.h), tr("scroll_hint"));
        }

        private int thumbHeight() { return Math.max(12, getHeight() * VISIBLE_ROWS / Math.max(VISIBLE_ROWS, rows.size())); }
        private int thumbY() {
            return getY() + (maxScroll() == 0 ? 0 : (int)Math.round((getHeight() - thumbHeight()) * (double)scroll / maxScroll()));
        }

        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            fill(g, getX(), getY(), getWidth(), getHeight(), color("hair"));
            fill(g, getX(), thumbY(), getWidth(), thumbHeight(), color(active ? "accent" : "disabled"));
            if (active && isHoveredOrFocused()) g.renderOutline(getX() - 1, thumbY(), getWidth() + 2, thumbHeight(), color("accent"));
        }

        @Override public void onClick(double mouseX, double mouseY) {
            grabOffset = mouseY >= thumbY() && mouseY < thumbY() + thumbHeight() ? mouseY - thumbY() : thumbHeight() / 2.0;
            scrollFromMouse(mouseY);
        }

        @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            scrollFromMouse(mouseY);
        }

        private void scrollFromMouse(double mouseY) {
            if (active) setScroll((mouseY - getY() - grabOffset) * maxScroll() / (getHeight() - thumbHeight()));
        }

        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!active) return false;
            switch (keyCode) {
                case GLFW.GLFW_KEY_UP -> setScroll(scroll - ROW_HEIGHT);
                case GLFW.GLFW_KEY_DOWN -> setScroll(scroll + ROW_HEIGHT);
                case GLFW.GLFW_KEY_HOME -> setScroll(0);
                case GLFW.GLFW_KEY_END -> setScroll(maxScroll());
                default -> { return false; }
            }
            return true;
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            if (active) output.add(NarratedElementType.USAGE, tr("scroll_usage"));
        }
    }
}
