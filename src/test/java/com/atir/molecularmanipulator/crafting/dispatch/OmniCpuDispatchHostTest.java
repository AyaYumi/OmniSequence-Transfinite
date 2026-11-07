package com.atir.molecularmanipulator.crafting.dispatch;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.crafting.MolecularRemainderContext;
import com.atir.molecularmanipulator.mixin.CraftingCpuLogicMixin;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Shared-boundary wiring checks, independent of the optional dispatcher implementation. */
class OmniCpuDispatchHostTest {
    @Test void allHostHooksAreConcreteInTheSharedCpu() throws Exception {
        var hooks = Arrays.stream(OmniCpuDispatchHost.class.getDeclaredMethods())
                .filter(method -> !method.getName().equals("omniDispatch$reservedInput")).toList();
        assertEquals(15, hooks.size(), "Forge retains the extra shared paused hook");
        for (var hook : hooks) {
            assertTrue(hook.getName().startsWith("omniDispatch$"));
            var implementation = CraftingCpuLogicMixin.class.getDeclaredMethod(hook.getName(), hook.getParameterTypes());
            assertFalse(Modifier.isAbstract(implementation.getModifiers()), hook.getName());
            assertEquals(hook.getReturnType(), implementation.getReturnType());
        }
    }

    @Test void sharedHostAndItsDescriptorsLoadWithoutTheOptionalImplementation() throws Exception {
        var urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .filter(path -> !path.contains("neoecoae-"))
                .map(path -> {
                    try { return java.nio.file.Path.of(path).toUri().toURL(); }
                    catch (java.net.MalformedURLException failure) { throw new AssertionError(failure); }
                }).toArray(java.net.URL[]::new);
        try (var isolated = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("cn.dancingsnow.neoecoae.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        }) {
            for (var type : List.of(CraftingCpuLogicMixin.class, OmniCpuDispatchHost.class,
                    OmniCpuDispatchLedger.class, OmniCpuDispatchReceipt.class, OmniCpuDispatchRunner.class,
                    MolecularRemainderContext.class)) {
                assertNotNull(Class.forName(type.getName(), false, isolated).getDeclaredMethods());
            }
            assertNotNull(Class.forName(OmniCpuDispatchLedger.class.getName(), true, isolated)
                    .getConstructor().newInstance());
        }
    }

    @Test void sharedDescriptorsDoNotLinkOptionalIntegrations() throws Exception {
        for (var type : List.of(OmniCpuDispatchHost.class, OmniCpuDispatchLedger.class,
                OmniCpuDispatchReceipt.class, OmniCpuDispatchRunner.class, MolecularRemainderContext.class)) {
            var node = read(type);
            for (var method : node.methods) {
                assertIndependent(method.desc);
                if (method.signature != null) assertIndependent(method.signature);
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        assertIndependent(call.owner);
                        assertIndependent(call.desc);
                    } else if (instruction instanceof FieldInsnNode field) {
                        assertIndependent(field.owner);
                        assertIndependent(field.desc);
                    }
                }
            }
        }
    }

    @Test void renamedPrivateHooksAndLedgerKeepTheirForgeSignatures() throws Exception {
        assertEquals(OmniCpuDispatchLedger.class,
                CraftingCpuLogicMixin.class.getDeclaredField("molecularmanipulator$dispatchLedger").getType());
        assertNotNull(CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$holdDispatchAccounting",
                boolean.class, org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class));
        assertNotNull(CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$notifyOwnedOutput", AEKey.class));
        assertNotNull(CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$deliverFinalOutput",
                AEKey.class, long.class, Actionable.class));
        assertNotNull(CraftingCpuLogicMixin.class.getDeclaredMethod("molecularmanipulator$flushFinalStock"));
        assertEquals(CompoundTag.class, OmniCpuDispatchLedger.class.getDeclaredMethod("write").getReturnType());
        assertNotNull(OmniCpuDispatchLedger.class.getDeclaredMethod("read", CompoundTag.class));
        var owner = Type.getInternalName(OmniCpuDispatchLedger.class);
        var node = read(CraftingCpuLogicMixin.class);
        assertTrue(calls(node, "molecularmanipulator$writeExactCraftingState", owner,
                "write", "()Lnet/minecraft/nbt/CompoundTag;"));
        assertTrue(calls(node, "molecularmanipulator$resetPreviousExactInventory", owner,
                "read", "(Lnet/minecraft/nbt/CompoundTag;)V"));
        assertTrue(calls(node, "omniDispatch$paused", owner, "paused", "()Z"));
    }

    @Test void renamedOutputAndAccountingCallsUseTheSharedBoundary() throws Exception {
        var node = read(CraftingCpuLogicMixin.class);
        var owner = Type.getInternalName(CraftingCpuLogicMixin.class);
        assertTrue(calls(node, "molecularmanipulator$endOmniDispatch", owner,
                "molecularmanipulator$flushFinalStock", "()V"));
        assertTrue(calls(node, "omniDispatch$insertExact", owner,
                "molecularmanipulator$notifyOwnedOutput", "(Lappeng/api/stacks/AEKey;)V"));
        assertTrue(node.methods.stream().filter(method -> method.name.equals("molecularmanipulator$flushFinalStock"))
                .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .anyMatch(instruction -> instruction instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode dynamic
                        && Arrays.stream(dynamic.bsmArgs).anyMatch(argument -> argument instanceof org.objectweb.asm.Handle handle
                                && handle.getName().equals("molecularmanipulator$deliverFinalOutput"))));
    }

    private static void assertIndependent(String descriptor) {
        assertFalse(descriptor.contains("cn/dancingsnow/neoecoae/"), descriptor);
        assertFalse(descriptor.contains("com/atir/molecularmanipulator/integration/eco/"), descriptor);
    }

    private static ClassNode read(Class<?> type) throws Exception {
        var node = new ClassNode();
        try (var stream = type.getResourceAsStream("/" + Type.getInternalName(type) + ".class")) {
            new ClassReader(java.util.Objects.requireNonNull(stream)).accept(node, 0);
        }
        return node;
    }

    private static boolean calls(ClassNode node, String methodName, String owner, String name, String descriptor) {
        return node.methods.stream().filter(method -> method.name.equals(methodName))
                .flatMap(method -> Arrays.stream(method.instructions.toArray()))
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call
                        && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor));
    }
}
