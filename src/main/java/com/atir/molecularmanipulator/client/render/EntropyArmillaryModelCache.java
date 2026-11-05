package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Reuses baked vertex attributes while submitting through the caller's original render layer. */
final class EntropyArmillaryModelCache implements AutoCloseable {
    private static final MethodHandle IRIS_MATERIALS = irisMaterials();
    private final Map<ModelResourceLocation, Mesh> meshes = new HashMap<>();
    private final ArmillaryGpuModels gpu = new ArmillaryGpuModels();
    private boolean shaders;
    private Object materials;

    /** Shader reloads can change the layout/material IDs; join outstanding transforms before calling. */
    void prepare(boolean shaders) {
        Object materials = null;
        if (shaders && IRIS_MATERIALS != null) {
            try { materials = (Object) IRIS_MATERIALS.invokeExact(); }
            catch (Throwable failure) { throw new IllegalStateException("Cannot read Iris material mapping", failure); }
        }
        if (this.shaders != shaders || this.materials != materials) {
            close();
            this.shaders = shaders;
            this.materials = materials;
        }
    }

    boolean gpuActive() { return gpu.active(); }

    boolean submitGpu(ModelResourceLocation model, List<BakedQuad> quads,
            net.minecraft.client.renderer.MultiBufferSource buffers, net.minecraft.client.renderer.RenderType layer,
            PoseStack.Pose pose, int light, float brightness, float alpha) {
        return shaders && IRIS_MATERIALS != null && gpu.submit(model, quads, buffers, layer, pose, light, brightness, alpha);
    }

    /** Render-thread only: finish baking before publishing the read-only mesh to workers. */
    Mesh mesh(ModelResourceLocation model, List<BakedQuad> quads) {
        return meshes.computeIfAbsent(model, ignored -> Mesh.bake(quads));
    }

    boolean submit(ModelResourceLocation model, List<BakedQuad> quads, VertexConsumer target,
            PoseStack.Pose pose, int light, float brightness, float alpha) {
        // Wrapped consumers retain the ordinary API: only a direct builder can safely finish
        // its pending immediate vertex before a native batch is appended.
        if (shaders && IRIS_MATERIALS == null || !(target instanceof BufferBuilder)) return false;
        var writer = SodiumVertexBatch.of(target);
        if (writer == null) return false;
        var mesh = mesh(model, quads);
        try {
            if (writer.format() != mesh.format) return false;
            mesh.transform(pose, light, brightness, alpha);
            try (var stack = MemoryStack.stackPush()) {
                writer.push(stack, mesh.transformedAddress(), mesh.count);
            }
            return true;
        } catch (Throwable failure) {
            // Never replay a batch after a failed append: it may already have written vertices.
            throw new IllegalStateException("Cannot submit cached armillary model " + model, failure);
        }
    }

    /**
     * Render-thread only, after joining all outstanding transforms and finishing default-output draws.
     * Caller-created outputs own their memory and must be closed separately after their draws finish.
     */
    @Override public void close() {
        gpu.close();
        meshes.values().forEach(Mesh::close);
        meshes.clear();
        materials = null;
        shaders = false;
    }

    static final class Mesh implements AutoCloseable {
        private static final float[] BRIGHTNESS = {1F, 1F, 1F, 1F};
        private static final int[] DARK = {0, 0, 0, 0};
        final VertexFormat format;
        final int count;
        private final ByteBuffer vertices;
        private final Output defaultOutput;
        // Initialized during construction and never modified after the mesh is published.
        private final boolean[] normalizeNormals;
        private final boolean[] uniformNormals;
        private final int stride, position, color, light, normal, tangent;
        private boolean closed;

        Mesh(MeshData data) {
            this(data, new boolean[data.drawState().vertexCount()]);
        }

        private Mesh(MeshData data, boolean[] normalizeNormals) {
            format = data.drawState().format();
            count = data.drawState().vertexCount();
            this.normalizeNormals = normalizeNormals;
            uniformNormals = new boolean[(count + 3) >>> 2];
            stride = format.getVertexSize();
            position = format.getOffset(VertexFormatElement.POSITION);
            color = format.getOffset(VertexFormatElement.COLOR);
            light = format.getOffset(VertexFormatElement.UV2);
            normal = format.getOffset(VertexFormatElement.NORMAL);
            tangent = format.getElements().stream().filter(e -> format.getElementName(e).equals("at_tangent"))
                    .mapToInt(format::getOffset).findFirst().orElse(-1);
            var template = MemoryUtil.memAlloc(bytes());
            try {
                MemoryUtil.memCopy(MemoryUtil.memAddress(data.vertexBuffer()), MemoryUtil.memAddress(template), bytes());
                vertices = template;
                detectUniformNormals();
                defaultOutput = newOutput();
            } catch (RuntimeException | Error failure) {
                MemoryUtil.memFree(template);
                throw failure;
            }
        }

