package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.client.gui.theme.OsqSpriteRenderer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.JsonOps;
import java.awt.image.BufferedImage;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/** Executes real widget code without a Minecraft client, window or OpenGL context. */
public class UiRenderRecorder extends GuiGraphics {
    static {
        // JUnit has no mod-discovery phase; vanilla bootstrap still asks NeoForge for feature flags.
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // GuiGraphics 1.21 reads the client's sprite manager in its constructor.
        if (net.minecraft.client.Minecraft.getInstance() == null) {
            try {
                var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                var client = ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(net.minecraft.client.Minecraft.class);
                var instance = net.minecraft.client.Minecraft.class.getDeclaredField("instance");
                instance.setAccessible(true);
                instance.set(null, client);
            } catch (ReflectiveOperationException exception) { throw new ExceptionInInitializerError(exception); }
        }
    }

    private static void ensureBootstrapped() { }

    public static BufferedImage surface(JsonObject json) throws Exception {
        var entry = new OsqLayout.Entry();
        entry.sprite = json.get("sprite").getAsString();
        entry.material = json.get("material").getAsString();
        var rect = json.getAsJsonArray("rect");
        entry.x = rect.get(0).getAsFloat(); entry.y = rect.get(1).getAsFloat();
        entry.w = rect.get(2).getAsFloat(); entry.h = rect.get(3).getAsFloat();
        if (json.has("pad")) for (int i = 0; i < 4; i++) entry.pad[i] = json.getAsJsonArray("pad").get(i).getAsInt();
        return surface(entry);
    }

