package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Resident glyphs and layout emitters generate complete code trails at their original Iris draw points. */
final class StarOrreryGpuRain {
    private static final Map<Integer, Table> TABLES = new HashMap<>();
    private static final Layer[] LAYERS = new Layer[2];
    // Matches the bounded per-pass row cache in star_orrery_rain.comp (maximum detailed layout).
    private static final int MAX_COLUMNS = 72;
    private static Program generator, attributes;
    private static GeneratorUniforms generatorUniforms;
    private static AttributeUniforms attributeUniforms;
    private static Table generatorTable;
    private static SingularityVertexLayout attributeLayout;
    private static int generatorPass = -1;
    private static int templates;
    private static boolean unavailable;

    private StarOrreryGpuRain() {}

    /** Capture before starting workers, so only an accepted GPU path omits their duplicate rain generation. */
    static Frame capture(PoseStack poses, MultiBufferSource buffers, int lit, int quality,
            double distanceSqr, float flowTime) {
        var iris = StarOrreryGpuModels.IRIS;
        if (lit <= 0 || !StarOrreryEffects.effectsVisible(quality, distanceSqr)
                || unavailable || iris == null || !SingularityEffectLayers.shadersActive()
                || !GL.getCapabilities().OpenGL43 || !iris.supports(buffers)) return null;
        var state = SingularityVertexLayout.captureLevel();
        if (state == null) return null;
        if (generator == null) {
            try { initialize(); }
            catch (IOException | RuntimeException failure) {
                clear();
                unavailable = true;
                MolecularManipulator.LOGGER.warn("GPU star_orrery rain unavailable; retaining complete CPU trails", failure);
                return null;
            }
        }
        boolean detailed = StarOrreryEffects.isDetailed(quality, distanceSqr);
        int rings = Math.clamp(lit, 1, StarOrreryEffects.RINGS);
        int key = rings * 2 + (detailed ? 1 : 0);
        var table = TABLES.computeIfAbsent(key, ignored -> new Table(rings, detailed));
        if (table.columns > MAX_COLUMNS) return null; // Future layouts must retain complete CPU fallback, not truncate.
        for (int pass = 0; pass < LAYERS.length; pass++) {
            if (LAYERS[pass] == null) LAYERS[pass] = new Layer(pass != 0, state.layout());
        }
        return new Frame(buffers, new Command(new Matrix4f(poses.last().pose()), table, flowTime, state.entityIds()));
    }

    static final class Frame {
        private final MultiBufferSource buffers;
        private final Command command;
        private int submitted;

        private Frame(MultiBufferSource buffers, Command command) { this.buffers = buffers; this.command = command; }

        void submit(boolean glow) {
            int pass = glow ? 1 : 0, bit = 1 << pass;
            if ((submitted & bit) != 0) throw new IllegalStateException("A GPU rain pass cannot be submitted twice");
            submitted |= bit;
            var layer = LAYERS[pass];
            var marker = buffers.getBuffer(layer);
            layer.commands.addLast(command);
            // Only this marker passes through Iris buffering. It keeps the original per-instance order.
            for (int corner = 0; corner < 4; corner++) marker.addVertex(0, 0, 0).setColor(-1);
        }
    }

    private record Command(Matrix4f pose, Table table, float flowTime, long materials) {}

    private static final class Table implements AutoCloseable {
        final int buffer, columns, lit;
        final boolean detailed;

        Table(int lit, boolean detailed) {
            this.lit = lit; this.detailed = detailed;
            var data = StarOrreryRainGeometry.columns(lit, detailed);
            columns = data.size();
            var bytes = MemoryUtil.memAlloc(columns * StarOrreryRainGeometry.COLUMN_BYTES);
            try {
                data.forEach(column -> column.write(bytes));
                bytes.flip();
                buffer = uploadStatic(bytes);
            } finally { MemoryUtil.memFree(bytes); }
        }

        int maxQuads(boolean glow) { return StarOrreryRainGeometry.maxQuads(lit, detailed, glow); }
        @Override public void close() { GlStateManager._glDeleteBuffers(buffer); }
    }

    private static final class Layer extends RenderType implements AutoCloseable {
        final ArrayDeque<Command> commands = new ArrayDeque<>();
        final boolean glow;
        final SingularityVertexLayout layout;
        private int compact, output, indirect, array, capacity;
        private boolean closed;

        Layer(boolean glow, SingularityVertexLayout layout) {
            this(OmniRenderLayers.starOrreryRainShader(glow), glow, layout);
        }

