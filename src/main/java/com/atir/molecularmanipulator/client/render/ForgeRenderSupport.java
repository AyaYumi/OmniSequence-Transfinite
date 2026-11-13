package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Vector3f;

/** Shared 1.20.1 render operations used by the deferred geometry and GPU paths. */
public final class ForgeRenderSupport {
    private ForgeRenderSupport() {}

    public interface BuilderAccess { VertexFormat omni$vertexFormat(); }
    public interface LayerAccess { boolean omni$sortOnUpload(); }
    public interface BufferAccess { int omni$vertexBufferId(); }
    public interface GpuLayer { void draw(BufferBuilder.RenderedBuffer mesh, boolean stateAlreadySet); }

    public static BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        var builder = Tesselator.getInstance().getBuilder();
        builder.begin(mode, format);
        return builder;
    }

    public static PoseStack.Pose copyPose(PoseStack.Pose source) {
        var copy = new PoseStack().last();
        copy.pose().set(source.pose());
        copy.normal().set(source.normal());
        return copy;
    }

    /** Forge's late AFTER_LEVEL event carries the projection stack, not the world view stack. */
    static org.joml.Matrix4f viewMatrix() {
        return new org.joml.Matrix4f(RenderSystem.getInverseViewRotationMatrix()).invert();
    }

    /** 1.20.1 BER poses already include camera rotation; late effects store camera-relative poses. */
    static org.joml.Matrix4f cameraRelativePose(PoseStack.Pose pose) {
        return new org.joml.Matrix4f(RenderSystem.getInverseViewRotationMatrix()).mul(pose.pose());
    }

    static Vector3f transformNormal(PoseStack.Pose pose, float x, float y, float z, Vector3f target) {
        return pose.normal().transform(x, y, z, target).normalize();
    }

    static int offset(VertexFormat format, VertexFormatElement element) {
        int index = format.getElements().indexOf(element);
        return index < 0 ? -1 : format.getOffset(index);
    }

    static int offset(VertexFormat format, String name) {
        int index = format.getElementAttributeNames().indexOf(name);
        return index < 0 ? -1 : format.getOffset(index);
    }

    static boolean sorts(RenderType layer) { return ((LayerAccess) layer).omni$sortOnUpload(); }

    /** The same uniforms supplied by 1.20.1 VertexBuffer.drawWithShader, for custom GPU draws. */
    static void defaultUniforms(ShaderInstance shader, VertexFormat.Mode mode) {
        for (int unit = 0; unit < 12; unit++) shader.setSampler("Sampler" + unit, RenderSystem.getShaderTexture(unit));
        if (shader.MODEL_VIEW_MATRIX != null) shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        if (shader.PROJECTION_MATRIX != null) shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        if (shader.COLOR_MODULATOR != null) shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if (shader.GLINT_ALPHA != null) shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        if (shader.FOG_START != null) shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if (shader.FOG_END != null) shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if (shader.FOG_COLOR != null) shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if (shader.FOG_SHAPE != null) shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if (shader.TEXTURE_MATRIX != null) shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if (shader.GAME_TIME != null) shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        if (shader.SCREEN_SIZE != null) {
            var window = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }
        if (shader.LINE_WIDTH != null && (mode == VertexFormat.Mode.LINES || mode == VertexFormat.Mode.LINE_STRIP))
            shader.LINE_WIDTH.set(RenderSystem.getShaderLineWidth());
        RenderSystem.setupShaderLights(shader);
    }

    /** Called at Oculus's original segment draw, with its wrapped render state already active. */
    public static boolean drawOculusSegment(Object segment) {
        var iris = StarOrreryGpuModels.IRIS;
        return iris != null && iris.drawSegment(segment);
    }
}
