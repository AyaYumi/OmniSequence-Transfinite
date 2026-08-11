package com.atir.molecularmanipulator.mixin;

import appeng.helpers.patternprovider.PatternContainer;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps AE2 Labeled Patterns label lookup attached to the physical block entity
 * while its terminal operates on segmented pattern-container views.
 */
@Pseudo
@Mixin(targets = "org.ae2LabeledPatterns.integration.CheckProvider", remap = false)
public abstract class LabeledPatternCheckProviderMixin {
    @ModifyVariable(method = "isEntityProvider", at = @At("HEAD"), argsOnly = true)
    private static Object molecularmanipulator$unwrapForProviderCheck(Object object) {
        return unwrap(object);
    }

    @ModifyVariable(method = "getEntityProviderLabel", at = @At("HEAD"), argsOnly = true)
    private static Object molecularmanipulator$unwrapForLabelLookup(Object object) {
        return unwrap(object);
    }

    private static Object unwrap(Object object) {
        return object instanceof PatternContainer container
                ? SegmentedPatternContainers.unwrap(container)
                : object;
    }
}
