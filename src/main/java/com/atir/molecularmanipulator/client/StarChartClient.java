package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.building.BuildingPackage;
import com.atir.molecularmanipulator.network.BuildingPayloads;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** In-world survey geometry, actual conflict locations and a compact reusable-tool control panel. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class StarChartClient {
    private static final List<BuildingPayloads.Placement> PROJECTION = new ArrayList<>();
    private static UUID request;
    private static int received, expected, conflicts;
    private static long keepUntil;
    private static String error = "";
    private StarChartClient() {}
    private static Component text(String key, Object... args) { return Component.translatable("star_chart.molecularmanipulator." + key, args); }
    public static void receive(BuildingPayloads.Preview preview) {
        if (!preview.request().equals(request)) return;
        if (preview.page() == 0) { PROJECTION.clear(); received = 0; }
        PROJECTION.addAll(preview.placements()); received++; expected = preview.totalPages(); conflicts = preview.conflicts(); error = preview.error();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || PROJECTION.isEmpty()) return;
        if (!(mc.screen instanceof ChartScreen) && mc.level.getGameTime() > keepUntil) { PROJECTION.clear(); return; }
        var poses = event.getPoseStack(); var camera = event.getCamera().getPosition();
        poses.pushPose(); poses.translate(-camera.x, -camera.y, -camera.z);
        var buffers = mc.renderBuffers().bufferSource(); var out = buffers.getBuffer(RenderType.lines());
        for (var placement : PROJECTION) {
            var state = Block.stateById(placement.state());
            if (state.isAir() && !placement.conflict()) continue;
            var pos = placement.position();
            float r = placement.conflict() ? 1F : .55F, g = placement.conflict() ? .25F : .82F, b = placement.conflict() ? .20F : 1F;
            LevelRenderer.renderLineBox(poses, out, new AABB(pos).deflate(.015), r, g, b, .42F);
        }
        if (!PROJECTION.isEmpty()) {
            AABB bounds = new AABB(PROJECTION.getFirst().position());
            for (var placement : PROJECTION) bounds = bounds.minmax(new AABB(placement.position()));
            LevelRenderer.renderLineBox(poses, out, bounds.inflate(.06), .95F, .82F, .50F, .9F);
        }
        buffers.endBatch(RenderType.lines()); poses.popPose();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { PROJECTION.clear(); request = null; }
    @EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> BuildingPayloads.handlers(open -> Minecraft.getInstance().setScreen(new ChartScreen(open)), StarChartClient::receive,
                    details -> { if (Minecraft.getInstance().screen instanceof DetailsScreen screen) screen.receive(details); }));
        }
    }
    public static final class ChartScreen extends Screen {
        private final BuildingPayloads.Open open;
        private BlockPos base;
        private Direction facing;
        private final List<Integer> slots = new ArrayList<>();
        private int selected;
        private Button deploy, details;
        private int panelX, panelY;
        ChartScreen(BuildingPayloads.Open open) {
            super(text("title")); this.open = open; base = open.base();
            facing = Minecraft.getInstance().player.getDirection().getOpposite();
            var inventory = Minecraft.getInstance().player.getInventory();
            for (int i = 0; i < inventory.items.size(); i++) if (BuildingPackage.type(inventory.getItem(i)) != null) slots.add(i);
        }
        @Override protected void init() {
            panelX = Math.max(6, width - 274); panelY = Math.max(6, height - 222);
            int x = panelX + 10, y = panelY + 41;
            if (open.seal()) {
                addRenderableWidget(Button.builder(text("seal"), button -> {
                    PacketDistributor.sendToServer(new BuildingPayloads.Action(UUID.randomUUID(), BuildingPayloads.SEAL, 0, base, facing, open.target()));
                    onClose();
                }).bounds(x, y + 80, 244, 20).build());
            } else {
                addRenderableWidget(Button.builder(text("next_package"), button -> {
                    if (!slots.isEmpty()) selected = (selected + 1) % slots.size(); survey();
                }).bounds(x, y, 119, 20).build());
                addRenderableWidget(Button.builder(text("rotate"), button -> { facing = facing.getClockWise(); survey(); }).bounds(x + 125, y, 119, 20).build());
                for (int axis = 0; axis < 3; axis++) for (int sign : new int[]{-1, 1}) {
                    final int a = axis, s = sign;
                    addRenderableWidget(Button.builder(Component.literal((axis == 0 ? "X " : axis == 1 ? "Y " : "Z ") + (sign < 0 ? "−" : "+")), button -> {
                        int step = hasShiftDown() ? 8 : 1;
                        base = base.offset(a == 0 ? s * step : 0, a == 1 ? s * step : 0, a == 2 ? s * step : 0); survey();
                    }).bounds(x + axis * 83 + (sign > 0 ? 40 : 0), y + 27, 37, 20).build());
                }
                details = addRenderableWidget(Button.builder(text("details"), button -> {
                    if (!slots.isEmpty()) minecraft.setScreen(new DetailsScreen(this, slots.get(selected)));
                }).bounds(x, y + 121, 76, 20).build());
                deploy = addRenderableWidget(Button.builder(text("deploy"), button -> {
                    if (slots.isEmpty()) return;
                    PacketDistributor.sendToServer(new BuildingPayloads.Action(request, BuildingPayloads.DEPLOY, slots.get(selected), base, facing, open.target()));
                    keepUntil = minecraft.level.getGameTime() + 240; minecraft.setScreen(null);
                }).bounds(x + 82, y + 121, 162, 20).build());
                survey();
            }
            addRenderableWidget(Button.builder(text("cancel"), button -> onClose()).bounds(x, panelY + 190, 244, 20).build());
        }
        private void survey() {
            PROJECTION.clear(); conflicts = 0; received = expected = 0; error = ""; request = UUID.randomUUID();
            if (slots.isEmpty()) return;
            PacketDistributor.sendToServer(new BuildingPayloads.Action(request, BuildingPayloads.PREVIEW, slots.get(selected), base, facing, open.target()));
        }
        @Override public void tick() {
            if (deploy != null) deploy.active = !slots.isEmpty() && expected > 0 && received == expected && conflicts == 0 && error.isEmpty();
            if (details != null) details.active = !slots.isEmpty() && BuildingPackage.archive(minecraft.player.getInventory().getItem(slots.get(selected))) != null;
        }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(panelX, panelY, panelX + 264, panelY + 218, 0xED202632);
            graphics.fill(panelX, panelY, panelX + 264, panelY + 2, 0xFFCFBA83);
            graphics.drawString(font, title, panelX + 10, panelY + 11, 0xFFF0D8A4);
            int x = panelX + 10;
            if (open.seal()) {
                graphics.drawWordWrap(font, text("seal_contents"), x, panelY + 42, 244, 0xE5EDF1);
            } else {
                var packageName = slots.isEmpty() ? text("no_package") : minecraft.player.getInventory().getItem(slots.get(selected)).getHoverName();
                graphics.drawString(font, packageName, x, panelY + 28, 0xEDF4FA);
                graphics.drawString(font, text("position", base.getX(), base.getY(), base.getZ()), x, panelY + 99, 0xC6DBEA);
                graphics.drawString(font, text("facing", text("direction_" + facing.getName())), x, panelY + 112, 0xC6DBEA);
                Component status = !error.isEmpty() ? text("unavailable") : expected == 0 || received < expected ? text("surveying")
                        : text("conflicts", conflicts, PROJECTION.size());
                graphics.drawString(font, status, x, panelY + 127, conflicts > 0 || !error.isEmpty() ? 0xFF8D7F : 0xAEE1EA);
                graphics.drawString(font, text("shift_step"), x, panelY + 143, 0x9DAFC1);
            }
            super.render(graphics, mouseX, mouseY, partial);
        }
        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {}
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { PROJECTION.clear(); keepUntil = 0; super.onClose(); }
    }
    public static final class DetailsScreen extends Screen {
        private final ChartScreen parent;
        private final int slot;
        private UUID request;
        private BuildingPayloads.Details data;
        private int x, y, panelWidth;
        DetailsScreen(ChartScreen parent, int slot) { super(text("details")); this.parent = parent; this.slot = slot; }
        @Override protected void init() {
            panelWidth = Math.min(420, width - 16); x = (width - panelWidth) / 2; y = Math.max(4, (height - 284) / 2);
            addRenderableWidget(Button.builder(Component.literal("←"), button -> query(data == null ? 0 : data.page() - 1)).bounds(x + 8, y + 254, 40, 20).build());
            addRenderableWidget(Button.builder(Component.literal("→"), button -> query(data == null ? 0 : data.page() + 1)).bounds(x + 54, y + 254, 40, 20).build());
            addRenderableWidget(Button.builder(text("back"), button -> onClose()).bounds(x + panelWidth - 98, y + 254, 90, 20).build());
            query(0);
        }
        private void query(int page) { request = UUID.randomUUID(); PacketDistributor.sendToServer(new BuildingPayloads.DetailsQuery(request, slot, page)); }
        private void receive(BuildingPayloads.Details details) { if (details.request().equals(request)) data = details; }
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            graphics.fill(x, y, x + panelWidth, y + 282, 0xF4202632);
            graphics.drawString(font, title, x + 8, y + 9, 0xFFF0D8A4);
            if (data == null || data.unavailable()) graphics.drawString(font, text(data == null ? "surveying" : "unavailable"), x + 8, y + 27, 0xE5EDF1);
            else {
                graphics.drawString(font, text("archive_summary", data.blocks(), String.format(Locale.ROOT, "%.2f", data.nativeBytes() / 1048576D)), x + 8, y + 24, 0xB7D7E7);
                int row = y + 40;
                for (var entry : data.entries()) {
                    var name = Block.stateById(entry.state()).getBlock().getName();
                    graphics.drawString(font, font.split(name, panelWidth - 114).getFirst(), x + 8, row, 0xE5EDF1);
                    var bytes = Component.literal(String.format(Locale.ROOT, "%.1f KiB", entry.nativeBytes() / 1024D));
                    graphics.drawString(font, bytes, x + panelWidth - 8 - font.width(bytes), row, 0xB7D7E7);
                    var pos = entry.offset();
                    graphics.drawString(font, Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), x + 12, row + 9, 0x91A7BB);
                    row += 18;
                }
                graphics.drawCenteredString(font, (data.page() + 1) + " / " + data.pages(), x + panelWidth / 2, y + 260, 0xE5EDF1);
            }
            super.render(graphics, mouseX, mouseY, partial);
        }
        @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partial) {}
        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { minecraft.setScreen(parent); }
    }
}
