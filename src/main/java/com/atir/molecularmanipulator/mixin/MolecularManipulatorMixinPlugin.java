package com.atir.molecularmanipulator.mixin;

import net.neoforged.fml.loading.FMLLoader;
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
        if (mixinClassName.endsWith(".SingularityLithiumCollisionMixin")) {
            return loadingModList != null && loadingModList.getModFileById("lithium") != null;
        }
        if (mixinClassName.endsWith(".MatterCpuJobContextMixin")) {
            if (targetClassName.startsWith("net.pedroksl.advanced_ae.")) {
                return loadingModList != null && loadingModList.getModFileById("advanced_ae") != null;
            }
            if (targetClassName.startsWith("cn.dancingsnow.neoecoae.")) {
                return loadingModList != null && loadingModList.getModFileById("neoecoae") != null;
            }
            return true;
        }
        if (mixinClassName.endsWith(".TrinityMatterCpuJobContextMixin")) {
            return loadingModList != null && loadingModList.getModFileById("data_energistics") != null;
        }
        if (mixinClassName.endsWith(".LegacyAelisTaskReconciliationMixin")) {
            var applied = loadingModList == null ? null : loadingModList.getModFileById("appliedenhancements");
            return applied != null && applied.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("appliedenhancements") && mod.getVersion().toString().equals("1.1.0"));
        }
        if (mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1).startsWith("Eco")) {
            var eco = loadingModList == null ? null : loadingModList.getModFileById("neoecoae");
            return eco != null && eco.getMods().stream().anyMatch(mod -> mod.getModId().equals("neoecoae")
                    && mod.getVersion().compareTo(new org.apache.maven.artifact.versioning.DefaultArtifactVersion("21.2.10")) >= 0);
        }
        if (mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1).startsWith("LightningWireless")) {
            // Private scheduler hooks are verified against this exact implementation.
            // Do not guess compatible descriptors for older or future Lightning builds.
            var lightning = loadingModList == null ? null : loadingModList.getModFileById("ae2lt");
            return lightning != null && lightning.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("ae2lt") && mod.getVersion().toString().equals("2.1.2-beta4"));
        }
        if (mixinClassName.endsWith(".ThunderboltOmniCpuDispatchMixin")) {
            var thunderbolt = loadingModList == null ? null : loadingModList.getModFileById("thunderbolt");
            return thunderbolt != null && thunderbolt.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("thunderbolt") && mod.getVersion().compareTo(
                            new org.apache.maven.artifact.versioning.DefaultArtifactVersion("2.0.3-beta3")) >= 0);
        }
        if (mixinClassName.endsWith(".ThunderboltMatterPatternBatchMixin")) {
            return loadingModList != null && loadingModList.getModFileById("thunderbolt") != null;
        }
        if (mixinClassName.endsWith(".DataEnergisticsDepotSimulationMixin")) {
            var data = loadingModList == null ? null : loadingModList.getModFileById("data_energistics");
            return data != null && data.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("data_energistics")
                            && java.util.Set.of("3.3.0", "3.3.5").contains(mod.getVersion().toString()));
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
        if (mixinClassName.endsWith(".ExtendedAEPlusFabricationProviderCacheMixin")
                || mixinClassName.endsWith(".ExtendedAEPlusFabricationProviderUploadMixin")) {
            var eaep = loadingModList == null ? null : loadingModList.getModFileById("extendedae_plus");
            return eaep != null && eaep.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("extendedae_plus") && mod.getVersion().toString().equals("1.6.4.1"));
        }
        if (EXTENDEDAE_PLUS_UPLOAD_MIXIN.equals(mixinClassName)) {
            return loadingModList != null && loadingModList.getModFileById("extendedae_plus") != null;
        }
        return true;
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
