package com.atir.molecularmanipulator.blockentity;

/** Implemented by the controller; parts never store an independent copy of its setting. */
public interface MachineEffectHost {
    MachineEffectSettings getMachineEffects();
}
