package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LabCrystallizationTest {
    @Test
    void outputStartsOnlyAfterTheInputHasBeenAbsorbed() {
        assertFalse(LabCrystallization.intake(40));
        assertFalse(LabCrystallization.delivery(40));
        assertTrue(LabCrystallization.intake(90));
        assertFalse(LabCrystallization.delivery(90));
        assertFalse(LabCrystallization.intake(LabCrystallization.ABSORBED));
        assertTrue(LabCrystallization.delivery(LabCrystallization.ABSORBED));
        assertFalse(LabCrystallization.delivery(LabCrystallization.DURATION));
    }

    @Test
    void transportPathsJoinTheActualTrayAndHolePositions() {
        assertEquals(LabCrystallization.INPUT, LabCrystallization.inputPosition(0));
        assertEquals(LabCrystallization.INPUT, LabCrystallization.inputPosition(LabCrystallization.INTAKE_START));
        assertEquals(LabCrystallization.BLACK, LabCrystallization.inputPosition(LabCrystallization.ABSORBED));
        assertEquals(LabCrystallization.WHITE_OUTLET, LabCrystallization.outputPosition(LabCrystallization.ABSORBED));
        assertEquals(LabCrystallization.OUTPUT, LabCrystallization.outputPosition(LabCrystallization.DURATION));
        for (float age = 0; age <= LabCrystallization.DURATION; age += .25F) {
            for (var point : new net.minecraft.world.phys.Vec3[] {LabCrystallization.inputPosition(age), LabCrystallization.outputPosition(age)}) {
                assertTrue(Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z));
                assertTrue(Math.abs(point.x) < 20 && point.y >= 3 && point.y < 20 && point.z >= 0 && point.z < 50);
            }
        }
    }
}
