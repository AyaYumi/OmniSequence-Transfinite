package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.api.crafting.*;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.ForgeRecipeCodecs;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipeInput;
import com.atir.molecularmanipulator.menu.MatterPatternBufferMenuState;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.verification.VerificationAEKey;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterAEKeyGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1200, 80, 160);
    private static final AEKey HOT = new VerificationAEKey("gas", "hot"), COLD = new VerificationAEKey("gas", "cold");

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void genericInputsSurviveAssemblyLifecycle(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 72; x <= 78; x++) for (int z = 6; z <= 16; z++) {
            level.setChunkForced(x, z, true); level.getChunk(x, z);
        }
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(ORIGIN, Direction.NORTH, part);
            if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity old) old.clearContent();
            if (!MatterFabricationStructure.isController(part)) level.setBlock(pos, MatterFabricationStructure.partState(part.type()), 3);
        }
        // Recreate service nodes when reusing the isolated world from a previous test run.
        level.setBlock(ORIGIN, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(ORIGIN, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(ORIGIN);
        var bay = MatterFabricationStructure.worldPos(ORIGIN, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().get(0));
        level.setBlock(bay, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = ORIGIN.offset(30, 0, 0);
        level.setBlock(powerPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                controller.getMainNode().getNode() != null && assembly.getMainNode().getNode() != null
                        && power.getMainNode().getNode() != null, "Wait for native AE2 node initialization"))
                .thenExecute(() -> {
            controller.refreshStructure(); assembly.setControllerPos(ORIGIN);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        }).thenWaitUntil(() -> helper.assertTrue(controller.isStructureFormed() && controller.getMainNode().isActive() && assembly.isOperational(),
                "Wait for the real well and AE grid to become active")).thenExecute(() -> {
            verifyLifecycle(helper, controller, assembly);
            verifyOutputIsolation(helper, controller, assembly);
            System.out.println("MATTER_AEKEY_PASS: custom key registration, recipe and menu codecs, exact matching, long batching, active/queued/refund reload and ME return");
        }).thenSucceed();
    }

    private static void verifyLifecycle(GameTestHelper helper, MatterFabricationBlockEntity controller,
            MatterFabricationPatternAssemblyBlockEntity assembly) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var holder = level.getRecipeManager().getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get()).stream()
                .filter(recipe -> recipe.id().equals(new ResourceLocation("molecularmanipulator:verification_ae_inputs"))).findFirst().orElseThrow();
        var recipe = holder.value();
        helper.assertTrue(recipe.aeInputs().get(0).what().equals(HOT), "Data-pack codec must resolve the registered third-party key");
        var codec = new MatterFabricationRecipe.Serializer();
        var ops = JsonOps.INSTANCE;
        var typedNbt = new CompoundTag(); typedNbt.putInt("quality", 1); typedNbt.putLong("serial", Long.MAX_VALUE);
        typedNbt.putByteArray("bytes", new byte[]{1, 2}); typedNbt.putIntArray("ints", new int[]{1, 2});
        var exactKeys = List.of(new GenericStack(AEItemKey.of(Items.DIAMOND, typedNbt), 3),
                new GenericStack(AEFluidKey.of(Fluids.WATER, typedNbt), 500));
        for (var exact : exactKeys) {
            var encodedKey = ForgeRecipeCodecs.GENERIC_STACK.encodeStart(ops, exact).getOrThrow(false, message -> {});
            helper.assertTrue(exact.equals(ForgeRecipeCodecs.GENERIC_STACK.parse(ops, encodedKey).getOrThrow(false, message -> {})),
                    "Generic JSON must preserve exact numeric NBT types and arrays");
        }
        var json = MatterFabricationRecipe.Serializer.CODEC.codec().encodeStart(ops, recipe).getOrThrow(false, message -> {});
        helper.assertTrue(MatterFabricationRecipe.Serializer.CODEC.codec().parse(ops, json).getOrThrow(false, message -> {}).aeInputs().equals(recipe.aeInputs()), "JSON must round-trip generic resources");
        var quotedAmount = json.deepCopy().getAsJsonObject();
        quotedAmount.getAsJsonArray("ae_inputs").get(0).getAsJsonObject().addProperty("#", "3000000000");
        helper.assertTrue(MatterFabricationRecipe.Serializer.CODEC.codec().parse(ops, quotedAmount).result().isEmpty(), "AE input amounts use numeric JSON, unlike the research decimal-string codec");
        for (Number invalidAmount : List.<Number>of(0, -1, 1.5, new java.math.BigInteger("9223372036854775808"))) {
            var invalid = json.deepCopy().getAsJsonObject();
            invalid.getAsJsonArray("ae_inputs").get(0).getAsJsonObject().addProperty("#", invalidAmount);
            helper.assertTrue(MatterFabricationRecipe.Serializer.CODEC.codec().parse(ops, invalid).result().isEmpty(),
                    "Malformed generic costs must reject the recipe instead of dropping its AE inputs");
        }
        var unknownType = json.deepCopy().getAsJsonObject();
        unknownType.getAsJsonArray("ae_inputs").get(0).getAsJsonObject().addProperty("#t", "molecularmanipulator:missing_key_type");
        helper.assertTrue(MatterFabricationRecipe.Serializer.CODEC.codec().parse(ops, unknownType).result().isEmpty(), "Missing key types must not become free inputs");
        var longAmount = json.deepCopy().getAsJsonObject();
        longAmount.getAsJsonArray("ae_inputs").get(0).getAsJsonObject().addProperty("#", Long.MAX_VALUE);
        helper.assertTrue(MatterFabricationRecipe.Serializer.CODEC.codec().parse(ops, longAmount).getOrThrow(false, message -> {}).aeInputs().get(0).amount() == Long.MAX_VALUE,
                "A JSON integer literal must preserve the full long range");
        var packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.toNetwork(packet, recipe);
            helper.assertTrue(codec.fromNetwork(holder.id(), packet).aeInputs().equals(recipe.aeInputs()), "Recipe sync must preserve long amounts and variants");
            packet.clear();
            var page = new MatterPatternBufferMenuState(0, 1, 0, 0, 1, false, false, recipe.aeInputs());
            page.writeToPacket(packet);
            helper.assertTrue(new MatterPatternBufferMenuState(packet).contents().equals(page.contents()), "Cache menu packets must retain third-party keys");
            packet.clear();
            var nbtRecipe = new MatterFabricationRecipe(List.of(), List.of(new ItemStack(Items.STONE)), FluidStack.EMPTY, FluidStack.EMPTY,
                    exactKeys, 2, 0, false).withId(new ResourceLocation("molecularmanipulator:verification_nbt"));
            codec.toNetwork(packet, nbtRecipe);
            helper.assertTrue(codec.fromNetwork(nbtRecipe.id(), packet).aeInputs().equals(exactKeys), "Recipe network sync must retain exact NBT keys");
        } finally { packet.release(); }
        for (var input : recipe.aeInputs()) helper.assertTrue(input.equals(GenericStack.unwrapItemStack(GenericStack.wrapInItemStack(input))),
                "JEI/guide display wrappers must retain exact inputs");
        var unit = new LinkedHashMap<AEKey, Long>();
        unit.put(AEItemKey.of(Items.DIAMOND), 2L); unit.put(HOT, 3_000_000_000L); unit.put(COLD, 7L);
        unit.put(AEFluidKey.of(Fluids.LAVA), 100L); unit.put(AEFluidKey.of(Fluids.WATER), 250L);
        var encoded = encodePattern(stacks(unit), List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 1)));
        assembly.getLogic().getPatternInv().setItemDirect(0, encoded);
        assembly.getLogic().updatePatterns();
        var pattern = assembly.getLogic().getAvailablePatterns().get(0);
        helper.assertTrue(MatterFabricationBatch.match(controller, pattern, unit, 1) != null, "Items, multiple fluids and multiple third-party variants must match together");
        helper.assertTrue(recipe.consumptionPlan(new MatterFabricationRecipeInput(List.of(new ItemStack(Items.DIAMOND, 2)), new FluidStack(Fluids.WATER, 250))) == null,
                "Manual ports must never craft without the generic inputs");
        var wrong = new LinkedHashMap<>(unit); wrong.remove(COLD); wrong.put(new VerificationAEKey("gas", "wrong"), 7L);
        var wrongCounters = counters(wrong);
        helper.assertTrue(!assembly.getLogic().pushPattern(pattern, wrongCounters) && !wrongCounters[0].isEmpty(), "Rejected variants must leave AE's delivery untouched");
        wrong = new LinkedHashMap<>(unit); wrong.put(HOT, unit.get(HOT) + 1);
        helper.assertTrue(MatterFabricationBatch.match(controller, pattern, wrong, 1) == null, "Excess inputs must be rejected");
        wrong = new LinkedHashMap<>(unit); wrong.put(HOT, unit.get(HOT) - 1);
        helper.assertTrue(MatterFabricationBatch.match(controller, pattern, wrong, 1) == null, "Missing inputs must be rejected");
        verifyOverlappingInputs(helper);

        var buffer = assembly.getBuffer();
        var ordinary = counters(unit);
        helper.assertTrue(assembly.getLogic().pushPattern(pattern, ordinary) && ordinary[0].isEmpty(), "Ordinary AE delivery must commit all resource types");
        var saved = new CompoundTag(); buffer.save(saved); buffer.load(saved);
        helper.assertTrue(!buffer.isUnavailable() && amounts(buffer.contents(false)).equals(unit), "Queued materials must survive NBT reload");
        buffer.process(controller);
        helper.assertTrue(buffer.isProcessing(), "Two-tick recipe must retain an active batch after its first tick");
        saved = new CompoundTag(); buffer.save(saved); buffer.load(saved);
        helper.assertTrue(buffer.isProcessing() && amounts(buffer.contents(false)).equals(unit), "Active generic materials must survive reload");
        var completion = buffer.process(controller);
        helper.assertTrue(buffer.contents(false).isEmpty() && amounts(buffer.contents(true)).equals(Map.of(AEItemKey.of(Items.EMERALD), 1L)),
                "Completed work must consume the generic resources exactly once and retain blocked output: "
                        + completion + " inputs=" + buffer.contents(false) + " outputs=" + buffer.contents(true));
        buffer.clear();

        long crafts = 3_000_000_000L;
        var probe = new OmniBatchProbe(pattern, unit.entrySet().stream().map(entry -> new OmniBatchProbe.Input(0, entry.getKey(), entry.getValue())).toList(), Long.MAX_VALUE);
        var admission = assembly.getLogic().prepareOmniBatch(probe);
        helper.assertTrue(admission != null && admission.maxCrafts() == Long.MAX_VALUE / unit.get(HOT), "Generic costs must bound the long batch without overflow");
        var supplied = MatterPatternBuffer.scaled(unit, crafts);
        var request = new OmniBatchRequest(UUID.randomUUID(), null, pattern, crafts,
                supplied.entrySet().stream().map(entry -> new OmniBatchRequest.Input(0, entry.getKey(), entry.getValue())).toList(),
                List.of(new GenericStack(AEItemKey.of(Items.EMERALD), crafts)));
        var accepted = new boolean[1];
        admission.commit(new OmniBatchDelivery() {
            @Override public OmniBatchRequest request() { return request; }
            @Override public void accept(Receipt receipt) { accepted[0] = receipt.ownership() == Ownership.PERSISTED_PROVIDER_QUEUE; }
            @Override public void reject(Rejection rejection) { throw new AssertionError("Unexpected batch rejection: " + rejection); }
        });
        helper.assertTrue(accepted[0] && amounts(buffer.contents(false)).equals(supplied), "More than Integer.MAX_VALUE crafts must remain long-valued");
        var part = MatterFabricationBatch.takeInputs(recipe, supplied, crafts, 2);
        helper.assertTrue(part.equals(MatterPatternBuffer.scaled(unit, 2)), "Batch splitting must retain every generic input");
        buffer.refundQueuedInputs();
        saved = new CompoundTag(); buffer.save(saved); buffer.load(saved);
        helper.assertTrue(!buffer.isUnavailable() && amounts(buffer.contents(false)).equals(supplied) && buffer.queuedPatterns() == 0,
                "Blocked refunds must survive reload without losing long amounts");
        var returned = new LinkedHashMap<AEKey, Long>();
        MEStorage storage = new MEStorage() {
            @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) returned.merge(what, amount, Math::addExact);
                return amount;
            }
            @Override public Component getDescription() { return Component.literal("Verification storage"); }
        };
        var service = assembly.getMainNode().getGrid().getStorageService();
        IStorageProvider provider = mounts -> mounts.mount(storage, 0);
        service.addGlobalStorageProvider(provider);
        try {
            buffer.serverTick(); buffer.serverTick();
            helper.assertTrue(returned.equals(supplied) && !buffer.hasContents(), "Every refund must reach ME storage exactly once");
        } finally { service.removeGlobalStorageProvider(provider); }
    }

    private static void verifyOverlappingInputs(GameTestHelper helper) {
        var diamond = AEItemKey.of(Items.DIAMOND); var emerald = AEItemKey.of(Items.EMERALD);
        var recipe = new MatterFabricationRecipe(List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND, Items.EMERALD), 1)),
                List.of(new ItemStack(Items.STONE)), FluidStack.EMPTY, FluidStack.EMPTY,
                List.of(new GenericStack(diamond, 1)), 2, 0, false);
        var supplied = new LinkedHashMap<AEKey, Long>(); supplied.put(diamond, 2L); supplied.put(emerald, 2L);
        var part = MatterFabricationBatch.takeInputs(recipe, supplied, 2, 1);
        helper.assertTrue(part.equals(Map.of(diamond, 1L, emerald, 1L)), "Exact item keys and flexible ingredients must share allocation without stranding the remainder");
        var legacy = new MatterFabricationRecipe(recipe.ingredients(), recipe.results(), FluidStack.EMPTY, FluidStack.EMPTY, 2, 0);
        helper.assertTrue(legacy.aeInputs().isEmpty() && legacy.consumptionPlan(new MatterFabricationRecipeInput(List.of(new ItemStack(Items.EMERALD)))) != null,
                "Existing recipe constructors and manual item recipes must remain compatible");
    }

    private static void verifyOutputIsolation(GameTestHelper helper, MatterFabricationBlockEntity controller,
            MatterFabricationPatternAssemblyBlockEntity assembly) {
        var manager = helper.getLevel().getRecipeManager(); var original = List.copyOf(manager.getRecipes());
        var a = AEItemKey.of(Items.DIAMOND); var b = AEItemKey.of(Items.EMERALD);
        var c = AEItemKey.of(Items.STONE); var d = AEItemKey.of(Items.COBBLESTONE);
        var buffer = assembly.getBuffer();
        try {
            for (int ratio = 1; ratio <= 2; ratio++) {
                var recipeC = new MatterFabricationRecipe(List.of(
                        new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 10),
                        new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.EMERALD), 10)),
                        List.of(new ItemStack(Items.STONE)), FluidStack.EMPTY, FluidStack.EMPTY, 1, 0).withId(new ResourceLocation("molecularmanipulator:verification_c"));
                var recipeD = new MatterFabricationRecipe(List.of(
                        new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 10 * ratio),
                        new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.EMERALD), 10 * ratio)),
                        List.of(new ItemStack(Items.COBBLESTONE)), FluidStack.EMPTY, FluidStack.EMPTY, 1, 0).withId(new ResourceLocation("molecularmanipulator:verification_d"));
                manager.replaceRecipes(List.of(recipeC, recipeD));
                Map<AEKey, Long> unitC = Map.of(a, 10L, b, 10L), unitD = Map.of(a, 10L * ratio, b, 10L * ratio);
                var encodedC = encodePattern(stacks(unitC), List.of(new GenericStack(c, 1)));
                var encodedD = encodePattern(stacks(unitD), List.of(new GenericStack(d, 1)));
                assembly.getLogic().getPatternInv().setItemDirect(0, encodedC); assembly.getLogic().getPatternInv().setItemDirect(1, encodedD);
                assembly.getLogic().updatePatterns();
                var patternC = PatternDetailsHelper.decodePattern(encodedC, helper.getLevel());
                var patternD = PatternDetailsHelper.decodePattern(encodedD, helper.getLevel());
                helper.assertTrue(assembly.getLogic().pushPattern(patternC, counters(unitC))
                        && assembly.getLogic().pushPattern(patternD, counters(unitD))
                        && assembly.getLogic().pushPattern(patternC, counters(unitC)), "Interleaved C/D patterns must accept their own complete inputs");
                helper.assertTrue(buffer.queuedPatterns() == 2, "Different outputs must retain separate queues");
                var saved = new CompoundTag(); buffer.save(saved); buffer.load(saved);
                for (int tick = 0; tick < 6; tick++) buffer.process(controller);
                helper.assertTrue(buffer.contents(false).isEmpty() && amounts(buffer.contents(true)).equals(Map.of(c, 2L, d, 1L)),
                        "Equal or proportional inputs must produce 2C+1D after save/reload");
                buffer.clear();
                var admission = assembly.getLogic().prepareOmniBatch(new OmniBatchProbe(patternC,
                        List.of(new OmniBatchProbe.Input(0, a, 10), new OmniBatchProbe.Input(1, b, 10)), 2));
                helper.assertTrue(admission != null && admission.maxCrafts() == 2, "C batch must be admitted");
                var request = new OmniBatchRequest(UUID.randomUUID(), null, patternC, 2,
                        List.of(new OmniBatchRequest.Input(0, a, 20), new OmniBatchRequest.Input(1, b, 20)), List.of(new GenericStack(c, 2)));
                var accepted = new boolean[1];
                admission.commit(new OmniBatchDelivery() {
                    @Override public OmniBatchRequest request() { return request; }
                    @Override public void accept(Receipt receipt) { accepted[0] = true; }
                    @Override public void reject(Rejection rejection) { throw new AssertionError("Unexpected rejection: " + rejection); }
                });
                helper.assertTrue(accepted[0], "C batch must commit");
                for (int tick = 0; tick < 4; tick++) buffer.process(controller);
                helper.assertTrue(buffer.contents(false).isEmpty() && amounts(buffer.contents(true)).equals(Map.of(c, 2L)), "20A+20B in a C batch must produce 2C");
                buffer.clear();
            }
            System.out.println("MATTER_OUTPUT_ISOLATION_PASS: identical/proportional inputs, separate queues, NBT reload and API batches");
        } finally { buffer.clear(); manager.replaceRecipes(original); }
    }

    private static KeyCounter[] counters(Map<AEKey, Long> amounts) {
        var counter = new KeyCounter(); amounts.forEach(counter::add); return new KeyCounter[]{counter};
    }
    private static List<GenericStack> stacks(Map<AEKey, Long> amounts) {
        return amounts.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
    }
    private static Map<AEKey, Long> amounts(List<GenericStack> stacks) {
        var amounts = new LinkedHashMap<AEKey, Long>(); stacks.forEach(stack -> amounts.merge(stack.what(), stack.amount(), Math::addExact)); return amounts;
    }
    private static ItemStack encodePattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        return PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new));
    }
}
