package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityTarget;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class PatternProviderQueueGameTests {
    private static final String RECIPE = "molecularmanipulatorBatchRecipe";
    private static final String VERSION = "molecularmanipulatorSmartQueueVersion";
    private static final String REFUND = "molecularmanipulatorLegacyBatchRefund";

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void capacitySelectsOneSideAndKeepsItsPartialRemainder(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(),
                PatternProviderBlockEntity.class);
        var power = fixture.place(fixture.origin.north(8), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),
                AENetworkedBlockEntity.class);
        var west = fixture.place(fixture.origin.west(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
        var east = fixture.place(fixture.origin.east(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
        fillChest(west, 57);
        fillChest(east, 61);
        var logic = provider.getLogic();
        var target = (OmniCapacityTarget) logic;
        var coal = AEItemKey.of(Items.COAL);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var craft = BigInteger.valueOf(3);

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(provider.getMainNode().getNode() != null && power.getMainNode().getNode() != null,
                    "Both AE2 grid nodes should be initialized");
        }).thenExecute(() -> GridHelper.createConnection(power.getMainNode().getNode(), provider.getMainNode().getNode()))
                .thenWaitUntil(() -> helper.assertTrue(provider.getMainNode().isActive(), "Provider should have a powered channel"))
                .thenExecute(() -> {
                    var pattern = PatternDetailsHelper.encodeProcessingPattern(
                            List.of(new GenericStack(coal, 1), new GenericStack(coal, 1)),
                            List.of(new GenericStack(iron, 1)));
                    logic.getPatternInv().setItemDirect(0, pattern);
                    logic.updatePatterns();
                    var request = request(fixture, logic);
                    try (var admission = target.omniDispatch$prepareTarget(request)) {
                        helper.assertTrue(admission != null && craft.equals(admission.maximumCrafts()),
                                "Capacity is the largest whole-copy fit on a single side");
                        helper.assertTrue(!logic.isBusy() && west.getItem(0).getCount() == 57 && east.getItem(0).getCount() == 61,
                                "Probing should leave the queue and inventories untouched");
                        west.setItem(0, new ItemStack(Items.COAL, 63));
                        east.setItem(0, new ItemStack(Items.COAL, 55));
                        helper.assertTrue(BigInteger.ZERO.equals(admission.submit(craft)) && !logic.isBusy(),
                                "A shrunken target should reject without rerouting to a different side");
                    }

                    west.setItem(0, new ItemStack(Items.COAL, 57));
                    east.setItem(0, new ItemStack(Items.COAL, 61));
                    try (var admission = target.omniDispatch$prepareTarget(request)) {
                        helper.assertTrue(admission != null && craft.equals(admission.submit(craft)),
                                "Submit should transfer ownership of the whole reservation");
                    }
                    helper.assertTrue(west.getItem(0).getCount() == 57 && east.getItem(0).getCount() == 61,
                            "The vanilla tick should be responsible for inventory insertion");
                    assertQueue(fixture, logic, new GenericStack(coal, 6), Direction.WEST);
                    try (var admission = target.omniDispatch$prepareTarget(request)) {
                        helper.assertTrue(admission != null && admission.maximumCrafts().signum() == 0,
                                "A provider with owned stock should have no admission room");
                    }
                    west.setItem(0, new ItemStack(Items.COAL, 62));
                }).thenWaitUntil(() -> helper.assertTrue(west.getItem(0).getCount() == 64, "AE2 should insert the partial amount"))
                .thenExecute(() -> {
                    helper.assertTrue(east.getItem(0).getCount() == 61, "The other side should keep its original amount");
                    assertQueue(fixture, logic, new GenericStack(coal, 4), Direction.WEST);
                    west.setItem(0, new ItemStack(Items.COAL, 60));
                }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "AE2 should retry and drain the same queue"))
                .thenExecute(() -> {
                    var saved = save(fixture, logic);
                    helper.assertTrue(west.getItem(0).getCount() == 64 && east.getItem(0).getCount() == 61
                                    && queue(saved).isEmpty() && !saved.contains(PatternProviderLogic.NBT_SEND_DIRECTION),
                            "All six units should be accounted for and the drained queue should have no saved direction");
                    east.setItem(0, new ItemStack(Items.COAL, 64));
                    try (var admission = target.omniDispatch$prepareTarget(request(fixture, logic))) {
                        helper.assertTrue(admission != null && admission.maximumCrafts().signum() == 0,
                                "Full adjacent inventories should report known zero capacity");
                    }
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE)
    public static void oldQueuesPreserveOwnedStockAndDropItOnce(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var providerState = AEBlocks.PATTERN_PROVIDER.block().defaultBlockState();
        var smart = fixture.place(fixture.origin, providerState, PatternProviderBlockEntity.class).getLogic();
        var adaptive = fixture.place(fixture.origin.east(3), providerState, PatternProviderBlockEntity.class).getLogic();
        var registries = fixture.level.registryAccess();
        var coal = new GenericStack(AEItemKey.of(Items.COAL), 7);
        var iron = new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3);
        var refund = new GenericStack(AEItemKey.of(Items.DIAMOND), 5);
        var tag = new CompoundTag();
        var sent = new ListTag();
        sent.add(GenericStack.writeTag(registries, iron));
        sent.add(GenericStack.writeTag(registries, coal));
        tag.put(PatternProviderLogic.NBT_SEND_LIST, sent);
        tag.putByte(PatternProviderLogic.NBT_SEND_DIRECTION, (byte) Direction.SOUTH.get3DDataValue());
        var recipe = new ListTag();
        recipe.add(GenericStack.writeTag(registries, new GenericStack(coal.what(), 999)));
        tag.put(RECIPE, recipe);
        var refunds = new ListTag();
        refunds.add(GenericStack.writeTag(registries, refund));
        tag.put(REFUND, refunds);
        tag.putInt(VERSION, 2);

        smart.readFromNBT(tag.copy(), registries);
        var saved = save(fixture, smart);
        helper.assertTrue(queue(saved).equals(sent) && saved.getByte(PatternProviderLogic.NBT_SEND_DIRECTION)
                        == Direction.SOUTH.get3DDataValue() && saved.getList(REFUND, Tag.TAG_COMPOUND).equals(refunds),
                "Version 2 keeps the exact native backlog, side, order and separate refund");
        helper.assertTrue(!saved.contains(RECIPE) && !saved.contains(VERSION), "Recipe metadata should not be written back");

        tag.remove(VERSION);
        adaptive.readFromNBT(tag, registries);
        saved = save(fixture, adaptive);
        var recovered = saved.getList(REFUND, Tag.TAG_COMPOUND);
        helper.assertTrue(queue(saved).isEmpty() && !saved.contains(PatternProviderLogic.NBT_SEND_DIRECTION)
                        && recovered.size() == 3 && refund.equals(GenericStack.readTag(registries, recovered.getCompound(0)))
                        && iron.equals(GenericStack.readTag(registries, recovered.getCompound(1)))
                        && coal.equals(GenericStack.readTag(registries, recovered.getCompound(2))),
                "An older adaptive queue should refund the saved material quantities");

        var drops = new ArrayList<ItemStack>();
        adaptive.addDrops(drops);
        helper.assertTrue(drops.stream().filter(stack -> stack.is(Items.COAL)).mapToInt(ItemStack::getCount).sum() == 7
                        && drops.stream().filter(stack -> stack.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum() == 3
                        && drops.stream().filter(stack -> stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum() == 5,
                "Drops should include the full recovered stock exactly once");
        adaptive.clearContent();
        helper.assertTrue(!save(fixture, adaptive).contains(REFUND), "Clearing owned contents should clear the refund tag");
        helper.succeed();
    }

    private static void fillChest(ChestBlockEntity chest, int coal) {
        chest.setItem(0, new ItemStack(Items.COAL, coal));
        for (int slot = 1; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
    }

    private static OmniCapacityRequest request(ServerRegressionFixture fixture, PatternProviderLogic logic) {
        var inputs = new KeyCounter();
        inputs.add(AEItemKey.of(Items.COAL), 2);
        var outputs = new KeyCounter();
        outputs.add(AEItemKey.of(Items.IRON_INGOT), 1);
        return new OmniCapacityRequest(UUID.randomUUID(), logic, logic.getAvailablePatterns().getFirst(),
                new KeyCounter[] { inputs }, outputs, new KeyCounter(), BigInteger.ONE.shiftLeft(80), fixture.level);
    }

    private static void assertQueue(ServerRegressionFixture fixture, PatternProviderLogic logic,
            GenericStack expected, Direction side) {
        var saved = save(fixture, logic);
        var queue = queue(saved);
        fixture.helper.assertTrue(queue.size() == 1
                        && expected.equals(GenericStack.readTag(fixture.level.registryAccess(), queue.getCompound(0)))
                        && saved.getByte(PatternProviderLogic.NBT_SEND_DIRECTION) == side.get3DDataValue(),
                "The saved native queue should hold the expected remainder for the admitted side");
    }

    private static CompoundTag save(ServerRegressionFixture fixture, PatternProviderLogic logic) {
        var tag = new CompoundTag();
        logic.writeToNBT(tag, fixture.level.registryAccess());
        return tag;
    }

    private static ListTag queue(CompoundTag tag) {
        return tag.getList(PatternProviderLogic.NBT_SEND_LIST, Tag.TAG_COMPOUND);
    }
}
