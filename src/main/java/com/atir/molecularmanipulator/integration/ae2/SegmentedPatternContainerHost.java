package com.atir.molecularmanipulator.integration.ae2;

import appeng.helpers.patternprovider.PatternContainer;

import java.util.List;

/**
 * Exposes one physical pattern provider as multiple terminal-only containers.
 * Crafting-provider registration remains on the physical host and its grid node.
 */
public interface SegmentedPatternContainerHost {
    List<PatternContainer> molecularmanipulator$getTerminalPatternContainers();
}
