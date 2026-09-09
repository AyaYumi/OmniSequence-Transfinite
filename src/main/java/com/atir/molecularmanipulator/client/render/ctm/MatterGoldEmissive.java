package com.atir.molecularmanipulator.client.render.ctm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Partitions final CTM faces at original texel boundaries; gold and fluid-port blue markings are full-bright. */
public final class MatterGoldEmissive {
    private final Map<TextureAtlasSprite, List<Region>> masks = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<BakedQuad, List<BakedQuad>> quads = Collections.synchronizedMap(new IdentityHashMap<>());
    private record Region(int x0, int y0, int x1, int y1, boolean emissive) {}

    public List<BakedQuad> apply(List<BakedQuad> source) {
        if (source.isEmpty()) return source;
        var result = new ArrayList<BakedQuad>();
        boolean changed = false;
        for (var quad : source) {
            var fragments = quads.computeIfAbsent(quad, this::partition);
            changed |= fragments.size() != 1 || fragments.getFirst() != quad;
            result.addAll(fragments);
        }
        return changed ? List.copyOf(result) : source;
    }

    /** NativeImage stores ABGR. Neutral pearl/silver and transparent texels never enter the emissive mask. */
    public static boolean isGoldPixel(int abgr) {
        int alpha = abgr >>> 24, red = abgr & 255, green = abgr >>> 8 & 255, blue = abgr >>> 16 & 255;
        return alpha >= 128 && red >= 100 && green >= 85 && red - blue >= 20
                && green - blue >= 7 && red >= green - 4 && red - green <= 75;
    }

    public static boolean isBluePixel(int abgr) {
        int alpha = abgr >>> 24, red = abgr & 255, green = abgr >>> 8 & 255, blue = abgr >>> 16 & 255;
        return alpha >= 128 && blue >= 70 && green >= 50 && blue - red >= 25
                && blue - green >= 12 && green - red >= 12;
    }

    public static boolean isEmissivePixel(ResourceLocation texture, int abgr) {
        if (!texture.getNamespace().equals("molecularmanipulator") || !texture.getPath().startsWith("block/matter_fabrication_")) return false;
        boolean fluidPort = texture.getPath().equals("block/matter_fabrication_fluid_input")
                || texture.getPath().equals("block/matter_fabrication_fluid_output");
        return isGoldPixel(abgr) || fluidPort && isBluePixel(abgr);
    }

