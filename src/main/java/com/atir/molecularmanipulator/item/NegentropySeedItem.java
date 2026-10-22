package com.atir.molecularmanipulator.item;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Early-game entropy-stripping seed.
 *
 * One use moves an entroized Fluix parent rock one step toward a negentropy
 * parent rock. Once the source is negentropy, further uses repair it:
 * severe -> damaged -> flawed. The flawless stage is reserved for a later
 * Negative Entropy Engine mechanic and cannot be reached with this seed.
 */
public final class NegentropySeedItem extends Item {
    private static final ResourceLocation ENTRO_FULLY =
            extendedae("entro_budding_fully");
    private static final ResourceLocation ENTRO_MOSTLY =
            extendedae("entro_budding_mostly");
    private static final ResourceLocation ENTRO_HALF =
            extendedae("entro_budding_half");
    private static final ResourceLocation ENTRO_HARDLY =
            extendedae("entro_budding_hardly");

    public NegentropySeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Block next = nextBlock(state);
        if (next == null) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, next.defaultBlockState());
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.9F, 0.75F + level.random.nextFloat() * 0.25F);

            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD,
                        pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                        12, 0.45, 0.4, 0.45, 0.02);
                server.sendParticles(ParticleTypes.ENCHANT,
                        pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5,
                        14, 0.55, 0.5, 0.55, 0.05);
            }

            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.negentropy_seed")
                .withStyle(ChatFormatting.GRAY));
    }

    private static Block nextBlock(BlockState state) {
        if (isState(state, ENTRO_FULLY)) {
            return extBlock("entro_budding_mostly");
        }
        if (isState(state, ENTRO_MOSTLY)) {
            return extBlock("entro_budding_half");
        }
        if (isState(state, ENTRO_HALF)) {
            return extBlock("entro_budding_hardly");
        }
        if (isState(state, ENTRO_HARDLY)) {
            return ModContent.NEGATIVE_ENTROPY_DAMAGED_BUDDING_QUARTZ.get();
        }
        if (state.is(ModContent.NEGATIVE_ENTROPY_DAMAGED_BUDDING_QUARTZ.get())) {
            return ModContent.NEGATIVE_ENTROPY_CHIPPED_BUDDING_QUARTZ.get();
        }
        if (state.is(ModContent.NEGATIVE_ENTROPY_CHIPPED_BUDDING_QUARTZ.get())) {
            return ModContent.NEGATIVE_ENTROPY_FLAWED_BUDDING_QUARTZ.get();
        }
        return null;
    }

    private static boolean isState(BlockState state, ResourceLocation id) {
        Block block = extBlock(id.getPath());
        return block != null && state.is(block);
    }

    private static Block extBlock(String path) {
        return BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath("extendedae", path))
                .orElse(null);
    }

    private static ResourceLocation extendedae(String path) {
        return ResourceLocation.fromNamespaceAndPath("extendedae", path);
    }
}
