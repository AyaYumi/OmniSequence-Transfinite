package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.network.DetachedSignalTimePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class DetachedSignalClientTime {
    private static float scale = 1F;

    private DetachedSignalClientTime() {}

    public static void receive(DetachedSignalTimePayload payload) {
        scale = payload.accelerated() ? 2F : .5F;
    }

    public static float scale() {
        return scale;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        scale = 1F;
    }
}