        /** Render-thread only: builders and any Sodium/Iris encoding stay out of worker transforms. */
        static Mesh bake(List<BakedQuad> quads) {
            try (var storage = new ByteBufferBuilder(Math.max(256, quads.size() * 4 * DefaultVertexFormat.NEW_ENTITY.getVertexSize()))) {
                var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                boolean sodiumNormals = SodiumVertexBatch.of(builder) != null;
                var normalizeNormals = new boolean[quads.size() * 4];
                var identity = new PoseStack();
                int vertex = 0;
                for (var quad : quads) {
                    builder.putBulkData(identity.last(), quad, BRIGHTNESS, 1F, 1F, 1F, 1F,
                            DARK, OverlayTexture.NO_OVERLAY, false);
                    boolean bakedNormal = false;
                    var source = quad.getVertices();
                    for (int corner = 0; corner < 4; corner++) {
                        bakedNormal |= (source[corner * 8 + 7] & 0xFFFFFF) != 0;
                        // Sodium's ordinary entity encoder normalizes authored normals
                        // under nonuniform scale, whereas NeoForge's immediate API does not.
                        normalizeNormals[vertex++] = sodiumNormals || !bakedNormal;
                    }
                }
                try (var data = builder.buildOrThrow()) {
                    return new Mesh(data, normalizeNormals);
                }
            }
        }

        int bytes() { return count * stride; }
        long transformedAddress() { return defaultOutput.address(); }
        ByteBuffer transformed() { return defaultOutput.buffer(); }

        /** Allocate once per outstanding draw; reuse only after its transform and submission finish. */
        Output newOutput() {
            if (closed) throw new IllegalStateException("Mesh is closed");
            return new Output(this);
        }

        /** Single-thread compatibility path; workers must each use a separate caller-owned output. */
        void transform(PoseStack.Pose pose, int sceneLight, float brightness, float alpha) {
            transform(defaultOutput, pose, sceneLight, brightness, alpha);
        }

        /** The caller exclusively owns output and keeps pose unchanged until this transform finishes. */
        void transform(Output output, PoseStack.Pose pose, int sceneLight, float brightness, float alpha) {
            if (output.mesh != this) throw new IllegalArgumentException("Output belongs to another mesh");
            if (closed || output.closed) throw new IllegalStateException("Mesh or output is closed");
            long source = MemoryUtil.memAddress(vertices), target = output.address;
            var transformed = output.transformed;
            byte channel = (byte) (int) (brightness * 255F), opacity = (byte) (int) (alpha * 255F);
            int packedColor = Byte.toUnsignedInt(channel) * 0x010101 | Byte.toUnsignedInt(opacity) << 24;
            boolean writeColor = !output.colorInitialized || channel != output.lastChannel || opacity != output.lastOpacity;
            boolean writeLight = !output.lightInitialized || sceneLight != output.lastSceneLight;
            var matrix = pose.pose();
            boolean mirrored = tangent >= 0 && matrix.determinant3x3() < 0F;
            int quadNormal = 0, quadTangent = 0;
            for (int vertex = 0; vertex < count; vertex++, source += stride, target += stride) {
                matrix.transformPosition(MemoryUtil.memGetFloat(source + position),
                        MemoryUtil.memGetFloat(source + position + 4), MemoryUtil.memGetFloat(source + position + 8), transformed);
                MemoryUtil.memPutFloat(target + position, transformed.x);
                MemoryUtil.memPutFloat(target + position + 4, transformed.y);
                MemoryUtil.memPutFloat(target + position + 8, transformed.z);
                if (writeColor) MemoryUtil.memPutInt(target + color, packedColor);
                if (writeLight) {
                    // Keep authored emissivity, just as VertexConsumer.applyBakedLighting does.
                    int bakedLight = MemoryUtil.memGetInt(source + light);
                    MemoryUtil.memPutInt(target + light, Math.max(sceneLight & 65535, bakedLight & 65535)
                            | Math.max(sceneLight >>> 16, bakedLight >>> 16) << 16);
                }
                if (tangent >= 0) {
                    // Iris bakes the same face normal and tangent into all four corners.
                    // Transform that basis once per quad instead of four times per frame.
                    if ((vertex & 3) == 0) {
                        pose.transformNormal(unpack(source + normal), unpack(source + normal + 1), unpack(source + normal + 2), transformed);
                        if (mirrored) transformed.negate();
                        quadNormal = packDirection(transformed);
                        matrix.transformDirection(unpack(source + tangent), unpack(source + tangent + 1),
                                unpack(source + tangent + 2), transformed);
                        if (transformed.lengthSquared() > 0F) transformed.normalize();
                        quadTangent = packDirection(transformed);
                    }
                    putDirection(target + normal, quadNormal);
                    putDirection(target + tangent, quadTangent);
                } else if (uniformNormals[vertex >>> 2]) {
                    // Generated normals (and flat authored faces) are identical in all
                    // four corners. Transform that normal once per quad, exactly as
                    // putBulkData derives one face normal for the whole quad.
                    if ((vertex & 3) == 0) {
                        if (normalizeNormals[vertex]) {
                            pose.transformNormal(unpack(source + normal), unpack(source + normal + 1), unpack(source + normal + 2), transformed);
                        } else {
                            pose.normal().transform(unpack(source + normal), unpack(source + normal + 1), unpack(source + normal + 2), transformed);
                        }
                        quadNormal = packDirection(transformed);
                    }
                    putDirection(target + normal, quadNormal);
                } else {
                    if (normalizeNormals[vertex]) {
                        pose.transformNormal(unpack(source + normal), unpack(source + normal + 1), unpack(source + normal + 2), transformed);
                    } else {
                        // NeoForge transforms authored normals without renormalizing them.
                        // Keep that behavior for ordinary shaders, including nonuniform scales.
                        pose.normal().transform(unpack(source + normal), unpack(source + normal + 1), unpack(source + normal + 2), transformed);
                    }
                    putDirection(target + normal, packDirection(transformed));
                }
            }
            // Publish the cache state only after every vertex was updated successfully.
            if (writeColor) {
                output.colorInitialized = true;
                output.lastChannel = channel;
                output.lastOpacity = opacity;
            }
            if (writeLight) {
                output.lightInitialized = true;
                output.lastSceneLight = sceneLight;
            }
        }

