package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastMode;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue PATTERN_PAGES;
    public static final ModConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue IDLE_POWER;
    public static final ModConfigSpec.LongValue MAX_CRAFTING_ORDER_AMOUNT;
    public static final ModConfigSpec.EnumValue<OmniMaxFastMode> OMNI_MAX_FAST_MODE;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_MAX_NODES;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_COMPILE_BUDGET_MS;
    public static final ModConfigSpec.BooleanValue OMNI_MAX_FAST_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue OMNI_BATCH_DISPATCH_ENABLED;
    public static final ModConfigSpec.BooleanValue OMNI_BATCH_ALLOW_SUBSTITUTION_PATTERNS;
    public static final ModConfigSpec.IntValue OMNI_DISPATCH_TARGET_BUDGET_MS;
    public static final ModConfigSpec.IntValue OMNI_DISPATCH_HARD_BUDGET_MS;
    public static final ModConfigSpec.LongValue OMNI_DISPATCH_MAX_WORK_UNITS;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.IntValue DYNAMIC_EFFECT_LEVEL;

    static {
        var server = new ModConfigSpec.Builder();
        PATTERN_PAGES = server.comment("Number of pattern pages for Molecular Centers.")
                .translation("molecularmanipulator.configuration.pattern_pages")
                .defineInRange("pattern_pages", 20, 1, 1000);
        BUILD_BLOCKS_PER_TICK = server.comment("Maximum structure blocks placed or removed per tick.")
                .translation("molecularmanipulator.configuration.build_blocks_per_tick")
                .defineInRange("build_blocks_per_tick", 32, 1, 256);
        IDLE_POWER = server.comment("Molecular Center idle power usage in AE/t.")
                .translation("molecularmanipulator.configuration.idle_power")
                .defineInRange("idle_power", 128, 1, 4096);
        MAX_CRAFTING_ORDER_AMOUNT = server.comment(
                "Maximum amount allowed for a single AE2 autocrafting order. Values above Integer.MAX_VALUE use the mod's long-amount request path.")
                .translation("molecularmanipulator.configuration.max_crafting_order_amount")
                .defineInRange("max_crafting_order_amount", 1_000_000_000_000L, 1L, Long.MAX_VALUE);
        OMNI_MAX_FAST_MODE = server.comment(
                "Omni-Computation Core crafting-plan optimizer. SAFE aggregates only deterministic recipes; every unsupported branch falls back to AE2.")
                .translation("molecularmanipulator.configuration.omni_max_fast_mode")
                .defineEnum("omni_max_fast_mode", OmniMaxFastMode.SAFE);
        OMNI_MAX_FAST_MAX_NODES = server.comment(
                "Maximum unique recipe-tree nodes compiled by the Omni-Computation Core optimizer before falling back to AE2.")
                .translation("molecularmanipulator.configuration.omni_max_fast_max_nodes")
                .defineInRange("omni_max_fast_max_nodes", 8192, 64, 65536);
        OMNI_MAX_FAST_COMPILE_BUDGET_MS = server.comment(
                "Maximum graph compilation time in milliseconds before the Omni-Computation Core optimizer falls back to AE2.")
                .translation("molecularmanipulator.configuration.omni_max_fast_compile_budget_ms")
                .defineInRange("omni_max_fast_compile_budget_ms", 100, 1, 5000);
        OMNI_MAX_FAST_DIAGNOSTICS = server.comment(
                "Log Omni-Computation Core graph aggregation successes and fallback reasons.")
                .translation("molecularmanipulator.configuration.omni_max_fast_diagnostics")
                .define("omni_max_fast_diagnostics", false);
        OMNI_BATCH_DISPATCH_ENABLED = server.comment(
                "Enable multi-craft material extraction and dispatch for explicitly compatible crafting providers.")
                .translation("molecularmanipulator.configuration.omni_batch_dispatch_enabled")
                .define("omni_batch_dispatch_enabled", true);
        OMNI_BATCH_ALLOW_SUBSTITUTION_PATTERNS = server.comment(
                "Allow item-substitution patterns to use batch dispatch. Fluid-only substitution remains deterministic and is allowed by default. Disabled by default for contextual and NBT-sensitive item matching.")
                .translation("molecularmanipulator.configuration.omni_batch_allow_substitution_patterns")
                .define("omni_batch_allow_substitution_patterns", false);
        OMNI_DISPATCH_TARGET_BUDGET_MS = server.comment(
                "Target Omni crafting dispatch time per controller and server tick. The adaptive work-unit budget uses this value; logical batch size is not capped.")
                .translation("molecularmanipulator.configuration.omni_dispatch_target_budget_ms")
                .defineInRange("omni_dispatch_target_budget_ms", 16, 1, 20);
        OMNI_DISPATCH_HARD_BUDGET_MS = server.comment(
                "Emergency wall-clock limit shared by every Omni controller on the server during one tick. Work resumes on the next tick.")
                .translation("molecularmanipulator.configuration.omni_dispatch_hard_budget_ms")
                .defineInRange("omni_dispatch_hard_budget_ms", 40, 1, 50);
        OMNI_DISPATCH_MAX_WORK_UNITS = server.comment(
                "Maximum adaptive dispatch work units per Omni controller and tick. Input extraction and each provider attempt cost one unit, regardless of logical batch size.")
                .translation("molecularmanipulator.configuration.omni_dispatch_max_work_units")
                .defineInRange("omni_dispatch_max_work_units", 2_147_483_647L, 64L, Long.MAX_VALUE);
        SERVER_SPEC = server.build();

        var client = new ModConfigSpec.Builder();
        DYNAMIC_EFFECT_LEVEL = client.comment(
                "Dynamic multiblock effects: 0=off, 1=reduced, 2=full astral rings and quantum gate.")
                .translation("molecularmanipulator.configuration.dynamic_effect_level")
                .defineInRange("dynamic_effect_level", 2, 0, 2);
        CLIENT_SPEC = client.build();
    }

    private ModConfig() {
    }

    public static int activePatternSlots() {
        return PATTERN_PAGES.get() * 36;
    }

    public static void register(ModContainer container) {
        ConfigFileMigration.migrateGlobalConfigs();
        container.registerConfig(Type.SERVER, SERVER_SPEC, ConfigFileMigration.SERVER_FILE);
        container.registerConfig(Type.CLIENT, CLIENT_SPEC, ConfigFileMigration.CLIENT_FILE);
    }
}
