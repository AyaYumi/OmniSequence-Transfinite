package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.TaixuBlockEntity;
import com.atir.molecularmanipulator.blockentity.TaixuStructure;
import com.atir.molecularmanipulator.menu.TaixuMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public final class TaixuScreen extends ResponsiveContainerScreen<TaixuMenu> {
    private Button build, dismantle, cancel, overview, materials, dock, embed;
    private boolean materialPage;
    private long armedAt;
    private String lastNeeded = "";
    private final int[] needed = new int[TaixuStructure.Type.values().length];
    public TaixuScreen(TaixuMenu menu, Inventory inventory, Component title, ScreenStyle style) { super(menu, inventory, title, style); }
    private static Component tr(String key, Object... args) { return Component.translatable("gui.molecularmanipulator.taixu." + key, args); }
    @Override protected void init() {
        super.init();
        overview = button(12, 22, 70, "overview", b -> materialPage = false);
        materials = button(86, 22, 82, "materials", b -> materialPage = true);
        cancel = button(172, 22, 70, "motion_start", b -> menu.request(menu.operation == TaixuBlockEntity.Operation.IDLE && !menu.embedRequested ? "motion" : "cancel"));
        dock = button(246, 22, 74, "dock", b -> menu.request("dock"));
        dock.setTooltip(Tooltip.create(tr("dock_hint")));
        build = button(12, 246, 70, "build", b -> menu.request(menu.operation == TaixuBlockEntity.Operation.IDLE ? "build" : "pause"));
        var preview = button(90, 246, 70, "preview", b -> { TaixuGhostPreview.toggle(menu.getMachine()); onClose(); });
        preview.setTooltip(Tooltip.create(tr("preview_hint")));
        button(168, 246, 70, "refresh", b -> menu.request("refresh"));
        dismantle = button(246, 246, 74, "dismantle", b -> {
            long now = Util.getMillis();
            if (armedAt != 0 && now - armedAt < 250) return;
            menu.request("dismantle");
            armedAt = armedAt != 0 && now - armedAt <= 3000 ? 0 : now;
        });
        dismantle.setTooltip(Tooltip.create(tr("dismantle_hint")));
        embed = button(20, 217, 116, "embed", b -> menu.request(menu.structureVersion == TaixuStructure.EMBEDDED_VERSION ? "upgrade" : "embed"));
        embed.setTooltip(Tooltip.create(tr("embed_hint")));
        updateWidgets();
    }
    private Button button(int x, int y, int width, String text, Button.OnPress action) {
        return addScreenWidget(AeUiTheme.button(leftPos + x, topPos + y, width, 18, tr(text), action));
    }
    private void updateWidgets() {
        boolean active = menu.operation != TaixuBlockEntity.Operation.IDLE;
        build.setMessage(tr(active ? menu.paused ? "resume" : "pause" : "build"));
        build.active = !menu.embedRequested && (active || !menu.formed && !menu.movingBodies);
        cancel.active = menu.embedRequested || active || menu.motionMode != 3 && (menu.formed || menu.movingBodies);
        cancel.setMessage(tr(active || menu.embedRequested ? "cancel" : menu.motionMode == 1 ? "motion_pause" : menu.motionMode == 2 ? "motion_resume" : "motion_start"));
        cancel.setTooltip(active ? null : Tooltip.create(tr("motion_hint")));
        dock.active = !menu.embedRequested && menu.movingBodies && menu.motionMode != 3;
        dismantle.active = !menu.embedRequested && !active && menu.correct > 1 && menu.motionMode != 3;
        embed.visible = !materialPage && menu.structureVersion < TaixuStructure.VERSION;
        embed.active = menu.formed && !active && !menu.embedRequested && menu.motionMode != 3;
        String migration = menu.structureVersion == TaixuStructure.EMBEDDED_VERSION ? "upgrade" : "embed";
        embed.setMessage(tr(migration + (menu.embedRequested ? "_waiting" : "")));
        embed.setTooltip(Tooltip.create(tr(migration + "_hint")));
        if (armedAt != 0 && Util.getMillis() - armedAt > 3000) armedAt = 0;
        dismantle.setMessage(tr(armedAt == 0 ? "dismantle" : "confirm"));
        overview.active = materialPage; materials.active = !materialPage;
        if (!lastNeeded.equals(menu.needed)) {
            lastNeeded = menu.needed; Arrays.fill(needed, 0);
            var counts = lastNeeded.split(",");
            for (int i = 0; i < Math.min(counts.length, needed.length); i++) {
                try { needed[i] = Integer.parseInt(counts[i]); } catch (NumberFormatException ignored) { }
            }
        }
    }
    @Override public void containerTick() { super.containerTick(); updateWidgets(); }
    @Override public void drawBG(GuiGraphics g, int ox, int oy, int mx, int my, float partialTick) {
        super.drawBG(g, ox, oy, mx, my, partialTick);
        AeUiTheme.panel(g, leftPos + 12, topPos + 46, leftPos + 320, topPos + 238);
        AeUiTheme.slotGrid(g, leftPos + 84, topPos + 281, 9, 3);
        AeUiTheme.slotGrid(g, leftPos + 84, topPos + 339, 9, 1);
        AeUiTheme.slot(g, leftPos + 21, topPos + 281);
    }
    private void text(GuiGraphics g, Component text, int x, int y, int width, int color) { AeUiTheme.label(g, font, text, x, y, width, color); }
    @Override public void drawFG(GuiGraphics g, int ox, int oy, int mx, int my) {
        text(g, Component.translatable("block.molecularmanipulator.taixu_creation_nexus"), 12, 10, 308, AeUiTheme.PRIMARY_TEXT);
        if (materialPage) drawMaterials(g); else drawOverview(g);
        text(g, Component.translatable("container.inventory"), 85, 270, 162, AeUiTheme.PRIMARY_TEXT);
        text(g, tr("recovery"), 16, 270, 62, AeUiTheme.PRIMARY_TEXT);
        text(g, tr("recovery_hint"), 16, 307, 62, AeUiTheme.MUTED_TEXT);
    }
    private void drawOverview(GuiGraphics g) {
        text(g, tr(menu.formed ? "formed" : "incomplete"), 20, 55, 140, menu.formed ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        text(g, tr(menu.networkOnline ? "network_online" : "network_offline"), 172, 55, 140, menu.networkOnline ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
        text(g, tr("dimensions"), 20, 72, 292, AeUiTheme.PRIMARY_TEXT);
        int minY = menu.getMachine().getBlockPos().getY() - TaixuStructure.anchor(menu.structureVersion).getY();
        text(g, tr("height_range", minY, minY + 128, TaixuStructure.anchor(menu.structureVersion).getY()), 20, 87, 292, AeUiTheme.MUTED_TEXT);
        text(g, tr("structure_count", menu.correct, TaixuStructure.parts(menu.structureVersion).size()), 20, 105, 292, AeUiTheme.PRIMARY_TEXT);
        AeUiTheme.progress(g, 20, 118, 292, 6, (float) menu.correct / TaixuStructure.parts(menu.structureVersion).size(), AeUiTheme.CYAN);
        text(g, tr("inspection", menu.missing, menu.conflicts, menu.unloaded), 20, 131, 292, menu.conflicts > 0 ? AeUiTheme.ERROR : AeUiTheme.MUTED_TEXT);
        text(g, menu.embedRequested ? tr(menu.structureVersion == TaixuStructure.EMBEDDED_VERSION ? "upgrade_status" : "embed_status") : menu.operation == TaixuBlockEntity.Operation.IDLE && (menu.movingBodies || !menu.motionMessage.equals("off")) ? tr("motion." + menu.motionMessage)
                : tr("status." + menu.status.name().toLowerCase(Locale.ROOT)), 20, 149, 292, AeUiTheme.PRIMARY_TEXT);
        if (menu.operation != TaixuBlockEntity.Operation.IDLE) {
            text(g, tr("operation_count", menu.progress, menu.operationTotal), 20, 164, 292, AeUiTheme.MUTED_TEXT);
            AeUiTheme.progress(g, 20, 177, 292, 5, (float) menu.progress / Math.max(1, menu.operationTotal), AeUiTheme.ACCENT);
        } else text(g, Component.translatable("tooltip.molecularmanipulator.taixu.lore"), 20, 166, 292, AeUiTheme.ACCENT);
        Component detail = menu.neededMaterial >= 0 && menu.neededMaterial < needed.length
                ? tr("waiting_material", TaixuStructure.block(TaixuStructure.Type.values()[menu.neededMaterial]).getName())
                : menu.problem.isEmpty() ? tr("motion_hint") : tr("problem", menu.problem);
        text(g, detail, 20, 190, 292, AeUiTheme.WARNING);
        text(g, menu.neededMaterial >= 0 && !menu.problem.isEmpty() ? tr("problem", menu.problem) : tr("build_hint"), 20, 207, 292, AeUiTheme.MUTED_TEXT);
        if (menu.structureVersion == TaixuStructure.LEGACY_VERSION) text(g, tr("embed_available"), 144, 224, 168, AeUiTheme.MUTED_TEXT);
        else if (menu.structureVersion == TaixuStructure.EMBEDDED_VERSION) text(g, tr("upgrade_available"), 144, 224, 168, AeUiTheme.MUTED_TEXT);
        else text(g, tr("production_pending"), 20, 224, 292, AeUiTheme.MUTED_TEXT);
    }
    private void drawMaterials(GuiGraphics g) {
        text(g, tr("material_columns"), 20, 54, 292, AeUiTheme.PRIMARY_TEXT);
        var types = materialTypes();
        for (int i = 0; i < types.size(); i++) {
            int col = i / 7, row = i % 7, x = 20 + col * 148, y = 72 + row * 22;
            var type = types.get(i); int ordinal = type.ordinal();
            g.renderItem(new ItemStack(TaixuStructure.block(type)), x, y);
            text(g, TaixuStructure.block(type).getName(), x + 20, y, 122, AeUiTheme.PRIMARY_TEXT);
            text(g, Component.literal(TaixuStructure.counts(menu.structureVersion).getOrDefault(type, 0) + " / " + needed[ordinal]),
                    x + 20, y + 10, 122, needed[ordinal] == 0 ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        }
        text(g, tr("material_hint"), 20, 228, 292, AeUiTheme.MUTED_TEXT);
    }
    private List<TaixuStructure.Type> materialTypes() {
        return Arrays.stream(TaixuStructure.Type.values()).filter(type -> type != TaixuStructure.Type.CONTROLLER
                && TaixuStructure.counts(menu.structureVersion).getOrDefault(type, 0) > 0).toList();
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        if (!materialPage) return;
        double x = logicalMouseX(mx) - leftPos, y = logicalMouseY(my) - topPos;
        if (x < 20 || x >= 312 || y < 72 || y >= 226) return;
        int index = ((int) x - 20) / 148 * 7 + ((int) y - 72) / 22;
        var types = materialTypes();
        if (index < types.size()) {
            var type = types.get(index);
            g.renderComponentTooltip(font, List.of(TaixuStructure.block(type).getName(),
                    tr("material_detail", TaixuStructure.counts(menu.structureVersion).getOrDefault(type, 0), needed[type.ordinal()])), mx, my);
        }
    }
}
