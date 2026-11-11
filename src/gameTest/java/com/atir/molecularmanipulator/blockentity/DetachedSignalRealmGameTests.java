package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.network.LanternPayloads;
import com.atir.molecularmanipulator.registry.SimulationLanternContent;
import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import com.atir.molecularmanipulator.world.SimulationLanternLights;
import com.atir.molecularmanipulator.world.LanternSimulation;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class DetachedSignalRealmGameTests {
    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void realmIsEmptyAndHasOneAnchorAndAnIndependentVariableClock(GameTestHelper helper) {
        var realm = realm(helper);
        DetachedSignalRealm.ensureAnchor(realm);
        helper.assertTrue(realm.getLevelData() instanceof DetachedSignalRealm.LevelData, "Realm owns its time data");
        helper.assertTrue(realm.dimensionType().fixedTime().orElseThrow() == 18000L, "The dimension type fixes midnight");
        realm.setDayTime(6000L);
        helper.assertTrue(realm.getDayTime() == 18000L && !realm.isRaining(), "Commands and overworld weather cannot change eternal night");
        int blocks = 0;
        for (var pos : BlockPos.betweenClosed(-3, 60, -3, 3, 70, 3)) {
            if (!realm.getBlockState(pos).isAir()) blocks++;
        }
        helper.assertTrue(blocks == 1 && realm.getBlockState(DetachedSignalRealm.ANCHOR).is(Blocks.BEDROCK),
                "There is exactly one bedrock block, not a void-biome start platform");
        long local = realm.getGameTime(), ordinary = helper.getLevel().getGameTime();
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(helper.getLevel().getGameTime() - ordinary == 5L, "Other dimensions still tick normally");
            long elapsed = realm.getGameTime() - local;
            helper.assertTrue(elapsed >= 2L && elapsed <= 10L, "Realm time stays between half and double speed");
            helper.assertTrue(DetachedSignalRealm.timeScale(realm) == .5F || DetachedSignalRealm.timeScale(realm) == 2F,
                    "Only the two requested time rates are used");
            helper.succeed();
        });
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 750)
    public static void entitiesMachinesAndScheduledBlockTicksFollowBothTimeRates(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var realm = realm(helper);
        var pos = new BlockPos(32, 70, 0);
        realm.setChunkForced(2, 0, true);
        realm.getChunkAt(pos);
        var item = new ItemEntity(realm, 34.5D, 73D, .5D, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO); // ItemEntity starts with random lateral velocity; stay in the forced chunk.
        realm.addFreshEntity(item);
        realm.setBlockAndUpdate(pos, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true));
        var furnacePos = pos.above(2);
        realm.setBlockAndUpdate(furnacePos, Blocks.FURNACE.defaultBlockState());
        var furnace = (net.minecraft.world.level.block.entity.FurnaceBlockEntity) realm.getBlockEntity(furnacePos);
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 64));
        furnace.setItem(1, new ItemStack(Items.COAL, 64));
        long[] eventTicks = {0L};
        java.util.function.Consumer<LevelTickEvent.Post> listener = event -> {
            if (event.getLevel() == realm) eventTicks[0]++;
        };
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, listener);
        fixture.onCleanup(() -> NeoForge.EVENT_BUS.unregister(listener));
        fixture.onCleanup(() -> {
            item.discard();
            realm.removeBlockEntity(furnacePos);
            realm.setBlockAndUpdate(furnacePos, Blocks.AIR.defaultBlockState());
            realm.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            realm.setChunkForced(2, 0, false);
        });
        // Once the forced chunk is ready, follow real local time through at least one phase change.
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(item.getAge() > 0, "Entity ticking chunk is ready"))
                .thenExecute(() -> {
                    // Block/chunk initialization can schedule a shorter lamp tick; isolate the deliberate eight-tick schedule.
                    realm.getBlockTicks().clearArea(new BoundingBox(pos));
                    realm.setBlock(pos, Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true), 2);
                    realm.scheduleTick(pos, Blocks.REDSTONE_LAMP, 8);
                    long startTime = realm.getGameTime();
                    long startEvents = eventTicks[0];
                    int startAge = item.getAge();
                    int startCooking = cookingProgress(furnace, realm);
                    long startStep = helper.getTick();
                    helper.onEachTick(new Runnable() {
                        private boolean sawSlow, sawFast;
                        private int steps;
                        private long previousTime = startTime;
                        private long previousStep = startStep;
                        @Override public void run() {
                            if (helper.getTick() <= previousStep) return;
                            previousStep = helper.getTick();
                            long elapsed = realm.getGameTime() - startTime;
                            long advanced = realm.getGameTime() - previousTime;
                            float scale = DetachedSignalRealm.timeScale(realm);
                            sawSlow |= scale == .5F;
                            sawFast |= scale == 2F;
                            helper.assertTrue(scale == .5F ? advanced <= 1L : advanced == 2L,
                                    "Rate=" + scale + ", advanced=" + advanced + ", sample=" + steps
                                            + ", test tick=" + helper.getTick() + ", start=" + startStep);
                            helper.assertTrue(item.getAge() - startAge == elapsed,
                                    "Item age=" + (item.getAge() - startAge) + ", local time=" + elapsed
                                            + ", rate=" + scale + ", step=" + steps + ", removed=" + item.isRemoved());
                            helper.assertTrue(cookingProgress(furnace, realm) - startCooking == elapsed,
                                    "Furnace cooking follows local time in both phases");
                            helper.assertTrue(eventTicks[0] - startEvents == elapsed, "NeoForge tick events follow the same local clock");
                            helper.assertTrue(realm.getBlockState(pos).getValue(RedstoneLampBlock.LIT) == (elapsed < 8L),
                                    "Scheduled lamp: lit=" + realm.getBlockState(pos).getValue(RedstoneLampBlock.LIT)
                                            + ", local time=" + elapsed + ", rate=" + scale + ", sample=" + steps);
                            helper.assertTrue(!DetachedSignalRealm.isAdditionalTick(realm), "The additional-tick flag is cleared");
                            previousTime = realm.getGameTime();
                            if (++steps >= 10 && sawSlow && sawFast && elapsed >= 8L) helper.succeed();
                        }
                    });
                });
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void chunkGenerationTicketsKeepTheNormalServerLifetime(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var realm = realm(helper);
        var chunk = new ChunkPos(128, 128);
        var ticket = TicketType.create("detached_signal_test", Comparator.comparingLong(ChunkPos::toLong), 6);
        realm.getChunkSource().addRegionTicket(ticket, chunk, 2, chunk);
        fixture.onCleanup(() -> realm.getChunkSource().removeRegionTicket(ticket, chunk, 2, chunk));
        realm.getChunk(chunk.x, chunk.z);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(realm.getChunkSource().hasChunk(chunk.x, chunk.z),
                    "Six-tick chunk tickets must not expire after only four global ticks of double simulation");
            helper.assertTrue(!DetachedSignalRealm.isAdditionalTick(realm), "Additional-tick state never escapes the local tick");
            helper.succeed();
        });
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 250)
    public static void lanternVoidEntryWorksFromOverworldNetherAndEndWithoutVoidDamage(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var player = fixture.player("detached-signal-entry", fixture.origin);
        fixture.onCleanup(() -> { LanternSimulation.exit(player); player.discard(); });
        helper.getLevel().addNewPlayer(player);
        var sequence = helper.startSequence();
        for (var sourceKey : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            sequence.thenExecute(() -> {
                var source = helper.getLevel().getServer().getLevel(sourceKey);
                player.teleportTo(source, .5D, 100D, .5D, 0F, 0F);
                var lantern = new ItemStack(SimulationLanternContent.ITEM.get());
                SimulationLanternContent.setLit(lantern, true);
                player.setItemInHand(InteractionHand.MAIN_HAND, lantern);
                LanternSimulation.action(player, LanternPayloads.CLOSE);
            }).thenIdle(21).thenExecute(() -> {
                LanternSimulation.action(player, LanternPayloads.OPEN);
                helper.assertTrue(LanternSimulation.isActive(player), "Native close/open activates simulation in " + sourceKey.location());
                player.setHealth(1F);
                player.setPos(.5D, player.level().getMinBuildHeight() - 33D, .5D);
                helper.assertTrue(SimulationLanternLights.illuminates(player.level(), player.getEyePosition()),
                        "Your own carried light follows you into the void");
                helper.assertTrue(!DetachedSignalRealm.canEnter(player), "A carried lit lantern blocks entry");
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                helper.assertTrue(DetachedSignalRealm.canEnter(player), "Leaving all light qualifies in " + sourceKey.location());
                player.fallDistance = 100F;
                player.setDeltaMovement(0D, -3D, 0D);
                // Same pre-physics event used by actual player ticks, before void damage can run.
                DetachedSignalRealm.beforePlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player));
                helper.assertTrue(DetachedSignalRealm.isRealm(player.level()), "Void entry reaches the actual dimension");
                helper.assertTrue(player.getHealth() == 1F && player.fallDistance == 0F
                                && player.getDeltaMovement().equals(Vec3.ZERO), "Arrival preserves low health and clears the fall");
                helper.assertTrue(!LanternSimulation.isActive(player) && !DetachedSignalRealm.canEnter(player),
                        "Dimension transition ends simulation and cannot recursively re-enter");
                helper.assertTrue(player.position().equals(new Vec3(.5D, 65D, .5D)), "One block is directly beneath your feet");
            });
        }
        sequence.thenSucceed();
    }

    private static int cookingProgress(net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace, ServerLevel level) {
        return furnace.getItem(2).getCount() * 200 + furnace.saveWithFullMetadata(level.registryAccess()).getShort("CookTime");
    }

    private static ServerLevel realm(GameTestHelper helper) {
        var realm = helper.getLevel().getServer().getLevel(DetachedSignalRealm.DIMENSION);
        helper.assertTrue(realm != null, "Dimension datapack entry was loaded");
        return realm;
    }
}
