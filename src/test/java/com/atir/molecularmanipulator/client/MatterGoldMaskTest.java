package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;
import com.atir.molecularmanipulator.client.render.ctm.MatterGoldEmissive;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class MatterGoldMaskTest {
    @Test
    void blueEmissionIsLimitedToTheFluidPortMarkings() throws Exception {
        for (String kind : new String[]{"input", "output"}) {
            String name = "matter_fabrication_fluid_" + kind;
            var texture = new ResourceLocation("molecularmanipulator:block/" + name);
            var original = ImageIO.read(Path.of("tools/textures/static", name + ".png").toFile());
            var image = ImageIO.read(Path.of("src/main/resources/assets/molecularmanipulator/textures/block", name + ".png").toFile());
            int selected = 0;
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < 16; x++) {
                int argb = image.getRGB(x, y);
                int abgr = (argb & 0xFF00FF00) | ((argb & 255) << 16) | ((argb >>> 16) & 255);
                boolean blueMarking = original.getRGB(x, y % 16) == 0xFF406282;
                assertEquals(blueMarking, MatterGoldEmissive.isEmissivePixel(texture, abgr), "Shell pixel selected at " + x + "," + y);
                if (blueMarking) selected++;
            }
            assertEquals((kind.equals("input") ? 28 : 52) * image.getHeight() / 16, selected);
        }
        int blue = 0xFF826240;
        assertFalse(MatterGoldEmissive.isEmissivePixel(new ResourceLocation("molecularmanipulator:block/matter_fabrication_casing"), blue));
        assertFalse(MatterGoldEmissive.isEmissivePixel(new ResourceLocation("another_mod:block/matter_fabrication_fluid_input"), blue));
        assertFalse(MatterGoldEmissive.isBluePixel(blue & 0xFFFFFF));
    }

    @Test
    void goldMasksAndNeutralShellsStayIdenticalAcrossEveryAnimationFrame() throws Exception {
        int textures = 0, glowingMaterials = 0;
        try (var paths = Files.list(Path.of("src/main/resources/assets/molecularmanipulator/textures/block"))) {
            for (var path : paths.filter(p -> p.getFileName().toString().matches("matter_fabrication_.*\\.png")).toList()) {
                var image = ImageIO.read(path.toFile());
                assertEquals(16, image.getWidth()); assertEquals(0, image.getHeight() % 16);
                var source = Path.of("tools/textures/static", path.getFileName().toString());
                var original = Files.exists(source) ? ImageIO.read(source.toFile()) : image;
                int selected = 0;
                for (int y=0;y<image.getHeight();y++) for(int x=0;x<16;x++) {
                    int argb=image.getRGB(x,y);
                    int abgr=(argb&0xFF00FF00)|((argb&255)<<16)|((argb>>>16)&255);
                    int originalArgb = original.getRGB(x, y % 16);
                    int originalAbgr = (originalArgb & 0xFF00FF00) | ((originalArgb & 255) << 16) | ((originalArgb >>> 16) & 255);
                    assertEquals(MatterGoldEmissive.isGoldPixel(originalAbgr), MatterGoldEmissive.isGoldPixel(abgr), path + " " + x + "," + y);
                    var texture = new ResourceLocation("molecularmanipulator:block/" + path.getFileName().toString().replace(".png", ""));
                    if (!MatterGoldEmissive.isEmissivePixel(texture, originalAbgr)) {
                        assertEquals(originalArgb, argb, "Neutral shell changed in " + path + " at " + x + "," + y);
                    }
                    assertEquals(originalArgb >>> 24, argb >>> 24, "Texture alpha changed");
                    if(MatterGoldEmissive.isGoldPixel(abgr)) selected++;
                }
                boolean neutral = path.getFileName().toString().matches("matter_fabrication_(casing|casing_top|glass|fluid_input|fluid_output)\\.png");
                if(neutral) assertEquals(0,selected,"Neutral casing and glass must not glow as a whole");
                else {assertTrue(selected>0,"Missing gold pixels in "+path);glowingMaterials++;}
                textures++;
            }
        }
        assertEquals(16,textures); assertEquals(11,glowingMaterials);
    }
}
