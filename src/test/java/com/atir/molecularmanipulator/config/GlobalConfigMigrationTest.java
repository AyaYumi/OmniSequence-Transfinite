package com.atir.molecularmanipulator.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GlobalConfigMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void everyLegacyFlatOptionMapsToExactlyOneCurrentGroupedOption() {
        var mappings = ConfigFileMigration.flatCommonOptionPaths();
        assertEquals(30, mappings.size());
        assertEquals(optionPaths(ModConfig.COMMON_SPEC),
                Set.copyOf(mappings.values()));

        var clientMappings = ConfigFileMigration.flatClientOptionPaths();
        assertEquals(2, clientMappings.size());
        assertEquals(optionPaths(ModConfig.CLIENT_SPEC),
                Set.copyOf(clientMappings.values()));
    }

    @Test
    void flatValuesArePreservedWhenMigratedToGroupedPaths() throws Exception {
        var file = temporaryDirectory.resolve(ConfigFileMigration.COMMON_FILE);
        Files.writeString(file, """
                pattern_pages = 37
                matter_sequence_capacity = 9000000000000000000
                matter_speed_card_4_parallel = 321
                omni_max_fast_mode = "OFF"
                omni_compat_dispatch_max_time_us = 12345
                """);

        assertTrue(ConfigSchemaGuard.migrateOptionPaths(
                file, ModConfig.COMMON_SPEC,
                ConfigFileMigration.flatCommonOptionPaths(), "test"));

        var migrated = readToml(file);
        assertEquals(37,
                ((Number) migrated.getRaw("general.pattern_pages")).intValue());
        assertEquals(9_000_000_000_000_000_000L,
                ((Number) migrated.getRaw(
                        "matter.storage.sequence_capacity")).longValue());
        assertEquals(321,
                ((Number) migrated.getRaw(
                        "matter.speed_cards.card_4.parallel_operations")).intValue());
        assertEquals("OFF", migrated.getRaw("crafting.max_fast.mode"));
        assertEquals(12_345,
                ((Number) migrated.getRaw(
                        "crafting.compatibility_dispatch.max_time_us")).intValue());
        assertFalse(migrated.contains("pattern_pages"));
        assertFalse(migrated.contains("omni_max_fast_mode"));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve(
                "omnisequence-transfinite-common-1.toml.bak")));
    }

    private static CommentedConfig readToml(Path file) throws Exception {
        var config = CommentedConfig.of(LinkedHashMap::new,
                TomlFormat.instance());
        try (var reader = Files.newBufferedReader(file)) {
            new TomlParser().parse(reader, config, ParsingMode.REPLACE);
        }
        return config;
    }

    private static Set<String> optionPaths(ForgeConfigSpec spec) {
        var result = new HashSet<String>();
        collectOptionPaths(spec.getSpec(), "", result);
        return Set.copyOf(result);
    }

    private static void collectOptionPaths(UnmodifiableConfig config,
            String parent, Set<String> destination) {
        for (var entry : config.entrySet()) {
            String path = parent.isEmpty()
                    ? entry.getKey()
                    : parent + "." + entry.getKey();
            Object value = entry.getRawValue();
            if (value instanceof UnmodifiableConfig section) {
                collectOptionPaths(section, path, destination);
            } else if (value instanceof ForgeConfigSpec.ValueSpec) {
                destination.add(path);
            }
        }
    }
}
