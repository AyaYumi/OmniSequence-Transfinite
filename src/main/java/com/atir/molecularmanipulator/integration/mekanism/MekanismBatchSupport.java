package com.atir.molecularmanipulator.integration.mekanism;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.storage.CompositeStorage;
import com.atir.molecularmanipulator.integration.ae2.PatternInputCapacity;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Capacity proof for Applied Mekanistics keys and the sided input stores of known Mekanism machines. */
public final class MekanismBatchSupport {
    private static final String CHEMICAL_KEY = "me.ramidzkh.mekae2.ae2.MekanismKey";
    private static final String CHEMICAL_FACADE = "me.ramidzkh.mekae2.ae2.stack.ChemicalHandlerFacade";
    private static final String ITEM_FACADE = "appeng.me.storage.ExternalStorageFacade$ItemHandlerFacade";
    private static final String PROXY_ITEM = "mekanism.common.capabilities.proxy.ProxyItemHandler";
    private static final String PROXY_CHEMICAL = "mekanism.common.capabilities.proxy.ProxyChemicalHandler";
    private static final String BASIC_TANK = "mekanism.api.chemical.BasicChemicalTank";
    private static final String REACTION_CHAMBER = "mekanism.common.tile.machine.TileEntityPressurizedReactionChamber";
    private static final String FLUID_FACADE = "appeng.me.storage.ExternalStorageFacade$FluidHandlerFacade";
    private static final String PROXY_FLUID = "mekanism.common.capabilities.proxy.ProxyFluidHandler";

    private MekanismBatchSupport() {}

    public static boolean isChemicalKey(AEKey key) {
        return hasClass(key, CHEMICAL_KEY);
    }

    /** Returns the number of complete copies receivable on this face, or -1 when unsupported. */
    public static long limit(@Nullable BlockEntity target, @Nullable IItemHandler items, PatternProviderTarget adapter,
            List<GenericStack> unitInputs, long requestedCrafts) {
        AEItemKey item = null;
        AEFluidKey fluid = null;
        AEKey chemical = null;
        long itemsPerCraft = 0;
        long fluidPerCraft = 0;
        long chemicalPerCraft = 0;
        for (int i = 0; i < unitInputs.size(); i++) {
            var input = unitInputs.get(i);
            long amount = input.amount();
            if (amount <= 0) return -1;
            if (input.what() instanceof AEItemKey itemKey) {
                if ((item != null && !item.equals(itemKey)) || amount > Long.MAX_VALUE - itemsPerCraft) return -1;
                item = itemKey;
                itemsPerCraft += amount;
            } else if (input.what() instanceof AEFluidKey fluidKey) {
                if ((fluid != null && !fluid.equals(fluidKey)) || amount > Long.MAX_VALUE - fluidPerCraft) return -1;
                fluid = fluidKey;
                fluidPerCraft += amount;
            } else if (isChemicalKey(input.what())) {
                if ((chemical != null && !chemical.equals(input.what())) || amount > Long.MAX_VALUE - chemicalPerCraft) {
                    return -1;
                }
                chemical = input.what();
                chemicalPerCraft += amount;
            } else {
                return -1;
            }
        }
        if (chemical == null || (fluid != null && (item == null || !hasClass(target, REACTION_CHAMBER)))) return -1;

        long copies = Math.min(requestedCrafts, Long.MAX_VALUE / chemicalPerCraft);
        if (item != null) copies = Math.min(copies, Long.MAX_VALUE / itemsPerCraft);
        if (fluid != null) copies = Math.min(copies, Long.MAX_VALUE / fluidPerCraft);
        if (copies <= 0) return 0;
        try {
            if (item != null) {
                if (target == null) return -1;
                var machine = MACHINES.get(target.getClass());
                if (machine == null) return -1;
                copies = limitItems(target, items, adapter, machine, item, itemsPerCraft, fluid, fluidPerCraft,
                        chemical, chemicalPerCraft, copies);
                if (copies <= 0) return copies;
            }
            if (fluid != null) {
                copies = simulate(adapter, fluid, fluidPerCraft, copies);
                if (copies <= 0) return copies;
            }
            copies = simulate(adapter, chemical, chemicalPerCraft, copies);
            if (copies <= 0) return copies;
            for (var input : unitInputs) {
                if ((chemical.equals(input.what()) && input.amount() != chemicalPerCraft)
                        || (input.what().equals(fluid) && input.amount() != fluidPerCraft)) {
                    // The aggregate reserves the tank once. Validate each sparse offer at its actual size too.
                    long amount = input.amount() * copies;
                    if (adapter.insert(input.what(), amount, Actionable.SIMULATE) != amount) return -1;
                }
            }
            return copies;
        } catch (LinkageError unavailable) {
            return -1;
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable unavailable) {
            return -1;
        }
    }

