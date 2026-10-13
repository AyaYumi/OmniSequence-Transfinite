package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MolecularAutoCraftOutputTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final AEKey FIRST = AEItemKey.of(Items.OAK_PLANKS);
    private static final AEKey SECOND = AEItemKey.of(Items.BUCKET);

    @Test
    void limitsBillionCraftBatchesWithOneProbeAndExactArithmetic() {
        var target = new Target(8_000_000_003L);
        assertEquals(2_000_000_000L, MolecularAutoCraftOutput.capacity(
                amounts(FIRST, 12_000_000_000L), new Object2LongOpenHashMap<>(),
                3_000_000_000L, 3_000_000_000L, target));
        assertEquals(1, target.probes);
        assertEquals(0, target.stored.size());
        assertEquals(2_000_000_000L, MolecularAutoCraftOutput.capacity(
                amounts(FIRST, 4), new Object2LongOpenHashMap<>(),
                1, Integer.MAX_VALUE, target));
        assertEquals(2, target.probes);
    }

    @Test
    void countsPrimaryAndReturnedItemsOfTheSameKeyTogether() {
        var target = new Target(19);
        assertEquals(3, MolecularAutoCraftOutput.capacity(amounts(FIRST, 4),
                amounts(FIRST, 2), 1, 1000, target));
        assertEquals(1, target.probes);
    }

    @Test
    void deliversMillionsOfItemsWithoutRecoveryOrPerItemCalls() {
        var target = new Target(Long.MAX_VALUE);
        var result = MolecularAutoCraftOutput.deliver(amounts(FIRST, 8_000_000_000L),
                new Object2LongOpenHashMap<>(), target);
        assertTrue(result.committed());
        assertTrue(result.recovery().isEmpty());
        assertEquals(8_000_000_000L, target.stored.getLong(FIRST));
        assertEquals(1, target.insertions);
    }

    @Test
    void sharedCapacityCollisionRollsBackEveryOutputBeforeInputRefund() {
        var target = new Target(100);
        var primary = amounts(FIRST, 80);
        var remainders = amounts(SECOND, 80);
        // Independent simulations each see the same remaining space.
        assertEquals(80, MolecularAutoCraftOutput.capacity(primary, remainders, 80, 80, target));
        var result = MolecularAutoCraftOutput.deliver(primary, remainders, target);
        assertFalse(result.committed());
        assertTrue(result.recovery().isEmpty());
        assertEquals(0, target.total());
    }

    @Test
    void refusedRollbackCommitsAndRetainsExactlyTheUndeliveredAmounts() {
        var target = new Target(100);
        target.canExtract = false;
        var primary = amounts(FIRST, 80);
        var remainders = amounts(SECOND, 80);
        var result = MolecularAutoCraftOutput.deliver(primary, remainders, target);
        assertTrue(result.committed());
        assertEquals(100, target.total());
        assertEquals(60, result.recovery().values().longStream().sum());
        assertEquals(80, target.stored.getLong(FIRST) + result.recovery().getLong(FIRST));
        assertEquals(80, target.stored.getLong(SECOND) + result.recovery().getLong(SECOND));
    }

    @Test
    void changedDestinationRejectsWithoutCreatingOutputs() {
        var target = new Target(0);
        var result = MolecularAutoCraftOutput.deliver(amounts(FIRST, 1_000_000),
                new Object2LongOpenHashMap<>(), target);
        assertFalse(result.committed());
        assertTrue(result.recovery().isEmpty());
        assertEquals(0, target.total());
    }

    private static Object2LongOpenHashMap<AEKey> amounts(AEKey key, long count) {
        var result = new Object2LongOpenHashMap<AEKey>();
        result.put(key, count);
        return result;
    }

    private static final class Target implements MolecularAutoCrafterHost.OutputDestination {
        private final Object2LongOpenHashMap<AEKey> stored = new Object2LongOpenHashMap<>();
        private final long capacity;
        private int probes;
        private int insertions;
        private boolean canExtract = true;

        private Target(long capacity) {
            this.capacity = capacity;
        }

        private long total() {
            return stored.values().longStream().sum();
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode) {
            long accepted = Math.min(amount, capacity - total());
            if (mode == Actionable.SIMULATE) probes++;
            else {
                insertions++;
                stored.addTo(key, accepted);
            }
            return accepted;
        }

        @Override
        public long extract(AEKey key, long amount) {
            long recovered = canExtract ? Math.min(amount, stored.getLong(key)) : 0;
            stored.addTo(key, -recovered);
            return recovered;
        }
    }
}
