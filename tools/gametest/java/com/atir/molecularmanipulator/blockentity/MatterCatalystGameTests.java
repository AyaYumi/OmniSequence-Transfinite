package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.*;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.SlotSemantics;
import appeng.menu.slot.AppEngSlot;
import com.atir.molecularmanipulator.crafting.*;
import com.atir.molecularmanipulator.menu.MatterFabricationPatternAssemblyMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.*;
import com.atir.molecularmanipulator.verification.VerificationAEKey;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterCatalystGameTests {
    private static final ResourceLocation RECIPE = id("shared_catalyst"), SECOND = id("second_catalyst"), RESEARCH = id("catalyst_research");
    private static final AEKey LIGHTNING = new VerificationAEKey("lightning", "high");
    private static final Map<AEKey, Long> UNIT = Map.of(AEFluidKey.of(Fluids.WATER), 1000L, LIGHTNING, 1L);

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void sharedCatalystsAndDurableBatches(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = new BlockPos(1200, 80, 160);
        var otherOrigin = origin.offset(80, 0, 0);
        var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
        for (var center : List.of(origin, otherOrigin)) for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(center, Direction.NORTH, part);
            var chunk = new net.minecraft.world.level.ChunkPos(pos);
            if (chunks.add(chunk)) { level.setChunkForced(chunk.x, chunk.z, true); level.getChunkAt(pos); }
            if (level.getBlockEntity(pos) instanceof appeng.blockentity.AEBaseBlockEntity old) old.clearContent();
            level.setBlock(pos, MatterFabricationStructure.isController(part)
                    ? ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    : MatterFabricationStructure.partState(part.type()), 3);
        }
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        var other = (MatterFabricationBlockEntity) level.getBlockEntity(otherOrigin);
        var bays = MatterFabricationStructure.patternAssemblyBays();
        var aPos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, bays.get(0));
        var bPos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, bays.get(1));
        for (var pos : List.of(aPos, bPos)) level.setBlock(pos, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var a = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(aPos);
        var b = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bPos);
        var powerPos = origin.offset(30, 0, 0);
        var powerChunk = new net.minecraft.world.level.ChunkPos(powerPos);
        chunks.add(powerChunk); level.setChunkForced(powerChunk.x, powerChunk.z, true); level.getChunkAt(powerPos);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        helper.runAfterDelay(5, () -> {
            controller.refreshStructure(); other.refreshStructure();
            a.setControllerPos(origin); b.setControllerPos(origin);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(a.isOperational() && b.isOperational() && other.isStructureFormed(), "Both wells and sharing assemblies must form");
            verifyRealLightningImport(helper);
            var originals = List.copyOf(level.getRecipeManager().getRecipes());
            try {
                var recipe = recipe(Items.EMERALD, List.of(counted(Items.DIAMOND_BLOCK, 1)));
                var secondary = recipe(Items.AMETHYST_SHARD, recipe.catalysts());
                var recipes = new ArrayList<RecipeHolder<?>>(originals);
                recipes.add(new RecipeHolder<>(RECIPE, recipe)); recipes.add(new RecipeHolder<>(SECOND, secondary));
                recipes.add(new RecipeHolder<>(id("alternate_catalyst"), recipe(Items.EMERALD, List.of(counted(Items.EMERALD_BLOCK, 1)))));
                recipes.add(new RecipeHolder<>(RESEARCH, new MatterResearchRecipe("Catalyst verification", List.of(), List.of(), 1, 0,
                        List.of(RECIPE, SECOND), List.of(), 0, 1, List.of(new ResearchDepth(1, 2048, 1, 0, Optional.empty())))));
                level.getRecipeManager().replaceRecipes(recipes);
                MatterResearchApi.setCompletionCount(controller, RESEARCH.toString(), 1);
                verifyCodec(helper, recipe);
                var encoded = MatterFabricationPatternEncoding.encode(recipe, List.of());
                a.getLogic().getPatternInv().setItemDirect(0, encoded);
                a.getLogic().updatePatterns();
                b.getLogic().getPatternInv().setItemDirect(0, MatterFabricationPatternEncoding.encode(secondary, List.of()));
                b.getLogic().updatePatterns();
                var pattern = a.getLogic().getAvailablePatterns().getFirst();
                helper.assertTrue(inputs(pattern).equals(UNIT), "Encoded patterns must contain only water and lightning, never the catalyst");
                var rejected = counters(UNIT);
                helper.assertTrue(!a.getLogic().pushPattern(pattern, rejected) && MatterFabricationPatternLogic.batchInputs(rejected).equals(UNIT),
                        "Missing catalyst must reject ordinary delivery without taking its materials");
                helper.assertTrue(a.getLogic().prepareCountedBatch(pattern, UNIT, 1026) == null, "Missing catalyst must reject native batches");
                helper.assertTrue(a.getLogic().hasMissingCatalysts(), "Missing catalysts must be visible before a CPU delivery is accepted");
                a.getCatalystInventory().setItemDirect(0, new ItemStack(Items.EMERALD_BLOCK));
                helper.assertTrue(a.getLogic().pushPattern(pattern, counters(UNIT)), "An available alternative catalyst branch must be usable");
                b.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK));
                helper.assertTrue(a.getBuffer().process(controller).progress() == 1, "A newly available earlier branch must not strand queued work");
                a.getCatalystInventory().clear();
                helper.assertTrue(a.getBuffer().process(controller).state() == MatterFabricationBlockEntity.ProcessingState.WAITING_CATALYST,
                        "Active work must keep the catalyst requirement of its admitted branch");
                a.getCatalystInventory().setItemDirect(0, new ItemStack(Items.EMERALD_BLOCK));
                for (int i = 0; i < 3; i++) a.getBuffer().process(controller);
                helper.assertTrue(MatterFabricationPatternLogic.batchInputs(a.getBuffer().contents(true)).equals(Map.of(AEItemKey.of(Items.EMERALD), 1L)),
                        "The admitted alternative branch must resume and complete once");
                a.getBuffer().clear(); a.getCatalystInventory().clear();
                b.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK));
                helper.assertTrue(a.getCatalystInventory().isEmpty() && controller.hasCatalysts(recipe), "A must use B's catalyst without copying it");
                helper.assertTrue(!a.getLogic().hasMissingCatalysts(), "A catalyst in another assembly must clear the missing-prerequisite status");
                helper.assertTrue(!other.hasCatalysts(recipe), "A different well must not share this catalyst");
                var overlap = recipe(Items.EMERALD, List.of(new MatterFabricationRecipe.CountedIngredient(
                        Ingredient.of(Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK), 1), counted(Items.DIAMOND_BLOCK, 1)));
                helper.assertTrue(!controller.hasCatalysts(overlap), "Overlapping requirements must not count one item twice");
                a.getCatalystInventory().setItemDirect(0, new ItemStack(Items.EMERALD_BLOCK));
                helper.assertTrue(controller.hasCatalysts(overlap), "Catalyst counts must aggregate across assemblies and satisfy overlapping ingredients");
                a.getCatalystInventory().clear();
                var stale = a.getLogic().prepareCountedBatch(pattern, UNIT, 1026);
                helper.assertTrue(stale != null && stale.maxCrafts() == 1026, "One catalyst must permit a complete native batch");
                b.getCatalystInventory().clear();
                helper.assertTrue(!stale.commitPrototype(counters(UNIT), 1026), "Removing a catalyst after probing must reject commit");
                b.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK));
                var admission = a.getLogic().prepareCountedBatch(pattern, UNIT, 1026);
                helper.assertTrue(admission.commitPrototype(counters(UNIT), 1026), "Native delivery must accept 1026 crafts with one shared catalyst");
                helper.assertTrue(MatterFabricationPatternLogic.batchInputs(a.getBuffer().contents(false)).equals(MatterPatternBuffer.scaled(UNIT, 1026)),
                        "Batch queue must own exactly the scaled consumables");
                b.getCatalystInventory().clear();
                var queuedPause = a.getBuffer().process(controller);
                helper.assertTrue(queuedPause.state() == MatterFabricationBlockEntity.ProcessingState.WAITING_CATALYST
                        && a.getBuffer().queuedPatterns() == 1, "Queued work must wait without taking another catalyst or discarding inputs");
                b.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK));
                var first = a.getBuffer().process(controller);
                helper.assertTrue(first.progress() == 1, "A batch must begin with the shared catalyst");
                var heldInputs = a.getBuffer().contents(false);
                b.getCatalystInventory().clear();
                var paused = a.getBuffer().process(controller);
                helper.assertTrue(paused.state() == MatterFabricationBlockEntity.ProcessingState.WAITING_CATALYST && paused.progress() == first.progress()
                                && a.getBuffer().contents(false).equals(heldInputs), "Removing the catalyst must preserve progress and all held inputs");
                var pausedSave = new CompoundTag();
                a.getBuffer().save(pausedSave, level.registryAccess()); a.getBuffer().load(pausedSave, level.registryAccess());
                helper.assertTrue(a.getBuffer().process(controller).progress() == first.progress() && a.getBuffer().waitingForCatalyst(),
                        "A saved active batch must retain its catalyst condition and paused progress");
                b.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK));
                verifyPersistence(helper, b);
                for (int i = 0; i < 3; i++) a.getBuffer().process(controller);
                helper.assertTrue(MatterFabricationPatternLogic.batchInputs(a.getBuffer().contents(true)).equals(Map.of(AEItemKey.of(Items.EMERALD), 1026L)),
                        "Resuming must produce exactly the requested outputs");
                helper.assertTrue(b.getCatalystInventory().getStackInSlot(0).getCount() == 1 && a.getBuffer().contents(false).isEmpty(),
                        "1026 crafts must consume every input and leave exactly the original catalyst");
                a.getBuffer().clear();
                var ordinary = counters(UNIT);
                helper.assertTrue(b.getLogic().pushPattern(b.getLogic().getAvailablePatterns().getFirst(), ordinary), "Another pattern must reuse the same catalyst");
                for (int i = 0; i < 4; i++) b.getBuffer().process(controller);
                helper.assertTrue(MatterFabricationPatternLogic.batchInputs(b.getBuffer().contents(true)).equals(Map.of(AEItemKey.of(Items.AMETHYST_SHARD), 1L)),
                        "Second assembly must produce its own output with the same retained catalyst");
                b.getBuffer().clear();
                verifyMenu(helper, b);
                b.setControllerPos(null);
                helper.assertTrue(!controller.hasCatalysts(recipe), "An unlinked assembly must immediately leave the shared inventory");
                b.setControllerPos(origin);
                helper.assertTrue(controller.hasCatalysts(recipe), "Relinking must restore the shared catalyst immediately");
                b.setRemoved();
                helper.assertTrue(!controller.hasCatalysts(recipe), "Removing the owning assembly must invalidate the shared cache immediately");
                b.clearRemoved();
                controller.invalidateCatalysts();
                System.out.println("MATTER_CATALYST_PASS: shared ownership, 1026 crafts, pause/resume, codecs, menus and portable contents");
                helper.succeed();
            } finally {
                a.clearContent(); b.clearContent();
                level.getRecipeManager().replaceRecipes(originals);
                for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }

    private static void verifyMenu(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CatalystTest"));
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        MatterFabricationPatternAssemblyMenu menu;
        try {
            var constructor = MatterFabricationPatternAssemblyMenu.class.getDeclaredConstructor(int.class, net.minecraft.world.entity.player.Inventory.class,
                    MatterFabricationPatternAssemblyBlockEntity.class);
            constructor.setAccessible(true);
            menu = constructor.newInstance(71, player.getInventory(), assembly);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        player.containerMenu = menu;
        helper.assertTrue(menu.getSlots(SlotSemantics.MACHINE_INPUT).size() == 36
                && menu.getSlots(SlotSemantics.MACHINE_INPUT).stream().noneMatch(slot -> ((AppEngSlot) slot).isActive()), "Catalyst slots must be hidden on the pattern page");
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_BLOCK));
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().index);
        helper.assertTrue(assembly.getCatalystInventory().getStackInSlot(0).getCount() == 1,
                "Shift-click on the pattern page must not insert into hidden catalyst slots");
        menu.receiveClientAction("buffer_view", "3"); menu.broadcastChanges();
        helper.assertTrue(menu.view == 3 && menu.sharedCatalystTypes == 1
                && menu.getSlots(SlotSemantics.MACHINE_INPUT).stream().allMatch(slot -> ((AppEngSlot) slot).isActive())
                && menu.getSlots(SlotSemantics.ENCODED_PATTERN).stream().noneMatch(slot -> ((AppEngSlot) slot).isActive()), "The catalyst page must activate only its own slots");
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_BLOCK));
        menu.quickMoveStack(player, menu.getSlots(SlotSemantics.PLAYER_HOTBAR).getFirst().index);
        helper.assertTrue(assembly.getCatalystInventory().getStackInSlot(0).getCount() == 2,
                "Shift-click on the catalyst page must insert a real retained catalyst");
        assembly.getCatalystInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_BLOCK));
        menu.receiveClientAction("buffer_view", "0");
        helper.assertTrue(menu.getSlots(SlotSemantics.MACHINE_INPUT).stream().noneMatch(slot -> ((AppEngSlot) slot).isActive()), "Switching back must hide catalyst slots on the server");
        player.containerMenu = player.inventoryMenu;
    }

    private static void verifyPersistence(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly) {
        var registries = helper.getLevel().registryAccess();
        var saved = assembly.saveWithFullMetadata(registries);
        var restored = (MatterFabricationPatternAssemblyBlockEntity) BlockEntity.loadStatic(assembly.getBlockPos(), assembly.getBlockState(), saved, registries);
        helper.assertTrue(restored.getCatalystInventory().getStackInSlot(0).is(Items.DIAMOND_BLOCK), "Catalysts must survive normal saves");
        var drops = new ArrayList<ItemStack>(); assembly.addAdditionalDrops(helper.getLevel(), assembly.getBlockPos(), drops);
        helper.assertTrue(drops.size() == 1, "Assembly must pack its catalysts once");
        var relocated = new MatterFabricationPatternAssemblyBlockEntity(assembly.getBlockPos().above(), assembly.getBlockState());
        relocated.loadTag(drops.getFirst().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA).copyTag(), registries);
        helper.assertTrue(relocated.getCatalystInventory().getStackInSlot(0).getCount() == 1
                && relocated.getController() == null, "Portable drops must retain the catalyst without the old controller link");
    }

    private static void verifyCodec(GameTestHelper helper, MatterFabricationRecipe recipe) {
        var serializer = new MatterFabricationRecipe.Serializer();
        var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json = serializer.codec().codec().encodeStart(ops, recipe).getOrThrow();
        var decoded = serializer.codec().codec().parse(ops, json).getOrThrow();
        helper.assertTrue(decoded.ingredients().isEmpty() && decoded.catalysts().size() == 1
                && decoded.catalysts().getFirst().ingredient().test(new ItemStack(Items.DIAMOND_BLOCK)), "Recipe JSON must retain separate catalyst requirements");
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            serializer.streamCodec().encode(packet, recipe);
            var synced = serializer.streamCodec().decode(packet);
            helper.assertTrue(synced.catalysts().size() == 1 && synced.catalysts().getFirst().count() == 1
                    && synced.aeInputs().equals(recipe.aeInputs()) && packet.readableBytes() == 0, "Recipe sync must preserve catalysts and resource amounts");
        } finally { packet.release(); }
    }

    private static void verifyRealLightningImport(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2lt")) return;
        var recipes = MatterRecipeIndex.get(helper.getLevel()).fabrication().stream().map(RecipeHolder::value).filter(recipe ->
                recipe.results().stream().anyMatch(output -> BuiltInRegistries.ITEM.getKey(output.getItem()).toString().equals("ae2lt:overload_crystal"))
                        && !recipe.catalysts().isEmpty()).toList();
        helper.assertTrue(!recipes.isEmpty(), "The real Lightning Tech catalyzer must import with a catalyst");
        for (var recipe : recipes) {
            helper.assertTrue(recipe.ingredients().isEmpty() && recipe.catalysts().getFirst().count() == 1
                    && recipe.fluidInput().getFluid() == Fluids.WATER && recipe.fluidInput().getAmount() == 1000
                    && recipe.aeInputs().stream().anyMatch(input -> input.what().getType().getId().toString().equals("ae2lt:lightning") && input.amount() == 1),
                    "Real catalyzer import must retain its block, consume fixed water and the recipe's lightning cost");
        }
        System.out.println("MATTER_CATALYST_LIGHTNING_IMPORT_PASS");
    }

    private static MatterFabricationRecipe recipe(Item output, List<MatterFabricationRecipe.CountedIngredient> catalysts) {
        return new MatterFabricationRecipe(List.of(), catalysts, List.of(new ItemStack(output)), new FluidStack(Fluids.WATER, 1000),
                FluidStack.EMPTY, List.of(new GenericStack(LIGHTNING, 1)), List.of(), 4, 0, false);
    }
    private static MatterFabricationRecipe.CountedIngredient counted(Item item, int count) { return new MatterFabricationRecipe.CountedIngredient(Ingredient.of(item), count); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "verification/" + path); }
    private static KeyCounter[] counters(Map<AEKey, Long> values) { var counter = new KeyCounter(); values.forEach(counter::add); return new KeyCounter[]{counter}; }
    private static Map<AEKey, Long> inputs(appeng.api.crafting.IPatternDetails pattern) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var input : pattern.getInputs()) for (var stack : input.getPossibleInputs()) result.merge(stack.what(), stack.amount() * input.getMultiplier(), Math::addExact);
        return result;
    }
}
