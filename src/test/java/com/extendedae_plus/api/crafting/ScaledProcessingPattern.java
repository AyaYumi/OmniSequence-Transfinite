package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;

/** Optional EAEP wrapper ABI fixture; only the isolated GameTests load the real addon. */
public class ScaledProcessingPattern implements IPatternDetails {
    protected final IPatternDetails original;
    protected final long multiplier;

    public ScaledProcessingPattern(IPatternDetails original, long multiplier) {
        this.original = original;
        this.multiplier = multiplier;
    }

    public IPatternDetails getOriginal() { return original; }
    @Override public AEItemKey getDefinition() { return original.getDefinition(); }
    @Override public IInput[] getInputs() { return new IInput[0]; }
    @Override public List<GenericStack> getOutputs() { return List.of(); }
}
