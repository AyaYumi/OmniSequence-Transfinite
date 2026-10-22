package com.atir.molecularmanipulator.integration.eco;

import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.LoggerFactory;

/** Accesses ECO fields only after the menu's actual runtime class has been transformed. */
public final class EcoOmniPlanMenuAccess {
    private static final ClassValue<EcoOmniPlanMenuAccess> CACHE = new ClassValue<>() {
        @Override protected EcoOmniPlanMenuAccess computeValue(Class<?> type) {
            return new EcoOmniPlanMenuAccess(type);
        }
    };

    private final Class<?> type;
    // Cache failures as well as successes, independently: a broken confirmation
    // accessor must not prevent disabling availability or writing the diagnostic.
    private final Slot confirmed;
    private final Slot options;
    private final Slot available;
    private final Slot diagnostic;
    private final AtomicBoolean reported = new AtomicBoolean();

    private EcoOmniPlanMenuAccess(Class<?> type) {
        this.type = type;
        confirmed = slot(type, "neoecoae$confirmedPlanningResult", ECOPlanningResult.class);
        options = slot(type, "neoecoae$originalOptions", ECOPlannerOptions.class);
        available = slot(type, "neoecoae$bigOrderCpu", boolean.class);
        diagnostic = slot(type, "neoecoae$planningDiagnostic", String.class);
    }

    public static EcoOmniPlanMenuAccess of(Object menu) { return CACHE.get(menu.getClass()); }

    public void requireReady() {
        confirmed.require(); options.require(); available.require(); diagnostic.require();
    }

    public ECOPlanningResult confirmed(Object menu) { return (ECOPlanningResult) confirmed.get(menu); }
    public ECOPlannerOptions options(Object menu) { return (ECOPlannerOptions) options.get(menu); }
    public void clearConfirmed(Object menu) { confirmed.set(menu, null); }
    public void available(Object menu, boolean value) { available.set(menu, value); }
    public void diagnostic(Object menu, String value) { diagnostic.set(menu, value); }

    /** Best effort for each independent field; every failed write remains in the reported exception. */
    public void failClosed(Object menu, Throwable failure) {
        try { available(menu, false); } catch (Failure secondary) { suppress(failure, secondary); }
        try { diagnostic(menu, "Omni: MENU_ACCESS_FAILED"); } catch (Failure secondary) { suppress(failure, secondary); }
        report(failure);
    }

    public void report(Throwable failure) {
        if (reported.compareAndSet(false, true)) {
            LoggerFactory.getLogger(EcoOmniPlanMenuAccess.class)
                    .error("Disabled Omni ECO confirmation: runtime menu access failed for " + type.getName(), failure);
        }
    }

    private static void suppress(Throwable failure, Throwable secondary) {
        if (failure != secondary && java.util.Arrays.stream(failure.getSuppressed()).noneMatch(item -> item == secondary)) {
            failure.addSuppressed(secondary);
        }
    }

    /**
     * Exact logical name wins; otherwise require a unique suffix match across the
     * actual class hierarchy. Never infer a field from its type alone. Validate
     * the selected signature before making it accessible, including primitive types.
     */
    public static Field resolveField(Class<?> type, String logicalName, Class<?> expectedType)
            throws ReflectiveOperationException {
        var exact = new ArrayList<Field>();
        var suffixed = new ArrayList<Field>();
        for (var owner = type; owner != null; owner = owner.getSuperclass()) {
            for (var field : owner.getDeclaredFields()) {
                if (field.getName().equals(logicalName)) exact.add(field);
                else if (field.getName().endsWith(logicalName)) suffixed.add(field);
            }
        }
        List<Field> matches = exact.isEmpty() ? suffixed : exact;
        if (matches.size() != 1) {
            throw new NoSuchFieldException(type.getName() + ": expected one field for " + logicalName
                    + " : " + expectedType.getTypeName() + ", found " + matches);
        }
        var field = matches.getFirst();
        if (field.getType() != expectedType || Modifier.isStatic(field.getModifiers())
                || Modifier.isFinal(field.getModifiers())) {
            throw new NoSuchFieldException("Invalid ECO menu field signature for " + logicalName
                    + " : " + expectedType.getTypeName() + ": " + field);
        }
        if (!field.trySetAccessible()) throw new IllegalAccessException("Cannot access ECO menu field " + field);
        return field;
    }

    private static Slot slot(Class<?> type, String name, Class<?> expectedType) {
        try {
            return new Slot(resolveField(type, name, expectedType), null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return new Slot(null, new Failure("Cannot resolve " + type.getName() + "." + name, failure));
        }
    }

    private record Slot(Field field, Failure failure) {
        void require() { if (failure != null) throw failure; }
        Object get(Object menu) {
            require();
            try { return field.get(menu); }
            catch (IllegalAccessException | RuntimeException failure) {
                throw new Failure("Cannot read " + field, failure);
            }
        }
        void set(Object menu, Object value) {
            require();
            try { field.set(menu, value); }
            catch (IllegalAccessException | RuntimeException failure) {
                throw new Failure("Cannot write " + field, failure);
            }
        }
    }

    public static final class Failure extends IllegalStateException {
        private Failure(String message, Throwable cause) { super(message, cause); }
    }
}
