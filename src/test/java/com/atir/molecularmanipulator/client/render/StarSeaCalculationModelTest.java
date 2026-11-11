package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.QuadTransformers;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StarSeaCalculationModelTest {
    private static final Path SOURCE = Path.of("src/test/resources/fixtures/star_sea_calculation/"
            + "m1-cabinet-uv-alignment/star-listener-m1.bbmodel");
    private static final Path MODELS = Path.of(
            "src/main/resources/assets/molecularmanipulator/models/block/star_sea_calculation_mainframe");

    @Test
    void standaloneIdsMatchTheTwoNewResources() {
        assertEquals(ModelResourceLocation.STANDALONE_VARIANT, StarSeaCalculationModel.SHELL.variant());
        assertEquals(ModelResourceLocation.STANDALONE_VARIANT, StarSeaCalculationModel.GLASS.variant());
        assertEquals("molecularmanipulator:block/star_sea_calculation_mainframe/shell", StarSeaCalculationModel.SHELL.id().toString());
        assertEquals("molecularmanipulator:block/star_sea_calculation_mainframe/glass", StarSeaCalculationModel.GLASS.id().toString());
    }

    @Test
    void miniatureToWorldTransformMatchesAllSourceCornersInAllFourFacings() throws Exception {
        var source = JsonParser.parseString(Files.readString(SOURCE)).getAsJsonObject().getAsJsonArray("elements");
        for (var facing : Direction.Plane.HORIZONTAL) {
            var poses = new PoseStack();
            // A BER's/ghost's existing world/camera transform must remain intact.
            poses.translate(13.125, -40.75, 99.5);
            StarSeaCalculationModel.transform(poses, facing);
            for (String part : List.of("shell", "glass")) {
                var miniature = JsonParser.parseString(Files.readString(MODELS.resolve(part + ".json")))
                        .getAsJsonObject().getAsJsonArray("elements");
                int index = 0;
                for (var value : source) {
                    var original = value.getAsJsonObject();
                    boolean glass = original.get("uuid").getAsString().equals("dc28d5fa-530d-5d5b-b835-db1c13427be4");
                    if (glass != part.equals("glass")) continue;
                    var mini = miniature.get(index++).getAsJsonObject();
                    for (int corner = 0; corner < 8; corner++) {
                        double[] bb = new double[3];
                        float[] position = new float[3];
                        for (int axis = 0; axis < 3; axis++) {
                            String end = (corner & (1 << axis)) == 0 ? "from" : "to";
                            bb[axis] = original.getAsJsonArray(end).get(axis).getAsDouble();
                            position[axis] = mini.getAsJsonArray(end).get(axis).getAsFloat() / 16;
                        }
                        var actual = poses.last().pose().transformPosition(new Vector3f(position));
                        var expected = rotate(new Vec3(bb[0] / 16 + .5, bb[1] / 16, bb[2] / 16 + 2.5), facing)
                                .add(13.125, -40.75, 99.5);
                        assertEquals(expected.x, actual.x, 2E-5, facing + " " + part);
                        assertEquals(expected.y, actual.y, 2E-5, facing + " " + part);
                        assertEquals(expected.z, actual.z, 2E-5, facing + " " + part);
                    }
                }
                assertEquals(miniature.asList().stream().filter(element -> !element.getAsJsonObject().has("neoforge_data")).count(), index);
            }
        }
    }

    @Test
    void anchorIsBottomFrontAndTransformDoesNotScaleTheHeightTwice() {
        var expectedCenters = List.of(new Vec3(.5, 0, 2.5), new Vec3(-1.5, 0, .5),
                new Vec3(.5, 0, -1.5), new Vec3(2.5, 0, .5));
        var facings = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
        for (int q = 0; q < 4; q++) {
            var poses = new PoseStack();
            StarSeaCalculationModel.transform(poses, facings.get(q));
            var bottom = poses.last().pose().transformPosition(new Vector3f(.5F, 0, .5F));
            var top = poses.last().pose().transformPosition(new Vector3f(.5F, 18F / 16, .5F));
            assertEquals(expectedCenters.get(q).x, bottom.x, 1E-6);
            assertEquals(expectedCenters.get(q).z, bottom.z, 1E-6);
            assertEquals(0, bottom.y, 1E-6);
            assertEquals(9, top.y, 1E-6);
        }
        assertThrows(IllegalArgumentException.class, () -> StarSeaCalculationModel.transform(new PoseStack(), Direction.UP));
    }

    @Test
    void untintedFacesReceiveExplicitRgbaWorldLightAndOverlayExactlyOnceWithoutAnOuterTransform() {
        var poses = new PoseStack();
        poses.translate(7.25, -3.5, 11.75);
        var before = new Matrix4f(poses.last().pose());
        var capture = new Capture();
        int light = (7 << 4) | (11 << 20), overlay = (5 << 16) | 7;
        var quads = List.of(quad(Direction.NORTH), quad(Direction.EAST));
        assertTrue(quads.stream().noneMatch(BakedQuad::isTinted));
        StarSeaCalculationModel.drawQuads(poses, capture, quads, light, overlay, .25F, .5F, .75F, .375F);
        assertEquals(before, poses.last().pose(), "Draw must neither expand nor move the MINI model");
        assertEquals(8, capture.positions.size(), "Two quads produce eight vertices, not repeated model passes");
        for (int i = 0; i < 8; i++) {
            assertArrayEquals(new int[] {63, 127, 191, 95}, capture.colors.get(i));
            assertArrayEquals(new int[] {light & 65535, light >>> 16}, capture.lights.get(i));
            assertArrayEquals(new int[] {overlay & 65535, overlay >>> 16}, capture.overlays.get(i));
            assertArrayEquals(new float[] {.125F, .75F}, capture.uvs.get(i), 1E-7F);
        }
        assertArrayEquals(new float[] {7.25F, -3.5F, 11.75F}, capture.positions.getFirst(), 1E-7F);
        StarSeaCalculationModel.clear();
        StarSeaCalculationModel.clear(); // Reload clearing is idempotent and needs no GL context.
    }

    @Test
    void onlyOpaqueGrayscaleShellsWithTheStandardOverlayUseCachedSubmission() {
        int overlay = OverlayTexture.NO_OVERLAY;
        assertTrue(StarSeaCalculationModel.cacheableShell(false, overlay, 1, 1, 1));
        assertTrue(StarSeaCalculationModel.cacheableShell(false, overlay, .25F, .25F, .25F));
        assertFalse(StarSeaCalculationModel.cacheableShell(true, overlay, 1, 1, 1),
                "Even white placement ghosts must keep their faces in the translucent sorting domain");
        assertFalse(StarSeaCalculationModel.cacheableShell(false, overlay, .25F, .5F, .75F));
        assertFalse(StarSeaCalculationModel.cacheableShell(false, overlay ^ 1, 1, 1, 1));
    }

    @Test
    void cachedShellMatchesImmediateVerticesForEveryFacingLightingAndOpacity() {
        var ordinary = quad(Direction.NORTH);
        var glow = quad(Direction.UP);
        QuadTransformers.applyingLightmap(15, 15).processInPlace(glow);
        var quads = List.of(ordinary, glow, quad(Direction.EAST));
        try (var cache = new StarOrreryModelCache()) {
            var mesh = cache.mesh(StarSeaCalculationModel.SHELL, quads);
            long address = mesh.transformedAddress();
            for (var facing : Direction.Plane.HORIZONTAL) {
                var poses = new PoseStack();
                poses.translate(13.125, -40.75, 99.5);
                StarSeaCalculationModel.transform(poses, facing);
                var before = new Matrix4f(poses.last().pose());
                for (int light : new int[] {0, LightTexture.pack(2, 3), LightTexture.FULL_BRIGHT}) {
                    for (float brightness : new float[] {1, .25F}) for (float alpha : new float[] {1, .375F}) {
                        mesh.transform(poses.last(), light, brightness, alpha);
                        // Repeated draws reuse one mesh/output; no per-building vertex allocation.
                        assertSame(mesh, cache.mesh(StarSeaCalculationModel.SHELL, quads));
                        assertEquals(address, mesh.transformedAddress());
                        try (var storage = new ByteBufferBuilder(1024)) {
                            var expected = new BufferBuilder(storage, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                            StarSeaCalculationModel.drawQuads(poses, expected, quads, light,
                                    OverlayTexture.NO_OVERLAY, brightness, brightness, brightness, alpha);
                            try (var data = expected.buildOrThrow()) {
                                assertEquals(data.drawState().format(), mesh.format);
                                assertEquals(data.drawState().vertexCount(), mesh.count);
                                int stride = mesh.format.getVertexSize();
                                for (int vertex = 0; vertex < mesh.count; vertex++) {
                                    int at = vertex * stride;
                                    for (int axis = 0; axis < 3; axis++)
                                        assertEquals(data.vertexBuffer().getFloat(at + axis * 4),
                                                mesh.transformed().getFloat(at + axis * 4), 2E-5, facing.toString());
                                    for (var element : mesh.format.getElements()) {
                                        if (element == VertexFormatElement.POSITION) continue;
                                        int offset = at + mesh.format.getOffset(element);
                                        for (int b = 0; b < element.byteSize(); b++) {
                                            int expectedByte = data.vertexBuffer().get(offset + b);
                                            int actualByte = mesh.transformed().get(offset + b);
                                            assertTrue(Math.abs(expectedByte - actualByte)
                                                    <= (element == VertexFormatElement.NORMAL ? 1 : 0),
                                                    facing + " " + element + " vertex=" + vertex + " byte=" + b);
                                        }
                                    }
                                }
                            }
                        }
                        assertEquals(before, poses.last().pose());
                    }
                }
            }
            cache.close();
            assertThrows(IllegalStateException.class, mesh::newOutput);
        }
    }

    @Test
    void generatedModelsDeserializeWithOnlySelectedGlowFacesFullBright() throws Exception {
        var shell = BlockModel.fromString(Files.readString(MODELS.resolve("shell.json")));
        int ordinary = 0, glowing = 0;
        for (var element : shell.getElements()) {
            var data = element.getFaceData();
            if (data.blockLight() == 0 && data.skyLight() == 0) {
                ordinary++;
            } else {
                glowing++;
                assertEquals(15, data.blockLight());
                assertEquals(15, data.skyLight());
                assertFalse(data.ambientOcclusion());
                assertFalse(element.shade);
                for (var face : element.faces.values()) {
                    assertEquals(data, face.faceData(), "Glow faces inherit the element's full-bright light");
                    assertEquals("#emissive", face.texture());
                    assertNull(face.cullForDirection());
                }
            }
        }
        assertEquals(119, ordinary);
        assertTrue(glowing > 0 && glowing < ordinary);
        var glass = BlockModel.fromString(Files.readString(MODELS.resolve("glass.json")));
        assertEquals(1, glass.getElements().size());
        assertEquals(0, glass.getElements().getFirst().getFaceData().blockLight());
    }

    @Test
    void cabinetSlicesUseMinecraftFaceVerticesToKeepOneContinuousIslandPerFacade() throws Exception {
        var document = JsonParser.parseString(Files.readString(MODELS.resolve("shell.json")))
                .getAsJsonObject().getAsJsonArray("elements");
        var bakedInput = BlockModel.fromString(Files.readString(MODELS.resolve("shell.json"))).getElements();
        var names = java.util.Set.of("解码机柜 / 后壳", "下部踢板", "上部解码面板", "接口中隔梁", "接口侧立框");
        int checkedFaces = 0;
        for (int index = 0; index < document.size(); index++) {
            var json = document.get(index).getAsJsonObject();
            if (json.has("neoforge_data") || !names.contains(json.get("name").getAsString())) continue;
            var cube = bakedInput.get(index);
            for (var side : Direction.Plane.HORIZONTAL) {
                boolean exterior = switch (side) {
                    case NORTH -> Math.abs(cube.from.z - 5.2F) < 1E-5;
                    case SOUTH -> Math.abs(cube.to.z - 10.8F) < 1E-5;
                    case EAST -> Math.abs(cube.to.x - 10.8F) < 1E-5;
                    case WEST -> Math.abs(cube.from.x - 5.2F) < 1E-5;
                    default -> false;
                };
                if (!exterior) continue;
                checkedFaces++;
                var uv = cube.faces.get(side).uv();
                for (int vertex = 0; vertex < 4; vertex++) {
                    // Use Minecraft's actual FaceInfo and BlockFaceUV instead of the
                    // concept renderer's old, horizontally reversed corner order.
                    var info = FaceInfo.fromFacing(side).getVertexInfo(vertex);
                    double x = (faceCoordinate(info.xFace, cube.from, cube.to) - 8) * 8;
                    double y = faceCoordinate(info.yFace, cube.from, cube.to) * 8;
                    double z = (faceCoordinate(info.zFace, cube.from, cube.to) - 8) * 8;
                    double horizontal = switch (side) {
                        case NORTH -> 22.4 - x;
                        case SOUTH -> x + 22.4;
                        case EAST -> 22.4 - z;
                        case WEST -> z + 22.4;
                        default -> throw new AssertionError(side);
                    };
                    int islandX = side == Direction.NORTH || side == Direction.EAST ? 208 : 272;
                    int islandY = side == Direction.NORTH || side == Direction.SOUTH ? 272 : 320;
                    assertEquals(islandX + .5 + horizontal / 44.8 * 47, uv.getU(vertex) * 32, 2E-4,
                            json.get("name").getAsString() + " " + side + " native U vertex=" + vertex);
                    assertEquals(islandY + .5 + (35.2 - y) / 25.6 * 31, uv.getV(vertex) * 32, 2E-4);
                }
            }
        }
        assertEquals(14, checkedFaces, "Rear plus every front casing slice, including both side strips");
    }

    private static float faceCoordinate(int face, Vector3f from, Vector3f to) {
        return switch (Direction.from3DDataValue(face)) {
            case WEST -> from.x;
            case EAST -> to.x;
            case DOWN -> from.y;
            case UP -> to.y;
            case NORTH -> from.z;
            case SOUTH -> to.z;
        };
    }

    @Test
    void glowKeepsBakedFullBrightnessWithoutBrighteningCasingOrChangingGhostAlpha() {
        var ordinary = quad(Direction.NORTH);
        var glow = quad(Direction.NORTH);
        QuadTransformers.applyingLightmap(15, 15).processInPlace(glow);
        for (float alpha : new float[] {1F, .375F}) {
            var capture = new Capture();
            int worldLight = LightTexture.pack(2, 3);
            StarSeaCalculationModel.drawQuads(new PoseStack(), capture, List.of(ordinary, glow),
                    worldLight, 0, 1, 1, 1, alpha);
            assertEquals(8, capture.positions.size());
            for (int vertex = 0; vertex < 8; vertex++) {
                int expected = vertex < 4 ? worldLight : LightTexture.FULL_BRIGHT;
                assertArrayEquals(new int[] {expected & 65535, expected >>> 16}, capture.lights.get(vertex));
                assertEquals((int) (alpha * 255), capture.colors.get(vertex)[3]);
            }
        }
    }

    private static BakedQuad quad(Direction side) {
        int[] vertices = new int[32];
        for (int corner = 0; corner < 4; corner++) {
            int base = corner * 8;
            vertices[base] = Float.floatToRawIntBits(corner == 1 || corner == 2 ? 1F : 0F);
            vertices[base + 1] = Float.floatToRawIntBits(corner >= 2 ? 1F : 0F);
            vertices[base + 3] = -1;
            vertices[base + 4] = Float.floatToRawIntBits(.125F);
            vertices[base + 5] = Float.floatToRawIntBits(.75F);
        }
        return new BakedQuad(vertices, -1, side, null, true);
    }

    private static Vec3 rotate(Vec3 point, Direction facing) {
        return switch (facing) {
            case NORTH -> point;
            case EAST -> new Vec3(1 - point.z, point.y, point.x);
            case SOUTH -> new Vec3(1 - point.x, point.y, 1 - point.z);
            case WEST -> new Vec3(point.z, point.y, 1 - point.x);
            default -> throw new IllegalArgumentException();
        };
    }

    private static final class Capture implements VertexConsumer {
        final List<float[]> positions = new ArrayList<>();
        final List<int[]> colors = new ArrayList<>();
        final List<float[]> uvs = new ArrayList<>();
        final List<int[]> lights = new ArrayList<>();
        final List<int[]> overlays = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) { positions.add(new float[] {x, y, z}); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { colors.add(new int[] {r, g, b, a}); return this; }
        @Override public VertexConsumer setUv(float u, float v) { uvs.add(new float[] {u, v}); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { overlays.add(new int[] {u, v}); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { lights.add(new int[] {u, v}); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }
}
