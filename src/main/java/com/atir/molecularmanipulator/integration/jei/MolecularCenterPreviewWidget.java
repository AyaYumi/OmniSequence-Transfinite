package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

final class MolecularCenterPreviewWidget implements IRecipeWidget, IJeiInputHandler {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 168;
    private static final int VIEW_LEFT = 4;
    private static final int VIEW_TOP = 48;
    private static final int VIEW_RIGHT = 172;
    private static final int VIEW_BOTTOM = 164;
    private static final ScreenPosition POSITION = new ScreenPosition(0, 0);
    private static final ScreenRectangle AREA = new ScreenRectangle(0, 0, WIDTH, HEIGHT);
    private static final List<RenderPart> PARTS = createRenderParts(false);
    private static final List<RenderPart> OVERVIEW_PARTS = createRenderParts(true);

    private int rotation;
    private int layer = -1;

    @Override
    public ScreenPosition getPosition() {
        return POSITION;
    }

    @Override
    public ScreenRectangle getArea() {
        return AREA;
    }

    @Override
    public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.fill(0, 0, WIDTH, HEIGHT, 0xFF171B21);
        drawPanel(graphics, 3, 2, 173, 23);
        drawButton(graphics, font, 5, 4, 20, 21, Component.literal("<"), isInside(mouseX, mouseY, 5, 4, 20, 21));
        drawButton(graphics, font, 156, 4, 171, 21, Component.literal(">"), isInside(mouseX, mouseY, 156, 4, 171, 21));
        graphics.drawCenteredString(font, Component.translatable("gui.molecularmanipulator.jei_structure"),
                WIDTH / 2, 8, 0xFFF1F1F1);

        drawPanel(graphics, 3, 25, 173, 46);
        drawButton(graphics, font, 5, 27, 20, 44, Component.literal("<"), isInside(mouseX, mouseY, 5, 27, 20, 44));
        drawButton(graphics, font, 156, 27, 171, 44, Component.literal(">"), isInside(mouseX, mouseY, 156, 27, 171, 44));
        Component layerLabel = layer < 0
                ? Component.translatable("gui.molecularmanipulator.jei_all_layers")
                : Component.translatable("gui.molecularmanipulator.jei_selected_layer", layer + 1,
                        MolecularCenterStructure.HEIGHT);
        graphics.drawCenteredString(font, layerLabel, WIDTH / 2, 31, 0xFFF1F1F1);

