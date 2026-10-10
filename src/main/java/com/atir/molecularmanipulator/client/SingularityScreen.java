package com.atir.molecularmanipulator.client;

import appeng.client.gui.style.ScreenStyle;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityDuplicationProcessor;
import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import com.atir.molecularmanipulator.menu.SingularityMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SingularityScreen extends ResponsiveContainerScreen<SingularityMenu> {
    private Button build, preview, refresh, dismantle, cancel, overview, collectionTab, duplicationTab, embed, collectionToggle;
    private boolean collectionPage, duplicationPage;
    private long armedAt;
    private int collectionScroll;

    public SingularityScreen(SingularityMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.molecularmanipulator.singularity." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        overview = button(12, 22, 76, "overview", b -> showOverview());
        collectionTab = button(92, 22, 82, "collection", b -> showCollection());
        duplicationTab = button(178, 22, 82, "duplication", b -> showDuplication());
        // This is only a construction cancel action now. Motion is controlled by
        // the resource-collection device state and has no standalone button.
        cancel = button(140, 217, 74, "cancel", b -> menu.request("cancel"));
        build = button(12, 246, 70, "build", b -> menu.request(menu.operation == SingularityBlockEntity.Operation.IDLE ? "build" : "pause"));
        preview = button(90, 246, 70, "preview", b -> {
            SingularityGhostPreview.toggle(menu.getMachine());
            onClose();
        });
        preview.setTooltip(Tooltip.create(tr("preview_hint")));
        refresh = button(168, 246, 70, "refresh", b -> menu.request("refresh"));
        dismantle = button(246, 246, 74, "dismantle", b -> {
            long now = Util.getMillis();
            if (armedAt != 0 && now - armedAt < 250) return;
            menu.request("dismantle");
            armedAt = armedAt != 0 && now - armedAt <= 3000 ? 0 : now;
        });
        dismantle.setTooltip(Tooltip.create(tr("dismantle_hint")));
        embed = button(20, 217, 116, "embed", b -> menu.request(menu.structureVersion == SingularityStructure.EMBEDDED_VERSION ? "upgrade" : "embed"));
        embed.setTooltip(Tooltip.create(tr("embed_hint")));
        collectionToggle = button(12, 246, 100, "collection_start", b -> menu.request("collection"));
        updateWidgets();
    }

    private void showOverview() {
        collectionPage = false;
        duplicationPage = false;
    }

    private void showCollection() {
        collectionPage = true;
        duplicationPage = false;
    }

    private void showDuplication() {
        collectionPage = false;
        duplicationPage = true;
    }

    private Button button(int x, int y, int width, String text, Button.OnPress action) {
        return addScreenWidget(AeUiTheme.button(leftPos + x, topPos + y, width, 18, tr(text), action));
    }

    private void updateWidgets() {
        boolean operationActive = menu.operation != SingularityBlockEntity.Operation.IDLE;
        boolean secondaryPage = collectionPage || duplicationPage;
        menu.setDuplicationSlotsActive(duplicationPage);
        menu.setQuantumSlotActive(!secondaryPage);
        build.visible = !secondaryPage;
        preview.visible = refresh.visible = !secondaryPage;
        build.setMessage(tr(operationActive ? menu.paused ? "resume" : "pause" : "build"));
        build.active = !secondaryPage && !menu.embedRequested
                && (operationActive || !menu.formed && !menu.movingBodies);

        cancel.visible = menu.embedRequested || operationActive;
        cancel.active = cancel.visible;
        cancel.setMessage(tr("cancel"));

        dismantle.visible = !secondaryPage;
        dismantle.active = !secondaryPage && !menu.embedRequested && !operationActive
                && !menu.collectionActive && menu.correct > 1 && menu.motionMode != 3;
        embed.visible = !secondaryPage && menu.structureVersion < SingularityStructure.VERSION;
        embed.active = menu.formed && !operationActive && !menu.embedRequested && menu.motionMode != 3
                && !menu.collectionActive;
        String migration = menu.structureVersion == SingularityStructure.EMBEDDED_VERSION ? "upgrade" : "embed";
        embed.setMessage(tr(migration + (menu.embedRequested ? "_waiting" : "")));
        embed.setTooltip(Tooltip.create(tr(migration + "_hint")));

        if (armedAt != 0 && Util.getMillis() - armedAt > 3000) armedAt = 0;
        dismantle.setMessage(tr(armedAt == 0 ? "dismantle" : "confirm"));
        overview.active = collectionPage || duplicationPage;
        collectionTab.active = !collectionPage;
        duplicationTab.active = !duplicationPage;

        collectionToggle.visible = collectionPage;
        collectionToggle.setMessage(tr(menu.collectionStarting ? "collection_starting"
                : menu.collectionActive ? "collection_stop" : "collection_start"));
        collectionToggle.active = collectionPage && menu.formed && !operationActive && !menu.embedRequested
                && menu.motionMode != 3 && (menu.collectionActive || menu.networkOnline);
        if (menu.getMachine().isSingleBlock()) {
            build.visible = preview.visible = refresh.visible = dismantle.visible = cancel.visible = embed.visible = false;
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    @Override
    public void drawBG(GuiGraphics g, int ox, int oy, int mx, int my, float partialTick) {
        super.drawBG(g, ox, oy, mx, my, partialTick);
        AeUiTheme.panel(g, leftPos + 12, topPos + 46, leftPos + 320, topPos + 238);
        AeUiTheme.slotGrid(g, leftPos + 84, topPos + 281, 9, 3);
        AeUiTheme.slotGrid(g, leftPos + 84, topPos + 339, 9, 1);
        if (!collectionPage && !duplicationPage) {
            AeUiTheme.slot(g, leftPos + SingularityMenu.QUANTUM_X - 1, topPos + SingularityMenu.QUANTUM_Y - 1);
        }
        if (duplicationPage) {
            AeUiTheme.slot(g, leftPos + SingularityMenu.SAMPLE_X - 1, topPos + SingularityMenu.DUPLICATION_Y - 1);
            AeUiTheme.slot(g, leftPos + SingularityMenu.BLACK_HOLE_X - 1, topPos + SingularityMenu.DUPLICATION_Y - 1);
        }
    }

    private void text(GuiGraphics g, Component text, int x, int y, int width, int color) {
        AeUiTheme.label(g, font, text, x, y, width, color);
    }

    @Override
    public void drawFG(GuiGraphics g, int ox, int oy, int mx, int my) {
        text(g, menu.getMachine().getBlockState().getBlock().getName(), 12, 10, 308, AeUiTheme.PRIMARY_TEXT);
        if (collectionPage) drawCollection(g);
        else if (duplicationPage) drawDuplication(g);
        else drawOverview(g);
        text(g, Component.translatable("container.inventory"), 85, 270, 162, AeUiTheme.PRIMARY_TEXT);
    }

    private void drawOverview(GuiGraphics g) {
        if (menu.getMachine().isSingleBlock()) {
            text(g, tr("compact_ready"), 20, 55, 140, AeUiTheme.SUCCESS);
            text(g, tr(menu.networkOnline ? "network_online" : "network_offline"), 172, 55, 140,
                    menu.networkOnline ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
            text(g, tr("running_status", overviewStatus()), 20, 76, 292, AeUiTheme.PRIMARY_TEXT);
            text(g, tr("compact_collection"), 20, 100, 292, AeUiTheme.MUTED_TEXT);
            text(g, tr("compact_duplication"), 20, 122, 292, AeUiTheme.MUTED_TEXT);
            text(g, tr("compact_network"), 20, 145, 292, AeUiTheme.ACCENT);
            text(g, tr("compact_chunks"), 20, 164, 292, AeUiTheme.MUTED_TEXT);
            drawQuantumInput(g);
            return;
        }
        text(g, tr(menu.formed ? "formed" : "incomplete"), 20, 55, 140,
                menu.formed ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        text(g, tr(menu.networkOnline ? "network_online" : "network_offline"), 172, 55, 140,
                menu.networkOnline ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
        text(g, tr("running_status", overviewStatus()), 20, 72, 292,
                menu.collectionActive && menu.motionMode == 1 ? AeUiTheme.SUCCESS : AeUiTheme.PRIMARY_TEXT);
        text(g, tr("structure_count", menu.correct, SingularityStructure.parts(menu.structureVersion).size()), 20, 90, 292, AeUiTheme.PRIMARY_TEXT);
        AeUiTheme.progress(g, 20, 104, 292, 6,
                (float) menu.correct / Math.max(1, SingularityStructure.parts(menu.structureVersion).size()), AeUiTheme.CYAN);
        text(g, tr("inspection", menu.missing, menu.conflicts, menu.unloaded), 20, 117, 292,
                menu.conflicts > 0 ? AeUiTheme.ERROR : AeUiTheme.MUTED_TEXT);
        if (menu.operation != SingularityBlockEntity.Operation.IDLE) {
            text(g, tr("operation_count", menu.progress, menu.operationTotal), 20, 135, 292, AeUiTheme.MUTED_TEXT);
            AeUiTheme.progress(g, 20, 147, 292, 5,
                    (float) menu.progress / Math.max(1, menu.operationTotal), AeUiTheme.ACCENT);
        }
        Component detail = menu.neededMaterial >= 0 && menu.neededMaterial < SingularityStructure.Type.values().length
                ? tr("waiting_material", SingularityStructure.block(SingularityStructure.Type.values()[menu.neededMaterial]).getName())
                : menu.problem.isEmpty() ? tr("build_hint") : tr("problem", menu.problem);
        text(g, detail, 20, 163, 292, menu.problem.isEmpty() ? AeUiTheme.MUTED_TEXT : AeUiTheme.WARNING);
        boolean migrationAvailable = menu.structureVersion == SingularityStructure.LEGACY_VERSION
                || menu.structureVersion == SingularityStructure.EMBEDDED_VERSION;
        if (migrationAvailable && menu.operation == SingularityBlockEntity.Operation.IDLE) {
            text(g, tr(menu.structureVersion == SingularityStructure.EMBEDDED_VERSION
                    ? "upgrade_available" : "embed_available"), 20, 135, 292, AeUiTheme.MUTED_TEXT);
        }
        drawQuantumInput(g);
    }

    private void drawQuantumInput(GuiGraphics g) {
        text(g, tr("quantum_input"), 50, 181, 254, AeUiTheme.PRIMARY_TEXT);
        text(g, tr("quantum_input_hint"), 50, 192, 254, AeUiTheme.MUTED_TEXT);
        text(g, tr("quantum_state", menu.quantumFrequency, Component.translatable("gui.molecularmanipulator.quantum_state." + quantumStateKey())), 50, 203, 254,
                quantumConnected() ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
    }

    private Component overviewStatus() {
        if (!menu.formed) return tr("status.not_formed");
        if (menu.getMachine().isSingleBlock()) {
            if (!menu.networkOnline) return tr("network_offline");
            if (menu.status == SingularityBlockEntity.Status.STORAGE_FULL) return tr("status.storage_full");
            if (menu.collectionActive) return tr("status.running");
        }
        if (menu.collectionStarting) return tr("collection_starting_status");
        if (menu.collectionActive && menu.motionMode == 1) return tr("status.running");
        if (menu.collectionActive) return tr("status.collection_paused");
        return tr("status." + menu.status.name().toLowerCase(Locale.ROOT));
    }

    private void drawCollection(GuiGraphics g) {
        text(g, tr("collection_title"), 20, 54, 292, AeUiTheme.PRIMARY_TEXT);
        boolean storageFull = menu.status == SingularityBlockEntity.Status.STORAGE_FULL;
        text(g, tr(menu.getMachine().isSingleBlock() && menu.collectionActive && !menu.networkOnline ? "status.collection_paused"
                : menu.collectionStarting ? "collection_starting_status"
                : storageFull ? "status.storage_full"
                : menu.collectionActive ? "collection_running" : "collection_stopped"),
                20, 74, 292, storageFull ? AeUiTheme.ERROR
                        : menu.collectionActive ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        text(g, tr("collection_network", menu.networkOnline ? tr("online") : tr("offline")),
                20, 92, 292, menu.networkOnline ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        text(g, tr("quantum_state", menu.quantumFrequency, quantumStateKey()), 20, 110, 292,
                quantumConnected() ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
        text(g, tr(menu.getMachine().isSingleBlock() ? "compact_collection_hint" : "collection_hint"), 20, 128, 292, AeUiTheme.MUTED_TEXT);

        List<ItemStack> resources = collectionResources();
        text(g, tr("collection_resources", resources.size(), menu.collectionBatchSize),
                20, 143, 292, AeUiTheme.ACCENT);
        int maxScroll = Math.max(0, (resources.size() + 2) / 3 - 4);
        collectionScroll = Math.min(collectionScroll, maxScroll);
        if (resources.isEmpty()) {
            text(g, tr("collection_resources_empty"), 20, 166, 292, AeUiTheme.WARNING);
            return;
        }
        int first = collectionScroll * 3;
        for (int visible = 0; visible < 12; visible++) {
            int index = first + visible;
            if (index >= resources.size()) break;
            int col = visible % 3;
            int row = visible / 3;
            int x = 20 + col * 97;
            int y = 153 + row * 20;
            ItemStack stack = resources.get(index);
            g.renderItem(stack, x, y);
            text(g, stack.getHoverName(), x + 18, y + 4, 77, AeUiTheme.PRIMARY_TEXT);
        }
        if (maxScroll > 0) {
            text(g, tr("collection_scroll", collectionScroll + 1, maxScroll + 1), 236, 227, 76,
                    AeUiTheme.MUTED_TEXT);
        }
    }

    private List<ItemStack> collectionResources() {
        List<ItemStack> result = new ArrayList<>();
        for (String raw : menu.collectionItems.split(",")) {
            if (raw.isBlank()) continue;
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (id == null) continue;
            var item = BuiltInRegistries.ITEM.get(id);
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty()) result.add(stack);
        }
        return result;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (collectionPage) {
            double x = logicalMouseX(mouseX) - leftPos;
            double y = logicalMouseY(mouseY) - topPos;
            if (x >= 14 && x < 318 && y >= 140 && y < 235) {
                int rows = (collectionResources().size() + 2) / 3;
                int maxScroll = Math.max(0, rows - 4);
                if (maxScroll > 0 && scrollY != 0) {
                    collectionScroll = Math.max(0, Math.min(maxScroll,
                            collectionScroll + (scrollY < 0 ? 1 : -1)));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    private void drawDuplication(GuiGraphics g) {
        text(g, tr("duplication_title"), 20, 54, 292, AeUiTheme.PRIMARY_TEXT);
        text(g, tr("duplication_cost", SingularityDuplicationProcessor.MATTER_PER_ITEM), 20, 70, 292, AeUiTheme.WARNING);
        text(g, tr("collection_network", menu.networkOnline ? tr("online") : tr("offline")),
                20, 86, 292, menu.networkOnline ? AeUiTheme.SUCCESS : AeUiTheme.WARNING);
        text(g, tr("duplication_energy", menu.duplicationEnergyPriority), 20, 102, 292, AeUiTheme.ACCENT);
        text(g, tr("duplication_sample"), 20, 124, 130, AeUiTheme.PRIMARY_TEXT);
        text(g, tr("duplication_black_hole"), 164, 124, 130, AeUiTheme.PRIMARY_TEXT);
        text(g, tr("duplication_black_hole_count", menu.blackHoleCount), 164, 174, 130, AeUiTheme.ACCENT);
        text(g, tr("duplication_speed", menu.duplicationMatterPerCycle, menu.duplicationIntervalTicks), 20, 186, 292,
                menu.blackHoleCount > 0 ? AeUiTheme.SUCCESS : AeUiTheme.MUTED_TEXT);
        text(g, tr("duplication_energy_fe", formatEnergy(menu.duplicationFePerCycle),
                formatEnergy(menu.lastDuplicationFeConsumed)), 20, 200, 292, AeUiTheme.ACCENT);
        text(g, tr("duplication_energy_ae", formatEnergy(menu.duplicationAePerCycle),
                formatEnergy(menu.lastDuplicationAeConsumed)), 20, 214, 292, AeUiTheme.ACCENT);
        text(g, tr("duplication_fluid_storage"), 20, 228, 292, AeUiTheme.MUTED_TEXT);
    }

    private static String formatEnergy(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
    private static String formatEnergy(double value) {
        return String.format(Locale.ROOT, "%,.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private boolean quantumConnected() {
        return menu.quantumLinkState == QuantumLinkState.CONNECTED
                || menu.quantumLinkState == QuantumLinkState.CONNECTED_BUILD_ONLY;
    }

    private String quantumStateKey() {
        return menu.quantumLinkState.name().toLowerCase(Locale.ROOT);
    }
}