    /** Capture the production frame/contour vertices before GPU submission, then sample the bundled PNG.
     * Reflections are checked separately; this exposes the frame's own alpha, including translucent cards. */
    public static BufferedImage surface(OsqLayout.Entry entry) throws Exception {
        var file = Path.of("src/main/resources/assets/molecularmanipulator/textures/gui/sprites/"
                + entry.sprite.split(":", 2)[1] + ".png");
        var pixels = ImageIO.read(file.toFile());
        var metadata = JsonParser.parseString(Files.readString(Path.of(file + ".mcmeta"))).getAsJsonObject();
        var scaling = GuiSpriteScaling.CODEC.parse(JsonOps.INSTANCE,
                metadata.getAsJsonObject("gui").get("scaling")).getOrThrow();
        int width = Math.round(entry.w) + entry.pad[0] + entry.pad[2];
        int height = Math.round(entry.h) + entry.pad[1] + entry.pad[3];
        var graphics = new UiRenderRecorder();
        var client = net.minecraft.client.Minecraft.getInstance();
        var managerField = client.getClass().getDeclaredField("guiSprites");
        managerField.setAccessible(true);
        var previousManager = managerField.get(client);
        var cacheField = OsqSpriteRenderer.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked") var cache = (Map<Object, Object>) cacheField.get(null);
        var previousCache = new LinkedHashMap<>(cache);
        var manager = ResponsiveScreenTestFixture.allocate(SurfaceSprites.class);
        manager.scaling = scaling;
        var id = ResourceLocation.parse(entry.sprite);
        try (var contents = new SpriteContents(id, new FrameSize(pixels.getWidth(), pixels.getHeight()),
                new NativeImage(pixels.getWidth(), pixels.getHeight(), false), ResourceMetadata.EMPTY);
                var bytes = new ByteBufferBuilder(4096);
                var batch = OsqSpriteRenderer.batch()) {
            manager.sprite = new SurfaceSprite(contents);
            managerField.set(client, manager);
            cache.clear();
            var buffer = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            var pending = batch.getClass().getDeclaredField("buffer"); pending.setAccessible(true);
            var atlas = batch.getClass().getDeclaredField("atlas"); atlas.setAccessible(true);
            var filtered = batch.getClass().getDeclaredField("filtered"); filtered.setAccessible(true);
            pending.set(batch, buffer); atlas.set(batch, manager.sprite.atlasLocation()); filtered.set(batch, true);
            var frame = new OsqLayout.Entry();
            frame.x = entry.x; frame.y = entry.y; frame.pad = entry.pad;
            frame.joinedTab = entry.joinedTab;
            // Only the reflection pass branches on material kind; the frame uses the real selected sprite.
            frame.material = "card";
            try {
                var draw = Class.forName("com.atir.molecularmanipulator.client.gui.theme.OsqMaterialSurface")
                        .getDeclaredMethod("draw", GuiGraphics.class, OsqLayout.Entry.class, ResourceLocation.class, int.class, int.class);
                draw.setAccessible(true);
                draw.invoke(null, graphics, frame, id, width, height);
                try (var mesh = buffer.buildOrThrow()) {
                    var vertices = mesh.vertexBuffer().order(ByteOrder.nativeOrder());
                    int stride = mesh.drawState().format().getVertexSize();
                    var result = new BufferedImage(width * 4, height * 4, BufferedImage.TYPE_INT_ARGB);
                    for (int i = 0; i < mesh.drawState().vertexCount(); i += 4) {
                        int a = i * stride, b = (i + 2) * stride;
                        float left = vertices.getFloat(a), top = vertices.getFloat(a + 4);
                        float right = vertices.getFloat(b), bottom = vertices.getFloat(b + 4);
                        float u0 = vertices.getFloat(a + 12), v0 = vertices.getFloat(a + 16);
                        float u1 = vertices.getFloat(b + 12), v1 = vertices.getFloat(b + 16);
                        for (int y = Math.max(0, (int)Math.ceil(top * 4 - .5)); y < Math.min(result.getHeight(), (int)Math.ceil(bottom * 4 - .5)); y++)
                            for (int x = Math.max(0, (int)Math.ceil(left * 4 - .5)); x < Math.min(result.getWidth(), (int)Math.ceil(right * 4 - .5)); x++) {
                                float u = u0 + (u1 - u0) * ((x + .5f) / 4 - left) / (right - left);
                                float v = v0 + (v1 - v0) * ((y + .5f) / 4 - top) / (bottom - top);
                                result.setRGB(x, y, bilinear(pixels, u * pixels.getWidth() - .5f, v * pixels.getHeight() - .5f));
                            }
                    }
                    return result;
                }
            } finally {
                // The native mesh was consumed by this recorder, so closing the batch must not submit it to GL.
                pending.set(batch, null);
            }
        } finally {
            managerField.set(client, previousManager);
            cache.clear(); cache.putAll(previousCache);
        }
    }

    private static int bilinear(BufferedImage image, float x, float y) {
        x = Math.clamp(x, 0, image.getWidth() - 1); y = Math.clamp(y, 0, image.getHeight() - 1);
        int left = (int)x, top = (int)y, right = Math.min(left + 1, image.getWidth() - 1), bottom = Math.min(top + 1, image.getHeight() - 1);
        float dx = x - left, dy = y - top;
        int result = 0;
        for (int shift : new int[]{0, 8, 16, 24}) {
            float first = (image.getRGB(left, top) >>> shift & 255) * (1 - dx) + (image.getRGB(right, top) >>> shift & 255) * dx;
            float second = (image.getRGB(left, bottom) >>> shift & 255) * (1 - dx) + (image.getRGB(right, bottom) >>> shift & 255) * dx;
            result |= Math.round(first * (1 - dy) + second * dy) << shift;
        }
        return result;
    }

    private static final class SurfaceSprite extends TextureAtlasSprite {
        SurfaceSprite(SpriteContents contents) {
            super(ResourceLocation.withDefaultNamespace("textures/atlas/gui.png"), contents,
                    contents.width(), contents.height(), 0, 0);
        }
    }

    private static final class SurfaceSprites extends GuiSpriteManager {
        private TextureAtlasSprite sprite;
        private GuiSpriteScaling scaling;
        private SurfaceSprites() { super(null); }
        @Override public TextureAtlasSprite getSprite(ResourceLocation id) { return sprite; }
        @Override public GuiSpriteScaling getSpriteScaling(TextureAtlasSprite sprite) { return scaling; }
    }

