package com.atir.molecularmanipulator.config;

import com.atir.molecularmanipulator.blockentity.StarSeaCalculationProduction;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class ModConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec.IntValue PATTERN_PAGES;
    public static final ModConfigSpec.IntValue BUILD_BLOCKS_PER_TICK;
    public static final ModConfigSpec.IntValue IDLE_POWER;
    public static final ModConfigSpec.BooleanValue FORCE_LOAD_CHUNKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SINGULARITY_COLLECTION_ITEM_TAGS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SINGULARITY_COLLECTION_ITEM_BLACKLIST;
    public static final ModConfigSpec.IntValue SINGULARITY_COLLECTION_BATCH_SIZE;
    public static final ModConfigSpec.IntValue SINGULARITY_COLLECTION_INTERVAL_TICKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SINGULARITY_DUPLICATION_ITEM_BLACKLIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SINGULARITY_DUPLICATION_ENERGY_PRIORITY;
    public static final ModConfigSpec.IntValue SINGULARITY_DUPLICATION_FE_PER_UNIT;
    public static final ModConfigSpec.IntValue SINGULARITY_DUPLICATION_AE_PER_UNIT;
    public static final ModConfigSpec.IntValue SINGULARITY_DUPLICATION_MATTER_PER_BLACK_HOLE;
    public static final ModConfigSpec.IntValue SINGULARITY_DUPLICATION_INTERVAL_TICKS;
    public static final ModConfigSpec.BooleanValue OMNI_BATCH_DISPATCH_ENABLED;
    public static final ModConfigSpec.IntValue OMNI_COMPAT_DISPATCH_MAX_CALLS_PER_TICK;
    public static final ModConfigSpec.IntValue OMNI_COMPAT_DISPATCH_MAX_TIME_US;
    public static final ModConfigSpec.LongValue OMNI_DISPATCH_MAX_WORK_UNITS;
    public static final ModConfigSpec.BooleanValue OMNI_COALESCE_RETURN_NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue OMNI_DIRECT_NATIVE_OUTPUT_RETURN;
    public static final ModConfigSpec.IntValue NEXUS_IDLE_POWER;
    public static final ModConfigSpec.IntValue STAR_SEA_OUTPUT_BITS_PER_TICK;
    public static final ModConfigSpec.DoubleValue STAR_SEA_ENERGY_PER_TICK;
    public static final ModConfigSpec.IntValue STAR_SEA_DETACHED_SIGNAL_OUTPUT_MULTIPLIER;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.IntValue DYNAMIC_EFFECT_LEVEL;

    static {
        var server = new ModConfigSpec.Builder();
        server.comment(
                "Sequence Array controller, construction, and power settings.",
                "构序阵列控制器、结构施工与耗电设置。")
                .translation("molecularmanipulator.configuration.sequence_array")
                .push("sequence_array");
        PATTERN_PAGES = server.comment("Pattern pages per Sequence Array, with 36 slots per page.", "构序阵列的样板页数，每页 36 槽。")
                .translation("molecularmanipulator.configuration.pattern_pages")
                .defineInRange("pattern_pages", 200, 1, 300);
        BUILD_BLOCKS_PER_TICK = server.comment("Maximum Sequence Array structure blocks placed or removed per tick.", "构序阵列每 tick 最多安放或拆卸的结构方块数。")
                .translation("molecularmanipulator.configuration.build_blocks_per_tick")
                .defineInRange("build_blocks_per_tick", 32, 1, 256);
        IDLE_POWER = server.comment("Molecular Center idle power usage in AE/t.", "构序阵列控制器维持 ME 联网时的基础待机耗电（AE/t）。")
                .translation("molecularmanipulator.configuration.idle_power")
                .defineInRange("idle_power", 128, 1, 4096);
        server.pop();
        server.comment("Event Horizon Singularity Hub resource collection and matter duplication settings.",
                "视界奇点天枢的资源采集与物质复制设置。")
                .translation("molecularmanipulator.configuration.singularity_hub")
                .push("singularity_hub");
        server.comment(
                "Collection item tags, item-ID blacklist, per-item output and collection interval.",
                "采集物品标签、物品 ID 黑名单、单类产量和采集间隔。")
                .translation("molecularmanipulator.configuration.singularity_collection")
                .push("singularity_collection");
        SINGULARITY_COLLECTION_ITEM_TAGS = server.comment(
                "Select resources by item tag, with or without #. Tags are merged and deduplicated before the item blacklist is applied. An empty list stops collection.",
                "按物品标签设置采集范围。可带或不带 #；多个标签合并去重，再排除物品黑名单。留空 [] 停止采集。")
                .translation("molecularmanipulator.configuration.singularity_collection_item_tags")
                .defineListAllowEmpty("item_tags",
                        List.of("#c:raw_ores", "#c:raw_materials", "#minecraft:raw_ores", "#minecraft:logs", "#c:logs"),
                        () -> "#minecraft:logs", ModConfig::isValidTagEntry);
        SINGULARITY_COLLECTION_ITEM_BLACKLIST = server.comment(
                "Exact item IDs excluded from resource collection, even when their whitelist tags match. An empty list excludes nothing.",
                "资源采集物品 ID 黑名单，优先于物品标签白名单；留空 [] 不排除物品。",
                "Example / 示例：item_blacklist = [\"minecraft:raw_iron\", \"minecraft:oak_log\"]")
                .translation("molecularmanipulator.configuration.singularity_collection_item_blacklist")
                .defineListAllowEmpty("item_blacklist", List.<String>of(), () -> "minecraft:raw_iron",
                        ModConfig::isValidItemIdEntry);
        SINGULARITY_COLLECTION_BATCH_SIZE = server.comment(
                "Output per resource each collection cycle. Default: 1000.",
                "每次采集时，每种资源的产出数量。默认：1000。")
                .translation("molecularmanipulator.configuration.singularity_collection_batch_size")
                .defineInRange("batch_size", 1000, 1, 1_000_000);
        SINGULARITY_COLLECTION_INTERVAL_TICKS = server.comment(
                "Ticks between resource collection cycles. Changing this starts a fresh cycle. Default: 20.",
                "两次资源采集之间的 tick 数，修改后重新计时。默认：20。")
                .translation("molecularmanipulator.configuration.singularity_collection_interval_ticks")
                .defineInRange("interval_ticks", 20, 1, 1200);
        server.pop();
        server.comment(
                "Item duplication blacklist, sequence-matter production and black-hole energy settings.",
                "物品复制黑名单、奇点序质生产与黑洞能源设置。")
                .translation("molecularmanipulator.configuration.singularity_duplication")
                .push("singularity_duplication");
        SINGULARITY_DUPLICATION_ITEM_BLACKLIST = server.comment(
                "Exact item IDs that cannot be copied by either Singularity Hub, regardless of item components. An empty list blocks nothing. Does not affect collection or Sequence Matter generation.",
                "两种奇点天枢共用的复制物品 ID 黑名单，同一物品的所有组件变体均禁止复制；留空 [] 不限制。不影响资源采集或奇点序质生产。",
                "Default / 默认：item_blacklist = []")
                .translation("molecularmanipulator.configuration.singularity_duplication_item_blacklist")
                .defineListAllowEmpty("item_blacklist", List.<String>of(), () -> "minecraft:dragon_egg",
                        ModConfig::isValidItemIdEntry);
        SINGULARITY_DUPLICATION_ENERGY_PRIORITY = server.comment(
                "Use energy sources in list order. fe comes from adjacent blocks; ae comes from the connected ME network.",
                "按列表顺序使用能源。fe 来自相邻方块，ae 来自已连接的 ME 网络。")
                .translation("molecularmanipulator.configuration.singularity_duplication_energy_priority")
                .defineList("energy_priority", List.of("fe", "ae"), () -> "fe", ModConfig::isValidEnergyPriority);
        SINGULARITY_DUPLICATION_FE_PER_UNIT = server.comment(
                "FE consumed per millibucket of singularity sequence matter. Full-cycle FE cost = black-hole count * matter_per_black_hole * fe_per_unit.",
                "每生产 1 mB 奇点序质流体消耗的 FE；满速每次消耗 = 黑洞数量 × matter_per_black_hole × fe_per_unit。")
                .translation("molecularmanipulator.configuration.singularity_duplication_fe_per_unit")
                .defineInRange("fe_per_unit", 1000, 1, Integer.MAX_VALUE);
        SINGULARITY_DUPLICATION_AE_PER_UNIT = server.comment(
                "AE power consumed per millibucket of singularity sequence matter. Full-cycle AE cost = black-hole count * matter_per_black_hole * ae_per_unit.",
                "每生产 1 mB 奇点序质流体消耗的 AE；满速每次消耗 = 黑洞数量 × matter_per_black_hole × ae_per_unit。")
                .translation("molecularmanipulator.configuration.singularity_duplication_ae_per_unit")
                .defineInRange("ae_per_unit", 256, 1, Integer.MAX_VALUE);
        SINGULARITY_DUPLICATION_MATTER_PER_BLACK_HOLE = server.comment(
                "Sequence Matter generated per miniature black hole per cycle, in mB.",
                "每个微型黑洞每次的奇点序质产量（mB）。")
                .translation("molecularmanipulator.configuration.singularity_duplication_matter_per_black_hole")
                .defineInRange("matter_per_black_hole", 20, 1, 1000000);
        SINGULARITY_DUPLICATION_INTERVAL_TICKS = server.comment(
                "Ticks between sequence-matter production cycles. 20 ticks = 1 second; production never catches up with missed cycles.",
                "奇点序质生产间隔（tick）；20 tick = 1 秒，卸载或暂停期间不补发产物。")
                .translation("molecularmanipulator.configuration.singularity_duplication_interval_ticks")
                .defineInRange("interval_ticks", 20, 1, 1200);
        server.pop();
        server.pop();
        server.comment("Shared multiblock chunk-loading settings.", "所有多方块共用的区块加载设置。")
                .translation("molecularmanipulator.configuration.multiblocks")
                .push("multiblocks");
        FORCE_LOAD_CHUNKS = server.comment(
                "Keep multiblock chunks loaded while formed or under construction. When disabled, keep the machine and its motion area loaded yourself. Miniature white holes always keep their chunks loaded.",
                "多方块成型或施工时保持所占区块加载。关闭后需自行加载机器及运动范围所在区块。微型白洞始终保持加载。")
                .translation("molecularmanipulator.configuration.force_load_chunks")
                .define("force_load_chunks", true);
        server.pop();
        server.comment("Omni-Computation Core crafting dispatch and output-return settings.",
                "万物演算核心的合成派发与产物回收设置。")
                .translation("molecularmanipulator.configuration.omni_computation")
                .push("omni_computation");
        server.comment(
                "Crafting dispatch budgets, batch transfers and output-return settings.",
                "合成派发预算、批量推送和产物回收设置。")
                .translation("molecularmanipulator.configuration.dispatch")
                .push("dispatch");
        OMNI_BATCH_DISPATCH_ENABLED = server.comment(
                "Allow batch transfers of crafting materials. When disabled, transfer one craft at a time.",
                "允许批量推送合成材料。关闭后每次只推送一份。")
                .translation("molecularmanipulator.configuration.omni_batch_dispatch_enabled")
                .define("omni_batch_dispatch_enabled", true);
        OMNI_COMPAT_DISPATCH_MAX_CALLS_PER_TICK = server.comment(
                "Maximum single-craft dispatch calls per Omni-Computation Core per tick.",
                "每个万物演算核心每 tick 的单份配方推送次数上限。")
                .translation("molecularmanipulator.configuration.omni_compat_dispatch_max_calls_per_tick")
                .defineInRange(
                        "omni_compat_dispatch_max_calls_per_tick",
                        Integer.MAX_VALUE, 256, Integer.MAX_VALUE);
        OMNI_COMPAT_DISPATCH_MAX_TIME_US = server.comment(
                "Maximum single-craft dispatch time per tick in microseconds, shared by all active Omni-Computation Cores. The budget shrinks as average server MSPT approaches 45.",
                "所有活跃万物演算核心共享的每 tick 单份配方派发时间预算（微秒）；平均 MSPT 接近 45 时自动收缩。")
                .translation("molecularmanipulator.configuration.omni_compat_dispatch_max_time_us")
                .defineInRange(
                        "omni_compat_dispatch_max_time_us",
                        50_000, 250, 50_000);
        OMNI_DISPATCH_MAX_WORK_UNITS = server.comment(
                "Maximum dispatch work units per Omni controller and tick. Input extraction and each provider attempt cost one unit, regardless of logical batch size.",
                "单个万物演算核心每 tick 的派发工作预算；一次输入抽取和一次供应器尝试各计一个单元，与批次数量无关。")
                .translation("molecularmanipulator.configuration.omni_dispatch_max_work_units")
                .defineInRange("omni_dispatch_max_work_units", 2_147_483_647L, 64L, Long.MAX_VALUE);
        OMNI_COALESCE_RETURN_NOTIFICATIONS = server.comment(
                "Combine output-return notifications for each resource to reduce repeated updates.",
                "合并同种资源的产物回收通知，减少重复更新。")
                .translation("molecularmanipulator.configuration.omni_coalesce_return_notifications")
                .define("omni_coalesce_return_notifications", true);
        OMNI_DIRECT_NATIVE_OUTPUT_RETURN = server.comment(
                "Return UselessMod intermediate outputs directly to their bound CPU on the same network.",
                "将 UselessMod 中间产物直接返回同一网络中绑定的 CPU。")
                .translation("molecularmanipulator.configuration.omni_direct_native_output_return")
                .define("omni_direct_native_output_return", true);
        server.pop();
        server.pop();
        server.comment("Transfinite Compute Nexus power settings.", "超限算枢耗电设置。")
                .translation("molecularmanipulator.configuration.transfinite_compute_nexus")
                .push("transfinite_compute_nexus");
        NEXUS_IDLE_POWER = server.comment("Base idle power for the Transfinite Compute Nexus in AE/t.",
                "超限算枢的基础待机耗电（AE/t）。")
                .translation("molecularmanipulator.configuration.nexus_idle_power")
                .defineInRange("idle_power", 16_384, 1, Integer.MAX_VALUE);
        server.pop();
        server.comment("Star Sea Calculation Mainframe production and power settings.", "星海解算主楼的产出与耗能设置。")
                .translation("molecularmanipulator.configuration.star_sea_calculation")
                .push("star_sea_calculation");
        STAR_SEA_OUTPUT_BITS_PER_TICK = server.comment(
                "Base Star Signal output per operating local tick, in bits. Default: 128.",
                "每个有效本地工作刻的基础星讯产量（bit），默认 128。")
                .translation("molecularmanipulator.configuration.star_sea_output_bits_per_tick")
                .defineInRange("output_bits_per_tick", 128, 1, 1_000_000);
        STAR_SEA_ENERGY_PER_TICK = server.comment(
                "AE consumed per operating local tick. The dimension output bonus does not increase this cost. Set 0 for free operation. Default: 1024.",
                "每个有效本地工作刻的 AE 消耗，维度产出加成不增加此能耗；设为 0 可免耗能运行。默认 1024。")
                .translation("molecularmanipulator.configuration.star_sea_energy_per_tick")
                .defineInRange("energy_per_tick", 1024D, 0D, StarSeaCalculationProduction.MAX_ENERGY_PER_TICK);
        STAR_SEA_DETACHED_SIGNAL_OUTPUT_MULTIPLIER = server.comment(
                "Output multiplier in the Detached Signal Realm. Its half-speed/double-speed simulation applies separately. Default: 8.",
                "离讯之境中的产出倍率，默认 8；该维度的半速／双速时间另外影响每秒工作次数。")
                .translation("molecularmanipulator.configuration.star_sea_detached_signal_output_multiplier")
                .defineInRange("detached_signal_output_multiplier", 8, 1, 1_000_000);
        server.pop();
        SERVER_SPEC = server.build();

        var client = new ModConfigSpec.Builder();
        client.comment(
                "Client-side visual effect settings.",
                "客户端视觉效果设置。")
                .translation("molecularmanipulator.configuration.visual")
                .push("visual");
        DYNAMIC_EFFECT_LEVEL = client.comment(
                "Multiblock effects: 0=off, 1=reduced, 2=full.",
                "多方块特效等级：0 关闭，1 精简，2 完整。")
                .translation("molecularmanipulator.configuration.dynamic_effect_level")
                .defineInRange("dynamic_effect_level", 2, 0, 2);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private ModConfig() {
    }

    private static boolean isValidTagEntry(Object value) {
        if (!(value instanceof String text)) return false;
        text = text.trim();
        if (text.startsWith("#")) text = text.substring(1);
        return ResourceLocation.tryParse(text) != null;
    }

    private static boolean isValidItemIdEntry(Object value) {
        return value instanceof String text && text.trim().contains(":")
                && ResourceLocation.tryParse(text.trim()) != null;
    }

    private static boolean isValidEnergyPriority(Object value) {
        return value instanceof String text && (text.trim().equalsIgnoreCase("fe")
                || text.trim().equalsIgnoreCase("ae"));
    }

    public static int activePatternSlots() {
        return PATTERN_PAGES.get() * 36;
    }





    public static void register(ModContainer container) {
        ConfigFileMigration.migrateGlobalConfigs();
        ConfigFileMigration.refreshGlobalConfigSchemas(SERVER_SPEC, CLIENT_SPEC);
        container.registerConfig(Type.COMMON, SERVER_SPEC, ConfigFileMigration.SERVER_FILE);
        container.registerConfig(Type.CLIENT, CLIENT_SPEC, ConfigFileMigration.CLIENT_FILE);
    }
}
