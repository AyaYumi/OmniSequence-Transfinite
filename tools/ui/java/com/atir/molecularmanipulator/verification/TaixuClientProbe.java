package com.atir.molecularmanipulator.verification;

import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.client.TaixuScreen;
import com.atir.molecularmanipulator.integration.jei.TaixuStructureJeiCategory;
import com.atir.molecularmanipulator.registry.TaixuContent;
import mezz.jei.api.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.List;

@JeiPlugin
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
public final class TaixuClientProbe implements IModPlugin {
    private static IJeiRuntime jei;
    private static int ticks, stage, frames;
    private static boolean initialized;
    private static boolean menuRequested;
    private static int ridingSince;
    private static net.minecraft.world.phys.Vec3 ridingStart;
    private static String capture;
    private static int sampledFrames;
    private static int shaderPhase;
    private static long previousFrame, measuredNanos;
    private static int measuredFrames;
    private static final boolean FLICKER_PROBE = Boolean.getBoolean("taixu.probe.flicker");
    private static final boolean TIANYI_PROBE = Boolean.getBoolean("taixu.probe.tianyi");
    private static final boolean EMBEDDED_PROBE = Boolean.getBoolean("taixu.probe.embedded");
    private static final boolean SUSPENDED_PROBE = Boolean.getBoolean("taixu.probe.suspended");
    private static final BlockPos EMBEDDED_CONTROLLER = new BlockPos(0, 146, 27);
    private static int openingFrames;
    private static BlockPos tianyiController;
    private static final BlockPos CONTROLLER = new BlockPos(0, 120, 0);
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.parse("molecularmanipulator:taixu_client_probe"); }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) { jei = runtime; }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        var mc = Minecraft.getInstance();
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        mc.options.pauseOnLostFocus = false;
        if (++ticks > 2400) throw new IllegalStateException("Taixu client probe timed out: stage=" + stage + " screen=" + mc.screen);
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (SUSPENDED_PROBE) { suspendedTick(mc); return; }
        if (EMBEDDED_PROBE) { embeddedTick(mc); return; }
        if (TIANYI_PROBE) { tianyiTick(mc); return; }
        if (FLICKER_PROBE) {
            if (!initialized) {
                initialized = true;
                mc.setScreen(null); mc.options.hideGui = true;
                mc.options.renderDistance().set(16); mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                mc.options.fov().set(70);
                mc.options.broadcastOptions();
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                    server.overworld().setDayTime(18000);
                    player.connection.teleport(20, 154, -25, 18.6F, 9.0F);
                }); frames = 0;
            }
            // Exercise the real packet path after the entity tick, before rendering.
            for (var body : com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(mc.level)) {
                body.lerpTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), body.getXRot(), 3);
            }
            if (sampledFrames == 16 && shaderPhase == 0 || sampledFrames == 32 && shaderPhase == 1) {
                var apiType = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                var api = apiType.getMethod("getInstance").invoke(null);
                var config = apiType.getMethod("getConfig").invoke(api);
                var configType = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
                boolean enabled = shaderPhase == 1;
                configType.getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
                System.out.println("TAIXU_SHADER_TOGGLE enabled=" + enabled);
                shaderPhase++; frames = 0; previousFrame = measuredNanos = 0; measuredFrames = 0;
            }
            if (sampledFrames >= 48 && stage == 0) {
                stage++;
                System.out.println("TAIXU_FLICKER_SEQUENCE_PASS frames=" + sampledFrames + " shaderReloads=2"); mc.stop();
            }
            return;
        }
        if (!initialized) {
            initialized = true; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.renderDistance().set(16); mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            mc.options.fov().set(50);
            mc.options.broadcastOptions();
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var level = server.overworld();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                level.setDayTime(6000);
                player.teleportTo(CONTROLLER.getX() + .5, CONTROLLER.getY() + 1, CONTROLLER.getZ() - 2);
                level.setBlock(CONTROLLER, TaixuContent.CONTROLLER.get().defaultBlockState(), 3);
            });
        }
        if (!menuRequested && mc.level.getBlockEntity(CONTROLLER) instanceof TaixuBlockEntity) {
            menuRequested = true;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                ((TaixuBlockEntity) server.overworld().getBlockEntity(CONTROLLER)).openMenu(server.getPlayerList().getPlayer(mc.player.getUUID()));
            });
        }
        if (capture != null || frames < 30) return;
        if (stage == 0 && mc.screen instanceof TaixuScreen) { capture = "taixu-ui-overview.png"; stage++; }
        else if (stage == 1 && mc.screen instanceof TaixuScreen screen) {
            var field = TaixuScreen.class.getDeclaredField("materialPage"); field.setAccessible(true); field.setBoolean(screen, true);
            frames = 0; stage++;
        } else if (stage == 2) { capture = "taixu-ui-materials.png"; stage++; }
        else if (stage == 3) { mc.options.guiScale().set(4); mc.resizeDisplay(); frames = 0; stage++; }
        else if (stage == 4) { capture = "taixu-ui-scale4.png"; stage++; }
        else if (stage == 5 && jei != null) {
            mc.options.guiScale().set(2); mc.resizeDisplay();
            jei.getRecipesGui().showTypes(List.of(TaixuStructureJeiCategory.TYPE)); frames = 0; stage++;
        } else if (stage == 6) { capture = "taixu-jei.png"; stage++; }
        else if (stage == 7) {
            mc.setScreen(null);
            mc.options.hideGui = true;
            com.atir.molecularmanipulator.client.TaixuGhostPreview.toggle((TaixuBlockEntity) mc.level.getBlockEntity(CONTROLLER));
            mc.getSingleplayerServer().execute(() -> {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                player.connection.teleport(100, 174, -105, 36.5F, 8.0F);
            });
            frames = 0; stage++;
        } else if (stage == 8 && frames > 180) { capture = "taixu-world-projection.png"; stage++; }
        else if (stage == 9) {
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var machine = (TaixuBlockEntity) server.overworld().getBlockEntity(CONTROLLER);
                player.teleportTo(.5, 121, -2); machine.startBuild(player);
                player.connection.teleport(100, 174, -105, 36.5F, 8.0F);
            });
            frames = 0; stage++;
        } else if (stage == 10 && mc.level.getBlockEntity(CONTROLLER) instanceof TaixuBlockEntity machine && machine.formed() && frames > 360) {
            capture = "taixu-world-formed.png"; stage++;
        } else if (stage == 11) {
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.teleportTo(.5, 121, -2);
                ((TaixuBlockEntity) server.overworld().getBlockEntity(CONTROLLER)).motion().toggle(player);
                player.connection.teleport(100, 174, -105, 36.5F, 8.0F);
            }); frames = 0; stage++;
        } else if (stage == 12 && frames > 180) {
            int visibleBodies = com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(mc.level).size();
            if (visibleBodies != 11) throw new AssertionError("Full motion view must track eleven bodies, got " + visibleBodies);
            System.out.println("TAIXU_FULL_VIEW_PASS trackedBodies=" + visibleBodies);
            var allVisible = new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(), new org.joml.Matrix4f()) {
                @Override public boolean isVisible(net.minecraft.world.phys.AABB box) { return true; }
            };
            var camera = mc.gameRenderer.getMainCamera().getPosition();
            long drawable = com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(mc.level).stream()
                    .filter(body -> mc.getEntityRenderDispatcher().shouldRender(body, allVisible, camera.x, camera.y, camera.z)).count();
            if (drawable != 11) throw new AssertionError("Render mods culled moving bodies: drawable=" + drawable);
            System.out.println("TAIXU_DISTANCE_CULLING_PASS drawableBodies=" + drawable + " camera=" + camera);
            capture = "taixu-world-moving.png"; stage++;
        }
        else if (stage == 13) {
            mc.options.hideGui = false;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                var body = com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(server.overworld()).stream().filter(b -> b.groupId() == 0).findFirst().orElseThrow();
                var feet = body.toWorld(new net.minecraft.world.phys.Vec3(.5, 37, 28.5), body.pose(0));
                player.setGameMode(GameType.SURVIVAL); player.getAbilities().flying = false; player.onUpdateAbilities();
                player.connection.teleport(feet.x, feet.y, feet.z, 180, 35);
            }); ridingSince = ticks; frames = 0; stage++;
        } else if (stage == 14 && ticks > ridingSince + 10) { ridingStart = mc.player.position(); ridingSince = ticks; stage++; }
        else if (stage == 15 && ticks > ridingSince + 180) {
            double distance = mc.player.position().distanceTo(ridingStart);
            if (distance < 3 || Math.abs(mc.player.getY() - ridingStart.y) > .4)
                throw new AssertionError("Live client ring carriage failed: distance=" + distance + " start=" + ridingStart + " end=" + mc.player.position());
            if (!com.atir.molecularmanipulator.world.TaixuMotionWorld.supported(mc.player)) throw new AssertionError("Live rider lost platform support");
            System.out.println("TAIXU_LIVE_RIDER_PASS distance=" + distance + " height=" + mc.player.getY());
            capture = "taixu-ring-rider.png"; stage++;
        } else if (stage == 16) {
            mc.options.hideGui = true;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                server.overworld().setDayTime(18000);
                player.connection.teleport(100, 174, -105, 36.5F, 8.0F);
            }); frames = 0; stage++;
        } else if (stage == 17 && frames > 180) { capture = "taixu-world-night.png"; stage++; }
        else if (stage == 18) {
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                server.overworld().setDayTime(6000);
                server.getPlayerList().getPlayer(mc.player.getUUID()).connection.teleport(100, 229, -90, 39, 27);
            }); frames = 0; stage++;
        } else if (stage == 19 && frames > 180) { capture = "taixu-world-overhead.png"; stage++; }
        else if (stage == 20) { System.out.println("TAIXU_CLIENT_UI_PASS realMenu=true realJei=true guiScales=2,4 projection=true formedRenderer=true physicalMotion=true liveRider=true nightEffects=true"); mc.stop(); stage++; }
    }

    private static void embeddedTick(Minecraft mc) {
        if (!initialized) {
            initialized = true; mc.options.guiScale().set(2); mc.resizeDisplay();
            mc.options.renderDistance().set(16); mc.options.fov().set(50);
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF); mc.options.broadcastOptions();
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var level = server.overworld();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                level.setDayTime(18000); player.connection.teleport(.5, 121, -2, 0, 0);
                var machine = (TaixuBlockEntity) level.getBlockEntity(CONTROLLER);
                if (machine == null || machine.structureVersion() != 2) throw new IllegalStateException("Expected existing legacy fixture");
                machine.requestInspection(player); machine.openMenu(player);
            }); frames = 0;
        }
        if (capture != null) return;
        if (stage == 0 && frames > 120 && mc.screen instanceof TaixuScreen) {
            capture = "embedded-legacy-menu.png"; stage = 1;
        } else if (stage == 1 && mc.screen instanceof TaixuScreen screen) {
            screen.getMenu().request("embed"); stage = 2; frames = 0;
        } else if (stage == 2 && mc.level.getBlockEntity(EMBEDDED_CONTROLLER) instanceof TaixuBlockEntity machine
                && machine.structureVersion() == 3 && mc.level.getBlockState(CONTROLLER).isAir()) {
            mc.setScreen(null); mc.options.hideGui = true;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var embedded = (TaixuBlockEntity) server.overworld().getBlockEntity(EMBEDDED_CONTROLLER);
                if (!embedded.formed() || embedded.motion().hasBodies()) throw new IllegalStateException("Migration failed to restore the central blueprint");
                var newParts = new java.util.HashSet<BlockPos>(); TaixuStructure.parts().forEach(p -> newParts.add(p.pos()));
                for (var part : TaixuStructure.parts(2)) if (!newParts.contains(part.pos())
                        && !server.overworld().getBlockState(TaixuStructure.worldPos(CONTROLLER, Direction.NORTH, part, 2)).isAir())
                    throw new IllegalStateException("External platform remains");
                System.out.println("TAIXU_EMBEDDED_WORLD_PASS formed=true oldPlatformRemoved=true parts=" + embedded.inspection().correct());
            });
            tianyiCamera(mc, .5, 146.8, 21, 0, 5, 18000); stage = 3;
        } else if (stage == 3 && frames > 150) { capture = "embedded-central-idle.png"; stage = 4; }
        else if (stage == 4) {
            mc.options.hideGui = false;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.connection.teleport(.5, 146, 26, 0, 0);
                ((TaixuBlockEntity) server.overworld().getBlockEntity(EMBEDDED_CONTROLLER)).openMenu(player);
            }); stage = 5; frames = 0;
        } else if (stage == 5 && frames > 120 && mc.screen instanceof TaixuScreen) { capture = "embedded-central-menu.png"; stage = 6; }
        else if (stage == 6 && mc.screen instanceof TaixuScreen screen) {
            screen.getMenu().request("motion"); stage = 7; frames = 0;
        } else if (stage == 7 && com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(mc.level).size() == 11 && frames > 60) {
            mc.setScreen(null); mc.options.hideGui = true;
            tianyiCamera(mc, .5, 146.8, 21, 0, 5, 18000); stage = 8;
        } else if (stage == 8 && frames > 600) { capture = "embedded-central-running.png"; stage = 9; }
        else if (stage == 9) { tianyiCamera(mc, 100, 174, -105, 36.5F, 8, 18000); stage = 10; }
        else if (stage == 10 && frames > 150) { capture = "embedded-night-wide.png"; stage = 11; }
        else if (stage == 11) { tianyiCamera(mc, 100, 174, -105, 36.5F, 8, 6000); stage = 12; }
        else if (stage == 12 && frames > 150) { capture = "embedded-day-wide.png"; stage = 13; }
        else if (stage == 13) {
            System.out.println("TAIXU_EMBEDDED_CLIENT_PASS packetMigration=true oldMenu=true centralMenu=true centralController=true movingBodies=11 day=true night=true");
            mc.stop(); stage = 14;
        }
    }

    private static void suspendedTick(Minecraft mc) throws Exception {
        if (!initialized) {
            initialized=true;mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.renderDistance().set(16);
            mc.options.fov().set(50);mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);mc.options.broadcastOptions();
            tianyiController=EMBEDDED_CONTROLLER;
            mc.getSingleplayerServer().execute(()->{
                var server=mc.getSingleplayerServer();var p=server.getPlayerList().getPlayer(mc.player.getUUID());
                p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
                p.connection.teleport(.5,147,25,0,0);server.overworld().setDayTime(6000);
                var m=(TaixuBlockEntity)server.overworld().getBlockEntity(EMBEDDED_CONTROLLER);
                if(m==null || m.structureVersion()!=3)throw new IllegalStateException("Expected saved V3 embedded fixture");
                m.requestInspection(p);m.openMenu(p);
            });frames=0;
        }
        if(capture!=null)return;
        if(stage==0 && frames>120 && mc.screen instanceof TaixuScreen){capture="suspended-upgrade-menu.png";stage=1;}
        else if(stage==1 && mc.screen instanceof TaixuScreen s){s.getMenu().request("upgrade");stage=2;frames=0;}
        else if(stage==2 && mc.level.getBlockEntity(EMBEDDED_CONTROLLER) instanceof TaixuBlockEntity m && m.structureVersion()==4 && m.formed()){
            System.out.println("TAIXU_SUSPENDED_CLIENT_UPGRADE_PASS layout=4 autoBuild=true");capture="suspended-formed-menu.png";stage=3;
        }else if(stage==3 && mc.screen instanceof TaixuScreen s){s.getMenu().request("motion");stage=4;frames=0;}
        else if(stage==4 && frames>160 && com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(mc.level).stream().filter(b->b.controller().equals(EMBEDDED_CONTROLLER)).count()==11){
            mc.setScreen(null);mc.options.hideGui=true;tianyiCamera(mc,100,184,-105,36.5F,11,6000);stage=5;
        }else if(stage==5 && frames>240){capture="suspended-day-wide.png";stage=6;}
        else if(stage==6){tianyiCamera(mc,100,184,-105,36.5F,11,18000);stage=7;}
        else if(stage==7 && frames>150){capture="suspended-night-wide.png";stage=8;}
        else if(stage==8){tianyiCamera(mc,31,168,-24,28,7,6000);stage=9;}
        else if(stage==9 && frames>150){capture="suspended-central-crystal.png";stage=10;}
        else if(stage==10){tianyiCamera(mc,59,163,5,124,2,6000);stage=11;}
        else if(stage==11 && frames>150){capture="suspended-outer-chambers.png";stage=12;}
        else if(stage==12){tianyiToggle(mc);stage=13;}
        else if(stage==13 && frames>100){capture="suspended-paused.png";stage=14;}
        else if(stage==14){tianyiToggle(mc);stage=15;}
        else if(stage==15 && frames>100){capture="suspended-resumed.png";stage=16;}
        else if(stage==16){tianyiShaders(false);tianyiCamera(mc,100,184,-105,36.5F,11,6000);stage=17;}
        else if(stage==17 && frames>180){capture="suspended-no-shader.png";stage=18;}
        else if(stage==18){tianyiShaders(true);frames=0;stage=19;}
        else if(stage==19 && frames>180){capture="suspended-shader-reloaded.png";stage=20;}
        else if(stage==20){
            var bodies=com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(mc.level).stream().filter(b->b.controller().equals(EMBEDDED_CONTROLLER)).toList();
            if(bodies.size()!=11 || bodies.stream().anyMatch(b->b.structureVersion()!=4))throw new AssertionError("Suspended body geometry/version mismatch");
            for(var b:bodies){
                b.lerpTo(b.getX(),b.getY(),b.getZ(),0,0,3);
                var expected=TaixuMotionGeometry.transform(TaixuMotionGeometry.group(b.groupId(),b.structureVersion()).bounds(),p->b.toWorld(p,b.pose(0))).inflate(.05);
                if(!expected.equals(b.getBoundingBox()))throw new AssertionError("Position update lost current physical bounds: group="+b.groupId());
            }
            System.out.println("TAIXU_SUSPENDED_CLIENT_PASS packetUpgrade=true autoBuild=true movingBodies=11 layout=4 day=true night=true pauseResume=true shaderReload=true bounds=true");mc.stop();stage=21;
        }
    }

    private static void tianyiTick(Minecraft mc) throws Exception {
        var bodies = com.atir.molecularmanipulator.world.TaixuMotionWorld.bodies(mc.level);
        if (!initialized) {
            if (bodies.size() != 11) return;
            initialized = true;
            tianyiController = bodies.iterator().next().controller();
            mc.setScreen(null); mc.options.hideGui = true;
            mc.options.renderDistance().set(16); mc.options.fov().set(50);
            mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
            mc.options.broadcastOptions();
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                server.overworld().setDayTime(18000);
                player.connection.teleport(100, 174, -105, 36.5F, 8);
            });
            stage = 0; frames = 0;
        }
        if (capture != null || bodies.isEmpty()) return;
        var body = bodies.iterator().next();
        if (stage == 0 && frames > 180) {
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.teleportTo(tianyiController.getX() + .5, tianyiController.getY() + 1, tianyiController.getZ() - 2);
                var machine = (TaixuBlockEntity) server.overworld().getBlockEntity(tianyiController);
                var saved = machine.motion().save(); saved.putLong("phase", 0); saved.putInt("mode", 2);
                machine.motion().load(saved); machine.motion().serverTick(); machine.motion().toggle(player);
                player.connection.teleport(100, 174, -105, 36.5F, 8);
            });
            stage = 1; frames = 0;
        } else if (stage == 1 && body.motionMode() == 1 && body.motionAge(0) >= openingFrames * 5 && body.motionAge(0) < 220) {
            capture = String.format("tianyi-opening-%03d.png", openingFrames++);
            System.out.println("TIANYI_OPENING_FRAME age=" + body.motionAge(0));
            if (openingFrames == 33) { stage = 2; frames = 0; }
        } else if (stage == 2 && frames > 120) { capture = "tianyi-night-wide.png"; stage = 3; }
        else if (stage == 3) { tianyiCamera(mc, 20, 154, -25, 18.6F, 9, 18000); stage = 4; }
        else if (stage == 4 && frames > 120) { capture = "tianyi-night-near.png"; stage = 5; }
        else if (stage == 5) { tianyiCamera(mc, 18, 149, 5, 31.8F, 6.7F, 18000); stage = 6; }
        else if (stage == 6 && frames > 100) { capture = "tianyi-crystal.png"; stage = 7; }
        else if (stage == 7) { tianyiCamera(mc, 100, 174, -105, 36.5F, 8, 6000); stage = 8; }
        else if (stage == 8 && frames > 100) { capture = "tianyi-day-wide.png"; stage = 9; }
        else if (stage == 9) { tianyiToggle(mc); stage = 10; }
        else if (stage == 10 && frames > 120 && body.motionMode() == 2) {
            capture = "tianyi-paused.png";
            System.out.println("TIANYI_PAUSE_PASS age=" + body.motionAge(0)); stage = 11;
        } else if (stage == 11) { tianyiToggle(mc); stage = 12; }
        else if (stage == 12 && frames > 120 && body.motionMode() == 1) {
            capture = "tianyi-resumed.png";
            System.out.println("TIANYI_RESUME_PASS age=" + body.motionAge(0)); stage = 13;
        } else if (stage == 13) {
            tianyiShaders(false); tianyiCamera(mc, 20, 154, -25, 18.6F, 9, 18000); stage = 14;
        } else if (stage == 14 && frames > 120) { capture = "tianyi-no-shader.png"; stage = 15; }
        else if (stage == 15) { tianyiShaders(true); frames = 0; stage = 16; }
        else if (stage == 16 && frames > 120) { capture = "tianyi-shader-reloaded.png"; stage = 17; }
        else if (stage == 17) {
            System.out.println("TIANYI_CLIENT_PASS openingFrames=" + openingFrames + " trackedBodies=" + bodies.size()
                    + " pause=true resume=true shaderReloads=2 day=true night=true");
            mc.stop(); stage = 18;
        }
    }

    private static void tianyiCamera(Minecraft mc, double x, double y, double z, float yaw, float pitch, long day) {
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer(); server.overworld().setDayTime(day);
            server.getPlayerList().getPlayer(mc.player.getUUID()).connection.teleport(x, y, z, yaw, pitch);
        }); frames = 0;
    }

    private static void tianyiToggle(Minecraft mc) {
        mc.getSingleplayerServer().execute(() -> {
            var server = mc.getSingleplayerServer(); var player = server.getPlayerList().getPlayer(mc.player.getUUID());
            player.teleportTo(tianyiController.getX() + .5, tianyiController.getY() + 1, tianyiController.getZ() - 2);
            ((TaixuBlockEntity) server.overworld().getBlockEntity(tianyiController)).motion().toggle(player);
            player.connection.teleport(100, 174, -105, 36.5F, 8);
        }); frames = 0;
    }

    private static void tianyiShaders(boolean enabled) throws Exception {
        var apiType = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        var api = apiType.getMethod("getInstance").invoke(null);
        var config = apiType.getMethod("getConfig").invoke(api);
        Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig")
                .getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, enabled);
        System.out.println("TIANYI_SHADER_TOGGLE enabled=" + enabled);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) throws Exception {
        frames++;
        if (FLICKER_PROBE && initialized) {
            long now = System.nanoTime();
            if (frames > 60 && frames <= 120 && previousFrame != 0) { measuredNanos += now - previousFrame; measuredFrames++; }
            previousFrame = now;
            if (frames == 120) System.out.println("TAIXU_FRAME_TIME shaderPhase=" + shaderPhase + " ms=" + measuredNanos / 1_000_000D / measuredFrames);
        }
        if (FLICKER_PROBE && initialized && frames > 120 && frames % 2 == 0 && sampledFrames < (shaderPhase + 1) * 16 && sampledFrames < 48) {
            capture = String.format("taixu-near-%03d.png", sampledFrames++);
        }
        if (capture == null) return;
        var output = Path.of(System.getProperty("taixu.probe.output")); Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(output.resolve(capture));
        }
        System.out.println("TAIXU_CAPTURE " + capture); capture = null;
        if (!FLICKER_PROBE) frames = 0;
    }
}
