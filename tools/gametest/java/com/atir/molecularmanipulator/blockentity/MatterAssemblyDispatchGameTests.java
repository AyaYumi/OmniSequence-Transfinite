package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.*;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.helpers.MachineSource;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.atir.molecularmanipulator.crafting.*;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Proxy;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.*;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterAssemblyDispatchGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void nexusDeliversFullLongQueueWithFiniteDispatchPower(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = new BlockPos(2800, 80, 160);
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            level.getChunkAt(pos);
            if (level.getBlockEntity(pos) instanceof appeng.blockentity.AEBaseBlockEntity old) old.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, MatterFabricationStructure.isController(part)
                    ? ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    : MatterFabricationStructure.partState(part.type()), 3);
        }
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().get(0));
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = origin.east(30);
        level.setChunkForced(powerPos.getX() >> 4, powerPos.getZ() >> 4, true);
        level.getChunkAt(powerPos);
        for (var pos : List.of(powerPos, powerPos.east(), powerPos.east(2))) {
            if (level.getBlockEntity(pos) instanceof appeng.blockentity.AEBaseBlockEntity old) old.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        level.setBlock(powerPos.east(), ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        level.setBlock(powerPos.east(2), ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        var nexus = (OmniComputationCoreBlockEntity) level.getBlockEntity(powerPos.east());
        var recipe = new MatterFabricationRecipe(
                List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 1),
                        new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1)),
                List.of(new ItemStack(Items.EMERALD)), FluidStack.EMPTY, FluidStack.EMPTY, 200, 256).withId(new ResourceLocation("molecularmanipulator:verification_full_queue"));
        var recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
        recipes.add(recipe);
        level.getRecipeManager().replaceRecipes(recipes);
        helper.runAfterDelay(5, () -> {
            controller.refreshStructure(); assembly.setControllerPos(origin);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(assembly.isOperational() && nexus.getCluster().isActive(), "Real well and Nexus must be online");
            var encoded = MatterFabricationPatternEncoding.encode(recipe, List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.IRON_INGOT)));
            assembly.getLogic().getPatternInv().setItemDirect(0, encoded);
            assembly.getLogic().updatePatterns();
        });
        helper.runAfterDelay(60, () -> {
            var pattern = assembly.getLogic().getAvailablePatterns().get(0);
            var diamond = AEItemKey.of(Items.DIAMOND);
            var iron = AEItemKey.of(Items.IRON_INGOT);
            var emerald = AEItemKey.of(Items.EMERALD);
            // Reproduce the energy truncation independently of the new durable-queue path.
            var inventory = new ListCraftingInventory(ignored -> {});
            inventory.insert(diamond, 20_000_000_000L, Actionable.MODULATE);
            inventory.insert(iron, 20_000_000_000L, Actionable.MODULATE);
            var expected = new KeyCounter(); var containers = new KeyCounter();
            var first = CraftingCpuHelper.extractPatternInputs(pattern, inventory, level, expected, containers);
            var ordinaryEnergy = new DispatchPower(16_200_000_000D);
            var ordinary = MolecularBatchCraftingExtractor.expandFromFirst(pattern, inventory, ordinaryEnergy.service,
                    level, first, expected, containers, 20_000_000_000L, false);
            helper.assertTrue(ordinary != null && ordinary.craftCount() == 8_100_000_000L,
                    "Aggregate upfront power must reproduce the 8.1G limit");
            System.out.println("MATTER_8_1G_REPRODUCED: ordinary aggregate extraction=" + ordinary.craftCount());
            int cpuOffset = 0;
            for (long count : new long[]{20_000_000_000L, Long.MAX_VALUE}) {
                var targetNexus = (OmniComputationCoreBlockEntity) level.getBlockEntity(powerPos.east(1 + cpuOffset++));
                var cpu = targetNexus.getCluster().craftingLogic;
                var plan = new CraftingPlan(new GenericStack(emerald, count), 100, false, false,
                        new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, count));
                helper.assertTrue(controller.getMainNode().getGrid().getCraftingService()
                        .submitJob(plan, null, targetNexus.getCluster(), false, new MachineSource(targetNexus)).successful(), "Real Nexus order must submit");
                cpu.getInventory().insert(diamond, count, Actionable.MODULATE);
                cpu.getInventory().insert(iron, count, Actionable.MODULATE);
                var finitePower = new DispatchPower(16);
                System.out.println("MATTER_QUEUE_BEFORE count=" + count + " diamond=" + cpu.getInventory().list.get(diamond)
                        + " waiting=" + cpu.getWaitingFor(emerald) + " queue=" + assembly.getBuffer().contents(false));
                cpu.tickCraftingLogic(finitePower.service, (appeng.me.service.CraftingService) controller.getMainNode().getGrid().getCraftingService());
                System.out.println("MATTER_QUEUE_AFTER consumed=" + finitePower.consumed + " queue=" + assembly.getBuffer().contents(false));
                helper.assertTrue(cpu.getInventory().list.isEmpty(),
                        "One real Nexus push must transfer every input: count=" + count + ", diamond=" + cpu.getInventory().list.get(diamond)
                                + ", iron=" + cpu.getInventory().list.get(iron) + ", waiting=" + cpu.getWaitingFor(emerald));
                var total = MatterFabricationPatternLogic.batchInputs(assembly.getBuffer().contents(false));
                helper.assertTrue(total.equals(Map.of(diamond, count, iron, count)) && assembly.getBuffer().queuedPatterns() == 1,
                        "The durable queue must hold the complete long material vector exactly once");
                helper.assertTrue(cpu.getWaitingFor(emerald) == count && cpu.getPendingOutputs(emerald) == 0,
                        "AE waiting and task accounting must match the full delivery");
                helper.assertTrue(finitePower.consumed == 2,
                        "Dispatch must charge one two-input delivery, while well processing keeps its recipe power cost");
                var saved = new CompoundTag();
                assembly.getBuffer().save(saved);
                assembly.getBuffer().load(saved);
                helper.assertTrue(MatterFabricationPatternLogic.batchInputs(assembly.getBuffer().contents(false)).equals(total),
                        "A full long queue must survive NBT reload without overflow");
                var excess = new KeyCounter(); excess.add(diamond, 1); excess.add(iron, 1);
                helper.assertTrue(count != Long.MAX_VALUE || !assembly.getLogic().pushPattern(pattern, new KeyCounter[]{excess}) && excess.get(diamond) == 1,
                        "A full queue must reject additional delivery while preserving CPU ownership");
                cpu.cancel();
                assembly.getBuffer().refundQueuedInputs();
                helper.assertTrue(MatterFabricationPatternLogic.batchInputs(assembly.getBuffer().contents(false)).equals(total),
                        "Refund must preserve every long input exactly once");
                assembly.getBuffer().clear();
                System.out.println("MATTER_NEXUS_FULL_QUEUE_PASS count=" + count + " deliveries=1 dispatchPower=" + finitePower.consumed);
            }
            helper.succeed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40)
    public static void hiddenPageSlotsRetainContentsAndBlockInteraction(GameTestHelper helper) throws Exception {
        var assembly = new MatterFabricationPatternAssemblyBlockEntity(BlockPos.ZERO,
                ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState());
        assembly.setLevel(helper.getLevel());
        var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIAMOND), 1)},
                new GenericStack[]{new GenericStack(AEItemKey.of(Items.EMERALD), 1)});
        for (int i = 0; i < 36; i++) {
            assembly.getLogic().getPatternInv().setItemDirect(i, encoded.copy());
            assembly.getCatalystInventory().setItemDirect(i, new ItemStack(Items.IRON_INGOT, i + 1));
        }
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "MenuSyncTest"));
        var constructor = MatterFabricationPatternAssemblyMenu.class.getDeclaredConstructor(int.class,
                net.minecraft.world.entity.player.Inventory.class, MatterFabricationPatternAssemblyBlockEntity.class);
        constructor.setAccessible(true);
        var menu = constructor.newInstance(73, player.getInventory(), assembly);
        player.containerMenu = menu;
        for (int view : new int[]{1, 0, 2, 0, 3, 0, 3, 2, 3}) {
            var data = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            data.writeVarInt(menu.containerId).writeUtf("buffer_view").writeUtf(Integer.toString(view));
            var action = new appeng.core.sync.packets.GuiDataSyncPacket(data);
            try {
                action.serverPacketData(player);
            } finally {
                action.getData().release();
                data.release();
            }
            menu.broadcastChanges();
            for (var semantic : List.of(SlotSemantics.ENCODED_PATTERN, SlotSemantics.MACHINE_INPUT)) {
                for (var slot : menu.getSlots(semantic)) {
                    var aeSlot = (AppEngSlot) slot;
                    boolean visible = semantic == SlotSemantics.ENCODED_PATTERN ? view == 0 : view == 3;
                    helper.assertTrue(!aeSlot.getItem().isEmpty() && aeSlot.isActive() == visible,
                            "All 36 slots must keep sync contents while only the selected page is active");
                    if (!visible) helper.assertTrue(!aeSlot.mayPickup(player) && !aeSlot.mayPlace(encoded)
                                    && menu.quickMoveStack(player, aeSlot.index).isEmpty(),
                            "Hidden slots must reject pickup, placement and shift-click");
                }
            }
        }
        player.containerMenu = player.inventoryMenu;
        System.out.println("MATTER_ASSEMBLY_MENU_SERVER_PASS: every page retains all slots and hidden slots reject interaction");
        helper.succeed();
    }

    private static final class DispatchPower {
        final IEnergyService service;
        double available, consumed;
        DispatchPower(double power) {
            available = power;
            service = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(), new Class[]{IEnergyService.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("extractAEPower")) {
                            double taken = Math.min((double) args[0], available);
                            if (args[1] == Actionable.MODULATE) { available -= taken; consumed += taken; }
                            return taken;
                        }
                        if (method.getReturnType() == boolean.class) return true;
                        if (method.getReturnType() == double.class) return available;
                        throw new UnsupportedOperationException(method.toString());
                    });
        }
    }
}
