package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.building.BuildingPackage;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.network.BuildingPayloads;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** World-space survey geometry and OSQ-skinned controls for the reusable calibrator. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class StarChartClient {
    private static final String KEY = "star_chart.molecularmanipulator.";
    private static final List<BuildingPayloads.Placement> PROJECTION = new ArrayList<>();
    private static UUID request;
    private static int received, expected, conflicts;
    private static String error = "";
    private static Component siteSummary = Component.empty(), siteDetail = Component.empty();
    private static final StarChartProjection GEOMETRY = new StarChartProjection();
    private static final SectionedGhostPreviewRenderer CACHE = new SectionedGhostPreviewRenderer(100);
    private static ClientLevel surveyLevel;
    private static ChartScreen surveyScreen;
    private static boolean geometryDirty, deploying, awaitingDeployment;

    private StarChartClient() {}

    private static Component text(String key, Object... args) { return Component.translatable(KEY + key, args); }

    public static void receive(BuildingPayloads.Preview preview) {
        if (!preview.request().equals(request)) return;
        if (preview.page() == 0) { PROJECTION.clear(); received = 0; }
        PROJECTION.addAll(preview.placements()); received++; expected = preview.totalPages(); conflicts = preview.conflicts(); error = preview.error();
        siteSummary = preview.summary(); siteDetail = preview.detail();
        geometryDirty = received == expected;
    }

    private static void deployed(BuildingPayloads.Deployment result) {
        if (!result.request().equals(request)) return;
        awaitingDeployment = false;
        deploying = result.accepted();
        if (surveyScreen != null) {
            if (deploying) surveyScreen.transferState = "DEPLOYING";
            else Minecraft.getInstance().setScreen(surveyScreen);
        }
    }

    private static void cancelled(BuildingPayloads.DeploymentCancelled result) {
        // Withdrawal removes only construction ghosts, never the pinned point or its panel.
        if (surveyLevel != null && surveyLevel.dimension().location().equals(result.dimension())
                && PROJECTION.stream().anyMatch(entry -> entry.position().equals(result.controller()))) {
            clearProjection(); deploying = false;
            if (surveyScreen != null) surveyScreen.transferState = "ROLLING_BACK";
        }
    }

    static ChartScreen retainedPanel(ClientLevel level) {
        return surveyLevel == level ? surveyScreen : null;
    }
    private static void open(BuildingPayloads.Open candidate) {
        var mc = Minecraft.getInstance();
        var retained = retainedPanel(mc.level);
        mc.setScreen(retained != null ? retained : new ChartScreen(candidate));
    }
    private static void transferStatus(BuildingPayloads.TransferStatus status) {
        if (surveyScreen != null) surveyScreen.receive(status);
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (surveyLevel != null && surveyLevel != mc.level) clear();
        if (mc.level == null || surveyLevel != mc.level) return;
        if (surveyScreen != null) surveyScreen.pollTransfer();
        if (geometryDirty) {
            GEOMETRY.replace(PROJECTION);
            CACHE.update(GEOMETRY.blocks());
            geometryDirty = false;
        }
        if (deploying && GEOMETRY.advance((pos, state) -> mc.level.hasChunkAt(pos)
                && mc.level.getBlockState(pos).is(state.getBlock()))) {
            CACHE.update(GEOMETRY.blocks());
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        var bounds = GEOMETRY.bounds();
        if (mc.level != surveyLevel || bounds == null || CACHE.isEmpty()
                || bounds.distanceToSqr(event.getCamera().getPosition()) > 256D * 256D) return;
        CACHE.render(event);
        // Only one cached footprint box, rather than twelve new edges for every block every frame.
        if (!deploying && event.getFrustum().isVisible(bounds)) {
            var poses = event.getPoseStack(); var camera = event.getCamera().getPosition();
            poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
            var buffers = mc.renderBuffers().bufferSource();
            LevelRenderer.renderLineBox(poses, buffers.getBuffer(RenderType.lines()), bounds, .95F, .82F, .50F, .9F);
            buffers.endBatch(RenderType.lines()); poses.popPose();
        }
    }

    static void onResourceReload() {
        CACHE.close();
        // Rebuild GPU resources from the remaining ghosts, not the original survey: completed blocks stay gone.
        CACHE.update(GEOMETRY.blocks());
    }

    private static void clearProjection() {
        PROJECTION.clear(); GEOMETRY.clear(); CACHE.close(); geometryDirty = false;
    }
    private static void clear() {
        clearProjection();
        siteSummary = siteDetail = Component.empty();
        request = null; surveyLevel = null; surveyScreen = null;
        received = expected = conflicts = 0; error = ""; geometryDirty = deploying = awaitingDeployment = false;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> BuildingPayloads.handlers(StarChartClient::open, StarChartClient::receive,
                    details -> { if (Minecraft.getInstance().screen instanceof DetailsScreen screen) screen.receive(details); },
                    StarChartClient::deployed, StarChartClient::cancelled, StarChartClient::transferStatus));
        }
    }

    /** Plain tool screens have no menu/slots, but use the same logical GUI units as the machines. */
    abstract static class PanelScreen extends Screen {
        private final String layoutName;
        private final boolean docked;
        OsqUi ui;

        PanelScreen(Component title, String layoutName, boolean docked) {
            super(title);
            this.layoutName = layoutName;
            this.docked = docked;
        }

        @Override protected void init() { ui = new OsqUi(layoutName); }

        final Button button(String id, Supplier<Component> label, Runnable action) {
            return addRenderableWidget(ui.button(id, 0, 0, label, action));
        }

        final void bind(String id, Supplier<Component> value) {
            var entry = ui.layout.find(id);
            ui.at(entry.x, entry.baseline, value);
        }

        final int color(String name) { return OsqLayout.argb(ui.layout.find("color." + name).color); }

        final float panelScale() {
            return Math.min(1, Math.min(Math.max(1, width - 8f) / ui.layout.width,
                    Math.max(1, height - 8f) / ui.layout.height));
        }

        final float panelX() {
            float spare = width - ui.layout.width * panelScale();
            return docked ? Math.max(4, spare - 6) : spare / 2;
        }

        final float panelY() {
            float spare = height - ui.layout.height * panelScale();
            return docked ? Math.max(4, spare - 6) : spare / 2;
        }

        final double logicalX(double x) { return (x - panelX()) / panelScale(); }
        final double logicalY(double y) { return (y - panelY()) / panelScale(); }

        @Override public final void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
            int x = (int)Math.floor(logicalX(mouseX)), y = (int)Math.floor(logicalY(mouseY));
            g.pose().pushPose();
            try {
                g.pose().translate(panelX(), panelY(), 0);
                g.pose().scale(panelScale(), panelScale(), 1);
                ui.background(g, font, 0, 0);
                drawItems(g);
                // Finish item depth/buffer changes before the shared layout paints labels.
                g.flush();
                ui.foreground(g, font);
                drawContents(g, x, y);
                super.render(g, x, y, partial);
            } finally {
                g.pose().popPose();
            }
        }

        void drawItems(GuiGraphics g) {}
        void drawContents(GuiGraphics g, int mouseX, int mouseY) {}

        final void tooltip(Component value) {
            setTooltipForNextRenderPass(font.split(value, Math.min(300, Math.max(80, width - 16))),
                    DefaultTooltipPositioner.INSTANCE, true);
        }

        @Override public void setTooltipForNextRenderPass(List<FormattedCharSequence> tooltip,
                ClientTooltipPositioner positioner, boolean override) {
            // Screen renders deferred tooltips outside the fitted pose, at the raw mouse position.
            super.setTooltipForNextRenderPass(tooltip, DefaultTooltipPositioner.INSTANCE, override);
        }

        @Override public void mouseMoved(double x, double y) {
            for (var child : children()) child.mouseMoved(logicalX(x), logicalY(y));
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            return super.mouseClicked(logicalX(x), logicalY(y), button);
        }
        @Override public boolean mouseReleased(double x, double y, int button) {
            return super.mouseReleased(logicalX(x), logicalY(y), button);
        }
        @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            return super.mouseDragged(logicalX(x), logicalY(y), button, dx / panelScale(), dy / panelScale());
        }
        @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
            return super.mouseScrolled(logicalX(x), logicalY(y), dx, dy);
        }
        @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}
        @Override public boolean isPauseScreen() { return false; }
    }

    public static final class ChartScreen extends PanelScreen {
        private final BuildingPayloads.Open open;
        private BlockPos base;
        private Direction facing;
        private final List<Integer> slots = new ArrayList<>();
        private final List<Button> placementButtons = new ArrayList<>();
        private int selected;
        private Button deploy, details, nextPackage, interrupt;
        private UUID transferRequest, transferId;
        private String transferState = "";
        private boolean awaitingInterruption;
        private int statusTicks;

        ChartScreen(BuildingPayloads.Open open) {
            super(text("title"), open.seal() ? "star_chart_seal" : "star_chart_calibrator", true);
            clear();
            this.open = open;
            if (!open.seal()) { surveyScreen = this; surveyLevel = Minecraft.getInstance().level; }
            base = open.base();
            facing = Minecraft.getInstance().player.getDirection().getOpposite();
        }

        @Override protected void init() {
            super.init();
            placementButtons.clear();
            syncSlots();
            if (open.seal()) {
                bind("target.name", () -> minecraft.level.getBlockState(open.target()).getBlock().getName());
                bind("target.position", () -> text("position", open.target().getX(), open.target().getY(), open.target().getZ()));
                var seal = button("seal", () -> text("seal"), () -> {
                    PacketDistributor.sendToServer(new BuildingPayloads.Action(UUID.randomUUID(), BuildingPayloads.SEAL, 0, base, facing, open.target()));
                    onClose();
                });
                ui.state(seal, () -> "danger");
            } else {
                nextPackage = button("next_package", () -> text("next_package"), () -> {
                    if (!slots.isEmpty()) selected = (selected + 1) % slots.size();
                    transferId = null; transferState = "";
                    queryTransfer(false);
                    survey();
                    refresh();
                });
                placementButtons.add(button("rotate", () -> text("rotate"), () -> {
                    facing = facing.getClockWise();
                    survey();
                    refresh();
                }));
                for (int axis = 0; axis < 3; axis++) {
                    final int a = axis;
                    String name = "xyz".substring(axis, axis + 1);
                    bind("axis." + name, () -> Component.literal(name.toUpperCase(Locale.ROOT) + "  " + (a == 0 ? base.getX() : a == 1 ? base.getY() : base.getZ())));
                    for (int sign : new int[]{-1, 1}) {
                        var move = button(name + (sign < 0 ? ".minus" : ".plus"),
                                () -> Component.literal(sign < 0 ? "−" : "+"), () -> {
                                    int step = (hasShiftDown() ? 8 : 1) * sign;
                                    base = base.offset(a == 0 ? step : 0, a == 1 ? step : 0, a == 2 ? step : 0);
                                    survey();
                                    refresh();
                                });
                        move.setTooltip(Tooltip.create(Component.literal(name.toUpperCase(Locale.ROOT) + (sign < 0 ? " −\n" : " +\n"))
                                .append(text("shift_step"))));
                        placementButtons.add(move);
                    }
                }
                details = button("details", () -> text("details"), () -> {
                    if (!slots.isEmpty()) minecraft.setScreen(new DetailsScreen(this, slots.get(selected)));
                });
                deploy = button("deploy", () -> text("deploy"), () -> {
                    if (!canDeploy()) return;
                    PacketDistributor.sendToServer(new BuildingPayloads.Action(request, BuildingPayloads.DEPLOY, slots.get(selected), base, facing, open.target()));
                    // Wait for the server reservation before hiding any newly built positions.
                    transferId = null; transferState = "";
                    awaitingDeployment = true;
                    minecraft.setScreen(null);
                });
                ui.state(deploy, () -> "confirm");
                interrupt = button("interrupt", () -> text("interrupt_recover"), () -> {
                    if (!canInterrupt()) return;
                    awaitingInterruption = true;
                    queryTransfer(true);
                    refresh();
                });
                interrupt.setTooltip(Tooltip.create(text("interrupt_help")));
                ui.state(interrupt, () -> "danger");
                bind("package.name", () -> slots.isEmpty() ? text("no_package") : packageStack().getHoverName());
                bind("package.count", () -> Component.literal(slots.isEmpty() ? "0 / 0" : (selected + 1) + " / " + slots.size()));
                bind("facing", () -> text("facing", text("direction_" + facing.getName())));
                bind("status", this::status);
                ui.color(KEY + "surveying", () -> color(!error.isEmpty() || conflicts > 0 ? "error" : canDeploy() ? "accent" : "warning"));
                ui.sprite(18, 255, () -> ui.sprite("dot_" + (!error.isEmpty() || conflicts > 0 ? "err" : canDeploy() ? "ok" : "warn")));
                ui.progress(18, 282, () -> expected == 0 ? 0 : (double)received / expected);
                if (!transferActive() && !awaitingDeployment && BuildingPackage.reservation(packageStack()) == null) survey();
                queryTransfer(false);
                refresh();
            }
            var cancel = button("cancel", () -> text("cancel"), () -> { clear(); minecraft.setScreen(null); });
            cancel.setTooltip(Tooltip.create(text("cancel_help")));
        }

        private ItemStack packageStack() {
            return slots.isEmpty() ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(slots.get(selected));
        }

        private boolean canDeploy() {
            return !slots.isEmpty() && !awaitingDeployment && !transferActive() && expected > 0 && received == expected && conflicts == 0 && error.isEmpty();
        }
        private boolean transferActive() {
            return "DEPLOYING".equals(transferState) || "BLOCKED".equals(transferState) || "PAUSED".equals(transferState) || "ROLLING_BACK".equals(transferState);
        }
        private boolean canInterrupt() {
            return transferId != null && transferActive() && !"ROLLING_BACK".equals(transferState) && !awaitingInterruption;
        }
        private void syncSlots() {
            int previous = slots.isEmpty() ? -1 : slots.get(Math.min(selected, slots.size() - 1));
            slots.clear();
            var inventory = minecraft.player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) if (BuildingPackage.type(inventory.getItem(i)) != null) slots.add(i);
            selected = Math.max(0, slots.indexOf(previous));
            if (previous < 0) for (int i = 0; i < slots.size(); i++)
                if (BuildingPackage.reservation(inventory.getItem(slots.get(i))) != null) { selected = i; break; }
        }
        private void queryTransfer(boolean interrupt) {
            transferRequest = UUID.randomUUID(); statusTicks = 0;
            PacketDistributor.sendToServer(new BuildingPayloads.TransferControl(transferRequest, transferId,
                    slots.isEmpty() ? -1 : slots.get(selected), open.target(), interrupt));
        }
        private void pollTransfer() {
            if (!open.seal() && (transferActive() || awaitingDeployment) && transferRequest == null
                    && !awaitingInterruption && ++statusTicks >= 20) queryTransfer(false);
        }
        private void receive(BuildingPayloads.TransferStatus status) {
            if (!status.request().equals(transferRequest)) return;
            transferRequest = null;
            boolean wasActive = transferActive();
            transferId = status.operation(); transferState = status.state(); awaitingInterruption = false;
            if (transferActive()) {
                if ("ROLLING_BACK".equals(transferState)) { clearProjection(); deploying = false; }
            } else if (wasActive) {
                clearProjection(); deploying = false; awaitingDeployment = false;
                syncSlots(); survey();
            }
            if (interrupt != null) refresh();
        }

        private Component status() {
            if (awaitingInterruption) return text("interrupt_pending");
            if (transferActive()) return text("transfer_" + transferState.toLowerCase(Locale.ROOT));
            if (awaitingDeployment) return text("deploy_pending");
            if ("UNAVAILABLE".equals(transferState)) return text("unavailable");
            if (slots.isEmpty()) return text("no_package");
            if (!error.isEmpty()) return text("unavailable");
            if (expected == 0 || received < expected) return text("surveying");
            return siteSummary.getString().isEmpty() ? text("conflicts", conflicts, PROJECTION.size()) : siteSummary;
        }

        private void survey() {
            PROJECTION.clear(); GEOMETRY.clear(); CACHE.close();
            conflicts = 0; received = expected = 0; error = ""; geometryDirty = false; request = UUID.randomUUID();
            siteSummary = siteDetail = Component.empty();
            if (slots.isEmpty()) return;
            PacketDistributor.sendToServer(new BuildingPayloads.Action(request, BuildingPayloads.PREVIEW, slots.get(selected), base, facing, open.target()));
        }

        private void refresh() {
            if (deploy == null) return;
            deploy.active = canDeploy();
            details.active = !slots.isEmpty() && BuildingPackage.archive(packageStack()) != null;
            boolean idle = !transferActive() && !awaitingDeployment && !awaitingInterruption;
            nextPackage.active = idle && slots.size() > 1;
            for (var button : placementButtons) button.active = idle && !slots.isEmpty();
            interrupt.active = canInterrupt();
        }

        @Override public void tick() {
            if (!open.seal() && !slots.isEmpty() && BuildingPackage.type(packageStack()) == null) {
                syncSlots();
                if (!transferActive() && !awaitingDeployment) survey();
            }
            refresh();
        }

        @Override void drawItems(GuiGraphics g) {
            if (!open.seal() && !slots.isEmpty()) g.renderItem(packageStack(), 18, 71);
        }

        @Override void drawContents(GuiGraphics g, int mouseX, int mouseY) {
            if (!open.seal() && mouseX >= 8 && mouseX < 304 && mouseY >= 244 && mouseY < 292) {
                tooltip(!error.isEmpty() ? Component.literal(error) : transferActive() ? text("interrupt_help") : siteDetail);
            } else if (!open.seal() && mouseX >= 18 && mouseX < 294 && mouseY >= 67 && mouseY < 91 && !slots.isEmpty()) {
                tooltip(packageStack().getHoverName());
            } else if (open.seal() && mouseX >= 18 && mouseX < 294 && mouseY >= 69 && mouseY < 88) {
                tooltip(minecraft.level.getBlockState(open.target()).getBlock().getName());
            }
        }

        @Override public void onClose() {
            if (open.seal()) clear();
            // A real screen-less world view restores vanilla mouse look, walking and flying.
            minecraft.setScreen(null);
        }
    }

    public static final class DetailsScreen extends PanelScreen {
        private final ChartScreen parent;
        private final int slot;
        private UUID request;
        private BuildingPayloads.Details data;
        private boolean pending;
        private Button previous, next;

        DetailsScreen(ChartScreen parent, int slot) {
            super(text("details"), "star_chart_details", false);
            this.parent = parent;
            this.slot = slot;
        }

        @Override protected void init() {
            super.init();
            previous = button("prev", () -> Component.literal("←"), () -> query(data.page() - 1));
            next = button("next", () -> Component.literal("→"), () -> query(data.page() + 1));
            button("back", () -> text("back"), this::onClose);
            bind("archive.summary", () -> data == null ? text("surveying") : data.unavailable() ? text("unavailable")
                    : text("archive_summary", data.blocks(), String.format(Locale.ROOT, "%.2f", data.nativeBytes() / 1048576D)));
            bind("page", () -> Component.literal(data == null || data.unavailable() ? "— / —" : (data.page() + 1) + " / " + data.pages()));
            query(data == null ? 0 : data.page());
        }

        private void query(int page) {
            pending = true;
            refresh();
            request = UUID.randomUUID();
            PacketDistributor.sendToServer(new BuildingPayloads.DetailsQuery(request, slot, page));
        }

        private void receive(BuildingPayloads.Details details) {
            if (!details.request().equals(request)) return;
            data = details;
            pending = false;
            refresh();
        }

        private void refresh() {
            boolean available = !pending && data != null && !data.unavailable();
            previous.active = available && data.page() > 0;
            next.active = available && data.page() + 1 < data.pages();
        }

        @Override void drawItems(GuiGraphics g) {
            if (data == null || data.unavailable()) return;
            for (int i = 0; i < data.entries().size(); i++) {
                var row = ui.layout.find("row." + i);
                var item = new ItemStack(Block.stateById(data.entries().get(i).state()).getBlock());
                if (!item.isEmpty()) g.renderItem(item, Math.round(row.x) + 4, Math.round(row.y) + 2);
            }
        }

        @Override void drawContents(GuiGraphics g, int mouseX, int mouseY) {
            if (data == null || data.unavailable() || data.entries().isEmpty()) {
                ui.centeredText(g, text(data == null ? "surveying" : data.unavailable() ? "unavailable" : "details_empty"),
                        18, 210, 364, color("muted"));
                return;
            }
            for (int i = 0; i < data.entries().size(); i++) {
                var entry = data.entries().get(i);
                var row = ui.layout.find("row." + i);
                var name = Block.stateById(entry.state()).getBlock().getName();
                var pos = entry.offset();
                var offset = Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                ui.centeredText(g, name, row.x + 26, row.y + 7, 244, color("ink2"));
                ui.centeredText(g, offset, row.x + 26, row.y + 17, 244, color("muted"));
                var bytes = Component.literal(String.format(Locale.ROOT, "%.1f KiB", entry.nativeBytes() / 1024D));
                var label = new OsqLayout.Entry();
                label.x = 300; label.centerY = row.y + 11; label.w = 78; label.size = 7.5f;
                label.fontId = "minecraft:default";
                label.color = ui.layout.find("color.muted").color; label.alignment = "right";
                ui.layout.renderSingleLine(g, font, label, bytes, 0, 0);
                if (mouseX >= row.x && mouseX < row.x + row.w && mouseY >= row.y && mouseY < row.y + row.h) {
                    tooltip(name.copy().append("\n").append(offset).append(" · ").append(bytes));
                }
            }
        }

        @Override public void onClose() { minecraft.setScreen(parent); }
    }
}
