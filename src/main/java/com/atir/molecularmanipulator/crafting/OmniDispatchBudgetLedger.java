package com.atir.molecularmanipulator.crafting;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure per-tick accounting for the Omni controller's shared dispatch budgets.
 *
 * <p>Lane reservations are allocated once per tick. A claim consumes its own
 * reservation and may borrow work and attempts as before. Finite recipe copies
 * are separate: a ready lane can use all copies returned by earlier lanes
 * without consuming another lane's reservation. A second
 * claim in the same tick is rejected without touching the returned pool, and a
 * second record is rejected so unused work cannot be refunded twice.</p>
 *
 * <p>All lanes share the same totals. All arithmetic saturates instead of overflowing.</p>
 */
public final class OmniDispatchBudgetLedger<L> {
    public static final Claim EMPTY_CLAIM = new Claim(0L, 0);

    public record Claim(long workUnits, int unscaledAttempts, long recipeCopies) {
        public Claim(long workUnits, int unscaledAttempts) {
            this(workUnits, unscaledAttempts, 0L);
        }

        public Claim {
            if (workUnits < 0L || unscaledAttempts < 0 || recipeCopies < 0L) {
                throw new IllegalArgumentException("Negative dispatch claim");
            }
        }

        public boolean isEmpty() {
            return workUnits == 0L && unscaledAttempts == 0 && recipeCopies == 0L;
        }
    }

    private record LaneClaim(Claim allowance, boolean recorded) {
    }

    private final Map<L, Claim> reservations = new IdentityHashMap<>();
    private final Map<L, LaneClaim> claims = new IdentityHashMap<>();
    private long tick = Long.MIN_VALUE;
    private boolean claiming;
    private long totalWorkUnits;
    private long workUnitsRemaining;
    private long reservedWorkUnitTotal;
    private int totalUnscaledAttempts;
    private int unscaledAttemptsRemaining;
    private int reservedUnscaledAttemptTotal;
    private long totalRecipeCopies;
    private long recipeCopiesRemaining;
    private long reservedRecipeCopyTotal;

    /**
     * Starts a new tick and discards all reservations and claims. Must be called
     * before the tick's reservations are registered.
     */
    public void beginTick(long tick, long totalWorkUnits, int totalUnscaledAttempts) {
        beginTick(tick, totalWorkUnits, totalUnscaledAttempts, 0L);
    }

    public void beginTick(long tick, long totalWorkUnits, int totalUnscaledAttempts, long totalRecipeCopies) {
        this.tick = tick;
        claiming = false;
        this.totalWorkUnits = Math.max(0L, totalWorkUnits);
        this.workUnitsRemaining = this.totalWorkUnits;
        this.reservedWorkUnitTotal = 0L;
        this.totalUnscaledAttempts = Math.max(0, totalUnscaledAttempts);
        this.unscaledAttemptsRemaining = this.totalUnscaledAttempts;
        this.reservedUnscaledAttemptTotal = 0;
        this.totalRecipeCopies = Math.max(0L, totalRecipeCopies);
        this.recipeCopiesRemaining = this.totalRecipeCopies;
        this.reservedRecipeCopyTotal = 0L;
        reservations.clear();
        claims.clear();
    }

    /** Reserves one lane's fair share. Returns false for a duplicate reservation. */
    public boolean reserve(L lane, long workUnits, int unscaledAttempts) {
        return reserve(lane, workUnits, unscaledAttempts, 0L);
    }

    public boolean reserve(L lane, long workUnits, int unscaledAttempts, long recipeCopies) {
        Objects.requireNonNull(lane, "lane");
        if (workUnits < 0L || unscaledAttempts < 0 || recipeCopies < 0L) {
            throw new IllegalArgumentException("Negative dispatch reservation");
        }
        if (claiming || reservations.containsKey(lane)) {
            return false;
        }
        long work = Math.min(workUnits, totalWorkUnits - reservedWorkUnitTotal);
        int unscaled = Math.min(unscaledAttempts, totalUnscaledAttempts - reservedUnscaledAttemptTotal);
        long copies = Math.min(recipeCopies, totalRecipeCopies - reservedRecipeCopyTotal);
        reservations.put(lane, new Claim(work, unscaled, copies));
        reservedWorkUnitTotal += work;
        reservedUnscaledAttemptTotal += unscaled;
        reservedRecipeCopyTotal += copies;
        return true;
    }

