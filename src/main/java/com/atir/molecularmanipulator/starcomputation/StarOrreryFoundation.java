package com.atir.molecularmanipulator.starcomputation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/** Open physical ring frame. Formation hides its meshes, not its blocks or collision. */
public final class StarOrreryFoundation {
    public static final int FLOOR_Y = -37;
    public static final int TOP_Y = -34;
    public static final int OUTER_RADIUS = 18;
    public static final int WELL_RADIUS = 5;
    public enum Material { PLINTH, TRIM }
    public enum Probe { MATCH, OTHER, UNLOADED }
    public record Member(BlockPos offset, Material material) {}
    public record Scan(boolean complete, boolean resolved, BlockPos problem, Material expected) {}
    private static final List<Member> MEMBERS = create();
    private static final List<BlockPos> RESTORATION_OFFSETS = createRestorationOffsets();

    private StarOrreryFoundation() {}

    private static List<Member> create() {
        var members = new ArrayList<Member>();
        for (int y = FLOOR_Y; y <= TOP_Y; y++) {
            for (int x = -OUTER_RADIUS; x <= OUTER_RADIUS; x++) for (int z = -OUTER_RADIUS; z <= OUTER_RADIUS; z++) {
                var offset = new BlockPos(x, y, z);
                var material = materialAt(offset);
                if (material != null) members.add(new Member(offset, material));
            }
        }
        return List.copyOf(members);
    }

    /** Four-fold symmetry keeps both the narrow bridges and ring materials facing-independent. */
    @org.jetbrains.annotations.Nullable
    public static Material materialAt(BlockPos offset) {
        int y = offset.getY(), x = offset.getX(), z = offset.getZ();
        if (y < FLOOR_Y || y > TOP_Y || Math.abs((long) x) > OUTER_RADIUS
                || Math.abs((long) z) > OUTER_RADIUS) return null;
        int square = x * x + z * z;
        if (square <= WELL_RADIUS * WELL_RADIUS) return null;
        if (y == FLOOR_Y) {
            // Antigravity ring, outer index ring and four three-block-wide bridges.
            boolean ring = square > 49 && square <= 100 || square > 225 && square <= 324;
            boolean bridge = square <= 324 && (Math.abs(x) <= 1 || Math.abs(z) <= 1);
            if (!ring && !bridge) return null;
            return square > 81 && square <= 100 || square > 289 && square <= 324
                    || x == 0 || z == 0 ? Material.TRIM : Material.PLINTH;
        }
        // The middle dial ring is one block thick. Only the open well collar rises above it.
        boolean collar = square <= 49;
        boolean dial = y == -36 && square > 144 && square <= 196;
        if (!collar && !dial) return null;
        return collar && square > 36 || dial && square > 169 ? Material.TRIM : Material.PLINTH;
    }

    /**
     * Past and current positions are visited only for visual restoration/template replacement.
     * Old hidden terraces must become visible again without deleting any player materials.
     * This is deliberately separate from members(), scan() and the chunk-ticket footprint.
     */
    private static List<BlockPos> createRestorationOffsets() {
        var offsets = new java.util.LinkedHashSet<BlockPos>();
        for (int layer = 0; layer < 6; layer++) {
            int radius = 30 - (layer / 2) * 6;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                int square = x * x + z * z;
                if (square <= radius * radius && !(layer >= 4 && square <= 25)) {
                    offsets.add(new BlockPos(x, -38 + layer, z));
                }
            }
        }
        for (var member : MEMBERS) offsets.add(member.offset());
        return List.copyOf(offsets);
    }

    public static List<BlockPos> restorationOffsets() { return RESTORATION_OFFSETS; }

    public static List<Member> members() { return MEMBERS; }

    public static BlockPos position(BlockPos core, Direction facing, Member member) {
        return core.offset(member.offset().rotate(StarOrreryLayout.rotationFor(facing)));
    }

    /** Missing known blocks invalidate formation even if another chunk is unavailable. */
    public static Scan scan(BlockPos core, Direction facing, BiFunction<BlockPos, Material, Probe> reader) {
        BlockPos unloaded = null;
        Material expected = null;
        for (var member : MEMBERS) {
            var pos = position(core, facing, member);
            var probe = reader.apply(pos, member.material());
            if (probe == Probe.OTHER) return new Scan(false, true, pos, member.material());
            if (probe == Probe.UNLOADED && unloaded == null) {
                unloaded = pos;
                expected = member.material();
            }
        }
        return new Scan(unloaded == null, unloaded == null, unloaded, expected);
    }

    public static Set<ChunkPos> chunksFor(BlockPos core) {
        var chunks = new HashSet<ChunkPos>();
        for (var member : MEMBERS) chunks.add(new ChunkPos(core.offset(member.offset())));
        return Set.copyOf(chunks);
    }

    public static Set<ChunkPos> restorationChunksFor(BlockPos core) {
        var chunks = new HashSet<ChunkPos>();
        for (var offset : RESTORATION_OFFSETS) chunks.add(new ChunkPos(core.offset(offset)));
        return Set.copyOf(chunks);
    }
}
