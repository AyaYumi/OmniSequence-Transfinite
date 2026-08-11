package com.atir.molecularmanipulator.sequence;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class MatterSequenceRegistry {
    public static final String CONFIG_FILE_NAME = "matter_rewrite_rules.json";
    private static final long MAX_CONFIGURED_SEQUENCE = Long.MAX_VALUE;
    private static final Pattern MATCHER_PATTERN =
            Pattern.compile("#?[a-z0-9_.-]+:[a-z0-9/._-]+(?:\\*)?");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, MatterValue> DEFAULT_VALUES = Map.ofEntries(
            entry("minecraft:iron_nugget", 32, 0, 0, 0),
            entry("minecraft:iron_ingot", 288, 0, 0, 0),
            entry("minecraft:iron_block", 2592, 0, 0, 0),
            entry("minecraft:copper_ingot", 256, 0, 0, 0),
            entry("minecraft:copper_block", 2304, 0, 0, 0),
            entry("minecraft:gold_nugget", 64, 0, 0, 0),
            entry("minecraft:gold_ingot", 576, 0, 0, 0),
            entry("minecraft:gold_block", 5184, 0, 0, 0),
            entry("minecraft:netherite_scrap", 1024, 256, 256, 0),
            entry("minecraft:netherite_ingot", 6144, 1024, 1024, 0),
            entry("minecraft:coal", 0, 96, 0, 96),
            entry("minecraft:charcoal", 0, 32, 0, 160),
            entry("minecraft:redstone", 0, 32, 96, 0),
            entry("minecraft:lapis_lazuli", 0, 64, 384, 0),
            entry("minecraft:quartz", 0, 64, 256, 0),
            entry("minecraft:amethyst_shard", 0, 64, 384, 0),
            entry("minecraft:diamond", 0, 256, 4096, 0),
            entry("minecraft:emerald", 0, 256, 3072, 0),
            entry("minecraft:stone", 0, 80, 0, 0),
            entry("minecraft:cobblestone", 0, 64, 0, 0),
            entry("minecraft:deepslate", 0, 96, 0, 0),
            entry("minecraft:cobbled_deepslate", 0, 80, 0, 0),
            entry("minecraft:sand", 0, 48, 0, 0),
            entry("minecraft:glass", 0, 96, 32, 0),
            entry("minecraft:obsidian", 0, 768, 256, 0),
            entry("minecraft:oak_log", 0, 0, 0, 256),
            entry("minecraft:spruce_log", 0, 0, 0, 256),
            entry("minecraft:birch_log", 0, 0, 0, 256),
            entry("minecraft:jungle_log", 0, 0, 0, 256),
            entry("minecraft:acacia_log", 0, 0, 0, 256),
            entry("minecraft:dark_oak_log", 0, 0, 0, 256),
            entry("minecraft:mangrove_log", 0, 0, 0, 256),
            entry("minecraft:cherry_log", 0, 0, 0, 256),
            entry("minecraft:wheat", 0, 0, 0, 128),
            entry("minecraft:leather", 0, 0, 0, 256),
            entry("minecraft:slime_ball", 0, 0, 32, 192),
            entry("ae2:certus_quartz_crystal", 0, 64, 384, 0),
            entry("ae2:charged_certus_quartz_crystal", 0, 96, 576, 0),
            entry("ae2:fluix_crystal", 0, 96, 768, 0),
            entry("ae2:certus_quartz_dust", 0, 64, 320, 0),
            entry("ae2:fluix_dust", 0, 96, 640, 0),
            entry("ae2:sky_dust", 0, 192, 128, 0),
            entry("ae2:silicon", 0, 96, 192, 0),
            entry("ae2:quartz_block", 0, 576, 2304, 0),
            entry("ae2:quartz_vibrant_glass", 0, 384, 1024, 0),
            entry("ae2:fluix_block", 0, 864, 6912, 0),
            entry("molecularmanipulator:molecular_center_casing", 1024, 256, 512, 0),
            entry("molecularmanipulator:molecular_center_glass", 0, 256, 768, 0),
            entry("molecularmanipulator:molecular_center_coil", 512, 256, 1024, 0),
            entry("molecularmanipulator:molecular_center_stabilizer", 256, 256, 1024, 0));
    private static volatile Map<String, MatterRule> rules = defaultRules();

    private MatterSequenceRegistry() {
    }

    public static Path configPath() {
        return FMLPaths.CONFIGDIR.get()
                .resolve(MolecularManipulator.MOD_ID)
                .resolve(CONFIG_FILE_NAME);
    }

    public static synchronized void loadOrCreate() {
        var path = configPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                Files.writeString(path, createDefaultJson(), StandardCharsets.UTF_8);
                MolecularManipulator.LOGGER.info("Created matter rewrite configuration at {}", path);
            }
            try {
                ensureDocumentation(path);
            } catch (IOException exception) {
                MolecularManipulator.LOGGER.warn(
                        "Could not add entropy documentation to {}; rules will still be loaded",
                        path, exception);
            }
            rules = readRules(path);
            MolecularManipulator.LOGGER.info("Loaded {} matter rewrite rules from {}", rules.size(), path);
        } catch (Exception exception) {
            rules = defaultRules();
            MolecularManipulator.LOGGER.error(
                    "Could not load matter rewrite rules from {}; built-in defaults will be used",
                    path, exception);
        }
    }

    public static MatterValue deconstructionOf(ItemStack stack) {
        var rule = findRule(stack);
        return rule == null ? null : rule.deconstruct();
    }

    public static MatterValue rewriteCostOf(ItemStack stack) {
        var rule = findRule(stack);
        return rule == null ? null : rule.rewrite();
    }

    private static MatterRule findRule(ItemStack stack) {
        if (stack.isEmpty()
                || !ItemStack.isSameItemSameComponents(stack, stack.getItem().getDefaultInstance())) {
            return null;
        }
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var exact = rules.get(itemId);
        if (exact != null || rules.containsKey(itemId)) {
            return exact;
        }

        MatterRule best = null;
        int bestSpecificity = -1;
        var tags = stack.getTags().toList();
        for (var configured : rules.entrySet()) {
            String matcher = configured.getKey();
            if (!matcher.startsWith("#")) {
                continue;
            }
            String tagMatcher = matcher.substring(1);
            boolean wildcard = tagMatcher.endsWith("*");
            String expected = wildcard ? tagMatcher.substring(0, tagMatcher.length() - 1) : tagMatcher;
            for (var tag : tags) {
                String tagId = tag.location().toString();
                if ((wildcard && tagId.startsWith(expected)) || (!wildcard && tagId.equals(expected))) {
                    if (expected.length() > bestSpecificity) {
                        best = configured.getValue();
                        bestSpecificity = expected.length();
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static Map<String, MatterRule> readRules(Path path) throws IOException {
        JsonElement document;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            document = com.google.gson.JsonParser.parseReader(reader);
        }
        if (!document.isJsonObject()) {
            throw new IllegalArgumentException("Root must be a JSON object");
        }
        var configuredRules = document.getAsJsonObject().get("rules");
        if (configuredRules == null || !configuredRules.isJsonObject()) {
            throw new IllegalArgumentException("Missing object property 'rules'");
        }

        var result = new LinkedHashMap<String, MatterRule>();
        for (var configured : configuredRules.getAsJsonObject().entrySet()) {
            String matcher = configured.getKey();
            if (!MATCHER_PATTERN.matcher(matcher).matches()
                    || matcher.indexOf('*') >= 0 && !matcher.startsWith("#")) {
                MolecularManipulator.LOGGER.warn("Ignoring invalid matter rule matcher '{}'", matcher);
                continue;
            }
            if (!configured.getValue().isJsonObject()) {
                MolecularManipulator.LOGGER.warn("Ignoring matter rule '{}' because it is not an object", matcher);
                continue;
            }
            var object = configured.getValue().getAsJsonObject();
            var deconstruct = readValue(matcher, "deconstruct", object.get("deconstruct"));
            var rewrite = readValue(matcher, "rewrite", object.get("rewrite"));
            result.put(matcher, new MatterRule(deconstruct, rewrite));
        }
        return Collections.unmodifiableMap(result);
    }

    private static MatterValue readValue(String matcher, String operation, JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            MolecularManipulator.LOGGER.warn(
                    "Ignoring {} value for matter rule '{}' because it is not an object",
                    operation, matcher);
            return null;
        }
        var object = element.getAsJsonObject();
        try {
            var value = new MatterValue(
                    readAmount(object, "metal"),
                    readAmount(object, "mineral"),
                    readAmount(object, "crystal"),
                    readAmount(object, "organic"));
            return value.total() > 0 ? value : null;
        } catch (RuntimeException exception) {
            MolecularManipulator.LOGGER.warn(
                    "Ignoring invalid {} value for matter rule '{}': {}",
                    operation, matcher, exception.getMessage());
            return null;
        }
    }

    private static long readAmount(JsonObject object, String property) {
        var element = object.get(property);
        if (element == null) {
            return 0;
        }
        long value = element.getAsLong();
        if (value < 0 || value > MAX_CONFIGURED_SEQUENCE) {
            throw new IllegalArgumentException(
                    property + " must be between 0 and " + MAX_CONFIGURED_SEQUENCE);
        }
        return value;
    }

    private static String createDefaultJson() {
        var root = new JsonObject();
        root.addProperty("format", 4);
        var comments = new JsonArray();
        comments.add("Keys are item IDs. Keys beginning with # are item tags; a final * matches a tag prefix.");
        comments.add("deconstruct values are the exact sequence amounts produced per item.");
        comments.add("rewrite values are the exact sequence amounts consumed per copied item.");
        comments.add("Entropy per deconstructed item = max(1, floor(saturated sum of its four deconstruct values / 64)).");
        comments.add("Entropy per rewritten item = max(1, floor(saturated sum of its four rewrite values / 16)).");
        comments.add("An operation waits until current entropy + entropy per item is no greater than sequence_array.matter_rewrite.matter_entropy_capacity.");
        comments.add("Change Matter Rewrite limits under sequence_array.matter_rewrite in config/omnisequence-transfinite-server.toml.");
        comments.add("Installed speed-card rules under sequence_array.matter_rewrite.speed_cards configure parallel operations, batch ticks, and entropy cooling multipliers.");
        comments.add("Omit deconstruct or rewrite to disable that operation. An exact empty item rule overrides tag rules.");
        comments.add("Changes are loaded when a server or single-player world starts.");
        root.add("_comment", comments);
        var chineseComments = new JsonArray();
        chineseComments.add("普通键为物品ID；以#开头的是物品标签，标签末尾的*表示按前缀匹配。");
        chineseComments.add("deconstruct 是每件物品分解后实际产出的四类序列。");
        chineseComments.add("rewrite 是每复制一件物品实际消耗的四类序列。");
        chineseComments.add("每分解一件物品产生的熵 = max(1, 四类 deconstruct 数值饱和求和后 / 64 向下取整)。");
        chineseComments.add("每重写一件物品产生的熵 = max(1, 四类 rewrite 数值饱和求和后 / 16 向下取整)。");
        chineseComments.add("只有当前熵 + 单件熵不超过 sequence_array.matter_rewrite.matter_entropy_capacity 时，操作才会开始。");
        chineseComments.add("构序容量、熵上限和每秒散热速度位于 config/omnisequence-transfinite-server.toml 的 sequence_array.matter_rewrite 分类中。");
        chineseComments.add("服务器 TOML 的 sequence_array.matter_rewrite.speed_cards 分组可分别配置安装 0～4 张加速卡时的并行量、批次 tick 和熵散热倍率。");
        chineseComments.add("省略 deconstruct 或 rewrite 即单独禁用该操作；精确物品空规则可以覆盖标签规则。");
        chineseComments.add("修改后重新进入服务器或单人世界即可加载。");
        root.add("_comment_zh", chineseComments);
        addEntropyDocumentation(root);
        var jsonRules = new JsonObject();
        for (var entry : defaultRules().entrySet()) {
            var rule = new JsonObject();
            if (entry.getValue().deconstruct() != null) {
                rule.add("deconstruct", writeValue(entry.getValue().deconstruct()));
            }
            if (entry.getValue().rewrite() != null) {
                rule.add("rewrite", writeValue(entry.getValue().rewrite()));
            }
            jsonRules.add(entry.getKey(), rule);
        }
        root.add("rules", jsonRules);
        return GSON.toJson(root) + System.lineSeparator();
    }

    private static void ensureDocumentation(Path path) throws IOException {
        JsonElement document;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            document = com.google.gson.JsonParser.parseReader(reader);
        }
        if (!document.isJsonObject()) {
            return;
        }
        var root = document.getAsJsonObject();
        if (readFormat(root) >= 4
                && root.has("_entropy_calculation")
                && root.has("_entropy_calculation_zh")) {
            return;
        }
        root.addProperty("format", 4);
        addEntropyDocumentation(root);
        Files.writeString(path, GSON.toJson(root) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        MolecularManipulator.LOGGER.info(
                "Added entropy calculation and server option documentation to {}", path);
    }

    private static int readFormat(JsonObject root) {
        try {
            return root.has("format") ? root.get("format").getAsInt() : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static void addEntropyDocumentation(JsonObject root) {
        var entropy = new JsonObject();
        entropy.addProperty("deconstruct_entropy_per_item",
                "max(1, floor(saturated_sum(deconstruct.metal, mineral, crystal, organic) / 64))");
        entropy.addProperty("rewrite_entropy_per_item",
                "max(1, floor(saturated_sum(rewrite.metal, mineral, crystal, organic) / 16))");
        entropy.addProperty("operation_condition",
                "current_entropy + entropy_per_item <= sequence_array.matter_rewrite.matter_entropy_capacity");
        entropy.addProperty("cooling",
                "Every second, sequence_array.matter_rewrite.matter_entropy_cooling_per_second multiplied by the current speed_cards cooling multiplier is removed from current entropy (saturated at Long.MAX_VALUE).");
        entropy.addProperty("speed_cards",
                "sequence_array.matter_rewrite.speed_cards.card_0 through card_4 configure parallel operations, cycle ticks, and entropy cooling multipliers for each installed-card count.");
        entropy.addProperty("server_config",
                "config/omnisequence-transfinite-server.toml: sequence_array.matter_rewrite.*");
        root.add("_entropy_calculation", entropy);

        var entropyZh = new JsonObject();
        entropyZh.addProperty("分解单件熵值",
                "max(1, deconstruct 四类数值饱和求和后 / 64 向下取整)");
        entropyZh.addProperty("重写单件熵值",
                "max(1, rewrite 四类数值饱和求和后 / 16 向下取整)");
        entropyZh.addProperty("操作条件",
                "当前熵值 + 单件熵值 <= sequence_array.matter_rewrite.matter_entropy_capacity");
        entropyZh.addProperty("自然散热",
                "每秒从当前熵值中扣除 sequence_array.matter_rewrite.matter_entropy_cooling_per_second × 当前加速卡档位的散热倍率；乘法按 Long.MAX_VALUE 饱和");
        entropyZh.addProperty("加速卡档位",
                "sequence_array.matter_rewrite.speed_cards 的 card_0～card_4 分别配置对应加速卡张数的并行量、批次 tick 和熵散热倍率");
        entropyZh.addProperty("服务器配置",
                "config/omnisequence-transfinite-server.toml：sequence_array.matter_rewrite.*");
        root.add("_entropy_calculation_zh", entropyZh);
    }

    private static JsonObject writeValue(MatterValue value) {
        var object = new JsonObject();
        object.addProperty("metal", value.metal());
        object.addProperty("mineral", value.mineral());
        object.addProperty("crystal", value.crystal());
        object.addProperty("organic", value.organic());
        return object;
    }

    private static Map<String, MatterRule> defaultRules() {
        var defaults = new LinkedHashMap<String, MatterRule>();
        DEFAULT_VALUES.forEach((id, value) ->
                defaults.put(id, new MatterRule(recovered(value), value)));
        addTagDefault(defaults, "#c:nuggets/*", 32, 0, 0, 0);
        addTagDefault(defaults, "#c:ingots/*", 288, 0, 0, 0);
        addTagDefault(defaults, "#c:raw_materials/*", 192, 96, 0, 0);
        addTagDefault(defaults, "#c:ores/*", 192, 256, 0, 0);
        addTagDefault(defaults, "#c:gems/*", 0, 96, 512, 0);
        addTagDefault(defaults, "#c:crystals/*", 0, 96, 512, 0);
        addTagDefault(defaults, "#c:dusts/*", 0, 96, 192, 0);
        addTagDefault(defaults, "#c:storage_blocks/*", 2592, 0, 0, 0);
        addTagDefault(defaults, "#minecraft:logs", 0, 0, 0, 256);
        addTagDefault(defaults, "#minecraft:planks", 0, 0, 0, 64);
        addTagDefault(defaults, "#c:crops/*", 0, 0, 0, 96);
        addTagDefault(defaults, "#c:seeds/*", 0, 0, 0, 96);
        addTagDefault(defaults, "#c:stones/*", 0, 64, 0, 0);
        addTagDefault(defaults, "#c:cobblestones/*", 0, 64, 0, 0);
        addTagDefault(defaults, "#c:sands/*", 0, 64, 0, 0);
        return Collections.unmodifiableMap(defaults);
    }

    private static void addTagDefault(Map<String, MatterRule> defaults, String matcher,
            long metal, long mineral, long crystal, long organic) {
        var value = new MatterValue(metal, mineral, crystal, organic);
        defaults.put(matcher, new MatterRule(recovered(value), value));
    }

    private static MatterValue recovered(MatterValue value) {
        return new MatterValue(
                recoveredComponent(value.metal()),
                recoveredComponent(value.mineral()),
                recoveredComponent(value.crystal()),
                recoveredComponent(value.organic()));
    }

    private static long recoveredComponent(long value) {
        if (value <= 0) {
            return 0;
        }
        long wholePercent = value / 100;
        long remainder = value % 100;
        return Math.max(1, wholePercent * 85 + remainder * 85 / 100);
    }

    private static Map.Entry<String, MatterValue> entry(String id,
            long metal, long mineral, long crystal, long organic) {
        return Map.entry(id, new MatterValue(metal, mineral, crystal, organic));
    }

    private record MatterRule(MatterValue deconstruct, MatterValue rewrite) {
    }

    public record MatterValue(long metal, long mineral, long crystal, long organic) {
        public long total() {
            return saturatedAdd(saturatedAdd(metal, mineral), saturatedAdd(crystal, organic));
        }

        private static long saturatedAdd(long left, long right) {
            return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
        }
    }
}
