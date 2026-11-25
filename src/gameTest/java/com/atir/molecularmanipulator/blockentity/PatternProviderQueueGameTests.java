package com.atir.molecularmanipulator.blockentity;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityTarget;
import com.atir.molecularmanipulator.registry.ModContent;
import com.glodblock.github.extendedae.common.EAESingletons;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class PatternProviderQueueGameTests {
    private static final String RECIPE = "molecularmanipulatorBatchRecipe";
    private static final String VERSION = "molecularmanipulatorSmartQueueVersion";
    private static final String REFUND = "molecularmanipulatorLegacyBatchRefund";

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 300)
    public static void finiteCellAdmitsItsFullRoomAndReloadsTheRemainder(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(),
                PatternProviderBlockEntity.class);
        fixture.place(fixture.origin.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var chest = fixture.place(fixture.origin.west(), AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
        chest.setCell(AEItems.ITEM_CELL_16K.stack());
        var cell = fixture.level.getCapability(AECapabilities.ME_STORAGE, chest.getBlockPos(), Direction.EAST);
        helper.assertTrue(cell != null, "The ME chest should expose its finite item cell toward the provider");
        var source = IActionSource.empty();
        var coal = AEItemKey.of(Items.COAL);
        long full = cell.insert(coal, 1_000_000, Actionable.MODULATE, source);
        helper.assertTrue(full >= 100_000 && full < 1_000_000
                        && cell.extract(coal, 100_000, Actionable.MODULATE, source) == 100_000,
                "Filling the cell and extracting 100,000 coal should leave exactly that much space");
        var logic = provider.getLogic();
        installPattern(logic, PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(coal, 1)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1))));
        var request = request(fixture, logic, BigInteger.valueOf(1_000_000));
        var large = BigInteger.valueOf(100_000);
        var small = BigInteger.valueOf(64);
        long remainder = 100_000 - 64;
        var restored = new PatternProviderBlockEntity[1];

        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(provider.getMainNode().isActive(),
                "The provider should receive a channel from the adjacent creative cell")).thenExecute(() -> {
            try (var admission = prepare(request)) {
                helper.assertTrue(admission != null && large.equals(admission.maximumCrafts()),
                        "One prediction should offer all 100,000 whole crafts that fit the cell");
                helper.assertTrue(cell.insert(coal, remainder, Actionable.MODULATE, source) == remainder,
                        "A competing insertion should leave room for 64 crafts");
                helper.assertTrue(BigInteger.ZERO.equals(admission.submit(large)), "The stale reservation should reject all 100,000");
            }
            assertDrained(fixture, logic);
            helper.assertTrue(cell.getAvailableStacks().get(coal) == full - 64, "A rejected submit should deliver no coal");
            try (var admission = prepare(request)) {
                helper.assertTrue(admission != null && small.equals(admission.maximumCrafts()) && small.equals(admission.submit(small)),
                        "The same request should now accept exactly 64 crafts in one submit");
            }
            assertQueue(fixture, logic, Direction.WEST, new GenericStack(coal, 64));
        }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "The 64 coal should drain into the cell"))
                .thenExecute(() -> {
                    helper.assertTrue(cell.getAvailableStacks().get(coal) == full
                                    && cell.extract(coal, 100_000, Actionable.MODULATE, source) == 100_000,
                            "The small batch should fill the cell before 100,000 spaces are freed again");
                    try (var admission = prepare(request)) {
                        helper.assertTrue(admission != null && large.equals(admission.maximumCrafts())
                                        && large.equals(admission.submit(large)), "One submit should own exactly 100,000 crafts");
                    }
                    assertQueue(fixture, logic, Direction.WEST, new GenericStack(coal, 100_000));
                    helper.assertTrue(cell.getAvailableStacks().get(coal) == full - 100_000
                                    && cell.insert(coal, remainder, Actionable.MODULATE, source) == remainder,
                            "The accepted stock should stay in the queue while another insertion leaves only 64 spaces");
                }).thenWaitUntil(() -> helper.assertTrue(cell.getAvailableStacks().get(coal) == full,
                        "The native provider should insert the 64 coal that still fit"))
                .thenExecute(() -> {
                    var pending = new GenericStack(coal, remainder);
                    assertQueue(fixture, logic, Direction.WEST, pending);
                    var registries = fixture.level.registryAccess();
                    var saved = provider.saveWithFullMetadata(registries);
                    var state = provider.getBlockState();
                    fixture.level.removeBlockEntity(fixture.origin);
                    restored[0] = (PatternProviderBlockEntity) BlockEntity.loadStatic(fixture.origin, state, saved, registries);
                    helper.assertTrue(restored[0] != null, "The saved provider should restore at its original position");
                    fixture.level.setBlockEntity(restored[0]);
                    assertQueue(fixture, restored[0].getLogic(), Direction.WEST, pending);
                    helper.assertTrue(cell.extract(coal, remainder, Actionable.MODULATE, source) == remainder,
                            "The chest should free exactly enough room for the persisted remainder");
                }).thenWaitUntil(() -> helper.assertTrue(!restored[0].getLogic().isBusy(), "The restored queue should drain"))
                .thenExecute(() -> {
                    assertDrained(fixture, restored[0].getLogic());
                    helper.assertTrue(cell.getAvailableStacks().get(coal) == full,
                            "Exactly 99,936 more coal should arrive after the provider reloads");
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void mixedCellBatchReservesBytesAndSkipsUnrelatedAssemblers(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, EAESingletons.EX_PATTERN_PROVIDER.defaultBlockState(),
                PatternProviderBlockEntity.class);
        fixture.place(fixture.origin.east(), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
        fixture.place(fixture.origin.south(), EAESingletons.EX_ASSEMBLER.defaultBlockState());
        var chest = fixture.place(fixture.origin.west(), AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
        chest.setCell(AEItems.ITEM_CELL_16K.stack());
        var cell = fixture.level.getCapability(AECapabilities.ME_STORAGE, chest.getBlockPos(), Direction.EAST);
        var coal = AEItemKey.of(Items.COAL);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var source = IActionSource.empty();
        helper.assertTrue(cell != null && cell.insert(coal, 1, Actionable.MODULATE, source) == 1, "Seed the cell with one coal");
        var logic = provider.getLogic();
        // Two types cost 128 bytes each. Of the remaining 129,024 item units, one is already occupied.
        long copies = ((16_384 - 2 * 128) * 8 - 1) / 16;
        var expected = BigInteger.valueOf(copies);
        poweredProviders(fixture, provider).thenExecute(() -> {
            for (var side : List.of(Direction.EAST, Direction.SOUTH)) {
                var assembler = ICraftingMachine.of(fixture.level, fixture.origin.relative(side), side.getOpposite());
                helper.assertTrue(assembler != null && assembler.acceptsPlans(), "Both neighboring assemblers must be active targets");
            }
            installPattern(logic, mixedPattern());
            var request = request(fixture, logic, BigInteger.ONE.shiftLeft(80));
            try (var beforeCanner = prepare(request)) {
                fixture.place(fixture.origin.north(), EAESingletons.CANER.defaultBlockState());
                var canner = ICraftingMachine.of(fixture.level, fixture.origin.north(), Direction.SOUTH);
                helper.assertTrue(canner != null && canner.acceptsPlans() && beforeCanner != null
                                && beforeCanner.submit(expected).signum() == 0,
                        "A newly adjacent machine that handles processing patterns should invalidate the chest prediction");
                try (var withCanner = prepare(request)) {
                    helper.assertTrue(withCanner == null, "The native processing machine should keep priority over the chest");
                }
                assertDrained(fixture, logic);
                fixture.place(fixture.origin.north(), Blocks.AIR.defaultBlockState());
            }
            try (var admission = prepare(request)) {
                helper.assertTrue(admission != null && expected.equals(admission.maximumCrafts()) && expected.equals(admission.submit(expected)),
                        "The processing pattern should admit 8,063 whole copies to the ME chest beside both assemblers");
            }
            assertQueue(fixture, logic, Direction.WEST, new GenericStack(coal, copies), new GenericStack(gold, 13 * copies),
                    new GenericStack(coal, 2 * copies));
            helper.assertTrue(cell.getAvailableStacks().get(coal) == 1 && cell.getAvailableStacks().get(gold) == 0,
                    "Only the provider should own the accepted batch before its first send");
        }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "The ME chest should receive the complete queue"))
                .thenExecute(() -> {
                    var stock = cell.getAvailableStacks();
                    helper.assertTrue(stock.get(coal) == 3 * copies + 1 && stock.get(gold) == 13 * copies,
                            "The cell should hold the seed and every accepted input");
                    assertCapacity(fixture, request(fixture, logic, BigInteger.TEN), 0, "Fifteen free item units cannot fit another craft");
                    var voidCell = AEItems.ITEM_CELL_16K.stack();
                    var upgrades = AEItems.ITEM_CELL_16K.asItem().getUpgrades(voidCell);
                    upgrades.setItemDirect(0, AEItems.VOID_CARD.stack());
                    chest.setCell(voidCell);
                    helper.assertTrue(upgrades.isInstalled(AEItems.VOID_CARD) && cell.insert(coal, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE,
                            "The real void cell should report more acceptance than physical room");
                    try (var voidAdmission = prepare(request(fixture, logic, BigInteger.TEN))) {
                        helper.assertTrue(voidAdmission == null, "Void acceptance cannot prove physical storage for a mixed batch");
                    }
                    installPattern(logic, PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(coal, 1)),
                            List.of(new GenericStack(gold, 1))));
                    try (var singleVoid = prepare(request(fixture, logic, BigInteger.valueOf(1_000_000)))) {
                        helper.assertTrue(singleVoid == null, "A void cell cannot prove it will store a million coal either");
                    }
                    assertDrained(fixture, logic);
                }).thenSucceed();
    }

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

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void jointCapacityPreservesSparseOrderAndAdmittedSide(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(),
                PatternProviderBlockEntity.class);
        var west = fixture.place(fixture.origin.west(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
        var east = fixture.place(fixture.origin.east(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
        leaveEmptySlots(west, 1);
        leaveEmptySlots(east, 1);
        var logic = provider.getLogic();
        var coal = AEItemKey.of(Items.COAL);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var crafts = BigInteger.valueOf(4);
        var registries = fixture.level.registryAccess();

        poweredProviders(fixture, provider).thenExecute(() -> {
            installPattern(logic, mixedPattern());
            var request = request(fixture, logic, BigInteger.TEN);
            var providerBefore = save(fixture, logic);
            var westBefore = west.saveWithoutMetadata(registries);
            var eastBefore = east.saveWithoutMetadata(registries);
            assertCapacity(fixture, request, 0, "One empty slot per face cannot fit both input keys");
            helper.assertTrue(providerBefore.equals(save(fixture, logic))
                            && westBefore.equals(west.saveWithoutMetadata(registries))
                            && eastBefore.equals(east.saveWithoutMetadata(registries)),
                    "The joint probe should leave the provider and both inventories unchanged");

            west.setItem(1, ItemStack.EMPTY);
            westBefore = west.saveWithoutMetadata(registries);
            try (var admission = prepare(request)) {
                helper.assertTrue(admission != null && crafts.equals(admission.maximumCrafts()),
                        "Two empty slots should fit four crafts with three coal and thirteen gold each");
                helper.assertTrue(providerBefore.equals(save(fixture, logic))
                                && westBefore.equals(west.saveWithoutMetadata(registries))
                                && eastBefore.equals(east.saveWithoutMetadata(registries)),
                        "A positive probe should neither claim the queue nor fill either face");
                west.setItem(1, new ItemStack(Items.GOLD_INGOT, 55));
                east.setItem(1, ItemStack.EMPTY);
                helper.assertTrue(BigInteger.ZERO.equals(admission.submit(crafts)),
                        "A stale reservation should reject the whole batch even when the other face can fit it");
            }
            helper.assertTrue(providerBefore.equals(save(fixture, logic)) && !logic.isBusy()
                            && west.getItem(0).isEmpty() && west.getItem(1).getCount() == 55
                            && east.getItem(0).isEmpty() && east.getItem(1).isEmpty(),
                    "Rejected submit should preserve the queue and the newly changed space on both faces");

            west.setItem(1, ItemStack.EMPTY);
            east.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            westBefore = west.saveWithoutMetadata(registries);
            try (var admission = prepare(request)) {
                helper.assertTrue(admission != null && crafts.equals(admission.submit(crafts)),
                        "The provider should accept all four crafts once the admitted inventory fits");
            }
            assertQueue(fixture, logic, Direction.WEST, new GenericStack(coal, 4), new GenericStack(gold, 52),
                    new GenericStack(coal, 8));
            helper.assertTrue(westBefore.equals(west.saveWithoutMetadata(registries))
                            && eastBefore.equals(east.saveWithoutMetadata(registries))
                            && request.unitInputs()[0].get(coal) == 3 && request.unitInputs()[1].get(gold) == 13
                            && request.unitOutputs().get(iron) == 5,
                    "Commit should own the scaled inputs while leaving inventories and the unit recipe unchanged");

            west.setItem(1, new ItemStack(Items.GOLD_INGOT, 60));
            east.setItem(1, ItemStack.EMPTY);
        }).thenWaitUntil(() -> helper.assertTrue(west.getItem(1).getCount() == 64,
                "The native tick should insert the four gold that still fit"))
                .thenExecute(() -> {
                    helper.assertTrue(west.getItem(0).is(Items.COAL) && west.getItem(0).getCount() == 12,
                            "Both separated coal entries should drain into the admitted chest");
                    assertQueue(fixture, logic, Direction.WEST, new GenericStack(gold, 48));
                    helper.assertTrue(east.getItem(0).isEmpty() && east.getItem(1).isEmpty(),
                            "The backlog should retain its original side even when the other face has room");
                    west.setItem(1, new ItemStack(Items.GOLD_INGOT, 16));
                }).thenWaitUntil(() -> helper.assertTrue(!logic.isBusy(), "The retained gold should drain in place"))
                .thenExecute(() -> {
                    assertDrained(fixture, logic);
                    helper.assertTrue(west.getItem(0).getCount() == 12 && west.getItem(1).is(Items.GOLD_INGOT)
                                    && west.getItem(1).getCount() == 64 && east.getItem(0).isEmpty()
                                    && east.getItem(1).isEmpty(),
                            "The admitted chest should receive twelve coal and fifty-two gold in total");
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void differentFacesAndChestHalvesRespectOwnedBacklog(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var providerState = AEBlocks.PATTERN_PROVIDER.block().defaultBlockState();
        var chestState = Blocks.CHEST.defaultBlockState();
        var west = fixture.place(fixture.origin, chestState, ChestBlockEntity.class);
        var east = fixture.place(fixture.origin.east(), chestState.setValue(ChestBlock.TYPE, ChestType.RIGHT),
                ChestBlockEntity.class);
        var first = fixture.place(fixture.origin.west(), providerState, PatternProviderBlockEntity.class);
        var peer = fixture.place(fixture.origin.north(), providerState, PatternProviderBlockEntity.class);
        var alias = fixture.place(fixture.origin.east().south(), providerState, PatternProviderBlockEntity.class);
        leaveEmptySlots(west, 1);
        leaveEmptySlots(east, 1);
        var coal = AEItemKey.of(Items.COAL);
        var gold = AEItemKey.of(Items.GOLD_INGOT);

        poweredProviders(fixture, first, peer, alias).thenExecute(() -> {
            var westHandler = fixture.level.getCapability(Capabilities.ItemHandler.BLOCK, west.getBlockPos(), Direction.WEST);
            var eastHandler = fixture.level.getCapability(Capabilities.ItemHandler.BLOCK, east.getBlockPos(), Direction.SOUTH);
            helper.assertTrue(westHandler != null && eastHandler != null && westHandler.getSlots() == 54
                            && eastHandler.getSlots() == 54,
                    "Each half should expose the full double chest through its actual item capability");
            for (var provider : List.of(first, peer, alias)) {
                installPattern(provider.getLogic(), mixedPattern());
            }
            try (var ownerAdmission = prepare(request(fixture, first.getLogic(), BigInteger.TWO));
                    var peerPrediction = prepare(request(fixture, peer.getLogic(), BigInteger.TWO));
                    var aliasPrediction = prepare(request(fixture, alias.getLogic(), BigInteger.TWO))) {
                helper.assertTrue(ownerAdmission != null && BigInteger.TWO.equals(ownerAdmission.maximumCrafts())
                                && peerPrediction != null && BigInteger.TWO.equals(peerPrediction.maximumCrafts())
                                && aliasPrediction != null && BigInteger.TWO.equals(aliasPrediction.maximumCrafts()),
                        "All three independent predictions should see room before the first provider commits");
                helper.assertTrue(BigInteger.TWO.equals(ownerAdmission.submit(BigInteger.TWO)),
                        "The first provider should commit both crafts");
                helper.assertTrue(BigInteger.ZERO.equals(peerPrediction.submit(BigInteger.TWO)),
                        "A prior prediction from another face should fail its submit recheck");
                helper.assertTrue(BigInteger.ZERO.equals(aliasPrediction.submit(BigInteger.TWO)),
                        "A prior prediction from the other chest half should fail its submit recheck");
            }
            assertQueue(fixture, first.getLogic(), Direction.EAST, new GenericStack(coal, 2),
                    new GenericStack(gold, 26), new GenericStack(coal, 4));
            assertDrained(fixture, peer.getLogic());
            assertDrained(fixture, alias.getLogic());
            helper.assertTrue(west.getItem(0).isEmpty() && east.getItem(0).isEmpty(),
                    "Backlog ownership alone should block peers while the target still has its full physical room");
            assertCapacity(fixture, request(fixture, peer.getLogic(), BigInteger.TWO), 0,
                    "A current prediction from another face should see the owned backlog");
            assertCapacity(fixture, request(fixture, alias.getLogic(), BigInteger.TWO), 0,
                    "A current prediction through the other chest half should see the same backlog");
            east.setItem(0, new ItemStack(Items.GOLD_INGOT, 60));
        }).thenWaitUntil(() -> helper.assertTrue(east.getItem(0).getCount() == 64,
                "The first provider should partially drain into the shared inventory"))
                .thenExecute(() -> {
                    helper.assertTrue(west.getItem(0).is(Items.COAL) && west.getItem(0).getCount() == 6,
                            "Only the first provider's two crafts should have delivered coal");
                    assertQueue(fixture, first.getLogic(), Direction.EAST, new GenericStack(gold, 22));
                    east.setItem(0, new ItemStack(Items.GOLD_INGOT, 12));
                    assertCapacity(fixture, request(fixture, peer.getLogic(), BigInteger.TWO), 0,
                            "Newly freed room should stay unavailable while any of the first backlog is owned");
                    assertCapacity(fixture, request(fixture, alias.getLogic(), BigInteger.TWO), 0,
                            "The other half should remain unavailable until the entire first backlog drains");
                }).thenWaitUntil(() -> helper.assertTrue(!first.getLogic().isBusy(), "The first provider should drain"))
                .thenExecute(() -> {
                    assertDrained(fixture, first.getLogic());
                    helper.assertTrue(east.getItem(0).getCount() == 34, "The remaining twenty-two gold should arrive once");
                    assertCapacity(fixture, request(fixture, peer.getLogic(), BigInteger.TWO), 2,
                            "The original chest half should admit again after drain");
                    try (var admission = prepare(request(fixture, alias.getLogic(), BigInteger.TWO))) {
                        helper.assertTrue(admission != null && BigInteger.TWO.equals(admission.submit(BigInteger.TWO)),
                                "The provider on the other half should be able to commit after drain");
                    }
                    assertQueue(fixture, alias.getLogic(), Direction.NORTH, new GenericStack(coal, 2),
                            new GenericStack(gold, 26), new GenericStack(coal, 4));
                }).thenWaitUntil(() -> helper.assertTrue(!alias.getLogic().isBusy(), "The second backlog should drain"))
                .thenExecute(() -> {
                    assertDrained(fixture, alias.getLogic());
                    helper.assertTrue(west.getItem(0).getCount() == 12 && east.getItem(0).getCount() == 60,
                            "The double chest should receive exactly the two admitted batches");
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE)
    public static void blockingModeUsesNativePatternPush(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(),
                PatternProviderBlockEntity.class);
        var chest = fixture.place(fixture.origin.west(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
        leaveEmptySlots(chest, 2);
        chest.setItem(0, new ItemStack(Items.COAL));
        var logic = provider.getLogic();
        poweredProviders(fixture, provider).thenExecute(() -> {
            installPattern(logic, mixedPattern());
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
            var request = request(fixture, logic, BigInteger.TEN);
            try (var admission = prepare(request)) {
                helper.assertTrue(admission == null, "Blocking mode should leave this pattern to native AE2 dispatch");
            }
            helper.assertTrue(!logic.pushPattern(request.pattern(), request.unitInputs())
                            && chest.getItem(0).getCount() == 1 && chest.getItem(1).isEmpty(),
                    "Native blocking should reject a chest that still contains the pattern's coal input");

            chest.setItem(0, ItemStack.EMPTY);
            helper.assertTrue(logic.pushPattern(request.pattern(), request.unitInputs()),
                    "The same native push should accept a single craft once the blocking input is removed");
            helper.assertTrue(chest.getItem(0).is(Items.COAL) && chest.getItem(0).getCount() == 3
                            && chest.getItem(1).is(Items.GOLD_INGOT) && chest.getItem(1).getCount() == 13,
                    "Native push should deliver the exact input quantities of one craft");
            assertDrained(fixture, logic);
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE)
    public static void dismantledProviderReturnsWholeInputsAcrossPartialDeposits(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var provider = fixture.place(fixture.origin, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState(),
                PatternProviderBlockEntity.class);
        var chest = fixture.place(fixture.origin.east(2), AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
        chest.setCell(AEItems.ITEM_CELL_16K.stack());
        var cell = fixture.level.getCapability(AECapabilities.ME_STORAGE, chest.getBlockPos(), Direction.EAST);
        var coal = new GenericStack(AEItemKey.of(Items.COAL), 100_000);
        var water = new GenericStack(AEFluidKey.of(Fluids.WATER), Long.MAX_VALUE);
        var queue = new ListTag();
        var registries = fixture.level.registryAccess();
        queue.add(GenericStack.writeTag(registries, coal));
        queue.add(GenericStack.writeTag(registries, water));
        var tag = new CompoundTag();
        tag.put(PatternProviderLogic.NBT_SEND_LIST, queue);
        tag.putByte(PatternProviderLogic.NBT_SEND_DIRECTION, (byte) Direction.WEST.get3DDataValue());
        provider.getLogic().readFromNBT(tag, registries);
        var player = fixture.player("recover-input", fixture.origin);
        var source = IActionSource.ofPlayer(player);
        long full = cell.insert(coal.what(), Long.MAX_VALUE, Actionable.MODULATE, source);
        helper.assertTrue(full >= coal.amount() && cell.extract(coal.what(), 64, Actionable.MODULATE, source) == 64,
                "The ME chest should have exactly 64 free inputs");
        fixture.level.destroyBlock(fixture.origin, true);
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            var parcels = fixture.level.getEntitiesOfClass(ItemEntity.class, new AABB(fixture.origin).inflate(2),
                    entity -> entity.getItem().is(ModContent.RECOVERED_PATTERN_INPUT.get()));
            helper.assertTrue(parcels.size() == 2, "Breaking the provider should produce one parcel per undelivered input");
            helper.assertTrue(parcels.stream().anyMatch(entity -> water.equals(entity.getItem().get(AEComponents.WRAPPED_STACK))),
                    "The fluid quantity should survive dismantling at the long boundary");
            var entity = parcels.stream().filter(drop -> coal.equals(drop.getItem().get(AEComponents.WRAPPED_STACK))).findFirst().orElseThrow();
            var parcel = entity.getItem();
            entity.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, parcel);
            var pos = chest.getBlockPos();
            var hit = new BlockHitResult(pos.getCenter(), Direction.EAST, pos, false);
            player.gameMode.useItemOn(player, fixture.level, parcel, InteractionHand.MAIN_HAND, hit);
            var remaining = new GenericStack(coal.what(), coal.amount() - 64);
            helper.assertTrue(remaining.equals(parcel.get(AEComponents.WRAPPED_STACK)) && cell.getAvailableStacks().get(coal.what()) == full,
                    "Only the 64 coal accepted by the chest should leave the parcel");
            cell.extract(coal.what(), remaining.amount(), Actionable.MODULATE, source);
            player.gameMode.useItemOn(player, fixture.level, parcel, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(parcel.isEmpty() && cell.getAvailableStacks().get(coal.what()) == full,
                    "The remaining 99,936 coal should return exactly once");
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

    private static GameTestSequence poweredProviders(ServerRegressionFixture fixture, PatternProviderBlockEntity... providers) {
        var power = fixture.place(fixture.origin.north(8), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(),
                AENetworkedBlockEntity.class);
        return fixture.helper.startSequence().thenWaitUntil(() -> {
            fixture.helper.assertTrue(power.getMainNode().getNode() != null, "The power cell grid node should be initialized");
            for (var provider : providers) {
                fixture.helper.assertTrue(provider.getMainNode().getNode() != null, "Provider grid node should be initialized");
            }
        }).thenExecute(() -> {
            for (var provider : providers) {
                GridHelper.createConnection(power.getMainNode().getNode(), provider.getMainNode().getNode());
            }
        }).thenWaitUntil(() -> {
            for (var provider : providers) {
                fixture.helper.assertTrue(provider.getMainNode().isActive(), "Provider should have a powered channel");
            }
        });
    }

    private static ItemStack mixedPattern() {
        var coal = AEItemKey.of(Items.COAL);
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(coal, 1), new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 13),
                        new GenericStack(coal, 2)),
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 5)));
    }

    private static void installPattern(PatternProviderLogic logic, ItemStack pattern) {
        logic.getPatternInv().setItemDirect(0, pattern);
        logic.updatePatterns();
    }

    private static void leaveEmptySlots(ChestBlockEntity chest, int emptySlots) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            chest.setItem(slot, slot < emptySlots ? ItemStack.EMPTY : new ItemStack(Items.COBBLESTONE, 64));
        }
    }

    private static void assertCapacity(ServerRegressionFixture fixture, OmniCapacityRequest request, long crafts,
            String message) {
        try (var admission = prepare(request)) {
            fixture.helper.assertTrue(admission != null && BigInteger.valueOf(crafts).equals(admission.maximumCrafts()), message);
        }
    }

    private static OmniCapacityAdmission prepare(OmniCapacityRequest request) {
        return ((OmniCapacityTarget) request.provider()).omniDispatch$prepareTarget(request);
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

    private static OmniCapacityRequest request(ServerRegressionFixture fixture, PatternProviderLogic logic, BigInteger maximum) {
        var pattern = logic.getAvailablePatterns().getFirst();
        var patternInputs = pattern.getInputs();
        var inputs = new KeyCounter[patternInputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            var input = patternInputs[slot];
            inputs[slot] = new KeyCounter();
            inputs[slot].add(input.getPossibleInputs()[0].what(), input.getMultiplier());
        }
        var outputs = new KeyCounter();
        for (var output : pattern.getOutputs()) {
            outputs.add(output.what(), output.amount());
        }
        return new OmniCapacityRequest(UUID.randomUUID(), logic, pattern, inputs, outputs, new KeyCounter(), maximum, fixture.level);
    }

    private static void assertQueue(ServerRegressionFixture fixture, PatternProviderLogic logic,
            GenericStack expected, Direction side) {
        assertQueue(fixture, logic, side, expected);
    }

    private static void assertQueue(ServerRegressionFixture fixture, PatternProviderLogic logic, Direction side,
            GenericStack... expected) {
        var saved = save(fixture, logic);
        var queue = queue(saved);
        fixture.helper.assertTrue(logic.isBusy() && queue.size() == expected.length && saved.contains(PatternProviderLogic.NBT_SEND_DIRECTION)
                        && saved.getByte(PatternProviderLogic.NBT_SEND_DIRECTION) == side.get3DDataValue(),
                "The saved native queue should contain the expected number of entries for " + side);
        for (int index = 0; index < expected.length; index++) {
            var actual = GenericStack.readTag(fixture.level.registryAccess(), queue.getCompound(index));
            fixture.helper.assertTrue(expected[index].equals(actual),
                    "Saved queue entry " + index + " should be " + expected[index] + ", got " + actual);
        }
    }

    private static void assertDrained(ServerRegressionFixture fixture, PatternProviderLogic logic) {
        var saved = save(fixture, logic);
        fixture.helper.assertTrue(!logic.isBusy() && queue(saved).isEmpty() && !saved.contains(PatternProviderLogic.NBT_SEND_DIRECTION),
                "The drained or idle provider should have no saved stock or send direction");
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