    private static long limitItems(BlockEntity target, @Nullable IItemHandler items, PatternProviderTarget adapter,
            Machine machine, AEItemKey item, long itemsPerCraft, @Nullable AEFluidKey fluid, long fluidPerCraft,
            AEKey chemical, long chemicalPerCraft, long copies) throws Throwable {
        // Inspect the handlers this adapter will actually insert into. Two positive probes cannot
        // prove independence when either channel maps to a shared or redirected inventory.
        Object storage = PatternInputCapacity.targetStorage(adapter);
        var access = OptionalAccess.INSTANCE;
        if (access == null || storage == null || storage.getClass() != CompositeStorage.class) return -1;
        var channels = (Map<?, ?>) access.channels.invokeExact(storage);
        Object itemFacade = channels.get(item.getType());
        Object chemicalFacade = channels.get(chemical.getType());
        if (itemFacade == null || chemicalFacade == null) return 0;
        if (!hasClass(items, PROXY_ITEM) || !hasClass(itemFacade, ITEM_FACADE)
                || !hasClass(chemicalFacade, CHEMICAL_FACADE)
                || access.itemHandler.invokeExact(itemFacade) != items
                || access.itemOwner.invokeExact((Object) items) != target) return -1;

        Object chemicalHandler = access.chemicalHandler.invokeExact(chemicalFacade);
        if (!hasClass(chemicalHandler, PROXY_CHEMICAL)
                || access.chemicalOwner.invokeExact(chemicalHandler) != target) return -1;
        Direction side = (Direction) access.side.invokeExact((Object) items);
        if (side == null || side != (Direction) access.side.invokeExact(chemicalHandler)) return -1;

        var tanks = (List<?>) machine.tanks.invokeExact(target, side);
        if (tanks.isEmpty()) return 0;
        if (machine.reaction != null) {
            if (fluid == null) return -1;
            var reaction = machine.reaction;
            Object facade = channels.get(fluid.getType());
            if (facade == null) return 0;
            if (!hasClass(facade, FLUID_FACADE)) return -1;
            Object handler = reaction.fluidHandler.invokeExact(facade);
            if (!hasClass(handler, PROXY_FLUID) || reaction.fluidOwner.invokeExact(handler) != target
                    || (Direction) access.side.invokeExact(handler) != side) return -1;
            var fluidTanks = (List<?>) reaction.fluidTanks.invokeExact(target, side);
            if (fluidTanks.isEmpty()) return 0;
            if (fluidTanks.size() != 1 || fluidTanks.getFirst() != reaction.inputFluid.invokeExact((Object) target)
                    || !hasClass(fluidTanks.getFirst(), "mekanism.common.capabilities.fluid.BasicFluidTank")) return -1;
            Object input = reaction.inputChemical.invokeExact((Object) target);
            if (!hasClass(input, BASIC_TANK)) return -1;
            // The only other native PRC tank is its output. It rejects external insertion.
            Object output = reaction.outputChemical.invokeExact((Object) target);
            boolean hasInput = false;
            for (var tank : tanks) {
                if (tank == input && !hasInput) hasInput = true;
                else if (tank != output || !hasClass(output, BASIC_TANK)) return -1;
            }
            if (!hasInput) return 0;
        } else {
            var internalTanks = (List<?>) machine.tanks.invokeExact(target, (Direction) null);
            if (tanks.size() != 1 || internalTanks.size() != 1 || tanks.getFirst() != internalTanks.getFirst()
                    || !hasClass(tanks.getFirst(), BASIC_TANK)) return -1;
        }

        var slots = (List<?>) machine.slots.invokeExact(target, side);
        int processSlots = 0;
        for (int i = 0; i < slots.size(); i++) {
            Object slot = slots.get(i);
            if (hasClass(slot, machine.inputSlot)) {
                processSlots++;
            } else if (!hasClass(slot, machine.outputSlot)) {
                // A chemical conversion slot or another auxiliary buffer can accept the item too.
                return -1;
            }
        }
        if (processSlots == 0) return 0;
        if (machine.findRecipe == null && processSlots != 1) return -1;
        if (!PatternInputCapacity.hasConstantStackLimit(item)) return -1;

        var unitItem = item.toStack((int) Math.min(Integer.MAX_VALUE, itemsPerCraft));
        Object unitChemical = access.withAmount.invokeExact((Object) chemical, chemicalPerCraft);
        // A valid complete group stays type-compatible as its member inputs arrive in sparse order.
        if (machine.reaction != null) {
            Object unitFluid = fluid.toStack((int) Math.min(Integer.MAX_VALUE, fluidPerCraft));
            if (!(boolean) machine.containsRecipe.invokeExact(target, (Object) unitItem, unitFluid, unitChemical)) return -1;
        } else if (!(boolean) machine.containsRecipe.invokeExact(target, (Object) unitItem, unitChemical)) return -1;

        if (machine.findRecipe != null && !factorySlotsStayValid(target, items, machine, slots, item,
                unitItem, unitChemical, access)) return -1;

        // Each process slot and each native item offer uses an int, but the combined room
        // across independent lanes can exceed it. Ask the actual sided proxy once per lane.
        long wanted = itemsPerCraft * copies;
        int offerSize = (int) Math.min(Integer.MAX_VALUE, wanted);
        var offer = item.toStack(offerSize);
        long itemRoom = 0;
        for (int i = 0; i < slots.size(); i++) {
            if (hasClass(slots.get(i), machine.inputSlot)) {
                itemRoom += offerSize - items.insertItem(i, offer, true).getCount();
                if (itemRoom >= wanted) return copies;
            }
        }
        return itemRoom / itemsPerCraft;
    }

