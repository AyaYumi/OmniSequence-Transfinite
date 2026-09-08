package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Conservative reusable-input adapters shared by planning, extraction and the
 * three molecular crafting providers.
 */
public final class MolecularReusableInputAdapters {
    // Finite tools are exhaustively validated against the real recipe for every
    // damage state. Keep one synchronous provider push below a watchdog-risky
    // amount of recipe work; larger orders continue in subsequent batches.
    public static final long MAX_DETERMINISTIC_TRANSITIONS = 2_048;

    private MolecularReusableInputAdapters() {
    }

    public enum Mode {
        CONSUMABLE,
        INVARIANT_REUSABLE,
        DETERMINISTIC_DAMAGE,
        UNSUPPORTED
    }

    public record Analysis(Mode mode, AEKey initialKey, long safeCrafts,
            @Nullable AEKey finalKey) {
        public boolean isReusable() {
            return mode == Mode.INVARIANT_REUSABLE
                    || mode == Mode.DETERMINISTIC_DAMAGE;
        }

        public boolean isSupported() {
            return mode != Mode.UNSUPPORTED;
        }
    }

    /**
     * Analyses the actual key extracted by AE2, never just the encoded pattern
     * template. Damageable items are accepted only when every observed
     * transition is exactly Damage+1 and Unbreaking is absent.
     */
    public static Analysis analyze(IPatternDetails.IInput input, AEKey initialKey,
            Level level, long requestedCrafts) {
        if (input == null || initialKey == null || level == null || requestedCrafts <= 0) {
            return unsupported(initialKey);
        }

        try {
            if (!input.isValid(initialKey, level)) {
                return unsupported(initialKey);
            }

            AEKey firstRemainder = input.getRemainingKey(initialKey);
            if (firstRemainder == null) {
                if (isDeterministicDamageCandidate(initialKey)) {
                    // A tool on its final use legitimately has no remainder. It
                    // can execute one craft, but can never form a multi-craft batch.
                    return new Analysis(Mode.DETERMINISTIC_DAMAGE, initialKey, 1, null);
                }
                return new Analysis(Mode.CONSUMABLE, initialKey, Long.MAX_VALUE, null);
            }

            if (firstRemainder.equals(initialKey)) {
                // A damageable item can return the same key due to an Unbreaking
                // roll or another contextual rule. Never cache that random result
                // as an infinite catalyst. Minecraft 1.21 also reports stacks with
                // MAX_DAMAGE=0 as damageable; reusable recipe items such as the
                // Master Infusion Crystal intentionally use that representation.
                // An explicit UNBREAKABLE component is likewise a stable invariant.
                if (!(initialKey instanceof AEItemKey itemKey)
                        || hasFiniteMutableDurability(itemKey.toStack())) {
                    return unsupported(initialKey);
                }
                return new Analysis(Mode.INVARIANT_REUSABLE, initialKey,
                        Long.MAX_VALUE, initialKey);
            }

            if (!isDeterministicDamageCandidate(initialKey)
                    || !isExactDamageStep(initialKey, firstRemainder)) {
                return unsupported(initialKey);
            }

            long limit = Math.min(requestedCrafts, MAX_DETERMINISTIC_TRANSITIONS);
            long completed = 1;
            AEKey current = firstRemainder;
            while (completed < limit) {
                if (!input.isValid(current, level)) {
                    break;
                }
                AEKey next = input.getRemainingKey(current);
                completed++;
                if (next == null) {
                    return new Analysis(Mode.DETERMINISTIC_DAMAGE, initialKey,
                            completed, null);
                }
                if (!isExactDamageStep(current, next)) {
                    return unsupported(initialKey);
                }
                current = next;
            }

            return new Analysis(Mode.DETERMINISTIC_DAMAGE, initialKey,
                    completed, current);
        } catch (RuntimeException exception) {
            return unsupported(initialKey);
        }
    }

    public static boolean isExactDamageStep(AEKey current, AEKey next) {
        if (!(current instanceof AEItemKey currentItem)
                || !(next instanceof AEItemKey nextItem)) {
            return false;
        }

        ItemStack currentStack = currentItem.toStack();
        if (!currentStack.isDamageableItem()
                || hasUnbreaking(currentStack)
                || currentStack.getDamageValue() == Integer.MAX_VALUE) {
            return false;
        }

        ItemStack expected = currentStack.copy();
        expected.setCount(1);
        expected.setDamageValue(currentStack.getDamageValue() + 1);
        AEItemKey expectedKey = AEItemKey.of(expected);
        return expectedKey != null && expectedKey.equals(nextItem);
    }

    private static boolean isDeterministicDamageCandidate(AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return false;
        }
        ItemStack stack = itemKey.toStack();
        return hasFiniteMutableDurability(stack)
                && stack.hasCraftingRemainingItem()
                && !hasUnbreaking(stack);
    }

    private static boolean hasFiniteMutableDurability(ItemStack stack) {
        return stack.isDamageableItem()
                && stack.getMaxDamage() > 0
                && !(stack.hasTag() && stack.getTag().getBoolean("Unbreakable"));
    }

    private static boolean hasUnbreaking(ItemStack stack) {
        for (var enchantment : stack.getAllEnchantments().keySet()) {
            if (enchantment == Enchantments.UNBREAKING) {
                return true;
            }
        }
        return false;
    }

    private static Analysis unsupported(@Nullable AEKey initialKey) {
        return new Analysis(Mode.UNSUPPORTED, initialKey, 0, null);
    }
}
