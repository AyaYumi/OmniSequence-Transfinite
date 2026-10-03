package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Completes color-only quads with the entity attributes required by Iris shader packs. */
public final class SingularityShaderVertices implements VertexConsumer {
    private final VertexConsumer target;
    private final Vector3f[] positions = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    private final int[] colors = new int[4];
    private int count;

    public SingularityShaderVertices(VertexConsumer target) { this.target = target; }

    @Override public VertexConsumer addVertex(float x, float y, float z) {
        positions[count].set(x, y, z);
        return this;
    }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) {
        colors[count++] = a << 24 | r << 16 | g << 8 | b;
        if (count == 4) {
            var normal = new Vector3f(positions[1]).sub(positions[0])
                    .cross(new Vector3f(positions[2]).sub(positions[0]));
            if (normal.lengthSquared() < 1e-12F)
                normal.set(positions[2]).sub(positions[0]).cross(new Vector3f(positions[3]).sub(positions[0]));
            if (normal.lengthSquared() < 1e-12F) normal.set(0, 1, 0);
            else normal.normalize();
            for (int i = 0; i < 4; i++) {
                var p = positions[i];
                target.addVertex(p.x, p.y, p.z).setColor(colors[i])
                        .setUv(i == 1 || i == 2 ? 1 : 0, i >= 2 ? 1 : 0)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(normal.x, normal.y, normal.z);
            }
            count = 0;
        }
        return this;
    }
    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
}