    /**
     * Issues the lane's allowance for this tick. A duplicate claim returns an
     * empty claim and leaves the returned pools untouched.
     */
    public Claim claim(L lane) {
        Objects.requireNonNull(lane, "lane");
        claiming = true;
        if (claims.containsKey(lane)) {
            return EMPTY_CLAIM;
        }

        var reservation = reservations.remove(lane);
        if (reservation == null) {
            claims.put(lane, new LaneClaim(EMPTY_CLAIM, false));
            return EMPTY_CLAIM;
        }
        long reservedWork = reservation.workUnits();
        int reservedUnscaled = reservation.unscaledAttempts();
        long reservedCopies = reservation.recipeCopies();

        // The lane's own reservation is already part of the remaining pool.
        // Consume it first so "returned" only contains other lanes' unclaimed
        // shares and earlier refunds.
        workUnitsRemaining = Math.max(0L, workUnitsRemaining - reservedWork);
        reservedWorkUnitTotal = Math.max(0L, reservedWorkUnitTotal - reservedWork);
        unscaledAttemptsRemaining = Math.max(
                0, unscaledAttemptsRemaining - reservedUnscaled);
        reservedUnscaledAttemptTotal = Math.max(
                0, reservedUnscaledAttemptTotal - reservedUnscaled);

        long returnedWork = Math.max(0L, workUnitsRemaining - reservedWorkUnitTotal);
        long borrowedWork = Math.min(reservedWork, returnedWork);
        long workAllowance = saturatingAdd(reservedWork, borrowedWork);
        workUnitsRemaining = Math.max(0L, workUnitsRemaining - borrowedWork);

        int returnedUnscaled = Math.max(
                0, unscaledAttemptsRemaining - reservedUnscaledAttemptTotal);
        int borrowedUnscaled = Math.min(reservedUnscaled, returnedUnscaled);
        int unscaledAllowance = saturatingAdd(reservedUnscaled, borrowedUnscaled);
        unscaledAttemptsRemaining = Math.max(
                0, unscaledAttemptsRemaining - borrowedUnscaled);

        reservedRecipeCopyTotal -= reservedCopies;
        // A lane with no work allowance releases its copies immediately.
        long copies = workAllowance > 0 ? recipeCopiesRemaining - reservedRecipeCopyTotal : 0L;
        recipeCopiesRemaining -= copies;
        var claim = new Claim(workAllowance, unscaledAllowance, copies);
        claims.put(lane, new LaneClaim(claim, false));
        return claim;
    }

    /**
     * Returns unused work from the actual claim issued to this lane. The
     * declared allowance must match that claim; stale or duplicate callers are
     * ignored, and only the first successful record refunds anything.
     */
    public boolean record(
            L lane,
            long declaredWorkUnits,
            int declaredUnscaledAttempts,
            long usedWorkUnits,
            int usedUnscaledAttempts) {
        return record(lane, declaredWorkUnits, declaredUnscaledAttempts, 0L, usedWorkUnits, usedUnscaledAttempts, 0L);
    }

    public boolean record(L lane, long declaredWorkUnits, int declaredUnscaledAttempts, long declaredRecipeCopies,
            long usedWorkUnits, int usedUnscaledAttempts, long usedRecipeCopies) {
        Objects.requireNonNull(lane, "lane");
        var entry = claims.get(lane);
        if (entry == null || entry.recorded()) {
            return false;
        }
        var allowance = entry.allowance();
        if (allowance.workUnits() != declaredWorkUnits
                || allowance.unscaledAttempts() != declaredUnscaledAttempts
                || allowance.recipeCopies() != declaredRecipeCopies) {
            return false;
        }

        long chargedWork = Math.max(
                0L, Math.min(allowance.workUnits(), usedWorkUnits));
        long unusedWork = allowance.workUnits() - chargedWork;
        workUnitsRemaining = Math.min(
                totalWorkUnits, saturatingAdd(workUnitsRemaining, unusedWork));

        int chargedUnscaled = Math.max(
                0, Math.min(allowance.unscaledAttempts(), usedUnscaledAttempts));
        int unusedUnscaled = allowance.unscaledAttempts() - chargedUnscaled;
        unscaledAttemptsRemaining = Math.min(
                totalUnscaledAttempts,
                saturatingAdd(unscaledAttemptsRemaining, unusedUnscaled));

        long chargedCopies = Math.clamp(usedRecipeCopies, 0L, allowance.recipeCopies());
        recipeCopiesRemaining += allowance.recipeCopies() - chargedCopies;
        claims.put(lane, new LaneClaim(allowance, true));
        return true;
    }

    public boolean hasClaimed(L lane) {
        return claims.containsKey(lane);
    }

    public long tick() {
        return tick;
    }

    public long totalWorkUnits() {
        return totalWorkUnits;
    }

    public long remainingWorkUnits() {
        return workUnitsRemaining;
    }

    public long reservedWorkUnits() {
        return reservedWorkUnitTotal;
    }

    public int totalUnscaledAttempts() {
        return totalUnscaledAttempts;
    }

    public int remainingUnscaledAttempts() {
        return unscaledAttemptsRemaining;
    }

    public int reservedUnscaledAttempts() {
        return reservedUnscaledAttemptTotal;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static int saturatingAdd(int left, int right) {
        if (right > Integer.MAX_VALUE - left) {
            return Integer.MAX_VALUE;
        }
        return left + right;
    }
}
