package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.network.BuildingPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class StarChartScreenStateTest {
    @Test void unfoldingStillRequiresTheSelectedPackageAndTheEntireConflictFreeSurvey() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var saved = new HashMap<String, Object>();
        for (String name : List.of("request", "received", "expected", "conflicts", "error", "PROJECTION", "awaitingDeployment", "geometryDirty", "siteSummary", "siteDetail")) {
            var field = StarChartClient.class.getDeclaredField(name); field.setAccessible(true);
            saved.put(name, name.equals("PROJECTION") ? new ArrayList<>((List<?>)field.get(null)) : field.get(null));
        }
        try {
            var screen = ResponsiveScreenTestFixture.allocate(StarChartClient.ChartScreen.class);
            var canDeploy = screen.getClass().getDeclaredMethod("canDeploy"); canDeploy.setAccessible(true);
            ResponsiveScreenTestFixture.set(screen, "slots", List.of(0));
            UUID active = UUID.randomUUID();
            state("request", active); state("received", 0); state("expected", 0); state("conflicts", 0); state("error", "");
            state("awaitingDeployment", false);
            assertEquals(false, canDeploy.invoke(screen));
            StarChartClient.receive(new BuildingPayloads.Preview(UUID.randomUUID(), 0, 1, 0, List.of(), ""));
            assertEquals(false, canDeploy.invoke(screen), "Ignore stale responses for another placement");
            StarChartClient.receive(new BuildingPayloads.Preview(active, 0, 2, 0, List.of(), ""));
            assertEquals(false, canDeploy.invoke(screen), "One page does not complete a two-page survey");
            StarChartClient.receive(new BuildingPayloads.Preview(active, 1, 2, 0, List.of(), ""));
            assertEquals(true, canDeploy.invoke(screen));
            for (String phase : List.of("DEPLOYING", "BLOCKED", "PAUSED", "ROLLING_BACK")) {
                ResponsiveScreenTestFixture.set(screen, "transferState", phase);
                assertEquals(false, canDeploy.invoke(screen), "An active operation cannot authorize a second deployment");
            }
            ResponsiveScreenTestFixture.set(screen, "transferState", "");
            state("awaitingDeployment", true);
            assertEquals(false, canDeploy.invoke(screen), "Do not submit again before the server confirms the reservation");
            state("awaitingDeployment", false);
            state("conflicts", 1); assertEquals(false, canDeploy.invoke(screen));
            state("conflicts", 0); state("error", "Archive missing"); assertEquals(false, canDeploy.invoke(screen));
            state("error", ""); ResponsiveScreenTestFixture.set(screen, "slots", List.of());
            assertEquals(false, canDeploy.invoke(screen));
        } finally {
            var field = StarChartClient.class.getDeclaredField("PROJECTION"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var projection = (List<Object>)field.get(null);
            projection.clear(); projection.addAll((List<?>)saved.remove("PROJECTION"));
            for (var entry : saved.entrySet()) state(entry.getKey(), entry.getValue());
        }
    }

    @Test void constructionProgressStartsOnlyAfterTheMatchingServerAcknowledgement() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var saved = new HashMap<String, Object>();
        for (String name : List.of("request", "awaitingDeployment", "deploying", "surveyScreen")) {
            var field = StarChartClient.class.getDeclaredField(name); field.setAccessible(true);
            saved.put(name, field.get(null));
        }
        try {
            var active = UUID.randomUUID();
            var screen = ResponsiveScreenTestFixture.allocate(StarChartClient.ChartScreen.class);
            state("request", active); state("awaitingDeployment", true); state("deploying", false); state("surveyScreen", screen);
            var deployed = StarChartClient.class.getDeclaredMethod("deployed", BuildingPayloads.Deployment.class);
            deployed.setAccessible(true);
            deployed.invoke(null, new BuildingPayloads.Deployment(UUID.randomUUID(), true));
            assertEquals(true, getState("awaitingDeployment"));
            assertEquals(false, getState("deploying"));
            assertSame(screen, getState("surveyScreen"));
            deployed.invoke(null, new BuildingPayloads.Deployment(active, true));
            assertEquals(false, getState("awaitingDeployment"));
            assertEquals(true, getState("deploying"));
            assertSame(screen, getState("surveyScreen"), "Deployment must keep the pinned panel available for interruption and reopening");
        } finally {
            for (var entry : saved.entrySet()) state(entry.getKey(), entry.getValue());
        }
    }

    @Test void rightClickReusesThePinnedPanelAndLocationUntilExplicitCancellation() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var saved = new HashMap<String, Object>();
        for (String name : List.of("surveyScreen", "surveyLevel", "deploying", "awaitingDeployment")) saved.put(name, getState(name));
        try {
            var level = ResponsiveScreenTestFixture.allocate(net.minecraft.client.multiplayer.ClientLevel.class);
            var screen = ResponsiveScreenTestFixture.allocate(StarChartClient.ChartScreen.class);
            var base = new net.minecraft.core.BlockPos(12, 80, 34);
            ResponsiveScreenTestFixture.set(screen, "base", base);
            ResponsiveScreenTestFixture.set(screen, "facing", net.minecraft.core.Direction.WEST);
            ResponsiveScreenTestFixture.set(screen, "selected", 2);
            state("surveyLevel", level); state("surveyScreen", screen);
            for (boolean deploying : List.of(false, true)) {
                state("deploying", deploying); state("awaitingDeployment", deploying);
                assertSame(screen, StarChartClient.retainedPanel(level), "Right-click must reopen the same panel, even during construction");
                for (var expected : java.util.Map.of("base", base, "facing", net.minecraft.core.Direction.WEST, "selected", 2).entrySet()) {
                    var field = screen.getClass().getDeclaredField(expected.getKey()); field.setAccessible(true);
                    assertEquals(expected.getValue(), field.get(screen));
                }
            }
            assertNull(StarChartClient.retainedPanel(ResponsiveScreenTestFixture.allocate(net.minecraft.client.multiplayer.ClientLevel.class)));
            state("surveyScreen", null); // The Cancel point action releases this exact reference.
            assertNull(StarChartClient.retainedPanel(level), "A new point is selectable only after releasing the previous panel");
        } finally {
            for (var entry : saved.entrySet()) state(entry.getKey(), entry.getValue());
        }
    }

    @Test void interruptButtonOnlyEnablesForOwnedUnfinishedOperationsAndNotPendingRollback() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var screen = ResponsiveScreenTestFixture.allocate(StarChartClient.ChartScreen.class);
        var method = screen.getClass().getDeclaredMethod("canInterrupt"); method.setAccessible(true);
        ResponsiveScreenTestFixture.set(screen, "transferId", UUID.randomUUID());
        for (String state : List.of("DEPLOYING", "BLOCKED", "PAUSED")) {
            ResponsiveScreenTestFixture.set(screen, "transferState", state);
            assertEquals(true, method.invoke(screen));
        }
        for (String state : List.of("", "SEALED", "CANCELLED", "DEPLOYED", "ROLLING_BACK", "UNAVAILABLE")) {
            ResponsiveScreenTestFixture.set(screen, "transferState", state);
            assertEquals(false, method.invoke(screen));
        }
        ResponsiveScreenTestFixture.set(screen, "transferState", "BLOCKED");
        ResponsiveScreenTestFixture.set(screen, "awaitingInterruption", true);
        assertEquals(false, method.invoke(screen), "No duplicate request while interruption is awaiting acknowledgement");
        ResponsiveScreenTestFixture.set(screen, "awaitingInterruption", false);
        ResponsiveScreenTestFixture.set(screen, "transferId", null);
        assertEquals(false, method.invoke(screen), "Server must identify the player's operation before enabling interruption");
    }

    private static Object getState(String name) throws Exception {
        var field = StarChartClient.class.getDeclaredField(name); field.setAccessible(true);
        return field.get(null);
    }

    @Test void archivePaginationDisablesPendingUnavailableAndOutOfRangeActions() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var screen = ResponsiveScreenTestFixture.allocate(StarChartClient.DetailsScreen.class);
        var previous = Button.builder(Component.empty(), b -> {}).bounds(0, 0, 26, 22).build();
        var next = Button.builder(Component.empty(), b -> {}).bounds(0, 0, 26, 22).build();
        ResponsiveScreenTestFixture.set(screen, "previous", previous);
        ResponsiveScreenTestFixture.set(screen, "next", next);
        var refresh = screen.getClass().getDeclaredMethod("refresh"); refresh.setAccessible(true);
        for (int page = 0; page < 3; page++) {
            var data = new BuildingPayloads.Details(UUID.randomUUID(), page, 3, 100, 1024, List.of(), false);
            ResponsiveScreenTestFixture.set(screen, "data", data);
            ResponsiveScreenTestFixture.set(screen, "pending", false);
            refresh.invoke(screen);
            assertEquals(page > 0, previous.active); assertEquals(page < 2, next.active);
            ResponsiveScreenTestFixture.set(screen, "pending", true);
            refresh.invoke(screen); assertFalse(previous.active); assertFalse(next.active);
        }
        ResponsiveScreenTestFixture.set(screen, "pending", false);
        ResponsiveScreenTestFixture.set(screen, "data", new BuildingPayloads.Details(UUID.randomUUID(), 0, 1, 0, 0, List.of(), true));
        refresh.invoke(screen); assertFalse(previous.active); assertFalse(next.active);
        ResponsiveScreenTestFixture.set(screen, "data", null);
        refresh.invoke(screen); assertFalse(previous.active); assertFalse(next.active);
    }

    private static void state(String name, Object value) throws Exception {
        var field = StarChartClient.class.getDeclaredField(name); field.setAccessible(true); field.set(null, value);
    }
}
