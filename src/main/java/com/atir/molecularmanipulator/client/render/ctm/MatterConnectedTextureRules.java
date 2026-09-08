package com.atir.molecularmanipulator.client.render.ctm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Pure face-space connection and UV rules shared by client rendering and server-side verification. */
public final class MatterConnectedTextureRules {
    public static final int L = 1;
    public static final int R = 2;
    public static final int T = 4;
    public static final int B = 8;
    public static final int TL = 16;
    public static final int TR = 32;
    public static final int BL = 64;
    public static final int BR = 128;
    public static final float BORDER = 2.0F / 16.0F;

    private MatterConnectedTextureRules() {
    }

    /** u/v are world directions in which the baked face's texture coordinates increase. */
    public record Face(String texture, Direction u, Direction v) {
        public Face {
            Objects.requireNonNull(texture);
            Objects.requireNonNull(u);
            Objects.requireNonNull(v);
            if (u.getAxis() == v.getAxis()) {
                throw new IllegalArgumentException("Face texture axes must be perpendicular");
            }
        }
    }

    public enum Mode {
        ISOTROPIC,
        U_ONLY,
        V_ONLY,
        ICON
    }

    /** Position and UV coordinates are normalized within the original baked quad, not the entire sprite. */
    public record Patch(float x0, float y0, float x1, float y1,
            float u0, float v0, float u1, float v1, boolean preservesOutline) {
        public Patch(float x0, float y0, float x1, float y1,
                float u0, float v0, float u1, float v1) {
            this(x0, y0, x1, y1, u0, v0, u1, v1, false);
        }

        public boolean remapped() {
            return u0 != x0 || v0 != y0 || u1 != x1 || v1 != y1;
        }

        public boolean useCasingBackground(Face face) {
            String texture = textureName(face.texture());
            return remapped() && !preservesOutline && (mode(face) == Mode.ICON
                    || texture.equals("matter_fabrication_coil_top")
                    || texture.equals("matter_fabrication_core_top"));
        }
    }

    public static Mode mode(Face face) {
        return mode(face.texture());
    }

    public static Mode mode(String texture) {
        String name = textureName(texture);
        if (name.endsWith("_top") || name.equals("matter_fabrication_casing")
                || name.equals("matter_fabrication_glass")) {
            return Mode.ISOTROPIC;
        }
        if (name.equals("matter_fabrication_coil")) return Mode.U_ONLY;
        if (name.equals("matter_fabrication_stabilizer")) return Mode.V_ONLY;
        return Mode.ICON;
    }

    public static float edgeBand(Face face) {
        return switch (textureName(face.texture())) {
            case "matter_fabrication_glass" -> 1.0F / 16.0F;
            // The edited ports encode input/output in the first inset blue/gold ring.
            // Connect only their neutral outermost pixel, keeping that functional marking intact.
            case "matter_fabrication_item_input", "matter_fabrication_item_output",
                    "matter_fabrication_fluid_input", "matter_fabrication_fluid_output" -> 1.0F / 16.0F;
            // Rounded casing corners extend into the third pixel; the stabilizer cap reaches the fourth.
            case "matter_fabrication_casing" -> 3.0F / 16.0F;
            case "matter_fabrication_stabilizer_top" -> 4.0F / 16.0F;
            default -> BORDER;
        };
    }

    private static String textureName(String texture) {
        return texture.substring(Math.max(texture.lastIndexOf('/'), texture.lastIndexOf(':')) + 1);
    }

