package com.atir.molecularmanipulator.client.render.ctm;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.ModelEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MatterConnectedModels {
    private static volatile MatterConnectedModel.LookupContext pendingContext;

    private MatterConnectedModels() {
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        pendingContext = null;
        var top = new ResourceLocation(MolecularManipulator.MOD_ID, "block/matter_fabrication_casing_top");
        // The Minecraft lookup is deferred until chunk/preview rendering; baking reads only its event registry.
        var context = new MatterConnectedModel.LookupContext(
                state -> Minecraft.getInstance().getBlockRenderer().getBlockModel(state),
                sprite -> Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(sprite));
        Map<BakedModel, MatterConnectedModel> wrappers = new IdentityHashMap<>();
        event.getModels().replaceAll((location, model) -> {
            if (!(location instanceof ModelResourceLocation modelLocation) || !location.getNamespace().equals(MolecularManipulator.MOD_ID)
                    || !location.getPath().startsWith("matter_fabrication_")
                    || modelLocation.getVariant().equals("inventory")
                    || model instanceof MatterConnectedModel) return model;
            return wrappers.computeIfAbsent(model, original -> new MatterConnectedModel(original, context));
        });
        if (!wrappers.isEmpty()) pendingContext = context;
    }

    @SubscribeEvent
    public static void bakingCompleted(ModelEvent.BakingCompleted event) {
        var completed = pendingContext;
        pendingContext = null;
        // Publish during the render-thread apply phase; its block-state cache swap follows in the same apply.
        if (completed != null) MatterConnectedModel.installed(completed);
    }
}
