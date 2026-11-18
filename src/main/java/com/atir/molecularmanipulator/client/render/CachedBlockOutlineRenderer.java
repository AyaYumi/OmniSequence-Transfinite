package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderHighlightEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class CachedBlockOutlineRenderer {
    private static VoxelShape cachedShape;
    private static Edge[] cachedEdges = new Edge[0];

    private CachedBlockOutlineRenderer() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderHighlightEvent.Block event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var pos = event.getTarget().getBlockPos();
        var state = level.getBlockState(pos);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(MolecularManipulator.MOD_ID)
                || state.isAir() || !level.getWorldBorder().isWithinBounds(pos)) return;

        var camera = event.getCamera();
        var shape = state.getShape(level, pos, CollisionContext.of(camera.getEntity()));
        if (shape != cachedShape) {
            cachedEdges = buildEdges(shape);
            cachedShape = shape;
        }

        var cameraPos = camera.getPosition();
        double offsetX = pos.getX() - cameraPos.x;
        double offsetY = pos.getY() - cameraPos.y;
        double offsetZ = pos.getZ() - cameraPos.z;
        var pose = event.getPoseStack().last();
        var consumer = event.getMultiBufferSource().getBuffer(RenderType.lines());
        for (var edge : cachedEdges) edge.render(pose, consumer, offsetX, offsetY, offsetZ);
        event.setCanceled(true);
    }

    private static Edge[] buildEdges(VoxelShape shape) {
        var edges = new ArrayList<Edge>();
        shape.forAllEdges((startX, startY, startZ, endX, endY, endZ) -> {
            float normalX = (float) (endX - startX);
            float normalY = (float) (endY - startY);
            float normalZ = (float) (endZ - startZ);
            float length = Mth.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            edges.add(new Edge(startX, startY, startZ, endX, endY, endZ,
                    normalX / length, normalY / length, normalZ / length));
        });
        return edges.toArray(Edge[]::new);
    }

    private record Edge(double startX, double startY, double startZ,
            double endX, double endY, double endZ, float normalX, float normalY, float normalZ) {
        private void render(PoseStack.Pose pose, VertexConsumer consumer,
                double offsetX, double offsetY, double offsetZ) {
            consumer.vertex(pose.pose(), (float) (startX + offsetX), (float) (startY + offsetY), (float) (startZ + offsetZ))
                    .color(0F, 0F, 0F, .4F).normal(pose.normal(), normalX, normalY, normalZ).endVertex();
            consumer.vertex(pose.pose(), (float) (endX + offsetX), (float) (endY + offsetY), (float) (endZ + offsetZ))
                    .color(0F, 0F, 0F, .4F).normal(pose.normal(), normalX, normalY, normalZ).endVertex();
        }
    }
}
