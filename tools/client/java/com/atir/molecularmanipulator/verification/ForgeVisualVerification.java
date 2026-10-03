package com.atir.molecularmanipulator.verification;

import appeng.api.networking.GridHelper;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import com.atir.molecularmanipulator.entity.SingularityAssemblyEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.SingularityContent;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Render-only pack regression; runs in the disposable world owned by the client runner. */
public final class ForgeVisualVerification {
    private static final BlockPos HUB = new BlockPos(0, 120, 160);
    private static final BlockPos POWER = HUB.offset(60, 0, 0);
    private static int step, wait;
    private static volatile boolean serverReady;
    private static volatile Throwable failure;

    private ForgeVisualVerification() {}

    public static void bookmark(Minecraft mc, ResponsiveContainerScreen<?> screen) throws Exception {
        Object panel = field(screen.getClass(), "researchPanel").get(screen);
        Object selected = field(panel.getClass(), "selected").get(panel);
        var definition = com.atir.molecularmanipulator.research.MatterResearchApi.definitions(mc.level).stream()
                .filter(recipe -> recipe.id().equals(selected)).findFirst().orElseThrow();
        Button button = (Button) field(panel.getClass(), "bookmark").get(panel);
        var bounds = screen.responsiveBounds();
        double x = screen.responsiveScreenX(button.getX() + button.getWidth() / 2);
        double y = screen.responsiveScreenY(button.getY() + button.getHeight() / 2);
        if (!button.visible || !button.active || !screen.mouseClicked(x, y, 0))
            throw new IllegalStateException("Research bookmark click was not handled");
        screen.mouseReleased(x, y, 0);
        var plugin = Class.forName("com.atir.molecularmanipulator.integration.jei.ResearchJeiBookmarks");
        Object runtime = field(plugin, "runtime").get(null);
        Object manager = mezz.jei.api.runtime.IJeiRuntime.class.getMethod("getBookmarkManager").invoke(runtime);
        var managerApi = Class.forName("mezz.jei.api.runtime.IBookmarkManager");
        var contains = managerApi.getMethod("contains", mezz.jei.api.ingredients.ITypedIngredient.class);
        int checked = 0;
        for (var cost : definition.value().costsFor(1)) {
            var examples = cost.ingredient().getItems();
            if (examples.length == 0) continue;
            var typed = ((mezz.jei.api.runtime.IJeiRuntime) runtime).getIngredientManager().createTypedIngredient(
                    mezz.jei.api.constants.VanillaTypes.ITEM_STACK, examples[0].copyWithCount(1)).orElseThrow();
            if (!Boolean.TRUE.equals(contains.invoke(manager, typed))) throw new IllegalStateException("JEI missed research material");
            checked++;
        }
        if (checked == 0) throw new IllegalStateException("Research bookmark fixture had no materials");
        if (Boolean.getBoolean("omni.menusOnlyVerification")) {
            var overlay = ((mezz.jei.api.runtime.IJeiRuntime) runtime).getBookmarkOverlay();
            Object state = field(overlay.getClass(), "toggleState").get(overlay);
            if (!Boolean.TRUE.equals(Class.forName("mezz.jei.common.config.IClientToggleState")
                    .getMethod("isBookmarkOverlayEnabled").invoke(state)))
                throw new IllegalStateException("Research bookmarks were saved but the overlay stayed disabled");
        }
        System.out.println("FORGE_VISUAL_BOOKMARK_PASS actualScreenClick=true materials=" + checked + " bounds=" + bounds);
    }

