package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndWarpTravelTest {
    @BeforeAll
    static void bootstrap() {
        HighEnergyLabWorldgenTest.bootstrap();
    }

    @Test
    void preparingTheIslandDoesNotGenerateTheLabAndEachSiteRunsOnlyOnce() {
        var data = new EndWarpTravel();
        var calls = new AtomicInteger();
        assertFalse(data.isDirty());
        assertTrue(data.prepareOnce(true, () -> { calls.incrementAndGet(); return true; }));
        assertTrue(data.isDirty());
        var islandOnly = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertTrue(islandOnly.getBoolean("IslandGenerated"));
        assertFalse(islandOnly.getBoolean("LabGenerated"));
        assertTrue(data.prepareOnce(true, () -> fail("An existing island must survive removal of its pad")));
        assertEquals(1, calls.get());
        assertTrue(data.prepareOnce(false, () -> { calls.incrementAndGet(); return true; }));
        assertTrue(data.prepareOnce(false, () -> fail("An existing lab must survive removal of its pad")));
        assertEquals(2, calls.get());
    }

    @Test
    void unsuccessfulPlacementIsNotPersistedAndCanRetry() {
        for (boolean returning : new boolean[] {true, false}) {
            var data = new EndWarpTravel();
            assertFalse(data.prepareOnce(returning, () -> false));
            assertFalse(data.isDirty());
            var saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
            assertFalse(saved.getBoolean("IslandGenerated"));
            assertFalse(saved.getBoolean("LabGenerated"));
            assertTrue(data.prepareOnce(returning, () -> true));
            assertTrue(data.isDirty());
        }
    }

    @Test
    void missingOrFailedTemplateDoesNotSetACompletionFlag() {
        var data = new EndWarpTravel();
        assertThrows(IllegalStateException.class, () -> data.prepareOnce(false, () -> {
            throw new IllegalStateException("Missing landing template");
        }));
        assertFalse(data.isDirty());
        assertTrue(data.prepareOnce(false, () -> true));
    }

    @Test
    void aRestartPreservesCompletedSitesWithoutRunningTheirGenerationAgain() {
        for (boolean returning : new boolean[] {true, false}) {
            var data = new EndWarpTravel();
            assertTrue(data.prepareOnce(returning, () -> true));
            var restored = EndWarpTravel.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
            assertFalse(restored.isDirty());
            assertTrue(restored.prepareOnce(returning, () -> fail("A saved site must never be rebuilt")));
            assertFalse(restored.isDirty());
            var calls = new AtomicInteger();
            assertTrue(restored.prepareOnce(!returning, () -> { calls.incrementAndGet(); return true; }));
            assertEquals(1, calls.get(), "The other site must still prepare on first use");
            var both = restored.save(new CompoundTag(), RegistryAccess.EMPTY);
            assertTrue(both.getBoolean("IslandGenerated"));
            assertTrue(both.getBoolean("LabGenerated"));
        }
    }
}
