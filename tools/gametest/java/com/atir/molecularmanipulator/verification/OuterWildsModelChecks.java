package com.atir.molecularmanipulator.verification;

import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.block.GhostMatterBlock;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashSet;

/** Test-only startup check; never bundled into the released mod. */
@EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class OuterWildsModelChecks {
    private static volatile boolean baked;

    @SubscribeEvent
    public static void verify(ModelEvent.BakingCompleted event) {
        int variants = 0;
        var materials = new HashSet<ResourceLocation>();
        for (var state : ModContent.GRAVITY_CRYSTAL_BLOCK.get().getStateDefinition().getPossibleStates()) {
            var model = event.getModels().get(BlockModelShaper.stateToModelLocation(state));
            verifyQuads(model, state, 80, materials);
            variants++;
        }
        if (materials.size() != 4) throw new IllegalStateException("Crystal must bake all four OBJ materials: " + materials);
        for (var state : ModContent.GHOST_MATTER_BLOCK.get().getStateDefinition().getPossibleStates()) {
            var model = event.getModels().get(BlockModelShaper.stateToModelLocation(state));
            var ghostMaterials = new HashSet<ResourceLocation>();
            if (state.getValue(GhostMatterBlock.DISPERSED)) {
                verifyQuads(model, state, 0, ghostMaterials);
                if (!model.getQuads(state, null, RandomSource.create(1), ModelData.EMPTY, null).isEmpty())
                    throw new IllegalStateException("Dispersed mist must have no crystal mesh");
                continue;
            }
            verifyQuads(model, state, 168, ghostMaterials);
            if (ghostMaterials.size() != 4) throw new IllegalStateException("Faceted deposit must use all four crystal materials");
        }
        for (var name : new String[]{"gravity_crystal", "ghost_matter"}) {
            var model = event.getModels().get(ModelResourceLocation.inventory(
                    ResourceLocation.fromNamespaceAndPath("molecularmanipulator", name)));
            verifyQuads(model, null, 4, new HashSet<>());
            if (name.equals("ghost_matter") && model.getQuads(null, null, RandomSource.create(1), ModelData.EMPTY, null)
                    .getFirst().getSprite().contents().getUniqueFrames().count() != 32)
                throw new IllegalStateException("Ghost Matter item must load all 32 animation frames");
        }
        LogUtils.getLogger().info("OUTER_WILDS_MODELS_PASS gravityVariants={} materials={} ghostVariants={} ghostAnimationFrames=32 items=2 missingTextures=0",
                variants, materials.size(), ModContent.GHOST_MATTER_BLOCK.get().getStateDefinition().getPossibleStates().size());
        baked = true;
    }

    private static void verifyQuads(BakedModel model, BlockState state, int minimum, HashSet<ResourceLocation> materials) {
        if (model == null) throw new IllegalStateException("Model not loaded: " + state);
        var random = RandomSource.create(1);
        var quads = new java.util.ArrayList<>(model.getQuads(state, null, random, ModelData.EMPTY, null));
        for (var face : Direction.values()) quads.addAll(model.getQuads(state, face, random, ModelData.EMPTY, null));
        if (quads.size() < minimum) throw new IllegalStateException("Incomplete model for " + state + ": " + quads.size());
        for (var quad : quads) {
            var sprite = quad.getSprite().contents().name();
            if (sprite.equals(MissingTextureAtlasSprite.getLocation())) throw new IllegalStateException("Missing texture: " + state);
            materials.add(sprite);
            int[] vertices = quad.getVertices();
            int stride = vertices.length / 4;
            for (int vertex = 0; vertex < 4; vertex++) for (int axis = 0; axis < 3; axis++) {
                float coordinate = Float.intBitsToFloat(vertices[vertex * stride + axis]);
                if (!Float.isFinite(coordinate) || coordinate < -.05F || coordinate > 1.05F)
                    throw new IllegalStateException("Mesh vertex outside the block: " + coordinate + " " + state);
            }
        }
    }

    @EventBusSubscriber(modid = "molecularmanipulator", value = Dist.CLIENT)
    public static final class Shutdown {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            if (baked && !Boolean.getBoolean("omnisequence.gravity_client_check")
                    && !Boolean.getBoolean("omnisequence.ghost_client_check")) Minecraft.getInstance().stop();
        }
    }
}
