package com.atir.molecularmanipulator.integration;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.ForgeTestBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Vanilla carriers avoid AE2 deferred item registration; the real transfer and inventories are exercised. */
class MatterFabricationUploadTransferTest {
    static {
        try { ForgeTestBootstrap.bootstrapMinecraft(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
        ForgeTestBootstrap.bootstrapAeKeyTypes();
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
        assertEquals(1, network.amount(BLANK));
        assertTrue(transfer.insert(destination, 0, pattern()));
        assertEquals(0, network.amount(BLANK));
        assertTrue(ItemStack.matches(pattern(), destination.getStackInSlot(0)));
        assertEquals(1, network.extractions);
        assertEquals(0, network.enumerations);
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

    @Test void localBlankHasPriorityAndConsumesExactlyOneWithoutDroppingNbt() {
        local.setItemDirect(0, namedBlank(5));
        network.put(BLANK, 10);
        assertTrue(transfer.hasBlank());
        assertTrue(transfer.insert(new Destination(), 0, pattern()));
        assertTrue(ItemStack.matches(namedBlank(4), local.getStackInSlot(0)));
        assertEquals(10, network.amount(BLANK));
        assertEquals(0, network.probes);
        assertEquals(0, network.extractions);
    }

    @Test void namedNetworkBlanksRetainTheirExactKeyAndEnumerateOnlyOnce() {
        var named = AEItemKey.of(namedBlank(1));
        network.put(named, 1);
        assertTrue(transfer.hasBlank());
        assertTrue(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(0, network.amount(named));
        assertEquals(0, network.amount(BLANK));
        assertEquals(named, network.lastExtracted);
        assertEquals(1, network.enumerations);
    }

    @Test void anotherItemNamedLikeABlankIsNotABlank() {
        var impostor = new ItemStack(Items.STONE);
        impostor.setHoverName(Component.literal("空白样板"));
        var key = AEItemKey.of(impostor);
        network.put(key, 7);
        assertFalse(transfer.hasBlank());
        assertFalse(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(7, network.amount(key));
        assertEquals(0, network.extractions);
    }

    @Test void invalidOccupiedAndSimulatedRejectionsNeverConsumeABlank() {
        network.put(BLANK, 4);
        var destination = new Destination();
        destination.setItemDirect(0, new ItemStack(Items.DIAMOND));
        assertFalse(transfer.insert(destination, 0, pattern()));
        assertFalse(transfer.insert(destination, -1, pattern()));
        assertFalse(transfer.insert(destination, 1, pattern()));
        assertFalse(transfer.insert(destination, 0, ItemStack.EMPTY));
        assertFalse(transfer.insert(destination, 0, pattern().copyWithCount(2)));
        assertTrue(destination.getStackInSlot(0).is(Items.DIAMOND));
        var rejected = new Destination();
        rejected.acceptSimulation = false;
        assertFalse(transfer.insert(rejected, 0, pattern()));
        assertTrue(rejected.isEmpty());
        assertEquals(4, network.amount(BLANK));
        assertEquals(0, network.extractions);
    }

    @Test void staleListingsCannotBypassLiveExtractionOrPermissions() {
        var named = AEItemKey.of(namedBlank(1));
        network.listed.put(named, 100L);
        network.put(BLANK, 1);
        network.denyExtraction = true;
        assertFalse(transfer.hasBlank());
        assertFalse(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(1, network.amount(BLANK));
        assertEquals(0, network.extractions);
    }

    @Test void staleEmptyListingDoesNotHideAnExtractablePlainBlank() {
        network.amounts.put(BLANK, 1L);
        assertTrue(transfer.hasBlank());
        assertTrue(transfer.insert(new Destination(), 0, pattern()));
        assertEquals(0, network.amount(BLANK));
        assertEquals(0, network.enumerations);
    }

    @Test void failedExtractionAfterSuccessfulSimulationCannotCreateAPattern() {
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

    @Test void unavailableNetworkRefundReturnsExactNbtToTerminalOrPlayer() {
        for (boolean fullTerminal : List.of(false, true)) {
            var local = new AppEngInternalInventory(1);
            if (fullTerminal) local.setItemDirect(0, new ItemStack(Items.STONE, 64));
            var named = AEItemKey.of(namedBlank(1));
            network.put(named, 1);
            network.rejectRefund = true;
            var transfer = new MatterFabricationUploadTransfer(local, network, network.source,
                    Items.PAPER, stack -> recovered.add(stack.copy()));
            var destination = new Destination();
            destination.acceptCommit = false;
            assertFalse(transfer.insert(destination, 0, pattern()));
            assertEquals(0, network.amount(named));
            assertTrue(destination.isEmpty());
            if (fullTerminal) {
                assertEquals(64, local.getStackInSlot(0).getCount());
                assertTrue(local.getStackInSlot(0).is(Items.STONE));
                assertEquals(1, recovered.size());
                assertTrue(ItemStack.matches(namedBlank(1), recovered.get(0)));
            } else {
                assertTrue(ItemStack.matches(namedBlank(1), local.getStackInSlot(0)));
                assertTrue(recovered.isEmpty());
            }
        }
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

    @Test void destinationExceptionAfterWritingCannotDuplicateBlankAndPattern() {
        network.put(BLANK, 1);
        var destination = new Destination();
        destination.throwAfterWrite = true;
        assertThrows(IllegalStateException.class, () -> transfer.insert(destination, 0, pattern()));
        assertTrue(destination.isEmpty());
        assertEquals(1, network.amount(BLANK));
        assertTrue(local.isEmpty());
        assertTrue(recovered.isEmpty());
    }

    @Test void localCallbackExceptionAfterRemovalRestoresAllNbtWithoutCreatingAPattern() {
        var throwing = new AppEngInternalInventory(1) {
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
                var result = super.extractItem(slot, amount, simulate);
                if (!simulate) throw new IllegalStateException("After removing the blank");
                return result;
            }
        };
        throwing.setItemDirect(0, namedBlank(5));
        var transfer = new MatterFabricationUploadTransfer(throwing, null, network.source,
                Items.PAPER, stack -> fail("Local blanks need no network recovery"));
        var destination = new Destination();
        assertThrows(IllegalStateException.class, () -> transfer.insert(destination, 0, pattern()));
        assertTrue(ItemStack.matches(namedBlank(5), throwing.getStackInSlot(0)));
        assertTrue(destination.isEmpty());
    }

    @Test void localOnlyCallersWorkWithoutANetwork() {
        local.setItemDirect(0, namedBlank(1));
        var localOnly = new MatterFabricationUploadTransfer(local, null, network.source, Items.PAPER,
                stack -> fail("Local-only transfer must not need recovery"));
        assertTrue(localOnly.hasBlank());
        assertTrue(localOnly.insert(new Destination(), 0, pattern()));
        assertTrue(local.isEmpty());
        assertFalse(localOnly.hasBlank());
    }

    private static ItemStack pattern() {
        var stack = new ItemStack(Items.MAP);
        stack.getOrCreateTag().putLong("recipe_output_amount", 4_294_967_297L);
        return stack;
    }

    private static ItemStack namedBlank(int count) {
        var blank = new ItemStack(Items.PAPER, count);
        blank.setHoverName(Component.literal("保留原名的空白样板"));
        blank.getOrCreateTag().putLong("addon_payload", 4_294_967_296L);
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
            assertSame(this.source, source);
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
