package com.atir.molecularmanipulator.client.gui.theme;

import java.util.ArrayList;
import java.util.List;

/** Vanilla 1.21.1 sprite tessellation, separated from GL for regression tests. */
final class OsqSpriteGeometry {
    record Quad(int x, int y, int width, int height, float u0, float v0, float u1, float v1) {}

    private OsqSpriteGeometry() {}

    static List<Quad> stretch(int width, int height) {
        return width <= 0 || height <= 0 ? List.of()
                : List.of(new Quad(0, 0, width, height, 0, 0, 1, 1));
    }

    static List<Quad> tile(int width, int height, int tileWidth, int tileHeight) {
        var result = new ArrayList<Quad>();
        tiled(result, 0, 0, width, height, 0, 0, tileWidth, tileHeight, tileWidth, tileHeight);
        return List.copyOf(result);
    }

    static List<Quad> nineSlice(int width, int height, int sourceWidth, int sourceHeight,
            int left, int top, int right, int bottom) {
        if (width <= 0 || height <= 0) return List.of();
        var result = new ArrayList<Quad>();
        int l = Math.min(left, width / 2), r = Math.min(right, width / 2);
        int t = Math.min(top, height / 2), b = Math.min(bottom, height / 2);
        if (width == sourceWidth && height == sourceHeight) return stretch(width, height);
        if (height == sourceHeight) {
            quad(result, 0, 0, l, height, 0, 0, sourceWidth, sourceHeight);
            tiled(result, l, 0, width-r-l, height, l, 0, sourceWidth-r-l, sourceHeight, sourceWidth, sourceHeight);
            quad(result, width-r, 0, r, height, sourceWidth-r, 0, sourceWidth, sourceHeight);
        } else if (width == sourceWidth) {
            quad(result, 0, 0, width, t, 0, 0, sourceWidth, sourceHeight);
            tiled(result, 0, t, width, height-b-t, 0, t, sourceWidth, sourceHeight-b-t, sourceWidth, sourceHeight);
            quad(result, 0, height-b, width, b, 0, sourceHeight-b, sourceWidth, sourceHeight);
        } else {
            quad(result, 0, 0, l, t, 0, 0, sourceWidth, sourceHeight);
            tiled(result, l, 0, width-r-l, t, l, 0, sourceWidth-r-l, t, sourceWidth, sourceHeight);
            quad(result, width-r, 0, r, t, sourceWidth-r, 0, sourceWidth, sourceHeight);
            quad(result, 0, height-b, l, b, 0, sourceHeight-b, sourceWidth, sourceHeight);
            tiled(result, l, height-b, width-r-l, b, l, sourceHeight-b, sourceWidth-r-l, b, sourceWidth, sourceHeight);
            quad(result, width-r, height-b, r, b, sourceWidth-r, sourceHeight-b, sourceWidth, sourceHeight);
            tiled(result, 0, t, l, height-b-t, 0, t, l, sourceHeight-b-t, sourceWidth, sourceHeight);
            tiled(result, l, t, width-r-l, height-b-t, l, t, sourceWidth-r-l, sourceHeight-b-t, sourceWidth, sourceHeight);
            // Match vanilla's asymmetric-border source width, including its clipped right edge.
            tiled(result, width-r, t, l, height-b-t, sourceWidth-r, t, r, sourceHeight-b-t, sourceWidth, sourceHeight);
        }
        return List.copyOf(result);
    }

    private static void tiled(List<Quad> out, int x, int y, int width, int height,
            int u, int v, int tileWidth, int tileHeight, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) return;
        if (tileWidth <= 0 || tileHeight <= 0) throw new IllegalArgumentException("Non-positive sprite tile size");
        for (int dx = 0; dx < width; dx += tileWidth)
            for (int dy = 0; dy < height; dy += tileHeight)
                quad(out, x+dx, y+dy, Math.min(tileWidth, width-dx), Math.min(tileHeight, height-dy),
                        u, v, sourceWidth, sourceHeight);
    }

    private static void quad(List<Quad> out, int x, int y, int width, int height,
            int u, int v, int sourceWidth, int sourceHeight) {
        if (width > 0 && height > 0)
            out.add(new Quad(x, y, width, height, u/(float)sourceWidth, v/(float)sourceHeight,
                    (u+width)/(float)sourceWidth, (v+height)/(float)sourceHeight));
    }
}
