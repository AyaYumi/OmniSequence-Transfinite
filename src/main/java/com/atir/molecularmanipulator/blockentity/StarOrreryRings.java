package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.starcomputation.StarOrreryFoundation;
import com.atir.molecularmanipulator.starcomputation.StarOrreryCoreStructure;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/**
 * Nine-Ring Star Orrery scan. Only the fixed ring slots contribute ring credit;
 * the central body is checked separately and retired anchor columns are unsupported.
 *
 * <p>Rings are validated from zero outwards. A known missing/wrong member proves
 * that the current ring is incomplete, while an unloaded chunk cannot prove
 * anything and stops the scan at the last fully verified ring.</p>
 */
public final class StarOrreryRings {
    public static final int RINGS = StarOrreryLayout.RINGS;

    public enum Probe {
        MATCH,
        OTHER,
        UNLOADED
    }

    /** {@code lit} is the verified prefix; {@code resolved} is false at an unloaded boundary. */
    public record Scan(int lit, boolean resolved) {
        public Scan {
            lit = com.atir.molecularmanipulator.util.MathCompat.clamp(lit, 0, RINGS);
        }
    }

    private StarOrreryRings() {
    }

    public static int slotCount(int ring) {
        return StarOrreryLayout.slotCount(ring);
    }

    /** 0 = base, 1 = quantum, 2 = terminal. */
    public static int tierOf(int ring) {
        return StarOrreryLayout.tierOf(ring);
    }

    public static BlockPos slot(BlockPos core, Direction facing, int ring, int slot) {
        return StarOrreryLayout.slot(core, facing, ring, slot);
    }

    public static BlockPos offset(Direction facing, int ring, int slot) {
        return StarOrreryLayout.offset(facing, ring, slot);
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
     * Chunks touched by the real foundation, central 3x3x3 and actual slots of the first
     * {@code rings} rings. Bounded by the fixed layout; never loads chunks.
     */
    public static Set<ChunkPos> chunksFor(BlockPos core, Direction facing, int rings) {
        var chunks = new HashSet<ChunkPos>(StarOrreryFoundation.chunksFor(core));
        chunks.add(new ChunkPos(core));
        for (var member : StarOrreryCoreStructure.members()) chunks.add(new ChunkPos(core.offset(member.offset())));
        int limit = com.atir.molecularmanipulator.util.MathCompat.clamp(rings, 0, RINGS);
        for (int ring = 0; ring < limit; ring++) {
            int count = slotCount(ring);
            for (int slot = 0; slot < count; slot++) {
                chunks.add(new ChunkPos(slot(core, facing, ring, slot)));
            }
        }
        return chunks;
    }
}
