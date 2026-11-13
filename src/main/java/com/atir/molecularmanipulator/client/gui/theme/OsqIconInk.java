package com.atir.molecularmanipulator.client.gui.theme;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Normalized visible center; transparent icon margins must not shift a button label. */
public final class OsqIconInk {
    public record Center(float x, float y, float aspect, float width) {
        public Center(float x, float y) { this(x, y, 1, 1); }
        public Center(float x, float y, float aspect) { this(x, y, aspect, 1); }
        public float fitWidth(float width, float height) { return Math.min(width, height * aspect); }
        public float fitHeight(float width, float height) { return fitWidth(width, height) / aspect; }
    }
    private static final Map<String, Center> CENTERS = new HashMap<>();
    private static long generation = Long.MIN_VALUE;
    private OsqIconInk() { }

    public static Center center(String sprite) {
        var id = new ResourceLocation(sprite);
        return center(sprite, new ResourceLocation(id.getNamespace(),
                "textures/gui/sprites/" + id.getPath() + ".png"), null);
    }

    /** Native AE icons retain their atlas pixels, aspect and reload behavior. */
    public static Center center(appeng.client.gui.Icon icon) {
        return center("ae-icon:" + icon.name(), appeng.client.gui.Icon.TEXTURE, icon);
    }

    private static Center center(String key, ResourceLocation file, appeng.client.gui.Icon icon) {
        long current = OsqFonts.resourceGeneration();
        if (generation != current) { CENTERS.clear(); generation = current; }
        return CENTERS.computeIfAbsent(key, ignored -> {
            try (var stream = Minecraft.getInstance().getResourceManager().open(file)) {
                var image = ImageIO.read(stream);
                if (icon != null) {
                    float sx = image.getWidth() / (float)appeng.client.gui.Icon.TEXTURE_WIDTH;
                    float sy = image.getHeight() / (float)appeng.client.gui.Icon.TEXTURE_HEIGHT;
                    image = image.getSubimage(Math.round(icon.x*sx), Math.round(icon.y*sy),
                            Math.round(icon.width*sx), Math.round(icon.height*sy));
                }
                return measure(image);
            } catch (java.io.IOException exception) {
                com.mojang.logging.LogUtils.getLogger().warn("OSQ cannot measure icon {}", key, exception);
                return new Center(.5f, .5f);
            }
        });
    }

    static Center measure(BufferedImage image) {
        int left = image.getWidth(), top = image.getHeight(), right = 0, bottom = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if ((image.getRGB(x, y) >>> 24) == 0) continue;
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x + 1); bottom = Math.max(bottom, y + 1);
        }
        float aspect = image.getWidth() / (float)image.getHeight();
        return right == 0 ? new Center(.5f, .5f, aspect)
                : new Center((left + right) / (2f * image.getWidth()), (top + bottom) / (2f * image.getHeight()),
                        aspect, (right - left) / (float)image.getWidth());
    }
}
