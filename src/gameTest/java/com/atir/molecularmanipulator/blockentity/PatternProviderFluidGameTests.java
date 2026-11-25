package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityTarget;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
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
        var power = fixture.place(fixture.origin.north(8), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),
                AENetworkedBlockEntity.class);
        var destination = fixture.origin.west();
        fixture.place(destination, Blocks.POLISHED_ANDESITE.defaultBlockState());
        var items = new ItemStackHandler(1);
        items.setStackInSlot(0, new ItemStack(Items.COAL, 31));
        var tank = new FluidTank(AEFluidKey.AMOUNT_BUCKET);
        STORAGE.put(destination, new FixtureStorage(fixture.level, items, tank));
        fixture.onCleanup(() -> {
            STORAGE.remove(destination);
            fixture.level.invalidateCapabilities(destination);
        });
        fixture.level.invalidateCapabilities(destination);

        var logic = provider.getLogic();
        var capacity = (OmniCapacityTarget) logic;
        var coal = AEItemKey.of(Items.COAL);
        var water = AEFluidKey.of(Fluids.WATER);
        var lava = AEFluidKey.of(Fluids.LAVA);
        var mixedInputs = List.of(new GenericStack(coal, 2), new GenericStack(water, 250));
        var crafts = BigInteger.valueOf(3);

        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(provider.getMainNode().getNode() != null
                && power.getMainNode().getNode() != null, "Both AE2 nodes should initialize"))
                .thenExecute(() -> GridHelper.createConnection(power.getMainNode().getNode(), provider.getMainNode().getNode()))
                .thenWaitUntil(() -> helper.assertTrue(provider.getMainNode().isActive(), "Provider should have a powered channel"))
                .thenExecute(() -> {
                    helper.assertTrue(fixture.level.getCapability(Capabilities.ItemHandler.BLOCK, destination, Direction.EAST) == items
                                    && fixture.level.getCapability(Capabilities.FluidHandler.BLOCK, destination, Direction.EAST) == tank,
                            "The provider's target should expose the fixture's actual NeoForge handlers");
                    var competing = install(fixture, logic, List.of(new GenericStack(water, 250), new GenericStack(lava, 125)));
                    try (var admission = capacity.omniDispatch$prepareTarget(competing)) {
                        helper.assertTrue(admission != null && admission.maximumCrafts().signum() == 0,
                                "Water and lava cannot share the one empty tank");
                    }
                    assertDrained(fixture, logic);
                    helper.assertTrue(tank.isEmpty() && items.getStackInSlot(0).getCount() == 31,
                            "A failed joint probe should leave the empty tank and the item slot unchanged");

                    tank.setFluid(new FluidStack(Fluids.WATER, 100));
                    var mixed = install(fixture, logic, mixedInputs);
                    try (var admission = capacity.omniDispatch$prepareTarget(mixed)) {
                        helper.assertTrue(admission != null && crafts.equals(admission.maximumCrafts()),
                                "Nine hundred mB of water room should limit the pair to three whole crafts");
                        assertDrained(fixture, logic);
                        assertContents(helper, items, tank, 31, 100);
                        helper.assertTrue(crafts.equals(admission.submit(crafts)), "Submit should accept all three crafts");
                    }
                    assertContents(helper, items, tank, 31, 100);
                    var saved = save(fixture, logic);
                    var queue = saved.getList(PatternProviderLogic.NBT_SEND_LIST, Tag.TAG_COMPOUND);
                    var registries = fixture.level.registryAccess();
                    helper.assertTrue(queue.size() == 2
                                    && new GenericStack(coal, 6).equals(GenericStack.readTag(registries, queue.getCompound(0)))
                                    && new GenericStack(water, 750).equals(GenericStack.readTag(registries, queue.getCompound(1)))
                                    && saved.getByte(PatternProviderLogic.NBT_SEND_DIRECTION) == Direction.WEST.get3DDataValue(),
                            "The durable queue should own six coal and 750 mB of water in pattern order for the west face");
                }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "AE2 should drain the mixed queue"))
                .thenExecute(() -> {
                    assertContents(helper, items, tank, 37, 850);
                    assertDrained(fixture, logic);

                    // The subclass retains normal insertion behavior but has no supported slot-layout proof.
                    var nativeItems = new ItemStackHandler(1) {};
                    tank.setFluid(FluidStack.EMPTY);
                    STORAGE.put(destination, new FixtureStorage(fixture.level, nativeItems, tank));
                    fixture.level.invalidateCapabilities(destination);
                    var mixed = install(fixture, logic, mixedInputs);
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

    private static OmniCapacityRequest install(ServerRegressionFixture fixture, PatternProviderLogic logic,
            List<GenericStack> inputs) {
        var output = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1);
        logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output)));
        logic.updatePatterns();
        var unitInputs = new KeyCounter[inputs.size()];
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            unitInputs[index] = new KeyCounter();
            unitInputs[index].add(input.what(), input.amount());
        }
        var unitOutputs = new KeyCounter();
        unitOutputs.add(output.what(), output.amount());
        return new OmniCapacityRequest(UUID.randomUUID(), logic, logic.getAvailablePatterns().getFirst(),
                unitInputs, unitOutputs, new KeyCounter(), BigInteger.TEN, fixture.level);
    }

    private static void assertContents(GameTestHelper helper, ItemStackHandler items, FluidTank tank, int coal, int water) {
        var stack = items.getStackInSlot(0);
        helper.assertTrue(stack.is(Items.COAL) && stack.getCount() == coal && tank.getFluid().getFluid() == Fluids.WATER
                        && tank.getFluidAmount() == water,
                "Expected " + coal + " coal and " + water + " mB of water in the target");
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
