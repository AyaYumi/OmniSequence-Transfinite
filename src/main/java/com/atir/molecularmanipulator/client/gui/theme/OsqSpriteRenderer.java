package com.atir.molecularmanipulator.client.gui.theme;

import com.mojang.blaze3d.systems.RenderSystem;
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

/** Submit all nine-slice tiles together, not one GL draw per eight-pixel tile. */
public final class OsqSpriteRenderer {
    private record Key(ResourceLocation id, int width, int height) {}
    private record Mesh(ResourceLocation atlas, float[] vertices) {}
    private static final Map<Key, Mesh> CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, Mesh> entry) { return size() > 256; }
    };

    private OsqSpriteRenderer() {}

    public static void clear() { CACHE.clear(); }

    public static void draw(GuiGraphics graphics, ResourceLocation id, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) return;
        var mesh = CACHE.computeIfAbsent(new Key(id, width, height), OsqSpriteRenderer::build);
        if (mesh.vertices.length == 0) return;
        // Identical shader, blend/depth/scissor state, UVs, pose and ordering to GuiGraphics.innerBlit.
        RenderSystem.setShaderTexture(0, mesh.atlas);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        var data = mesh.vertices;
        for (int i = 0; i < data.length; i += 4)
            buffer.addVertex(matrix, x+data[i], y+data[i+1], 0).setUv(data[i+2], data[i+3]);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static Mesh build(Key key) {
        var manager = Minecraft.getInstance().getGuiSprites();
        TextureAtlasSprite sprite = manager.getSprite(key.id);
        var scaling = manager.getSpriteScaling(sprite);
        var quads = scaling instanceof GuiSpriteScaling.NineSlice slice
                ? OsqSpriteGeometry.nineSlice(key.width, key.height, slice.width(), slice.height(),
                        slice.border().left(), slice.border().top(), slice.border().right(), slice.border().bottom())
                : scaling instanceof GuiSpriteScaling.Tile tile
                        ? OsqSpriteGeometry.tile(key.width, key.height, tile.width(), tile.height())
                        : OsqSpriteGeometry.stretch(key.width, key.height);
        float[] vertices = new float[quads.size() * 16];
        int i = 0;
        for (var quad : quads) {
            float u0 = sprite.getU(quad.u0()), u1 = sprite.getU(quad.u1());
            float v0 = sprite.getV(quad.v0()), v1 = sprite.getV(quad.v1());
            float x = quad.x(), y = quad.y(), right = x+quad.width(), bottom = y+quad.height();
            for (float value : new float[]{x,y,u0,v0, x,bottom,u0,v1, right,bottom,u1,v1, right,y,u1,v0})
                vertices[i++] = value;
        }
        return new Mesh(sprite.atlasLocation(), vertices);
    }
}
