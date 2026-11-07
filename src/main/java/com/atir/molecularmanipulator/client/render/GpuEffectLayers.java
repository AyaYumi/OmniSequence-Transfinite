package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.opengl.*;

/** Sort compact colored geometry in the original phase; generate the Iris attributes on the GPU. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class GpuEffectLayers {
    private static final Map<RenderType, Layer> LAYERS = new HashMap<>();
    private static int program;
    private static boolean unavailable;
    private static int quadCount, layoutUniform, attributesUniform, extendedUniform;

    private GpuEffectLayers() {}

    static VertexConsumer consumer(MultiBufferSource buffers, RenderType base) {
        var iris = StarOrreryGpuModels.IRIS;
        if (unavailable || iris == null
                || !GL.getCapabilities().OpenGL43 || !iris.supports(buffers)) return null;
        var state = SingularityVertexLayout.captureLevel();
        if (state == null) return null;
        if (program == 0) {
            try {
                program = compile();
                quadCount = GL20.glGetUniformLocation(program, "QuadCount");
                layoutUniform = GL20.glGetUniformLocation(program, "Layout");
                attributesUniform = GL20.glGetUniformLocation(program, "Attributes");
                extendedUniform = GL20.glGetUniformLocation(program, "Extended");
            } catch (IOException | RuntimeException failure) {
                unavailable = true;
                MolecularManipulator.LOGGER.warn("GPU effect attributes unavailable; retaining CPU vertices", failure);
                return null;
            }
        }
        // A single layer retains the original sorting domain across different materials.
        // The material snapshot travels with each compact vertex through Iris buffering.
        return new SingularityShaderVertices(buffers.getBuffer(
                LAYERS.computeIfAbsent(base, ignored -> new Layer(base, state.layout()))), state.entityIds());
    }

    private static final class Layer extends RenderType implements AutoCloseable {
        private final SingularityVertexLayout layout;
        private final EffectSourceStream sourceStream = new EffectSourceStream();
        private int source, output, indices, array;
        private int capacity;

        Layer(RenderType base, SingularityVertexLayout layout) {
            // Preserve the base layer's ordering. Its compact positions produce identical
            // quad centroids, so the resulting sorted index buffer is reusable.
            super("omni_gpu_attributes_" + LAYERS.size(), SingularityVertexLayout.COLOR_MATERIAL_FORMAT,
                    VertexFormat.Mode.QUADS, base.bufferSize(), base.affectsCrumbling(), base.sortOnUpload(),
                    base::setupRenderState, base::clearRenderState);
            this.layout = layout;
            StarOrreryGpuModels.IRIS.copyTransparency(base, this);
        }

        private void allocate(int vertices) {
            if (array == 0) {
                source = GlStateManager._glGenBuffers();
                output = GlStateManager._glGenBuffers();
                indices = GlStateManager._glGenBuffers();
                array = GlStateManager._glGenVertexArrays();
                BufferUploader.invalidate();
                GlStateManager._glBindVertexArray(array);
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, output);
                layout.format.setupBufferState();
            }
            int bytes = vertices * layout.stride;
            if (bytes > capacity) {
                capacity = Math.max(65536, Integer.highestOneBit(bytes - 1) << 1);
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, capacity, GL15.GL_DYNAMIC_DRAW);
            }
        }

        private void transform(MeshData mesh) {
            int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int previousBuffer = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
            int previousSource = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 0);
            int previousOutput = GL30.glGetIntegeri(GL43.GL_SHADER_STORAGE_BUFFER_BINDING, 1);
            try {
                allocate(mesh.drawState().vertexCount());
                int uploadedSource = sourceStream.upload(mesh.vertexBuffer());
                if (uploadedSource == 0) {
                    uploadedSource = source;
                    GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, source);
                    GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, mesh.vertexBuffer(), GL15.GL_STREAM_DRAW);
                }
                GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                GlStateManager._glUseProgram(program);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, uploadedSource);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, output);
                GL30.glUniform1ui(quadCount, mesh.drawState().vertexCount() / 4);
                GL20.glUniform4i(layoutUniform, layout.stride, layout.position, layout.color, layout.uv);
                GL20.glUniform4i(attributesUniform, layout.overlay, layout.light, layout.normal, layout.entities);
                GL20.glUniform2i(extendedUniform, layout.midUv, layout.tangent);
                GL43.glDispatchCompute((mesh.drawState().vertexCount() / 4 + 63) / 64, 1, 1);
                sourceStream.dispatched();
                GL43.glMemoryBarrier(GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            } finally {
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, previousSource);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, previousOutput);
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, previousBuffer);
                GlStateManager._glUseProgram(previousProgram);
            }
        }

        @Override public void draw(MeshData mesh) {
            boolean merged = StarOrreryGpuModels.IRIS.merged();
            try (mesh) {
                if (!merged) setupRenderState();
                try {
                    transform(mesh);
                    BufferUploader.invalidate();
                    GlStateManager._glBindVertexArray(array);
                    // The optional compact-mesh hook joins this original sealed domain here.
                    // Upload and draw stay on the render thread, in Iris's original segment order.
                    var sortedIndices = mesh.indexBuffer();
                    int indexType;
                    if (sortedIndices != null) {
                        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indices);
                        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, sortedIndices, GL15.GL_STREAM_DRAW);
                        indexType = mesh.drawState().indexType().asGLType;
                    } else {
                        var sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                        sequential.bind(mesh.drawState().indexCount());
                        indexType = sequential.type().asGLType;
                    }
                    var shader = RenderSystem.getShader();
                    shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(),
                            RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
                    shader.apply();
                    try { RenderSystem.drawElements(GL11.GL_TRIANGLES, mesh.drawState().indexCount(), indexType); }
                    finally { shader.clear(); }
                } finally {
                    VertexBuffer.unbind();
                    if (!merged) clearRenderState();
                }
            }
        }

        @Override public void close() {
            sourceStream.close();
            if (array != 0) {
                GlStateManager._glDeleteBuffers(source);
                GlStateManager._glDeleteBuffers(output);
                GlStateManager._glDeleteBuffers(indices);
                GlStateManager._glDeleteVertexArrays(array);
            }
            array = source = output = indices = capacity = 0;
        }
    }

    private static int compile() throws IOException {
        String text;
        try (var stream = GpuEffectLayers.class.getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/effect_attributes.comp")) {
            if (stream == null) throw new IOException("Missing effect attribute compute shader");
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER), linked = 0;
        try {
            GL20.glShaderSource(shader, text);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            linked = GL20.glCreateProgram();
            GL20.glAttachShader(linked, shader);
            GL20.glLinkProgram(linked);
            if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(linked));
            return linked;
        } catch (RuntimeException failure) {
            if (linked != 0) GL20.glDeleteProgram(linked);
            throw failure;
        } finally { GL20.glDeleteShader(shader); }
    }

    public static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(GpuEffectLayers::clear); return; }
        // Drain undrawn domains as well as those already consumed, before releasing/reusing resources.
        GpuEffectSorting.clear();
        LAYERS.values().forEach(Layer::close);
        LAYERS.clear();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0; unavailable = false;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ParallelEffectGeometry.clear();
        clear();
    }
}
