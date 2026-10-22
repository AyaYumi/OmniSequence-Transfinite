package com.atir.molecularmanipulator.integration.eco;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.*;
import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.crafting.inv.ListCraftingInventory;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import com.atir.molecularmanipulator.crafting.OmniExactInventory;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.Test;

class EcoCpuDispatchLedgerTest {
    static {
        EcoMolecularTestPattern.bootstrap();
        try { Class.forName(EcoCpuDispatchTest.class.getName()); }
        catch (ClassNotFoundException failure) { throw new AssertionError(failure); }
    }
    private static final BigInteger HUGE = BigInteger.ONE.shiftLeft(100);
    private static EcoMolecularTestPattern pattern() { return new EcoMolecularTestPattern(CONTAINER, key -> RETURNED); }
    private static EcoCpuDispatchReceipt receipt(BigInteger count) {
        return new EcoCpuDispatchReceipt(UUID.randomUUID(), pattern(), count,
                Map.of(MATERIAL, count), Map.of(OUTPUT, count), Map.of(RETURNED, count), false);
    }
    private static ListCraftingInventory window() { return new ListCraftingInventory(key -> fail("Ownership mutation notified too early")); }

    @Test void ordinaryStagingIncludesStatefulReturnsAndRollbackPreservesEarlierWaiting() {
        var ledger = new EcoCpuDispatchLedger(); var waiting = window();
        waiting.list.set(OUTPUT, 9); waiting.list.set(RETURNED, 4);
        var stage = ledger.stage(waiting, null, receipt(HUGE), () -> fail("Rejected batch committed"));
        assertEquals(HUGE.add(BigInteger.valueOf(9)), ledger.available(waiting, null, OUTPUT));
        assertEquals(HUGE.add(BigInteger.valueOf(4)), ledger.available(waiting, null, RETURNED));
        assertEquals(Long.MAX_VALUE, waiting.list.get(OUTPUT));
        stage.rollback(); stage.rollback(); stage.commit();
        assertEquals(9, waiting.list.get(OUTPUT)); assertEquals(4, waiting.list.get(RETURNED));
        assertFalse(ledger.staging()); assertFalse(ledger.paused());
    }
    @Test void exactStagingAndRollbackPreserveExistingUnexposedCredits() {
        var ledger = new EcoCpuDispatchLedger(); var waiting = window();
        var state = OmniExactCraftingState.create(Map.of(pattern(), HUGE), Map.of());
        state.queueOutput(OUTPUT, HUGE); ledger.refill(waiting, state, OUTPUT);
        var stage = ledger.stage(waiting, state, receipt(HUGE), () -> {});
        assertEquals(HUGE.multiply(BigInteger.TWO), ledger.available(waiting, state, OUTPUT));
        stage.rollback();
        assertEquals(HUGE, ledger.available(waiting, state, OUTPUT));
        assertEquals(BigInteger.ZERO, ledger.available(waiting, state, RETURNED));
    }
    @Test void synchronousReturnUsesStagedCreditAndCommitRunsOnce() {
        var ledger = new EcoCpuDispatchLedger(); var waiting = window();
        var state = OmniExactCraftingState.create(Map.of(pattern(), HUGE), Map.of());
        var commits = new AtomicInteger();
        var stage = ledger.stage(waiting, state, receipt(HUGE), commits::incrementAndGet);
        ledger.consume(waiting, state, OUTPUT, HUGE);
        assertEquals(BigInteger.ZERO, ledger.available(waiting, state, OUTPUT));
        assertEquals(HUGE, state.completedOutputs().get(OUTPUT));
        stage.commit(); stage.commit(); stage.rollback();
        assertEquals(1, commits.get()); assertFalse(ledger.paused());
    }
    @Test void completeUuidReceptionRemovesZeroRowsForWaitingEmptyCompletionGuards() {
        var ledger = new EcoCpuDispatchLedger(); var waiting = window();
        var stage = ledger.stage(waiting, null, receipt(BigInteger.valueOf(4)), () -> {});
        stage.commit();
        ledger.consume(waiting, null, OUTPUT, BigInteger.valueOf(4));
        assertEquals(0, waiting.list.get(OUTPUT));
        ledger.consume(waiting, null, RETURNED, BigInteger.valueOf(4));
        assertTrue(waiting.list.isEmpty(), "A zero-valued row must not strand a physically complete child");
    }

