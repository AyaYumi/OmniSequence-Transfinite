package com.atir.molecularmanipulator.crafting.dispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** The real CPU-selected prototype for exactly one craft. Its holders are read-only to admissions. */
public record OmniCapacityRequest(UUID jobId, ICraftingProvider provider, IPatternDetails pattern,
        KeyCounter[] unitInputs, KeyCounter unitOutputs, KeyCounter unitRemainders,
        BigInteger maximumCrafts, Level level) {
}
