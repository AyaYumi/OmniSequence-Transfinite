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
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

/** Bakes the auto crafter with the same full-bright line overlay as the nexus. */
public final class AutoCrafterGlowGeometry implements IUnbakedGeometry<AutoCrafterGlowGeometry> {
    public static final IGeometryLoader<AutoCrafterGlowGeometry> LOADER =
            (json, context) -> new AutoCrafterGlowGeometry();

    private AutoCrafterGlowGeometry() {
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState,
            ItemOverrides overrides) {
        return new AutoCrafterGlowBakedModel(
                spriteGetter.apply(context.getMaterial("base")),
                spriteGetter.apply(context.getMaterial("glow")));
    }
}
