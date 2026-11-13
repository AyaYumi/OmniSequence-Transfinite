package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.network.WarpScreenPayload;
import com.atir.molecularmanipulator.world.WarpAnimation;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;

/** A short dark transition over the actual world, independent of HUD visibility. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class WarpTransitionOverlay {
    private static int phase;
    private static long startedAt;

    private WarpTransitionOverlay() {}

    public static void begin(WarpScreenPayload payload) {
        phase = payload.phase();
        startedAt = payload.startedAt();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (phase == WarpAnimation.IDLE || mc.level == null || mc.player == null) return;
        float age = Math.max(0, mc.level.getGameTime() - startedAt
                + event.getPartialTick());
        float opacity = WarpAnimation.screenOpacity(phase, age);
        if (phase == WarpAnimation.ARRIVING && opacity == 0) phase = WarpAnimation.IDLE;
        if (opacity <= 0) return;
        var graphics = event.getGuiGraphics();
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                Math.round(opacity * 255) << 24 | 0x030408);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        phase = WarpAnimation.IDLE;
    }
}
