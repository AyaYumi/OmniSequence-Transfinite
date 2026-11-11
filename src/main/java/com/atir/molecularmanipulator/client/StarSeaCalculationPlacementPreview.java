package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.client.render.StarSeaCalculationModel;
import com.atir.molecularmanipulator.registry.StarSeaCalculationContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Aim with either hand: a full-size teal/red building, not a construction progress overlay. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class StarSeaCalculationPlacementPreview {
    private static Level checkedLevel;
    private static BlockPos checkedOrigin;
    private static Direction checkedFacing;
    private static InteractionHand checkedHand;
    private static long checkedTick = Long.MIN_VALUE;
    private static boolean valid;

    private StarSeaCalculationPlacementPreview() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || mc.player.isSpectator()
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != BlockHitResult.Type.BLOCK) {
            clear();
            return;
        }
        var hand = heldHand(mc);
        if (hand == null) { clear(); return; }
        var stack = mc.player.getItemInHand(hand);
        // Vanilla's context also handles aiming at replaceable grass; the server uses exactly this anchor.
        var context = new BlockPlaceContext(mc.player, hand, stack, hit);
        var origin = context.getClickedPos();
        var facing = context.getHorizontalDirection().getOpposite();
        if (checkedLevel != mc.level || checkedTick != mc.level.getGameTime() || !origin.equals(checkedOrigin)
                || checkedFacing != facing || checkedHand != hand) {
            valid = StarSeaCalculationContent.BLOCK.get().canPlaceBuilding(context, facing);
            checkedLevel = mc.level;
            checkedTick = mc.level.getGameTime();
            checkedOrigin = origin;
            checkedFacing = facing;
            checkedHand = hand;
        }
        var bounds = com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock.bounds(origin, facing);
        if (!event.getFrustum().isVisible(bounds)) return;
        float r = valid ? .35F : 1F, g = valid ? 1F : .24F, b = valid ? .85F : .16F;
        var camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        StarSeaCalculationModel.transform(poses, facing);
        StarSeaCalculationModel.draw(poses, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                r, g, b, .42F, true);
        buffers.endBatch(Sheets.translucentCullBlockSheet());
        poses.popPose();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poses, buffers.getBuffer(RenderType.lines()), bounds.inflate(.003), r, g, b, .8F);
        buffers.endBatch(RenderType.lines());
        poses.popPose();
    }

    private static InteractionHand heldHand(Minecraft mc) {
        if (mc.player.getMainHandItem().is(StarSeaCalculationContent.ITEM.get())) return InteractionHand.MAIN_HAND;
        return mc.player.getOffhandItem().is(StarSeaCalculationContent.ITEM.get()) ? InteractionHand.OFF_HAND : null;
    }

    public static void clear() {
        checkedLevel = null;
        checkedOrigin = null;
        checkedFacing = null;
        checkedHand = null;
        checkedTick = Long.MIN_VALUE;
    }
}
