package com.atir.molecularmanipulator.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Resource-pack regressions independent of a Minecraft client or GPU. */
class OsqLayoutResourcesTest {
    private static final Path LIGHT=Path.of("src/main/resources/assets/molecularmanipulator");
    private static final Path DARK=Path.of("resourcepacks/OmniSequence Dark/assets/molecularmanipulator");
    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    @Test void bothThemesContainTheSameTwentyLayoutsAndStableSpriteIds() throws Exception {
        int count=0;
        try(var paths=Files.list(LIGHT.resolve("osq_light/layout"))) {
            for(var lightPath:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                count++;
                var light=json(lightPath);var dark=json(DARK.resolve("osq_light/layout").resolve(lightPath.getFileName()));
                assertEquals(light.get("screen"),dark.get("screen"));
                assertEquals(light.get("size"),dark.get("size"));
                for(var root:new Path[]{LIGHT,DARK}) {
                    var layout=json(root.resolve("osq_light/layout").resolve(lightPath.getFileName()));
                    for(var element:layout.getAsJsonArray("draw")) {
                        var entry=element.getAsJsonObject();
                        if(!entry.has("sprite")||entry.get("sprite").isJsonNull())continue;
                        String sprite=entry.get("sprite").getAsString();
                        assertTrue(sprite.startsWith("molecularmanipulator:osq_light/"),sprite);
                        assertTrue(Files.isRegularFile(root.resolve("textures/gui/sprites/"+sprite.split(":",2)[1]+".png")),sprite);
                        if(entry.has("pad"))assertEquals(4,entry.getAsJsonArray("pad").size());
                    }
                }
            }
        }
        assertEquals(20,count);
    }
    @Test void allLayoutTranslationsAndFontsResolve() throws Exception {
        var zh=json(LIGHT.resolve("lang/zh_cn.json"));var en=json(LIGHT.resolve("lang/en_us.json"));
        try(var paths=Files.list(LIGHT.resolve("osq_light/layout"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                for(var element:json(path).getAsJsonArray("draw")) {
                    var entry=element.getAsJsonObject();
                    if(entry.has("lang")) {
                        String key=entry.get("lang").getAsString();
                        assertTrue(zh.has(key),key);assertTrue(en.has(key),key);
                    }
                    if(entry.has("font_id"))assertTrue(Files.isRegularFile(LIGHT.resolve("font/"
                            +entry.get("font_id").getAsString().split(":",2)[1]+".json")));
                }
            }
        }
    }
    @Test void researchUnlockTranslationsKeepUnicodeAndFormatArguments() throws Exception {
        var zh=json(LIGHT.resolve("lang/zh_cn.json"));var en=json(LIGHT.resolve("lang/en_us.json"));
        String prefix="gui.molecularmanipulator.research.";
        for(String key:new String[]{"show_unlocks","show_materials","no_unlocks","unlock_recipe_summary",
                "recipe_inputs","recipe_outputs","recipe_catalysts","unlock_on_completion","recipe_unavailable"}) {
            assertTrue(zh.has(prefix+key),key);assertTrue(en.has(prefix+key),key);
            assertFalse(zh.get(prefix+key).getAsString().contains("?"),key);
            assertFalse(en.get(prefix+key).getAsString().contains("?"),key);
        }
        assertEquals("解锁配方 ×%s",zh.get(prefix+"show_unlocks").getAsString());
        assertEquals("返回材料",zh.get(prefix+"show_materials").getAsString());
        assertEquals("%s t · %s AE/t",zh.get(prefix+"unlock_recipe_summary").getAsString());
        assertEquals("Unlocks ×%s",en.get(prefix+"show_unlocks").getAsString());
    }
    @Test void darkPackActuallyOverridesWindowPadding() throws Exception {
        var light=json(LIGHT.resolve("osq_light/layout/omni_core.json")).getAsJsonArray("draw").get(0).getAsJsonObject();
        var dark=json(DARK.resolve("osq_light/layout/omni_core.json")).getAsJsonArray("draw").get(0).getAsJsonObject();
        assertEquals(1,light.getAsJsonArray("pad").get(3).getAsInt());
        assertEquals(12,dark.getAsJsonArray("pad").get(3).getAsInt());
    }

    @Test void autoCrafterHasNoOutputBufferOrPaginationAndInventoryClosesTheGap() throws Exception {
        for(var root:new Path[]{LIGHT,DARK}) {
            var layout=json(root.resolve("osq_light/layout/seq_autocrafter.json"));
            assertEquals(200,layout.getAsJsonArray("size").get(1).getAsInt());
            for(var element:layout.getAsJsonArray("draw")) {
                var entry=element.getAsJsonObject();
                if(entry.has("id"))assertFalse(java.util.Set.of("buffer","buf","prev","next")
                        .contains(entry.get("id").getAsString()));
                if(entry.has("lang"))assertNotEquals("gui.molecularmanipulator.fabrication.buffer.output",entry.get("lang").getAsString());
                if(entry.has("k")&&"card".equals(entry.get("k").getAsString())
                        &&"inv".equals(entry.get("id").getAsString()))
                    assertEquals(104,entry.getAsJsonArray("rect").get(1).getAsInt());
            }
        }
    }

    @Test void allOutputSelectorsHaveSixIconSlotsAndPortToolbarMatchesAutoCrafter() throws Exception {
        for(var root:new Path[]{LIGHT,DARK})for(var name:new String[]{"seq_autocrafter","fab_port_item","fab_port_fluid"}) {
            var layout=json(root.resolve("osq_light/layout/"+name+".json"));
            int directions=0,toolbar=0,popups=0;
            for(var element:layout.getAsJsonArray("draw")) {
                var entry=element.getAsJsonObject();
                if(entry.has("lang"))assertFalse(entry.get("lang").getAsString()
                        .startsWith("gui.molecularmanipulator.fabrication.port.side."),"Labels must not obscure neighbor icons");
                if(!entry.has("k")||!entry.has("id"))continue;
                String id=entry.get("id").getAsString();
                if("pop".equals(entry.get("k").getAsString()))popups++;
                if(java.util.Set.of("d北","d西","d东","d南","d上","d下").contains(id)) {
                    directions++;
                    assertEquals(22,entry.getAsJsonArray("rect").get(2).getAsInt());
                    assertEquals(22,entry.getAsJsonArray("rect").get(3).getAsInt());
                }
                if(id.startsWith("tool")) {
                    toolbar++;
                    var r=entry.getAsJsonArray("rect");
                    assertEquals(-26,r.get(0).getAsInt());assertEquals(22,r.get(2).getAsInt());assertEquals(22,r.get(3).getAsInt());
                }
            }
            assertEquals(6,directions,name);assertEquals(1,popups,name);
            assertEquals(name.equals("seq_autocrafter")?3:2,toolbar,name);
        }
    }

    @Test void processedResourcesEmbedTheEntireDarkPack() throws Exception {
        var root="/resourcepacks/osq_dark/";
        try(var metadata=getClass().getResourceAsStream(root+"pack.mcmeta")) {
            assertNotNull(metadata,"The mod must bundle pack.mcmeta");
            var pack=JsonParser.parseString(new String(metadata.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(34,pack.getAsJsonObject("pack").get("pack_format").getAsInt());
        }
        try(var paths=Files.walk(DARK)) {
            for(var file:paths.filter(Files::isRegularFile).toList()) {
                String relative=DARK.relativize(file).toString().replace('\\','/');
                try(var resource=getClass().getResourceAsStream(root+"assets/molecularmanipulator/"+relative)) {
                    assertNotNull(resource,relative);
                    assertArrayEquals(Files.readAllBytes(file),resource.readAllBytes(),relative);
                }
            }
        }
    }
}