    public record Text(String value, int x, int y, int color, boolean shadow, Matrix4f transform) { }
    public record Rect(int left, int top, int right, int bottom, int color, boolean selection, Matrix4f transform) { }
    public record Item(net.minecraft.world.item.ItemStack stack, int x, int y, Matrix4f transform) { }
    public final List<Text> text = new ArrayList<>();
    public final List<Rect> rects = new ArrayList<>();
    public final List<Item> items = new ArrayList<>();
    public final List<net.minecraft.client.renderer.Rect2i> scissors = new ArrayList<>();
    public int clipDepth;
    public int clips;
    public int flushes;

    @Override public void flush() { flushes++; }

    public UiRenderRecorder() { super(net.minecraft.client.Minecraft.getInstance(), null); }

    @Override public void renderItem(net.minecraft.world.item.ItemStack stack, int x, int y) {
        items.add(new Item(stack, x, y, new Matrix4f(pose().last().pose())));
    }

    @Override public int drawString(Font font, String value, int x, int y, int color, boolean shadow) {
        if (value == null) return 0;
        text.add(new Text(value, x, y, color, shadow, new Matrix4f(pose().last().pose())));
        return x + font.width(value) + (shadow ? 1 : 0);
    }

    @Override public int drawString(Font font, FormattedCharSequence value, int x, int y, int color, boolean shadow) {
        int[] resolved = {color};
        value.accept((index, style, codePoint) -> {
            if (style.getColor() != null) resolved[0] = color & 0xFF000000 | style.getColor().getValue();
            return false;
        });
        return drawString(font, string(value), x, y, resolved[0], shadow);
    }

    @Override public int drawString(Font font, Component value, int x, int y, int color, boolean shadow) {
        return drawString(font, value.getVisualOrderText(), x, y, color, shadow);
    }

    @Override public void fill(int left, int top, int right, int bottom, int color) {
        rects.add(new Rect(left, top, right, bottom, color, false, new Matrix4f(pose().last().pose())));
    }

    @Override public void fill(RenderType type, int left, int top, int right, int bottom, int color) {
        rects.add(new Rect(left, top, right, bottom, color, true, new Matrix4f(pose().last().pose())));
    }

    @Override public void enableScissor(int left, int top, int right, int bottom) {
        clipDepth++; clips++;
        scissors.add(new net.minecraft.client.renderer.Rect2i(left, top, right - left, bottom - top));
    }
    @Override public void disableScissor() { clipDepth--; }

    public static String string(FormattedCharSequence sequence) {
        var result = new StringBuilder();
        sequence.accept((index, style, codePoint) -> { result.appendCodePoint(codePoint); return true; });
        return result.toString();
    }

    /** Deterministic glyph advances; tests verify drawing contracts, not a replacement game font. */
    public static final class MetricsFont extends Font {
        public MetricsFont() { super(id -> null, false); ensureBootstrapped(); }
        private static int advance(int codePoint) { return codePoint >= 0x2E80 ? 9 : 6; }
        @Override public int width(String value) { return value.codePoints().map(MetricsFont::advance).sum(); }
        @Override public int width(FormattedText value) { return width(value.getString()); }
        @Override public int width(FormattedCharSequence value) { return width(string(value)); }
        @Override public String plainSubstrByWidth(String value, int width) { return plainSubstrByWidth(value, width, false); }
        @Override public String plainSubstrByWidth(String value, int width, boolean reverse) {
            int length = 0, used = 0;
            if (reverse) {
                int start = value.length();
                while (start > 0) {
                    int cp = value.codePointBefore(start);
                    if (used + advance(cp) > width) break;
                    used += advance(cp); start -= Character.charCount(cp);
                }
                return value.substring(start);
            }
            while (length < value.length()) {
                int cp = value.codePointAt(length);
                if (used + advance(cp) > width) break;
                used += advance(cp); length += Character.charCount(cp);
            }
            return value.substring(0, length);
        }
    }
}
