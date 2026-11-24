package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternContainer;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class AdvancedAEProviderDispatchGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void shortSparseInputDoesNotPartiallyStockExternalInventory(GameTestHelper helper) {
        var providerBlock = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation("advanced_ae", "adv_pattern_provider"))
                .orElseThrow(() -> new IllegalStateException("AdvancedAE pattern provider block is missing"));
        var providerPos = new BlockPos(2, 1, 1);
        var targetPos = providerPos.east();
        helper.setBlock(providerPos, providerBlock.defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST));
        helper.setBlock(providerPos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(targetPos, Blocks.BARREL);
        var host = (AENetworkBlockEntity) helper.getBlockEntity(providerPos);
        var target = (BarrelBlockEntity) helper.getBlockEntity(targetPos);
        var node = host.getMainNode();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(node.isActive(), "Powered AdvancedAE provider"))
                .thenExecute(() -> dispatch(helper, host, target)).thenSucceed();
    }

    private static void dispatch(GameTestHelper helper, AENetworkBlockEntity host, BarrelBlockEntity target) {
        var provider = host.getMainNode().getNode().getService(ICraftingProvider.class);
        helper.assertTrue(provider != null, "AdvancedAE block must register its crafting provider service");
        var swordStack = new ItemStack(Items.IRON_SWORD);
        swordStack.setDamageValue(7);
        var sword = AEItemKey.of(swordStack);
        var coal = AEItemKey.of(Items.COAL);
        var sparse = new GenericStack[]{new GenericStack(sword, 1), null, new GenericStack(coal, 3),
                new GenericStack(sword, 1)};
        var encoded = PatternDetailsHelper.encodeProcessingPattern(sparse,
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.EMERALD), 1)});
        ((PatternContainer) host).getTerminalPatternInventory().setItemDirect(0, encoded);
        var patterns = provider.getAvailablePatterns();
        helper.assertTrue(patterns.size() == 1 && patterns.get(0).getClass() == AEProcessingPattern.class,
                "The real provider must decode and offer exactly one native AE2 processing pattern");
        var pattern = (AEProcessingPattern) patterns.get(0);
        helper.assertTrue(pattern.getInputs().length == 2 && Arrays.equals(pattern.getSparseInputs(), sparse),
                "AE2 must condense the duplicate input while retaining the sparse slot order");

        // The first sword and all coal fit; only the second sword slot is short. Supply coal first
        // so successful insertion in holder order is distinguishable from native sparse order.
        var coalInput = new KeyCounter();
        coalInput.add(coal, 3);
        var swordInput = new KeyCounter();
        swordInput.add(sword, 1);
        var holder = new KeyCounter[]{coalInput, swordInput};
        helper.assertTrue(!provider.pushPattern(pattern, holder), "Short native pattern must be rejected");
        helper.assertTrue(target.isEmpty(), "Rejected native pattern must not partially stock the barrel");
        helper.assertTrue(!provider.isBusy(), "Rejected native pattern must not queue inputs for sending");
        helper.assertTrue(holder[0] == coalInput && holder[1] == swordInput
                        && coalInput.size() == 1 && coalInput.get(coal) == 3
                        && swordInput.size() == 1 && swordInput.get(sword) == 1,
                "Rejected native pattern must leave both holder entries intact");

        swordInput.add(sword, 1);
        helper.assertTrue(provider.pushPattern(pattern, holder), "The same pattern must dispatch once fully supplied");
        // Unstackable swords make an accidental coalesced or holder-ordered dispatch visible in the barrel.
        assertSlot(helper, target, 0, sword, 1);
        assertSlot(helper, target, 1, coal, 3);
        assertSlot(helper, target, 2, sword, 1);
        for (int slot = 3; slot < target.getContainerSize(); slot++) {
            helper.assertTrue(target.getItem(slot).isEmpty(), "Unexpected extra input in barrel slot " + slot);
        }
        helper.assertTrue(!provider.isBusy(), "Fully inserted inputs must not remain in the send queue");
    }

    private static void assertSlot(GameTestHelper helper, BarrelBlockEntity target, int slot, AEItemKey key, int count) {
        var stack = target.getItem(slot);
        helper.assertTrue(key.equals(AEItemKey.of(stack)) && stack.getCount() == count,
                "Expected " + count + " of " + key + " in barrel slot " + slot + ", got " + stack);
    }
}
