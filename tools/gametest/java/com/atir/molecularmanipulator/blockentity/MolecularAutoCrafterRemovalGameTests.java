package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real AE2 wrench/vanilla removal and item placement, including retained private inventories. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularAutoCrafterRemovalGameTests {
    private static final long BUFFERED = (1L << 33) + 37;

    @GameTest(template = "multiblock_dismantle_empty")
    public static void wrenchPatternsAndSettingsSurviveRepeatedPlacement(GameTestHelper helper) {
        for (int slots : new int[]{1, MolecularAutoCrafter.PATTERN_SLOTS}) {
            roundTripWithWrench(helper, slots, slots > 1 ? BUFFERED : 0, false);
        }
        System.out.println("AUTO_CRAFTER_WRENCH_PATTERNS_PASS slots=1,9 rounds=2 machineCount=1 settings=true");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty")
    public static void wrenchOutputOnlyAndEmptyMachinesReturnExactlyOneItem(GameTestHelper helper) {
        roundTripWithWrench(helper, 0, BUFFERED, false);
        roundTripWithWrench(helper, 0, 0, false);
        System.out.println("AUTO_CRAFTER_WRENCH_OUTPUTS_PASS outputOnly=true empty=true longAmounts=true machineCount=1");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty")
    public static void fullPlayerInventoryDropsOneRetainedMachine(GameTestHelper helper) {
        roundTripWithWrench(helper, MolecularAutoCrafter.PATTERN_SLOTS, BUFFERED, true);
        System.out.println("AUTO_CRAFTER_WRENCH_FULL_INVENTORY_PASS worldDrop=1 inventoryMachines=0 rounds=2");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty")
    public static void ordinaryBreakingAlsoReturnsOneMachineWithAllContents(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = player(helper, pos, false);
        for (int slots : new int[]{0, 1, MolecularAutoCrafter.PATTERN_SLOTS}) {
            long buffered = slots > 1 ? BUFFERED : 0;
            var machine = create(helper, pos);
            var patterns = configure(helper, machine, slots, buffered);
            var ordinary = Block.getDrops(machine.getBlockState(), level, pos, machine);
            helper.assertTrue(ordinary.stream().filter(stack -> stack.is(ModContent.MOLECULAR_AUTO_CRAFTER_ITEM.get()))
                            .mapToInt(ItemStack::getCount).sum() == (machine.hasRemovalRecovery() ? 0 : 1),
                    "Only retained-content removal suppresses the ordinary machine loot");
            helper.assertTrue(level.destroyBlock(pos, true), "Ordinary block removal succeeds");
            var drop = takeOnlyMachine(helper, player, pos, true);
            var restored = place(helper, player, pos, drop);
            assertContents(helper, restored, patterns, buffered);
            // Remove the replacement through the wrench path before testing the next case.
            dismantle(helper, player, pos);
            takeOnlyMachine(helper, player, pos, false);
        }
        System.out.println("AUTO_CRAFTER_NORMAL_BREAK_PASS empty=true patterns=1,9 buffered=true machineCount=1");
        helper.succeed();
    }

    private static void roundTripWithWrench(GameTestHelper helper, int slots, long buffered, boolean full) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var player = player(helper, pos, full);
        var machine = create(helper, pos);
        var patterns = configure(helper, machine, slots, buffered);
        for (int round = 0; round < 2; round++) {
            dismantle(helper, player, pos);
            helper.assertTrue(!machine.hasRemovalRecovery(), "The removed owner is cleared before onRemove");
            var extra = new ArrayList<ItemStack>();
            machine.addAdditionalDrops(helper.getLevel(), pos, extra);
            helper.assertTrue(extra.isEmpty(), "Cleared contents cannot produce a second retained drop");
            var drop = takeOnlyMachine(helper, player, pos, full);
            helper.assertTrue(drop.has(DataComponents.BLOCK_ENTITY_DATA) == (slots > 0 || buffered > 0),
                    "Only a non-empty machine carries retained contents");
            machine = place(helper, player, pos, drop);
            assertContents(helper, machine, patterns, buffered);
        }
        dismantle(helper, player, pos);
        takeOnlyMachine(helper, player, pos, full);
    }

    private static MolecularAutoCrafterBlockEntity create(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModContent.MOLECULAR_AUTO_CRAFTER.get().defaultBlockState(), 3);
        return (MolecularAutoCrafterBlockEntity) level.getBlockEntity(pos);
    }

    private static FakePlayer player(GameTestHelper helper, BlockPos pos, boolean full) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[OmniRemoval]"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
        if (full) {
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
        }
        return player;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> configure(GameTestHelper helper, MolecularAutoCrafterBlockEntity machine,
            int slots, long buffered) {
        var recipe = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace("oak_planks")).orElseThrow();
        var grid = new ItemStack[9];
        Arrays.fill(grid, ItemStack.EMPTY);
        grid[0] = new ItemStack(Items.OAK_LOG);
        var patterns = new ArrayList<ItemStack>();
        for (int slot = 0; slot < slots; slot++) {
            var pattern = PatternDetailsHelper.encodeCraftingPattern(recipe, grid,
                    new ItemStack(Items.OAK_PLANKS, 4), false, false);
            pattern.set(DataComponents.CUSTOM_NAME, Component.literal("Retained pattern " + slot));
            patterns.add(pattern.copy());
            machine.getAutoCraftPatternInventory().setItemDirect(slot, pattern);
            machine.getAutoCrafter().setEnabled(slot, true);
        }
        machine.setName("Retained Auto Crafter");
        if (slots > 0 || buffered > 0) {
            machine.toggleOutputMode();
            machine.toggleOutputSide(Direction.UP.get3DDataValue());
        }
        if (buffered > 0) machine.queueAutoCraftRefund(AEItemKey.of(Items.DIAMOND), buffered);
        return patterns;
    }

    private static void dismantle(GameTestHelper helper, FakePlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(pos.getCenter(), Direction.NORTH, pos, false);
        var event = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction()
                        && helper.getLevel().getBlockState(pos).isAir(),
                "The registered AE2 shift-wrench event must dismantle the actual block");
    }

    private static ItemStack takeOnlyMachine(GameTestHelper helper, FakePlayer player, BlockPos pos, boolean inWorld) {
        var drops = new ArrayList<ItemStack>();
        var inventory = player.getInventory();
        int inventoryMachines = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack.is(ModContent.MOLECULAR_AUTO_CRAFTER_ITEM.get())) {
                inventoryMachines += stack.getCount();
                drops.add(inventory.removeItemNoUpdate(slot));
            }
        }
        var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        for (var entity : entities) {
            drops.add(entity.getItem());
            entity.discard();
        }
        int machines = drops.stream().filter(stack -> stack.is(ModContent.MOLECULAR_AUTO_CRAFTER_ITEM.get()))
                .mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(machines == 1, "Removing one auto-crafter must return exactly one machine, got " + machines);
        helper.assertTrue(drops.size() == 1, "Patterns and buffered resources must remain inside the single machine");
        helper.assertTrue(inWorld ? inventoryMachines == 0 : inventoryMachines == 1,
                "The single machine must go to the expected inventory/world destination");
        return drops.getFirst();
    }

    private static MolecularAutoCrafterBlockEntity place(GameTestHelper helper, FakePlayer player, BlockPos pos,
            ItemStack stack) {
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(pos.getCenter().add(0, -.5, 0), Direction.UP, pos.below(), false);
        var context = new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction() && stack.isEmpty(),
                "Actual survival placement consumes the only recovered machine item");
        return (MolecularAutoCrafterBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static void assertContents(GameTestHelper helper, MolecularAutoCrafterBlockEntity machine,
            List<ItemStack> patterns, long buffered) {
        helper.assertTrue(machine != null && machine.getCustomName() != null
                        && machine.getCustomName().getString().equals("Retained Auto Crafter"),
                "Actual placement restores the machine and its custom name");
        for (int slot = 0; slot < MolecularAutoCrafter.PATTERN_SLOTS; slot++) {
            var actual = machine.getAutoCraftPatternInventory().getStackInSlot(slot);
            helper.assertTrue(slot < patterns.size() ? ItemStack.matches(actual, patterns.get(slot)) : actual.isEmpty(),
                    "Every original physical pattern, count and component must survive in its slot: " + slot);
            if (slot < patterns.size()) helper.assertTrue(machine.getAutoCrafter().getView(slot).enabled(),
                    "Independent slot settings survive placement");
        }
        helper.assertTrue(machine.getBufferedAutoCraftAmount(AEItemKey.of(Items.DIAMOND)) == buffered,
                "Buffered long resource amounts survive exactly, without loss or duplication");
        if (!patterns.isEmpty() || buffered > 0) helper.assertTrue(
                machine.getOutputMode() == MolecularAutoCrafterBlockEntity.OutputMode.ADJACENT
                        && machine.getOutputSides() == 1 << Direction.UP.get3DDataValue(),
                "Retained output mode and sides survive placement");
    }
}
