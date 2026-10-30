package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider.PushResult;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class MolecularAdaptiveBatchControllerTest {
    private long tick;
    private final MolecularAdaptiveBatchController controller = new MolecularAdaptiveBatchController(() -> tick);
    private final ICraftingProvider provider = provider(() -> false, false);
    private final IPatternDetails pattern = pattern();

    @Test void ordinaryProviderGrowsThroughEveryRoundInTheSameTick() {
        for (long round = 1; round <= 4096; round *= 2) {
            assertEquals(round, available());
            controller.onAccepted(provider, pattern, round, round, PushResult.ACCEPTED_FULL);
        }
        assertEquals(8192, available());
        assertEquals(0, tick);
        assertFalse(controller.isSingleOnly(provider, pattern));
    }

    @Test void eachPushStillConsumesOnlyOneOrdinaryCall() {
        assertEquals(1, available());
        accept(1);
        assertEquals(2, available());
        accept(1);
        assertEquals(1, available());
        accept(1);
        assertEquals(4, available());
        accept(3);
        assertEquals(1, available());
        accept(1);
        assertEquals(8, available());
    }

    @Test void capacityKeepsTheWholeAcceptedCountIncludingAnIncompleteRound() {
        barrier(13);
        assertEquals(0, available());
        for (tick = 1; tick < 20; tick++) {
            assertEquals(13, available());
            accept(13);
            assertEquals(0, available(), "No extra failing call during the cooldown");
        }
        assertEquals(14, available());
    }

    @Test void recoveryAddsOneTwoFourEightAboveTheSameBaseline() {
        barrier(13);
        tick = 20;
        assertEquals(14, available());
        accept(14);
        assertEquals(1, available(), "The next target is 13 + 2 = 15");
        accept(1);
        assertEquals(2, available(), "The next target is 13 + 4 = 17");
        accept(2);
        assertEquals(4, available(), "The next target is 13 + 8 = 21");
        accept(4);
        assertEquals(8, available(), "The next target is 13 + 16 = 29");
    }

    @Test void anotherBarrierRestartsCooldownAtTheNewActualRate() {
        barrier(13);
        tick = 20;
        accept(18);
        controller.onRejected(provider, pattern, 1);
        assertEquals(0, available());
        for (tick = 21; tick < 40; tick++) {
            assertEquals(18, available());
            accept(18);
            assertEquals(0, available());
        }
        assertEquals(19, available());
        accept(19);
        assertEquals(1, available());
    }

    @Test void capacityLossAdjustsToActualSuccessfulCallsWithoutRepeatedHalving() {
        barrier(100);
        tick++;
        accept(3);
        controller.onRejected(provider, pattern, 1);
        tick++;
        assertEquals(3, available());
    }

    @Test void noSuccessfulCallDoesNotEraseTheKnownRate() {
        barrier(100);
        tick++;
        controller.onRejected(provider, pattern, 1);
        tick++;
        assertEquals(100, available());
    }

    @Test void busyBeforeDispatchDoesNotResetRecoveryOrShrinkCapacity() {
        barrier(13);
        tick = 19;
        controller.onBusy(provider, pattern);
        assertEquals(0, available());
        tick = 20;
        assertEquals(14, available());
    }

    @Test void nativeProviderFillingTheReceiverOnItsFirstPushStopsProbing() {
        var busy = new AtomicBoolean(false);
        var nativeProvider = provider(busy::get, false);
        controller.onAccepted(nativeProvider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        busy.set(true);
        controller.onBusy(nativeProvider, pattern);
        assertEquals(0, controller.getAvailableCrafts(nativeProvider, pattern, Long.MAX_VALUE));
        tick++;
        busy.set(false);
        assertEquals(1, controller.getAvailableCrafts(nativeProvider, pattern, Long.MAX_VALUE));
        controller.onAccepted(nativeProvider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(0, controller.getAvailableCrafts(nativeProvider, pattern, Long.MAX_VALUE));
    }

    @Test void growthDoesNotQueryAnyProviderBatchCapability() {
        var unsupported = provider(() -> false, true);
        assertEquals(1, controller.getAvailableCrafts(unsupported, pattern, 100));
        controller.onAccepted(unsupported, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(2, controller.getAvailableCrafts(unsupported, pattern, 100));
        controller.onAccepted(unsupported, pattern, 2, 2, PushResult.ACCEPTED_FULL);
        assertEquals(4, controller.getAvailableCrafts(unsupported, pattern, 100));
    }

    @Test void aTemporaryInputWindowDoesNotEraseGrowth() {
        accept(7);
        assertEquals(8, available());
        assertEquals(1, controller.getAvailableCrafts(provider, pattern, 1));
        accept(1);
        assertEquals(7, available());
        tick++;
        assertEquals(15, available(), "A partial round continues without becoming capacity");
    }

    @Test void aTickBudgetEndingDuringRecoveryRetainsTheProbeTarget() {
        barrier(13);
        tick = 20;
        accept(15);
        assertEquals(2, available());
        tick++;
        assertEquals(17, available());
        accept(17);
        assertEquals(4, available());
    }

    @Test void noInputWindowDoesNotStartOrBlockAProbe() {
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, 0));
        assertEquals(1, available());
        accept(7);
        assertEquals(0, controller.getAvailableCrafts(provider, pattern, -1));
        assertEquals(8, available());
    }

    @Test void queuedOwnershipHaltsThisTickAndRetainsItsActualRate() {
        accept(7);
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_QUEUED);
        assertEquals(0, available());
        tick++;
        assertEquals(8, available());
        assertFalse(controller.isSingleOnly(provider, pattern));
    }

    @Test void unverifiedOwnershipAndAccountingMismatchForceOneCallPerTick() {
        controller.onAccepted(provider, pattern, 1, 1, PushResult.ACCEPTED_UNVERIFIED);
        assertEquals(0, available());
        tick++;
        assertEquals(1, available());
        accept(1);
        assertEquals(0, available());
        assertTrue(controller.isSingleOnly(provider, pattern));
        controller.setActiveJob(new Object());
        accept(7);
        controller.onStatusVerification(provider, pattern, false);
        assertEquals(0, available());
        tick++;
        assertEquals(1, available());
    }

    @Test void forceSingleRemainsUntilTheJobIdentityChanges() {
        var job = new Object();
        controller.setActiveJob(job);
        accept(7);
        controller.forceSingle(provider, pattern);
        tick++;
        assertEquals(1, available());
        accept(1);
        assertEquals(0, available());
        controller.setActiveJob(job);
        tick++;
        assertEquals(1, available());
        controller.setActiveJob(new Object());
        assertEquals(1, available());
        assertFalse(controller.isSingleOnly(provider, pattern));
        accept(1);
        assertEquals(2, available());
    }

    @Test void providerAndPatternIdentityKeepIndependentCapacity() {
        barrier(13);
        var otherProvider = provider(() -> false, false);
        var otherPattern = pattern();
        assertEquals(1, controller.getAvailableCrafts(otherProvider, pattern, 100));
        assertEquals(1, controller.getAvailableCrafts(provider, otherPattern, 100));
        controller.onAccepted(otherProvider, pattern, 1, 1, PushResult.ACCEPTED_FULL);
        assertEquals(2, controller.getAvailableCrafts(otherProvider, pattern, 100));
        assertEquals(0, available());
    }

    @Test void attemptOrderMovesOnAcceptedRejectedAndBusyFeedback() {
        assertEquals(Long.MIN_VALUE, controller.getLastAttemptOrder(provider, pattern));
        accept(1);
        long first = controller.getLastAttemptOrder(provider, pattern);
        controller.onRejected(provider, pattern, 1);
        long rejected = controller.getLastAttemptOrder(provider, pattern);
        controller.onBusy(provider, pattern);
        assertTrue(rejected > first);
        assertTrue(controller.getLastAttemptOrder(provider, pattern) > rejected);
    }

    @Test void growthAndCooldownSaturateAtLongMaxWithoutWrapping() {
        for (int round = 0; round < 63; round++) {
            long available = available();
            assertTrue(available > 0);
            controller.onAccepted(provider, pattern, available, available, PushResult.ACCEPTED_FULL);
        }
        assertEquals(0, available());
        tick = Long.MAX_VALUE - 10;
        assertEquals(Long.MAX_VALUE, available());
        accept(1);
        controller.onRejected(provider, pattern, 1);
        tick = Long.MAX_VALUE - 1;
        assertEquals(1, available());
        tick = Long.MAX_VALUE;
        assertEquals(2, available());
    }

    @Test void reusableRejectionsStillBackOffAndDuplicateFeedbackDoesNotShrinkAgain() {
        assertEquals(Long.MAX_VALUE, controller.getReusableWindowCap(provider, pattern));
        controller.onReusableRejected(provider, pattern, 64);
        controller.onReusableRejected(provider, pattern, 56);
        assertEquals(0, controller.getReusableWindowCap(provider, pattern));
        tick++;
        assertEquals(56, controller.getReusableWindowCap(provider, pattern));
        controller.onReusableRejected(provider, pattern, 56);
        tick++;
        assertEquals(42, controller.getReusableWindowCap(provider, pattern));
        controller.onReusableRejected(provider, pattern, 42);
        tick++;
        assertEquals(21, controller.getReusableWindowCap(provider, pattern));
        controller.onReusableAccepted(provider, pattern);
        controller.onReusableRejected(provider, pattern, 21);
        tick++;
        assertEquals(18, controller.getReusableWindowCap(provider, pattern));
    }

    @Test void busyDoesNotReduceTheReusableWindow() {
        var busy = provider(() -> true, false);
        controller.onReusableProviderRejected(busy, pattern, 1000);
        assertEquals(0, controller.getReusableWindowCap(busy, pattern));
        tick++;
        assertEquals(Long.MAX_VALUE, controller.getReusableWindowCap(busy, pattern));
    }

    @Test void unavailableBusySignalStillEndsTheFailedTick() {
        for (boolean linkage : new boolean[] {false, true}) {
            var broken = provider(() -> {
                if (linkage) throw new NoClassDefFoundError("busy dependency");
                throw new IllegalStateException("busy unavailable");
            }, false);
            assertDoesNotThrow(() -> controller.onProviderRejected(broken, pattern, 1));
            assertEquals(0, controller.getAvailableCrafts(broken, pattern, 100));
            tick++;
            assertEquals(1, controller.getAvailableCrafts(broken, pattern, 100));
            assertDoesNotThrow(() -> controller.onReusableProviderRejected(broken, pattern, 64));
            tick++;
            assertEquals(56, controller.getReusableWindowCap(broken, pattern));
        }
    }

    @Test void invalidFeedbackCannotCreateAcceptedOrBlockedState() {
        assertEquals(0, controller.getAvailableCrafts(null, pattern, 10));
        assertEquals(0, controller.getAvailableCrafts(provider, null, 10));
        assertDoesNotThrow(() -> controller.forceSingle(null, pattern));
        controller.onAccepted(provider, pattern, 0, 1, PushResult.ACCEPTED_FULL);
        controller.onAccepted(provider, pattern, 1, 0, PushResult.ACCEPTED_FULL);
        controller.onAccepted(provider, pattern, 1, 1, null);
        controller.onRejected(provider, pattern, 0);
        controller.onBusy(null, pattern);
        controller.onStatusVerification(null, pattern, false);
        assertEquals(1, available());
    }

    private long available() { return controller.getAvailableCrafts(provider, pattern, Long.MAX_VALUE); }

    private void accept(long calls) {
        while (calls > 0) {
            long step = Math.min(calls, available());
            assertTrue(step > 0, "All requested successful calls must be permitted");
            controller.onAccepted(provider, pattern, step, step, PushResult.ACCEPTED_FULL);
            calls -= step;
        }
    }

    private void barrier(long accepted) {
        accept(accepted);
        controller.onRejected(provider, pattern, 1);
    }

    private static IPatternDetails pattern() {
        return (IPatternDetails) Proxy.newProxyInstance(MolecularAdaptiveBatchControllerTest.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method.getName());
                });
    }

    private static ICraftingProvider provider(java.util.function.BooleanSupplier busy, boolean scaledInterface) {
        return (ICraftingProvider) Proxy.newProxyInstance(MolecularAdaptiveBatchControllerTest.class.getClassLoader(),
                scaledInterface ? new Class<?>[]{ICraftingProvider.class, MolecularScaledBatchProvider.class}
                        : new Class<?>[]{ICraftingProvider.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isBusy" -> busy.getAsBoolean();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError("Ordinary probing must not query " + method.getName());
                });
    }
}
