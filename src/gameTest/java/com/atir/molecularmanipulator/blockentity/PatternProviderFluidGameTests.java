package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityTarget;
import com.glodblock.github.extendedae.common.EAESingletons;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

@GameTestHolder(MolecularManipulator.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class PatternProviderFluidGameTests {
    private static final Map<BlockPos, FixtureStorage> STORAGE = new ConcurrentHashMap<>();

    private record FixtureStorage(Level level, ItemStackHandler items, FluidTank tank) {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, entity, side) -> {
            var storage = storageAt(level, pos, side);
            return storage == null ? null : storage.items();
        }, Blocks.POLISHED_ANDESITE);
        event.registerBlock(Capabilities.FluidHandler.BLOCK, (level, pos, state, entity, side) -> {
            var storage = storageAt(level, pos, side);
            return storage == null ? null : storage.tank();
        }, Blocks.POLISHED_ANDESITE);
    }

    private static @Nullable FixtureStorage storageAt(Level level, BlockPos pos, @Nullable Direction side) {
        if (side != Direction.EAST) return null;
        var storage = STORAGE.get(pos);
        return storage != null && storage.level() == level ? storage : null;
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void jointItemAndFluidCapacity(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(),
                PatternProviderBlockEntity.class);
        var items = new ItemStackHandler(1);
        items.setStackInSlot(0, new ItemStack(Items.COAL, 31));
        var tank = new FluidTank(AEFluidKey.AMOUNT_BUCKET);
        var destination = placeStorage(fixture, items, tank);

        var logic = provider.getLogic();
        var capacity = (OmniCapacityTarget) logic;
        var coal = AEItemKey.of(Items.COAL);
        var water = AEFluidKey.of(Fluids.WATER);
        var lava = AEFluidKey.of(Fluids.LAVA);
        var mixedInputs = List.of(new GenericStack(coal, 2), new GenericStack(water, 250));
        var crafts = BigInteger.valueOf(3);

        powerProvider(fixture, provider)
                .thenExecute(() -> {
                    helper.assertTrue(fixture.level.getCapability(Capabilities.ItemHandler.BLOCK, destination, Direction.EAST) == items
                                    && fixture.level.getCapability(Capabilities.FluidHandler.BLOCK, destination, Direction.EAST) == tank,
                            "The provider's target should expose the fixture's actual NeoForge handlers");
                    var competing = install(fixture, logic, List.of(new GenericStack(water, 250), new GenericStack(lava, 125)),
                            BigInteger.TEN);
                    try (var admission = capacity.omniDispatch$prepareTarget(competing)) {
                        helper.assertTrue(admission != null && admission.maximumCrafts().signum() == 0,
                                "Water and lava cannot share the one empty tank");
                    }
                    assertDrained(fixture, logic);
                    helper.assertTrue(tank.isEmpty() && items.getStackInSlot(0).getCount() == 31,
                            "A failed joint probe should leave the empty tank and the item slot unchanged");

                    tank.setFluid(new FluidStack(Fluids.WATER, 100));
                    var mixed = install(fixture, logic, mixedInputs, BigInteger.TEN);
                    try (var admission = capacity.omniDispatch$prepareTarget(mixed)) {
                        helper.assertTrue(admission != null && crafts.equals(admission.maximumCrafts()),
                                "Nine hundred mB of water room should limit the pair to three whole crafts");
                        assertDrained(fixture, logic);
                        assertContents(helper, items, tank, 31, 100);
                        helper.assertTrue(crafts.equals(admission.submit(crafts)), "Submit should accept all three crafts");
                    }
                    assertContents(helper, items, tank, 31, 100);
                    assertWestQueue(fixture, logic, new GenericStack(coal, 6), new GenericStack(water, 750));
                }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "AE2 should drain the mixed queue"))
                .thenExecute(() -> {
                    assertContents(helper, items, tank, 37, 850);
                    assertDrained(fixture, logic);

                    // The subclass retains normal insertion behavior but has no supported slot-layout proof.
                    var nativeItems = new ItemStackHandler(1) {};
                    tank.setFluid(FluidStack.EMPTY);
                    STORAGE.put(destination, new FixtureStorage(fixture.level, nativeItems, tank));
                    fixture.level.invalidateCapabilities(destination);
                    var mixed = install(fixture, logic, mixedInputs, BigInteger.TEN);
                    try (var admission = capacity.omniDispatch$prepareTarget(mixed)) {
                        helper.assertTrue(admission == null, "The unproven handler should leave dispatch to native AE2");
                    }
                    helper.assertTrue(tank.isEmpty() && nativeItems.getStackInSlot(0).isEmpty(),
                            "The unsupported probe should leave both handlers empty");
                    helper.assertTrue(logic.pushPattern(mixed.pattern(), mixed.unitInputs()),
                            "Native AE2 should push one craft through the same capabilities");
                    assertContents(helper, nativeItems, tank, 2, 250);
                    assertDrained(fixture, logic);
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void mixedBatchUsesAllSlotsAndDeliversBulkFluid(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(),
                PatternProviderBlockEntity.class);
        var logic = provider.getLogic();
        var items = new ItemStackHandler(2_048);
        int copies = items.getSlots() / 2 * 64;
        int waterPerCraft = 250;
        var tank = new FluidTank((copies + 1) * waterPerCraft);
        placeStorage(fixture, items, tank);
        var coal = AEItemKey.of(Items.COAL);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var water = AEFluidKey.of(Fluids.WATER);
        var inputs = List.of(new GenericStack(coal, 1), new GenericStack(gold, 1), new GenericStack(water, waterPerCraft));
        var crafts = BigInteger.valueOf(copies);

        powerProvider(fixture, provider).thenExecute(() -> {
            var request = install(fixture, logic, inputs, BigInteger.ONE.shiftLeft(80));
            try (var admission = ((OmniCapacityTarget) logic).omniDispatch$prepareTarget(request)) {
                helper.assertTrue(admission != null && crafts.equals(admission.maximumCrafts()),
                        "The two item keys should each fill 1,024 slots, limiting the batch to 65,536 crafts");
                helper.assertTrue(crafts.equals(admission.submit(crafts)), "One submit should accept the complete mixed batch");
            }
            assertWestQueue(fixture, logic, new GenericStack(coal, copies), new GenericStack(gold, copies),
                    new GenericStack(water, (long) copies * waterPerCraft));
        }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "AE2 should drain all three inputs"))
                .thenExecute(() -> {
                    for (int slot = 0; slot < items.getSlots(); slot++) {
                        var stack = items.getStackInSlot(slot);
                        var expected = slot < items.getSlots() / 2 ? Items.COAL : Items.GOLD_INGOT;
                        helper.assertTrue(stack.is(expected) && stack.getCount() == 64, "Incorrect delivered stack in slot " + slot);
                    }
                    helper.assertTrue(tank.getFluid().getFluid() == Fluids.WATER && tank.getFluidAmount() == copies * waterPerCraft,
                            "The target should receive exactly 16,384,000 mB of water alongside the items");
                    assertDrained(fixture, logic);
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void extendedInterfaceUsesItsFilteredAndSharedSlots(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var state = AEBlocks.PATTERN_PROVIDER.block().defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.WEST);
        var provider = fixture.place(fixture.origin, state, PatternProviderBlockEntity.class);
        var destination = fixture.place(fixture.origin.west(), EAESingletons.EX_INTERFACE.defaultBlockState(), InterfaceBlockEntity.class);
        var logic = provider.getLogic();
        var config = destination.getInterfaceLogic().getConfig();
        var storage = destination.getInterfaceLogic().getStorage();
        var water = AEFluidKey.of(Fluids.WATER);
        var lava = AEFluidKey.of(Fluids.LAVA);
        config.setStack(0, new GenericStack(water, 1));
        config.setStack(1, new GenericStack(lava, 1));
        for (int slot = 3; slot < config.size(); slot++) config.setStack(slot, new GenericStack(AEItemKey.of(Items.COAL), 1));
        storage.setCapacity(water.getType(), 10_000);
        var copies = BigInteger.valueOf(5_000);
        powerProvider(fixture, provider).thenExecute(() -> {
            var request = install(fixture, logic, List.of(new GenericStack(water, 2), new GenericStack(lava, 3)), BigInteger.ONE.shiftLeft(80));
            try (var admission = ((OmniCapacityTarget) logic).omniDispatch$prepareTarget(request)) {
                helper.assertTrue(admission != null && copies.equals(admission.maximumCrafts()) && copies.equals(admission.submit(copies)),
                        "Water should leave the shared slot to lava at 5,000 crafts and claim it at 5,001");
            }
            helper.assertTrue(storage.isEmpty(), "Admitted inputs should remain in the provider until its next tick");
            assertWestQueue(fixture, logic, new GenericStack(water, 10_000), new GenericStack(lava, 15_000));
        }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "Both fluids should drain into the configured interface"))
                .thenExecute(() -> {
                    helper.assertTrue(new GenericStack(water, 10_000).equals(storage.getStack(0))
                                    && new GenericStack(lava, 10_000).equals(storage.getStack(1))
                                    && new GenericStack(lava, 5_000).equals(storage.getStack(2)), "Only lava should occupy the shared slot");
                    assertDrained(fixture, logic);
                }).thenSucceed();
    }

    @GameTestGenerator
    public static List<TestFunction> appliedMekanisticsTests() {
        if (!ModList.get().isLoaded("appmek")) return List.of();
        return List.of(new TestFunction("defaultBatch", "reactionchamberusesallthreeinputs",
                MolecularManipulator.MOD_ID + ":" + ServerRegressionFixture.TEMPLATE, 200, 0, true,
                PatternProviderFluidGameTests::reactionChamberUsesAllThreeInputs),
                chemicalMachineTest("chemical_injection_chamber", Items.DIRT, "water_vapor", "minecraft:mud", 1),
                chemicalMachineTest("osmium_compressor", Items.GLOWSTONE_DUST, "osmium", "mekanism:ingot_refined_glowstone", 1),
                chemicalMachineTest("purification_chamber", Items.RAW_IRON, "oxygen", "mekanism:clump_iron", 2));
    }

    private static TestFunction chemicalMachineTest(String machineId, Item ingredient, String chemicalId, String outputId, int outputCount) {
        return new TestFunction("defaultBatch", machineId + "usesbothinputs", MolecularManipulator.MOD_ID + ":" + ServerRegressionFixture.TEMPLATE,
                200, 0, true, helper -> {
                    var fixture = new ServerRegressionFixture(helper);
                    var state = AEBlocks.PATTERN_PROVIDER.block().defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.WEST);
                    var provider = fixture.place(fixture.origin, state, PatternProviderBlockEntity.class);
                    var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", machineId));
                    var machine = fixture.place(fixture.origin.west(), block.defaultBlockState());
                    configureMekanismInputs(machine, Direction.EAST, "ITEM", "CHEMICAL");
                    var item = AEItemKey.of(ingredient);
                    var chemical = chemicalKey(fixture, chemicalId);
                    var product = new GenericStack(AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(outputId))), outputCount);
                    var logic = provider.getLogic();
                    var copies = BigInteger.valueOf(37);
                    powerProvider(fixture, provider).thenExecute(() -> {
                        var face = PatternProviderTarget.get(fixture.level, machine.getBlockPos(), machine, Direction.EAST, IActionSource.empty());
                        helper.assertTrue(face != null, "The selected machine face must expose storage");
                        long seed = face.insert(chemical, Long.MAX_VALUE, Actionable.SIMULATE) - 37;
                        helper.assertTrue(seed > 0 && face.insert(chemical, seed, Actionable.MODULATE) == seed, "Leave 37 units of tank room");
                        var request = install(fixture, logic, List.of(new GenericStack(item, 1), new GenericStack(chemical, 1)),
                                List.of(product), BigInteger.ONE.shiftLeft(80));
                        try (var admission = ((OmniCapacityTarget) logic).omniDispatch$prepareTarget(request)) {
                            helper.assertTrue(admission != null && copies.equals(admission.maximumCrafts()) && copies.equals(admission.submit(copies)),
                                    "One submission should reserve all 37 complete input pairs");
                        }
                        assertWestQueue(fixture, logic, new GenericStack(item, 37), new GenericStack(chemical, 37));
                    }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "The machine should accept the complete queue"))
                            .thenExecute(() -> {
                                var face = PatternProviderTarget.get(fixture.level, machine.getBlockPos(), machine, Direction.EAST, IActionSource.empty());
                                var items = fixture.level.getCapability(Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), Direction.EAST);
                                helper.assertTrue(items != null && item.matches(items.getStackInSlot(0)) && items.getStackInSlot(0).getCount() == 37
                                                && face != null && face.insert(chemical, 1, Actionable.SIMULATE) == 0,
                                        "All 37 items should arrive and the chemical tank should be full");
                            }).thenSucceed();
                });
    }

    private static AEKey chemicalKey(ServerRegressionFixture fixture, String id) {
        var data = new CompoundTag();
        data.putString(AEKey.TYPE_FIELD, "appmek:chemical");
        data.putString("id", "mekanism:" + id);
        var key = AEKey.fromTagGeneric(fixture.level.registryAccess(), data);
        fixture.helper.assertTrue(key != null, "Applied Mekanistics should decode " + id);
        return key;
    }

    private static void reactionChamberUsesAllThreeInputs(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var state = AEBlocks.PATTERN_PROVIDER.block().defaultBlockState().setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.WEST);
        var provider = fixture.place(fixture.origin, state, PatternProviderBlockEntity.class);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:pressurized_reaction_chamber"));
        var chamber = fixture.place(fixture.origin.west(), block.defaultBlockState());
        configureMekanismInputs(chamber, Direction.EAST, "ITEM", "FLUID", "CHEMICAL");
        var oxygen = chemicalKey(fixture, "oxygen");
        var water = AEFluidKey.of(Fluids.WATER);
        var coal = AEItemKey.of(Items.COAL);
        var inputs = List.of(new GenericStack(oxygen, 100), new GenericStack(water, 100), new GenericStack(coal, 1));
        var sulfur = AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:dust_sulfur")));
        var outputs = List.of(new GenericStack(sulfur, 1), new GenericStack(chemicalKey(fixture, "hydrogen"), 100));
        var logic = provider.getLogic();
        var target = (OmniCapacityTarget) logic;
        var copies = BigInteger.valueOf(36);
        powerProvider(fixture, provider).thenExecute(() -> {
            var face = PatternProviderTarget.get(fixture.level, chamber.getBlockPos(), chamber, Direction.EAST, IActionSource.empty());
            helper.assertTrue(face != null, "The reaction chamber should expose its three input channels");
            var substrate = AEItemKey.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:substrate")));
            var incompatible = List.of(new GenericStack(substrate, 1), new GenericStack(water, 100), new GenericStack(oxygen, 100));
            for (var input : incompatible) {
                helper.assertTrue(face.insert(input.what(), input.amount(), Actionable.SIMULATE) == input.amount(),
                        "An empty chamber should accept each independent probe for " + input.what());
            }
            try (var unknown = target.omniDispatch$prepareTarget(install(fixture, logic, incompatible, outputs, BigInteger.TEN))) {
                helper.assertTrue(unknown == null, "Substrate with water needs ethene, and three independent probes cannot certify oxygen");
            }
            assertDrained(fixture, logic);
            helper.assertTrue(face.insert(water, 6_300, Actionable.MODULATE) == 6_300, "Seed the real reaction chamber water tank");
            var request = install(fixture, logic, inputs, outputs, BigInteger.ONE.shiftLeft(80));
            try (var admission = target.omniDispatch$prepareTarget(request)) {
                helper.assertTrue(admission != null && BigInteger.valueOf(37).equals(admission.maximumCrafts()), "3,700 mB should limit all three inputs to 37 crafts");
                helper.assertTrue(face.insert(water, 100, Actionable.MODULATE) == 100 && admission.submit(BigInteger.valueOf(37)).signum() == 0,
                        "A competing water insert should reject the entire stale batch");
            }
            assertDrained(fixture, logic);
            try (var admission = target.omniDispatch$prepareTarget(request)) {
                helper.assertTrue(admission != null && copies.equals(admission.maximumCrafts()) && copies.equals(admission.submit(copies)),
                        "The remaining room should accept 36 crafts in one submission");
            }
            assertWestQueue(fixture, logic, new GenericStack(oxygen, 3_600), new GenericStack(water, 3_600), new GenericStack(coal, 36));
        }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "The unpowered chamber should receive every input"))
                .thenExecute(() -> {
                    var items = fixture.level.getCapability(Capabilities.ItemHandler.BLOCK, chamber.getBlockPos(), Direction.EAST);
                    var fluids = fixture.level.getCapability(Capabilities.FluidHandler.BLOCK, chamber.getBlockPos(), Direction.EAST);
                    helper.assertTrue(items != null && coal.matches(items.getStackInSlot(0)) && items.getStackInSlot(0).getCount() == 36
                                    && fluids != null && water.matches(fluids.getFluidInTank(0)) && fluids.getFluidInTank(0).getAmount() == 10_000,
                            "The chamber should hold 36 coal and its full 10,000 mB of water");
                    try {
                        var tank = chamber.getClass().getField("inputGasTank").get(chamber);
                        helper.assertTrue((long) tank.getClass().getMethod("getStored").invoke(tank) == 3_600, "The input tank should contain exactly 3,600 oxygen");
                    } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                    assertDrained(fixture, logic);
                }).thenSucceed();
    }

    private static void configureMekanismInputs(BlockEntity machine, Direction side, String... channels) {
        try {
            var transmission = Class.forName("mekanism.common.lib.transmitter.TransmissionType");
            var relative = Class.forName("mekanism.api.RelativeSide");
            var dataType = Class.forName("mekanism.common.tile.component.config.DataType");
            var facing = machine.getClass().getMethod("getDirection").invoke(machine);
            var face = relative.getMethod("fromDirections", Direction.class, Direction.class).invoke(null, facing, side);
            var input = dataType.getField("INPUT").get(null);
            var component = machine.getClass().getMethod("getConfig").invoke(machine);
            for (var name : channels) {
                var channel = transmission.getField(name).get(null);
                var config = component.getClass().getMethod("getConfig", transmission).invoke(component, channel);
                if ((boolean) config.getClass().getMethod("setDataType", dataType, relative).invoke(config, input, face)) {
                    component.getClass().getMethod("sideChanged", transmission, relative).invoke(component, channel, face);
                }
            }
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void fluidBatchStopsAtTheSignedLongBoundary(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var providerState = AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.WEST);
        var provider = fixture.place(fixture.origin, providerState, PatternProviderBlockEntity.class);
        var destination = fixture.place(fixture.origin.west(), AEBlocks.INTERFACE.block().defaultBlockState(), InterfaceBlockEntity.class);
        var logic = provider.getLogic();
        var water = AEFluidKey.of(Fluids.WATER);
        int perCraft = Integer.MAX_VALUE;
        var crafts = BigInteger.valueOf(Long.MAX_VALUE / perCraft);
        var expected = new GenericStack(water, Long.MAX_VALUE - 1);
        var config = destination.getInterfaceLogic().getConfig();
        var storage = destination.getInterfaceLogic().getStorage();
        config.setCapacity(water.getType(), Long.MAX_VALUE);
        storage.setCapacity(water.getType(), Long.MAX_VALUE);
        config.setStack(0, expected);

        powerProvider(fixture, provider).thenExecute(() -> {
            var request = install(fixture, logic, List.of(new GenericStack(water, perCraft)), BigInteger.ONE.shiftLeft(80));
            var target = (OmniCapacityTarget) logic;
            try (var admission = target.omniDispatch$prepareTarget(request)) {
                helper.assertTrue(admission != null && crafts.equals(admission.maximumCrafts()),
                        "An Integer.MAX_VALUE-mB input should admit exactly 4,294,967,298 copies");
                helper.assertTrue(BigInteger.ZERO.equals(admission.submit(crafts.add(BigInteger.ONE))),
                        "One additional copy would overflow the queued amount and must reject the whole submit");
            }
            assertDrained(fixture, logic);
            helper.assertTrue(storage.isEmpty(), "The rejected overflowing submission should leave the interface empty");
            try (var admission = target.omniDispatch$prepareTarget(request)) {
                helper.assertTrue(admission != null && crafts.equals(admission.submit(crafts)),
                        "The largest representable whole batch should commit in one submit");
            }
            assertWestQueue(fixture, logic, expected);
        }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "The interface should accept the entire long-valued queue"))
                .thenExecute(() -> {
                    helper.assertTrue(expected.equals(storage.getStack(0)), "The interface should contain exactly Long.MAX_VALUE - 1 mB");
                    assertDrained(fixture, logic);
                }).thenSucceed();
    }

    private static BlockPos placeStorage(ServerRegressionFixture fixture, ItemStackHandler items, FluidTank tank) {
        var destination = fixture.origin.west();
        fixture.place(destination, Blocks.POLISHED_ANDESITE.defaultBlockState());
        STORAGE.put(destination, new FixtureStorage(fixture.level, items, tank));
        fixture.onCleanup(() -> {
            STORAGE.remove(destination);
            fixture.level.invalidateCapabilities(destination);
        });
        fixture.level.invalidateCapabilities(destination);
        return destination;
    }

    private static GameTestSequence powerProvider(ServerRegressionFixture fixture, PatternProviderBlockEntity provider) {
        var power = fixture.place(fixture.origin.north(8), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),
                AENetworkedBlockEntity.class);
        var helper = fixture.helper;
        return helper.startSequence().thenWaitUntil(() -> helper.assertTrue(provider.getMainNode().getNode() != null
                        && power.getMainNode().getNode() != null, "Both AE2 nodes should initialize"))
                .thenExecute(() -> GridHelper.createConnection(power.getMainNode().getNode(), provider.getMainNode().getNode()))
                .thenWaitUntil(() -> helper.assertTrue(provider.getMainNode().isActive(), "Provider should have a powered channel"));
    }

    private static OmniCapacityRequest install(ServerRegressionFixture fixture, PatternProviderLogic logic,
            List<GenericStack> inputs, BigInteger maximum) {
        return install(fixture, logic, inputs, List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)), maximum);
    }

    private static OmniCapacityRequest install(ServerRegressionFixture fixture, PatternProviderLogic logic,
            List<GenericStack> inputs, List<GenericStack> outputs, BigInteger maximum) {
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs, outputs));
        logic.updatePatterns();
        var pattern = logic.getAvailablePatterns().getFirst();
        var patternInputs = pattern.getInputs();
        var unitInputs = new KeyCounter[patternInputs.length];
        for (int index = 0; index < patternInputs.length; index++) {
            var input = patternInputs[index];
            unitInputs[index] = new KeyCounter();
            unitInputs[index].add(input.getPossibleInputs()[0].what(), input.getMultiplier());
        }
        var unitOutputs = new KeyCounter();
        for (var output : outputs) unitOutputs.add(output.what(), output.amount());
        return new OmniCapacityRequest(UUID.randomUUID(), logic, pattern, unitInputs, unitOutputs, new KeyCounter(), maximum, fixture.level);
    }

    private static void assertContents(GameTestHelper helper, ItemStackHandler items, FluidTank tank, int coal, int water) {
        var stack = items.getStackInSlot(0);
        helper.assertTrue(stack.is(Items.COAL) && stack.getCount() == coal && tank.getFluid().getFluid() == Fluids.WATER
                        && tank.getFluidAmount() == water,
                "Expected " + coal + " coal and " + water + " mB of water in the target");
    }

    private static void assertWestQueue(ServerRegressionFixture fixture, PatternProviderLogic logic, GenericStack... expected) {
        var saved = save(fixture, logic);
        var queue = saved.getList(PatternProviderLogic.NBT_SEND_LIST, Tag.TAG_COMPOUND);
        fixture.helper.assertTrue(logic.isBusy() && queue.size() == expected.length
                        && saved.getByte(PatternProviderLogic.NBT_SEND_DIRECTION) == Direction.WEST.get3DDataValue(),
                "The provider should own the complete batch for the west face");
        for (int index = 0; index < expected.length; index++) {
            var actual = GenericStack.readTag(fixture.level.registryAccess(), queue.getCompound(index));
            fixture.helper.assertTrue(expected[index].equals(actual),
                    "Expected " + expected[index] + " in queue entry " + index + ", got " + actual);
        }
    }

    private static void assertDrained(ServerRegressionFixture fixture, PatternProviderLogic logic) {
        var saved = save(fixture, logic);
        fixture.helper.assertTrue(!logic.isBusy() && saved.getList(PatternProviderLogic.NBT_SEND_LIST, Tag.TAG_COMPOUND).isEmpty()
                        && !saved.contains(PatternProviderLogic.NBT_SEND_DIRECTION),
                "The idle provider should have no saved inputs or send direction");
    }

    private static CompoundTag save(ServerRegressionFixture fixture, PatternProviderLogic logic) {
        var saved = new CompoundTag();
        logic.writeToNBT(saved, fixture.level.registryAccess());
        return saved;
    }
}
