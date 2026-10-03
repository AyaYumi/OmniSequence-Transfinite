package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.*;
import appeng.core.definitions.AEBlocks;
import appeng.me.helpers.MachineSource;
import com.appliedenhancements.api.*;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.*;
import com.atir.molecularmanipulator.registry.*;
import com.atir.molecularmanipulator.research.*;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

/** Additional installed-pack regressions for complete jobs, reserved research and fluid production. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class PackFeatureGameTests {
    private static final ResourceLocation LOG_RECIPE = new ResourceLocation("minecraft:oak_planks");

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 2400)
    public static void sustainedIdleResearchCollectionAndDuplication(GameTestHelper h) {
        var level = h.getLevel();
        var hubPos = new BlockPos(8800, 100, 8800);
        var wellPos = hubPos.offset(128, 0, 0);
        for (var part : SingularityStructure.parts()) {
            var at = SingularityStructure.worldPos(hubPos, Direction.NORTH, part); force(level, at);
            level.setBlock(at, SingularityStructure.state(part, Direction.NORTH), 3);
        }
        level.setBlockAndUpdate(hubPos, SingularityContent.CONTROLLER.get().defaultBlockState());
        for (var part : MatterFabricationStructure.parts()) {
            var at = MatterFabricationStructure.worldPos(wellPos, Direction.NORTH, part); force(level, at);
            level.setBlock(at, MatterFabricationStructure.partState(part.type()), 3);
        }
        level.setBlockAndUpdate(wellPos, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState());
        var powerPos = hubPos.offset(32, 0, 0); force(level, powerPos);
        level.setBlockAndUpdate(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var hub = (SingularityBlockEntity) level.getBlockEntity(hubPos);
        var well = (MatterFabricationBlockEntity) level.getBlockEntity(wellPos);
        var power = (appeng.blockentity.networking.CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        var stock = new Stock();
        IStorageProvider mount = mounts -> mounts.mount(stock, 0);
        var original = List.copyOf(level.getRecipeManager().getRecipes());
        var researchId = new ResourceLocation("molecularmanipulator:pack_sustained_research");
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        var previousPosition = player.position();
        h.startSequence().thenWaitUntil(() -> h.assertTrue(hub.getMainNode().isReady()
                        && well.getMainNode().isReady() && power.getMainNode().isActive(), "Wait for sustained test nodes"))
                .thenExecute(() -> {
                    hub.scheduleInspection(); hub.serverTick(); well.refreshStructure();
                    GridHelper.createConnection(hub.getMainNode().getNode(), power.getMainNode().getNode());
                    GridHelper.createConnection(well.getMainNode().getNode(), power.getMainNode().getNode());
                    hub.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(mount);
                }).thenWaitUntil(() -> h.assertTrue(hub.formed() && hub.networkOnline()
                        && well.isStructureFormed() && well.getMainNode().isActive(), "Both formed machines must be powered"))
                .thenExecute(() -> {
                    com.atir.molecularmanipulator.verification.PackFlowVerification.timingPhase("sustained_idle");
                    System.out.println("PACK_SUSTAINED_IDLE_BEGIN ticks=600");
                })
                .thenIdle(600)
                .thenExecute(() -> {
                    h.assertTrue(stock.amounts.isEmpty() && !hub.collectionActive(), "Idle machines must not create output");
                    var definition = new MatterResearchRecipe("Sustained research", List.of(), List.of(), 100_000,
                            2048, List.of(), List.of(), 0, 3, List.of(new ResearchDepth(1, 1, 1, 0, Optional.empty())))
                            .withId(researchId);
                    var recipes = new ArrayList<Recipe<?>>(original); recipes.add(definition);
                    level.getRecipeManager().replaceRecipes(recipes);
                    h.assertTrue(MatterResearchApi.start(well, researchId.toString()), "Long research must start");
                    player.teleportTo(hubPos.getX() + .5, hubPos.getY() + 1, hubPos.getZ() + .5);
                    hub.getDuplicationInventory().setItemDirect(1, new ItemStack(ModContent.BLACK_HOLE.get(), 64));
                    hub.getDuplicationInventory().setItemDirect(0, new ItemStack(Items.DIAMOND));
                    hub.toggleCollection(player);
                    h.assertTrue(hub.collectionActive() && hub.motion().hasBodies(), "Actual collection lifecycle must activate motion");
                    player.teleportTo(previousPosition.x, previousPosition.y, previousPosition.z);
                    com.atir.molecularmanipulator.verification.PackFlowVerification.timingPhase("sustained_active");
                    System.out.println("PACK_SUSTAINED_ACTIVE_BEGIN ticks=600 research=true resources=" + hub.collectionItemIds().split(",").length + " blackHoles=64 copy=true motion=true");
                }).thenIdle(600)
                .thenExecute(() -> {
                    try {
                        h.assertTrue(well.getResearch().hasTask(researchId) && !well.getResearch().isPaused(researchId), "Research must remain active through real server ticks");
                        h.assertTrue(hub.collectionActive() && hub.motion().hasBodies(), "Collection and motion must stay active");
                        h.assertTrue(stock.amounts.getOrDefault(AEItemKey.of(Items.DIAMOND), 0L) > 0, "Real ticks must produce duplicated items");
                        var collected = stock.amounts.entrySet().stream().filter(e -> e.getKey() instanceof AEItemKey && !e.getKey().equals(AEItemKey.of(Items.DIAMOND))).count();
                        h.assertTrue(collected > 0, "Real ticks must collect tagged resources");
                        System.out.println("PACK_SUSTAINED_PASS idleTicks=600 activeTicks=600 collectedTypes=" + collected
                                + " copies=" + stock.amounts.get(AEItemKey.of(Items.DIAMOND)));
                    } finally {
                        com.atir.molecularmanipulator.verification.PackFlowVerification.timingPhase("sustained_cleanup");
                        player.teleportTo(hubPos.getX() + .5, hubPos.getY() + 1, hubPos.getZ() + .5);
                        if (hub.collectionActive()) hub.toggleCollection(player);
                        hub.getDuplicationInventory().setItemDirect(0, ItemStack.EMPTY);
                        hub.getDuplicationInventory().setItemDirect(1, ItemStack.EMPTY);
                        well.getResearch().clearStoredMaterials();
                        hub.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(mount);
                        level.getRecipeManager().replaceRecipes(original);
                        player.teleportTo(previousPosition.x, previousPosition.y, previousPosition.z);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 1600)
    public static void continuousResearchOrdersReserveDependencyMaterials(GameTestHelper h) {
        var level=h.getLevel(); var pos=new BlockPos(8000,100,8000);
        for(var part:MatterFabricationStructure.parts()) {
            var at=MatterFabricationStructure.worldPos(pos,Direction.NORTH,part);force(level,at);
            level.setBlock(at,MatterFabricationStructure.partState(part.type()),3);
        }
        level.setBlockAndUpdate(pos,ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState());
        var well=(MatterFabricationBlockEntity)level.getBlockEntity(pos);
        var providerPos=pos.offset(32,0,0);force(level,providerPos);
        level.setBlockAndUpdate(providerPos,ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState());
        level.setBlockAndUpdate(providerPos.east(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(providerPos.north(),ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState());
        var provider=(MolecularManipulatorBlockEntity)level.getBlockEntity(providerPos);
        var stock=new Stock();stock.amounts.put(AEItemKey.of(Items.OAK_LOG),64L);
        IStorageProvider mount=mounts -> mounts.mount(stock,0);
        var original=List.copyOf(level.getRecipeManager().getRecipes());
        var id=new ResourceLocation("molecularmanipulator:pack_chain_research");
        h.startSequence().thenWaitUntil(() -> h.assertTrue(well.getMainNode().getNode()!=null && provider.getMainNode().isActive(),"Wait for well and provider"))
                .thenExecute(() -> {
                    well.refreshStructure();GridHelper.createConnection(well.getMainNode().getNode(),provider.getMainNode().getNode());
                    var costs=List.of(new MatterResearchRecipe.Cost(Ingredient.of(Items.OAK_PLANKS),4),
                            new MatterResearchRecipe.Cost(Ingredient.of(Items.STICK),8));
                    var research=new MatterResearchRecipe("Dependency chain",List.of(),costs,40,0,List.of(),List.of(),0,3,
                            List.of(new ResearchDepth(1,1,1,0,Optional.empty()),new ResearchDepth(2,2,1,0,Optional.empty()),
                                    new ResearchDepth(4,4,1,0,Optional.empty()))).withId(id);
                    var recipes=new ArrayList<Recipe<?>>(original);recipes.add(research);level.getRecipeManager().replaceRecipes(recipes);
                    var logs=new ItemStack[9];Arrays.fill(logs,ItemStack.EMPTY);logs[0]=new ItemStack(Items.OAK_LOG);
                    var sticks=new ItemStack[9];Arrays.fill(sticks,ItemStack.EMPTY);sticks[0]=new ItemStack(Items.OAK_PLANKS);sticks[3]=new ItemStack(Items.OAK_PLANKS);
                    provider.getLogic().getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeCraftingPattern(
                            (CraftingRecipe)level.getRecipeManager().byKey(LOG_RECIPE).orElseThrow(),logs,new ItemStack(Items.OAK_PLANKS,4),false,false));
                    provider.getLogic().getPatternInv().setItemDirect(1,PatternDetailsHelper.encodeCraftingPattern(
                            (CraftingRecipe)level.getRecipeManager().byKey(new ResourceLocation("minecraft:stick")).orElseThrow(),sticks,new ItemStack(Items.STICK,4),false,false));
                    well.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(mount);
                }).thenWaitUntil(() -> h.assertTrue(well.isStructureFormed() && well.getMainNode().isActive()
                        && well.getMainNode().getGrid().getCraftingService().isCraftable(AEItemKey.of(Items.STICK)),"Wait for published dependency chain"))
                .thenExecute(() -> h.assertTrue(MatterResearchApi.orderMissing(well,id,true),"One click starts continuous maximum research ordering"))
                .thenWaitUntil(() -> h.assertTrue(well.getResearch().completionCount(id)==3,
                        "Both dependent materials must arrive and complete maximum research without clicking again; orders="+well.getResearchOrders().clientState(level.registryAccess())))
                .thenExecute(() -> {
                    h.assertTrue(stock.amounts.get(AEItemKey.of(Items.OAK_LOG))==50,"Exactly 14 logs fund 28 reserved planks and 56 sticks");
                    h.assertTrue(!well.getResearch().isPreparing(id)&&!well.getResearch().hasTask(id),"Completed order becomes idle");
                    well.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(mount);
                    level.getRecipeManager().replaceRecipes(original);
                    System.out.println("PACK_RESEARCH_CHAIN_PASS actualAEOrders=true reservedDependency=true oneClick=true exactInputs=true maximum=true idle=true");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void quantumConnectionClaimConflictAndRemoval(GameTestHelper h) {
        var level=h.getLevel(); var pos=new BlockPos(7600,100,7600); force(level,pos); force(level,pos.offset(32,0,0));
        level.setBlockAndUpdate(pos,SingularityContent.CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.offset(32,0,0),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var hub=(SingularityBlockEntity)level.getBlockEntity(pos);
        var remote=(appeng.blockentity.networking.CreativeEnergyCellBlockEntity)level.getBlockEntity(pos.offset(32,0,0));
        long frequency=981_716_431;
        h.startSequence().thenWaitUntil(() -> h.assertTrue(hub.getMainNode().isReady() && remote.getMainNode().isActive(),"Wait for quantum nodes"))
                .thenExecute(() -> {
                    appeng.api.features.Locatables.quantumNetworkBridges().register(level,frequency,remote);
                    var singularity=appeng.core.definitions.AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
                    singularity.getOrCreateTag().putLong(appeng.blockentity.qnb.QuantumBridgeBlockEntity.TAG_FREQUENCY,frequency);
                    hub.getQuantumInventory().setItemDirect(0,singularity);
                }).thenWaitUntil(() -> h.assertTrue(hub.networkOnline(),"Quantum connection must power isolated hub"))
                .thenExecute(() -> {
                    h.assertTrue(hub.getMainNode().getGrid()==remote.getMainNode().getGrid(),"Actual AE grid must merge over quantum connection");
                    h.assertTrue(!com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry.claim(level,pos.offset(16,0,0),frequency),"Frequency cannot be claimed by another controller");
                    hub.getQuantumInventory().setItemDirect(0,ItemStack.EMPTY);
                    h.assertTrue(hub.getQuantumFrequency()==0 && hub.getMainNode().getGrid()!=remote.getMainNode().getGrid(),"Removing singularity disconnects grids");
                    h.assertTrue(com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry.claim(level,pos.offset(16,0,0),frequency),"Removal releases frequency ownership");
                    com.atir.molecularmanipulator.integration.ae2.EntangledQuantumFrequencyRegistry.release(level,pos.offset(16,0,0),frequency);
                    appeng.api.features.Locatables.quantumNetworkBridges().unregister(level,frequency);
                    System.out.println("PACK_QUANTUM_PASS realGridMerge=true powered=true duplicateClaimBlocked=true removalDisconnect=true release=true");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 80)
    public static void installedEaepWrapperRetainsNativeMultiplier(GameTestHelper h) {
        try {
            var key = AEItemKey.of(Items.OAK_LOG);
            var output = AEItemKey.of(Items.OAK_PLANKS);
            var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(key, 1)},
                    new GenericStack[]{new GenericStack(output, 4)});
            var original = PatternDetailsHelper.decodePattern(encoded, h.getLevel());
            var aware = Class.forName("com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern");
            h.assertTrue(aware.isInstance(original), "Installed EAEP must transform the real processing pattern");
            var toggle = aware.getMethod("eap$setAllowScaling", boolean.class);
            toggle.invoke(original, false);
            h.assertTrue(!AelisSmartDoublingApi.isExternallyManaged(original), "Disabled EAEP pattern retains ordinary batching");
            toggle.invoke(original, true);
            h.assertTrue(AelisSmartDoublingApi.isExternallyManaged(original), "Enabled EAEP pattern bypasses local multiplier");
            var type = Class.forName("com.extendedae_plus.api.crafting.ScaledProcessingPattern");
            var batch = (appeng.api.crafting.IPatternDetails) type.getConstructors()[0].newInstance(original, 7L);
            h.assertTrue(AelisSmartDoublingApi.isExternallyManaged(batch)
                    && !(batch instanceof com.github.appliedenhancements.integration.ae2.AelisScaledPattern),
                    "Real EAEP wrapper keeps its native interface");
            var scale = com.appliedenhancements.runtime.SmartDoublingPatternAccess.resolve(batch);
            h.assertTrue(scale.original() == original && scale.multiplier() == 7, "Read the installed private native multiplier");
            h.assertTrue(!MolecularBatchDispatchSafety.isBatchablePattern(original)
                    && !MolecularBatchDispatchSafety.isBatchablePattern(batch), "Omni runtime must bypass both enabled forms");
            var normalized = MolecularExternalScaledPattern.unwrapMultiInput(batch);
            h.assertTrue(normalized.patternDetails() == batch && normalized.multiplier() == 1, "No second normalization");
            var source = new appeng.crafting.CraftingPlan(new GenericStack(output, 60), 64, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(original, 15L));
            var exact = AelisExactCraftingPlanApi.attachExecutionMetadata(source, BigInteger.valueOf(60),
                    Map.of(original, BigInteger.valueOf(15)), Map.of());
            var target = new appeng.crafting.CraftingPlan(source.finalOutput(), 64, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(batch, 2L, original, 1L));
            var merged = AelisCycleExecutionApi.copyMetadata(exact, target);
            h.assertTrue(AelisExactCraftingPlanApi.getPatternTimes(merged).equals(Map.of(batch, BigInteger.TWO, original, BigInteger.ONE)),
                    "Native split and remainder preserve exact work");
            h.assertTrue(OmniSmartDoublingPlanner.rewriteForSubmission(target,
                    ignored -> { throw new AssertionError("External pattern must bypass provider lookup"); }) == target,
                    "Submission must retain the installed wrapper");
            System.out.println("PACK_EAEP_PASS installed1.6.2=true enabledState=true privateMultiplier=true noSecondScale=true exactRemainder=true");
            h.succeed();
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 1200)
    public static void actualPlanningSubmissionAndCompletion(GameTestHelper h) {
        var level = h.getLevel();
        var pos = new BlockPos(6400, 100, 6400);
        force(level, pos);
        level.setBlockAndUpdate(pos, ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState());
        var provider = (MolecularManipulatorBlockEntity) level.getBlockEntity(pos);
        var cpu = (OmniComputationCoreBlockEntity) level.getBlockEntity(pos.north());
        var ingredients = new ItemStack[9]; Arrays.fill(ingredients, ItemStack.EMPTY);
        ingredients[0] = new ItemStack(Items.OAK_LOG);
        var encoded = PatternDetailsHelper.encodeCraftingPattern(
                (CraftingRecipe) level.getRecipeManager().byKey(LOG_RECIPE).orElseThrow(), ingredients,
                new ItemStack(Items.OAK_PLANKS, 4), false, false);
        provider.getLogic().getPatternInv().setItemDirect(0, encoded);
        var stock = new Stock(); stock.amounts.put(AEItemKey.of(Items.OAK_LOG), 1024L);
        IStorageProvider mount = mounts -> mounts.mount(stock, 0);
        Future<ICraftingPlan>[] plan = new Future[1];
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(provider.getMainNode().isActive() && cpu.isNetworkOnline(), "Wait for physical provider/CPU grid");
            h.assertTrue(!provider.getLogic().getAvailablePatterns().isEmpty(), "Native pattern must publish");
        }).thenExecute(() -> {
            var grid = provider.getMainNode().getGrid();
            grid.getStorageService().addGlobalStorageProvider(mount);
            var requester = new ICraftingSimulationRequester() {
                public IActionSource getActionSource() { return new MachineSource(provider); }
                public appeng.api.networking.IGridNode getGridNode() { return provider.getMainNode().getNode(); }
            };
            plan[0] = grid.getCraftingService().beginCraftingCalculation(level, requester,
                    AEItemKey.of(Items.OAK_PLANKS), 256, CalculationStrategy.REPORT_MISSING_ITEMS);
        }).thenWaitUntil(() -> h.assertTrue(plan[0].isDone(), "Wait for asynchronous planning"))
                .thenExecute(() -> {
                    try {
                        var result = plan[0].get();
                        h.assertTrue(!result.simulation(), "Materials must permit a real plan");
                        var submitted = provider.getMainNode().getGrid().getCraftingService()
                                .submitJob(result, null, cpu.getCluster(), false, new MachineSource(provider));
                        h.assertTrue(submitted.successful(), "Plan must submit to real Omni CPU");
                    } catch (Exception error) { throw new RuntimeException(error); }
                }).thenWaitUntil(() -> {
                    h.assertTrue(stock.amounts.getOrDefault(AEItemKey.of(Items.OAK_PLANKS), 0L) == 256,
                            "Native CPU dispatch and output return must complete the exact order");
                    h.assertTrue(stock.amounts.get(AEItemKey.of(Items.OAK_LOG)) == 960,
                            "Only the required 64 logs may be consumed");
                    h.assertTrue(cpu.allCpus().stream().noneMatch(c -> c.isBusy()), "Completed CPU lanes must become idle");
                }).thenExecute(() -> {
                    provider.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(mount);
                    System.out.println("PACK_ACTUAL_CRAFT_PASS planning=true submission=true dispatch=true output256=true inputs64=true idle=true");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 1200)
    public static void appliedPlanningModesAndExactLargeRequests(GameTestHelper h) {
        var level = h.getLevel(); var pos = new BlockPos(6500,100,6500); force(level,pos);
        level.setBlockAndUpdate(pos, ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState());
        level.setBlockAndUpdate(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var provider = (MolecularManipulatorBlockEntity) level.getBlockEntity(pos);
        var ingredients = new ItemStack[9]; Arrays.fill(ingredients,ItemStack.EMPTY); ingredients[0] = new ItemStack(Items.OAK_LOG);
        var encoded = PatternDetailsHelper.encodeCraftingPattern((CraftingRecipe)level.getRecipeManager().byKey(LOG_RECIPE).orElseThrow(),
                ingredients,new ItemStack(Items.OAK_PLANKS,4),false,false);
        provider.getLogic().getPatternInv().setItemDirect(0,encoded);
        var stock = new Stock(); stock.amounts.put(AEItemKey.of(Items.OAK_LOG),1024L);
        IStorageProvider mount = mounts -> mounts.mount(stock,0);
        Future<ICraftingPlan>[] future = new Future[1];
        var previousMode = com.appliedenhancements.Config.MAX_CRAFTING_ORDER_AMOUNT.get();
        var previousExact = com.appliedenhancements.Config.ENABLE_AELIS_BIG_INTEGER_PLANNING.get();
        var sequence = h.startSequence().thenWaitUntil(() -> h.assertTrue(provider.getMainNode().isActive()
                && !provider.getLogic().getAvailablePatterns().isEmpty(),"Wait for real planner network"))
                .thenExecute(() -> provider.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(mount));
        for (var mode : com.appliedenhancements.CraftingOrderMode.values()) {
            var amount = mode == com.appliedenhancements.CraftingOrderMode.DISABLED ? BigInteger.valueOf(256)
                    : mode == com.appliedenhancements.CraftingOrderMode.LONG_MAX ? BigInteger.valueOf(3_000_000_000L)
                    : new BigInteger("100000000000000000000");
            sequence.thenExecute(() -> {
                com.appliedenhancements.Config.MAX_CRAFTING_ORDER_AMOUNT.set(mode);
                com.appliedenhancements.Config.ENABLE_AELIS_BIG_INTEGER_PLANNING.set(true);
                var requester = new ICraftingSimulationRequester() {
                    public IActionSource getActionSource() {return new MachineSource(provider);}
                    public appeng.api.networking.IGridNode getGridNode() {return provider.getMainNode().getNode();}
                };
                future[0] = mode == com.appliedenhancements.CraftingOrderMode.BIG_INTEGER
                        ? AelisExactCraftingService.begin(level,requester,AEItemKey.of(Items.OAK_PLANKS),amount)
                        : provider.getMainNode().getGrid().getCraftingService().beginCraftingCalculation(level,requester,
                                AEItemKey.of(Items.OAK_PLANKS),amount.longValueExact(),CalculationStrategy.REPORT_MISSING_ITEMS);
            }).thenWaitUntil(() -> h.assertTrue(future[0].isDone(),"Wait for " + mode + " calculation"))
                    .thenExecute(() -> {
                        try {
                            var plan = future[0].get();
                            h.assertTrue(AelisExactCraftingPlanApi.getFinalOutputAmount(plan).equals(amount),"Exact requested amount: " + mode);
                            h.assertTrue(plan.simulation() == (mode != com.appliedenhancements.CraftingOrderMode.DISABLED),
                                    "Finite material shortage stays a simulation: " + mode);
                            h.assertTrue(stock.amounts.get(AEItemKey.of(Items.OAK_LOG)) == 1024,"Planning never consumes live stock");
                            System.out.println("PACK_PLANNING_PASS mode="+mode+" amount="+amount+" simulation="+plan.simulation());
                        } catch(Exception failure) {throw new RuntimeException(failure);}
                        finally {com.appliedenhancements.Config.MAX_CRAFTING_ORDER_AMOUNT.set(previousMode);
                            com.appliedenhancements.Config.ENABLE_AELIS_BIG_INTEGER_PLANNING.set(previousExact);}
                    });
        }
        sequence.thenExecute(() -> {
            var first = new PatternSlotRef(1,0); var second = new PatternSlotRef(1,1); var invalid = new PatternSlotRef(1,2);
            var entries = List.of(new PatternDuplicateApi.PatternEntry(first,encoded),new PatternDuplicateApi.PatternEntry(second,encoded.copy()),
                    new PatternDuplicateApi.PatternEntry(invalid,appeng.core.definitions.AEItems.CRAFTING_PATTERN.stack()));
            h.assertTrue(PatternDuplicateApi.findDuplicateSlots(entries,level).equals(Set.of(first,second)),"Native encoded duplicate patterns");
            h.assertTrue(PatternDuplicateApi.findInvalidSlots(entries,level).equals(Set.of(invalid)),"Invalid encoded pattern detection");
            provider.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(mount);
            System.out.println("PACK_PATTERN_PASS actualDecode=true duplicates=true invalid=true");
        }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 600)
    public static void maximumResearchReservationStopAndReload(GameTestHelper h) {
        var level = h.getLevel(); var pos = new BlockPos(6800, 100, 6800);
        for (var part : MatterFabricationStructure.parts()) {
            var at = MatterFabricationStructure.worldPos(pos, Direction.NORTH, part);
            force(level, at);
            level.setBlock(at, MatterFabricationStructure.partState(part.type()), 3);
        }
        level.setBlockAndUpdate(pos, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState());
        var machine = (MatterFabricationBlockEntity) level.getBlockEntity(pos);
        force(level, pos.offset(30, 0, 0));
        level.setBlockAndUpdate(pos.offset(30, 0, 0), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var power = (appeng.blockentity.networking.CreativeEnergyCellBlockEntity) level.getBlockEntity(pos.offset(30, 0, 0));
        h.startSequence().thenWaitUntil(() -> h.assertTrue(machine.getMainNode().getNode() != null
                && power.getMainNode().getNode() != null, "Wait for well/power nodes"))
                .thenExecute(() -> { machine.refreshStructure(); GridHelper.createConnection(machine.getMainNode().getNode(), power.getMainNode().getNode()); })
                .thenWaitUntil(() -> h.assertTrue(machine.isStructureFormed() && machine.getMainNode().isActive(), "Well must form and power"))
                .thenExecute(() -> {
                    var original = List.copyOf(level.getRecipeManager().getRecipes());
                    var id = new ResourceLocation("molecularmanipulator:pack_research");
                    var parent = new ResourceLocation("molecularmanipulator:pack_parent");
                    var costs = List.of(new MatterResearchRecipe.Cost(Ingredient.of(Items.OAK_LOG), 1),
                            new MatterResearchRecipe.Cost(Ingredient.of(Items.OAK_PLANKS), 4));
                    var definition = new MatterResearchRecipe("Pack research", List.of(parent), costs, 10, 0,
                            List.of(), List.of(), 0, 3, List.of(
                            new ResearchDepth(1, 1, 1, 0, Optional.empty()),
                            new ResearchDepth(2, 2, 1, 0, Optional.empty()),
                            new ResearchDepth(4, 4, 1, 0, Optional.empty()))).withId(id);
                    var prerequisite = new MatterResearchRecipe("Parent", List.of(), List.of(), 10, 0,
                            List.of(), List.of(), 0, 2).withId(parent);
                    var recipes = new ArrayList<Recipe<?>>(original); recipes.add(definition); recipes.add(prerequisite);
                    var stock = new Stock();
                    IStorageProvider mount = mounts -> mounts.mount(stock, 0);
                    var storageService = machine.getMainNode().getGrid().getStorageService();
                    storageService.addGlobalStorageProvider(mount);
                    try {
                        level.getRecipeManager().replaceRecipes(recipes);
                        h.assertTrue(!MatterResearchApi.orderMissing(machine, id, true), "Stage prerequisite must block ordering");
                        MatterResearchApi.setCompleted(machine, parent.toString(), true);
                        h.assertTrue(MatterResearchApi.orderMissing(machine, id, true), "Maximum target must persist while materials are absent");
                        var log = AEItemKey.of(Items.OAK_LOG); var plank = AEItemKey.of(Items.OAK_PLANKS);
                        h.assertTrue(machine.getResearch().acceptOrderedMaterial(machine, id, log, 7, Actionable.MODULATE) == 7,
                                "Delivered base material must be reserved outside AE crafting stock");
                        h.assertTrue(stock.amounts.getOrDefault(log, 0L) == 0, "Dependent recipes cannot consume reserved logs");
                        var saved = machine.getResearch().save(); machine.getResearch().load(saved);
                        h.assertTrue(machine.getResearch().isPreparing(id), "Ordering target and reserved material must survive reload");
                        h.assertTrue(MatterResearchApi.stopPreparation(machine, id), "Preparation must stop before research begins");
                        h.assertTrue(stock.amounts.getOrDefault(log, 0L) == 7, "Stop must refund exactly the reserved logs to AE");
                        stock.amounts.clear();
                        h.assertTrue(MatterResearchApi.orderMissing(machine, id, true), "Reordering must work after stop");
                        machine.getResearch().acceptOrderedMaterial(machine, id, log, 7, Actionable.MODULATE);
                        machine.getResearch().acceptOrderedMaterial(machine, id, plank, 28, Actionable.MODULATE);
                        h.assertTrue(MatterResearchApi.start(machine, id.toString()), "All current-to-maximum costs must start one research");
                        h.assertTrue(!MatterResearchApi.stopPreparation(machine, id), "Running research cannot be stopped as preparation");
                        for (int i = 0; i < 10; i++) machine.getResearch().tick(machine);
                        h.assertTrue(machine.getResearch().completionCount(id) == 3, "One research must complete directly to maximum");
                        saved = machine.getResearch().save(); machine.getResearch().load(saved);
                        h.assertTrue(machine.getResearch().completionCount(id) == 3 && !machine.getResearch().hasTask(id),
                                "Completed maximum level must survive reload");
                        System.out.println("PACK_RESEARCH_PASS prerequisites=true reservedBase=true stopRefund=true maxSingleRun=true reload=true");
                    } finally { storageService.removeGlobalStorageProvider(mount); level.getRecipeManager().replaceRecipes(original); }
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void hubResourcesFluidProductionAndCopy(GameTestHelper h) {
        var level = h.getLevel(); var pos = new BlockPos(7200, 100, 7200);
        for (var part : SingularityStructure.parts()) {
            var at = SingularityStructure.worldPos(pos, Direction.NORTH, part); force(level, at);
            level.setBlock(at, SingularityStructure.state(part, Direction.NORTH), 3);
        }
        level.setBlockAndUpdate(pos, SingularityContent.CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var machine = (SingularityBlockEntity) level.getBlockEntity(pos);
        h.startSequence().thenWaitUntil(() -> { machine.scheduleInspection(); machine.serverTick();
            h.assertTrue(machine.formed() && machine.networkOnline(), "Hub must form and connect to physical AE power"); })
                .thenExecute(() -> {
                    var stock = new Stock();
                    IStorageProvider mount = mounts -> mounts.mount(stock, 0);
                    var storageService = machine.getMainNode().getGrid().getStorageService();
                    storageService.addGlobalStorageProvider(mount);
                    var previousTags = ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get();
                    var previousBlacklist = ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get();
                    var previousRate = ModConfig.SINGULARITY_DUPLICATION_MATTER_PER_BLACK_HOLE.get();
                    var previousInterval = ModConfig.SINGULARITY_DUPLICATION_INTERVAL_TICKS.get();
                    var previousEnergy = ModConfig.SINGULARITY_DUPLICATION_ENERGY_PRIORITY.get();
                    try {
                        ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of("minecraft:logs"));
                        ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of("minecraft:oak_log"));
                        var expected = new HashSet<>(List.of(machine.collectionItemIds().split(",")));
                        h.assertTrue(!expected.contains("minecraft:oak_log") && expected.contains("minecraft:birch_log"), "Item tags and exclusion must drive displayed resource list");
                        var collect = SingularityBlockEntity.class.getDeclaredMethod("collectResource"); collect.setAccessible(true); collect.invoke(machine);
                        for (var item : expected) h.assertTrue(stock.amounts.getOrDefault(AEItemKey.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(item))), 0L)
                                == machine.collectionBatchSizeForMenu(), "Every configured resource must generate in the same cycle: " + item);
                        h.assertTrue(stock.amounts.getOrDefault(AEItemKey.of(Items.OAK_LOG), 0L) == 0, "Excluded resource cannot generate");
                        ModConfig.SINGULARITY_DUPLICATION_MATTER_PER_BLACK_HOLE.set(1000);
                        ModConfig.SINGULARITY_DUPLICATION_INTERVAL_TICKS.set(20);
                        ModConfig.SINGULARITY_DUPLICATION_ENERGY_PRIORITY.set(List.of("ae", "fe"));
                        var matter = AEFluidKey.of(ModFluids.SEQUENCE_MATTER.get());
                        machine.getDuplicationInventory().setItemDirect(1, new ItemStack(ModContent.BLACK_HOLE.get()));
                        var tick = SingularityBlockEntity.class.getDeclaredMethod("tickDuplication"); tick.setAccessible(true); tick.invoke(machine);
                        var time = level.getGameTime();
                        try {
                            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + 20);
                            tick.invoke(machine);
                            h.assertTrue(stock.amounts.getOrDefault(matter, 0L) == 1000 && machine.lastDuplicationAeConsumed() > 0, "One black hole must produce one configured fluid cycle and report AE use");
                            machine.getDuplicationInventory().setItemDirect(1, new ItemStack(ModContent.BLACK_HOLE.get(), 64));
                            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + 40);
                            tick.invoke(machine);
                            h.assertTrue(stock.amounts.getOrDefault(matter, 0L) == 65000, "64 black holes must scale production exactly");
                            machine.getDuplicationInventory().setItemDirect(1, ItemStack.EMPTY);
                            machine.getDuplicationInventory().setItemDirect(0, new ItemStack(Items.DIAMOND));
                            tick.invoke(machine);
                            h.assertTrue(stock.amounts.get(AEItemKey.of(Items.DIAMOND)) == 1 && stock.amounts.get(matter) == 64000,
                                    "Copy must consume precisely 1000 mB and return one item to AE");
                        } finally { ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time); }
                        System.out.println("PACK_HUB_PASS tagUnion=true blacklist=true simultaneous=true fluidAE=true black64=true copy1000mB=true");
                        var previousFe = ModConfig.SINGULARITY_DUPLICATION_FE_PER_UNIT.get();
                        var previousAe = ModConfig.SINGULARITY_DUPLICATION_AE_PER_UNIT.get();
                        try {
                            ModConfig.SINGULARITY_DUPLICATION_FE_PER_UNIT.set(32);
                            ModConfig.SINGULARITY_DUPLICATION_AE_PER_UNIT.set(8);
                            ModConfig.SINGULARITY_DUPLICATION_ENERGY_PRIORITY.set(List.of("fe", "ae"));
                            var at = pos.east(); level.setBlockAndUpdate(at, Blocks.CHEST.defaultBlockState());
                            var battery = new TestBattery(at); level.setBlockEntity(battery);
                            var consume = SingularityBlockEntity.class.getDeclaredMethod("consumeDuplicationEnergy", int.class);
                            consume.setAccessible(true);
                            var clearEnergy = SingularityBlockEntity.class.getDeclaredMethod("clearDuplicationEnergyUsage");
                            clearEnergy.setAccessible(true); clearEnergy.invoke(machine);
                            h.assertTrue((int) consume.invoke(machine, 1000) == 1000
                                    && machine.lastDuplicationFeConsumed() == 32000 && machine.lastDuplicationAeConsumed() == 0,
                                    "FE priority must consume the configured cost without AE consumption");
                            clearEnergy.invoke(machine);
                            h.assertTrue((int) consume.invoke(machine, 1000) == 1000
                                    && machine.lastDuplicationFeConsumed() == 0 && machine.lastDuplicationAeConsumed() == 8000,
                                    "An empty FE source must fall back to the configured AE cost");
                            stock.writable = false;
                            machine.getDuplicationInventory().setItemDirect(0, ItemStack.EMPTY);
                            machine.getDuplicationInventory().setItemDirect(1, new ItemStack(ModContent.BLACK_HOLE.get()));
                            tick.invoke(machine);
                            var blockedTime = level.getGameTime();
                            try {
                                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(blockedTime+20);
                                tick.invoke(machine);
                                h.assertTrue(machine.lastDuplicationFeConsumed() == 0 && machine.lastDuplicationAeConsumed() == 0,
                                        "Full storage must stop generation before consuming any energy");
                            } finally { ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(blockedTime); }
                            System.out.println("PACK_ENERGY_PASS fePriority=true aeFallback=true configuredCosts=true fullStorageNoDraw=true");
                        } finally {
                            ModConfig.SINGULARITY_DUPLICATION_FE_PER_UNIT.set(previousFe);
                            ModConfig.SINGULARITY_DUPLICATION_AE_PER_UNIT.set(previousAe);
                            stock.writable = true; level.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState());
                        }
                    } catch (Exception error) { throw new RuntimeException(error); }
                    finally {
                        storageService.removeGlobalStorageProvider(mount);
                        ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(previousTags); ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(previousBlacklist);
                        ModConfig.SINGULARITY_DUPLICATION_MATTER_PER_BLACK_HOLE.set(previousRate); ModConfig.SINGULARITY_DUPLICATION_INTERVAL_TICKS.set(previousInterval);
                        ModConfig.SINGULARITY_DUPLICATION_ENERGY_PRIORITY.set(previousEnergy);
                    }
                }).thenSucceed();
    }

    private static void force(ServerLevel level, BlockPos pos) {
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true); level.getChunkAt(pos);
    }

    private static final class Stock implements MEStorage {
        boolean writable = true;
        final Map<AEKey, Long> amounts = new LinkedHashMap<>();
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, amounts.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE) amounts.merge(key, -accepted, Long::sum);
            return accepted;
        }
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (!writable) return 0;
            if (mode == Actionable.MODULATE) amounts.merge(key, amount, Math::addExact);
            return amount;
        }
        public void getAvailableStacks(KeyCounter counter) { amounts.forEach(counter::add); }
        public Component getDescription() { return Component.literal("Isolated pack test storage"); }
    }

    private static final class TestBattery extends net.minecraft.world.level.block.entity.BlockEntity {
        private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> energy =
                net.minecraftforge.common.util.LazyOptional.of(() -> new net.minecraftforge.energy.EnergyStorage(32000, 0, 32000, 32000));
        TestBattery(BlockPos pos) { super(net.minecraft.world.level.block.entity.BlockEntityType.CHEST, pos, Blocks.CHEST.defaultBlockState()); }
        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
                net.minecraftforge.common.capabilities.Capability<T> capability, Direction side) {
            return capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY ? energy.cast() : super.getCapability(capability, side);
        }
    }
}
