package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.TaixuContent;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class TaixuConstructionGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1600, 120, 1600);
    private static final Direction FACING = Direction.EAST;
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 1000)
    public static void constructionPersistenceProtectionAndRecovery(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var chunks = TaixuStructure.chunks(ORIGIN, FACING);
        for (var chunk : chunks) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        for (var part : TaixuStructure.parts()) level.setBlock(pos(part), Blocks.AIR.defaultBlockState(), 2);
        for (var air : TaixuStructure.requiredAir()) level.setBlock(TaixuStructure.worldPos(ORIGIN, FACING, air), Blocks.AIR.defaultBlockState(), 2);
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("d79be3e7-9f13-43f1-8ef3-91e4823ecdd8"), "TaixuVerifier"));
        player.setPos(ORIGIN.getX() + .5, ORIGIN.getY() + 1, ORIGIN.getZ() + .5);
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
        var previous = players.put(player.getUUID(), player);
        try {
            level.setBlock(ORIGIN, TaixuContent.CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, FACING), 3);
            var machine = (TaixuBlockEntity) level.getBlockEntity(ORIGIN);
            player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
            machine.startBuild(player); retry(level, machine);
            helper.assertTrue(machine.status() == TaixuBlockEntity.Status.MATERIAL && machine.progress() == 0, "Missing material must retain the cursor");
            var first = TaixuStructure.parts().getFirst(); var material = new ItemStack(TaixuStructure.block(first.type()));
            player.getInventory().add(material.copy());
            Consumer<BlockEvent.EntityPlaceEvent> deny = event -> { if (event.getPos().equals(pos(first))) event.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(deny);
            try {
                retry(level, machine);
                helper.assertTrue(machine.status() == TaixuBlockEntity.Status.PROTECTED && level.getBlockState(pos(first)).isAir(), "Cancelled placement must restore the old world state");
                helper.assertTrue(machine.getInternalInventory().getStackInSlot(0).getCount() == 1, "Cancelled placement must retain exactly one refund");
            } finally { NeoForge.EVENT_BUS.unregister(deny); }
            machine = reload(level, machine);
            retry(level, machine);
            helper.assertTrue(TaixuStructure.matches(level.getBlockState(pos(first)), first, FACING), "Refund must fund the same first placement after reload");
            helper.assertTrue(player.getInventory().isEmpty() && machine.getInternalInventory().isEmpty(), "Survival placement must consume exactly one item");
            machine.togglePause(player); int cursor = machine.progress(); retry(level, machine);
            helper.assertTrue(machine.progress() == cursor && machine.paused(), "Pause must preserve work");
            machine = reload(level, machine); helper.assertTrue(machine.paused(), "Pause must survive reload");
            machine.togglePause(player); player.setGameMode(GameType.CREATIVE);
            var conflict = TaixuStructure.parts().get(cursor); level.setBlock(pos(conflict), Blocks.DIRT.defaultBlockState(), 3);
            retry(level, machine); helper.assertTrue(machine.status() == TaixuBlockEntity.Status.CONFLICT && machine.progress() == cursor, "Foreign block must not be overwritten");
            level.setBlock(pos(conflict), Blocks.AIR.defaultBlockState(), 3); retry(level, machine);
            machine = reload(level, machine); retry(level, machine);
            for (int tick = 0; tick < 1000 && machine.operation() != TaixuBlockEntity.Operation.IDLE; tick++) machine.serverTick();
            helper.assertTrue(machine.formed() && machine.status() == TaixuBlockEntity.Status.COMPLETE, "Full rotated blueprint must form");
            helper.assertTrue(machine.inspection().correct() == TaixuStructure.parts().size(), "Inspection and construction must agree exactly");
            helper.assertTrue(TaixuStructure.materials().stream().mapToInt(ItemStack::getCount).sum() + 1 == machine.inspection().correct(), "JEI bill of materials must match actual construction");
            machine.startDismantle(player); machine.serverTick(); int removed = machine.progress();
            helper.assertTrue(removed == 64, "Dismantling is bounded per tick");
            machine = reload(level, machine);
            helper.assertTrue(machine.progress() == removed, "Recovery cursor must persist");
            player.setGameMode(GameType.SURVIVAL);
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            retry(level, machine);
            helper.assertTrue(machine.status() == TaixuBlockEntity.Status.STORAGE_FULL && !machine.getInternalInventory().isEmpty(), "Full storage must preserve recovery in the visible buffer");
            int blocked = machine.progress(); retry(level, machine);
            helper.assertTrue(machine.progress() == blocked, "Full buffer must stop all further removal");
            var savedRecovery = machine.getInternalInventory().getStackInSlot(0).copy(); machine = reload(level, machine);
            helper.assertTrue(ItemStack.matches(savedRecovery, machine.getInternalInventory().getStackInSlot(0)), "Recovery buffer must persist");
            player.getInventory().clearContent(); player.setGameMode(GameType.CREATIVE); retry(level, machine);
            for (int tick = 0; tick < 1000 && machine.operation() != TaixuBlockEntity.Operation.IDLE; tick++) machine.serverTick();
            helper.assertTrue(machine.operation() == TaixuBlockEntity.Operation.IDLE && level.getBlockEntity(ORIGIN) == machine, "Reclaim must finish and retain its controller");
            for (var part : TaixuStructure.parts()) if (!TaixuStructure.isController(part)) helper.assertTrue(level.getBlockState(pos(part)).isAir(), "All authored parts should be reclaimed");
            helper.assertTrue(!TaixuStructure.fits(level, new BlockPos(0, level.getMaxBuildHeight() - 10, 0), FACING), "High placement must fail before construction");
            System.out.println("TAIXU_CONSTRUCTION_PASS parts=" + TaixuStructure.parts().size() + " materials=" + TaixuStructure.counts()
                    + " survival=true rollback=true pause=true saveReload=true rotated=true recoveryBackpressure=true");
            helper.succeed();
        } finally {
            if (previous == null) players.remove(player.getUUID()); else players.put(player.getUUID(), previous);
            level.setBlock(ORIGIN, Blocks.AIR.defaultBlockState(), 3);
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
    private static BlockPos pos(TaixuStructure.Part part) { return TaixuStructure.worldPos(ORIGIN, FACING, part); }
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void wiredMeSupplyAndRecovery(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var anchor = new BlockPos(2016, 120, 2016);
        var footprint = TaixuStructure.chunks(anchor, Direction.NORTH);
        for (var chunk : footprint) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunk(chunk.x, chunk.z); }
        for (var part : TaixuStructure.parts()) level.setBlock(TaixuStructure.worldPos(anchor, Direction.NORTH, part), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(anchor, TaixuContent.CONTROLLER.get().defaultBlockState(), 3);
        level.setBlock(anchor.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var machine = (TaixuBlockEntity) level.getBlockEntity(anchor);
        helper.runAfterDelay(60, () -> {
            try {
                helper.assertTrue(machine.networkOnline(), "Controller must receive power and a channel through a physical neighbour");
                var item = appeng.api.stacks.AEItemKey.of(TaixuContent.SPIRE.get().asItem());
                long[] stock = {2};
                var storage = new appeng.api.storage.MEStorage() {
                    @Override public long extract(appeng.api.stacks.AEKey what, long amount, appeng.api.config.Actionable mode, appeng.api.networking.security.IActionSource source) {
                        if (!what.equals(item)) return 0;
                        long taken = Math.min(stock[0], amount); if (mode == appeng.api.config.Actionable.MODULATE) stock[0] -= taken; return taken;
                    }
                    @Override public long insert(appeng.api.stacks.AEKey what, long amount, appeng.api.config.Actionable mode, appeng.api.networking.security.IActionSource source) {
                        if (!what.equals(item)) return 0;
                        if (mode == appeng.api.config.Actionable.MODULATE) stock[0] += amount; return amount;
                    }
                    @Override public void getAvailableStacks(appeng.api.stacks.KeyCounter counter) { counter.add(item, stock[0]); }
                    @Override public net.minecraft.network.chat.Component getDescription() { return net.minecraft.network.chat.Component.literal("Taixu verification storage"); }
                };
                var service = machine.getMainNode().getGrid().getStorageService();
                appeng.api.storage.IStorageProvider provider = mounts -> mounts.mount(storage, 0);
                service.addGlobalStorageProvider(provider);
                var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("c1a04a02-226a-408d-ae05-71c41b14ebf6"), "TaixuMeVerifier"));
                player.setPos(anchor.getX() + .5, anchor.getY() + 1, anchor.getZ() - 1); player.setGameMode(GameType.SURVIVAL);
                var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
                @SuppressWarnings("unchecked") var players = (Map<UUID, ServerPlayer>) field.get(level.getServer().getPlayerList());
                players.put(player.getUUID(), player);
                try {
                    player.getInventory().clearContent(); player.getInventory().add(new ItemStack(TaixuContent.SPIRE.get()));
                    machine.startBuild(player); retry(level, machine);
                    helper.assertTrue(stock[0] == 2 && player.getInventory().isEmpty(), "Player material must precede ME extraction");
                    machine.cancel(player);
                    var first = TaixuStructure.worldPos(anchor, Direction.NORTH, TaixuStructure.parts().getFirst());
                    level.setBlock(first, Blocks.AIR.defaultBlockState(), 3);
                    machine.startBuild(player); retry(level, machine);
                    helper.assertTrue(stock[0] == 1 && level.getBlockState(first).is(TaixuContent.SPIRE.get()), "Wired ME must supply the exact missing material once");
                    players.remove(player.getUUID()); int stopped = machine.progress(); retry(level, machine);
                    helper.assertTrue(machine.progress() == stopped && machine.status() == TaixuBlockEntity.Status.OWNER_OFFLINE, "Offline owner must stop world mutation");
                    players.put(player.getUUID(), player); machine.cancel(player); machine.startDismantle(player); retry(level, machine);
                    helper.assertTrue(stock[0] == 2 && player.getInventory().isEmpty() && machine.getInternalInventory().isEmpty(), "Recovered material must return to ME exactly once");
                    var unloaded = new BlockPos(30000, 120, 30000);
                    helper.assertTrue(!level.hasChunkAt(unloaded), "Unloaded test site must start unloaded");
                    helper.assertTrue(TaixuStructure.inspect(level, unloaded, Direction.NORTH).unloaded() > 0 && !level.hasChunkAt(unloaded), "Inspection must not synchronously load absent chunks");
                    System.out.println("TAIXU_ME_PASS wiredChannel=true playerFirst=true exactExtraction=true exactRecovery=true offlineWait=true unloadedSafe=true");
                    helper.succeed();
                } finally { players.remove(player.getUUID()); service.removeGlobalStorageProvider(provider); }
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally {
                level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3); level.setBlock(anchor.north(), Blocks.AIR.defaultBlockState(), 3);
                for (var chunk : footprint) level.setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }
    private static void retry(ServerLevel level, TaixuBlockEntity machine) { ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime((level.getGameTime() / 20 + 1) * 20); machine.serverTick(); }
    private static TaixuBlockEntity reload(ServerLevel level, TaixuBlockEntity machine) {
        var tag = machine.saveWithFullMetadata(level.registryAccess()); var state = machine.getBlockState();
        level.removeBlockEntity(ORIGIN);
        var reloaded = (TaixuBlockEntity) BlockEntity.loadStatic(ORIGIN, state, tag, level.registryAccess());
        level.setBlockEntity(reloaded); return reloaded;
    }
}
