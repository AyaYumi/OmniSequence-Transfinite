package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.GreenFireLanternBlock;
import com.atir.molecularmanipulator.registry.GreenFireLanternContent;
import com.atir.molecularmanipulator.world.GreenFireLanternLights;
import com.atir.molecularmanipulator.world.LanternSimulation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.GlStateBackup;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Keeps unlit surfaces black while the player is inside any lit lantern sphere.
 * Reveals the dark block grid only after the player leaves every light pool.
 * Runs after the other level passes and before the first-person hand and HUD.
 * Outside simulation, only held lanterns contribute a local surface light.
 */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class LanternSimulationRenderer {
    private static final int MAX_LIGHTS = 32;
    private static final float LIGHT_RADIUS = 10.0F;
    private static final float BOUNDARY_WIDTH = 1.0F;

    // Subtract the double-precision camera position before converting to floats.
    // One sorted list merges placed and held sources, without stacking overlaps.
    private static final float[] LIGHT_POSITIONS = new float[MAX_LIGHTS * 3];
    private static final double[] LIGHT_DISTANCES = new double[MAX_LIGHTS];
    private static int lightCount;
    private static RenderTarget scene;
    private static Level sceneLevel;

    private LanternSimulationRenderer() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        var player = mc.player;
        var target = mc.getMainRenderTarget();
        if (scene != null && (sceneLevel != level || target == null || !matchesTarget(target))) {
            release();
        }
        if (level == null || player == null || target == null || target.width <= 0 || target.height <= 0
                || target.frameBufferId < 0 || target.getDepthTextureId() < 0) return;
        var shader = OmniShaders.lanternSimulation();
        if (shader == null) return;

        boolean simulation = LanternSimulation.isActive(player);
        // The camera uses the same interpolation as the rendered world, including freezing.
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        // Include diagonal visible chunks and sources just beyond their visible surfaces.
        double sourceDistance = Math.max(32.0D, mc.gameRenderer.getRenderDistance()) * Math.sqrt(2.0D)
                + LIGHT_RADIUS;
        collectLights(level, player, camera, partialTick, simulation, sourceDistance * sourceDistance);
        if (!simulation && lightCount == 0) return;
        // Use the player's interpolated eyes, independent of third-person camera placement.
        boolean revealWireframe = simulation && !isObserverLit(player.getEyePosition(partialTick).subtract(camera));

        // TextureTarget allocation also changes the viewport, clear values and texture binding.
        // Capture those as well as the ordinary draw state, even on the first frame/resize.
        try (var state = new SavedState()) {
            try {
                RenderSystem.disableScissor();
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                RenderSystem.disableColorLogicOp();
                RenderSystem.colorMask(true, true, true, true);
                ensureTarget(target, level);
                copyScene(target);
                RenderSystem.viewport(0, 0, target.width, target.height);
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableBlend();
                RenderSystem.disableCull();
                RenderSystem.setShader(() -> shader);
                draw(shader, event.getModelViewMatrix(), event.getProjectionMatrix(), camera, simulation, revealWireframe);
            } finally {
                // Also reset ShaderInstance's cached program on an interrupted draw.
                shader.clear();
            }
        }
    }

    private static void collectLights(Level level, Player localPlayer, Vec3 camera, float partialTick,
            boolean simulation, double maxDistanceSqr) {
        lightCount = 0;
        if (simulation) {
            // The common index contains loaded lantern BEs; never scan or load chunks here.
            // Recheck current block state every frame so extinguished sources disappear immediately.
            for (BlockPos pos : GreenFireLanternLights.positions(level)) {
                double x = pos.getX() + 0.5D - camera.x;
                double y = pos.getY() + 0.5D - camera.y;
                double z = pos.getZ() + 0.5D - camera.z;
                double distanceSqr = x * x + y * y + z * z;
                if (distanceSqr > maxDistanceSqr || !level.isLoaded(pos)
                        || (lightCount == MAX_LIGHTS && distanceSqr >= LIGHT_DISTANCES[MAX_LIGHTS - 1])) {
                    continue;
                }
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof GreenFireLanternBlock
                        && state.hasProperty(GreenFireLanternBlock.LIT)
                        && state.getValue(GreenFireLanternBlock.LIT)) {
                    addLight(x, y, z, distanceSqr);
                }
            }
        }
        // Add the local player explicitly, including when absent from the client's player list.
        addHeldLight(localPlayer, camera, partialTick, maxDistanceSqr);
        for (Player other : level.players()) {
            if (other != localPlayer && !other.isRemoved()) {
                addHeldLight(other, camera, partialTick, maxDistanceSqr);
            }
        }
    }

    private static void addHeldLight(Player player, Vec3 camera, float partialTick, double maxDistanceSqr) {
        if (!isLitLantern(player.getMainHandItem()) && !isLitLantern(player.getOffhandItem())) return;
        // Both hands share one pool centred one metre above the carrier's interpolated feet.
        Vec3 center = player.getPosition(partialTick).add(0.0D, 1.0D, 0.0D);
        double x = center.x - camera.x;
        double y = center.y - camera.y;
        double z = center.z - camera.z;
        double distanceSqr = x * x + y * y + z * z;
        if (distanceSqr <= maxDistanceSqr) addLight(x, y, z, distanceSqr);
    }

    private static boolean isLitLantern(ItemStack stack) {
        return !stack.isEmpty() && GreenFireLanternContent.isLantern(stack)
                && GreenFireLanternContent.isLit(stack);
    }

    private static void addLight(double x, double y, double z, double distanceSqr) {
        int index = 0;
        while (index < lightCount && LIGHT_DISTANCES[index] <= distanceSqr) index++;
        if (index >= MAX_LIGHTS) return;
        int last = Math.min(lightCount, MAX_LIGHTS - 1);
        for (int move = last; move > index; move--) {
            LIGHT_DISTANCES[move] = LIGHT_DISTANCES[move - 1];
            System.arraycopy(LIGHT_POSITIONS, (move - 1) * 3, LIGHT_POSITIONS, move * 3, 3);
        }
        LIGHT_DISTANCES[index] = distanceSqr;
        LIGHT_POSITIONS[index * 3] = (float) x;
        LIGHT_POSITIONS[index * 3 + 1] = (float) y;
        LIGHT_POSITIONS[index * 3 + 2] = (float) z;
        lightCount = Math.min(lightCount + 1, MAX_LIGHTS);
    }

    private static boolean isObserverLit(Vec3 observer) {
        for (int index = 0; index < lightCount; index++) {
            double x = observer.x - LIGHT_POSITIONS[index * 3];
            double y = observer.y - LIGHT_POSITIONS[index * 3 + 1];
            double z = observer.z - LIGHT_POSITIONS[index * 3 + 2];
            if (x * x + y * y + z * z <= LIGHT_RADIUS * LIGHT_RADIUS) return true;
        }
        return false;
    }

    private static boolean matchesTarget(RenderTarget target) {
        return scene.width == target.width && scene.height == target.height
                && scene.isStencilEnabled() == target.isStencilEnabled();
    }

    private static void ensureTarget(RenderTarget target, Level level) {
        if (scene == null) {
            scene = new TextureTarget(target.width, target.height, true, Minecraft.ON_OSX);
            sceneLevel = level;
            // Matching the depth/stencil format is required for a legal depth blit.
            if (target.isStencilEnabled()) scene.enableStencil();
            scene.setFilterMode(GL11.GL_NEAREST);
        }
    }

    private static void copyScene(RenderTarget target) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, target.width, target.height,
                0, 0, scene.width, scene.height,
                GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        target.bindWrite(false);
    }

    private static void draw(ShaderInstance shader, Matrix4f view, Matrix4f projection,
            Vec3 camera, boolean simulation, boolean revealWireframe) {
        // Invert the actual rendered projection: it includes hurt/walk bobbing and its translation.
        var inverseProjection = SingularityLensProjection.inverseProjection(projection);
        shader.setSampler("SceneColor", scene.getColorTextureId());
        shader.setSampler("SceneDepth", scene.getDepthTextureId());
        shader.safeGetUniform("SceneSize").set((float) scene.width, (float) scene.height);
        shader.safeGetUniform("InverseProjection").set(inverseProjection);
        shader.safeGetUniform("InverseView").set(new Matrix4f(view).invert());
        shader.safeGetUniform("RayOrigin").set(
                SingularityLensProjection.rayOriginInViewSpace(inverseProjection));
        shader.safeGetUniform("CameraFrac").set(
                (float) (camera.x - Math.floor(camera.x)),
                (float) (camera.y - Math.floor(camera.y)),
                (float) (camera.z - Math.floor(camera.z)));
        shader.safeGetUniform("LightPos").set(LIGHT_POSITIONS);
        shader.safeGetUniform("LightCount").set(lightCount);
        shader.safeGetUniform("LightRadius").set(LIGHT_RADIUS);
        shader.safeGetUniform("BoundaryWidth").set(BOUNDARY_WIDTH);
        shader.safeGetUniform("SimulationEnabled").set(simulation ? 1.0F : 0.0F);
        shader.safeGetUniform("RevealWireframe").set(revealWireframe ? 1.0F : 0.0F);

        // Positions are already clip-space; the pass never modifies the global view/projection.
        var mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        mesh.addVertex(-1.0F, -1.0F, 0.0F);
        mesh.addVertex(1.0F, -1.0F, 0.0F);
        mesh.addVertex(1.0F, 1.0F, 0.0F);
        mesh.addVertex(-1.0F, 1.0F, 0.0F);
        BufferUploader.drawWithShader(mesh.buildOrThrow());
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        release();
    }

    /**
     * Resource-reload hook for the main client listener's render-thread apply step.
     * Also called on shader registration/reload, target resize, level changes and logout.
     */
    public static void release() {
        RenderSystem.assertOnRenderThreadOrInit();
        lightCount = 0;
        sceneLevel = null;
        if (scene != null) {
            try (var state = new SavedState()) {
                var oldScene = scene;
                scene = null;
                oldScene.destroyBuffers();
            }
        }
    }

    /** Includes state not covered by NeoForge's ordinary GL backup. */
    private static final class SavedState implements AutoCloseable {
        private final GlStateBackup flags = new GlStateBackup();
        private final ShaderInstance shader = RenderSystem.getShader();
        private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        private final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        private final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        private final int vertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        private final int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        private final boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        private final int[] viewport = new int[4];
        private final float[] clearColor = new float[4];
        private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        private final int activeTexture = GlStateManager._getActiveTexture();
        private final int activeTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        private final int colorBinding;
        private final int depthBinding;

        private SavedState() {
            RenderSystem.backupGlState(flags);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            colorBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            depthBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._activeTexture(activeTexture);
        }

        @Override
        public void close() {
            RenderSystem.setShader(() -> shader);
            GlStateManager._glUseProgram(program);
            // Raw VAO restoration must invalidate BufferUploader's remembered immediate buffer.
            BufferUploader.invalidate();
            GlStateManager._glBindVertexArray(vertexArray);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(colorBinding);
            GlStateManager._activeTexture(GL13.GL_TEXTURE1);
            GlStateManager._bindTexture(depthBinding);
            GlStateManager._activeTexture(activeTexture);
            GlStateManager._bindTexture(activeTextureBinding);
            RenderSystem.restoreGlState(flags);
            if (stencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GlStateManager._clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            GlStateManager._clearDepth(clearDepth);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        }
    }
}
