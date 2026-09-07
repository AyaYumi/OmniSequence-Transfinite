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
                    category("pattern_pages", "sequence_array.pattern_pages"),
                    category("build_blocks_per_tick", "sequence_array.build_blocks_per_tick"),
                    category("idle_power", "sequence_array.idle_power"),
                    category("matter_sequence_capacity",
                            "sequence_array.matter_rewrite.matter_sequence_capacity"),
                    category("matter_entropy_capacity",
                            "sequence_array.matter_rewrite.matter_entropy_capacity"),
                    category("matter_entropy_cooling_per_second",
                            "sequence_array.matter_rewrite.matter_entropy_cooling_per_second"),
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
                            "omni_computation.dispatch.omni_dispatch_max_work_units"),
                    speedCardCategory(0, "parallel"),
                    speedCardCategory(0, "cycle_ticks"),
                    speedCardCategory(0, "cooling_multiplier"),
                    speedCardCategory(1, "parallel"),
                    speedCardCategory(1, "cycle_ticks"),
                    speedCardCategory(1, "cooling_multiplier"),
                    speedCardCategory(2, "parallel"),
                    speedCardCategory(2, "cycle_ticks"),
                    speedCardCategory(2, "cooling_multiplier"),
                    speedCardCategory(3, "parallel"),
                    speedCardCategory(3, "cycle_ticks"),
                    speedCardCategory(3, "cooling_multiplier"),
                    speedCardCategory(4, "parallel"),
                    speedCardCategory(4, "cycle_ticks"),
                    speedCardCategory(4, "cooling_multiplier"));
    private static final Map<String, String> CLIENT_CATEGORY_MIGRATIONS = Map.of(
            "matter_sequence_tooltip_mode", "tooltips.matter_sequence_tooltip_mode",
            "dynamic_effect_level", "visual.dynamic_effect_level");

    private ConfigFileMigration() {
    }

    public static void migrateGlobalConfigs() {
        migrateDirectory(FMLPaths.CONFIGDIR.get());
        migrateDirectory(FMLPaths.GAMEDIR.get().resolve("defaultconfigs"));
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
        ConfigSchemaGuard.relocateOptions(
                directory.resolve(CLIENT_FILE), CLIENT_CATEGORY_MIGRATIONS, "client");
    }

    private static void refreshDirectory(Path directory,
            ModConfigSpec serverSpec, ModConfigSpec clientSpec) {
        categorizeDirectory(directory);
        removeRetiredServerOptions(directory, "server/default");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(SERVER_FILE), serverSpec, "server/default");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(CLIENT_FILE), clientSpec, "client");
    }

    private static Map.Entry<String, String> category(String oldPath, String newPath) {
        return Map.entry(oldPath, newPath);
    }

    private static Map.Entry<String, String> speedCardCategory(int cards, String suffix) {
        String option = "card_" + cards + "_" + suffix;
        return category("matter_speed_cards." + option,
                "sequence_array.matter_rewrite.speed_cards." + option);
    }

    private static void removeRetiredServerOptions(
            Path directory, String displayName) {
        ConfigSchemaGuard.removeObsoleteOptions(
                directory.resolve(SERVER_FILE),
                java.util.List.of(RETIRED_UNSCALED_DISPATCH_LIMIT,
                        RETIRED_BATCH_SUBSTITUTION_OPTION, "ae2_crafting",
                        "omni_computation.optimizer", "omni_computation.cache",
                        "omni_computation.execution"),
                displayName);
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
