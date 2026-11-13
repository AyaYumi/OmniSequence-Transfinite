package com.atir.molecularmanipulator.mixin;

import appeng.blockentity.misc.InscriberRecipes;
import appeng.recipes.handlers.InscriberRecipe;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.stream.StreamSupport;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The inscriber uses its own byType loop, not RecipeManager.getRecipeFor. */
@Mixin(value = InscriberRecipes.class, remap = false)
public abstract class RecipeLockAEInscriberRecipesMixin {
    @WrapMethod(method = "makeNamePressRecipe")
    private static InscriberRecipe recipeLock$identifyDynamicNamePress(ItemStack input, ItemStack top, ItemStack bottom,
            Operation<InscriberRecipe> original) {
        return RecipeLocks.identifyNamePress(original.call(input, top, bottom));
    }

    @WrapOperation(method = "findRecipe", at = @At(value = "INVOKE",
            target = "Lappeng/blockentity/misc/InscriberRecipes;getRecipes(Lnet/minecraft/world/level/Level;)Ljava/lang/Iterable;"))
    private static Iterable<InscriberRecipe> recipeLock$executionCandidates(Level level,
            Operation<Iterable<InscriberRecipe>> original) {
        var recipes = original.call(level);
        if (level.isClientSide || !RecipeExecutionContext.active() || !RecipeLocks.hasRules(level)) {
            return recipes;
        }
        // Keep searching after a denied match, but do not filter press validation or recipe viewers.
        return StreamSupport.stream(recipes.spliterator(), false)
                .filter(recipe -> RecipeLocks.allowedInContext(level, recipe)).toList();
    }
}
