package com.atir.molecularmanipulator.recipelock;

import com.atir.molecularmanipulator.crafting.MatterFabricationRecipeInput;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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

/** A declaration, not an executable recipe. KubeJS uses the ordinary event.custom API. */
public record RecipeLockRecipe(List<ResourceLocation> requires, String mode, Selector match)
        implements Recipe<MatterFabricationRecipeInput> {
    public RecipeLockRecipe {
        requires = List.copyOf(requires);
    }

    public record Selector(List<ResourceLocation> recipeIds, List<String> recipeIdRegex,
                           List<ResourceLocation> recipeTypes, List<String> namespaces,
                           List<ResourceLocation> excludeIds) {
        public Selector {
            recipeIds = List.copyOf(recipeIds);
            recipeIdRegex = List.copyOf(recipeIdRegex);
            recipeTypes = List.copyOf(recipeTypes);
            namespaces = List.copyOf(namespaces);
            excludeIds = List.copyOf(excludeIds);
        }

        public static final Codec<Selector> CODEC = RecordCodecBuilder.<Selector>create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("recipe_ids", List.of()).forGetter(Selector::recipeIds),
                Codec.STRING.listOf().optionalFieldOf("recipe_id_regex", List.of()).forGetter(Selector::recipeIdRegex),
                ResourceLocation.CODEC.listOf().optionalFieldOf("recipe_types", List.of()).forGetter(Selector::recipeTypes),
                Codec.STRING.listOf().optionalFieldOf("namespaces", List.of()).forGetter(Selector::namespaces),
                ResourceLocation.CODEC.listOf().optionalFieldOf("exclude_ids", List.of()).forGetter(Selector::excludeIds)
        ).apply(instance, Selector::new)).validate(selector -> {
            String error = selector.error();
            return error == null ? DataResult.success(selector) : DataResult.error(() -> error);
        });

        public String error() {
            if (recipeIds.isEmpty() && recipeIdRegex.isEmpty() && recipeTypes.isEmpty() && namespaces.isEmpty()) {
                return "recipe_lock.match needs at least one positive selector";
            }
            for (var namespace : namespaces) {
                if (namespace.isEmpty() || ResourceLocation.tryBuild(namespace, "probe") == null) {
                    return "Invalid recipe namespace: " + namespace;
                }
            }
            for (var expression : recipeIdRegex) {
                try {
                    Pattern.compile(expression);
                } catch (PatternSyntaxException invalid) {
                    return "Invalid recipe_id_regex: " + invalid.getDescription();
                }
            }
            return null;
        }
    }

    public static final MapCodec<RecipeLockRecipe> CODEC = RecordCodecBuilder.<RecipeLockRecipe>mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.listOf().fieldOf("requires").forGetter(RecipeLockRecipe::requires),
            Codec.STRING.optionalFieldOf("mode", "all").forGetter(RecipeLockRecipe::mode),
            Selector.CODEC.fieldOf("match").forGetter(RecipeLockRecipe::match)
    ).apply(instance, RecipeLockRecipe::new)).validate(recipe -> {
        String error = recipe.error();
        return error == null ? DataResult.success(recipe) : DataResult.error(() -> error);
    });

    public String error() {
        if (requires.isEmpty()) return "recipe_lock.requires cannot be empty";
        if (!"all".equals(mode) && !"any".equals(mode)) return "recipe_lock.mode must be all or any";
        return match == null ? "Missing recipe_lock.match" : match.error();
    }

    @Override public boolean matches(MatterFabricationRecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(MatterFabricationRecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.RECIPE_LOCK_RECIPE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.RECIPE_LOCK_RECIPE_TYPE.get(); }

    public static final class Serializer implements RecipeSerializer<RecipeLockRecipe> {
        @Override public MapCodec<RecipeLockRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, RecipeLockRecipe> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    }
}
