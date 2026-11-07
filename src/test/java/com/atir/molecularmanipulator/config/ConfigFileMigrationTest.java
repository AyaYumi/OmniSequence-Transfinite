package com.atir.molecularmanipulator.config;

import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFileMigrationTest {
    @TempDir
    Path directory;

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
                omni_batch_dispatch_enabled = false
                omni_compat_dispatch_max_calls_per_tick = 4096
                omni_dispatch_max_work_units = 1234
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
        assertEquals(List.of("#minecraft:planks"), config.get("singularity_hub.singularity_collection.item_tags"));
        assertEquals(List.of("minecraft:oak_planks"), config.get("singularity_hub.singularity_collection.item_blacklist"));
        assertEquals(Boolean.FALSE, config.get("omni_computation.dispatch.omni_batch_dispatch_enabled"));
        assertEquals(4096, config.getInt("omni_computation.dispatch.omni_compat_dispatch_max_calls_per_tick"));
        assertEquals(1234, config.getInt("omni_computation.dispatch.omni_dispatch_max_work_units"));
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
        assertEquals(List.of("#minecraft:planks"), config.get("singularity_hub.singularity_collection.item_tags"));
        assertFalse(config.contains("sequence_array.singularity_collection"));
        assertFalse(config.contains("omni_computation.dispatch.omni_profile_exact_returns"));
        assertFalse(config.contains("omni_computation.dispatch.omni_return_profile_sample_interval"));

        String migrated = Files.readString(file);
        refreshDirectory();
        assertEquals(migrated, Files.readString(file), "Categorized legacy collection values must also survive the next startup");
    }

    private void refreshDirectory() throws Exception {
        // Exercise migration -> retirement -> schema-check in a disposable directory;
        // never read or write the active instance's config/defaultconfigs directories.
        var method = ConfigFileMigration.class.getDeclaredMethod("refreshDirectory",
                Path.class, ForgeConfigSpec.class, ForgeConfigSpec.class);
        method.setAccessible(true);
        method.invoke(null, directory, ModConfig.SERVER_SPEC, ModConfig.CLIENT_SPEC);
    }
}
