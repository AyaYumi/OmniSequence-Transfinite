package com.atir.molecularmanipulator.recipelock;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.StarComputationResearchData;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Pure permission/save boundaries. The live AE2 union is exercised separately by RecipeLockNetworkGameTests. */
class RecipeLockPermissionsTest {
    private static final ResourceLocation RECIPE = ResourceLocation.parse("pack:locked_recipe");
    private static final ResourceLocation RESEARCH = ResourceLocation.parse("pack:custom_research");
    private static final ResourceLocation OTHER_RESEARCH = ResourceLocation.parse("other:custom_research");

    @BeforeAll
    static void bootstrap() { RecipeLockRecipeTest.bootstrap(); }

    @Test
    void alternatingTeamsNeverReuseAnotherTeamsAnswerAndAPermanentUnlockIsVisibleWithoutRebuilding() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var data = new StarComputationResearchData();
        var index = index();
        assertTrue(data.addUnlock(first, RESEARCH));
        assertTrue(data.addUnlock(second, OTHER_RESEARCH));
        data.setDirty(false);
        for (int lookup = 0; lookup < 8; lookup++) {
            assertTrue(index.allowed(RECIPE, data.getUnlockedNodes(first)));
            assertFalse(index.allowed(RECIPE, data.getUnlockedNodes(second)));
            assertFalse(index.allowed(RECIPE, data.getUnlockedNodes(null)));
        }
        assertFalse(data.isDirty(), "Admission only reads the owning team's progress");
        var beforeUnlock = data.getUnlockedNodes(second);
        assertTrue(data.addUnlock(second, RESEARCH));
        assertTrue(index.allowed(RECIPE, data.getUnlockedNodes(second)));
        assertEquals(Set.of(OTHER_RESEARCH), beforeUnlock, "Published progress snapshots do not grow in place");
        assertThrows(UnsupportedOperationException.class, () -> beforeUnlock.add(RESEARCH));
        assertEquals(Set.of(RESEARCH), data.getUnlockedNodes(first));
    }

    @Test
    void temporaryGrantSetUnionAuthorizesUseButDoesNotChangeEitherTeamsSavedIds() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var data = new StarComputationResearchData();
        data.addUnlock(first, RESEARCH);
        data.addUnlock(second, OTHER_RESEARCH);
        data.setDirty(false);
        var before = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        var borrowed = new HashSet<>(data.getUnlockedNodes(second));
        borrowed.addAll(data.getUnlockedNodes(first));
        assertTrue(index().allowed(RECIPE, borrowed));
        assertFalse(index().allowed(RECIPE, data.getUnlockedNodes(second)), "Discarding live grants denies the next admission");
        assertEquals(before, data.save(new CompoundTag(), RegistryAccess.EMPTY));
        assertFalse(data.isDirty());
        var restarted = StarComputationResearchData.load(before, RegistryAccess.EMPTY);
        assertEquals(Set.of(RESEARCH), restarted.getUnlockedNodes(first));
        assertEquals(Set.of(OTHER_RESEARCH), restarted.getUnlockedNodes(second));
        assertFalse(index().allowed(RECIPE, restarted.getUnlockedNodes(second)), "A save/restart cannot make borrowed research permanent");
    }

    private static RecipeLockIndex index() {
        var selector = new RecipeLockRecipe.Selector(List.of(RECIPE), List.of(), List.of(), List.of(), List.of());
        var lock = new RecipeHolder<>(ResourceLocation.parse("pack:rule"), new RecipeLockRecipe(List.of(RESEARCH), "all", selector));
        return RecipeLockIndex.build(Map.of(RECIPE, ResourceLocation.parse("minecraft:crafting")), List.of(lock), Set.of(RESEARCH));
    }
}