        private Layer(RenderType base, boolean glow, SingularityVertexLayout layout) {
            super("omni_star_orrery_gpu_rain_" + (glow ? "glow" : "depth"), DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 256, base.affectsCrumbling(), false,
                    base::setupRenderState, base::clearRenderState);
            if (base.sortOnUpload()) throw new IllegalArgumentException("Procedural rain requires its original unsorted layer");
            this.glow = glow; this.layout = layout;
            StarOrreryGpuModels.IRIS.copyTransparency(base, this);
        }

        private void allocate(int maxQuads) {
            if (array == 0) {
                compact = GlStateManager._glGenBuffers(); output = GlStateManager._glGenBuffers();
                indirect = GlStateManager._glGenBuffers(); array = GlStateManager._glGenVertexArrays();
                BufferUploader.invalidate();
                GlStateManager._glBindVertexArray(array);
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, output);
                layout.format.setupBufferState();
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, indirect);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, 32L, GL15.GL_DYNAMIC_DRAW);
            }
            if (maxQuads > capacity) {
                capacity = maxQuads;
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, compact);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) capacity * 4 * 24, GL15.GL_DYNAMIC_DRAW);
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, output);
                GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, (long) capacity * 4 * layout.stride, GL15.GL_DYNAMIC_DRAW);
            }
        }

        private void generate(Command command) {
            try (var stack = MemoryStack.stackPush();
                    var bindings = new StarOrreryComputeBindings(stack, 4, true)) {
                allocate(command.table.maxQuads(glow));
                // Compact/output/indirect storage may still be consumed by the preceding draw.
                GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
                GlStateManager._glUseProgram(generator.id);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, command.table.buffer);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, compact);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 2, indirect);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 3, templates);
                GL20.glUniformMatrix4fv(generatorUniforms.pose, false, command.pose.get(stack.mallocFloat(16)));
                if (generatorTable != command.table) {
                    GL30.glUniform1ui(generatorUniforms.columnCount, command.table.columns);
                    GL20.glUniform1i(generatorUniforms.detailed, command.table.detailed ? 1 : 0);
                    generatorTable = command.table;
                }
                int pass = glow ? 1 : 0;
                if (generatorPass != pass) {
                    GL20.glUniform1i(generatorUniforms.glow, pass);
                    generatorPass = pass;
                }
                GL20.glUniform1f(generatorUniforms.flowTime, command.flowTime);
                GL30.glUniform2ui(generatorUniforms.material, (int) command.materials, (int) (command.materials >>> 32));
                GL43.glDispatchCompute(1, 1, 1);
                GL43.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT | GL43.GL_COMMAND_BARRIER_BIT);
                GlStateManager._glUseProgram(attributes.id);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 0, compact);
                GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, output);
                if (attributeLayout != layout) {
                    GL20.glUniform4i(attributeUniforms.layout, layout.stride, layout.position, layout.color, layout.uv);
                    GL20.glUniform4i(attributeUniforms.attributes, layout.overlay, layout.light, layout.normal, layout.entities);
                    GL20.glUniform2i(attributeUniforms.extended, layout.midUv, layout.tangent);
                    attributeLayout = layout;
                }
                GlStateManager._glBindBuffer(GL43.GL_DISPATCH_INDIRECT_BUFFER, indirect);
                GL43.glDispatchComputeIndirect(20L);
                GL43.glMemoryBarrier(GL43.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43.GL_SHADER_STORAGE_BARRIER_BIT
                        | GL43.GL_COMMAND_BARRIER_BIT);
            }
        }

        @Override public void draw(MeshData marker) {
            boolean merged = StarOrreryGpuModels.IRIS.merged();
            int previousIndirect = GL11.glGetInteger(GL40.GL_DRAW_INDIRECT_BUFFER_BINDING);
            try (marker) {
                if (closed) return;
                if (!merged) setupRenderState();
                try {
                    int draws = marker.drawState().vertexCount() / 4;
                    for (int draw = 0; draw < draws; draw++) {
                        var command = commands.removeFirst();
                        generate(command);
                        BufferUploader.invalidate();
                        GlStateManager._glBindVertexArray(array);
                        var sequential = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
                        sequential.bind(command.table.maxQuads(glow) * 6);
                        GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, indirect);
                        var shader = RenderSystem.getShader();
                        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, RenderSystem.getModelViewMatrix(),
                                RenderSystem.getProjectionMatrix(), Minecraft.getInstance().getWindow());
                        shader.apply();
                        try { GL40.glDrawElementsIndirect(GL11.GL_TRIANGLES, sequential.type().asGLType, 0L); }
                        finally { shader.clear(); }
                    }
                } finally {
                    VertexBuffer.unbind();
                    if (!merged) clearRenderState();
                }
            } finally { GlStateManager._glBindBuffer(GL40.GL_DRAW_INDIRECT_BUFFER, previousIndirect); }
        }

        @Override public void close() {
            closed = true; commands.clear();
            if (array != 0) {
                GlStateManager._glDeleteBuffers(compact); GlStateManager._glDeleteBuffers(output);
                GlStateManager._glDeleteBuffers(indirect); GlStateManager._glDeleteVertexArrays(array);
            }
            array = compact = output = indirect = capacity = 0;
        }
    }

    private static void initialize() throws IOException {
        generator = Program.compile("star_orrery_rain.comp", "");
        generatorUniforms = new GeneratorUniforms(generator);
        attributes = Program.compile("effect_attributes.comp", "PROCEDURAL_RAIN");
        attributeUniforms = new AttributeUniforms(attributes);
        var data = MemoryUtil.memCalloc(StarOrreryRainGeometry.TEMPLATE_BYTES);
        try {
            StarOrreryRainGeometry.writeTemplates(data);
            data.flip(); templates = uploadStatic(data);
        } finally { MemoryUtil.memFree(data); }
    }

    private static int uploadStatic(java.nio.ByteBuffer data) {
        int binding = GL11.glGetInteger(GL43.GL_SHADER_STORAGE_BUFFER_BINDING);
        int buffer = GlStateManager._glGenBuffers();
        try {
            GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, buffer);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_STATIC_DRAW);
            return buffer;
        } catch (RuntimeException | Error failure) {
            GlStateManager._glDeleteBuffers(buffer);
            throw failure;
        } finally { GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, binding); }
    }

    private static final class GeneratorUniforms {
        final int pose, columnCount, flowTime, detailed, glow, material;
        GeneratorUniforms(Program program) {
            pose = program.uniform("Pose");
            columnCount = program.uniform("ColumnCount");
            flowTime = program.uniform("FlowTime");
            detailed = program.uniform("Detailed");
            glow = program.uniform("Glow");
            material = program.uniform("Material");
        }
    }

    private static final class AttributeUniforms {
        final int layout, attributes, extended;
        AttributeUniforms(Program program) {
            layout = program.uniform("Layout");
            attributes = program.uniform("Attributes");
            extended = program.uniform("Extended");
        }
    }

    private static final class Program implements AutoCloseable {
        final int id;
        private Program(int id) { this.id = id; }
        // Called only while building the location records, never in generate().
        int uniform(String name) { return GL20.glGetUniformLocation(id, name); }

        static Program compile(String resource, String define) throws IOException {
            String text;
            try (var input = StarOrreryGpuRain.class.getResourceAsStream(
                    "/assets/molecularmanipulator/shaders/core/" + resource)) {
                if (input == null) throw new IOException("Missing compute shader " + resource);
                text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (!define.isEmpty()) {
                int versionEnd = text.indexOf('\n') + 1;
                text = text.substring(0, versionEnd) + "#define " + define + "\n" + text.substring(versionEnd);
            }
            int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER), linked = 0;
            try {
                GL20.glShaderSource(shader, text); GL20.glCompileShader(shader);
                if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
                    throw new IllegalStateException(resource + ": " + GL20.glGetShaderInfoLog(shader));
                linked = GL20.glCreateProgram(); GL20.glAttachShader(linked, shader); GL20.glLinkProgram(linked);
                if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0)
                    throw new IllegalStateException(resource + ": " + GL20.glGetProgramInfoLog(linked));
                return new Program(linked);
            } catch (RuntimeException failure) {
                if (linked != 0) GL20.glDeleteProgram(linked);
                throw failure;
            } finally { GL20.glDeleteShader(shader); }
        }
        @Override public void close() { GL20.glDeleteProgram(id); }
    }

    static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(StarOrreryGpuRain::clear); return; }
        for (int pass = 0; pass < LAYERS.length; pass++) {
            if (LAYERS[pass] != null) LAYERS[pass].close();
            LAYERS[pass] = null;
        }
        TABLES.values().forEach(Table::close); TABLES.clear();
        if (templates != 0) GlStateManager._glDeleteBuffers(templates);
        if (generator != null) generator.close();
        if (attributes != null) attributes.close();
        generator = attributes = null;
        generatorUniforms = null; attributeUniforms = null;
        generatorTable = null; attributeLayout = null; generatorPass = -1;
        templates = 0; unavailable = false;
    }
}
