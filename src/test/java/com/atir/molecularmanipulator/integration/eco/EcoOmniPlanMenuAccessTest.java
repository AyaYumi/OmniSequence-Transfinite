package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

/** Runtime fixtures deliberately model renamed @Unique fields, including ambiguous signatures. */
class EcoOmniPlanMenuAccessTest {
    private static final String FLAG = "neoecoae$bigOrderCpu";

    @Test void exactNameTakesPrecedenceOverRenamedCandidates() throws ReflectiveOperationException {
        var menu = new ExactAndRenamed();
        var field = EcoOmniPlanMenuAccess.resolveField(menu.getClass(), FLAG, boolean.class);
        assertEquals(FLAG, field.getName());
        field.setBoolean(menu, true);
        assertTrue(menu.neoecoae$bigOrderCpu);
        assertFalse(menu.md123$neoecoae$bigOrderCpu);
    }

    @Test void uniqueRenamedFieldIsResolvedFromActualHierarchyAndPrimitiveSignature() throws ReflectiveOperationException {
        var menu = new RuntimeSubclass();
        var field = EcoOmniPlanMenuAccess.resolveField(menu.getClass(), FLAG, boolean.class);
        assertEquals(RenamedMenu.class, field.getDeclaringClass());
        assertEquals("md123$neoecoae$bigOrderCpu", field.getName());
        field.setBoolean(menu, true);
        assertTrue(field.getBoolean(menu));
        assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(menu.getClass(), FLAG, Boolean.class));
    }

    @Test void ambiguityIsRejectedBeforeTypeFiltering() {
        var failure = assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(Ambiguous.class, FLAG, boolean.class));
        assertTrue(failure.getMessage().contains("first$"));
        assertTrue(failure.getMessage().contains("second$"));
        assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(DuplicateInHierarchy.class, FLAG, boolean.class));
    }

    @Test void exactWrongTypeCannotBeBypassedWithAValidSuffix() {
        assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(WrongExactType.class, FLAG, boolean.class));
        assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(BoxedFlag.class, FLAG, boolean.class));
    }

    @Test void unrelatedSameTypeFieldIsNeverAGuessAndStaticOrFinalFieldsAreRejected() {
        assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(Unrelated.class, FLAG, boolean.class));
        assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(StaticFlag.class, FLAG, boolean.class));
        assertThrows(NoSuchFieldException.class,
                () -> EcoOmniPlanMenuAccess.resolveField(FinalFlag.class, FLAG, boolean.class));
    }

    @Test void allFourRenamedAccessorsAreCachedPerActualClassButValuesStayLive() {
        bootstrap();
        var first = new RenamedMenu();
        var second = new RenamedMenu();
        var access = EcoOmniPlanMenuAccess.of(first);
        assertSame(access, EcoOmniPlanMenuAccess.of(second));
        assertNotSame(access, EcoOmniPlanMenuAccess.of(new RuntimeSubclass()));
        access.requireReady();
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 123), 0,
                true, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
        var confirmed = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, plan,
                new ECOPlanTrace(), List.of(), List.of(), List.of(), 0);
        var options = new ECOPlannerOptions(true, true, Set.of(), true);
        first.md123$neoecoae$confirmedPlanningResult = confirmed;
        first.md123$neoecoae$originalOptions = options;
        assertSame(confirmed, access.confirmed(first));
        assertSame(options, access.options(first));
        assertNull(access.confirmed(second), "The cache must contain accessors, never another menu's state");
        var order = EcoOmniPlanMenu.prepare(plan, access.confirmed(first), confirmed, access.options(first), true);
        assertSame(confirmed, order.original());
        assertSame(options, order.options());
        assertSame(options, order.parent().options());

        var replacement = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, plan,
                new ECOPlanTrace(), List.of(), List.of(), List.of(), 0);
        first.md123$neoecoae$confirmedPlanningResult = replacement;
        assertSame(replacement, access.confirmed(first), "Always read ECO's current confirmation identity");
        assertEquals("CONFIRMED_RESULT_CHANGED", assertThrows(EcoOmniPlanMenu.Rejected.class,
                () -> EcoOmniPlanMenu.prepare(plan, access.confirmed(first), confirmed, access.options(first), true)).getMessage());
        access.available(first, true);
        access.diagnostic(first, "preview");
        assertTrue(first.md123$neoecoae$bigOrderCpu);
        assertEquals("preview", first.md123$neoecoae$planningDiagnostic);
        assertFalse(second.md123$neoecoae$bigOrderCpu);
        access.clearConfirmed(first);
        assertNull(first.md123$neoecoae$confirmedPlanningResult);
        assertSame(options, first.md123$neoecoae$originalOptions);
    }

    @Test void cachedConfirmationFailureStillClosesAvailabilityAndReportsDiagnostic() {
        var menu = new BrokenConfirmation();
        var access = EcoOmniPlanMenuAccess.of(menu);
        var failure = assertThrows(EcoOmniPlanMenuAccess.Failure.class, access::requireReady);
        assertSame(failure, assertThrows(EcoOmniPlanMenuAccess.Failure.class, access::requireReady));
        assertTrue(failure.getCause().getMessage().contains("confirmedPlanningResult"));
        access.failClosed(menu, failure);
        assertFalse(menu.md123$neoecoae$bigOrderCpu);
        assertEquals("Omni: MENU_ACCESS_FAILED", menu.md123$neoecoae$planningDiagnostic);
    }

    @Test void availabilityAndDiagnosticFailuresRemainVisibleAsSuppressedCausesWithoutRepeatedGrowth() {
        var menu = new Object();
        var access = EcoOmniPlanMenuAccess.of(menu);
        var failure = assertThrows(EcoOmniPlanMenuAccess.Failure.class, access::requireReady);
        access.failClosed(menu, failure);
        assertEquals(2, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("bigOrderCpu"));
        assertTrue(failure.getSuppressed()[1].getMessage().contains("planningDiagnostic"));
        access.failClosed(menu, failure);
        assertEquals(2, failure.getSuppressed().length);
    }

    private static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static class RenamedMenu {
        private ECOPlanningResult md123$neoecoae$confirmedPlanningResult;
        private ECOPlannerOptions md123$neoecoae$originalOptions;
        private boolean md123$neoecoae$bigOrderCpu;
        private String md123$neoecoae$planningDiagnostic;
    }
    private static final class RuntimeSubclass extends RenamedMenu {}
    private static class ExactAndRenamed {
        private boolean neoecoae$bigOrderCpu;
        private boolean md123$neoecoae$bigOrderCpu;
    }
    private static final class DuplicateInHierarchy extends ExactAndRenamed {
        private boolean neoecoae$bigOrderCpu;
    }
    private static final class Ambiguous {
        private boolean first$neoecoae$bigOrderCpu;
        private String second$neoecoae$bigOrderCpu;
    }
    private static final class WrongExactType {
        private String neoecoae$bigOrderCpu;
        private boolean md123$neoecoae$bigOrderCpu;
    }
    private static final class BoxedFlag { private Boolean md123$neoecoae$bigOrderCpu; }
    private static final class Unrelated { private boolean otherAvailability; }
    private static final class StaticFlag { private static boolean neoecoae$bigOrderCpu; }
    private static final class FinalFlag { private final boolean neoecoae$bigOrderCpu = false; }
    private static final class BrokenConfirmation {
        private Object md123$neoecoae$confirmedPlanningResult;
        private ECOPlannerOptions md123$neoecoae$originalOptions;
        private boolean md123$neoecoae$bigOrderCpu = true;
        private String md123$neoecoae$planningDiagnostic = "old";
    }
}
