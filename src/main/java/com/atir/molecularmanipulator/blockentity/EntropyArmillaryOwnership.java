package com.atir.molecularmanipulator.blockentity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Server-thread ownership of armillary slots.
 *
 * <p>Facing + ring + slot is not enough to identify a controller: two engines
 * can overlap geometrically or be built next to each other. A slot is therefore
 * claimed by exactly one core at a time. The cache only mirrors the blocks the
 * cores actually verify; it is cleared on level unload and rebuilt by the normal
 * ten-tick scan.</p>
 */
public final class EntropyArmillaryOwnership {
    private static final Map<ServerLevel, Map<BlockPos, BlockPos>> OWNERS = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<BlockPos, Set<BlockPos>>> MEMBERS = new WeakHashMap<>();

    private EntropyArmillaryOwnership() {
    }

    public static boolean claim(ServerLevel level, BlockPos core, BlockPos anchor) {
        var coreKey = core.immutable();
        var anchorKey = anchor.immutable();
        var owners = OWNERS.computeIfAbsent(level, ignored -> new HashMap<>());
        var previous = owners.putIfAbsent(anchorKey, coreKey);
        if (previous != null && !previous.equals(coreKey)) {
            // A controller whose chunk is no longer loaded cannot keep a slot
            // reserved forever. Ownership is taken over only after the owner
            // can no longer be validated at all.
            if (level.hasChunkAt(previous)) {
                return false;
            }
            releaseIfOwner(level, anchorKey, previous);
            owners = OWNERS.computeIfAbsent(level, ignored -> new HashMap<>());
            owners.put(anchorKey, coreKey);
        }
        MEMBERS.computeIfAbsent(level, ignored -> new HashMap<>())
                .computeIfAbsent(coreKey, ignored -> new HashSet<>())
                .add(anchorKey);
        return true;
    }

    @Nullable
    public static BlockPos owner(ServerLevel level, BlockPos anchor) {
        var owners = OWNERS.get(level);
        return owners == null ? null : owners.get(anchor);
    }

    public static boolean ownedByOther(ServerLevel level, BlockPos anchor, BlockPos core) {
        var owner = owner(level, anchor);
        return owner != null && !owner.equals(core);
    }

    public static Set<BlockPos> members(ServerLevel level, BlockPos core) {
        var members = MEMBERS.get(level);
        if (members == null) {
            return Set.of();
        }
        var owned = members.get(core);
        return owned == null ? Set.of() : Set.copyOf(owned);
    }

    public static void retain(ServerLevel level, BlockPos core, Set<BlockPos> keep) {
        var coreKey = core.immutable();
        var members = MEMBERS.get(level);
        if (members == null) {
            return;
        }
        var owned = members.get(coreKey);
        if (owned == null) {
            return;
        }
        for (var anchor : new ArrayList<>(owned)) {
            if (!keep.contains(anchor)) {
                releaseIfOwner(level, anchor, coreKey);
            }
        }
    }

    public static void releaseIfOwner(ServerLevel level, BlockPos anchor, BlockPos core) {
        var owners = OWNERS.get(level);
        if (owners == null) {
            return;
        }
        var coreKey = core.immutable();
        if (coreKey.equals(owners.get(anchor))) {
            owners.remove(anchor);
            var members = MEMBERS.get(level);
            if (members != null) {
                var owned = members.get(coreKey);
                if (owned != null) {
                    owned.remove(anchor);
                    if (owned.isEmpty()) {
                        members.remove(coreKey);
                    }
                }
                if (members.isEmpty()) {
                    MEMBERS.remove(level);
                }
            }
            if (owners.isEmpty()) {
                OWNERS.remove(level);
            }
        }
    }

    public static void release(ServerLevel level, BlockPos core) {
        var coreKey = core.immutable();
        var members = MEMBERS.get(level);
        if (members == null) {
            return;
        }
        var owned = members.remove(coreKey);
        if (owned != null) {
            var owners = OWNERS.get(level);
            if (owners != null) {
                for (var anchor : owned) {
                    if (coreKey.equals(owners.get(anchor))) {
                        owners.remove(anchor);
                    }
                }
                if (owners.isEmpty()) {
                    OWNERS.remove(level);
                }
            }
        }
        if (members.isEmpty()) {
            MEMBERS.remove(level);
        }
    }

    /** Level unload keeps no unverified ownership across a server/save boundary. */
    public static void clear(ServerLevel level) {
        OWNERS.remove(level);
        MEMBERS.remove(level);
    }
}
