package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** GPU transforms local meshes, then the original Iris render phase draws ordinary world-space vertices. */
final class StarOrreryGpuModels implements AutoCloseable {
    static final IrisApi IRIS = IrisApi.load();
    private final Map<ModelResourceLocation, Model> models = new HashMap<>();
    private int program;
    private Uniforms uniforms;
    private SingularityVertexLayout configuredLayout;
    private int configuredQuads = -1;
    private boolean unavailable, used;

    boolean submit(ModelResourceLocation id, List<BakedQuad> quads, MultiBufferSource buffers, RenderType base,
            PoseStack.Pose pose, int light, float brightness, float alpha) {
        if (unavailable || IRIS == null
                || !GL.getCapabilities().OpenGL43 || !IRIS.supports(buffers)) return false;
        var state = SingularityVertexLayout.captureLevel();
        if (state == null) return false;
        if (program == 0) {
            try { initialize(); }
            catch (IOException | RuntimeException failure) {
                unavailable = true;
                MolecularManipulator.LOGGER.warn("GPU star_orrery transforms unavailable; retaining CPU submission", failure);
                return false;
            }
        }
        var model = models.computeIfAbsent(id, ignored -> new Model(quads, base));
        var target = buffers.getBuffer(model.type);
        var writer = SodiumVertexBatch.of(target);
        if (writer == null || SingularityVertexLayout.of(writer.format()) != model.layout) return false;
        var command = new Command(pose.copy(), light, brightness, alpha, state.entityIds());
        model.commands.addLast(command);
        // A four-vertex marker keeps the command in the existing phase/order graph.
        // Its draw callback consumes the marker, transforms the resident mesh and draws that mesh instead.
        for (int corner = 0; corner < 4; corner++) {
            target.addVertex(0, 0, 0, -1, 0, 0, OverlayTexture.NO_OVERLAY, light, 0, 1, 0);
        }
        used = true;
        return true;
    }

    boolean active() { return program != 0 && used; }

    private record Command(PoseStack.Pose pose, int light, float brightness, float alpha, long materials) {}

    private final class Model implements AutoCloseable {
        final SingularityVertexLayout layout;
        final VertexBuffer output;
        final int source, outputId, count;
        final ArrayDeque<Command> commands = new ArrayDeque<>();
        final GpuType type;

