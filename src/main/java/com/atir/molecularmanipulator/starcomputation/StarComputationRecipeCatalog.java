package com.atir.molecularmanipulator.starcomputation;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.guisync.PacketWritable;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.recipelock.RecipeLockRecipe;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/** Server-resolved outputs in current research-list order; contains no cached team/network authorization. */
public record StarComputationRecipeCatalog(List<List<Entry>> nodes) implements PacketWritable {
    public static final int MAX_RECIPES_PER_NODE = 8;
    // A Matter recipe may have six item results, one fluid result and six arbitrary AE outputs.
    public static final int MAX_OUTPUTS_PER_RECIPE = MatterFabricationRecipe.MAX_OUTPUTS * 2 + 1;
    public static final StarComputationRecipeCatalog EMPTY = new StarComputationRecipeCatalog(List.of());

    public record Entry(ResourceLocation id, boolean present, List<GenericStack> outputs) {
        public Entry {
            Objects.requireNonNull(id);
            outputs = List.copyOf(outputs);
            if (outputs.size() > MAX_OUTPUTS_PER_RECIPE || !present && !outputs.isEmpty()
                    || outputs.stream().anyMatch(stack -> stack.amount() <= 0)) {
                throw new IllegalArgumentException("Invalid star computation recipe outputs: " + id);
            }
        }
    }

    public StarComputationRecipeCatalog {
        nodes = nodes.stream().map(entries -> {
            if (entries.size() > MAX_RECIPES_PER_NODE) throw new IllegalArgumentException("Too many entropy recipes");
            return List.copyOf(entries);
        }).toList();
    }

    /** AE2 CustomField requires a public record constructor taking exactly this buffer type. */
    public StarComputationRecipeCatalog(RegistryFriendlyByteBuf buffer) {
        this(readNodes(buffer));
    }

    public List<Entry> recipes(int node) {
        return node >= 0 && node < nodes.size() ? nodes.get(node) : List.of();
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(nodes.size());
        for (var entries : nodes) {
            buffer.writeVarInt(entries.size());
            for (var entry : entries) {
                buffer.writeResourceLocation(entry.id());
                buffer.writeBoolean(entry.present());
                buffer.writeVarInt(entry.outputs().size());
                for (var output : entry.outputs()) GenericStack.STREAM_CODEC.encode(buffer, output);
            }
        }
    }

    private static List<List<Entry>> readNodes(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        // Even an empty per-node list consumes a byte. No arbitrary limit on the research directory's size.
        if (count < 0 || count > buffer.readableBytes()) throw new IllegalArgumentException("Invalid research node count: " + count);
        var nodes = new ArrayList<List<Entry>>(count);
        for (int node = 0; node < count; node++) {
            int size = boundedSize(buffer, MAX_RECIPES_PER_NODE);
            var entries = new ArrayList<Entry>(size);
            for (int index = 0; index < size; index++) {
                var id = buffer.readResourceLocation();
                boolean present = buffer.readBoolean();
                int outputCount = boundedSize(buffer, MAX_OUTPUTS_PER_RECIPE);
                var outputs = new ArrayList<GenericStack>(outputCount);
                for (int output = 0; output < outputCount; output++) {
                    outputs.add(GenericStack.STREAM_CODEC.decode(buffer));
                }
                entries.add(new Entry(id, present, outputs));
            }
            nodes.add(entries);
        }
        return nodes;
    }

    private static int boundedSize(RegistryFriendlyByteBuf buffer, int maximum) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Invalid entropy catalog list size: " + size);
        return size;
    }

    private static Entry resolve(ResourceLocation id, RecipeHolder<?> holder, HolderLookup.Provider registries) {
        var outputs = new ArrayList<GenericStack>();
        if (holder != null) {
            if (holder.value() instanceof MatterFabricationRecipe recipe) {
                for (var stack : recipe.results()) if (!stack.isEmpty()) {
                    outputs.add(new GenericStack(AEItemKey.of(stack), stack.getCount()));
                }
                if (!recipe.fluidResult().isEmpty()) {
                    outputs.add(new GenericStack(AEFluidKey.of(recipe.fluidResult()), recipe.fluidResult().getAmount()));
                }
                outputs.addAll(recipe.aeOutputs());
            } else {
                // A data pack can replace a gated recipe with an ordinary crafting recipe.
                var stack = holder.value().getResultItem(registries);
                if (!stack.isEmpty()) outputs.add(new GenericStack(AEItemKey.of(stack), stack.getCount()));
            }
        }
        return new Entry(id, holder != null, outputs);
    }

    /** Menu-local, server-only definition cache. Reload can replace holders without replacing RecipeManager. */
    public static final class ServerCache {
        private RecipeManager manager;
        private Collection<RecipeHolder<?>> source;
        private long generation = Long.MIN_VALUE;
        private StarResearchNodes.Catalog directory;
        private StarComputationRecipeCatalog catalog = EMPTY;

        public StarComputationRecipeCatalog get(Level level, long tick) {
            return get(level, StarResearchNodes.catalog(level), tick);
        }

        /** Pass the same directory used for GuiSync 28 so every preview index addresses that exact list. */
        public StarComputationRecipeCatalog get(Level level, StarResearchNodes.Catalog currentNodes, long tick) {
            var current = level.getRecipeManager();
            var currentSource = current.getRecipes();
            long currentGeneration = PatternRecipeReloads.generation();
            // Cheap identity checks run every call; no polling delay may publish previews for a stale node list.
            if (current == manager && source == currentSource && generation == currentGeneration
                    && directory == currentNodes) return catalog;

            var recipes = new TreeMap<ResourceLocation, RecipeHolder<?>>();
            for (var holder : currentSource) recipes.put(holder.id(), holder);
            // Imported fabrication holders are outside RecipeManager and also inherit their source ID's locks.
            for (var holder : MatterRecipeIndex.get(level).fabrication()) recipes.putIfAbsent(holder.id(), holder);
            // Preserve diagnostics for missing exact targets, but let RecipeLocks decide whether selectors match.
            for (var holder : currentSource) if (holder.value() instanceof RecipeLockRecipe lock && lock.match() != null) {
                for (var id : lock.match().recipeIds()) recipes.putIfAbsent(id, null);
            }

            var byNode = new LinkedHashMap<ResourceLocation, List<Entry>>();
            currentNodes.nodes().keySet().forEach(id -> byNode.put(id, new ArrayList<>()));
            for (var recipe : recipes.entrySet()) {
                var holder = recipe.getValue();
                var requirements = holder == null ? RecipeLocks.requirements(level, recipe.getKey())
                        : RecipeLocks.requirements(level, holder);
                var matchingNodes = new LinkedHashSet<ResourceLocation>();
                for (var rule : requirements) matchingNodes.addAll(rule.nodes());
                Entry resolved = null;
                for (var id : matchingNodes) {
                    var entries = byNode.get(id);
                    if (entries == null || entries.size() >= MAX_RECIPES_PER_NODE) continue;
                    if (resolved == null) resolved = resolve(recipe.getKey(), holder, level.registryAccess());
                    entries.add(resolved);
                }
            }
            catalog = new StarComputationRecipeCatalog(new ArrayList<>(byNode.values()));
            manager = current;
            source = currentSource;
            generation = currentGeneration;
            directory = currentNodes;
            return catalog;
        }
    }
}
