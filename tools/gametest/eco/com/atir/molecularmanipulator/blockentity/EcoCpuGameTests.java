package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.api.me.output.ECOCraftingOutputRouter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.recipe.IntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.recipe.LargeIntegratedWorkingStationRecipe;
import cn.dancingsnow.neoecoae.crafting.planner.ECOCancellation;
import cn.dancingsnow.neoecoae.crafting.planner.compile.CraftingNetworkCompiler;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveMetrics;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveResult;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.CycleSolveStatus;
import cn.dancingsnow.neoecoae.crafting.planner.cycle.PatternRun;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.CyclePlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatchAccess;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatchHost;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpu;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpuAccess;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanExecution;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.research.ResearchDepth;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Actual powered AE2 CPUs, ECO plans, a Matter well and a native ECO workstation provider.
 * Only the mounted storage is a small controlled fixture; no substituted CPU/provider/runtime,
 * reflective field access, hand-installed execution state or direct task-acceptance calls.
 * All helpers live here because eco.init.gradle intentionally includes only selected fixtures.
 */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class EcoCpuGameTests {
    private static final AEKey INPUT = AEItemKey.of(Items.DIAMOND);
    private static final AEKey OUTPUT = AEItemKey.of(Items.EMERALD);
    private static final long CRAFTS = 1026;
    private static final long PRODUCTS = CRAFTS * 2;
    private static final BigInteger EXACT_BYTES = BigInteger.TEN.pow(30);
    private static final Set<ResourceLocation> FUZZY = Set.of(ResourceLocation.withDefaultNamespace("diamond"));

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 260)
    public static void mixedInHostEnablesOnlyOwnedOmniCpu(GameTestHelper helper) {
        powered(helper, 0, f -> {
            helper.assertTrue(f.host().ecoDispatch$enabled() && ((EcoOmniPlanCpu) f.logic()).ecoOmni$handlesPlans(),
                    "Actual transformed Omni CPU must enable the complete ECO host and plan attachment");
            var ordinaryPos = f.cpuPos.east(5);
            reset(helper, ordinaryPos, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            sequence(helper).thenWaitUntil(() -> {
                var ordinary = (CraftingBlockEntity) helper.getLevel().getBlockEntity(ordinaryPos);
                helper.assertTrue(ordinary.getCluster() != null, "Real AE2 1K CPU must form");
            }).thenExecute(() -> {
                var ordinary = ((CraftingBlockEntity) helper.getLevel().getBlockEntity(ordinaryPos)).getCluster();
                helper.assertTrue(ordinary.craftingLogic instanceof EcoCpuDispatchHost,
                        "Shared host mixin must actually be applied to native AE2 CPU logic");
                helper.assertTrue(!((EcoCpuDispatchHost) ordinary.craftingLogic).ecoDispatch$enabled(),
                        "Unowned AE2 CPU must retain disabled Omni dispatch host");
                finish(helper, f, "host_enabled_ownership");
            }).thenSucceed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 320)
    public static void ordinaryEcoPlanUsesNativeFastPathAndSettlesWaiting(GameTestHelper helper) {
        powered(helper, 1, f -> {
            var result = result(f, CRAFTS);
            var plan = result.plan();
            ECOPlanningResultRegistry.register(plan, result);
            submit(helper, f, plan);
            assertMetadata(helper, f, result.planningId(), BigInteger.valueOf(CRAFTS));
            helper.assertTrue(!f.host().ecoDispatch$exactOrder(), "Ordinary ECO plan must retain ordinary dispatch mode");
            complete(helper, f, "ordinary_prepare_dispatch_outputs");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 320)
    public static void exactEcoPlanAttachesMetadataAndAdvancesActualCpu(GameTestHelper helper) {
        powered(helper, 2, f -> {
            var result = exactResult(f, CRAFTS);
            var plan = exactPlan(helper, result);
            submit(helper, f, plan);
            assertMetadata(helper, f, null, BigInteger.valueOf(CRAFTS));
            helper.assertTrue(f.host().ecoDispatch$exactOrder()
                            && execution(f).theoreticalBytes().equals(BigInteger.TEN.pow(30))
                            && execution(f).fuzzyPlanningItemIds().equals(FUZZY),
                    "Exact ECO source must reach actual Omni host with theoretical bytes and fuzzy metadata intact");
            complete(helper, f, "exact_plan_metadata_progress");
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 420)
    public static void submittedWaitingAndProviderOwnershipSurviveCpuReload(GameTestHelper helper) {
        powered(helper, 3, f -> {
            var result = result(f, CRAFTS);
            ECOPlanningResultRegistry.register(result.plan(), result);
            submit(helper, f, result.plan());
            sequence(helper).thenWaitUntil(() -> assertDispatched(helper, f)).thenExecute(() -> {
                f.assembly.getCatalystInventory().clear();
                var before = execution(f).remainingByTask();
                var id = f.host().ecoDispatch$jobId();
                var saved = f.core().saveWithFullMetadata(helper.getLevel().registryAccess());
                var oldCore = f.core();
                oldCore.onChunkUnloaded();
                helper.getLevel().removeBlockEntity(f.cpuPos);
                var loaded = BlockEntity.loadStatic(f.cpuPos, helper.getLevel().getBlockState(f.cpuPos),
                        saved, helper.getLevel().registryAccess());
                helper.assertTrue(loaded instanceof OmniComputationCoreBlockEntity,
                        "World block-entity codec must restore the actual nexus");
                helper.getLevel().setBlockEntity(loaded);
                // The assembly has its own real queue codec; it is not reconstructed from CPU expectations.
                var buffer = new CompoundTag();
                f.buffer().save(buffer, helper.getLevel().registryAccess());
                f.buffer().load(buffer, helper.getLevel().registryAccess());
                sequence(helper).thenWaitUntil(() -> helper.assertTrue(f.core().isNetworkOnline(),
                        "Restored CPU must reconnect to real powered grid")).thenExecute(() -> {
                    // Rebuild explicit test-grid connections after the old nexus node was unloaded.
                    GridHelper.createConnection(f.controller.getMainNode().getNode(), f.core().getMainNode().getNode());
                    GridHelper.createConnection(f.storageNode.getNode(), f.core().getMainNode().getNode());
                    IStorageProvider.requestUpdate(f.storageNode);
                }).thenWaitUntil(() -> helper.assertTrue(f.assembly.isOperational()
                                && f.storageNode.getGrid() == f.core().getMainNode().getGrid(),
                        "Restored provider and finite inventory must rejoin the CPU grid")).thenExecute(() -> {
                    helper.assertTrue(id.equals(f.host().ecoDispatch$jobId())
                                    && execution(f).remainingByTask().equals(before)
                                    && result.planningId().equals(execution(f).planningId())
                                    && f.logic().getWaitingFor(OUTPUT) == PRODUCTS
                                    && ownedInputs(f) == CRAFTS,
                            "Reload must retain UUID, task progress, phase metadata, exact waiting and physical provider inputs");
                    catalyst(f);
                }).thenWaitUntil(() -> assertComplete(helper, f)).thenExecute(() -> {
                    helper.assertTrue(f.stock.amount(INPUT) == 0 && f.stock.amount(OUTPUT) == PRODUCTS,
                            "Reloaded work must deliver every product once without replaying the input reservation");
                    finish(helper, f, "cpu_provider_reload");
                }).thenSucceed();
            });
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 320)
    public static void cancellationRecoversAcceptedProviderInputsExactlyOnce(GameTestHelper helper) {
        powered(helper, 4, f -> {
            var result = exactResult(f, CRAFTS);
            var plan = exactPlan(helper, result);
            submit(helper, f, plan);
            sequence(helper).thenWaitUntil(() -> assertDispatched(helper, f)).thenExecute(() -> {
                f.assembly.getCatalystInventory().clear();
                var id = f.host().ecoDispatch$jobId();
                f.logic().cancel();
                var saved = new CompoundTag();
                f.buffer().save(saved, helper.getLevel().registryAccess());
                f.buffer().load(saved, helper.getLevel().registryAccess());
                cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle.cancelAndRecover(helper.getLevel(), id);
                f.buffer().serverTick();
                f.buffer().serverTick();
                helper.assertTrue(!f.cpu().isBusy() && f.logic().getWaitingFor(OUTPUT) == 0,
                        "Canceled CPU must clear its output waiting ledger");
            }).thenWaitUntil(() -> helper.assertTrue(f.stock.amount(INPUT) == CRAFTS && ownedInputs(f) == 0,
                    "Actual ECO lifecycle cancellation must recover all queued/active input ownership to ME storage"))
                    .thenIdle(5).thenExecute(() -> {
                        helper.assertTrue(f.stock.amount(INPUT) == CRAFTS && f.stock.amount(OUTPUT) == 0
                                        && !f.buffer().isProcessing() && f.buffer().queuedPatterns() == 0,
                                "Repeated cancel and buffer reload must conserve every input without duplicated refunds or products");
                        finish(helper, f, "cancel_conservation");
                    }).thenSucceed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 260)
    public static void rejectedEcoSubmissionRollsBackPhysicalStorage(GameTestHelper helper) {
        powered(helper, 5, f -> {
            var result = result(f, CRAFTS);
            ECOPlanningResultRegistry.register(result.plan(), result);
            f.stock.values.set(INPUT, CRAFTS - 1);
            var submitted = f.service().submitJob(result.plan(), null, f.cpu(), false, new MachineSource(f.core()));
            helper.assertTrue(!submitted.successful() && !f.cpu().isBusy()
                            && f.stock.amount(INPUT) == CRAFTS - 1 && ownedInputs(f) == 0
                            && f.logic().getWaitingFor(OUTPUT) == 0 && f.stock.amount(OUTPUT) == 0,
                    "AE2 submission shortage must roll back actual extraction without creating a CPU task, waiting credit or provider ownership: " + submitted);
            finish(helper, f, "submission_reject_conservation");
            helper.succeed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 260)
    public static void staleRealFastPathPreparationRejectsWithoutMaterialLoss(GameTestHelper helper) {
        powered(helper, 6, f -> {
            var inventory = new ListCraftingInventory(ignored -> {});
            inventory.insert(INPUT, CRAFTS, Actionable.MODULATE);
            var unit = new KeyCounter(); unit.add(INPUT, 1);
            var out = new KeyCounter(); out.add(OUTPUT, 2);
            var prepared = ECOFastPathFacade.prepare(f.assembly.getLogic(), f.pattern,
                    new KeyCounter[]{unit}, out, new KeyCounter(), inventory, CRAFTS, 0,
                    f.core().getMainNode().getGrid().getEnergyService(), helper.getLevel(), UUID.randomUUID());
            helper.assertTrue(prepared != null && prepared.craftCount() == CRAFTS,
                    "Real ECO planner must prepare 1026 copies against the mixin-backed Matter provider");
            f.assembly.getCatalystInventory().clear();
            int[] committed = {0}; int[] refunded = {0};
            boolean accepted = prepared.submit(amount -> new ECOFastPathFacade.Reservation() {
                @Override public void commit() { committed[0]++; }
                @Override public void refund() { refunded[0]++; }
            });
            helper.assertTrue(!accepted && committed[0] == 0 && refunded[0] == 1
                            && inventory.list.get(INPUT) == CRAFTS && ownedInputs(f) == 0
                            && f.logic().getWaitingFor(OUTPUT) == 0 && !f.cpu().isBusy(),
                    "Catalyst removed after prepare must reject real ECO dispatch and refund its material/energy leases exactly once");
            finish(helper, f, "stale_prepare_reject_conservation");
            helper.succeed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void phasedEcoPlanWaitsForRealIntermediateBeforeSecondDispatch(GameTestHelper helper) {
        powered(helper, 7, f -> {
            var finalKey = AEItemKey.of(Items.GOLD_INGOT);
            var firstDefinition = f.pattern.getDefinition();
            var secondDefinition = installRecipe(f, "phase_tail", 1, List.of(
                    new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.EMERALD), 2)),
                    List.of(new ItemStack(Items.GOLD_INGOT)), 8).getDefinition();
            sequence(helper).thenIdle(3).thenWaitUntil(() -> {
                publishedPattern(f, OUTPUT, firstDefinition);
                publishedPattern(f, finalKey, secondDefinition);
            }).thenExecute(() -> {
                f.pattern = publishedPattern(f, OUTPUT, firstDefinition);
                var second = publishedPattern(f, finalKey, secondDefinition);
                var used = new KeyCounter(); used.add(INPUT, CRAFTS);
                var plan = new CraftingPlan(new GenericStack(finalKey, CRAFTS), 100, false, false,
                        used, new KeyCounter(), new KeyCounter(), Map.of(f.pattern, CRAFTS, second, CRAFTS));
                var components = List.of(dag(0, f.pattern, Map.of(OUTPUT, PRODUCTS)),
                        dag(1, second, Map.of(finalKey, CRAFTS)));
                var provenance = new MaterialProvenance();
                allocate(provenance, MaterialDemand.input(f.pattern, 0, INPUT, PlannerAmount.of(CRAFTS)),
                        MaterialSource.Stock.INSTANCE);
                allocate(provenance, MaterialDemand.input(second, 0, OUTPUT, PlannerAmount.of(PRODUCTS)),
                        new MaterialSource.PatternOutput(f.pattern, true));
                allocate(provenance, MaterialDemand.goal(finalKey, PlannerAmount.of(CRAFTS)),
                        new MaterialSource.PatternOutput(second, true));
                var result = planningResult(helper, PlanningStatus.SUCCESS, plan, components, provenance);
                helper.assertTrue(result.executionPlan() != null && result.executionPlan().mode() == ExecutionMode.PHASED_DAG
                                && result.executionPlan().phases().size() == 2
                                && result.executionPlan().phases().get(1).dependencies().equals(List.of(0)),
                        "Real ECO plan builder must derive two phases and the material dependency");
                ECOPlanningResultRegistry.register(plan, result);
                submit(helper, f, plan);
                helper.assertTrue(execution(f).plan().mode() == ExecutionMode.PHASED_DAG
                                && execution(f).plan().phases().size() == 2,
                        "CraftingService admission must retain original ECO phase metadata");
                sequence(helper).thenWaitUntil(() -> helper.assertTrue(f.logic().getWaitingFor(OUTPUT) == PRODUCTS,
                        "First phase must really dispatch")).thenExecute(() -> {
                    helper.assertTrue(f.host().ecoDispatch$remaining(f.pattern).signum() == 0
                                    && f.host().ecoDispatch$remaining(second).equals(BigInteger.valueOf(CRAFTS))
                                    && f.logic().getWaitingFor(finalKey) == 0 && f.stock.amount(OUTPUT) == 0,
                            "Second phase must not advance from projected intermediate products");
                }).thenWaitUntil(() -> helper.assertTrue(f.logic().getWaitingFor(finalKey) == CRAFTS,
                        "Second phase must dispatch only after receiving real intermediate output"))
                        .thenExecute(() -> helper.assertTrue(execution(f).isComplete()
                                        && f.access().ecoOmni$tasks().isEmpty() && f.logic().getWaitingFor(OUTPUT) == 0,
                                "Both real phase ledgers must advance exactly once and consume the intermediate waiting credit"))
                        .thenWaitUntil(() -> helper.assertTrue(!f.cpu().isBusy() && f.stock.amount(finalKey) == CRAFTS,
                                "Phased order must finish with actual final products"))
                        .thenExecute(() -> {
                            helper.assertTrue(f.stock.amount(INPUT) == 0 && f.stock.amount(OUTPUT) == 0
                                            && ownedInputs(f) == 0 && f.buffer().contents(true).isEmpty(),
                                    "Phase chain must conserve all raw and intermediate materials");
                            finish(helper, f, "phased_real_intermediate");
                        }).thenSucceed();
            });
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void orderedCycleEcoPlanRetainsSeedAndCompactRepeatProgress(GameTestHelper helper) {
        powered(helper, 8, f -> {
            var finalKey = AEItemKey.of(Items.GOLD_INGOT);
            var firstDefinition = installRecipe(f, "cycle_forward", 0, List.of(
                    new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 1)),
                    List.of(new ItemStack(Items.EMERALD, 3)), 2).getDefinition();
            // AE2 advertises only primary outputs; the goal must lead the return pattern.
            var secondDefinition = installRecipe(f, "cycle_return", 1, List.of(
                    new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.EMERALD), 3)),
                    List.of(new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND)), 2).getDefinition();
            f.stock.values.set(INPUT, 1);
            sequence(helper).thenIdle(3).thenWaitUntil(() -> {
                publishedPattern(f, OUTPUT, firstDefinition);
                publishedPattern(f, finalKey, secondDefinition);
            }).thenExecute(() -> {
                var first = publishedPattern(f, OUTPUT, firstDefinition);
                var second = publishedPattern(f, finalKey, secondDefinition);
                f.pattern = first;
                final cn.dancingsnow.neoecoae.crafting.planner.compile.CompiledNetwork network;
                try { network = new CraftingNetworkCompiler().compile(f.service(), finalKey, ECOCancellation.NONE); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                var a = network.producersOf(OUTPUT).stream()
                        .filter(p -> p.details().getDefinition().equals(firstDefinition)).findFirst()
                        .orElseThrow(() -> new GameTestAssertException("Real ECO compiler must include the published cycle forward pattern"));
                var b = network.producersOf(finalKey).stream()
                        .filter(p -> p.details().getDefinition().equals(secondDefinition)).findFirst()
                        .orElseThrow(() -> new GameTestAssertException("Real ECO compiler must include the published cycle return pattern"));
                var times = Map.of(first, 3L, second, 3L);
                var solved = new CycleSolveResult(CycleSolveStatus.SUCCESS, times, Map.of(), Map.of(INPUT, 1L), Map.of(),
                        Map.of(finalKey, 3L, INPUT, 1L), Map.of(finalKey, 3L), List.of(),
                        List.of(new PatternRun(a, 1), new PatternRun(b, 1, 2, 3)), List.of(), CycleSolveMetrics.NONE);
                var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.CYCLIC,
                        ComponentPlanningResult.Status.PLANNED, Map.of(finalKey, 3L), Set.of(first, second),
                        Set.of(first, second), CyclePlanningStatus.SOLVED, CycleExternalDemandStatus.SOLVED, Map.of(),
                        "GameTest compact seed cycle", solved, CycleExecutionDisposition.ORDERED_EXECUTION, Map.of());
                var used = new KeyCounter(); used.add(INPUT, 1);
                var plan = new CraftingPlan(new GenericStack(finalKey, 3), 100, false, false,
                        used, new KeyCounter(), new KeyCounter(), times);
                var provenance = new MaterialProvenance();
                allocate(provenance, MaterialDemand.boundary(0, INPUT, PlannerAmount.of(1)),
                        MaterialSource.Stock.INSTANCE);
                allocate(provenance, MaterialDemand.goal(finalKey, PlannerAmount.of(3)),
                        new MaterialSource.CycleOutput(0));
                var result = planningResult(helper, PlanningStatus.SUCCESS, plan, List.of(component), provenance);
                helper.assertTrue(result.executionContract().mode() == ExecutionMode.ORDERED_CYCLE,
                        "Real ECO result must build an executable ordered cycle contract");
                ECOPlanningResultRegistry.register(plan, result);
                submit(helper, f, plan);
                var phase = execution(f).plan().phases().getFirst();
                helper.assertTrue(phase.type() == ECOExecutionSchedule.Type.CYCLE
                                && phase.initialSeed().equals(Map.of(INPUT, 1L))
                                && phase.steps().size() == 2 && phase.steps().getLast().repeatWidth() == 2
                                && phase.steps().getLast().repetitions() == 3,
                        "Original seed and compact repeat-group metadata must reach actual CPU admission");
                sequence(helper).thenWaitUntil(() -> helper.assertTrue(f.logic().getWaitingFor(OUTPUT) == 3,
                        "First real cycle firing must start using the single physical seed"))
                        .thenExecute(() -> helper.assertTrue(f.host().ecoDispatch$remaining(first).equals(BigInteger.TWO)
                                        && f.host().ecoDispatch$remaining(second).equals(BigInteger.valueOf(3)),
                                "Acceptance must advance only the first ordered task; the cycle return remains pending"))
                        .thenWaitUntil(() -> helper.assertTrue(!f.cpu().isBusy() && f.stock.amount(finalKey) == 3,
                                "All three actual cycle repetitions must complete"))
                        .thenIdle(3).thenExecute(() -> {
                            helper.assertTrue(f.stock.amount(INPUT) == 1 && f.stock.amount(OUTPUT) == 0
                                            && f.buffer().contents(false).isEmpty() && f.buffer().contents(true).isEmpty(),
                                    "Ordered cycle must return its one physical seed and emit exactly three net products");
                            finish(helper, f, "ordered_cycle_seed_repeat_progress");
                        }).thenSucceed();
            });
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 320)
    public static void realEcoOutputRouterSimulationAndWrongUuidDoNotConsumeWaiting(GameTestHelper helper) {
        powered(helper, 9, f -> {
            var result = exactResult(f, CRAFTS);
            submit(helper, f, exactPlan(helper, result));
            sequence(helper).thenWaitUntil(() -> assertDispatched(helper, f)).thenExecute(() -> {
                helper.assertTrue(f.service() instanceof ECOCraftingOutputRouter,
                        "ECO CraftingService output-router mixin must actually be applied");
                var router = (ECOCraftingOutputRouter) f.service();
                var id = f.host().ecoDispatch$jobId();
                helper.assertTrue(router.neoecoae$insertExactIntoCpuForJob(UUID.randomUUID(), OUTPUT,
                                BigInteger.valueOf(PRODUCTS), Actionable.MODULATE).signum() == 0
                                && router.neoecoae$insertExactIntoCpuForJob(id, INPUT,
                                BigInteger.ONE, Actionable.SIMULATE).equals(BigInteger.ONE),
                        "Foreign UUID must reject; matching unexpected byproducts must simulate full durable ownership");
                helper.assertTrue(router.neoecoae$insertExactIntoCpuForJob(id, OUTPUT,
                                BigInteger.TEN.pow(30), Actionable.SIMULATE).equals(BigInteger.TEN.pow(30))
                                && f.logic().getWaitingFor(OUTPUT) == PRODUCTS && f.stock.amount(OUTPUT) == 0,
                        "Actual ECO output router must simulate complete ownership without taking products or credit");
            }).thenWaitUntil(() -> assertComplete(helper, f)).thenExecute(() -> {
                helper.assertTrue(f.stock.amount(OUTPUT) == PRODUCTS && f.stock.amount(INPUT) == 0,
                        "Real provider outputs must still settle the unchanged simulated waiting exactly once");
                finish(helper, f, "router_uuid_simulation_conservation");
            }).thenSucceed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 560)
    public static void nativeEcoWorkstationProviderDispatchesAndRoutesRealOutputs(GameTestHelper helper) {
        powered(helper, 10, f -> {
            // The real ECO 3 x 2 x 2 controller calculator verifies these twelve blocks.
            var pos = f.cpuPos.east(12);
            var casing = ecoBlock(helper, "large_integrated_working_station_casing");
            for (var p : BlockPos.betweenClosed(pos.offset(-1, -1, 0), pos.offset(1, 0, 1)))
                reset(helper, p, casing.defaultBlockState());
            reset(helper, pos, ecoBlock(helper, "integrated_working_station").defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            reset(helper, pos.below().south(), ecoBlock(helper, "large_integrated_working_station_interface").defaultBlockState());
            reset(helper, pos.below().south().west(), ecoBlock(helper, "large_integrated_working_station_output_hatch").defaultBlockState());
            reset(helper, pos.below().south().east(), ecoBlock(helper, "large_integrated_working_station_input_hatch").defaultBlockState());
            helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof ECOLargeIntegratedWorkingStationBlockEntity,
                    "ECO registry must create its actual large workstation controller");
            var machine = (ECOLargeIntegratedWorkingStationBlockEntity) helper.getLevel().getBlockEntity(pos);
            var providerEntity = (ECOLargeIntegratedWorkingStationInterfaceBlockEntity)
                    helper.getLevel().getBlockEntity(pos.below().south());
            f.assembly.getLogic().getPatternInv().setItemDirect(0, ItemStack.EMPTY);
            f.assembly.getLogic().updatePatterns();
            var recipeId = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "gametest/eco_native_cpu");
            var recipe = new LargeIntegratedWorkingStationRecipe(new IntegratedWorkingStationRecipe(
                    List.of(SizedIngredient.of(Items.DIAMOND, 1)),
                    new SizedFluidIngredient(FluidIngredient.empty(), 1),
                    new ItemStack(Items.EMERALD, 2), FluidStack.EMPTY, 1));
            var recipes = new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());
            recipes.add(new RecipeHolder<>(recipeId, recipe));
            helper.getLevel().getRecipeManager().replaceRecipes(recipes);
            later(helper, 5, () -> {
                machine.rebuildMultiblock();
                GridHelper.createConnection(machine.getMainNode().getNode(), f.core().getMainNode().getNode());
                GridHelper.createConnection(providerEntity.getMainNode().getNode(), f.core().getMainNode().getNode());
                var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(INPUT, 1)),
                        List.of(new GenericStack(OUTPUT, 2)));
                providerEntity.getWorkstationProvider().getPatternInv().setItemDirect(0, encoded);
                providerEntity.getWorkstationProvider().updatePatterns();
                ICraftingProvider.requestUpdate(providerEntity.getMainNode());
            });
            sequence(helper).thenWaitUntil(() -> helper.assertTrue(machine.isFormed()
                                    && machine.canAcceptPattern()
                                    && !providerEntity.getWorkstationProvider().getAvailablePatterns().isEmpty(),
                            "Native ECO workstation must physically form, power up and advertise its real recipe"))
                    .thenIdle(3).thenWaitUntil(() -> publishedPattern(f, OUTPUT,
                            providerEntity.getWorkstationProvider().getAvailablePatterns().getFirst().getDefinition()))
                    .thenExecute(() -> {
                        var provider = providerEntity.getWorkstationProvider();
                        helper.assertTrue(ECOParallelCraftingProviders.find(provider) == provider
                                        && provider.eco$getAvailableParallelSlots() >= 16,
                                "Native ECO parallel provider must be discoverable with real available capacity");
                        f.pattern = publishedPattern(f, OUTPUT, provider.getAvailablePatterns().getFirst().getDefinition());
                        var result = result(f, 16);
                        ECOPlanningResultRegistry.register(result.plan(), result);
                        submit(helper, f, result.plan());
                        // A formed workstation completes in one grid tick. Await its real
                        // CPU allowance rather than requiring admission in the submission tick.
                    }).thenWaitUntil(() -> {
                        f.logic().tickCraftingLogic(f.core().getMainNode().getGrid().getEnergyService(), f.service());
                        helper.assertTrue(f.logic().getWaitingFor(OUTPUT) == 32
                                        || !f.cpu().isBusy() && f.stock.amount(OUTPUT) == 32,
                                "Actual Omni dispatcher must accept the native batch under its real tick budget");
                        if (f.logic().getWaitingFor(OUTPUT) == 32) {
                            helper.assertTrue(f.host().ecoDispatch$remaining(f.pattern).signum() == 0
                                            && execution(f).isComplete()
                                            && machine.getCurrentBatchInputs().stream().filter(s -> s.what().equals(INPUT))
                                                    .mapToLong(GenericStack::amount).sum() == 16
                                            && f.stock.amount(INPUT) == CRAFTS - 16 && f.stock.amount(OUTPUT) == 0
                                            && f.buffer().contents(false).isEmpty(),
                                    "Native ECO machine must own accepted materials until physical production");
                        }
                    }).thenWaitUntil(() -> {
                        outputDiagnostic(f, "native_existing");
                        helper.assertTrue(!f.cpu().isBusy() && f.stock.amount(OUTPUT) == 32,
                            "Native ECO workstation must execute its real batch and route products to the UUID waiting ledger");
                    })
                    .thenExecute(() -> {
                        helper.assertTrue(f.logic().getWaitingFor(OUTPUT) == 0
                                        && f.stock.amount(INPUT) == CRAFTS - 16
                                        && machine.getCurrentBatchInputs().isEmpty(),
                                "Native ECO provider completion must conserve inputs and clear both machine ownership and CPU waiting");
                        machine.clearContent();
                        finish(helper, f, "native_eco_provider_prepare_dispatch_outputs");
                    }).thenSucceed();
        });
    }

    private static net.minecraft.world.level.block.Block ecoBlock(GameTestHelper helper, String name) {
        var id = ResourceLocation.fromNamespaceAndPath("neoecoae", name);
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "Real ECO block must be registered: " + id);
        return BuiltInRegistries.BLOCK.get(id);
    }

    private static ComponentPlanningResult dag(int id, IPatternDetails pattern, Map<AEKey, Long> requiredOutputs) {
        return new ComponentPlanningResult(id, ComponentPlanningResult.Type.ACYCLIC,
                ComponentPlanningResult.Status.PLANNED, requiredOutputs, Set.of(pattern), Set.of(pattern), null,
                CycleExternalDemandStatus.SOLVED, Map.of(), "GameTest real phase", null,
                CycleExecutionDisposition.NOT_REQUIRED, Map.of());
    }

    static IPatternDetails installRecipe(Fixture f, String name, int slot,
            List<MatterFabricationRecipe.CountedIngredient> inputs, List<ItemStack> outputs, int duration) {
        var recipeId = ResourceLocation.fromNamespaceAndPath("molecularmanipulator",
                "gametest/eco_cpu_" + f.cpuPos.getX() + "_" + name);
        var researchId = recipeId.withSuffix("_research");
        var recipe = new MatterFabricationRecipe(inputs,
                List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND_BLOCK), 1)),
                outputs, FluidStack.EMPTY, FluidStack.EMPTY, List.of(), List.of(), duration, 2, false);
        var research = new MatterResearchRecipe("ECO CPU phase fixture", List.of(), List.of(), 1, 0,
                List.of(recipeId), List.of(), 0, 1,
                List.of(new ResearchDepth(1, 4096, 1, 0, Optional.empty())));
        var recipes = new ArrayList<>(f.helper.getLevel().getRecipeManager().getRecipes());
        recipes.add(new RecipeHolder<>(recipeId, recipe)); recipes.add(new RecipeHolder<>(researchId, research));
        f.helper.getLevel().getRecipeManager().replaceRecipes(recipes);
        MatterResearchApi.setCompletionCount(f.controller, researchId.toString(), 1);
        f.assembly.getLogic().getPatternInv().setItemDirect(slot, MatterFabricationPatternEncoding.encode(recipe,
                inputs.stream().map(input -> input.ingredient().getItems()[0].copyWithCount(input.count())).toList()));
        f.assembly.getLogic().updatePatterns();
        ICraftingProvider.requestUpdate(f.assembly.getMainNode());
        return f.assembly.getLogic().getAvailablePatterns().stream().filter(p -> p.getDefinition().equals(
                AEItemKey.of(f.assembly.getLogic().getPatternInv().getStackInSlot(slot)))).findFirst().orElseThrow();
    }

    /** Resolve only actual grid advertisements after provider pattern updates have settled. */
    static IPatternDetails publishedPattern(Fixture f, AEKey output, AEItemKey definition) {
        f.helper.assertTrue(f.cpu() != null && f.cpu().isActive()
                        && f.core().getMainNode().isActive() && f.storageNode.getNode().isActive(),
                "CPU cluster and mounted inventory must be active after grid topology/pattern updates");
        return f.service().getCraftingFor(output).stream()
                .filter(pattern -> pattern.getDefinition().equals(definition)).findFirst()
                .orElseThrow(() -> new GameTestAssertException(
                        "Real AE2 grid must advertise pattern " + definition + " for " + output));
    }

    private static void complete(GameTestHelper helper, Fixture f, String marker) {
        sequence(helper).thenWaitUntil(() -> assertDispatched(helper, f)).thenWaitUntil(() -> assertComplete(helper, f))
                .thenExecute(() -> {
                    helper.assertTrue(f.stock.amount(INPUT) == 0 && f.stock.amount(OUTPUT) == PRODUCTS
                                    && ownedInputs(f) == 0 && f.buffer().contents(true).isEmpty()
                                    && f.assembly.getCatalystInventory().getStackInSlot(0).getCount() == 1,
                            "Real provider must consume exactly 1026 inputs, deliver 2052 products, and retain its catalyst once");
                    finish(helper, f, marker);
                }).thenSucceed();
    }

    private static void assertDispatched(GameTestHelper helper, Fixture f) {
        helper.assertTrue(f.logic().getWaitingFor(OUTPUT) == PRODUCTS, "CPU must expose the complete native batch waiting credit");
        helper.assertTrue(f.host().ecoDispatch$remaining(f.pattern).signum() == 0
                        && execution(f).remainingByTask().values().stream().allMatch(n -> n.signum() == 0)
                        && f.access().ecoOmni$tasks().isEmpty() && ownedInputs(f) == CRAFTS
                        && f.access().ecoOmni$physicalAmount(INPUT).signum() == 0
                        && f.stock.amount(INPUT) == 0 && f.stock.amount(OUTPUT) == 0,
                "ECO acceptance must advance AE2/exact/phase tasks once, own every real input and await physical production");
        helper.assertTrue(!((EcoCpuDispatchAccess) f.logic()).ecoDispatch$dispatcher().suspended(),
                "Native FastPath CPU transaction must finish with a clean journal");
    }

    private static void assertComplete(GameTestHelper helper, Fixture f) {
        if (helper.getTick() % 100 == 0) {
            var saved = new CompoundTag();
            f.logic().writeToNBT(saved, helper.getLevel().registryAccess());
            System.out.println("ECO_CPU_COMPLETION_DIAG tick=" + helper.getTick() + " pos=" + f.cpuPos
                    + " busy=" + f.cpu().isBusy() + " stock=" + f.stock.amount(OUTPUT)
                    + " waiting=" + f.logic().getWaitingFor(OUTPUT) + " ownedInputs=" + ownedInputs(f)
                    + " outputs=" + f.buffer().contents(true) + " cpu=" + saved);
        }
        helper.assertTrue(!f.cpu().isBusy() && f.stock.amount(OUTPUT) == PRODUCTS,
                "Real AE2 CPU must finish only after actual well outputs settle its waiting ledger: busy="
                        + f.cpu().isBusy() + " stock=" + f.stock.amount(OUTPUT) + " waiting=" + f.logic().getWaitingFor(OUTPUT)
                        + " ownedInputs=" + ownedInputs(f) + " outputs=" + f.buffer().contents(true)
                        + " remaining=" + (f.access().ecoOmni$exactState() == null ? "none"
                                : f.access().ecoOmni$exactState().outputProgress().remaining()));
        helper.assertTrue(f.logic().getWaitingFor(OUTPUT) == 0, "Completed CPU must clear waiting exactly");
    }

    private static void assertMetadata(GameTestHelper helper, Fixture f, UUID planningId, BigInteger remaining) {
        helper.assertTrue(f.host().ecoDispatch$enabled() && f.access().ecoOmni$managed()
                        && f.access().ecoOmni$exactState() != null && execution(f) != null
                        && execution(f).remainingByPattern().get(f.pattern).equals(remaining)
                        && f.host().ecoDispatch$remaining(f.pattern).equals(remaining),
                "Submitting original ECO plan through CraftingService must attach actual exact task and ECO execution state");
        if (planningId != null) helper.assertTrue(planningId.equals(execution(f).planningId()),
                "Original ECO planning UUID must survive admission without plan rewriting");
    }

    static ECOPlanningResult result(Fixture f, long crafts) {
        return result(f, crafts, PlanningStatus.SUCCESS);
    }

    private static ECOPlanningResult exactResult(Fixture f, long crafts) {
        // ECO's exact adapter accepts the unrepresentable status, even when only bytes exceed long.
        // Keep the physical task/material counts small enough for this real provider fixture.
        var result = result(f, crafts, PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE);
        result.setTheoreticalBytes(PlannerAmount.of(EXACT_BYTES));
        return result;
    }

    private static ECOPlanningResult result(Fixture f, long crafts, PlanningStatus status) {
        var used = new KeyCounter(); used.add(INPUT, crafts);
        long products = Math.multiplyExact(crafts, 2);
        long projectedBytes = status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE ? Long.MAX_VALUE : 100;
        var plan = new CraftingPlan(new GenericStack(OUTPUT, products), projectedBytes, false, false,
                used, new KeyCounter(), new KeyCounter(), Map.of(f.pattern, crafts));
        var provenance = new MaterialProvenance();
        allocate(provenance, MaterialDemand.input(f.pattern, 0, INPUT, PlannerAmount.of(crafts)),
                MaterialSource.Stock.INSTANCE);
        allocate(provenance, MaterialDemand.goal(OUTPUT, PlannerAmount.of(products)),
                new MaterialSource.PatternOutput(f.pattern, true));
        var result = planningResult(f.helper, status, plan,
                List.of(dag(0, f.pattern, Map.of(OUTPUT, products))), provenance);
        result.setFuzzyPlanningItemIds(FUZZY);
        return result;
    }

    static void allocate(MaterialProvenance provenance, MaterialDemand demand, MaterialSource source) {
        provenance.register(demand);
        provenance.allocate(demand, demand.key(), source, demand.amount());
    }

    private static Map<AEKey, PlannerAmount> exactAmounts(KeyCounter counter) {
        var amounts = new LinkedHashMap<AEKey, PlannerAmount>();
        for (var stack : counter) amounts.put(stack.getKey(), PlannerAmount.of(stack.getLongValue()));
        return Map.copyOf(amounts);
    }

    static ECOPlanningResult planningResult(GameTestHelper helper, PlanningStatus status, CraftingPlan plan,
            List<ComponentPlanningResult> components, MaterialProvenance provenance) {
        var attribution = provenance.freeze();
        attribution.requireComplete();
        var result = new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), components,
                components.stream().map(ComponentPlanningResult::componentId).toList(), 0, attribution);
        result.setTheoreticalBytes(PlannerAmount.of(plan.bytes()));
        result.setExactMaterials(exactAmounts(plan.usedItems()), exactAmounts(plan.emittedItems()),
                exactAmounts(plan.missingItems()));
        helper.assertTrue(components.stream().allMatch(c -> c.status() == ComponentPlanningResult.Status.PLANNED
                        && c.externalDemandStatus() == CycleExternalDemandStatus.SOLVED
                        && c.externalMissingItems().isEmpty()) && result.exactMissingItems().isEmpty(),
                "Fixture must describe fully planned components with no unresolved material demand");
        if (status == PlanningStatus.SUCCESS) {
            var contract = result.executionContract();
            helper.assertTrue(contract.executable() && contract.error() == null && result.executionPlanError() == null,
                    "Ordinary ECO fixture must build an executable contract: " + contract.error()
                            + "; " + result.executionPlanError());
            assertExecutionPlan(helper, plan, contract.executionPlan());
        }
        return result;
    }

    static ECOExactCraftingPlan exactPlan(GameTestHelper helper, ECOPlanningResult result) {
        helper.assertTrue(result.status() == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE
                        && result.theoreticalBytes().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                        && ECOBigOrderAdmission.allows(result, false),
                "Exact fixture must satisfy real ECO big-order admission without force or missing materials");
        var plan = new ECOExactCraftingPlan(result, false);
        assertExecutionPlan(helper, plan, plan.execution());
        var expectedTasks = new LinkedHashMap<IPatternDetails, BigInteger>();
        result.exactPatternTimes().forEach((pattern, count) -> expectedTasks.put(pattern, count.toBigInteger()));
        helper.assertTrue(plan.exactTasks().equals(expectedTasks)
                        && plan.theoreticalBytes().equals(result.theoreticalBytes())
                        && exactAmounts(plan.usedItems()).equals(result.exactUsedItems())
                        && plan.deferredStock().isEmpty() && plan.deferredEmitted().isEmpty(),
                "Real ECO exact adapter must retain every task, theoretical byte and physical input reservation");
        return plan;
    }

    private static void assertExecutionPlan(GameTestHelper helper, ICraftingPlan plan, ECOExecutionPlan execution) {
        helper.assertTrue(execution != null && execution.mode() != ExecutionMode.BLOCKED
                        && !execution.phases().isEmpty(),
                "Fixture must carry real executable ECO phases");
        var scheduled = new LinkedHashMap<IPatternDetails, Long>();
        for (var task : execution.tasks()) scheduled.merge(task.pattern(), task.totalCount(), Math::addExact);
        helper.assertTrue(scheduled.equals(plan.patternTimes())
                        && execution.phases().stream().flatMap(phase -> phase.taskIds().stream()).distinct().count()
                                == execution.tasks().size(),
                "Actual ECO execution phases must cover the complete submitted positive task vector");
    }

    private static void submit(GameTestHelper helper, Fixture f, ICraftingPlan plan) {
        var submitted = f.service().submitJob(plan, null, f.cpu(), false, new MachineSource(f.core()));
        helper.assertTrue(submitted.successful(), "Real Omni CPU must accept original ECO plan: " + submitted);
    }

    static EcoOmniPlanExecution execution(Fixture f) { return ((EcoOmniPlanCpu) f.logic()).ecoOmni$getExecution(); }
    private static long ownedInputs(Fixture f) {
        return f.buffer().contents(false).stream().filter(s -> s.what().equals(INPUT)).mapToLong(GenericStack::amount).sum();
    }
    private static void catalyst(Fixture f) { f.assembly.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK)); }
    static void finish(GameTestHelper helper, Fixture f, String marker) {
        f.storageNode.destroy();
        f.core().clearContent();
        f.buffer().clear();
        System.out.println("ECO_CPU_PASS " + marker);
    }

    static final class Fixture {
        final GameTestHelper helper;
        final BlockPos cpuPos;
        final MatterFabricationBlockEntity controller;
        final MatterFabricationPatternAssemblyBlockEntity assembly;
        final Stock stock = new Stock();
        IManagedGridNode storageNode;
        IPatternDetails pattern;
        Fixture(GameTestHelper helper, BlockPos cpuPos, MatterFabricationBlockEntity controller,
                MatterFabricationPatternAssemblyBlockEntity assembly) {
            this.helper = helper; this.cpuPos = cpuPos; this.controller = controller; this.assembly = assembly;
            stock.values.add(INPUT, CRAFTS);
        }
        OmniComputationCoreBlockEntity core() { return (OmniComputationCoreBlockEntity) helper.getLevel().getBlockEntity(cpuPos); }
        CraftingCPUCluster cpu() {
            return core().allCpus().stream().filter(CraftingCPUCluster::isBusy).findFirst().orElse(core().getCluster());
        }
        CraftingCpuLogic logic() { return cpu().craftingLogic; }
        EcoCpuDispatchHost host() { return (EcoCpuDispatchHost) logic(); }
        EcoOmniPlanCpuAccess access() { return (EcoOmniPlanCpuAccess) logic(); }
        MatterPatternBuffer buffer() { return assembly.getBuffer(); }
        CraftingService service() { return (CraftingService) core().getMainNode().getGrid().getCraftingService(); }
    }

    /** A finite ME inventory mounted on a real grid, like the ae2-compat stock fixture. */
    static final class Stock implements MEStorage {
        final KeyCounter values = new KeyCounter();
        long insertCapacity = Long.MAX_VALUE;
        long amount(AEKey key) { return values.get(key); }
        @Override public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            long taken = Math.min(Math.max(0, requested), values.get(key));
            if (mode == Actionable.MODULATE) values.add(key, -taken);
            return taken;
        }
        @Override public long insert(AEKey key, long offered, Actionable mode, IActionSource source) {
            long taken = Math.min(insertCapacity, Math.min(Math.max(0, offered), Long.MAX_VALUE - values.get(key)));
            if (mode == Actionable.MODULATE) { values.add(key, taken); insertCapacity -= taken; }
            return taken;
        }
        @Override public void getAvailableStacks(KeyCounter counter) { counter.addAll(values); }
        @Override public Component getDescription() { return Component.literal("ECO CPU finite fixture storage"); }
    }

    static void powered(GameTestHelper helper, int lane, Consumer<Fixture> action) {
        var level = helper.getLevel();
        var origin = new BlockPos(970_000 + lane * 96, 100, 960);
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            reset(helper, pos, MatterFabricationStructure.isController(part)
                    ? ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    : MatterFabricationStructure.partState(part.type()));
        }
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().getFirst());
        reset(helper, bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState());
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var cpuPos = origin.east(30);
        reset(helper, cpuPos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState());
        reset(helper, cpuPos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var f = new Fixture(helper, cpuPos, controller, assembly);
        later(helper, 5, () -> {
            controller.refreshStructure(); assembly.setControllerPos(origin);
            GridHelper.createConnection(controller.getMainNode().getNode(), f.core().getMainNode().getNode());
            f.storageNode = GridHelper.createManagedNode(f.stock, (owner, changed) -> {})
                    .setInWorldNode(false).setIdlePowerUsage(0)
                    .addService(IStorageProvider.class, mounts -> mounts.mount(f.stock));
            f.storageNode.create(level, cpuPos.above());
            GridHelper.createConnection(f.storageNode.getNode(), f.core().getMainNode().getNode());
            IStorageProvider.requestUpdate(f.storageNode);
        });
        sequence(helper).thenWaitUntil(() -> helper.assertTrue(assembly.isOperational() && f.core().isNetworkOnline(),
                "Actual Matter multiblock, nexus and finite ME inventory must form on a powered AE2 grid"))
                .thenExecute(() -> {
                    var recipeId = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "gametest/eco_cpu_" + lane);
                    var researchId = recipeId.withSuffix("_research");
                    var recipe = new MatterFabricationRecipe(
                            List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 1)),
                            List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND_BLOCK), 1)),
                            List.of(new ItemStack(Items.EMERALD, 2)), FluidStack.EMPTY, FluidStack.EMPTY,
                            List.of(), List.of(), 80, 2, false);
                    var research = new MatterResearchRecipe("ECO CPU fixture", List.of(), List.of(), 1, 0,
                            List.of(recipeId), List.of(), 0, 1,
                            List.of(new ResearchDepth(1, 4096, 1, 0, Optional.empty())));
                    var recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
                    recipes.add(new RecipeHolder<>(recipeId, recipe)); recipes.add(new RecipeHolder<>(researchId, research));
                    level.getRecipeManager().replaceRecipes(recipes);
                    MatterResearchApi.setCompletionCount(controller, researchId.toString(), 1);
                    catalyst(f);
                    assembly.getLogic().getPatternInv().setItemDirect(0,
                            MatterFabricationPatternEncoding.encode(recipe, List.of(new ItemStack(Items.DIAMOND))));
                    assembly.getLogic().updatePatterns();
                    f.pattern = assembly.getLogic().getAvailablePatterns().getFirst();
                    helper.assertTrue(ECOFastPathFacade.supports(assembly.getLogic())
                                    && ECOFastPathFacade.resolveProvider(assembly.getLogic()) != null,
                            "Real ECO must resolve the installed provider mixin");
                    ICraftingProvider.requestUpdate(assembly.getMainNode());
                }).thenIdle(3).thenWaitUntil(() -> publishedPattern(f, OUTPUT, f.pattern.getDefinition()))
                .thenExecute(() -> {
                    f.pattern = publishedPattern(f, OUTPUT, f.pattern.getDefinition());
                    action.accept(f);
                });
    }

    static void outputDiagnostic(Fixture f, String name) {
        if (f.helper.getTick() % 100 != 0) return;
        var saved = new CompoundTag();
        f.logic().writeToNBT(saved, f.helper.getLevel().registryAccess());
        System.out.println("ECO_OUTPUT_DIAG " + name + " tick=" + f.helper.getTick()
                + " stock=" + f.stock.values + " busy=" + f.cpu().isBusy() + " cpu=" + saved);
    }

    static CheckedSequence sequence(GameTestHelper helper) {
        return new CheckedSequence(helper, helper.startSequence());
    }

    private static void later(GameTestHelper helper, long delay, Runnable task) {
        helper.runAfterDelay(delay, checked(helper, task));
    }

    /** Native sequence execution catches only GameTestAssertException. Preserve other failures as test reports. */
    private static Runnable checked(GameTestHelper helper, Runnable task) {
        GameTestAssertException[] unexpected = {null};
        return () -> {
            if (unexpected[0] != null) throw unexpected[0];
            try {
                task.run();
            } catch (GameTestAssertException assertion) {
                throw assertion;
            } catch (Throwable failure) {
                try {
                    helper.fail("Unexpected ECO CPU callback failure at tick " + helper.getTick() + ": " + failure);
                } catch (GameTestAssertException report) {
                    report.initCause(failure);
                    unexpected[0] = report;
                    // thenWaitUntil retries assertions. Also fail a separate sequence on the next tick
                    // so an unexpected exception cannot turn into a timeout or repeat a mutation.
                    helper.startSequence().thenFail(() -> report);
                    throw report;
                }
            }
        };
    }

    record CheckedSequence(GameTestHelper helper, GameTestSequence delegate) {
        CheckedSequence thenWaitUntil(Runnable task) {
            delegate.thenWaitUntil(checked(helper, task));
            return this;
        }
        CheckedSequence thenExecute(Runnable task) {
            delegate.thenExecute(checked(helper, task));
            return this;
        }
        CheckedSequence thenIdle(int ticks) {
            delegate.thenIdle(ticks);
            return this;
        }
        void thenSucceed() { delegate.thenSucceed(); }
    }

    static void reset(GameTestHelper helper, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        var level = helper.getLevel();
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true); level.getChunkAt(pos);
        if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity old) old.clearContent();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, state, 3);
    }
}
