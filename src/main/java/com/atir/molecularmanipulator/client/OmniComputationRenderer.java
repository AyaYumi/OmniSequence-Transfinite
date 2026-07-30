package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class OmniComputationRenderer implements BlockEntityRenderer<OmniComputationCoreBlockEntity> {
    private static final ResourceLocation FIELD_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final ResourceLocation RING_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    private static final ResourceLocation PORTAL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
    private static final int CYAN = 0x55EEFF;
    private static final int BLUE = 0x409CFF;
    private static final int PURPLE = 0x9D61FF;
    private static final int DEEP_PURPLE = 0x572DCC;
    private static final int GOLD = 0xFFD98A;
    private static final int WHITE = 0xF2FCFF;
    private static final int[][] LOWER_PYLONS = {
            {-11, -11}, {0, -12}, {11, -11}, {12, 0},
            {11, 11}, {0, 12}, {-11, 11}, {-12, 0}
    };

    public OmniComputationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OmniComputationCoreBlockEntity core, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0 || core.getLevel() == null
                || !core.getBlockState().getValue(BlockStateProperties.POWERED)) {
            return;
        }

        Direction facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Vec3 visualCenter = OmniComputationStructure.worldPoint(core.getBlockPos(), facing,
                OmniComputationStructure.EFFECT_X, OmniComputationStructure.EFFECT_Y,
                OmniComputationStructure.EFFECT_Z);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distanceSquared = camera.distanceToSqr(visualCenter);
        boolean detailed = effectLevel > 1 && distanceSquared < 160.0 * 160.0;
        boolean distant = distanceSquared > 96.0 * 96.0;
        int torusSegments = detailed ? 96 : distant ? 32 : 56;
        int torusSides = detailed ? 8 : 6;
        int activity = core.getClientVisualActivity();
        float activityStrength = activity <= 0
                ? 0.0F
                : Math.min(1.0F, (float) (Math.log1p(activity) / Math.log(9.0)));
        float angle = core.sampleClientVisualAngle(partialTick);
        float pulse = 1.0F + (float) Math.sin(angle * 0.075F) * (0.035F + activityStrength * 0.045F);

        poseStack.pushPose();
        poseStack.translate(visualCenter.x - core.getBlockPos().getX(),
                visualCenter.y - core.getBlockPos().getY(),
                visualCenter.z - core.getBlockPos().getZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation(facing)));

        renderQuantumGate(poseStack, buffers, angle, activityStrength, detailed, torusSegments, torusSides);

        var fieldType = RenderType.entityTranslucentEmissive(FIELD_TEXTURE);
        var field = buffers.getBuffer(fieldType);
        renderSingularity(poseStack, field, angle, activityStrength, pulse, detailed);

        var ringType = RenderType.energySwirl(RING_TEXTURE,
                angle * 0.0032F, -angle * 0.0021F);
        var rings = buffers.getBuffer(ringType);
        renderAstralRings(poseStack, rings, angle, activityStrength, detailed, torusSegments, torusSides);

        field = buffers.getBuffer(fieldType);
        if (detailed) {
            renderPylonBeams(poseStack, field, angle, activityStrength);
            renderOrbitalNodes(poseStack, field, angle, activity);
        }
        renderCrownBeam(poseStack, field, angle, activityStrength, detailed);
        rings = buffers.getBuffer(ringType);
        renderCompletionPulse(poseStack, rings, core.getClientCompletionPulse(), torusSegments, torusSides);
        poseStack.popPose();
    }

    private static void renderQuantumGate(PoseStack poseStack, MultiBufferSource buffers, float angle,
            float activityStrength, boolean detailed, int segments, int sides) {
        float gatePulse = 1.0F + (float) Math.sin(angle * 0.11F) * 0.018F;
        int gateAlpha = Math.round(135.0F + activityStrength * 75.0F);
        var gate = buffers.getBuffer(RenderType.energySwirl(RING_TEXTURE,
                -angle * 0.0028F, angle * 0.0017F));

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.scale(gatePulse, gatePulse, gatePulse * 1.16F);
        renderTorus(poseStack, gate, 8.25F, 0.22F + activityStrength * 0.06F,
                PURPLE, gateAlpha, segments, sides);
        if (detailed) {
            renderTorus(poseStack, gate, 7.72F, 0.065F, CYAN,
                    Math.round(95.0F + activityStrength * 90.0F), segments, 5);
            renderTorus(poseStack, gate, 8.78F, 0.045F, BLUE,
                    Math.round(65.0F + activityStrength * 75.0F), segments, 5);
        }
        poseStack.popPose();

        if (!detailed) {
            return;
        }

        var portal = buffers.getBuffer(RenderType.entityTranslucentEmissive(PORTAL_TEXTURE));
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, 0.035F);
        poseStack.scale(1.0F, 1.16F, 1.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle * (0.16F + activityStrength * 0.22F)));
        renderDisc(poseStack, portal, 7.48F, 64, 7, DEEP_PURPLE,
                Math.round(20.0F + activityStrength * 45.0F));
        poseStack.translate(0.0F, 0.0F, 0.025F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-angle * 0.37F));
        renderDisc(poseStack, portal, 6.92F, 56, 6, BLUE,
                Math.round(10.0F + activityStrength * 27.0F));
        poseStack.popPose();
    }

    private static void renderSingularity(PoseStack poseStack, VertexConsumer consumer, float angle,
            float activityStrength, float pulse, boolean detailed) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * 0.82F));
        renderSphere(poseStack, consumer, 3.35F * pulse, detailed ? 28 : 18,
                detailed ? 14 : 9, PURPLE, Math.round(34.0F + activityStrength * 24.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(angle * -0.31F));
        renderSphere(poseStack, consumer, 1.48F * pulse, detailed ? 24 : 16,
                detailed ? 12 : 8, CYAN, Math.round(185.0F + activityStrength * 55.0F));
        renderSphere(poseStack, consumer, 0.72F, detailed ? 20 : 12,
                detailed ? 10 : 6, WHITE, Math.round(210.0F + activityStrength * 45.0F));
        poseStack.popPose();
    }

    private static void renderAstralRings(PoseStack poseStack, VertexConsumer consumer, float angle,
            float activityStrength, boolean detailed, int segments, int sides) {
        float speed = 1.0F + activityStrength * 1.75F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * 0.52F * speed));
        poseStack.mulPose(Axis.ZP.rotationDegrees(12.0F));
        renderTorus(poseStack, consumer, 4.45F, 0.105F, CYAN, 220, segments, sides);
        poseStack.popPose();

        if (!detailed) {
            return;
        }

        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(64.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(-angle * 0.39F * speed));
        renderTorus(poseStack, consumer, 5.72F, 0.09F, PURPLE, 205, segments, sides);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(72.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle * 0.31F * speed));
        renderTorus(poseStack, consumer, 6.88F, 0.072F, BLUE, 180, segments, sides);
        poseStack.popPose();
    }

    private static void renderPylonBeams(PoseStack poseStack, VertexConsumer consumer, float angle,
            float activityStrength) {
        for (int index = 0; index < LOWER_PYLONS.length; index++) {
            int[] pylon = LOWER_PYLONS[index];
            float wave = 0.5F + 0.5F * (float) Math.sin(angle * 0.19F - index * Math.PI / 4.0);
            float intensity = activityStrength > 0.0F
                    ? 0.32F + activityStrength * 0.48F + wave * 0.2F
                    : Math.max(0.0F, (wave - 0.72F) / 0.28F) * 0.42F;
            if (intensity <= 0.025F) {
                continue;
            }
            Vec3 start = new Vec3(pylon[0], 15.0 - OmniComputationStructure.EFFECT_Y, pylon[1]);
            renderBeamBetween(poseStack, consumer, start, Vec3.ZERO,
                    0.035F + activityStrength * 0.035F, CYAN,
                    Math.round(35.0F + intensity * 150.0F));
        }
    }

    private static void renderOrbitalNodes(PoseStack poseStack, VertexConsumer consumer, float angle,
            int activity) {
        int nodes = Math.min(16, 6 + activity * 2);
        float nodeSpeed = 0.75F + Math.min(2.0F, activity * 0.18F);
        for (int index = 0; index < nodes; index++) {
            double phase = Math.PI * 2.0 * index / nodes + Math.toRadians(angle * nodeSpeed);
            float radius = index % 2 == 0 ? 5.72F : 6.88F;
            float x = (float) Math.cos(phase) * radius;
            float z = (float) Math.sin(phase) * radius;
            float y = (float) Math.sin(phase * 2.0) * (index % 2 == 0 ? 1.25F : 1.8F);
            poseStack.pushPose();
            poseStack.translate(x, y, z);
            renderSphere(poseStack, consumer, index % 3 == 0 ? 0.2F : 0.13F,
                    10, 5, index % 2 == 0 ? GOLD : WHITE, 235);
            poseStack.popPose();
        }
    }

    private static void renderCrownBeam(PoseStack poseStack, VertexConsumer consumer, float angle,
            float activityStrength, boolean detailed) {
        float shimmer = 0.72F + 0.28F * (float) Math.sin(angle * 0.13F);
        float radius = detailed ? 0.075F + activityStrength * 0.055F : 0.055F;
        int alpha = Math.round((22.0F + activityStrength * 78.0F) * shimmer);
        renderBeamColumn(poseStack, consumer, radius, 20.6F, BLUE, alpha);
    }

    private static void renderCompletionPulse(PoseStack poseStack, VertexConsumer consumer, float pulse,
            int segments, int sides) {
        if (pulse <= 0.001F) {
            return;
        }
        float progress = 1.0F - pulse;
        poseStack.pushPose();
        poseStack.translate(0.0F, progress * 2.8F, 0.0F);
        renderTorus(poseStack, consumer, 3.2F + progress * 12.8F,
                0.08F + pulse * 0.15F, GOLD, Math.round(pulse * 210.0F), segments, sides);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        renderTorus(poseStack, consumer, 2.4F + progress * 9.6F,
                0.055F + pulse * 0.1F, WHITE, Math.round(pulse * 145.0F), segments, sides);
        poseStack.popPose();
    }

    private static void renderBeamBetween(PoseStack poseStack, VertexConsumer consumer, Vec3 start, Vec3 end,
            float radius, int color, int alpha) {
        Vec3 delta = end.subtract(start);
        float length = (float) delta.length();
        if (length <= 0.0001F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(start.x, start.y, start.z);
        poseStack.mulPose(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F,
                (float) (delta.x / length), (float) (delta.y / length), (float) (delta.z / length)));
        renderBeamColumn(poseStack, consumer, radius, length, color, alpha);
        poseStack.popPose();
    }

    private static void renderBeamColumn(PoseStack poseStack, VertexConsumer consumer, float radius,
            float length, int color, int alpha) {
        var pose = poseStack.last();
        emitQuad(pose, consumer,
                -radius, 0, -radius, radius, 0, -radius,
                radius, length, -radius, -radius, length, -radius,
                0, 0, -1, color, alpha);
        emitQuad(pose, consumer,
                radius, 0, radius, -radius, 0, radius,
                -radius, length, radius, radius, length, radius,
                0, 0, 1, color, alpha);
        emitQuad(pose, consumer,
                -radius, 0, radius, -radius, 0, -radius,
                -radius, length, -radius, -radius, length, radius,
                -1, 0, 0, color, alpha);
        emitQuad(pose, consumer,
                radius, 0, -radius, radius, 0, radius,
                radius, length, radius, radius, length, -radius,
                1, 0, 0, color, alpha);
    }

    private static void renderDisc(PoseStack poseStack, VertexConsumer consumer, float radius,
            int segments, int rings, int color, int alpha) {
        var pose = poseStack.last();
        for (int ring = 0; ring < rings; ring++) {
            float inner = radius * ring / rings;
            float outer = radius * (ring + 1) / rings;
            for (int segment = 0; segment < segments; segment++) {
                double angle0 = Math.PI * 2.0 * segment / segments;
                double angle1 = Math.PI * 2.0 * (segment + 1) / segments;
                float innerX0 = (float) Math.cos(angle0) * inner;
                float innerY0 = (float) Math.sin(angle0) * inner;
                float outerX0 = (float) Math.cos(angle0) * outer;
                float outerY0 = (float) Math.sin(angle0) * outer;
                float outerX1 = (float) Math.cos(angle1) * outer;
                float outerY1 = (float) Math.sin(angle1) * outer;
                float innerX1 = (float) Math.cos(angle1) * inner;
                float innerY1 = (float) Math.sin(angle1) * inner;
                emitDiscQuad(pose, consumer, radius,
                        innerX0, innerY0, outerX0, outerY0, outerX1, outerY1, innerX1, innerY1,
                        0.0F, 0.0F, 1.0F, color, alpha, false);
                emitDiscQuad(pose, consumer, radius,
                        innerX0, innerY0, innerX1, innerY1, outerX1, outerY1, outerX0, outerY0,
                        0.0F, 0.0F, -1.0F, color, alpha, true);
            }
        }
    }

    private static void emitDiscQuad(PoseStack.Pose pose, VertexConsumer consumer, float radius,
            float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
            float normalX, float normalY, float normalZ, int color, int alpha, boolean reverseUv) {
        emitVertex(pose, consumer, x0, y0, 0, normalX, normalY, normalZ,
                discUv(x0, radius, reverseUv), discUv(y0, radius, false), color, alpha);
        emitVertex(pose, consumer, x1, y1, 0, normalX, normalY, normalZ,
                discUv(x1, radius, reverseUv), discUv(y1, radius, false), color, alpha);
        emitVertex(pose, consumer, x2, y2, 0, normalX, normalY, normalZ,
                discUv(x2, radius, reverseUv), discUv(y2, radius, false), color, alpha);
        emitVertex(pose, consumer, x3, y3, 0, normalX, normalY, normalZ,
                discUv(x3, radius, reverseUv), discUv(y3, radius, false), color, alpha);
    }

    private static float discUv(float value, float radius, boolean reverse) {
        float uv = value / radius * 0.5F + 0.5F;
        return reverse ? 1.0F - uv : uv;
    }

    private static void renderSphere(PoseStack poseStack, VertexConsumer consumer, float radius,
            int segments, int stacks, int color, int alpha) {
        var pose = poseStack.last();
        for (int stack = 0; stack < stacks; stack++) {
            double latitude0 = -Math.PI / 2.0 + Math.PI * stack / stacks;
            double latitude1 = -Math.PI / 2.0 + Math.PI * (stack + 1) / stacks;
            float v0 = (float) stack / stacks;
            float v1 = (float) (stack + 1) / stacks;
            for (int segment = 0; segment < segments; segment++) {
                double longitude0 = Math.PI * 2.0 * segment / segments;
                double longitude1 = Math.PI * 2.0 * (segment + 1) / segments;
                float u0 = (float) segment / segments;
                float u1 = (float) (segment + 1) / segments;
                emitSphereVertex(pose, consumer, radius, latitude0, longitude0,
                        u0, v0, color, alpha);
                emitSphereVertex(pose, consumer, radius, latitude1, longitude0,
                        u0, v1, color, alpha);
                emitSphereVertex(pose, consumer, radius, latitude1, longitude1,
                        u1, v1, color, alpha);
                emitSphereVertex(pose, consumer, radius, latitude0, longitude1,
                        u1, v0, color, alpha);
            }
        }
    }

    private static void emitSphereVertex(PoseStack.Pose pose, VertexConsumer consumer, float radius,
            double latitude, double longitude, float u, float v, int color, int alpha) {
        float horizontal = (float) Math.cos(latitude);
        float normalX = horizontal * (float) Math.cos(longitude);
        float normalY = (float) Math.sin(latitude);
        float normalZ = horizontal * (float) Math.sin(longitude);
        emitVertex(pose, consumer, normalX * radius, normalY * radius, normalZ * radius,
                normalX, normalY, normalZ, u, v, color, alpha);
    }

    private static void renderTorus(PoseStack poseStack, VertexConsumer consumer, float radius,
            float tubeRadius, int color, int alpha, int segments, int sides) {
        var pose = poseStack.last();
        for (int segment = 0; segment < segments; segment++) {
            double major0 = Math.PI * 2.0 * segment / segments;
            double major1 = Math.PI * 2.0 * (segment + 1) / segments;
            float u0 = (float) segment / segments * 4.0F;
            float u1 = (float) (segment + 1) / segments * 4.0F;
            for (int side = 0; side < sides; side++) {
                double minor0 = Math.PI * 2.0 * side / sides;
                double minor1 = Math.PI * 2.0 * (side + 1) / sides;
                float v0 = (float) side / sides;
                float v1 = (float) (side + 1) / sides;
                emitTorusVertex(pose, consumer, radius, tubeRadius, major0, minor0,
                        u0, v0, color, alpha);
                emitTorusVertex(pose, consumer, radius, tubeRadius, major1, minor0,
                        u1, v0, color, alpha);
                emitTorusVertex(pose, consumer, radius, tubeRadius, major1, minor1,
                        u1, v1, color, alpha);
                emitTorusVertex(pose, consumer, radius, tubeRadius, major0, minor1,
                        u0, v1, color, alpha);
            }
        }
    }

    private static void emitTorusVertex(PoseStack.Pose pose, VertexConsumer consumer,
            float radius, float tubeRadius, double major, double minor,
            float u, float v, int color, int alpha) {
        float majorCos = (float) Math.cos(major);
        float majorSin = (float) Math.sin(major);
        float minorCos = (float) Math.cos(minor);
        float minorSin = (float) Math.sin(minor);
        float ringRadius = radius + tubeRadius * minorCos;
        float x = ringRadius * majorCos;
        float y = tubeRadius * minorSin;
        float z = ringRadius * majorSin;
        float normalX = minorCos * majorCos;
        float normalY = minorSin;
        float normalZ = minorCos * majorSin;
        emitVertex(pose, consumer, x, y, z, normalX, normalY, normalZ,
                u, v, color, alpha);
    }

    private static void emitQuad(PoseStack.Pose pose, VertexConsumer consumer,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3,
            float normalX, float normalY, float normalZ, int color, int alpha) {
        emitVertex(pose, consumer, x0, y0, z0, normalX, normalY, normalZ, 0, 0, color, alpha);
        emitVertex(pose, consumer, x1, y1, z1, normalX, normalY, normalZ, 1, 0, color, alpha);
        emitVertex(pose, consumer, x2, y2, z2, normalX, normalY, normalZ, 1, 1, color, alpha);
        emitVertex(pose, consumer, x3, y3, z3, normalX, normalY, normalZ, 0, 1, color, alpha);
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer,
            float x, float y, float z, float normalX, float normalY, float normalZ,
            float u, float v, int color, int alpha) {
        consumer.addVertex(pose, x, y, z)
                .setColor(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }

    private static float facingRotation(Direction facing) {
        return switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public boolean shouldRenderOffScreen(OmniComputationCoreBlockEntity core) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    @Override
    public boolean shouldRender(OmniComputationCoreBlockEntity core, Vec3 cameraPos) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(OmniComputationCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(42.0, 52.0, 42.0);
    }
}
