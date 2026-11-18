package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.network.WarpScreenPayload;
import com.atir.molecularmanipulator.world.WarpAnimation;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** A short dark transition over the actual world, independent of HUD visibility. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class WarpTransitionOverlay {
    private static int phase;
    private static long startedAt;

    private WarpTransitionOverlay() {}

    public static void begin(WarpScreenPayload payload) {
        phase = payload.phase();
        startedAt = Util.getMillis();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (phase == WarpAnimation.IDLE || mc.level == null || mc.player == null) return;
        float age = Math.max(0, (Util.getMillis() - startedAt) / 50F);
        var graphics = event.getGuiGraphics();
        if (phase == WarpAnimation.PREPARING) {
            int alpha = 14 + Math.round(6 * (.5F + .5F * (float) Math.sin(age * .15F)));
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24 | 0x15221D);
            graphics.drawCenteredString(mc.font, Component.translatable("message.molecularmanipulator.end_warp.preparing"),
                    graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 28, 0xFFE9D5A0);
            return;
        }
        float opacity = WarpAnimation.screenOpacity(phase, age);
        if (phase == WarpAnimation.ARRIVING && opacity == 0) phase = WarpAnimation.IDLE;
        if (opacity <= 0) return;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                Math.round(opacity * 255) << 24 | 0x030408);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        phase = WarpAnimation.IDLE;
    }
}
