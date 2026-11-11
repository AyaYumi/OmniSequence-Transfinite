package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Invokes the core's actual payment implementation with real AE2 interfaces, without forming a world or grid. */
class StarComputationInformationExtractionTest {
    @BeforeAll
    static void bootstrap() { StarResearchNodeRecipeTest.bootstrap(); }

    @Test
    void zeroCostSucceedsWithoutTouchingTheNodeGridStorageInventoryOrActionSource() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var core = (StarComputationEngineCoreBlockEntity) ((sun.misc.Unsafe) field.get(null))
                .allocateInstance(StarComputationEngineCoreBlockEntity.class);
        var infrastructure = new ArrayList<String>();
        var modes = new ArrayList<Actionable>();
        var amounts = new ArrayList<Long>();
        var source = proxy(IActionSource.class, (object, method, args) -> {
            fail("Payment must pass the supplied action source through, not inspect it: " + method.getName());
            return null;
        });
        var storage = proxy(MEStorage.class, (object, method, args) -> {
            assertEquals("extract", method.getName());
            assertSame(StarSignalKey.INSTANCE, args[0]);
            assertSame(source, args[3]);
            amounts.add((Long) args[1]);
            modes.add((Actionable) args[2]);
            return args[1];
        });
        var service = proxy(IStorageService.class, (object, method, args) -> {
            assertEquals("getInventory", method.getName());
            infrastructure.add("inventory");
            return storage;
        });
        var grid = proxy(IGrid.class, (object, method, args) -> {
            assertEquals("getStorageService", method.getName());
            infrastructure.add("storage");
            return service;
        });
        var node = proxy(IManagedGridNode.class, (object, method, args) -> {
            assertEquals("getGrid", method.getName());
            infrastructure.add("grid");
            return grid;
        });
        var mainNode = AENetworkedBlockEntity.class.getDeclaredField("mainNode");
        mainNode.setAccessible(true);
        mainNode.set(core, node);
        var extract = StarComputationEngineCoreBlockEntity.class.getDeclaredMethod("extractInformation", long.class, IActionSource.class);
        extract.setAccessible(true);

        assertEquals(true, extract.invoke(core, 0L, source));
        assertTrue(infrastructure.isEmpty(), "A free material grant must not even query storage availability");
        assertTrue(modes.isEmpty());
        assertTrue(amounts.isEmpty());
        // Positive control: the same installed interfaces observe the actual simulate-then-modulate payment path.
        assertEquals(true, extract.invoke(core, 7L, source));
        assertEquals(List.of("grid", "storage", "inventory"), infrastructure);
        assertEquals(List.of(Actionable.SIMULATE, Actionable.MODULATE), modes);
        assertEquals(List.of(7L, 7L), amounts);
        assertEquals(true, extract.invoke(core, 0L, source));
        assertEquals(3, infrastructure.size());
        assertEquals(2, modes.size());
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
