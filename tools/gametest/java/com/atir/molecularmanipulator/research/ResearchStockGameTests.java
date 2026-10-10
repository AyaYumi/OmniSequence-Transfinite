package com.atir.molecularmanipulator.research;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class ResearchStockGameTests {
    private static final AEItemKey QUARTZ = AEItemKey.of(Items.QUARTZ);

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void ordersShareOneReadAndConsumeStockOnce(GameTestHelper helper) throws Exception {
        verify(helper, false, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void changedStockInvalidatesPreparationSnapshot(GameTestHelper helper) throws Exception {
        verify(helper, true, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void partialAdmissionRefundsAndRereadsProviders(GameTestHelper helper) throws Exception {
        verify(helper, false, true);
    }

    private static void verify(GameTestHelper helper, boolean changeStock, boolean partialExtraction) throws Exception {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        // The standalone host avoids unrelated well production ticks during the stock assertions.
        var controller = new MatterFabricationBlockEntity(pos,
                ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(level);
        var formed = MatterFabricationBlockEntity.class.getDeclaredField("structureFormed");
        formed.setAccessible(true);
        formed.setBoolean(controller, true);
        controller.getMainNode().setFlags().setInWorldNode(false).create(level, pos);
        var powerPos = helper.absolutePos(new BlockPos(3, 1, 2));
        helper.setBlock(new BlockPos(3, 1, 2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(5, () -> {
            var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.runAfterDelay(40, () -> {
            var manager = level.getRecipeManager();
            var original = List.copyOf(manager.getRecipes());
            var storage = new CountingStorage(changeStock || partialExtraction ? 4 : 8);
            var service = controller.getMainNode().getGrid().getStorageService();
            IStorageProvider provider = mounts -> mounts.mount(storage, 0);
            try {
                helper.assertTrue(controller.getMainNode().isActive(), "Research fixture must have an active AE grid");
                var first = definition("first");
                var second = definition("second");
                var third = definition("third");
                manager.replaceRecipes(List.of(first, second, third));
                service.addGlobalStorageProvider(provider);
                var research = controller.getResearch();
                int reads = storage.reads;
                storage.loseOnSimulation = changeStock;
                storage.partialExtraction = partialExtraction;
                helper.assertTrue(research.orderMissing(controller, first.id()), "First research must enter preparation");
                if (partialExtraction) {
                    helper.assertTrue(!research.hasTask(first.id()) && storage.amount == 4 && storage.returned == 3,
                            "A partial payment must refund its three withdrawn items before any research starts");
                    helper.assertTrue(research.orderMissing(controller, second.id()) && research.hasTask(second.id()),
                            "Another research must be able to use the refunded live materials");
                    helper.assertTrue(storage.reads - reads == 2 && storage.withdrawn - storage.returned == 4 && storage.amount == 0,
                            "Refund must invalidate the snapshot and retain exact material ownership");
                } else if (changeStock) {
                    helper.assertTrue(!research.hasTask(first.id()) && storage.withdrawn == 0,
                            "A snapshot cannot admit materials that the live provider no longer supplies");
                    helper.assertTrue(research.orderMissing(controller, second.id()), "Second research must enter preparation");
                    helper.assertTrue(storage.reads - reads == 2 && !research.hasTask(second.id()),
                            "Failed live validation must force the next preparation to reread providers");
                    storage.amount = 4;
                    helper.assertTrue(research.start(controller, second.id()), "Explicit start must read newly supplied stock immediately");
                    helper.assertTrue(storage.withdrawn == 4 && storage.amount == 0,
                            "Recovered admission must consume exactly one research cost");
                } else {
                    helper.assertTrue(research.orderMissing(controller, second.id()), "Second research must enter preparation");
                    helper.assertTrue(research.orderMissing(controller, third.id()), "Third research must remain queued");
                    helper.assertTrue(storage.reads - reads == 1,
                            "Same-tick orders and their admission must share exactly one provider enumeration");
                    helper.assertTrue(research.hasTask(first.id()) && research.hasTask(second.id())
                                    && !research.hasTask(third.id()) && research.isPreparing(third.id()),
                            "Consumed snapshot stock must not be offered to a later research");
                    helper.assertTrue(storage.withdrawn == 8 && storage.amount == 0,
                            "Two admitted researches must consume stock exactly once");
                }
                System.out.println("RESEARCH_STOCK_PASS: changedStock=" + changeStock + " partialExtraction=" + partialExtraction
                        + " providerReads=" + (storage.reads - reads) + " withdrawn=" + storage.withdrawn + " returned=" + storage.returned);
                helper.succeed();
            } finally {
                service.removeGlobalStorageProvider(provider);
                manager.replaceRecipes(original);
                controller.getMainNode().destroy();
            }
        });
    }

    private static MatterResearchRecipe definition(String name) {
        return new MatterResearchRecipe("Stock regression", List.of(),
                        List.of(new MatterResearchRecipe.Cost(Ingredient.of(Items.QUARTZ), 4)),
                        100, 0, List.of(), List.of(), 0, 1,
                        List.of(new ResearchDepth(1, 1, 1, 0, Optional.empty()))).withId(new ResourceLocation("molecularmanipulator", "verification/stock_" + name));
    }

    private static final class CountingStorage implements MEStorage {
        long amount, withdrawn, returned;
        int reads;
        boolean loseOnSimulation, partialExtraction;

        CountingStorage(long amount) { this.amount = amount; }

        @Override public void getAvailableStacks(KeyCounter counter) {
            reads++;
            if (amount > 0) counter.add(QUARTZ, amount);
        }

        @Override public long extract(AEKey key, long requested, Actionable mode, IActionSource source) {
            if (!QUARTZ.equals(key) || requested <= 0) return 0;
            if (mode == Actionable.SIMULATE && loseOnSimulation) {
                amount = 0;
                loseOnSimulation = false;
            }
            long taken = Math.min(requested, amount);
            if (mode == Actionable.MODULATE) {
                if (partialExtraction) { taken = Math.max(0, taken - 1); partialExtraction = false; }
                amount -= taken; withdrawn += taken;
            }
            return taken;
        }

        @Override public long insert(AEKey key, long requested, Actionable mode, IActionSource source) {
            if (!QUARTZ.equals(key) || requested <= 0) return 0;
            if (mode == Actionable.MODULATE) { amount += requested; returned += requested; }
            return requested;
        }

        @Override public Component getDescription() { return Component.literal("Research stock regression"); }
    }
}
