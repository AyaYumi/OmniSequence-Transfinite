package com.atir.molecularmanipulator.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Resource-pack regressions independent of a Minecraft client or GPU. */
class OsqLayoutResourcesTest {
    private static final Path LIGHT=Path.of("src/main/resources/assets/molecularmanipulator");
    private static final List<String> RETIRED_COMPONENT_LEGEND_KEYS=List.of(
            "gui.molecularmanipulator.fab.s015", "gui.molecularmanipulator.fab.s016",
            "gui.molecularmanipulator.seq.s013", "gui.molecularmanipulator.seq.s014",
            "gui.molecularmanipulator.seq.s015", "gui.molecularmanipulator.seq.s016",
            "gui.molecularmanipulator.sing.s021");
    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    @Test void allTwentyFiveLayoutsKeepStableScreenAndSpriteIds() throws Exception {
        int count=0;
        try(var paths=Files.list(LIGHT.resolve("osq_light/layout"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                count++;
                var layout=json(path);
                assertEquals(path.getFileName().toString().replace(".json",""),layout.get("screen").getAsString());
                var size=layout.getAsJsonArray("size");
                assertEquals(2,size.size());
                assertTrue(size.get(0).getAsInt()>0&&size.get(1).getAsInt()>0,path.toString());
                for(var element:layout.getAsJsonArray("draw")) {
                    var entry=element.getAsJsonObject();
                    if(!entry.has("sprite")||entry.get("sprite").isJsonNull())continue;
                    String sprite=entry.get("sprite").getAsString();
                    assertTrue(sprite.startsWith("molecularmanipulator:osq_light/"),sprite);
                    assertTrue(Files.isRegularFile(LIGHT.resolve("textures/gui/sprites/"+sprite.split(":",2)[1]+".png")),sprite);
                    if(entry.has("pad"))assertEquals(4,entry.getAsJsonArray("pad").size());
                }
            }
        }
        assertEquals(25,count);
    }
    @Test void allLayoutTranslationsResolveAndTextUsesTheDefaultMinecraftFont() throws Exception {
        var zh=json(LIGHT.resolve("lang/zh_cn.json"));var en=json(LIGHT.resolve("lang/en_us.json"));
        try(var paths=Files.list(LIGHT.resolve("osq_light/layout"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                for(var element:json(path).getAsJsonArray("draw")) {
                    var entry=element.getAsJsonObject();
                    if(entry.has("lang")) {
                        String key=entry.get("lang").getAsString();
                        assertTrue(zh.has(key),path+" zh_cn "+key);assertTrue(en.has(key),path+" en_us "+key);
                    }
                    if("text".equals(entry.get("type").getAsString())) {
                        assertTrue(entry.has("font_id"),path+": text must declare the default font");
                        assertTrue(entry.get("size").getAsFloat()>0,path+": retain the authored text size");
                    }
                    if(entry.has("font_id"))assertEquals("minecraft:default",entry.get("font_id").getAsString(),path.toString());
                }
            }
        }
    }

    @Test void retiredComponentLegendTranslationsAndSpritesHaveNoLayoutConsumers() throws Exception {
        for(String locale:new String[]{"en_us","zh_cn"}) {
            var translations=json(LIGHT.resolve("lang/"+locale+".json"));
            for(String key:RETIRED_COMPONENT_LEGEND_KEYS)assertFalse(translations.has(key),locale+" "+key);
        }
        try(var paths=Files.list(LIGHT.resolve("osq_light/layout"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                String source=Files.readString(path);
                assertFalse(source.contains("/legend_"),path+": no decorative legend sprites");
                for(String key:RETIRED_COMPONENT_LEGEND_KEYS)assertFalse(source.contains(key),path+" "+key);
            }
        }
        for(var family:Map.of("fab",List.of("core","stab"),"seq",List.of("core","gold","stab"),
                "sing",List.of("core","gold","stab")).entrySet())for(String icon:family.getValue()) {
            var texture=LIGHT.resolve("textures/gui/sprites/osq_light/"+family.getKey()+"/legend_"+icon+".png");
            assertFalse(Files.exists(texture),"Delete only retired GUI legend artwork: "+texture);
        }
    }

    @Test void multiblockOverviewsKeepUsefulCardsStructureControlsSlotsAndTelemetryWithoutLegendDraws() throws Exception {
        var ranges=Map.of("seq_center_overview",new int[]{14,25},"fab_overview",new int[]{7,12},
                "sing_overview",new int[]{2,10});
        for(var range:ranges.entrySet()) {
            String name=range.getKey();var path=LIGHT.resolve("osq_light/layout/"+name+".json");var layout=json(path);
            var draw=layout.getAsJsonArray("draw").asList().stream().map(e->e.getAsJsonObject()).toList();
            assertTrue(draw.stream().noneMatch(e->e.has("z")&&e.get("z").getAsDouble()>=range.getValue()[0]
                    &&e.get("z").getAsDouble()<=range.getValue()[1]),path+": remove whole legend entries, including demo values");
            String[] controls=name.equals("seq_center_overview")
                    ?new String[]{"projection","build","dismantle","search","prev","next","pattern_upload","migrate_patterns"}
                    :new String[]{"build","preview","refresh","dismantle"};
            for(String section:new String[]{"draw","boxes"}) {
                var entries=layout.getAsJsonArray(section).asList().stream().map(e->e.getAsJsonObject()).toList();
                for(var card:Map.of("hero","card","count","blk").entrySet())
                    assertEquals(1L,entries.stream().filter(e->e.has("k")&&e.has("id")
                            &&card.getValue().equals(e.get("k").getAsString())
                            &&card.getKey().equals(e.get("id").getAsString())).count(),path+" "+section+" "+card.getKey());
                for(String id:controls)assertEquals(1L,entries.stream().filter(e->e.has("k")&&e.has("id")
                        &&"ctl".equals(e.get("k").getAsString())&&id.equals(e.get("id").getAsString())).count(),
                        path+" "+section+" retains "+id);
                for(var slots:Map.of("inv",36,"qes",1,"pat",name.equals("seq_center_overview")?36:0).entrySet())
                    assertEquals(slots.getValue().longValue(),entries.stream().filter(e->e.has("k")&&e.has("id")
                            &&"slot".equals(e.get("k").getAsString())&&slots.getKey().equals(e.get("id").getAsString())).count(),
                            path+" "+section+" retains "+slots.getKey()+" slots");
            }
            String[] labels=switch(name) {
                case "seq_center_overview"->new String[]{"formed","overview_patterns","overview_auto_craft","overview_pipeline","overview_pending"};
                case "fab_overview"->new String[]{"fab.s017","fabrication.state.running","singularity.network_online",
                        "fab.s018","fab.s019","fab.s020","fab.s021","fab.s022","fab.s023"};
                default->new String[]{"fab.s017","formed","singularity.network_online","singularity.status.running",
                        "sing.s012","sing.s022","sing.s023","sing.s024"};
            };
            for(String key:labels)assertTrue(draw.stream().anyMatch(e->e.has("lang")
                    &&("gui.molecularmanipulator."+key).equals(e.get("lang").getAsString())),path+" retains "+key);
            float[][] bindings=switch(name) {
                case "seq_center_overview"->new float[][]{{208.5f,87},{217.63f,87},{249.58f,87},{200.5f,181},
                        {211.31f,181},{254,177},{301.09f,177},{352,177},{65.39f,179.5f}};
                case "fab_overview"->new float[][]{{16.5f,116},{29.03f,116},{59.23f,116},{69.34f,116},
                        {327.36f,63},{213,86},{334,86},{212.5f,129},{233.95f,129},{367.59f,126},
                        {66.02f,168},{226.36f,188},{238.27f,188}};
                default->new float[][]{{24.5f,120},{43.09f,120},{75.02f,120},{87.59f,120},
                        {172.5f,166.5f},{225.5f,166.5f},{278.5f,166.5f},{66.02f,187.5f}};
            };
            for(float[] address:bindings)assertEquals(1L,draw.stream().filter(e->e.has("dynamic")&&e.get("dynamic").getAsBoolean()
                    &&e.get("x").getAsFloat()==address[0]&&e.get("baseline").getAsFloat()==address[1]).count(),
                    path+": live telemetry binding at "+address[0]+", "+address[1]);
        }
    }

    @Test void researchPagesKeepActualMaterialCostsAndOrderControls() throws Exception {
        for(String name:new String[]{"fab_research","fab_research_detail"}) {
            var path=LIGHT.resolve("osq_light/layout/"+name+".json");
            var entries=json(path).getAsJsonArray("draw").asList().stream().map(e->e.getAsJsonObject()).toList();
            String slot=name.equals("fab_research")?"mat":"ord";
            assertEquals(3L,entries.stream().filter(e->e.has("k")&&e.has("id")&&"slot".equals(e.get("k").getAsString())
                    &&slot.equals(e.get("id").getAsString())).count(),path+": keep real research material slots");
            for(String id:new String[]{"order","pause","stop"})assertTrue(entries.stream().anyMatch(e->e.has("k")&&e.has("id")
                    &&"ctl".equals(e.get("k").getAsString())&&id.equals(e.get("id").getAsString())),path+" "+id);
            for(String key:new String[]{"research.next_quantity_title","fab.s059"})assertTrue(entries.stream().anyMatch(e->e.has("lang")
                    &&("gui.molecularmanipulator."+key).equals(e.get("lang").getAsString())),path+" "+key);
        }
    }

    @Test void centerPagesUseLivePatternTransfersInsteadOfRetiredCoreAssets() throws Exception {
        for(String name:new String[]{"seq_center_overview","seq_center_autocraft","seq_center_colors"}) {
            var path=LIGHT.resolve("osq_light/layout/"+name+".json");var layout=json(path);
            assertFalse(Files.readString(path).contains("quantum_upload_core"),"Remove retired keys, do not restore or omit them");
            for(String section:new String[]{"draw","boxes"})for(var element:layout.getAsJsonArray(section)) {
                var entry=element.getAsJsonObject();
                if(entry.has("id"))assertNotEquals("core",entry.get("id").getAsString(),path+" "+section);
            }
            var entries=layout.getAsJsonArray("draw").asList().stream().map(e->e.getAsJsonObject()).toList();
            for(String id:new String[]{"pattern_upload","migrate_patterns"}) {
                var controls=entries.stream().filter(e->e.has("id")&&id.equals(e.get("id").getAsString())).toList();
                assertEquals(1,controls.size(),path+" "+id);var control=controls.getFirst();
                assertEquals("ctl",control.get("k").getAsString());
                assertTrue(control.getAsJsonArray("rect").get(3).getAsFloat()>=22,"Keep native OSQ hit-target height");
                var labels=entries.stream().filter(e->e.has("owner")&&id.equals(e.get("owner").getAsString())&&"text".equals(e.get("type").getAsString())).toList();
                assertEquals(1,labels.size(),path+" "+id+" owns exactly one label");
                assertEquals(id.equals("pattern_upload")?"gui.molecularmanipulator.pattern_upload.off"
                        :"gui.molecularmanipulator.pattern_migration.button",labels.getFirst().get("lang").getAsString());
            }
            for(var count:java.util.Map.of("pat",36,"inv",36,"qes",1).entrySet())
                assertEquals(count.getValue().longValue(),entries.stream().filter(e->e.has("k")&&"slot".equals(e.get("k").getAsString())
                        &&count.getKey().equals(e.get("id").getAsString())).count(),path+" retains native "+count.getKey()+" slots");
        }
        var screen=Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/MolecularCenterScreen.java"));
        assertTrue(screen.contains("initPatternTransferControls();"));
        assertTrue(screen.contains("ui.button(\"pattern_upload\""));assertTrue(screen.contains("ui.button(\"migrate_patterns\""));
        assertTrue(screen.contains("menu.requestSetPatternUpload(!menu.automaticPatternUpload)"));
        assertTrue(screen.contains("menu::requestMigratePatterns"));
        assertFalse(screen.contains("quantum_upload_core"),"No omission-based workaround may hide stale resources");
        assertFalse(screen.contains("getUploadCoreSlot"),"The screen must not recreate a retired functional slot");
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
    @Test void omniOverviewsRemoveThePhaseHeadingAndPlaceholderWithoutRemovingLiveTelemetry() throws Exception {
        String phase="gui.molecularmanipulator.omni.s002";
        for(String locale:List.of("en_us","zh_cn"))assertFalse(json(LIGHT.resolve("lang/"+locale+".json")).has(phase));
        for(String name:List.of("omni_core","omni_core_legacy")){
            var layout=json(LIGHT.resolve("osq_light/layout/"+name+".json"));
            var entries=layout.getAsJsonArray("draw").asList().stream().map(e->e.getAsJsonObject()).toList();
            assertTrue(entries.stream().noneMatch(e->e.has("lang")&&phase.equals(e.get("lang").getAsString())));
            assertTrue(entries.stream().noneMatch(e->e.has("x")&&e.get("x").getAsFloat()==177.53f
                    &&e.has("baseline")&&e.get("baseline").getAsFloat()==95.5f));
            for(String key:List.of("omni.s001","omni.formed","omni.online","omni.storage","omni.parallel",
                    "omni.s003","omni.active_jobs","omni.virtual_lanes","omni.calculating","omni.calculation_stats"))
                assertTrue(entries.stream().anyMatch(e->e.has("lang")&&("gui.molecularmanipulator."+key).equals(e.get("lang").getAsString())),name+" retains "+key);
            for(var slot:Map.of("inv",36,"qes",1).entrySet())assertEquals(slot.getValue().longValue(),entries.stream()
                    .filter(e->e.has("k")&&"slot".equals(e.get("k").getAsString())&&slot.getKey().equals(e.get("id").getAsString())).count());
        }
    }

    @Test void omniWindowKeepsItsUnpaddedBakedGlass() throws Exception {
        var window=json(LIGHT.resolve("osq_light/layout/omni_core.json")).getAsJsonArray("draw").get(0).getAsJsonObject();
        assertEquals(0,window.getAsJsonArray("pad").get(3).getAsInt());
        assertTrue(window.get("sprite").getAsString().contains("/glass/"));
        assertEquals("fixed",window.get("slicing").getAsString());
    }

    @Test void omniComputeCardsKeepTheAuthoredFeatureMaterialAndLightForeground() throws Exception {
        for(String name:new String[]{"omni_core","omni_core_legacy"}) {
            var entries=json(LIGHT.resolve("osq_light/layout/"+name+".json")).getAsJsonArray("draw")
                    .asList().stream().map(e->e.getAsJsonObject()).toList();
            var card=entries.stream().filter(e->e.has("id")&&"compute".equals(e.get("id").getAsString())).findFirst().orElseThrow();
            assertEquals(JsonParser.parseString("[239,103,142,100]"),card.get("rect"),"Do not move or resize the compute card");
            var count=entries.stream().filter(e->e.has("id")&&"count".equals(e.get("id").getAsString())).findFirst().orElseThrow();
            var neutral=javax.imageio.ImageIO.read(LIGHT.resolve("textures/gui/sprites/"
                    +count.get("sprite").getAsString().split(":",2)[1]+".png").toFile());
            int material=neutral.getRGB(neutral.getWidth()/2,neutral.getHeight()/2);
            // The runtime count surface and authored compute card use the same feature material.
            assertEquals(0xFF55536C,material);
            var image=javax.imageio.ImageIO.read(LIGHT.resolve("textures/gui/sprites/"
                    +card.get("sprite").getAsString().split(":",2)[1]+".png").toFile());
            assertEquals("fixed",card.get("slicing").getAsString());
            assertEquals(592,image.getWidth());assertEquals(424,image.getHeight());
            assertEquals(material,image.getRGB(image.getWidth()/2,image.getHeight()*7/10),
                    name+" must use the existing feature material, not raised-control gray");
            for(int x:new int[]{252,314}) {
                var number=entries.stream().filter(e->"text".equals(e.get("type").getAsString())
                        &&e.get("x").getAsFloat()==x&&e.get("baseline").getAsFloat()==147).findFirst().orElseThrow();
                assertTrue(number.get("dynamic").getAsBoolean(),"Keep live jobs and CPU lanes at their binding addresses");
                assertEquals("#FFF2F2F2",number.get("color").getAsString());
            }
            for(String key:new String[]{"s003","active_jobs","virtual_lanes","material_calculation","calculating","calculation_stats"}) {
                var text=entries.stream().filter(e->e.has("lang")&&("gui.molecularmanipulator.omni."+key)
                        .equals(e.get("lang").getAsString())).findFirst().orElseThrow();
                String expected=key.equals("calculating")?"#FFF2F2F2":"#B8F2F2F2";
                assertEquals(expected,text.get("color").getAsString(),name+" "+key);
            }
        }
    }

    @Test void autoCrafterHasNoOutputBufferOrPaginationAndInventoryClosesTheGap() throws Exception {
        var layout=json(LIGHT.resolve("osq_light/layout/seq_autocrafter.json"));
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

    @Test void allOutputSelectorsHaveSixIconSlotsAndPortToolbarMatchesAutoCrafter() throws Exception {
        for(var name:new String[]{"seq_autocrafter","fab_port_item","fab_port_fluid"}) {
            var layout=json(LIGHT.resolve("osq_light/layout/"+name+".json"));
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

    @Test void outputSelectorsUseOnlyTheSixRequestedCrossCellsAndRetainNativeSlots() throws Exception {
        var cells=Map.of("d上",new int[]{1,0},"d西",new int[]{0,1},"d北",new int[]{1,1},
                "d东",new int[]{2,1},"d下",new int[]{1,2},"d南",new int[]{2,2});
        for(String name:List.of("seq_autocrafter","fab_port_item","fab_port_fluid")) {
            var path=LIGHT.resolve("osq_light/layout/"+name+".json");var layout=json(path);
            int top=name.equals("seq_autocrafter")?38:61;
            for(String section:List.of("draw","boxes")) {
                var entries=layout.getAsJsonArray(section).asList().stream().map(e->e.getAsJsonObject()).toList();
                for(var cell:cells.entrySet()) {
                    var matches=entries.stream().filter(e->e.has("id")&&cell.getKey().equals(e.get("id").getAsString())).toList();
                    assertEquals(1,matches.size(),path+" "+section+" "+cell.getKey());
                    var entry=matches.getFirst();int x,y,w,h;
                    if(section.equals("draw")) {
                        var rect=entry.getAsJsonArray("rect");x=rect.get(0).getAsInt();y=rect.get(1).getAsInt();
                        w=rect.get(2).getAsInt();h=rect.get(3).getAsInt();
                    } else {
                        var rect=entry.getAsJsonObject("r");x=rect.get("x").getAsInt();y=rect.get("y").getAsInt();
                        w=rect.get("w").getAsInt();h=rect.get("h").getAsInt();
                    }
                    assertEquals(62+22*cell.getValue()[0],x,path+" "+cell.getKey());
                    assertEquals(top+22*cell.getValue()[1],y,path+" "+cell.getKey());
                    assertEquals(22,w);assertEquals(22,h);
                }
                assertEquals(36L,entries.stream().filter(e->e.has("id")&&"inv".equals(e.get("id").getAsString())
                        &&"slot".equals(e.get("k").getAsString())).count(),path+" retains the native inventory");
            }
            var draw=layout.getAsJsonArray("draw").asList().stream().map(e->e.getAsJsonObject()).toList();
            assertFalse(draw.stream().anyMatch(e->e.has("lang")&&"gui.molecularmanipulator.seq.s002".equals(e.get("lang").getAsString())),
                    "Do not advertise a world-coordinate layout for block-relative controls");
            var inventory=draw.stream().filter(e->e.has("id")&&"inv".equals(e.get("id").getAsString())
                    &&"card".equals(e.get("k").getAsString())).findFirst().orElseThrow();
            assertEquals(name.equals("seq_autocrafter")?104:140,inventory.getAsJsonArray("rect").get(1).getAsInt());
        }
    }

    @Test void outputDirectionToolbarsUseTheSameNativeAeSidesIcon() throws Exception {
        for(String name:List.of("seq_autocrafter","fab_port_item","fab_port_fluid")) {
            var entries=json(LIGHT.resolve("osq_light/layout/"+name+".json")).getAsJsonArray("draw")
                    .asList().stream().map(e->e.getAsJsonObject()).toList();
            String owner=name.equals("seq_autocrafter")?"tool2":"tool1";
            var button=entries.stream().filter(e->e.has("id")&&owner.equals(e.get("id").getAsString())).findFirst().orElseThrow();
            assertSame(appeng.client.gui.Icon.INSCRIBER_SEPARATE_SIDES,
                    appeng.client.gui.Icon.valueOf(button.get("ae_icon").getAsString()));
            assertFalse(entries.stream().anyMatch(e->e.has("owner")&&owner.equals(e.get("owner").getAsString())),
                    "Never overlay a generated approximation on AE2's native icon");
        }
        for(String family:List.of("seq","fab"))assertFalse(Files.exists(LIGHT.resolve(
                "textures/gui/sprites/osq_light/"+family+"/icons/output_direction_white_16.png")));
    }

    @Test void blockRelativeOutputSidesRotateToTheExistingWorldSideBits() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var north=net.minecraft.core.Direction.NORTH;var east=net.minecraft.core.Direction.EAST;
        var south=net.minecraft.core.Direction.SOUTH;var west=net.minecraft.core.Direction.WEST;
        var up=net.minecraft.core.Direction.UP;var down=net.minecraft.core.Direction.DOWN;
        var facings=new net.minecraft.core.Direction[]{north,east,south,west};
        var expected=new net.minecraft.core.Direction[][]{{north,west,east,south,up,down},{east,north,south,west,up,down},
                {south,east,west,north,up,down},{west,south,north,east,up,down}};
        for(int facing=0;facing<facings.length;facing++) {
            var state=net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,facings[facing]);
            int mask=0;
            for(int i=0;i<OsqNeighborDirections.OUTPUT_SIDES.length;i++) {
                var relative=OsqNeighborDirections.OUTPUT_SIDES[i];var side=OsqNeighborDirections.worldSide(state,relative);
                assertEquals(expected[facing][i],side,facings[facing]+" "+relative);
                int bit=1<<side.get3DDataValue();assertEquals(0,mask&bit);mask|=bit;
                var legacy=OsqNeighborDirections.tooltip(null,net.minecraft.core.BlockPos.ZERO,side,bit);
                var hint=OsqNeighborDirections.tooltip(null,net.minecraft.core.BlockPos.ZERO,relative,side,bit);
                assertEquals(legacy,hint.getSiblings().getLast(),"Keep the full world-side/state/click/neighbor tooltip");
                assertNotEquals(hint,OsqNeighborDirections.tooltip(null,net.minecraft.core.BlockPos.ZERO,relative,side,0));
                assertSame(net.minecraft.world.item.ItemStack.EMPTY,OsqNeighborDirections.icon(null,net.minecraft.core.BlockPos.ZERO,side));
            }
            assertEquals(63,mask,"Each world bit must occur exactly once; never send a relative-side ordinal");
        }
        var portState=net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        assertArrayEquals(expected[0],OsqNeighborDirections.SIDES,"Do not redefine the legacy world-side API");
        for(int i=0;i<OsqNeighborDirections.OUTPUT_SIDES.length;i++)
            assertEquals(expected[0][i],OsqNeighborDirections.worldSide(portState,OsqNeighborDirections.OUTPUT_SIDES[i]),
                    "Ports have no facing property and keep their native north/up orientation");
    }

    @Test void neutralCountSurfacesAndEveryDangerButtonUseTheirSharedApprovedMaterial() throws Exception {
        for(String name:List.of("fab_overview","omni_core","omni_core_legacy",
                "seq_center_overview","seq_manipulator","sing_overview")) {
            var entries=json(LIGHT.resolve("osq_light/layout/"+name+".json")).getAsJsonArray("draw")
                    .asList().stream().map(e->e.getAsJsonObject()).toList();
            var count=entries.stream().filter(e->e.has("id")&&"count".equals(e.get("id").getAsString())).findFirst().orElseThrow();
            String sprite=count.get("sprite").getAsString();
            var image=javax.imageio.ImageIO.read(LIGHT.resolve("textures/gui/sprites/"+sprite.split(":",2)[1]+".png").toFile());
            assertEquals(0xFF55536C,image.getRGB(image.getWidth()/2,image.getHeight()/2),name+" "+sprite);
        }
        for(var palette:com.atir.molecularmanipulator.client.gui.theme.OsqPalette.values())if(!palette.dark) {
            assertEquals(0xFF55536C,palette.blk);assertEquals(palette.blk,palette.feat);
        }
        var approved=LIGHT.resolve("textures/gui/sprites/osq_light/fab/button_danger.png");
        for(String family:List.of("fab","omni","seq","sing")) {
            var path=LIGHT.resolve("textures/gui/sprites/osq_light/"+family+"/button_danger.png");
            var image=javax.imageio.ImageIO.read(path.toFile());var reference=javax.imageio.ImageIO.read(approved.toFile());
            assertEquals(reference.getWidth(),image.getWidth());assertEquals(reference.getHeight(),image.getHeight());
            assertArrayEquals(reference.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()),
                    image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()),path.toString());
            assertEquals(json(Path.of(approved+".mcmeta")),json(Path.of(path+".mcmeta")),"The same bevel needs the same nine-slice borders");
        }
    }
}
