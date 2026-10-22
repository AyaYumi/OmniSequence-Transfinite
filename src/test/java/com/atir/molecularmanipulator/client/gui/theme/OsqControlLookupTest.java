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
            String[] icons={"q_","out_","compass_"};
            for(int i=0;i<3;i++) {
                String expectedIcon=icons[i];
                var button=layout.findControl("tool"+i);
                assertEquals(22,button.w);assertEquals(22,button.h);
                assertEquals(-26,button.x);assertEquals(30+i*26,button.y);
                var icon=layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/"+expectedIcon)
                        &&e.x>=button.x&&e.x+e.w<=button.x+button.w&&e.y>=button.y&&e.y+e.h<=button.y+button.h)
                        .findFirst().orElseThrow();
                assertEquals(12,icon.w);assertEquals(12,icon.h);
                assertEquals(button.x+button.w/2,icon.x+icon.w/2);
                assertEquals(button.y+button.h/2,icon.y+icon.h/2);
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
            assertEquals(200,layout.height);assertEquals(layout.height,layout.find("window").h);
        }
    }
    @Test void sourceTabsHaveTheirOwnReplaceableBackgroundsAndFullCatalystWidth() throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        for(String root:new String[]{"src/main/resources","resourcepacks/OmniSequence Dark"}) {
            var layout=constructor.newInstance(JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/molecularmanipulator/osq_light/layout/fab_assembly_input.json"))).getAsJsonObject());
            var tabs=layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/tab_")).toList();
            assertEquals(4,tabs.size());
            var generated=new OsqLayout.Entry();generated.x=tabs.getFirst().x;generated.y=62;
            generated.w=26.38f;generated.h=15;
            assertTrue(OsqLayout.sameRect(tabs.getFirst(),generated));
            assertFalse(OsqLayout.sameRect(tabs.get(1),generated));
            assertEquals(32.56f,tabs.getLast().w,.01f,"Catalyst tab must not be truncated to the two-character tab width");
        }
    }

    @Test void quantumFrequencyAndStatusUseSeparateLinesInBothThemes() throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        for(String root:new String[]{"src/main/resources","resourcepacks/OmniSequence Dark"}) {
            var layout=constructor.newInstance(JsonParser.parseString(Files.readString(Path.of(root,
                    "assets/molecularmanipulator/osq_light/layout/fab_overview.json"))).getAsJsonObject());
            var frequency=layout.draw.stream().filter(e->"text".equals(e.type)&&e.x==262.02f).findFirst().orElseThrow();
            var state=layout.findText("gui.molecularmanipulator.fab.s026");
            assertTrue(frequency.w>=120,"A full unsigned 64-bit hex value needs room");
            assertEquals(188,frequency.baseline);
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
            assertEquals(226, button.y);
            assertNotSame(layout.find("order"), button);
            assertThrows(IllegalArgumentException.class, () -> layout.findControl("window"));
        }
    }
}
