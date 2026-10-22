package com.atir.molecularmanipulator.crafting;

import appeng.api.networking.crafting.ICraftingLink;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** Job identity for ordinary/counting CPU APIs that do not include an ID in their provider push. */
public final class MatterCraftingJobContext {
    private static final ThreadLocal<Supplier<ICraftingLink>> CURRENT = new ThreadLocal<>();
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            try {
                Method link = method(type, "getLastLink");
                Field owner;
                try { owner = field(type, "cpu"); }
                catch (NoSuchFieldException absent) { owner = field(type, "cluster"); }
                Method level;
                try { level = method(owner.getType(), "getLevel"); }
                catch (NoSuchMethodException absent) { level = method(owner.getType(), "level"); }
                return new Access(link, owner, level);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Missing crafting job identity on " + type.getName(), error);
            }
        }
    };

    private MatterCraftingJobContext() { }

    public static Scope open(Supplier<ICraftingLink> link) {
        var previous = CURRENT.get();
        CURRENT.set(link);
        return new Scope(previous);
    }

    public static Scope openCpu(Object logic) {
        return open(() -> link(logic));
    }

    public static UUID currentJobId() {
        var supplier = CURRENT.get();
        var link = supplier == null ? null : supplier.get();
        return link == null ? null : link.getCraftingID();
    }

    public static void markCanceled(Object logic) {
        try {
            var access = ACCESS.get(logic.getClass());
            var link = (ICraftingLink) access.link.invoke(logic);
            var owner = access.owner.get(logic);
            if (link != null && owner != null) {
                MolecularBatchCancellationData.markCanceled((Level) access.level.invoke(owner), link.getCraftingID());
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot record crafting job cancellation", error);
        }
    }

    private static ICraftingLink link(Object logic) {
        try {
            return (ICraftingLink) ACCESS.get(logic.getClass()).link.invoke(logic);
        } catch (ReflectiveOperationException error) {
            // Never silently attribute a native CPU's materials to an unowned queue.
            throw new IllegalStateException("Cannot identify crafting job", error);
        }
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                var method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                var field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private record Access(Method link, Field owner, Method level) { }

    public static final class Scope implements AutoCloseable {
        private final Supplier<ICraftingLink> previous;
        private boolean closed;
        private Scope(Supplier<ICraftingLink> previous) { this.previous = previous; }
        @Override public void close() {
            if (closed) return;
            closed = true;
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
