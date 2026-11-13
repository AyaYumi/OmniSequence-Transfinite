package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** Saves the render flags changed by the lens, nebula, lantern and late glyph passes. */
final class ForgeGlState implements AutoCloseable {
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final boolean logic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
    private final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int rgbEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
    private final int alphaEquation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final int[] scissorBox = new int[4];
    private final boolean[] colorMask = new boolean[4];
    private final float[] shaderColor = RenderSystem.getShaderColor().clone();

    ForgeGlState() {
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            for (int index = 0; index < 4; index++) colorMask[index] = mask.get(index) != 0;
        }
    }

    @Override public void close() {
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        GL20.glBlendEquationSeparate(rgbEquation, alphaEquation);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(depthFunc);
        RenderSystem.depthMask(depthMask);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (logic) RenderSystem.enableColorLogicOp(); else RenderSystem.disableColorLogicOp();
        RenderSystem.colorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (!scissor) RenderSystem.disableScissor();
    }
}
