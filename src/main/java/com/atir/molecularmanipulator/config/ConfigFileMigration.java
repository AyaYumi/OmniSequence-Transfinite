package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ConfigFileMigration {
    public static final String CLIENT_FILE = "omnisequence-transfinite-client.toml";
    public static final String SERVER_FILE = "omnisequence-transfinite-server.toml";

    private static final String LEGACY_CLIENT_FILE = "molecularmanipulator-client.toml";
    private static final String LEGACY_SERVER_FILE = "molecularmanipulator-server.toml";
    private static final String RETIRED_UNSCALED_DISPATCH_LIMIT =
            "omni_unscaled_dispatch_attempts_per_tick";
    private static final String RETIRED_BATCH_SUBSTITUTION_OPTION =
            "omni_batch_allow_substitution_patterns";
    private static final Map<String, String> SERVER_CATEGORY_MIGRATIONS =
            Map.ofEntries(
                    category("sequence_array.force_load_chunks", "multiblocks.force_load_chunks"),
                    category("force_load_chunks", "multiblocks.force_load_chunks"),
                    category("sequence_array.singularity_collection.raw_ore_tags", "singularity_hub.singularity_collection.raw_ore_tags"),
                    category("sequence_array.singularity_collection.log_tags", "singularity_hub.singularity_collection.log_tags"),
                    category("sequence_array.singularity_collection.item_blacklist", "singularity_hub.singularity_collection.item_blacklist"),
                    category("sequence_array.singularity_collection.batch_size", "singularity_hub.singularity_collection.batch_size"),
                    category("sequence_array.singularity_collection.interval_ticks", "singularity_hub.singularity_collection.interval_ticks"),
                    category("sequence_array.singularity_duplication.energy_priority", "singularity_hub.singularity_duplication.energy_priority"),
                    category("sequence_array.singularity_duplication.fe_per_unit", "singularity_hub.singularity_duplication.fe_per_unit"),
                    category("sequence_array.singularity_duplication.ae_per_unit", "singularity_hub.singularity_duplication.ae_per_unit"),
                    category("sequence_array.singularity_duplication.matter_per_black_hole", "singularity_hub.singularity_duplication.matter_per_black_hole"),
                    category("sequence_array.singularity_duplication.interval_ticks", "singularity_hub.singularity_duplication.interval_ticks"),
                    category("pattern_pages", "sequence_array.pattern_pages"),
                    category("build_blocks_per_tick", "sequence_array.build_blocks_per_tick"),
                    category("idle_power", "sequence_array.idle_power"),
                    category("max_crafting_order_amount",
                            "ae2_crafting.max_crafting_order_amount"),
                    category("omni_max_fast_mode",
                            "omni_computation.optimizer.omni_max_fast_mode"),
                    category("omni_max_fast_max_nodes",
                            "omni_computation.optimizer.omni_max_fast_max_nodes"),
                    category("omni_max_fast_compile_budget_ms",
                            "omni_computation.optimizer.omni_max_fast_compile_budget_ms"),
                    category("omni_max_fast_diagnostics",
                            "omni_computation.optimizer.omni_max_fast_diagnostics"),
                    category("omni_max_fast_graph_cache_enabled",
                            "omni_computation.cache.omni_max_fast_graph_cache_enabled"),
                    category("omni_max_fast_graph_cache_size",
                            "omni_computation.cache.omni_max_fast_graph_cache_size"),
                    category("omni_max_fast_graph_cache_ttl_minutes",
                            "omni_computation.cache.omni_max_fast_graph_cache_ttl_minutes"),
                    category("omni_max_fast_parallel_execution_enabled",
                            "omni_computation.execution.omni_max_fast_parallel_execution_enabled"),
                    category("omni_max_fast_parallel_thread_pool_size",
                            "omni_computation.execution.omni_max_fast_parallel_thread_pool_size"),
                    category("omni_max_fast_smart_candidate_selection",
                            "omni_computation.execution.omni_max_fast_smart_candidate_selection"),
                    category("omni_max_fast_precompile_enabled",
                            "omni_computation.execution.omni_max_fast_precompile_enabled"),
                    category("omni_max_fast_precompile_common_items",
                            "omni_computation.execution.omni_max_fast_precompile_common_items"),
                    category("omni_batch_dispatch_enabled",
                            "omni_computation.dispatch.omni_batch_dispatch_enabled"),
                    category("omni_compat_dispatch_max_calls_per_tick",
                            "omni_computation.dispatch.omni_compat_dispatch_max_calls_per_tick"),
                    category("omni_compat_dispatch_max_time_us",
                            "omni_computation.dispatch.omni_compat_dispatch_max_time_us"),
                    category("omni_dispatch_max_work_units",
                            "omni_computation.dispatch.omni_dispatch_max_work_units"));
    private static final Map<String, String> CLIENT_CATEGORY_MIGRATIONS = Map.of(
            "dynamic_effect_level", "visual.dynamic_effect_level");

    private ConfigFileMigration() {
    }

    public static void migrateGlobalConfigs() {
        migrateDirectory(FMLPaths.CONFIGDIR.get());
        migrateDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"));
        removeRetiredClientOptions(FMLPaths.CONFIGDIR.get());
        removeRetiredClientOptions(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"));
        categorizeDirectory(FMLPaths.CONFIGDIR.get());
        categorizeDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"));
        removeRetiredServerOptions(FMLPaths.CONFIGDIR.get(), "server/default");
        removeRetiredServerOptions(
                FMLPaths.GAMEDIR.get().resolve("defaultconfigs"),
                "server/default");
    }

    public static void refreshGlobalConfigSchemas(
            ModConfigSpec serverSpec, ModConfigSpec clientSpec) {
        refreshDirectory(FMLPaths.CONFIGDIR.get(), serverSpec, clientSpec);
        refreshDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"),
                serverSpec, clientSpec);
    }

    private static void migrateDirectory(Path directory) {
        migrateFile(directory, LEGACY_CLIENT_FILE, CLIENT_FILE);
        migrateFile(directory, LEGACY_SERVER_FILE, SERVER_FILE);
    }

    private static void categorizeDirectory(Path directory) {
        ConfigSchemaGuard.relocateOptions(
                directory.resolve(SERVER_FILE), SERVER_CATEGORY_MIGRATIONS, "server/default");
        ConfigSchemaGuard.mergeCollectorItemTags(directory.resolve(SERVER_FILE), "server/default");
        ConfigSchemaGuard.relocateOptions(
                directory.resolve(CLIENT_FILE), CLIENT_CATEGORY_MIGRATIONS, "client");
    }

    private static void refreshDirectory(Path directory,
            ModConfigSpec serverSpec, ModConfigSpec clientSpec) {
        categorizeDirectory(directory);
        removeRetiredServerOptions(directory, "server/default");
        removeRetiredClientOptions(directory);
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(SERVER_FILE), serverSpec, "server/default");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(CLIENT_FILE), clientSpec, "client");
    }

    private static Map.Entry<String, String> category(String oldPath, String newPath) {
        return Map.entry(oldPath, newPath);
    }


    private static void removeRetiredServerOptions(
            Path directory, String displayName) {
        ConfigSchemaGuard.removeObsoleteOptions(
                directory.resolve(SERVER_FILE),
                java.util.List.of(RETIRED_UNSCALED_DISPATCH_LIMIT,
                        RETIRED_BATCH_SUBSTITUTION_OPTION, "ae2_crafting",
                        "omni_computation.optimizer", "omni_computation.cache",
                        "omni_computation.execution", "sequence_array.matter_rewrite",
                        "matter_sequence_capacity", "matter_entropy_capacity",
                        "matter_entropy_cooling_per_second", "matter_speed_cards"),
                displayName);
    }

    private static void removeRetiredClientOptions(Path directory) {
        ConfigSchemaGuard.removeObsoleteOptions(directory.resolve(CLIENT_FILE),
                java.util.List.of("tooltips", "matter_sequence_tooltip_mode"), "client");
    }

    static void migrateFile(Path directory, String legacyFileName, String fileName) {
        var legacyFile = directory.resolve(legacyFileName);
        var file = directory.resolve(fileName);
        if (Files.exists(file) || !Files.isRegularFile(legacyFile)) {
            return;
        }

        try {
            Files.copy(legacyFile, file);
            MolecularManipulator.LOGGER.info("Migrated config file {} to {}", legacyFile, file);
        } catch (FileAlreadyExistsException ignored) {
            // Another startup path completed the same non-overwriting migration.
        } catch (IOException exception) {
            MolecularManipulator.LOGGER.warn("Could not migrate config file {} to {}",
                    legacyFile, file, exception);
        }
    }
}
