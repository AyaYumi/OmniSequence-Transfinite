package com.atir.molecularmanipulator.blockentity;

import static com.atir.molecularmanipulator.blockentity.EcoCpuGameTests.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import appeng.me.helpers.MachineSource;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.*;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.*;
import cn.dancingsnow.neoecoae.crafting.planner.result.*;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeIntegratedWorkingStationRecipe;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCpuDispatchLedger;
import com.atir.molecularmanipulator.integration.eco.EcoOmniParentOrderCpu;
import com.google.common.collect.ImmutableSet;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real providers, physical recipes, grid insertion and requester links exercise output ownership. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class EcoOutputGameTests {
    private static final AEKey INPUT = AEItemKey.of(Items.DIAMOND);
    private static final AEKey OUTPUT = AEItemKey.of(Items.EMERALD);

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void ordinaryRequesterPartialRejectWithFullNetworkRetainsExactProgress(GameTestHelper helper) {
        powered(helper, 20, f -> partialRequester(helper, f, false));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void aelisExactRequesterPartialRejectWithFullNetworkRetainsExactProgress(GameTestHelper helper) {
        powered(helper, 21, f -> partialRequester(helper, f, true));
    }

    private static void partialRequester(GameTestHelper helper, Fixture f, boolean aelis) {
        f.stock.values.set(INPUT, 2);
        f.stock.insertCapacity = 0;
        var requester = new Requester(f);
        ICraftingPlan plan;
        if (aelis) {
            // Deliberately register equal ECO content first: ownership/admission must ignore this foreign signature.
            var collision = result(f, 2);
            ECOPlanningResultRegistry.register(collision.plan(), collision);
            var used = new KeyCounter(); used.add(INPUT, 2);
            var ordinary = new CraftingPlan(new GenericStack(OUTPUT, 4), 100, false, false,
                    used, new KeyCounter(), new KeyCounter(), Map.of(f.pattern, 2L));
            // Unused infinite planning stock forces Aelis exact execution without changing this recipe's inputs.
            plan = AelisExactCraftingPlanApi.attachExecutionMetadata(ordinary, BigInteger.valueOf(4),
                    Map.of(f.pattern, BigInteger.TWO), Map.of(AEItemKey.of(Items.BARRIER), BigInteger.TEN.pow(30)));
            helper.assertTrue(AelisExactCraftingPlanApi.executionRequirement(plan).requiresExactExecution(),
                    "Fixture must request actual Aelis exact execution");
            helper.assertTrue(ECOPlanningResultRegistry.find(plan) == collision
                            && ECOPlanningResultRegistry.findExact(plan) == null
                            && !com.atir.molecularmanipulator.integration.eco.EcoOmniPlanBridge.owns(plan),
                    "Aelis-only metadata must not acquire ECO ownership from an equal recipe/count signature");
        } else {
            var result = result(f, 2);
            ECOPlanningResultRegistry.register(result.plan(), result);
            plan = result.plan();
        }
        submitRequester(helper, f, plan, requester);
        helper.assertTrue(f.access().ecoOmni$exactState() != null && f.access().ecoOmni$managed() != aelis,
                "Both real exact paths must attach correctly, including non-ECO Aelis metadata: state="
                        + f.access().ecoOmni$exactState() + " managed=" + f.access().ecoOmni$managed()
                        + " requirement=" + AelisExactCraftingPlanApi.executionRequirement(plan));
        sequence(helper).thenWaitUntil(() -> helper.assertTrue(requester.delivered == 1,
                "Real requester must partially accept MODULATE despite simulating complete capacity"))
                .thenIdle(5).thenExecute(() -> {
                    helper.assertTrue(f.cpu().isBusy() && !requester.link.isDone() && requester.delivered == 1
                                    && f.access().ecoOmni$exactState().outputProgress().remaining().equals(BigInteger.valueOf(3))
                                    && f.logic().getWaitingFor(OUTPUT) == 0 && retained(f, OUTPUT).equals(BigInteger.valueOf(3))
                                    && f.buffer().contents(true).isEmpty() && f.stock.amount(OUTPUT) == 0,
                            "Rejected tail must remain physically owned; goal/link cannot complete from the producer offer");
                    requester.allowance = Long.MAX_VALUE;
                }).thenWaitUntil(() -> helper.assertTrue(!f.cpu().isBusy() && requester.link.isDone(),
                        "Reopened requester must complete actual CPU/link"))
                .thenIdle(3).thenExecute(() -> {
                    helper.assertTrue(requester.delivered == 4 && f.stock.amount(OUTPUT) == 0
                                    && retained(f, OUTPUT).signum() == 0 && f.buffer().contents(true).isEmpty(),
                            "Exactly four products must reach requester once with no full-network fallback or lost tail");
                    finish(helper, f, aelis ? "aelis_partial_requester" : "eco_partial_requester");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void standaloneFullNetworkRetainsOwnedFinalUntilCapacityReturns(GameTestHelper helper) {
        powered(helper, 22, f -> {
            f.stock.values.set(INPUT, 2); f.stock.insertCapacity = 0;
            var result = result(f, 2);
            ECOPlanningResultRegistry.register(result.plan(), result);
            var submitted = f.service().submitJob(result.plan(), null, f.cpu(), false, new MachineSource(f.core()));
            helper.assertTrue(submitted.successful(), "Real standalone ECO order must submit");
            sequence(helper).thenWaitUntil(() -> helper.assertTrue(f.logic().getWaitingFor(OUTPUT) == 0
                            && retained(f, OUTPUT).equals(BigInteger.valueOf(4)), "Actual provider must transfer all products to CPU"))
                    .thenIdle(5).thenExecute(() -> {
                        helper.assertTrue(f.cpu().isBusy() && f.stock.amount(OUTPUT) == 0
                                        && f.access().ecoOmni$exactState().outputProgress().remaining().equals(BigInteger.valueOf(4))
                                        && f.buffer().contents(true).isEmpty(),
                                "Full storage must leave goal outstanding while CPU retains all four real products");
                        f.stock.insertCapacity = Long.MAX_VALUE;
                    }).thenWaitUntil(() -> helper.assertTrue(!f.cpu().isBusy() && f.stock.amount(OUTPUT) == 4,
                            "Actual storage acceptance must settle goal after capacity returns"))
                    .thenExecute(() -> { helper.assertTrue(retained(f, OUTPUT).signum() == 0, "No duplicate CPU tail");
                        finish(helper, f, "full_network_retry"); }).thenSucceed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void ordinaryFinalKeyGrowthCycleProtectsNextPhysicalInputs(GameTestHelper helper) {
        powered(helper, 23, f -> {
            var definition = installRecipe(f, "final_growth", 0, List.of(
                    new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 1)),
                    List.of(new ItemStack(Items.DIAMOND, 2)), 4).getDefinition();
            sequence(helper).thenIdle(3).thenWaitUntil(() -> publishedPattern(f, INPUT, definition))
                    .thenExecute(() -> { f.pattern = publishedPattern(f, INPUT, definition); growth(helper, f, false); });
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void uuidFinalKeyGrowthCycleProtectsNextPhysicalInputs(GameTestHelper helper) {
        powered(helper, 24, f -> nativeProvider(helper, f, Items.DIAMOND, Items.DIAMOND,
                ignored -> growth(helper, f, true)));
    }

    private static void growth(GameTestHelper helper, Fixture f, boolean exact) {
        f.stock.values.set(INPUT, 1);
        var result = growthResult(helper, f, exact);
        ICraftingPlan plan;
        if (exact) plan = exactPlan(helper, result);
        else { ECOPlanningResultRegistry.register(result.plan(), result); plan = result.plan(); }
        var requester = new Requester(f); requester.allowance = Long.MAX_VALUE;
        submitRequester(helper, f, plan, requester);
        helper.assertTrue(f.host().omniDispatch$reservedInput(INPUT).signum() > 0,
                "Actual execution runtime must reserve final-key feedback/startup material before the first firing");
        sequence(helper).thenWaitUntil(() -> {
                    helper.assertTrue(!f.cpu().isBusy() && requester.link.isDone(),
                        "Three real A -> 2A firings from one seed must finish with A as final key");
                })
                .thenIdle(5).thenExecute(() -> {
                    helper.assertTrue(requester.delivered == 3 && f.stock.amount(INPUT) == 1
                                    && retained(f, INPUT).signum() == 0 && f.buffer().contents(true).isEmpty()
                                    && f.buffer().contents(false).isEmpty(),
                            "Net goal is three, original seed is refunded once, and feedback protection must release at completion");
                    finish(helper, f, exact ? "uuid_final_key_growth" : "ordinary_final_key_growth");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void uuidExactOversupplyOwnsFourButDeliversOnlyGoalThree(GameTestHelper helper) {
        powered(helper, 25, f -> nativeProvider(helper, f, Items.DIAMOND, Items.EMERALD, machine -> {
            f.stock.values.set(INPUT, 2); f.stock.insertCapacity = 0;
            var requester = new Requester(f); requester.allowance = Long.MAX_VALUE;
            submitRequester(helper, f, exactPlan(helper, oversupplyResult(helper, f)), requester);
            sequence(helper).thenWaitUntil(() -> {
                        helper.assertTrue(!f.cpu().isBusy() && requester.link.isDone(),
                            "Expected UUID tail must drain so exact child can finish");
                    })
                    .thenIdle(3).thenExecute(() -> {
                        helper.assertTrue(requester.delivered == 3 && retained(f, OUTPUT).equals(BigInteger.ONE)
                                        && f.stock.amount(OUTPUT) == 0 && machine.getCurrentBatchInputs().isEmpty(),
                                "Actual UUID worker must transfer all four products; requester gets three and CPU retains one at full storage");
                        f.stock.insertCapacity = Long.MAX_VALUE;
                    }).thenWaitUntil(() -> helper.assertTrue(f.stock.amount(OUTPUT) == 1 && retained(f, OUTPUT).signum() == 0,
                            "Surplus must refund through real storage exactly once"))
                    .thenExecute(() -> { helper.assertTrue(requester.delivered == 3, "Surplus cannot overdeliver requester");
                        finish(helper, f, "uuid_exact_surplus"); }).thenSucceed();
        }));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 700)
    public static void realParentWaitsForPartialRequesterAndDrainsUuidOversupply(GameTestHelper helper) {
        powered(helper, 26, f -> nativeProvider(helper, f, Items.DIAMOND, Items.EMERALD, machine -> {
            f.stock.values.set(INPUT, 2); f.stock.insertCapacity = 0;
            var requester = new Requester(f);
            var request = new ECOBigOrderRequest(OUTPUT, BigInteger.valueOf(3), false,
                    new ECOPlannerOptions(true, true, Set.of()));
            var submitted = request.submit(carrier -> f.service().submitJob(carrier, requester, f.cpu(), false,
                    new MachineSource(f.core())));
            helper.assertTrue(submitted.successful(), "Real parent carrier must submit with linked requester");
            requester.link = submitted.link();
            sequence(helper).thenWaitUntil(() -> helper.assertTrue(requester.delivered == 1,
                            "Asynchronously planned real parent child must produce and partially deliver"))
                    .thenIdle(5).thenExecute(() -> {
                        var parent = (EcoOmniParentOrderCpu) f.logic();
                        helper.assertTrue(f.cpu().isBusy() && !requester.link.isDone() && parent.ecoOmni$parentProgress() != null
                                        && f.access().ecoOmni$exactState().outputProgress().remaining().equals(BigInteger.TWO)
                                        && f.logic().getWaitingFor(OUTPUT) == 0 && retained(f, OUTPUT).equals(BigInteger.valueOf(3))
                                        && machine.getCurrentBatchInputs().isEmpty(),
                                "Parent must retain child while requester rejects, with all four UUID outputs owned and no stranded waiting tail");
                        requester.allowance = Long.MAX_VALUE;
                    }).thenWaitUntil(() -> {
                        helper.assertTrue(!f.cpu().isBusy() && requester.link.isDone(),
                            "Parent must complete after exactly three actual destination accepts");
                    })
                    .thenExecute(() -> {
                        helper.assertTrue(requester.delivered == 3 && retained(f, OUTPUT).equals(BigInteger.ONE)
                                        && ((EcoOmniParentOrderCpu) f.logic()).ecoOmni$parentProgress() == null,
                                "Parent clears only after delivered goal and empty expected waiting; surplus remains physical CPU stock");
                        f.stock.insertCapacity = Long.MAX_VALUE;
                    }).thenWaitUntil(() -> helper.assertTrue(f.stock.amount(OUTPUT) == 1 && retained(f, OUTPUT).signum() == 0,
                            "Parent surplus must refund once after storage reopens"))
                    .thenExecute(() -> { helper.assertTrue(requester.delivered == 3, "Parent cannot overdeliver final requester");
                        finish(helper, f, "parent_partial_uuid_surplus"); }).thenSucceed();
        }));
    }

    private static ECOPlanningResult oversupplyResult(GameTestHelper helper, Fixture f) {
        var used = new KeyCounter(); used.add(INPUT, 2);
        var plan = new CraftingPlan(new GenericStack(OUTPUT, 3), Long.MAX_VALUE, false, false,
                used, new KeyCounter(), new KeyCounter(), Map.of(f.pattern, 2L));
        var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.ACYCLIC,
                ComponentPlanningResult.Status.PLANNED, Map.of(OUTPUT, 3L), Set.of(f.pattern), Set.of(f.pattern), null,
                CycleExternalDemandStatus.SOLVED, Map.of(), "Real output surplus fixture", null,
                CycleExecutionDisposition.NOT_REQUIRED, Map.of());
        var provenance = new MaterialProvenance();
        allocate(provenance, MaterialDemand.input(f.pattern, 0, INPUT, PlannerAmount.of(2)), MaterialSource.Stock.INSTANCE);
        allocate(provenance, MaterialDemand.goal(OUTPUT, PlannerAmount.of(3)), new MaterialSource.PatternOutput(f.pattern, true));
        var result = planningResult(helper, PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, plan, List.of(component), provenance);
        result.setTheoreticalBytes(PlannerAmount.of(BigInteger.TEN.pow(30)));
        return result;
    }

    private static ECOPlanningResult growthResult(GameTestHelper helper, Fixture f, boolean exact) {
        final cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork network;
        try { network = new CraftingNetworkCompiler().compile(f.service(), INPUT, ECOCancellation.NONE); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        var producer = network.producersOf(INPUT).stream()
                .filter(p -> p.details().getDefinition().equals(f.pattern.getDefinition())).findFirst().orElseThrow();
        var times = Map.of(f.pattern, 3L);
        var solved = new CycleSolveResult(CycleSolveStatus.SUCCESS, times, Map.of(), Map.of(INPUT, 1L), Map.of(),
                Map.of(INPUT, 4L), Map.of(INPUT, 3L), List.of(), List.of(new PatternRun(producer, 1, 1, 3)),
                List.of(), CycleSolveMetrics.NONE);
        var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.CYCLIC,
                ComponentPlanningResult.Status.PLANNED, Map.of(INPUT, 3L), Set.of(f.pattern), Set.of(f.pattern),
                CyclePlanningStatus.SOLVED, CycleExternalDemandStatus.SOLVED, Map.of(), "Real final-key growth fixture",
                solved, CycleExecutionDisposition.ORDERED_EXECUTION, Map.of());
        var used = new KeyCounter(); used.add(INPUT, 1);
        var plan = new CraftingPlan(new GenericStack(INPUT, 3), exact ? Long.MAX_VALUE : 100, false, false,
                used, new KeyCounter(), new KeyCounter(), times);
        var provenance = new MaterialProvenance();
        allocate(provenance, MaterialDemand.boundary(0, INPUT, PlannerAmount.of(1)), MaterialSource.Stock.INSTANCE);
        allocate(provenance, MaterialDemand.goal(INPUT, PlannerAmount.of(3)), new MaterialSource.CycleOutput(0));
        var result = planningResult(helper, exact ? PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE : PlanningStatus.SUCCESS,
                plan, List.of(component), provenance);
        if (exact) result.setTheoreticalBytes(PlannerAmount.of(BigInteger.TEN.pow(30)));
        return result;
    }

    private static BigInteger retained(Fixture f, AEKey key) {
        var saved = new CompoundTag(); f.logic().writeToNBT(saved, f.helper.getLevel().registryAccess());
        var amounts = saved.getCompound(OmniCpuDispatchLedger.NBT_KEY).getList("finalStock", net.minecraft.nbt.Tag.TAG_COMPOUND);
        var total = f.access().ecoOmni$physicalAmount(key);
        for (var value : amounts) {
            var entry = (CompoundTag) value;
            if (key.equals(AEKey.fromTagGeneric(f.helper.getLevel().registryAccess(), entry)))
                total = total.add(new BigInteger(entry.getString("amount")));
        }
        return total;
    }

    private static void submitRequester(GameTestHelper helper, Fixture f, ICraftingPlan plan, Requester requester) {
        var submitted = f.service().submitJob(plan, requester, f.cpu(), false, new MachineSource(f.core()));
        helper.assertTrue(submitted.successful(), "Real requester order must submit: " + submitted);
        requester.link = submitted.link();
    }

    /** Controlled sink is attached to an actual grid node and paired through real CraftingLink/Nexus. */
    private static final class Requester implements ICraftingRequester {
        final Fixture fixture;
        ICraftingLink link;
        long allowance = 1, delivered;
        Requester(Fixture fixture) { this.fixture = fixture; }
        @Override public IGridNode getActionableNode() { return fixture.storageNode.getNode(); }
        @Override public ImmutableSet<ICraftingLink> getRequestedJobs() { return link == null ? ImmutableSet.of() : ImmutableSet.of(link); }
        @Override public long insertCraftedItems(ICraftingLink link, AEKey key, long amount, Actionable mode) {
            if (mode == Actionable.SIMULATE) return amount;
            long accepted = Math.min(amount, allowance);
            allowance -= accepted; delivered += accepted;
            return accepted;
        }
        @Override public void jobStateChange(ICraftingLink link) { }
    }

    /** Native ECO workstation really consumes inputs and emits its UUID-directed output. */
    private static void nativeProvider(GameTestHelper helper, Fixture f, Item input, Item output,
            Consumer<ECOLargeIntegratedWorkingStationBlockEntity> action) {
        var pos = f.cpuPos.east(12);
        var casing = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("neoecoae", "large_integrated_working_station_casing"));
        for (var at : net.minecraft.core.BlockPos.betweenClosed(pos.offset(-1, -1, 0), pos.offset(1, 0, 1)))
            reset(helper, at, casing.defaultBlockState());
        reset(helper, pos, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("neoecoae", "integrated_working_station"))
                .defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
        for (var suffix : List.of("interface", "output_hatch", "input_hatch")) {
            var at = suffix.equals("interface") ? pos.below().south()
                    : suffix.equals("output_hatch") ? pos.below().south().west() : pos.below().south().east();
            reset(helper, at, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("neoecoae",
                    "large_integrated_working_station_" + suffix)).defaultBlockState());
        }
        var machine = (ECOLargeIntegratedWorkingStationBlockEntity) helper.getLevel().getBlockEntity(pos);
        var provider = (ECOLargeIntegratedWorkingStationInterfaceBlockEntity) helper.getLevel().getBlockEntity(pos.below().south());
        f.assembly.getLogic().getPatternInv().setItemDirect(0, ItemStack.EMPTY);
        f.assembly.getLogic().updatePatterns();
        var id = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "gametest/output_boundary_" + f.cpuPos.getX());
        var recipe = new LargeIntegratedWorkingStationRecipe(new IntegratedWorkingStationRecipe(
                List.of(SizedIngredient.of(input, 1)), new SizedFluidIngredient(FluidIngredient.empty(), 1),
                new ItemStack(output, 2), FluidStack.EMPTY, 1));
        var recipes = new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
        recipes.add(new RecipeHolder<>(id, recipe)); helper.getLevel().getRecipeManager().replaceRecipes(recipes);
        sequence(helper).thenIdle(5).thenExecute(() -> {
            machine.rebuildMultiblock();
            GridHelper.createConnection(machine.getMainNode().getNode(), f.core().getMainNode().getNode());
            GridHelper.createConnection(provider.getMainNode().getNode(), f.core().getMainNode().getNode());
            provider.getWorkstationProvider().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(input), 1)), List.of(new GenericStack(AEItemKey.of(output), 2))));
            provider.getWorkstationProvider().updatePatterns(); ICraftingProvider.requestUpdate(provider.getMainNode());
        }).thenWaitUntil(() -> helper.assertTrue(machine.isFormed() && machine.canAcceptPattern()
                        && !provider.getWorkstationProvider().getAvailablePatterns().isEmpty(), "Native ECO provider must physically form and advertise"))
                .thenIdle(3).thenWaitUntil(() -> publishedPattern(f, AEItemKey.of(output),
                        provider.getWorkstationProvider().getAvailablePatterns().getFirst().getDefinition()))
                .thenExecute(() -> {
                    f.pattern = publishedPattern(f, AEItemKey.of(output), provider.getWorkstationProvider().getAvailablePatterns().getFirst().getDefinition());
                    action.accept(machine);
                });
    }
}
