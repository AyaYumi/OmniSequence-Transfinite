package com.atir.molecularmanipulator.client.render;

import appeng.api.client.AEKeyRenderHandler;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.ae2.NegativeEntropyInfoKey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Draws the 16-frame data-flow strip as the AE key icon.
 */
public final class NegativeEntropyInfoRenderer
        implements AEKeyRenderHandler<NegativeEntropyInfoKey> {
    private static final ResourceLocation TEXTURE =
            MolecularManipulator.id("textures/gui/key/negentropy_information_flow.png");
    private static final int FRAME_COUNT = 16;
    private static final int FRAME_HEIGHT = 16;
    private static final int TEXTURE_HEIGHT = FRAME_COUNT * FRAME_HEIGHT;

    @Override
    public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y,
                          NegativeEntropyInfoKey key) {
        long ticks = minecraft.level != null
                ? minecraft.level.getGameTime()
                : System.currentTimeMillis() / 50L;
        int frame = (int) ((ticks / 2L) % FRAME_COUNT);
        graphics.blit(
                TEXTURE,
                x,
                y,
                0.0F,
                frame * (float) FRAME_HEIGHT,
                16,
                16,
                16,
                TEXTURE_HEIGHT
        );
    }

    @Override
    public void drawOnBlockFace(PoseStack poseStack, MultiBufferSource bufferSource,
                                NegativeEntropyInfoKey key, float scale, int packedLight,
                                Level level) {
        // The key is only rendered in terminal/GUI slots for this implementation.
    }

    @Override
    public Component getDisplayName(NegativeEntropyInfoKey key) {
        return key.getDisplayName();
    }
}