    private static boolean factorySlotsStayValid(BlockEntity target, IItemHandler items, Machine machine,
            List<?> slots, AEItemKey item, ItemStack unitItem, Object unitChemical, OptionalAccess access) throws Throwable {
        // A factory input consults its paired output. Chemistry can arrive before an empty
        // lane's first item, or between two native item offers into that lane.
        var allSlots = (List<?>) machine.slots.invokeExact(target, (Direction) null);
        int nextLane = 0;
        ItemStack expectedOutput = null;
        for (int i = 0; i < slots.size(); i++) {
            Object slot = slots.get(i);
            if (!hasClass(slot, machine.inputSlot)) continue;
            while (nextLane < allSlots.size() && allSlots.get(nextLane) != slot) nextLane++;
            // Each process registers its input immediately before its output. Matching in order
            // also makes sure we never count a repeated reference to the same slot.
            if (nextLane >= allSlots.size() - 1) return false;
            Object outputSlot = allSlots.get(nextLane + 1);
            if (!hasClass(outputSlot, machine.outputSlot)) return false;
            nextLane += 2;

            var present = items.getStackInSlot(i);
            if (!present.isEmpty() && (!item.matches(present)
                    || present.getCount() >= (int) access.slotLimit.invokeExact(slot, unitItem))) {
                continue;
            }
            var output = (ItemStack) access.slotStack.invokeExact(outputSlot);
            if (output.isEmpty()) continue;
            if (expectedOutput == null) {
                Object recipe = machine.findRecipe.invokeExact(target, (Object) unitItem, unitChemical);
                if (!hasFixedOutput(recipe)) return false;
                expectedOutput = (ItemStack) access.recipeOutput.invokeExact(recipe);
            }
            if (!ItemStack.isSameItemSameComponents(output, expectedOutput)) return false;
        }
        return true;
    }

    private static long simulate(PatternProviderTarget adapter, AEKey key, long perCraft, long crafts) {
        long requested = perCraft * crafts;
        long inserted = adapter.insert(key, requested, Actionable.SIMULATE);
        if (inserted == requested) return crafts;
        if (inserted < 0 || inserted > requested) return -1;
        long whole = inserted / perCraft;
        if (whole == 0) return 0;
        return adapter.insert(key, whole * perCraft, Actionable.SIMULATE) == whole * perCraft ? whole : -1;
    }

    private static boolean hasClass(@Nullable Object value, String className) {
        return value != null && value.getClass().getName().equals(className);
    }

    private static boolean hasFixedOutput(@Nullable Object recipe) {
        if (recipe == null) return false;
        return switch (recipe.getClass().getName()) {
            case "mekanism.api.recipes.basic.BasicCompressingRecipe",
                    "mekanism.api.recipes.basic.BasicInjectingRecipe",
                    "mekanism.api.recipes.basic.BasicMetallurgicInfuserRecipe",
                    "mekanism.api.recipes.basic.BasicPurifyingRecipe" -> true;
            default -> false;
        };
    }

    private record Reaction(MethodHandle fluidHandler, MethodHandle fluidOwner, MethodHandle fluidTanks,
            MethodHandle inputFluid, MethodHandle inputChemical, MethodHandle outputChemical) {}

    private record Machine(String inputSlot, String outputSlot, MethodHandle slots, MethodHandle tanks,
            MethodHandle containsRecipe, @Nullable MethodHandle findRecipe, @Nullable Reaction reaction) {}

