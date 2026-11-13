package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.LanternSimulation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A budding parent rock that grows the star-computation quartz family on its
 * exposed faces.  The growth order mirrors AE2's certus quartz:
 * small bud -> medium bud -> large bud -> cluster.
 */
public final class StarMarrowParentRockBlock extends Block {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final @Nullable Supplier<Block> nextStage;
    private final int decayChance;

    public StarMarrowParentRockBlock(
            Properties properties,
            @Nullable Supplier<Block> nextStage,
            int decayChance
    ) {
        super(properties);
        this.nextStage = nextStage;
        this.decayChance = decayChance;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (!state.is(ModContent.FLAWED_STAR_MARROW_PARENT_ROCK.get())
                || !(builder.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player)
                || player instanceof FakePlayer
                || !LanternSimulation.isActive(player)) {
            return drops;
        }

        // Keep the loot table's count, components and unrelated drops; only the actor's result changes.
        var simulatedDrops = new ArrayList<ItemStack>(drops.size());
        for (ItemStack stack : drops) {
            simulatedDrops.add(simulatedStack(stack));
        }
        return simulatedDrops;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level,
            BlockPos pos, Player player) {
        ItemStack stack = super.getCloneItemStack(state, target, level, pos, player);
        if (state.is(ModContent.FLAWED_STAR_MARROW_PARENT_ROCK.get())
                && player != null && !(player instanceof FakePlayer) && LanternSimulation.isActive(player)) {
            return simulatedStack(stack);
        }
        return stack;
    }

    private static ItemStack simulatedStack(ItemStack stack) {
        if (!stack.is(ModContent.FLAWED_STAR_MARROW_PARENT_ROCK_ITEM.get())) return stack;
        var simulated = new ItemStack(ModContent.FLAWLESS_STAR_MARROW_PARENT_ROCK_ITEM.get(), stack.getCount());
        if (stack.hasTag()) simulated.setTag(stack.getTag().copy());
        return simulated;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }

        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        BlockState grownState = null;

        if (canClusterGrowAtState(targetState)) {
            grownState = ModContent.STAR_MARROW_CRYSTAL_BUD_SMALL.get().defaultBlockState();
        } else if (targetState.is(ModContent.STAR_MARROW_CRYSTAL_BUD_SMALL.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            grownState = ModContent.STAR_MARROW_CRYSTAL_BUD_MEDIUM.get().defaultBlockState();
        } else if (targetState.is(ModContent.STAR_MARROW_CRYSTAL_BUD_MEDIUM.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            grownState = ModContent.STAR_MARROW_CRYSTAL_BUD_LARGE.get().defaultBlockState();
        } else if (targetState.is(ModContent.STAR_MARROW_CRYSTAL_BUD_LARGE.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            grownState = ModContent.STAR_MARROW_CRYSTAL_CLUSTER.get().defaultBlockState();
        }

        if (grownState == null) {
            return;
        }

        boolean waterlogged = targetState.getFluidState().getType() == Fluids.WATER
                && targetState.getFluidState().getAmount() == 8;
        grownState = grownState
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, waterlogged);
        level.setBlockAndUpdate(targetPos, grownState);

        // A successful growth can cost a degradable parent rock one quality step.
        // Flawless parent rock has no next stage and never decays.
        if (nextStage != null && decayChance > 0 && random.nextInt(decayChance) == 0) {
            level.setBlockAndUpdate(pos, nextStage.get().defaultBlockState());
        }
    }

    private static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
