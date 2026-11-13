package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.entity.HighEnergyLabEffectsEntity;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HighEnergyLabRenderer extends EntityRenderer<HighEnergyLabEffectsEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft",
            "textures/block/amethyst_block.png");

    public HighEnergyLabRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HighEnergyLabContent.EFFECTS.get(), HighEnergyLabRenderer::new);
        event.registerBlockEntityRenderer(HighEnergyLabContent.CRADLE_BE.get(), HighEnergyLabCradleRenderer::new);
    }

    @Override
    public void render(HighEnergyLabEffectsEntity entity, float yaw, float partialTick,
            PoseStack poses, MultiBufferSource buffers, int packedLight) {
        if (!entity.powered()) return;
        poses.pushPose();
        // StructureTemplate rotates entity yaw together with the vanilla blocks.
        poses.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
        poses.translate(0,-4,-50);
        float seconds = (entity.level().getGameTime() % 240000 + partialTick) / 20F;
        boolean detailed = entityRenderDispatcher.distanceToSqr(entity) < 160D * 160D;
        for (boolean white : new boolean[] {false, true}) {
            poses.pushPose();
            poses.translate(white ? 13 : -13, 12, 0);
            var center = poses.last().pose().transformPosition(0, 0, 0, new Vector3f());
            float radius = white ? 4.3F : 5.2F;
            CosmicSingularityPostRenderer.enqueue(poses, white, seconds * 20, center.lengthSquared(),
                    radius, radius * (1.25F / .30F));
            poses.popPose();
        }
        HighEnergyLabEffects.render(poses, buffers.getBuffer(OmniRenderLayers.solidEmissiveColor()),
                true, seconds, detailed, false, entity.platform());
        // The scene lens needs filament depth to preserve strands passing in front of either core.
        HighEnergyLabEffects.render(poses, buffers.getBuffer(OmniRenderLayers.additiveDepthColor()),
                true, seconds, detailed, true, entity.platform());
        poses.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(HighEnergyLabEffectsEntity entity) {
        return TEXTURE;
    }
}
