package com.atir.molecularmanipulator.starcomputation;

import com.atir.molecularmanipulator.crafting.MatterFabricationRecipeInput;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Ordinary data-pack/KubeJS declaration; it never executes in a crafting inventory. */
public record StarComputationUpgradeRecipe(int level, double energy, double aePerTick, Condition conditions,
                                           boolean autoBuild)
        implements Recipe<MatterFabricationRecipeInput> {
    public static final int MAX_DEPTH = 32;
    public static final int MAX_ELEMENTS = 1024;
    private static final Set<String> GROUPS = Set.of("all", "any", "at_least");
    private static final Set<String> LEAVES = Set.of("ftb_quests", "machines", "research", "nodes", "anchors");
    private static final Set<String> ANCHOR_TIERS = Set.of("reference", "quantum", "terminal");
    // A separate holder avoids initialization cycles when a caller uses Target/Condition.CODEC first.
    private static final class Numbers {
        // Codec.INT normally truncates JSON fractions. Configuration counts must be exact integers.
        private static final Codec<Integer> INTEGER = new Codec<>() {
            @Override public <T> DataResult<Pair<Integer, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getNumberValue(input).flatMap(number -> {
                    try {
                        return DataResult.success(Pair.of(new BigDecimal(number.toString()).intValueExact(), ops.empty()));
                    } catch (ArithmeticException | NumberFormatException invalid) {
                        return DataResult.error(() -> "Expected an exact 32-bit integer: " + number);
                    }
                });
            }
            @Override public <T> DataResult<T> encode(Integer value, DynamicOps<T> ops, T prefix) {
                return ops.mergeToPrimitive(prefix, ops.createInt(value));
            }
        };
        private static Codec<Integer> integer(int minimum, int maximum) {
            return INTEGER.validate(value -> value >= minimum && value <= maximum ? DataResult.success(value)
                    : DataResult.error(() -> "Expected integer in " + minimum + ".." + maximum + ": " + value));
        }
    }
    private static final Codec<Double> ENERGY = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value >= 0 ? DataResult.success(value)
                    : DataResult.error(() -> "energy must be finite and >= 0"));
    private static final Codec<Double> RATE = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value > 0 ? DataResult.success(value)
                    : DataResult.error(() -> "ae_per_tick must be finite and > 0 (including zero-energy upgrades)"));

    public static final MapCodec<StarComputationUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Numbers.integer(1, 9).fieldOf("level").forGetter(StarComputationUpgradeRecipe::level),
            ENERGY.fieldOf("energy").forGetter(StarComputationUpgradeRecipe::energy),
            RATE.fieldOf("ae_per_tick").forGetter(StarComputationUpgradeRecipe::aePerTick),
            Condition.CODEC.fieldOf("conditions").forGetter(StarComputationUpgradeRecipe::conditions),
            Codec.BOOL.optionalFieldOf("auto_build", false).forGetter(StarComputationUpgradeRecipe::autoBuild)
    ).apply(instance, StarComputationUpgradeRecipe::new));

    /** Existing Java/KubeJS declarations keep their explicit-build behavior unless they opt in. */
    public StarComputationUpgradeRecipe(int level, double energy, double aePerTick, Condition conditions) {
        this(level, energy, aePerTick, conditions, false);
    }

    /** Validation belongs to the codecs: malformed data returns DataResult.error, not a constructor exception. */
    public record Target(String id, int count, String title) {
        public static final Codec<Target> CODEC = RecordCodecBuilder.<Target>create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(Target::id),
                Numbers.integer(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(Target::count),
                Codec.STRING.optionalFieldOf("title", "").forGetter(Target::title)
        ).apply(instance, Target::new)).validate(target -> target.id().isBlank()
                ? DataResult.error(() -> "Upgrade target id cannot be empty") : DataResult.success(target));
    }

    public record Condition(String type, List<Condition> children, List<Target> targets, String match,
                            int required, boolean sameMachine, boolean formed, String title) {
        // Bounded lazy recursion also rejects excessively deep input *during decoding*, before stack overflow.
        public static final Codec<Condition> CODEC = codec(0);

        public Condition {
            children = List.copyOf(children);
            targets = List.copyOf(targets);
        }

        private static Codec<Condition> codec(int depth) {
            Codec<Condition> child = depth < MAX_DEPTH
                    ? Codec.lazyInitialized(() -> codec(depth + 1))
                    : Codec.STRING.comapFlatMap(ignored -> DataResult.error(() -> "Upgrade condition nesting exceeds " + MAX_DEPTH),
                            ignored -> "too_deep");
            return RecordCodecBuilder.<Condition>create(instance -> instance.group(
                    Codec.STRING.fieldOf("type").forGetter(Condition::type),
                    child.listOf().optionalFieldOf("children", List.of()).forGetter(Condition::children),
                    Target.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(Condition::targets),
                    Codec.STRING.optionalFieldOf("match", "all").forGetter(Condition::match),
                    Numbers.integer(1, Integer.MAX_VALUE).optionalFieldOf("required", 1).forGetter(Condition::required),
                    Codec.BOOL.optionalFieldOf("same_machine", true).forGetter(Condition::sameMachine),
                    Codec.BOOL.optionalFieldOf("formed", true).forGetter(Condition::formed),
                    Codec.STRING.optionalFieldOf("title", "").forGetter(Condition::title)
            ).apply(instance, Condition::new)).validate(condition -> {
                String error = condition.error();
                return error == null ? DataResult.success(condition) : DataResult.error(() -> error);
            });
        }

        public boolean group() { return GROUPS.contains(type); }

        /** Also used defensively for Java/KubeJS-created records that did not go through a codec. */
        public String error() { return error(0, new int[] {0}); }

        private String error(int depth, int[] elements) {
            if (depth > MAX_DEPTH || ++elements[0] > MAX_ELEMENTS) return "Upgrade condition tree is too large";
            if (type == null || match == null) return "Upgrade type and match cannot be null";
            if (!group() && !LEAVES.contains(type)) return "Unknown upgrade condition type: " + type;
            if (!GROUPS.contains(match)) return "Unknown upgrade target match: " + match;
            if (title == null) return "Upgrade condition title cannot be null";
            int size = group() ? children.size() : targets.size();
            if (size == 0) return group() ? "Combination children cannot be empty" : "Condition targets cannot be empty";
            if (group() ? !targets.isEmpty() : !children.isEmpty()) return "Use children for groups and targets for leaves, not both";
            if (required < 1 || required > size) return "Upgrade required threshold must be in 1.." + size;
            for (var target : targets) {
                if (++elements[0] > MAX_ELEMENTS) return "Upgrade condition tree is too large";
                if (target.id() == null || target.id().isBlank() || target.count() < 1 || target.title() == null) {
                    return "Upgrade targets need an id, positive count and non-null title";
                }
                if (type.equals("anchors") && !ANCHOR_TIERS.contains(target.id())) {
                    return "Unknown upgrade anchor tier: " + target.id();
                }
                if (type.equals("ftb_quests") && !target.id().matches("(?:0[xX])?[0-9a-fA-F]{1,16}")) {
                    return "FTB quest id must be a hexadecimal string of 1..16 digits (optional 0x prefix): " + target.id();
                }
                String id = type.equals("machines") && target.id().startsWith("#") ? target.id().substring(1) : target.id();
                if ((type.equals("machines") || type.equals("research"))
                        && (id.isBlank() || ResourceLocation.tryParse(id) == null)) {
                    return "Invalid upgrade target resource id: " + target.id();
                }
            }
            for (var child : children) {
                String error = child.error(depth + 1, elements);
                if (error != null) return error;
            }
            return null;
        }
    }

    public String error() {
        if (level < 1 || level > 9 || !Double.isFinite(energy) || energy < 0
                || !Double.isFinite(aePerTick) || aePerTick <= 0) return "Invalid upgrade level, energy or rate";
        return conditions == null ? "Missing upgrade conditions" : conditions.error();
    }

    @Override public boolean matches(MatterFabricationRecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(MatterFabricationRecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.STAR_ORRERY_UPGRADE_RECIPE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.STAR_ORRERY_UPGRADE_RECIPE_TYPE.get(); }

    public static final class Serializer implements RecipeSerializer<StarComputationUpgradeRecipe> {
        @Override public MapCodec<StarComputationUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, StarComputationUpgradeRecipe> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    }
}
