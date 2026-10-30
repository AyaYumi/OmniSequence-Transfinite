package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import com.atir.molecularmanipulator.registry.ModContent;
import com.jerry.mekextras.common.tile.factory.TileEntityExtraFactory;
import com.jerry.mekextras.common.tile.factory.TileEntityExtraItemStackChemicalToItemStackFactory;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.registries.MekanismChemicals;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessDispatchMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessSpeedMode;
import java.util.List;
import java.util.Map;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import net.minecraft.network.chat.Component;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual external-inventory dispatch and powered processing through real factory recipes. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class ProcessingDispatchGameTests {
    private enum RecipeMode { SMELTING, INFUSING, CARBON_FIRST, BLOCKING }
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void extendedProviderBuffersThirteenSlotsAndHonorsBackpressure(GameTestHelper helper) {
        verifyDispatch(helper, false, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void wirelessProviderBuffersThirteenSlotsAndHonorsBackpressure(GameTestHelper helper) {
        verifyDispatch(helper, true, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40_000)
    public static void extendedProviderCompletesTenThousandChainedSmelts(GameTestHelper helper) {
        verifyDispatch(helper, false, true);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40_000)
    public static void wirelessProviderCompletesTenThousandChainedSmelts(GameTestHelper helper) {
        verifyDispatch(helper, true, true);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void extendedProviderBuffersIronAndCarbonAndHonorsBackpressure(GameTestHelper helper) {
        verifyDispatch(helper, false, false, RecipeMode.INFUSING);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40_000)
    public static void extendedProviderCompletesTenThousandInfusions(GameTestHelper helper) {
        verifyDispatch(helper, false, true, RecipeMode.INFUSING);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void extendedProviderBuffersCarbonBeforeIron(GameTestHelper helper) {
        verifyDispatch(helper, false, false, RecipeMode.CARBON_FIRST);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void extendedProviderPreservesInfusionBlockingMode(GameTestHelper helper) {
        verifyDispatch(helper, false, false, RecipeMode.BLOCKING);
    }

    private static void verifyDispatch(GameTestHelper helper, boolean wireless, boolean process) {
        verifyDispatch(helper, wireless, process, RecipeMode.SMELTING);
    }

    private static void verifyDispatch(GameTestHelper helper, boolean wireless, boolean process, RecipeMode recipe) {
        boolean infuse = recipe != RecipeMode.SMELTING;
        var level = helper.getLevel();
        var corePos = new BlockPos(1, 1, 1);
        var providerPos = new BlockPos(3, 1, 1);
        var factoryPos = wireless ? new BlockPos(3, 1, 3) : new BlockPos(4, 1, 1);
        var powerPos = new BlockPos(1, 1, 2);
        if (process) {
            var absoluteFactory = helper.absolutePos(factoryPos);
            level.setChunkForced(absoluteFactory.getX() >> 4, absoluteFactory.getZ() >> 4, true);
            level.getChunkAt(absoluteFactory);
        }
        helper.setBlock(corePos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(providerPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(
                wireless ? "ae2lt:overloaded_pattern_provider" : "extendedae:ex_pattern_provider")));
        helper.setBlock(factoryPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse(infuse
                ? "mekanism_extras:supreme_infusing_factory" : "mekanism_extras:supreme_smelting_factory")));
        helper.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(10, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(corePos);
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
            helper.assertTrue(core.getMainNode().getNode() != null && provider.getMainNode().getNode() != null,
                    "Fixture nodes must exist before connecting");
            GridHelper.createConnection(core.getActionableNode(), provider.getActionableNode());
            if (wireless) {
                var overloaded = (OverloadedPatternProviderBlockEntity) provider;
                overloaded.setProviderMode(ProviderMode.WIRELESS);
                overloaded.setWirelessSpeedMode(WirelessSpeedMode.FAST);
                overloaded.setWirelessDispatchMode(WirelessDispatchMode.EVEN_DISTRIBUTION);
                overloaded.setAdaptiveBatchEnabled(true);
                helper.assertTrue(overloaded.addOrUpdateConnection(level.dimension(),
                        helper.absolutePos(factoryPos), Direction.WEST), "Wireless target must bind");
            }
            var factory = (TileEntityExtraFactory<?>) helper.getBlockEntity(factoryPos);
            if (!factory.isSorting()) factory.toggleSorting();
            var config = factory.getConfig();
            var side = RelativeSide.fromDirections(factory.getDirection(), Direction.WEST);
            config.getConfig(TransmissionType.ITEM).setDataType(DataType.INPUT, side);
            config.sideChanged(TransmissionType.ITEM, side);
            if (infuse) {
                config.getConfig(TransmissionType.CHEMICAL).setDataType(DataType.INPUT, side);
                config.sideChanged(TransmissionType.CHEMICAL, side);
            }
            helper.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(factoryPos), Direction.WEST) != null,
                    "Configured factory side must allow external input");
        });
        helper.runAfterDelay(35, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(corePos);
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
            helper.assertTrue(core.isNetworkOnline() && provider.getMainNode().isActive(), "Real ME network must be active");
            if (infuse) {
                runInfusing(helper, core, provider, factoryPos, process, recipe);
                return;
            }
            if (process) {
                runPoweredChain(helper, core, provider, factoryPos);
                return;
            }
            var input = AEItemKey.of(Items.STONE);
            var output = AEItemKey.of(Items.SMOOTH_STONE);
            var encoded = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(input, 1)), List.of(new GenericStack(output, 1)));
            provider.getLogic().getPatternInv().setItemDirect(0, encoded);
            provider.getLogic().updatePatterns();
            ICraftingProvider.requestUpdate(provider.getMainNode());
            IPatternDetails pattern = provider.getLogic().getAvailablePatterns().getFirst();
            var service = core.getMainNode().getGrid().getCraftingService();
            long requested = 1_000_000;
            var plan = new CraftingPlan(new GenericStack(output, requested), 100, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, requested));
            var cpu = core.getCluster();
            helper.assertTrue(service.submitJob(plan, null, cpu, false, IActionSource.empty()).successful(),
                    "Native CPU order must submit without an ECO plan");
            cpu.craftingLogic.getInventory().insert(input, requested, Actionable.MODULATE);
            helper.runAfterDelay(80, () -> {
                var handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(factoryPos), Direction.WEST);
                helper.assertTrue(handler != null, "Factory input side must expose its real item handler");
                long owned = 0;
                int occupied = 0;
                int smallestBuffer = Integer.MAX_VALUE;
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    var stack = handler.getStackInSlot(slot);
                    if (stack.is(Items.STONE)) {
                        owned += stack.getCount();
                        occupied++;
                        smallestBuffer = Math.min(smallestBuffer, stack.getCount());
                    }
                }
                long waiting = cpu.craftingLogic.getWaitingFor(output);
                long retained = cpu.craftingLogic.getInventory().list.get(input);
                helper.assertTrue(occupied == 13 && smallestBuffer >= 64,
                        "All 13 real input slots must receive buffered batches, not single items: occupied=" + occupied
                                + ", owned=" + owned + ", waiting=" + waiting + ", retained=" + retained);
                helper.assertTrue(owned == waiting && retained + owned == requested,
                        "Buffered input must match CPU input and waiting accounting");
                // Providers may legitimately leave room smaller than their learned batch.
                // Fill that room from fixture stock to test a genuinely full receiver,
                // without requiring an arbitrary batch algorithm to occupy every last item.
                for (int slot = 0; slot < handler.getSlots(); slot++) {
                    handler.insertItem(slot, new net.minecraft.world.item.ItemStack(
                            Items.STONE, handler.getSlotLimit(slot)), false);
                    helper.assertTrue(!handler.insertItem(slot, Items.STONE.getDefaultInstance(), true).isEmpty(),
                            "Backpressure fixture must fill every exposed input slot");
                }
                long fullWaiting = waiting;
                helper.runAfterDelay(20, () -> {
                    helper.assertTrue(cpu.craftingLogic.getWaitingFor(output) == fullWaiting
                                    && cpu.craftingLogic.getInventory().list.get(input) == retained,
                            "Full-machine backpressure must not consume more CPU materials");
                    core.clearContent();
                    helper.succeed();
                });
            });
        });
    }

    private static void runInfusing(GameTestHelper helper, OmniComputationCoreBlockEntity core,
            PatternProviderBlockEntity provider, BlockPos factoryPos, boolean process, RecipeMode recipe) {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var carbon = MekanismKey.of(new ChemicalStack(MekanismChemicals.CARBON.get(), 1));
        var enriched = AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:enriched_iron")));
        var ingredients = recipe == RecipeMode.CARBON_FIRST
                ? List.of(new GenericStack(carbon, 10), new GenericStack(iron, 1))
                : List.of(new GenericStack(iron, 1), new GenericStack(carbon, 10));
        if (recipe == RecipeMode.BLOCKING) {
            provider.getLogic().getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
        }
        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                ingredients, List.of(new GenericStack(enriched, 1))));
        provider.getLogic().updatePatterns();
        ICraftingProvider.requestUpdate(provider.getMainNode());
        var pattern = provider.getLogic().getAvailablePatterns().getFirst();
        var stored = new KeyCounter();
        var storageNode = mountOutputStorage(helper, core, stored);
        var cpu = core.getCluster();
        var service = (CraftingService) core.getMainNode().getGrid().getCraftingService();
        long requested = process ? 10_000 : 1_000_000;
        var plan = new CraftingPlan(new GenericStack(enriched, requested), 200, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, requested));
        helper.assertTrue(service.submitJob(plan, null, cpu, false, IActionSource.empty()).successful(),
                "Native iron/carbon processing order must submit");
        cpu.craftingLogic.getInventory().insert(iron, requested, Actionable.MODULATE);
        cpu.craftingLogic.getInventory().insert(carbon, 10 * requested, Actionable.MODULATE);
        var factory = (TileEntityExtraItemStackChemicalToItemStackFactory) helper.getBlockEntity(factoryPos);
        var input = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(factoryPos), Direction.WEST);
        if (!process) {
            helper.runAfterDelay(80, () -> {
                long bufferedIron = 0;
                int occupied = 0;
                int smallestBuffer = Integer.MAX_VALUE;
                for (int slot = 0; slot < input.getSlots(); slot++) {
                    var stack = input.getStackInSlot(slot);
                    if (stack.is(Items.IRON_INGOT)) {
                        bufferedIron += stack.getCount();
                        occupied++;
                        smallestBuffer = Math.min(smallestBuffer, stack.getCount());
                    }
                }
                long waiting = cpu.craftingLogic.getWaitingFor(enriched);
                long retainedIron = cpu.craftingLogic.getInventory().list.get(iron);
                long retainedCarbon = cpu.craftingLogic.getInventory().list.get(carbon);
                long bufferedCarbon = factory.getChemicalTank().getStored();
                if (recipe == RecipeMode.BLOCKING) {
                    helper.assertTrue(bufferedIron == 1 && bufferedCarbon == 10 && waiting == 1
                                    && retainedIron == requested - 1 && retainedCarbon == 10 * (requested - 1),
                            "Enabled provider blocking must still admit only one recipe into the unpowered receiver");
                    storageNode.destroy();
                    core.clearContent();
                    helper.succeed();
                    return;
                }
                helper.assertTrue(occupied == 13 && smallestBuffer >= 64,
                        "Iron/carbon pattern must buffer all 13 processes: occupied=" + occupied
                                + ", iron=" + bufferedIron + ", carbon=" + bufferedCarbon + ", waiting=" + waiting
                                + ", retainedIron=" + retainedIron + ", retainedCarbon=" + retainedCarbon);
                helper.assertTrue(bufferedIron == waiting && bufferedCarbon == 10 * waiting
                                && retainedIron + waiting == requested && retainedCarbon + bufferedCarbon == 10 * requested,
                        "Both actual receiver inputs must match the native CPU's exact recipe ratio and waiting count");
                // Block only the chemical input while leaving item room. No part of another recipe may leave the CPU.
                factory.getChemicalTank().setStack(carbon.withAmount(factory.getChemicalTank().getCapacity()));
                helper.runAfterDelay(20, () -> {
                    helper.assertTrue(cpu.craftingLogic.getWaitingFor(enriched) == waiting
                                    && cpu.craftingLogic.getInventory().list.get(iron) == retainedIron
                                    && cpu.craftingLogic.getInventory().list.get(carbon) == retainedCarbon,
                            "A full chemical tank must backpressure the whole recipe without consuming either input");
                    // Then leave chemical room but fill every item slot from independent fixture stock.
                    factory.getChemicalTank().setEmpty();
                    for (int slot = 0; slot < input.getSlots(); slot++) {
                        input.insertItem(slot, new net.minecraft.world.item.ItemStack(Items.IRON_INGOT,
                                input.getSlotLimit(slot)), false);
                        helper.assertTrue(!input.insertItem(slot, Items.IRON_INGOT.getDefaultInstance(), true).isEmpty(),
                                "Backpressure fixture must fill each iron input slot");
                    }
                    helper.runAfterDelay(20, () -> {
                        helper.assertTrue(cpu.craftingLogic.getWaitingFor(enriched) == waiting
                                        && cpu.craftingLogic.getInventory().list.get(iron) == retainedIron
                                        && cpu.craftingLogic.getInventory().list.get(carbon) == retainedCarbon
                                        && factory.getChemicalTank().isEmpty(),
                                "Full item inputs must also stop chemical dispatch without consuming CPU stock");
                        storageNode.destroy();
                        core.clearContent();
                        helper.succeed();
                    });
                });
            });
            return;
        }
        factory.getComponent().addUpgrades(Upgrade.SPEED, 8);
        var outputSide = RelativeSide.fromDirections(factory.getDirection(), Direction.EAST);
        factory.getConfig().getConfig(TransmissionType.ITEM).setDataType(DataType.OUTPUT, outputSide);
        factory.getConfig().sideChanged(TransmissionType.ITEM, outputSide);
        var output = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(factoryPos), Direction.EAST);
        new Runnable() {
            int ticks;
            long products;
            @Override public void run() {
                if (++ticks >= 39_000) {
                    helper.fail("Powered infusion must complete: products=" + products + ", stored=" + stored
                            + ", waiting=" + cpu.craftingLogic.getWaitingFor(enriched)
                            + ", CPU=" + cpu.craftingLogic.getInventory().list
                            + ", carbon=" + factory.getChemicalTank().getStored());
                    return;
                }
                factory.getEnergyContainer().setEnergy(factory.getEnergyContainer().getMaxEnergy());
                for (int slot = 0; slot < output.getSlots(); slot++) {
                    var stack = output.extractItem(slot, Integer.MAX_VALUE, false);
                    if (stack.isEmpty()) continue;
                    var key = AEItemKey.of(stack);
                    helper.assertTrue(key.equals(enriched), "Only actual enriched iron may return from the factory");
                    products += stack.getCount();
                    long accepted = service.insertIntoCpus(key, stack.getCount(), Actionable.MODULATE);
                    stored.add(key, stack.getCount() - accepted);
                }
                if (stored.get(enriched) == requested && !cpu.isBusy()) {
                    helper.assertTrue(products == requested && factory.getChemicalTank().isEmpty(),
                            "Exactly 10,000 iron and 100,000 carbon must become 10,000 enriched iron");
                    helper.assertTrue(cpu.craftingLogic.getInventory().list.isEmpty()
                                    && cpu.craftingLogic.getWaitingFor(enriched) == 0,
                            "Completed infusion CPU must retain no inputs or waiting products");
                    for (var slot : factory.getInventorySlots(null)) {
                        helper.assertTrue(slot.isEmpty(), "Completed infusing factory must retain no items");
                    }
                    helper.assertTrue(!provider.getLogic().isBusy(), "Infusion provider must drain its owned queue");
                    storageNode.destroy();
                    core.clearContent();
                    helper.succeed();
                } else {
                    helper.runAfterDelay(1, () -> run());
                }
            }
        }.run();
    }

    private static appeng.api.networking.IManagedGridNode mountOutputStorage(GameTestHelper helper,
            OmniComputationCoreBlockEntity core, KeyCounter stored) {
        var storage = new MEStorage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) stored.add(key, amount);
                return amount;
            }
            @Override public void getAvailableStacks(KeyCounter stacks) { stacks.addAll(stored); }
            @Override public Component getDescription() { return Component.literal("Processing regression output"); }
        };
        var node = GridHelper.createManagedNode(storage, (owner, reason) -> {})
                .setInWorldNode(false).setIdlePowerUsage(0)
                .addService(IStorageProvider.class, mounts -> mounts.mount(storage));
        node.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 3)));
        GridHelper.createConnection(core.getActionableNode(), node.getNode());
        IStorageProvider.requestUpdate(node);
        return node;
    }

    private static void runPoweredChain(GameTestHelper helper, OmniComputationCoreBlockEntity core,
            PatternProviderBlockEntity provider, BlockPos factoryPos) {
        var cobblestone = AEItemKey.of(Items.COBBLESTONE);
        var stone = AEItemKey.of(Items.STONE);
        var smooth = AEItemKey.of(Items.SMOOTH_STONE);
        var inventory = provider.getLogic().getPatternInv();
        inventory.setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(cobblestone, 1)), List.of(new GenericStack(stone, 1))));
        inventory.setItemDirect(1, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(stone, 1)), List.of(new GenericStack(smooth, 1))));
        provider.getLogic().updatePatterns();
        ICraftingProvider.requestUpdate(provider.getMainNode());
        var patterns = provider.getLogic().getAvailablePatterns();
        var first = patterns.stream().filter(p -> p.getOutputs().getFirst().what().equals(stone)).findFirst().orElseThrow();
        var second = patterns.stream().filter(p -> p.getOutputs().getFirst().what().equals(smooth)).findFirst().orElseThrow();
        var stored = new KeyCounter();
        var storageNode = mountOutputStorage(helper, core, stored);
        var cpu = core.getCluster();
        var service = (CraftingService) core.getMainNode().getGrid().getCraftingService();
        var plan = new CraftingPlan(new GenericStack(smooth, 10_000), 200, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(first, 10_000L, second, 10_000L));
        helper.assertTrue(service.submitJob(plan, null, cpu, false, IActionSource.empty()).successful(),
                "Native two-stage smelting job must submit");
        cpu.craftingLogic.getInventory().insert(cobblestone, 10_000, Actionable.MODULATE);
        var factory = (TileEntityExtraFactory<?>) helper.getBlockEntity(factoryPos);
        factory.getComponent().addUpgrades(Upgrade.SPEED, 8);
        var outputSide = RelativeSide.fromDirections(factory.getDirection(), Direction.EAST);
        factory.getConfig().getConfig(TransmissionType.ITEM).setDataType(DataType.OUTPUT, outputSide);
        factory.getConfig().sideChanged(TransmissionType.ITEM, outputSide);
        var output = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(factoryPos), Direction.EAST);
        helper.assertTrue(output != null, "Factory output side must expose its real item handler");
        new Runnable() {
            long smelts;
            int ticks;
            @Override public void run() {
                if (++ticks >= 39_000) {
                    helper.fail("Powered smelting must complete: smelts=" + smelts
                            + ", stored=" + stored + ", waitingStone=" + cpu.craftingLogic.getWaitingFor(stone)
                            + ", waitingSmooth=" + cpu.craftingLogic.getWaitingFor(smooth)
                            + ", CPU=" + cpu.craftingLogic.getInventory().list
                            + ", machine=" + factory.getInventorySlots(null).stream().map(s -> s.getStack().toString()).toList()
                            + ", machineTicks=" + factory.ticker + ", ticksRequired=" + factory.getTicksRequired()
                            + ", ops=" + factory.getOperationsPerTick() + ", canFunction=" + factory.canFunction());
                    return;
                }
                // Controlled power and import-bus equivalent; recipes, machine slots,
                // processing time, CPU/provider dispatch and intermediate waits are real.
                factory.getEnergyContainer().setEnergy(factory.getEnergyContainer().getMaxEnergy());
                for (int slot = 0; slot < output.getSlots(); slot++) {
                    var stack = output.extractItem(slot, Integer.MAX_VALUE, false);
                    if (stack.isEmpty()) continue;
                    var key = AEItemKey.of(stack);
                    helper.assertTrue(key.equals(stone) || key.equals(smooth), "Only real smelting products may return");
                    smelts += stack.getCount();
                    long accepted = service.insertIntoCpus(key, stack.getCount(), Actionable.MODULATE);
                    stored.add(key, stack.getCount() - accepted);
                }
                if (stored.get(smooth) == 10_000 && !cpu.isBusy()) {
                    helper.assertTrue(smelts == 20_000 && stored.get(stone) == 0 && stored.get(cobblestone) == 0,
                            "Both real recipes must execute exactly 10,000 times");
                    helper.assertTrue(cpu.craftingLogic.getInventory().list.isEmpty()
                                    && cpu.craftingLogic.getWaitingFor(stone) == 0
                                    && cpu.craftingLogic.getWaitingFor(smooth) == 0,
                            "Completed CPU must retain no input or pending products");
                    for (var slot : factory.getInventorySlots(null)) {
                        helper.assertTrue(slot.isEmpty(), "Completed factory must retain no material or output");
                    }
                    helper.assertTrue(!provider.getLogic().isBusy(), "Provider must drain its owned queue");
                    storageNode.destroy();
                    core.clearContent();
                    helper.succeed();
                } else {
                    helper.runAfterDelay(1, () -> run());
                }
            }
        }.run();
    }
}
