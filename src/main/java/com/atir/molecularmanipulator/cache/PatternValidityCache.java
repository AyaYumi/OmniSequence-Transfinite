package com.atir.molecularmanipulator.cache;

import appeng.api.stacks.AEKey;
import net.minecraft.world.level.Level;

public record PatternValidityCache(AEKey input, Level level, boolean valid) {
}
