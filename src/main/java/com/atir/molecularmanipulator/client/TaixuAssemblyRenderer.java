package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.entity.TaixuAssemblyEntity;
import com.atir.molecularmanipulator.client.render.ctm.MatterConnectedModel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import java.util.*;

/** Shared static meshes; transforms and ordinary atlas/lightmap shaders move complete physical bodies. */
public final class TaixuAssemblyRenderer extends EntityRenderer<TaixuAssemblyEntity> {
    private static final Map<Integer, List<Mesh>> MESHES = new HashMap<>();
    private static int generation = -1;
    private record Mesh(RenderType type, VertexBuffer buffer) {}
    public TaixuAssemblyRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(TaixuAssemblyEntity body) { return TextureAtlas.LOCATION_BLOCKS; }
    @Override public void render(TaixuAssemblyEntity body, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!body.configured()) return;
        int current = MatterConnectedModel.generation();
        if (generation != current) { clear(); generation = current; }
        int meshKey = body.structureVersion() * TaixuMotionGeometry.GROUP_COUNT + body.groupId();
        var meshes = MESHES.computeIfAbsent(meshKey, TaixuAssemblyRenderer::build);
        var transform = body.pose(partialTick);
        pose.pushPose();
        pose.translate(0, transform.lift(), 0);
        pose.mulPose(Axis.YP.rotationDegrees((float) -(TaixuMotionGeometry.facingAngle(body.facing()) + transform.angle())));
        pose.translate(-.5, 0, -.5);
        var view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        for (var mesh : meshes) {
            mesh.type.setupRenderState();
            try { mesh.buffer.bind(); mesh.buffer.drawWithShader(view, RenderSystem.getProjectionMatrix(), RenderSystem.getShader()); }
            finally { VertexBuffer.unbind(); mesh.type.clearRenderState(); }
        }
        pose.popPose();
        super.render(body, yaw, partialTick, pose, buffers, light);
    }
    private static List<Mesh> build(int key) {
        int group = key % TaixuMotionGeometry.GROUP_COUNT, version = key / TaixuMotionGeometry.GROUP_COUNT;
        var mc = Minecraft.getInstance(); var dispatcher = mc.getBlockRenderer();
        var states = new HashMap<BlockPos, BlockState>();
        for (var part : TaixuMotionGeometry.group(group, version).parts()) states.put(part.pos(), TaixuStructure.state(part, Direction.NORTH));
        var result = new ArrayList<Mesh>();
        for (boolean glass : new boolean[]{false, true}) {
            var type = glass ? RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS) : RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
            var builder = Tesselator.getInstance().begin(type.mode(), type.format()); var poses = new PoseStack();
            for (var part : TaixuMotionGeometry.group(group, version).parts()) {
                if ((part.type() == TaixuStructure.Type.GLASS) != glass) continue;
                var pos = part.pos(); var state = states.get(pos);
                long masks = MatterConnectedModel.connectionMasks(p -> states.getOrDefault(p, Blocks.AIR.defaultBlockState()), pos, state);
                poses.pushPose(); poses.translate(part.x(), part.y(), part.z());
                dispatcher.getModelRenderer().renderModel(poses.last(), builder, state, dispatcher.getBlockModel(state),
                        1, 1, 1, LightTexture.pack(0, 15), OverlayTexture.NO_OVERLAY, MatterConnectedModel.modelData(masks), type);
                poses.popPose();
            }
            var data = builder.build();
            if (data != null) {
                var buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try { buffer.bind(); buffer.upload(data); } finally { VertexBuffer.unbind(); }
                result.add(new Mesh(type, buffer));
            }
        }
        return List.copyOf(result);
    }
    static void clear() {
        if (!RenderSystem.isOnRenderThread()) { RenderSystem.recordRenderCall(TaixuAssemblyRenderer::clear); return; }
        MESHES.values().forEach(meshes -> meshes.forEach(mesh -> mesh.buffer.close())); MESHES.clear(); generation = -1;
    }
}
