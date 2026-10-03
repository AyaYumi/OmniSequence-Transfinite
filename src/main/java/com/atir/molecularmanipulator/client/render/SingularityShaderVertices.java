package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Completes color-only quads with the entity attributes required by shader packs. */
public final class SingularityShaderVertices implements VertexConsumer {
    private final VertexConsumer target;
    private final Vector3f[] positions = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final int[] colors = new int[4];
    private int count;
    private Integer defaultColor;
    public SingularityShaderVertices(VertexConsumer target) { this.target = target; }
    @Override public VertexConsumer vertex(double x, double y, double z) {
        positions[count].set((float)x, (float)y, (float)z);
        if (defaultColor != null) colors[count] = defaultColor;
        return this;
    }
    @Override public VertexConsumer color(int r, int g, int b, int a) {
        colors[count] = a << 24 | r << 16 | g << 8 | b;
        return this;
    }
    @Override public void endVertex() {
        if (++count != 4) return;
        var normal = new Vector3f(positions[1]).sub(positions[0])
                .cross(new Vector3f(positions[2]).sub(positions[0]));
        if (normal.lengthSquared() < 1e-12F)
            normal.set(positions[2]).sub(positions[0]).cross(new Vector3f(positions[3]).sub(positions[0]));
        if (normal.lengthSquared() < 1e-12F) normal.set(0, 1, 0);
        else normal.normalize();
        for (int i = 0; i < 4; i++) {
            var p = positions[i]; int c = colors[i];
            target.vertex(p.x, p.y, p.z).color(c >> 16 & 255, c >> 8 & 255, c & 255, c >>> 24)
                    .uv(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(normal.x, normal.y, normal.z).endVertex();
        }
        count = 0;
    }
    @Override public VertexConsumer uv(float u, float v) { return this; }
    @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
    @Override public VertexConsumer uv2(int u, int v) { return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    @Override public void defaultColor(int r, int g, int b, int a) { defaultColor = a << 24 | r << 16 | g << 8 | b; }
    @Override public void unsetDefaultColor() { defaultColor = null; }
}
