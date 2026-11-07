package com.atir.molecularmanipulator.client.gui.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqControlLookupTest {
    @Test void autoCrafterSideButtonsAreLargeAndMatchTheirIcons() throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        for(String root:new String[]{"src/main/resources","resourcepacks/OmniSequence Dark"}) {
            var layout=constructor.newInstance(JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/molecularmanipulator/osq_light/layout/seq_autocrafter.json"))).getAsJsonObject());
            String[] icons={"native_HELP","out_","compass_"};
            for(int i=0;i<3;i++) {
                String expectedIcon=icons[i];
                var button=layout.findControl("tool"+i);
                assertEquals(22,button.w);assertEquals(22,button.h);
                assertEquals(-26,button.x);assertEquals(30+i*26,button.y);
                if(i==0){assertEquals("HELP",button.aeIcon);continue;}
                var icon=layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/"+expectedIcon)
                        &&e.x>=button.x&&e.x+e.w<=button.x+button.w&&e.y>=button.y&&e.y+e.h<=button.y+button.h)
                        .findFirst().orElseThrow();
                assertEquals(16,icon.w);assertEquals(16,icon.h);
                assertTrue(layout.owns(button,icon));
                var image=javax.imageio.ImageIO.read(Path.of(root,"assets/molecularmanipulator/textures/gui/sprites/"
                        +icon.sprite.split(":",2)[1]+".png").toFile());
                var ink=OsqIconInk.measure(image);
                assertEquals(button.x+button.w/2,icon.x+icon.w*ink.x(),.01f);
                assertEquals(button.y+10,icon.y+icon.h*ink.y(),.01f);
            }
            var dirs=layout.find("dirs");
            assertEquals(layout.find("slots").x,dirs.x);assertEquals(layout.find("slots").y,dirs.y);
            assertTrue(dirs.x>=0&&dirs.x+dirs.w<=layout.width,"Direction picker must never extend offscreen");
        }
    }

    @Test void autoCrafterInventoryFollowsPatternsWithoutAnOutputBuffer() throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        for(String root:new String[]{"src/main/resources","resourcepacks/OmniSequence Dark"}) {
            var layout=constructor.newInstance(JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/molecularmanipulator/osq_light/layout/seq_autocrafter.json"))).getAsJsonObject());
            assertThrows(IllegalArgumentException.class,()->layout.findControl("prev"));
            assertThrows(IllegalArgumentException.class,()->layout.findControl("next"));
            assertFalse(layout.draw.stream().anyMatch(e->"buffer".equals(e.id)||"buf".equals(e.id)
                    ||"gui.molecularmanipulator.fabrication.buffer.output".equals(e.lang)));
            var inventory=layout.draw.stream().filter(e->"inv".equals(e.id)&&"card".equals(e.kind)).findFirst().orElseThrow();
            var patterns=layout.find("slots");
            assertEquals(patterns.y+patterns.h+6,inventory.y);
            assertEquals(200,layout.height);
            var window=layout.find("window");
            assertTrue(window.y<=0&&window.y+window.h>=layout.height);
        }
    }
    @Test void sourceTabsHaveTheirOwnReplaceableBackgroundsAndFullCatalystWidth() throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        for(String root:new String[]{"src/main/resources","resourcepacks/OmniSequence Dark"}) {
            var layout=constructor.newInstance(JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/molecularmanipulator/osq_light/layout/fab_assembly_input.json"))).getAsJsonObject());
            var tabs=layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/tab_")).toList();
            assertEquals(4,tabs.size());
            var generated=new OsqLayout.Entry();generated.x=8;generated.y=69;
            generated.w=42;generated.h=22;
            assertSame(tabs.getFirst(),layout.findControl("tab0"));
            assertTrue(OsqLayout.sameRect(tabs.getFirst(),generated));
            assertFalse(OsqLayout.sameRect(tabs.get(1),generated));
            assertTrue(tabs.getLast().w>=44,"Catalyst tab keeps room for both localized labels and tab padding");
            assertTrue(tabs.getLast().x+tabs.getLast().w<=182);
            for(int i=1;i<tabs.size();i++)assertTrue(tabs.get(i).x-tabs.get(i-1).x-tabs.get(i-1).w>=4);
        }
    }

    @Test void quantumFrequencyAndStatusUseSeparateLinesInBothThemes() throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        for(String root:new String[]{"src/main/resources","resourcepacks/OmniSequence Dark"}) {
            var layout=constructor.newInstance(JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/molecularmanipulator/osq_light/layout/fab_overview.json"))).getAsJsonObject());
            var frequency=layout.draw.stream().filter(e->"text".equals(e.type)&&e.x==66.02f).findFirst().orElseThrow();
            var state=layout.findText("gui.molecularmanipulator.fab.s026");
            assertTrue(frequency.w>=120,"A full unsigned 64-bit hex value needs room");
            assertEquals(168,frequency.baseline);
            assertTrue(state.baseline-frequency.baseline>=20);
            assertTrue(state.w>=170);
            var card=layout.find("quantum");
            assertTrue(state.baseline<card.y+card.h);
        }
    }
    @Test void repeatedPanelIdDoesNotStealTheResearchOrderButton() throws Exception {
        var constructor = OsqLayout.class.getDeclaredConstructor(JsonObject.class);
        constructor.setAccessible(true);
        for (String root : new String[]{"src/main/resources", "resourcepacks/OmniSequence Dark"}) {
            var json = JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/molecularmanipulator/osq_light/layout/fab_research.json"))).getAsJsonObject();
            var layout = constructor.newInstance(json);
            assertNotEquals("ctl", layout.find("order").kind);
            var button = layout.findControl("order");
            assertEquals("ctl", button.kind);
            assertEquals(300, button.x);
            assertEquals(289, button.y);
            assertEquals(22, button.h);
            assertNotSame(layout.find("order"), button);
            assertThrows(IllegalArgumentException.class, () -> layout.findControl("window"));
        }
    }
}
