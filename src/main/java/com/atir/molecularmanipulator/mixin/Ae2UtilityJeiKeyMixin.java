package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Native lightning JEI ingredients are already AE keys, not item or fluid stacks. */
@Pseudo
@Mixin(targets = "com.lhy.ae2utility.util.GenericIngredientUtil", remap = false)
public abstract class Ae2UtilityJeiKeyMixin {
    @ModifyReturnValue(method = "toAEKey(Ljava/lang/Object;)Lappeng/api/stacks/AEKey;",
            at = @At("RETURN"), require = 0)
    private static AEKey molecularmanipulator$acceptNativeAeKey(AEKey original, Object ingredient) {
        return original == null && ingredient instanceof AEKey key ? key : original;
    }
}
