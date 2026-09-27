package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.MolecularManipulator;

/** Server-thread-only, aggregated diagnostics. No per-item log output. */
public final class OmniExactReturnProfiler {
    private static long calls, elapsed, accounting, inventory, notifications, requested, sent, maximum;
    private static long nextReport;
    private static long observed;
    private OmniExactReturnProfiler() { }

    public static void record(long nanos, long accountingNanos, long inventoryNanos,
            long notificationNanos, long requestedCount, long sentCount) {
        observed++;
        calls++; elapsed += nanos; accounting += accountingNanos; inventory += inventoryNanos;
        notifications += notificationNanos; requested += requestedCount; sent += sentCount;
        maximum = Math.max(maximum, nanos);
        long now = System.nanoTime();
        if (nextReport == 0) nextReport = now + 10_000_000_000L;
        if (now >= nextReport) {
            var result = snapshotAndReset();
            MolecularManipulator.LOGGER.info(
                    "Omni exact return profile: observedCalls={}, timedCalls={}, sampledTotalMs={}, sampledAccountingMs={}, sampledInventoryMs={}, sampledNotificationMs={}, sampledNotificationsRequested={}, sampledNotificationsSent={}, maxSampledCallUs={}",
                    result.observedCalls(), result.calls(), result.totalNanos() / 1_000_000.0, result.accountingNanos() / 1_000_000.0,
                    result.inventoryNanos() / 1_000_000.0, result.notificationNanos() / 1_000_000.0,
                    result.notificationsRequested(), result.notificationsSent(), result.maxCallNanos() / 1_000.0);
            nextReport = now + 10_000_000_000L;
        }
    }
    public static void recordUnprofiled() { observed++; }

    public static Snapshot snapshotAndReset() {
        var result = new Snapshot(calls, elapsed, accounting, inventory, notifications, requested, sent, maximum, observed);
        calls = elapsed = accounting = inventory = notifications = requested = sent = maximum = 0;
        nextReport = 0;
        observed = 0;
        return result;
    }

    public record Snapshot(long calls, long totalNanos, long accountingNanos, long inventoryNanos,
            long notificationNanos, long notificationsRequested, long notificationsSent, long maxCallNanos, long observedCalls) { }
}
