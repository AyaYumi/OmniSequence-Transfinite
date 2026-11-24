package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class OrdinaryPatternProviderGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 320)
    public static void vanillaProvidersAlternateGrowingProbesAndResumeAfterBackpressure(GameTestHelper helper) {
        var writes = new ArrayList<Insertion>();
        placeVanillaGrid(helper);
        var first = placeBarrel(helper, Direction.WEST, writes, 30, 0);
        var second = placeBarrel(helper, Direction.EAST, writes, 62, 0);

        helper.runAfterDelay(140, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(VANILLA_CORE);
            var cpu = startVanillaJob(helper, core, false);
            helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(first.isFull() && second.isFull(),
                            "Both powered providers must fill their available inventory space"))
                    .thenExecuteAfter(25, () -> {
                        helper.assertTrue(writes.size() == 9,
                                "Expected four probes at each provider and one extra probe at the open target: " + writes);
                        var amounts = List.of(2, 4, 8, 16);
                        Direction lead = writes.get(0).side();
                        for (int i = 0; i < amounts.size(); i++) {
                            helper.assertTrue(writes.get(2 * i).equals(new Insertion(lead, Items.COBBLESTONE, amounts.get(i)))
                                            && writes.get(2 * i + 1).equals(new Insertion(lead.getOpposite(),
                                                    Items.COBBLESTONE, amounts.get(i))),
                                    "Real writes must alternate 1, 2, 4, 8 recipes across both providers: " + writes);
                        }
                        helper.assertTrue(writes.get(8).equals(new Insertion(Direction.EAST, Items.COBBLESTONE, 32)),
                                "The second provider must continue with a sixteen-recipe probe: " + writes);
                        assertQueue(helper, Direction.WEST, List.of());
                        assertQueue(helper, Direction.EAST, List.of());
                        assertVanillaAccounting(helper, cpu, 46, false);
                        first.setRoom(0, Items.COBBLESTONE, 14);
                        second.setRoom(0, Items.COBBLESTONE, 18);
                    })
                    .thenWaitUntil(() -> helper.assertTrue(first.isFull() && second.isFull(),
                            "Both providers must dispatch again once their targets reopen"))
                    .thenExecuteAfter(2, () -> {
                        var resumed = writes.subList(9, writes.size());
                        helper.assertTrue(written(resumed, Direction.WEST) == 14 && written(resumed, Direction.EAST) == 18,
                                "Reopened targets must each accept only the newly available space: " + resumed);
                        assertQueue(helper, Direction.WEST, List.of());
                        assertQueue(helper, Direction.EAST, List.of());
                        assertVanillaAccounting(helper, cpu, 62, false);
                        core.clearContent();
                    })
                    .thenSucceed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 320)
    public static void vanillaProviderPersistsOnlyTheUnwrittenRemainderOfOneRecipe(GameTestHelper helper) {
        var writes = new ArrayList<Insertion>();
        placeVanillaGrid(helper);
        var first = placeBarrel(helper, Direction.WEST, writes, 5, 10);
        var second = placeBarrel(helper, Direction.EAST, writes, 7, 14);
        first.rejectCoalAfterSecondCobblestone = true;

        helper.runAfterDelay(140, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(VANILLA_CORE);
            var cpu = startVanillaJob(helper, core, true);
            helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(first.cobblestoneWritten >= 2 && second.isFull(),
                            "The first target must become blocked mid-recipe while the second keeps dispatching"))
                    .thenExecuteAfter(25, () -> {
                        var partial = writes.stream().filter(write -> write.side() == Direction.WEST).toList();
                        helper.assertTrue(partial.equals(List.of(
                                        new Insertion(Direction.WEST, Items.COBBLESTONE, 1),
                                        new Insertion(Direction.WEST, Items.COAL, 2),
                                        new Insertion(Direction.WEST, Items.COBBLESTONE, 1))),
                                "Only one complete recipe and the first ingredient of the next may be written: " + partial);
                        assertCompleteRecipes(helper, writes, Direction.EAST, 7);
                        assertQueue(helper, Direction.WEST, List.of(new GenericStack(AEItemKey.of(Items.COAL), 2)));
                        assertQueue(helper, Direction.EAST, List.of());
                        assertVanillaAccounting(helper, cpu, 9, true);
                        first.rejectCoalAfterSecondCobblestone = false;
                    })
                    .thenWaitUntil(() -> helper.assertTrue(first.isFull(),
                            "The queued remainder and the following recipes must reach the reopened target"))
                    .thenExecuteAfter(2, () -> {
                        assertCompleteRecipes(helper, writes, Direction.WEST, 5);
                        assertCompleteRecipes(helper, writes, Direction.EAST, 7);
                        assertQueue(helper, Direction.WEST, List.of());
                        assertQueue(helper, Direction.EAST, List.of());
                        assertVanillaAccounting(helper, cpu, 12, true);
                        core.clearContent();
                    })
                    .thenSucceed();
        });
    }

    private static final BlockPos VANILLA_CORE = new BlockPos(2, 1, 1);
    private static final long VANILLA_REQUESTED = 1000;

    private static void placeVanillaGrid(GameTestHelper helper) {
        helper.setBlock(VANILLA_CORE, ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(VANILLA_CORE.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(VANILLA_CORE.west(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.WEST));
        helper.setBlock(VANILLA_CORE.east(), AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST));
    }

    private static RecordingBarrel placeBarrel(GameTestHelper helper, Direction side, List<Insertion> writes,
            int cobblestoneRoom, int coalRoom) {
        var position = VANILLA_CORE.relative(side, 2);
        var absolute = helper.absolutePos(position);
        helper.setBlock(position, Blocks.BARREL);
        var level = helper.getLevel();
        var barrel = new RecordingBarrel(absolute, level.getBlockState(absolute), side, writes, coalRoom > 0);
        level.removeBlockEntity(absolute);
        level.setBlockEntity(barrel);
        barrel.setRoom(0, Items.COBBLESTONE, cobblestoneRoom);
        for (int slot = 1; slot < barrel.getContainerSize(); slot++) {
            barrel.setRoom(slot, Items.OBSIDIAN, 0);
        }
        if (coalRoom > 0) {
            barrel.setRoom(1, Items.COAL, coalRoom);
        }
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, absolute, side.getOpposite());
        helper.assertTrue(handler != null && handler.insertItem(0,
                        new ItemStack(Items.COBBLESTONE, cobblestoneRoom), true).isEmpty(),
                "The provider side of the barrel must expose real insertable inventory space");
        return barrel;
    }

    private static CraftingCpuLogic startVanillaJob(GameTestHelper helper, OmniComputationCoreBlockEntity core,
            boolean multiInput) {
        helper.assertTrue(ModConfig.OMNI_BATCH_DISPATCH_ENABLED.get(), "Enable Omni batch dispatch for these probes");
        var grid = core.getMainNode().getGrid();
        var cluster = core.getCluster();
        helper.assertTrue(core.isNetworkOnline() && cluster != null && grid != null,
                "The fixture requires an online Nexus with a virtual CPU");
        var first = vanillaProvider(helper, Direction.WEST);
        var second = vanillaProvider(helper, Direction.EAST);
        var input = AEItemKey.of(Items.COBBLESTONE);
        var coal = AEItemKey.of(Items.COAL);
        var output = AEItemKey.of(Items.EMERALD);
        var ingredients = multiInput ? List.of(new GenericStack(input, 1), new GenericStack(coal, 2))
                : List.of(new GenericStack(input, 2));
        var encoded = PatternDetailsHelper.encodeProcessingPattern(ingredients, List.of(new GenericStack(output, 2)));
        for (var host : List.of(first, second)) {
            var logic = host.getLogic();
            helper.assertTrue(host.getMainNode().getGrid() == grid && host.getMainNode().isActive()
                            && logic.getClass() == PatternProviderLogic.class && !logic.isBlocking()
                            && logic.getCraftingLockedReason() == LockCraftingMode.NONE,
                    "Both providers must use unlocked vanilla logic on the powered Nexus grid");
            logic.getPatternInv().setItemDirect(0, encoded.copy());
            logic.updatePatterns();
        }
        var patterns = first.getLogic().getAvailablePatterns();
        helper.assertTrue(patterns.size() == 1 && patterns.get(0).getClass() == AEProcessingPattern.class
                        && patterns.equals(second.getLogic().getAvailablePatterns()),
                "Both vanilla providers must advertise the same ordinary AE2 processing pattern");
        var plan = new CraftingPlan(new GenericStack(output, VANILLA_REQUESTED * 2), 100, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(patterns.get(0), VANILLA_REQUESTED));
        helper.assertTrue(grid.getCraftingService().submitJob(plan, null, cluster, false, IActionSource.empty()).successful(),
                "The large vanilla-provider order must submit");
        var cpu = cluster.craftingLogic;
        cpu.getInventory().insert(input, VANILLA_REQUESTED * (multiInput ? 1 : 2), Actionable.MODULATE);
        if (multiInput) {
            cpu.getInventory().insert(coal, VANILLA_REQUESTED * 2, Actionable.MODULATE);
        }
        return cpu;
    }

    private static PatternProviderBlockEntity vanillaProvider(GameTestHelper helper, Direction side) {
        return (PatternProviderBlockEntity) helper.getBlockEntity(VANILLA_CORE.relative(side));
    }

    private static void assertVanillaAccounting(GameTestHelper helper, CraftingCpuLogic cpu, long started,
            boolean multiInput) {
        long remaining = VANILLA_REQUESTED - started;
        var output = AEItemKey.of(Items.EMERALD);
        helper.assertTrue(cpu.getWaitingFor(output) == started * 2 && cpu.getPendingOutputs(output) == remaining * 2
                        && cpu.getInventory().list.get(AEItemKey.of(Items.COBBLESTONE)) == remaining * (multiInput ? 1 : 2)
                        && (!multiInput || cpu.getInventory().list.get(AEItemKey.of(Items.COAL)) == remaining * 2),
                "Only started recipes may consume CPU inputs and become waiting outputs. Expected " + started
                        + " started recipes, waiting=" + cpu.getWaitingFor(output)
                        + ", pending=" + cpu.getPendingOutputs(output));
    }

    private static void assertQueue(GameTestHelper helper, Direction side, List<GenericStack> expected) {
        var logic = vanillaProvider(helper, side).getLogic();
        var saved = new CompoundTag();
        logic.writeToNBT(saved, helper.getLevel().registryAccess());
        var persisted = saved.getList(PatternProviderLogic.NBT_SEND_LIST, Tag.TAG_COMPOUND);
        var actual = new ArrayList<GenericStack>();
        for (int i = 0; i < persisted.size(); i++) {
            actual.add(GenericStack.readTag(helper.getLevel().registryAccess(), persisted.getCompound(i)));
        }
        helper.assertTrue(actual.equals(expected) && logic.isBusy() == !expected.isEmpty(),
                "Unexpected durable send queue for " + side + ": " + actual);
    }

    private static int written(List<Insertion> writes, Direction side) {
        return writes.stream().filter(write -> write.side() == side).mapToInt(Insertion::amount).sum();
    }

    private static void assertCompleteRecipes(GameTestHelper helper, List<Insertion> writes, Direction side, int count) {
        int complete = 0;
        int coalDue = 0;
        for (var write : writes) {
            if (write.side() != side) continue;
            if (write.item() == Items.COBBLESTONE) {
                helper.assertTrue(coalDue == 0 && write.amount() == 1,
                        "A new recipe began before the previous recipe was complete: " + writes);
                coalDue = 2;
            } else {
                helper.assertTrue(write.item() == Items.COAL && write.amount() > 0 && write.amount() <= coalDue,
                        "A recipe received the wrong remaining ingredient amount: " + writes);
                coalDue -= write.amount();
                if (coalDue == 0) complete++;
            }
        }
        helper.assertTrue(complete == count && coalDue == 0, "Expected " + count + " complete recipes: " + writes);
    }

    private record Insertion(Direction side, Item item, int amount) { }

    // The normal barrel capability calls setItem only for a real inventory write.
    private static final class RecordingBarrel extends BarrelBlockEntity {
        private final Direction side;
        private final List<Insertion> writes;
        private final boolean multiInput;
        private int cobblestoneWritten;
        private boolean rejectCoalAfterSecondCobblestone;

        private RecordingBarrel(BlockPos position, BlockState state, Direction side, List<Insertion> writes,
                boolean multiInput) {
            super(position, state);
            this.side = side;
            this.writes = writes;
            this.multiInput = multiInput;
        }

        private void setRoom(int slot, Item item, int room) {
            super.setItem(slot, new ItemStack(item, 64 - room));
        }

        private boolean isFull() {
            return getItem(0).getCount() == 64 && (!multiInput || getItem(1).getCount() == 64);
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            // Both ingredients fit during simulation of the second recipe. Writing its first
            // ingredient changes the target before AE2 tries to write the coal.
            return !(rejectCoalAfterSecondCobblestone && stack.is(Items.COAL) && cobblestoneWritten >= 2)
                    && super.canPlaceItem(slot, stack);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int before = getItem(slot).getCount();
            super.setItem(slot, stack);
            var updated = getItem(slot);
            int amount = updated.getCount() - before;
            if (amount > 0 && (updated.is(Items.COBBLESTONE) || updated.is(Items.COAL))) {
                writes.add(new Insertion(side, updated.getItem(), amount));
                if (updated.is(Items.COBBLESTONE)) {
                    cobblestoneWritten += amount;
                }
            }
        }
    }
}
