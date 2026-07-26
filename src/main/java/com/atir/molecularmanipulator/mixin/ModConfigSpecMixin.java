package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.config.ConfigSchemaGuard;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.ConfigSpec.CorrectionListener;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeConfigSpec.class, remap = false)
public abstract class ModConfigSpecMixin {
    @Inject(
            method = "correct(Lcom/electronwill/nightconfig/core/CommentedConfig;"
                    + "Lcom/electronwill/nightconfig/core/ConfigSpec$CorrectionListener;"
                    + "Lcom/electronwill/nightconfig/core/ConfigSpec$CorrectionListener;)I",
            at = @At("HEAD"))
    private void molecularmanipulator$resetOutdatedConfig(
            CommentedConfig config, CorrectionListener listener,
            @Nullable CorrectionListener commentListener,
            CallbackInfoReturnable<Integer> callback) {
        ConfigSchemaGuard.resetLoadedTomlIfOutdated(
                (ForgeConfigSpec) (Object) this, config);
    }
}
