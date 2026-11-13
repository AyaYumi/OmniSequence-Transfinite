package com.atir.molecularmanipulator.client.gui.theme;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.util.GsonHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Batch native pixel sprites separately from filtered, continuous frames on 1.20.1 textures. */
public final class OsqSpriteRenderer {
    private record Key(ResourceLocation id, int width, int height, boolean filtered) {}
    private record Mesh(ResourceLocation texture, float[] vertices, boolean filtered) {}
    private record Sprite(ResourceLocation texture, Scaling scaling) {}
    private record Pixels(int width, int height) {}
    record Scaling(String type, int width, int height, int left, int top, int right, int bottom) {
        private static final Scaling STRETCH = new Scaling("stretch", 1, 1, 0, 0, 0, 0);
    }
    private static final MetadataSectionSerializer<Scaling> GUI_METADATA = new MetadataSectionSerializer<>() {
        @Override public String getMetadataSectionName() { return "gui"; }
        @Override public Scaling fromJson(JsonObject json) {
            if (!json.has("scaling")) return Scaling.STRETCH;
            var scaling = GsonHelper.getAsJsonObject(json, "scaling");
            String type = GsonHelper.getAsString(scaling, "type", "stretch");
            if (type.equals("stretch")) return Scaling.STRETCH;
            int width = GsonHelper.getAsInt(scaling, "width"), height = GsonHelper.getAsInt(scaling, "height");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Non-positive GUI sprite dimensions");
            if (type.equals("tile")) return new Scaling(type, width, height, 0, 0, 0, 0);
            if (!type.equals("nine_slice")) throw new IllegalArgumentException("Unknown GUI sprite scaling: " + type);
            var border = scaling.get("border");
            int left, top, right, bottom;
            if (border.isJsonPrimitive()) {
                left = top = right = bottom = border.getAsInt();
            } else {
                var edges = border.getAsJsonObject();
                left = GsonHelper.getAsInt(edges, "left");
                top = GsonHelper.getAsInt(edges, "top");
                right = GsonHelper.getAsInt(edges, "right");
                bottom = GsonHelper.getAsInt(edges, "bottom");
            }
            if (left < 0 || top < 0 || right < 0 || bottom < 0 || left + right >= width || top + bottom >= height)
                throw new IllegalArgumentException("Invalid GUI sprite borders");
            return new Scaling(type, width, height, left, top, right, bottom);
        }
    };
    private static final Map<Key, Mesh> CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Mesh> entry) { return size() > 256; }
    };
    private static final Map<ResourceLocation, Sprite> SPRITES = new HashMap<>();
    private static final Map<ResourceLocation, Pixels> PIXELS = new HashMap<>();

    private static Batch active;
    private OsqSpriteRenderer() {}

    public static void clear() {
        CACHE.clear();
        SPRITES.clear();
        PIXELS.clear();
    }

    /** Finish queued frames before drawing a reflection or changing shader state. */
    public static void flush() { if (active != null) active.flush(); }

    /** Callers flush before text, fills, items or scissors. */
    public static Batch batch() {
        if (active != null) throw new IllegalStateException("Nested OSQ sprite batch");
        return active = new Batch();
    }

    private static BufferBuilder begin() {
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        return buffer;
    }

    public static final class Batch implements AutoCloseable {
        private BufferBuilder buffer;
        private ResourceLocation texture;
        private boolean filtered, closed;

        private Batch() {}

        private BufferBuilder buffer(ResourceLocation nextTexture, boolean nextFiltered) {
            if (buffer != null && (!texture.equals(nextTexture) || filtered != nextFiltered)) flush();
            if (buffer == null) {
                texture = nextTexture;
                filtered = nextFiltered;
                buffer = begin();
            }
            return buffer;
        }

        public void flush() {
            if (buffer == null) return;
            var pending = buffer;
            buffer = null;
            submit(pending, texture, filtered);
        }

        @Override public void close() {
            if (closed) return;
            try { flush(); }
            finally { active = null; closed = true; }
        }
    }

    /** Linear sampling belongs to this draw; preserve the texture's filters and the caller's binding. */
    private static void submit(BufferBuilder pending, ResourceLocation id, boolean filtered) {
        if (!filtered) {
            RenderSystem.setShaderTexture(0, id);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            BufferUploader.drawWithShader(pending.end());
            return;
        }
        int activeTexture = GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            RenderSystem.setShaderTexture(0, id);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            var texture = Minecraft.getInstance().getTextureManager().getTexture(id);
            texture.bind();
            int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            try {
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                BufferUploader.drawWithShader(pending.end());
            } finally {
                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                texture.bind();
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
                RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
            }
        } finally {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(binding);
            RenderSystem.activeTexture(activeTexture);
        }
    }

    public static void draw(GuiGraphics graphics, ResourceLocation id, int x, int y, int width, int height) {
        drawRegion(graphics, id, x, y, width, height, 0, 0, width, height, false);
    }

    static void drawMaterial(GuiGraphics graphics, ResourceLocation id, int width, int height) {
        drawMaterialRegion(graphics, id, 0, 0, width, height, 0, 0, width, height);
    }

    static void drawMaterialRegion(GuiGraphics graphics, ResourceLocation id, float x, float y, int width, int height,
            float left, float top, float right, float bottom) {
        drawRegion(graphics, id, x, y, width, height, left, top, right, bottom, true);
    }

    /** Crop a tessellated frame in local coordinates without adding scissors or changing its thickness. */
    private static void drawRegion(GuiGraphics graphics, ResourceLocation id, float x, float y, int width, int height,
            float left, float top, float right, float bottom, boolean filtered) {
        left = Math.max(0, left); top = Math.max(0, top);
        right = Math.min(width, right); bottom = Math.min(height, bottom);
        if (right <= left || bottom <= top) return;
        var mesh = CACHE.computeIfAbsent(new Key(id, width, height, filtered), OsqSpriteRenderer::build);
        if (mesh.vertices.length == 0) return;
        var matrix = graphics.pose().last().pose();
        var scope = active;
        var buffer = scope == null ? begin() : scope.buffer(mesh.texture, mesh.filtered);
        var data = mesh.vertices;
        for (int i = 0; i < data.length; i += 16) {
            float l = Math.max(left, data[i]), t = Math.max(top, data[i+1]);
            float r = Math.min(right, data[i+8]), b = Math.min(bottom, data[i+5]);
            if (r <= l || b <= t) continue;
            float du = (data[i+10] - data[i+2]) / (data[i+8] - data[i]);
            float dv = (data[i+7] - data[i+3]) / (data[i+5] - data[i+1]);
            float u0 = data[i+2] + (l - data[i]) * du, u1 = data[i+2] + (r - data[i]) * du;
            float v0 = data[i+3] + (t - data[i+1]) * dv, v1 = data[i+3] + (b - data[i+1]) * dv;
            buffer.vertex(matrix, x+l, y+t, 0).uv(u0, v0).endVertex();
            buffer.vertex(matrix, x+l, y+b, 0).uv(u0, v1).endVertex();
            buffer.vertex(matrix, x+r, y+b, 0).uv(u1, v1).endVertex();
            buffer.vertex(matrix, x+r, y+t, 0).uv(u1, v0).endVertex();
        }
        if (scope == null) submit(buffer, mesh.texture, mesh.filtered);
    }

    /** A fixed-size crop of the independent light field, using the same isolated linear sampling. */
    static void drawReflection(GuiGraphics graphics, ResourceLocation id, int fieldWidth, int fieldHeight,
            int u, int v, int x, int y, int width, int height) {
        var sprite = sprite(id);
        var pixels = PIXELS.computeIfAbsent(sprite.texture, OsqSpriteRenderer::readPixels);
        float halfU = .5f/pixels.width, halfV = .5f/pixels.height;
        float u0 = Math.max(halfU, u/(float)fieldWidth);
        float v0 = Math.max(halfV, v/(float)fieldHeight);
        float u1 = Math.min(1-halfU, (u+width)/(float)fieldWidth);
        float v1 = Math.min(1-halfV, (v+height)/(float)fieldHeight);
        var matrix = graphics.pose().last().pose();
        var buffer = begin();
        buffer.vertex(matrix, x, y, 0).uv(u0, v0).endVertex();
        buffer.vertex(matrix, x, y+height, 0).uv(u0, v1).endVertex();
        buffer.vertex(matrix, x+width, y+height, 0).uv(u1, v1).endVertex();
        buffer.vertex(matrix, x+width, y, 0).uv(u1, v0).endVertex();
        submit(buffer, sprite.texture, true);
    }

    /** Material reflection bounds use the same resource-pack metadata as the frame mesh. */
    static Scaling scaling(ResourceLocation id) { return sprite(id).scaling; }

    private static Sprite sprite(ResourceLocation id) {
        return SPRITES.computeIfAbsent(id, OsqSpriteRenderer::readSprite);
    }

    private static Sprite readSprite(ResourceLocation id) {
        var texture = new ResourceLocation(id.getNamespace(), "textures/gui/sprites/" + id.getPath() + ".png");
        Scaling scaling = Scaling.STRETCH;
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isPresent()) scaling = resource.get().metadata().getSection(GUI_METADATA).orElse(Scaling.STRETCH);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Cannot read GUI sprite metadata: " + texture, exception);
        }
        return new Sprite(texture, scaling);
    }

    /** Decode only filtered textures, cache their actual PNG dimensions, and release native image memory. */
    private static Pixels readPixels(ResourceLocation texture) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            var image = MissingTextureAtlasSprite.getTexture().getPixels();
            return new Pixels(image.getWidth(), image.getHeight());
        }
        try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
            return new Pixels(image.getWidth(), image.getHeight());
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException("Cannot read GUI sprite pixels: " + texture, exception);
        }
    }

    private static Mesh build(Key key) {
        var sprite = sprite(key.id);
        var scaling = sprite.scaling;
        var quads = switch (scaling.type) {
            case "nine_slice" -> key.filtered
                    ? OsqSpriteGeometry.frame(key.width, key.height, scaling.width, scaling.height,
                            scaling.left, scaling.top, scaling.right, scaling.bottom)
                    : OsqSpriteGeometry.nineSlice(key.width, key.height, scaling.width, scaling.height,
                            scaling.left, scaling.top, scaling.right, scaling.bottom);
            case "tile" -> OsqSpriteGeometry.tile(key.width, key.height, scaling.width, scaling.height);
            default -> OsqSpriteGeometry.stretch(key.width, key.height);
        };
        var pixels = key.filtered ? PIXELS.computeIfAbsent(sprite.texture, OsqSpriteRenderer::readPixels) : null;
        float[] vertices = new float[quads.size() * 16];
        int i = 0;
        for (var quad : quads) {
            float u0 = quad.u0(), u1 = quad.u1(), v0 = quad.v0(), v1 = quad.v1();
            if (key.filtered) {
                // Use physical PNG texel centers, independent of the metadata's logical GUI dimensions.
                float du = Math.min((u1-u0)/2, .5f/pixels.width);
                float dv = Math.min((v1-v0)/2, .5f/pixels.height);
                u0 += du; u1 -= du; v0 += dv; v1 -= dv;
            }
            float x = quad.x(), y = quad.y(), right = x+quad.width(), bottom = y+quad.height();
            for (float value : new float[]{x,y,u0,v0, x,bottom,u0,v1, right,bottom,u1,v1, right,y,u1,v0})
                vertices[i++] = value;
        }
        return new Mesh(sprite.texture, vertices, key.filtered);
    }
}
