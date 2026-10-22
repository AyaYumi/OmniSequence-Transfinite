package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Small deterministic recipe fixture; assembly uses the supplied real keys. */
public final class EcoMolecularTestPattern implements IMolecularAssemblerSupportedPattern {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(),
                    List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    public static void bootstrap() { }

    public static final AEItemKey MATERIAL = AEItemKey.of(Items.IRON_INGOT);
    public static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    public static final AEItemKey CONTAINER = AEItemKey.of(Items.MILK_BUCKET);
    public static final AEItemKey RETURNED = AEItemKey.of(Items.BUCKET);
    private final AEItemKey key;
    private final Function<AEKey, AEKey> transition;
    private final IInput[] inputs;

    public EcoMolecularTestPattern(AEItemKey key, Function<AEKey, AEKey> transition) {
        this.key = key;
        this.transition = transition;
        inputs = new IInput[] { input(key, transition), input(MATERIAL, ignored -> null) };
    }

    private static IInput input(AEItemKey template, Function<AEKey, AEKey> transition) {
        return new IInput() {
            public GenericStack[] getPossibleInputs() {
                return new GenericStack[] { new GenericStack(template, 1) };
            }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey candidate, Level level) {
                return candidate instanceof AEItemKey item && item.getItem() == template.getItem();
            }
            public AEKey getRemainingKey(AEKey candidate) { return transition.apply(candidate); }
        };
    }

    public static AEItemKey tool(int initialDamage, int maxDamage, UUID identity) {
        // Milk buckets already have a crafting remainder, as real reusable
        // tools do. Components let this fixture exercise deterministic wear.
        var stack = new ItemStack(Items.MILK_BUCKET);
        stack.set(DataComponents.MAX_DAMAGE, maxDamage);
        stack.setDamageValue(initialDamage);
        var tag = new CompoundTag();
        tag.putUUID("tool_identity", identity);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return AEItemKey.of(stack);
    }

    public static AEKey worn(AEKey key, int breakDamage) {
        var stack = ((AEItemKey) key).toStack();
        int next = stack.getDamageValue() + 1;
        if (next >= breakDamage) return null;
        stack.setDamageValue(next);
        return AEItemKey.of(stack);
    }

    public static Level level() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) field.get(null);
        var result = (Level) unsafe.allocateInstance(ServerLevel.class);
        var dimension = Level.class.getDeclaredField("dimension");
        dimension.setAccessible(true);
        dimension.set(result, Level.OVERWORLD);
        var levelData = Level.class.getDeclaredField("levelData");
        levelData.setAccessible(true);
        levelData.set(result, java.lang.reflect.Proxy.newProxyInstance(
                EcoMolecularTestPattern.class.getClassLoader(), new Class<?>[] {levelData.getType()},
                (proxy, method, args) -> {
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                }));
        return result;
    }

    public com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan plan(long crafts)
            throws Exception {
        return java.util.Objects.requireNonNull(EcoMolecularBatchModel.analyze(
                this, first(), level(), remainders())).reusablePlan(crafts);
    }

    public KeyCounter[] supplied(long crafts) throws Exception {
        return java.util.Objects.requireNonNull(EcoMolecularBatchModel.analyze(
                this, first(), level(), remainders())).inputs(crafts);
    }

    public AutoCloseable remainderScope(KeyCounter[] inputs, long crafts) {
        var total = new KeyCounter();
        for (var entry : remainders()) total.add(entry.getKey(), Math.multiplyExact(entry.getLongValue(), crafts));
        return EcoMolecularRemainderContext.open(this, inputs, total);
    }

    public KeyCounter[] first() {
        return new KeyCounter[] { counter(key, 1), counter(MATERIAL, 1) };
    }

    public static KeyCounter counter(AEKey key, long amount) {
        var result = new KeyCounter();
        if (key != null && amount > 0) result.add(key, amount);
        return result;
    }

    public KeyCounter remainders() { return counter(transition.apply(key), 1); }
    public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
    public IInput[] getInputs() { return inputs; }
    public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, 1)); }
    public ItemStack assemble(CraftingInput input, Level level) { return OUTPUT.toStack(); }
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var result = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int index = 0; index < input.size(); index++) {
            var stack = input.getItem(index);
            if (!stack.isEmpty() && stack.is(key.getItem())) {
                var remainder = transition.apply(AEItemKey.of(stack));
                if (remainder instanceof AEItemKey item) result.set(index, item.toStack());
            }
        }
        return result;
    }
    public boolean isItemValid(int slot, AEItemKey key, Level level) { return true; }
    public boolean isSlotEnabled(int slot) { return slot < 2; }
    public void fillCraftingGrid(KeyCounter[] holders, CraftingGridAccessor grid) {
        for (int index = 0; index < holders.length; index++) {
            var entry = holders[index].getFirstEntry();
            if (entry == null || entry.getLongValue() <= 0) throw new IllegalArgumentException();
            grid.set(index, ((AEItemKey) entry.getKey()).toStack());
            holders[index].remove(entry.getKey(), 1);
        }
    }
}
