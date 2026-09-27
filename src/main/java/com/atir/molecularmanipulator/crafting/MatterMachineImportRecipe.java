package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

/** Data-pack/KubeJS declaration for importing every recipe of an addon machine. */
public record MatterMachineImportRecipe(ResourceLocation machine, List<ResourceLocation> serializers,
        MatterResearchRecipe research, Optional<Fields> inputs, Optional<Fields> outputs,
        int processingTime, double aePerTick)
        implements Recipe<MatterFabricationRecipeInput> {
    public static final MapCodec<MatterMachineImportRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("machine").forGetter(MatterMachineImportRecipe::machine),
            ResourceLocation.CODEC.listOf().fieldOf("serializers").forGetter(MatterMachineImportRecipe::serializers),
            MatterResearchRecipe.CODEC.codec().fieldOf("research").forGetter(MatterMachineImportRecipe::research),
            Fields.CODEC.optionalFieldOf("inputs").forGetter(MatterMachineImportRecipe::inputs),
            Fields.CODEC.optionalFieldOf("outputs").forGetter(MatterMachineImportRecipe::outputs),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("processing_time", 200)
                    .forGetter(MatterMachineImportRecipe::processingTime),
            Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("ae_per_tick", 256.0)
                    .forGetter(MatterMachineImportRecipe::aePerTick)
    ).apply(instance, MatterMachineImportRecipe::new));

    public MatterMachineImportRecipe {
        serializers = List.copyOf(serializers);
        if (serializers.isEmpty()) throw new IllegalArgumentException("Machine import needs at least one serializer");
        if (processingTime < 1 || !Double.isFinite(aePerTick) || aePerTick < 0) {
            throw new IllegalArgumentException("Machine import needs positive time and finite nonnegative power");
        }
    }

    public record Fields(List<String> items, List<String> fluids, List<String> resources,
            List<ResourceRule> aeKeys) {
        public static final Codec<Fields> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Fields::items),
                Codec.STRING.listOf().optionalFieldOf("fluids", List.of()).forGetter(Fields::fluids),
                Codec.STRING.listOf().optionalFieldOf("resources", List.of()).forGetter(Fields::resources),
                ResourceRule.CODEC.listOf().optionalFieldOf("ae_keys", List.of()).forGetter(Fields::aeKeys)
        ).apply(instance, Fields::new));

        public Fields {
            items = List.copyOf(items);
            fluids = List.copyOf(fluids);
            resources = List.copyOf(resources);
            aeKeys = List.copyOf(aeKeys);
            items.forEach(MatterMachineImportRecipe::validatePointer);
            fluids.forEach(MatterMachineImportRecipe::validatePointer);
            resources.forEach(MatterMachineImportRecipe::validatePointer);
            if (items.isEmpty() && fluids.isEmpty() && resources.isEmpty() && aeKeys.isEmpty()) {
                throw new IllegalArgumentException("Machine field mapping cannot be empty");
            }
        }
    }

    public record ResourceRule(ResourceLocation keyType, String amountPath, long amountDefault,
            Map<String, JsonElement> fields, Map<String, String> fieldPaths) {
        private static final Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(
                value -> value.convert(JsonOps.INSTANCE).getValue(), value -> new Dynamic<>(JsonOps.INSTANCE, value));
        public static final Codec<ResourceRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("key_type").forGetter(ResourceRule::keyType),
                Codec.STRING.optionalFieldOf("amount_path", "").forGetter(ResourceRule::amountPath),
                Codec.LONG.optionalFieldOf("amount_default", 0L).forGetter(ResourceRule::amountDefault),
                Codec.unboundedMap(Codec.STRING, JSON).optionalFieldOf("fields", Map.of())
                        .forGetter(ResourceRule::fields),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("field_paths", Map.of())
                        .forGetter(ResourceRule::fieldPaths)
        ).apply(instance, ResourceRule::new));

        public ResourceRule {
            fields = Map.copyOf(fields);
            fieldPaths = Map.copyOf(fieldPaths);
            validatePointer(amountPath);
            fieldPaths.values().forEach(MatterMachineImportRecipe::validatePointer);
            if (amountDefault < 0 || fields.containsKey("#t") || fields.containsKey("#")
                    || fieldPaths.containsKey("#t") || fieldPaths.containsKey("#")) {
                throw new IllegalArgumentException("Invalid AE key mapping");
            }
        }
    }

    private static void validatePointer(String pointer) {
        if (!pointer.isEmpty() && !pointer.startsWith("/")) {
            throw new IllegalArgumentException("Field paths must be JSON pointers: " + pointer);
        }
    }

    @Override public boolean matches(MatterFabricationRecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(MatterFabricationRecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.MATTER_MACHINE_IMPORT_RECIPE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.MATTER_MACHINE_IMPORT_RECIPE_TYPE.get(); }

    public static final class Serializer implements RecipeSerializer<MatterMachineImportRecipe> {
        @Override public MapCodec<MatterMachineImportRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MatterMachineImportRecipe> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    }
}
