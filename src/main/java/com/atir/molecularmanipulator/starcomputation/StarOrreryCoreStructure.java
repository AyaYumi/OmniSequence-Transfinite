package com.atir.molecularmanipulator.starcomputation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/** The orrery's central 3x3x3 body, excluding its controller. Never a standalone machine. */
public final class StarOrreryCoreStructure {
    public enum Material { CASING, OBSERVATION_PORT }
    public enum Probe { MATCH, OTHER, UNLOADED }
    public record Member(BlockPos offset, Material material) {}
    public record Scan(boolean complete, boolean resolved, @Nullable BlockPos problem, @Nullable Material expected) {}
    private static final List<Member> MEMBERS = create();

    private StarOrreryCoreStructure() {}

    private static List<Member> create() {
        var members = new ArrayList<Member>();
        for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            var offset = new BlockPos(x, y, z);
            var material = materialAt(offset);
            if (material != null) members.add(new Member(offset, material));
        }
        return List.copyOf(members);
    }

    public static List<Member> members() { return MEMBERS; }

    /** Four horizontal face centres are ports; the other 22 outer cells are casings. */
    @Nullable
    public static Material materialAt(BlockPos offset) {
        int x = offset.getX(), y = offset.getY(), z = offset.getZ();
        if (x < -1 || x > 1 || y < -1 || y > 1 || z < -1 || z > 1 || offset.equals(BlockPos.ZERO)) return null;
        return y == 0 && Math.abs(x) + Math.abs(z) == 1 ? Material.OBSERVATION_PORT : Material.CASING;
    }

    /** A known missing or misplaced part invalidates formation even across an unloaded boundary. */
    public static Scan scan(BlockPos core, BiFunction<BlockPos, Material, Probe> reader) {
        BlockPos unloaded = null;
        Material expected = null;
        for (var member : MEMBERS) {
            var pos = core.offset(member.offset());
            var probe = reader.apply(pos, member.material());
            if (probe == Probe.OTHER) return new Scan(false, true, pos, member.material());
            if (probe == Probe.UNLOADED && unloaded == null) {
                unloaded = pos;
                expected = member.material();
            }
        }
        return new Scan(unloaded == null, unloaded == null, unloaded, expected);
    }
}
