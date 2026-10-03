package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.ForgeConfigSpec;

/** Keep oversized renderers visible with Xenon, Sodium Extras and Entity Culling. */
public final class SingularityRenderCompatibility {
    private SingularityRenderCompatibility() {}

    public static void initialize() {
        registerEntityCulling();
        String configClass;
        if (ModList.get().isLoaded("xenon")) configClass = "org.embeddedt.embeddium.extras.ExtrasConfig";
        else if (ModList.get().isLoaded("sodiumextras")) configClass = "toni.sodiumextras.EmbyConfig";
        else return;
        try {
            // Optional integration with the optimizer's own per-type cache, before the first world.
            var config = Class.forName(configClass);
            allow(config, "entityWhitelist", "molecularmanipulator:singularity_assembly");
            allow(config, "tileEntityWhitelist", "molecularmanipulator:event_horizon_singularity_hub");
            allow(config, "tileEntityWhitelist", "molecularmanipulator:cosmic_singularity");
            allow(config, "tileEntityWhitelist", "molecularmanipulator:white_hole_resource_core");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Could not register Singularity distance-culling compatibility", ex);
        }
    }

    private static void registerEntityCulling() {
        if (!ModList.get().isLoaded("entityculling")) return;
        try {
            var type = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
            Object instance = type.getField("instance").get(null);
            java.util.function.Function<net.minecraft.world.level.block.entity.BlockEntity, Boolean> blocks =
                    block -> block instanceof com.atir.molecularmanipulator.blockentity.SingularityBlockEntity
                            || block instanceof com.atir.molecularmanipulator.blockentity.CosmicSingularityBlockEntity
                            || block instanceof com.atir.molecularmanipulator.blockentity.SingularityCoreBlockEntity;
            java.util.function.Function<net.minecraft.world.entity.Entity, Boolean> entities =
                    entity -> entity instanceof com.atir.molecularmanipulator.entity.SingularityAssemblyEntity;
            type.getMethod("addDynamicBlockEntityWhitelist", java.util.function.Function.class).invoke(instance, blocks);
            type.getMethod("addDynamicEntityWhitelist", java.util.function.Function.class).invoke(instance, entities);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Could not register Singularity Entity Culling compatibility", failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static void allow(Class<?> config, String field, String id) throws ReflectiveOperationException {
        Object value = config.getField(field).get(null);
        if (!(value instanceof ForgeConfigSpec.ConfigValue<?> option) || !(option.get() instanceof List<?> entries)
                || entries.stream().anyMatch(entry -> !(entry instanceof String))) {
            throw new IllegalStateException("Unsupported Sodium Extras allowlist: " + field);
        }
        if (entries.contains(id) || entries.contains("molecularmanipulator:*")) return;
        var updated = new ArrayList<>((List<String>) entries);
        updated.add(id);
        var listOption = (ForgeConfigSpec.ConfigValue<List<String>>) option;
        listOption.set(List.copyOf(updated));
        listOption.save();
        MolecularManipulator.LOGGER.info("Singularity: registered {} in distance-culling {}", id, field);
    }
}
