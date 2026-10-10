package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.WhiteHoleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class CosmicSingularityGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "cosmic_capture", timeoutTicks = 100)
    public static void cachedEntityScanKeepsCaptureAndNewArrivalsCorrect(GameTestHelper helper) {
        var level = helper.getLevel();
        var black = helper.absolutePos(new BlockPos(1, 10, 1));
        var white = black.offset(32, 0, 0);
        var center = black.getCenter();
        long time = level.getGameTime();
        ItemEntity first = new ItemEntity(level, center.x, center.y, center.z, new ItemStack(Items.DIAMOND));
        ItemEntity late = new ItemEntity(level, center.x + 5, center.y, center.z, new ItemStack(Items.GOLD_INGOT));
        var pig = EntityType.PIG.create(level);
        boolean pack = Boolean.getBoolean("omni.packVerification");
        var player = pack ? level.getServer().getPlayerList().getPlayers().get(0) : net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("55ca2c8d-ae1d-4404-9236-e21193d3064d"), "CaptureVerifier"));
        var previousPosition = player.position(); var previousVelocity = player.getDeltaMovement();
        try {
            level.setBlockAndUpdate(white, ModContent.WHITE_HOLE_BLOCK.get().defaultBlockState());
            var ownership = new net.minecraft.nbt.CompoundTag();
            var team = java.util.UUID.fromString("0166c5b5-e73f-4bb2-9c1c-516c225c30d3");
            ownership.putUUID("bound_team", team);
            ((CosmicSingularityBlockEntity) level.getBlockEntity(white)).load(ownership);
            WhiteHoleRegistry.register(level, white, team);
            helper.assertTrue(white.equals(WhiteHoleRegistry.nearest(level, black, team)), "Registered team exit must be found");
            level.setBlockAndUpdate(black, ModContent.BLACK_HOLE_BLOCK.get().defaultBlockState());
            var machine = (CosmicSingularityBlockEntity) level.getBlockEntity(black);
            machine.load(ownership);
            first.setNoGravity(true); level.addFreshEntity(first);
            pig.setPos(center.x, center.y, center.z); pig.setNoGravity(true); level.addFreshEntity(pig);
            if (pack) player.teleportTo(center.x, center.y, center.z);
            else { player.setPos(center.x, center.y, center.z); level.addFreshEntity(player); }
            player.setDeltaMovement(Vec3.ZERO);
            machine.serverTick();
            helper.assertTrue(first.position().distanceTo(white.getCenter()) < 2, "Items must emerge from the white hole");
            helper.assertTrue(pig.position().distanceTo(white.getCenter()) < 2, "Non-player mobs must emerge from the white hole");
            helper.assertTrue(player.position().equals(center) && player.getDeltaMovement().equals(Vec3.ZERO), "Players must never be attracted or teleported");
            var exit = first.position();
            late.setNoGravity(true); late.setDeltaMovement(Vec3.ZERO); level.addFreshEntity(late);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + 1); machine.serverTick();
            helper.assertTrue(late.getDeltaMovement().equals(Vec3.ZERO), "A cached interval must not rescan new arrivals");
            helper.assertTrue(first.position().equals(exit), "An entity teleported outside the capture bounds must be ignored");
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + 5); machine.serverTick();
            helper.assertTrue(late.getDeltaMovement().x < 0, "The next local scan must attract new arrivals");
            late.setDeltaMovement(Vec3.ZERO); late.setPos(center.x + 20, center.y, center.z);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + 6); machine.serverTick();
            helper.assertTrue(late.getDeltaMovement().equals(Vec3.ZERO), "Leaving the bounds must stop attraction immediately");
            level.setBlockAndUpdate(white, Blocks.AIR.defaultBlockState());
            helper.assertTrue(WhiteHoleRegistry.nearest(level, black, team) == null, "Removing the white hole must release its registry claim");
            machine.serverTick();
            System.out.println("COSMIC_CAPTURE_PASS: items, mobs, scan interval, new arrivals, exits and team exit cleanup");
            helper.succeed();
        } finally {
            first.discard(); late.discard(); pig.discard();
            if (pack) { player.teleportTo(previousPosition.x,previousPosition.y,previousPosition.z); player.setDeltaMovement(previousVelocity); }
            else player.discard();
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time);
            level.setBlockAndUpdate(black, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(white, Blocks.AIR.defaultBlockState());
            WhiteHoleRegistry.unregister(level, white);
        }
    }
}
