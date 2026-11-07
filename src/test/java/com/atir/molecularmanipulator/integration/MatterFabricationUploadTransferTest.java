package com.atir.molecularmanipulator.integration;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.inv.AppEngInternalInventory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Uses vanilla item identities so no AE2 deferred registrations or game server are required. */
class MatterFabricationUploadTransferTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final AEItemKey BLANK = AEItemKey.of(Items.PAPER);
    private final AppEngInternalInventory local = new AppEngInternalInventory(null, 1);
    private final Stock network = new Stock();
    private final List<ItemStack> recovered = new ArrayList<>();
    private final MatterFabricationUploadTransfer transfer = new MatterFabricationUploadTransfer(
            local, network, network.source, Items.PAPER, stack -> recovered.add(stack.copy()));

    @Test void oneNetworkBlankIsEnoughWithAnEmptyTerminalSlot() {
        network.put(BLANK, 1);
        var destination = new Destination();
        assertTrue(transfer.hasBlank());
        assertEquals(1, network.amount(BLANK), "Availability checks must not spend a blank");
        assertTrue(transfer.insert(destination, 0, pattern()));
        assertEquals(0, network.amount(BLANK));
        assertEquals(1, destination.getStackInSlot(0).getCount());
        assertEquals(1, network.extractions);
        assertEquals(0, network.enumerations, "Plain blanks use a live exact-key probe");
        assertTrue(local.isEmpty());
        assertTrue(recovered.isEmpty());
    }

    @Test void noBlankCannotCreateAPattern() {
        var destination = new Destination();
        assertFalse(transfer.hasBlank());
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertTrue(destination.isEmpty());
        assertEquals(0, network.extractions);
        assertTrue(recovered.isEmpty());
    }

    @Test void localBlankKeepsExistingPriorityAndConsumesExactlyOne() {
        local.setItemDirect(0, namedBlank(5));
        network.put(BLANK, 10);
        assertTrue(transfer.hasBlank());
        assertTrue(transfer.insert(new Destination(), 0, pattern()));
        assertTrue(ItemStack.matches(namedBlank(4), local.getStackInSlot(0)));
        assertEquals(10, network.amount(BLANK));
        assertEquals(0, network.probes);
        assertEquals(0, network.extractions);
    }

    @Test void namedNetworkBlanksUseItemIdentityAndRetainTheirExactKey() {
        var named = AEItemKey.of(namedBlank(1));
        network.put(named, 1);
        assertTrue(transfer.hasBlank());
        assertTrue(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(0, network.amount(named));
        assertEquals(0, network.amount(BLANK));
        assertEquals(named, network.lastExtracted);
        assertEquals(1, network.enumerations, "Availability and commit must share the one-click selected key");
    }

    @Test void anotherItemNamedLikeABlankIsNotABlank() {
        var impostor = new ItemStack(Items.STONE);
        impostor.set(DataComponents.CUSTOM_NAME, Component.literal("空白样板"));
        var key = AEItemKey.of(impostor);
        network.put(key, 7);
        assertFalse(transfer.hasBlank());
        assertFalse(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(7, network.amount(key));
        assertEquals(0, network.extractions);
    }

    @Test void invalidOrOccupiedDestinationsDoNotConsumeAnyBlank() {
        network.put(BLANK, 4);
        var destination = new Destination();
        destination.setItemDirect(0, new ItemStack(Items.DIAMOND));
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertFalse(transfer.insert(destination, -1, pattern()));
        assertFalse(transfer.insert(destination, 1, pattern()));
        assertFalse(transfer.insert(destination, 0, ItemStack.EMPTY));
        assertFalse(transfer.insert(destination, 0, pattern().copyWithCount(2)));
        assertTrue(destination.getStackInSlot(0).is(Items.DIAMOND));
        assertEquals(4, network.amount(BLANK));
        assertEquals(0, network.extractions);
    }

    @Test void aRejectedSimulationDoesNotConsumeAnyBlank() {
        network.put(BLANK, 1);
        var destination = new Destination();
        destination.acceptSimulation = false;
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertTrue(destination.isEmpty());
        assertEquals(1, network.amount(BLANK));
        assertEquals(0, network.extractions);
    }

    @Test void staleAvailabilityCannotBypassLiveExtractionOrPermissions() {
        var named = AEItemKey.of(namedBlank(1));
        network.listed.put(named, 100L);
        network.put(BLANK, 1);
        network.denyExtraction = true;
        assertFalse(transfer.hasBlank());
        assertFalse(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(1, network.amount(BLANK));
        assertEquals(0, network.extractions);
    }

    @Test void aStaleEmptyListingDoesNotHideAnExtractablePlainBlank() {
        network.amounts.put(BLANK, 1L);
        assertTrue(transfer.hasBlank());
        assertTrue(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(0, network.amount(BLANK));
        assertEquals(0, network.enumerations);
    }

    @Test void extractionMayFailAfterSuccessfulSimulationWithoutCreatingAPattern() {
        network.put(BLANK, 1);
        network.denyCommit = true;
        var destination = new Destination();
        assertTrue(transfer.hasBlank());
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertTrue(destination.isEmpty());
        assertEquals(1, network.amount(BLANK));
        assertTrue(local.isEmpty());
        assertTrue(recovered.isEmpty());
    }

    @Test void rejectedCommitReturnsTheExactNetworkBlank() {
        var named = AEItemKey.of(namedBlank(1));
        network.put(named, 1);
        var destination = new Destination();
        destination.acceptCommit = false;
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertTrue(destination.isEmpty());
        assertEquals(1, network.amount(named));
        assertEquals(0, network.amount(BLANK));
        assertEquals(1, network.refunds);
        assertTrue(local.isEmpty());
        assertTrue(recovered.isEmpty());
    }

    @Test void unavailableNetworkRefundReturnsTheBlankToTheTerminal() {
        var named = AEItemKey.of(namedBlank(1));
        network.put(named, 1);
        network.rejectRefund = true;
        var destination = new Destination();
        destination.acceptCommit = false;
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertEquals(0, network.amount(named));
        assertTrue(ItemStack.matches(namedBlank(1), local.getStackInSlot(0)));
        assertTrue(destination.isEmpty());
        assertTrue(recovered.isEmpty());
    }

    @Test void fullRefundPathsRetainTheBlankForPlayerRecoveryInsteadOfDiscardingIt() {
        var named = AEItemKey.of(namedBlank(1));
        network.put(named, 1);
        network.rejectRefund = true;
        local.setItemDirect(0, new ItemStack(Items.STONE, 64));
        var destination = new Destination();
        destination.acceptCommit = false;
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertEquals(0, network.amount(named));
        assertEquals(64, local.getStackInSlot(0).getCount());
        assertTrue(local.getStackInSlot(0).is(Items.STONE));
        assertEquals(1, recovered.size());
        assertTrue(ItemStack.matches(namedBlank(1), recovered.getFirst()));
        assertTrue(destination.isEmpty());
    }

    @Test void failedLocalCommitRestoresTheEntireOriginalStack() {
        local.setItemDirect(0, namedBlank(5));
        var destination = new Destination();
        destination.acceptCommit = false;
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertTrue(ItemStack.matches(namedBlank(5), local.getStackInSlot(0)));
        assertTrue(destination.isEmpty());
        assertEquals(0, network.extractions);
    }

    @Test void aDestinationExceptionAfterWritingCannotDuplicateTheBlankAndPattern() {
        network.put(BLANK, 1);
        var destination = new Destination();
        destination.throwAfterWrite = true;
        assertThrows(IllegalStateException.class, () -> transfer.insert(destination, 0, pattern()));
        assertTrue(destination.isEmpty());
        assertEquals(1, network.amount(BLANK));
        assertTrue(local.isEmpty());
        assertTrue(recovered.isEmpty());
    }

    @Test void localOnlyCallersStillWorkWithoutANetwork() {
        local.setItemDirect(0, namedBlank(1));
        var localOnly = new MatterFabricationUploadTransfer(local, null, network.source, Items.PAPER,
                stack -> fail("Local-only transfer must not need recovery"));
        assertTrue(localOnly.hasBlank());
        assertTrue(localOnly.insert(new Destination(), 0, pattern()));
        assertTrue(local.isEmpty());
        assertFalse(localOnly.hasBlank());
    }

    private static ItemStack pattern() { return new ItemStack(Items.MAP); }

    private static ItemStack namedBlank(int count) {
        var blank = new ItemStack(Items.PAPER, count);
        blank.set(DataComponents.CUSTOM_NAME, Component.literal("保留原名的空白样板"));
        return blank;
    }

    private static final class Destination extends AppEngInternalInventory {
        boolean acceptSimulation = true, acceptCommit = true, throwAfterWrite;
        Destination() { super(null, 1); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (simulate ? !acceptSimulation : !acceptCommit) return stack;
            var remainder = super.insertItem(slot, stack, simulate);
            if (!simulate && throwAfterWrite) throw new IllegalStateException("Destination callback failed");
            return remainder;
        }
    }

    private static final class Stock implements MEStorage {
        final IActionSource source = IActionSource.empty();
        final Map<AEKey, Long> amounts = new LinkedHashMap<>(), listed = new LinkedHashMap<>();
        boolean denyExtraction, denyCommit, rejectRefund;
        int probes, extractions, refunds, enumerations;
        AEKey lastExtracted;
        void put(AEKey key, long amount) { amounts.put(key, amount); listed.put(key, amount); }
        long amount(AEKey key) { return amounts.getOrDefault(key, 0L); }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            assertSame(this.source, source, "Every probe and extraction must retain the menu's action source");
            assertEquals(1, amount);
            probes++;
            if (denyExtraction || mode == Actionable.MODULATE && denyCommit) return 0;
            long accepted = Math.min(amount, amount(key));
            if (mode == Actionable.MODULATE) {
                extractions++;
                amounts.put(key, amount(key) - accepted);
                lastExtracted = key;
            }
            return accepted;
        }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            assertSame(this.source, source);
            assertEquals(Actionable.MODULATE, mode);
            assertEquals(1, amount);
            refunds++;
            if (rejectRefund) return 0;
            amounts.merge(key, amount, Long::sum);
            return amount;
        }
        @Override public void getAvailableStacks(KeyCounter counter) {
            enumerations++;
            listed.forEach(counter::add);
        }
        @Override public Component getDescription() { return Component.literal("Fabrication upload stock"); }
    }
}
