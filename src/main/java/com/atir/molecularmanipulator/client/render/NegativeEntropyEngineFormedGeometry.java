package com.atir.molecularmanipulator.client.render;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/** Loads the formed engine's tier and connection atlases. */
public final class NegativeEntropyEngineFormedGeometry
        implements IUnbakedGeometry<NegativeEntropyEngineFormedGeometry> {
    public static final IGeometryLoader<NegativeEntropyEngineFormedGeometry> LOADER =
            NegativeEntropyEngineFormedGeometry::read;
    private static final String[] TIER_NAMES = {"none", "base", "quantum", "terminal"};

    private NegativeEntropyEngineFormedGeometry() {
    }

    private static NegativeEntropyEngineFormedGeometry read(JsonObject json, JsonDeserializationContext context) {
        return new NegativeEntropyEngineFormedGeometry();
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
            ItemOverrides overrides) {
        TextureAtlasSprite[][] base = new TextureAtlasSprite[4][16];
        TextureAtlasSprite[][] light = new TextureAtlasSprite[4][16];
        for (int tier = 0; tier < 4; tier++) {
            String tierName = TIER_NAMES[tier];
            for (int mask = 0; mask < 16; mask++) {
                String baseKey = tier == 0 ? "base" : "base_" + tierName + "_connected_" + mask;
                String lightKey = tier == 0 ? "light" : "light_" + tierName + "_connected_" + mask;
                base[tier][mask] = spriteGetter.apply(context.getMaterial(baseKey));
                light[tier][mask] = spriteGetter.apply(context.getMaterial(lightKey));
            }
        }
        return new NegativeEntropyEngineFormedBakedModel(
                spriteGetter.apply(context.getMaterial("particle")), base, light, modelState);
    }
}
