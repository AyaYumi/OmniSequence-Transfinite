package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 160)
    public static void ordinaryProviderPushesOneRecipeAtATimeAcrossBackpressure(GameTestHelper helper) {
        var level = helper.getLevel();
        var corePos = new BlockPos(1, 1, 1);
        var providerPos = new BlockPos(3, 1, 1);
        var barrelPos = new BlockPos(4, 1, 1);
        helper.setBlock(corePos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(new BlockPos(1, 1, 2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(barrelPos, Blocks.BARREL);

        // Keep the vanilla barrel capability; observe actual inventory writes through it.
        var absoluteBarrel = helper.absolutePos(barrelPos);
        var receiver = new RecordingBarrel(absoluteBarrel, level.getBlockState(absoluteBarrel));
        level.removeBlockEntity(absoluteBarrel);
        level.setBlockEntity(receiver);
        for (int slot = 1; slot < receiver.getContainerSize(); slot++) {
            receiver.setItem(slot, new ItemStack(Items.OBSIDIAN, 64));
        }
        receiver.setItem(0, new ItemStack(Items.COBBLESTONE, 48));
        receiver.additions.clear();

        helper.runAfterDelay(10, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(corePos);
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
            GridHelper.createConnection(core.getActionableNode(), provider.getActionableNode());
        });
        helper.runAfterDelay(25, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(corePos);
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
            var logic = provider.getLogic();
            helper.assertTrue(core.isNetworkOnline() && provider.getMainNode().isActive()
                            && logic.getClass() == PatternProviderLogic.class && !logic.isBlocking(),
                    "Fixture must use a real online nonblocking AE2 pattern provider");
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, absoluteBarrel, Direction.WEST);
            helper.assertTrue(handler != null && handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 16), true).isEmpty(),
                    "The ordinary AE2 provider must have a real external inventory with room for eight recipes");

            var input = AEItemKey.of(Items.COBBLESTONE);
            var output = AEItemKey.of(Items.STONE);
            long requested = 12;
            logic.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(input, 2)), List.of(new GenericStack(output, 1))));
            logic.updatePatterns();
            ICraftingProvider.requestUpdate(provider.getMainNode());
            var pattern = logic.getAvailablePatterns().getFirst();
            var cluster = core.getCluster();
            var cpu = cluster.craftingLogic;
            var service = core.getMainNode().getGrid().getCraftingService();
            var plan = new CraftingPlan(new GenericStack(output, requested), 100, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, requested));
            helper.assertTrue(service.submitJob(plan, null, cluster, false, IActionSource.empty()).successful(),
                    "Ordinary processing order must submit");
            cpu.getInventory().insert(input, requested * 2, Actionable.MODULATE);

            helper.runAfterDelay(20, () -> {
                helper.assertTrue(receiver.getItem(0).getCount() == 64
                                && receiver.additions.equals(List.of(2, 2, 2, 2, 2, 2, 2, 2)),
                        "Room for eight recipes must produce eight distinct two-item insertions: " + receiver.additions);
                helper.assertTrue(cpu.getWaitingFor(output) == 8 && cpu.getInventory().list.get(input) == 8 && !logic.isBusy(),
                        "Only accepted recipes may leave the CPU; a full receiver must not create a provider queue");
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(receiver.additions.size() == 8 && cpu.getWaitingFor(output) == 8
                                    && cpu.getInventory().list.get(input) == 8 && !logic.isBusy(),
                            "A full barrel must not accept work even after the reprobe interval");
                    receiver.setItem(0, new ItemStack(Items.COBBLESTONE, 56));
                    helper.runAfterDelay(20, () -> {
                        helper.assertTrue(receiver.getItem(0).getCount() == 64
                                        && receiver.additions.subList(8, receiver.additions.size()).equals(List.of(2, 2, 2, 2)),
                                "Reopened capacity must receive the four remaining original recipes: " + receiver.additions);
                        helper.assertTrue(cpu.getWaitingFor(output) == requested && cpu.getInventory().list.get(input) == 0
                                        && !logic.isBusy(),
                                "All accepted inputs must correspond to exactly the requested waiting output count");
                        core.clearContent();
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static final class RecordingBarrel extends BarrelBlockEntity {
        private final List<Integer> additions = new ArrayList<>();

        private RecordingBarrel(BlockPos pos, BlockState state) {
            super(pos, state);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot == 0 && stack.is(Items.COBBLESTONE)) {
                int added = stack.getCount() - getItem(slot).getCount();
                if (added > 0) additions.add(added);
            }
            super.setItem(slot, stack);
        }
    }
}
