package com.atir.molecularmanipulator.registry;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.Map;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.registries.RegisterEvent;

/** Retain the source mod's renamed IDs without registering duplicate blocks, items or structures. */
@Mod.EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class RegistryIdMappings {
    private static final Map<String, String> BLOCK_IDS = Map.of(
            "green_fire_lantern", "simulation_lantern",
            "high_energy_lab_power_interface", "twin_singularity_observation_ring_power_interface",
            "high_energy_lab_platform", "twin_singularity_observation_ring_platform",
            "high_energy_lab_input_cradle", "twin_singularity_observation_ring_input_cradle",
            "high_energy_lab_output_cradle", "twin_singularity_observation_ring_output_cradle");
    private static final Map<String, String> BLOCK_ENTITY_IDS = Map.of(
            "green_fire_lantern", "simulation_lantern",
            "high_energy_lab_power_interface", "twin_singularity_observation_ring_power_interface",
            "high_energy_lab_cradle", "twin_singularity_observation_ring_cradle");
    private static final ResourceLocation OLD_STRUCTURE = MolecularManipulator.id("end_high_energy_lab");
    private static final ResourceLocation AIR = new ResourceLocation("minecraft", "air");

    private RegistryIdMappings() {}

    /** Called on the mod bus after DeferredRegister has populated each registry. */
    public static void registerAliases(RegisterEvent event) {
        var key = event.getRegistryKey();
        if (event.getForgeRegistry() instanceof ForgeRegistry<?> registry) {
            // ForgeRegistry, unlike Forge's DeferredRegister, really does provide addAlias in 1.20.1.
            // Install aliases even for fresh worlds: templates and saved item/entity NBT also use old names.
            if (key.equals(Registries.BLOCK) || key.equals(Registries.ITEM)) {
                registerAliases(registry, BLOCK_IDS);
                registry.addAlias(MolecularManipulator.id("resource_confluence_port"), AIR);
                if (key.equals(Registries.ITEM)) {
                    registry.addAlias(MolecularManipulator.id("singularity_sequence_matter"), AIR);
                }
            } else if (key.equals(Registries.BLOCK_ENTITY_TYPE)) {
                registerAliases(registry, BLOCK_ENTITY_IDS);
            } else if (key.equals(Registries.ENTITY_TYPE)) {
                registry.addAlias(MolecularManipulator.id("high_energy_lab_effects"), HighEnergyLabContent.EFFECTS.getId());
            }
        } else if (key.equals(Registries.STRUCTURE_TYPE) || key.equals(Registries.STRUCTURE_PIECE)) {
            retainVanillaId(event.getVanillaRegistry(), OLD_STRUCTURE, HighEnergyLabContent.ID);
        }
    }

    private static void registerAliases(ForgeRegistry<?> registry, Map<String, String> names) {
        names.forEach((oldName, newName) -> registry.addAlias(
                MolecularManipulator.id(oldName), MolecularManipulator.id(newName)));
    }

    @SubscribeEvent
    public static void remap(MissingMappingsEvent event) {
        // Preserve numeric mappings in Forge world snapshots as well as resolving named NBT lookups.
        for (var mapping : event.getMappings(Registries.BLOCK, MolecularManipulator.MOD_ID)) {
            var name = BLOCK_IDS.get(mapping.getKey().getPath());
            if (name != null) mapping.remap(ForgeRegistries.BLOCKS.getValue(MolecularManipulator.id(name)));
        }
        for (var mapping : event.getMappings(Registries.ITEM, MolecularManipulator.MOD_ID)) {
            var name = BLOCK_IDS.get(mapping.getKey().getPath());
            if (name != null) mapping.remap(ForgeRegistries.ITEMS.getValue(MolecularManipulator.id(name)));
        }
        for (var mapping : event.getMappings(Registries.BLOCK_ENTITY_TYPE, MolecularManipulator.MOD_ID)) {
            var name = BLOCK_ENTITY_IDS.get(mapping.getKey().getPath());
            if (name != null) mapping.remap(ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(MolecularManipulator.id(name)));
        }
        for (var mapping : event.getMappings(Registries.ENTITY_TYPE, MolecularManipulator.MOD_ID)) {
            if (mapping.getKey().getPath().equals("high_energy_lab_effects")) {
                mapping.remap(HighEnergyLabContent.EFFECTS.get());
            }
        }
    }

    @SubscribeEvent
    public static void aliasSavedStructures(ServerAboutToStartEvent event) {
        // These data-pack registries do not participate in Forge's MissingMappingsEvent.
        // Resolve old chunk starts/references before any level begins reading chunks.
        var registries = event.getServer().registryAccess();
        retainVanillaId(registries.registryOrThrow(Registries.STRUCTURE), OLD_STRUCTURE, HighEnergyLabContent.ID);
        retainVanillaId(registries.registryOrThrow(Registries.STRUCTURE_SET),
                MolecularManipulator.id("end_high_energy_labs"), MolecularManipulator.id("twin_singularity_observation_rings"));
    }

    private static <T> void retainVanillaId(Registry<T> registry, ResourceLocation oldId, ResourceLocation currentId) {
        if (registry.containsKey(oldId)) return; // An explicit data-pack entry still takes precedence.
        registry.getHolder(ResourceKey.create(registry.key(), currentId)).ifPresent(holder -> {
            // AT exposes only the two name lookup maps. Keep byId/byValue and the holder's canonical key intact:
            // reads accept the legacy name, iteration is not duplicated, and subsequent saves use currentId.
            var mapped = (MappedRegistry<T>) registry;
            mapped.byLocation.put(oldId, holder);
            mapped.byKey.put(ResourceKey.create(registry.key(), oldId), holder);
        });
    }
}
