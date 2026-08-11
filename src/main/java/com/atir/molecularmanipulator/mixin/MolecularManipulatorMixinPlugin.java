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
        if (ADVANCED_AE_MIXIN.equals(mixinClassName)) {
            return loadingModList != null && loadingModList.getModFileById("advanced_ae") != null;
        }
        if (LABELED_PATTERNS_MIXIN.equals(mixinClassName)) {
            return loadingModList != null && loadingModList.getModFileById("ae2labeledpatterns") != null;
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
