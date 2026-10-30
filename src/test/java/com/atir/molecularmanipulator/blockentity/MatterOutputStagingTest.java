package com.atir.molecularmanipulator.blockentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MatterOutputStagingTest {
    static {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final AEKey OUTPUT = AEItemKey.of(Items.DIAMOND);

    @Test void instantProductionCannotReachStorageBeforeCpuAccounting() {
        var staged = new MatterOutputStaging();
        var ready = new Object2LongLinkedOpenHashMap<AEKey>();
        var transfer = new AEKeyTransferScheduler();
        assertEquals(64, staged.store(ready, OUTPUT, 64, 10));
        for (int i = 0; i < 4; i++) {
            staged.release(ready, 10); // Controller/assembly/accelerator may tick in either order.
            transfer.flush(ready, AEKeyTransferScheduler.defaultBudget(), (key, amount) -> {
                fail("Same-tick output would bypass a CPU still registering its expected outputs");
                return amount;
            });
        }
        staged.release(ready, 11);
        long[] received = {0};
        transfer.flush(ready, AEKeyTransferScheduler.defaultBudget(), (key, amount) -> {
            received[0] += amount;
            return amount;
        });
        assertEquals(64, received[0]);
        assertTrue(ready.isEmpty());
        assertTrue(staged.isEmpty());
    }

    @Test void continuousOneTickBatchesDoNotPostponeEarlierOutputsOrReleaseFreshOnes() {
        var staged = new MatterOutputStaging();
        var ready = new Object2LongLinkedOpenHashMap<AEKey>();
        staged.store(ready, OUTPUT, 10, 20);
        staged.store(ready, OUTPUT, 20, 21); // Releases the previous batch before staging this one.
        staged.release(ready, 21);
        assertEquals(10, ready.getLong(OUTPUT));
        assertEquals(30, staged.storedAmount(ready, OUTPUT));
        staged.store(ready, OUTPUT, 30, 22);
        staged.release(ready, 22);
        assertEquals(30, ready.getLong(OUTPUT));
        assertEquals(60, staged.storedAmount(ready, OUTPUT));
        staged.release(ready, 23);
        assertEquals(60, ready.getLong(OUTPUT));
    }

    @Test void blockedAndPartialReturnsKeepOwnershipAndDoNotExposeFreshOutput() {
        var staged = new MatterOutputStaging();
        var ready = new Object2LongLinkedOpenHashMap<AEKey>();
        var transfer = new AEKeyTransferScheduler();
        staged.store(ready, OUTPUT, 100, 30);
        staged.release(ready, 31);
        transfer.flush(ready, AEKeyTransferScheduler.defaultBudget(), (key, amount) -> 0);
        assertEquals(100, staged.storedAmount(ready, OUTPUT));
        staged.store(ready, OUTPUT, 50, 31);
        transfer.flush(ready, AEKeyTransferScheduler.defaultBudget(), (key, amount) -> 40);
        assertEquals(110, staged.storedAmount(ready, OUTPUT));
        assertEquals(60, ready.getLong(OUTPUT));
        staged.release(ready, 32);
        assertEquals(110, ready.getLong(OUTPUT));
    }

    @Test void capacityIncludesBothReadyAndStagedOutputs() {
        var staged = new MatterOutputStaging();
        var ready = new Object2LongLinkedOpenHashMap<AEKey>();
        ready.put(OUTPUT, Long.MAX_VALUE - 10);
        assertEquals(7, staged.store(ready, OUTPUT, 7, 40));
        assertEquals(3, staged.store(ready, OUTPUT, 7, 40));
        assertEquals(0, staged.store(ready, OUTPUT, 1, 40));
        assertEquals(Long.MAX_VALUE, staged.storedAmount(ready, OUTPUT));
        staged.release(ready, 41);
        assertEquals(Long.MAX_VALUE, ready.getLong(OUTPUT));
    }

    @Test void restoredOutputWaitsForCpuLoadingAndIsNotDuplicated() {
        var staged = new MatterOutputStaging();
        var ready = new Object2LongLinkedOpenHashMap<AEKey>();
        staged.restore(Map.of(OUTPUT, 90L));
        staged.release(ready, 50);
        staged.release(ready, 50);
        assertTrue(ready.isEmpty());
        var saved = new HashMap<AEKey, Long>();
        staged.addTo(saved);
        assertEquals(Map.of(OUTPUT, 90L), saved);
        staged.release(ready, 51);
        staged.release(ready, 52);
        assertEquals(90, ready.getLong(OUTPUT));
        assertTrue(staged.isEmpty());
    }
}
