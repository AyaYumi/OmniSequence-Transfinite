package com.extendedae_plus.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;

/** Test-only ABI fixture matching EAEP 1.5.5; never packaged in the mod. */
public class ScaledProcessingPattern implements IPatternDetails {
    protected final IPatternDetails original;
    protected final long multiplier;
    public ScaledProcessingPattern(IPatternDetails original, long multiplier) {
        this.original = original; this.multiplier = multiplier;
    }
    public IPatternDetails getOriginal() { return original; }
    @Override public AEItemKey getDefinition() { return original.getDefinition(); }
    @Override public IInput[] getInputs() { return original.getInputs(); }
    @Override public List<GenericStack> getOutputs() { return List.of(); }
}
