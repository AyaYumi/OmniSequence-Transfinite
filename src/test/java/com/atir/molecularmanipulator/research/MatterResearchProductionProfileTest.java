package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class MatterResearchProductionProfileTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final RecipeHolder<MatterResearchRecipe> FOUNDATION = research("ae_foundation", ResearchDepth.DEFAULTS);

    @Test
    void ownerlessBasicRecipesUseOnlyTheCompletedFoundationParallelism() {
        assertEquals(1, MatterResearchApi.productionParallel(false, List.of(), FOUNDATION, id -> 0));
        assertEquals(256, MatterResearchApi.productionParallel(false, List.of(), FOUNDATION, id -> 2));
        assertEquals(Long.MAX_VALUE, MatterResearchApi.productionParallel(false, List.of(), FOUNDATION, id -> 9));
        assertEquals(Long.MAX_VALUE, MatterResearchApi.productionParallel(false, List.of(), FOUNDATION, id -> Integer.MAX_VALUE),
                "Completion counts from older/custom definitions are clamped to the current last depth");
        assertEquals(1, MatterResearchApi.productionParallel(false, List.of(), null, id -> 9),
                "A missing/removed foundation definition cannot invent a shared bonus");
    }

    @Test
    void lockedOrExplicitlyOwnedRecipesNeverBorrowFoundationProgress() {
        var owner = research("owner", List.of(depth(7), depth(31)));
        var completed = Map.of(FOUNDATION.id(), 9);
        assertEquals(1, MatterResearchApi.productionParallel(true, List.of(), FOUNDATION,
                id -> completed.getOrDefault(id, 0)));
        assertEquals(1, MatterResearchApi.productionParallel(false, List.of(owner), FOUNDATION,
                id -> completed.getOrDefault(id, 0)), "An unfinished explicit owner still prevents fallback");
        assertEquals(31, MatterResearchApi.productionParallel(false, List.of(owner), FOUNDATION,
                id -> id.equals(owner.id()) ? 2 : 9), "Explicit owner parallelism wins even over completed foundation");
    }

    @Test
    void multipleOwnersUseTheMaximumCompletedDepthWithoutAddingOrMultiplying() {
        var first = research("first", List.of(depth(8), depth(64)));
        var second = research("second", List.of(depth(32)));
        var unfinished = research("unfinished", List.of(depth(Long.MAX_VALUE)));
        var completed = Map.of(first.id(), 1, second.id(), Integer.MAX_VALUE, unfinished.id(), 0);
        assertEquals(32, MatterResearchApi.productionParallel(true, List.of(first, second, unfinished), FOUNDATION,
                id -> completed.getOrDefault(id, 0)));
    }

    @Test
    void progressChangesAndReplacementDefinitionsAreReadLive() {
        var completed = new HashMap<ResourceLocation, Integer>();
        assertEquals(1, MatterResearchApi.productionParallel(false, List.of(), FOUNDATION,
                id -> completed.getOrDefault(id, 0)));
        completed.put(FOUNDATION.id(), 2);
        assertEquals(256, MatterResearchApi.productionParallel(false, List.of(), FOUNDATION,
                id -> completed.getOrDefault(id, 0)));
        var replacement = research("ae_foundation", List.of(depth(13)));
        assertEquals(13, MatterResearchApi.productionParallel(false, List.of(), replacement,
                id -> completed.getOrDefault(id, 0)));
        completed.clear();
        assertEquals(1, MatterResearchApi.productionParallel(false, List.of(), replacement,
                id -> completed.getOrDefault(id, 0)), "Resetting completion cannot retain a cached bonus");
    }

    @Test
    void productionTimingStillComesFromStarComputationNotLegacyResearchSpeedFields() throws Exception {
        var profile = method("productionProfile");
        assertTrue(callIndex(profile, "StarComputationNetwork", "forGrid") >= 0);
        assertTrue(callIndex(profile, "StarComputationBenefits", "processingTicks") >= 0);
        assertTrue(callIndex(profile, "MatterResearchApi", "productionParallel") >= 0,
                "The public machine API must use the owner/foundation parallelism selection");
        for (var method : List.of(profile, method("productionParallel"))) {
            assertEquals(-1, callIndex(method, "ResearchDepth", "ticks"));
            assertEquals(-1, callIndex(method, "ResearchDepth", "speedMultiplier"));
            assertEquals(-1, callIndex(method, "ResearchDepth", "processingTicks"));
        }
    }

    @Test
    void globalRecipePermissionIsRequiredBeforeAnyResearchOwnerCanGrantPermission() throws Exception {
        var permission = method("hasPermission");
        int gate = callIndex(permission, "RecipeLocks", "allowed");
        int research = callIndex(permission, "MatterResearchProgress", "completionCount");
        assertTrue(gate >= 0 && research > gate,
                "Foundation fallback must not replace global team/network permissions or original owner research");
        assertTrue(callIndex(method("canUseRecipe"), "MatterFabricationRecipe", "sourceRecipeId") >= 0,
                "Imported recipes must carry their source ID through the same permission entry point");
        assertTrue(callIndex(method("canUseRecipe"), "MatterResearchApi", "hasPermission") >= 0);
        assertTrue(callIndex(method("isRecipeUnlocked"), "MatterResearchApi", "hasPermission") >= 0);
    }

    private static ResearchDepth depth(long parallel) {
        // These legacy speed fields are deliberately extreme: they must not affect production timing.
        return new ResearchDepth(1, parallel, Long.MAX_VALUE, 1, Optional.empty());
    }

    private static RecipeHolder<MatterResearchRecipe> research(String path, List<ResearchDepth> depths) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "research/" + path),
                new MatterResearchRecipe(path, List.of(), List.of(), 600, 0, List.of(), List.of(), 0, 0, depths));
    }

    private static MethodNode method(String name) throws Exception {
        var type = new ClassNode();
        try (var input = MatterResearchApi.class.getResourceAsStream("MatterResearchApi.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        return type.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        int index = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.endsWith("/" + owner) && call.name.equals(name)) return index;
            index++;
        }
        return -1;
    }
}
