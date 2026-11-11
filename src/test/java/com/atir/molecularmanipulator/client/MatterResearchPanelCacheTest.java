package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MatterResearchPanelCacheTest {
    @BeforeAll static void bootstrap() throws Exception { Class.forName(UiRenderRecorder.class.getName()); }

    @org.junit.jupiter.api.BeforeAll static void initializeCodecRegistry() throws Exception {
        // These tests use the production GenericStack codec; JUnit does not run AE2 registration.
        Class.forName(UiRenderRecorder.class.getName());
        try { appeng.api.stacks.AEKeyTypesInternal.getRegistry(); }
        catch (IllegalStateException uninitialized) {
            var types = new net.minecraft.core.MappedRegistry<appeng.api.stacks.AEKeyType>(
                    appeng.api.stacks.AEKeyType.REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
            appeng.api.stacks.AEKeyTypesInternal.setRegistry(types);
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.items());
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.fluids());
            types.freeze();
        }
        var item = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        item.setAccessible(true);
        item.set(appeng.core.definitions.AEItems.MISSING_CONTENT,
                net.neoforged.neoforge.registries.DeferredItem.createItem(
                        ResourceLocation.withDefaultNamespace("barrier")));
    }

    @Test void selectionLookupTracksSelectionAndSameIdDefinitionReloads() throws Exception {
        var panel = panel();
        var first = definition("test:first", 100);
        var second = definition("test:second", 200);
        int[] traversals = {0};
        List<RecipeHolder<MatterResearchRecipe>> definitions = new java.util.AbstractList<>() {
            @Override public RecipeHolder<MatterResearchRecipe> get(int index) {
                traversals[0]++;
                return index == 0 ? first : second;
            }
            @Override public int size() { return 2; }
        };
        refreshDefinitions(panel, definitions);
        assertSame(first, invoke(panel, "selected"));
        ResponsiveScreenTestFixture.set(panel, "selected", second.id());
        ResponsiveScreenTestFixture.set(panel, "scroll", 40.0);
        refreshDefinitions(panel, definitions);
        assertSame(second, invoke(panel, "selected"));
        assertEquals(40.0, get(panel, "scroll"));
        int builtTraversals = traversals[0];
        for (int i = 0; i < 100; i++) {
            refreshDefinitions(panel, definitions);
            assertSame(second, invoke(panel, "selected"));
        }
        assertEquals(builtTraversals, traversals[0], "Repeated selected() and unchanged updates must not traverse definitions");
        var replacement = definition("test:second", 600);
        refreshDefinitions(panel, List.of(first, replacement));
        assertSame(replacement, invoke(panel, "selected"));
        assertEquals(40.0, get(panel, "scroll"), "Reloading a retained selection keeps scroll state");
        refreshDefinitions(panel, List.of(first));
        assertSame(first, invoke(panel, "selected"));
        assertEquals(0.0, get(panel, "scroll"));
        refreshDefinitions(panel, List.of());
        assertNull(invoke(panel, "selected"));
        assertNull(get(panel, "selected"));
    }

    @Test void progressAndCompletionCountsStayLiveWithTheSameCachedSelection() throws Exception {
        var panel = panel();
        var holder = definition("test:project", 100);
        refreshDefinitions(panel, List.of(holder));
        var tasks = new JsonObject();
        var task = new JsonObject();
        task.addProperty("progress", 20);
        tasks.add(holder.id().toString(), task);
        ResponsiveScreenTestFixture.set(panel, "tasks", tasks);
        ResponsiveScreenTestFixture.set(panel, "counts", new JsonObject());
        ResponsiveScreenTestFixture.set(panel, "completed", java.util.Set.of());
        assertEquals(20, invoke(panel, "progressTicks"));
        assertEquals(0.2F, (float) invoke(panel, "progressRatio"), 0.0001F);
        task.addProperty("progress", 80);
        assertEquals(80, invoke(panel, "progressTicks"));
        assertEquals(0.8F, (float) invoke(panel, "progressRatio"), 0.0001F);
        var counts = new JsonObject();
        counts.addProperty(holder.id().toString(), 3);
        ResponsiveScreenTestFixture.set(panel, "counts", counts);
        assertEquals(3, invoke(panel, "count", RecipeHolder.class, holder));
        assertSame(holder, invoke(panel, "selected"));
        ResponsiveScreenTestFixture.set(panel, "tasks", new JsonObject());
        assertEquals(0, invoke(panel, "progressTicks"));
    }

    @Test void unchangedOrderJsonReusesDecodedRowsAcrossOtherStateUpdatesButChangedAmountsDoNot() throws Exception {
        var panel = panel();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var selected = ResourceLocation.parse("test:project");
        var items = orderItems(registries, 100, 40, "queued");
        ResponsiveScreenTestFixture.set(panel, "orderItems", items);
        refreshOrders(panel, registries);
        var source = decodedOrders(panel);
        assertEquals(1, source.size());
        var cache = new MatterResearchOrders();
        var view = cache.forSelection(source, selected);
        for (int i = 0; i < 10; i++) {
            ResponsiveScreenTestFixture.set(panel, "orderItems", JsonParser.parseString(items.toString()).getAsJsonArray());
            refreshOrders(panel, registries);
            assertSame(source, decodedOrders(panel), "Equal order items must not be decoded for progress/stock-only syncs");
            assertSame(view, cache.forSelection(decodedOrders(panel), selected));
            assertSame(get(panel, "orderItems"), get(panel, "decodedOrderItems"), "Stable ticks use an identity equality fast path");
        }
        ResponsiveScreenTestFixture.set(panel, "orderItems", orderItems(registries, 75, 6, "submitted"));
        refreshOrders(panel, registries);
        var changed = cache.forSelection(decodedOrders(panel), selected);
        assertNotSame(view, changed);
        assertEquals(75, changed.rows().getFirst().stack().amount());
        assertEquals(6, changed.rows().getLast().stack().amount());
        assertEquals("submitted", changed.status());
        ResponsiveScreenTestFixture.set(panel, "orderItems", null);
        refreshOrders(panel, registries);
        assertTrue(decodedOrders(panel).isEmpty(), "Removed orders clear cached details");
    }

    @Test void orderDecodeWaitsForRegistryContextAndInvalidatesIfItChanges() throws Exception {
        var panel = panel();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ResponsiveScreenTestFixture.set(panel, "orderItems", orderItems(registries, 9, 3, "queued"));
        refreshOrders(panel, null);
        assertTrue(decodedOrders(panel).isEmpty());
        refreshOrders(panel, registries);
        var first = decodedOrders(panel);
        assertEquals(1, first.size());
        var reloaded = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        refreshOrders(panel, reloaded);
        assertNotSame(first, decodedOrders(panel));
        assertEquals(first, decodedOrders(panel));
    }

    @Test void tallerDetailClipUsesScreenSpaceBoundsAndFlushesOnBothSides() throws Exception {
        var panel = panel();
        var graphics = new UiRenderRecorder();
        graphics.pose().translate(12.5F, 14.25F, 0);
        graphics.pose().scale(0.75F, 0.75F, 1);
        var before = new org.joml.Matrix4f(graphics.pose().last().pose());
        invoke(panel, "beginList", net.minecraft.client.gui.GuiGraphics.class, graphics);
        assertEquals(1, graphics.flushes);
        assertEquals(1, graphics.clipDepth);
        var clip = graphics.scissors.getFirst();
        assertEquals(170, clip.getX());
        assertEquals(143, clip.getY());
        assertEquals(127, clip.getWidth());
        assertEquals(58, clip.getHeight());
        invoke(panel, "endList", net.minecraft.client.gui.GuiGraphics.class, graphics);
        assertEquals(2, graphics.flushes);
        assertEquals(0, graphics.clipDepth);
        assertEquals(before, graphics.pose().last().pose());
    }

    @Test void researchHoverUsesOnlyTheVisibleListAndRejectsStaleRowsAfterNavigation() throws Exception {
        var panel=hoverPanel();
        var ingredient=panel.ingredientAt(250,245);
        assertNotNull(ingredient);
        assertEquals(AEItemKey.of(Items.DIAMOND),ingredient.stack().what());
        assertEquals(4096,ingredient.stack().amount());
        assertEquals(244,ingredient.bounds().getY());
        assertEquals(4,ingredient.bounds().getHeight(),"Only the visible four pixels of the last row may accept JEI input");
        assertEquals("Diamond",panel.tooltipAt(250,245).getFirst().getString());
        for(double[] point:new double[][]{{211,245},{378,245},{250,248},{250,171}}){
            assertNull(panel.ingredientAt(point[0],point[1]));
            assertTrue(panel.tooltipAt(point[0],point[1]).isEmpty());
        }
        for(String navigation:List.of("scroll","showUnlocks","showOrderDetails","selected")){
            var current=hoverPanel();
            ResponsiveScreenTestFixture.set(current,navigation,switch(navigation){
                case "scroll" -> 24.0;
                case "selected" -> ResourceLocation.parse("test:other");
                default -> true;
            });
            assertNull(current.ingredientAt(250,245),navigation);
            assertTrue(current.tooltipAt(250,245).isEmpty(),navigation);
        }
        ResponsiveScreenTestFixture.set(panel,"visible",false);
        assertNull(panel.ingredientAt(250,245));
    }

    @Test void researchIngredientBoundsFollowTheFittedScreenAndDisappearOnTheOverview() throws Exception {
        var panel=hoverPanel();
        var screen=ResponsiveScreenTestFixture.allocate(MatterFabricationScreen.class);
        ResponsiveScreenTestFixture.set(screen,"researchPanel",panel);
        ResponsiveScreenTestFixture.set(screen,"researchPage",true);
        for(int[] viewport:new int[][]{{854,480},{320,240},{567,240}}){
            int width=viewport[0],height=viewport[1],left=(width-400)/2,top=(height-380)/2;
            screen.width=width;screen.height=height;
            ResponsiveScreenTestFixture.set(screen,"imageWidth",400);
            ResponsiveScreenTestFixture.set(screen,"imageHeight",380);
            ResponsiveScreenTestFixture.set(screen,"leftPos",left);
            ResponsiveScreenTestFixture.set(screen,"topPos",top);
            int x=screen.responsiveScreenX(left+250),y=screen.responsiveScreenY(top+246);
            var ingredient=screen.researchIngredientAt(x,y);
            assertNotNull(ingredient);
            assertTrue(ingredient.bounds().contains(x,y));
            assertEquals(screen.responsiveScreenY(top+244),ingredient.bounds().getY());
            assertEquals(screen.responsiveScreenY(top+248)-ingredient.bounds().getY(),ingredient.bounds().getHeight());
            assertNull(screen.researchIngredientAt(x,screen.responsiveScreenY(top+249)));
        }
        ResponsiveScreenTestFixture.set(screen,"researchPage",false);
        assertNull(screen.researchIngredientAt(250,245));
    }

    private static MatterResearchPanel hoverPanel() throws Exception {
        var panel=panel();
        ResponsiveScreenTestFixture.set(panel,"visible",true);
        ResponsiveScreenTestFixture.set(panel,"selected",ResourceLocation.parse("test:project"));
        ResponsiveScreenTestFixture.set(panel,"renderedSelection",panel.selection());
        var row=Class.forName(MatterResearchPanel.class.getName()+"$QuantityTooltip").getDeclaredConstructors()[0];
        row.setAccessible(true);
        ResponsiveScreenTestFixture.set(panel,"quantityTooltips",List.of(row.newInstance(212,244,166,24,
                new GenericStack(AEItemKey.of(Items.DIAMOND),4096),List.of(net.minecraft.network.chat.Component.literal("Diamond")))));
        return panel;
    }

    private static MatterResearchPanel panel() throws Exception {
        var panel = ResponsiveScreenTestFixture.allocate(MatterResearchPanel.class);
        ResponsiveScreenTestFixture.set(panel, "definitions", List.of());
        ResponsiveScreenTestFixture.set(panel, "definitionsById", new HashMap<>());
        ResponsiveScreenTestFixture.set(panel, "allOrderEntries", List.of());
        return panel;
    }

    private static RecipeHolder<MatterResearchRecipe> definition(String id, int duration) {
        return new RecipeHolder<>(ResourceLocation.parse(id), new MatterResearchRecipe(id, List.of(), List.of(),
                duration, 0, List.of(), List.of(), 0, 1));
    }

    private static JsonArray orderItems(HolderLookup.Provider registries, long amount, long missing, String status) {
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var entry = new JsonObject();
        entry.add("target", GenericStack.CODEC.encodeStart(ops, new GenericStack(AEItemKey.of(Items.DIAMOND), amount)).getOrThrow());
        var missingItems = new JsonArray();
        missingItems.add(GenericStack.CODEC.encodeStart(ops, new GenericStack(AEItemKey.of(Items.IRON_INGOT), missing)).getOrThrow());
        entry.add("missing", missingItems);
        entry.addProperty("queued", status.equals("queued") ? amount : 0);
        entry.addProperty("active", status.equals("submitted") ? amount : 0);
        entry.addProperty("status", status);
        entry.addProperty("owner", "test:project");
        var items = new JsonArray();
        items.add(entry);
        return items;
    }

    private static void refreshDefinitions(MatterResearchPanel panel, List<RecipeHolder<MatterResearchRecipe>> source) throws Exception {
        invoke(panel, "updateDefinitions", List.class, source);
    }
    private static void refreshOrders(MatterResearchPanel panel, HolderLookup.Provider registries) throws Exception {
        invoke(panel, "updateOrderEntries", HolderLookup.Provider.class, registries);
    }
    @SuppressWarnings("unchecked")
    private static List<MatterResearchOrders.Entry> decodedOrders(MatterResearchPanel panel) throws Exception {
        return (List<MatterResearchOrders.Entry>) get(panel, "allOrderEntries");
    }
    private static Object get(MatterResearchPanel panel, String name) throws Exception {
        var field = MatterResearchPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(panel);
    }
    private static Object invoke(MatterResearchPanel panel, String name) throws Exception {
        var method = MatterResearchPanel.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(panel);
    }
    private static Object invoke(MatterResearchPanel panel, String name, Class<?> type, Object argument) throws Exception {
        var method = MatterResearchPanel.class.getDeclaredMethod(name, type);
        method.setAccessible(true);
        return method.invoke(panel, argument);
    }
}
