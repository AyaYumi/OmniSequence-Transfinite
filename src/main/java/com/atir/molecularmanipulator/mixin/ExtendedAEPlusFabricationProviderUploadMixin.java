package com.atir.molecularmanipulator.mixin;

import appeng.helpers.patternprovider.PatternContainer;
import com.atir.molecularmanipulator.integration.extendedaeplus.MatterFabricationProviderUpload;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Keep EAEP's real insertion/duplicate/recycling path; change only its automatic fabrication target filter. */
@Pseudo
@Mixin(targets = "com.extendedae_plus.util.uploadPattern.ProviderUploadUtil", remap = false)
public abstract class ExtendedAEPlusFabricationProviderUploadMixin {
    @WrapOperation(method = "uploadFromEncodingMenuToNamedProvider",
            at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target =
                    "Lcom/extendedae_plus/util/PatternProviderDataUtil;getProviderDisplayName(Lappeng/helpers/patternprovider/PatternContainer;)Ljava/lang/String;"))
    private static String molecularmanipulator$matchFabricationMachine(PatternContainer container,
            Operation<String> original, @Local(argsOnly = true) String requestedName,
            @Local(argsOnly = true) ServerPlayer player) {
        if (!MatterFabricationProviderUpload.isAutomaticTarget(requestedName)) return original.call(container);
        // Native providers and foreign/unformed wells must never match the automatic category token.
        return MatterFabricationProviderUpload.accepts(container, player) ? requestedName : "";
    }
}