        private void detectUniformNormals() {
            // The tangent path already shares one face basis per quad.
            if (tangent >= 0) return;
            long address = MemoryUtil.memAddress(vertices) + normal;
            for (int quad = 0; quad < uniformNormals.length; quad++) {
                int vertex = quad * 4;
                if (vertex + 3 >= count) { uniformNormals[quad] = false; continue; }
                long first = address + (long) vertex * stride;
                byte x = MemoryUtil.memGetByte(first), y = MemoryUtil.memGetByte(first + 1),
                        z = MemoryUtil.memGetByte(first + 2);
                boolean uniform = normalizeNormals[vertex] == normalizeNormals[vertex + 1]
                        && normalizeNormals[vertex] == normalizeNormals[vertex + 2]
                        && normalizeNormals[vertex] == normalizeNormals[vertex + 3];
                for (int corner = 1; corner < 4 && uniform; corner++) {
                    long cornerAddress = first + (long) corner * stride;
                    uniform = x == MemoryUtil.memGetByte(cornerAddress)
                            && y == MemoryUtil.memGetByte(cornerAddress + 1)
                            && z == MemoryUtil.memGetByte(cornerAddress + 2);
                }
                uniformNormals[quad] = uniform;
            }
        }

        private static float unpack(long address) { return MemoryUtil.memGetByte(address) / 127F; }
        private static int packDirection(Vector3f direction) {
            return Byte.toUnsignedInt(pack(direction.x)) | Byte.toUnsignedInt(pack(direction.y)) << 8
                    | Byte.toUnsignedInt(pack(direction.z)) << 16;
        }
        private static void putDirection(long address, int direction) {
            MemoryUtil.memPutByte(address, (byte) direction);
            MemoryUtil.memPutByte(address + 1, (byte) (direction >>> 8));
            MemoryUtil.memPutByte(address + 2, (byte) (direction >>> 16));
        }
        private static byte pack(float value) { return (byte) (int) (Math.clamp(value, -1F, 1F) * 127F); }
        /** Join outstanding transforms before closing; independent outputs remain caller-owned. */
        @Override public void close() {
            if (closed) return;
            closed = true;
            MemoryUtil.memFree(vertices);
            defaultOutput.close();
        }

        /**
         * One draw's native storage, memo and scratch. Never transform, submit or close the same
         * output concurrently. Mesh/cache closure does not free caller-created outputs.
         */
        static final class Output implements AutoCloseable {
            private final Mesh mesh;
            private final ByteBuffer buffer;
            private final long address;
            private final Vector3f transformed = new Vector3f();
            private boolean colorInitialized, lightInitialized, closed;
            private byte lastChannel, lastOpacity;
            private int lastSceneLight;

            private Output(Mesh mesh) {
                this.mesh = mesh;
                buffer = MemoryUtil.memAlloc(mesh.bytes());
                address = MemoryUtil.memAddress(buffer);
                try {
                    // UVs, overlay, Iris material/mid-UV, tangent handedness and padding never change.
                    // Copy them once; transforms rewrite only animated attributes in place.
                    MemoryUtil.memCopy(MemoryUtil.memAddress(mesh.vertices), address, mesh.bytes());
                } catch (RuntimeException | Error failure) {
                    MemoryUtil.memFree(buffer);
                    throw failure;
                }
            }

            ByteBuffer buffer() { return buffer; }
            long address() { return address; }

            /** Call only after this output's transform and draw submission have finished. */
            @Override public void close() {
                if (closed) return;
                closed = true;
                MemoryUtil.memFree(buffer);
            }
        }
    }

    private static MethodHandle irisMaterials() {
        try {
            var type = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings");
            return MethodHandles.lookup().unreflect(type.getMethod("getBlockStateIds"))
                    .bindTo(type.getField("INSTANCE").get(null)).asType(MethodType.methodType(Object.class));
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException incompatible) {
            MolecularManipulator.LOGGER.warn("Iris material-aware model caching is unavailable", incompatible);
            return null;
        }
    }
}
