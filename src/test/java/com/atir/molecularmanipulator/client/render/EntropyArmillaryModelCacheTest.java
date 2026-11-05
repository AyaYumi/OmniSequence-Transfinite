package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.math.Axis;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class EntropyArmillaryModelCacheTest {
    @Test
    void cachedModelsKeepPositionsUvOverlayLightingAndAnimatedOpacity() {
        var quads = normalQuads();
        try (var mesh = EntropyArmillaryModelCache.Mesh.bake(quads); var output = mesh.newOutput()) {
            for (float xScale : new float[] {.28F, -.28F}) {
                var pose = new PoseStack();
                pose.translate(123.25, -17.5, 36.75);
                pose.mulPose(Axis.YP.rotationDegrees(37));
                pose.mulPose(Axis.ZP.rotationDegrees(-90));
                pose.scale(xScale, .40F, .18F);
                for (int light : new int[] {LightTexture.FULL_BRIGHT, LightTexture.pack(12, 3), 0, LightTexture.pack(3, 15)}) {
                    for (float brightness : new float[] {1F, .25F}) for (float alpha : new float[] {1F, .35F}) {
                        mesh.transform(pose.last(), light, brightness, alpha);
                        mesh.transform(output, pose.last(), light, brightness, alpha);
                        // The second identical submit exercises each output's color/light fast path.
                        mesh.transform(pose.last(), light, brightness, alpha);
                        mesh.transform(output, pose.last(), light, brightness, alpha);
                        try (var storage = new ByteBufferBuilder(2048)) {
                            var expected = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                            for (var quad : quads) {
                                expected.putBulkData(pose.last(), quad, new float[] {1,1,1,1}, brightness,
                                        brightness, brightness, alpha, new int[] {light,light,light,light},
                                        OverlayTexture.NO_OVERLAY, false);
                            }
                            try (var data = expected.buildOrThrow()) {
                                assertVerticesMatch(data.vertexBuffer(), mesh.transformed(), mesh.format, mesh.count);
                                assertVerticesMatch(data.vertexBuffer(), output.buffer(), mesh.format, mesh.count);
                                assertArrayEquals(snapshot(mesh.transformed()), snapshot(output.buffer()));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void simultaneousTransformsOfOneTemplateKeepOutputsIndependent() throws Exception {
        var quads = new ArrayList<BakedQuad>();
        var normals = normalQuads();
        for (int repeat = 0; repeat < 32; repeat++) quads.addAll(normals);
        try (var mesh = EntropyArmillaryModelCache.Mesh.bake(quads)) {
            assertConcurrentTransforms(mesh);
        }
    }

    @Test
    void cacheClosureLeavesCallerOwnedOutputAvailableForSubmission() {
        var model = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("test", "armillary"));
        try (var cache = new EntropyArmillaryModelCache()) {
            var mesh = cache.mesh(model, List.of(quad(0, LightTexture.pack(12, 3))));
            assertSame(mesh, cache.mesh(model, List.of(quad(99, 0))));
            try (var output = mesh.newOutput()) {
                var input = input(1, 2);
                mesh.transform(output, input.pose, input.light, input.brightness, input.alpha);
                var bytes = snapshot(output.buffer());
                long address = output.address();
                cache.close();
                assertEquals(address, output.address());
                assertArrayEquals(bytes, snapshot(output.buffer()));
                assertThrows(IllegalStateException.class, mesh::newOutput);
                assertThrows(IllegalStateException.class,
                        () -> mesh.transform(output, input.pose, input.light, input.brightness, input.alpha));
            }
        }
    }

    @Test
    void outputsRejectOtherTemplatesAndClosedStorage() {
        var quads = normalQuads();
        try (var mesh = EntropyArmillaryModelCache.Mesh.bake(quads);
             var other = EntropyArmillaryModelCache.Mesh.bake(quads);
             var output = mesh.newOutput()) {
            var input = input(0, 0);
            assertThrows(IllegalArgumentException.class,
                    () -> other.transform(output, input.pose, input.light, input.brightness, input.alpha));
            output.close();
            assertThrows(IllegalStateException.class,
                    () -> mesh.transform(output, input.pose, input.light, input.brightness, input.alpha));
        }
    }

    @Test
    void transformedShaderTangentsRetainMaterialIdsMidUvAndHandedness() throws Exception {
        var tangent = element(VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.GENERIC, 4);
        var material = element(VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.GENERIC, 3);
        var midUv = element(VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.GENERIC, 2);
        var format = VertexFormat.builder().add("Position", VertexFormatElement.POSITION)
                .add("Color", VertexFormatElement.COLOR).add("UV0", VertexFormatElement.UV0)
                .add("UV1", VertexFormatElement.UV1).add("UV2", VertexFormatElement.UV2)
                .add("Normal", VertexFormatElement.NORMAL).padding(1)
                .add("iris_Entity", material).add("mc_midTexCoord", midUv).add("at_tangent", tangent).build();
        int stride = format.getVertexSize(), count = 64;
        try (var storage = new ByteBufferBuilder(stride * count)) {
            long pointer = storage.reserve(stride * count);
            // Nonzero padding catches accidental wide normal/tangent writes.
            MemoryUtil.memSet(pointer, 0x6D, stride * count);
            for (int vertex = 0; vertex < count; vertex++) {
                int corner = vertex & 3;
                long p = pointer + (long) vertex * stride;
                MemoryUtil.memPutFloat(p, (vertex >>> 2) + (corner == 1 || corner == 2 ? 1F : 0F));
                MemoryUtil.memPutFloat(p + 4, 0F);
                MemoryUtil.memPutFloat(p + 8, corner >= 2 ? 1F : 0F);
                MemoryUtil.memPutInt(p + format.getOffset(VertexFormatElement.COLOR), 0x9D57A6D3);
                MemoryUtil.memPutFloat(p + format.getOffset(VertexFormatElement.UV0), .125F * corner);
                MemoryUtil.memPutFloat(p + format.getOffset(VertexFormatElement.UV0) + 4, .25F * corner);
                MemoryUtil.memPutInt(p + format.getOffset(VertexFormatElement.UV1), OverlayTexture.NO_OVERLAY);
                MemoryUtil.memPutInt(p + format.getOffset(VertexFormatElement.UV2), LightTexture.pack(corner * 2, 7 - corner));
                MemoryUtil.memPutByte(p + format.getOffset(VertexFormatElement.NORMAL), (byte) 0);
                MemoryUtil.memPutByte(p + format.getOffset(VertexFormatElement.NORMAL) + 1, (byte) 127);
                MemoryUtil.memPutByte(p + format.getOffset(VertexFormatElement.NORMAL) + 2, (byte) 0);
                MemoryUtil.memPutByte(p + format.getOffset(tangent), (byte) 127);
                MemoryUtil.memPutByte(p + format.getOffset(tangent) + 1, (byte) 0);
                MemoryUtil.memPutByte(p + format.getOffset(tangent) + 2, (byte) 0);
                MemoryUtil.memPutByte(p + format.getOffset(tangent) + 3, (byte) ((vertex & 4) == 0 ? -127 : 127));
                MemoryUtil.memPutShort(p + format.getOffset(material), (short) (31 + vertex));
                MemoryUtil.memPutShort(p + format.getOffset(material) + 2, (short) 700);
                MemoryUtil.memPutShort(p + format.getOffset(material) + 4, (short) -1);
                MemoryUtil.memPutFloat(p + format.getOffset(midUv), .375F);
                MemoryUtil.memPutFloat(p + format.getOffset(midUv) + 4, .625F);
            }
            try (var data = new MeshData(storage.build(), new MeshData.DrawState(format, count, count / 4 * 6,
                    VertexFormat.Mode.QUADS, VertexFormat.IndexType.SHORT));
                 var mesh = new EntropyArmillaryModelCache.Mesh(data);
                 var output = mesh.newOutput()) {
                assertConcurrentTransforms(mesh);
                for (float xScale : new float[] {.28F, -.28F}) {
                    var pose = new PoseStack();
                    pose.translate(-17, 3, 42);
                    pose.mulPose(Axis.YP.rotationDegrees(71));
                    pose.mulPose(Axis.XP.rotationDegrees(24));
                    pose.scale(xScale, .40F, .18F);
                    mesh.transform(pose.last(), LightTexture.FULL_BRIGHT, 1, .35F);
                    mesh.transform(output, pose.last(), LightTexture.FULL_BRIGHT, 1, .35F);
                    assertArrayEquals(snapshot(mesh.transformed()), snapshot(output.buffer()));
                    var normal = pose.last().transformNormal(0, 1, 0, new Vector3f());
                    if (xScale < 0) normal.negate();
                    var direction = pose.last().pose().transformDirection(new Vector3f(1, 0, 0)).normalize();
                    for (int vertex = 0; vertex < count; vertex++) {
                        int p = vertex * stride;
                        assertDirection(output.buffer(), p + format.getOffset(VertexFormatElement.NORMAL), normal);
                        assertDirection(output.buffer(), p + format.getOffset(tangent), direction);
                        var position = pose.last().pose().transformPosition(data.vertexBuffer().getFloat(p),
                                data.vertexBuffer().getFloat(p + 4), data.vertexBuffer().getFloat(p + 8), new Vector3f());
                        assertEquals(position.x, output.buffer().getFloat(p), 2E-5);
                        assertEquals(position.y, output.buffer().getFloat(p + 4), 2E-5);
                        assertEquals(position.z, output.buffer().getFloat(p + 8), 2E-5);
                    }
                    assertStableAttributes(snapshot(data.vertexBuffer()), output.buffer(), format, count);
                }
            }
        }
    }

    private record TransformInput(PoseStack.Pose pose, int light, float brightness, float alpha) {}

    private static TransformInput input(int worker, int step) {
        var pose = new PoseStack();
        pose.translate(123.25 + worker * 17, -17.5 + step, 36.75 - worker);
        pose.mulPose(Axis.YP.rotationDegrees(37 + worker * 29 + step * 7));
        pose.mulPose(Axis.ZP.rotationDegrees(-90 + step * 11));
        pose.scale(((worker + step) & 1) == 0 ? .28F : -.28F, .40F + worker * .03F, .18F + step * .01F);
        int light = switch ((worker + step / 2) % 4) {
            case 0 -> LightTexture.FULL_BRIGHT;
            case 1 -> 0;
            case 2 -> LightTexture.pack(12, 3);
            default -> LightTexture.pack(3, 15);
        };
        float brightness = (worker + step / 2) % 3 == 0 ? 1F : .25F;
        float alpha = (worker + step / 3) % 3 == 0 ? 1F : .35F;
        return new TransformInput(pose.last().copy(), light, brightness, alpha);
    }

    private static void assertConcurrentTransforms(EntropyArmillaryModelCache.Mesh mesh) throws Exception {
        int workerCount = 4, steps = 12;
        var template = snapshot(mesh.transformed());
        var inputs = new TransformInput[workerCount][steps];
        var expected = new byte[workerCount][steps][];
        for (int worker = 0; worker < workerCount; worker++) for (int step = 0; step < steps; step++) {
            var input = inputs[worker][step] = input(worker, step);
            mesh.transform(input.pose, input.light, input.brightness, input.alpha);
            expected[worker][step] = snapshot(mesh.transformed());
        }
        var defaultBeforeWorkers = snapshot(mesh.transformed());
        long defaultAddress = mesh.transformedAddress();
        var outputs = new ArrayList<EntropyArmillaryModelCache.Mesh.Output>();
        try {
            for (int worker = 0; worker < workerCount; worker++) {
                var output = mesh.newOutput();
                outputs.add(output);
                assertArrayEquals(template, snapshot(output.buffer()), "New outputs must start from the immutable template");
                assertNotEquals(defaultAddress, output.address());
                for (int previous = 0; previous < worker; previous++) {
                    assertNotEquals(outputs.get(previous).address(), output.address());
                }
                // Tag the unused normal-padding byte to detect any per-transform template recopy.
                int stride = mesh.format.getVertexSize(), padding = mesh.format.getOffset(VertexFormatElement.NORMAL) + 3;
                for (int vertex = 0; vertex < mesh.count; vertex++) {
                    int offset = vertex * stride + padding;
                    byte marker = (byte) (0x60 + worker);
                    output.buffer().put(offset, marker);
                    for (var serial : expected[worker]) serial[offset] = marker;
                }
            }
            var barrier = new CyclicBarrier(workerCount);
            try (var workers = Executors.newFixedThreadPool(workerCount)) {
                var tasks = new ArrayList<Callable<Void>>();
                for (int worker = 0; worker < workerCount; worker++) {
                    int index = worker;
                    var output = outputs.get(worker);
                    var buffer = output.buffer();
                    long address = output.address();
                    tasks.add(() -> {
                        for (int iteration = 0; iteration < steps * 4; iteration++) {
                            int step = iteration % steps;
                            var input = inputs[index][step];
                            barrier.await(5, TimeUnit.SECONDS);
                            mesh.transform(output, input.pose, input.light, input.brightness, input.alpha);
                            barrier.await(5, TimeUnit.SECONDS);
                            assertSame(buffer, output.buffer());
                            assertEquals(address, output.address());
                            assertEquals(MemoryUtil.memAddress(buffer), output.address());
                            assertArrayEquals(expected[index][step], snapshot(buffer),
                                    "Worker " + index + ", iteration " + iteration);
                        }
                        return null;
                    });
                }
                for (var result : workers.invokeAll(tasks, 30, TimeUnit.SECONDS)) result.get();
            }
            assertEquals(defaultAddress, mesh.transformedAddress());
            assertArrayEquals(defaultBeforeWorkers, snapshot(mesh.transformed()), "Workers must not overwrite the default output");
            try (var fresh = mesh.newOutput()) {
                assertArrayEquals(template, snapshot(fresh.buffer()), "Workers must not overwrite the template");
            }
        } finally {
            // The executor closes and joins before any native storage is released, including on failure.
            outputs.forEach(EntropyArmillaryModelCache.Mesh.Output::close);
        }
    }

    private static byte[] snapshot(ByteBuffer buffer) {
        var bytes = new byte[buffer.capacity()];
        for (int offset = 0; offset < bytes.length; offset++) bytes[offset] = buffer.get(offset);
        return bytes;
    }

    private static void assertStableAttributes(byte[] template, ByteBuffer actual, VertexFormat format, int count) {
        int stride = format.getVertexSize();
        int position = format.getOffset(VertexFormatElement.POSITION), color = format.getOffset(VertexFormatElement.COLOR);
        int light = format.getOffset(VertexFormatElement.UV2), normal = format.getOffset(VertexFormatElement.NORMAL);
        int tangent = format.getElements().stream().filter(e -> format.getElementName(e).equals("at_tangent"))
                .mapToInt(format::getOffset).findFirst().orElse(-1);
        for (int vertex = 0; vertex < count; vertex++) for (int offset = 0; offset < stride; offset++) {
            if (offset >= position && offset < position + 12 || offset >= color && offset < color + 4
                    || offset >= light && offset < light + 4 || offset >= normal && offset < normal + 3
                    || tangent >= 0 && offset >= tangent && offset < tangent + 3) continue;
            int address = vertex * stride + offset;
            assertEquals(template[address], actual.get(address), "Stable byte " + address);
        }
    }

    private static List<BakedQuad> normalQuads() {
        var generatedNormals = quad(6, 0);
        for (int corner = 0; corner < 4; corner++) generatedNormals.getVertices()[corner * 8 + 7] = 0;
        var mixedNormals = quad(-4, LightTexture.pack(2, 7));
        for (int corner = 0; corner < 4; corner++) {
            mixedNormals.getVertices()[corner * 8 + 7] = switch (corner) {
                case 0 -> 127;
                case 1 -> 127 << 8;
                case 2 -> 127 << 16;
                default -> 0xFF;
            };
        }
        return List.of(quad(0, 0), quad(3, LightTexture.pack(12, 3)), generatedNormals, mixedNormals);
    }

    private static VertexFormatElement element(VertexFormatElement.Type type, VertexFormatElement.Usage usage, int count) {
        int id = 0;
        while (VertexFormatElement.byId(id) != null) id++;
        return VertexFormatElement.register(id, 0, type, usage, count);
    }

    private static BakedQuad quad(float offset, int bakedLight) {
        int[] vertices = new int[32];
        for (int vertex = 0; vertex < 4; vertex++) {
            int p = vertex * 8;
            float x = vertex == 1 || vertex == 2 ? 2.5F : -.75F;
            float y = vertex >= 2 ? 3.25F : -.25F;
            vertices[p] = Float.floatToRawIntBits(x + offset);
            vertices[p + 1] = Float.floatToRawIntBits(y);
            vertices[p + 2] = Float.floatToRawIntBits(.5F);
            vertices[p + 3] = 0x9D57A6D3;
            vertices[p + 4] = Float.floatToRawIntBits(vertex == 1 || vertex == 2 ? .75F : .125F);
            vertices[p + 5] = Float.floatToRawIntBits(vertex >= 2 ? .625F : .25F);
            vertices[p + 6] = bakedLight;
            vertices[p + 7] = 127 << 16;
        }
        return new BakedQuad(vertices, -1, Direction.SOUTH, null, false);
    }

    private static void assertVerticesMatch(ByteBuffer expected, ByteBuffer actual, VertexFormat format, int count) {
        int stride = format.getVertexSize();
        for (int vertex = 0; vertex < count; vertex++) {
            int p = vertex * stride;
            for (var attribute : format.getElements()) {
                int offset = p + format.getOffset(attribute);
                if (attribute == VertexFormatElement.POSITION || attribute == VertexFormatElement.UV0) {
                    for (int component = 0; component < attribute.count(); component++) {
                        assertEquals(expected.getFloat(offset + 4 * component), actual.getFloat(offset + 4 * component), 2E-5);
                    }
                } else if (attribute == VertexFormatElement.NORMAL) {
                    for (int component = 0; component < 3; component++) {
                        assertEquals(expected.get(offset + component), actual.get(offset + component), 2);
                    }
                } else {
                    for (int b = 0; b < attribute.byteSize(); b++) assertEquals(expected.get(offset + b), actual.get(offset + b));
                }
            }
        }
    }

    private static void assertDirection(ByteBuffer vertices, int offset, Vector3f expected) {
        assertEquals(expected.x, vertices.get(offset) / 127F, 2F / 127F);
        assertEquals(expected.y, vertices.get(offset + 1) / 127F, 2F / 127F);
        assertEquals(expected.z, vertices.get(offset + 2) / 127F, 2F / 127F);
    }
}
