package com.atir.molecularmanipulator.client.gui.theme;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Batch native pixel sprites separately from filtered, continuous material frames. */
public final class OsqSpriteRenderer {
    private record Key(ResourceLocation id, int width, int height, boolean filtered) {}
    private record Mesh(ResourceLocation atlas, float[] vertices, boolean filtered) {}
    private static final Map<Key, Mesh> CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Mesh> entry) { return size() > 256; }
    };

    private static Batch active;
    private OsqSpriteRenderer() {}

    public static void clear() { CACHE.clear(); }

    /** Finish queued frames before drawing a reflection or changing shader state. */
    public static void flush() { if (active != null) active.flush(); }

    /** Limited to consecutive layout sprites; callers flush before text, fills, items or scissors. */
    public static Batch batch() {
        if (active != null) throw new IllegalStateException("Nested OSQ sprite batch");
        return active = new Batch();
    }

    public static final class Batch implements AutoCloseable {
        private BufferBuilder buffer;
        private ResourceLocation atlas;
        private boolean filtered, closed;

        private Batch() {}

        private BufferBuilder buffer(ResourceLocation nextAtlas, boolean nextFiltered) {
            if (buffer != null && (!atlas.equals(nextAtlas) || filtered != nextFiltered)) flush();
            if (buffer == null) {
                atlas = nextAtlas;
                filtered = nextFiltered;
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            }
            return buffer;
        }

        public void flush() {
            if (buffer == null) return;
            var pending = buffer;
            buffer = null;
            submit(pending, atlas, filtered);
        }

        @Override public void close() {
            if (closed) return;
            try { flush(); }
            finally { active = null; closed = true; }
        }
    }

    /** Filtering is draw-local: never leave the shared GUI atlas blurred for text, icons or controls. */
    private static void submit(BufferBuilder pending, ResourceLocation atlas, boolean filtered) {
        RenderSystem.setShaderTexture(0, atlas);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        if (!filtered) {
            BufferUploader.drawWithShader(pending.buildOrThrow());
            return;
        }
        var texture = Minecraft.getInstance().getTextureManager().getTexture(atlas);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        texture.bind();
        int min = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int mag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        try {
            BufferUploader.drawWithShader(pending.buildOrThrow());
        } finally {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            texture.bind();
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, min);
            RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mag);
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
        var buffer = scope == null
                ? Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
                : scope.buffer(mesh.atlas, mesh.filtered);
        var data = mesh.vertices;
        for (int i = 0; i < data.length; i += 16) {
            float l = Math.max(left, data[i]), t = Math.max(top, data[i+1]);
            float r = Math.min(right, data[i+8]), b = Math.min(bottom, data[i+5]);
            if (r <= l || b <= t) continue;
            float du = (data[i+10] - data[i+2]) / (data[i+8] - data[i]);
            float dv = (data[i+7] - data[i+3]) / (data[i+5] - data[i+1]);
            float u0 = data[i+2] + (l - data[i]) * du, u1 = data[i+2] + (r - data[i]) * du;
            float v0 = data[i+3] + (t - data[i+1]) * dv, v1 = data[i+3] + (b - data[i+1]) * dv;
            buffer.addVertex(matrix, x+l, y+t, 0).setUv(u0, v0);
            buffer.addVertex(matrix, x+l, y+b, 0).setUv(u0, v1);
            buffer.addVertex(matrix, x+r, y+b, 0).setUv(u1, v1);
            buffer.addVertex(matrix, x+r, y+t, 0).setUv(u1, v0);
        }
        if (scope == null) submit(buffer, mesh.atlas, mesh.filtered);
    }

    /** A fixed-size crop of the independent light field, using the same isolated linear sampling. */
    static void drawReflection(GuiGraphics graphics, ResourceLocation id, int fieldWidth, int fieldHeight,
            int u, int v, int x, int y, int width, int height) {
        var sprite = Minecraft.getInstance().getGuiSprites().getSprite(id);
        float halfU = .5f/sprite.contents().width(), halfV = .5f/sprite.contents().height();
        float u0 = sprite.getU(Math.max(halfU, u/(float)fieldWidth));
        float v0 = sprite.getV(Math.max(halfV, v/(float)fieldHeight));
        float u1 = sprite.getU(Math.min(1-halfU, (u+width)/(float)fieldWidth));
        float v1 = sprite.getV(Math.min(1-halfV, (v+height)/(float)fieldHeight));
        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        buffer.addVertex(matrix, x, y, 0).setUv(u0, v0);
        buffer.addVertex(matrix, x, y+height, 0).setUv(u0, v1);
        buffer.addVertex(matrix, x+width, y+height, 0).setUv(u1, v1);
        buffer.addVertex(matrix, x+width, y, 0).setUv(u1, v0);
        submit(buffer, sprite.atlasLocation(), true);
    }

    private static Mesh build(Key key) {
        var manager = Minecraft.getInstance().getGuiSprites();
        TextureAtlasSprite sprite = manager.getSprite(key.id);
        var scaling = manager.getSpriteScaling(sprite);
        var quads = scaling instanceof GuiSpriteScaling.NineSlice slice
                ? key.filtered
                        ? OsqSpriteGeometry.frame(key.width, key.height, slice.width(), slice.height(),
                                slice.border().left(), slice.border().top(), slice.border().right(), slice.border().bottom())
                        : OsqSpriteGeometry.nineSlice(key.width, key.height, slice.width(), slice.height(),
                                slice.border().left(), slice.border().top(), slice.border().right(), slice.border().bottom())
                : scaling instanceof GuiSpriteScaling.Tile tile
                        ? OsqSpriteGeometry.tile(key.width, key.height, tile.width(), tile.height())
                        : OsqSpriteGeometry.stretch(key.width, key.height);
        float[] vertices = new float[quads.size() * 16];
        int i = 0;
        for (var quad : quads) {
            float u0 = quad.u0(), u1 = quad.u1(), v0 = quad.v0(), v1 = quad.v1();
            if (key.filtered) {
                // Stay inside each slice's texel centers: no neighboring atlas sprite or corner-row bleed.
                float du = Math.min((u1-u0)/2, .5f/sprite.contents().width());
                float dv = Math.min((v1-v0)/2, .5f/sprite.contents().height());
                u0 += du; u1 -= du; v0 += dv; v1 -= dv;
            }
            u0 = sprite.getU(u0); u1 = sprite.getU(u1);
            v0 = sprite.getV(v0); v1 = sprite.getV(v1);
            float x = quad.x(), y = quad.y(), right = x+quad.width(), bottom = y+quad.height();
            for (float value : new float[]{x,y,u0,v0, x,bottom,u0,v1, right,bottom,u1,v1, right,y,u1,v0})
                vertices[i++] = value;
        }
        return new Mesh(sprite.atlasLocation(), vertices, key.filtered);
    }
}