    public static boolean tick(Minecraft mc) throws Exception {
        if (failure != null) throw new IllegalStateException("Visual server fixture failed", failure);
        if (wait > 0) { wait--; return false; }
        if (step == 0) {
            mc.player.closeContainer();
            mc.options.hideGui = true;
            mc.options.renderDistance().set(24);
            mc.options.entityDistanceScaling().set(1.0);
            mc.options.bobView().set(false);
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var level = mc.getSingleplayerServer().overworld();
                    for (int x = -4; x <= 4; x++) for (int z = -1; z <= 15; z++) {
                        level.setChunkForced(x, z, true);
                        level.getChunk(x, z);
                    }
                    level.setDayTime(6000);
                    level.setBlockAndUpdate(new BlockPos(-2, 120, 0), ModContent.BLACK_HOLE_BLOCK.get().defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(2, 120, 0), ModContent.WHITE_HOLE_BLOCK.get().defaultBlockState());
                    for (var part : SingularityStructure.parts()) level.setBlock(
                            SingularityStructure.worldPos(HUB, Direction.NORTH, part),
                            SingularityStructure.state(part, Direction.NORTH), 2);
                    level.setBlockAndUpdate(HUB, SingularityContent.CONTROLLER.get().defaultBlockState());
                    level.setBlockAndUpdate(POWER, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                    for (var air : SingularityStructure.requiredAir()) level.setBlock(
                            SingularityStructure.worldPos(HUB, Direction.NORTH, air), Blocks.AIR.defaultBlockState(), 2);
                    serverReady = true;
                } catch (Throwable problem) { failure = problem; }
            });
            step++; wait = 160; return false;
        }
        if (!serverReady) return false;
        switch (step) {
            case 1 -> { camera(mc, new Vec3(.5, 121, -7), new Vec3(.5, 120.5, .5)); wait = 80; }
            case 2 -> { capture(mc, "holes-near"); camera(mc, new Vec3(.5, 126, -70), new Vec3(.5, 120.5, .5)); wait = 80; }
            case 3 -> {
                capture(mc, "holes-far");
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var level = mc.getSingleplayerServer().overworld();
                        var machine = (SingularityBlockEntity) level.getBlockEntity(HUB);
                        var power = (appeng.blockentity.networking.CreativeEnergyCellBlockEntity) level.getBlockEntity(POWER);
                        GridHelper.createConnection(machine.getMainNode().getNode(), power.getMainNode().getNode());
                        machine.scheduleInspection(); machine.serverTick();
                        if (!machine.formed()) throw new IllegalStateException("Visual Hub did not form");
                    } catch (Throwable problem) { failure = problem; }
                });
                camera(mc, new Vec3(75, 156, 70), new Vec3(.5, 131.5, 167.5)); wait = 100;
            }
            case 4 -> {
                capture(mc, "hub-idle");
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var level = mc.getSingleplayerServer().overworld();
                        var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                        player.teleportTo(HUB.getX() + .5, HUB.getY() + 1, HUB.getZ() + .5);
                        var machine = (SingularityBlockEntity) level.getBlockEntity(HUB);
                        machine.toggleCollection(player);
                        if (!machine.collectionActive() || !machine.motion().hasBodies())
                            throw new IllegalStateException("Collection did not start: " + machine.motion().message() + " network=" + machine.networkOnline());
                    } catch (Throwable problem) { failure = problem; }
                });
                wait = 120;
            }
            case 5 -> { camera(mc, new Vec3(75, 156, 70), new Vec3(.5, 131.5, 167.5)); wait = 100; }
            case 6 -> {
                validateMeshes(mc);
                capture(mc, "hub-running");
                camera(mc, new Vec3(130, 162, 0), new Vec3(.5, 131.5, 167.5)); wait = 100;
            }
            case 7 -> {
                validateMeshes(mc);
                capture(mc, "hub-running-far");
                camera(mc, new Vec3(-70, 116, 100), new Vec3(.5, 131.5, 167.5)); wait = 100;
            }
            case 8 -> { validateMeshes(mc); capture(mc, "hub-running-side"); System.out.println("FORGE_VISUAL_ALL_PASS"); return true; }
        }
        step++; return false;
    }

    private static void validateMeshes(Minecraft mc) throws Exception {
        List<SingularityAssemblyEntity> bodies = mc.level.getEntitiesOfClass(SingularityAssemblyEntity.class,
                new AABB(HUB).inflate(180));
        if (bodies.size() != 11) throw new IllegalStateException("Client did not track all eleven bodies: " + bodies.size());
        var renderer = mc.getEntityRenderDispatcher().getRenderer(bodies.get(0));
        Map<?, ?> meshes = (Map<?, ?>) field(renderer.getClass(), "MESHES").get(null);
        if (meshes.size() != 11 || meshes.values().stream().anyMatch(value -> ((List<?>) value).isEmpty()))
            throw new IllegalStateException("Assembly block models produced empty meshes: " + meshes.size());
        var culling = Class.forName("dev.tr7zw.entityculling.EntityCullingModBase");
        var instance = culling.getField("instance").get(null);
        if (!Boolean.TRUE.equals(culling.getMethod("isDynamicWhitelisted", net.minecraft.world.entity.Entity.class).invoke(instance, bodies.get(0))))
            throw new IllegalStateException("Assembly missing Entity Culling compatibility");
        System.out.println("FORGE_VISUAL_MOTION_PASS tracked=11 meshes=11 nonempty=true cullingWhitelist=true");
    }

    private static void camera(Minecraft mc, Vec3 position, Vec3 target) {
        var direction = target.subtract(position);
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z)));
        mc.player.setPos(position.x, position.y, position.z);
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            player.teleportTo(position.x, position.y, position.z);
            player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO);
            player.setYRot(yaw); player.setXRot(pitch);
        });
    }

    private static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> {});
        System.out.println("FORGE_VISUAL_CAPTURE=" + name);
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
}
