package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MolecularAutoCraftPatternCacheTest {
    @Test
    void unchangedSlotsDecodeOnlyOnceAcrossReconciliationCraftingAndMenuTicks() {
        var cache = new MolecularAutoCraftPatternCache<String, Object>();
        var level = new Object();
        var recipes = new Object();
        var decodes = new AtomicInteger();
        var details = new Object[MolecularAutoCraftSchema.PATTERN_SLOTS];
        for (int slot = 0; slot < details.length; slot++) details[slot] = new Object();

        // Two minutes at 20 TPS, with reconciliation, crafting and menu reads.
        for (int tick = 0; tick < 2400; tick++) {
            for (int pass = 0; pass < 3; pass++) {
                for (int slot = 0; slot < details.length; slot++) {
                    Object expected = details[slot];
                    assertSame(expected, cache.get(slot, "pattern_" + slot, level, recipes, () -> {
                        decodes.incrementAndGet();
                        return expected;
                    }));
                }
            }
        }
        assertEquals(9, decodes.get(), "Stable slots must not be decoded again each tick");
    }

    @Test
    void detectsDefinitionChangesEvenWithoutInventoryCallbacks() {
        var cache = new MolecularAutoCraftPatternCache<String, String>();
        var level = new Object();
        var recipes = new Object();
        assertEquals("first", cache.get(0, "original", level, recipes, () -> "first"));
        assertEquals("second", cache.get(0, "replacement", level, recipes, () -> "second"));
        assertEquals("reinserted", cache.get(0, "original", level, recipes, () -> "reinserted"));
    }

    @Test
    void invalidatingOneSlotDoesNotEvictOtherSlots() {
        var cache = new MolecularAutoCraftPatternCache<String, String>();
        var level = new Object();
        var recipes = new Object();
        cache.get(0, "same_pattern", level, recipes, () -> "slot_zero");
        cache.get(1, "same_pattern", level, recipes, () -> "slot_one");
        cache.invalidate(0);
        assertEquals("new_zero", cache.get(0, "same_pattern", level, recipes, () -> "new_zero"));
        assertEquals("slot_one", cache.get(1, "same_pattern", level, recipes, () -> {
            fail("Changing another slot must not discard this pattern");
            return null;
        }));
    }

    @Test
    void recipeReloadInvalidatesAllSlotsEvenWithEqualRecipeIdsAndCounts() {
        var cache = new MolecularAutoCraftPatternCache<String, String>();
        var level = new Object();
        var recipes = new ArrayList<>(List.of("recipe_a", "recipe_b"));
        var reloaded = new ArrayList<>(recipes);
        assertEquals(recipes, reloaded);
        assertNotSame(recipes, reloaded);
        cache.get(0, "pattern", level, recipes, () -> "old_zero");
        cache.get(1, "pattern", level, recipes, () -> "old_one");
        assertEquals("new_zero", cache.get(0, "pattern", level, reloaded, () -> "new_zero"));
        assertEquals("new_one", cache.get(1, "pattern", level, reloaded, () -> "new_one"));
    }

    @Test
    void changingWorldInvalidatesPatternsEvenWithTheSameRecipeManager() {
        var cache = new MolecularAutoCraftPatternCache<String, String>();
        var recipes = new Object();
        cache.get(0, "pattern", new Object(), recipes, () -> "old_world");
        assertEquals("new_world", cache.get(0, "pattern", new Object(), recipes, () -> "new_world"));
    }

    @Test
    void clearForLoadAndDismantleForcesRedecodeInTheSameContext() {
        var cache = new MolecularAutoCraftPatternCache<String, String>();
        var level = new Object();
        var recipes = new Object();
        cache.get(0, "pattern", level, recipes, () -> "before_load");
        cache.clear();
        assertEquals("after_load", cache.get(0, "pattern", level, recipes, () -> "after_load"));
    }

    @Test
    void invalidPatternsAreNegativelyCachedButReloadAndSlotChangesRetryThem() {
        var cache = new MolecularAutoCraftPatternCache<String, String>();
        var level = new Object();
        var recipes = new Object();
        var decodes = new AtomicInteger();
        for (int tick = 0; tick < 100; tick++) {
            assertNull(cache.get(0, "invalid", level, recipes, () -> {
                decodes.incrementAndGet();
                return null;
            }));
        }
        assertEquals(1, decodes.get());
        var reloaded = new Object();
        assertEquals("now_valid", cache.get(0, "invalid", level, reloaded, () -> "now_valid"));
        cache.invalidate(0);
        assertEquals("reinserted", cache.get(0, "invalid", level, reloaded, () -> "reinserted"));
    }
}
