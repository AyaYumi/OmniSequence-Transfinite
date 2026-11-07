package com.atir.molecularmanipulator.config;

import com.electronwill.nightconfig.toml.TomlParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigFileMigrationTest {
    @TempDir Path directory;

    @Test
    void retiredFlatAndCategorizedOptionsAreRemovedWithoutResettingDispatchValues() throws Exception {
        var file = directory.resolve(ConfigFileMigration.SERVER_FILE);
        String[] retired = {
                "max_crafting_order_amount", "omni_max_fast_mode", "omni_max_fast_max_nodes",
                "omni_max_fast_compile_budget_ms", "omni_max_fast_diagnostics",
                "omni_max_fast_graph_cache_enabled", "omni_max_fast_graph_cache_size",
                "omni_max_fast_graph_cache_ttl_minutes", "omni_max_fast_parallel_execution_enabled",
                "omni_max_fast_parallel_thread_pool_size", "omni_max_fast_smart_candidate_selection",
                "omni_max_fast_precompile_enabled", "omni_max_fast_precompile_common_items"
        };
        var input = new StringBuilder();
        for (String option : retired) input.append(option).append(" = 1\n");
        input.append("""
                [ae2_crafting]
                max_crafting_order_amount = 1
                [omni_computation.optimizer]
                omni_max_fast_diagnostics = true
                [omni_computation.cache]
                omni_max_fast_graph_cache_size = 12
                [omni_computation.execution]
                omni_max_fast_parallel_execution_enabled = true
                [omni_computation.dispatch]
                omni_dispatch_max_work_units = 64
                omni_batch_dispatch_enabled = false
                omni_profile_exact_returns = true
                omni_return_profile_sample_interval = 16
                [sequence_array]
                build_blocks_per_tick = 7
                [singularity_hub.singularity_collection]
                item_tags = ["#minecraft:planks"]
                batch_size = 37
                interval_ticks = 7
                """);
        Files.writeString(file, input);

        refreshDirectory();
        String cleaned = Files.readString(file);
        for (String option : retired) assertFalse(cleaned.contains(option), option);
        for (String section : new String[]{"ae2_crafting", "optimizer", "cache", "execution"}) {
            assertFalse(cleaned.contains(section), section);
        }
        assertTrue(cleaned.contains("omni_dispatch_max_work_units = 64"));
        assertTrue(cleaned.contains("omni_batch_dispatch_enabled = false"));
        assertTrue(cleaned.contains("build_blocks_per_tick = 7"));
        var config = new TomlParser().parse(cleaned);
        assertFalse(config.contains("omni_computation.dispatch.omni_profile_exact_returns"));
        assertFalse(config.contains("omni_computation.dispatch.omni_return_profile_sample_interval"));
        assertEquals(37, config.getInt("singularity_hub.singularity_collection.batch_size"));
        assertEquals(7, config.getInt("singularity_hub.singularity_collection.interval_ticks"));
        assertEquals(java.util.List.of("#minecraft:planks"), config.get("singularity_hub.singularity_collection.item_tags"));
        refreshDirectory();
        assertEquals(cleaned, Files.readString(file), "Cleanup and schema refresh must be idempotent");
        assertFalse(Files.exists(directory.resolve("omnisequence-transfinite-server-2.toml.bak")),
                "Combined flat/categorized retirement must not trigger a schema reset or a second backup");
    }

    @Test
    void retiredProfilingOptionsCannotResetCollectionSettings() throws Exception {
        String original = """
                [singularity_hub.singularity_collection]
                item_tags = ["#minecraft:planks"]
                item_blacklist = ["minecraft:oak_planks"]
                batch_size = 37
                interval_ticks = 7

                [singularity_hub.singularity_duplication]
                interval_ticks = 53

                [omni_computation.dispatch]
                omni_profile_exact_returns = false
                omni_return_profile_sample_interval = 64
                """;
        var file = directory.resolve(ConfigFileMigration.SERVER_FILE);
        Files.writeString(file, original);

        refreshDirectory();

        var config = new TomlParser().parse(Files.readString(file));
        assertEquals(37, config.getInt("singularity_hub.singularity_collection.batch_size"));
        assertEquals(7, config.getInt("singularity_hub.singularity_collection.interval_ticks"));
        assertEquals(53, config.getInt("singularity_hub.singularity_duplication.interval_ticks"));
        assertEquals(java.util.List.of("#minecraft:planks"), config.get("singularity_hub.singularity_collection.item_tags"));
        assertEquals(java.util.List.of("minecraft:oak_planks"), config.get("singularity_hub.singularity_collection.item_blacklist"));
        assertFalse(config.contains("omni_computation.dispatch.omni_profile_exact_returns"));
        assertFalse(config.contains("omni_computation.dispatch.omni_return_profile_sample_interval"));
        assertEquals(original, Files.readString(directory.resolve("omnisequence-transfinite-server-1.toml.bak")));

        String migrated = Files.readString(file);
        refreshDirectory();
        assertEquals(migrated, Files.readString(file), "Migration must not reset values or rewrite the file on the next startup");
        assertFalse(Files.exists(directory.resolve("omnisequence-transfinite-server-2.toml.bak")));
    }

    @Test
    void legacyCollectionPathsAndTagsRetainTheirValuesWithRetiredProfilingOptions() throws Exception {
        var file = directory.resolve(ConfigFileMigration.SERVER_FILE);
        Files.writeString(file, """
                [sequence_array.singularity_collection]
                raw_ore_tags = ["#minecraft:planks"]
                log_tags = []
                batch_size = 12345
                interval_ticks = 53

                [omni_computation.dispatch]
                omni_profile_exact_returns = true
                omni_return_profile_sample_interval = 16
                """);

        refreshDirectory();

        var config = new TomlParser().parse(Files.readString(file));
        assertEquals(12345, config.getInt("singularity_hub.singularity_collection.batch_size"));
        assertEquals(53, config.getInt("singularity_hub.singularity_collection.interval_ticks"));
        assertEquals(java.util.List.of("#minecraft:planks"), config.get("singularity_hub.singularity_collection.item_tags"));
        assertFalse(config.contains("sequence_array.singularity_collection"));
        assertFalse(config.contains("omni_computation.dispatch.omni_profile_exact_returns"));
        assertFalse(config.contains("omni_computation.dispatch.omni_return_profile_sample_interval"));
    }

    private void refreshDirectory() throws Exception {
        // Exercise the same retirement -> migration -> schema-check order used at startup,
        // but never read or write the active instance's config/defaultconfigs directories.
        var method = ConfigFileMigration.class.getDeclaredMethod("refreshDirectory",
                Path.class, net.neoforged.neoforge.common.ModConfigSpec.class,
                net.neoforged.neoforge.common.ModConfigSpec.class);
        method.setAccessible(true);
        method.invoke(null, directory, ModConfig.SERVER_SPEC, ModConfig.CLIENT_SPEC);
    }
}
