package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/** Client visual lookup shared by BERs and animateTick; no client classes are loaded on the server. */
public final class MachineEffectLevels {
    private MachineEffectLevels() {}

    // Client render-thread only. Values retain coordinates/candidates, never a Level or BE,
    // so discarded client worlds can actually leave this weak-key cache.
    private static final Map<Level, OwnershipCache> OWNERS = new WeakHashMap<>();
    private static final class OwnershipCache {
        final Long2ObjectOpenHashMap<CachedOwner> molecular = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<CachedOwner> singularityCores = new Long2ObjectOpenHashMap<>();
        final Long2ObjectOpenHashMap<CachedOwner> singularityNodes = new Long2ObjectOpenHashMap<>();
    }
    private record CachedOwner(Candidate candidate, long retryAt) {}

    public static int forMachine(BlockEntity machine) {
        int override = machine instanceof MachineEffectHost host
                ? host.getMachineEffects().getOverride() : MachineEffectSettings.INHERIT;
        // Visual entry points are client-only; explicit overrides do not need to read client configuration.
        return override >= MachineEffectSettings.OFF && override <= MachineEffectSettings.FULL
                ? override : ModConfig.DYNAMIC_EFFECT_LEVEL.get();
    }

    public static int forMolecularCore(Level level, BlockPos partPos) {
        var cache = OWNERS.computeIfAbsent(level, ignored -> new OwnershipCache());
        return resolve(level, partPos, MolecularCores.CANDIDATES, cache.molecular);
    }

    public static int forSingularityPart(Level level, BlockPos partPos, SingularityStructure.Type type) {
        var cache = OWNERS.computeIfAbsent(level, ignored -> new OwnershipCache());
        return type == SingularityStructure.Type.CORE
                ? resolve(level, partPos, SingularityParts.CORES, cache.singularityCores)
                : resolve(level, partPos, SingularityParts.NODES, cache.singularityNodes);
    }

    private static int resolve(Level level, BlockPos partPos, List<? extends Candidate> candidates,
            Long2ObjectOpenHashMap<CachedOwner> owners) {
        long key = partPos.asLong();
        var cached = owners.get(key);
        if (cached != null) {
            if (cached.candidate() == null) {
                if (level.getGameTime() < cached.retryAt()) return ModConfig.DYNAMIC_EFFECT_LEVEL.get();
            } else {
                var machine = liveOwner(level, partPos, cached.candidate());
                if (machine != null) return forMachine(machine);
            }
        }
        for (var candidate : candidates) {
            var machine = liveOwner(level, partPos, candidate);
            if (machine != null) {
                owners.put(key, new CachedOwner(candidate, 0));
                return forMachine(machine);
            }
        }
        // An unbound decorative block must not probe every blueprint candidate each frame.
        owners.put(key, new CachedOwner(null, level.getGameTime() + 20));
        return ModConfig.DYNAMIC_EFFECT_LEVEL.get();
    }

    private static BlockEntity liveOwner(Level level, BlockPos partPos, Candidate candidate) {
        var controller = partPos.subtract(candidate.offset());
        if (!level.hasChunkAt(controller)) return null;
        var machine = level.getBlockEntity(controller);
        return machine != null && !machine.isRemoved() && candidate.matches(machine) ? machine : null;
    }

    private static Direction facing(BlockEntity machine) {
        return machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
    }

    // Invert only authored effect-bearing coordinates, never scan a structure volume or load chunks.
    private interface Candidate {
        BlockPos offset();
        boolean matches(BlockEntity machine);
    }
    record MolecularCandidate(BlockPos offset, Direction facing, MolecularCenterStructure.StructureLayout layout)
            implements Candidate {
        @Override public boolean matches(BlockEntity machine) {
            return machine instanceof MolecularCenterBlockEntity center
                    && MachineEffectLevels.facing(center) == facing && center.getControllerAnchorLayout() == layout;
        }
    }
    record SingularityCandidate(BlockPos offset, Direction facing, int version) implements Candidate {
        @Override public boolean matches(BlockEntity machine) {
            return machine instanceof SingularityBlockEntity hub && !hub.isSingleBlock()
                    && hub.facing() == facing && hub.structureVersion() == version;
        }
    }

    static List<MolecularCandidate> molecularCoreCandidates() {
        return MolecularCores.CANDIDATES;
    }

    static List<SingularityCandidate> singularityCandidates(SingularityStructure.Type type) {
        return type == SingularityStructure.Type.CORE ? SingularityParts.CORES : SingularityParts.NODES;
    }

    private static final class MolecularCores {
        static final List<MolecularCandidate> CANDIDATES = create();

        private static List<MolecularCandidate> create() {
            var candidates = new ArrayList<MolecularCandidate>();
            for (var layout : MolecularCenterStructure.StructureLayout.values()) {
                if (!layout.isFormed()) continue;
                for (var part : MolecularCenterStructure.parts(layout)) {
                    if (part.partType() != MolecularCenterStructure.PartType.CORE) continue;
                    for (var direction : Direction.Plane.HORIZONTAL) {
                        candidates.add(new MolecularCandidate(MolecularCenterStructure.worldPos(
                                BlockPos.ZERO, direction, part, layout), direction, layout));
                    }
                }
            }
            return List.copyOf(candidates);
        }
    }

    private static final class SingularityParts {
        static final List<SingularityCandidate> CORES = create(SingularityStructure.Type.CORE);
        static final List<SingularityCandidate> NODES = create(SingularityStructure.Type.COLLECTION_NODE);

        private static List<SingularityCandidate> create(SingularityStructure.Type type) {
            var candidates = new ArrayList<SingularityCandidate>();
            for (int version : new int[]{SingularityStructure.VERSION,
                    SingularityStructure.EMBEDDED_VERSION, SingularityStructure.LEGACY_VERSION}) {
                for (var part : SingularityStructure.parts(version)) {
                    if (part.type() != type) continue;
                    for (var direction : Direction.Plane.HORIZONTAL) {
                        candidates.add(new SingularityCandidate(SingularityStructure.worldPos(
                                BlockPos.ZERO, direction, part, version), direction, version));
                    }
                }
            }
            return List.copyOf(candidates);
        }
    }
}
