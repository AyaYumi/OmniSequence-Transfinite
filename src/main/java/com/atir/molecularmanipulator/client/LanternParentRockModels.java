package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/** Local simulation appearance for flawed parent rock; the world keeps its original block state. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class LanternParentRockModels {
    private static final ResourceLocation FLAWED = ResourceLocation.fromNamespaceAndPath(
            MolecularManipulator.MOD_ID, "flawed_star_marrow_parent_rock");
    private static final ResourceLocation FLAWLESS = ResourceLocation.fromNamespaceAndPath(
            MolecularManipulator.MOD_ID, "flawless_star_marrow_parent_rock");
    private static volatile boolean active;

    private LanternParentRockModels() {}

    /**
     * Publish the local player's synced simulation flag before calling levelRenderer.allChanged()
     * on each transition. Reset to false when the local player disconnects or loses that state.
     * Chunk workers read only this flag, without accessing Minecraft or a player entity.
     */
    public static void setActive(boolean active) {
        LanternParentRockModels.active = active;
    }

    @SubscribeEvent
    public static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        models.replaceAll((location, model) -> {
            if (!location.id().equals(FLAWED)
                    || location.variant().equals(ModelResourceLocation.INVENTORY_VARIANT)
                    || location.variant().equals(ModelResourceLocation.STANDALONE_VARIANT)
                    || model instanceof ParentRockModel) {
                return model;
            }
            // Both registered blocks already have baked models. Read only this reload's registry.
            BakedModel flawless = models.get(new ModelResourceLocation(FLAWLESS, location.variant()));
            return flawless == null ? model : new ParentRockModel(model, flawless);
        });
    }

    private static final class ParentRockModel extends BakedModelWrapper<BakedModel> {
        private final BakedModel flawless;

        private ParentRockModel(BakedModel flawed, BakedModel flawless) {
            super(flawed);
            this.flawless = flawless;
        }

        private BakedModel selected() {
            return active ? flawless : originalModel;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return selected().getQuads(state, side, random);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                ModelData data, @Nullable RenderType renderType) {
            // NeoForge and Sodium use this overload, including null render types for breaking overlays.
            return selected().getQuads(state, side, random, data, renderType);
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return selected().getModelData(level, pos, state, data);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            return selected().getRenderTypes(state, random, data);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return selected().useAmbientOcclusion();
        }

        @Override
        public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
            return selected().useAmbientOcclusion(state, data, renderType);
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return selected().getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return selected().getParticleIcon(data);
        }

        @Override
        public boolean isGui3d() {
            return selected().isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return selected().usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return selected().isCustomRenderer();
        }
    }
}
