package com.atir.molecularmanipulator.mixin;

import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class MolecularManipulatorMixinPlugin implements IMixinConfigPlugin {
    private static final DefaultArtifactVersion ECO_NATIVE_API_VERSION = new DefaultArtifactVersion("20.5.0");
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
        if (mixinClassName.endsWith(".MatterCpuJobContextMixin")) {
            if (targetClassName.startsWith("net.pedroksl.advanced_ae."))
                return loadingModList != null && loadingModList.getModFileById("advanced_ae") != null;
            if (targetClassName.startsWith("cn.dancingsnow.neoecoae.")) {
                var eco = loadingModList == null ? null : loadingModList.getModFileById("neoecoae");
                boolean legacy = targetClassName.equals("cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic");
                return eco != null && eco.getMods().stream().anyMatch(mod -> mod.getModId().equals("neoecoae")
                        && (mod.getVersion().compareTo(ECO_NATIVE_API_VERSION) < 0) == legacy);
            }
            return true;
        }
        if (mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1).startsWith("Eco")) {
            var eco = loadingModList == null ? null : loadingModList.getModFileById("neoecoae");
            return eco != null && eco.getMods().stream().anyMatch(mod -> mod.getModId().equals("neoecoae")
                    && mod.getVersion().compareTo(ECO_NATIVE_API_VERSION) >= 0);
        }
        if (mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1).startsWith("LightningWireless")) {
            // Private batch and scheduler hooks are verified only against these implementations.
            // Do not guess compatible descriptors for older or future Lightning builds.
            var lightning = loadingModList == null ? null : loadingModList.getModFileById("ae2lt");
            var supportedVersions = mixinClassName.endsWith(".LightningWirelessBatchRampMixin")
                    ? Set.of("2.1.4") : Set.of("2.1.2-beta4", "2.1.2", "2.1.4");
            return lightning != null && lightning.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("ae2lt") && supportedVersions.contains(mod.getVersion().toString()));
        }
        if (mixinClassName.endsWith(".DataEnergisticsDepotSimulationMixin")) {
            var data = loadingModList == null ? null : loadingModList.getModFileById("data_energistics");
            return data != null && data.getMods().stream().anyMatch(mod ->
                    mod.getModId().equals("data_energistics")
                            && Set.of("3.3.0", "3.3.5").contains(mod.getVersion().toString()));
        }
        if (mixinClassName.endsWith(".ThunderboltOmniCpuDispatchMixin")) {
            // A Forge port must supply the executor ABI, not merely the public provider interface.
            // Missing/incompatible optional APIs leave the shared AE2/ECO dispatcher in place.
            return loadingModList != null && loadingModList.getModFileById("thunderbolt") != null
                    && hasThunderboltDispatchApi();
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

    private static boolean hasThunderboltDispatchApi() {
        var api = "com/moakiee/thunderbolt/api/crafting/batch/";
        var core = "com/moakiee/thunderbolt/core/crafting/batch/";
        if (!hasOptionalMethods(core + "BatchExecutor", true,
                "runBatchOnly(IL" + core + "BatchCpuAccounting$Mode;Lappeng/me/service/CraftingService;"
                        + "Lappeng/api/networking/energy/IEnergyService;L" + api + "BatchJobView;"
                        + "Lappeng/crafting/inv/ListCraftingInventory;Ljava/util/Map;Ljava/lang/Runnable;"
                        + "Ljava/util/Map;IJZL" + core + "TickProviderDispatchSchedule;)L"
                        + core + "BatchExecutor$BatchRunResult;")) return false;
        var mode = optionalClass(core + "BatchCpuAccounting$Mode");
        var modeDescriptor = "L" + core + "BatchCpuAccounting$Mode;";
        return mode != null && Set.of("LINEAR", "SUCCESSFUL_DISPATCH").stream().allMatch(name ->
                mode.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(modeDescriptor)
                        && (field.access & Opcodes.ACC_ENUM) != 0))
                && hasOptionalMethods(api + "IBatchCraftingProvider", false,
                        "getBatchCapacity(Lappeng/api/crafting/IPatternDetails;)J",
                        "pushBatch(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;J)J")
                && hasOptionalMethods(api + "BatchJobView", false,
                        "level()Lnet/minecraft/world/level/Level;", "craftingId()Ljava/util/UUID;",
                        "taskIterator()Ljava/util/Iterator;", "waitingFor()Lappeng/crafting/inv/ListCraftingInventory;",
                        "insertWaitingFor(Lappeng/api/stacks/AEKey;J)V",
                        "addContainerMaxItems(JLappeng/api/stacks/AEKeyType;)V",
                        "failDispatch(Ljava/lang/String;Ljava/lang/Throwable;)V")
                && hasOptionalMethods(api + "BatchTaskHandle", false,
                        "details()Lappeng/api/crafting/IPatternDetails;", "getValue()J", "setValue(J)V")
                && hasOptionalMethods(core + "TickProviderDispatchSchedule", false, "<init>()V", "beginTick(J)V")
                && hasOptionalMethods(core + "BatchExecutor$BatchRunResult", false,
                        "dispatchedCopies()J", "consumedCpuOps()I");
    }

    private static boolean hasOptionalMethods(String owner, boolean isStatic, String... signatures) {
        var type = optionalClass(owner);
        if (type == null) return false;
        for (var signature : signatures) {
            if (type.methods.stream().noneMatch(method -> (method.name + method.desc).equals(signature)
                    && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic)) return false;
        }
        return true;
    }

    private static ClassNode optionalClass(String owner) {
        // Inspect raw descriptors without loading a mixin target or linking NeoForge-only classes.
        try (var input = MolecularManipulatorMixinPlugin.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            if (input == null) return null;
            var type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return type;
        } catch (IOException | IllegalArgumentException incompatible) {
            return null;
        }
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
