package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.Entry;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.Row;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades.Evaluation;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StarComputationUpgradeCatalogTest {
    @BeforeAll
    static void bootstrap() { StarComputationUpgradeRecipeTest.bootstrap(); }

    @Test
    void fixedNineLevelsExposeAutomaticCostsAndRulesWithoutFallbacks() {
        var manager = new StarComputationUpgradesTest.ReloadableRecipes();
        manager.replaceRecipes(List.of(
                new RecipeHolder<>(ResourceLocation.parse("kubejs:ignored_auto"), StarComputationUpgradesTest.recipe(1, 999)),
                new RecipeHolder<>(ResourceLocation.parse("kubejs:four"), StarComputationUpgradesTest.recipe(4, 4096)),
                new RecipeHolder<>(ResourceLocation.parse("kubejs:nine"), StarComputationUpgradesTest.recipe(9, 9000))));
        var rows = List.of(new Row(0, true, StarComputationUpgrades.text("group.all", 1, 1, 1)));
        var catalog = StarComputationUpgrades.catalog(manager, rule -> new Evaluation(rule.recipe().level() == 4, rows));
        assertEquals(9, catalog.entries().size());
        for (int level = 1; level <= 3; level++) {
            assertTrue(catalog.entry(level).configured());
            assertEquals(0, catalog.entry(level).energy());
            assertTrue(catalog.entry(level).conditionsMet());
        }
        assertTrue(catalog.entry(4).configured());
        assertEquals(4096, catalog.entry(4).energy());
        assertEquals(256, catalog.entry(4).aePerTick());
        assertTrue(catalog.entry(4).conditionsMet());
        for (int level = 5; level <= 8; level++) assertFalse(catalog.entry(level).configured());
        assertTrue(catalog.entry(9).configured());
        assertFalse(catalog.entry(9).conditionsMet());
        assertEquals(catalog, roundTrip(catalog));
        manager.holders.add(new RecipeHolder<>(ResourceLocation.parse("other:four"), StarComputationUpgradesTest.recipe(4, 0)));
        var conflict = StarComputationUpgrades.catalog(manager, rule -> new Evaluation(true, rows));
        assertFalse(conflict.entry(4).configured());
        assertFalse(conflict.entry(4).conditionsMet());
        assertTrue(conflict.entry(4).rows().toString().contains("upgrade.row.conflict"));
        manager.replaceRecipes(List.of());
        assertEquals(StarComputationUpgradeCatalog.EMPTY, StarComputationUpgrades.catalog(manager,
                ignored -> { fail("Removed/automatic definitions must not be evaluated"); return null; }));
    }

    @Test
    void packetRoundTripRetainsRealNamesTranslationsTitlesAndUnavailableReasons() {
        var rows = List.of(
                new Row(0, false, StarComputationUpgrades.text("row.research"),
                        List.of(StarComputationUpgrades.text("condition.research", StarComputationUpgrades.text("match.all"),
                                1, 2, StarComputationUpgrades.text("research_same")))),
                new Row(1, true, Component.translatable("block.minecraft.furnace"),
                        List.of(StarComputationUpgrades.text("detail.machines", Component.translatable("block.minecraft.furnace"), 3, 3))),
                new Row(2, false, Component.literal("Actual FTB quest title"),
                        List.of(StarComputationUpgrades.text("detail.ftb_quests", Component.literal("Actual FTB quest title"), 0, 1),
                                StarComputationUpgrades.text("detail.ftb_unavailable"))),
                new Row(2, false, Component.translatable("research.kubejs.actual_title"),
                        List.of(StarComputationUpgrades.text("detail.research", Component.translatable("research.kubejs.actual_title"), 0, 2),
                                StarComputationUpgrades.text("incomplete"))));
        var entries = new ArrayList<>(StarComputationUpgradeCatalog.EMPTY.entries());
        entries.set(3, new Entry(4, true, 0, 1, false, rows));
        var catalog = new StarComputationUpgradeCatalog(entries);
        assertEquals(catalog, roundTrip(catalog));
        var decoded = roundTrip(catalog).entry(4).rows();
        assertEquals(List.of(0, 1, 2, 2), decoded.stream().map(Row::depth).toList());
        assertTrue(decoded.toString().contains("Actual FTB quest title"));
        assertTrue(decoded.toString().contains("research.kubejs.actual_title"));
        assertTrue(decoded.toString().contains("block.minecraft.furnace"));
        assertTrue(decoded.toString().contains("detail.ftb_unavailable"));
        assertEquals(rows.get(2).details(), decoded.get(2).details(), "Literal tooltip arguments must not cause GuiSync snapshot churn");
    }

    @Test
    void ae2GuiSyncUsesPublicBufferConstructorAndDetectsOnlyChangedSnapshots() {
        var server = new SyncedView(); var client = new SyncedView();
        var entries = new ArrayList<>(server.catalog.entries());
        entries.set(8, new Entry(9, true, 1024, 32, true,
                List.of(new Row(0, true, StarComputationUpgrades.text("automatic")))));
        server.catalog = new StarComputationUpgradeCatalog(entries);
        var outgoing = new DataSynchronization(server); var incoming = new DataSynchronization(client);
        assertTrue(outgoing.hasChanges());
        var buffer = buffer();
        try {
            outgoing.writeFull(buffer);
            var changed = new ShortOpenHashSet();
            incoming.readUpdate(buffer, changed);
            assertTrue(changed.contains((short) 28));
            assertEquals(server.catalog, client.catalog);
            assertFalse(outgoing.hasChanges());
            server.catalog = new StarComputationUpgradeCatalog(new ArrayList<>(server.catalog.entries()));
            assertFalse(outgoing.hasChanges());
            server.catalog = StarComputationUpgradeCatalog.EMPTY;
            assertTrue(outgoing.hasChanges());
            buffer.clear(); outgoing.writeUpdate(buffer); incoming.readUpdate(buffer, changed);
            assertEquals(StarComputationUpgradeCatalog.EMPTY, client.catalog);
        } finally { buffer.release(); }
    }

    public static final class SyncedView {
        @GuiSync(28) public StarComputationUpgradeCatalog catalog = StarComputationUpgradeCatalog.EMPTY;
    }

    @Test
    void constructorAndPacketRejectInvalidLengthsDepthOrderingAndCostsBeforeAllocatingRows() {
        assertThrows(IllegalArgumentException.class, () -> new StarComputationUpgradeCatalog(List.of()));
        assertThrows(IllegalArgumentException.class, () -> StarComputationUpgradeCatalog.EMPTY.entry(0));
        assertThrows(IllegalArgumentException.class, () -> StarComputationUpgradeCatalog.EMPTY.entry(10));
        assertThrows(IllegalArgumentException.class, () -> new Row(-1, false, Component.empty()));
        assertThrows(IllegalArgumentException.class, () -> new Entry(4, true, 0, 0, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Entry(4, true, Double.NaN, 1, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Entry(4, true, 0, Double.POSITIVE_INFINITY, false, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Entry(4, false, 0, 0, true, List.of()));
        for (int size : new int[] {-1, 0, 8, 10, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new StarComputationUpgradeCatalog(buffer));
            } finally { buffer.release(); }
        }
        for (int size : new int[] {-1, StarComputationUpgradeCatalog.MAX_ROWS + 1, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                header(buffer); buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new StarComputationUpgradeCatalog(buffer));
            } finally { buffer.release(); }
        }
        var buffer = buffer();
        try {
            header(buffer); buffer.writeVarInt(1); buffer.writeVarInt(StarComputationUpgradeRecipe.MAX_DEPTH + 2);
            assertThrows(IllegalArgumentException.class, () -> new StarComputationUpgradeCatalog(buffer));
            buffer.clear(); buffer.writeVarInt(9); buffer.writeVarInt(2);
            assertThrows(IllegalArgumentException.class, () -> new StarComputationUpgradeCatalog(buffer));
        } finally { buffer.release(); }
        var entries = new ArrayList<>(StarComputationUpgradeCatalog.EMPTY.entries());
        entries.set(0, new Entry(1, true, 1, 1, true, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new StarComputationUpgradeCatalog(entries));
    }

    @Test
    void autoAssemblyAndLowLevelMaterialFailuresSurviveGuiSync() {
        var entries = new ArrayList<>(StarComputationUpgradeCatalog.EMPTY.entries());
        entries.set(0, new Entry(1, true, 0, 1, false,
                List.of(new Row(0, false, StarComputationUpgrades.text("row.anchors"))), true));
        entries.set(3, new Entry(4, true, 0, 1, true, List.of(), true));
        var catalog = roundTrip(new StarComputationUpgradeCatalog(entries));
        assertTrue(catalog.entry(1).autoBuild());
        assertFalse(catalog.entry(1).conditionsMet());
        assertTrue(catalog.entry(4).autoBuild());
        assertTrue(catalog.entry(4).conditionsMet());
        assertFalse(catalog.entry(5).autoBuild());
    }

    @Test
    void entriesRowsAndEvaluationAreImmutableSnapshots() {
        var details = new ArrayList<>(List.of(StarComputationUpgrades.text("automatic")));
        var rows = new ArrayList<>(List.of(new Row(0, true, StarComputationUpgrades.text("row.automatic"), details)));
        var entry = new Entry(4, true, 0, 1, true, rows);
        var evaluation = new Evaluation(true, rows);
        var entries = new ArrayList<>(StarComputationUpgradeCatalog.EMPTY.entries()); entries.set(3, entry);
        var catalog = new StarComputationUpgradeCatalog(entries);
        rows.clear(); entries.clear(); details.clear();
        assertEquals(1, catalog.entry(4).rows().getFirst().details().size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.entry(4).rows().getFirst().details().clear());
        assertEquals(1, catalog.entry(4).rows().size());
        assertEquals(1, evaluation.rows().size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> entry.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> evaluation.rows().clear());
    }

    private static void header(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(9); buffer.writeVarInt(1); buffer.writeBoolean(true);
        buffer.writeDouble(0); buffer.writeDouble(1); buffer.writeBoolean(true); buffer.writeBoolean(false);
    }

    private static StarComputationUpgradeCatalog roundTrip(StarComputationUpgradeCatalog catalog) {
        var buffer = buffer();
        try {
            catalog.writeToPacket(buffer);
            var decoded = new StarComputationUpgradeCatalog(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally { buffer.release(); }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
}
