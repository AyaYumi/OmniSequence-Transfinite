package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastMode;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class ModConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec.IntValue PATTERN_PAGES;
    public static final ForgeConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ForgeConfigSpec.IntValue IDLE_POWER;
    public static final ForgeConfigSpec.LongValue MATTER_SEQUENCE_CAPACITY;
    public static final ForgeConfigSpec.LongValue MATTER_ENTROPY_CAPACITY;
    public static final ForgeConfigSpec.LongValue MATTER_ENTROPY_COOLING_PER_SECOND;
    public static final List<ForgeConfigSpec.IntValue> MATTER_SPEED_CARD_PARALLEL;
    public static final List<ForgeConfigSpec.IntValue> MATTER_SPEED_CARD_CYCLE_TICKS;
    public static final List<ForgeConfigSpec.LongValue> MATTER_SPEED_CARD_COOLING_MULTIPLIER;
    public static final ForgeConfigSpec.LongValue MAX_CRAFTING_ORDER_AMOUNT;
    public static final ForgeConfigSpec.EnumValue<OmniMaxFastMode> OMNI_MAX_FAST_MODE;
    public static final ForgeConfigSpec.IntValue OMNI_MAX_FAST_MAX_NODES;
    public static final ForgeConfigSpec.IntValue OMNI_MAX_FAST_COMPILE_BUDGET_MS;
    public static final ForgeConfigSpec.BooleanValue OMNI_MAX_FAST_DIAGNOSTICS;
    public static final ForgeConfigSpec.BooleanValue OMNI_BATCH_DISPATCH_ENABLED;
    public static final ForgeConfigSpec.IntValue OMNI_COMPAT_DISPATCH_MAX_CALLS_PER_TICK;
    public static final ForgeConfigSpec.IntValue OMNI_COMPAT_DISPATCH_MAX_TIME_US;
    public static final ForgeConfigSpec.LongValue OMNI_DISPATCH_MAX_WORK_UNITS;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.EnumValue<MatterSequenceTooltipMode>
            MATTER_SEQUENCE_TOOLTIP_MODE;
    public static final ForgeConfigSpec.IntValue DYNAMIC_EFFECT_LEVEL;

    static {
        var common = new ForgeConfigSpec.Builder();

        common.comment("General Molecular Center settings.")
                .translation("molecularmanipulator.configuration.category.general")
                .push("general");
        PATTERN_PAGES = common.comment("Number of pattern pages for Molecular Centers.")
                .translation("molecularmanipulator.configuration.pattern_pages")
                .defineInRange("pattern_pages", 200, 1, 1000);
        BUILD_BLOCKS_PER_TICK = common.comment("Maximum structure blocks placed or removed per tick.")
                .translation("molecularmanipulator.configuration.build_blocks_per_tick")
                .defineInRange("build_blocks_per_tick", 32, 1, 256);
        IDLE_POWER = common.comment("Molecular Center idle power usage in AE/t.")
                .translation("molecularmanipulator.configuration.idle_power")
                .defineInRange("idle_power", 128, 1, 4096);
        common.pop();

        common.comment("Matter sequence, entropy, and acceleration-card settings.")
                .translation("molecularmanipulator.configuration.category.matter")
                .push("matter");
        common.comment("Matter sequence and entropy storage capacities.")
                .translation("molecularmanipulator.configuration.category.matter.storage")
                .push("storage");
        MATTER_SEQUENCE_CAPACITY = common.comment(
                "Maximum stored amount for each Matter Sequence category. Arithmetic saturates at this value and Long.MAX_VALUE is supported.")
                .translation("molecularmanipulator.configuration.matter_sequence_capacity")
                .defineInRange("sequence_capacity", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
        MATTER_ENTROPY_CAPACITY = common.comment(
                "Maximum rewrite entropy stored by the Sequence Array.")
                .translation("molecularmanipulator.configuration.matter_entropy_capacity")
                .defineInRange("entropy_capacity", 1_000_000L, 1L, Long.MAX_VALUE);
        common.pop();

        common.comment("Entropy cooling settings.")
                .translation("molecularmanipulator.configuration.category.matter.entropy")
                .push("entropy");
        MATTER_ENTROPY_COOLING_PER_SECOND = common.comment(
                "Base entropy cooling per second before the installed acceleration-card multiplier.")
                .translation("molecularmanipulator.configuration.matter_entropy_cooling_per_second")
                .defineInRange("cooling_per_second", 25L, 1L, Long.MAX_VALUE);
        common.pop();

        int[] defaultParallel = { 1, 2, 4, 16, 64 };
        int[] defaultCycleTicks = { 20, 10, 5, 2, 1 };
        long[] defaultCoolingMultiplier = { 1L, 2L, 4L, 16L, 64L };
        var speedCardParallel = new ArrayList<ForgeConfigSpec.IntValue>(5);
        var speedCardCycleTicks = new ArrayList<ForgeConfigSpec.IntValue>(5);
        var speedCardCoolingMultiplier = new ArrayList<ForgeConfigSpec.LongValue>(5);
        common.comment("Per-tier acceleration-card processing settings.")
                .translation("molecularmanipulator.configuration.category.matter.speed_cards")
                .push("speed_cards");
        for (int cards = 0; cards <= 4; cards++) {
            common.comment("Settings used with exactly " + cards + " installed acceleration cards.")
                    .translation("molecularmanipulator.configuration.category.matter.speed_cards.card_"
                            + cards)
                    .push("card_" + cards);
            speedCardParallel.add(common.comment(
                    "Matter operations completed per batch with " + cards + " acceleration cards.")
                    .translation("molecularmanipulator.configuration.matter_speed_card_"
                            + cards + "_parallel")
                    .defineInRange("parallel_operations",
                            defaultParallel[cards], 1, 4096));
            speedCardCycleTicks.add(common.comment(
                    "Ticks between matter batches with " + cards + " acceleration cards.")
                    .translation("molecularmanipulator.configuration.matter_speed_card_"
                            + cards + "_cycle_ticks")
                    .defineInRange("cycle_ticks",
                            defaultCycleTicks[cards], 1, 1200));
            speedCardCoolingMultiplier.add(common.comment(
                    "Entropy cooling multiplier with " + cards + " acceleration cards.")
                    .translation("molecularmanipulator.configuration.matter_speed_card_"
                            + cards + "_cooling_multiplier")
                    .defineInRange("cooling_multiplier",
                            defaultCoolingMultiplier[cards], 1L, Long.MAX_VALUE));
            common.pop();
        }
        common.pop(2);
        MATTER_SPEED_CARD_PARALLEL = List.copyOf(speedCardParallel);
        MATTER_SPEED_CARD_CYCLE_TICKS = List.copyOf(speedCardCycleTicks);
        MATTER_SPEED_CARD_COOLING_MULTIPLIER = List.copyOf(speedCardCoolingMultiplier);

        common.comment("AE2 crafting planning and dispatch settings.")
                .translation("molecularmanipulator.configuration.category.crafting")
                .push("crafting");
        common.comment("Crafting request size limits.")
                .translation("molecularmanipulator.configuration.category.crafting.order")
                .push("order");
        MAX_CRAFTING_ORDER_AMOUNT = common.comment(
                "Maximum amount allowed for a single AE2 autocrafting order. Values above Integer.MAX_VALUE use the mod's long-amount request path.")
                .translation("molecularmanipulator.configuration.max_crafting_order_amount")
                .defineInRange("max_amount", 1_000_000_000_000L, 1L, Long.MAX_VALUE);
        common.pop();

        common.comment("MAX_FAST crafting-plan optimizer settings.")
                .translation("molecularmanipulator.configuration.category.crafting.max_fast")
                .push("max_fast");
        OMNI_MAX_FAST_MODE = common.comment(
                "Omni-Computation Core crafting-plan optimizer. SAFE aggregates only deterministic recipes; every unsupported branch falls back to AE2. AGGRESSIVE is retained as a compatibility alias and obeys the same safety boundaries.")
                .translation("molecularmanipulator.configuration.omni_max_fast_mode")
                .defineEnum("mode", OmniMaxFastMode.SAFE);
        OMNI_MAX_FAST_MAX_NODES = common.comment(
                "Maximum unique recipe-tree nodes compiled by the Omni-Computation Core optimizer before falling back to AE2.")
                .translation("molecularmanipulator.configuration.omni_max_fast_max_nodes")
                .defineInRange("max_nodes", 8192, 64, 65536);
        OMNI_MAX_FAST_COMPILE_BUDGET_MS = common.comment(
                "Maximum graph compilation time in milliseconds before the Omni-Computation Core optimizer falls back to AE2.")
                .translation("molecularmanipulator.configuration.omni_max_fast_compile_budget_ms")
                .defineInRange("compile_budget_ms", 100, 1, 5000);
        OMNI_MAX_FAST_DIAGNOSTICS = common.comment(
                "Log Omni-Computation Core graph aggregation successes and fallback reasons.")
                .translation("molecularmanipulator.configuration.omni_max_fast_diagnostics")
                .define("diagnostics", false);
        common.pop();

        common.comment("Native multi-craft batch dispatch settings.")
                .translation("molecularmanipulator.configuration.category.crafting.batch_dispatch")
                .push("batch_dispatch");
        OMNI_BATCH_DISPATCH_ENABLED = common.comment(
                "Enable multi-craft material extraction and dispatch for explicitly compatible crafting providers.")
                .translation("molecularmanipulator.configuration.omni_batch_dispatch_enabled")
                .define("enabled", true);
        OMNI_DISPATCH_MAX_WORK_UNITS = common.comment(
                "Maximum dispatch work units per Omni controller and tick. Input extraction and each provider attempt cost one unit, regardless of logical batch size.")
                .translation("molecularmanipulator.configuration.omni_dispatch_max_work_units")
                .defineInRange("max_work_units", 2_147_483_647L, 64L, Long.MAX_VALUE);
        common.pop();

        common.comment("Fallback dispatch settings for providers without the batch API.")
                .translation("molecularmanipulator.configuration.category.crafting.compatibility_dispatch")
                .push("compatibility_dispatch");
        OMNI_COMPAT_DISPATCH_MAX_CALLS_PER_TICK = common.comment(
                "Hard safety ceiling for complete one-recipe provider calls shared by one Omni-Computation Core per tick. The adaptive time budget normally stops dispatch much earlier.")
                .translation("molecularmanipulator.configuration.omni_compat_dispatch_max_calls_per_tick")
                .defineInRange(
                        "max_calls_per_tick",
                        Integer.MAX_VALUE, 256, Integer.MAX_VALUE);
        OMNI_COMPAT_DISPATCH_MAX_TIME_US = common.comment(
                "Maximum server-wide main-thread time in microseconds used by compatibility one-recipe dispatch each tick. All active Omni cores share one deadline, which shrinks automatically as average server MSPT approaches 45.")
                .translation("molecularmanipulator.configuration.omni_compat_dispatch_max_time_us")
                .defineInRange(
                        "max_time_us",
                        20_000, 250, 50_000);
        common.pop(2);
        COMMON_SPEC = common.build();

        var client = new ForgeConfigSpec.Builder();
        client.comment("Client-side display and visual-effect settings.")
                .translation("molecularmanipulator.configuration.category.display")
                .push("display");
        MATTER_SEQUENCE_TOOLTIP_MODE = client.comment(
                "Matter Sequence item tooltip display mode. DISABLED turns it off, HOLD_SHIFT expands it while Shift is held, and ALWAYS_VISIBLE keeps it visible.")
                .translation(
                        "molecularmanipulator.configuration.matter_sequence_tooltip_mode")
                .defineEnum("matter_sequence_tooltip_mode",
                        MatterSequenceTooltipMode.HOLD_SHIFT);
        DYNAMIC_EFFECT_LEVEL = client.comment(
                "Dynamic multiblock effects: 0=off, 1=reduced, 2=full astral rings and quantum gate.")
                .translation("molecularmanipulator.configuration.dynamic_effect_level")
                .defineInRange("dynamic_effect_level", 2, 0, 2);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private ModConfig() {
    }

    public static int activePatternSlots() {
        return PATTERN_PAGES.get() * 36;
    }

    public static int matterParallelOperations(int installedSpeedCards) {
        return MATTER_SPEED_CARD_PARALLEL.get(speedCardIndex(installedSpeedCards)).get();
    }

    public static int matterCycleTicks(int installedSpeedCards) {
        return MATTER_SPEED_CARD_CYCLE_TICKS.get(speedCardIndex(installedSpeedCards)).get();
    }

    public static long matterCoolingMultiplier(int installedSpeedCards) {
        return MATTER_SPEED_CARD_COOLING_MULTIPLIER.get(
                speedCardIndex(installedSpeedCards)).get();
    }

    private static int speedCardIndex(int installedSpeedCards) {
        return Math.max(0, Math.min(4, installedSpeedCards));
    }

    public static void register() {
        ConfigFileMigration.migrateGlobalConfigs();
        ConfigFileMigration.refreshGlobalConfigSchemas(COMMON_SPEC, CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(Type.COMMON, COMMON_SPEC, ConfigFileMigration.COMMON_FILE);
        ModLoadingContext.get().registerConfig(Type.CLIENT, CLIENT_SPEC, ConfigFileMigration.CLIENT_FILE);
    }
}
