package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.crafting.ForgeMachineRecipeJson;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional script reload hook; only final JSON enters the machine import cache. */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.RecipesEventJS", remap = false)
public abstract class KubeJsMachineRecipeJsonMixin {
    @Inject(method = "post", at = @At("RETURN"))
    private void molecularmanipulator$captureScriptJson(RecipeManager manager,
            Map<ResourceLocation, JsonElement> source, CallbackInfo callback) {
        ForgeMachineRecipeJson.captureKubeJs(manager, this);
    }
}
