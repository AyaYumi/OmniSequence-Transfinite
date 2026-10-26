package com.atir.molecularmanipulator.entropy;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.guisync.PacketWritable;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

/** Server-resolved outputs, not recipe definitions or guesses based on an item's registry name. */
public record NegativeEntropyRecipeCatalog(List<List<Entry>> nodes) implements PacketWritable {
    public static final int NODE_COUNT = 5;
    public static final int MAX_RECIPES_PER_NODE = 8;
    // A Matter recipe may have six item results, one fluid result and six arbitrary AE outputs.
    public static final int MAX_OUTPUTS_PER_RECIPE = MatterFabricationRecipe.MAX_OUTPUTS * 2 + 1;
    public static final NegativeEntropyRecipeCatalog EMPTY = new NegativeEntropyRecipeCatalog(
            java.util.Collections.nCopies(NODE_COUNT, List.of()));

    public record Entry(ResourceLocation id, boolean present, List<GenericStack> outputs) {
        public Entry {
            Objects.requireNonNull(id);
            outputs = List.copyOf(outputs);
            if (outputs.size() > MAX_OUTPUTS_PER_RECIPE || !present && !outputs.isEmpty()
                    || outputs.stream().anyMatch(stack -> stack.amount() <= 0)) {
                throw new IllegalArgumentException("Invalid negative entropy recipe outputs: " + id);
            }
        }
    }

    public NegativeEntropyRecipeCatalog {
        if (nodes.size() != NODE_COUNT) throw new IllegalArgumentException("Expected five entropy nodes");
        nodes = nodes.stream().map(entries -> {
            if (entries.size() > MAX_RECIPES_PER_NODE) throw new IllegalArgumentException("Too many entropy recipes");
            return List.copyOf(entries);
        }).toList();
    }

    /** AE2 CustomField requires a public record constructor taking exactly this buffer type. */
    public NegativeEntropyRecipeCatalog(RegistryFriendlyByteBuf buffer) {
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
        if (buffer.readVarInt() != NODE_COUNT) throw new IllegalArgumentException("Expected five entropy nodes");
        var nodes = new ArrayList<List<Entry>>(NODE_COUNT);
        for (int node = 0; node < NODE_COUNT; node++) {
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

    /** Menu-local, server-only cache. Reload can replace holders without replacing RecipeManager. */
    public static final class ServerCache {
        public static final int CHECK_INTERVAL_TICKS = 10;
        private RecipeManager manager;
        private List<RecipeHolder<?>> holders = List.of();
        private long nextCheck;
        private NegativeEntropyRecipeCatalog catalog = EMPTY;

        public NegativeEntropyRecipeCatalog get(RecipeManager current, HolderLookup.Provider registries, long tick) {
            if (current == manager && tick < nextCheck) return catalog;
            nextCheck = tick + CHECK_INTERVAL_TICKS;
            var currentHolders = new ArrayList<RecipeHolder<?>>();
            for (var node : NegativeEntropyRecipes.NODES) {
                for (var id : node.recipeIds()) currentHolders.add(current.byKey(id).orElse(null));
            }
            boolean changed = current != manager || holders.size() != currentHolders.size();
            for (int index = 0; !changed && index < holders.size(); index++) {
                changed = holders.get(index) != currentHolders.get(index);
            }
            if (!changed) return catalog;
            var nodes = new ArrayList<List<Entry>>(NODE_COUNT);
            int index = 0;
            for (var node : NegativeEntropyRecipes.NODES) {
                var entries = new ArrayList<Entry>();
                for (var id : node.recipeIds()) entries.add(resolve(id, currentHolders.get(index++), registries));
                nodes.add(entries);
            }
            catalog = new NegativeEntropyRecipeCatalog(nodes);
            manager = current;
            // Null holders intentionally represent missing recipes; List.copyOf would reject them.
            holders = currentHolders;
            return catalog;
        }
    }
}