    private static final ClassValue<Machine> MACHINES = new ClassValue<>() {
        @Override
        protected @Nullable Machine computeValue(Class<?> type) {
            String inputSlot;
            String outputSlot = "mekanism.common.inventory.slot.OutputInventorySlot";
            boolean factory = true;
            switch (type.getName()) {
                case "mekanism.common.tile.machine.TileEntityMetallurgicInfuser",
                        "mekanism.common.tile.machine.TileEntityChemicalInjectionChamber",
                        "mekanism.common.tile.machine.TileEntityOsmiumCompressor",
                        "mekanism.common.tile.machine.TileEntityPurificationChamber", REACTION_CHAMBER -> {
                    inputSlot = "mekanism.common.inventory.slot.InputInventorySlot";
                    factory = false;
                }
                case "mekanism.common.tile.factory.TileEntityItemStackChemicalToItemStackFactory" ->
                    inputSlot = "mekanism.common.inventory.slot.FactoryInputInventorySlot";
                case "com.jerry.mekextras.common.tile.factory.TileEntityExtraItemStackChemicalToItemStackFactory" -> {
                    inputSlot = "com.jerry.mekextras.common.inventory.slot.ExtraFactoryInputInventorySlot";
                    outputSlot = "com.jerry.mekextras.common.inventory.slot.ExtraFactoryOutputInventorySlot";
                }
                default -> { return null; }
            }
            try {
                var sidedList = MethodType.methodType(List.class, BlockEntity.class, Direction.class);
                var pair = MethodType.methodType(boolean.class, BlockEntity.class, Object.class, Object.class);
                var reaction = type.getName().equals(REACTION_CHAMBER)
                        ? new Reaction(getter(load(FLUID_FACADE), "handler", Object.class),
                                publicMethod(load(PROXY_FLUID), "getInternalHandler", MethodType.methodType(Object.class, Object.class)),
                                publicMethod(type, "getFluidTanks", sidedList, Direction.class),
                                getter(type, "inputFluidTank", Object.class), getter(type, "inputGasTank", Object.class),
                                getter(type, "outputGasTank", Object.class)) : null;
                return new Machine(inputSlot, outputSlot,
                        publicMethod(type, "getInventorySlots", sidedList, Direction.class),
                        publicMethod(type, "getChemicalTanks", sidedList, Direction.class),
                        reaction == null ? publicMethod(type, "containsRecipeAB", pair, Object.class, Object.class)
                                : publicMethod(type, "containsRecipeABC", pair.appendParameterTypes(Object.class),
                                        Object.class, Object.class, Object.class),
                        factory ? publicMethod(type, "findFirstRecipe",
                                MethodType.methodType(Object.class, BlockEntity.class, Object.class, Object.class),
                                Object.class, Object.class) : null, reaction);
            } catch (ReflectiveOperationException | LinkageError | SecurityException unavailable) {
                return null;
            }
        }
    };

    private record OptionalAccess(MethodHandle channels, MethodHandle itemHandler, MethodHandle chemicalHandler,
            MethodHandle side, MethodHandle itemOwner, MethodHandle chemicalOwner, MethodHandle withAmount,
            MethodHandle slotStack, MethodHandle slotLimit, MethodHandle recipeOutput) {
        private static final @Nullable OptionalAccess INSTANCE = bind();

        private static @Nullable OptionalAccess bind() {
            try {
                var objectGetter = MethodType.methodType(Object.class, Object.class);
                var stackGetter = MethodType.methodType(ItemStack.class, Object.class);
                var slotType = load("mekanism.api.inventory.IInventorySlot");
                return new OptionalAccess(getter(CompositeStorage.class, "storages", Map.class),
                        getter(load(ITEM_FACADE), "handler", Object.class),
                        publicMethod(load(CHEMICAL_FACADE), "handler", objectGetter),
                        getter(load("mekanism.common.capabilities.proxy.ProxyHandler"), "side", Direction.class),
                        getter(load(PROXY_ITEM), "inventory", Object.class),
                        publicMethod(load(PROXY_CHEMICAL), "getInternalHandler", objectGetter),
                        publicMethod(load(CHEMICAL_KEY), "withAmount", objectGetter.appendParameterTypes(long.class),
                                long.class),
                        publicMethod(slotType, "getStack", stackGetter),
                        publicMethod(slotType, "getLimit", MethodType.methodType(int.class, Object.class, ItemStack.class),
                                ItemStack.class),
                        publicMethod(load("mekanism.api.recipes.basic.BasicItemStackChemicalToItemStackRecipe"),
                                "getOutputRaw", stackGetter));
            } catch (ReflectiveOperationException | LinkageError | SecurityException unavailable) {
                return null;
            }
        }
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        return Class.forName(name, false, MekanismBatchSupport.class.getClassLoader());
    }

    private static MethodHandle getter(Class<?> owner, String name, Class<?> type) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        if (!field.trySetAccessible()) throw new IllegalAccessException(name);
        return MethodHandles.lookup().unreflectGetter(field).asType(MethodType.methodType(type, Object.class));
    }

    private static MethodHandle publicMethod(Class<?> owner, String name, MethodType type, Class<?>... parameters)
            throws ReflectiveOperationException {
        return MethodHandles.publicLookup().unreflect(owner.getMethod(name, parameters)).asType(type);
    }
}
