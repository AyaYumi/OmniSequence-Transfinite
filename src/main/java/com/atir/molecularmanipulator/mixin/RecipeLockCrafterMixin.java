package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Authorize at the start of each redstone craft, before output, remainders, or input changes. */
@Mixin(CrafterBlock.class)
public abstract class RecipeLockCrafterMixin {
    @WrapMethod(method = "dispenseFrom")
    private void recipeLock$craftAsBlockEntity(BlockState state, ServerLevel level, BlockPos pos,
            Operation<Void> original) {
        if (level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter) {
            try (var scope = RecipeExecutionContext.open(crafter)) {
                original.call(state, level, pos);
            }
        } else {
            original.call(state, level, pos);
        }
    }

    @WrapOperation(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/CrafterBlock;getPotentialResults(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> recipeLock$recheckSharedCache(Level level, CraftingInput input,
            Operation<Optional<RecipeHolder<CraftingRecipe>>> original,
            BlockState state, ServerLevel serverLevel, BlockPos pos) {
        if (!level.isClientSide && RecipeLocks.hasRules(level)
                && level.getBlockEntity(pos) instanceof CrafterBlockEntity crafter) {
            // The static RecipeCache is keyed only by ingredients, not owner/team/unlock revision.
            // Bypass it only at execution: neither denied nor allowed team-specific answers enter it.
            return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .filter(recipe -> RecipeLocks.allowed(crafter, recipe));
        }
        return original.call(level, input);
    }
}
