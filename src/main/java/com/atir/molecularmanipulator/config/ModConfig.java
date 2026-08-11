package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastMode;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class ModConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue PATTERN_PAGES;
    public static final ModConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue IDLE_POWER;
    public static final ModConfigSpec.LongValue MATTER_SEQUENCE_CAPACITY;
    public static final ModConfigSpec.LongValue MATTER_ENTROPY_CAPACITY;
    public static final ModConfigSpec.LongValue MATTER_ENTROPY_COOLING_PER_SECOND;
    public static final List<ModConfigSpec.IntValue> MATTER_SPEED_CARD_PARALLEL;
    public static final List<ModConfigSpec.IntValue> MATTER_SPEED_CARD_CYCLE_TICKS;
    public static final List<ModConfigSpec.LongValue> MATTER_SPEED_CARD_COOLING_MULTIPLIER;
    public static final ModConfigSpec.LongValue MAX_CRAFTING_ORDER_AMOUNT;
    public static final ModConfigSpec.EnumValue<OmniMaxFastMode> OMNI_MAX_FAST_MODE;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_MAX_NODES;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_COMPILE_BUDGET_MS;
    public static final ModConfigSpec.BooleanValue OMNI_MAX_FAST_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue OMNI_MAX_FAST_GRAPH_CACHE_ENABLED;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_GRAPH_CACHE_SIZE;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_GRAPH_CACHE_TTL_MINUTES;
    public static final ModConfigSpec.BooleanValue OMNI_MAX_FAST_PARALLEL_EXECUTION_ENABLED;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_PARALLEL_THREAD_POOL_SIZE;
    public static final ModConfigSpec.BooleanValue OMNI_MAX_FAST_SMART_CANDIDATE_SELECTION;
    public static final ModConfigSpec.BooleanValue OMNI_MAX_FAST_PRECOMPILE_ENABLED;
    public static final ModConfigSpec.IntValue OMNI_MAX_FAST_PRECOMPILE_COMMON_ITEMS;
    public static final ModConfigSpec.BooleanValue OMNI_BATCH_DISPATCH_ENABLED;
    public static final ModConfigSpec.IntValue OMNI_COMPAT_DISPATCH_MAX_CALLS_PER_TICK;
    public static final ModConfigSpec.IntValue OMNI_COMPAT_DISPATCH_MAX_TIME_US;
    public static final ModConfigSpec.LongValue OMNI_DISPATCH_MAX_WORK_UNITS;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.EnumValue<MatterSequenceTooltipMode>
            MATTER_SEQUENCE_TOOLTIP_MODE;
    public static final ModConfigSpec.IntValue DYNAMIC_EFFECT_LEVEL;

    static {
        var server = new ModConfigSpec.Builder();
        server.comment(
                "Sequence Array controller, construction, power, and Matter Rewrite settings.",
                "构序阵列控制器、结构施工、耗电与物质重写设置。")
                .push("sequence_array");
        PATTERN_PAGES = server.comment("Number of pattern pages for Molecular Centers.")
                .translation("molecularmanipulator.configuration.pattern_pages")
                .defineInRange("pattern_pages", 20, 1, 1000);
        BUILD_BLOCKS_PER_TICK = server.comment("Maximum structure blocks placed or removed per tick.")
                .translation("molecularmanipulator.configuration.build_blocks_per_tick")
                .defineInRange("build_blocks_per_tick", 32, 1, 256);
        IDLE_POWER = server.comment("Molecular Center idle power usage in AE/t.")
                .translation("molecularmanipulator.configuration.idle_power")
                .defineInRange("idle_power", 128, 1, 4096);
        server.comment(
                "Matter Sequence storage, entropy, cooling, and processing speed.",
                "物质构序存储、熵值、散热与处理速度设置。")
                .push("matter_rewrite");
        MATTER_SEQUENCE_CAPACITY = server.comment(
                "Maximum amount stored independently for each Matter Sequence type. Range: 1 to Long.MAX_VALUE; default: Long.MAX_VALUE (9223372036854775807).",
                "每一种物质构序可独立存储的最大数量。范围：1～Long.MAX_VALUE；默认：Long.MAX_VALUE（9223372036854775807）。")
                .translation("molecularmanipulator.configuration.matter_sequence_capacity")
                .defineInRange("matter_sequence_capacity", Long.MAX_VALUE, 1L, Long.MAX_VALUE);
        MATTER_ENTROPY_CAPACITY = server.comment(
                "Maximum rewrite entropy held by a Molecular Center. Range: 1 to Long.MAX_VALUE; default: 1000000.",
                "Deconstruction entropy/item = max(1, saturated total sequence / 64); rewrite entropy/item = max(1, saturated total sequence / 16).",
                "构序阵列可容纳的最大熵值。范围：1～Long.MAX_VALUE；默认：1000000。",
                "分解单件熵=max(1, 四类产出饱和总和/64)；重写单件熵=max(1, 四类消耗饱和总和/16)。")
                .translation("molecularmanipulator.configuration.matter_entropy_capacity")
                .defineInRange("matter_entropy_capacity", 1_000_000L, 1L, Long.MAX_VALUE);
        MATTER_ENTROPY_COOLING_PER_SECOND = server.comment(
                "Base entropy removed per second before applying the installed speed-card cooling multiplier. Range: 1 to Long.MAX_VALUE; default: 25.",
                "Effective cooling/second = this value * the current card-count cooling multiplier; multiplication saturates at Long.MAX_VALUE.",
                "应用加速卡散热倍率前的基础每秒散热值。范围：1～Long.MAX_VALUE；默认：25。",
                "有效每秒散热=本值×当前加速卡张数对应的散热倍率；乘法超过 Long.MAX_VALUE 时按上限饱和。")
                .translation("molecularmanipulator.configuration.matter_entropy_cooling_per_second")
                .defineInRange("matter_entropy_cooling_per_second", 25L, 1L, Long.MAX_VALUE);
        int[] defaultParallel = {1, 2, 4, 16, 64};
        int[] defaultCycleTicks = {20, 10, 5, 2, 1};
        long[] defaultCoolingMultiplier = {1L, 2L, 4L, 16L, 64L};
        var speedCardParallel = new ArrayList<ModConfigSpec.IntValue>(5);
        var speedCardCycleTicks = new ArrayList<ModConfigSpec.IntValue>(5);
        var speedCardCoolingMultiplier = new ArrayList<ModConfigSpec.LongValue>(5);
        server.comment(
                "Per-card-count processing and entropy-cooling rules for zero through four installed AE2 speed cards.",
                "Parallel controls items processed per batch; cycle_ticks controls ticks between batches; cooling_multiplier accelerates entropy cooling.",
                "安装 0～4 张 AE2 加速卡时分别使用的处理与散热规则。",
                "parallel 为每批并行物品数；cycle_ticks 为批次间隔 tick；cooling_multiplier 为熵散热倍率。")
                .push("speed_cards");
        for (int cards = 0; cards <= 4; cards++) {
            speedCardParallel.add(server.comment(
                    "Maximum matter operations processed together with " + cards
                            + " installed speed card(s). Range: 1-4096; default: "
                            + defaultParallel[cards] + ".",
                    "安装 " + cards + " 张加速卡时每批最多并行处理的物品数。范围：1～4096；默认："
                            + defaultParallel[cards] + "。")
                    .translation("molecularmanipulator.configuration.matter_speed_card_"
                            + cards + "_parallel")
                    .defineInRange("card_" + cards + "_parallel",
                            defaultParallel[cards], 1, 4096));
            speedCardCycleTicks.add(server.comment(
                    "Ticks between matter-processing batches with " + cards
                            + " installed speed card(s). Range: 1-1200; default: "
                            + defaultCycleTicks[cards] + ".",
                    "安装 " + cards + " 张加速卡时两个处理批次之间的 tick 数。范围：1～1200；默认："
                            + defaultCycleTicks[cards] + "。")
                    .translation("molecularmanipulator.configuration.matter_speed_card_"
                            + cards + "_cycle_ticks")
                    .defineInRange("card_" + cards + "_cycle_ticks",
                            defaultCycleTicks[cards], 1, 1200));
            speedCardCoolingMultiplier.add(server.comment(
                    "Multiplier applied to matter_entropy_cooling_per_second with " + cards
                            + " installed speed card(s). Range: 1 to Long.MAX_VALUE; default: "
                            + defaultCoolingMultiplier[cards] + ".",
                    "安装 " + cards + " 张加速卡时应用到基础每秒散热值的倍率。范围：1～Long.MAX_VALUE；默认："
                            + defaultCoolingMultiplier[cards] + "。")
                    .translation("molecularmanipulator.configuration.matter_speed_card_"
                            + cards + "_cooling_multiplier")
                    .defineInRange("card_" + cards + "_cooling_multiplier",
                            defaultCoolingMultiplier[cards], 1L, Long.MAX_VALUE));
        }
        server.pop();
        MATTER_SPEED_CARD_PARALLEL = List.copyOf(speedCardParallel);
        MATTER_SPEED_CARD_CYCLE_TICKS = List.copyOf(speedCardCycleTicks);
        MATTER_SPEED_CARD_COOLING_MULTIPLIER = List.copyOf(speedCardCoolingMultiplier);
        server.pop();
        server.pop();

        server.comment(
                "General AE2 autocrafting request limits.",
                "AE2 自动合成请求的通用限制。")
                .push("ae2_crafting");
        MAX_CRAFTING_ORDER_AMOUNT = server.comment(
                "Maximum amount allowed for a single AE2 autocrafting order. Values above Integer.MAX_VALUE use the mod's long-amount request path.")
                .translation("molecularmanipulator.configuration.max_crafting_order_amount")
                .defineInRange("max_crafting_order_amount", 1_000_000_000_000L, 1L, Long.MAX_VALUE);
        server.pop();

        server.comment(
                "Omni-Computation Core optimizer, cache, execution, and dispatch settings.",
                "万物演算核心的优化器、缓存、执行与派发设置。")
                .push("omni_computation");
        server.comment(
                "Recipe graph compilation and optimizer safety limits.",
                "配方图编译与优化器安全限制。")
                .push("optimizer");
        OMNI_MAX_FAST_MODE = server.comment(
                "Omni-Computation Core crafting-plan optimizer. OFF disables optimization. SAFE (default) uses a whitelist of known-compatible pattern types. AGGRESSIVE attempts all pattern types, letting runtime checks catch incompatibilities (supports future mods without updates).")
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
        server.pop();

        server.comment(
                "Compiled recipe graph cache settings.",
                "已编译配方图缓存设置。")
                .push("cache");
        OMNI_MAX_FAST_GRAPH_CACHE_ENABLED = server.comment(
                "Enable compiled recipe graph caching for repeated crafting requests. Reduces compile time by 50-80% for identical crafts.")
                .translation("molecularmanipulator.configuration.omni_max_fast_graph_cache_enabled")
                .define("omni_max_fast_graph_cache_enabled", true);
        OMNI_MAX_FAST_GRAPH_CACHE_SIZE = server.comment(
                "Maximum cached recipe graphs. LRU eviction after limit. Each entry uses ~50-200KB depending on recipe complexity.")
                .translation("molecularmanipulator.configuration.omni_max_fast_graph_cache_size")
                .defineInRange("omni_max_fast_graph_cache_size", 256, 16, 2048);
        OMNI_MAX_FAST_GRAPH_CACHE_TTL_MINUTES = server.comment(
                "Maximum age in minutes for cached recipe graphs. Entries older than this are evicted even if cache size is below limit.")
                .translation("molecularmanipulator.configuration.omni_max_fast_graph_cache_ttl_minutes")
                .defineInRange("omni_max_fast_graph_cache_ttl_minutes", 5, 1, 60);
        server.pop();

        server.comment(
                "Parallel graph execution, candidate selection, and precompilation settings.",
                "并行配方图执行、候选样板选择与预编译设置。")
                .push("execution");
        OMNI_MAX_FAST_PARALLEL_EXECUTION_ENABLED = server.comment(
                "Enable parallel topological execution for independent recipe graph layers. Can speed up large crafts by 2-4x on multi-core systems.")
                .translation("molecularmanipulator.configuration.omni_max_fast_parallel_execution_enabled")
                .define("omni_max_fast_parallel_execution_enabled", true);
        OMNI_MAX_FAST_PARALLEL_THREAD_POOL_SIZE = server.comment(
                "Thread pool size for parallel execution. 0 = auto (CPU cores - 2). Higher values may improve large craft throughput.")
                .translation("molecularmanipulator.configuration.omni_max_fast_parallel_thread_pool_size")
                .defineInRange("omni_max_fast_parallel_thread_pool_size", 0, 0, 64);
        OMNI_MAX_FAST_SMART_CANDIDATE_SELECTION = server.comment(
                "Enable smart candidate pattern selection based on inventory availability. Reduces compilation failures by choosing patterns with available materials.")
                .translation("molecularmanipulator.configuration.omni_max_fast_smart_candidate_selection")
                .define("omni_max_fast_smart_candidate_selection", true);
        OMNI_MAX_FAST_PRECOMPILE_ENABLED = server.comment(
                "Enable background precompilation of common recipe graphs on world load and recipe reload. Reduces first-craft latency.")
                .translation("molecularmanipulator.configuration.omni_max_fast_precompile_enabled")
                .define("omni_max_fast_precompile_enabled", false);
        OMNI_MAX_FAST_PRECOMPILE_COMMON_ITEMS = server.comment(
                "Number of most-used items to precompile when precompilation is enabled. Higher values increase startup time but improve responsiveness.")
                .translation("molecularmanipulator.configuration.omni_max_fast_precompile_common_items")
                .defineInRange("omni_max_fast_precompile_common_items", 50, 10, 500);
        server.pop();

        server.comment(
                "Crafting-provider batch dispatch and main-thread work budgets.",
                "合成供应器批量派发与主线程工作预算。")
                .push("dispatch");
        OMNI_BATCH_DISPATCH_ENABLED = server.comment(
                "Enable multi-craft material extraction and dispatch for explicitly compatible crafting providers.")
                .translation("molecularmanipulator.configuration.omni_batch_dispatch_enabled")
                .define("omni_batch_dispatch_enabled", true);
        OMNI_COMPAT_DISPATCH_MAX_CALLS_PER_TICK = server.comment(
                "Hard safety ceiling for complete one-recipe provider calls shared by one Omni-Computation Core per tick. The adaptive time budget normally stops dispatch much earlier.")
                .translation("molecularmanipulator.configuration.omni_compat_dispatch_max_calls_per_tick")
                .defineInRange(
                        "omni_compat_dispatch_max_calls_per_tick",
                        Integer.MAX_VALUE, 256, Integer.MAX_VALUE);
        OMNI_COMPAT_DISPATCH_MAX_TIME_US = server.comment(
                "Maximum server-wide main-thread time in microseconds used by compatibility one-recipe dispatch each tick. All active Omni cores share one deadline, which shrinks automatically as average server MSPT approaches 45.")
                .translation("molecularmanipulator.configuration.omni_compat_dispatch_max_time_us")
                .defineInRange(
                        "omni_compat_dispatch_max_time_us",
                        20_000, 250, 50_000);
        OMNI_DISPATCH_MAX_WORK_UNITS = server.comment(
                "Maximum dispatch work units per Omni controller and tick. Input extraction and each provider attempt cost one unit, regardless of logical batch size.")
                .translation("molecularmanipulator.configuration.omni_dispatch_max_work_units")
                .defineInRange("omni_dispatch_max_work_units", 2_147_483_647L, 64L, Long.MAX_VALUE);
        server.pop();
        server.pop();
        SERVER_SPEC = server.build();

        var client = new ModConfigSpec.Builder();
        client.comment(
                "Item tooltip display settings.",
                "物品提示显示设置。")
                .push("tooltips");
        MATTER_SEQUENCE_TOOLTIP_MODE = client.comment(
                "Matter Sequence item tooltip display mode. DISABLED turns it off, HOLD_SHIFT expands it while Shift is held, and ALWAYS_VISIBLE keeps it visible.")
                .translation(
                        "molecularmanipulator.configuration.matter_sequence_tooltip_mode")
                .defineEnum("matter_sequence_tooltip_mode",
                        MatterSequenceTooltipMode.HOLD_SHIFT);
        client.pop();
        client.comment(
                "Client-side visual effect settings.",
                "客户端视觉效果设置。")
                .push("visual");
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

    public static long matterEntropyCoolingMultiplier(int installedSpeedCards) {
        return MATTER_SPEED_CARD_COOLING_MULTIPLIER.get(
                speedCardIndex(installedSpeedCards)).get();
    }

    private static int speedCardIndex(int installedSpeedCards) {
        return Math.max(0, Math.min(4, installedSpeedCards));
    }

    public static void register(ModContainer container) {
        ConfigFileMigration.migrateGlobalConfigs();
        ConfigFileMigration.refreshGlobalConfigSchemas(SERVER_SPEC, CLIENT_SPEC);
        container.registerConfig(Type.COMMON, SERVER_SPEC, ConfigFileMigration.SERVER_FILE);
        container.registerConfig(Type.CLIENT, CLIENT_SPEC, ConfigFileMigration.CLIENT_FILE);
    }
}
