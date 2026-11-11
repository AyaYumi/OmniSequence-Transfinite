package com.atir.molecularmanipulator.blockentity;

import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.*;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.atir.molecularmanipulator.recipelock.RecipeMachineOwnership;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.starcomputation.StarComputationNetwork;
import com.atir.molecularmanipulator.starcomputation.StarComputationResearchData;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual powered AE2 managed nodes, actual provider targeting and actual vanilla Crafter execution. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class RecipeLockNetworkGameTests {
    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 300, batch = "recipe_lock_network_shared")
    public static void liveEngineUnionLicensesCraftingButNeverMergesPermanentTeamProgress(GameTestHelper helper) {
        onPoweredGrid(helper, fixture -> {
            var recipe = ordinary(fixture, "shared_network", Items.COMMAND_BLOCK, Items.QUARTZ);
            withTemporaryRecipes(fixture, List.of(recipe, node(NODE_A), node(NODE_B),
                    lock(fixture, "shared_network_lock", recipe.id(), NODE_A, NODE_B)), data -> {
                helper.assertTrue(!RecipeLocks.allowed(fixture.machine, recipe), "An active network without paid research grants nothing");
                seedTeams(fixture, data);
                var before = data.save(new CompoundTag(), fixture.level.registryAccess());
                data.setDirty(false);
                var grid = fixture.provider.getMainNode().getGrid();
                helper.assertTrue(grid.getMachines(StarComputationEngineCoreBlockEntity.class)
                                .containsAll(Set.of(fixture.engineA, fixture.engineB)),
                        "Both concrete production engines are registered as owners of real nodes on this AE2 grid");
                helper.assertTrue(StarComputationNetwork.researchIds(grid).equals(Set.of(NODE_A, NODE_B)),
                        "Research authorization is the live union of both owning teams' online engines, not only the best engine");
                helper.assertTrue(!RecipeLocks.allowed(fixture.level, recipe.id(), fixture.teamA, null)
                                && !RecipeLocks.allowed(fixture.level, recipe.id(), fixture.teamB, null),
                        "Neither team's distinct permanent progress alone satisfies the two-node rule");
                helper.assertTrue(RecipeLocks.allowed(fixture.level, recipe.id(), fixture.teamA, grid)
                                && RecipeLocks.allowed(fixture.level, recipe.id(), fixture.teamB, grid),
                        "Either team can temporarily borrow the missing authorization from this same live grid");
                helper.assertTrue(RecipeLocks.allowed(fixture.machine, recipe),
                        "A real adjacent, active, correctly facing Pattern Provider licenses the bound vanilla machine");
                helper.assertTrue(!RecipeLocks.allowed(fixture.wrongFacingMachine, recipe),
                        "An equally active provider facing away from another machine must not license that machine");
                helper.assertTrue(!RecipeLocks.allowed(fixture.powerNeighbor, recipe),
                        "Merely placing a machine beside a powered ME block does not borrow its network");
                helper.assertTrue(!RecipeLocks.allowedForPlayer(fixture.playerB, recipe),
                        "A nearby player's ordinary inventory/workbench context does not borrow the adjacent network");

                fixture.machine.setItem(0, new ItemStack(Items.COMMAND_BLOCK, 4));
                assertMatch(helper, fixture, fixture.machine, true);
                craft(fixture, fixture.machine);
                helper.assertTrue(fixture.machine.getItem(0).getCount() == 3 && count(fixture.output, Items.QUARTZ) == 1,
                        "A native Crafter performs one ordinary shapeless craft using the borrowed license");
                assertProgressUntouched(helper, fixture, data, before);

                // Structure validation is changed only in this fixture; no subclass overrides production permission APIs.
                validated(fixture.engineA, false);
                helper.assertTrue(fixture.engineA.getMainNode().isActive()
                                && StarComputationNetwork.researchIds(grid).equals(Set.of(NODE_B)),
                        "An unformed engine stops sharing even while its real node is still active and connected");
                assertMatch(helper, fixture, fixture.machine, false);
                craft(fixture, fixture.machine);
                helper.assertTrue(fixture.machine.getItem(0).getCount() == 3 && count(fixture.output, Items.QUARTZ) == 1,
                        "The very next admission and native craft reject a lost engine license without consuming material");
                validated(fixture.engineA, true);
                helper.assertTrue(RecipeLocks.allowed(fixture.machine, recipe),
                        "Restoring the live engine makes authorization visible without a recipe reload or permanent grant");

                fixture.engineA.getMainNode().destroy();
                helper.assertTrue(!fixture.engineA.getMainNode().isActive()
                                && !StarComputationNetwork.researchIds(fixture.provider.getMainNode().getGrid()).contains(NODE_A),
                        "Destroying the real engine node removes its authorization from live grid enumeration");
                helper.assertTrue(!RecipeLocks.allowed(fixture.machine, recipe), "A formerly valid engine cannot lend after going offline");
                craft(fixture, fixture.machine);
                helper.assertTrue(fixture.machine.getItem(0).getCount() == 3 && count(fixture.output, Items.QUARTZ) == 1,
                        "An offline engine's formerly cached positive answer cannot consume the next input");
                assertProgressUntouched(helper, fixture, data, before);
            });
        });
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 300, batch = "recipe_lock_network_disconnect")
    public static void losingTheLastFacingProviderRevokesTheNextCachedAdmissionWithoutPersistingBorrowedResearch(GameTestHelper helper) {
        onPoweredGrid(helper, fixture -> {
            var recipe = ordinary(fixture, "disconnected_network", Items.CHAIN_COMMAND_BLOCK, Items.PRISMARINE_SHARD);
            withTemporaryRecipes(fixture, List.of(recipe, node(NODE_A), node(NODE_B),
                    lock(fixture, "disconnected_network_lock", recipe.id(), NODE_A, NODE_B)), data -> {
                seedTeams(fixture, data);
                fixture.machine.setItem(0, new ItemStack(Items.CHAIN_COMMAND_BLOCK, 4));
                var before = data.save(new CompoundTag(), fixture.level.registryAccess());
                data.setDirty(false);
                var check = RecipeManager.<CraftingInput, CraftingRecipe>createCheck(RecipeType.CRAFTING);
                try (var scope = RecipeExecutionContext.open(fixture.machine)) {
                    helper.assertTrue(check.getRecipeFor(fixture.machine.asCraftInput(), fixture.level).isPresent(),
                            "Seed a positive real CachedCheck while the provider and both granting engines are active");
                }
                craft(fixture, fixture.machine);
                helper.assertTrue(fixture.machine.getItem(0).getCount() == 3 && count(fixture.output, Items.PRISMARINE_SHARD) == 1,
                        "The pre-disconnection craft genuinely executes with shared authorization");
                helper.assertTrue(fixture.engineA.isFormed() && fixture.engineB.isFormed()
                                && fixture.engineA.getMainNode().isActive() && fixture.engineB.getMainNode().isActive(),
                        "Both research engines are valid and online immediately before removing only the machine's provider node");

                // Use the real managed-node lifecycle, not a mocked IGrid or an overridden active flag.
                fixture.provider.getMainNode().destroy();
                helper.assertTrue(fixture.provider.getMainNode().getNode() == null,
                        "The sole correctly-facing provider now has no live actionable node");
                helper.assertTrue(!RecipeLocks.allowed(fixture.machine, recipe), "Borrowed grants vanish on the next machine admission");
                try (var scope = RecipeExecutionContext.open(fixture.machine)) {
                    helper.assertTrue(check.getRecipeFor(fixture.machine.asCraftInput(), fixture.level).isEmpty(),
                            "Even an ID retained by CachedCheck cannot reuse the disconnected network's positive answer");
                }
                craft(fixture, fixture.machine);
                helper.assertTrue(fixture.machine.getItem(0).getCount() == 3 && count(fixture.output, Items.PRISMARINE_SHARD) == 1,
                        "The next native redstone-craft tick neither consumes input nor emits output off-network");
                helper.assertTrue(!RecipeLocks.allowed(fixture.level, recipe.id(), fixture.teamB, null),
                        "No historical network association is consulted when the bound team is evaluated without a live grid");
                assertProgressUntouched(helper, fixture, data, before);
            });
        });
    }

    private static void seedTeams(NetworkFixture fixture, StarComputationResearchData data) {
        fixture.helper.assertTrue(data.addUnlock(fixture.teamA, NODE_A) && data.addUnlock(fixture.teamB, NODE_B),
                "Create distinct, directly constructed permanent research IDs for the two teams");
    }

    private static void assertProgressUntouched(GameTestHelper helper, NetworkFixture fixture,
                                                StarComputationResearchData data, CompoundTag before) {
        helper.assertTrue(data.getUnlockedNodes(fixture.teamA).equals(Set.of(NODE_A))
                        && data.getUnlockedNodes(fixture.teamB).equals(Set.of(NODE_B)),
                "Borrowing from A never writes A's research ID to team B, or vice versa");
        helper.assertTrue(before.equals(data.save(new CompoundTag(), fixture.level.registryAccess())) && !data.isDirty(),
                "Repeated live-grid authorization and native crafting leave the complete permanent progress save unchanged");
        var restarted = StarComputationResearchData.load(data.save(new CompoundTag(), fixture.level.registryAccess()), fixture.level.registryAccess());
        helper.assertTrue(restarted.getUnlockedNodes(fixture.teamB).equals(Set.of(NODE_B)),
                "Serializing and reloading after shared crafting cannot turn the borrowed ID into permanent progress");
    }

    private static void onPoweredGrid(GameTestHelper helper, Consumer<NetworkFixture> assertions) {
        onPoweredGrid(helper, new NetworkFixture(helper), List.of(), assertions);
    }

    /** Additional real executors must be placed before waiting for the shared grid to initialize. */
    static void onPoweredGrid(GameTestHelper helper, NetworkFixture fixture,
                              List<? extends IManagedGridNode> additionalNodes, Consumer<NetworkFixture> assertions) {
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(fixture.engineA.getMainNode().getNode() != null && fixture.engineB.getMainNode().getNode() != null
                            && fixture.power.getMainNode().getNode() != null && fixture.provider.getMainNode().getNode() != null
                            && fixture.wrongFacingProvider.getMainNode().getNode() != null
                            && additionalNodes.stream().allMatch(node -> node.getNode() != null),
                    "The real block entities initialize their actual IManagedGridNodes before connecting");
        }).thenExecute(() -> {
            var power = fixture.power.getMainNode().getNode();
            GridHelper.createConnection(power, fixture.engineA.getMainNode().getNode());
            GridHelper.createConnection(power, fixture.engineB.getMainNode().getNode());
            GridHelper.createConnection(power, fixture.provider.getMainNode().getNode());
            GridHelper.createConnection(power, fixture.wrongFacingProvider.getMainNode().getNode());
            for (var node : additionalNodes) GridHelper.createConnection(power, node.getNode());
        }).thenWaitUntil(() -> {
            var grid = fixture.power.getMainNode().getGrid();
            helper.assertTrue(fixture.power.getMainNode().isActive() && fixture.engineA.getMainNode().isActive()
                            && fixture.engineB.getMainNode().isActive() && fixture.provider.getMainNode().isActive()
                            && fixture.wrongFacingProvider.getMainNode().isActive()
                            && grid == fixture.engineA.getMainNode().getGrid() && grid == fixture.engineB.getMainNode().getGrid()
                            && grid == fixture.provider.getMainNode().getGrid() && grid == fixture.wrongFacingProvider.getMainNode().getGrid()
                            && additionalNodes.stream().allMatch(node -> node.isActive() && node.getGrid() == grid),
                    "Both real engines, both real providers and any added executors share one powered, booted AE2 network with working channels");
        }).thenExecute(() -> {
            // Keep this final callback synchronous: fixture-only validation cannot survive the native multiblock rescan,
            // and temporary recipes/progress must never remain installed across ticks or after assertion failures.
            validated(fixture.engineA, true);
            validated(fixture.engineB, true);
            helper.assertTrue(fixture.engineA.isFormed() && fixture.engineB.isFormed(), "The fixture supplies runtime-validated engine state");
            assertions.accept(fixture);
        }).thenSucceed();
    }

    static void validated(StarComputationEngineCoreBlockEntity engine, boolean valid) {
        try {
            for (String name : List.of("formed", "structureValidated", "starOrreryValidated")) {
                var field = StarComputationEngineCoreBlockEntity.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setBoolean(engine, valid);
            }
            var state = engine.getBlockState().setValue(StarComputationEngineCoreBlock.STAR_ORRERY, true)
                    .setValue(StarComputationEngineCoreBlock.FORMED, valid);
            engine.getLevel().setBlock(engine.getBlockPos(), state, 2);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot set isolated recipe-lock engine fixture validation", error);
        }
    }

    /** No simulated grid, node, provider or engine implementation is substituted for production behavior. */
    static final class NetworkFixture extends ServerRegressionFixture {
        final StarComputationEngineCoreBlockEntity engineA;
        final StarComputationEngineCoreBlockEntity engineB;
        final AENetworkedBlockEntity power;
        final PatternProviderBlockEntity provider;
        final PatternProviderBlockEntity wrongFacingProvider;
        final CrafterBlockEntity machine;
        final CrafterBlockEntity wrongFacingMachine;
        final CrafterBlockEntity powerNeighbor;
        final ChestBlockEntity output;
        final ServerPlayer playerB;
        final UUID teamA;
        final UUID teamB;

        NetworkFixture(GameTestHelper helper) {
            super(helper);
            engineA = place(origin, ModContent.STAR_COMPUTATION_ENGINE_CORE.get().defaultBlockState(), StarComputationEngineCoreBlockEntity.class);
            engineB = place(origin.east(8), ModContent.STAR_COMPUTATION_ENGINE_CORE.get().defaultBlockState(), StarComputationEngineCoreBlockEntity.class);
            power = place(origin.east(16), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), AENetworkedBlockEntity.class);
            machine = place(origin.east(32), Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            provider = place(machine.getBlockPos().west(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                    .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST), PatternProviderBlockEntity.class);
            output = place(machine.getBlockPos().north(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
            wrongFacingMachine = place(origin.east(40), Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            wrongFacingProvider = place(wrongFacingMachine.getBlockPos().east(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                    .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST), PatternProviderBlockEntity.class);
            powerNeighbor = place(power.getBlockPos().north(), Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var playerA = player("lock-network-a", origin);
            playerB = player("lock-network-b", machine.getBlockPos());
            teamA = team(helper, playerA);
            teamB = team(helper, playerB);
            helper.assertTrue(!teamA.equals(teamB), "Network fixture owns two genuinely distinct teams");
            engineA.bindOwner(playerA);
            engineB.bindOwner(playerB);
            helper.assertTrue(RecipeMachineOwnership.bind(machine, playerB)
                            && RecipeMachineOwnership.bind(wrongFacingMachine, playerB)
                            && RecipeMachineOwnership.bind(powerNeighbor, playerB),
                    "Bind the actual vanilla executors to the borrower team without storing any grants on them");
            helper.assertTrue(provider.getTargets().equals(Set.of(Direction.EAST))
                            && !wrongFacingProvider.getTargets().contains(Direction.WEST),
                    "The provider blockstates genuinely distinguish facing-toward from facing-away machines");
        }
    }
}
