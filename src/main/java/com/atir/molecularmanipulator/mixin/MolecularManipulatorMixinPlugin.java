package com.atir.molecularmanipulator.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class MolecularManipulatorMixinPlugin implements IMixinConfigPlugin {
    private static final String ADVANCED_AE_MIXIN = AdvancedAECraftingCpuLogicMixin.class.getName();
    private static final String LABELED_PATTERNS_MIXIN = LabeledPatternCheckProviderMixin.class.getName();
    private static final String JEI_RESPONSIVE_SLOT_MIXIN = JeiResponsiveSlotMixin.class.getName();
    private static final String JEI_RESPONSIVE_RENDER_MIXIN = JeiResponsiveRenderMixin.class.getName();
    private static final String EXTENDEDAE_PLUS_UPLOAD_MIXIN =
            ExtendedAEPlusPatternUploadMixin.class.getName();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var loadingModList = FMLLoader.getLoadingModList();
        if (mixinClassName.endsWith(".RadiumSingularityCollisionMixin")) {
            return loadingModList != null && loadingModList.getModFileById("radium") != null;
        }
        if (mixinClassName.endsWith(".KubeJsMachineRecipeJsonMixin")) {
            return loadingModList != null && loadingModList.getModFileById("kubejs") != null;
        }
        if (mixinClassName.endsWith(".LegacyAelisTaskReconciliationMixin")) {
            var applied = loadingModList == null ? null : loadingModList.getModFileById("appliedenhancements");
            return applied != null && applied.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("appliedenhancements") && mod.getVersion().toString().startsWith("1.1.0"));
        }
        if (mixinClassName.endsWith(".EcoMatterPatternBatchMixin")) {
            return loadingModList != null && loadingModList.getModFileById("neoecoae") != null
                    && hasOptionalApi("cn/dancingsnow/neoecoae/api/me/provider/ECOFastPathDispatchProvider.class");
        }
        if (mixinClassName.endsWith(".ThunderboltMatterPatternBatchMixin")) {
            return loadingModList != null && loadingModList.getModFileById("thunderbolt") != null
                    && hasOptionalApi("com/moakiee/thunderbolt/api/crafting/batch/IBatchCraftingProvider.class");
        }
        if (mixinClassName.endsWith(".DataEnergisticsCpuIdentityMixin")) {
            return loadingModList != null && loadingModList.getModFileById("data_energistics") != null;
        }
        if (mixinClassName.endsWith(".UselessExactOutputReturnMixin")) {
            return loadingModList != null && loadingModList.getModFileById("useless_mod") != null;
        }
        if (ADVANCED_AE_MIXIN.equals(mixinClassName)) {
            return loadingModList != null && loadingModList.getModFileById("advanced_ae") != null;
        }
        if (LABELED_PATTERNS_MIXIN.equals(mixinClassName)) {
            return loadingModList != null && loadingModList.getModFileById("ae2labeledpatterns") != null;
        }
        if (JEI_RESPONSIVE_SLOT_MIXIN.equals(mixinClassName) || JEI_RESPONSIVE_RENDER_MIXIN.equals(mixinClassName)) {
            return loadingModList != null && loadingModList.getModFileById("jei") != null;
        }
        if (mixinClassName.endsWith(".JeiAeStackAmountMixin")) {
            return loadingModList != null && loadingModList.getModFileById("jei") != null
                    && loadingModList.getModFileById("ae2jeiintegration") != null;
        }
        if (mixinClassName.endsWith(".Ae2UtilityJeiAmountMixin")
                || mixinClassName.endsWith(".Ae2UtilityJeiKeyMixin")) {
            return loadingModList != null && loadingModList.getModFileById("jei") != null
                    && loadingModList.getModFileById("ae2utility") != null;
        }
        if (EXTENDEDAE_PLUS_UPLOAD_MIXIN.equals(mixinClassName)
                || mixinClassName.endsWith(".ExtendedAEPlusMatrixUploadMixin")) {
            return loadingModList != null && loadingModList.getModFileById("extendedae_plus") != null;
        }
        return true;
    }

    private static boolean hasOptionalApi(String resource) {
        return MolecularManipulatorMixinPlugin.class.getClassLoader().getResource(resource) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