    public static boolean isEmissive(BakedQuad quad) {
        if (quad.isShade() || quad.hasAmbientOcclusion()) return false;
        for (int vertex = 0; vertex < 4; vertex++) {
            if (quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2] != LightTexture.FULL_BRIGHT) return false;
        }
        return true;
    }

    private List<Region> mask(TextureAtlasSprite sprite) {
        var contents = sprite.contents();
        if (!contents.name().getNamespace().equals("molecularmanipulator")
                || !contents.name().getPath().startsWith("block/matter_fabrication_")
                || contents.width() != 16 || contents.height() != 16) return List.of();
        var image = contents.getOriginalImage();
        if (image.getWidth() % 16 != 0 || image.getHeight() % 16 != 0) return List.of();
        int columns = image.getWidth() / 16;
        int[] frames = image.getWidth() == 16 && image.getHeight() == 16
                ? new int[]{0} : contents.getUniqueFrames().toArray();
        boolean[][] emissive = new boolean[16][16], used = new boolean[16][16];
        boolean any = false;
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            // Geometry is cached, so only material that stays emissive in every played frame
            // can be full-bright. This keeps the neutral shell dark while highlights move.
            boolean stable = frames.length > 0;
            for (int frame : frames) {
                if (!isEmissivePixel(contents.name(), image.getPixelRGBA(
                        x + frame % columns * 16, y + frame / columns * 16))) {
                    stable = false;
                    break;
                }
            }
            emissive[y][x] = stable;
            any |= emissive[y][x];
        }
        if (!any) return List.of();
        var result = new ArrayList<Region>();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            if (used[y][x]) continue;
            boolean type = emissive[y][x];
            int right = x + 1;
            while (right < 16 && !used[y][right] && emissive[y][right] == type) right++;
            int bottom = y + 1;
            outer: while (bottom < 16) {
                for (int i = x; i < right; i++) if (used[bottom][i] || emissive[bottom][i] != type) break outer;
                bottom++;
            }
            for (int row = y; row < bottom; row++) for (int col = x; col < right; col++) used[row][col] = true;
            result.add(new Region(x, y, right, bottom, type));
        }
        return List.copyOf(result);
    }

    private List<BakedQuad> partition(BakedQuad source) {
        var sprite = source.getSprite();
        var regions = masks.computeIfAbsent(sprite, this::mask);
        if (regions.isEmpty()) return List.of(source);
        int[] input = source.getVertices();
        if (input.length != IQuadTransformer.STRIDE * 4) return List.of(source);
        float[] us = new float[4], vs = new float[4];
        float minU = Float.POSITIVE_INFINITY, maxU = Float.NEGATIVE_INFINITY, minV = minU, maxV = maxU;
        for (int i = 0; i < 4; i++) {
            int base = i * IQuadTransformer.STRIDE;
            us[i] = sprite.getUOffset(Float.intBitsToFloat(input[base + IQuadTransformer.UV0]));
            vs[i] = sprite.getVOffset(Float.intBitsToFloat(input[base + IQuadTransformer.UV0 + 1]));
            minU = Math.min(minU, us[i]); maxU = Math.max(maxU, us[i]);
            minV = Math.min(minV, vs[i]); maxV = Math.max(maxV, vs[i]);
        }
        if (!Float.isFinite(minU + maxU + minV + maxV) || maxU - minU < 1e-6F || maxV - minV < 1e-6F
                || minU < -1e-4F || minV < -1e-4F || maxU > 1.0001F || maxV > 1.0001F) return List.of(source);
        int[] corners = {-1, -1, -1, -1}, ux = new int[4], vy = new int[4];
        for (int i = 0; i < 4; i++) {
            float u = (us[i] - minU) / (maxU - minU), v = (vs[i] - minV) / (maxV - minV);
            if (Math.min(Math.abs(u), Math.abs(1-u)) > 1e-4F || Math.min(Math.abs(v), Math.abs(1-v)) > 1e-4F) return List.of(source);
            ux[i] = Math.round(u); vy[i] = Math.round(v);
            int corner = ux[i] + 2 * vy[i];
            if (corners[corner] != -1) return List.of(source);
            corners[corner] = i;
        }
        var result = new ArrayList<BakedQuad>();
        boolean hasEmissive = false;
        for (var region : regions) {
            float u0 = Math.max(minU, region.x0() / 16F), u1 = Math.min(maxU, region.x1() / 16F);
            float v0 = Math.max(minV, region.y0() / 16F), v1 = Math.min(maxV, region.y1() / 16F);
            if (u1 - u0 < 1e-7F || v1 - v0 < 1e-7F) continue;
            int[] output = new int[input.length];
            for (int i = 0; i < 4; i++) {
                int base = i * IQuadTransformer.STRIDE;
                System.arraycopy(input, base, output, base, IQuadTransformer.STRIDE);
                float u = ux[i] == 0 ? u0 : u1, v = vy[i] == 0 ? v0 : v1;
                float x = (u-minU)/(maxU-minU), y = (v-minV)/(maxV-minV);
                for (int axis = 0; axis < 3; axis++) output[base + IQuadTransformer.POSITION + axis]
                        = Float.floatToRawIntBits(interpolate(input, corners, IQuadTransformer.POSITION + axis, x, y));
                output[base + IQuadTransformer.COLOR] = packed(input, corners, IQuadTransformer.COLOR, x, y, 8);
                output[base + IQuadTransformer.UV2] = region.emissive() ? LightTexture.FULL_BRIGHT
                        : packed(input, corners, IQuadTransformer.UV2, x, y, 16);
                output[base + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU(u));
                output[base + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV(v));
            }
            hasEmissive |= region.emissive();
            result.add(new BakedQuad(output, source.getTintIndex(), source.getDirection(), sprite,
                    region.emissive() ? false : source.isShade(), region.emissive() ? false : source.hasAmbientOcclusion()));
        }
        return hasEmissive ? List.copyOf(result) : List.of(source);
    }

    private static float interpolate(int[] input, int[] corners, int attribute, float x, float y) {
        float a = Float.intBitsToFloat(input[corners[0]*IQuadTransformer.STRIDE+attribute]);
        float b = Float.intBitsToFloat(input[corners[1]*IQuadTransformer.STRIDE+attribute]);
        float c = Float.intBitsToFloat(input[corners[2]*IQuadTransformer.STRIDE+attribute]);
        float d = Float.intBitsToFloat(input[corners[3]*IQuadTransformer.STRIDE+attribute]);
        return (a+(b-a)*x)*(1-y)+(c+(d-c)*x)*y;
    }

    private static int packed(int[] input, int[] corners, int attribute, float x, float y, int bits) {
        int result = 0; long mask = (1L<<bits)-1;
        for (int shift = 0; shift < 32; shift += bits) {
            double a=input[corners[0]*IQuadTransformer.STRIDE+attribute]>>>shift & mask;
            double b=input[corners[1]*IQuadTransformer.STRIDE+attribute]>>>shift & mask;
            double c=input[corners[2]*IQuadTransformer.STRIDE+attribute]>>>shift & mask;
            double d=input[corners[3]*IQuadTransformer.STRIDE+attribute]>>>shift & mask;
            result |= (int)Math.round((a+(b-a)*x)*(1-y)+(c+(d-c)*x)*y)<<shift;
        }
        return result;
    }
}
