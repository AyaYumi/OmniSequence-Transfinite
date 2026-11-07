package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.menu.SingularityMenu;
import com.atir.molecularmanipulator.network.SingularityCollectionPayload;
import com.atir.molecularmanipulator.network.SingularityCollectionState;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SingularityCollectionCacheTest {
    @BeforeAll static void bootstrap() throws Exception { Class.forName(UiRenderRecorder.class.getName()); }

    @Test void completedSnapshotReusesBothTheListAndItsDisplayStacksAcrossFrames() throws Exception {
        var menu = menu();
        var screen = screen(menu);
        assertTrue(menu.acceptCollectionResources(chunk(1, 0, 2, Items.IRON_INGOT, Items.COPPER_INGOT)));
        var first = resources(screen);
        assertSame(menu.collectionResources(), first);
        menu.collectionBatchSize = 10_000;
        menu.collectionActive = true;
        for (int i = 0; i < 1_000; i++) {
            var reused = resources(screen);
            assertSame(first, reused);
            assertSame(first.getFirst(), reused.getFirst());
        }
        assertEquals(List.of(Items.IRON_INGOT, Items.COPPER_INGOT), first.stream().map(ItemStack::getItem).toList());
        assertTrue(first.stream().allMatch(stack -> stack.getCount() == 1), "Batch quantities must not alter display stacks");
    }

    @Test void screenKeepsThePreviousCompleteListWhileReplacementChunksAreInFlight() throws Exception {
        var menu = menu();
        var screen = screen(menu);
        assertTrue(menu.acceptCollectionResources(chunk(1, 0, 1, Items.IRON_INGOT)));
        var previous = resources(screen);
        assertTrue(menu.acceptCollectionResources(chunk(2, 0, 4, Items.COPPER_INGOT, Items.DIAMOND)));
        assertSame(previous, resources(screen));
        assertFalse(menu.acceptCollectionResources(new SingularityCollectionPayload(8, 3, 0, 0, List.of())),
                "Another container's packet must not reset this menu's receiver");
        assertSame(previous, resources(screen));
        assertTrue(menu.acceptCollectionResources(chunk(2, 2, 4, Items.AIR, Items.COPPER_INGOT)));
        var complete = resources(screen);
        assertEquals(List.of(Items.COPPER_INGOT, Items.DIAMOND, Items.COPPER_INGOT),
                complete.stream().map(ItemStack::getItem).toList());
        assertNotSame(complete.getFirst(), complete.getLast(), "Duplicate identities still represent separate display stacks");
        assertThrows(UnsupportedOperationException.class, complete::clear);
        assertEquals(Items.IRON_INGOT, previous.getFirst().getItem());
    }

    @Test void replacingAndClearingTheSnapshotDoesNotRebuildScreenPageState() throws Exception {
        var menu = menu();
        var screen = screen(menu);
        ResponsiveScreenTestFixture.set(screen, "collectionScroll", 2);
        assertTrue(menu.acceptCollectionResources(chunk(1, 0, 1, Items.IRON_INGOT)));
        var first = resources(screen);
        assertTrue(menu.acceptCollectionResources(chunk(2, 0, 1, Items.DIAMOND)));
        var changed = resources(screen);
        assertNotSame(first, changed);
        assertEquals(Items.DIAMOND, changed.getFirst().getItem());
        assertTrue(menu.acceptCollectionResources(chunk(3, 0, 0)));
        assertEquals(List.of(), resources(screen));
        assertSame(resources(screen), resources(screen));
        var scroll = SingularityScreen.class.getDeclaredField("collectionScroll");
        scroll.setAccessible(true);
        assertEquals(2, scroll.getInt(screen), "Snapshot caching does not rebuild the screen or its input state");
    }

    private static SingularityMenu menu() throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(SingularityMenu.class);
        ResponsiveScreenTestFixture.set(menu, "containerId", 7);
        ResponsiveScreenTestFixture.set(menu, "collectionState", new SingularityCollectionState());
        return menu;
    }

    private static SingularityScreen screen(SingularityMenu menu) throws Exception {
        var screen = ResponsiveScreenTestFixture.allocate(SingularityScreen.class);
        ResponsiveScreenTestFixture.set(screen, "menu", menu);
        return screen;
    }

    private static SingularityCollectionPayload chunk(long generation, int offset, int total, Item... items) {
        return new SingularityCollectionPayload(7, generation, offset, total, List.of(items));
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> resources(SingularityScreen screen) throws Exception {
        var method = SingularityScreen.class.getDeclaredMethod("collectionResources");
        method.setAccessible(true);
        return (List<ItemStack>) method.invoke(screen);
    }
}