    /**
     * The provider must also answer for pos itself, and return null for hidden or unsupported faces.
     * Only same-plane neighbors are queried; a diagonal connects only when both bordering edges connect.
     */
    public static int connections(BlockPos pos, Direction normal, Face self,
            BiFunction<BlockPos, Direction, Face> exposedFaces) {
        Objects.requireNonNull(pos);
        Objects.requireNonNull(normal);
        Objects.requireNonNull(exposedFaces);
        if (self == null || self.u().getAxis() == normal.getAxis() || self.v().getAxis() == normal.getAxis()
                || !compatible(self, exposedFaces.apply(pos, normal))) {
            return 0;
        }
        Mode mode = mode(self);
        int mask = 0;
        if (mode != Mode.V_ONLY) {
            if (compatible(self, exposedFaces.apply(pos.relative(self.u().getOpposite()), normal))) mask |= L;
            if (compatible(self, exposedFaces.apply(pos.relative(self.u()), normal))) mask |= R;
        }
        if (mode != Mode.U_ONLY) {
            if (compatible(self, exposedFaces.apply(pos.relative(self.v().getOpposite()), normal))) mask |= T;
            if (compatible(self, exposedFaces.apply(pos.relative(self.v()), normal))) mask |= B;
        }
        if ((mask & (L | T)) == (L | T)
                && compatible(self, exposedFaces.apply(pos.relative(self.u().getOpposite()).relative(self.v().getOpposite()), normal))) mask |= TL;
        if ((mask & (R | T)) == (R | T)
                && compatible(self, exposedFaces.apply(pos.relative(self.u()).relative(self.v().getOpposite()), normal))) mask |= TR;
        if ((mask & (L | B)) == (L | B)
                && compatible(self, exposedFaces.apply(pos.relative(self.u().getOpposite()).relative(self.v()), normal))) mask |= BL;
        if ((mask & (R | B)) == (R | B)
                && compatible(self, exposedFaces.apply(pos.relative(self.u()).relative(self.v()), normal))) mask |= BR;
        return mask;
    }

    public static boolean compatible(Face self, Face neighbor) {
        if (self == null || neighbor == null || !self.texture().equals(neighbor.texture())) return false;
        return switch (mode(self)) {
            case ISOTROPIC -> true;
            // The gold bands are symmetric. Reversing them preserves continuity,
            // but a quarter turn would join perpendicular traces.
            case U_ONLY, V_ONLY -> self.u().getAxis() == neighbor.u().getAxis()
                    && self.v().getAxis() == neighbor.v().getAxis();
            case ICON -> self.u() == neighbor.u() && self.v() == neighbor.v();
        };
    }

    public static List<Patch> patches(Face face, int mask) {
        int allowed = switch (mode(face)) {
            case U_ONLY -> L | R;
            case V_ONLY -> T | B;
            default -> 255;
        };
        mask &= allowed;
        String texture = textureName(face.texture());
        if (mode(face) == Mode.ICON || texture.equals("matter_fabrication_coil_top")
                || texture.equals("matter_fabrication_core_top")) {
            float border = edgeBand(face);
            // Ports have a one-pixel removable seam, but a two-pixel outer frame including their color code.
            float outline = Math.max(border, BORDER);
            return iconPatches(face, mask, border, outline);
        }
        return patches(mask, edgeBand(face));
    }

