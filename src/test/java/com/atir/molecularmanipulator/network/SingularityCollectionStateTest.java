package com.atir.molecularmanipulator.network;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** One menu-owned receiver; container-id admission is deliberately the menu's separate responsibility. */
class SingularityCollectionStateTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void publishesOnlyACompleteSnapshotAndKeepsThePreviousListVisibleUntilThen() {
        var state = new SingularityCollectionState();
        assertTrue(state.accept(chunk(1, 0, 1, Items.NETHER_STAR)));
        var previous = state.resources();
        assertTrue(state.accept(chunk(2, 0, 6, Items.STONE, Items.PAPER)));
        assertSame(previous, state.resources());
        assertItems(List.of(Items.NETHER_STAR), state.resources());
        assertTrue(state.accept(chunk(2, 2, 6, Items.DIAMOND, Items.COAL)));
        assertSame(previous, state.resources());
        assertTrue(state.accept(chunk(2, 4, 6, Items.EMERALD, Items.QUARTZ)));
        assertNotSame(previous, state.resources());
        assertItems(List.of(Items.STONE, Items.PAPER, Items.DIAMOND, Items.COAL, Items.EMERALD, Items.QUARTZ),
                state.resources());
        assertItems(List.of(Items.NETHER_STAR), previous);
        assertThrows(UnsupportedOperationException.class, () -> state.resources().add(new ItemStack(Items.PAPER)));
    }

    @Test
    void duplicateFirstMiddleAndFinalChunksCannotAdvanceOrRepublishASnapshot() {
        var state = new SingularityCollectionState();
        var first = chunk(0, 0, 6, Items.STONE, Items.PAPER);
        var middle = chunk(0, 2, 6, Items.DIAMOND, Items.COAL);
        var last = chunk(0, 4, 6, Items.EMERALD, Items.QUARTZ);
        var empty = state.resources();
        assertTrue(state.accept(first));
        assertFalse(state.accept(first));
        assertTrue(state.accept(middle));
        assertFalse(state.accept(middle));
        assertSame(empty, state.resources());
        assertTrue(state.accept(last));
        var completed = state.resources();
        assertFalse(state.accept(first));
        assertFalse(state.accept(middle));
        assertFalse(state.accept(last));
        assertSame(completed, state.resources());
        assertItems(List.of(Items.STONE, Items.PAPER, Items.DIAMOND, Items.COAL, Items.EMERALD, Items.QUARTZ), completed);
    }

    @Test
    void stalePacketsCannotReplaceACompletedSnapshotOrPolluteAnActiveOne() {
        var state = new SingularityCollectionState();
        var old = chunk(10, 0, 2, Items.COAL, Items.QUARTZ);
        assertTrue(state.accept(old));
        var visible = state.resources();
        assertFalse(state.accept(chunk(9, 0, 1, Items.NETHER_STAR)));
        assertFalse(state.accept(chunk(9, 2, 4, Items.NETHER_STAR, Items.GOLD_INGOT)));
        assertSame(visible, state.resources());

        assertTrue(state.accept(chunk(11, 0, 4, Items.PAPER, Items.STONE)));
        assertFalse(state.accept(old));
        assertFalse(state.accept(chunk(10, 2, 4, Items.NETHER_STAR, Items.GOLD_INGOT)));
        assertSame(visible, state.resources());
        assertTrue(state.accept(chunk(11, 2, 4, Items.DIAMOND, Items.EMERALD)));
        assertItems(List.of(Items.PAPER, Items.STONE, Items.DIAMOND, Items.EMERALD), state.resources());
        assertItems(List.of(Items.COAL, Items.QUARTZ), visible);
    }

    @Test
    void futureContinuationCannotStartASnapshotOrAdvanceTheActiveGeneration() {
        var state = new SingularityCollectionState();
        var futureTail = chunk(41, 2, 4, Items.DIAMOND, Items.EMERALD);
        var empty = state.resources();
        assertFalse(state.accept(futureTail), "A nonzero offset cannot start a snapshot");
        assertSame(empty, state.resources());
        assertTrue(state.accept(chunk(40, 0, 4, Items.COAL, Items.QUARTZ)),
                "An ignored future continuation must not update the generation watermark");
        assertFalse(state.accept(futureTail));
        assertTrue(state.accept(chunk(40, 2, 4, Items.STONE, Items.PAPER)));
        var previous = state.resources();
        assertItems(List.of(Items.COAL, Items.QUARTZ, Items.STONE, Items.PAPER), previous);

        assertTrue(state.accept(chunk(41, 0, 4, Items.GOLD_INGOT, Items.IRON_INGOT)));
        assertSame(previous, state.resources());
        assertTrue(state.accept(futureTail));
        assertItems(List.of(Items.GOLD_INGOT, Items.IRON_INGOT, Items.DIAMOND, Items.EMERALD), state.resources());
    }

    @Test
    void newerOffsetZeroAbandonsAnIncompleteOlderGenerationWithoutClearingVisibleResources() {
        var state = new SingularityCollectionState();
        assertTrue(state.accept(chunk(7, 0, 1, Items.NETHER_STAR)));
        var visible = state.resources();
        assertTrue(state.accept(chunk(8, 0, 6, Items.COAL, Items.QUARTZ)));
        assertTrue(state.accept(chunk(9, 0, 4, Items.STONE, Items.PAPER)));
        assertSame(visible, state.resources());
        assertFalse(state.accept(chunk(8, 0, 6, Items.COAL, Items.QUARTZ)));
        assertFalse(state.accept(chunk(8, 2, 6, Items.GOLD_INGOT, Items.IRON_INGOT)));
        assertFalse(state.accept(chunk(8, 4, 6, Items.APPLE, Items.CARROT)));
        assertSame(visible, state.resources());
        assertTrue(state.accept(chunk(9, 2, 4, Items.DIAMOND, Items.EMERALD)));
        assertItems(List.of(Items.STONE, Items.PAPER, Items.DIAMOND, Items.EMERALD), state.resources());
        assertItems(List.of(Items.NETHER_STAR), visible);
    }

    @Test
    void missingAndOverlappingOffsetsAreRejectedWithoutConsumingPendingProgress() {
        var state = new SingularityCollectionState();
        var empty = state.resources();
        var missingMiddle = chunk(1, 2, 6, Items.DIAMOND, Items.COAL);
        var last = chunk(1, 4, 6, Items.EMERALD, Items.QUARTZ);
        assertTrue(state.accept(chunk(1, 0, 6, Items.STONE, Items.PAPER)));
        assertFalse(state.accept(last), "Do not buffer a tail past a missing offset");
        assertFalse(state.accept(chunk(1, 1, 6, Items.GOLD_INGOT, Items.IRON_INGOT)), "Reject an overlap");
        assertFalse(state.accept(chunk(1, 3, 6, Items.APPLE, Items.CARROT)), "Reject an unaligned gap");
        assertSame(empty, state.resources());
        assertTrue(state.accept(missingMiddle), "Rejected chunks must not advance the expected offset");
        assertSame(empty, state.resources(), "A previously rejected tail is not silently replayed");
        assertTrue(state.accept(last));
        assertItems(List.of(Items.STONE, Items.PAPER, Items.DIAMOND, Items.COAL, Items.EMERALD, Items.QUARTZ),
                state.resources());
    }

    @Test
    void mismatchedTotalsAndSameGenerationRestartsCannotCorruptPendingItems() {
        var state = new SingularityCollectionState();
        var empty = state.resources();
        assertTrue(state.accept(chunk(50, 0, 4, Items.STONE, Items.PAPER)));
        assertFalse(state.accept(chunk(50, 2, 5, Items.GOLD_INGOT, Items.IRON_INGOT)));
        assertFalse(state.accept(chunk(50, 0, 3, Items.NETHER_STAR)));
        assertFalse(state.accept(chunk(50, 0, 0)), "An empty snapshot also needs a newer generation");
        assertSame(empty, state.resources());
        assertTrue(state.accept(chunk(50, 2, 4, Items.DIAMOND, Items.EMERALD)));
        assertItems(List.of(Items.STONE, Items.PAPER, Items.DIAMOND, Items.EMERALD), state.resources());
        var complete = state.resources();
        assertFalse(state.accept(chunk(50, 0, 0)));
        assertSame(complete, state.resources());
    }

    @Test
    void resourcesReuseListAndStackIdentityAcross1000ReadsAndEmptyClearing() {
        var state = new SingularityCollectionState();
        assertStableIdentity(state, state.resources());
        assertTrue(state.accept(chunk(0, 0, 2, Items.DIAMOND, Items.PAPER)));
        var populated = state.resources();
        assertStableIdentity(state, populated);

        assertTrue(state.accept(chunk(1, 0, 2, Items.COAL)));
        assertStableIdentity(state, populated);
        assertTrue(state.accept(chunk(2, 0, 0)), "Empty snapshots must discard incomplete pending items too");
        var cleared = state.resources();
        assertTrue(cleared.isEmpty());
        assertNotSame(populated, cleared);
        assertStableIdentity(state, cleared);
        assertFalse(state.accept(chunk(1, 1, 2, Items.QUARTZ)));
        assertFalse(state.accept(chunk(2, 0, 0)));
        assertSame(cleared, state.resources());
        assertItems(List.of(Items.DIAMOND, Items.PAPER), populated);

        assertTrue(state.accept(chunk(3, 0, 1, Items.EMERALD)));
        assertItems(List.of(Items.EMERALD), state.resources());
        assertStableIdentity(state, state.resources());
        assertTrue(cleared.isEmpty(), "Previously published snapshots remain unchanged after later completions");
    }

    @Test
    void generationZeroAndFullPositiveLongRangeAreNotTruncated() {
        var state = new SingularityCollectionState();
        assertTrue(state.accept(chunk(0, 0, 1, Items.PAPER)));
        assertItems(List.of(Items.PAPER), state.resources());
        assertTrue(state.accept(chunk(1L << 40, 0, 1, Items.DIAMOND)));
        assertItems(List.of(Items.DIAMOND), state.resources());
        assertTrue(state.accept(chunk(Long.MAX_VALUE, 0, 1, Items.EMERALD)));
        var latest = state.resources();
        assertFalse(state.accept(chunk(Long.MAX_VALUE - 1, 0, 1, Items.COAL)));
        assertFalse(state.accept(chunk(Long.MAX_VALUE, 0, 1, Items.PAPER)));
        assertSame(latest, state.resources());
        assertItems(List.of(Items.EMERALD), latest);
    }

    private static SingularityCollectionPayload chunk(long generation, int offset, int total, Item... items) {
        return new SingularityCollectionPayload(7, generation, offset, total, List.of(items));
    }

    private static void assertItems(List<Item> expected, List<ItemStack> actual) {
        assertEquals(expected, actual.stream().map(ItemStack::getItem).toList());
        for (var stack : actual) {
            assertFalse(stack.isEmpty());
            assertEquals(1, stack.getCount());
        }
    }

    private static void assertStableIdentity(SingularityCollectionState state, List<ItemStack> expected) {
        for (int call = 0; call < 1_000; call++) {
            var actual = state.resources();
            assertSame(expected, actual, "Rendering must not rebuild the resource list");
            for (int index = 0; index < expected.size(); index++) {
                assertSame(expected.get(index), actual.get(index), "Rendering must not rebuild ItemStacks");
            }
        }
    }
}
