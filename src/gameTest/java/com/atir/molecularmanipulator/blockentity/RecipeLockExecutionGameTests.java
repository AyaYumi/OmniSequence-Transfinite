package com.atir.molecularmanipulator.blockentity;

import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.*;
import static com.atir.molecularmanipulator.blockentity.RecipeLockNetworkGameTests.onPoweredGrid;
import static com.atir.molecularmanipulator.blockentity.RecipeLockNetworkGameTests.validated;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.blockentity.misc.InscriberRecipes;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.pattern.AECraftingPattern;
import com.atir.molecularmanipulator.blockentity.RecipeLockNetworkGameTests.NetworkFixture;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.atir.molecularmanipulator.recipelock.RecipeMachineOwnership;
import com.atir.molecularmanipulator.starcomputation.StarComputationNetwork;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native menus, furnace ticks, dynamic AE tasks and real encoded-pattern execution on a powered grid. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class RecipeLockExecutionGameTests {
    private static final ResourceLocation NODE_C = id("execution_research_c");
    private static final Item MENU_INPUT = Items.BARRIER;
    private static final Item MENU_OUTPUT = Items.AMETHYST_SHARD;
    private static final Item ASSEMBLER_INPUT = Items.REPEATING_COMMAND_BLOCK;
    private static final Item ASSEMBLER_OUTPUT = Items.LAPIS_LAZULI;

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_execution_inventory")
    public static void inventoryResultSlotAndQuickMoveRevalidateStaleOutputAfterRuleReload(GameTestHelper helper) {
        exerciseVanillaMenu(helper, false);
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_execution_workbench")
    public static void craftingTableResultSlotAndQuickMoveRevalidateStaleOutputAfterRuleReload(GameTestHelper helper) {
        exerciseVanillaMenu(helper, true);
    }

    private static void exerciseVanillaMenu(GameTestHelper helper, boolean workbench) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            fixture.place(fixture.origin, Blocks.CRAFTING_TABLE.defaultBlockState());
            var player = fixture.player(workbench ? "lock-table" : "lock-inventory", fixture.origin.south(2));
            var owner = team(helper, player);
            AbstractContainerMenu menu = workbench
                    ? new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(fixture.level, fixture.origin))
                    : player.inventoryMenu;
            player.containerMenu = menu;
            helper.assertTrue((workbench ? menu instanceof CraftingMenu : menu instanceof InventoryMenu)
                            && menu.stillValid(player) && menu.getSlot(0) instanceof ResultSlot,
                    "Use the actual server-owned vanilla menu and its native ResultSlot");
            var inputs = (CraftingContainer) menu.getSlot(1).container;
            var result = (ResultSlot) menu.getSlot(0);
            var results = (ResultContainer) result.container;
            helper.assertTrue(inputs.getWidth() == (workbench ? 3 : 2), "Exercise the correct native grid dimensions");
            var name = workbench ? "execution_table" : "execution_inventory";
            var recipe = ordinary(fixture, name, MENU_INPUT, MENU_OUTPUT);
            withTemporaryRecipes(fixture, List.of(recipe, node(NODE_A), node(NODE_B),
                    lock(fixture, name + "_lock", recipe.id(), NODE_A)), data -> {
                // Slot.set -> TransientCraftingContainer -> slotsChanged -> server result calculation.
                menu.getSlot(1).set(new ItemStack(MENU_INPUT, 3));
                helper.assertTrue(result.getItem().isEmpty() && count(inputs, MENU_INPUT) == 3,
                        "An unauthorized grid has no server result without removing any ingredient");
                helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty()
                                && result.safeTake(64, 64, player).isEmpty(),
                        "Neither the real shift-click method nor safeTake can manufacture a denied result");
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().isEmpty() && count(inputs, MENU_INPUT) == 3
                                && count(player.getInventory(), MENU_OUTPUT) == 0,
                        "An ordinary denied result-slot click is also material-neutral");

                helper.assertTrue(data.addUnlock(owner, NODE_A), "Grant the real player's permanent team research");
                // No client prediction or direct result-slot writes: ask the server menu to recalculate.
                menu.slotsChanged(inputs);
                menu.broadcastChanges();
                helper.assertTrue(result.getItem().is(MENU_OUTPUT) && result.getItem().getCount() == 1
                                && results.getRecipeUsed() != null && results.getRecipeUsed().id().equals(recipe.id()),
                        "The existing menu refreshes to the real recipe result immediately after a grant");
                menu.clicked(0, 0, ClickType.PICKUP, player);
                helper.assertTrue(menu.getCarried().is(MENU_OUTPUT) && menu.getCarried().getCount() == 1
                                && count(inputs, MENU_INPUT) == 2,
                        "Native pickup calls ResultSlot.onTake and consumes exactly one ingredient");
                helper.assertTrue(player.getInventory().add(menu.getCarried()) && menu.getCarried().isEmpty(),
                        "Transfer that actually crafted cursor item into the real player inventory");
                menu.setCarried(ItemStack.EMPTY);
                var shifted = menu.quickMoveStack(player, 0);
                helper.assertTrue(shifted.is(MENU_OUTPUT) && shifted.getCount() == 1
                                && count(inputs, MENU_INPUT) == 1 && count(player.getInventory(), MENU_OUTPUT) == 2,
                        "Native quickMoveStack transfers one output and consumes one input even though onQuickCraft clears recipeUsed");

                var staleOutput = result.getItem().copy();
                var retainedRecipe = results.getRecipeUsed();
                helper.assertTrue(staleOutput.is(MENU_OUTPUT) && retainedRecipe != null,
                        "Leave an authentic positive result and recipeUsed in the open menu before changing the rule");
                replaceAndNotify(fixture, List.of(recipe, node(NODE_A), node(NODE_B),
                        lock(fixture, name + "_lock", recipe.id(), NODE_B)));
                helper.assertTrue(ItemStack.matches(result.getItem(), staleOutput) && results.getRecipeUsed() == retainedRecipe
                                && !RecipeLocks.allowedForPlayer(player, retainedRecipe),
                        "Reload revokes execution while the old, genuinely calculated result remains visible and unrefreshed");
                helper.assertTrue(!result.mayPickup(player), "ResultSlot pickup rechecks the retained recipe against the live rule");
                // Direct quickMoveStack bypasses AbstractContainerMenu's outer mayPickup guard.
                helper.assertTrue(menu.quickMoveStack(player, 0).isEmpty()
                                && result.remove(1).isEmpty() && result.safeTake(64, 64, player).isEmpty(),
                        "Direct shift-click, direct ResultSlot.remove and safeTake cannot extract a stale denied result");
                menu.clicked(0, 0, ClickType.PICKUP, player);
                menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
                menu.clicked(0, 4, ClickType.SWAP, player);
                menu.clicked(0, 1, ClickType.THROW, player);
                helper.assertTrue(count(inputs, MENU_INPUT) == 1 && count(player.getInventory(), MENU_INPUT) == 0
                                && count(player.getInventory(), MENU_OUTPUT) == 2 && menu.getCarried().isEmpty()
                                && ItemStack.matches(result.getItem(), staleOutput),
                        "Pickup, repeated shift-click, hotbar swap and throw neither consume input nor deliver the stale output");
                assertNoDrops(fixture, player.blockPosition(), MENU_INPUT, MENU_OUTPUT);

                menu.slotsChanged(inputs);
                helper.assertTrue(result.getItem().isEmpty() && count(inputs, MENU_INPUT) == 1,
                        "A subsequent real server refresh also removes the denied preview without consuming material");
                helper.assertTrue(data.addUnlock(owner, NODE_B), "Pay the new rule's separate permanent grant");
                menu.slotsChanged(inputs);
                helper.assertTrue(result.getItem().is(MENU_OUTPUT), "The same open menu recovers after the new grant");
                helper.assertTrue(menu.quickMoveStack(player, 0).is(MENU_OUTPUT)
                                && inputs.isEmpty() && result.getItem().isEmpty()
                                && count(player.getInventory(), MENU_OUTPUT) == 3,
                        "All three actual ingredients become exactly three products, with no stale-result duplication or loss");
                helper.assertTrue(!RecipeExecutionContext.active(), "Every native menu/slot invocation closes its actor context");
                assertNoDrops(fixture, player.blockPosition(), MENU_INPUT, MENU_OUTPUT);
            });
        }
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_execution_furnace")
    public static void furnaceServerTickPreservesDeniedFuelAndResumesAfterUnlock(GameTestHelper helper) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var furnace = fixture.place(fixture.origin, Blocks.FURNACE.defaultBlockState(), FurnaceBlockEntity.class);
            var player = fixture.player("lock-furnace", fixture.origin);
            var owner = team(helper, player);
            helper.assertTrue(RecipeMachineOwnership.bind(furnace, player), "Bind the actual vanilla furnace to the owning team");
            var recipe = new RecipeHolder<>(id("execution_smelting"), new SmeltingRecipe("", CookingBookCategory.MISC,
                    Ingredient.of(Items.STRUCTURE_VOID), new ItemStack(Items.GOLD_NUGGET), 0, 4));
            withTemporaryRecipes(fixture, List.of(recipe, node(NODE_A), node(NODE_B),
                    lock(fixture, "execution_smelting_lock", recipe.id(), NODE_A)), data -> {
                furnace.setItem(0, new ItemStack(Items.STRUCTURE_VOID, 3));
                furnace.setItem(1, new ItemStack(Items.COAL, 2));
                for (int tick = 0; tick < 12; tick++) tickFurnace(fixture, furnace);
                var denied = furnace.saveWithoutMetadata(fixture.level.registryAccess());
                helper.assertTrue(furnace.getItem(0).getCount() == 3 && furnace.getItem(1).getCount() == 2
                                && furnace.getItem(2).isEmpty() && denied.getInt("BurnTime") == 0 && denied.getInt("CookTime") == 0
                                && !furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT),
                        "Real denied serverTick calls neither ignite the furnace, consume fuel/input nor emit output");

                helper.assertTrue(data.addUnlock(owner, NODE_A), "Unlock without changing either input or fuel inventory");
                tickFurnace(fixture, furnace);
                var started = furnace.saveWithoutMetadata(fixture.level.registryAccess());
                helper.assertTrue(furnace.getItem(0).getCount() == 3 && furnace.getItem(1).getCount() == 1
                                && furnace.getItem(2).isEmpty() && started.getInt("BurnTime") > 0 && started.getInt("CookTime") == 1
                                && furnace.getBlockState().getValue(AbstractFurnaceBlock.LIT),
                        "The very next native tick observes the live grant, consumes exactly one coal and begins cooking");
                // setItem computed vanilla's denied fallback cook time (200). Do not reset its cached
                // timing/progress or reinsert ingredients to make the first authorized craft finish.
                for (int tick = 0; tick < 240 && furnace.getItem(2).isEmpty(); tick++) tickFurnace(fixture, furnace);
                helper.assertTrue(furnace.getItem(0).getCount() == 2 && furnace.getItem(1).getCount() == 1
                                && furnace.getItem(2).is(Items.GOLD_NUGGET) && furnace.getItem(2).getCount() == 1,
                        "Actual vanilla completion consumes one input and produces the recipe output, not just a positive lookup");

                replaceAndNotify(fixture, List.of(recipe, node(NODE_A), node(NODE_B),
                        lock(fixture, "execution_smelting_lock", recipe.id(), NODE_B)));
                for (int tick = 0; tick < 12; tick++) tickFurnace(fixture, furnace);
                helper.assertTrue(furnace.getItem(0).getCount() == 2 && furnace.getItem(1).getCount() == 1
                                && furnace.getItem(2).getCount() == 1
                                && furnace.saveWithoutMetadata(fixture.level.registryAccess()).getInt("CookTime") == 0,
                        "Even a still-burning furnace and its positive CachedCheck cannot smelt the next input after rule reload");
                helper.assertTrue(data.addUnlock(owner, NODE_B), "Grant the replacement rule while the same furnace is still burning");
                for (int tick = 0; tick < 12 && furnace.getItem(2).getCount() < 2; tick++) tickFurnace(fixture, furnace);
                helper.assertTrue(furnace.getItem(0).getCount() == 1 && furnace.getItem(1).getCount() == 1
                                && furnace.getItem(2).getCount() == 2,
                        "The same native executor resumes and conserves input/output without consuming a second fuel item");
                helper.assertTrue(!RecipeExecutionContext.active(), "Furnace timing and serverTick scopes do not escape their invocations");
                assertNoDrops(fixture, furnace.getBlockPos(), Items.STRUCTURE_VOID, Items.GOLD_NUGGET);
            });
        }
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_execution_name_press")
    public static void dynamicNamePressIsDefaultOpenAndHonorsItsVirtualRecipeId(GameTestHelper helper) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var machine = fixture.place(fixture.origin, AEBlocks.INSCRIBER.block().defaultBlockState(), InscriberBlockEntity.class);
            var player = fixture.player("lock-name-press", fixture.origin);
            var owner = team(helper, player);
            helper.assertTrue(RecipeMachineOwnership.bind(machine, player), "Bind the real AE2 inscriber");
            var unrelated = ordinary(fixture, "execution_unrelated", Items.JIGSAW, Items.EMERALD);
            var unrelatedLock = lock(fixture, "execution_unrelated_lock", unrelated.id(), NODE_B);
            var press = AEItems.NAME_PRESS.stack();
            var name = Component.literal("Execution name");
            press.set(AEComponents.NAME_PRESS_NAME, name);
            var input = new ItemStack(Items.DIAMOND, 3);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("Before rename"));
            withTemporaryRecipes(fixture, List.of(unrelated, node(NODE_A), node(NODE_B), unrelatedLock), data -> {
                var inventory = machine.getInternalInventory();
                inventory.setItemDirect(0, press.copy()); // native combined inventory: top, bottom, input, output
                inventory.setItemDirect(2, input.copy());
                helper.assertTrue(RecipeLocks.hasRules(fixture.level) && !RecipeLocks.allowed(machine, unrelated),
                        "Keep an unrelated lock active so anonymous recipe identity would fail closed without the NamePress adapter");
                var dynamic = InscriberRecipes.findRecipe(fixture.level, input, press, ItemStack.EMPTY, true);
                helper.assertTrue(dynamic != null && fixture.level.getRecipeManager().getRecipes().stream()
                                .noneMatch(holder -> holder.value() == dynamic),
                        "The real AE2 finder synthesizes a NamePress task absent from the RecipeManager identity catalogue");
                helper.assertTrue(RecipeLocks.allowed(machine, dynamic) && dynamic.getResultItem().is(Items.DIAMOND)
                                && dynamic.getResultItem().getCount() == 1
                                && name.equals(dynamic.getResultItem().get(DataComponents.CUSTOM_NAME)),
                        "An otherwise anonymous dynamic task remains default-open and actually carries the pressed name");
                var nativeTask = machine.getTask(); // public in AE2 19.2.18; no reflected or substituted getTask implementation
                helper.assertTrue(nativeTask != null && machine.getTask() == nativeTask && RecipeLocks.allowed(machine, nativeTask),
                        "The real InscriberBlockEntity.getTask lookup and cached return both accept default-open renaming");

                var nameLock = lock(fixture, "execution_name_lock", RecipeLocks.NAME_PRESS_RECIPE, NODE_A);
                replaceAndNotify(fixture, List.of(unrelated, node(NODE_A), node(NODE_B), unrelatedLock, nameLock));
                helper.assertTrue(!RecipeLocks.allowed(machine, dynamic) && !RecipeLocks.allowed(machine, nativeTask),
                        "Retained dynamic task objects obey the exact virtual ID ae2:inscriber/name_press after reload");
                try (var scope = RecipeExecutionContext.open(machine)) {
                    var newlyFound = InscriberRecipes.findRecipe(fixture.level, input, press, ItemStack.EMPTY, true);
                    helper.assertTrue(newlyFound != null && !RecipeLocks.allowed(machine, newlyFound),
                            "Even a newly synthesized task is identified and denied by the virtual-ID rule, not mistaken for default-open");
                }
                helper.assertTrue(machine.getTask() == null, "The real inscriber rejects its previously cached NamePress work before execution");
                helper.assertTrue(data.addUnlock(owner, NODE_A), "Unlock the virtual NamePress operation for this owning team");
                var licensed = machine.getTask();
                helper.assertTrue(licensed != null && machine.getTask() == licensed && RecipeLocks.allowed(machine, licensed),
                        "The same real inscriber recovers without changing the press/input and may cache its newly licensed task");

                replaceAndNotify(fixture, List.of(unrelated, node(NODE_A), node(NODE_B), unrelatedLock,
                        lock(fixture, "execution_name_lock", RecipeLocks.NAME_PRESS_RECIPE, NODE_B)));
                helper.assertTrue(!RecipeLocks.allowed(machine, licensed) && machine.getTask() == null,
                        "A stricter virtual-ID rule also invalidates a previously positive cached dynamic task");
                replaceAndNotify(fixture, List.of(unrelated, node(NODE_A), node(NODE_B), unrelatedLock));
                var openAgain = machine.getTask();
                helper.assertTrue(RecipeLocks.hasRules(fixture.level) && openAgain != null && RecipeLocks.allowed(machine, openAgain)
                                && !RecipeLocks.allowed(machine, unrelated) && !data.isUnlocked(owner, NODE_B),
                        "Removing only the virtual-ID declaration restores default-open naming while other locks remain active");
                helper.assertTrue(ItemStack.matches(inventory.getStackInSlot(0), press) && inventory.getStackInSlot(1).isEmpty()
                                && ItemStack.matches(inventory.getStackInSlot(2), input) && inventory.getStackInSlot(3).isEmpty(),
                        "All actual finder/getTask cache checks preserve presses, the original named input stack and the empty output");
                helper.assertTrue(!RecipeExecutionContext.active(), "Dynamic-task lookups close their machine scope");
            });
        }
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 300, batch = "recipe_lock_execution_assembler_offline")
    public static void assemblerFinishesAdmittedPatternAfterSharedResearchGoesOffline(GameTestHelper helper) {
        exerciseAssembler(helper, false);
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 300, batch = "recipe_lock_execution_assembler_reload")
    public static void assemblerFinishesAdmittedPatternAfterLockRuleReload(GameTestHelper helper) {
        exerciseAssembler(helper, true);
    }

    private static void exerciseAssembler(GameTestHelper helper, boolean reloadRule) {
        var fixture = new NetworkFixture(helper);
        var assembler = fixture.place(fixture.origin.east(48), AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState(),
                MolecularAssemblerBlockEntity.class);
        helper.assertTrue(RecipeMachineOwnership.bind(assembler, fixture.playerB), "Bind the actual AE2 assembler to the borrower team");
        onPoweredGrid(helper, fixture, List.of(assembler.getMainNode()), ready -> {
            var name = reloadRule ? "execution_assembler_reload" : "execution_assembler_offline";
            var recipe = ordinary(ready, name, ASSEMBLER_INPUT, ASSEMBLER_OUTPUT);
            var initialLock = lock(ready, name + "_lock", recipe.id(), NODE_A, NODE_B);
            withTemporaryRecipes(ready, List.of(recipe, node(NODE_A), node(NODE_B), node(NODE_C), initialLock), data -> {
                helper.assertTrue(data.addUnlock(ready.teamA, NODE_A) && data.addUnlock(ready.teamB, NODE_B),
                        "Give the two real engine teams distinct permanent grants, neither sufficient alone");
                var before = data.save(new CompoundTag(), ready.level.registryAccess());
                data.setDirty(false);
                var grid = assembler.getMainNode().getGrid();
                var node = assembler.getMainNode().getNode();
                helper.assertTrue(node != null && assembler.getMainNode().isActive()
                                && grid == ready.engineA.getMainNode().getGrid() && grid == ready.engineB.getMainNode().getGrid()
                                && StarComputationNetwork.researchIds(grid).equals(Set.of(NODE_A, NODE_B))
                                && RecipeLocks.allowed(assembler, recipe) && !RecipeLocks.allowedForPlayer(ready.playerB, recipe),
                        "Only the real assembler's live powered network union licenses the recipe, not its owner's permanent progress");

                var sparseInputs = new ItemStack[9];
                Arrays.fill(sparseInputs, ItemStack.EMPTY);
                sparseInputs[0] = new ItemStack(ASSEMBLER_INPUT);
                var encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, sparseInputs,
                        new ItemStack(ASSEMBLER_OUTPUT), false, false);
                var decoded = PatternDetailsHelper.decodePattern(encoded, ready.level);
                helper.assertTrue(decoded instanceof AECraftingPattern && decoded.getInputs().length == 1
                                && decoded.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN).recipeId().equals(recipe.id()),
                        "Use AE2's real encoded crafting-pattern component and its native AECraftingPattern decoder");
                var firstOffer = offerOneAssemblerInput();
                var nextOffer = offerOneAssemblerInput();
                assertAssemblerBalance(ready, assembler, firstOffer, nextOffer, null, 0);
                helper.assertTrue(assembler.pushPattern(decoded, firstOffer, Direction.NORTH),
                        "Native pushPattern genuinely accepts and fills one authorized craft");
                helper.assertTrue(firstOffer[0].isEmpty() && nextOffer[0].get(AEItemKey.of(ASSEMBLER_INPUT)) == 1
                                && assembler.getCurrentPattern() == decoded && assembler.getCraftingProgress() == 0
                                && countInternal(assembler.getInternalInventory(), ASSEMBLER_INPUT) == 1,
                        "The accepted input leaves its offered KeyCounter and occupies the native grid before any progress or output");
                assertAssemblerBalance(ready, assembler, firstOffer, nextOffer, null, 0);

                if (reloadRule) {
                    replaceAndNotify(ready, List.of(recipe, node(NODE_A), node(NODE_B), node(NODE_C),
                            lock(ready, name + "_lock", recipe.id(), NODE_C)));
                } else {
                    validated(ready.engineA, false);
                    helper.assertTrue(ready.engineA.getMainNode().isActive()
                                    && StarComputationNetwork.researchIds(grid).equals(Set.of(NODE_B)),
                            "The sharing engine loses structure validity, not node power or the assembler's working network");
                }
                helper.assertTrue(assembler.getMainNode().isActive() && ready.power.getMainNode().isActive()
                                && !RecipeLocks.allowed(assembler, recipe) && !RecipeLocks.allowedPattern(assembler, decoded),
                        "Live admission is now denied while the already accepted assembler still has real power");

                // Target north is initially air. Complete the admitted craft into the native output
                // buffer first, then test the separate output-ejection branch after revocation too.
                for (int tick = 0; tick < 24 && assembler.getInternalInventory().getStackInSlot(9).isEmpty(); tick++)
                    assembler.tickingRequest(node, 1);
                var buffered = assembler.getInternalInventory().getStackInSlot(9);
                helper.assertTrue(buffered.is(ASSEMBLER_OUTPUT) && buffered.getCount() == 1
                                && countInternal(assembler.getInternalInventory(), ASSEMBLER_INPUT) == 0,
                        "Real tickingRequest power/progress/assemble completes exactly the job admitted before revocation");
                assertAssemblerBalance(ready, assembler, firstOffer, nextOffer, null, 1);
                var output = ready.place(assembler.getBlockPos().north(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
                assembler.tickingRequest(node, 1);
                helper.assertTrue(assembler.getInternalInventory().isEmpty() && assembler.getCurrentPattern() == null
                                && assembler.acceptsPlans() && count(output, ASSEMBLER_OUTPUT) == 1,
                        "The native denied-state output branch delivers the paid job to a real chest and leaves genuine capacity for another job");
                helper.assertTrue(!assembler.pushPattern(decoded, nextOffer, Direction.NORTH)
                                && nextOffer[0].get(AEItemKey.of(ASSEMBLER_INPUT)) == 1 && assembler.getInternalInventory().isEmpty(),
                        "The identical cached pattern's next order is refused on permissions, not busyness, without taking its offered input");
                for (int tick = 0; tick < 4; tick++) assembler.tickingRequest(node, 1);
                assertAssemblerBalance(ready, assembler, firstOffer, nextOffer, output, 1);

                if (reloadRule) {
                    replaceAndNotify(ready, List.of(recipe, node(NODE_A), node(NODE_B), node(NODE_C), initialLock));
                } else {
                    validated(ready.engineA, true);
                }
                helper.assertTrue(RecipeLocks.allowedPattern(assembler, decoded)
                                && assembler.pushPattern(decoded, nextOffer, Direction.NORTH) && nextOffer[0].isEmpty(),
                        "Restoring live authorization accepts that same untouched next order and its same encoded pattern");
                for (int tick = 0; tick < 24 && count(output, ASSEMBLER_OUTPUT) < 2; tick++) assembler.tickingRequest(node, 1);
                for (int tick = 0; tick < 4; tick++) assembler.tickingRequest(node, 1);
                helper.assertTrue(assembler.getInternalInventory().isEmpty() && count(output, ASSEMBLER_OUTPUT) == 2,
                        "Two admitted real crafts settle exactly twice; no cached admission authorizes a third round");
                assertAssemblerBalance(ready, assembler, firstOffer, nextOffer, output, 2);
                helper.assertTrue(data.getUnlockedNodes(ready.teamA).equals(Set.of(NODE_A))
                                && data.getUnlockedNodes(ready.teamB).equals(Set.of(NODE_B))
                                && before.equals(data.save(new CompoundTag(), ready.level.registryAccess())) && !data.isDirty(),
                        "Admission, revocation, settlement and retry never persist borrowed research into either team's saved progress");
                helper.assertTrue(!RecipeExecutionContext.active(), "Native assembler push/ticking scopes never leak to other executors");
            });
        });
    }

    private static void tickFurnace(ServerRegressionFixture fixture, FurnaceBlockEntity furnace) {
        AbstractFurnaceBlockEntity.serverTick(fixture.level, furnace.getBlockPos(), furnace.getBlockState(), furnace);
    }

    private static KeyCounter[] offerOneAssemblerInput() {
        var counter = new KeyCounter();
        counter.add(AEItemKey.of(ASSEMBLER_INPUT), 1);
        return new KeyCounter[] { counter };
    }

    private static void assertAssemblerBalance(NetworkFixture fixture, MolecularAssemblerBlockEntity assembler,
                                                KeyCounter[] firstOffer, KeyCounter[] nextOffer,
                                                ChestBlockEntity output, int expectedProducts) {
        long remainingInputs = firstOffer[0].get(AEItemKey.of(ASSEMBLER_INPUT))
                + nextOffer[0].get(AEItemKey.of(ASSEMBLER_INPUT))
                + countInternal(assembler.getInternalInventory(), ASSEMBLER_INPUT)
                + (output == null ? 0 : count(output, ASSEMBLER_INPUT));
        int products = countInternal(assembler.getInternalInventory(), ASSEMBLER_OUTPUT)
                + (output == null ? 0 : count(output, ASSEMBLER_OUTPUT));
        fixture.helper.assertTrue(remainingInputs == 2 - expectedProducts && products == expectedProducts,
                "Two offered items must remain exactly conserved across KeyCounters, the native assembler grid/buffer and the real chest; inputs="
                        + remainingInputs + ", products=" + products + ", expected products=" + expectedProducts);
        assertNoDrops(fixture, assembler.getBlockPos(), ASSEMBLER_INPUT, ASSEMBLER_OUTPUT);
    }

    private static int countInternal(InternalInventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void assertNoDrops(ServerRegressionFixture fixture, BlockPos pos, Item input, Item output) {
        fixture.helper.assertTrue(fixture.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4),
                        entity -> entity.getItem().is(input) || entity.getItem().is(output)).isEmpty(),
                "Rejected or settled native operations must not leak ingredients/products into world item entities");
    }
}
