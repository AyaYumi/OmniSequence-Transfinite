package com.atir.molecularmanipulator.blockentity;

/** Pure persistence contract for the automatic crafter's independent slots. */
final class MolecularAutoCraftSchema {
    static final int PATTERN_SLOTS = 9;
    static final int VERSION = 2;

    private MolecularAutoCraftSchema() {
    }

    static boolean isIndependentSlot(int slot) {
        return slot >= 0 && slot < PATTERN_SLOTS;
    }

    static boolean shouldLoadConfig(int version) {
        return version == VERSION;
    }
}