        Model(List<BakedQuad> quads, RenderType base) {
            try (var mesh = StarOrreryModelCache.Mesh.bake(quads);
                    var storage = new ByteBufferBuilder(mesh.bytes())) {
                layout = SingularityVertexLayout.of(mesh.format);
                if (layout == null || layout.tangent < 0) throw new IllegalStateException("Expected Iris entity vertices");
                count = mesh.count;
                source = GlStateManager._glGenBuffers();
                int binding = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
                try {
                    GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, source);
                    GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, mesh.transformed(), GL15.GL_STATIC_DRAW);
                } finally { GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, binding); }
                // Seed immutable attributes once. Compute updates only animated bytes in this VBO.
                MemoryUtil.memCopy(mesh.transformedAddress(), storage.reserve(mesh.bytes()), mesh.bytes());
                output = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try {
                    output.bind();
                    output.upload(new MeshData(storage.build(), new MeshData.DrawState(mesh.format, count,
                            count / 4 * 6, VertexFormat.Mode.QUADS, VertexFormat.IndexType.least(count))));
                    outputId = IRIS.bufferId(output);
                } finally { VertexBuffer.unbind(); }
            }
            type = new GpuType(base, this);
        }

        void transform(Command command) {
            try (var stack = MemoryStack.stackPush();
                    var bindings = new StarOrreryComputeBindings(stack, 2, false)) {
                // The output may have been read by an earlier main/shadow draw of this same model.
                GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                GlStateManager._glUseProgram(program);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, source);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, outputId);
                GL20.glUniformMatrix4fv(uniforms.pose, false, command.pose.pose().get(stack.mallocFloat(16)));
                GL20.glUniformMatrix3fv(uniforms.normalPose, false, command.pose.normal().get(stack.mallocFloat(9)));
                // This private program retains its uniforms across graphics draws and Iris phases.
                if (configuredLayout != layout) {
                    GL20.glUniform4i(uniforms.layout, layout.stride, layout.color, layout.light, layout.normal);
                    GL20.glUniform3i(uniforms.extended, layout.entities, layout.midUv, layout.tangent);
                    configuredLayout = layout;
                }
                if (configuredQuads != count / 4) {
                    GL30.glUniform1ui(uniforms.quadCount, count / 4);
                    configuredQuads = count / 4;
                }
                GL30.glUniform1ui(uniforms.sceneLight, command.light);
                int channel = (int) (command.brightness * 255F) & 255, opacity = (int) (command.alpha * 255F) & 255;
                GL30.glUniform1ui(uniforms.color, channel * 0x010101 | opacity << 24);
                GL30.glUniform2ui(uniforms.material, (int) command.materials, (int) (command.materials >>> 32));
                GL20.glUniform1i(uniforms.normalizeNormal, IRIS.trusted(command.pose) ? 0 : 1);
                GL20.glUniform1i(uniforms.mirrored, command.pose.pose().determinant3x3() < 0F ? 1 : 0);
                GL43.glDispatchCompute((count / 4 + 63) / 64, 1, 1);
                GL43.glMemoryBarrier(GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT);
            }
        }

        @Override public void close() {
            commands.clear();
            output.close();
            GlStateManager._glDeleteBuffers(source);
        }
    }

    private final class GpuType extends RenderType {
        private final Model model;
        GpuType(RenderType base, Model model) {
            super("star_orrery_resident_" + models.size(), base.format(), base.mode(), 256,
                    base.affectsCrumbling(), false, base::setupRenderState, base::clearRenderState);
            this.model = model;
            IRIS.copyTransparency(base, this);
        }
        @Override public void draw(MeshData marker) {
            boolean merged = IRIS.merged();
            try (marker) {
                if (!merged) setupRenderState();
                try {
                    int draws = marker.drawState().vertexCount() / 4;
                    for (int draw = 0; draw < draws; draw++) {
                        var command = model.commands.removeFirst();
                        model.transform(command);
                        try {
                            model.output.bind();
                            model.output.drawWithShader(RenderSystem.getModelViewMatrix(),
                                    RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                        } finally { VertexBuffer.unbind(); }
                    }
                } finally { if (!merged) clearRenderState(); }
            }
        }
    }

    private static final class Uniforms {
        final int pose, normalPose, layout, extended, quadCount, sceneLight, color, material, normalizeNormal, mirrored;

        Uniforms(int program) {
            pose = GL20.glGetUniformLocation(program, "Pose");
            normalPose = GL20.glGetUniformLocation(program, "NormalPose");
            layout = GL20.glGetUniformLocation(program, "Layout");
            extended = GL20.glGetUniformLocation(program, "Extended");
            quadCount = GL20.glGetUniformLocation(program, "QuadCount");
            sceneLight = GL20.glGetUniformLocation(program, "SceneLight");
            color = GL20.glGetUniformLocation(program, "Color");
            material = GL20.glGetUniformLocation(program, "Material");
            normalizeNormal = GL20.glGetUniformLocation(program, "NormalizeNormal");
            mirrored = GL20.glGetUniformLocation(program, "Mirrored");
        }
    }

    private void initialize() throws IOException {
        int linked = compile();
        try {
            uniforms = new Uniforms(linked);
            program = linked;
        } catch (RuntimeException | Error failure) {
            GL20.glDeleteProgram(linked);
            throw failure;
        }
    }

    private static int compile() throws IOException {
        String source;
        try (var input = StarOrreryGpuModels.class.getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/star_orrery_transform.comp")) {
            if (input == null) throw new IOException("Missing star_orrery transform shader");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER), program = 0;
        try {
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetShaderInfoLog(shader));
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, shader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
                throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
            return program;
        } catch (RuntimeException failure) {
            if (program != 0) GL20.glDeleteProgram(program);
            throw failure;
        } finally { GL20.glDeleteShader(shader); }
    }

    @Override public void close() {
        models.values().forEach(Model::close);
        models.clear();
        if (program != 0) GL20.glDeleteProgram(program);
        program = 0;
        uniforms = null;
        configuredLayout = null;
        configuredQuads = -1;
        unavailable = false;
        used = false;
    }

    record IrisApi(Class<?> full, Class<?> unflushable, Class<?> wrapper,
            MethodHandle original, MethodHandle getTransparency, MethodHandle setTransparency,
            MethodHandle merge, MethodHandle vertexBufferId, MethodHandle trustedNormals) {
        static IrisApi load() {
            try {
                var lookup = MethodHandles.lookup();
                var full = Class.forName("net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource");
                var unflushable = Class.forName(full.getName() + "$UnflushableWrapper");
                var wrapper = Class.forName("net.irisshaders.iris.layer.BufferSourceWrapper");
                var blend = Class.forName("net.irisshaders.batchedentityrendering.impl.BlendingStateHolder");
                var transparency = Class.forName("net.irisshaders.batchedentityrendering.impl.TransparencyType");
                var immediate = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
                var bufferId = VertexBuffer.class.getDeclaredField("vertexBufferId"); bufferId.setAccessible(true);
                var trusted = PoseStack.Pose.class.getDeclaredField("trustedNormals"); trusted.setAccessible(true);
                return new IrisApi(full, unflushable, wrapper, lookup.unreflect(wrapper.getMethod("getOriginal"))
                        .asType(MethodType.methodType(MultiBufferSource.class, Object.class)),
                        lookup.unreflect(blend.getMethod("getTransparencyType"))
                                .asType(MethodType.methodType(Object.class, Object.class)),
                        lookup.unreflect(blend.getMethod("setTransparencyType", transparency))
                                .asType(MethodType.methodType(void.class, Object.class, Object.class)),
                        lookup.unreflectGetter(immediate.getField("mergeRendering")),
                        lookup.unreflectGetter(bufferId), lookup.unreflectGetter(trusted));
            } catch (ClassNotFoundException absent) { return null; }
            catch (ReflectiveOperationException incompatible) {
                MolecularManipulator.LOGGER.warn("Iris resident model hooks unavailable; retaining CPU submission", incompatible);
                return null;
            }
        }
        boolean supports(MultiBufferSource buffers) {
            try {
                while (wrapper.isInstance(buffers)) buffers = (MultiBufferSource) original.invokeExact((Object) buffers);
                return full.isInstance(buffers) || unflushable.isInstance(buffers);
            } catch (Throwable failure) { throw new IllegalStateException("Cannot inspect Iris buffer source", failure); }
        }
        void copyTransparency(RenderType source, RenderType target) {
            try {
                var value = (Object) getTransparency.invokeExact((Object) source);
                setTransparency.invokeExact((Object) target, value);
            } catch (Throwable failure) { throw new IllegalStateException("Cannot preserve Iris render phase", failure); }
        }
        boolean merged() {
            try { return (boolean) merge.invokeExact(); }
            catch (Throwable failure) { throw new IllegalStateException("Cannot inspect Iris batching phase", failure); }
        }
        int bufferId(VertexBuffer buffer) {
            try { return (int) vertexBufferId.invokeExact(buffer); }
            catch (Throwable failure) { throw new IllegalStateException("Cannot access resident vertex buffer", failure); }
        }
        boolean trusted(PoseStack.Pose pose) {
            try { return (boolean) trustedNormals.invokeExact(pose); }
            catch (Throwable failure) { throw new IllegalStateException("Cannot inspect normal transform", failure); }
        }
    }
}
