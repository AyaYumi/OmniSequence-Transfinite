package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises baked-model routing without a Minecraft instance, world, or rendering context. */
class LanternParentRockModelsTest {
    private static final String FLAWED = "flawed_star_marrow_parent_rock";
    private static final String FLAWLESS = "flawless_star_marrow_parent_rock";

    @BeforeEach
    @AfterEach
    void resetLocalState() {
        LanternParentRockModels.setActive(false);
    }

    @Test
    void theSameCachedModelSwitchesBothVanillaAndNeoForgeQuadsOnEachTransition() {
        var flawed = new TestModel(false);
        var flawless = new TestModel(true);
        var models = models(flawed, flawless);
        bake(models);
        BakedModel wrapped = models.get(location(FLAWED, ""));
        assertNotSame(flawed, wrapped);
        var random = RandomSource.create(42);
        var data = ModelData.builder().build();

        for (boolean active : new boolean[] {false, true, false}) {
            LanternParentRockModels.setActive(active);
            TestModel expected = active ? flawless : flawed;
            var sides = new ArrayList<Direction>(List.of(Direction.values()));
            sides.add(null);
            for (Direction side : sides) {
                assertSame(expected.legacyQuads, wrapped.getQuads(null, side, random));
                assertSame(expected.extendedQuads, wrapped.getQuads(null, side, random, data, null));
                assertSame(side, expected.lastSide);
                assertSame(random, expected.lastRandom);
                assertSame(data, expected.lastData);
            }
            assertEquals(active, wrapped.useAmbientOcclusion());
            assertEquals(active, wrapped.usesBlockLight());
        }
    }

    @Test
    void onlyFlawedWorldModelsAreWrapped() {
        var flawed = new TestModel(false);
        var flawless = new TestModel(true);
        var models = models(flawed, flawless);
        var inventory = location(FLAWED, ModelResourceLocation.INVENTORY_VARIANT);
        var standalone = location(FLAWED, ModelResourceLocation.STANDALONE_VARIANT);
        var otherNamespace = new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("othermod", FLAWED), "");
        var chipped = location("damaged_star_marrow_parent_rock", "");
        for (var location : List.of(inventory, standalone, otherNamespace, chipped)) models.put(location, flawed);
        bake(models);
        LanternParentRockModels.setActive(true);

        assertSame(flawless, models.get(location(FLAWLESS, "")));
        for (var location : List.of(inventory, standalone, otherNamespace, chipped)) {
            assertSame(flawed, models.get(location));
        }
        assertSame(flawless.legacyQuads, models.get(location(FLAWED, "")).getQuads(null, null, RandomSource.create()));
    }

    @Test
    void resourceReloadUsesTheNewModelsAndRetainsTheLocalFlag() {
        var first = models(new TestModel(false), new TestModel(true));
        bake(first);
        BakedModel wrapped = first.get(location(FLAWED, ""));
        bake(first);
        assertSame(wrapped, first.get(location(FLAWED, "")), "Repeated events must not nest wrappers");

        LanternParentRockModels.setActive(true);
        var newFlawed = new TestModel(false);
        var newFlawless = new TestModel(true);
        var reloaded = models(newFlawed, newFlawless);
        bake(reloaded);
        BakedModel reloadedModel = reloaded.get(location(FLAWED, ""));
        assertSame(newFlawless.extendedQuads,
                reloadedModel.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null));
        LanternParentRockModels.setActive(false);
        assertSame(newFlawed.extendedQuads,
                reloadedModel.getQuads(null, null, RandomSource.create(), ModelData.EMPTY, null));
    }

    @Test
    void missingCounterpartsKeepTheOriginalModelAndMatchingVariantsUseTheirOwnCounterpart() {
        var flawed = new TestModel(false);
        var flawless = new TestModel(true);
        var models = new HashMap<ModelResourceLocation, BakedModel>();
        var missing = location(FLAWED, "missing=true");
        var matching = location(FLAWED, "custom=true");
        models.put(missing, flawed);
        models.put(matching, flawed);
        models.put(location(FLAWLESS, "custom=true"), flawless);
        bake(models);
        LanternParentRockModels.setActive(true);

        assertSame(flawed, models.get(missing));
        assertSame(flawless.legacyQuads, models.get(matching).getQuads(null, null, RandomSource.create()));
    }

    private static Map<ModelResourceLocation, BakedModel> models(BakedModel flawed, BakedModel flawless) {
        var models = new HashMap<ModelResourceLocation, BakedModel>();
        models.put(location(FLAWED, ""), flawed);
        models.put(location(FLAWLESS, ""), flawless);
        return models;
    }

    private static ModelResourceLocation location(String path, String variant) {
        return new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("molecularmanipulator", path), variant);
    }

    private static void bake(Map<ModelResourceLocation, BakedModel> models) {
        LanternParentRockModels.modifyBakingResult(new ModelEvent.ModifyBakingResult(models, null, null));
    }

    private static final class TestModel implements BakedModel {
        private final boolean light;
        // Distinct lists let assertions identify the delegate and overload without creating rendering resources.
        private final List<BakedQuad> legacyQuads = new ArrayList<>();
        private final List<BakedQuad> extendedQuads = new ArrayList<>();
        private Direction lastSide;
        private RandomSource lastRandom;
        private ModelData lastData;

        private TestModel(boolean light) { this.light = light; }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return legacyQuads;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                ModelData data, RenderType renderType) {
            lastSide = side;
            lastRandom = random;
            lastData = data;
            assertNull(renderType, "Breaking overlays must be forwarded without choosing a render layer");
            return extendedQuads;
        }

        @Override public boolean useAmbientOcclusion() { return light; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return light; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
