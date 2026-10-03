package com.atir.molecularmanipulator.verification;

import java.util.ArrayDeque;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Drives production-mapped GameTests after real client/menu verification. */
@Mod("omniverification")
public final class PackFlowVerification {
    public PackFlowVerification() {
        if (!Boolean.getBoolean("omni.packVerification"))
            throw new IllegalStateException("Test mod requires the isolated verification flag");
    }

    public static volatile boolean menusFinished;
    private static volatile boolean finished;
    private static boolean initialized;
    private static int passed, failed, settle;
    private static long ticks;
    private static long tickStart;
    private static final java.util.ArrayList<Long> tickTimes = new java.util.ArrayList<>();
    private static GameTestInfo current;
    private static String timingPhase;
    private static final ArrayDeque<TestFunction> pending = new ArrayDeque<>();

    public static void timingPhase(String phase) {
        reportTiming();
        timingPhase = phase;
    }

    private static void reportTiming() {
        if (tickTimes.isEmpty()) return;
        tickTimes.sort(Long::compare);
        double mean = tickTimes.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        double p95 = tickTimes.get(Math.min(tickTimes.size() - 1, (int) (tickTimes.size() * .95))) / 1_000_000.0;
        double max = tickTimes.get(tickTimes.size() - 1) / 1_000_000.0;
        System.out.println("PACK_FLOW_MSPT=" + (timingPhase == null ? current.getTestName() : timingPhase)
                + " samples=" + tickTimes.size() + " mean=" + mean + " p95=" + p95 + " max=" + max);
        tickTimes.clear();
    }

    @Mod.EventBusSubscriber(modid = "omniverification", value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && finished) Minecraft.getInstance().stop();
        }

        @SubscribeEvent
        @SuppressWarnings("deprecation")
        public static void serverTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START) { tickStart = System.nanoTime(); return; }
            if (event.phase != TickEvent.Phase.END || !menusFinished || finished) return;
            if (current != null && tickStart > 0) tickTimes.add(System.nanoTime() - tickStart);
            var level = event.getServer().overworld();
            try {
                if (!initialized) {
                    initialized = true;
                    Class<?>[] classes = {
                        com.atir.molecularmanipulator.blockentity.Forge206GameTests.class,
                        com.atir.molecularmanipulator.blockentity.MatterAEKeyGameTests.class,
                        com.atir.molecularmanipulator.blockentity.MatterRecipeLookupGameTests.class,
                        com.atir.molecularmanipulator.blockentity.MolecularCenterPatternPortGameTests.class,
                        com.atir.molecularmanipulator.blockentity.MolecularLongBatchGameTests.class,
                        com.atir.molecularmanipulator.blockentity.TransfiniteComputeNexusGameTests.class,
                        com.atir.molecularmanipulator.world.MultiblockSpawnGameTests.class,
                        com.atir.molecularmanipulator.blockentity.SingularityPaletteGameTests.class,
                        com.atir.molecularmanipulator.blockentity.SingularityConstructionGameTests.class,
                        com.atir.molecularmanipulator.blockentity.SingularityMotionGameTests.class,
                        com.atir.molecularmanipulator.blockentity.SingularityEmbeddingGameTests.class,
                        com.atir.molecularmanipulator.blockentity.SingularitySuspendedGameTests.class,
                        com.atir.molecularmanipulator.blockentity.CosmicSingularityGameTests.class,
                        MatterDismantleGameTests.class, MolecularDismantleGameTests.class,
                        OmniDismantleGameTests.class, Legacy139MigrationGameTests.class,
                        com.atir.molecularmanipulator.blockentity.AdvancedAEBatchGameTests.class,
                        Ae2CompatibilityGameTests.class,
                        com.atir.molecularmanipulator.blockentity.PackFeatureGameTests.class
                    };
                    for (var type : classes) GameTestRegistry.register(type);
                    GameTestRegistry.getAllTestFunctions().stream()
                            .filter(test -> test.getStructureName().startsWith("molecularmanipulator:"))
                            .filter(test -> System.getProperty("omni.packTestFilter", "").isEmpty()
                                    || test.getTestName().contains(System.getProperty("omni.packTestFilter")))
                            .sorted(Comparator.comparing(TestFunction::getTestName)).forEach(pending::add);
                    System.out.println("PACK_FLOW_BEGIN tests=" + pending.size());
                    var player = event.getServer().getPlayerList().getPlayers().get(0);
                    player.teleportTo(0.5, 200, 0.5);
                    player.setNoGravity(true);
                }
                if (++ticks > 50_000) throw new IllegalStateException("Pack flow verification timed out");
                // Forge disables automatic GameTest registration/ticking in production.
                GameTestTicker.SINGLETON.tick();
                if (current != null && current.isDone()) {
                    reportTiming(); timingPhase = null;
                    if (current.hasSucceeded()) {
                        passed++;
                        System.out.println("PACK_FLOW_PASS=" + current.getTestName() + " ms=" + current.getRunTime());
                    } else {
                        failed++;
                        System.out.println("PACK_FLOW_FAIL=" + current.getTestName());
                        current.getError().printStackTrace();
                    }
                    current = null; settle = 40;
                }
                if (current != null) return;
                if (settle-- > 0) return;
                if (pending.isEmpty()) {
                    System.out.println("PACK_FLOW_COMPLETE passed=" + passed + " failed=" + failed);
                    if (failed == 0) System.out.println("PACK_FLOW_ALL_PASS");
                    finished = true;
                    return;
                }
                var test = pending.removeFirst();
                current = new GameTestInfo(test, Rotation.NONE, level);
                System.out.println("PACK_FLOW_START=" + test.getTestName());
                GameTestRunner.runTest(current, new BlockPos(0, 160, 0), GameTestTicker.SINGLETON);
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.out.println("PACK_FLOW_FATAL=" + failure);
                finished = true;
            }
        }
    }
}
