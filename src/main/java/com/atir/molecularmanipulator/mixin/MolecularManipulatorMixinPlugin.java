package com.atir.molecularmanipulator.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class MolecularManipulatorMixinPlugin implements IMixinConfigPlugin {
    private static final String ADVANCED_AE_MIXIN =
            "com.atir.molecularmanipulator.mixin.AdvancedAECraftingCpuLogicMixin";
    private static final String CRAFT_CONFIRM_MENU_RESOURCE =
            "appeng/menu/me/crafting/CraftConfirmMenu.class";
    private static final String CRAFT_CONFIRM_INT_MIXIN =
            "com.atir.molecularmanipulator.mixin.CraftConfirmMenuMixin";
    private static final String CRAFT_CONFIRM_LONG_MIXIN =
            "com.atir.molecularmanipulator.mixin.CraftConfirmMenuLongMixin";
    private static final String PLAN_JOB_LONG_DESCRIPTOR =
            "(Lappeng/api/stacks/AEKey;JLappeng/api/networking/crafting/CalculationStrategy;)Z";

    private static volatile CraftConfirmAmountType craftConfirmAmountType;

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (ADVANCED_AE_MIXIN.equals(mixinClassName)) {
            var loadingModList = FMLLoader.getLoadingModList();
            return loadingModList != null && loadingModList.getModFileById("advanced_ae") != null;
        }

        if (CRAFT_CONFIRM_INT_MIXIN.equals(mixinClassName)) {
            return getCraftConfirmAmountType() == CraftConfirmAmountType.INT;
        }
        if (CRAFT_CONFIRM_LONG_MIXIN.equals(mixinClassName)) {
            return getCraftConfirmAmountType() == CraftConfirmAmountType.LONG;
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

    private static CraftConfirmAmountType getCraftConfirmAmountType() {
        var cached = craftConfirmAmountType;
        if (cached != null) {
            return cached;
        }

        synchronized (MolecularManipulatorMixinPlugin.class) {
            cached = craftConfirmAmountType;
            if (cached == null) {
                cached = inspectCraftConfirmAmountType();
                craftConfirmAmountType = cached;
            }
        }
        return cached;
    }

    private static CraftConfirmAmountType inspectCraftConfirmAmountType() {
        final ClassNode targetClass = new ClassNode();
        try (var classBytes = MixinService.getService()
                .getResourceAsStream(CRAFT_CONFIRM_MENU_RESOURCE)) {
            if (classBytes == null) {
                throw new IllegalStateException(
                        "Unable to find AE2 CraftConfirmMenu bytecode for int/long amount compatibility");
            }
            new ClassReader(classBytes).accept(
                    targetClass,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to inspect AE2 CraftConfirmMenu for int/long amount compatibility",
                    exception);
        }

        for (FieldNode field : targetClass.fields) {
            if (!"amount".equals(field.name)) {
                continue;
            }
            return switch (field.desc) {
                case "I" -> CraftConfirmAmountType.INT;
                case "J" -> {
                    var hasLongPlanJob = targetClass.methods.stream()
                            .anyMatch(method -> "planJob".equals(method.name)
                                    && PLAN_JOB_LONG_DESCRIPTOR.equals(method.desc));
                    if (!hasLongPlanJob) {
                        throw new IllegalStateException(
                                "AE2 CraftConfirmMenu stores long amounts but has no "
                                        + "planJob(AEKey, long, CalculationStrategy)");
                    }
                    yield CraftConfirmAmountType.LONG;
                }
                default -> throw unsupportedCraftConfirmAmount(field.desc);
            };
        }
        throw unsupportedCraftConfirmAmount("<missing>");
    }

    private static IllegalStateException unsupportedCraftConfirmAmount(String descriptor) {
        return new IllegalStateException(
                "Unsupported AE2 CraftConfirmMenu amount field descriptor: " + descriptor
                        + " (expected I for official AE2 or J for AE2-UELM)");
    }

    private enum CraftConfirmAmountType {
        INT,
        LONG
    }
}
