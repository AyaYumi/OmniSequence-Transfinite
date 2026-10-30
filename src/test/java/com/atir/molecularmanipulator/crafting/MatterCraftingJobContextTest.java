package com.atir.molecularmanipulator.crafting;

import appeng.api.networking.crafting.ICraftingLink;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatterCraftingJobContextTest {
    private static ICraftingLink link(UUID id) {
        return (ICraftingLink) Proxy.newProxyInstance(ICraftingLink.class.getClassLoader(),
                new Class<?>[]{ICraftingLink.class}, (proxy, method, args) ->
                        method.getName().equals("getCraftingID") ? id : null);
    }

    @Test void nestedScopesRestoreAndExceptionCannotLeakAnotherTasksIdentity() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertNull(MatterCraftingJobContext.currentJobId());
        try (var outer = MatterCraftingJobContext.open(() -> link(first))) {
            assertEquals(first, MatterCraftingJobContext.currentJobId());
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = MatterCraftingJobContext.open(() -> link(second))) {
                    assertEquals(second, MatterCraftingJobContext.currentJobId());
                    throw new IllegalStateException("dispatch failed");
                }
            });
            assertEquals(first, MatterCraftingJobContext.currentJobId());
        }
        assertNull(MatterCraftingJobContext.currentJobId());
    }

    @Test void linkIsReadAtCommitRatherThanStaleTickStart() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        ICraftingLink[] current = {link(first)};
        try (var scope = MatterCraftingJobContext.open(() -> current[0])) {
            assertEquals(first, MatterCraftingJobContext.currentJobId());
            current[0] = link(second);
            assertEquals(second, MatterCraftingJobContext.currentJobId());
            current[0] = null;
            assertNull(MatterCraftingJobContext.currentJobId());
        }
    }

    @Test void reflectionSupportsAeClusterAndPrivateOptionalCpuOwners() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var ae = new AeLogic(link(first));
        try (var scope = MatterCraftingJobContext.openCpu(ae)) {
            assertEquals(first, MatterCraftingJobContext.currentJobId());
            ae.current = link(second);
            assertEquals(second, MatterCraftingJobContext.currentJobId());
        }
        var optional = new DerivedOptionalLogic(link(second));
        try (var scope = MatterCraftingJobContext.openCpu(optional)) {
            assertEquals(second, MatterCraftingJobContext.currentJobId());
        }
        // Null level is normal in an unattached fixture and must not invent a cancellation.
        MatterCraftingJobContext.markCanceled(ae);
        MatterCraftingJobContext.markCanceled(optional);
        assertNull(MatterCraftingJobContext.currentJobId());
    }

    @Test void unsupportedCpuCannotSilentlyAssignUnownedInputs() {
        try (var scope = MatterCraftingJobContext.openCpu(new Object())) {
            assertThrows(IllegalStateException.class, MatterCraftingJobContext::currentJobId);
        }
        assertNull(MatterCraftingJobContext.currentJobId());
    }

    private static class AeOwner {
        private net.minecraft.world.level.Level getLevel() { return null; }
    }
    private static class AeLogic {
        private final AeOwner cluster = new AeOwner();
        private ICraftingLink current;
        private AeLogic(ICraftingLink link) { current = link; }
        private ICraftingLink getLastLink() { return current; }
    }
    private static class OptionalOwner {
        private net.minecraft.world.level.Level level() { return null; }
    }
    private static class OptionalLogic {
        private final OptionalOwner cpu = new OptionalOwner();
        private final ICraftingLink current;
        private OptionalLogic(ICraftingLink link) { current = link; }
        private ICraftingLink getLastLink() { return current; }
    }
    private static final class DerivedOptionalLogic extends OptionalLogic {
        private DerivedOptionalLogic(ICraftingLink link) { super(link); }
    }
}
