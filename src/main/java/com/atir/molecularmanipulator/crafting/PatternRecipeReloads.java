package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import java.util.concurrent.atomic.AtomicLong;

/** Invalidates passive pattern decodes after server reloads and client recipe synchronization. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class PatternRecipeReloads {
    private static final AtomicLong GENERATION = new AtomicLong();

    private PatternRecipeReloads() { }

    public static long generation() {
        return GENERATION.get();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // Joining players do not change server recipes. A full sync follows a
        // completed reload, including tag changes used by crafting ingredients.
        if (event.getPlayer() == null) {
            GENERATION.incrementAndGet();
        }
    }

    // Keep client event types out of the dedicated-server subscriber.
    @EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private Client() { }

        @SubscribeEvent
        public static void onRecipesUpdated(net.minecraftforge.client.event.RecipesUpdatedEvent event) {
            GENERATION.incrementAndGet();
        }
    }
}
