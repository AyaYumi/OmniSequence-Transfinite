package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.client.OmniUiTheme;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** One bounded 3D viewport and one input path shared by all three JEI structures. */
abstract class InteractiveStructurePreviewWidget implements IRecipeWidget, IJeiGuiEventListener {
    static final int WIDTH = 248;
    private final int height;
    final ScreenRectangle view;
    private static final int[] BUTTON_LEFT = {5, 25, 102, 127, 147, 172};
    private static final int[] BUTTON_WIDTH = {18, 75, 18, 18, 18, 71};
    final StructurePreviewCamera camera = new StructurePreviewCamera();
    protected int layer = -1;
    private final List<RenderPart> overview;
    private final Map<Integer, List<RenderPart>> layers;
    private final int minY;
    private final int layerCount;
    private final double centerX, centerY, centerZ;
    private final int width, structureHeight, depth;
    private int dragButton = -1;
    private int pressedButton = -1;

    protected InteractiveStructurePreviewWidget(List<RenderPart> all, List<RenderPart> overview,
            int minY, int layerCount, int height) {
        this.height = height;
        view = new ScreenRectangle(5, 26, WIDTH - 10, height - 43);
        this.overview = overview;
        this.minY = minY;
        this.layerCount = layerCount;
        layers = all.stream().collect(Collectors.groupingBy(RenderPart::y));
        int minX = all.stream().mapToInt(RenderPart::x).min().orElse(0);
        int maxX = all.stream().mapToInt(RenderPart::x).max().orElse(0);
        int minZ = all.stream().mapToInt(RenderPart::z).min().orElse(0);
        int maxZ = all.stream().mapToInt(RenderPart::z).max().orElse(0);
        width = maxX - minX + 1;
        depth = maxZ - minZ + 1;
        structureHeight = layerCount;
        centerX = (minX + maxX + 1) / 2.0;
        centerY = minY + layerCount / 2.0;
        centerZ = (minZ + maxZ + 1) / 2.0;
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, 0, WIDTH, height); }

    @Override
    public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        OmniUiTheme.panel(graphics, 0, 0, WIDTH, height);
        OmniUiTheme.insetPanel(graphics, view.left() - 1, view.top() - 1, view.right() + 1, view.bottom() + 1);
        drawStructure(graphics);
        Component layerText = Component.translatable(layer < 0
                ? "gui.molecularmanipulator.jei_all_layers" : "gui.molecularmanipulator.jei_selected_layer",
                layer + 1, layerCount);
        Component[] labels = {Component.literal("<"), layerText, Component.literal(">"),
                Component.literal("-"), Component.literal("+"), Component.translatable("gui.molecularmanipulator.jei_reset_view")};
        for (int i = 0; i < labels.length; i++) {
            int left = BUTTON_LEFT[i], right = left + BUTTON_WIDTH[i];
            OmniUiTheme.buttonFace(graphics, left, 4, right, 22, buttonAt(mouseX, mouseY) == i);
            text(graphics, labels[i], left + 2, 9, BUTTON_WIDTH[i] - 4, OmniUiTheme.PRIMARY_TEXT, true);
        }
        // Text is emitted once, without the vanilla drop shadow, after the 3D pass.
        OmniUiTheme.insetPanel(graphics, 7, 28, 99, 42);
        text(graphics, Component.literal(dimensions()),
                10, 31, 100, OmniUiTheme.PRIMARY_TEXT, false);
        text(graphics, Component.translatable("gui.molecularmanipulator.jei_preview_controls"),
                6, height - 12, WIDTH - 12, OmniUiTheme.PRIMARY_TEXT, true);
    }

    private void drawStructure(GuiGraphics graphics) {
        var minecraft = Minecraft.getInstance();
        var pose = graphics.pose();
        var clip = StructurePreviewCamera.screenBounds(pose.last().pose(), view);
        graphics.flush();
        graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        pose.pushPose();
        try {
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Lighting.setupFor3DItems();
            float scale = StructurePreviewCamera.fitScale(width, layer < 0 ? structureHeight : 1, depth,
                    view.width(), view.height()) * (float) camera.zoom;
            pose.translate((view.left() + view.right()) / 2.0 + camera.panX,
                    (view.top() + view.bottom()) / 2.0 + camera.panY, 180);
            // Keep GUI depth bounded even at maximum magnification; x/y retain the full zoom.
            pose.scale(scale, -scale, 1);
            pose.mulPose(Axis.XP.rotationDegrees((float) camera.pitch));
            pose.mulPose(Axis.YP.rotationDegrees((float) camera.yaw));
            pose.translate(-centerX, -(layer < 0 ? centerY : minY + layer + 0.5), -centerZ);
            var dispatcher = minecraft.getBlockRenderer();
            var buffer = minecraft.renderBuffers().bufferSource();
            var connections = connectedData();
            for (var part : layer < 0 ? overview : layers.getOrDefault(minY + layer, List.of())) {
                pose.pushPose();
                pose.translate(part.x(), part.y(), part.z());
                dispatcher.renderSingleBlock(part.state(), pose, buffer, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, connections.getOrDefault(part.position(), ModelData.EMPTY), null);
                pose.popPose();
            }
            // GuiGraphics.flush() disables depth: finish the block batch directly instead.
            buffer.endBatch();
        } finally {
            pose.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            graphics.disableScissor();
            Lighting.setupForFlatItems();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest();
        }
    }

    protected Map<BlockPos, ModelData> connectedData() { return Map.of(); }
    protected String dimensions() { return width + " × " + depth + " × " + structureHeight; }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        dragButton = pressedButton = -1;
        if (button == 0 && buttonAt(x, y) >= 0) {
            pressedButton = buttonAt(x, y);
            return true;
        }
        if (insideView(x, y) && button >= 0 && button <= 2) {
            dragButton = button;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = dragButton == button || (button == 0 && pressedButton >= 0);
        if (button == 0 && pressedButton >= 0 && buttonAt(x, y) == pressedButton) {
            switch (pressedButton) {
                case 0 -> changeLayer(-1);
                case 1 -> layer = -1;
                case 2 -> changeLayer(1);
                case 3 -> camera.zoomAt(-1, 0, 0);
                case 4 -> camera.zoomAt(1, 0, 0);
                case 5 -> camera.reset();
            }
        }
        dragButton = pressedButton = -1;
        return handled;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragButton != button) return false;
        if (button == 0) camera.rotate(dx, dy);
        else camera.pan(dx, dy);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollY) {
        if (!insideView(x, y) || scrollY == 0) return false;
        camera.zoomAt(scrollY, x - (view.left() + view.right()) / 2.0,
                y - (view.top() + view.bottom()) / 2.0);
        return true;
    }

    @Override
    public boolean keyPressed(double x, double y, int key, int scanCode, int modifiers) {
        if (!insideView(x, y)) return false;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> camera.rotate(-15, 0);
            case GLFW.GLFW_KEY_RIGHT -> camera.rotate(15, 0);
            case GLFW.GLFW_KEY_UP -> camera.rotate(0, -15);
            case GLFW.GLFW_KEY_DOWN -> camera.rotate(0, 15);
            case GLFW.GLFW_KEY_HOME -> camera.reset();
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> camera.zoomAt(1, 0, 0);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> camera.zoomAt(-1, 0, 0);
            case GLFW.GLFW_KEY_PAGE_UP -> changeLayer(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> changeLayer(1);
            default -> { return false; }
        }
        return true;
    }

    private void changeLayer(int direction) {
        layer = Math.floorMod(layer + 1 + direction, layerCount + 1) - 1;
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, double x, double y) {
        if (y >= height - 15 && y < height && x >= 0 && x < WIDTH) {
            tooltip.add(Component.translatable("gui.molecularmanipulator.jei_preview_controls_tooltip"));
        } else if (buttonAt(x, y) == 1) {
            tooltip.add(Component.translatable("gui.molecularmanipulator.jei_show_all_layers"));
        }
    }

    static void text(GuiGraphics graphics, Component label, int x, int y, int maxWidth, int color, boolean centered) {
        var font = Minecraft.getInstance().font;
        String value = label.getString();
        if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, maxWidth - font.width("…")) + "…";
        graphics.drawString(font, value, centered ? x + (maxWidth - font.width(value)) / 2 : x, y, color, false);
    }

    private boolean insideView(double x, double y) {
        return x >= view.left() && x < view.right() && y >= view.top() && y < view.bottom();
    }

    private static int buttonAt(double x, double y) {
        if (y < 4 || y >= 22) return -1;
        for (int i = 0; i < BUTTON_LEFT.length; i++) {
            if (x >= BUTTON_LEFT[i] && x < BUTTON_LEFT[i] + BUTTON_WIDTH[i]) return i;
        }
        return -1;
    }

    protected record RenderPart(BlockPos position, BlockState state) {
        RenderPart(int x, int y, int z, BlockState state) { this(new BlockPos(x, y, z), state); }
        int x() { return position.getX(); }
        int y() { return position.getY(); }
        int z() { return position.getZ(); }
    }
}
