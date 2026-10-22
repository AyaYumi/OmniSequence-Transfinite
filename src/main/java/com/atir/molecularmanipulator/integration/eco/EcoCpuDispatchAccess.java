package com.atir.molecularmanipulator.integration.eco;

/** Implemented by the optional CPU mixin, never by an optional ECO type. */
public interface EcoCpuDispatchAccess {
    EcoCpuDispatch ecoDispatch$dispatcher();
}
