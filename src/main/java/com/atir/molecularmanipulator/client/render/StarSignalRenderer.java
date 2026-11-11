package com.atir.molecularmanipulator.client.render;

import appeng.api.client.AEKeyRenderHandler;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Draws the transparent star-core icon with signal pulses sampled from a 64-frame strip.
 */
public final class StarSignalRenderer
        implements AEKeyRenderHandler<StarSignalKey> {
    private static final ResourceLocation TEXTURE =
            MolecularManipulator.id("textures/gui/key/star_signal_flow.png");

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y,
                          StarSignalKey key) {
        double ticks = minecraft.level != null
                ? minecraft.level.getGameTime() + minecraft.getTimer().getGameTimeDeltaPartialTick(true)
                : Util.getMillis() / 50.0;
        int frame = StarSignalAnimation.frameAt(ticks);
        graphics.blit(
                TEXTURE,
                x,
                y,
                0.0F,
                frame * (float) StarSignalAnimation.FRAME_SIZE,
                StarSignalAnimation.FRAME_SIZE,
                StarSignalAnimation.FRAME_SIZE,
                StarSignalAnimation.FRAME_SIZE,
                StarSignalAnimation.TEXTURE_HEIGHT
        );
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource bufferSource,
                                StarSignalKey key, float scale, int packedLight,
                                Level level) {
        // The key is only rendered in terminal/GUI slots for this implementation.
    }

    @Override
    public Component getDisplayName(StarSignalKey key) {
        return key.getDisplayName();
    }
}
