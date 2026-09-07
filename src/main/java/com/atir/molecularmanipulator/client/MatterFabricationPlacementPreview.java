package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.MatterFabricationPatternAssemblyBlock;
import com.atir.molecularmanipulator.block.MatterFabricationPortBlock;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Installation sockets share the blueprint's validation rules and controller transform. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class MatterFabricationPlacementPreview {
    private static final double MAX_DISTANCE_SQUARED = 96.0 * 96.0;

    private MatterFabricationPlacementPreview() {
    }

    static boolean isPlacementItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item
                && (item.getBlock() instanceof MatterFabricationPortBlock
                        || item.getBlock() instanceof MatterFabricationPatternAssemblyBlock);
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (isPlacementItem(event.getItemStack())) {
            event.getToolTip().add(Component.translatable(
                    "tooltip.molecularmanipulator.matter_fabrication.placement").withStyle(ChatFormatting.AQUA));
            event.getToolTip().add(Component.translatable(
                    "tooltip.molecularmanipulator.matter_fabrication.placement_occupied").withStyle(ChatFormatting.GOLD));
        }
    }

    static void render(MatterFabricationBlockEntity machine, PoseStack poseStack, MultiBufferSource buffers) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = machine.getLevel();
        if (player == null || level == null || level != minecraft.level
                || (!isPlacementItem(player.getMainHandItem()) && !isPlacementItem(player.getOffhandItem()))
                || minecraft.gameRenderer.getMainCamera().getPosition()
                        .distanceToSqr(machine.getBlockPos().getCenter()) > MAX_DISTANCE_SQUARED) {
            return;
        }
        var facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var lines = buffers.getBuffer(OmniRenderLayers.placementLines());
        // All five service blocks are accepted in these same current-layout sockets.
        for (var part : MatterFabricationStructure.patternAssemblyBays()) {
            var pos = MatterFabricationStructure.worldPos(machine.getBlockPos(), facing, part);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            var state = level.getBlockState(pos);
            boolean available = state.canBeReplaced()
                    || state.is(MatterFabricationStructure.partState(part.type()).getBlock());
            var bounds = new AABB(pos.subtract(machine.getBlockPos())).inflate(0.006);
            LevelRenderer.renderLineBox(poseStack, lines, bounds,
                    available ? 0.2F : 1.0F, available ? 0.9F : 0.65F, available ? 1.0F : 0.15F, 0.9F);
        }
    }
}
