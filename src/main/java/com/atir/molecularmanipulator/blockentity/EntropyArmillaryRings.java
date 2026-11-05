package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryFoundation;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * Nine-ring armillary scan. This is deliberately independent of the legacy
 * 3x3 anchor columns: only blocks with the armillary flag are interpreted here.
 *
 * <p>Rings are validated from zero outwards. A known missing/wrong member proves
 * that the current ring is incomplete, while an unloaded chunk cannot prove
 * anything and stops the scan at the last fully verified ring.</p>
 */
public final class EntropyArmillaryRings {
    public static final int RINGS = EntropyArmillaryLayout.RINGS;

    public enum Probe {
        MATCH,
        OTHER,
        UNLOADED
    }

    /** {@code lit} is the verified prefix; {@code resolved} is false at an unloaded boundary. */
    public record Scan(int lit, boolean resolved) {
        public Scan {
            lit = Math.clamp(lit, 0, RINGS);
        }
    }

    private EntropyArmillaryRings() {
    }

    public static int slotCount(int ring) {
        return EntropyArmillaryLayout.slotCount(ring);
    }

    /** 0 = base, 1 = quantum, 2 = terminal. */
    public static int tierOf(int ring) {
        return EntropyArmillaryLayout.tierOf(ring);
    }

    public static BlockPos slot(BlockPos core, Direction facing, int ring, int slot) {
        return EntropyArmillaryLayout.slot(core, facing, ring, slot);
    }

    public static BlockPos offset(Direction facing, int ring, int slot) {
        return EntropyArmillaryLayout.offset(facing, ring, slot);
    }

    /**
     * Strict outer prefix scan. Never probes past the first unresolved ring, so
     * an unloaded chunk cannot silently count as air and no unbounded radius is
     * ever walked.
     */
    public static Scan scan(BlockPos core, Direction facing,
            BiFunction<BlockPos, Integer, Probe> reader) {
        for (int ring = 0; ring < RINGS; ring++) {
            boolean unloaded = false;
            int count = slotCount(ring);
            for (int slot = 0; slot < count; slot++) {
                var pos = slot(core, facing, ring, slot);
                var probe = reader.apply(pos, ring);
                if (probe == Probe.OTHER) {
                    // A known hole/wrong tier already proves this ring incomplete;
                    // another unknown member of the same ring cannot change that.
                    return new Scan(ring, true);
                }
                if (probe == Probe.UNLOADED) {
                    unloaded = true;
                }
            }
            if (unloaded) {
                return new Scan(ring, false);
            }
        }
        return new Scan(RINGS, true);
    }

    /**
     * Chunks touched by the real foundation, core and actual slots of the first
     * {@code rings} rings. Bounded by the fixed layout; never loads chunks.
     */
    public static Set<ChunkPos> chunksFor(BlockPos core, Direction facing, int rings) {
        var chunks = new HashSet<ChunkPos>(EntropyArmillaryFoundation.chunksFor(core));
        // The 3x3x3 shell may straddle a chunk corner even before a ring is lit.
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            chunks.add(new ChunkPos(core.offset(x, 0, z)));
        }
        chunks.add(new ChunkPos(core));
        int limit = Math.clamp(rings, 0, RINGS);
        for (int ring = 0; ring < limit; ring++) {
            int count = slotCount(ring);
            for (int slot = 0; slot < count; slot++) {
                chunks.add(new ChunkPos(slot(core, facing, ring, slot)));
            }
        }
        return chunks;
    }
}
