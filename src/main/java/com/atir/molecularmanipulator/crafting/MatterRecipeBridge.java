package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fluids.FluidStack;

/** Converts supported addon machine recipes into Matter Fabrication recipes at runtime. */
public final class MatterRecipeBridge {
    private static final List<MachineSpec> MACHINES = List.of(
            machine("data_reassembler", "data_energistics", "data_energistics:data_reassembler",
                    "data_energistics:data_reassembler"),
            machine("data_charger", "data_energistics", "data_energistics:data_charger",
                    "data_energistics:data_charger"),
            machine("data_integrated_charger", "data_energistics", "data_energistics:data_integrated_charger",
                    "data_energistics:data_integrated_charger", "data_energistics:data_charge_press"),
            machine("neoeco_working_station", "neoecoae", "neoecoae:integrated_working_station",
                    "neoecoae:integrated_working_station"),
            machine("lightning_crystal_catalyzer", "ae2lt", "ae2lt:crystal_catalyzer",
                    "ae2lt:crystal_catalyzer"),
            machine("lightning_assembly", "ae2lt", "ae2lt:lightning_assembly_chamber",
                    "ae2lt:lightning_assembly"),
            machine("lightning_simulation", "ae2lt", "ae2lt:lightning_simulation_room",
                    "ae2lt:lightning_simulation"),
            machine("overload_processing", "ae2lt", "ae2lt:overload_processing_factory",
                    "ae2lt:overload_processing"),
            machine("firmament_conversion", "ae2lt", "ae2lt:firmament_conversion_core",
                    "ae2lt:firmament_conversion"),
            machine("crystal_aggregator", "ae2cs", "ae2cs:crystal_aggregator",
                    "ae2cs:crystal_aggregator_recipe_serializer"),
            machine("circuit_etcher", "ae2cs", "ae2cs:circuit_etcher",
                    "ae2cs:circuit_etcher_recipe_serializer"),
            machine("crystal_pulverizer", "ae2cs", "ae2cs:crystal_pulverizer",
                    "ae2cs:crystal_pulverizer_recipe_serializer"),
            machine("eae_crystal_assembler", "expatternprovider", "expatternprovider:crystal_assembler",
                    "expatternprovider:crystal_assembler"),
            machine("eae_circuit_slicer", "expatternprovider", "expatternprovider:circuit_cutter",
                    "expatternprovider:circuit_cutter"),
            machine("aae_reaction_chamber", "advanced_ae", "advanced_ae:reaction_chamber",
                    "advanced_ae:reaction"));
    private static final Set<String> LIGHTNING_RECIPE_TYPES = Set.of(
            "ae2lt:crystal_catalyzer", "ae2lt:lightning_assembly",
            "ae2lt:lightning_simulation", "ae2lt:overload_processing");

    private MatterRecipeBridge() {}

    private static MachineSpec machine(String key, String modId, String itemId, String... recipeTypes) {
        return new MachineSpec(key, modId, new ResourceLocation(itemId), List.of(recipeTypes), null);
    }

    public record MachineSpec(String key, String modId, ResourceLocation machineItem, List<String> recipeTypes,
            MatterMachineImportRecipe declaration) {
        public MachineSpec(String key, String modId, ResourceLocation machineItem, List<String> recipeTypes) {
            this(key, modId, machineItem, recipeTypes, null);
        }
    }

    public record ImportedRecipe(MatterFabricationRecipe holder, String machineKey,
            String modId, ItemStack output, FluidStack fluidOutput, int branchCount) {
        public ImportedRecipe {
            output = output.copy();
            fluidOutput = fluidOutput.copy();
            branchCount = Math.max(1, branchCount);
        }
    }

    record Input(Ingredient ingredient, int count) {}
    record LightningRequirement(String tier, long amount) {}
    private record Branch(Output output,
            List<MatterFabricationRecipe.CountedIngredient> ingredients,
            List<MatterFabricationRecipe.CountedIngredient> catalysts,
            FluidStack fluid, List<appeng.api.stacks.GenericStack> aeInputs, String signature) {}

    public static List<MachineSpec> machines() { return MACHINES; }

    public static List<MachineSpec> machines(Collection<Recipe<?>> source) {
        var result = new ArrayList<>(MACHINES);
        source.stream().filter(holder -> holder instanceof MatterMachineImportRecipe)
                .sorted(Comparator.comparing(holder -> holder.getId().toString())).forEach(holder -> {
                    var declaration = (MatterMachineImportRecipe) holder;
                    var key = holder.getId().getNamespace() + "/" + holder.getId().getPath();
                    result.add(new MachineSpec(key, declaration.machine().getNamespace(), declaration.machine(),
                            declaration.serializers().stream().map(ResourceLocation::toString).toList(), declaration));
                });
        return List.copyOf(result);
    }

    public static List<ImportedRecipe> importRecipes(Level level, Collection<Recipe<?>> source) {
        return importRecipes(level, source, machines(source));
    }

