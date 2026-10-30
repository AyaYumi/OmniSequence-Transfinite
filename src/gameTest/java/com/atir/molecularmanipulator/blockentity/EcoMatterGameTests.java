package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.helpers.MachineSource;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOExactBatchPlanner;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
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
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Real wells, decoded AE2 processing patterns and ECO 20.5.0 material leases; no API stubs. */
@net.minecraftforge.gametest.GameTestHolder("molecularmanipulator")
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class EcoMatterGameTests {
    private static final AEKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    private static final AEKey DIAMOND = AEItemKey.of(Items.DIAMOND);
    private static final AEKey WATER = AEFluidKey.of(Fluids.WATER);
    private static final AEKey OUTPUT = AEItemKey.of(Items.EMERALD);
    private static final Map<AEKey, Long> UNIT = Map.of(MATERIAL, 3_000_000_000L, DIAMOND, 1L, WATER, 1000L);
    private static final long PARALLEL = 4096;
    private static final int DURATION = 4;

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void longBatchOwnsBillionsBeforeExecutingIntrinsicParallel(GameTestHelper helper) {
        powered(helper, 0, fixture -> {
            var wrong = new LinkedHashMap<>(MatterPatternBuffer.scaled(UNIT, 1026));
            wrong.remove(DIAMOND); wrong.put(AEItemKey.of(Items.AMETHYST_SHARD), 1026L);
            var context = context(helper, fixture, UUID.randomUUID());
            var mismatched = fixture.provider().eco$prepareFastPath(context);
            helper.assertTrue(mismatched != null && !mismatched.push(new ECOFastPathDispatchProvider.Batch(1026,
                            stacks(wrong), List.of(new GenericStack(OUTPUT, 2052)), List.of()))
                            && !fixture.buffer().hasContents(),
                    "A recipe-valid substitution must still match the specific input vector reserved by this preparation");
            long count = 3_000_000_000L;
            var inventory = inventory(count);
            var prepared = prepare(helper, fixture, inventory, count, UUID.randomUUID(), false);
            submit(helper, prepared);
            helper.assertTrue(inventory.list.isEmpty() && fixture.buffer().queuedPatterns() == 1,
                    "One ECO transaction must transfer all three billion crafts, without a 1024 dispatch cap");
            helper.assertTrue(inputs(fixture).equals(MatterPatternBuffer.scaled(UNIT, count))
                            && fixture.buffer().contents(true).isEmpty(),
                    "The provider must durably hold every material before producing outputs");
            roundTrip(helper, fixture);
            var first = fixture.buffer().process(fixture.controller());
            var active = saved(helper, fixture).getCompound("active");
            helper.assertTrue(first.progress() == 1 && first.duration() == DURATION
                            && first.power() == 2 * PARALLEL && active.getLong("crafts") == PARALLEL,
                    "Execution must use the well's real 4096 parallel profile and full per-tick recipe power");
            helper.assertTrue(inputs(fixture).equals(MatterPatternBuffer.scaled(UNIT, count)),
                    "Starting physical work must preserve total queued plus active input ownership");
            for (int tick = 1; tick < DURATION; tick++) fixture.buffer().process(fixture.controller());
            helper.assertTrue(outputs(fixture).equals(Map.of(OUTPUT, 2 * PARALLEL))
                            && inputs(fixture).equals(MatterPatternBuffer.scaled(UNIT, count - PARALLEL)),
                    "One real duration consumes precisely one intrinsic parallel batch and emits its complete outputs");
            catalystUnchanged(helper, fixture);
            pass(helper, fixture);
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void exactLeaseRollsBackMissingCatalystAndPersistsPausedWork(GameTestHelper helper) {
        powered(helper, 1, fixture -> {
            var id = UUID.randomUUID();
            var context = context(helper, fixture, id);
            fixture.assembly().getCatalystInventory().clear();
            helper.assertTrue(fixture.provider().eco$prepareFastPath(context) == null
                            && fixture.provider().eco$prepareExactFastPath(context, BigInteger.valueOf(1026)) == null,
                    "Missing catalysts must reject both native preparations without taking inputs");
            catalyst(fixture);
            var inventory = exactInventory(BigInteger.valueOf(1026));
            var original = inventory.snapshot();
            var stale = ECOExactBatchPlanner.prepare(fixture.provider(), context, inventory, BigInteger.valueOf(1026), Map.of());
            helper.assertTrue(stale != null, "Real ECO exact planner must prepare all 1026 crafts");
            fixture.assembly().getCatalystInventory().clear();
            var rejected = new Lease();
            helper.assertTrue(!stale.submit(rejected) && rejected.refunds == 1 && rejected.commits == 0
                            && inventory.snapshot().equals(original) && !fixture.buffer().hasContents(),
                    "A catalyst removed after preparation must roll back the actual ECO exact material lease once");
            catalyst(fixture);
            var accepted = ECOExactBatchPlanner.prepare(fixture.provider(), context, inventory, BigInteger.valueOf(1026), Map.of());
            var lease = new Lease();
            helper.assertTrue(accepted != null && accepted.submit(lease) && lease.commits == 1
                            && inventory.snapshot().isEmpty(), "A fresh exact preparation must transfer the full reservation");
            var first = fixture.buffer().process(fixture.controller());
            var owned = inputs(fixture);
            fixture.assembly().getCatalystInventory().clear();
            roundTrip(helper, fixture);
            var paused = fixture.buffer().process(fixture.controller());
            helper.assertTrue(paused.state() == MatterFabricationBlockEntity.ProcessingState.WAITING_CATALYST
                            && paused.progress() == first.progress() && inputs(fixture).equals(owned),
                    "NBT reload must preserve all active inputs and progress while the retained catalyst is missing");
            catalyst(fixture);
            for (int tick = first.progress(); tick < DURATION; tick++) fixture.buffer().process(fixture.controller());
            helper.assertTrue(inputs(fixture).isEmpty() && outputs(fixture).equals(Map.of(OUTPUT, 2052L)),
                    "Restored work must complete exactly 1026 crafts once");
            catalystUnchanged(helper, fixture);
            pass(helper, fixture);
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void cancellationAfterReloadIsolatesTwoUuidsForSamePattern(GameTestHelper helper) {
        powered(helper, 2, fixture -> {
            var firstId = UUID.randomUUID(); var secondId = UUID.randomUUID();
            submit(helper, prepare(helper, fixture, inventory(2048), 2048, firstId, false));
            submit(helper, prepare(helper, fixture, inventory(3072), 3072, secondId, false));
            helper.assertTrue(fixture.buffer().queuedPatterns() == 2, "Identical recipes from two UUIDs must remain separate");
            helper.assertTrue(fixture.buffer().process(fixture.controller()).progress() == 1,
                    "First UUID must own an actual active batch before cancellation");
            roundTrip(helper, fixture);
            ECOCraftingJobLifecycle.cancelAndRecover(helper.getLevel(), firstId);
            helper.assertTrue(MolecularBatchCancellationData.isCanceled(helper.getLevel(), firstId)
                            && !MolecularBatchCancellationData.isCanceled(helper.getLevel(), secondId),
                    "Real ECO cancellation must mark only the requested UUID");
            fixture.buffer().serverTick();
            var state = saved(helper, fixture);
            helper.assertTrue(!fixture.buffer().isProcessing() && fixture.buffer().queuedPatterns() == 1
                            && amounts(state.getList("refunds", Tag.TAG_COMPOUND), helper).equals(MatterPatternBuffer.scaled(UNIT, 2048))
                            && state.getList("queued", Tag.TAG_COMPOUND).getCompound(0).getUUID("crafting_id").equals(secondId),
                    "Cancel must refund only first UUID's active ownership and preserve second UUID's full queue");
            roundTrip(helper, fixture);
            fixture.buffer().serverTick();
            helper.assertTrue(saved(helper, fixture).equals(state), "Repeated cancel after reload must not duplicate refunds");
            for (int tick = 0; tick < DURATION; tick++) fixture.buffer().process(fixture.controller());
            helper.assertTrue(outputs(fixture).equals(Map.of(OUTPUT, 6144L))
                            && inputs(fixture).equals(MatterPatternBuffer.scaled(UNIT, 2048)),
                    "Only the surviving UUID produces output; canceled input remains a real refund");
            catalystUnchanged(helper, fixture);
            pass(helper, fixture);
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void nativeExactUsesMaximalLongCapacityAndRetainsCpuExcess(GameTestHelper helper) {
        powered(helper, 3, fixture -> {
            var requested = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(17));
            var inventory = exactInventory(requested);
            var original = inventory.snapshot();
            var context = context(helper, fixture, UUID.randomUUID());
            // Both representations must agree, and an overflowing exact vector cannot be
            // replaced by a small long vector even when that vector is a valid recipe.
            var bad = fixture.provider().eco$prepareFastPath(context);
            var overflow = new LinkedHashMap<>(ECOExactInventory.totals(stacks(UNIT), BigInteger.ONE));
            overflow.put(MATERIAL, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
            helper.assertTrue(bad != null && bad.supportsExactInputs()
                            && !bad.push(new ECOFastPathDispatchProvider.Batch(1, stacks(UNIT),
                                    List.of(fixture.pattern().getOutputs()), List.of(), overflow))
                            && !fixture.buffer().hasContents() && inventory.snapshot().equals(original),
                    "Overflowing exact input must reject without truncating, fabricating or taking source materials");
            var prepared = ECOExactBatchPlanner.prepare(fixture.provider(), context, inventory, requested, Map.of());
            long capacity = Long.MAX_VALUE / UNIT.get(MATERIAL);
            helper.assertTrue(prepared != null && prepared.craftCount().equals(BigInteger.valueOf(capacity))
                            && capacity > 1024 && !fixture.buffer().hasContents(),
                    "Native exact preparation must return the maximal long-representable material batch, independent of physical parallel");
            helper.assertTrue(prepared.outputs().equals(Map.of(OUTPUT, BigInteger.valueOf(capacity).multiply(BigInteger.TWO))),
                    "Real ECO exact expected outputs must cover the entire admitted batch");
            var lease = new Lease();
            helper.assertTrue(prepared.submit(lease) && lease.commits == 1 && lease.refunds == 0,
                    "Actual ECO exact planner must debit and transfer ownership once");
            for (var entry : UNIT.entrySet()) {
                var accepted = BigInteger.valueOf(entry.getValue()).multiply(BigInteger.valueOf(capacity));
                helper.assertTrue(inventory.amount(entry.getKey()).equals(original.get(entry.getKey()).subtract(accepted)),
                        "CPU must retain its entire unaccepted BigInteger excess for " + entry.getKey());
            }
            helper.assertTrue(inputs(fixture).equals(MatterPatternBuffer.scaled(UNIT, capacity))
                            && fixture.buffer().queuedPatterns() == 1 && outputs(fixture).isEmpty(),
                    "One durable queue entry must own the complete admitted exact vector before any physical output");
            roundTrip(helper, fixture);
            helper.assertTrue(inputs(fixture).equals(MatterPatternBuffer.scaled(UNIT, capacity)),
                    "Maximum admitted long material totals must survive real NBT codecs");
            helper.assertTrue(fixture.provider().eco$prepareExactFastPath(context, requested) == null,
                    "A full material queue must apply backpressure, never manufacture capacity");
            helper.assertTrue(fixture.buffer().process(fixture.controller()).power() == 2 * PARALLEL
                            && saved(helper, fixture).getCompound("active").getLong("crafts") == PARALLEL,
                    "Exact admission must preserve the same intrinsic physical parallel as long admission");
            pass(helper, fixture);
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void longPlannerAcceptsExactOnlyInputVectorWithoutLosingExcess(GameTestHelper helper) {
        powered(helper, 4, fixture -> {
            var initialCopies = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(73));
            var inventory = exactInventory(initialCopies);
            var original = inventory.snapshot();
            var prepared = prepare(helper, fixture, inventory, 1026, UUID.randomUUID(), true);
            helper.assertTrue(prepared.exactInputTotal().equals(ECOExactInventory.totals(stacks(UNIT), 1026)),
                    "The actual long planner must use ECO's exact-input lease protocol");
            submit(helper, prepared);
            for (var entry : prepared.exactInputTotal().entrySet())
                helper.assertTrue(inventory.amount(entry.getKey()).equals(original.get(entry.getKey()).subtract(entry.getValue())),
                        "Exact source excess must remain intact after bounded long dispatch");
            helper.assertTrue(inputs(fixture).equals(MatterPatternBuffer.scaled(UNIT, 1026)),
                    "Exact-only Batch.inputTotal materialization must transfer all admitted inputs");
            roundTrip(helper, fixture);
            pass(helper, fixture);
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void nativeEcoSchedulerQueuesWholeTaskBeforePhysicalProcessing(GameTestHelper helper) {
        powered(helper, 5, fixture -> helper.runAfterDelay(2, () -> {
            long count = 1_000_000;
            var grid = fixture.assembly().getMainNode().getGrid();
            var service = (CraftingService) grid.getCraftingService();
            helper.assertTrue(java.util.stream.StreamSupport.stream(
                            service.getProviders(fixture.pattern()).spliterator(), false)
                            .anyMatch(provider -> provider == fixture.assembly().getLogic()),
                    "The real grid must expose the well provider to ECO's scheduler");
            // Only the physical computation-host environment is controlled. Submission,
            // input inventory, provider selection, dispatch and accounting use ECO itself.
            var cpu = new ECOCraftingCPU(null, ECOTier.L9) {
                @Override public boolean isActive() { return true; }
                @Override public long getAvailableStorage() { return Long.MAX_VALUE; }
                @Override public int getCoProcessors() { return Integer.MAX_VALUE - 1; }
                @Override public net.minecraft.world.level.Level getLevel() { return helper.getLevel(); }
                @Override public appeng.api.networking.IGrid getGrid() { return grid; }
                @Override public appeng.api.networking.security.IActionSource getActionSource() {
                    return new MachineSource(fixture.assembly());
                }
                @Override public void markDirty() { }
            };
            var logic = cpu.getLogic();
            var plan = new CraftingPlan(new GenericStack(OUTPUT, 2 * count), 1, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(fixture.pattern(), count));
            var result = logic.trySubmitJob(grid, plan, new MachineSource(fixture.assembly()), null);
            helper.assertTrue(result.successful(), "Native ECO must accept the controlled ordinary plan: " + result);
            var allInputs = MatterPatternBuffer.scaled(UNIT, count);
            allInputs.forEach((key, amount) ->
                    logic.getInventory().insert(key, amount, appeng.api.config.Actionable.MODULATE));
            fixture.assembly().getCatalystInventory().clear();
            helper.assertTrue(logic.executeNormalCrafting(64, service, grid.getEnergyService(), helper.getLevel()) == 0
                            && !fixture.buffer().hasContents() && logic.getWaitingFor(OUTPUT) == 0,
                    "Missing catalysts must reject native scheduling before taking any materials");
            catalyst(fixture);
            var noPower = (appeng.api.networking.energy.IEnergyService) java.lang.reflect.Proxy.newProxyInstance(
                    appeng.api.networking.energy.IEnergyService.class.getClassLoader(),
                    new Class<?>[]{appeng.api.networking.energy.IEnergyService.class},
                    (proxy, method, args) -> method.getName().equals("extractAEPower")
                            ? 0.0 : method.invoke(grid.getEnergyService(), args));
            helper.assertTrue(logic.executeNormalCrafting(64, service, noPower, helper.getLevel()) == 0
                            && !fixture.buffer().hasContents() && logic.getWaitingFor(OUTPUT) == 0,
                    "Unavailable dispatch power must leave native provider ownership and waiting unchanged");
            for (var entry : allInputs.entrySet()) helper.assertTrue(
                    logic.getInventory().list.get(entry.getKey()) == entry.getValue(),
                    "Rejected native scheduling must retain every CPU material");
            // Verified native batches remain eligible after the ordinary push budget is exhausted.
            int pushed = logic.executeNormalCrafting(0, service, grid.getEnergyService(), helper.getLevel());
            helper.assertTrue(pushed == count && logic.getInventory().list.isEmpty()
                            && logic.getWaitingFor(OUTPUT) == 2 * count
                            && inputs(fixture).equals(allInputs),
                    "Native ECO scheduler must enqueue the whole million-craft task in one pass, not a small adaptive/ordinary window; actual=" + pushed);
            helper.assertTrue(logic.executeNormalCrafting(0, service, grid.getEnergyService(), helper.getLevel()) == 0
                            && inputs(fixture).equals(allInputs) && logic.getWaitingFor(OUTPUT) == 2 * count,
                    "A second scheduler pass must not replay submitted work or increase waiting");
            roundTrip(helper, fixture);
            helper.assertTrue(fixture.buffer().process(fixture.controller()).power() == 2 * PARALLEL,
                    "Large CPU intake must not alter the well's physical parallel or processing power");
            var craftingId = logic.getLastLink().getCraftingID();
            logic.cancel();
            fixture.buffer().serverTick();
            var canceled = saved(helper, fixture);
            helper.assertTrue(MolecularBatchCancellationData.isCanceled(helper.getLevel(), craftingId)
                            && !fixture.buffer().isProcessing() && fixture.buffer().queuedPatterns() == 0
                            && amounts(canceled.getList("refunds", Tag.TAG_COMPOUND), helper).equals(allInputs)
                            && outputs(fixture).isEmpty(),
                    "Native ECO cancellation must refund all queued and active inputs exactly once without output");
            roundTrip(helper, fixture);
            fixture.buffer().serverTick();
            helper.assertTrue(saved(helper, fixture).equals(canceled),
                    "Reload and repeated cancellation reconciliation must not duplicate refunds");
            pass(helper, fixture);
        }));
    }

    private record Fixture(MatterFabricationBlockEntity controller,
            MatterFabricationPatternAssemblyBlockEntity assembly, IPatternDetails pattern) {
        MatterPatternBuffer buffer() { return assembly.getBuffer(); }
        ECOFastPathDispatchProvider provider() { return ECOFastPathFacade.resolveProvider(assembly.getLogic()); }
    }

    private static void powered(GameTestHelper helper, int lane, Consumer<Fixture> action) {
        com.atir.molecularmanipulator.verification.RegressionConfigFixture.isolate();
        var level = helper.getLevel();
        var origin = new BlockPos(940_000 + lane * 96, 100, 960);
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true); level.getChunkAt(pos);
            if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity old) old.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, MatterFabricationStructure.isController(part)
                    ? ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    : MatterFabricationStructure.partState(part.type()), 3);
        }
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().get(0));
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = origin.east(30);
        level.setChunkForced(powerPos.getX() >> 4, powerPos.getZ() >> 4, true); level.getChunkAt(powerPos);
        if (level.getBlockEntity(powerPos) instanceof AEBaseBlockEntity old) old.clearContent();
        level.setBlock(powerPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        helper.runAfterDelay(5, () -> {
            controller.refreshStructure(); assembly.setControllerPos(origin);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(assembly.isOperational(),
                "Real fabrication well and pattern assembly must form and receive AE power")).thenExecute(() -> {
            var recipeId = new ResourceLocation("molecularmanipulator", "gametest/eco_matter_" + lane);
            var researchId = recipeId.withSuffix("_research");
            var recipe = new MatterFabricationRecipe(
                    List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND, Items.AMETHYST_SHARD), 1)),
                    List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND_BLOCK), 1)),
                    List.of(new ItemStack(Items.EMERALD, 2)), new FluidStack(Fluids.WATER, 1000), FluidStack.EMPTY,
                    List.of(new GenericStack(MATERIAL, UNIT.get(MATERIAL))), List.of(), DURATION, 2, false);
            var research = new MatterResearchRecipe("ECO matter fixture", List.of(), List.of(), 1, 0,
                    List.of(recipeId), List.of(), 0, 1,
                    List.of(new ResearchDepth(1, PARALLEL, 1, 0, Optional.empty())));
            var recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
            recipes.add(recipe.withId(recipeId)); recipes.add(research.withId(researchId));
            level.getRecipeManager().replaceRecipes(recipes);
            MatterResearchApi.setCompletionCount(controller, researchId.toString(), 1);
            helper.assertTrue(MatterResearchApi.productionProfile(controller,
                            MatterRecipeIndex.get(level).fabrication(recipeId)).parallel() == PARALLEL,
                    "The fixture must obtain its execution parallel through real research state");
            assembly.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK));
            assembly.getLogic().getPatternInv().setItemDirect(0,
                    MatterFabricationPatternEncoding.encode(recipe, List.of(new ItemStack(Items.DIAMOND))));
            assembly.getLogic().updatePatterns();
            var fixture = new Fixture(controller, assembly, assembly.getLogic().getAvailablePatterns().get(0));
            helper.assertTrue(ECOFastPathFacade.supports(assembly.getLogic()) && fixture.provider() != null,
                    "ECO must discover the existing matter provider mixin in the actual game runtime");
            action.accept(fixture);
        });
    }

    private static ECOBatchDispatchContext context(GameTestHelper helper, Fixture fixture, UUID id) {
        return new ECOBatchDispatchContext(fixture.pattern(), List.of(stacks(UNIT)), List.of(fixture.pattern().getOutputs()),
                List.of(), helper.getLevel(), id);
    }

    private static ECOFastPathFacade.PreparedBatch prepare(GameTestHelper helper, Fixture fixture,
            ListCraftingInventory inventory, long count, UUID id, boolean exact) {
        var unit = new KeyCounter(); UNIT.forEach(unit::add);
        var output = new KeyCounter(); List.of(fixture.pattern().getOutputs()).forEach(stack -> output.add(stack.what(), stack.amount()));
        var prepared = ECOFastPathFacade.prepare(fixture.assembly().getLogic(), fixture.pattern(),
                new KeyCounter[]{unit}, output, new KeyCounter(), inventory, count, 0, null, helper.getLevel(), id, exact);
        helper.assertTrue(prepared != null && prepared.craftCount() == count,
                "Real ECO fast-path planner must admit the full requested long batch");
        return prepared;
    }

    private static void submit(GameTestHelper helper, ECOFastPathFacade.PreparedBatch prepared) {
        var lease = new Lease();
        helper.assertTrue(prepared.submit(power -> lease) && lease.commits == 1 && lease.refunds == 0,
                "Real ECO transaction must commit input ownership exactly once");
    }

    private static ListCraftingInventory inventory(long count) {
        var inventory = new ListCraftingInventory(ignored -> {});
        MatterPatternBuffer.scaled(UNIT, count).forEach(inventory.list::add); return inventory;
    }

    private static ECOExactInventory exactInventory(BigInteger count) {
        var inventory = new ECOExactInventory(ignored -> {}); inventory.setEnabled(true);
        inventory.restore(ECOExactInventory.totals(stacks(UNIT), count)); return inventory;
    }

    private static List<GenericStack> stacks(Map<AEKey, Long> amounts) {
        return amounts.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
    }

    private static Map<AEKey, Long> inputs(Fixture fixture) { return MatterFabricationPatternLogic.batchInputs(fixture.buffer().contents(false)); }
    private static Map<AEKey, Long> outputs(Fixture fixture) { return MatterFabricationPatternLogic.batchInputs(fixture.buffer().contents(true)); }
    private static Map<AEKey, Long> amounts(ListTag tags, GameTestHelper helper) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var tag : tags) {
            var stack = GenericStack.readTag((CompoundTag) tag);
            if (stack == null) throw new AssertionError("Unresolved fixture material");
            result.merge(stack.what(), stack.amount(), Math::addExact);
        }
        return result;
    }

    private static CompoundTag saved(GameTestHelper helper, Fixture fixture) {
        var parent = new CompoundTag(); fixture.buffer().save(parent);
        return parent.getCompound("fabrication_pattern_buffer");
    }

    private static void roundTrip(GameTestHelper helper, Fixture fixture) {
        var before = saved(helper, fixture); var parent = new CompoundTag(); parent.put("fabrication_pattern_buffer", before);
        fixture.buffer().load(parent);
        helper.assertTrue(!fixture.buffer().isUnavailable() && saved(helper, fixture).equals(before),
                "All recipe, UUID, input, output and progress data must survive real NBT serialization");
    }

    private static void catalyst(Fixture fixture) { fixture.assembly().getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK)); }
    private static void catalystUnchanged(GameTestHelper helper, Fixture fixture) {
        var catalyst = fixture.assembly().getCatalystInventory().getStackInSlot(0);
        helper.assertTrue(catalyst.is(Items.DIAMOND_BLOCK) && catalyst.getCount() == 1,
                "One retained catalyst must remain one item after any batch size");
    }
    private static void pass(GameTestHelper helper, Fixture fixture) {
        fixture.assembly().clearContent();
        helper.succeed();
    }
    private static final class Lease implements ECOFastPathFacade.Reservation {
        int commits, refunds;
        public void commit() { commits++; }
        public void refund() { refunds++; }
    }
}
