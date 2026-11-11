package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import java.lang.reflect.Field;
import java.util.ArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises real menu pagination and search projections with different local/server capacities. */
class MolecularCenterPaginationTest {
    private Field cachedValue;
    private Object previousPages;

    @BeforeAll static void bootstrap() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
    }

    @BeforeEach void setLocalPages() throws Exception {
        cachedValue = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        previousPages = cachedValue.get(ModConfig.PATTERN_PAGES);
        cachedValue.set(ModConfig.PATTERN_PAGES, 200);
    }

    @AfterEach void restoreLocalPages() throws Exception {
        cachedValue.set(ModConfig.PATTERN_PAGES, previousPages);
    }

    @Test void clientCanReachAllThreeHundredServerPagesWithTwoHundredLocalPages() throws Exception {
        var menu = clientMenu(300);
        assertEquals(300, menu.getPageCount());
        applyPage(menu, 299);
        assertEquals(299, menu.getPage());
        applyPage(menu, 300);
        assertEquals(299, menu.getPage(), "Requests remain bounded by the server capacity");
    }

    @Test void clientCannotPageBeyondASmallerServerCapacityOrUseLocalDefaultsBeforeSync() throws Exception {
        var menu = clientMenu(7);
        assertEquals(7, menu.getPageCount());
        applyPage(menu, 199);
        assertEquals(6, menu.getPage());
        applyPage(menu, -1);
        assertEquals(0, menu.getPage());
        menu.patternCapacity = 0;
        assertEquals(1, menu.getPageCount(), "An unsynchronized menu must not expose local-only pages");
    }

    @Test void clientSearchCanProjectPatternsBeyondItsLocalConfiguredCapacity() throws Exception {
        var menu = clientMenu(300);
        var method = MolecularCenterMenu.class.getDeclaredMethod("applyPatternSearchPage",
                MolecularCenterMenu.PatternSearchPageRequest.class);
        method.setAccessible(true);
        method.invoke(menu, new MolecularCenterMenu.PatternSearchPageRequest(0, 1, new int[]{10_799}));
        assertTrue(menu.patternSearchActive);
        assertEquals(1, menu.patternSearchResultCount);
        assertEquals(1, menu.getPageCount());
        assertTrue(menu.getPatternSlots().getFirst().isActive());
        assertFalse(menu.getPatternSlots().get(1).isActive());
        applyPage(menu, 299);
        assertFalse(menu.patternSearchActive);
        assertEquals(299, menu.getPage());
        assertTrue(menu.getPatternSlots().getLast().isActive());
    }

    @Test void serverUsesCurrentConfigEvenBeforeItsCapacitySnapshotIsRefreshed() throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(MolecularCenterMenu.class);
        setWorld(menu, ResponsiveScreenTestFixture.allocate(ServerLevel.class));
        var center = ResponsiveScreenTestFixture.allocate(MolecularCenterBlockEntity.class);
        var logic = ResponsiveScreenTestFixture.allocate(MolecularCenterLogic.class);
        ResponsiveScreenTestFixture.set(logic, "fullPatternInventory", new AppEngInternalInventory(10_800));
        ResponsiveScreenTestFixture.set(center, "logic", logic);
        ResponsiveScreenTestFixture.set(menu, "center", center);
        menu.patternCapacity = 10_800;
        cachedValue.set(ModConfig.PATTERN_PAGES, 7);
        assertEquals(7, menu.getPageCount());
        cachedValue.set(ModConfig.PATTERN_PAGES, 300);
        assertEquals(300, menu.getPageCount());
    }

    private static MolecularCenterMenu clientMenu(int serverPages) throws Exception {
        var menu = ResponsiveScreenTestFixture.allocate(MolecularCenterMenu.class);
        var world = ResponsiveScreenTestFixture.allocate(ClientLevel.class);
        ResponsiveScreenTestFixture.set(world, "isClientSide", true);
        setWorld(menu, world);
        menu.patternCapacity = serverPages * 36;
        var view = Class.forName(MolecularCenterMenu.class.getName() + "$PagedInventory")
                .getDeclaredMethod("clientView");
        view.setAccessible(true);
        ResponsiveScreenTestFixture.set(menu, "pageInventory", view.invoke(null));
        var slots = new ArrayList<AppEngSlot>();
        var inventory = new AppEngInternalInventory(36);
        for (int slot = 0; slot < 36; slot++) slots.add(new AppEngSlot(inventory, slot));
        ResponsiveScreenTestFixture.set(menu, "patternSlots", slots);
        return menu;
    }

    private static void setWorld(MolecularCenterMenu menu, Level world) throws Exception {
        var player = ResponsiveScreenTestFixture.allocate(ServerPlayer.class);
        ResponsiveScreenTestFixture.set(player, "level", world);
        ResponsiveScreenTestFixture.set(menu, "playerInventory", new Inventory(player));
    }

    private static void applyPage(MolecularCenterMenu menu, int page) throws Exception {
        var method = MolecularCenterMenu.class.getDeclaredMethod("applyPage", int.class);
        method.setAccessible(true);
        method.invoke(menu, page);
    }
}