    public static List<ImportedRecipe> importRecipes(Level level, Collection<Recipe<?>> source,
            List<MachineSpec> machines) {
        var byType = new LinkedHashMap<String, MachineSpec>();
        for (var machine : machines) {
            if (!BuiltInRegistries.ITEM.containsKey(machine.machineItem)
                    || machine.declaration == null && !ModList.get().isLoaded(machine.modId)
                    || machine.declaration != null && !machine.declaration.research().available()) continue;
            for (var type : machine.recipeTypes) byType.put(type, machine);
        }
        var grouped = new LinkedHashMap<String, List<Branch>>();
        var info = new LinkedHashMap<String, GroupInfo>();
        var failures = new LinkedHashMap<String, List<ResourceLocation>>();
        for (var holder : source) {
            var recipe = holder;
            if (recipe instanceof MatterFabricationRecipe || recipe instanceof MatterMachineImportRecipe) continue;
            var serializerId = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
            var type = serializerId == null ? "" : serializerId.toString();
            var machine = byType.get(type);
            if (machine == null) continue;
            var encoded = encode(level, recipe);
            if (encoded == null) {
                recordFailure(failures, machine, holder.getId());
                continue;
            }
            var output = machine.declaration != null && machine.declaration.outputs().isPresent()
                    ? mappedOutput(level, encoded, machine.declaration.outputs().get())
                    : output(level, recipe, encoded, type);
            if (output == null || output.items().isEmpty() && output.fluids().isEmpty() && output.resources().isEmpty()
                    || output.items().size() > MatterFabricationRecipe.MAX_OUTPUTS
                    || output.resources().size() + Math.max(0, output.fluids().size() - 1) > MatterFabricationRecipe.MAX_OUTPUTS) {
                recordFailure(failures, machine, holder.getId());
                continue;
            }
            var branch = convert(level, recipe, output, encoded, type, machine);
            if (branch == null) {
                recordFailure(failures, machine, holder.getId());
                continue;
            }
            var key = machine.key + "|" + outputKey(output) + "|" + branch.signature;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(branch);
            info.putIfAbsent(key, new GroupInfo(machine, output, branch.signature));
        }
        failures.forEach((machine, ids) -> com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
                "Matter machine import {} skipped {} recipes with unreadable fields/resources or unsupported slot counts: {}",
                machine, ids.size(), ids));
        var byMachineOutput = new LinkedHashMap<String, List<Map.Entry<String, List<Branch>>>>();
        for (var entry : grouped.entrySet()) {
            var group = info.get(entry.getKey());
            byMachineOutput.computeIfAbsent(group.machine.key + "|" + outputKey(group.output), ignored -> new ArrayList<>())
                    .add(entry);
        }
        var result = new ArrayList<ImportedRecipe>();
        var usedIds = new LinkedHashSet<ResourceLocation>();
        for (var entries : byMachineOutput.values()) {
            entries.sort(Comparator.comparing(entry -> info.get(entry.getKey()).signature));
            var first = info.get(entries.get(0).getKey());
            var outputId = !first.output.items().isEmpty()
                    ? BuiltInRegistries.ITEM.getKey(first.output.items().get(0).getItem())
                    : !first.output.fluids().isEmpty()
                    ? BuiltInRegistries.FLUID.getKey(first.output.fluids().get(0).getFluid())
                    : new ResourceLocation("resource");
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                var group = info.get(entry.getKey());
                var branch = entry.getValue().get(0);
                var path = "fabrication/import/machine/" + group.machine.key + "/" + outputId.getPath()
                        + (index == 0 ? "" : "/branch_" + (index + 1));
                var id = MolecularManipulator.id(path);
                if (!usedIds.add(id)) {
                    var variant = path + "/variant_" + Integer.toHexString(
                            (outputKey(group.output) + group.signature).hashCode());
                    id = MolecularManipulator.id(variant);
                    int collision = 2;
                    while (!usedIds.add(id)) id = MolecularManipulator.id(variant + "_" + collision++);
                }
                var outputFluids = branch.output().fluids();
                var aeOutputs = new ArrayList<>(branch.output().resources());
                for (int fluidIndex = 1; fluidIndex < outputFluids.size(); fluidIndex++) {
                    var extra = outputFluids.get(fluidIndex);
                    aeOutputs.add(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEFluidKey.of(extra), extra.getAmount()));
                }
                var declaration = group.machine.declaration;
                var fabrication = new MatterFabricationRecipe(branch.ingredients(), branch.catalysts(),
                        branch.output().items(), branch.fluid(),
                        outputFluids.isEmpty() ? FluidStack.EMPTY : outputFluids.get(0),
                        branch.aeInputs(), aeOutputs, declaration == null ? 200 : declaration.processingTime(),
                        declaration == null ? 256 : declaration.aePerTick(), true);
                result.add(new ImportedRecipe(fabrication.withId(id), group.machine.key,
                        group.machine.modId,
                        group.output.items().isEmpty() ? ItemStack.EMPTY : group.output.items().get(0),
                        group.output.fluids().isEmpty() ? FluidStack.EMPTY : group.output.fluids().get(0),
                        entry.getValue().size()));
            }
        }
        return List.copyOf(result);
    }

    private static void recordFailure(Map<String, List<ResourceLocation>> failures, MachineSpec machine, ResourceLocation id) {
        if (machine.declaration != null) failures.computeIfAbsent(machine.key, ignored -> new ArrayList<>()).add(id);
    }

    private record GroupInfo(MachineSpec machine, Output output, String signature) {}

    private record Output(List<ItemStack> items, List<FluidStack> fluids,
            List<appeng.api.stacks.GenericStack> resources) {}

    private record MappedInputs(List<Input> items, List<FluidStack> fluids,
            List<appeng.api.stacks.GenericStack> resources) {}

    private static Output mappedOutput(Level level, JsonElement encoded, MatterMachineImportRecipe.Fields fields) {
        try {
            var ops = JsonOps.INSTANCE;
            var items = new ArrayList<ItemStack>();
            var fluids = new ArrayList<FluidStack>();
            var resources = new ArrayList<appeng.api.stacks.GenericStack>();
            for (var path : fields.items()) {
                var parsed = readMappedItemOutputs(ops, atPath(encoded, path));
                if (parsed.isEmpty()) return null;
                items.addAll(parsed);
            }
            for (var path : fields.fluids()) {
                var parsed = readFluids(ops, atPath(encoded, path));
                if (parsed.isEmpty()) return null;
                fluids.addAll(parsed);
            }
            for (var path : fields.resources()) {
                var parsed = readResources(level, atPath(encoded, path));
                if (parsed.isEmpty()) return null;
                resources.addAll(parsed);
            }
            for (var rule : fields.aeKeys()) {
                var parsed = mappedResource(level, encoded, rule);
                if (parsed == null) return null;
                resources.add(parsed);
            }
            return new Output(List.copyOf(items), List.copyOf(fluids), List.copyOf(resources));
        } catch (RuntimeException ignored) { return null; }
    }

    private static MappedInputs mappedInputs(Level level, JsonElement encoded, MatterMachineImportRecipe.Fields fields) {
        try {
            var ops = JsonOps.INSTANCE;
            var items = new ArrayList<Input>();
            var fluids = new ArrayList<FluidStack>();
            var resources = new ArrayList<appeng.api.stacks.GenericStack>();
            for (var path : fields.items()) {
                items.addAll(readMappedItemInputs(ops, atPath(encoded, path)));
            }
            for (var path : fields.fluids()) {
                var parsed = readFluids(ops, atPath(encoded, path));
                if (parsed.isEmpty()) return null;
                fluids.addAll(parsed);
            }
            for (var path : fields.resources()) {
                var parsed = readResources(level, atPath(encoded, path));
                if (parsed.isEmpty()) return null;
                resources.addAll(parsed);
            }
            for (var rule : fields.aeKeys()) {
                var parsed = mappedResource(level, encoded, rule);
                if (parsed == null) return null;
                resources.add(parsed);
            }
            return new MappedInputs(List.copyOf(items), List.copyOf(fluids), List.copyOf(resources));
        } catch (RuntimeException ignored) { return null; }
    }

    static JsonElement atPath(JsonElement root, String pointer) {
        if (pointer.isEmpty()) return root;
        if (!pointer.startsWith("/")) return null;
        JsonElement current = root;
        for (var part : pointer.substring(1).split("/", -1)) {
            var key = part.replace("~1", "/").replace("~0", "~");
            if (current instanceof JsonObject object) current = object.get(key);
            else if (current != null && current.isJsonArray()) {
                try { current = current.getAsJsonArray().get(Integer.parseInt(key)); }
                catch (RuntimeException error) { return null; }
            } else return null;
            if (current == null) return null;
        }
        return current;
    }

    static List<Input> readMappedItemInputs(DynamicOps<JsonElement> ops, JsonElement element) {
        if (element == null || element.isJsonNull()) throw new IllegalArgumentException("Missing item input");
        if (element.isJsonArray()) {
            var result = new ArrayList<Input>();
            for (var child : element.getAsJsonArray()) result.addAll(readMappedItemInputs(ops, child));
            if (result.isEmpty()) throw new IllegalArgumentException("Empty item inputs");
            return List.copyOf(result);
        }
        if (!(element instanceof JsonObject object)) throw new IllegalArgumentException("Item input must be an ingredient");
        var ingredient = ForgeRecipeCodecs.INGREDIENT.parse(ops, object.has("ingredient") ? object.get("ingredient") : object)
                .getOrThrow(false, message -> {});
        long count = strictAmount(object, 1);
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Item input count exceeds integer range");
        return List.of(new Input(ingredient, (int) count));
    }

    static List<ItemStack> readMappedItemOutputs(DynamicOps<JsonElement> ops, JsonElement element) {
        if (element == null || element.isJsonNull()) throw new IllegalArgumentException("Missing item output");
        if (element.isJsonArray()) {
            var result = new ArrayList<ItemStack>();
            for (var child : element.getAsJsonArray()) result.addAll(readMappedItemOutputs(ops, child));
            if (result.isEmpty()) throw new IllegalArgumentException("Empty item outputs");
            return List.copyOf(result);
        }
        if (!(element instanceof JsonObject object)) throw new IllegalArgumentException("Item output must be a stack");
        var normalized = object.deepCopy();
        if (normalized.has("item") && !normalized.has("id")) normalized.add("id", normalized.remove("item"));
        long count = strictAmount(normalized, 1);
        if (count > Integer.MAX_VALUE) throw new IllegalArgumentException("Item output count exceeds integer range");
        normalized.addProperty("count", 1);
        return List.of(ForgeRecipeCodecs.ITEM_STACK.parse(ops, normalized)
                .getOrThrow(false, message -> {}).copyWithCount((int) count));
    }

    private static long strictAmount(JsonObject object, long fallback) {
        for (var field : List.of("amount", "count", "#")) {
            if (object.has(field)) return positiveInteger(object.get(field));
        }
        if (fallback < 1) throw new IllegalArgumentException("Missing amount");
        return fallback;
    }

    private static long positiveInteger(JsonElement value) {
        long amount = new java.math.BigDecimal(value.getAsString()).longValueExact();
        if (amount < 1) throw new IllegalArgumentException("Amount must be positive");
        return amount;
    }

    private static List<FluidStack> readFluids(DynamicOps<JsonElement> ops, JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonArray()) {
            var result = new ArrayList<FluidStack>();
            for (var child : element.getAsJsonArray()) {
                var parsed = readFluids(ops, child);
                if (parsed.isEmpty()) throw new IllegalArgumentException("Unreadable fluid stack");
                result.addAll(parsed);
            }
            return List.copyOf(result);
        }
        if (!(element instanceof JsonObject object)) return List.of();
        var normalized = object.deepCopy();
        if (normalized.has("fluid") && !normalized.has("id")) normalized.add("id", normalized.remove("fluid"));
        long amount = strictAmount(normalized, 0);
        if (amount > Integer.MAX_VALUE) throw new IllegalArgumentException("Fluid amount exceeds integer range");
        normalized.addProperty("amount", amount);
        return List.of(ForgeRecipeCodecs.FLUID_STACK.parse(ops, normalized).getOrThrow(false, message -> {}));
    }

    private static List<appeng.api.stacks.GenericStack> readResources(Level level, JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonArray()) {
            var result = new ArrayList<appeng.api.stacks.GenericStack>();
            for (var child : element.getAsJsonArray()) {
                var parsed = readResources(level, child);
                if (parsed.isEmpty()) throw new IllegalArgumentException("Unreadable AE resource");
                result.addAll(parsed);
            }
            return List.copyOf(result);
        }
        if (!(element instanceof JsonObject object)) return List.of();
        var ops = JsonOps.INSTANCE;
        var normalized = object.deepCopy();
        if (normalized.has("#t")) normalized.addProperty("#", positiveInteger(normalized.get("#")));
        else normalized.addProperty("amount", strictAmount(normalized, 0));
        var stack = normalized.has("#t")
                ? ForgeRecipeCodecs.GENERIC_STACK.parse(ops, normalized).result().orElse(null)
                : resourceStack(level, normalized);
        return stack == null || stack.amount() <= 0 ? List.of() : List.of(stack);
    }

    private static appeng.api.stacks.GenericStack mappedResource(Level level, JsonElement encoded,
            MatterMachineImportRecipe.ResourceRule rule) {
        var object = mappedResourceData(encoded, rule);
        if (object == null) return null;
        var ops = JsonOps.INSTANCE;
        return ForgeRecipeCodecs.GENERIC_STACK.parse(ops, object).result().orElse(null);
    }

    static JsonObject mappedResourceData(JsonElement encoded, MatterMachineImportRecipe.ResourceRule rule) {
        long amount = rule.amountDefault();
        if (!rule.amountPath().isEmpty()) {
            var value = atPath(encoded, rule.amountPath());
            if (value != null) {
                try { amount = positiveInteger(value); } catch (RuntimeException ignored) { return null; }
            }
        }
        if (amount <= 0) return null;
        var object = new JsonObject();
        object.addProperty("#t", rule.keyType().toString());
        object.addProperty("#", amount);
        rule.fields().forEach((field, value) -> object.add(field, value.deepCopy()));
        for (var entry : rule.fieldPaths().entrySet()) {
            var value = atPath(encoded, entry.getValue());
            if (value == null) {
                if (!rule.fields().containsKey(entry.getKey())) return null;
            } else if (value.isJsonNull()) return null;
            else object.add(entry.getKey(), value.deepCopy());
        }
        return object;
    }

    private static JsonElement encode(Level level, Recipe<?> recipe) {
        return ForgeMachineRecipeJson.encode(level, recipe);
    }

    private static Output output(Level level, Recipe<?> recipe, JsonElement encoded, String type) {
        if (type.equals("data_energistics:data_reassembler") && encoded instanceof JsonObject root) {
            var items = new ArrayList<ItemStack>();
            var fluids = new ArrayList<FluidStack>();
            var resources = new ArrayList<appeng.api.stacks.GenericStack>();
            var ops = JsonOps.INSTANCE;
            if (root.get("outputs") instanceof JsonObject outputs) {
                items.addAll(readItemOutputs(ops, outputs.get("items")));
                var fluidValues = outputs.get("fluids");
                if (fluidValues != null && fluidValues.isJsonArray()) for (var value : fluidValues.getAsJsonArray()) {
                    var fluid = findOutputFluid(value, true);
                    if (!fluid.isEmpty()) fluids.add(fluid);
                }
                if (outputs.get("resource") instanceof JsonObject resource) {
                    var stack = resourceStack(level, resource);
                    if (stack != null) resources.add(stack);
                }
            }
            items.addAll(readItemOutputs(ops, root.get("item_outputs")));
            var fluidValues = root.get("fluid_outputs");
            if (fluidValues != null && fluidValues.isJsonArray()) for (var value : fluidValues.getAsJsonArray()) {
                var fluid = findOutputFluid(value, true);
                if (!fluid.isEmpty()) fluids.add(fluid);
            }
            if (root.get("resource_output") instanceof JsonObject resource) {
                var stack = resourceStack(level, resource);
                if (stack != null) resources.add(stack);
            }
            if (!items.isEmpty() || !fluids.isEmpty() || !resources.isEmpty())
                return new Output(List.copyOf(items), List.copyOf(fluids), List.copyOf(resources));
        }
        var fluid = findOutputFluid(encoded);
        if (encoded instanceof JsonObject object) {
            var ops = JsonOps.INSTANCE;
            for (var key : List.of("results", "result", "itemOutput", "output")) {
                var items = readItemOutputs(ops, object.get(key));
                if (!items.isEmpty()) return new Output(items,
                        fluid.isEmpty() ? List.of() : List.of(fluid), List.of());
            }
        }
        try {
            var stack = recipe.getResultItem(level.registryAccess());
            if (!stack.isEmpty()) return singleOutput(stack.copy(), fluid);
        } catch (RuntimeException ignored) {}
        if (!(encoded instanceof JsonObject object)) return singleOutput(ItemStack.EMPTY, fluid);
        var id = findOutputItem(object);
        var item = id == null || !BuiltInRegistries.ITEM.containsKey(id) ? ItemStack.EMPTY
                : new ItemStack(BuiltInRegistries.ITEM.get(id), (int) Math.max(1, Math.min(64, findOutputCount(object))));
        return singleOutput(item, fluid);
    }

    static List<ItemStack> readItemOutputs(DynamicOps<JsonElement> ops, JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonArray()) {
            var items = new ArrayList<ItemStack>();
            for (var child : element.getAsJsonArray()) items.addAll(readItemOutputs(ops, child));
            return List.copyOf(items);
        }
        if (!(element instanceof JsonObject object)) return List.of();
        if (object.get("items") != null) return readItemOutputs(ops, object.get("items"));
        if (object.has("#t")) {
            var generic = ForgeRecipeCodecs.GENERIC_STACK.parse(ops, object).result().orElse(null);
            if (generic != null && generic.what() instanceof appeng.api.stacks.AEItemKey item
                    && generic.amount() > 0 && generic.amount() <= Integer.MAX_VALUE)
                return List.of(item.toStack((int) generic.amount()));
            var id = findItemId(object);
            long count = number(object, "#", 0);
            if (id != null && count > 0 && count <= Integer.MAX_VALUE)
                return List.of(new ItemStack(BuiltInRegistries.ITEM.get(id), (int) count));
        }
        if (findItemId(object) == null) return List.of();
        var decoded = ForgeRecipeCodecs.ITEM_STACK.parse(ops, object).result().orElse(ItemStack.EMPTY);
        if (!decoded.isEmpty()) return List.of(decoded);
        long count = number(object, "count", number(object, "#", 1));
        if (count <= 0 || count > Integer.MAX_VALUE) return List.of();
        return List.of(new ItemStack(BuiltInRegistries.ITEM.get(findItemId(object)), (int) count));
    }

    private static Output singleOutput(ItemStack item, FluidStack fluid) {
        return new Output(item.isEmpty() ? List.of() : List.of(item),
                fluid.isEmpty() ? List.of() : List.of(fluid), List.of());
    }

    private static ResourceLocation findOutputItem(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            var direct = findItemId(object);
            if (direct != null) return direct;
            for (var key : List.of("result", "output", "outputs", "results", "product")) {
                var found = object.get(key);
                var id = findItemId(found);
                if (id != null) return id;
            }
            for (var entry : object.entrySet()) {
                var key = entry.getKey().toLowerCase();
                if (!key.contains("input") && !key.contains("ingredient") && !key.contains("fluid")) {
                    var id = findOutputItem(entry.getValue());
                    if (id != null) return id;
                }
            }
        } else if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                var id = findOutputItem(child);
                if (id != null) return id;
            }
        }
        return null;
    }

    private static ResourceLocation findItemId(JsonElement element) {
        if (!(element instanceof JsonObject object)) return null;
        for (var key : List.of("item", "id")) {
            var value = object.get(key);
            if (value != null && value.isJsonPrimitive()) {
                var id = ResourceLocation.tryParse(value.getAsString());
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) return id;
            }
        }
        return null;
    }

    private static long findOutputCount(JsonElement element) {
        if (element != null && element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) if (findOutputItem(child) != null) return findOutputCount(child);
            return 1;
        }
        if (!(element instanceof JsonObject object)) return 1;
        if (findItemId(object) != null) return number(object, "count", number(object, "#", 1));
        for (var key : List.of("result", "output", "outputs", "results", "product", "itemOutput")) {
            var found = object.get(key);
            if (findOutputItem(found) != null) return findOutputCount(found);
        }
        for (var entry : object.entrySet()) {
            var key = entry.getKey().toLowerCase();
            if (!key.contains("input") && !key.contains("ingredient") && !key.contains("fluid")
                    && findOutputItem(entry.getValue()) != null) return findOutputCount(entry.getValue());
        }
        return 1;
    }

    private static Branch convert(Level level, Recipe<?> recipe, Output output, JsonElement encoded, String type,
            MachineSpec machine) {
        try {
            var parsed = new ArrayList<Input>();
            var catalysts = new ArrayList<MatterFabricationRecipe.CountedIngredient>();
            var aeInputs = new ArrayList<appeng.api.stacks.GenericStack>();
            var mapped = machine.declaration != null && machine.declaration.inputs().isPresent()
                    ? mappedInputs(level, encoded, machine.declaration.inputs().get()) : null;
            if (machine.declaration != null && machine.declaration.inputs().isPresent() && mapped == null) return null;
            if (mapped != null) {
                parsed.addAll(mapped.items());
                aeInputs.addAll(mapped.resources());
            } else if (encoded != null) {
                if (type.equals("ae2lt:crystal_catalyzer") && encoded instanceof JsonObject object) {
                    var catalyst = object.get("catalyst");
                    if (catalyst != null && !catalyst.isJsonNull()) {
                        var ingredient = parseIngredient(level, catalyst, 1).orElse(null);
                        long count = number(object, "catalystCount", 1);
                        if (ingredient == null || count < 1 || count > Integer.MAX_VALUE) return null;
                        catalysts.add(new MatterFabricationRecipe.CountedIngredient(ingredient, (int) count));
                    }
                } else {
                    collectInputs(level, encoded, parsed, false);
                }
                collectGenericInputs(level, encoded, aeInputs);
                if (LIGHTNING_RECIPE_TYPES.contains(type) && encoded instanceof JsonObject object) {
                    var lightning = lightningInput(level, lightningRequirement(type, object));
                    if (lightning == null) return null;
                    aeInputs.add(lightning);
                }
                if (type.equals("data_energistics:data_charger") && encoded instanceof JsonObject object
                        && number(object, "data_flow", 0) > 0) {
                    var dataFlow = genericStack(level, "data_energistics:digitalization",
                            "data_energistics:data_flow", number(object, "data_flow", 0));
                    if (dataFlow == null) return null;
                    aeInputs.add(dataFlow);
                }
            }
            if (parsed.isEmpty() && mapped == null && !type.equals("ae2lt:crystal_catalyzer")) {
                for (var ingredient : recipe.getIngredients()) if (ingredient != null && !ingredient.isEmpty()) {
                    parsed.add(new Input(ingredient, 1));
                }
            }
            var merged = new LinkedHashMap<String, Long>();
            var examples = new LinkedHashMap<String, Ingredient>();
            for (var input : parsed) {
                var key = ingredientKey(input.ingredient);
                examples.putIfAbsent(key, input.ingredient);
                merged.merge(key, (long) input.count, Math::addExact);
            }
            var ingredients = new ArrayList<MatterFabricationRecipe.CountedIngredient>();
            for (var entry : merged.entrySet()) {
                if (entry.getValue() > Integer.MAX_VALUE) return null;
                ingredients.add(new MatterFabricationRecipe.CountedIngredient(
                        examples.get(entry.getKey()), entry.getValue().intValue()));
            }
            var fluids = mapped == null ? findFluidInputs(encoded) : mapped.fluids();
            // The catalyzer's fixed water cost is machine logic, absent from its recipe codec.
            if (mapped == null && type.equals("ae2lt:crystal_catalyzer") && fluids.isEmpty()) {
                fluids = List.of(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000));
            }
            if (mapped == null && type.equals("data_energistics:data_charge_press") && encoded instanceof JsonObject object
                    && fluids.isEmpty() && number(object, "fluid_amount", 0) > 0) {
                var fluidId = new ResourceLocation("data_energistics:data_corrosion_liquid");
                long amount = number(object, "fluid_amount", 0);
                if (!BuiltInRegistries.FLUID.containsKey(fluidId) || amount > Integer.MAX_VALUE) return null;
                fluids = List.of(new FluidStack(BuiltInRegistries.FLUID.get(fluidId), (int) amount));
            }
            var fluid = fluids.isEmpty() ? FluidStack.EMPTY : fluids.get(0);
            for (int index = 1; index < fluids.size(); index++) {
                var extra = fluids.get(index);
                var key = appeng.api.stacks.AEFluidKey.of(extra);
                aeInputs.add(new appeng.api.stacks.GenericStack(key, extra.getAmount()));
            }
            if (ingredients.size() + catalysts.size() + aeInputs.size() > MatterFabricationRecipe.MAX_INPUTS
                    || (ingredients.isEmpty() && aeInputs.isEmpty() && fluid.isEmpty())) return null;
            var signature = ingredients.stream().map(value -> ingredientKey(value.ingredient()) + "=" + value.count())
                    .sorted().reduce("", (a, b) -> a + ";" + b);
            signature += "|fluid=" + fluidKey(fluid) + "|ae=" + aeInputs.stream()
                    .map(value -> value.what().toString() + "#" + value.amount()).sorted().reduce("", (a,b) -> a + ";" + b);
            if (!catalysts.isEmpty()) signature += "|catalysts=" + catalysts.stream()
                    .map(value -> ingredientKey(value.ingredient()) + "=" + value.count()).sorted().reduce("", (a,b) -> a + ";" + b);
            return new Branch(output, List.copyOf(ingredients), List.copyOf(catalysts), fluid,
                    List.copyOf(aeInputs), signature);
        } catch (RuntimeException ignored) { return null; }
    }

    private static void collectInputs(Level level, JsonElement element, List<Input> result,
            boolean outputContext) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) collectInputs(level, child, result, outputContext);
            return;
        }
        if (!(element instanceof JsonObject object)) return;
        if (!outputContext && (object.has("item") || object.has("tag")) && !object.has("fluid")) {
            parseIngredient(level, object, amount(object)).ifPresent(value -> result.add(new Input(value, amount(object))));
            return;
        }
        if (!outputContext && object.has("ingredient") && object.get("ingredient") instanceof JsonObject ingredient
                && !ingredient.has("fluid")) {
            parseIngredient(level, ingredient, amount(object)).ifPresent(value -> result.add(new Input(value, amount(object))));
            return;
        }
        for (var entry : object.entrySet()) {
            var key = entry.getKey().toLowerCase();
            boolean childOutput = outputContext || isOutputField(key);
            if (!key.contains("fluid")) collectInputs(level, entry.getValue(), result, childOutput);
        }
    }

    private static void collectGenericInputs(Level level, JsonElement element,
            List<appeng.api.stacks.GenericStack> result) {
        if (!(element instanceof JsonObject object)) {
            if (element != null && element.isJsonArray()) for (var child : element.getAsJsonArray()) collectGenericInputs(level, child, result);
            return;
        }
        var resource = object.get("resource");
        if (resource instanceof JsonObject resourceObject && resourceObject.has("key_type")) {
            var stack = resourceStack(level, resourceObject);
            if (stack != null) result.add(stack);
        }
        for (var entry : object.entrySet()) {
            if (isOutputField(entry.getKey().toLowerCase())) continue;
            collectGenericInputs(level, entry.getValue(), result);
        }
    }

    private static appeng.api.stacks.GenericStack resourceStack(Level level, JsonObject resource) {
        var type = text(resource, "key_type");
        return type == null ? null : genericStack(level, type, text(resource, "resource"), number(resource, "amount", 0));
    }

    private static appeng.api.stacks.GenericStack genericStack(Level level, String type, String id, long amount) {
        if (amount <= 0) return null;
        try {
            var object = new JsonObject();
            object.addProperty("#t", type);
            if (id != null) {
                object.addProperty("id", id);
                object.addProperty("resource", id);
            }
            object.addProperty("#", amount);
            var ops = JsonOps.INSTANCE;
            return ForgeRecipeCodecs.GENERIC_STACK.parse(ops, object).result().orElse(null);
        } catch (RuntimeException ignored) { return null; }
    }

    static LightningRequirement lightningRequirement(String type, JsonObject recipe) {
        if (!LIGHTNING_RECIPE_TYPES.contains(type)) return null;
        var tier = text(recipe, "lightningTier");
        return new LightningRequirement(tier == null ? "high_voltage" : tier,
                number(recipe, "lightningCost", type.equals("ae2lt:crystal_catalyzer") ? 0 : 4));
    }

    private static appeng.api.stacks.GenericStack lightningInput(Level level, LightningRequirement requirement) {
        if (requirement == null || requirement.amount() <= 0
                || !"high_voltage".equals(requirement.tier())
                && !"extreme_high_voltage".equals(requirement.tier())) return null;
        try {
            var object = new JsonObject();
            object.addProperty("#t", "ae2lt:lightning");
            object.addProperty("tier", requirement.tier());
            object.addProperty("#", requirement.amount());
            var ops = JsonOps.INSTANCE;
            return ForgeRecipeCodecs.GENERIC_STACK.parse(ops, object).result().orElse(null);
        } catch (RuntimeException ignored) { return null; }
    }

    private static java.util.Optional<Ingredient> parseIngredient(Level level, JsonElement element, int ignored) {
        try {
            DynamicOps<JsonElement> ops = JsonOps.INSTANCE;
            return ForgeRecipeCodecs.INGREDIENT.parse(ops, element).result();
        } catch (RuntimeException ignoredError) { return java.util.Optional.empty(); }
    }

    private static List<FluidStack> findFluidInputs(JsonElement element) {
        var result = new ArrayList<FluidStack>();
        collectFluidInputs(element, result, false, false, 0);
        return result;
    }

    private static void collectFluidInputs(JsonElement element, List<FluidStack> result, boolean outputContext,
            boolean fluidContext, long inheritedAmount) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) collectFluidInputs(child, result, outputContext, fluidContext, inheritedAmount);
            return;
        }
        if (!(element instanceof JsonObject object)) return;
        if (!outputContext && fluidContext) {
            var id = text(object, "fluid");
            if (id == null) id = text(object, "id");
            var fluidId = id == null ? null : ResourceLocation.tryParse(id);
            var tagId = text(object, "tag");
            if (fluidId == null && tagId != null) {
                var tag = ResourceLocation.tryParse(tagId);
                if (tag != null) fluidId = BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, tag))
                        .flatMap(holders -> holders.stream().findFirst())
                        .map(holder -> BuiltInRegistries.FLUID.getKey(holder.value())).orElse(null);
            }
            long amount = number(object, "amount", number(object, "count", number(object, "#", inheritedAmount)));
            if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId) && amount > 0 && amount <= Integer.MAX_VALUE) {
                result.add(new FluidStack(BuiltInRegistries.FLUID.get(fluidId), (int) amount));
                return;
            }
        }
        for (var entry : object.entrySet()) {
            var key = entry.getKey().toLowerCase();
            boolean childOutput = outputContext || isOutputField(key);
            if (childOutput) continue;
            var value = entry.getValue();
            collectFluidInputs(value, result, childOutput, fluidContext || key.contains("fluid"),
                    number(object, "amount", inheritedAmount));
        }
    }

    private static FluidStack findOutputFluid(JsonElement element) {
        return findOutputFluid(element, false);
    }

    private static FluidStack findOutputFluid(JsonElement element, boolean outputContext) {
        if (element == null || element.isJsonNull()) return FluidStack.EMPTY;
        if (element.isJsonArray()) {
            for (var child : element.getAsJsonArray()) {
                var value = findOutputFluid(child, outputContext);
                if (!value.isEmpty()) return value;
            }
            return FluidStack.EMPTY;
        }
        if (!(element instanceof JsonObject object)) return FluidStack.EMPTY;
        if (outputContext) {
            var id = text(object, "fluid");
            if (id == null) id = text(object, "id");
            var fluidId = id == null ? null : ResourceLocation.tryParse(id);
            long amount = number(object, "amount", number(object, "count", number(object, "#", 0)));
            if (fluidId != null && BuiltInRegistries.FLUID.containsKey(fluidId) && amount > 0 && amount <= Integer.MAX_VALUE)
                return new FluidStack(BuiltInRegistries.FLUID.get(fluidId), (int) amount);
        }
        for (var entry : object.entrySet()) {
            var key = entry.getKey();
            if (!outputContext && !isOutputField(key.toLowerCase())) continue;
            var value = findOutputFluid(entry.getValue(), true);
            if (!value.isEmpty()) return value;
        }
        return FluidStack.EMPTY;
    }

    private static int amount(JsonObject object) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, number(object, "amount", number(object, "count", 1))));
    }

    private static boolean isOutputField(String key) {
        return key.contains("output") || key.contains("result")
                || key.contains("byproduct") || key.contains("secondary");
    }

    private static String ingredientKey(Ingredient ingredient) {
        var set = new LinkedHashSet<String>();
        for (var stack : ingredient.getItems()) set.add(stackKey(stack));
        return String.join(";", set);
    }

    private static String stackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "@" + java.util.Objects.hashCode(stack.getTag());
    }

    private static String fluidKey(FluidStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(stack.getFluid()) + "#" + stack.getAmount();
    }

    private static String outputKey(Output output) {
        var items = output.items().stream().map(item -> stackKey(item) + "#" + item.getCount()).sorted().toList();
        var fluids = output.fluids().stream().map(MatterRecipeBridge::fluidKey).sorted().toList();
        var resources = output.resources().stream()
                .map(stack -> stack.what() + "#" + stack.amount()).sorted().toList();
        return items + "|fluids=" + fluids + "|resources=" + resources;
    }

    private static String text(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
    }

    private static long number(JsonObject object, String key, long fallback) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return fallback;
        try { return value.getAsLong(); } catch (RuntimeException ignored) { return fallback; }
    }
}
