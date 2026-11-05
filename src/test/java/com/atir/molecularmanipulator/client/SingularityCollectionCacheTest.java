package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.menu.SingularityMenu;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SingularityCollectionCacheTest {
    @BeforeAll static void bootstrap() throws Exception { Class.forName(UiRenderRecorder.class.getName()); }

    @Test void sameRawSourceReusesBothTheListAndItsDisplayStacks() throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(SingularityMenu.class);
        var screen = screen(menu);
        menu.collectionItems = "minecraft:iron_ingot,minecraft:copper_ingot";
        var first = resources(screen);
        menu.collectionItems = new String(menu.collectionItems);
        menu.collectionBatchSize = 10_000;
        menu.collectionActive = true;
        for (int i = 0; i < 100; i++) {
            var reused = resources(screen);
            assertSame(first, reused);
            assertSame(first.getFirst(), reused.getFirst());
        }
        assertEquals(List.of(Items.IRON_INGOT, Items.COPPER_INGOT), first.stream().map(ItemStack::getItem).toList());
        assertTrue(first.stream().allMatch(stack -> stack.getCount() == 1), "Batch quantities must not alter display stacks");
    }

    @Test void parsingKeepsOrderDuplicatesAndTheExistingInvalidIdRules() throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(SingularityMenu.class);
        var screen = screen(menu);
        menu.collectionItems = " , minecraft:iron_ingot,INVALID ID,test:not_registered,minecraft:air,diamond,minecraft:iron_ingot,,";
        var parsed = resources(screen);
        assertEquals(List.of(Items.IRON_INGOT, Items.DIAMOND, Items.IRON_INGOT), parsed.stream().map(ItemStack::getItem).toList());
        assertNotSame(parsed.getFirst(), parsed.getLast(), "Duplicate entries still represent separate display stacks");
        assertThrows(UnsupportedOperationException.class, () -> parsed.clear());
    }

    @Test void replacingAndClearingTheSourceInvalidatesWithoutChangingPageState() throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(SingularityMenu.class);
        var screen = screen(menu);
        ResponsiveScreenTestFixture.set(screen, "collectionScroll", 2);
        menu.collectionItems = "minecraft:iron_ingot";
        var first = resources(screen);
        menu.collectionItems = "minecraft:diamond";
        var changed = resources(screen);
        assertNotSame(first, changed);
        assertEquals(Items.DIAMOND, changed.getFirst().getItem());
        menu.collectionItems = "";
        assertEquals(List.of(), resources(screen));
        assertSame(resources(screen), resources(screen));
        var scroll = SingularityScreen.class.getDeclaredField("collectionScroll");
        scroll.setAccessible(true);
        assertEquals(2, scroll.getInt(screen), "Data caching does not introduce new paging/input behavior");
    }

    private static SingularityScreen screen(SingularityMenu menu) throws Exception {
        var screen = ResponsiveScreenTestFixture.allocate(SingularityScreen.class);
        ResponsiveScreenTestFixture.set(screen, "menu", menu);
        ResponsiveScreenTestFixture.set(screen, "cachedCollectionResources", List.of());
        return screen;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> resources(SingularityScreen screen) throws Exception {
        var method = SingularityScreen.class.getDeclaredMethod("collectionResources");
        method.setAccessible(true);
        return (List<ItemStack>) method.invoke(screen);
    }
}