    private static List<Patch> iconPatches(Face face, int mask, float border, float outline) {
        mask = normalizedMask(mask);
        boolean assembly = textureName(face.texture()).equals("matter_fabrication_pattern_assembly");
        // Assembly corner fasteners reach pixel 3, but its central frame reaches pixel 2.
        // Extend cleanup only inside the four corner squares, never along the whole glyph edge.
        float corner = assembly ? 4.0F / 16 : outline;
        float[] cuts = corner > border
                ? new float[] {0, border, corner, 1 - corner, 1 - border, 1}
                : new float[] {0, border, 1 - border, 1};
        var result = new ArrayList<Patch>((cuts.length - 1) * (cuts.length - 1));
        for (int row = 0; row < cuts.length - 1; row++) for (int col = 0; col < cuts.length - 1; col++) {
            float x0 = cuts[col], x1 = cuts[col + 1], y0 = cuts[row], y1 = cuts[row + 1];
            boolean cornerSquare = assembly && (x1 <= corner || x0 >= 1 - corner)
                    && (y1 <= corner || y0 >= 1 - corner);
            float seam = cornerSquare ? corner : border;
            boolean left = x1 <= seam, right = x0 >= 1 - seam;
            boolean top = y1 <= seam, bottom = y0 >= 1 - seam;
            boolean horizontal = left && (mask & L) != 0 || right && (mask & R) != 0;
            boolean vertical = top && (mask & T) != 0 || bottom && (mask & B) != 0;
            if (horizontal && vertical) {
                int diagonal = top ? left ? TL : TR : left ? BL : BR;
                if ((mask & diagonal) == 0) horizontal = vertical = false;
            }
            float u0 = horizontal ? reflectedU(left ? 0 : 2, x0, border) : x0;
            float u1 = horizontal ? reflectedU(left ? 0 : 2, x1, border) : x1;
            float v0 = vertical ? reflectedU(top ? 0 : 2, y0, border) : y0;
            float v1 = vertical ? reflectedU(top ? 0 : 2, y1, border) : y1;
            boolean horizontalOutline = horizontal && !vertical
                    && (y1 <= outline && (mask & T) == 0 || y0 >= 1 - outline && (mask & B) == 0);
            boolean verticalOutline = vertical && !horizontal
                    && (x1 <= outline && (mask & L) == 0 || x0 >= 1 - outline && (mask & R) == 0);
            if (horizontalOutline) {
                // Sample a straight piece of this same top/bottom edge, not neutral casing filler.
                // The assembly glyph starts near the middle of its top band, so use its clear shoulder.
                float sample = assembly ? 4.0F / 16 : 0.5F;
                u0 = sample + 1.0F / 16;
                u1 = sample;
            }
            if (verticalOutline) {
                v0 = 9.0F / 16;
                v1 = 8.0F / 16;
            }
            result.add(new Patch(x0, y0, x1, y1, u0, v0, u1, v1, horizontalOutline || verticalOutline));
        }
        return List.copyOf(result);
    }

    /**
     * Return nine patches in top-to-bottom, left-to-right order. A zero mask is an identity split;
     * wrappers should keep the untouched original quad for that common case.
     */
    public static List<Patch> patches(int mask) {
        return patches(mask, BORDER);
    }

    private static List<Patch> patches(int mask, float border) {
        mask = normalizedMask(mask);
        float[] cuts = {0.0F, border, 1.0F - border, 1.0F};
        var patches = new ArrayList<Patch>(9);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
            float x0 = cuts[col], x1 = cuts[col + 1];
            float y0 = cuts[row], y1 = cuts[row + 1];
            boolean horizontal = col == 0 ? (mask & L) != 0 : col == 2 && (mask & R) != 0;
            boolean vertical = row == 0 ? (mask & T) != 0 : row == 2 && (mask & B) != 0;
            if (col != 1 && row != 1 && horizontal && vertical) {
                int diagonal = row == 0 ? col == 0 ? TL : TR : col == 0 ? BL : BR;
                if ((mask & diagonal) == 0) {
                    // Two neighbors without their diagonal leave an exposed concave corner.
                    horizontal = false;
                    vertical = false;
                }
            }
            float u0 = horizontal ? reflectedU(col, x0, border) : x0;
            float u1 = horizontal ? reflectedU(col, x1, border) : x1;
            float v0 = vertical ? reflectedU(row, y0, border) : y0;
            float v1 = vertical ? reflectedU(row, y1, border) : y1;
            patches.add(new Patch(x0, y0, x1, y1, u0, v0, u1, v1));
        }
        return List.copyOf(patches);
    }

    public static int normalizedMask(int mask) {
        mask &= 255;
        if ((mask & (L | T)) != (L | T)) mask &= ~TL;
        if ((mask & (R | T)) != (R | T)) mask &= ~TR;
        if ((mask & (L | B)) != (L | B)) mask &= ~BL;
        if ((mask & (R | B)) != (R | B)) mask &= ~BR;
        return mask;
    }

    private static float reflectedU(int band, float coordinate, float border) {
        return (band == 0 ? 2.0F * border : 2.0F * (1.0F - border)) - coordinate;
    }
}
