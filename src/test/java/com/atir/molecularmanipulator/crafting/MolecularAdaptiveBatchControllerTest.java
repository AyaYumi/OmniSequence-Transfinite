package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider.PushResult;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;

class MolecularAdaptiveBatchControllerTest {
    private static final long COOLDOWN =
            MolecularAdaptiveBatchController.TRANSIENT_FAILURE_COOLDOWN_TICKS;
    private static final long PROBE_INTERVAL =
            MolecularAdaptiveBatchController.RECOVERY_PROBE_INTERVAL_TICKS;

    @Test
    void invalidInputsAndWindowZeroAreIgnored() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(false, false);
        var pattern = pattern();

        assertEquals(0, controller.getAvailableCrafts(null, pattern, 10));
        assertEquals(0, controller.getAvailableCrafts(provider, null, 10));
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 0));
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, -1));
        assertEquals(Long.MIN_VALUE, controller.getLastAttemptOrder(null, pattern));
        assertFalse(controller.isSingleOnly(null, pattern));

        assertDoesNotThrow(() -> controller.forceSingle(null, pattern));
        assertDoesNotThrow(() -> controller.onAccepted(null, pattern, 1, 1,
                PushResult.ACCEPTED_FULL));
        assertDoesNotThrow(() -> controller.onAccepted(provider, pattern, 0, 1,
                PushResult.ACCEPTED_FULL));
        assertDoesNotThrow(() -> controller.onAccepted(provider, pattern, 1, 1, null));
        assertDoesNotThrow(() -> controller.onRejected(provider, pattern, 0));
        assertDoesNotThrow(() -> controller.onStatusVerification(null, pattern, false));

        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1));
    }

    @Test
    void ordinaryProviderGetsAtMostOnePushPerTickAndGrowsAcrossTicks() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(false, false);
        var pattern = pattern();

        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
        assertFalse(controller.isSingleOnly(provider, pattern));

        clock.advance(1);
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 2, 2, PushResult.ACCEPTED_FULL);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.advance(1);
        assertEquals(4, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void scaledProviderRepeatsVerifiedStableBatchButNotGrowth() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();

        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.advance(1);
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 2, 2, PushResult.ACCEPTED_FULL);
        // The 2 -> 4 growth belongs to the next tick; only 2 may repeat.
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 2, 2, PushResult.ACCEPTED_FULL);
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.advance(1);
        assertEquals(4, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void sameTickRepeatRechecksLiveCapabilityIncludingStableAndSingleOnlyPaths() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var available = new AtomicBoolean(true);
        var provider = provider(true, available::get);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);
        clock.advance(1);
        controller.onRejected(provider, pattern, 128);
        clock.advance(1);
        controller.onAccepted(provider, pattern, 64, 64, PushResult.ACCEPTED_FULL);
        available.set(false);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 64),
                "Even candidate <= stable must recheck the live target");
        available.set(true);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 64),
                "A changed target keeps same-tick backpressure");

        clock.advance(1);
        controller.forceSingle(provider, pattern);
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        available.set(false);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 64));

        controller.setActiveJob(new Object());
        available.set(true);
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        available.set(false);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 64),
                "The growth-limited repeat must recheck the live target too");
    }

    @Test
    void scaledInterfaceWithoutCapabilityStillGetsOnePushPerTick() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, false);
        var pattern = pattern();

        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.advance(1);
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void oneGrowthPerTickEvenForRepeatedStableBatches() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);

        clock.advance(1);
        assertEquals(128, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 128, 128, PushResult.ACCEPTED_FULL);
        // 128 was this tick's only growth step. Further pushes in this tick
        // repeat 128 and must never jump to 256.
        assertEquals(128, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 128, 128, PushResult.ACCEPTED_FULL);
        assertEquals(128, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 128, 128, PushResult.ACCEPTED_FULL);
        assertEquals(128, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.advance(1);
        assertEquals(256, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void rejectedGrowthKeepsStableSizeAndRecoveryNeverResumesDoubling() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);

        clock.advance(1);
        long rejectionTick = clock.value();
        assertEquals(128, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onRejected(provider, pattern, 128);
        assertFalse(controller.isSingleOnly(provider, pattern));
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.advance(1);
        assertEquals(64, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 64, 64, PushResult.ACCEPTED_FULL);
        assertEquals(64, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 64, 64, PushResult.ACCEPTED_FULL);
        clock.set(rejectionTick + PROBE_INTERVAL - 1);
        assertEquals(64, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.set(rejectionTick + PROBE_INTERVAL);
        assertEquals(68, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 68, 68, PushResult.ACCEPTED_FULL);
        assertEquals(68, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(PROBE_INTERVAL - 1);
        assertEquals(68, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(1);
        assertEquals(72, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.set(rejectionTick + COOLDOWN);
        assertEquals(72, controller.getAvailableCrafts(provider, pattern, 1024),
                "Cooldown expiry must not jump from 68 back to 128 or 136");
        controller.onAccepted(provider, pattern, 72, 72, PushResult.ACCEPTED_FULL);
        assertEquals(72, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(PROBE_INTERVAL);
        assertEquals(76, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void rejectionWithoutKnownStableSizeFallsBackAndRecovers() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(false, false);
        var pattern = pattern();

        controller.onRejected(provider, pattern, 32);
        assertFalse(controller.isSingleOnly(provider, pattern));
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.advance(1);
        assertEquals(28, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 28, 28, PushResult.ACCEPTED_FULL);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));

        clock.set(COOLDOWN - 1);
        assertEquals(29, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.set(COOLDOWN);
        assertEquals(29, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void rejectionBelowStableSizeDoesNotJumpBackToOldProbe() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(false, false);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);

        clock.advance(1);
        long rejectionTick = clock.value();
        // A temporary window makes the next probe only 32, which then fails.
        assertEquals(32, controller.getAvailableCrafts(provider, pattern, 32));
        controller.onRejected(provider, pattern, 32);

        clock.advance(1);
        assertEquals(28, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 28, 28, PushResult.ACCEPTED_FULL);
        clock.advance(1);
        assertEquals(28, controller.getAvailableCrafts(provider, pattern, 1024),
                "the recovering batch must not jump back toward historical 64");

        clock.set(rejectionTick + COOLDOWN - 1);
        assertEquals(29, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.set(rejectionTick + COOLDOWN);
        assertEquals(29, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void stableRejectionBacksOffGraduallyAndSuccessfulDeliveryResetsFailureStreak() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);

        clock.advance(1);
        controller.onRejected(provider, pattern, 64);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(1);
        assertEquals(56, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onRejected(provider, pattern, 56);
        clock.advance(1);
        assertEquals(42, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onRejected(provider, pattern, 42);
        clock.advance(1);
        assertEquals(21, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 21, 21, PushResult.ACCEPTED_FULL);
        assertEquals(21, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(1);
        controller.onRejected(provider, pattern, 21);
        clock.advance(1);
        assertEquals(18, controller.getAvailableCrafts(provider, pattern, 1024),
                "A successful full delivery resets rejection severity to 1/8");
        assertFalse(controller.isSingleOnly(provider, pattern));
    }

    @Test
    void duplicateRejectionFeedbackCannotShrinkAgainInTheSameTick() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);
        clock.advance(1);
        controller.onRejected(provider, pattern, 64);
        controller.onRejected(provider, pattern, 56);
        clock.advance(1);
        assertEquals(56, controller.getAvailableCrafts(provider, pattern, 1024));

        var reusable = pattern();
        controller.onReusableRejected(provider, reusable, 64);
        controller.onReusableRejected(provider, reusable, 56);
        assertEquals(0, controller.getReusableWindowCap(provider, reusable));
        clock.advance(1);
        assertEquals(56, controller.getReusableWindowCap(provider, reusable));
    }

    @Test
    void liveBusyFeedbackPreservesLearnedBatchAndReusableCap() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var busy = new AtomicBoolean(false);
        var provider = provider(true, () -> true, busy::get);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);

        clock.advance(1);
        busy.set(true);
        controller.onProviderRejected(provider, pattern, 128);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
        assertEquals(0, controller.getReusableWindowCap(provider, pattern));
        clock.advance(1);
        busy.set(false);
        assertEquals(128, controller.getAvailableCrafts(provider, pattern, 1024),
                "Busy must not become a capacity limit or slow-recovery decision");

        busy.set(true);
        controller.onReusableProviderRejected(provider, pattern, 1024);
        assertEquals(0, controller.getReusableWindowCap(provider, pattern));
        clock.advance(1);
        busy.set(false);
        assertEquals(Long.MAX_VALUE, controller.getReusableWindowCap(provider, pattern));
        controller.onProviderRejected(provider, pattern, 64);
        clock.advance(1);
        assertEquals(56, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void unavailableBusySignalKeepsConservativeRejectionAndDoesNotEscape() {
        for (boolean linkage : new boolean[] {false, true}) {
            var clock = new Tick(0);
            var controller = new MolecularAdaptiveBatchController(clock);
            var provider = provider(true, () -> true, () -> {
                if (linkage) throw new NoClassDefFoundError("provider busy dependency");
                throw new IllegalStateException("busy signal unavailable");
            });
            var pattern = pattern();
            assertDoesNotThrow(() -> controller.onProviderRejected(provider, pattern, 64));
            assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
            clock.advance(1);
            assertEquals(56, controller.getAvailableCrafts(provider, pattern, 1024));
            assertDoesNotThrow(() -> controller.onReusableProviderRejected(provider, pattern, 64));
            clock.advance(1);
            assertEquals(56, controller.getReusableWindowCap(provider, pattern));
        }
    }

    @Test
    void reusableRejectionSeverityResetsAfterAcceptanceWithoutResettingItsCap() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(false, false);
        var pattern = pattern();
        controller.onReusableProviderRejected(provider, pattern, 64);
        clock.advance(1);
        assertEquals(56, controller.getReusableWindowCap(provider, pattern));
        controller.onReusableProviderRejected(provider, pattern, 56);
        clock.advance(1);
        assertEquals(42, controller.getReusableWindowCap(provider, pattern));
        controller.onReusableAccepted(provider, pattern);
        assertEquals(42, controller.getReusableWindowCap(provider, pattern));
        controller.onReusableProviderRejected(provider, pattern, 42);
        clock.advance(1);
        assertEquals(36, controller.getReusableWindowCap(provider, pattern));
    }

    @Test
    void hardCapacityRecoveryConvergesWithoutPeriodicHalvingOrDoubling() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        long capacity = 100_000;
        long previousAccepted = 0;
        long rejectedProbe = 0;
        int rejections = 0;
        var rejectedSamples = new java.util.ArrayList<String>();
        for (int tick = 0; tick < 2000; tick++) {
            clock.set(tick);
            long batch = controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE);
            assertTrue(batch > 0);
            if (batch > capacity) {
                rejections++;
                if (rejectedSamples.size() < 20) rejectedSamples.add(tick + ":" + batch);
                rejectedProbe = batch;
                controller.onRejected(provider, pattern, batch);
                assertEquals(0, controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));
            } else {
                if (rejections > 0) {
                    assertTrue(batch >= previousAccepted, "A failed growth probe retains the verified batch");
                    assertTrue(batch < rejectedProbe, "Recovery must not double back to the rejected probe");
                    assertTrue(batch <= previousAccepted + Math.max(1, previousAccepted / 16));
                }
                controller.onAccepted(provider, pattern, batch, batch, PushResult.ACCEPTED_FULL);
                assertEquals(batch, controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));
                previousAccepted = batch;
            }
        }
        assertTrue(rejections > 0 && rejections < 20,
                "Capacity failures stay spaced instead of oscillating: rejections="
                        + rejections + ", accepted=" + previousAccepted + ", probes=" + rejectedSamples);
        assertTrue(previousAccepted >= capacity * 15 / 16, "Recovery approaches the available capacity");

        // A real capacity loss must still reduce promptly; smoothing is not a
        // licence to force an oversized batch into the provider.
        capacity = 1_000;
        boolean recovered = false;
        for (int tick = 2000; tick < 2020; tick++) {
            clock.set(tick);
            long batch = controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE);
            if (batch <= capacity) {
                controller.onAccepted(provider, pattern, batch, batch, PushResult.ACCEPTED_FULL);
                recovered = true;
                break;
            }
            controller.onRejected(provider, pattern, batch);
        }
        assertTrue(recovered, "Persistent real capacity loss still backs off within bounded ticks");
    }

    @Test
    void recoveryWindowShrinkDoesNotEraseOrAmplifyTheVerifiedTarget() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);
        clock.advance(1);
        long failedAt = clock.value();
        controller.onRejected(provider, pattern, 128);
        clock.advance(1);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1));
        controller.onAccepted(provider, pattern, 1, 128, PushResult.ACCEPTED_FULL);
        assertEquals(64, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.set(failedAt + PROBE_INTERVAL);
        assertEquals(68, controller.getAvailableCrafts(provider, pattern, 1024),
                "Neither the tiny accepted batch nor a huge request may destroy the recovery base");
    }

    @Test
    void queuedAndUnverifiedResultsKeepBackpressureAndDoNotRepeat() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 4);

        clock.advance(1);
        assertEquals(8, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onAccepted(provider, pattern, 8, 8, PushResult.ACCEPTED_QUEUED);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(1);
        assertEquals(8, controller.getAvailableCrafts(provider, pattern, 1024));

        var unverifiedClock = new Tick(0);
        var unverifiedController = new MolecularAdaptiveBatchController(unverifiedClock);
        var unverifiedPattern = pattern();
        growTo(unverifiedController, unverifiedClock, provider, unverifiedPattern, 4);

        unverifiedClock.advance(1);
        assertEquals(8, unverifiedController.getAvailableCrafts(
                provider, unverifiedPattern, 1024));
        unverifiedController.onAccepted(provider, unverifiedPattern, 8, 8,
                PushResult.ACCEPTED_UNVERIFIED);
        assertEquals(0, unverifiedController.getAvailableCrafts(
                provider, unverifiedPattern, 1024));

        unverifiedClock.advance(1);
        assertEquals(1, unverifiedController.getAvailableCrafts(
                provider, unverifiedPattern, 1024));
        unverifiedController.onAccepted(provider, unverifiedPattern, 1, 1,
                PushResult.ACCEPTED_FULL);
        assertEquals(1, unverifiedController.getAvailableCrafts(
                provider, unverifiedPattern, 1024));
        unverifiedClock.advance(1);
        assertEquals(2, unverifiedController.getAvailableCrafts(
                provider, unverifiedPattern, 1024));
    }

    @Test
    void cooldownExpiryCannotSkipSingleRevalidationAfterAnUnverifiedDelivery() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);
        clock.advance(1);
        long failedAt = clock.value();
        controller.onRejected(provider, pattern, 128);
        clock.set(failedAt + COOLDOWN - 1);
        controller.onAccepted(provider, pattern, 64, 64, PushResult.ACCEPTED_UNVERIFIED);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(1);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024),
                "Timer expiry cannot substitute for a fully verified single delivery");
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(PROBE_INTERVAL);
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    @Test
    void queuedAndUnverifiedAcceptanceResetSeverityWithoutDisprovingCapacity() {
        for (var result : new PushResult[]{PushResult.ACCEPTED_QUEUED, PushResult.ACCEPTED_UNVERIFIED}) {
            var clock = new Tick(0);
            var controller = new MolecularAdaptiveBatchController(clock);
            var provider = provider(true, true);
            var pattern = pattern();
            growTo(controller, clock, provider, pattern, 64);
            clock.advance(1);
            controller.onRejected(provider, pattern, 64);
            clock.advance(1);
            controller.onAccepted(provider, pattern, 56, 56, result);
            assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
            clock.advance(1);
            controller.onRejected(provider, pattern, 56);
            clock.advance(1);
            assertEquals(49, controller.getAvailableCrafts(provider, pattern, 1024),
                    result + " must reset failure severity but cannot erase the rejection bound");
        }
    }

    @Test
    void observedFailureBoundaryIsRetestedAfterCooldownAndClearedByFullSuccess() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 64);
        clock.advance(1);
        long failedAt = clock.value();
        controller.onRejected(provider, pattern, 65);
        clock.advance(1);
        controller.onAccepted(provider, pattern, 64, 64, PushResult.ACCEPTED_FULL);
        clock.set(failedAt + COOLDOWN - 1);
        assertEquals(64, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(1);
        assertEquals(65, controller.getAvailableCrafts(provider, pattern, 1024),
                "An expiring observed boundary is retryable, not a permanent hard cap");
        controller.onAccepted(provider, pattern, 65, 65, PushResult.ACCEPTED_FULL);
        assertEquals(65, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(PROBE_INTERVAL);
        assertEquals(69, controller.getAvailableCrafts(provider, pattern, 1024),
                "A full acceptance disproves the remembered boundary");
    }

    @Test
    void temporaryWindowShrinkDoesNotForgetLearnedStableBatch() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        growTo(controller, clock, provider, pattern, 8);

        clock.advance(1);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1));
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1));

        clock.advance(1);
        assertEquals(8, controller.getAvailableCrafts(provider, pattern, 8));
        clock.advance(1);
        assertEquals(16, controller.getAvailableCrafts(provider, pattern, 16));
    }

    @Test
    void forcedSingleOnlyAndStatusMismatchPersistUntilJobSwitch() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(true, true);
        var pattern = pattern();
        var firstJob = new Object();
        controller.setActiveJob(firstJob);
        growTo(controller, clock, provider, pattern, 4);

        // Re-setting the same active job must retain learned state.
        controller.setActiveJob(firstJob);
        clock.advance(1);
        assertEquals(8, controller.getAvailableCrafts(provider, pattern, 1024));

        controller.forceSingle(provider, pattern);
        assertTrue(controller.isSingleOnly(provider, pattern));
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(10_000);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));

        controller.setActiveJob(new Object());
        assertFalse(controller.isSingleOnly(provider, pattern));
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));

        growTo(controller, clock, provider, pattern, 4);
        clock.advance(1);
        controller.onStatusVerification(provider, pattern, false);
        assertTrue(controller.isSingleOnly(provider, pattern));
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(1);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1024));
        clock.advance(10_000);
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));

        controller.setActiveJob(new Object());
        assertFalse(controller.isSingleOnly(provider, pattern));
    }

    @Test
    void longMaxValuesAndTickOverflowSaturateSafely() {
        var clock = new Tick(0);
        var controller = new MolecularAdaptiveBatchController(clock);
        var provider = provider(false, false);
        var pattern = pattern();

        controller.onAccepted(provider, pattern, Long.MAX_VALUE, Long.MAX_VALUE,
                PushResult.ACCEPTED_FULL);
        clock.advance(1);
        assertEquals(Long.MAX_VALUE,
                controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));

        controller.onRejected(provider, pattern, Long.MAX_VALUE);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));
        clock.advance(1);
        long fallback = Long.MAX_VALUE - Long.MAX_VALUE / 8 - 1;
        assertEquals(fallback,
                controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));
        controller.onAccepted(provider, pattern, fallback, fallback, PushResult.ACCEPTED_FULL);
        clock.advance(COOLDOWN);
        assertEquals(fallback + fallback / 16,
                controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));
        controller.onAccepted(provider, pattern, Long.MAX_VALUE, Long.MAX_VALUE, PushResult.ACCEPTED_FULL);
        clock.advance(PROBE_INTERVAL);
        assertEquals(Long.MAX_VALUE, controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE));

        controller.setActiveJob(new Object());
        clock.set(Long.MAX_VALUE - 10);
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        clock.set(Long.MAX_VALUE - 1);
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));
        controller.onRejected(provider, pattern, 2);
        clock.set(Long.MAX_VALUE);
        // The cooldown end saturates at Long.MAX_VALUE and must not wrap.
        assertEquals(2, controller.getAvailableCrafts(provider, pattern, 1024));
    }

    private static void growTo(MolecularAdaptiveBatchController controller,
            Tick clock, ICraftingProvider provider, IPatternDetails pattern, long target) {
        long size = 1;
        while (true) {
            assertEquals(size, controller.getAvailableCrafts(provider, pattern, 1024),
                    "expected growth to " + size);
            controller.onAccepted(provider, pattern, size, size,
                    PushResult.ACCEPTED_FULL);
            if (size == target) {
                return;
            }
            clock.advance(1);
            size *= 2;
        }
    }

    private static ICraftingProvider provider(boolean scaledInterface,
            boolean supportsScaled) {
        return provider(scaledInterface, () -> supportsScaled);
    }

    private static ICraftingProvider provider(boolean scaledInterface,
            BooleanSupplier supportsScaled) {
        return provider(scaledInterface, supportsScaled, () -> false);
    }

    private static ICraftingProvider provider(boolean scaledInterface,
            BooleanSupplier supportsScaled, BooleanSupplier busy) {
        Class<?>[] interfaces = scaledInterface
                ? new Class<?>[]{ICraftingProvider.class, MolecularScaledBatchProvider.class}
                : new Class<?>[]{ICraftingProvider.class};
        return (ICraftingProvider) Proxy.newProxyInstance(
                MolecularAdaptiveBatchControllerTest.class.getClassLoader(),
                interfaces,
                (proxy, method, args) -> switch (method.getName()) {
                    case "molecularmanipulator$supportsScaledBatch" -> supportsScaled.getAsBoolean();
                    case "isBusy" -> busy.getAsBoolean();
                    case "toString" -> scaledInterface ? "scaled-provider" : "provider";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static IPatternDetails pattern() {
        return (IPatternDetails) Proxy.newProxyInstance(
                MolecularAdaptiveBatchControllerTest.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "pattern";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null ? null : args[0]);
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    private static final class Tick implements LongSupplier {
        private long value;

        private Tick(long value) {
            this.value = value;
        }

        @Override
        public long getAsLong() {
            return value;
        }

        private long value() {
            return value;
        }

        private void set(long value) {
            this.value = value;
        }

        private void advance(long delta) {
            value = value > Long.MAX_VALUE - delta
                    ? Long.MAX_VALUE
                    : value + delta;
        }
    }
}
