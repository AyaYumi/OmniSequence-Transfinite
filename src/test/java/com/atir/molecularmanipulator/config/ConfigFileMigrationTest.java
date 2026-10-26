package com.atir.molecularmanipulator.config;

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
                [sequence_array]
                build_blocks_per_tick = 7
                """);
        Files.writeString(file, input);

        var cleanup = ConfigFileMigration.class.getDeclaredMethod(
                "removeRetiredServerOptions", Path.class, String.class);
        cleanup.setAccessible(true);
        cleanup.invoke(null, directory, "test");
        String cleaned = Files.readString(file);
        for (String option : retired) assertFalse(cleaned.contains(option), option);
        for (String section : new String[]{"ae2_crafting", "optimizer", "cache", "execution"}) {
            assertFalse(cleaned.contains(section), section);
        }
        assertTrue(cleaned.contains("omni_dispatch_max_work_units = 64"));
        assertTrue(cleaned.contains("omni_batch_dispatch_enabled = false"));
        assertTrue(cleaned.contains("build_blocks_per_tick = 7"));
        cleanup.invoke(null, directory, "test");
        assertEquals(cleaned, Files.readString(file), "Cleanup must be idempotent");
    }
}
