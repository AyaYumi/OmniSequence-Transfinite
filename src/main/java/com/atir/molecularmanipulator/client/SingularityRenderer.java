package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import com.atir.molecularmanipulator.blockentity.SingularityMotionGeometry;
import com.atir.molecularmanipulator.client.render.ForgeRenderSupport;
import com.atir.molecularmanipulator.client.render.EarlyFrameGeometry;
import com.atir.molecularmanipulator.client.render.ParallelEffectGeometry;
import com.atir.molecularmanipulator.client.render.SingularityEffectLayers;
import com.atir.molecularmanipulator.client.render.SingularityShaderVertices;
import com.atir.molecularmanipulator.client.render.SingularityStructureEffects;
import com.atir.molecularmanipulator.client.render.SingularityEffectState;
import com.atir.molecularmanipulator.client.render.SingularityStellarCore;
import com.atir.molecularmanipulator.client.render.SingularityWhiteHoleRenderer;
import com.atir.molecularmanipulator.blockentity.MachineEffectLevels;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;

/** One bounded renderer for the formed structure; no additional ticking beam blocks. */
public final class SingularityRenderer implements BlockEntityRenderer<SingularityBlockEntity>,
        EarlyFrameGeometry.Source<SingularityBlockEntity, SingularityRenderer.Snapshot> {
    private static final int[] PASS_ENDS = {1, 2, 3, 9};
    // Start the center and tower groups first without changing any output/pass ordering.
    private static final int[] LAUNCH_ORDER = {3, 7, 8, 5, 4, 6, 0, 1, 2};

    private final java.util.Map<SingularityBlockEntity, SingularityEffectState> effects = new java.util.WeakHashMap<>();
    private final ParallelEffectGeometry geometry = new ParallelEffectGeometry();
    public SingularityRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(SingularityBlockEntity machine, float partialTick, PoseStack poses,
                                 MultiBufferSource buffers, int light, int overlay) {
        int detail = MachineEffectLevels.forMachine(machine);
        // Recheck at draw time too: an early-prepared snapshot must not bypass a new off override.
        if (machine.isSingleBlock() || !machine.formed() || detail <= 0 || machine.getLevel() == null) return;
        var snapshot = EarlyFrameGeometry.take(this, machine, partialTick, poses);
        if (snapshot == null) snapshot = capture(machine, partialTick, poses, geometry);
        if (snapshot == null) return;
        var frame = snapshot;
        try (frame) {
            var localPoses = snapshot.poses();
            // Physical moving block meshes are rendered independently by SingularityAssemblyRenderer.
            var crystal = SingularityEffectLayers.crystal(buffers);
            try (var batch = SingularityShaderVertices.batch(crystal)) {
                if (snapshot.prepared() != null) snapshot.prepared().submit(0, crystal);
                else renderPass(localPoses, crystal, 0, snapshot.frame(), snapshot.detailed(),
                        snapshot.motion(), snapshot.suspended());
            }
            if (snapshot.suspended()) {
                var star = SingularityEffectLayers.star(buffers);
                try (var batch = SingularityShaderVertices.batch(star)) {
                    if (snapshot.prepared() != null) snapshot.prepared().submit(1, star);
                    else renderPass(localPoses, star, 1, snapshot.frame(), snapshot.detailed(),
                            snapshot.motion(), snapshot.suspended());
                }
                if (snapshot.effectLevel() > 0) {
                    var glow = SingularityEffectLayers.glow(buffers);
                    try (var batch = SingularityShaderVertices.batch(glow)) {
                        if (snapshot.prepared() != null) snapshot.prepared().submit(2, glow);
                        else renderPass(localPoses, glow, 2, snapshot.frame(), snapshot.detailed(),
                                snapshot.motion(), snapshot.suspended());
                    }
                }
                SingularityWhiteHoleRenderer.enqueue(localPoses, snapshot.frame(), snapshot.localEye());
            }
            if (snapshot.effectLevel() > 0) {
                var glow = SingularityEffectLayers.glow(buffers);
                try (var batch = SingularityShaderVertices.batch(glow)) {
                    if (snapshot.prepared() != null) snapshot.prepared().submit(3, glow);
                    else renderPass(localPoses, glow, 3, snapshot.frame(), snapshot.detailed(),
                            snapshot.motion(), snapshot.suspended());
                }
            }
        }
    }

    @Override
    public Snapshot capture(SingularityBlockEntity machine, float partialTick, PoseStack base,
            ParallelEffectGeometry geometry) {
        if (machine.isSingleBlock()) return null;
        int detail = MachineEffectLevels.forMachine(machine);
        boolean suspended = machine.structureVersion() >= SingularityStructure.VERSION;
        // Do not schedule decorative geometry or the scene lens for an off machine.
        if (!machine.formed() || detail <= 0 || machine.getLevel() == null) return null;
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
        var poses = localPose(base, center, machine.facing());
        var frame = effects.computeIfAbsent(machine, ignored -> new SingularityEffectState()).sample(now, mode, age);
        var prepared = preparePasses(geometry, poses, frame, detailed, motion, suspended);
        var relativeEye = camera.subtract(Vec3.atCenterOf(machine.getBlockPos().offset(center)));
        double facing = Math.toRadians(SingularityMotionGeometry.facingAngle(machine.facing()));
        var localEye = new Vec3(relativeEye.x * Math.cos(facing) + relativeEye.z * Math.sin(facing),
                relativeEye.y, relativeEye.z * Math.cos(facing) - relativeEye.x * Math.sin(facing));
        return new Snapshot(ForgeRenderSupport.copyPose(poses.last()), frame, detailed, motion, suspended, detail, localEye, prepared);
    }

    private static PoseStack localPose(PoseStack base, BlockPos center,
            net.minecraft.core.Direction facing) {
        var poses = new PoseStack();
        poses.last().pose().set(base.last().pose());
        poses.last().normal().set(base.last().normal());
        poses.translate(center.getX() + .5, center.getY() + .5, center.getZ() + .5);
        poses.mulPose(Axis.YP.rotationDegrees((float) -SingularityMotionGeometry.facingAngle(facing)));
        return poses;
    }

    /** Immutable sampled easing/motion plus the optional prepared color geometry for one capture. */
    public record Snapshot(PoseStack.Pose pose, SingularityStructureEffects.Frame frame,
            boolean detailed, SingularityMotionGeometry.Pose[] motion, boolean suspended,
            int effectLevel, Vec3 localEye, ParallelEffectGeometry.Frame prepared)
            implements EarlyFrameGeometry.Snapshot {
        public Snapshot {
            motion = motion == null ? null : motion.clone();
        }

        @Override
        public SingularityMotionGeometry.Pose[] motion() {
            return motion == null ? null : motion.clone();
        }

        private PoseStack poses() {
            var poses = new PoseStack();
            poses.last().pose().set(pose.pose());
            poses.last().normal().set(pose.normal());
            return poses;
        }

        @Override
        public void close() {
            if (prepared != null) prepared.close();
        }
    }

    /** Capture after sampling the mutable easing state and world motion on the render thread. */
    static ParallelEffectGeometry.Frame preparePasses(ParallelEffectGeometry geometry, PoseStack poses,
            SingularityStructureEffects.Frame frame, boolean detailed,
            SingularityMotionGeometry.Pose[] motion, boolean suspended) {
        if (!suspended || !detailed || !ParallelEffectGeometry.isAvailable()) return null;
        var input = new GeometryFrame(ForgeRenderSupport.copyPose(poses.last()), frame, detailed,
                motion == null ? null : motion.clone(), suspended);
        return geometry.prepareGroups(PASS_ENDS, LAUNCH_ORDER,
                out -> input.render(out, 0), out -> input.render(out, 1), out -> input.render(out, 2),
                out -> input.renderCeremony(out, 0, 1), out -> input.renderCeremony(out, 1, 2),
                out -> input.renderCeremony(out, 2, 3), out -> input.renderCeremony(out, 3, 4),
                out -> input.renderCeremony(out, 4, 8), out -> input.renderCeremony(out, 8, 12));
    }

    private record GeometryFrame(PoseStack.Pose pose, SingularityStructureEffects.Frame frame,
            boolean detailed, SingularityMotionGeometry.Pose[] motion, boolean suspended) {
        private PoseStack poses() {
            // Each effect creates mutable per-call state and gets its own stack. The captured
            // matrix, motion records and static geometry tables are only read by workers.
            var poses = new PoseStack();
            poses.last().pose().set(pose.pose());
            poses.last().normal().set(pose.normal());
            return poses;
        }

        private void render(VertexConsumer out, int pass) {
            renderPass(poses(), out, pass, frame, detailed, motion, suspended);
        }

        private void renderCeremony(VertexConsumer out, int from, int to) {
            SingularityStructureEffects.renderRange(poses(), out, frame, detailed, motion, suspended, from, to);
        }
    }

    // Synchronous color-only dispatch, also used to compare prepared geometry in tests.
    static void renderPass(PoseStack poses, VertexConsumer out, int pass,
            SingularityStructureEffects.Frame frame, boolean detailed,
            SingularityMotionGeometry.Pose[] motion, boolean suspended) {
        switch (pass) {
            case 0 -> SingularityStructureEffects.renderCrystal(poses, out, frame, detailed, motion, suspended);
            case 1 -> SingularityStellarCore.renderStar(poses, out, frame, detailed);
            case 2 -> SingularityStellarCore.renderFlux(poses, out, frame, detailed);
            case 3 -> SingularityStructureEffects.render(poses, out, frame, detailed, motion, suspended);
            default -> throw new IllegalArgumentException("Unknown singularity geometry pass " + pass);
        }
    }
    public static void renderEffects(PoseStack poses, com.mojang.blaze3d.vertex.VertexConsumer out, float time, boolean detailed) {
        SingularityStructureEffects.render(poses, out, time, detailed, null);
    }
    @Override public int getViewDistance() { return 256; }
    @Override public boolean shouldRender(SingularityBlockEntity machine, Vec3 camera) {
        var bounds = machine.getRenderBoundingBox();
        double x = Math.max(bounds.minX, Math.min(camera.x, bounds.maxX));
        double y = Math.max(bounds.minY, Math.min(camera.y, bounds.maxY));
        double z = Math.max(bounds.minZ, Math.min(camera.z, bounds.maxZ));
        return camera.distanceToSqr(new Vec3(x, y, z)) < 256.0D * 256.0D;
    }
    @Override public boolean shouldRenderOffScreen(SingularityBlockEntity machine) { return true; }

}
