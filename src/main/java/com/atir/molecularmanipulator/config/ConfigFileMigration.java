package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigFileMigration {
    public static final String CLIENT_FILE = "omnisequence-transfinite-client.toml";
    public static final String COMMON_FILE = "omnisequence-transfinite-common.toml";

    private static final String LEGACY_CLIENT_FILE = "molecularmanipulator-client.toml";
    private static final String LEGACY_SERVER_FILE = "molecularmanipulator-server.toml";
    private static final String PREVIOUS_SERVER_FILE =
            "omnisequence-transfinite-server.toml";
    private static final String RETIRED_UNSCALED_DISPATCH_LIMIT =
            "omni_unscaled_dispatch_attempts_per_tick";
    private static final String RETIRED_BATCH_SUBSTITUTION_OPTION =
            "omni_batch_allow_substitution_patterns";
    private static final Map<String, String> FLAT_COMMON_OPTION_PATHS =
            createFlatCommonOptionPaths();
    private static final Map<String, String> FLAT_CLIENT_OPTION_PATHS = Map.of(
            "matter_sequence_tooltip_mode",
            "display.matter_sequence_tooltip_mode",
            "dynamic_effect_level",
            "display.dynamic_effect_level");

    private ConfigFileMigration() {
    }

    public static void migrateGlobalConfigs() {
        var configDirectory = FMLPaths.CONFIGDIR.get();
        migrateFile(configDirectory, LEGACY_CLIENT_FILE, CLIENT_FILE);
        migrateFile(configDirectory, PREVIOUS_SERVER_FILE, COMMON_FILE);
        migrateFile(configDirectory, LEGACY_SERVER_FILE, COMMON_FILE);

        if (!Files.isRegularFile(configDirectory.resolve(COMMON_FILE))) {
            var defaults = FMLPaths.GAMEDIR.get().resolve("defaultconfigs");
            migrateFile(defaults, PREVIOUS_SERVER_FILE,
                    configDirectory, COMMON_FILE);
            migrateFile(defaults, LEGACY_SERVER_FILE,
                    configDirectory, COMMON_FILE);
        }

        var commonFile = configDirectory.resolve(COMMON_FILE);
        ConfigSchemaGuard.migrateOptionPaths(
                commonFile, ModConfig.COMMON_SPEC,
                FLAT_COMMON_OPTION_PATHS, "common/global");
        ConfigSchemaGuard.migrateOptionPaths(
                configDirectory.resolve(CLIENT_FILE), ModConfig.CLIENT_SPEC,
                FLAT_CLIENT_OPTION_PATHS, "client");
        removeRetiredCommonOptions(configDirectory);
    }

    public static void refreshGlobalConfigSchemas(
            ForgeConfigSpec commonSpec, ForgeConfigSpec clientSpec) {
        var directory = FMLPaths.CONFIGDIR.get();
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(COMMON_FILE), commonSpec, "common/global");
        ConfigSchemaGuard.regenerateFileIfOutdated(
                directory.resolve(CLIENT_FILE), clientSpec, "client");
    }

    private static void removeRetiredCommonOptions(Path directory) {
        ConfigSchemaGuard.removeObsoleteOption(
                directory.resolve(COMMON_FILE),
                RETIRED_UNSCALED_DISPATCH_LIMIT,
                "common/global");
        ConfigSchemaGuard.removeObsoleteOption(
                directory.resolve(COMMON_FILE),
                RETIRED_BATCH_SUBSTITUTION_OPTION,
                "common/global");
    }

    static void migrateFile(Path directory, String legacyFileName, String fileName) {
        migrateFile(directory, legacyFileName, directory, fileName);
    }

    static Map<String, String> flatCommonOptionPaths() {
        return FLAT_COMMON_OPTION_PATHS;
    }

    static Map<String, String> flatClientOptionPaths() {
        return FLAT_CLIENT_OPTION_PATHS;
    }

    private static void migrateFile(
            Path sourceDirectory, String sourceFileName,
            Path targetDirectory, String targetFileName) {
        var legacyFile = sourceDirectory.resolve(sourceFileName);
        var file = targetDirectory.resolve(targetFileName);
        if (Files.exists(file) || !Files.isRegularFile(legacyFile)) {
            return;
        }

        try {
            Files.createDirectories(targetDirectory);
            Files.copy(legacyFile, file);
            MolecularManipulator.LOGGER.info("Migrated config file {} to {}", legacyFile, file);
        } catch (FileAlreadyExistsException ignored) {
            // Another startup path completed the same non-overwriting migration.
        } catch (IOException exception) {
            MolecularManipulator.LOGGER.warn("Could not migrate config file {} to {}",
                    legacyFile, file, exception);
        }
    }

    private static Map<String, String> createFlatCommonOptionPaths() {
        var paths = new LinkedHashMap<String, String>();
        paths.put("pattern_pages", "general.pattern_pages");
        paths.put("build_blocks_per_tick", "general.build_blocks_per_tick");
        paths.put("idle_power", "general.idle_power");
        paths.put("matter_sequence_capacity", "matter.storage.sequence_capacity");
        paths.put("matter_entropy_capacity", "matter.storage.entropy_capacity");
        paths.put("matter_entropy_cooling_per_second",
                "matter.entropy.cooling_per_second");
        for (int cards = 0; cards <= 4; cards++) {
            var oldPrefix = "matter_speed_card_" + cards + "_";
            var newPrefix = "matter.speed_cards.card_" + cards + ".";
            paths.put(oldPrefix + "parallel", newPrefix + "parallel_operations");
            paths.put(oldPrefix + "cycle_ticks", newPrefix + "cycle_ticks");
            paths.put(oldPrefix + "cooling_multiplier",
                    newPrefix + "cooling_multiplier");
        }
        paths.put("max_crafting_order_amount", "crafting.order.max_amount");
        paths.put("omni_max_fast_mode", "crafting.max_fast.mode");
        paths.put("omni_max_fast_max_nodes", "crafting.max_fast.max_nodes");
        paths.put("omni_max_fast_compile_budget_ms",
                "crafting.max_fast.compile_budget_ms");
        paths.put("omni_max_fast_diagnostics", "crafting.max_fast.diagnostics");
        paths.put("omni_batch_dispatch_enabled",
                "crafting.batch_dispatch.enabled");
        paths.put("omni_dispatch_max_work_units",
                "crafting.batch_dispatch.max_work_units");
        paths.put("omni_compat_dispatch_max_calls_per_tick",
                "crafting.compatibility_dispatch.max_calls_per_tick");
        paths.put("omni_compat_dispatch_max_time_us",
                "crafting.compatibility_dispatch.max_time_us");
        return Map.copyOf(paths);
    }
}
