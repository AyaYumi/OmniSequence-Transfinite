package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
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
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** AE2 19.2.18: admission belongs to one filled grid, not the lifetime of a cached/installed plan. */
@Mixin(value = MolecularAssemblerBlockEntity.class, remap = false)
public abstract class RecipeLockAEMolecularAssemblerMixin {
    @Shadow private IMolecularAssemblerSupportedPattern myPlan;
    @Shadow private ItemStack myPattern;
    @Shadow private boolean isAwake;
    @Shadow @Final private AppEngInternalInventory gridInv;
    @Shadow @Final private AppEngInternalInventory patternInv;

    @Unique private static final String recipeLock$ADMITTED_CRAFT = "molecularmanipulator:admitted_craft";
    @Unique private IMolecularAssemblerSupportedPattern recipeLock$admittedPlan;
    // Only populated by loadTag, until AE can decode that saved job with a level attached.
    @Unique private ItemStack recipeLock$restoringAdmission = ItemStack.EMPTY;

    @WrapMethod(method = "pushPattern")
    private boolean recipeLock$beforeAccept(IPatternDetails pattern, KeyCounter[] inputs, Direction side,
            Operation<Boolean> original) {
        var machine = (MolecularAssemblerBlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        if (RecipeLocks.hasRules(level) && !RecipeLocks.allowedPattern(machine, pattern)) {
            return false;
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            boolean accepted = original.call(pattern, inputs, side);
            if (accepted) {
                this.recipeLock$admittedPlan = this.myPlan;
                this.recipeLock$restoringAdmission = ItemStack.EMPTY;
                machine.saveChanges();
            }
            return accepted;
        }
    }

    @WrapMethod(method = "tickingRequest")
    private TickRateModulation recipeLock$beforeExecute(IGridNode node, int elapsed,
            Operation<TickRateModulation> original) {
        var machine = (MolecularAssemblerBlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return TickRateModulation.SLEEP;
        }
        if (this.recipeLock$admittedPlan != this.myPlan) {
            this.recipeLock$admittedPlan = null;
        }
        if (!this.gridInv.getStackInSlot(9).isEmpty()) {
            // This native early-return branch only ejects output/remainders, even after revocation.
            this.recipeLock$admittedPlan = null;
            this.recipeLock$restoringAdmission = ItemStack.EMPTY;
        } else if (this.myPlan != null && this.isAwake && this.recipeLock$admittedPlan == null) {
            // Includes installed patterns and unmarked legacy jobs. isAwake + no output means hasMats,
            // not merely a cached pattern. Grant exactly one craft before native power/progress changes.
            if (RecipeLocks.hasRules(level) && !RecipeLocks.allowedPattern(machine, this.myPlan)) {
                return TickRateModulation.SLOWER;
            }
            this.recipeLock$admittedPlan = this.myPlan;
            machine.saveChanges();
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            return original.call(node, elapsed);
        }
    }

    @WrapOperation(method = "tickingRequest", at = @At(value = "INVOKE",
            target = "Lappeng/blockentity/crafting/IMolecularAssemblerSupportedPattern;assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack recipeLock$consumeAdmission(IMolecularAssemblerSupportedPattern plan, CraftingInput input,
            Level level, Operation<ItemStack> original) {
        try {
            return original.call(plan, input, level);
        } finally {
            // Not hasMats' trial assembly. Consume even on empty/throwing assembly, before output callbacks
            // or remainders can retain the installed plan and enable another round with the same object.
            this.recipeLock$admittedPlan = null;
            this.recipeLock$restoringAdmission = ItemStack.EMPTY;
            ((MolecularAssemblerBlockEntity) (Object) this).saveChanges();
        }
    }

    @WrapMethod(method = "saveAdditional")
    private void recipeLock$writeAdmission(CompoundTag data, HolderLookup.Provider registries, Operation<Void> original) {
        this.recipeLock$bindRestoredAdmission();
        original.call(data, registries);
        data.remove(recipeLock$ADMITTED_CRAFT);
        var pattern = this.recipeLock$admittedPlan != null && this.recipeLock$admittedPlan == this.myPlan
                ? this.myPlan.getDefinition().toStack() : this.recipeLock$restoringAdmission;
        if (!pattern.isEmpty() && this.gridInv.getStackInSlot(9).isEmpty()) {
            // Native saveAdditional saves the forced myPlan OR installed pattern inventory, and the input grid.
            data.put(recipeLock$ADMITTED_CRAFT, pattern.save(registries));
        }
    }

    @WrapMethod(method = "loadTag")
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
        // Reference identity is intentional: an equal replacement/decoded plan cannot inherit a runtime token.
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
        } else if (((MolecularAssemblerBlockEntity) (Object) this).getLevel() != null
                || !ItemStack.matches(this.recipeLock$restoringAdmission, this.myPattern)
                && !ItemStack.matches(this.recipeLock$restoringAdmission, this.patternInv.getStackInSlot(0))) {
            // Keep a deferred load only while the original saved pattern is still present without a level.
            // A failed decode, removed pattern or a later installation must never create a fresh admission.
            this.recipeLock$restoringAdmission = ItemStack.EMPTY;
        }
    }
}
