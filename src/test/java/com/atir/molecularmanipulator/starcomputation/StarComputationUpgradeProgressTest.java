package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class StarComputationUpgradeProgressTest {
    private static final StarComputationUpgradeProgress.Result COMPLETE = StarComputationUpgradeProgress.Result.COMPLETE;
    private static final StarComputationUpgradeProgress.Result BLOCKED = StarComputationUpgradeProgress.Result.BLOCKED;

    @Test
    void physicalRingsAboveThreeDoNotBypassCertificationAndOnlyTheNextLevelCanStart() {
        var progress = new StarComputationUpgradeProgress();
        assertEquals(3, progress.certifiedLevel());
        for (int physical = 0; physical <= 9; physical++) assertEquals(Math.min(physical, 3), progress.effectiveLevel(physical));
        for (int target : new int[] {-1, 0, 1, 2, 3, 5, 6, 7, 8, 9, 10}) assertFalse(progress.start(target));
        assertTrue(progress.start(4));
        assertFalse(progress.start(4));
        assertFalse(progress.start(5));
        assertEquals(4, progress.targetLevel());
    }

    @Test
    void onlyActualPartialEnergyIsCreditedAndTheFinalTickCannotOverpay() {
        var progress = new StarComputationUpgradeProgress();
        progress.start(4);
        assertEquals(StarComputationUpgradeProgress.Result.RUNNING, progress.tick(100, 64, () -> true, demand -> {
            assertEquals(64, demand);
            return 25;
        }));
        assertEquals(25, progress.paidEnergy());
        assertEquals(25, progress.actualRate());
        assertEquals(3, progress.certifiedLevel());
        progress.tick(100, 64, () -> true, demand -> demand);
        assertEquals(89, progress.paidEnergy());
        assertEquals(COMPLETE, progress.tick(100, 64, () -> true, demand -> {
            assertEquals(11, demand);
            return demand;
        }));
        assertEquals(4, progress.certifiedLevel());
        assertEquals(11, progress.actualRate());
        assertEquals(0, progress.targetLevel());
        assertEquals(0, progress.paidEnergy());
        assertFalse(progress.running());
        assertTrue(progress.start(5));
    }

    @Test
    void manualPauseRetainsCreditAndCannotExtractUntilExplicitlyResumed() {
        var progress = new StarComputationUpgradeProgress();
        progress.start(4);
        progress.tick(100, 10, () -> true, demand -> demand);
        assertTrue(progress.pause());
        assertFalse(progress.pause());
        assertEquals(0, progress.actualRate());
        assertEquals(StarComputationUpgradeProgress.Result.PAUSED, progress.tick(100, 10, () -> true, demand -> {
            fail("Paused upgrades must never extract");
            return demand;
        }));
        assertEquals(10, progress.paidEnergy());
        assertFalse(progress.start(5));
        assertTrue(progress.start(4));
        progress.tick(100, 10, () -> true, demand -> demand);
        assertEquals(20, progress.paidEnergy());
    }

    @Test
    void offlineStructureMissingRulesAndChangedConditionsBlockWithoutLosingCredit() {
        var progress = new StarComputationUpgradeProgress();
        progress.start(4);
        progress.tick(100, 10, () -> true, demand -> demand);
        assertEquals(BLOCKED, progress.tick(100, 10, () -> false, demand -> {
            fail("An unmet gate must never extract");
            return demand;
        }));
        assertTrue(progress.running(), "Automatic blocking retains the player's run intent");
        assertEquals(10, progress.paidEnergy());
        assertEquals(0, progress.actualRate());
        progress.tick(100, 10, () -> true, demand -> demand);
        assertEquals(20, progress.paidEnergy());
    }

    @Test
    void completionRechecksConditionsEvenAfterTheLastPayment() {
        var progress = new StarComputationUpgradeProgress();
        var eligible = new AtomicBoolean(true);
        progress.start(4);
        progress.tick(10, 10, eligible::get, demand -> {
            eligible.set(false);
            return demand;
        });
        assertEquals(3, progress.certifiedLevel());
        assertEquals(10, progress.paidEnergy());
        assertTrue(progress.running());
        eligible.set(true);
        assertEquals(COMPLETE, progress.tick(10, 10, eligible::get, demand -> {
            fail("Fully paid progress is never charged twice");
            return 0;
        }));
        assertEquals(4, progress.certifiedLevel());
    }

    @Test
    void liveReloadCanIncreaseOrReducePriceAndRateWithoutDiscardingPaidEnergy() {
        var progress = new StarComputationUpgradeProgress();
        progress.start(4);
        progress.tick(100, 40, () -> true, demand -> demand);
        progress.tick(200, 7, () -> true, demand -> {
            assertEquals(7, demand, "Use the reloaded limit, not the starting rule");
            return demand;
        });
        assertEquals(47, progress.paidEnergy());
        assertEquals(BLOCKED, progress.tick(30, 7, () -> false, demand -> { fail(); return 0; }));
        assertEquals(COMPLETE, progress.tick(30, 7, () -> true, demand -> { fail(); return 0; }));
        assertEquals(4, progress.certifiedLevel());
    }

    @Test
    void zeroCostStillRequiresAnExplicitStartAndCurrentEligibility() {
        var progress = new StarComputationUpgradeProgress();
        assertEquals(StarComputationUpgradeProgress.Result.PAUSED, progress.tick(0, 1, () -> true, demand -> 0));
        progress.start(4);
        assertEquals(BLOCKED, progress.tick(0, 1, () -> false, demand -> { fail(); return 0; }));
        assertEquals(COMPLETE, progress.tick(0, 1, () -> true, demand -> { fail(); return 0; }));
        assertEquals(4, progress.certifiedLevel());
        assertFalse(progress.running(), "Never automatically start the next upgrade");
    }

    @Test
    void exhaustedOrNonFinitePowerCannotInventProgress() {
        for (double supplied : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var progress = new StarComputationUpgradeProgress();
            progress.start(4);
            assertEquals(StarComputationUpgradeProgress.Result.NO_POWER, progress.tick(100, 10, () -> true, demand -> supplied));
            assertEquals(0, progress.paidEnergy());
            assertEquals(0, progress.actualRate());
            assertEquals(3, progress.certifiedLevel());
        }
        var progress = new StarComputationUpgradeProgress();
        progress.start(4);
        progress.tick(100, 10, () -> true, demand -> 500);
        assertEquals(10, progress.paidEnergy(), "Credit cannot exceed the requested extraction");
    }

    @Test
    void invalidRuleNumbersNeverReachTheEnergyService() {
        var progress = new StarComputationUpgradeProgress();
        progress.start(4);
        var calls = new AtomicInteger();
        for (double cost : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(BLOCKED, progress.tick(cost, 10, () -> true, demand -> calls.incrementAndGet()));
        for (double rate : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(BLOCKED, progress.tick(100, rate, () -> true, demand -> calls.incrementAndGet()));
        assertEquals(0, calls.get());
    }

    @Test
    void restartAndNativePackageRoundTripsRetainRunningOrPausedCredit() {
        for (boolean paused : new boolean[] {false, true}) {
            var progress = new StarComputationUpgradeProgress();
            progress.start(4);
            progress.tick(100, 10, () -> true, demand -> 7.5);
            if (paused) progress.pause();
            var restored = roundTrip(progress);
            assertEquals(3, restored.certifiedLevel());
            assertEquals(4, restored.targetLevel());
            assertEquals(7.5, restored.paidEnergy());
            assertEquals(!paused, restored.running());
            assertEquals(0, restored.actualRate(), "Last tick's rate is not a persisted payment");
            assertFalse(restored.migrationPending());
        }
    }

    @Test
    void certificationSurvivesDamageRepairAndRuleRemovalWithoutRepayment() {
        var progress = new StarComputationUpgradeProgress();
        for (int target = 4; target <= 9; target++) {
            assertTrue(progress.start(target));
            assertEquals(COMPLETE, progress.tick(1, 1, () -> true, demand -> demand));
        }
        var restored = roundTrip(progress);
        assertEquals(9, restored.certifiedLevel());
        for (int physical = 0; physical <= 9; physical++) assertEquals(physical, restored.effectiveLevel(physical));
        assertEquals(StarComputationUpgradeProgress.Result.PAUSED, restored.tick(999, 1, () -> false, demand -> { fail(); return 0; }));
        assertFalse(restored.start(9));
    }

    @Test
    void legacyWorldsWaitForARealScanAndMigrationIsOneTimeAndPersistent() {
        var progress = new StarComputationUpgradeProgress();
        var oldSave = new CompoundTag();
        oldSave.putInt("star_orrery_lit_rings", 9);
        progress.load(oldSave);
        assertTrue(progress.migrationPending());
        assertEquals(3, progress.certifiedLevel(), "A saved ring hint alone grants no high-level certification");
        assertFalse(progress.start(4));
        progress = roundTrip(progress);
        assertTrue(progress.migrationPending(), "Saving before chunks restore cannot lose migration intent");
        assertTrue(progress.migrateVerified(7));
        assertEquals(7, progress.certifiedLevel());
        assertFalse(progress.migrateVerified(9), "Repairing or adding rings after migration cannot grant free certification");
        assertEquals(7, roundTrip(progress).certifiedLevel());
        assertTrue(progress.start(8));
    }

    @Test
    void corruptedProgressCannotSkipLevelsOrLoadNonFiniteCredit() {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putInt("certified_level", 3);
        tag.putInt("target_level", 9);
        tag.putDouble("paid_ae", Double.POSITIVE_INFINITY);
        tag.putBoolean("running", true);
        var root = new CompoundTag();
        root.put(StarComputationUpgradeProgress.TAG, tag);
        var progress = new StarComputationUpgradeProgress();
        progress.load(root);
        assertEquals(0, progress.targetLevel());
        assertEquals(0, progress.paidEnergy());
        assertFalse(progress.running());
        tag.putInt("target_level", 4);
        tag.putDouble("paid_ae", Double.NaN);
        progress.load(root);
        assertEquals(4, progress.targetLevel());
        assertEquals(0, progress.paidEnergy());
    }

    private static StarComputationUpgradeProgress roundTrip(StarComputationUpgradeProgress original) {
        var root = new CompoundTag();
        root.put(StarComputationUpgradeProgress.TAG, original.save());
        var restored = new StarComputationUpgradeProgress();
        restored.load(root.copy());
        return restored;
    }
}
