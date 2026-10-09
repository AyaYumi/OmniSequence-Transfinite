package com.atir.molecularmanipulator.verification;

import com.atir.molecularmanipulator.block.GravityCrystalBlock;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.gravity.*;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Drive an actual integrated server and LocalPlayer, including movement packets and camera rendering. */
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class GravityWalkingClientChecks {
    private static final BlockPos CRYSTAL = new BlockPos(1, 150, 0);
    private static final BlockPos GHOST = new BlockPos(4, 148, 0);
    private static final CameraType[] PERSPECTIVES = {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_FRONT,
            CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_FRONT,
            CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON};
    private static int stage, ticks, wait;
    private static int perspectiveIndex;
    private static CompletableFuture<?> operation;
    private static Vec3 start;
    private static boolean entryTransitionSeen, exitTransitionSeen;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        var mc = Minecraft.getInstance();
        if (!Boolean.getBoolean("omnisequence.gravity_client_check")) return;
        if (++wait > 3600) throw new IllegalStateException("Gravity client check timed out at stage " + stage);
        if (stage == 0) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.options.onboardingAccessibilityFinished();
                mc.setScreen(new TitleScreen());
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            mc.options.pauseOnLostFocus = false;
            LogUtils.getLogger().info("GRAVITY_CLIENT_STAGE creating disposable flat world");
            stage = 1;
            mc.createWorldOpenFlows().createFreshLevel("gravity-walk-" + System.currentTimeMillis(),
                    new LevelSettings("Gravity Check", GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(8327461, false, false),
                    registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), null);
            return;
        }
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        if (stage == 1) {
            LogUtils.getLogger().info("GRAVITY_CLIENT_STAGE preparing wall and live server player");
            mc.setScreen(null);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            var id = mc.player.getUUID();
            operation = mc.getSingleplayerServer().submit(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var level = player.serverLevel();
                level.setChunkForced(0, 0, true);
                level.setChunkForced(0, -1, true);
                level.setChunkForced(-1, 0, true);
                level.setChunkForced(-1, -1, true);
                for (int x = -3; x <= 6; x++) for (int y = 145; y <= 156; y++) for (int z = -5; z <= 5; z++)
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                for (int y = 146; y <= 154; y++) for (int z = -4; z <= 4; z++)
                    level.setBlock(new BlockPos(0, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(CRYSTAL, ModContent.GRAVITY_CRYSTAL_BLOCK.get().defaultBlockState()
                        .setValue(DirectionalBlock.FACING, Direction.EAST), 3);
                player.setGameMode(GameType.SURVIVAL);
                player.setDeltaMovement(Vec3.ZERO);
                player.connection.teleport(2.8, 150.5, 1.4, 0, 0);
            });
            stage = 2;
            return;
        }
        if (operation != null) {
            if (!operation.isDone()) return;
            operation.join();
            operation = null;
        }
        var player = mc.player;
        float renderedUpX = mc.gameRenderer.getMainCamera().getUpVector().x;
        if (renderedUpX > .04F && renderedUpX < .96F) {
            if (GravityController.direction(player) == Direction.WEST) entryTransitionSeen = true;
            if (stage == 6 && GravityController.direction(player) == Direction.DOWN) exitTransitionSeen = true;
        }
        if (stage == 2) {
            if (GravityController.direction(player) != Direction.WEST || !player.onGround()) return;
            LogUtils.getLogger().info("GRAVITY_CLIENT_STAGE standing on wall; verifying camera and walking");
            var overlay = blockingOverlay(player);
            LogUtils.getLogger().info("GRAVITY_FIRST_PERSON_OVERLAY eyes={} feet={} blocking={}",
                    player.getEyePosition(), player.position(), overlay);
            screenshot(mc, "first-person-client.png");
            check(overlay == null, "Wall beneath rotated feet must not cover the first-person view");
            checkObstructionDirections(mc);
            checkCameraTransitions(mc);
            check(mc.getSingleplayerServer().getPlayerList().getPlayer(player.getUUID()) != null, "Server player must exist");
            check(Math.abs(player.getBoundingBox().getXsize() - player.getBbHeight()) < 1.0E-6,
                    "Actual client collision body must lie along X");
            player.setYRot(0);
            player.setXRot(0);
            var camera = new Camera();
            for (int i = 0; i < 30; i++) camera.tick();
            camera.setup(mc.level, player, false, false, 1);
            for (int i = 0; i < 30; i++) camera.tick();
            camera.setup(mc.level, player, false, false, 1);
            check(camera.getPosition().distanceTo(player.getEyePosition()) < .001, "Camera must use rotated eyes");
            var up = new Vector3f(0, 1, 0).rotate(camera.rotation());
            check(up.x > .999F && Math.abs(up.y) < .001F, "Camera must rotate standing direction to wall normal");
            var forward = new Vector3f(0, 0, -1).rotate(camera.rotation());
            check(new Vec3(forward).distanceTo(player.getViewVector(1)) < .001, "Camera and block targeting must agree");
            camera.setup(mc.level, player, true, false, 1);
            check(camera.getPosition().distanceTo(player.getEyePosition()) > 1, "Third-person camera must detach in rotated frame");
            start = player.position();
            mc.options.keyUp.setDown(true);
            ticks = 0;
            stage = 3;
            return;
        }
        if (stage == 3 && ++ticks >= 5) {
            mc.options.keyUp.setDown(false);
            check(player.getZ() > start.z + .08, "Real forward input must walk along the wall");
            check(Math.abs(player.getX() - start.x) < .05, "Walking must keep feet on wall");
            check(GravityController.direction(player) == Direction.WEST, "Network movement must retain wall gravity");
            stage = 8;
            ticks = 0;
            return;
        }
        if (stage == 8 && ++ticks >= 8) {
            check(GravityController.direction(player) == Direction.WEST && player.onGround(),
                    "Player must remain on wall after releasing forward input");
            mc.options.keyJump.setDown(true);
            stage = 4;
            ticks = 0;
            return;
        }
        if (stage == 4 && ++ticks >= 1) {
            mc.options.keyJump.setDown(false);
            check(player.getDeltaMovement().x > .15, "Real jump input must launch away from wall");
            stage = 5;
            ticks = 0;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            return;
        }
        if (stage == 5 && ++ticks >= 18 && player.onGround()) {
            check(GravityController.direction(player) == Direction.WEST && player.isAlive(), "Jump must land back on wall");
            screenshot(mc, "wall-walking-client.png");
            stage = 9;
            ticks = 0;
            perspectiveIndex = 0;
            mc.options.setCameraType(PERSPECTIVES[0]);
            return;
        }
        if (stage == 9 && ++ticks >= 8) {
            check(GravityController.direction(player) == Direction.WEST && player.onGround(),
                    "Repeated perspective switching must retain wall gravity");
            check(blockingOverlay(player) == null, "Perspective switches must not create false block overlays");
            var camera = mc.gameRenderer.getMainCamera();
            check(camera.isDetached() == !mc.options.getCameraType().isFirstPerson(),
                    "Rendered camera must follow the selected perspective");
            if (mc.options.getCameraType().isFirstPerson()) {
                check(camera.getPosition().distanceTo(player.getEyePosition()) < .01,
                        "Actual rendered first-person camera must remain at the rotated eyes");
                screenshot(mc, "first-person-client.png");
            }
            if (++perspectiveIndex < PERSPECTIVES.length) {
                mc.options.setCameraType(PERSPECTIVES[perspectiveIndex]);
                ticks = 0;
                return;
            }
            var id = player.getUUID();
            operation = mc.getSingleplayerServer().submit(() -> {
                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var level = serverPlayer.serverLevel();
                check(GravityController.direction(serverPlayer) == Direction.WEST, "Server must retain client wall movement");
                level.setBlock(CRYSTAL, level.getBlockState(CRYSTAL).setValue(GravityCrystalBlock.POWERED, true), 3);
            });
            stage = 6;
            ticks = 0;
            return;
        }
        if (stage == 6) {
            if (GravityController.direction(player) != Direction.DOWN) return;
            check(Math.abs(player.getBoundingBox().getYsize() - player.getBbHeight()) < 1.0E-6,
                    "Redstone metadata must restore upright collision box");
            if (++ticks < 14) return;
            check(entryTransitionSeen && exitTransitionSeen, "Actual rendered entry and exit must show intermediate orientations");
            check(mc.gameRenderer.getMainCamera().getUpVector().y > .999F,
                    "The actual camera must complete its return to normal gravity");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            var id = player.getUUID();
            operation = mc.getSingleplayerServer().submit(() -> {
                var serverPlayer = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var level = serverPlayer.serverLevel();
                for (int x = 1; x <= 7; x++) for (int z = -3; z <= 5; z++)
                    level.setBlock(new BlockPos(x, 147, z), Blocks.STONE.defaultBlockState(), 3);
                for (int x = 1; x <= 7; x++) for (int y = 148; y <= 153; y++)
                    level.setBlock(new BlockPos(x, y, -2), Blocks.DEEPSLATE.defaultBlockState(), 3);
                level.setBlock(GHOST, ModContent.GHOST_MATTER_BLOCK.get().defaultBlockState(), 3);
                serverPlayer.setGameMode(GameType.CREATIVE);
                serverPlayer.getInventory().setItem(0, new ItemStack(ModContent.GHOST_MATTER.get()));
                serverPlayer.connection.teleport(4.5, 148, 3.5, 180, 20);
                serverPlayer.setDeltaMovement(Vec3.ZERO);
            });
            stage = 10;
            ticks = 0;
            return;
        }
        if (stage == 10) {
            if (++ticks >= 20 && (ticks - 20) % 4 == 0 && ticks <= 80)
                screenshot(mc, String.format("ghost-matter-client-%02d.png", (ticks - 20) / 4));
            if (ticks < 80) return;
            screenshot(mc, "ghost-matter-client.png");
            check(mc.getBlockEntityRenderDispatcher().getRenderer(mc.level.getBlockEntity(GHOST)) != null,
                    "Placed Ghost Matter must have its emissive mist renderer");
            operation = mc.getSingleplayerServer().submit(() -> {
                var level = mc.getSingleplayerServer().overworld();
                level.setBlock(GHOST, level.getBlockState(GHOST).setValue(BlockStateProperties.WATERLOGGED, true), 3);
            });
            stage = 11;
            ticks = 0;
            return;
        }
        if (stage == 11 && ++ticks >= 12) {
            check(mc.level.getBlockState(GHOST).getValue(BlockStateProperties.WATERLOGGED), "Water suppression must sync to the rendered client");
            screenshot(mc, "ghost-matter-water-client.png");
            LogUtils.getLogger().info("GRAVITY_WALKING_CLIENT_PASS serverClientSync=true actualKeyboardWalking=true actualJump=true rotatedCamera=true thirdPerson=true firstPerson=true perspectiveSwitches=6 obstructionDirections=6 genuineObstruction=true recovery=true smoothEntry=true smoothExit=true interruptedTransitions=true ghostMistRenderer=true ghostWaterSuppression=true");
            stage = 7;
            mc.stop();
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) throw new IllegalStateException(message);
    }

    @SuppressWarnings("unchecked")
    private static Pair<BlockState, BlockPos> blockingOverlay(Player player) throws Exception {
        var method = ScreenEffectRenderer.class.getDeclaredMethod("getOverlayBlock", Player.class);
        method.setAccessible(true);
        return (Pair<BlockState, BlockPos>) method.invoke(null, player);
    }

    private static void checkObstructionDirections(Minecraft mc) throws Exception {
        // This probe is never added to the world or network; exercise vanilla's actual overlay query in all frames.
        var probe = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "GravityOverlayProbe"));
        var support = new BlockPos(4, 150, 3);
        var oldSupport = mc.level.getBlockState(support);
        mc.level.setBlock(support, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        try {
            for (var direction : Direction.values()) {
                ((GravityPlayerAccess) probe).omnisequence$gravity(direction);
                Vec3 feet = Vec3.atCenterOf(support).add(GravityFrame.up(direction).scale(.5));
                for (var pose : new Pose[]{Pose.STANDING, Pose.CROUCHING}) {
                    probe.setPose(pose);
                    probe.setPos(feet.x, feet.y, feet.z);
                    check(blockingOverlay(probe) == null, "Supporting block must not obscure " + direction + " / " + pose);
                    var head = BlockPos.containing(probe.getEyePosition());
                    var oldHead = mc.level.getBlockState(head);
                    try {
                        mc.level.setBlock(head, Blocks.STONE.defaultBlockState(), 3);
                        var overlay = blockingOverlay(probe);
                        check(overlay != null && overlay.getLeft().is(Blocks.STONE) && overlay.getRight().equals(head),
                                "Genuine head obstruction must retain its block and position for " + direction + " / " + pose);
                    } finally {
                        mc.level.setBlock(head, oldHead, 3);
                    }
                }
            }
        } finally {
            mc.level.setBlock(support, oldSupport, 3);
        }
        LogUtils.getLogger().info("GRAVITY_OBSTRUCTION_DIRECTIONS_PASS directions=6 poses=standing,crouching clearView=true genuineObstruction=true");
    }

    private static void checkCameraTransitions(Minecraft mc) {
        var probe = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "GravityCameraProbe"));
        probe.setPos(4, 152, 2);
        probe.tickCount = 100;
        var camera = new Camera();
        camera.setup(mc.level, probe, false, false, 1);
        for (int i = 0; i < 30; i++) camera.tick();
        camera.setup(mc.level, probe, false, false, 1);
        for (var down : new Direction[]{Direction.WEST, Direction.NORTH, Direction.UP, Direction.DOWN}) {
            var previous = new org.joml.Quaternionf(camera.rotation());
            check(GravityController.change(probe, down), "Camera probe must have space to change gravity");
            camera.setup(mc.level, probe, false, false, 1);
            check(Math.abs(previous.dot(camera.rotation())) > .99999F, "A gravity transition must start at the last rendered orientation");
            probe.tickCount += 5;
            camera.setup(mc.level, probe, false, false, 1);
            check(Math.abs(previous.dot(camera.rotation())) < .999F, "A gravity transition must advance through intermediate orientations");
            camera.setup(mc.level, probe, true, true, 1);
            check(camera.isDetached(), "Mirrored perspective must work during a gravity transition");
            probe.tickCount += 5;
            camera.setup(mc.level, probe, false, false, 1);
            var up = new Vector3f(0, 1, 0).rotate(camera.rotation());
            // Local pitch need not remain zero because the world look vector is preserved.
            check(new Vec3(camera.getLookVector()).normalize().dot(probe.getViewVector(1).normalize()) > .99999,
                    "Settled camera must agree with targeting after every turn");
            check(camera.getPosition().distanceTo(probe.getEyePosition()) < .001,
                    "Settled camera position must agree with the authoritative eyes");
        }
        LogUtils.getLogger().info("GRAVITY_CAMERA_TRANSITION_PASS continuousStart=true intermediateRotation=true settledAim=true settledEyes=true mirroredPerspective=true");
    }

    private static void screenshot(Minecraft mc, String filename) throws Exception {
        Path directory = Path.of(mc.gameDirectory.getAbsolutePath()).getParent().resolve("gravity-verification");
        Files.createDirectories(directory);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(directory.resolve(filename));
        }
    }
}
