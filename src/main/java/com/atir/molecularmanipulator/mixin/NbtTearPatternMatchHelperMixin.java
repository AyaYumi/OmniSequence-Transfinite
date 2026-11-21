package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

import java.util.NoSuchElementException;

@Pseudo
@Mixin(targets = "com.lhy.ae2utility.card.NbtTearPatternMatchHelper", remap = false)
public abstract class NbtTearPatternMatchHelperMixin {
    @WrapMethod(method = {"matchesCraftingPatternInput", "matchesProcessingPatternInput"},
            require = 0, expect = 0)
    private static boolean molecularmanipulator$handleMissingPatternProvider(AEKey input,
            GenericStack template, Operation<Boolean> original) {
        try {
            return original.call(input, template);
        } catch (NoSuchElementException unavailableProvider) {
            return input != null && template != null && input.matches(template);
        }
    }
}
