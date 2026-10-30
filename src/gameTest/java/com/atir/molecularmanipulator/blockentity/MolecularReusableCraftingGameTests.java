package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.helpers.MachineSource;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerRequester;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import com.atir.molecularmanipulator.crafting.MolecularReusableInputAdapters;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpu;
import com.atir.molecularmanipulator.registry.ModContent;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual MATC recipes, finite ME stock, planning, submission, delivery and tool accounting. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularReusableCraftingGameTests {
    private static final long CRAFTS = 100_000_000L;
    private static final long CRYSTALS = 48_829L;

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void nexusExecutesCalculatedEssenceOrder(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 1, false, false, 0);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void nexusExecutesEcoEssenceOrder(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 2, true, false, 0);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void nexusExecutesEcoEssenceOrderWithBareCrystalKeys(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 3, true, true, 0);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void nexusExecutesEcoEssenceOrderWithDamagedCrystals(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 4, true, false, 2048);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void nexusExecutesSmallOrderWithMixedDamagedCrystals(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 5, false, false, 0, true);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void nexusExecutesEcoSmallOrderWithMixedDamagedCrystals(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 6, true, false, 0, true);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void nexusExecutesNativeOrdersOnSelectedVirtualCpu(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 7, false, false, 0, new OrderOptions(true, 2, true, false));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void nexusExecutesNativeOrderWithAutomaticCpuSelection(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 8, false, false, 0, new OrderOptions(true, 1, false, false));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void nexusExecutesNativeEssenceChainFromInferium(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 9, false, false, 0, new OrderOptions(false, 1, false, true));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void omniCoreExecutesNativeOrderWithMixedDamagedCrystals(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 10, false, false, 0, new OrderOptions(true, 1, true, false, true));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void nexusExecutesNativeOrderWithTerminalCrystal(GameTestHelper helper) {
        calculatedEssenceOrder(helper, 11, false, false, 0,
                new OrderOptions(false, 1, true, false, false, true));
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void nexusResumesPersistedNativeEssenceOrder(GameTestHelper helper) {
        final CompoundTag saved;
        try (var resource = MolecularReusableCraftingGameTests.class
                .getResourceAsStream("/native_cpu_stalled_essence_order.snbt")) {
            saved = TagParser.parseTag(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception failure) { throw new AssertionError(failure); }
        var level = helper.getLevel();
        var origin = new BlockPos(10440, 100, 808);
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        level.getChunkAt(origin);
        level.setBlock(origin, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        level.setBlock(origin.east(), ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState(), 3);
        level.setBlock(origin.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var nexus = (OmniComputationCoreBlockEntity) level.getBlockEntity(origin);
        var machine = (MolecularManipulatorBlockEntity) level.getBlockEntity(origin.east());
        var encoded = saved.getCompound("job").getList("tasks", Tag.TAG_COMPOUND).getCompound(0).copy();
        encoded.putByte("Count", (byte) 1);
        machine.getLogic().getPatternInv().setItemDirect(0, ItemStack.of(encoded));
        var stock = new Stock();
        var storageNode = new IManagedGridNode[1];
        var result = item("mysticalagradditions:insanium_essence");
        var essence = item("mysticalagriculture:supremium_essence");
        var crystal = item("matc:supremium_crystal");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(nexus.isNetworkOnline()
                        && machine.getMainNode().isActive() && machine.getLogic().getAvailablePatterns().size() == 1,
                        "The saved native order's exact pattern must publish"))
                .thenExecute(() -> {
                    storageNode[0] = GridHelper.createManagedNode(stock, (owner, changed) -> {})
                            .setInWorldNode(false).setIdlePowerUsage(0)
                            .addService(IStorageProvider.class, mounts -> mounts.mount(stock));
                    storageNode[0].create(level, origin.above());
                    GridHelper.createConnection(storageNode[0].getNode(), nexus.getMainNode().getNode());
                    IStorageProvider.requestUpdate(storageNode[0]);
                    nexus.getCluster().craftingLogic.readFromNBT(saved.copy());
                    helper.assertTrue(nexus.getCluster().isBusy(), "Restore the actual stalled native CPU task");
                }).thenWaitUntil(() -> {
                    helper.assertTrue(stock.values.get(result) == 1000 && !nexus.getCluster().isBusy(),
                            "The persisted native order must deliver all 1000 insanium; output=" + stock.values.get(result));
                    helper.assertTrue(stock.values.get(essence) == 0 && remainingUses(stock, crystal) == 3098,
                            "Restore and conserve all 4000 essence and both crystals' exact durability");
                }).thenExecute(() -> {
                    storageNode[0].destroy();
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40)
    public static void terminalCrystalPrototypeAcceptsTheSameReusableWearPool(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(
                new ResourceLocation("matc:insanium_essence")).orElseThrow();
        var inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);
        for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
            var choices = recipe.getIngredients().get(slot).getItems();
            if (choices.length > 0) inputs[slot] = Arrays.stream(choices)
                    .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("matc"))
                    .findFirst().orElse(choices[0]).copy();
        }
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeCraftingPattern(
                recipe, inputs, recipe.getResultItem(level.registryAccess()), true, true), level);
        var fresh = item("matc:supremium_crystal");
        var toolInput = Arrays.stream(pattern.getInputs()).filter(input -> Arrays.stream(input.getPossibleInputs())
                .anyMatch(stack -> stack.what() instanceof AEItemKey key && key.getItem() == fresh.getItem()))
                .findFirst().orElseThrow();
        var terminalStack = fresh.toStack();
        terminalStack.setDamageValue(terminalStack.getMaxDamage());
        var terminal = AEItemKey.of(terminalStack);
        helper.assertTrue(MolecularReusableInputAdapters.analyze(toolInput, terminal, level, 1).finalKey() == null,
                "The real MATC prototype must be on its terminal no-remainder use");
        helper.assertTrue(MolecularReusableInputAdapters.isDeterministicDamageVariant(toolInput, terminal, fresh, level)
                        && MolecularReusableInputAdapters.isDeterministicDamageVariant(toolInput, fresh, terminal, level),
                "A terminal extracted prototype and a usable crystal must share the same validated wear pool");
        var foreign = fresh.toStack();
        foreign.getOrCreateTag().putString("essenceRegression", "different components");
        helper.assertTrue(!MolecularReusableInputAdapters.isDeterministicDamageVariant(
                        toolInput, terminal, AEItemKey.of(foreign), level),
                "Non-damage NBT must remain pinned when the prototype is terminal");
        var unbreakable = fresh.toStack();
        unbreakable.getOrCreateTag().putBoolean("Unbreakable", true);
        helper.assertTrue(!MolecularReusableInputAdapters.isDeterministicDamageVariant(
                        toolInput, terminal, AEItemKey.of(unbreakable), level),
                "An unbreakable crystal cannot enter a finite durability pool");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void nexusCompletesNativeSmithingTemplateCycle(GameTestHelper helper) {
        smithingTemplateCycle(helper, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500)
    public static void nexusCompletesEcoSmithingTemplateCycle(GameTestHelper helper) {
        smithingTemplateCycle(helper, true);
    }

    private static void smithingTemplateCycle(GameTestHelper helper, boolean eco) {
        var level = helper.getLevel();
        var origin = new BlockPos(10824 + (eco ? 64 : 0), 100, 808);
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        level.getChunkAt(origin);
        level.setBlock(origin, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        level.setBlock(origin.east(), ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState(), 3);
        level.setBlock(origin.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var nexus = (OmniComputationCoreBlockEntity) level.getBlockEntity(origin);
        var machine = (MolecularManipulatorBlockEntity) level.getBlockEntity(origin.east());
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(
                new ResourceLocation("minecraft:netherite_upgrade_smithing_template")).orElseThrow();
        var diamond = AEItemKey.of(Items.DIAMOND);
        var template = AEItemKey.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        var netherrack = AEItemKey.of(Items.NETHERRACK);
        var inputs = new ItemStack[]{diamond.toStack(), template.toStack(), diamond.toStack(),
                diamond.toStack(), netherrack.toStack(), diamond.toStack(),
                diamond.toStack(), diamond.toStack(), diamond.toStack()};
        machine.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeCraftingPattern(
                recipe, inputs, recipe.getResultItem(level.registryAccess()), false, true));
        var stock = new Stock();
        stock.values.add(template, 1);
        stock.values.add(diamond, 7000);
        stock.values.add(netherrack, 1000);
        var storageNode = new IManagedGridNode[1];
        var future = new AtomicReference<Future<ICraftingPlan>>();
        var crafts = new AtomicLong();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(nexus.isNetworkOnline()
                        && machine.getMainNode().isActive() && machine.getLogic().getAvailablePatterns().size() == 1,
                        "The actual vanilla template duplication pattern must publish"))
                .thenExecute(() -> {
                    storageNode[0] = GridHelper.createManagedNode(stock, (owner, changed) -> {})
                            .setInWorldNode(false).setIdlePowerUsage(0)
                            .addService(IStorageProvider.class, mounts -> mounts.mount(stock));
                    storageNode[0].create(level, origin.above());
                    GridHelper.createConnection(storageNode[0].getNode(), nexus.getMainNode().getNode());
                    IStorageProvider.requestUpdate(storageNode[0]);
                }).thenIdle(5).thenExecute(() -> {
                    ICraftingSimulationRequester requester = () -> new MachineSource(nexus);
                    if (eco) requester = new ECOPlannerRequester(requester,
                            new ECOPlannerOptions(true, false, Set.of(), true));
                    future.set(nexus.getMainNode().getGrid().getCraftingService().beginCraftingCalculation(level,
                            requester, template, 1000, CalculationStrategy.REPORT_MISSING_ITEMS));
                }).thenWaitUntil(() -> helper.assertTrue(future.get().isDone(), "The template cycle planner must finish"))
                .thenExecute(() -> {
                    try {
                        var plan = future.get().get();
                        helper.assertTrue(!plan.simulation(), "One seed and finite vanilla ingredients must cover the cycle");
                        if (ModList.get().isLoaded("neoecoae")) helper.assertTrue(
                                (ECOPlanningResultRegistry.findExact(plan) != null) == eco,
                                "The regression must use its selected native or ECO planner");
                        crafts.set(plan.patternTimes().values().stream().mapToLong(Long::longValue).sum());
                        var submitted = nexus.getMainNode().getGrid().getCraftingService().submitJob(plan, null,
                                nexus.getCluster(), false, new MachineSource(nexus));
                        helper.assertTrue(submitted.successful(), "The 1000-template cycle must submit: " + submitted);
                    } catch (Exception failure) { throw new AssertionError(failure); }
                }).thenWaitUntil(() -> helper.assertTrue(!nexus.getCluster().isBusy(),
                        "The completed standalone template cycle must release its CPU"))
                .thenIdle(5).thenExecute(() -> {
                    var logic = nexus.getCluster().craftingLogic;
                    helper.assertTrue(logic.getFinalJobOutput() == null && logic.getInventory().list.isEmpty()
                                    && !machine.getLogic().isBusy(),
                            "Completed cycles must leave no visible task, retained seed or provider work");
                    helper.assertTrue(crafts.get() > 0 && stock.values.get(template) == 1 + crafts.get()
                                    && stock.values.get(template) >= 1000
                                    && stock.values.get(diamond) == 7000 - 7 * crafts.get()
                                    && stock.values.get(netherrack) == 1000 - crafts.get(),
                            "Conserve the original template and every actual vanilla recipe firing");
                    storageNode[0].destroy();
                }).thenSucceed();
    }

    private record OrderOptions(boolean mixedDamage, int orders, boolean selectCpu,
            boolean fromInferium, boolean multiblock, boolean terminalCrystal) {
        private OrderOptions(boolean mixedDamage, int orders, boolean selectCpu, boolean fromInferium) {
            this(mixedDamage, orders, selectCpu, fromInferium, false, false);
        }
        private OrderOptions(boolean mixedDamage, int orders, boolean selectCpu,
                boolean fromInferium, boolean multiblock) {
            this(mixedDamage, orders, selectCpu, fromInferium, multiblock, false);
        }
    }

    private static void calculatedEssenceOrder(GameTestHelper helper, int lane, boolean eco,
            boolean bareCrystalKeys, int damage) {
        calculatedEssenceOrder(helper, lane, eco, bareCrystalKeys, damage, false);
    }

    private static void calculatedEssenceOrder(GameTestHelper helper, int lane, boolean eco,
            boolean bareCrystalKeys, int damage, boolean mixedDamage) {
        calculatedEssenceOrder(helper, lane, eco, bareCrystalKeys, damage, new OrderOptions(mixedDamage, 1, true, false));
    }

    private static void calculatedEssenceOrder(GameTestHelper helper, int lane, boolean eco,
            boolean bareCrystalKeys, int damage, OrderOptions options) {
        boolean mixedDamage = options.mixedDamage();
        int orders = options.orders();
        long crafts = options.terminalCrystal() ? 1_000L
                : mixedDamage || options.fromInferium() ? 5_000L : CRAFTS;
        long totalCrafts = crafts * orders;
        var level = helper.getLevel();
        var origin = new BlockPos(9608 + lane * 64, 100, 808);
        level.setChunkForced(origin.getX() >> 4, origin.getZ() >> 4, true);
        level.getChunkAt(origin);
        var machinePos = options.multiblock() ? origin.north(40) : origin.east();
        var powerPos = options.multiblock() ? machinePos.west() : origin.west();
        // These fixtures also work when the disposable test world is reused.
        for (var position : new BlockPos[]{origin, machinePos, powerPos}) {
            level.setChunkForced(position.getX() >> 4, position.getZ() >> 4, true);
            level.getChunkAt(position);
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
        if (options.multiblock()) {
            for (var chunk : OmniComputationStructure.chunkFootprint(origin, Direction.NORTH,
                    OmniComputationStructure.StructureLayout.CURRENT)) {
                level.setChunkForced(chunk.x, chunk.z, true);
                level.getChunk(chunk.x, chunk.z);
            }
            for (var part : OmniComputationStructure.parts()) {
                if (part.type() != OmniComputationStructure.PartType.CONTROLLER)
                    level.setBlock(OmniComputationStructure.worldPos(origin, Direction.NORTH, part),
                            OmniComputationStructure.block(part.type()).defaultBlockState(), 3);
            }
            level.setBlock(origin, ModContent.OMNI_COMPUTATION_CONTROLLER.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        } else level.setBlock(origin, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        level.setBlock(machinePos, ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState(), 3);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var nexus = (OmniComputationCoreBlockEntity) level.getBlockEntity(origin);
        var machine = (MolecularManipulatorBlockEntity) level.getBlockEntity(machinePos);
        if (options.multiblock()) helper.runAfterDelay(5, () -> {
            nexus.refreshStructureNow();
            GridHelper.createConnection(nexus.getMainNode().getNode(), machine.getMainNode().getNode());
        });
        var stock = new Stock();
        var essence = item("mysticalagriculture:supremium_essence");
        var result = item("mysticalagradditions:insanium_essence");
        var crystalStack = item("matc:supremium_crystal").toStack();
        crystalStack.setDamageValue(damage);
        var crystal = bareCrystalKeys ? AEItemKey.of(crystalStack.getItem()) : AEItemKey.of(crystalStack);
        var startingEssence = options.fromInferium() ? item("mysticalagriculture:inferium_essence") : essence;
        long startingAmount = totalCrafts * (options.fromInferium() ? 1024 : 4);
        stock.values.add(startingEssence, startingAmount);
        if (options.terminalCrystal()) {
            var terminal = crystalStack.copy();
            terminal.setDamageValue(4096);
            stock.values.add(AEItemKey.of(terminal), 1);
            stock.values.add(crystal, 1);
        } else if (mixedDamage) {
            for (int used : new int[]{1, 1561, 2150}) {
                var usedCrystal = crystalStack.copy();
                usedCrystal.setDamageValue(used);
                stock.values.add(AEItemKey.of(usedCrystal), orders == 1 ? 1 : orders * 3L);
            }
        } else stock.values.add(crystal, CRYSTALS);
        long initialUses = remainingUses(stock, crystal);
        String[] lowerTiers = {"inferium", "prudentium", "tertium", "imperium"};
        var initialTierUses = new long[lowerTiers.length];
        for (int tier = 0; tier < lowerTiers.length; tier++) {
            var key = item("matc:" + lowerTiers[tier] + "_crystal");
            if (!options.terminalCrystal()) stock.values.add(key, options.fromInferium() ? CRYSTALS : 1);
            initialTierUses[tier] = remainingUses(stock, key);
        }
        String[] tiers = {"prudentium", "tertium", "imperium", "supremium", "insanium"};
        int index = 0;
        for (String tier : tiers) {
            for (String recipeId : new String[]{"matc:" + tier + "_essence",
                    (tier.equals("insanium") ? "mysticalagradditions:" : "mysticalagriculture:") + tier + "_essence_uncraft"}) {
                var recipe = (CraftingRecipe) level.getRecipeManager().byKey(new ResourceLocation(recipeId)).orElseThrow();
                var inputs = new ItemStack[9];
                Arrays.fill(inputs, ItemStack.EMPTY);
                for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
                    var choices = recipe.getIngredients().get(slot).getItems();
                    if (choices.length == 0) continue;
                    inputs[slot] = Arrays.stream(choices).filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem())
                            .getNamespace().equals("matc")).findFirst().orElse(choices[0]).copy();
                }
                machine.getLogic().getPatternInv().setItemDirect(index++, PatternDetailsHelper.encodeCraftingPattern(
                        recipe, inputs, recipe.getResultItem(level.registryAccess()), true, options.terminalCrystal()));
            }
        }
        var future = new AtomicReference<Future<ICraftingPlan>>();
        var storageNode = new IManagedGridNode[1];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(nexus.isNetworkOnline() && machine.getMainNode().isActive()
                        && machine.getLogic().getAvailablePatterns().size() == 10, "All real essence upgrade/downgrade patterns must publish"))
                .thenExecute(() -> {
                    helper.assertTrue(initialUses >= totalCrafts, "Actual MATC durability must cover the full order");
                    helper.assertTrue(!nexus.getCluster().isBusy(), "Each regression starts with a fresh CPU");
                    storageNode[0] = GridHelper.createManagedNode(stock, (owner, changed) -> {})
                            .setInWorldNode(false).setIdlePowerUsage(0)
                            .addService(IStorageProvider.class, mounts -> mounts.mount(stock));
                    storageNode[0].create(level, origin.above());
                    GridHelper.createConnection(storageNode[0].getNode(), nexus.getMainNode().getNode());
                    IStorageProvider.requestUpdate(storageNode[0]);
                }).thenIdle(5).thenExecute(() -> {
                    ICraftingSimulationRequester requester = () -> new MachineSource(nexus);
                    if (eco) requester = new ECOPlannerRequester(requester,
                            new ECOPlannerOptions(true, false, Set.of(), true));
                    future.set(nexus.getMainNode().getGrid().getCraftingService().beginCraftingCalculation(level,
                            requester, result, crafts, CalculationStrategy.REPORT_MISSING_ITEMS));
                }).thenWaitUntil(() -> helper.assertTrue(future.get().isDone(), "Actual essence planner must finish"))
                .thenExecute(() -> {
                    try {
                        var plan = future.get().get();
                        helper.assertTrue(!plan.simulation(), "Actual essence order must have sufficient materials");
                        if (ModList.get().isLoaded("neoecoae")) {
                            var planned = ECOPlanningResultRegistry.findExact(plan);
                            if (eco) helper.assertTrue(planned != null, "This regression must execute an actual ECO-planned order");
                            else helper.assertTrue(planned == null, "Native CPU regressions must not execute ECO plans");
                        }
                        for (int order = 0; order < orders; order++) {
                            var target = order == 0 ? nexus.getCluster() : nexus.allCpus().stream()
                                    .filter(cpu -> cpu != nexus.getCluster() && !cpu.isBusy()).findFirst().orElseThrow();
                            if (order > 0) helper.assertTrue(OmniComputationCoreBlockEntity.ownerOf(target) == nexus,
                                    "The selected virtual CPU must belong to this Nexus");
                            var submitted = nexus.getMainNode().getGrid().getCraftingService().submitJob(plan, null,
                                    options.selectCpu() ? target : null, false, new MachineSource(nexus));
                            helper.assertTrue(submitted.successful(),
                                    "Calculated essence order must submit to the selected native CPU: " + submitted);
                        }
                        helper.assertTrue(nexus.allCpus().stream().filter(cpu -> cpu.isBusy()).count() == orders,
                                "Every order must occupy a distinct Nexus CPU lane");
                        if (eco) {
                            var changedStack = crystal.toStack();
                            changedStack.getOrCreateTag().putString("essenceRegression", "different components");
                            var changedKey = AEItemKey.of(changedStack);
                            var inventory = new ListCraftingInventory(ignored -> {});
                            inventory.insert(essence, 4, Actionable.MODULATE);
                            inventory.insert(changedKey, 1, Actionable.MODULATE);
                            var execution = ((EcoOmniPlanCpu) nexus.getCluster().craftingLogic).ecoOmni$getExecution();
                            var dispatch = execution.begin(plan.patternTimes().keySet().iterator().next());
                            var guarded = dispatch.guard(inventory, key -> BigInteger.valueOf(inventory.list.get(key)));
                            helper.assertTrue(dispatch.extractInputs(guarded, level, new KeyCounter(), new KeyCounter()) == null,
                                    "A reusable allocation must reject a crystal with different non-damage components");
                            helper.assertTrue(inventory.list.get(essence) == 4 && inventory.list.get(changedKey) == 1,
                                    "Rejected allocation probes must return every physical ingredient");
                        }
                    } catch (Exception failure) { throw new AssertionError(failure); }
                }).thenWaitUntil(() -> {
                    helper.assertTrue(stock.values.get(result) == totalCrafts && nexus.allCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                            "Calculated order must finish with exactly " + totalCrafts + " insanium essence; output=" + stock.values.get(result));
                    helper.assertTrue(remainingUses(stock, crystal) == initialUses - totalCrafts,
                            "Every crystal must be consumed or returned at its exact remaining durability");
                }).thenExecute(() -> {
                    helper.assertTrue(stock.values.get(startingEssence) == 0,
                            "Consume exactly " + startingAmount + " starting essence");
                    for (int tier = 0; tier < lowerTiers.length; tier++) {
                        var key = item("matc:" + lowerTiers[tier] + "_crystal");
                        long used = options.fromInferium() ? totalCrafts * (1L << (2 * (4 - tier))) : 0;
                        helper.assertTrue(remainingUses(stock, key) == initialTierUses[tier] - used,
                                "Account for every use of " + lowerTiers[tier] + " crystals");
                    }
                    storageNode[0].destroy();
                }).thenSucceed();
    }

    private static long remainingUses(Stock stock, AEItemKey template) {
        long uses = 0;
        for (var entry : stock.values) {
            if (entry.getKey().getPrimaryKey().equals(template.getPrimaryKey())) {
                uses = Math.addExact(uses, Math.multiplyExact(entry.getLongValue(),
                        MolecularReusableInputAdapters.physicalUses(entry.getKey())));
            }
        }
        return uses;
    }

    private static final class Stock implements MEStorage {
        final KeyCounter values = new KeyCounter();
        @Override public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            long taken = Math.min(Math.max(0, requested), values.get(key));
            if (mode == Actionable.MODULATE) values.add(key, -taken);
            return taken;
        }
        @Override public long insert(AEKey key, long offered, Actionable mode, IActionSource source) {
            long taken = Math.min(Math.max(0, offered), Long.MAX_VALUE - values.get(key));
            if (mode == Actionable.MODULATE) values.add(key, taken);
            return taken;
        }
        @Override public void getAvailableStacks(KeyCounter counter) { counter.addAll(values); }
        @Override public Component getDescription() { return Component.literal("Essence regression stock"); }
    }

    private static AEItemKey item(String id) {
        var location = new ResourceLocation(id);
        if (!BuiltInRegistries.ITEM.containsKey(location)) throw new AssertionError("Missing reference-pack item: " + id);
        return AEItemKey.of(BuiltInRegistries.ITEM.get(location).getDefaultInstance());
    }
}
