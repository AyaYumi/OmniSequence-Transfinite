package com.atir.molecularmanipulator.verification;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Opt-in client runner for suites whose dependencies require an integrated server. */
@Mod.EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class IntegratedGameTestClient {
    private static final Path DIRECTORY = Minecraft.getInstance().gameDirectory.toPath();
    private static boolean worldRequested, registered;
    private static volatile boolean finished;
    private static int clientTicks;
    private static MultipleTestTracker tracker;
    private static List<GameTestInfo> running;
    private static GameTestTicker ticker;

    private static void finish(Throwable failure) {
        if (finished) return;
        finished = true;
        if (failure != null) failure.printStackTrace();
        var report = new LinkedHashMap<String, Object>();
        report.put("passed", failure == null);
        report.put("error", failure == null ? "" : failure.toString());
        if (running != null) report.put("tests", running.stream().map(test -> Map.of("name", test.getTestName(),
                "passed", test.hasSucceeded(), "error", test.getError() == null ? "" : test.getError().toString())).toList());
        try {
            Files.writeString(DIRECTORY.resolve("gametest-result.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception saveFailure) { saveFailure.printStackTrace(); }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
    }

    @SubscribeEvent public static void client(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (++clientTicks > 12000) throw new AssertionError("Integrated GameTest client timeout");
            if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                mc.options.onboardAccessibility = false;
                mc.setScreen(new TitleScreen());
            }
            if (!worldRequested && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                worldRequested = true;
                mc.options.pauseOnLostFocus = false;
                String name = "Regression-" + System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(name,
                        new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(91487, false, false),
                        registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions());
            }
        } catch (Throwable failure) { finish(failure); }
    }

    @SubscribeEvent public static void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            if (!registered) {
                registered = true;
                var functions = GameTestRegistry.getAllTestFunctions().stream()
                        .filter(test -> !"native-essence".equals(System.getProperty("molecularmanipulator.gameTestSuite"))
                                || !test.getTestName().contains("eco"))
                        .toList();
                if (functions.isEmpty()) throw new AssertionError("No selected GameTests registered");
                ticker = new GameTestTicker();
                running = new ArrayList<>(GameTestRunner.runTests(functions, new BlockPos(1000, 120, 1000),
                        Rotation.NONE, server.overworld(), ticker, 6));
                tracker = new MultipleTestTracker(running);
            }
            ticker.tick();
            if (tracker.isDone()) finish(tracker.hasFailedRequired() ? new AssertionError("Required GameTest failed") : null);
        } catch (Throwable failure) { finish(failure); }
    }
}
