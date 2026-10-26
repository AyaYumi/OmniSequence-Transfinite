package com.atir.molecularmanipulator.crafting;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Least-recently-served lane selection by stable object identity.
 *
 * <p>The old progress-lane selection sorted each volatile active set by
 * {@link System#identityHashCode(Object)} and indexed it by a rotation counter.
 * When lanes joined or left, the same lane could be selected again and again
 * while another lane was never selected. Remembering when each lane was last
 * served makes selection independent of the current set's size or ordering.
 * Temporarily ineligible lanes retain their waiting history without consuming
 * a service turn. A new lane joins at the back of the queue, so churn cannot
 * keep starving lanes that were already active.</p>
 *
 * <p>Pure Java and independent of server/deadline state. The service is
 * per-server, so it never creates a per-core deadline.</p>
 */
public final class OmniDispatchLaneRotation<L> {
    private final Map<L, Long> lastServed = new IdentityHashMap<>();
    private long serviceSequence;

    /**
     * Selects and marks the least recently served active lane. A lane seen for
     * the first time (or seen again after leaving the active set) joins at the
     * current service position, behind lanes that have already waited.
     * Returns null for an empty active set.
     */
    public L select(Collection<? extends L> activeLanes) {
        return select(activeLanes, ignored -> true);
    }

    /** Retains all active lanes, but serves only a lane eligible in this tick. */
    public L select(Collection<? extends L> activeLanes, Predicate<? super L> eligible) {
        if (activeLanes == null || activeLanes.isEmpty()) {
            lastServed.clear();
            return null;
        }

        Set<L> active = Collections.newSetFromMap(new IdentityHashMap<>());
        active.addAll(activeLanes);
        lastServed.keySet().removeIf(lane -> !active.contains(lane));

        L selected = null;
        long selectedServedAt = 0L;
        for (L lane : activeLanes) {
            if (lane == null || !active.contains(lane)) {
                continue;
            }
            // Joining lanes remember one fixed position instead of being
            // re-evaluated as new every tick, so churn cannot starve them.
            long servedAt = lastServed.computeIfAbsent(
                    lane, ignored -> serviceSequence);
            if (!eligible.test(lane)) {
                continue;
            }
            if (selected == null
                    || Long.compareUnsigned(servedAt, selectedServedAt) < 0) {
                selected = lane;
                selectedServedAt = servedAt;
            }
        }
        if (selected == null) {
            return null;
        }
        // Unsigned comparison keeps ordering correct even if the counter wraps
        // after Long.MAX_VALUE services.
        serviceSequence++;
        lastServed.put(selected, serviceSequence);
        return selected;
    }

    /** Number of remembered lanes, exposed for diagnostics and tests. */
    public int trackedLanes() {
        return lastServed.size();
    }
}
