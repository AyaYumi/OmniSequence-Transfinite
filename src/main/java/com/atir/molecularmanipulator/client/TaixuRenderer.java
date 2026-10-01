package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.TaixuBlockEntity;
import com.atir.molecularmanipulator.blockentity.TaixuStructure;
import com.atir.molecularmanipulator.blockentity.TaixuMotionGeometry;
import com.atir.molecularmanipulator.client.render.TaixuEffectLayers;
import com.atir.molecularmanipulator.client.render.TaixuStructureEffects;
import com.atir.molecularmanipulator.client.render.TaixuEffectState;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.world.TaixuMotionWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;

/** One bounded renderer for the formed structure; no additional ticking beam blocks. */
public final class TaixuRenderer implements BlockEntityRenderer<TaixuBlockEntity> {
    private final java.util.Map<TaixuBlockEntity, TaixuEffectState> effects = new java.util.WeakHashMap<>();
    public TaixuRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(TaixuBlockEntity machine, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        int detail = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        boolean suspended = machine.structureVersion() >= TaixuStructure.VERSION;
        if (!machine.formed() || detail <= 0 && !suspended || machine.getLevel() == null) return;
        var center = machine.worldPos(new BlockPos(0, 64, 0)).subtract(machine.getBlockPos());
        double now = machine.getLevel().getGameTime() + (double) partialTick;
        int mode = 0;
        double age = 160;
        var motion = new TaixuMotionGeometry.Pose[TaixuMotionGeometry.GROUP_COUNT];
        for (var body : TaixuMotionWorld.bodies(machine.getLevel())) {
            if (body.configured() && body.controller().equals(machine.getBlockPos())) {
                motion[body.groupId()] = body.pose(partialTick);
                mode = body.motionMode();
                age = body.motionAge(partialTick);
            }
        }
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean detailed = detail > 1 && camera.distanceToSqr(Vec3.atCenterOf(machine.getBlockPos().offset(center))) < 224 * 224;
        poses.pushPose(); poses.translate(center.getX() + .5, center.getY() + .5, center.getZ() + .5);
        poses.mulPose(Axis.YP.rotationDegrees((float) -TaixuMotionGeometry.facingAngle(machine.facing())));
        var frame = effects.computeIfAbsent(machine, ignored -> new TaixuEffectState()).sample(now, mode, age);
        // The suspended crystals are part of the design silhouette, even with decorative effects disabled.
        TaixuStructureEffects.renderCrystal(poses, buffers.getBuffer(TaixuEffectLayers.crystal()), frame, detailed, motion, suspended);
        if (detail > 0) TaixuStructureEffects.render(poses, buffers.getBuffer(TaixuEffectLayers.glow()), frame, detailed, motion, suspended);
        poses.popPose();
    }
    public static void renderEffects(PoseStack poses, com.mojang.blaze3d.vertex.VertexConsumer out, float time, boolean detailed) {
        TaixuStructureEffects.render(poses, out, time, detailed, null);
    }
    @Override public int getViewDistance() { return 256; }
    @Override public boolean shouldRenderOffScreen(TaixuBlockEntity machine) { return true; }
    @Override public AABB getRenderBoundingBox(TaixuBlockEntity machine) {
        var center = machine.worldPos(new BlockPos(0, 64, 0));
        return new AABB(center.getX() - 54, center.getY() - 47, center.getZ() - 54,
                center.getX() + 55, center.getY() + 71, center.getZ() + 55);
    }
}
