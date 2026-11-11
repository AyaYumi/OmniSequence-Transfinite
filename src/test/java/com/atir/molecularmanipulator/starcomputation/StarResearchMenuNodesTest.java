package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu.ActionResult;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StarResearchMenuNodesTest {
    private static final List<ResourceLocation> IDS = StarComputationResearchData.LEGACY_NODE_IDS;
    private static final ResourceLocation MATERIAL = IDS.get(1);

    @BeforeAll
    static void bootstrap() { StarResearchNodeRecipeTest.bootstrap(); }

    @Test
    void soleMaterialEntryIsFreeAtLevelThreeAndItsListIndexZeroNeverAliasesLegacyBitZero() {
        var snapshot = StarResearchMenuNodes.from(StarResearchNodes.build(Map.of()), Set.of());
        assertEquals(1, snapshot.nodes().size());
        var node = snapshot.node(0);
        assertEquals(0, node.index());
        assertEquals(MATERIAL, node.id());
        assertEquals(0, snapshot.indexOf(MATERIAL));
        assertEquals(3, node.requiredLevel());
        assertEquals(0, node.informationCost());
        assertEquals("gui.molecularmanipulator.star_computation.node.material", node.title());
        assertTrue(node.valid());
        assertEquals(List.of(), node.prerequisites());
        assertTrue(node.prerequisitesMet());
        assertFalse(node.unlocked());
        assertFalse(node.unlocked(1), "Foundation's permanent bit is not current list index zero");
        assertTrue(node.unlocked(2), "Material keeps permanent legacy bit 1");
        assertEquals(ActionResult.INSUFFICIENT_ENTROPY, preflight(node, 2, 0, 0));
        assertEquals(ActionResult.NONE, preflight(node, 3, 0, 0));
        assertEquals(ActionResult.NONE, preflight(node, 3, 0, 1));
        assertEquals(ActionResult.ALREADY_UNLOCKED, preflight(node, 3, 0, 2));
        assertEquals(snapshot, StarResearchMenuNodes.FALLBACK);
        assertEquals(snapshot, StarResearchMenuNodes.fallback(1));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> node.prerequisites().clear());
        assertEquals(snapshot, roundTrip(snapshot));
        var migrated = roundTrip(StarResearchMenuNodes.fallback(2));
        assertEquals(MATERIAL, migrated.node(0).id());
        assertTrue(migrated.node(0).unlocked());
        assertEquals(ActionResult.ALREADY_UNLOCKED, preflight(migrated.node(0), 3, 0, 0));
    }

    @Test
    void hiddenLegacyAndRemovedCustomProgressDoNotCountAsVisibleOrUnlockedResearch() {
        var data = new StarComputationResearchData();
        var team = UUID.randomUUID();
        int hiddenMask = 31 & ~2;
        data.mergeUnlockMask(team, hiddenMask);
        data.addUnlock(team, ResourceLocation.parse("removed:paid_node"));
        var directory = StarResearchNodes.build(Map.of());
        var snapshot = StarResearchMenuNodes.from(directory, data.getUnlockedNodes(team));
        assertEquals(hiddenMask, data.getUnlockMask(team));
        assertEquals(5, data.getUnlockedNodes(team).size(), "Four hidden legacy IDs and one removed custom ID are retained");
        assertEquals(List.of(MATERIAL), snapshot.nodes().stream().map(StarResearchMenuNodes.Node::id).toList());
        assertEquals(0, snapshot.nodes().stream().filter(StarResearchMenuNodes.Node::unlocked).count());
        for (var hidden : IDS) if (!hidden.equals(MATERIAL)) assertEquals(-1, snapshot.indexOf(hidden));
        assertEquals(StarResearchMenuNodes.FALLBACK, StarResearchMenuNodes.fallback(hiddenMask));
        assertEquals(ActionResult.NONE, preflight(snapshot.node(0), 3, 0, hiddenMask));
        data.addUnlock(team, MATERIAL);
        var paid = StarResearchMenuNodes.from(directory, data.getUnlockedNodes(team));
        assertEquals(1, paid.nodes().size());
        assertEquals(1, paid.nodes().stream().filter(StarResearchMenuNodes.Node::unlocked).count());
        assertEquals(31, data.getUnlockMask(team));
        assertFalse(snapshot.node(0).unlocked(), "Published snapshots do not mutate with saved progress");
    }

    @Test
    void explicitlyDefinedLegacyAndCustomNodesAllAppearWithCurrentTitleLevelPriceAndPrerequisites() {
        var custom = ResourceLocation.parse("pack:first_contact");
        var overrides = new HashMap<ResourceLocation, StarResearchNodeRecipe>();
        overrides.put(custom, recipe("首次接触", 1, 0));
        for (int index = 0; index < IDS.size(); index++) {
            overrides.put(IDS.get(index), recipe("Changed " + index, 2 + index, index == 4 ? Long.MAX_VALUE : index, custom));
        }
        var directory = StarResearchNodes.build(overrides);
        var snapshot = StarResearchMenuNodes.from(directory, Set.of(custom));
        assertEquals(6, snapshot.nodes().size(), "Custom definitions are real entries, not invisible prerequisites");
        assertEquals(overrides.keySet(), new HashSet<>(snapshot.nodes().stream().map(StarResearchMenuNodes.Node::id).toList()));
        for (int index = 0; index < IDS.size(); index++) {
            var node = snapshot.node(snapshot.indexOf(IDS.get(index)));
            assertEquals("Changed " + index, node.title());
            assertEquals(2 + index, node.requiredLevel());
            assertEquals(overrides.get(node.id()).informationCost(), node.informationCost());
            assertEquals(List.of(new StarResearchMenuNodes.Prerequisite(custom, "首次接触", true)), node.prerequisites());
            assertEquals(-1, node.prerequisites().getFirst().legacyIndex());
            assertTrue(node.prerequisitesMet());
            assertEquals(ActionResult.NONE, preflight(node, node.requiredLevel(), node.informationCost(), 0));
            assertEquals(ActionResult.INSUFFICIENT_ENTROPY, preflight(node, node.requiredLevel() - 1, Long.MAX_VALUE, 0));
            if (node.informationCost() > 0) {
                assertEquals(ActionResult.INSUFFICIENT_INFORMATION, preflight(node, 9, node.informationCost() - 1, 0));
            }
        }
        assertTrue(snapshot.node(snapshot.indexOf(custom)).unlocked());
        assertEquals(snapshot, roundTrip(snapshot), "Stable IDs, Long.MAX_VALUE and Unicode titles remain exact");
    }

    @Test
    void cheaperLowerLevelExplicitLegacyNodeWithRemovedOldPrerequisitesIsNotPrematurelyRejected() {
        var directory = StarResearchNodes.build(Map.of(IDS.get(4), recipe("Early transfinite", 1, 0)));
        var snapshot = StarResearchMenuNodes.from(directory, Set.of());
        var node = snapshot.node(snapshot.indexOf(IDS.get(4)));
        assertEquals(1, node.index(), "An explicitly reintroduced legacy ID is not pinned to its permanent save bit");
        assertEquals(ActionResult.NONE, preflight(node, 1, 0, 0), "No old L9 / 65536 / quantum+terminal gate");
        assertEquals(ActionResult.NOT_PERMITTED,
                StarComputationEngineMenu.unlockStatus(node, false, true, true, 9, Long.MAX_VALUE, 0));
        assertEquals(ActionResult.STRUCTURE_INCOMPLETE,
                StarComputationEngineMenu.unlockStatus(node, true, false, true, 9, Long.MAX_VALUE, 0));
        assertEquals(ActionResult.OFFLINE,
                StarComputationEngineMenu.unlockStatus(node, true, true, false, 9, Long.MAX_VALUE, 0));
        assertEquals(ActionResult.NONE, preflight(node, 1, 0, 1 << node.index()), "Material's bit cannot unlock transfinite");
        assertEquals(ActionResult.ALREADY_UNLOCKED, preflight(node, 9, Long.MAX_VALUE, 1 << 4));
    }

    @Test
    void customPrerequisitesReadOnlyOwningTeamPermanentProgressNotLegacyBitsOrAnotherTeamsGrant() {
        var parent = ResourceLocation.parse("pack:own_only");
        var child = ResourceLocation.parse("pack:child");
        var directory = StarResearchNodes.build(Map.of(parent, recipe("Own", 1, 0), child, recipe("Child", 1, 0, parent)));
        var data = new StarComputationResearchData();
        var owningTeam = UUID.randomUUID();
        var otherTeam = UUID.randomUUID();
        data.addUnlock(otherTeam, parent);
        var absent = StarResearchMenuNodes.from(directory, data.getUnlockedNodes(owningTeam));
        var node = absent.node(absent.indexOf(child));
        assertFalse(node.prerequisitesMet());
        assertFalse(node.unlocked(31), "No legacy bit grants an arbitrary custom ID");
        assertEquals(ActionResult.MISSING_PREREQUISITES, preflight(node, 9, Long.MAX_VALUE, 31));
        data.addUnlock(owningTeam, parent);
        var purchased = StarResearchMenuNodes.from(directory, data.getUnlockedNodes(owningTeam));
        assertTrue(purchased.node(purchased.indexOf(child)).prerequisitesMet());
        assertEquals(ActionResult.NONE, preflight(purchased.node(purchased.indexOf(child)), 1, 0, 31));
        assertFalse(node.prerequisitesMet(), "Published snapshots do not mutate when team progress changes");
        var unowned = StarResearchMenuNodes.from(directory, data.getUnlockedNodes(null));
        assertFalse(unowned.node(unowned.indexOf(child)).prerequisitesMet());
    }

    @Test
    void missingCyclicAndMalformedOverridesAndTheirExplicitDependentsStayVisibleButFailClosed() {
        var missing = ResourceLocation.parse("removed:node");
        var loop = ResourceLocation.parse("pack:loop");
        var downstream = ResourceLocation.parse("pack:downstream");
        for (var invalid : List.of(recipe("Missing", 1, 0, missing), recipe("Cycle", 1, 0, loop), recipe("Bad level", 10, -1))) {
            var directory = StarResearchNodes.build(Map.of(MATERIAL, invalid, loop, recipe("Loop", 1, 0, MATERIAL),
                    downstream, recipe("Downstream", 1, 0, MATERIAL)));
            var snapshot = StarResearchMenuNodes.from(directory, Set.of(missing, loop));
            var node = snapshot.node(snapshot.indexOf(MATERIAL));
            assertFalse(node.valid());
            assertFalse(snapshot.node(snapshot.indexOf(downstream)).valid(), "Explicit dependents retain the catalog error");
            assertEquals(ActionResult.INVALID_NODE, preflight(node, 9, Long.MAX_VALUE, 0));
            assertEquals(snapshot, roundTrip(snapshot), "Bad definitions remain diagnostic data, never fallback grants");
        }
    }

    @Test
    void snapshotAndPreflightMatchUnifiedDirectoryAcrossLevelsPricesAndFullIdProgressSets() {
        var parent = ResourceLocation.parse("pack:prerequisite");
        var directory = StarResearchNodes.build(Map.of(parent, recipe("Parent", 1, 0),
                IDS.get(0), recipe("New foundation", 4, 99, parent), MATERIAL, recipe("New material", 1, 0),
                IDS.get(3), recipe("New terminal", 2, Long.MAX_VALUE, MATERIAL, parent)));
        for (var permanent : List.of(Set.<ResourceLocation>of(), Set.of(parent), Set.of(parent, MATERIAL), Set.copyOf(IDS))) {
            var snapshot = StarResearchMenuNodes.from(directory, permanent);
            int mask = 0;
            for (int index = 0; index < IDS.size(); index++) if (permanent.contains(IDS.get(index))) mask |= 1 << index;
            for (int level = 0; level <= 9; level++) for (long balance : new long[] {0, 98, 99, Long.MAX_VALUE}) {
                for (var node : snapshot.nodes()) {
                    boolean expected = directory.canUnlock(node.id(), level, permanent) && balance >= node.informationCost();
                    assertEquals(expected, preflight(node, level, balance, mask) == ActionResult.NONE,
                            node.id() + " at L" + level + " with balance " + balance);
                }
            }
        }
    }

    @Test
    void sameManagerReloadRestoresTheFreeMaterialAtListIndexZeroWithoutCachingTeamProgress() {
        var manager = new net.minecraft.world.item.crafting.RecipeManager(RegistryAccess.EMPTY) {
            @Override public java.util.Collection<net.minecraft.world.item.crafting.RecipeHolder<?>> getRecipes() { return holders; }
            final List<net.minecraft.world.item.crafting.RecipeHolder<?>> holders = new ArrayList<>();
        };
        manager.holders.add(new net.minecraft.world.item.crafting.RecipeHolder<>(MATERIAL, recipe("Paid override", 1, 7)));
        var before = StarResearchMenuNodes.from(StarResearchNodes.catalog(manager, 900), Set.of());
        assertEquals(7, before.node(0).informationCost());
        manager.holders.clear();
        var after = StarResearchMenuNodes.from(StarResearchNodes.catalog(manager, 901), Set.of());
        assertEquals(MATERIAL, after.node(0).id());
        assertEquals(0, after.node(0).informationCost());
        assertEquals(3, after.node(0).requiredLevel());
        assertTrue(after.node(0).prerequisitesMet());
        assertEquals(ActionResult.NONE, preflight(after.node(0), 3, 0, 0));
        assertNotEquals(before, after);
        assertEquals(7, before.node(0).informationCost());
    }

    @Test
    void realAe2GuiSyncField28RoundTripsMoreThan32NodesAndCustomUnlockedFlagsAndOnlySendsVisibleChanges() {
        var custom = ResourceLocation.parse("pack:zz_custom");
        var definitions = new HashMap<ResourceLocation, StarResearchNodeRecipe>();
        for (int index = 0; index < 64; index++) {
            definitions.put(ResourceLocation.parse("pack:node_" + index), recipe("Node " + index, 1, 0));
        }
        definitions.put(custom, recipe("自定义研究", 1, 0));
        definitions.put(MATERIAL, recipe("Override", 2, Long.MAX_VALUE, custom));
        var directory = StarResearchNodes.build(definitions);
        var server = new SyncedView();
        var client = new SyncedView();
        server.researchNodes = StarResearchMenuNodes.from(directory, Set.of());
        int customIndex = server.researchNodes.indexOf(custom);
        assertTrue(customIndex > 32);
        var outgoing = new DataSynchronization(server);
        var incoming = new DataSynchronization(client);
        var buffer = buffer();
        try {
            outgoing.writeFull(buffer);
            var changed = new ShortOpenHashSet();
            incoming.readUpdate(buffer, changed);
            assertTrue(changed.contains((short) 28));
            assertEquals(definitions.size(), client.researchNodes.nodes().size());
            assertEquals(server.researchNodes, client.researchNodes);
            assertFalse(client.researchNodes.node(0).prerequisitesMet());
            assertFalse(client.researchNodes.node(customIndex).unlocked(31));
            assertFalse(outgoing.hasChanges());
            server.researchNodes = StarResearchMenuNodes.from(directory, Set.of(IDS.get(0)));
            assertFalse(outgoing.hasChanges(), "Hidden legacy progress does not produce a visible GuiSync change");
            server.researchNodes = StarResearchMenuNodes.from(directory, Set.of(custom));
            assertTrue(outgoing.hasChanges());
            buffer.clear();
            changed.clear();
            outgoing.writeUpdate(buffer);
            incoming.readUpdate(buffer, changed);
            assertTrue(changed.contains((short) 28));
            assertTrue(client.researchNodes.node(0).prerequisitesMet());
            var purchased = client.researchNodes.node(customIndex);
            assertEquals(custom, purchased.id());
            assertTrue(purchased.unlocked());
            assertEquals(ActionResult.ALREADY_UNLOCKED, preflight(purchased, 9, Long.MAX_VALUE, 0));
            assertEquals(1, client.researchNodes.nodes().stream().filter(StarResearchMenuNodes.Node::unlocked).count());
            assertEquals(server.researchNodes, client.researchNodes);
            server.researchNodes = StarResearchMenuNodes.from(directory, Set.of(custom));
            assertFalse(outgoing.hasChanges(), "Equal dynamic snapshots are not retransmitted");
        } finally { buffer.release(); }
    }

    @Test
    void packetAcceptsVariableNodeCountsButRejectsImpossibleNodeOrPrerequisiteAllocations() {
        for (int count : new int[] {0, 1, 4, 6, 97}) {
            var nodes = new ArrayList<StarResearchMenuNodes.Node>();
            for (int index = 0; index < count; index++) nodes.add(new StarResearchMenuNodes.Node(index,
                    ResourceLocation.parse("pack:node_" + index), "Node", 1, 0, List.of(), "", index > 32));
            var snapshot = new StarResearchMenuNodes(nodes);
            assertEquals(snapshot, roundTrip(snapshot));
        }
        for (int count : new int[] {-1, 1, 4, 6, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(count); // Positive counts are impossible without entry bytes, not because of a five-slot cap.
                assertThrows(IllegalArgumentException.class, () -> new StarResearchMenuNodes(buffer));
            } finally { buffer.release(); }
        }
        for (int count : new int[] {-1, 96, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(1);
                buffer.writeResourceLocation(MATERIAL);
                buffer.writeUtf("Node"); buffer.writeVarInt(1); buffer.writeLong(0); buffer.writeUtf("");
                buffer.writeBoolean(false);
                buffer.writeVarInt(count); // No actual prerequisites follow.
                assertThrows(IllegalArgumentException.class, () -> new StarResearchMenuNodes(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void moreThan32PrerequisitesAndAllTheirDefinitionsRoundTripWithoutAliasingLegacyBits() {
        var definitions = new HashMap<ResourceLocation, StarResearchNodeRecipe>();
        var parents = new ArrayList<ResourceLocation>();
        for (int index = 0; index < 96; index++) {
            var id = ResourceLocation.parse("pack:parent_" + index);
            parents.add(id);
            definitions.put(id, recipe("Parent " + index, 1, 0));
        }
        definitions.put(MATERIAL, new StarResearchNodeRecipe("Many", 1, 0, parents));
        var snapshot = StarResearchMenuNodes.from(StarResearchNodes.build(definitions), Set.copyOf(parents));
        assertEquals(97, snapshot.nodes().size());
        assertEquals(96, snapshot.node(0).prerequisites().size());
        assertTrue(snapshot.node(0).prerequisites().stream().allMatch(StarResearchMenuNodes.Prerequisite::unlocked));
        assertEquals(ActionResult.NONE, preflight(snapshot.node(0), 1, 0, 0));
        assertEquals(96, snapshot.nodes().stream().filter(StarResearchMenuNodes.Node::unlocked).count());
        assertEquals(snapshot, roundTrip(snapshot));
        var incomplete = new HashSet<>(parents);
        incomplete.remove(parents.getLast());
        assertEquals(ActionResult.MISSING_PREREQUISITES,
                preflight(StarResearchMenuNodes.from(StarResearchNodes.build(definitions), incomplete).node(0), 1, 0, 0));
    }

    @Test
    void serverHandlerRereadsAuthoritativeInputsAndPaysThroughStableIdRatherThanSyncedListPosition() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/menu/StarComputationEngineMenu.java"));
        assertTrue(source.contains("registerClientAction(ACTION_UNLOCK, String.class, this::unlockRecipe)"));
        assertNotNull(StarComputationEngineMenu.class.getMethod("requestUnlock", ResourceLocation.class));
        assertNotNull(StarComputationEngineMenu.class.getMethod("requestUnlock", int.class));
        String requests = source.substring(source.indexOf("public void requestUnlock(int index)"), source.indexOf("public void requestReclaim()"));
        assertTrue(requests.contains("requestUnlock(node(index).id())"));
        assertTrue(requests.contains("sendClientAction(ACTION_UNLOCK, id.toString())"));
        String handler = source.substring(source.indexOf("private void unlockRecipe(String name)"), source.indexOf("private void finish("));
        assertTrue(handler.contains("ResourceLocation.tryParse(name)"));
        assertTrue(handler.contains("StarResearchNodes.catalog(core.getLevel())"));
        assertTrue(handler.contains("core.getUnlockedResearchIds()"));
        assertTrue(handler.contains("current.indexOf(id)"));
        assertTrue(handler.contains("core.getBenefits().level()"));
        assertTrue(handler.contains("core.getInfoBalance()"));
        assertTrue(handler.contains("core.tryUnlockResearch((ServerPlayer) getPlayer(), id)"));
        assertFalse(handler.contains("core.unlockRecipe("));
        assertFalse(handler.contains("core.getUnlockMask()"));
        assertFalse(handler.contains("NODES.get("));
        assertFalse(handler.contains("researchNodes.node("));
        assertFalse(handler.contains("getResearchEntropyThreshold("));
        assertFalse(handler.contains("getEffectiveEntropy("));
        assertFalse(handler.contains("addUnlock("));
    }

    public static final class SyncedView {
        @GuiSync(28) public StarResearchMenuNodes researchNodes = StarResearchMenuNodes.FALLBACK;
    }

    private static ActionResult preflight(StarResearchMenuNodes.Node node, int level, long information, int mask) {
        return StarComputationEngineMenu.unlockStatus(node, true, true, true, level, information, mask);
    }

    private static StarResearchNodeRecipe recipe(String title, int level, long cost, ResourceLocation... parents) {
        return new StarResearchNodeRecipe(title, level, cost, List.of(parents));
    }

    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }

    private static StarResearchMenuNodes roundTrip(StarResearchMenuNodes source) {
        var buffer = buffer();
        try {
            source.writeToPacket(buffer);
            var result = new StarResearchMenuNodes(buffer);
            assertEquals(0, buffer.readableBytes());
            return result;
        } finally { buffer.release(); }
    }
}