        graphics.fill(VIEW_LEFT, VIEW_TOP, VIEW_RIGHT, VIEW_BOTTOM, 0xFF0D1117);
        graphics.fill(VIEW_LEFT, VIEW_TOP, VIEW_RIGHT, VIEW_TOP + 1, 0xFF6E7781);
        graphics.fill(VIEW_LEFT, VIEW_BOTTOM - 1, VIEW_RIGHT, VIEW_BOTTOM, 0xFF6E7781);
        graphics.fill(VIEW_LEFT, VIEW_TOP, VIEW_LEFT + 1, VIEW_BOTTOM, 0xFF6E7781);
        graphics.fill(VIEW_RIGHT - 1, VIEW_TOP, VIEW_RIGHT, VIEW_BOTTOM, 0xFF6E7781);
        graphics.drawString(font, Component.literal("31 x 31 x 46"), 8, 52, 0xFF9DA7B3, false);
        drawStructure(graphics);
    }

    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        var key = input.getKey();
        if (key.getType() != InputConstants.Type.MOUSE || key.getValue() != 0) {
            return false;
        }
        Runnable action = null;
        if (isInside(mouseX, mouseY, 5, 4, 20, 21)) {
            action = () -> rotation = Math.floorMod(rotation - 1, 4);
        } else if (isInside(mouseX, mouseY, 156, 4, 171, 21)) {
            action = () -> rotation = Math.floorMod(rotation + 1, 4);
        } else if (isInside(mouseX, mouseY, 5, 27, 20, 44)) {
            action = this::previousLayer;
        } else if (isInside(mouseX, mouseY, 156, 27, 171, 44)) {
            action = this::nextLayer;
        } else if (isInside(mouseX, mouseY, 21, 27, 155, 44)) {
            action = () -> layer = -1;
        }
        if (action == null) {
            return false;
        }
        if (!input.isSimulate()) {
            action.run();
        }
        return true;
    }

    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double scrollDeltaX, double scrollDeltaY) {
        if (!isInside(mouseX, mouseY, VIEW_LEFT, VIEW_TOP, VIEW_RIGHT, VIEW_BOTTOM) || scrollDeltaY == 0) {
            return false;
        }
        if (scrollDeltaY > 0) {
            previousLayer();
        } else {
            nextLayer();
        }
        return true;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key mouseKey,
            double dragX, double dragY) {
        if (mouseKey.getType() != InputConstants.Type.MOUSE || mouseKey.getValue() != 0
                || !isInside(mouseX, mouseY, VIEW_LEFT, VIEW_TOP, VIEW_RIGHT, VIEW_BOTTOM)
                || Math.abs(dragX) < 2.0) {
            return false;
        }
        rotation = Math.floorMod(rotation + (dragX > 0 ? 1 : -1), 4);
        return true;
    }

    private void previousLayer() {
        layer = layer < 0 ? MolecularCenterStructure.HEIGHT - 1 : layer == 0 ? -1 : layer - 1;
    }

    private void nextLayer() {
        layer = layer < 0 ? 0 : layer + 1 >= MolecularCenterStructure.HEIGHT ? -1 : layer + 1;
    }

    private void drawStructure(GuiGraphics graphics) {
        var minecraft = Minecraft.getInstance();
        var pose = graphics.pose();
        graphics.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        Lighting.setupFor3DItems();
        pose.pushPose();
        float scale = layer < 0 ? 1.65F : 3.25F;
        float centerY = layer < 0 ? 22.5F : layer + 0.5F;
        pose.translate(WIDTH / 2.0F, layer < 0 ? 112.0F : 108.0F, 180.0F);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(25.0F));
        pose.mulPose(Axis.YP.rotationDegrees(45.0F + rotation * 90.0F));
        pose.translate(-MolecularCenterStructure.VISUAL_CENTER_X, -centerY,
                -MolecularCenterStructure.VISUAL_CENTER_Z);

        var dispatcher = minecraft.getBlockRenderer();
        var buffer = minecraft.renderBuffers().bufferSource();
        var renderParts = layer < 0 ? OVERVIEW_PARTS : PARTS;
        for (var part : renderParts) {
            if (!shouldRender(part)) {
                continue;
            }
            pose.pushPose();
            pose.translate(part.x(), part.y(), part.z());
            dispatcher.renderSingleBlock(part.state(), pose, buffer, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        buffer.endBatch();
        pose.popPose();
        Lighting.setupForFlatItems();
        RenderSystem.disableDepthTest();
        graphics.flush();
    }

    private boolean shouldRender(RenderPart part) {
        if (layer >= 0) {
            return part.y() == layer;
        }
        if (part.type() == MolecularCenterStructure.PartType.CORE
                || part.type() == MolecularCenterStructure.PartType.STABILIZER) {
            return true;
        }
        int nearX = rotation == 0 || rotation == 1
                ? MolecularCenterStructure.MAX_X
                : MolecularCenterStructure.MIN_X;
        int nearZ = rotation == 0 || rotation == 3
                ? MolecularCenterStructure.MIN_Z
                : MolecularCenterStructure.MAX_Z;
        int xSign = nearX == MolecularCenterStructure.MAX_X ? 1 : -1;
        int zSign = nearZ == MolecularCenterStructure.MAX_Z ? 1 : -1;
        boolean nearQuadrant = (part.x() - MolecularCenterStructure.VISUAL_CENTER_X) * xSign > 0
                && (part.z() - MolecularCenterStructure.VISUAL_CENTER_Z) * zSign > 0;
        boolean densePlatform = part.y() >= 12 && part.y() <= 20
                && (part.type() == MolecularCenterStructure.PartType.AE_QUARTZ
                        || part.type() == MolecularCenterStructure.PartType.CASING);
        return !nearQuadrant || !densePlatform;
    }

    private static List<RenderPart> createRenderParts(boolean overview) {
        var result = new ArrayList<RenderPart>();
        for (var part : MolecularCenterStructure.parts()) {
            if (part.partType() == MolecularCenterStructure.PartType.AIR) {
                continue;
            }
            if (overview && shouldCullFromOverview(part)) {
                continue;
            }
            BlockState state;
            if (MolecularCenterStructure.isController(part)) {
                state = ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState();
            } else {
                state = MolecularCenterStructure.partState(part.partType());
            }
            result.add(new RenderPart(part.x(), part.y(), part.z(), state, part.partType()));
        }
        return List.copyOf(result);
    }

    private static boolean shouldCullFromOverview(MolecularCenterStructure.Part part) {
        if (part.partType() == MolecularCenterStructure.PartType.CORE) {
            return false;
        }
        boolean enclosed = MolecularCenterStructure.partAt(part.x() - 1, part.y(), part.z()) != null
                && MolecularCenterStructure.partAt(part.x() + 1, part.y(), part.z()) != null
                && MolecularCenterStructure.partAt(part.x(), part.y() - 1, part.z()) != null
                && MolecularCenterStructure.partAt(part.x(), part.y() + 1, part.z()) != null
                && MolecularCenterStructure.partAt(part.x(), part.y(), part.z() - 1) != null
                && MolecularCenterStructure.partAt(part.x(), part.y(), part.z() + 1) != null;
        if (enclosed) {
            return true;
        }
        if (part.partType() != MolecularCenterStructure.PartType.AE_QUARTZ
                || part.y() < 12 || part.y() > 20) {
            return false;
        }
        return MolecularCenterStructure.partAt(part.x() - 1, part.y(), part.z()) != null
                && MolecularCenterStructure.partAt(part.x() + 1, part.y(), part.z()) != null
                && MolecularCenterStructure.partAt(part.x(), part.y(), part.z() - 1) != null
                && MolecularCenterStructure.partAt(part.x(), part.y(), part.z() + 1) != null;
    }

    private static void drawPanel(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, 0xFF0B0E13);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF302A39);
    }

    private static void drawButton(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            int left, int top, int right, int bottom, Component label, boolean hovered) {
        graphics.fill(left, top, right, bottom, hovered ? 0xFF8B6DB1 : 0xFF17131D);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, hovered ? 0xFF554566 : 0xFF29232F);
        graphics.drawCenteredString(font, label, (left + right) / 2, top + 4, 0xFFF4F0F8);
    }

    private static boolean isInside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private record RenderPart(int x, int y, int z, BlockState state,
            MolecularCenterStructure.PartType type) {
    }
}
