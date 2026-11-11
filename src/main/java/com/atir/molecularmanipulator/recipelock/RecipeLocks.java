package com.atir.molecularmanipulator.recipelock;

import appeng.api.AECapabilities;
import appeng.api.crafting.IPatternDetails;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.me.items.CraftingTermMenu;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import java.util.ArrayList;
import java.util.Collection;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.starcomputation.StarComputationNetwork;
import com.atir.molecularmanipulator.starcomputation.StarComputationResearchData;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative recipe admission shared by manual crafting, executors and cached batches. */
public final class RecipeLocks {
    private record Cached(long generation, Collection<RecipeHolder<?>> source, RecipeLockIndex index) {}
    private static final Map<RecipeManager, Cached> INDEXES = new WeakHashMap<>();
    /** AE2 generates name-press tasks instead of registering recipes; expose one stable operation ID. */
    public static final ResourceLocation NAME_PRESS_RECIPE = ResourceLocation.parse("ae2:inscriber/name_press");
    private static final Set<Recipe<?>> NAME_PRESS_TASKS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<Recipe<?>, Boolean>()));

    public static appeng.recipes.handlers.InscriberRecipe identifyNamePress(appeng.recipes.handlers.InscriberRecipe task) {
        NAME_PRESS_TASKS.add(task);
        return task;
    }

    private RecipeLocks() {}

    private static synchronized RecipeLockIndex index(Level level) {
        var manager = level.getRecipeManager();
        long generation = PatternRecipeReloads.generation();
        var source = manager.getRecipes();
        var cached = INDEXES.get(manager);
        if (cached == null || cached.generation() != generation || cached.source() != source) {
            var recipes = new ArrayList<RecipeHolder<?>>(source);
            // Bridge holders live outside RecipeManager. Include them so type/namespace/regex selectors
            // protect their internal IDs as well as the independent checks on their source IDs.
            recipes.addAll(MatterRecipeIndex.get(level).fabrication());
            cached = new Cached(generation, source, RecipeLockIndex.compile(recipes, StarResearchNodes.catalog(level)));
            INDEXES.put(manager, cached);
        }
        return cached.index();
    }

    /** Definition-only display data; it does not report a player or network's current authorization. */
    public record ResearchRequirement(Set<ResourceLocation> nodes, boolean any, boolean valid) {
        public ResearchRequirement { nodes = Set.copyOf(nodes); }
    }

    public static List<ResearchRequirement> requirements(Level level, ResourceLocation recipeId) {
        return index(level).requirements(recipeId);
    }

    /** Include both a bridge holder's internal ID and its original recipe ID, just like admission. */
    public static List<ResearchRequirement> requirements(Level level, RecipeHolder<?> recipe) {
        return requirementsWithSource(level, recipe.id(), recipe.value());
    }

    public static List<ResearchRequirement> requirements(Level level, Recipe<?> recipe) {
        var id = index(level).idOf(recipe);
        if (id == null && NAME_PRESS_TASKS.contains(recipe)) id = NAME_PRESS_RECIPE;
        return requirementsWithSource(level, id, recipe);
    }

    private static List<ResearchRequirement> requirementsWithSource(
            Level level, @Nullable ResourceLocation id, Recipe<?> recipe) {
        var result = new ArrayList<ResearchRequirement>();
        if (id != null) result.addAll(requirements(level, id));
        if (recipe instanceof MatterFabricationRecipe matter && matter.sourceRecipeId() != null) {
            result.addAll(requirements(level, matter.sourceRecipeId()));
        }
        return result.stream().distinct().toList();
    }

    public static boolean hasRules(Level level) {
        return !level.isClientSide && index(level).hasRules();
    }

    /** The personal team's permanent grants plus the current, live network grants; this never writes either. */
    public static boolean allowed(Level level, ResourceLocation recipeId, @Nullable UUID team, @Nullable IGrid grid) {
        if (!(level instanceof ServerLevel server)) return true;
        var index = index(level);
        if (!index.locked(recipeId)) return true;
        var grants = new HashSet<>(StarComputationResearchData.get(server).getUnlockedNodes(team));
        grants.addAll(StarComputationNetwork.researchIds(grid));
        return index.allowed(recipeId, grants);
    }

    public static boolean allowedForPlayer(Player player, ResourceLocation recipeId) {
        if (!(player instanceof ServerPlayer server)) return true;
        return allowed(server.level(), recipeId, FtbTeamOwnership.forPlayer(server), playerGrid(server));
    }

    public static boolean allowedForPlayer(Player player, RecipeHolder<?> recipe) {
        return allowedForPlayer(player, recipe.id()) && sourceAllowed(player, recipe.value());
    }

    private static boolean sourceAllowed(Player player, Recipe<?> recipe) {
        return !(recipe instanceof MatterFabricationRecipe matter) || matter.sourceRecipeId() == null
                || allowedForPlayer(player, matter.sourceRecipeId());
    }

    /** A workbench never borrows a nearby cable/network. Only a real open ME crafting terminal can do so. */
    @Nullable private static IGrid playerGrid(ServerPlayer player) {
        if (!(player.containerMenu instanceof CraftingTermMenu terminal)) return null;
        var host = terminal.getActionSource().machine().orElse(null);
        return host == null ? null : activeGrid(host.getActionableNode());
    }

    public static boolean allowed(BlockEntity machine, ResourceLocation recipeId) {
        var level = machine.getLevel();
        if (level == null) return false;
        if (level.isClientSide || !index(level).locked(recipeId)) return true;
        var team = RecipeMachineOwnership.team(machine);
        if (allowed(level, recipeId, team, null)) return true;
        for (var grid : machineGrids(machine)) {
            if (allowed(level, recipeId, team, grid)) return true;
        }
        return false;
    }

    public static boolean allowed(BlockEntity machine, RecipeHolder<?> recipe) {
        return allowed(machine, recipe.id()) && sourceAllowed(machine, recipe.value());
    }

    public static boolean allowed(BlockEntity machine, Recipe<?> recipe) {
        var level = machine.getLevel();
        if (level == null) return false;
        if (level.isClientSide || !hasRules(level)) return true;
        var id = index(level).idOf(recipe);
        if (id != null) return allowed(machine, id) && sourceAllowed(machine, recipe);
        if (NAME_PRESS_TASKS.contains(recipe)) return allowed(machine, NAME_PRESS_RECIPE);
        // Bridge-created recipes are outside RecipeManager but retain the authoritative source ID.
        if (recipe instanceof MatterFabricationRecipe matter && matter.sourceRecipeId() != null) {
            return allowed(machine, matter.sourceRecipeId());
        }
        // A stale recipe object after /reload must not bypass the new ID index. Supported executors re-query.
        return false;
    }

    private static boolean sourceAllowed(BlockEntity machine, Recipe<?> recipe) {
        return !(recipe instanceof MatterFabricationRecipe matter) || matter.sourceRecipeId() == null
                || allowed(machine, matter.sourceRecipeId());
    }

    /** Processing patterns have no trustworthy recipe ID; their actual target machine is the enforcement point. */
    public static boolean allowedPattern(BlockEntity machine, IPatternDetails pattern) {
        var encoded = pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN);
        return encoded == null || allowed(machine, encoded.recipeId());
    }

    public static boolean allowedInContext(Level level, RecipeHolder<?> recipe) {
        if (level.isClientSide) return true;
        var actor = RecipeExecutionContext.actor();
        if (actor instanceof BlockEntity machine) return machine.getLevel() == level && allowed(machine, recipe);
        if (actor instanceof Player player) return player.level() == level && allowedForPlayer(player, recipe);
        return true;
    }

    /** Deliberately live: splitting networks, losing a channel or removing the last engine takes effect next admission. */
    private static Set<IGrid> machineGrids(BlockEntity machine) {
        var grids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        if (machine instanceof IActionHost host) add(grids, activeGrid(host.getActionableNode()));
        var level = machine.getLevel();
        if (level == null) return grids;
        var gridHost = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, machine.getBlockPos());
        if (gridHost != null) {
            for (var side : Direction.values()) add(grids, activeGrid(gridHost.getGridNode(side)));
        }
        // External processing is licensed only through an adjacent live provider facing this machine,
        // not arbitrary cables, the last player to open it, or a remembered dispatch network.
        for (var side : Direction.values()) {
            var position = machine.getBlockPos().relative(side);
            if (!level.hasChunkAt(position)) continue;
            var neighbor = level.getBlockEntity(position);
            if (neighbor instanceof PatternProviderLogicHost provider) {
                add(grids, providerGrid(provider, side.getOpposite()));
            }
            if (neighbor instanceof IPartHost parts) {
                var part = parts.getPart(side.getOpposite());
                if (part instanceof PatternProviderLogicHost provider) {
                    add(grids, providerGrid(provider, side.getOpposite()));
                }
            }
        }
        return grids;
    }

    @Nullable private static IGrid providerGrid(PatternProviderLogicHost provider, Direction target) {
        if (!provider.getTargets().contains(target)) return null;
        if (provider instanceof IActionHost host) return activeGrid(host.getActionableNode());
        if (provider instanceof IPart part) return activeGrid(part.getGridNode());
        return null;
    }

    private static void add(Set<IGrid> grids, @Nullable IGrid grid) {
        if (grid != null) grids.add(grid);
    }

    @Nullable private static IGrid activeGrid(@Nullable IGridNode node) {
        return node != null && node.isActive() ? node.getGrid() : null;
    }
}
