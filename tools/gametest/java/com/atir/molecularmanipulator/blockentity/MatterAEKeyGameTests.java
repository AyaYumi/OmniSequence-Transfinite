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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
        level.setBlock(ORIGIN, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(ORIGIN);
        var bay = MatterFabricationStructure.worldPos(ORIGIN, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().getFirst());
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = ORIGIN.offset(30, 0, 0);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        helper.runAfterDelay(5, () -> {
            controller.refreshStructure(); assembly.setControllerPos(ORIGIN);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(controller.isStructureFormed() && assembly.isOperational(), "Real well and AE grid must be active");
            verifyLifecycle(helper, controller, assembly);
            System.out.println("MATTER_AEKEY_PASS: custom key registration, recipe and menu codecs, exact matching, long batching, active/queued/refund reload and ME return");
            helper.succeed();
        });
    }

    private static void verifyLifecycle(GameTestHelper helper, MatterFabricationBlockEntity controller,
            MatterFabricationPatternAssemblyBlockEntity assembly) {
        var level = helper.getLevel();
        var registries = level.registryAccess();
        var holder = level.getRecipeManager().getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get()).stream()
                .filter(recipe -> recipe.id().equals(ResourceLocation.parse("molecularmanipulator:verification_ae_inputs"))).findFirst().orElseThrow();
        var recipe = holder.value();
        helper.assertTrue(recipe.aeInputs().getFirst().what().equals(HOT), "Data-pack codec must resolve the registered third-party key");
        var codec = new MatterFabricationRecipe.Serializer();
        var ops = registries.createSerializationContext(JsonOps.INSTANCE);
        var json = codec.codec().codec().encodeStart(ops, recipe).getOrThrow();
        helper.assertTrue(codec.codec().codec().parse(ops, json).getOrThrow().aeInputs().equals(recipe.aeInputs()), "JSON must round-trip generic resources");
        var quotedAmount = json.deepCopy().getAsJsonObject();
        quotedAmount.getAsJsonArray("ae_inputs").get(0).getAsJsonObject().addProperty("#", "3000000000");
        helper.assertTrue(codec.codec().codec().parse(ops, quotedAmount).result().isEmpty(), "AE input amounts use numeric JSON, unlike the research decimal-string codec");
        var longAmount = json.deepCopy().getAsJsonObject();
        longAmount.getAsJsonArray("ae_inputs").get(0).getAsJsonObject().addProperty("#", Long.MAX_VALUE);
        helper.assertTrue(codec.codec().codec().parse(ops, longAmount).getOrThrow().aeInputs().getFirst().amount() == Long.MAX_VALUE,
                "A JSON integer literal must preserve the full long range");
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            codec.streamCodec().encode(packet, recipe);
            helper.assertTrue(codec.streamCodec().decode(packet).aeInputs().equals(recipe.aeInputs()), "Recipe sync must preserve long amounts and variants");
            packet.clear();
            var page = new MatterPatternBufferMenuState(0, 1, 0, 0, 1, false, false, recipe.aeInputs());
            page.writeToPacket(packet);
            helper.assertTrue(new MatterPatternBufferMenuState(packet).contents().equals(page.contents()), "Cache menu packets must retain third-party keys");
        } finally { packet.release(); }
        for (var input : recipe.aeInputs()) helper.assertTrue(input.equals(GenericStack.unwrapItemStack(GenericStack.wrapInItemStack(input))),
                "JEI/guide display wrappers must retain exact inputs");
        var unit = new LinkedHashMap<AEKey, Long>();
        unit.put(AEItemKey.of(Items.DIAMOND), 2L); unit.put(HOT, 3_000_000_000L); unit.put(COLD, 7L);
        unit.put(AEFluidKey.of(Fluids.LAVA), 100L); unit.put(AEFluidKey.of(Fluids.WATER), 250L);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(stacks(unit), List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 1)));
        assembly.getLogic().getPatternInv().setItemDirect(0, encoded);
        assembly.getLogic().updatePatterns();
        var pattern = assembly.getLogic().getAvailablePatterns().getFirst();
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
        var saved = new CompoundTag(); buffer.save(saved, registries); buffer.load(saved, registries);
        helper.assertTrue(!buffer.isUnavailable() && amounts(buffer.contents(false)).equals(unit), "Queued materials must survive NBT reload");
        buffer.process(controller);
        helper.assertTrue(buffer.isProcessing(), "Two-tick recipe must retain an active batch after its first tick");
        saved = new CompoundTag(); buffer.save(saved, registries); buffer.load(saved, registries);
        helper.assertTrue(buffer.isProcessing() && amounts(buffer.contents(false)).equals(unit), "Active generic materials must survive reload");
        buffer.process(controller);
        helper.assertTrue(buffer.contents(false).isEmpty() && amounts(buffer.contents(true)).equals(Map.of(AEItemKey.of(Items.EMERALD), 1L)),
                "Completed work must consume the generic resources exactly once and retain blocked output");
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
        saved = new CompoundTag(); buffer.save(saved, registries); buffer.load(saved, registries);
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

    private static KeyCounter[] counters(Map<AEKey, Long> amounts) {
        var counter = new KeyCounter(); amounts.forEach(counter::add); return new KeyCounter[]{counter};
    }
    private static List<GenericStack> stacks(Map<AEKey, Long> amounts) {
        return amounts.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
    }
    private static Map<AEKey, Long> amounts(List<GenericStack> stacks) {
        var amounts = new LinkedHashMap<AEKey, Long>(); stacks.forEach(stack -> amounts.merge(stack.what(), stack.amount(), Math::addExact)); return amounts;
    }
}
