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

public final class NexusFormedGeometry implements IUnbakedGeometry<NexusFormedGeometry> {
    public static final IGeometryLoader<NexusFormedGeometry> LOADER = NexusFormedGeometry::read;

    private NexusFormedGeometry() {
    }

    private static NexusFormedGeometry read(JsonObject json, JsonDeserializationContext context) {
        return new NexusFormedGeometry();
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker,
            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        return new NexusFormedBakedModel(
                spriteGetter.apply(context.getMaterial("ring_corner")),
                spriteGetter.apply(context.getMaterial("ring_side_hor")),
                spriteGetter.apply(context.getMaterial("ring_side_ver")),
                spriteGetter.apply(context.getMaterial("light_base")),
                spriteGetter.apply(context.getMaterial("light")));
    }
}
