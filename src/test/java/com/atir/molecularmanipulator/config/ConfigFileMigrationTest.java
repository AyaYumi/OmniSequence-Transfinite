package com.atir.molecularmanipulator.config;

import com.electronwill.nightconfig.toml.TomlParser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest
    @MethodSource("explicitCollectorTagCases")
    void explicitCollectorTagsOverrideMalformedLegacyValues(String itemTags, String legacyTags) throws Exception {
        var file = directory.resolve(ConfigFileMigration.SERVER_FILE);
        String original = """
                [sequence_array]
                build_blocks_per_tick = 7
                [singularity_hub.singularity_collection]
                item_tags = %s
                %s
                batch_size = 37
                interval_ticks = 7
                item_blacklist = ["minecraft:oak_planks"]
                [singularity_hub.singularity_duplication]
                interval_ticks = 53
                [omni_computation.dispatch]
                omni_dispatch_max_work_units = 64
                """.formatted(itemTags, legacyTags);
        Files.writeString(file, original);

        refreshDirectory();

        String migrated = Files.readString(file);
        var config = new TomlParser().parse(migrated);
        assertEquals(itemTags.equals("[]") ? List.of() : List.of("#minecraft:planks"),
                config.get("singularity_hub.singularity_collection.item_tags"));
        assertFalse(config.contains("singularity_hub.singularity_collection.raw_ore_tags"));
        assertFalse(config.contains("singularity_hub.singularity_collection.log_tags"));
        assertEquals(37, config.getInt("singularity_hub.singularity_collection.batch_size"));
        assertEquals(7, config.getInt("singularity_hub.singularity_collection.interval_ticks"));
        assertEquals(List.of("minecraft:oak_planks"), config.get("singularity_hub.singularity_collection.item_blacklist"));
        assertEquals(53, config.getInt("singularity_hub.singularity_duplication.interval_ticks"));
        assertEquals(7, config.getInt("sequence_array.build_blocks_per_tick"));
        assertEquals(64L, config.getLong("omni_computation.dispatch.omni_dispatch_max_work_units"));
        assertEquals(original, Files.readString(directory.resolve("omnisequence-transfinite-server-1.toml.bak")));

        refreshDirectory();
        assertEquals(migrated, Files.readString(file), "The cleaned file must not be rewritten on the next startup");
        assertFalse(Files.exists(directory.resolve("omnisequence-transfinite-server-2.toml.bak")),
                "Discarded legacy values must not trigger a whole-file schema reset");
    }

    private static Stream<Arguments> explicitCollectorTagCases() {
        return Stream.of("[]", "[\"#minecraft:planks\"]").flatMap(itemTags -> Stream.of(
                "raw_ore_tags = 1\nlog_tags = []",
                "raw_ore_tags = []\nlog_tags = [42]",
                "raw_ore_tags = []\nlog_tags = \"#minecraft:logs\"")
                .map(legacyTags -> Arguments.of(itemTags, legacyTags)));
    }

    @Test
    void relocatedMalformedCollectorTagsCannotOverrideAnExplicitCurrentList() throws Exception {
        var file = directory.resolve(ConfigFileMigration.SERVER_FILE);
        Files.writeString(file, """
                [sequence_array.singularity_collection]
                raw_ore_tags = 1
                log_tags = [false]
                batch_size = 37
                [singularity_hub.singularity_collection]
                item_tags = []
                """);

        refreshDirectory();

        var config = new TomlParser().parse(Files.readString(file));
        assertEquals(List.of(), config.get("singularity_hub.singularity_collection.item_tags"));
        assertEquals(37, config.getInt("singularity_hub.singularity_collection.batch_size"));
        assertFalse(config.contains("sequence_array.singularity_collection"));
        assertFalse(config.contains("singularity_hub.singularity_collection.raw_ore_tags"));
        assertFalse(config.contains("singularity_hub.singularity_collection.log_tags"));
    }

    @Test
    void missingDuplicationBlacklistDefaultsToEmptyWithoutResettingExistingSettings() throws Exception {
        var file = directory.resolve(ConfigFileMigration.SERVER_FILE);
        String original = """
                [singularity_hub.singularity_collection]
                item_blacklist = ["minecraft:oak_log"]
                batch_size = 37
                [singularity_hub.singularity_duplication]
                energy_priority = ["ae"]
                fe_per_unit = 1007
                interval_ticks = 53
                """;
        Files.writeString(file, original);

        refreshDirectory();

        assertEquals(original, Files.readString(file), "Adding an option must not reset an existing config");
        var config = new TomlParser().parse(Files.readString(file));
        ModConfig.SERVER_SPEC.correct(config);
        assertEquals(List.of(), config.get("singularity_hub.singularity_duplication.item_blacklist"));
        assertEquals(List.of("minecraft:oak_log"), config.get("singularity_hub.singularity_collection.item_blacklist"));
        assertEquals(37, config.getInt("singularity_hub.singularity_collection.batch_size"));
        assertEquals(List.of("ae"), config.get("singularity_hub.singularity_duplication.energy_priority"));
        assertEquals(1007, config.getInt("singularity_hub.singularity_duplication.fe_per_unit"));
        assertEquals(53, config.getInt("singularity_hub.singularity_duplication.interval_ticks"));
    }

    @Test
    void duplicationAndCollectionBlacklistsRetainIndependentValues() throws Exception {
        var file = directory.resolve(ConfigFileMigration.SERVER_FILE);
        String original = """
                [singularity_hub.singularity_collection]
                item_blacklist = ["minecraft:oak_log"]
                [singularity_hub.singularity_duplication]
                item_blacklist = [" minecraft:dragon_egg ", "optionalmod:restricted_item"]
                interval_ticks = 53
                """;
        Files.writeString(file, original);

        refreshDirectory();

        assertEquals(original, Files.readString(file), "The new duplication key belongs to the current schema");
        var config = new TomlParser().parse(Files.readString(file));
        ModConfig.SERVER_SPEC.correct(config);
        assertEquals(List.of(" minecraft:dragon_egg ", "optionalmod:restricted_item"),
                config.get("singularity_hub.singularity_duplication.item_blacklist"));
        assertEquals(List.of("minecraft:oak_log"), config.get("singularity_hub.singularity_collection.item_blacklist"));
        assertEquals(53, config.getInt("singularity_hub.singularity_duplication.interval_ticks"));
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