    @Test void retainSealsStagingWithoutAdvancingOrRefundingIt() {
        var ledger = new EcoCpuDispatchLedger(); var waiting = window();
        var commits = new AtomicInteger();
        var stage = ledger.stage(waiting, null, receipt(HUGE), commits::incrementAndGet);
        stage.retain(); stage.retain(); stage.rollback(); stage.commit();
        assertEquals(0, commits.get()); assertTrue(ledger.paused());
        assertEquals(HUGE, ledger.available(waiting, null, OUTPUT));
    }
    @Test void commitFailureCannotBeReplayedAndRetainKeepsWaiting() {
        var ledger = new EcoCpuDispatchLedger(); var waiting = window(); var calls = new AtomicInteger();
        var stage = ledger.stage(waiting, null, receipt(HUGE), () -> { calls.incrementAndGet(); throw new IllegalStateException(); });
        assertThrows(IllegalStateException.class, stage::commit);
        stage.retain(); stage.commit();
        assertEquals(1, calls.get()); assertEquals(HUGE, ledger.available(waiting, null, OUTPUT));
    }
    @Test void reservationPreflightsAllKeysAndRefundIsIdempotentAcrossOverflow() {
        var stock = new OmniExactInventory(); var inventory = window();
        stock.insertWithoutNotification(inventory, MATERIAL, HUGE);
        assertNull(EcoCpuDispatchLedger.reserve(stock, inventory, Map.of(MATERIAL, HUGE, RETURNED, BigInteger.ONE)));
        assertEquals(HUGE, stock.amount(inventory, MATERIAL));
        var lease = EcoCpuDispatchLedger.reserve(stock, inventory, Map.of(MATERIAL, HUGE));
        assertNotNull(lease); assertEquals(BigInteger.ZERO, stock.amount(inventory, MATERIAL));
        lease.refund(); lease.refund();
        assertEquals(HUGE, stock.amount(inventory, MATERIAL));
        lease = EcoCpuDispatchLedger.reserve(stock, inventory, Map.of(MATERIAL, HUGE));
        lease.commit(); lease.refund();
        assertEquals(BigInteger.ZERO, stock.amount(inventory, MATERIAL));
    }
    @Test void physicalRefillExposesOverflowWithoutCallingObserversInsideOwnershipMutation() {
        var stock = new OmniExactInventory(); var inventory = window();
        stock.insertWithoutNotification(inventory, RETURNED, HUGE);
        inventory.list.set(RETURNED, 1);
        var before = stock.amount(inventory, RETURNED);
        assertTrue(EcoCpuDispatchLedger.refillPhysical(stock, inventory));
        assertEquals(Long.MAX_VALUE, inventory.list.get(RETURNED));
        assertEquals(before, stock.amount(inventory, RETURNED));
    }
    @Test void finalCapacityHonorsDestinationAndCannotReuseAnUndrainedWindow() {
        var ledger = new EcoCpuDispatchLedger();
        var capacity = ledger.finalCapacity(OUTPUT, HUGE, HUGE, (key, amount, mode) -> {
            assertEquals(Actionable.SIMULATE, mode); assertEquals(Long.MAX_VALUE, amount); return 7;
        });
        assertEquals(BigInteger.valueOf(7), capacity);
        assertFalse(ledger.hasFinalStock(), "Simulation never takes ownership");
        ledger.ownFinal(OUTPUT, capacity);
        assertEquals(BigInteger.ZERO, ledger.finalCapacity(OUTPUT, HUGE, HUGE, (key, amount, mode) -> fail("Capacity already reserved")));
        assertEquals(BigInteger.ZERO, new EcoCpuDispatchLedger().finalCapacity(OUTPUT, HUGE, HUGE, (key, amount, mode) -> 0));
    }
    @Test void finalPartialAcceptanceKeepsOnlyTheOwnedTailAndAdvancesOnlyDelivery() {
        var ledger = new EcoCpuDispatchLedger(); ledger.ownFinal(OUTPUT, BigInteger.TEN);
        var delivered = new AtomicInteger();
        ledger.flushFinal((key, amount, mode) -> mode == Actionable.SIMULATE ? amount : 3,
                (key, amount) -> delivered.addAndGet(Math.toIntExact(amount)), () -> {});
        assertEquals(3, delivered.get()); assertEquals(Map.of(OUTPUT, BigInteger.valueOf(7)), ledger.finalStock());
        assertFalse(ledger.paused());
    }
    @Test void finalFlushIsBoundedAndSavesIntentBeforeForeignOwnershipCode() {
        var ledger = new EcoCpuDispatchLedger(); ledger.ownFinal(OUTPUT, HUGE);
        var calls = new AtomicInteger();
        ledger.flushFinal((key, amount, mode) -> {
            assertEquals(Long.MAX_VALUE, amount);
            if (mode == Actionable.MODULATE) { calls.incrementAndGet(); assertTrue(ledger.paused()); }
            return amount;
        }, (key, amount) -> {}, () -> {});
        assertEquals(1, calls.get());
        assertEquals(HUGE.subtract(BigInteger.valueOf(Long.MAX_VALUE)), ledger.finalStock().get(OUTPUT));
    }
    @Test void throwingDestinationLeavesAnUnreplayableDurableOwnershipIntent() {
        var ledger = new EcoCpuDispatchLedger(); ledger.ownFinal(OUTPUT, BigInteger.TEN);
        assertThrows(IllegalStateException.class, () -> ledger.flushFinal((key, amount, mode) -> {
            if (mode == Actionable.MODULATE) throw new IllegalStateException("Requester accepted then threw"); return amount;
        }, (key, amount) -> fail(), () -> {}));
        assertTrue(ledger.paused()); assertTrue(ledger.finalStock().isEmpty());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = ledger.write(registries); var restored = new EcoCpuDispatchLedger(); restored.read(saved, registries);
        assertTrue(restored.paused()); assertEquals(saved, restored.write(registries));
        restored.flushFinal((key, amount, mode) -> fail("Uncertain delivery replayed"), (key, amount) -> fail(), () -> {});
        assertThrows(IllegalStateException.class, () -> restored.refundFinal((key, amount) -> fail()));
    }
    @Test void failedFinalProgressRetainsTheKnownAcceptedIntentWithoutReplaying() {
        var ledger = new EcoCpuDispatchLedger(); ledger.ownFinal(OUTPUT, BigInteger.TEN);
        assertThrows(IllegalStateException.class, () -> ledger.flushFinal((key, amount, mode) -> 3,
                (key, amount) -> { throw new IllegalStateException("Progress failed after delivery"); }, () -> {}));
        assertTrue(ledger.paused());
        assertEquals(BigInteger.valueOf(7), ledger.finalStock().get(OUTPUT));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var intent = ledger.write(registries).getList("deliveryIntent", net.minecraft.nbt.Tag.TAG_COMPOUND);
        assertEquals("3", intent.getCompound(0).getString("amount"));
    }
    @Test void ordinaryOverflowExactFlagAndInterruptedStageSurviveReload() {
        var ledger = new EcoCpuDispatchLedger(); var waiting = window();
        ledger.exactOrder(true); ledger.stage(waiting, null, receipt(HUGE), () -> {});
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = ledger.write(registries); var restored = new EcoCpuDispatchLedger(); restored.read(saved, registries);
        assertTrue(restored.exactOrder()); assertTrue(restored.paused());
        assertEquals(HUGE, restored.available(waiting, null, OUTPUT));
    }
    @Test void corruptedHostAccountingRetainsOriginalRecoveryData() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putInt("version", 99); tag.putString("future", "ownership");
        var ledger = new EcoCpuDispatchLedger(); ledger.read(tag, registries);
        assertTrue(ledger.paused()); assertEquals(tag, ledger.write(registries));
    }
}
