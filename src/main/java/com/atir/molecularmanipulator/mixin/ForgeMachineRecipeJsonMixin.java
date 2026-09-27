package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.crafting.ForgeMachineRecipeJson;
import com.google.gson.JsonElement;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeManager.class)
public abstract class ForgeMachineRecipeJsonMixin {
    @Inject(method = "apply", at = @At("RETURN"))
    private void molecularmanipulator$captureMachineJson(Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resources, ProfilerFiller profiler, CallbackInfo callback) {
        ForgeMachineRecipeJson.capture((RecipeManager) (Object) this, recipes);
    }
}
