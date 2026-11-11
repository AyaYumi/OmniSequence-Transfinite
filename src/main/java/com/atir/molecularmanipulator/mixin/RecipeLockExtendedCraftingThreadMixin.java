package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/**
 * ExtendedAE 1.21-2.2.32: shared executor for TileExMolecularAssembler and TileAssemblerMatrixCrafter.
 * CraftingMatrixThread overrides assemblePattern to return a cached output, but still calls this tick.
 */
@Mixin(targets = "com.glodblock.github.extendedae.common.me.CraftingThread", remap = false)
public abstract class RecipeLockExtendedCraftingThreadMixin {
    @Shadow @Final private AEBaseBlockEntity host;
    @Shadow @Final protected AppEngInternalInventory gridInv;
    @Shadow protected IMolecularAssemblerSupportedPattern myPlan;
    @Shadow private ItemStack myPattern;
    @Shadow private boolean isAwake;

    @Unique private static final String recipeLock$ADMITTED_CRAFT = "molecularmanipulator:admitted_craft";
    @Unique private IMolecularAssemblerSupportedPattern recipeLock$admittedPlan;
    @Unique private ItemStack recipeLock$restoringAdmission = ItemStack.EMPTY;

    @WrapMethod(method = "acceptJob")
    private boolean recipeLock$beforeAccept(IPatternDetails pattern, KeyCounter[] inputs, Direction side,
            Operation<Boolean> original) {
        var level = this.host.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        if (RecipeLocks.hasRules(level) && !RecipeLocks.allowedPattern(this.host, pattern)) {
            return false;
        }
        try (var scope = RecipeExecutionContext.open(this.host)) {
            boolean accepted = original.call(pattern, inputs, side);
            if (accepted) {
                this.recipeLock$admittedPlan = this.myPlan;
                this.recipeLock$restoringAdmission = ItemStack.EMPTY;
                this.host.saveChanges();
            }
            return accepted;
        }
    }

    @WrapMethod(method = "tick")
    private TickRateModulation recipeLock$beforeCachedExecution(int cards, int elapsed,
            Operation<TickRateModulation> original) {
        var level = this.host.getLevel();
        if (level == null || level.isClientSide) {
            return TickRateModulation.SLEEP;
        }
        if (this.recipeLock$admittedPlan != this.myPlan) {
            this.recipeLock$admittedPlan = null;
        }
        if (!this.gridInv.getStackInSlot(9).isEmpty()) {
            // The native early return ejects completed output/remainders without starting another job.
            this.recipeLock$admittedPlan = null;
            this.recipeLock$restoringAdmission = ItemStack.EMPTY;
        } else if (this.myPlan != null && this.isAwake && this.recipeLock$admittedPlan == null) {
            // Unmarked restored jobs still need live admission, before any power/progress change.
            if (RecipeLocks.hasRules(level) && !RecipeLocks.allowedPattern(this.host, this.myPlan)) {
                return TickRateModulation.SLOWER;
            }
            this.recipeLock$admittedPlan = this.myPlan;
            this.host.saveChanges();
        }
        try (var scope = RecipeExecutionContext.open(this.host)) {
            return original.call(cards, elapsed);
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/glodblock/github/extendedae/common/me/CraftingThread;assemblePattern(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack recipeLock$consumeAdmission(@Coerce Object thread, CraftingInput input, Operation<ItemStack> original) {
        try {
            // Wrap the virtual call in tick, so CraftingMatrixThread's cached-output override is covered too.
            return original.call(thread, input);
        } finally {
            this.recipeLock$admittedPlan = null;
            this.recipeLock$restoringAdmission = ItemStack.EMPTY;
            this.host.saveChanges();
        }
    }

    @WrapMethod(method = "writeNBT")
    private CompoundTag recipeLock$writeAdmission(HolderLookup.Provider registries, Operation<CompoundTag> original) {
        this.recipeLock$bindRestoredAdmission();
        var data = original.call(registries);
        var pattern = this.recipeLock$admittedPlan != null && this.recipeLock$admittedPlan == this.myPlan
                ? this.myPlan.getDefinition().toStack() : this.recipeLock$restoringAdmission;
        if (!pattern.isEmpty() && this.gridInv.getStackInSlot(9).isEmpty()) {
            // Stored inside this thread's native #ctN/myPlan tag; the host saves its input grid alongside it.
            data.put(recipeLock$ADMITTED_CRAFT, pattern.save(registries));
        }
        return data;
    }

    @WrapMethod(method = "readNBT")
    private void recipeLock$readAdmission(CompoundTag data, HolderLookup.Provider registries, Operation<Void> original) {
        this.recipeLock$admittedPlan = null;
        this.recipeLock$restoringAdmission = ItemStack.EMPTY;
        original.call(data, registries);
        this.recipeLock$restoringAdmission = ItemStack.parseOptional(registries, data.getCompound(recipeLock$ADMITTED_CRAFT));
        this.recipeLock$bindRestoredAdmission();
    }

    @WrapMethod(method = "recalculatePlan")
    private void recipeLock$afterPlanChange(Operation<Void> original) {
        original.call();
        this.recipeLock$bindRestoredAdmission();
    }

    @Unique
    private void recipeLock$bindRestoredAdmission() {
        if (this.recipeLock$admittedPlan != this.myPlan) {
            this.recipeLock$admittedPlan = null;
        }
        if (this.recipeLock$restoringAdmission.isEmpty()) {
            return;
        }
        if (this.myPlan != null) {
            if (ItemStack.matches(this.recipeLock$restoringAdmission, this.myPlan.getDefinition().toStack())) {
                this.recipeLock$admittedPlan = this.myPlan;
            }
            this.recipeLock$restoringAdmission = ItemStack.EMPTY;
        } else if (this.host.getLevel() != null || !ItemStack.matches(this.recipeLock$restoringAdmission, this.myPattern)) {
            // readNBT can precede level attachment AND, in the matrix host, input-inventory restoration.
            // Only the exact still-pending saved plan can bind once; failed/replaced plans lose the marker.
            this.recipeLock$restoringAdmission = ItemStack.EMPTY;
        }
    }
}
