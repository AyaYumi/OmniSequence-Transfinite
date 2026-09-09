import com.atir.molecularmanipulator.client.render.ctm.MatterGoldEmissive;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Hidden OpenGL probe; loads the shipped metadata and uses Minecraft's real sprite ticker. */
public final class TextureAnimationGpuProbe {
    private static final Path TEXTURES = Path.of("src/main/resources/assets/molecularmanipulator/textures/block");

    private static final class Sprite extends TextureAtlasSprite {
        Sprite(SpriteContents contents) {
            super(ResourceLocation.parse("minecraft:textures/atlas/blocks.png"), contents, 16, 16, 0, 0);
        }
    }

    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new AssertionError("GLFW initialization failed");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(32, 32, "Texture verification", 0, 0);
        if (window == 0) throw new AssertionError("Hidden OpenGL context failed");
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            RenderSystem.initRenderThread();
            System.out.println("GPU=" + GL11.glGetString(GL11.GL_RENDERER));
            int count = 0;
            try (var paths = Files.list(TEXTURES)) {
                for (var metadataPath : paths.filter(p -> p.toString().endsWith(".png.mcmeta")).sorted().toList()) {
                    verify(metadataPath);
                    count++;
                }
            }
            if (count != 20) throw new AssertionError("Expected 20 animated textures, got " + count);
            System.out.println("TEXTURE_GPU_ALL_PASS textures=" + count + " ticks=48 mipmaps=4");
        } finally {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static void verify(Path metadataPath) throws Exception {
        String name = metadataPath.getFileName().toString().replace(".png.mcmeta", "");
        ResourceMetadata metadata;
        NativeImage image;
        try (var input = Files.newInputStream(metadataPath)) {
            metadata = ResourceMetadata.fromJsonStream(input);
        }
        try (var input = Files.newInputStream(TEXTURES.resolve(name + ".png"))) {
            image = NativeImage.read(input);
        }
        var location = ResourceLocation.parse("molecularmanipulator:block/" + name);
        try (var contents = new SpriteContents(location, new FrameSize(16, 16), image, metadata)) {
            contents.increaseMipLevel(4);
            if (contents.getUniqueFrames().count() != 24) throw new AssertionError("Invalid frame metadata: " + name);
            int textureId = TextureUtil.generateTextureId();
            ByteBuffer pixels = MemoryUtil.memAlloc(16 * 16 * 4);
            try (var ticker = contents.createTicker()) {
                if (ticker == null) throw new AssertionError("Missing animation ticker: " + name);
                TextureUtil.prepareImage(textureId, 4, 16, 16);
                contents.uploadFirstFrame(0, 0);
                int changes = 0;
                int[] first = null, previous = null;
                for (int tick = 0; tick <= 48; tick++) {
                    GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                    int[] actual = new int[256];
                    int frame = tick / 2 % 24, next = (frame + 1) % 24;
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                        int index = (y * 16 + x) * 4;
                        int expected = image.getPixelRGBA(x, y + frame * 16);
                        int following = image.getPixelRGBA(x, y + next * 16);
                        int packed = 0;
                        for (int channel = 0; channel < 4; channel++) {
                            int value = pixels.get(index + channel) & 255;
                            int a = expected >>> (channel * 8) & 255;
                            int b = following >>> (channel * 8) & 255;
                            int wanted = tick % 2 == 1 && channel != 3 ? (a + b) / 2 : a;
                            if (value != wanted) throw new AssertionError(name + " upload mismatch tick=" + tick + " x=" + x + " y=" + y);
                            packed |= value << (channel * 8);
                        }
                        actual[y * 16 + x] = packed;
                    }
                    if (tick == 0) first = actual;
                    if (previous != null && !Arrays.equals(previous, actual)) changes++;
                    if (tick == 48 && !Arrays.equals(first, actual)) throw new AssertionError("Loop did not close: " + name);
                    previous = actual;
                    if (tick < 48) ticker.tickAndUpload(0, 0);
                }
                if (changes < 12) throw new AssertionError("Animation did not advance: " + name);
                if (name.startsWith("matter_fabrication_")) verifyEmission(new Sprite(contents), image, location);
                if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new AssertionError("OpenGL error: " + name);
                System.out.println("PASS " + name + " changedTicks=" + changes);
            } finally {
                MemoryUtil.memFree(pixels);
                TextureUtil.releaseTextureId(textureId);
            }
        }
    }

    private static void verifyEmission(TextureAtlasSprite sprite, NativeImage image, ResourceLocation location) {
        int[] vertices = new int[IQuadTransformer.STRIDE * 4];
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            vertices[offset + IQuadTransformer.POSITION] = Float.floatToRawIntBits(corners[vertex][0]);
            vertices[offset + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(corners[vertex][1]);
            vertices[offset + IQuadTransformer.COLOR] = -1;
            vertices[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU(corners[vertex][0]));
            vertices[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV(corners[vertex][1]));
        }
        var quad = new BakedQuad(vertices, -1, Direction.SOUTH, sprite, true, true);
        var regions = new MatterGoldEmissive().apply(List.of(quad));
        int glowing = 0, neutral = 0;
        for (var region : regions) {
            int[] data = region.getVertices();
            float u = 0, v = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                u += sprite.getUOffset(Float.intBitsToFloat(data[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0])) / 4;
                v += sprite.getVOffset(Float.intBitsToFloat(data[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0 + 1])) / 4;
            }
            boolean expected = MatterGoldEmissive.isEmissivePixel(location, image.getPixelRGBA((int)(u * 16), (int)(v * 16)));
            boolean actual = MatterGoldEmissive.isEmissive(region);
            if (expected != actual) throw new AssertionError("Wrong animated emissive region: " + location);
            if (actual) glowing++; else neutral++;
        }
        if (glowing == 0 || neutral == 0) throw new AssertionError("Missing material partition: " + location);
    }
}
