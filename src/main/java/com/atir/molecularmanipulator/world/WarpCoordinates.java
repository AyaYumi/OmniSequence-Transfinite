package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.block.WarpCoordinateInscriptionBlock;
import com.atir.molecularmanipulator.registry.WarpContent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class WarpCoordinates {
    public static final int GLYPH_COUNT = 6;

    private WarpCoordinates() {}

    public static int glyph(long seed, int key) {
        int code = 1 + RandomSource.create(seed ^ 0x5E2D6B4A19C837F1L).nextInt(215);
        for (int index = 0; index < key; index++) code /= GLYPH_COUNT;
        return code % GLYPH_COUNT;
    }

    public static void applyInscriptions(ServerLevelAccessor level, long seed, BoundingBox bounds) {
        for (int key = 0; key < 3; key++) {
            var pos = EndWarpRuinStructure.ORIGIN.offset(28, 22, 20 + key);
            if (bounds != null && !bounds.isInside(pos)) continue;
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            var state = level.getBlockState(pos);
            if (state.is(WarpContent.COORDINATE_INSCRIPTION.get())
                    && state.getValue(WarpCoordinateInscriptionBlock.GLYPH) != glyph(seed, key)) {
                level.setBlock(pos, state.setValue(WarpCoordinateInscriptionBlock.GLYPH, glyph(seed, key)), 2);
            }
        }
    }
}
