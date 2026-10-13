package com.atir.molecularmanipulator.verification;

import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.gravity.GravityController;
import com.atir.molecularmanipulator.world.gravity.GravityPlayerAccess;
import com.craisinlord.antarchy.content.gravity.AntarchyGravityApi;
import com.craisinlord.antarchy.content.gravity.AntarchyGravityDirection;
import com.craisinlord.antarchy.content.gravity.AntarchyGravityRotationUtil;
import com.craisinlord.antarchy.content.gravity.AntarchyGravityTransition;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Loaded only by the optional Antarchy verification source set. Drives the real client and server. */
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class AntarchyGravityClientChecks {
    private static final BlockPos CRYSTAL = new BlockPos(1, 150, 0);
    private static int stage, age, ticks;
    private static CompletableFuture<?> operation;
    private static Vec3 start;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("omnisequence.antarchy_gravity_check")) return;
        var mc = Minecraft.getInstance();
        if (++age > 3600) throw new IllegalStateException("Antarchy check timed out at stage " + stage);
        if (operation != null) {
            if (!operation.isDone()) return;
            operation.join();
            operation = null;
        }
        if (stage == 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardingAccessibilityFinished(); mc.setScreen(new TitleScreen());
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            stage = 1;
            mc.createWorldOpenFlows().createFreshLevel("antarchy-gravity-" + System.currentTimeMillis(),
                    new LevelSettings("Antarchy Gravity Check", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(8327461, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), null);
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        var player = mc.player;
        if (stage == 1) {
            mc.setScreen(null);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            checkAntarchyCamera(mc);
            operation = server(mc, p -> {
                var level = p.serverLevel();
                level.setChunkForced(0, 0, true);
                level.setChunkForced(0, -1, true);
                level.setChunkForced(-1, 0, true);
                level.setChunkForced(-1, -1, true);
                for (int x = -2; x <= 6; x++) for (int y = 147; y <= 156; y++) for (int z = -4; z <= 4; z++)
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                for (int y = 147; y <= 155; y++) for (int z = -4; z <= 4; z++)
                    level.setBlock(new BlockPos(0, y, z), Blocks.STONE.defaultBlockState(), 3);
                for (int x = 1; x <= 6; x++) for (int z = -4; z <= 4; z++) {
                    level.setBlock(new BlockPos(x, 155, z), Blocks.STONE.defaultBlockState(), 3);
                    level.setBlock(new BlockPos(x, 147, z), Blocks.STONE.defaultBlockState(), 3);
                }
                level.setBlock(CRYSTAL, ModContent.GRAVITY_CRYSTAL_BLOCK.get().defaultBlockState()
                        .setValue(DirectionalBlock.FACING, Direction.EAST), 3);
                p.setDeltaMovement(Vec3.ZERO);
                p.connection.teleport(2.8, 150.5, 1.4, 0, 0);
            });
            stage = 2; ticks = 0; return;
        }
        if (stage == 2) {
            if (GravityController.direction(player) != Direction.WEST || !player.onGround()) return;
            check(up(warmCamera(mc)).x > .999F, "Gravity crystal must retain its wall camera with Antarchy loaded");
            LogUtils.getLogger().info("ANTARCHY_GRAVITY_STAGE crystal wall gravity active; applying overlapping Antarchy inversion");
            operation = server(mc, p -> AntarchyGravityApi.setForcedGravityDirection(p, AntarchyGravityDirection.UP));
            stage = 3; ticks = 0; return;
        }
        if (stage == 3 && ++ticks >= 3) {
            check(AntarchyGravityApi.isGravityInverted(player), "Antarchy inversion must synchronize to the client");
            check(GravityController.direction(player) == Direction.DOWN,
                    "Overlapping Antarchy inversion must suspend the crystal frame instead of applying two gravity transforms");
            check(!((GravityPlayerAccess) player).omnisequence$gravity().equals(Direction.WEST),
                    "Server handoff must also clear the synchronized crystal direction");
            check(up(warmCamera(mc)).y < -.999F, "Overlapping inversion must retain Antarchy's upside-down camera");
            check(Math.abs(player.getBoundingBox().getXsize() - player.getBbWidth()) < 1E-6,
                    "External handoff must restore Antarchy's vertical collision body");
            // Place the inverted player at a ceiling away from the crystal and verify real input/collision.
            operation = server(mc, p -> {
                p.setDeltaMovement(Vec3.ZERO);
                p.connection.teleport(4.5, 154.5, .5, 0, 0);
            });
            stage = 4; ticks = 0; return;
        }
        if (stage == 4) {
            if (ticks == 0) LogUtils.getLogger().info("ANTARCHY_GRAVITY_STAGE ceiling walking position={} bounds={}",
                    player.position(), player.getBoundingBox());
            if (ticks > 100) throw new IllegalStateException("Inverted player did not land on ceiling: "
                    + player.position() + " / " + player.getBoundingBox() + " / velocity=" + AntarchyGravityApi.getWorldVelocity(player));
            if (++ticks < 20 || !player.onGround()) return;
            check(AntarchyGravityApi.isGravityInverted(player), "Ceiling walking must retain Antarchy gravity");
            var camera = warmCamera(mc);
            check(camera.getPosition().distanceTo(player.getEyePosition()) < .001, "Inverted first-person anchor must remain at Antarchy eyes");
            check(up(camera).y < -.999F, "Inverted camera must remain upside down");
            start = player.position();
            mc.options.keyUp.setDown(true);
            stage = 5; ticks = 0; return;
        }
        if (stage == 5 && ++ticks >= 5) {
            mc.options.keyUp.setDown(false);
            check(player.position().subtract(start).horizontalDistance() > .08, "Real input must walk along Antarchy's ceiling");
            check(Math.abs(player.getY() - start.y) < .05, "Inverted walking must keep feet on the ceiling");
            mc.options.keyJump.setDown(true);
            stage = 6; ticks = 0; return;
        }
        if (stage == 6 && ++ticks >= 1) {
            mc.options.keyJump.setDown(false);
            check(AntarchyGravityApi.getWorldVelocity(player).y < -.15, "Antarchy jump must launch down away from the ceiling");
            stage = 7; ticks = 0; return;
        }
        if (stage == 7 && ++ticks >= 18 && player.onGround()) {
            check(AntarchyGravityApi.isGravityInverted(player), "Inverted jump must land back on the ceiling");
            for (var perspective : CameraType.values()) {
                mc.options.setCameraType(perspective);
                var camera = warmCamera(mc);
                camera.setup(mc.level, player, !perspective.isFirstPerson(), perspective.isMirrored(), 1);
                check(camera.isDetached() == !perspective.isFirstPerson(), "Antarchy third-person camera must detach");
                if (!perspective.isFirstPerson())
                    check(camera.getPosition().distanceTo(player.getEyePosition()) > 1, "Antarchy third-person distance must be retained");
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            operation = server(mc, p -> {
                AntarchyGravityApi.setForcedGravityDirection(p, AntarchyGravityDirection.DOWN,
                        new AntarchyGravityTransition(10));
                p.setDeltaMovement(Vec3.ZERO);
                p.connection.teleport(2.8, 150.5, 1.4, 0, 0);
            });
            stage = 8; ticks = 0; return;
        }
        if (stage == 8) {
            check(GravityController.direction(player) == Direction.DOWN, "Forced normal Antarchy gravity must also retain ownership");
            if (++ticks < 15) return;
            check(up(warmCamera(mc)).y > .999F, "Antarchy's return transition must complete upright");
            operation = server(mc, p -> AntarchyGravityApi.clearForcedGravity(p));
            stage = 9; ticks = 0; return;
        }
        if (stage == 9) {
            if (GravityController.direction(player) != Direction.WEST || !player.onGround()) return;
            if (++ticks < 15) return;
            check(up(warmCamera(mc)).x > .999F, "Clearing external gravity must restore the still-present crystal field");
            operation = server(mc, p -> p.serverLevel().removeBlock(CRYSTAL, false));
            stage = 10; ticks = 0; return;
        }
        if (stage == 10) {
            if (GravityController.direction(player) != Direction.DOWN) return;
            if (++ticks < 15) return;
            check(up(warmCamera(mc)).y > .999F, "Removing the crystal must restore ordinary gravity");
            LogUtils.getLogger().info("ANTARCHY_GRAVITY_CLIENT_PASS startup=true crystalWall=true invertedEyes=true thirdPerson=true realCeilingWalking=true realInvertedJump=true overlapHandoff=true forcedDown=true externalRecovery=true crystalRecovery=true");
            stage = 11; mc.stop();
        }
    }

    private static void checkAntarchyCamera(Minecraft mc) {
        var probe = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "AntarchyCameraProbe"));
        probe.setPos(4, 152, 2);
        probe.tickCount = 100;
        AntarchyGravityApi.setGravityDirection(probe, AntarchyGravityDirection.UP);
        var camera = warmCamera(mc, probe);
        check(camera.getPosition().distanceTo(probe.getEyePosition()) < .001,
                "OmniSequence must preserve Antarchy's first-person anchor without a crystal field");
        check(up(camera).y < -.999F, "OmniSequence must preserve Antarchy's inverted rotation");
        camera.setup(mc.level, probe, true, true, 1);
        var mirrored = new Quaternionf(camera.rotation());
        AntarchyGravityApi.setGravityDirection(probe, AntarchyGravityDirection.DOWN);
        check(GravityController.change(probe, Direction.WEST), "A crystal must be admitted after instant external recovery");
        camera.setup(mc.level, probe, true, true, 1);
        check(Math.abs(mirrored.dot(camera.rotation())) > .99999F,
                "A mirrored external-to-crystal camera handoff must begin at the last displayed orientation");
        AntarchyGravityApi.applySyncedState(probe, AntarchyGravityDirection.DOWN,
                AntarchyGravityDirection.UP, false, 10, 5);
        // A stale crystal metadata packet must not reclaim a still-running external transition.
        ((GravityPlayerAccess) probe).omnisequence$gravity(Direction.WEST);
        check(GravityController.direction(probe) == Direction.DOWN,
                "An unforced Antarchy return transition must also suspend crystal movement");
        check(!GravityController.change(probe, Direction.WEST),
                "Crystal admission must wait until the external transition finishes");
        camera = warmCamera(mc, probe);
        var expectedEye = probe.getPosition(1).add(AntarchyGravityRotationUtil.getEyeOffset(probe, probe.getEyeHeight(), 1));
        check(camera.getPosition().distanceTo(expectedEye) < .001,
                "An unforced return transition must retain Antarchy's interpolated anchor");
        AntarchyGravityApi.applySyncedState(probe, AntarchyGravityDirection.DOWN,
                AntarchyGravityDirection.UP, false, 10, 0);
        check(GravityController.change(probe, Direction.DOWN), "A completed external transition must release crystal admission");
        LogUtils.getLogger().info("ANTARCHY_CAMERA_PASS invertedEyes=true invertedRotation=true unforcedTransition=true staleCrystalMetadata=true");
    }

    private static Camera warmCamera(Minecraft mc) { return warmCamera(mc, mc.player); }
    private static Vector3f up(Camera camera) { return new Vector3f(0, 1, 0).rotate(camera.rotation()); }
    private static Camera warmCamera(Minecraft mc, net.minecraft.world.entity.Entity entity) {
        var camera = new Camera();
        camera.setup(mc.level, entity, false, false, 1);
        for (int i = 0; i < 30; i++) camera.tick();
        camera.setup(mc.level, entity, false, false, 1);
        return camera;
    }

    private static CompletableFuture<?> server(Minecraft mc, Consumer<ServerPlayer> action) {
        var id = mc.player.getUUID();
        return mc.getSingleplayerServer().submit(() -> action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id)));
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }
}
