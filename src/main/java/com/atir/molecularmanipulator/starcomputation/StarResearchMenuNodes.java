package com.atir.molecularmanipulator.starcomputation;

import appeng.menu.guisync.PacketWritable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Current definitions and owning-team permanent progress. List indices are transient, never save bits. */
public record StarResearchMenuNodes(List<Node> nodes) implements PacketWritable {
    private static final StarResearchNodes.Catalog DEFAULTS = StarResearchNodes.build(Map.of());
    public static final StarResearchMenuNodes FALLBACK = from(DEFAULTS, Set.of());

    public record Prerequisite(ResourceLocation id, String title, boolean unlocked) {
        public Prerequisite {
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
        }
        public int legacyIndex() { return StarComputationResearchData.LEGACY_NODE_IDS.indexOf(id); }
    }

    public record Node(int index, ResourceLocation id, String title, int requiredLevel, long informationCost,
                       List<Prerequisite> prerequisites, String error, boolean unlocked) {
        public Node {
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
            Objects.requireNonNull(error);
            prerequisites = List.copyOf(prerequisites);
            if (index < 0) throw new IllegalArgumentException("Invalid research list index: " + index);
            // Invalid Java/KubeJS definitions must still be visible, but never purchasable.
            if (error.isEmpty() && (requiredLevel < 1 || requiredLevel > 9 || informationCost < 0)) {
                throw new IllegalArgumentException("Invalid research menu definition");
            }
        }

        /** Source compatibility for fixtures that explicitly construct a legacy node, not a dynamic list entry. */
        public Node(int legacyIndex, String title, int requiredLevel, long informationCost,
                    List<Prerequisite> prerequisites, String error) {
            this(legacyIndex, StarComputationResearchData.LEGACY_NODE_IDS.get(legacyIndex), title, requiredLevel,
                    informationCost, prerequisites, error, false);
        }

        /** Legacy masks describe IDs, never this snapshot's list position. Custom IDs use the synced flag only. */
        public boolean unlocked(int legacyMask) {
            int legacyIndex = StarComputationResearchData.LEGACY_NODE_IDS.indexOf(id);
            return unlocked || legacyIndex >= 0 && (legacyMask & (1 << legacyIndex)) != 0;
        }
        public boolean valid() { return error.isEmpty(); }
        public boolean prerequisitesMet() { return prerequisites.stream().allMatch(Prerequisite::unlocked); }
    }

    public StarResearchMenuNodes {
        nodes = List.copyOf(nodes);
        var ids = new HashSet<ResourceLocation>();
        for (int index = 0; index < nodes.size(); index++) {
            var node = nodes.get(index);
            if (node.index() != index) throw new IllegalArgumentException("Research indices must match list order");
            if (!ids.add(node.id())) throw new IllegalArgumentException("Duplicate research node: " + node.id());
        }
    }

    /** AE2 CustomMenuSyncSerializer requires this exact public buffer constructor. */
    public StarResearchMenuNodes(RegistryFriendlyByteBuf buffer) { this(readNodes(buffer)); }

    public Node node(int index) { return nodes.get(index); }

    /** Returns the transient list position, or -1 when this snapshot does not define the ID. */
    public int indexOf(ResourceLocation id) {
        for (int index = 0; index < nodes.size(); index++) if (nodes.get(index).id().equals(id)) return index;
        return -1;
    }

    /** The set MUST come from core.getUnlockedResearchIds(), never the ME network's borrowed authorization. */
    public static StarResearchMenuNodes from(StarResearchNodes.Catalog catalog, Set<ResourceLocation> permanentUnlocks) {
        var nodes = new ArrayList<Node>(catalog.nodes().size());
        for (var entry : catalog.nodes().entrySet()) {
            var id = entry.getKey();
            var recipe = entry.getValue();
            String error = catalog.error(id);
            var prerequisites = recipe.prerequisites().stream().distinct().map(parent -> {
                var definition = catalog.nodes().get(parent);
                return new Prerequisite(parent, title(parent, definition), permanentUnlocks.contains(parent));
            }).toList();
            nodes.add(new Node(nodes.size(), id, title(id, recipe), recipe.requiredLevel(), recipe.informationCost(),
                    prerequisites, error == null ? "" : error, permanentUnlocks.contains(id)));
        }
        return new StarResearchMenuNodes(nodes);
    }

    /** Default research view before the server synchronizes the current directory and permanent progress. */
    public static StarResearchMenuNodes fallback(int unlockMask) {
        if (unlockMask == 0) return FALLBACK;
        var ids = StarComputationResearchData.LEGACY_NODE_IDS;
        var unlocked = new HashSet<ResourceLocation>();
        for (int index = 0; index < ids.size(); index++) if ((unlockMask & (1 << index)) != 0) unlocked.add(ids.get(index));
        return from(DEFAULTS, unlocked);
    }

    private static String title(ResourceLocation id, StarResearchNodeRecipe recipe) {
        return recipe == null || recipe.title() == null || recipe.title().isBlank() ? id.toString() : recipe.title();
    }

    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(nodes.size());
        for (var node : nodes) {
            buffer.writeResourceLocation(node.id());
            buffer.writeUtf(node.title());
            buffer.writeVarInt(node.requiredLevel());
            buffer.writeLong(node.informationCost());
            buffer.writeUtf(node.error());
            buffer.writeBoolean(node.unlocked());
            buffer.writeVarInt(node.prerequisites().size());
            for (var parent : node.prerequisites()) {
                buffer.writeResourceLocation(parent.id());
                buffer.writeUtf(parent.title());
                buffer.writeBoolean(parent.unlocked());
            }
        }
    }

    private static List<Node> readNodes(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        // Each entry consumes at least one byte; bound allocation by the packet rather than a fixed node cap.
        if (count < 0 || count > buffer.readableBytes()) {
            throw new IllegalArgumentException("Invalid research node count: " + count);
        }
        var nodes = new ArrayList<Node>(count);
        for (int index = 0; index < count; index++) {
            var id = buffer.readResourceLocation();
            String title = buffer.readUtf();
            int level = buffer.readVarInt();
            long cost = buffer.readLong();
            String error = buffer.readUtf();
            boolean unlocked = buffer.readBoolean();
            int size = buffer.readVarInt();
            // Each prerequisite consumes at least one byte. Bound allocation by this packet, not a node-count cap.
            if (size < 0 || size > buffer.readableBytes()) throw new IllegalArgumentException("Invalid prerequisite count: " + size);
            var prerequisites = new ArrayList<Prerequisite>(size);
            for (int parent = 0; parent < size; parent++) {
                prerequisites.add(new Prerequisite(buffer.readResourceLocation(), buffer.readUtf(), buffer.readBoolean()));
            }
            nodes.add(new Node(index, id, title, level, cost, prerequisites, error, unlocked));
        }
        return nodes;
    }
}
