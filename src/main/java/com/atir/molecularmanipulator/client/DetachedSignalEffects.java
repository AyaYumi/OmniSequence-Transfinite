package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;

/** No sun, moon, clouds or terrain horizon; only the upward code streams break the darkness. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DetachedSignalEffects extends DimensionSpecialEffects {
    private DetachedSignalEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(DetachedSignalRealm.EFFECTS, new DetachedSignalEffects());
    }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(.006D, .009D, .018D);
    }

    @Override public boolean isFoggyAt(int x, int y) { return false; }
    @Override public float[] getSunriseColor(float timeOfDay, float partialTick) { return null; }
}
