package com.atir.molecularmanipulator.block;

import appeng.block.AEBaseEntityBlock;
import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class SequenceWirelessHubBlock extends AEBaseEntityBlock<SequenceWirelessHubBlockEntity> {
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public SequenceWirelessHubBlock() {
        // Emission belongs exclusively to the purple model quads, never to world lighting.
        super(Properties.of().strength(4.0F).requiresCorrectToolForDrops().lightLevel(state -> 0));
        registerDefaultState(defaultBlockState().setValue(ONLINE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ONLINE);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, SequenceWirelessHubBlockEntity hub) {
        return state.setValue(ONLINE, hub.isOnline());
    }

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            ItemStack stack, BlockHitResult hit) {
        var hub = getBlockEntity(level, pos);
        if (!level.isClientSide() && hub != null) {
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.wireless_hub.status",
                    Component.translatable("message.molecularmanipulator.wireless_hub." + (hub.isOnline() ? "online" : "offline")),
                    hub.connectedMachines()), false);
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.wireless_hub.usage"), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.wireless_hub"));
    }
}
