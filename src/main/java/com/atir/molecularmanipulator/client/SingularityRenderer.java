package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import com.atir.molecularmanipulator.blockentity.SingularityMotionGeometry;
import com.atir.molecularmanipulator.client.render.SingularityEffectLayers;
import com.atir.molecularmanipulator.client.render.SingularityStructureEffects;
import com.atir.molecularmanipulator.client.render.SingularityEffectState;
import com.atir.molecularmanipulator.client.render.SingularityStellarCore;
import com.atir.molecularmanipulator.client.render.SingularityWhiteHoleRenderer;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;

/** One bounded renderer for the formed structure; no additional ticking beam blocks. */
public final class SingularityRenderer implements BlockEntityRenderer<SingularityBlockEntity> {
    private final java.util.Map<SingularityBlockEntity, SingularityEffectState> effects = new java.util.WeakHashMap<>();
    public SingularityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(SingularityBlockEntity machine, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        int detail = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        boolean suspended = machine.structureVersion() >= SingularityStructure.VERSION;
        if (!machine.formed() || detail <= 0 && !suspended || machine.getLevel() == null) return;
        var center = machine.worldPos(new BlockPos(0, 64, 0)).subtract(machine.getBlockPos());
        double now = machine.getLevel().getGameTime() + (double) partialTick;
        int mode = 0;
        double age = 160;
        var motion = new SingularityMotionGeometry.Pose[SingularityMotionGeometry.GROUP_COUNT];
        for (var body : SingularityMotionWorld.bodies(machine.getLevel())) {
            if (body.configured() && body.controller().equals(machine.getBlockPos())) {
                motion[body.groupId()] = body.pose(partialTick);
                mode = body.motionMode();
                age = body.motionAge(partialTick);
            }
        }
        var camera = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean detailed = detail > 1 && camera.distanceToSqr(Vec3.atCenterOf(machine.getBlockPos().offset(center))) < 224 * 224;
        poses.pushPose(); poses.translate(center.getX() + .5, center.getY() + .5, center.getZ() + .5);
        poses.mulPose(Axis.YP.rotationDegrees((float) -SingularityMotionGeometry.facingAngle(machine.facing())));
        var frame = effects.computeIfAbsent(machine, ignored -> new SingularityEffectState()).sample(now, mode, age);
        // The suspended crystals are part of the design silhouette, even with decorative effects disabled.
        SingularityStructureEffects.renderCrystal(poses, SingularityEffectLayers.crystal(buffers), frame, detailed, motion, suspended);
        if (suspended) {
            SingularityStellarCore.renderStar(poses, SingularityEffectLayers.star(buffers), frame, detailed);
            if (detail > 0) SingularityStellarCore.renderFlux(poses, SingularityEffectLayers.glow(buffers), frame, detailed);
            var relativeEye = camera.subtract(Vec3.atCenterOf(machine.getBlockPos().offset(center)));
            double facing = Math.toRadians(SingularityMotionGeometry.facingAngle(machine.facing()));
            var localEye = new Vec3(relativeEye.x * Math.cos(facing) + relativeEye.z * Math.sin(facing),
                    relativeEye.y, relativeEye.z * Math.cos(facing) - relativeEye.x * Math.sin(facing));
            SingularityWhiteHoleRenderer.enqueue(poses, frame, localEye);
        }
        if (detail > 0) SingularityStructureEffects.render(poses, SingularityEffectLayers.glow(buffers), frame, detailed, motion, suspended);
        poses.popPose();
    }
    public static void renderEffects(PoseStack poses, com.mojang.blaze3d.vertex.VertexConsumer out, float time, boolean detailed) {
        SingularityStructureEffects.render(poses, out, time, detailed, null);
    }
    @Override public int getViewDistance() { return 256; }
    @Override public boolean shouldRenderOffScreen(SingularityBlockEntity machine) { return true; }

}
