package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.client.gui.theme.OsqFonts;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes.Node;
import com.atir.molecularmanipulator.starcomputation.StarResearchMenuNodes.Prerequisite;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real live drawing with Minecraft's default bitmap/Unihex providers; no window or approximate advances. */
class OsqRuntimeReadabilityTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/molecularmanipulator");

    @Test void wellOperationAndSupplyUseFreePanelSpaceForCompleteLiveValues() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var ui=new OsqUi("fab_overview");
            var operation=Component.translatable("gui.molecularmanipulator.fabrication.operation_update",6927,6927);
            ui.text("gui.molecularmanipulator.fab.s021",()->operation);
            ui.number(213,86,()->"6927 / 6927");
            ui.number(226.36f,188,()->"512");
            var details=Component.translatable("gui.molecularmanipulator.fabrication.link_details");
            ui.at(238.27f,188,()->details);
            var g=new InkRecorder(context);ui.foreground(g,context.font);
            var label=g.exact(operation.getString());var hero=ui.layout.find("hero");
            assertEquals(7.5f/9,label.scale,.001f);
            assertTrue(label.left>=hero.x+8&&label.right<=hero.x+hero.w-7);
            assertTrue(label.top>=hero.y+8&&label.bottom<=87-8);
            for(String value:List.of("6927 / 6927","512",details.getString())){
                var ink=g.exact(value);assertEquals(7.5f/9,ink.scale,.001f);
                assertTrue(ink.left>=212&&ink.right<=385,language+" "+ink);
            }
            var supply=g.exact("512");var hint=g.exact(details.getString());
            assertTrue(supply.bottom+5<=hint.top,language+" supply keeps a full separate line");
        }
    }

    @Test void centerSearchHintsAndFormationLabelsUseTheirAvailableWidthInBothLanguages() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(String name:List.of("seq_center_overview","seq_center_autocraft","seq_center_colors")){
                var ui=new OsqUi(name);var message=Component.translatable("gui.molecularmanipulator.pattern_search");
                var field=ui.field("search",0,0,context.font,message);
                var g=new InkRecorder(context);invoke(field,"renderContents",g);
                var ink=g.exact(message.getString());assertEquals(7.5f/9,ink.scale,.001f);
                assertTrue(ink.right<=field.getRight());
                assertTrue(ui.layout.findControl("search").x+ui.layout.findControl("search").w+2<=ui.layout.findControl("prev").x);
            }
            var ui=new OsqUi("seq_center_overview");
            var message=Component.translatable("gui.molecularmanipulator.incomplete");
            ui.text("gui.molecularmanipulator.formed",()->message);
            var g=new InkRecorder(context);ui.foreground(g,context.font);
            var ink=g.exact(message.getString());assertEquals(7.5f/9,ink.scale,.001f);
            assertTrue(ink.right<=416);
            var stocking=new OsqUi("seq_center_autocraft");
            stocking.number(231.22f,45,()->"/ 9");stocking.width(231.22f,45,24);
            for(int count:List.of(0,9)){
                stocking.number(226.36f,45,()->count);
                var values=new InkRecorder(context);stocking.foreground(values,context.font);
                var digits=values.ink.stream().filter(i->i.text.equals(Integer.toString(count))&&i.left>=236&&i.top<46).toList();
                assertEquals(1,digits.size(),language+" live stocking count: "+values.ink);
                var digit=digits.getFirst();var limit=values.exact("/ 9");
                assertEquals(7.5f/9,digit.scale,.001f);assertTrue(digit.right<=limit.left,language+" "+digit+" / "+limit);
                String caption=Component.translatable("gui.molecularmanipulator.overview_auto_craft").getString();
                var heading=values.ink.stream().filter(i->i.text.equals(caption)&&i.top>=35&&i.top<46).findFirst().orElseThrow();
                assertTrue(heading.right<=digit.left,language+" "+heading+" / "+digit);
            }
        }
    }

    @Test void centerOverviewKeepsLiveTelemetryVisibleAfterHiddenEffectsAndUsesTheCompactNativeSlots() throws Exception {
        var setting=com.atir.molecularmanipulator.config.ModConfig.DYNAMIC_EFFECT_LEVEL;var saved=field(setting,"cachedValue");
        try {
            ResponsiveScreenTestFixture.set(setting,"cachedValue",2);
            for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
                var center=center(context,"seq_center_overview");var screen=center.screen;var ui=center.ui;var menu=center.menu;
                invoke(screen,"initColorControls");((MachineEffectButton)field(screen,"effectsButton")).visible=false;
                invoke(screen,"bindOverviewTelemetry");
                ui.text("gui.molecularmanipulator.formed",()->Component.translatable("gui.molecularmanipulator."+(menu.formed?"formed":"incomplete")));
                ui.text("gui.molecularmanipulator.overview_online",()->Component.translatable("gui.molecularmanipulator.overview_"+(menu.operational?"online":"offline")));
                assertEquals(432,ui.layout.width);assertEquals(288,ui.layout.height);
                var inventory=ui.layout.find("inv");var quantum=ui.layout.find("quantum");
                var nativeInventory=new net.minecraft.world.SimpleContainer(36);
                var slots=new ArrayList<net.minecraft.world.inventory.Slot>();
                for(int i=0;i<36;i++)slots.add(new net.minecraft.world.inventory.Slot(nativeInventory,i,0,0));
                ui.slots(slots,"inv");int[] rows={201,219,237,259};
                for(int i=0;i<36;i++){
                    var slot=slots.get(i);assertEquals(17+18*(i%9),slot.x);assertEquals(rows[i/9],slot.y);
                    assertTrue(slot.y+16<=inventory.y+inventory.h-4);
                }
                var quantumSlot=new net.minecraft.world.inventory.Slot(new net.minecraft.world.SimpleContainer(1),0,0,0);
                ui.slot(quantumSlot,"qes");assertEquals(201,quantumSlot.x);assertEquals(174,quantumSlot.y);
                menu.quantumLinkState=MolecularCenterBlockEntity.QuantumLinkState.REMOTE_MISSING;menu.quantumFrequency=-1;
                menu.autoCraftEnabledMask=511;menu.patternCapacity=10800;
                for(boolean online:List.of(false,true))for(int used:new int[]{0,1,9999,10800})for(long quantity:new long[]{0,999990,Long.MAX_VALUE}){
                    menu.operational=online;menu.formed=online;menu.usedPatternSlots=used;
                    menu.activePipelineRecipes=10800;menu.activePipelineCrafts=quantity;menu.pendingOutputAmount=quantity;
                    var g=new InkRecorder(context);ui.foreground(g,context.font);
                    String sample=language+" online="+online+" used="+used+" quantity="+quantity;
                    var value=g.ink.stream().filter(i->i.text.equals(Integer.toString(used))&&i.scale>1.5f).findFirst().orElseThrow();
                    var capacity=g.exact("/ 10800");assertEquals(17f/9,value.scale,.001f);
                    assertTrue(value.right+7<=capacity.left,sample+" capacity follows the complete live count");
                    assertTrue(capacity.right<=ui.layout.find("count").x+ui.layout.find("count").w-8);
                    var production=g.ink.stream().filter(i->i.left>=192&&i.top>=113&&i.bottom<=159).toList();
                    assertEquals(10,production.size(),sample+" all three production columns retain their headings, values and units");
                    for(var ink:production){assertTrue(ink.scale>=7.5f/9-.001f);assertFalse(ink.text.contains("…"),sample+" "+ink);}
                    assertEquals(1,production.stream().filter(i->Math.abs(i.scale-15f/9)<.001f).count());
                    assertEquals(5,production.stream().filter(i->Math.abs(i.scale-8f/9)<.001f).count(),production.toString());
                    assertEquals(4,production.stream().filter(i->Math.abs(i.scale-7.5f/9)<.001f).count());
                    assertEquals(2,production.stream().filter(i->i.text.equals(OsqUi.compact(quantity))).count());
                    var heading=g.exact(Component.translatable("gui.molecularmanipulator.overview_pipeline").getString());
                    var recipes=production.stream().filter(i->i.text.equals("10800")).findFirst().orElseThrow();
                    var unit=g.exact(Component.translatable("gui.molecularmanipulator.seq.s017").getString());
                    assertTrue(heading.bottom+3<=recipes.top,sample);assertTrue(recipes.right+3<=unit.left,sample);
                    var lower=production.stream().filter(i->i.text.equals(OsqUi.compact(quantity))&&i.left<340).findFirst().orElseThrow();
                    assertTrue(recipes.bottom+3<=lower.top,sample);assertTrue(lower.bottom<=152,sample);
                    var environment=ui.layout.find("env");
                    for(String key:List.of("spawn_protection","spawn_protection_area")){
                        var ink=g.exact(Component.translatable("gui.molecularmanipulator."+key).getString());
                        assertEquals(7.5f/9,ink.scale,.001f);assertTrue(ink.left>=24&&ink.right<=environment.x+environment.w-8,sample+" "+ink);
                        assertTrue(ink.bottom<=environment.y+environment.h-4,sample+" "+ink);
                    }
                    var link=g.exact(Component.translatable("gui.molecularmanipulator.quantum_state.remote_missing").getString());
                    assertTrue(link.left>=quantum.x+38&&link.right<=quantum.x+quantum.w-8,sample+" "+link);
                    for(int a=0;a<g.ink.size();a++)for(int b=a+1;b<g.ink.size();b++)
                        assertFalse(g.ink.get(a).overlaps(g.ink.get(b)),sample+" "+g.ink.get(a)+" / "+g.ink.get(b));
                    for(var ink:g.ink)assertFalse(ink.text.contains("…"),sample+" "+ink);
                }
            }
        } finally {ResponsiveScreenTestFixture.set(setting,"cachedValue",saved);}
    }

    @Test void centerLimitAndReserveInputsKeepClearOfTheDividerAndCardEdges() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var ui=new OsqUi("seq_center_autocraft");var card=ui.layout.find("current");
            var divider=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.endsWith("/divider")&&e.requiresSelection).findFirst().orElseThrow();
            var limit=ui.field("limit",0,0,context.font,Component.empty());var limitBox=ui.layout.findControl("limit");
            var apply=ui.button("limit_set",0,0,Component::empty,()->{});
            assertEquals(78,limitBox.w);assertEquals(18,limitBox.h);
            assertTrue(limitBox.y+limitBox.h+7<=divider.y,"The painted output-limit frame must not rest on the divider");
            assertTrue(apply.getBottom()+7<=divider.y);
            var body=new InkRecorder(context);ui.foreground(body,context.font);
            var label=body.exact(Component.translatable("gui.molecularmanipulator.auto_craft_output_limit").getString());
            var heading=body.exact(Component.translatable("gui.molecularmanipulator.seq.s008").getString());
            assertEquals(7.5f/9,label.scale,.001f);assertEquals(7.5f/9,heading.scale,.001f);
            assertTrue(label.bottom+6<=limitBox.y);
            assertTrue(divider.y+divider.h+3<=heading.top);
            for(String value:List.of("0","4096")){
                limit.setValue(value);var ink=new InkRecorder(context);invoke(limit,"renderContents",ink);
                var digits=ink.exact(value);assertEquals(7.5f/9,digits.scale,.001f);
                assertTrue(digits.left>=limitBox.x+3&&digits.right<=limitBox.x+limitBox.w-3);
                assertTrue(digits.top>=limitBox.y+3&&digits.bottom<=limitBox.y+limitBox.h-3);
            }
            for(int i=0;i<9;i++){
                var field=ui.field("rsv_f"+i,0,0,context.font,Component.empty());
                var button=ui.button("rsv_ok"+i,0,0,Component::empty,()->{});var box=ui.layout.findControl("rsv_f"+i);
                assertEquals(62,box.w);assertEquals(18,box.h);
                assertEquals(180+i/2*20,box.y);assertEquals(box.y+3,field.getY());assertEquals(box.y+1,button.getY());
                assertEquals(16,button.getWidth());assertEquals(16,button.getHeight());
                assertTrue(heading.bottom+3<=box.y);
                assertTrue(box.y+box.h<=card.y+card.h-8,"Even the ninth input keeps the card's bottom inset");
                assertTrue(ui.layout.draw.stream().filter(e->box.id.equals(e.ownerId)).allMatch(e->ui.layout.owns(box,e)));
            }
            assertTrue(card.y+card.h+14<=ui.layout.findControl("projection").y);
            var image=javax.imageio.ImageIO.read(ASSETS.resolve("textures/gui/sprites/"+card.sprite.split(":",2)[1]+".png").toFile());
            assertEquals(Math.round((card.w+card.pad[0]+card.pad[2])*4),image.getWidth());
            assertEquals(Math.round((card.h+card.pad[1]+card.pad[3])*4),image.getHeight());
        }
    }

    @Test void centerReserveVisibilityAndItemOriginsFollowAllFiveMovedRows() throws Exception {
        try(var context=new Context(false,"en_us")){
            var ui=new OsqUi("seq_center_autocraft");
            var screen=ResponsiveScreenTestFixture.allocate(MolecularCenterScreen.class);
            var menu=ResponsiveScreenTestFixture.allocate(MolecularCenterMenu.class);
            ResponsiveScreenTestFixture.set(screen,"menu",menu);
            var visible=MolecularCenterScreen.class.getDeclaredMethod("autoCraftReserveVisible",OsqLayout.Entry.class,boolean.class);
            visible.setAccessible(true);
            int top=(Integer)field(screen,"AUTO_CRAFT_RESERVE_TOP");
            for(int i=0;i<9;i++){
                var control=ui.layout.findControl("rsv_f"+i);var apply=ui.layout.findControl("rsv_ok"+i);
                var slot=ui.entries("rsv"+(i%2)).stream().filter(e->e.y==control.y).findFirst().orElseThrow();
                assertEquals(slot.y+1,top+1+i/2*20,"The live item icon must move with its slot");
                for(int count:List.of(0,1,4,8,9))for(boolean selected:List.of(false,true)){
                    menu.autoCraftInputCount=count;
                    for(var entry:List.of(slot,control,apply))assertEquals(selected&&i<count,visible.invoke(screen,entry,selected),entry.id+" input="+i+" count="+count);
                }
            }
        }
    }

    @Test void quantumFrequencyMigrationUploadAndLiveStatusHaveSeparateRowsInEveryCenterPage() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(String name:List.of("seq_center_overview","seq_center_autocraft","seq_center_colors")){
                var center=center(context,name);var ui=center.ui;var menu=center.menu;
                assertNull(ui.layout.find("core"),name+" has no retired functional or ghost slot");
                assertNull(ui.layout.findText("gui.molecularmanipulator.quantum_upload_core"));
                assertNull(ui.layout.findText("gui.molecularmanipulator.quantum_upload_core_hint"));
                menu.quantumFrequency=-1L;
                for(var linkState:MolecularCenterBlockEntity.QuantumLinkState.values())
                for(boolean automatic:new boolean[]{false,true})for(String state:List.of("normal","hover","disabled")){
                    menu.quantumLinkState=linkState;
                    menu.automaticPatternUpload=automatic;menu.operational=!state.equals("disabled");
                    invoke(center.screen,"refreshPatternTransferControls");
                    for(var button:center.buttons)ResponsiveScreenTestFixture.set(button,"isHovered",state.equals("hover"));
                    var g=new InkRecorder(context);center.screen.drawFG(g,0,0,-1,-1);
                    for(var button:center.buttons)renderButtonContents(button,g);
                    String sample=language+" "+name+" "+linkState+" auto="+automatic+" "+state;
                    var title=g.exact(Component.translatable("gui.molecularmanipulator.quantum_title").getString());
                    var status=g.exact(Component.translatable("gui.molecularmanipulator.quantum_state."
                            +linkState.name().toLowerCase(java.util.Locale.ROOT)).getString());
                    var caption=g.exact(Component.translatable("gui.molecularmanipulator.fab.s025").getString());
                    var frequency=g.exact("FFFFFFFFFFFFFFFF");
                    String requirements=Component.translatable("gui.molecularmanipulator.overview_quantum_requirements",512).getString();
                    var upkeep=g.exact(requirements);
                    assertEquals(requirements,upkeep.text,sample);
                    assertTrue(title.bottom+3<=status.top,sample);
                    assertTrue(status.bottom+3<=caption.top,sample);
                    assertTrue(caption.right+5<=frequency.left,sample+" frequency uses live sixteen-digit width");
                    assertTrue(frequency.bottom+3<=upkeep.top,sample+" "+frequency+" / "+upkeep);
                    for(var ink:List.of(title,status,caption,frequency,upkeep))assertEquals(7.5f/9,ink.scale,.001f,sample);
                    for(int index=0;index<2;index++){
                        var button=center.buttons.get(index);
                        var box=ui.layout.findControl(index==0?"pattern_upload":"migrate_patterns");
                        String key=index==0?"gui.molecularmanipulator.pattern_upload."+(automatic?"on":"off")
                                :"gui.molecularmanipulator.pattern_migration.button";
                        String message=Component.translatable(key).getString();var ink=g.exact(message);
                        assertEquals(message,ink.text,sample);assertEquals(7.5f/9,ink.scale,.001f,sample);
                        assertEquals(1,g.ink.stream().filter(i->i.text.equals(message)).count(),sample+" replaces the static label exactly once");
                        String faceState=!button.active?"disabled":index==0&&automatic?"active":state.equals("hover")?"hover":"normal";
                        assertEquals(box.y+OsqUi.controlCenter("button",faceState,box.h,false),ink.center(),.8f,sample);
                        assertTrue(ink.left>=box.x+6.5f&&ink.right<=box.x+box.w-6.5f,sample+" "+ink);
                        assertTrue(upkeep.bottom+3<=box.y,sample+" "+upkeep+" / control y="+box.y);
                        assertTrue(center.screen.screenContent.owns(button),"New controls use the screen's responsive input/render transform");
                        assertNotNull(button.getTooltip(),"The real upload/migration explanations remain available");
                        var panel=ui.layout.find("quantum");
                        assertTrue(box.x>=panel.x&&box.x+box.w<=panel.x+panel.w&&box.y+box.h<=panel.y+panel.h,sample);
                        for(var slot:ui.layout.draw)if("slot".equals(slot.kind))
                            assertFalse(box.x<slot.x+slot.w&&box.x+box.w>slot.x&&box.y<slot.y+slot.h&&box.y+box.h>slot.y,
                                    sample+" control cannot cover native slot "+slot.id);
                    }
                    for(int a=0;a<g.ink.size();a++)for(int b=a+1;b<g.ink.size();b++)
                        assertFalse(g.ink.get(a).overlaps(g.ink.get(b)),sample+" "+g.ink.get(a)+" / "+g.ink.get(b));
                    if(language.equals("en_us"))for(var ink:g.ink)assertFalse(ink.text.endsWith("…"),sample+" "+ink);
                }
            }
        }
    }

    @Test void centerTransferControlsFollowServerReadinessAndStayReachableInSmallViewports() throws Exception {
        try(var context=new Context(false,"en_us")){
            for(String name:List.of("seq_center_overview","seq_center_autocraft","seq_center_colors")){
                var center=center(context,name);var menu=center.menu;
                menu.quantumLinkState=MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
                for(boolean online:new boolean[]{false,true})for(boolean building:new boolean[]{false,true})for(boolean dismantling:new boolean[]{false,true}){
                    menu.operational=online;menu.building=building;menu.dismantling=dismantling;
                    invoke(center.screen,"refreshPatternTransferControls");
                    assertEquals(online&&!building&&!dismantling,center.buttons.get(1).active,name);
                    assertTrue(center.buttons.getFirst().active,"Upload preference is configurable even before the array is online");
                    for(var button:center.buttons)assertTrue(button.visible,name+" keeps both entrances on every page");
                }
                menu.operational=true;menu.building=false;menu.dismantling=false;
                invoke(center.screen,"refreshPatternTransferControls");
                for(int[] viewport:new int[][]{{320,240},{567,240}}){
                    center.screen.width=viewport[0];center.screen.height=viewport[1];
                    var bounds=center.screen.responsiveBounds();
                    assertTrue(bounds.getX()>=3&&bounds.getY()>=3);
                    assertTrue(bounds.getX()+bounds.getWidth()<=viewport[0]-3);
                    assertTrue(bounds.getY()+bounds.getHeight()<=viewport[1]-3);
                    for(var button:center.buttons){
                        int x=center.screen.responsiveScreenX(button.getX()+button.getWidth()/2);
                        int y=center.screen.responsiveScreenY(button.getY()+button.getHeight()/2);
                        assertTrue(button.isMouseOver(center.screen.logicalMouseX(x),center.screen.logicalMouseY(y)),name);
                    }
                }
            }
        }
    }

    @Test void colorControlsSwatchesAndEffectsHaveSeparateAlignedRowsInBothLanguages() throws Exception {
        var setting=com.atir.molecularmanipulator.config.ModConfig.DYNAMIC_EFFECT_LEVEL;
        var saved=field(setting,"cachedValue");ResponsiveScreenTestFixture.set(setting,"cachedValue",setting.getDefault());
        try {
        for(String language:List.of("zh_cn","en_us"))
        for(boolean legacy:new boolean[]{false,true})try(var context=new Context(false,language)){
            var center=center(context,"seq_center_colors");var screen=center.screen;var ui=center.ui;var menu=center.menu;
            menu.legacyStructure=legacy;
            ResponsiveScreenTestFixture.set(screen,"colorUpdateConfirmation",new StructureUpdateConfirmation());
            invoke(screen,"initColorControls");
            var buttons=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
            assertEquals(legacy?21:19,buttons.size());
            for(int a=0;a<buttons.size();a++)for(int b=a+1;b<buttons.size();b++)
                assertFalse(overlaps(buttons.get(a),buttons.get(b)),language+" overlapping live controls");
            var effects=(MachineEffectButton)field(screen,"effectsButton");var reset=ui.layout.findControl("reset");
            assertEquals(reset.y,effects.getY());assertEquals(reset.h,effects.getHeight());
            assertEquals(reset.y+reset.h,effects.getBottom());
            assertTrue(effects.getRight()+6<=reset.x);assertNotNull(effects.getTooltip());
            assertTrue(ui.layout.findControl("c4R").y+20+3<=effects.getY());
            for(int color:new int[]{0,0xFFFFFF,0x010203})for(String state:List.of("normal","hover","disabled")){
                menu.fieldColor=menu.coreColor=menu.primaryRingColor=menu.secondaryRingColor=menu.latticeColor=color;
                var g=new InkRecorder(context);screen.drawFG(g,0,0,-1,-1);
                assertEquals(5,g.rects.size());
                for(int row=0;row<5;row++){
                    var swatch=ui.layout.find("sw"+row);var fill=g.rects.get(row);
                    assertEquals(0xFF000000|color,fill.color());
                    assertEquals(swatch.x+1,fill.left());assertEquals(swatch.x+swatch.w-1,fill.right());
                    assertEquals(swatch.y+1,fill.top());assertEquals(swatch.y+swatch.h-1,fill.bottom());
                    var caption=g.exact(Component.translatable("gui.molecularmanipulator.visual_color."+row).getString());
                    assertEquals(swatch.y+swatch.h/2,caption.center(),.8f);assertEquals(7.5f/9,caption.scale,.001f);
                    for(int channel=0;channel<3;channel++){
                        var button=buttons.get(2+row*3+channel);button.active=!state.equals("disabled");
                        ResponsiveScreenTestFixture.set(button,"isHovered",state.equals("hover"));
                        var contents=new InkRecorder(context);renderButtonContents(button,contents);
                        String value="RGB".charAt(channel)+" "+((color>>(16-channel*8))&255);
                        var label=contents.exact(value);assertEquals(7.5f/9,label.scale,.001f);
                        assertTrue(label.left>=button.getX()+3.5f&&label.right<=button.getRight()-3.5f);
                        assertTrue(label.top>=button.getY()&&label.bottom<=button.getBottom());
                    }
                }
            }
            for(int[] viewport:new int[][]{{320,240},{567,240}}){
                screen.width=viewport[0];screen.height=viewport[1];var bounds=screen.responsiveBounds();
                assertTrue(bounds.getX()>=3&&bounds.getY()>=3);
                assertTrue(bounds.getX()+bounds.getWidth()<=viewport[0]-3&&bounds.getY()+bounds.getHeight()<=viewport[1]-3);
                int x=screen.responsiveScreenX(effects.getX()+effects.getWidth()/2);
                int y=screen.responsiveScreenY(effects.getY()+effects.getHeight()/2);
                assertTrue(effects.isMouseOver(screen.logicalMouseX(x),screen.logicalMouseY(y)));
            }
        }
        } finally {ResponsiveScreenTestFixture.set(setting,"cachedValue",saved);}
    }

    @Test void machineEffectsUseTheSameSkinTypographyAndStatesAsOtherPageButtons() throws Exception {
        var setting=com.atir.molecularmanipulator.config.ModConfig.DYNAMIC_EFFECT_LEVEL;
        var saved=field(setting,"cachedValue");
        try {
            for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
                for(String name:List.of("seq_center_colors","fab_overview","omni_core","omni_core_legacy","sing_overview","sing_compact")){
                    var ui=new OsqUi(name);int[] authoritative={-1};var requests=new ArrayList<Integer>();
                    ResponsiveScreenTestFixture.set(setting,"cachedValue",2);
                    var effects=new MachineEffectButton(ui,ui.buttonBox(0,ui.layout.height+3,136,22),0,0,
                            ()->authoritative[0],requests::add);
                    var standard=ui.rect(148,ui.layout.height+3,136,22,0,0,effects::getMessage,()->{});
                    assertEquals(OsqUi.SkinButton.class,effects.getClass().getSuperclass());
                    for(int global:List.of(0,1,2))for(int override:List.of(-1,0,1,2)){
                        ResponsiveScreenTestFixture.set(setting,"cachedValue",global);authoritative[0]=override;effects.refresh();
                        int resolved=override<0?global:override;
                        String label=Component.translatable("gui.molecularmanipulator.effects.button",
                                Component.translatable("gui.molecularmanipulator.effects.level."+resolved)).getString();
                        assertEquals(label,effects.getMessage().getString());var tooltip=effects.getTooltip();assertNotNull(tooltip);
                        for(String state:List.of("normal","hover","focused","disabled")){
                            for(var button:List.of(effects,standard)){
                                button.active=!state.equals("disabled");button.setFocused(state.equals("focused"));
                                ResponsiveScreenTestFixture.set(button,"isHovered",state.equals("hover"));
                            }
                            var actual=new InkRecorder(context);renderButtonContents(effects,actual);
                            var reference=new InkRecorder(context);renderButtonContents(standard,reference);
                            var actualVisual=buttonVisual(effects);var referenceVisual=buttonVisual(standard);
                            String status=state.equals("focused")?"hover":state;
                            assertEquals(ui.sprite("button_"+status),field(actualVisual,"sprite"));
                            assertEquals(field(referenceVisual,"sprite"),field(actualVisual,"sprite"));
                            assertArrayEquals((int[])field(referenceVisual,"pad"),(int[])field(actualVisual,"pad"));
                            assertEquals(field(referenceVisual,"textColor"),field(actualVisual,"textColor"));
                            var ink=actual.exact(label);var matching=reference.exact(label);
                            assertEquals(7.5f/9,ink.scale,.001f);assertEquals(matching.top,ink.top,.001f);
                            assertTrue(ink.left>=effects.getX()+3&&ink.right<=effects.getRight()-3);
                            assertTrue(ink.top>=effects.getY()&&ink.bottom<=effects.getBottom());
                            assertSame(tooltip,effects.getTooltip(),"The inheritance/Shift explanation must not be replaced");
                        }
                    }
                    assertTrue(requests.isEmpty(),"Rendering and refreshing must not send effect-setting requests");
                }
            }
        } finally {ResponsiveScreenTestFixture.set(setting,"cachedValue",saved);}
    }

    @Test void engineOverviewCentersRingsAndSeparatesBenefitGroupsInBothLanguages() throws Exception {
        String prefix="gui.molecularmanipulator.star_computation.";
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var engine=engine(context);var screen=engine.screen;var menu=engine.menu;
            menu.anchorCount=246;menu.effectiveEntropy=2147483647;
            menu.anchorsReference=81;menu.anchorsQuantum=82;menu.anchorsTerminal=83;
            for(int level:new int[]{0,2,5,8,9})for(boolean overclock:new boolean[]{false,true}){
                menu.benefitLevel=level;menu.benefitsOverclocked=overclock;
                var g=new InkRecorder(context);invoke(screen,"drawOverview",g);
                var rows=g.ink.stream().filter(i->i.left>=231&&i.top>=135).toList();
                for(int a=0;a<rows.size();a++)for(int b=a+1;b<rows.size();b++)
                    assertFalse(rows.get(a).overlaps(rows.get(b)),"L"+level+" "+rows.get(a)+" / "+rows.get(b));
                for(var row:rows)assertTrue(row.scale>=7.5f/9-.001f,row.toString());
                Ink previous=null;
                for(String key:List.of("assembler_parallel","cpu_parallel","cpu_storage","fabrication_speed")){
                    var title=g.exact(Component.translatable(prefix+key).getString());
                    if(previous!=null)assertEquals(25,title.center()-previous.center(),.8f);
                    previous=title;
                }
                var footnote=g.exact(Component.translatable(prefix+"research_parallel_only").getString());
                var lastDetail=g.ink.get(g.ink.indexOf(previous)+2);
                assertTrue(footnote.bottom<242);assertTrue(footnote.top>=lastDetail.bottom+2,lastDetail+" / "+footnote);
                var first=g.exact(Component.translatable(prefix+"ring",9).getString());
                var last=g.exact(Component.translatable(prefix+"ring",1).getString());
                assertEquals(127,(first.center()+last.center())/2,.8f);
                assertTrue(first.top>70&&last.bottom<185);
                var rings=new InkRecorder(context);invoke(screen,"drawRings",rings);
                assertEquals(77,rings.rects.stream().mapToInt(UiRenderRecorder.Rect::top).min().orElseThrow());
                assertEquals(178,rings.rects.stream().mapToInt(UiRenderRecorder.Rect::bottom).max().orElseThrow());
                var icons=new InkRecorder(context);invoke(screen,"drawBenefitIcons",icons);
                var backs=icons.rects.stream().filter(r->r.right()-r.left()==13&&r.bottom()-r.top()==13).toList();
                assertEquals(4,backs.size());for(int i=1;i<4;i++)assertEquals(25,backs.get(i).top()-backs.get(i-1).top());
            }
        }
    }

    @Test void longRuntimeTextRetainsReadableBodySizeAndWholeCodepoints() throws Exception {
        try(var context=new Context(false,"zh_cn")){
            var ui=new OsqUi("fab_port_item");var g=new InkRecorder(context);
            ui.centeredText(g,Component.literal("一段很长的实际物品名称😀用于验证省略号"),10,20,60,ui.palette.ink);
            assertEquals(1,g.ink.size());var text=g.ink.getFirst();
            assertTrue(text.text.endsWith("…"));assertEquals(7.5f/9,text.scale,.001f);
            assertTrue(text.right<=70.5);assertTrue(text.center()>19.5&&text.center()<20.5);
        }
    }

    @Test void actualReturnLabelFitsBesideItsVisibleIconInAllStatesAndLanguages() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var ui=new OsqUi("fab_port_item");var box=ui.layout.findControl("return");
            var label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&ui.layout.owns(box,e)).findFirst().orElseThrow();
            var original=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/")&&ui.layout.owns(box,e)).findFirst().orElseThrow();
            var cache=new OsqButtonIconCache();
            for(String state:List.of("normal","hover","disabled")){
                String tint=state.equals("disabled")?"dis":state.equals("hover")?"ink":"white";
                String sprite=cache.resolve(OsqFonts.resourceGeneration(),original.sprite,null,tint,
                        id->context.resources.getResource(ResourceLocation.parse(id).withPrefix("textures/gui/sprites/").withSuffix(".png")).isPresent());
                var icon=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(sprite);
                float iw=icon.fitWidth(original.w,original.h)*icon.width();
                float width=OsqFonts.widthF(context.font,Component.translatable(
                        "gui.molecularmanipulator.fabrication.port.return_ae").getString(),OsqFonts.id(label.fontId),label.size);
                assertTrue(iw+6+width+14<=box.w,"Full 全部退回 AE label fits with real "+state+" pixels");
                float cy=OsqUi.controlCenter("button",state,box.h,false);
                label.drawX=box.x+(box.w-iw-6-width)/2+iw+6;label.w=width;label.centerY=box.y+cy;
                var g=new InkRecorder(context);
                ui.layout.renderSingleLine(g,context.font,label,Component.translatable(
                        "gui.molecularmanipulator.fabrication.port.return_ae"),0,0);
                var ink=g.ink.getFirst();assertEquals(box.y+cy,ink.center(),.8);
                assertEquals(label.size/9,ink.scale,.001);assertEquals(Component.translatable(
                        "gui.molecularmanipulator.fabrication.port.return_ae").getString(),ink.text);
            }
        }
    }

    @Test void everyPageSwitchUsesConnectedAeTextTabsWithFullChineseAndEnglishLabels() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){
                for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()){
                    String name=path.getFileName().toString().replace(".json","");
                    for(boolean compact:name.startsWith("sing_")?new boolean[]{false,true}:new boolean[]{false}){
                        var ui=new OsqUi(name);
                        if(compact)com.atir.molecularmanipulator.client.gui.theme.OsqSingularityPages.configure(ui.layout,true);
                        var tabs=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/tab_")
                                ||name.equals("star_computation_engine")&&("overview".equals(e.id)||"recipes".equals(e.id))).toList();
                        for(var box:tabs){
                            Component label;
                            if(name.equals("star_computation_engine"))label=Component.translatable("gui.molecularmanipulator.star_computation."+box.id);
                            else if(name.startsWith("fab_assembly_"))label=Component.translatable("gui.molecularmanipulator.fabrication.buffer."
                                    +new String[]{"patterns","input_tab","output_tab","catalysts"}[Integer.parseInt(box.id.substring(3))]);
                            else label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&ui.layout.owns(box,e)).findFirst().orElseThrow().staticText();
                            boolean[] selected={false};
                            var tab=ui.tab(box.id,13,17,()->label,()->selected[0],()->{});
                            assertEquals("PageTab",tab.getClass().getSimpleName());
                            var render=tab.getClass().getDeclaredMethod("renderWidget",net.minecraft.client.gui.GuiGraphics.class,int.class,int.class,float.class);
                            render.setAccessible(true);
                            for(String state:List.of("normal","hover","active","disabled")){
                                selected[0]=state.equals("active");tab.active=!state.equals("disabled");
                                ResponsiveScreenTestFixture.set(tab,"isHovered",state.equals("hover"));
                                var g=new InkRecorder(context);render.invoke(tab,g,0,0,0f);
                                assertEquals(1,g.ink.size(),name+" "+label.getString());var text=g.ink.getFirst();
                                assertEquals(label.getString(),text.text,name+" "+language+" "+state);
                                assertEquals(7.5f/9,text.scale,.001f);assertEquals(tab.getY()+10,text.center(),.8f);
                                assertTrue(text.left>=tab.getX()+4.5f&&text.right<=tab.getRight()-4.5f,name+" "+text);
                                assertEquals(state.equals("active")?0:3,g.rects.size(),"Selected glass is background-only; unselected tabs have only the outer frame, inner rim and fill");
                                assertNotNull(box.tabSurface);assertNotNull(ui.layout.find(box.tabPanel));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test void selectedGlassFollowsLivePageStateWithoutRepaintingItemsOrMutatingTheParent() throws Exception {
        try(var context=new Context(false,"zh_cn")){
            var ui=new OsqUi("fab_assembly_patterns");int[] page={0},presses={0};
            var buttons=new ArrayList<net.minecraft.client.gui.components.Button>();
            var parent=ui.layout.find("body");String original=parent.sprite;float y=parent.y;
            for(int i=0;i<4;i++){
                final int index=i;
                buttons.add(ui.tab("tab"+i,0,0,()->Component.literal("Tab"),()->page[0]==index,()->{page[0]=index;presses[0]++;}));
            }
            for(int i=0;i<4;i++){
                buttons.get(i).onPress();
                var surfaces=ui.tabSurfaces();assertEquals(1,surfaces.size());
                assertSame(ui.layout.findControl("tab"+i).tabSurface,surfaces.get(parent));
                assertEquals(original,parent.sprite);assertEquals(y,parent.y,"Changing pages cannot move native slot geometry");
                buttons.get(i).active=false;
                assertEquals(1,ui.tabSurfaces().size(),"A disabled selected page keeps its connected surface");
                var g=new InkRecorder(context);
                var render=buttons.get(i).getClass().getDeclaredMethod("renderWidget",net.minecraft.client.gui.GuiGraphics.class,int.class,int.class,float.class);
                render.setAccessible(true);render.invoke(buttons.get(i),g,0,0,0f);
                assertEquals(0,g.rects.size(),"The widget does not paint a second face over slots/items");
                buttons.get(i).visible=false;assertTrue(ui.tabSurfaces().isEmpty());buttons.get(i).visible=true;
            }
            assertEquals(4,presses[0],"Existing page-switch callbacks still run exactly once");
        }
    }

    @Test void researchAndEngineUseTheCurrentlyVisibleParentsForTheirSelectedGlass() throws Exception {
        try(var context=new Context(false,"zh_cn")){
            var menu=ResponsiveScreenTestFixture.allocate(com.atir.molecularmanipulator.menu.MatterFabricationMenu.class);
            menu.researchState=new com.atir.molecularmanipulator.research.MatterResearchMenuState("{\"completed\":[],\"tasks\":{}}");
            var panel=new MatterResearchPanel(menu,context.font,0,0,button->{});
            var field=MatterResearchPanel.class.getDeclaredField("ui");field.setAccessible(true);var research=(OsqUi)field.get(panel);
            assertSame(research.layout.findControl("tab1").tabSurface,research.tabSurfaces().get(research.layout.find("detailcard")),
                    "The screen's overview UI is not the research background renderer");
            var engine=new OsqUi("star_computation_engine");boolean[] recipes={false};
            engine.visible=e->e.id==null||!e.id.startsWith(recipes[0]?"ov_":"rc_");
            var overview=engine.tab("overview",0,0,()->Component.literal("Overview"),()->!recipes[0],()->{});
            var tree=engine.tab("recipes",0,0,()->Component.literal("Recipes"),()->recipes[0],()->{});
            var render=overview.getClass().getDeclaredMethod("renderWidget",net.minecraft.client.gui.GuiGraphics.class,int.class,int.class,float.class);
            render.setAccessible(true);
            for(boolean page:new boolean[]{false,true}){
                recipes[0]=page;var surfaces=engine.tabSurfaces();assertEquals(1,surfaces.size());
                assertSame(engine.layout.findControl(page?"recipes":"overview").tabSurface,surfaces.get(engine.layout.find(page?"rc_node":"ov_feature")));
                for(var tab:List.of(overview,tree)){
                    var g=new InkRecorder(context);render.invoke(tab,g,0,0,0f);
                    boolean selected=tab==(page?tree:overview);
                    assertEquals(selected?0:3,g.rects.size(),"Engine tabs use only the connected glass or normal frame, without an extra top stripe");
                    assertEquals(1,g.ink.size());
                }
            }
        }
    }

    @Test void assemblySaveAndFullOccupancyCounterFitWithoutEllipsis() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(String page:List.of("fab_assembly_patterns","fab_assembly_input","fab_assembly_catalyst")){
                var ui=new OsqUi(page);var box=ui.layout.findControl("save");
                var label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&ui.layout.owns(box,e)).findFirst().orElseThrow();
                var icon=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/")&&ui.layout.owns(box,e)).findFirst().orElseThrow();
                float width=OsqFonts.widthF(context.font,label.staticText().getString(),OsqFonts.id(label.fontId),label.size);
                assertTrue(width+14+6+icon.w<=box.w,page+" "+language+" save has room for its text and icon");
                ui.number(160.42f,22,()->"36 / 36");ui.at(170.19f,22,Component::empty);
                var g=new InkRecorder(context);ui.foreground(g,context.font);
                var count=g.containing("36 / 36");assertEquals("36 / 36",count.text);assertEquals(9f/9,count.scale,.001f);
                assertTrue(count.right<=182.5);assertTrue(count.left>=142);
            }
        }
    }

    @Test void compactCatalystMetadataKeepsItsLiveCountHintAndInventorySeparate() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var screen=ResponsiveScreenTestFixture.allocate(MatterFabricationPatternAssemblyScreen.class);var ui=new OsqUi("fab_assembly_catalyst");
            ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"font",context.font);
            var refund=ui.layout.findControl("return");ui.hide(e->ui.layout.owns(refund,e));
            var method=screen.getClass().getDeclaredMethod("centerMetadata",String.class,String.class,Component.class);method.setAccessible(true);
            for(int count:new int[]{0,36,Integer.MAX_VALUE}){
                var value=Component.translatable("gui.molecularmanipulator.fabrication.buffer.catalyst_types",count);
                ui.text("gui.molecularmanipulator.fabrication.buffer.catalyst_types",()->value);
                method.invoke(screen,"assembly_catalyst_count","assembly_catalyst_count_icon",value);
                var g=new InkRecorder(context);ui.foreground(g,context.font);
                var number=g.exact(value.getString());var hint=g.exact(Component.translatable(
                        "gui.molecularmanipulator.fabrication.buffer.catalyst_location").getString());
                assertEquals(7.5f/9,number.scale,.001f);assertEquals(7.5f/9,hint.scale,.001f);
                assertEquals(220,number.center(),.8f);assertEquals(232,hint.center(),.8f);
                assertTrue(number.bottom+3<=hint.top);assertTrue(hint.bottom+8<=ui.layout.find("inv").y);
            }
        }
    }

    @Test void omniComputeMaterialPreservesReadableLiveValuesAndActivityInBothLayouts() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(String page:List.of("omni_core","omni_core_legacy"))for(boolean active:new boolean[]{false,true}){
                var ui=new OsqUi(page);
                ui.number(252,147,()->active?43:0);ui.number(314,147,()->8);
                var activity=Component.translatable("gui.molecularmanipulator.omni."+(active?"calculating":"calculation_idle"),7);
                var stats=Component.translatable("gui.molecularmanipulator.omni.calculation_stats",726,53);
                ui.text("gui.molecularmanipulator.omni.calculating",()->activity);
                ui.text("gui.molecularmanipulator.omni.calculation_stats",()->stats);
                var g=new InkRecorder(context);ui.foreground(g,context.font);
                var card=ui.layout.find("compute");var numbers=List.of(g.exact(active?"43":"0"),g.exact("8"));
                var entries=new ArrayList<>(numbers);entries.add(g.exact(activity.getString()));entries.add(g.exact(stats.getString()));
                for(var ink:entries){assertTrue(ink.left>=card.x+8&&ink.right<=card.x+card.w-8);assertTrue(ink.bottom<card.y+card.h-8);assertFalse(ink.text.endsWith("…"));}
                for(int a=0;a<entries.size();a++)for(int b=a+1;b<entries.size();b++)assertFalse(entries.get(a).overlaps(entries.get(b)));
            }
        }
    }

    @Test void researchTimeAndPowerKeepTheirIconsSeparateAndVisuallyCenteredWithLiveValues() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var menu=ResponsiveScreenTestFixture.allocate(com.atir.molecularmanipulator.menu.MatterFabricationMenu.class);
            menu.researchState=new com.atir.molecularmanipulator.research.MatterResearchMenuState("{\"completed\":[],\"tasks\":{}}");
            var panel=new MatterResearchPanel(menu,context.font,0,0,button->{});var ui=(OsqUi)field(panel,"ui");
            var timer=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&e.x==222&&e.baseline==124).findFirst().orElseThrow();
            var power=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&Math.abs(e.x-265.11f)<.01f&&e.baseline==124).findFirst().orElseThrow();
            var clock=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.endsWith("/clock_mute_6")).findFirst().orElseThrow();
            var bolt=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.endsWith("/bolt_mute_6")).findFirst().orElseThrow();
            ui.visible=e->e==timer||e==power;
            var id=ResourceLocation.parse("molecularmanipulator:research/ae_foundation");
            for(int duration:List.of(600,72000))for(int progress:List.of(0,240,duration))for(int cost:List.of(0,256,2048,1000000)){
                var definition=new com.atir.molecularmanipulator.research.MatterResearchRecipe("research.molecularmanipulator.ae_foundation",
                        List.of(),List.of(),duration,cost,List.of(),List.of(),0,1);
                var holder=new net.minecraft.world.item.crafting.RecipeHolder<>(id,definition);
                ResponsiveScreenTestFixture.set(panel,"selectedDefinition",holder);
                ResponsiveScreenTestFixture.set(panel,"selected",id);
                var task=new com.google.gson.JsonObject();task.addProperty("progress",progress);
                var tasks=new com.google.gson.JsonObject();tasks.add(id.toString(),task);ResponsiveScreenTestFixture.set(panel,"tasks",tasks);
                var g=new InkRecorder(context);ui.foreground(g,context.font);
                var elapsed=g.exact(progress/20+" / "+duration/20+" s");
                var energy=g.exact(DisplayNumbers.compact(cost)+" AE/t");
                assertEquals(2,g.ink.size(),"Each live value must appear exactly once");
                for(int i=0;i<2;i++){
                    var icon=List.of(clock,bolt).get(i);var text=List.of(timer,power).get(i);var ink=List.of(elapsed,energy).get(i);
                    float x=icon.drawX==null?icon.x:icon.drawX,y=icon.drawY==null?icon.y:icon.drawY;
                    var pixels=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(icon.sprite);
                    assertEquals(6,icon.w);assertEquals(6,icon.h);assertEquals(7.5f/9,ink.scale,.001f);
                    assertEquals(126,y+icon.h*pixels.y(),.001f,"The visible icon, not its transparent canvas, sets the center");
                    assertEquals(y+icon.h*pixels.y(),ink.center(),.8f,language+" "+ink);
                    assertTrue(x+icon.w+3<=ink.left,"Reserve a real gap after the complete icon canvas");
                    assertTrue(ink.right<=text.drawX+text.w+.01f);
                    var card=ui.layout.find("detailcard");
                    assertTrue(x>=card.x+8&&ink.right<=card.x+card.w-8);
                    assertTrue(ink.bottom<=card.y+card.h-4);
                    assertTrue(y+icon.h<=card.y+card.h-4);
                }
                assertTrue(timer.drawX+timer.w+7<=bolt.drawX,"The energy icon cannot drift back into the time column");
                assertTrue(elapsed.right+7<=bolt.drawX);
            }
        }
    }

    @Test void researchOrderStatusUsesOneLiveLabelAndTheDotsVisibleCenterInEveryState() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var menu=ResponsiveScreenTestFixture.allocate(com.atir.molecularmanipulator.menu.MatterFabricationMenu.class);
            menu.researchState=new com.atir.molecularmanipulator.research.MatterResearchMenuState("{\"completed\":[],\"tasks\":{}}");
            var panel=new MatterResearchPanel(menu,context.font,0,0,button->{});
            var field=MatterResearchPanel.class.getDeclaredField("ui");field.setAccessible(true);var ui=(OsqUi)field.get(panel);
            ResponsiveScreenTestFixture.set(panel,"visible",true);
            var locale=JsonParser.parseString(Files.readString(ASSETS.resolve("lang/"+language+".json"))).getAsJsonObject();
            var states=locale.keySet().stream().filter(key->key.startsWith("gui.molecularmanipulator.research.order_state."))
                    .map(key->key.substring(key.lastIndexOf('.')+1)).toList();
            for(String state:states){
                ResponsiveScreenTestFixture.set(panel,"orders",new MatterResearchOrders.View(List.of(),List.of(),0,0,state.equals("submitted")?1:0,state));
                var g=new InkRecorder(context);panel.drawForeground(g);
                String message=Component.translatable("gui.molecularmanipulator.research.order_state."+state).getString();
                var text=g.containing(message);assertEquals(message,text.text);assertEquals(7.5f/9,text.scale,.001f);
                assertEquals(1,g.ink.stream().filter(i->i.text.equals(message)).count(),"The old manual status must not remain");
                var dot=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/dot_")&&e.x==17&&e.y==212).findFirst().orElseThrow();
                var ink=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(ui.sprite("dot_"+(state.equals("idle")?"mute":state.equals("submitted")?"ok":"warn")));
                assertEquals(dot.drawY+dot.h*ink.y(),text.center(),.8f,state+" "+language);
            }
        }
    }

    @Test void portsReplaceTheirWholeSourceButtonWithoutDrawingAnExtraOutline() throws Exception {
        try(var context=new Context(false,"zh_cn")){
            for(String page:List.of("fab_port_item","fab_port_fluid")){
                var ui=new OsqUi(page);var box=ui.layout.findControl(page.endsWith("fluid")?"auto":"return");
                ui.button(box.id,0,0,()->Component.translatable("gui.molecularmanipulator.fabrication.port.return_ae"),()->{});
                var field=OsqUi.class.getDeclaredField("hidden");field.setAccessible(true);
                var hidden=(java.util.Set<?>)field.get(ui);
                for(var entry:ui.layout.draw)if(ui.layout.owns(box,entry))assertTrue(hidden.contains(entry),"Every static control layer is replaced");
            }
        }
        var source=Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/MatterFabricationPortScreen.java"));
        assertFalse(source.contains("ui.outline("),"A separate fixed outline duplicates the depressed AE face");
    }

    @Test void handheldControlsAndSealParagraphUseReadableUnclippedTextInBothLanguages() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(String name:List.of("star_chart_calibrator","star_chart_seal","star_chart_details")){
                var ui=new OsqUi(name);
                for(var control:ui.layout.draw.stream().filter(e->"ctl".equals(e.kind)).toList()){
                    assertTrue(control.h>=22,name+" "+control.id);
                    var label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&control.id.equals(e.ownerId)).findFirst().orElseThrow();
                    float width=OsqFonts.widthF(context.font,label.staticText().getString(),OsqFonts.id(label.fontId),label.size);
                    assertTrue(width<=control.w-8+.5f,language+" "+name+" "+label.staticText().getString()+" width="+width);
                }
                var body=ui.layout.findText("star_chart.molecularmanipulator.seal_contents");
                if(body!=null){
                    var g=new InkRecorder(context);ui.layout.renderText(g,context.font,body,body.staticText(),0,0);
                    for(var line:g.ink){assertTrue(line.bottom<228);assertEquals(body.size/9,line.scale,.001f);}
                }
            }
        }
    }

    @Test void handheldPanelsFitSmallViewportsAndInverseTransformEveryPointerEvent() throws Exception {
        try(var context=new Context(false,"en_us")){
            for(String name:List.of("star_chart_calibrator","star_chart_seal","star_chart_details"))for(int[] view:new int[][]{{320,240},{567,240},{854,480}}){
                var panel=ResponsiveScreenTestFixture.allocate(HandheldProbe.class);
                panel.ui=new OsqUi(name);panel.width=view[0];panel.height=view[1];
                ResponsiveScreenTestFixture.set(panel,"docked",!name.endsWith("details"));
                ResponsiveScreenTestFixture.set(panel,"children",new ArrayList<>());
                ResponsiveScreenTestFixture.set(panel,"renderables",new ArrayList<>());
                ResponsiveScreenTestFixture.set(panel,"narratables",new ArrayList<>());
                float scale=panel.panelScale();
                assertTrue(panel.panelX()>=3.9&&panel.panelY()>=3.9);
                assertTrue(panel.panelX()+panel.ui.layout.width*scale<=panel.width-3.9);
                assertTrue(panel.panelY()+panel.ui.layout.height*scale<=panel.height-3.9);
                var input=new PointerProbe();panel.add(input);
                double x=panel.panelX()+20*scale,y=panel.panelY()+30*scale;
                panel.mouseMoved(x,y);assertEquals(20,input.x,.001);assertEquals(30,input.y,.001);
                assertTrue(panel.mouseClicked(x,y,0));assertSame(input,panel.getFocused());
                assertTrue(panel.mouseDragged(x+4*scale,y+2*scale,0,4*scale,2*scale));
                assertEquals(24,input.x,.001);assertEquals(32,input.y,.001);
                assertEquals(4,input.dx,.001);assertEquals(2,input.dy,.001);
                assertTrue(panel.mouseScrolled(x,y,0,-1));assertEquals(-1,input.scroll);
                assertTrue(panel.mouseReleased(x,y,0));assertEquals(20,input.x,.001);assertEquals(30,input.y,.001);
                assertFalse(panel.isPauseScreen());
            }
        }
    }

    @Test void archiveDetailsKeepAllElevenServerRowsAndTheirSizesAndOffsetsSeparate() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var screen=ResponsiveScreenTestFixture.allocate(StarChartClient.DetailsScreen.class);
            screen.ui=new OsqUi("star_chart_details");ResponsiveScreenTestFixture.set(screen,"font",context.font);
            var entries=new ArrayList<com.atir.molecularmanipulator.network.BuildingPayloads.DetailEntry>();
            for(int i=0;i<com.atir.molecularmanipulator.network.BuildingPayloads.DETAILS_PAGE_ENTRIES;i++)entries.add(
                    new com.atir.molecularmanipulator.network.BuildingPayloads.DetailEntry(new net.minecraft.core.BlockPos(-30000000,320,30000000),
                            net.minecraft.world.level.block.Block.getId(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()),Long.MAX_VALUE));
            ResponsiveScreenTestFixture.set(screen,"data",new com.atir.molecularmanipulator.network.BuildingPayloads.Details(
                    java.util.UUID.randomUUID(),0,2,1000,Long.MAX_VALUE,entries,false));
            var g=new InkRecorder(context);screen.drawContents(g,-1,-1);
            assertEquals(33,g.ink.size());
            for(int a=0;a<g.ink.size();a++){
                var ink=g.ink.get(a);assertEquals(7.5f/9,ink.scale,.001f);assertTrue(ink.bottom<344);
                for(int b=a+1;b<g.ink.size();b++)assertFalse(ink.overlaps(g.ink.get(b)),ink+" / "+g.ink.get(b));
            }
        }
    }

    @Test void terminalRowsKeepLongNamesStatusDotsAndRemoveButtonsSeparate() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var screen=ResponsiveScreenTestFixture.allocate(BuildingTerminalScreen.class);
            var ui=new OsqUi("building_access_terminal");
            ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"font",context.font);
            var rowType=Class.forName(BuildingTerminalScreen.class.getName()+"$RowView");
            var constructor=rowType.getDeclaredConstructors()[0];constructor.setAccessible(true);
            for(var state:com.atir.molecularmanipulator.menu.BuildingTerminalMenu.Status.values())for(boolean oversized:new boolean[]{false,true}){
                var rows=new ArrayList<>();var name=oversized?Component.literal("很长的建筑名称 Long building controller 😀".repeat(4))
                        :Component.translatable("block.molecularmanipulator.matter_fabrication_controller");
                for(int row=0;row<5;row++){
                    var entry=new com.atir.molecularmanipulator.menu.BuildingTerminalMenu.Entry(
                            net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD,new net.minecraft.core.BlockPos(-30000000,320,30000000)),
                            ResourceLocation.withDefaultNamespace("stone"),state);
                    rows.add(constructor.newInstance(entry,new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.STONE),name,
                            Component.literal("Overworld (-30000000, 320, 30000000)")));
                }
                ResponsiveScreenTestFixture.set(screen,"rows",rows);
                var g=new InkRecorder(context);invoke(screen,"drawIcons",g);invoke(screen,"drawRows",g);
                assertEquals(5,g.items.size());
                for(int row=0;row<5;row++){
                    var box=ui.layout.find("card."+row);float y=box.y;
                    var texts=g.ink.stream().filter(i->i.top>=y&&i.bottom<y+box.h&&!i.text.equals("?")).toList();
                    assertEquals(3,texts.size());
                    var title=texts.get(0);var detail=texts.get(1);var status=texts.get(2);
                    assertEquals(oversized,title.text.endsWith("…"));if(!oversized)assertEquals(name.getString(),title.text);
                    assertEquals(Component.translatable("gui.molecularmanipulator.building_terminal.status."+state.name().toLowerCase(java.util.Locale.ROOT)).getString(),status.text);
                    assertEquals(g.items.get(row).y()+8,title.center(),.8,language+" icon and title");
                    assertTrue(g.items.get(row).x()+22<=title.left,language+" icon to title gap");
                    var remove=ui.layout.findControl("remove."+row);assertTrue(title.bottom+3<remove.y);
                    var removeButton=ui.button("remove."+row,0,0,()->Component.translatable("gui.molecularmanipulator.building_terminal.action.remove"),()->{});
                    ui.state(removeButton,()->"danger");var buttonInk=new InkRecorder(context);renderButtonContents(removeButton,buttonInk);
                    var removeLabel=buttonInk.exact(removeButton.getMessage().getString());
                    assertEquals(removeLabel.center(),status.center(),.8,language+" status and Remove");
                    assertEquals(removeLabel.center(),detail.center(),.8,language+" position and Remove");
                    var dot=g.rects.get(row);assertEquals(status.center(),(dot.top()+dot.bottom())/2f,.8);
                    assertTrue(dot.right()+3<=status.left);assertTrue(detail.right+5<=dot.left());
                    assertTrue(status.right<=remove.x-5);assertTrue(detail.top>=y+26);
                    for(var text:texts){assertEquals(7.5f/9,text.scale,.001f);assertTrue(text.left>=box.x+7&&text.right<=box.x+box.w-7);}
                    for(int a=0;a<texts.size();a++)for(int b=a+1;b<texts.size();b++)assertFalse(texts.get(a).overlaps(texts.get(b)));
                }
            }
            var help=new InkRecorder(context);invoke(screen,"drawHelp",help);
            for(int a=0;a<help.ink.size();a++){
                assertTrue(help.ink.get(a).bottom<=308,language+" "+help.ink.get(a));
                assertEquals(7.5f/9,help.ink.get(a).scale,.001f);
                for(int b=a+1;b<help.ink.size();b++)assertFalse(help.ink.get(a).overlaps(help.ink.get(b)));
            }
        }
    }

    @Test void singularityDiagnosticsCenterTheWholeDataHintAndEffectsGroupInsideTheVisibleCard() throws Exception {
        var setting=com.atir.molecularmanipulator.config.ModConfig.DYNAMIC_EFFECT_LEVEL;var saved=field(setting,"cachedValue");
        try{
            ResponsiveScreenTestFixture.set(setting,"cachedValue",2);
            for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
                var ui=new OsqUi("sing_overview");var card=ui.layout.find("inspect");
                var image=javax.imageio.ImageIO.read(ASSETS.resolve("textures/gui/sprites/"+card.sprite.split(":",2)[1]+".png").toFile());
                int top=-1,bottom=-1;
                for(int y=0;y<image.getHeight();y++)if((image.getRGB(image.getWidth()/2,y)>>>24)>=230){if(top<0)top=y;bottom=y;}
                assertTrue(top>=0&&bottom>top);
                float frameTop=card.y-card.pad[1]+top/4f,frameBottom=card.y-card.pad[1]+(bottom+1)/4f;
                assertEquals(116f,frameTop);assertEquals(210f,frameBottom);
                var screen=ResponsiveScreenTestFixture.allocate(SingularityScreen.class);
                var menu=ResponsiveScreenTestFixture.allocate(com.atir.molecularmanipulator.menu.SingularityMenu.class);
                screenState(screen,menu,ui,context.font);
                // Transient cancellation/migration actions stay above diagnostics, in the status card's free row.
                ui.rect(173,40,68,17,0,0,Component::empty,()->{});
                ui.rect(246,40,87,17,0,0,Component::empty,()->{});
                invoke(screen,"initEffectControl");
                var effects=(MachineEffectButton)field(screen,"effects");assertEquals(160,effects.getWidth());assertEquals(18,effects.getHeight());
                ui.number(172.5f,166.5f,()->13301);ui.number(225.5f,166.5f,()->3);ui.number(278.5f,166.5f,()->24);
                ui.width(172.5f,166.5f,42);ui.width(225.5f,166.5f,42);ui.width(278.5f,166.5f,48);
                var divider=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.endsWith("/divider_dark")&&e.x==173).findFirst().orElseThrow();
                var g=new InkRecorder(context);ui.foreground(g,context.font);float groupTop=Float.POSITIVE_INFINITY;
                for(int i=0;i<3;i++){
                    String key="gui.molecularmanipulator.sing.s0"+(22+i);var entry=ui.layout.findText(key);
                    var heading=g.exact(Component.translatable(key).getString());var digits=g.exact(List.of("13301","3","24").get(i));
                    assertEquals(148.5f,entry.baseline);assertEquals(7.5f/9,heading.scale,.001f);
                    assertTrue(ui.layout.owns(card,entry));
                    assertTrue(heading.bottom+3<=digits.top);assertTrue(digits.bottom+4<=divider.y);
                    groupTop=Math.min(groupTop,heading.top);
                }
                var hint=g.exact(Component.translatable("gui.molecularmanipulator.singularity.build_hint").getString());
                assertEquals(7.5f/9,hint.scale,.001f);
                assertTrue(divider.y+divider.h+2.5f<=hint.top,language+" divider to hint: "+hint);
                assertTrue(hint.bottom+5<=effects.getY(),language+" hint to effects: "+hint);
                assertEquals(groupTop-frameTop,frameBottom-effects.getBottom(),1f,language+" whole-group top/bottom inset");
                assertTrue(groupTop-frameTop>=13&&frameBottom-effects.getBottom()>=13);
                for(int level:List.of(-1,0,1,2)){
                    menu.dynamicEffectOverride=level;effects.refresh();var buttonInk=new InkRecorder(context);renderButtonContents(effects,buttonInk);
                    var label=buttonInk.exact(effects.getMessage().getString());assertEquals(7.5f/9,label.scale,.001f);
                    assertTrue(label.top>=effects.getY()&&label.bottom<=effects.getBottom());
                }
            }
        }finally{ResponsiveScreenTestFixture.set(setting,"cachedValue",saved);}
    }

    @Test void singularityDuplicationShowsFullHeaderAndBothMetricsAfterHiddenControlsAreRegistered() throws Exception {
        var setting=com.atir.molecularmanipulator.config.ModConfig.DYNAMIC_EFFECT_LEVEL;var saved=field(setting,"cachedValue");
        try{
            ResponsiveScreenTestFixture.set(setting,"cachedValue",2);
            for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
                for(boolean compact:List.of(false,true)){
                    var ui=new OsqUi("sing_duplication");
                    com.atir.molecularmanipulator.client.gui.theme.OsqSingularityPages.configure(ui.layout,compact);
                    var screen=ResponsiveScreenTestFixture.allocate(SingularityScreen.class);
                    var menu=ResponsiveScreenTestFixture.allocate(com.atir.molecularmanipulator.menu.SingularityMenu.class);
                    screenState(screen,menu,ui,context.font);ResponsiveScreenTestFixture.set(screen,"duplicationPage",true);
                    var init=SingularityScreen.class.getDeclaredMethod("initReclaimControls",boolean.class);init.setAccessible(true);init.invoke(screen,true);
                    ui.rect(246,40,87,17,0,0,Component::empty,()->{});invoke(screen,"initEffectControl");invoke(screen,"bindDuplication");
                    var hole=ui.layout.find("hole");var rate=ui.layout.find("rate");var divider=ui.layout.find("metrics_divider");
                    var input=ui.layout.draw.stream().filter(e->"slot".equals(e.kind)&&"hole".equals(e.id)).findFirst().orElseThrow();
                    var slot=new net.minecraft.world.inventory.Slot(new net.minecraft.world.SimpleContainer(1),0,0,0);ui.slots(List.of(slot),"hole");
                    assertEquals(18,input.w);assertEquals(18,input.h);assertEquals(input.x+1,slot.x);assertEquals(input.y+1,slot.y);
                    var generator=ui.layout.findText("gui.molecularmanipulator.singularity.duplication_black_hole");
                    var generatorInk=new InkRecorder(context);ui.layout.renderText(generatorInk,context.font,generator,generator.staticText(),0,0);
                    var label=generatorInk.exact(generator.staticText().getString());
                    assertEquals(hole.y+hole.h/2,label.center(),.8f);assertEquals(label.center(),input.y+input.h/2,.8f);
                    assertTrue(input.x+input.w+5<=label.left);assertTrue(label.right<=hole.x+hole.w-8);
                    assertEquals(7.5f/9,label.scale,.001f);
                    var headings=new InkRecorder(context);
                    for(String key:List.of("block.molecularmanipulator.singularity_sequence_matter","gui.molecularmanipulator.sing.s014")){
                        var entry=ui.layout.draw.stream().filter(e->key.equals(e.lang)&&"rate".equals(e.ownerId)).findFirst().orElseThrow();
                        var text=new InkRecorder(context);ui.layout.renderText(text,context.font,entry,entry.staticText(),0,0);
                        assertEquals(entry.staticText().getString(),String.join(" ",text.ink.stream().map(Ink::text).toList()));
                        for(var ink:text.ink){
                            assertEquals(7.5f/9,ink.scale,.001f);assertFalse(ink.text.endsWith("…"));
                            assertTrue(ink.top>=rate.y+6&&ink.bottom<=174,language+" "+ink);
                            assertTrue(ink.left>=(entry.drawX==null?entry.x:entry.drawX));
                        }
                        headings.ink.addAll(text.ink);
                    }
                    for(boolean online:List.of(false,true))for(int count:new int[]{0,9,10,64})for(int matter:new int[]{0,1280,999990,64000000})for(int interval:new int[]{1,20,1200}){
                        menu.networkOnline=online;menu.blackHoleCount=count;menu.duplicationMatterPerCycle=matter;menu.duplicationIntervalTicks=interval;
                        menu.duplicationEnergyPriority="FE → AE";
                        ui.text("gui.molecularmanipulator.singularity.collection_network",()->Component.translatable(
                                "gui.molecularmanipulator.singularity.collection_network",Component.translatable("gui.molecularmanipulator.singularity."+(menu.networkOnline?"online":"offline"))));
                        var body=new InkRecorder(context);ui.foreground(body,context.font);
                        String sample=language+" compact="+compact+" online="+online+" count="+count+" matter="+matter+" interval="+interval;
                        for(String key:List.of("duplication_title","duplication_status","collection_network","duplication_energy")){
                            var entry=ui.layout.findText("gui.molecularmanipulator.singularity."+key);
                            var message=switch(key){
                                case "collection_network"->Component.translatable(entry.lang,Component.translatable("gui.molecularmanipulator.singularity."+(online?"online":"offline")));
                                case "duplication_energy"->Component.translatable(entry.lang,menu.duplicationEnergyPriority);
                                default->entry.staticText();
                            };
                            var ink=body.exact(message.getString());assertEquals(entry.size/9,ink.scale,.001f,sample+" "+ink);
                            assertTrue(ink.left>=16&&ink.right<=334,sample+" "+ink);
                        }
                        var number=body.ink.stream().filter(i->i.text.equals(Integer.toString(count))&&i.left>=264&&i.scale>1).findFirst().orElseThrow();
                        var maximum=body.exact("/ 64");var quantity=body.ink.stream().filter(i->i.text.equals(OsqUi.compact(matter))&&i.left<divider.x&&i.scale>1).findFirst().orElseThrow();
                        assertEquals(13f/9,number.scale,.001f);assertEquals(16f/9,quantity.scale,.001f);
                        assertTrue(number.right+4<=maximum.left,sample);assertEquals(number.center(),maximum.center(),.8f);
                        var unit=body.exact(Component.translatable("gui.molecularmanipulator.sing.s015").getString());
                        assertTrue(quantity.bottom+4<=unit.top,sample+" "+quantity+" / "+unit);
                        assertTrue(quantity.right+5<=divider.x,sample+" "+quantity);assertTrue(maximum.right<=rate.x+rate.w-8);
                        var step=body.ink.stream().filter(i->i.text.equals(Integer.toString(interval))&&i.left>=287&&i.scale<1).findFirst().orElseThrow();
                        var tick=body.exact("tick");assertTrue(step.right+3<=tick.left,sample);assertTrue(tick.right<=334);
                        assertTrue(number.bottom+5<=step.top,sample);assertTrue(unit.bottom<=rate.y+rate.h-7,sample);
                        for(var heading:headings.ink)assertTrue(heading.bottom+3<=Math.min(number.top,quantity.top),sample+" "+heading);
                        for(int a=0;a<body.ink.size();a++)for(int b=a+1;b<body.ink.size();b++)
                            assertFalse(body.ink.get(a).overlaps(body.ink.get(b)),sample+" "+body.ink.get(a)+" / "+body.ink.get(b));
                        for(var ink:body.ink)assertFalse(ink.text.endsWith("…"),sample+" "+ink);
                    }
                }
            }
        }finally{ResponsiveScreenTestFixture.set(setting,"cachedValue",saved);}
    }

    @Test void singularityDuplicationKeepsTheCompleteMatterNameClearOfBothFlowLines() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(boolean compact:List.of(false,true)){
                var ui=new OsqUi("sing_duplication");
                com.atir.molecularmanipulator.client.gui.theme.OsqSingularityPages.configure(ui.layout,compact);
                var title=ui.layout.findText("block.molecularmanipulator.singularity_sequence_matter");
                var lines=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.endsWith("/flow_line"))
                        .sorted(java.util.Comparator.comparingDouble(e->e.x)).toList();
                assertEquals(2,lines.size());var left=lines.getFirst();var right=lines.getLast();
                var icon=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/net_")&&e.y>80).findFirst().orElseThrow();
                var message=Component.translatable(title.lang);var g=new InkRecorder(context);
                ui.layout.renderSingleLine(g,context.font,title,message,0,0);
                var name=g.exact(message.getString());assertEquals(1,g.ink.size());
                String sample=language+" compact="+compact+" "+name;
                assertEquals(9f,title.size);assertEquals(1f,name.scale,.001f);assertEquals(113f,name.center(),.8f);
                assertTrue(left.x+left.w+8<=name.left,sample);
                assertTrue(name.right+8<=right.x,sample);
                assertTrue(OsqLayout.textX(title,title.w)+title.w+8<=right.x,sample+" text budget");
                assertTrue(right.w>=12);assertTrue(right.x+right.w+10<=icon.x);
                for(var line:lines)assertEquals(113f,line.y+line.h/2,.001f);
            }
        }
    }

    @Test void liveAssemblyCatalystMetadataMovesItsDrawingOverrideAndCentersTheVisibleIcon() throws Exception {
        try(var context=new Context(false,"zh_cn")){
            var screen=ResponsiveScreenTestFixture.allocate(MatterFabricationPatternAssemblyScreen.class);
            var ui=new OsqUi("fab_assembly_catalyst");
            ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"font",context.font);
            var value=Component.translatable("gui.molecularmanipulator.fabrication.buffer.catalyst_types",Integer.MAX_VALUE);
            var method=screen.getClass().getDeclaredMethod("centerMetadata",String.class,String.class,Component.class);method.setAccessible(true);
            method.invoke(screen,"assembly_catalyst_count","assembly_catalyst_count_icon",value);
            var label=ui.layout.find("assembly_catalyst_count");var icon=ui.layout.find("assembly_catalyst_count_icon");
            var ink=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(icon.sprite);
            assertEquals(label.centerY,icon.drawY+ink.y()*icon.h,.001);
            assertNotNull(label.drawX);assertTrue(label.drawX>icon.drawX+icon.w*ink.x());
            var hint=ui.layout.find("assembly_catalyst_hint");assertTrue(label.centerY+label.size/2+3<=hint.centerY-hint.size/2);
        }
    }

    @Test void everyPageWithRealFontsHasNoCollisionsOrClippedLabelsInEitherLanguage() throws Exception {
        int pages = 0;
        var truncated=new ArrayList<String>();
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){
                for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()){
                    String name=path.getFileName().toString().replace(".json","");
                    var ui=new OsqUi(name);
                    ui.visible=e->!"tip".equals(e.kind)&&!"pop".equals(e.kind)&&ui.layout.layerOf(e)==null;
                    var controls=ui.layout.draw.stream().filter(e->"ctl".equals(e.kind)||"edge".equals(e.kind)).toList();
                    ui.hide(e->controls.stream().anyMatch(control->ui.layout.owns(control,e)));
                    // Mirror the real screens' replacement of demonstration rows.
                    if(name.equals("fab_port_fluid")){
                        ui.number(96,89,()->"2,147,483,647 mB");ui.hide(e->"mB".equals(e.text));
                        ui.omit("gui.molecularmanipulator.fab.s031");
                    }
                    if(name.equals("fab_research_detail")){
                        ui.at(213,151,()->Component.translatable("gui.molecularmanipulator.research.order_missing_count",3));
                        ui.width(213,151,98);ui.at(263.88f,151,Component::empty);ui.at(271.98f,151,Component::empty);
                    }
                    if(name.equals("sing_collection")){
                        ui.at(17,102,()->Component.translatable("gui.molecularmanipulator.singularity.collection_resources",16,1000,20));
                        ui.width(17,102,225);ui.number(55.34f,102,()->"");ui.number(108.94f,102,()->"");
                        ui.at(64.5f,102,Component::empty);ui.at(125.8f,102,Component::empty);
                    }
                    var g=new InkRecorder(context);ui.foreground(g,context.font);
                    String sample=language+" "+name;
                    for(int a=0;a<g.ink.size();a++)for(int b=a+1;b<g.ink.size();b++)
                        assertFalse(g.ink.get(a).overlaps(g.ink.get(b)),sample+" "+g.ink.get(a)+" / "+g.ink.get(b));
                    for(var ink:g.ink)if(ink.text.endsWith("…"))truncated.add(sample+" "+ink);
                    pages++;
                }
            }
        }
        assertEquals(50,pages);
        assertTrue(truncated.isEmpty(),String.join("\n",truncated));
    }

    @Test void fixedButtonLabelsFitTheirLiveIconAndPaddingBudgetsInBothLanguages() throws Exception {
        var truncated=new ArrayList<String>();
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){
                for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()){
                    var ui=new OsqUi(path.getFileName().toString().replace(".json",""));
                    for(var box:ui.layout.draw.stream().filter(e->"ctl".equals(e.kind)&&e.aeIcon==null
                            &&e.sprite!=null&&!e.sprite.contains("field_")&&!e.sprite.contains("tab_")).toList()){
                        var label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&!e.dynamic&&e.langArgs==null&&ui.layout.owns(box,e)).findFirst().orElse(null);
                        if(label==null||label.staticText().getString().isBlank())continue;
                        var message=label.staticText();var button=ui.button(box.id,0,0,()->message,()->{});
                        for(String state:List.of("normal","hover","disabled")){
                            float iconSpan=0;var cache=new OsqButtonIconCache();
                            for(var icon:ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/")&&ui.layout.owns(box,e)).toList()){
                                String tint=state.equals("disabled")?"dis":state.equals("hover")?"ink":"white";
                                String sprite=cache.resolve(OsqFonts.resourceGeneration(),icon.sprite,null,tint,
                                        id->context.resources.getResource(ResourceLocation.parse(id).withPrefix("textures/gui/sprites/").withSuffix(".png")).isPresent());
                                var ink=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(sprite);
                                iconSpan+=ink.fitWidth(icon.w,icon.h)*ink.width()+3;
                            }
                            boolean icons=iconSpan>0;if(icons)iconSpan-=3;
                            float inset=button.getWidth()<=64?4:7,gap=icons?(button.getWidth()<=64?3:6):0;
                            float originalWidth=label.w,available=button.getWidth()-2*inset-iconSpan-gap;label.w=available;
                            var g=new InkRecorder(context);ui.layout.renderSingleLine(g,context.font,label,message,0,0);label.w=originalWidth;
                            float width=OsqFonts.widthF(context.font,message.getString(),OsqFonts.id(label.fontId),label.size)
                                    +Math.max(0,message.getString().codePointCount(0,message.getString().length())-1)*label.letterSpacing;
                            if(width*Math.min(1,7.5f/label.size)>available+.5f||g.ink.stream().anyMatch(ink->ink.text.endsWith("…")))
                                truncated.add(language+" "+ui.layout.screen+" "+box.id+" "+state+" expected="+message.getString()+" width="+width+" available="+available+" actual="+g.ink);
                        }
                    }
                }
            }
        }
        assertTrue(truncated.isEmpty(),String.join("\n",truncated));
    }

    @Test void autoCrafterDirectionPageMovesInventoryWithItsFrameAndRestoresTheCompactPage() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var screen=ResponsiveScreenTestFixture.allocate(MolecularAutoCrafterScreen.class);
            var container=new net.minecraft.world.SimpleContainer(36);
            var slots=java.util.stream.IntStream.range(0,36)
                    .mapToObj(i->new net.minecraft.world.inventory.Slot(container,i,0,0)).toList();
            // init reloads the source layout on every toggle, Escape and resize; no offsets accumulate.
            for(boolean open:new boolean[]{false,true,true,false,true,false}){
                var ui=new OsqUi("seq_autocrafter");
                ResponsiveScreenTestFixture.set(screen,"ui",ui);
                ResponsiveScreenTestFixture.set(screen,"directionsOpen",open);
                invoke(screen,"configureDirectionPage");ui.slots(slots,"inv");
                int height=open?214:200;assertEquals(height,field(screen,"imageHeight"));
                var inventory=ui.layout.find("inv");var frame=ui.layout.find("window");
                var content=ui.layout.find(open?"dirs":"slots");
                assertEquals(content.y+content.h+6,inventory.y);
                assertEquals(height-8,inventory.y+inventory.h);
                assertEquals(height+2,frame.h);
                var boxes=ui.entries("inv").stream().filter(e->"slot".equals(e.kind)).toList();
                assertEquals(36,boxes.size());
                for(int i=0;i<slots.size();i++){
                    var slot=slots.get(i);var box=boxes.get(i);
                    assertEquals(box.x+1,slot.x);assertEquals(box.y+1,slot.y);
                    assertTrue(slot.y>=inventory.y+6&&slot.y+16<=inventory.y+inventory.h-6);
                }
                for(int[] viewport:new int[][]{{854,480},{320,200}}){
                    screen.width=viewport[0];screen.height=viewport[1];
                    int left=(screen.width-ui.layout.width)/2,top=(screen.height-height)/2;
                    ResponsiveScreenTestFixture.set(screen,"leftPos",left);ResponsiveScreenTestFixture.set(screen,"topPos",top);
                    for(String id:OsqNeighborDirections.IDS){
                        var button=ui.button(id,left,top,()->Component.literal("F"),()->{});
                        assertEquals(22,button.getWidth());assertEquals(22,button.getHeight());
                        if(open)assertTrue(button.getY()>=top+content.y+8&&button.getBottom()<=top+content.y+content.h-8);
                        int x=screen.responsiveScreenX(button.getX()+11),y=screen.responsiveScreenY(button.getY()+11);
                        assertTrue(button.isMouseOver(screen.logicalMouseX(x),screen.logicalMouseY(y)));
                    }
                    var bounds=screen.responsiveBounds();
                    assertTrue(bounds.getX()>=3&&bounds.getY()>=3);
                    assertTrue(bounds.getX()+bounds.getWidth()<=screen.width-3&&bounds.getY()+bounds.getHeight()<=screen.height-3);
                }
            }
        }
    }

    @Test void emptyNeighborButtonsUseCompleteCompactDirectionsAndKeepTheirFullTooltips() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(String page:List.of("fab_port_item","fab_port_fluid","seq_autocrafter")){
                var ui=new OsqUi(page);
                for(var facing:net.minecraft.core.Direction.Plane.HORIZONTAL)for(int i=0;i<OsqNeighborDirections.OUTPUT_SIDES.length;i++){
                    var relative=OsqNeighborDirections.OUTPUT_SIDES[i];
                    var block=net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,facing);
                    var side=OsqNeighborDirections.worldSide(block,relative);var box=ui.layout.findControl(OsqNeighborDirections.IDS[i]);
                    var message=OsqNeighborDirections.label(relative);
                    assertEquals(1,message.getString().codePointCount(0,message.getString().length()));
                    var button=ui.button(box.id,0,0,()->message,()->{});ui.itemIcon(button,()->net.minecraft.world.item.ItemStack.EMPTY);
                    var hint=OsqNeighborDirections.tooltip(null,net.minecraft.core.BlockPos.ZERO,relative,side,0);
                    assertTrue(hint.getString().contains(Component.translatable("gui.molecularmanipulator.fabrication.port.side."+side.getName()).getString()));
                    assertFalse(hint.getString().contains("ae2."),"Full block-relative side names must resolve through AE2's real locale");
                    var tooltip=net.minecraft.client.gui.components.Tooltip.create(hint);button.setTooltip(tooltip);
                    for(String state:List.of("normal","hover","active","disabled")){
                        button.active=!state.equals("disabled");ui.state(button,()->state);
                        var g=new InkRecorder(context);renderButtonContents(button,g);
                        var text=g.exact(message.getString());assertEquals(8f/9,text.scale,.001f);
                        assertTrue(text.left>=box.x+2.5f&&text.right<=box.x+box.w-2.5f);
                        assertSame(tooltip,button.getTooltip(),"The compact caption must not replace the full direction/state/neighbor explanation");
                    }
                }
            }
        }
    }

    @Test void unboundedFallbackButtonLabelsKeepFullOverflowTooltipsAndClearThemWhenTheyFit() throws Exception {
        try(var context=new Context(false,"en_us")){
            var ui=new OsqUi("fab_port_item");var message=new Component[]{Component.literal("A genuinely long custom label 😀")};
            var button=ui.rect(500,500,36,22,0,0,()->message[0],()->{});
            assertNull(field(button,"labelTemplate"),"Exercise a live caption without a design-example text entry");
            var g=new InkRecorder(context);renderButtonContents(button,g);
            assertTrue(g.ink.getFirst().text.endsWith("…"));assertEquals(7.5f/9,g.ink.getFirst().scale,.001f);
            assertNotNull(button.getTooltip());assertEquals(message[0],field(button.getTooltip(),"message"));
            message[0]=Component.literal("OK");renderButtonContents(button,new InkRecorder(context));assertNull(button.getTooltip());
            var explanation=net.minecraft.client.gui.components.Tooltip.create(Component.literal("A dedicated action explanation"));
            button.setTooltip(explanation);message[0]=Component.literal("Another genuinely long custom label 😀");
            renderButtonContents(button,new InkRecorder(context));assertSame(explanation,button.getTooltip());
        }
    }

    @Test void nativeFieldHintsStayInsideTheFieldWithoutChangingTheEditableDraft() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(var sample:Map.of("seq_center_overview","search","seq_autocrafter_config","limit","fab_assembly_input","name").entrySet()){
                var ui=new OsqUi(sample.getKey());
                var hint=Component.translatable("gui.molecularmanipulator.auto_craft_amount_range");
                var field=ui.field(sample.getValue(),13,17,context.font,hint);
                var render=field.getClass().getDeclaredMethod("renderContents",net.minecraft.client.gui.GuiGraphics.class);render.setAccessible(true);
                var g=new InkRecorder(context);render.invoke(field,g);
                assertEquals(1,g.ink.size());var text=g.ink.getFirst();assertTrue(text.text.endsWith("…"));
                assertTrue(text.left>=field.getX()-.5f&&text.right<=field.getRight()+.5f,text.toString());
                assertEquals(7.5f/9,text.scale,.001f);assertEquals("",field.getValue());
                field.setMaxLength(19);field.setValue("9223372036854775807");field.moveCursorToEnd(false);
                var value=new InkRecorder(context);render.invoke(field,value);
                assertEquals("9223372036854775807",field.getValue());assertEquals(19,field.getCursorPosition());
                assertEquals(1,value.ink.size());assertTrue(value.ink.getFirst().right<=field.getRight()+.5f);
            }
        }
    }

    @Test void oversizedSingleRowLabelsNeverBecomeImplicitParagraphs() throws Exception {
        try(var context=new Context(false,"en_us")){
            var ui=new OsqUi("fab_port_item");
            var entry=ui.layout.findText("gui.molecularmanipulator.fab.s029");
            for(boolean single:new boolean[]{false,true}){
                var g=new InkRecorder(context);var value=Component.literal("A translated label that is too long for one row 😀");
                if(single)ui.layout.renderSingleLine(g,context.font,entry,value,0,0);
                else ui.layout.renderText(g,context.font,entry,value,0,0);
                assertEquals(1,g.ink.size());assertEquals(entry.size/9,g.ink.getFirst().scale,.001f);
                assertTrue(g.ink.getFirst().text.endsWith("…"));
            }
            entry.wrap=true;entry.w=80;var g=new InkRecorder(context);
            ui.layout.renderText(g,context.font,entry,Component.literal("This paragraph has a deliberately reserved multiline area."),0,0);
            assertTrue(g.ink.size()>1,"Explicit paragraphs still use word wrapping");
        }
    }

    @Test void liveStatusesAndPageCountersFitTheirActualRowsInBothLanguages() throws Exception {
        var truncated=new ArrayList<String>();
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var locale=JsonParser.parseString(Files.readString(ASSETS.resolve("lang/"+language+".json"))).getAsJsonObject();
            for(String page:List.of("fab_overview","seq_center_overview","seq_center_autocraft","seq_center_colors","omni_core","omni_core_legacy","sing_overview","sing_collection")){
                var ui=new OsqUi(page);
                var status=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&e.lang!=null
                        &&(e.lang.contains("quantum_state.")||e.lang.equals("gui.molecularmanipulator.fab.s026")||e.lang.equals("gui.molecularmanipulator.sing.s001"))).findFirst().orElseThrow();
                if(page.startsWith("omni_core"))ui.width(283,224.89f,85);
                for(String key:locale.keySet())if(key.startsWith("gui.molecularmanipulator.quantum_state.")){
                    var g=new InkRecorder(context);var message=Component.translatable(key);
                    ui.layout.renderSingleLine(g,context.font,status,message,0,0);
                    if(!message.getString().equals(g.ink.getFirst().text))truncated.add(language+" "+page+" "+key+" "+g.ink.getFirst());
                    assertEquals(1,g.ink.size());assertEquals(status.size/9,g.ink.getFirst().scale,.001f);
                }
            }
            for(String page:List.of("fab_assembly_input","seq_autocrafter_config","seq_center_overview","seq_center_autocraft","seq_center_colors","seq_manipulator","sing_collection")){
                var ui=new OsqUi(page);var entry=ui.layout.findText("gui.molecularmanipulator.page");
                var message=Component.translatable("gui.molecularmanipulator.page",113,200);var g=new InkRecorder(context);
                ui.layout.renderSingleLine(g,context.font,entry,message,0,0);
                if(!message.getString().equals(g.ink.getFirst().text))truncated.add(language+" "+page+" full page counter "+g.ink.getFirst());
                assertEquals(entry.size/9,g.ink.getFirst().scale,.001f);
            }
            var ui=new OsqUi("seq_center_autocraft");ui.width(230,143.89f,82);
            var entry=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&Math.abs(e.x-230)<.8f&&Math.abs(e.baseline-143.89f)<.8f).findFirst().orElseThrow();
            for(String key:locale.keySet())if(key.startsWith("gui.molecularmanipulator.auto_craft_state.")){
                var g=new InkRecorder(context);var message=Component.literal("#9 · ").append(Component.translatable(key));
                ui.layout.renderSingleLine(g,context.font,entry,message,0,0);
                if(!message.getString().equals(g.ink.getFirst().text))truncated.add(language+" "+key+" "+g.ink.getFirst());
                assertEquals(entry.size/9,g.ink.getFirst().scale,.001f);
            }
        }
        assertTrue(truncated.isEmpty(),String.join("\n",truncated));
    }

    @ParameterizedTest
    @ValueSource(strings={"zh_cn","en_us"})
    void defaultResearchRendersOneFreeL3IntegrationWithoutLegacyRowsOrScrollbar(String language) throws Exception {
        String prefix="gui.molecularmanipulator.star_computation.";
        try(var context=new Context(false,language)){
            var engine=engine(context,true);var screen=engine.screen;var menu=engine.menu;
            assertEquals(1,menu.nodes().size());var node=menu.node(0);
            assertEquals(ResourceLocation.parse("molecularmanipulator:material"),node.id());
            assertEquals(0,node.index());assertEquals(3,node.requiredLevel());assertEquals(0,node.informationCost());
            assertTrue(node.prerequisites().isEmpty());assertFalse(node.unlocked());
            menu.benefitLevel=3;menu.effectiveEntropy=menu.levelThreshold(3);menu.infoBalance=0;
            invoke(screen,"updateControls");
            var visible=engine.buttons.entrySet().stream().filter(e->e.getKey().startsWith("node.")&&e.getValue().visible).toList();
            assertEquals(List.of("node.0"),visible.stream().map(Map.Entry::getKey).toList());
            for(int row=1;row<5;row++)assertFalse(engine.buttons.get("node."+row).active);
            var scrollbar=(net.minecraft.client.gui.components.AbstractWidget)field(screen,"researchScrollbar");
            assertFalse(scrollbar.visible,"The sole default does not need scrolling chrome");
            assertTrue(engine.buttons.get("unlock").active);assertFalse(engine.buttons.get("prerequisites").active);
            var g=new InkRecorder(context);screen.drawFG(g,0,0,-1,-1);
            renderButtonContents(visible.getFirst().getValue(),g);
            var unlock=engine.buttons.get("unlock");
            var render=unlock.getClass().getDeclaredMethod("renderContents",net.minecraft.client.gui.GuiGraphics.class,String.class);
            render.setAccessible(true);render.invoke(unlock,g,"normal");
            String title=Component.translatable(node.title()).getString();assertNotEquals(node.title(),title,"Load the real locale, not raw keys");
            assertEquals(language.equals("zh_cn")?"单方块集成":"Single-Block Integration",title);
            assertEquals(2,g.ink.stream().filter(i->i.text.equals(title)).count(),"One list caption and its matching detail heading");
            assertEquals("0 bit",g.exact("0 bit").text);
            g.exact(Component.translatable(prefix+"no_prerequisites").getString());
            g.exact(Component.translatable(prefix+"unlock_count",0,1).getString());
            g.exact(Component.translatable(prefix+"pay_unlock","0").getString());
            for(String old:List.of("foundation","quantum","terminal","transfinite")){
                String hidden=Component.translatable(prefix+"node."+old).getString();
                assertTrue(g.ink.stream().noneMatch(i->i.text.equals(hidden)),"Archival node is not rendered: "+old);
            }
        }
    }

    @Test void researchCountUsesTheWholeSyncedDirectoryAndNeverHiddenLegacyBitsOrOnlyVisibleRows() throws Exception {
        String key="gui.molecularmanipulator.star_computation.unlock_count";
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var engine=engine(context,true);var menu=engine.menu;
            for(int mask:new int[]{StarComputationEngineMenu.ALL_UNLOCKS&~(1<<1),StarComputationEngineMenu.ALL_UNLOCKS}){
                menu.unlockMask=mask;menu.researchNodes=StarResearchMenuNodes.fallback(mask);invoke(engine.screen,"updateControls");
                var g=new InkRecorder(context);engine.screen.drawFG(g,0,0,-1,-1);
                int unlocked=(mask&(1<<1))==0?0:1;
                g.exact(Component.translatable(key,unlocked,1).getString());
                assertEquals(1,engine.buttons.entrySet().stream().filter(e->e.getKey().startsWith("node.")&&e.getValue().visible).count());
            }
            var nodes=new ArrayList<>(customResearchNodes(12,false).nodes());
            for(int index:List.of(3,11)){
                var node=nodes.get(index);nodes.set(index,new Node(index,node.id(),node.title(),node.requiredLevel(),
                        node.informationCost(),node.prerequisites(),node.error(),true));
            }
            menu.researchNodes=new StarResearchMenuNodes(nodes);invoke(engine.screen,"updateControls");
            assertEquals(5,engine.buttons.entrySet().stream().filter(e->e.getKey().startsWith("node.")&&e.getValue().visible).count());
            var g=new InkRecorder(context);engine.screen.drawFG(g,0,0,-1,-1);
            g.exact(Component.translatable(key,2,12).getString());
            assertFalse(menu.node(0).unlocked(),"A stale material bit cannot replace field 28's ownership flag");
        }
    }

    @Test void reusedResearchRowsRenderCurrentCustomTitlesAfterScrollAndSyncedReload() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var engine=engine(context,true,customResearchNodes(12,false));var screen=engine.screen;var menu=engine.menu;
            var scroll=screen.getClass().getDeclaredMethod("scrollResearchTo",int.class);scroll.setAccessible(true);scroll.invoke(screen,7);
            for(int row=0;row<5;row++){
                var button=engine.buttons.get("node."+row);var g=new InkRecorder(context);renderButtonContents(button,g);
                assertEquals(1,g.ink.size());var title=g.exact(menu.node(7+row).title());
                assertEquals(7.5f/9,title.scale,.001f);assertFalse(title.text.endsWith("…"));
                assertTrue(title.left>=button.getX()+4&&title.right<=button.getRight()-4);
                button.onPress();assertEquals(7+row,field(screen,"selectedNode"));
            }
            var row=engine.buttons.get("node.0");row.onPress();var original=menu.node(7);
            String title=language.equals("zh_cn")?"重载条目":"Reloaded study";
            var nodes=new ArrayList<>(menu.nodes());nodes.set(7,new Node(7,original.id(),title,original.requiredLevel(),
                    original.informationCost(),original.prerequisites(),"",true));
            menu.researchNodes=new StarResearchMenuNodes(nodes);invoke(screen,"updateControls");
            assertSame(row,((List<?>)field(screen,"nodeButtons")).getFirst(),"GuiSync must not require rebuilding the viewport's controls");
            assertTrue(screen.children().contains(row));
            var g=new InkRecorder(context);renderButtonContents(row,g);g.exact(title);
            assertTrue(g.ink.stream().noneMatch(i->i.text.equals(original.title())));
            assertTrue(((Component)field(row.getTooltip(),"message")).getString().contains(title));
            assertEquals(original.id(),field(screen,"selectedResearchId"));
            assertFalse(engine.buttons.get("unlock").active,"A custom permanent unlock disables purchase with no legacy bit");
            assertEquals(0,menu.unlockMask);
            var detail=new InkRecorder(context);screen.drawFG(detail,0,0,-1,-1);detail.exact(title);
            detail.exact(Component.translatable("gui.molecularmanipulator.star_computation.unlock_count",1,12).getString());
        }
    }

    @Test void engineLivePagesAndDynamicNodeStatesHaveNoCollisionsOrClippedLabelsInEitherLanguage() throws Exception {
        var issues=new ArrayList<String>();
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(boolean recipes:new boolean[]{false,true}){
                var engine=engine(context,recipes,customResearchNodes(12,false));var screen=engine.screen;var menu=engine.menu;
                menu.infoBalance=1000000000;menu.anchorCount=246;menu.anchorsReference=81;menu.anchorsQuantum=82;menu.anchorsTerminal=83;
                var select=screen.getClass().getDeclaredMethod("selectNode",int.class);select.setAccessible(true);
                for(int level:new int[]{0,2,5,8,9})for(boolean boosted:new boolean[]{false,true}){
                    menu.benefitLevel=level;menu.effectiveEntropy=menu.levelThreshold(level);menu.benefitsOverclocked=boosted;
                    for(int node=0;node<(recipes?menu.nodes().size():1);node++)for(boolean unlocked:new boolean[]{false,true}){
                        menu.researchNodes=customResearchNodes(12,unlocked);
                        menu.unlockMask=unlocked?0:StarComputationEngineMenu.ALL_UNLOCKS;select.invoke(screen,node);
                        var g=new InkRecorder(context);screen.drawFG(g,0,0,0,0);
                        for(var button:engine.buttons.entrySet())if(button.getValue().visible&&button.getKey().startsWith("node.")){
                            renderButtonContents(button.getValue(),g);
                        }
                        String state=language+" "+(recipes?"recipes":"overview")+" L"+level+" boost="+boosted+" node="+node+" unlocked="+unlocked;
                        for(var ink:g.ink)if(ink.text.endsWith("…")&&!ink.text.equals(Component.translatable(
                                "gui.molecularmanipulator.star_computation.catalog_waiting").getString()))issues.add(state+" clipped "+ink);
                        for(int a=0;a<g.ink.size();a++)for(int b=a+1;b<g.ink.size();b++)if(g.ink.get(a).overlaps(g.ink.get(b)))
                            issues.add(state+" overlap "+g.ink.get(a)+" / "+g.ink.get(b));
                    }
                }
            }
        }
        assertTrue(issues.isEmpty(),String.join("\n",issues.stream().limit(20).toList()));
    }

    @Test void dynamicRecipeViewportStaysSelectableAlongsidePrerequisitesAndNativeInventory() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var engine=engine(context,true,customResearchNodes(12,false));var screen=engine.screen;var ui=engine.ui;
            assertEquals(317,field(screen,"imageHeight"));
            var contents=ui.layout.find("rc_cont");var network=ui.layout.find("net");var inventory=ui.layout.find("inv");
            assertEquals(contents.y+contents.h+6,network.y);
            assertEquals(network.y+network.h+6,engine.buttons.get("unlock").getY());
            assertEquals(309,inventory.y+inventory.h);
            assertEquals(318,ui.layout.find("window").y+ui.layout.find("window").h);
            for(var slot:ui.layout.draw)if("slot".equals(slot.kind))
                assertTrue(slot.y>=inventory.y+6&&slot.y+slot.h<=inventory.y+inventory.h-6);
            var select=screen.getClass().getDeclaredMethod("selectNode",int.class);select.setAccessible(true);
            for(int[] viewport:new int[][]{{854,480},{320,240},{567,240}}){
                screen.width=viewport[0];screen.height=viewport[1];select.invoke(screen,0);
                for(int i=0;i<engine.menu.nodes().size();i++){
                    select.invoke(screen,i);int row=i-(Integer)field(screen,"researchScroll");
                    var button=engine.buttons.get("node."+row);
                    assertInstanceOf(OsqUi.SkinButton.class,button);
                    assertTrue(screen.screenContent.owns(button));assertTrue(button.visible&&button.active);
                    assertEquals(172,button.getWidth());assertEquals(22,button.getHeight());
                    assertTrue(button.getY()>=32&&button.getBottom()<=174-6);
                    if(row>0)assertTrue(engine.buttons.get("node."+(row-1)).getBottom()+2<=button.getY());
                    int x=screen.responsiveScreenX(button.getX()+button.getWidth()/2);
                    int y=screen.responsiveScreenY(button.getY()+button.getHeight()/2);
                    assertTrue(button.isMouseOver(screen.logicalMouseX(x),screen.logicalMouseY(y)));
                    button.onPress();assertEquals(i,field(screen,"selectedNode"));
                    var state=OsqUi.SkinButton.class.getDeclaredMethod("status");state.setAccessible(true);
                    for(int j=0;j<5;j++)assertEquals(j==row?"active":"normal",state.invoke(engine.buttons.get("node."+j)));
                    assertNotNull(button.getTooltip());
                    assertTrue(engine.buttons.entrySet().stream().filter(e->e.getKey().startsWith("node.")).allMatch(e->e.getValue().visible));
                    assertTrue(engine.buttons.entrySet().stream().filter(e->e.getKey().startsWith("up_level.")).noneMatch(e->e.getValue().visible));
                    var g=new InkRecorder(context);renderButtonContents(button,g);
                    assertEquals(1,g.ink.size());assertFalse(g.ink.getFirst().text.contains("…"));
                    assertEquals(Component.translatable(engine.menu.node(i).title()).getString(),g.ink.getFirst().text);
                    assertEquals(7.5f/9,g.ink.getFirst().scale,.001f);
                }
                assertTrue(engine.buttons.get("prerequisites").active);engine.buttons.get("prerequisites").onPress();
                int parent=(Integer)field(screen,"selectedNode");assertEquals(10,parent);
                int row=parent-(Integer)field(screen,"researchScroll");assertTrue(engine.buttons.get("node."+row).visible);
                if(viewport[1]==240){
                    var bounds=screen.responsiveBounds();
                    assertTrue(bounds.getX()>=3&&bounds.getY()>=3);
                    assertTrue(bounds.getX()+bounds.getWidth()<=screen.width-3&&bounds.getY()+bounds.getHeight()<=screen.height-3);
                }
            }
        }
    }

    @Test void engineStatisticsKeepFullSizeNumbersInsideTheReferenceGlassAndTrackLiveServerProgress() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(boolean recipes:new boolean[]{false,true}){
                var engine=engine(context,recipes,customResearchNodes(5,false));var ui=engine.ui;var menu=engine.menu;
                var panel=ui.layout.find(recipes?"rc_power":"ov_anchors");
                var sprite=javax.imageio.ImageIO.read(ASSETS.resolve("textures/gui/sprites/"+
                        panel.sprite.substring(panel.sprite.indexOf(':')+1)+".png").toFile());
                var progress=(Map<OsqLayout.Entry,java.util.function.DoubleSupplier>)field(ui,"progress");
                var bar=ui.layout.find(recipes?"rc_power_fill":"ov_anchors_fill");
                assertNotNull(progress.get(bar));
                assertEquals(0,progress.get(bar).getAsDouble(),.00001);
                for(int value:new int[]{1,24,96,504}){
                    menu.anchorCount=value;menu.effectiveEntropy=value*500L;
                    var g=new InkRecorder(context);invoke(engine.screen,recipes?"drawRecipes":"drawOverview",g);
                    var inside=g.ink.stream().filter(i->i.left>=16&&i.right<=(recipes?190:93)&&i.top>=(recipes?174:191)
                            &&i.bottom<=(recipes?214:236)).toList();
                    assertEquals(3,inside.size(),language+" statistic title, number and unit/count");
                    for(var text:inside){
                        assertFalse(text.text.contains("…"),language+" "+text);
                        float centerX=(text.left+text.right)/2,centerY=text.center();
                        int px=Math.round((centerX-panel.x+panel.pad[0])*4);
                        int py=Math.round((centerY-panel.y+panel.pad[1])*4);
                        assertTrue((sprite.getRGB(px,py)>>>24)>=230,"Text stays in the actual opaque glass");
                    }
                    for(int a=0;a<inside.size();a++)for(int b=a+1;b<inside.size();b++)
                        assertFalse(inside.get(a).overlaps(inside.get(b)),language+" "+inside);
                    assertTrue(inside.stream().allMatch(i->i.bottom<=bar.y-2),"Keep space above the progress bar");
                    assertEquals((recipes?17:26)/9f,inside.get(1).scale,.001f,"Retain the reference's large number");
                    double expected=recipes?(double)menu.effectiveEntropy/menu.nodeThreshold(menu.node(0)):value/504.0;
                    assertEquals(expected,progress.get(bar).getAsDouble(),.00001);
                }
                if(recipes){
                    engine.buttons.get("node.4").onPress();
                    assertEquals((double)menu.effectiveEntropy/menu.nodeThreshold(menu.node(4)),
                            progress.get(bar).getAsDouble(),.00001,"Power progress follows the selected recipe requirement");
                }
            }
        }
    }

    @Test void allPackageReclaimsKeepTheSameRedStateThroughHoverConfirmationAndDisabledPermissions() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            for(String page:List.of("fab_overview","seq_center_overview","seq_center_autocraft","omni_core","sing_overview")){
                var ui=new OsqUi(page);var armed=new boolean[]{false};
                var button=ui.button("dismantle",0,0,()->Component.translatable("gui.molecularmanipulator.dismantle"),()->{});
                ui.state(button,()->armed[0]?"confirm":"danger");
                for(boolean confirming:new boolean[]{false,true})for(boolean hover:new boolean[]{false,true})for(boolean enabled:new boolean[]{false,true}){
                    armed[0]=confirming;button.active=enabled;ResponsiveScreenTestFixture.set(button,"isHovered",hover);
                    var status=button.getClass().getDeclaredMethod("status");status.setAccessible(true);
                    String expected=!enabled?"disabled":confirming?"confirm":"danger";
                    assertEquals(expected,status.invoke(button),page);
                    var visual=button.getClass().getDeclaredMethod("visual",String.class,String.class,long.class);visual.setAccessible(true);
                    var frame=visual.invoke(button,"button",expected,OsqFonts.resourceGeneration());
                    assertTrue(((String)field(frame,"sprite")).endsWith("/button_"+expected),page);
                    assertEquals(enabled?0xFFF2F2F2:ui.palette.disT,ui.palette.controlText("button",expected,enabled));
                }
            }
            var engine=engine(context);var button=engine.buttons.get("reclaim");
            var status=button.getClass().getDeclaredMethod("status");status.setAccessible(true);
            for(boolean armed:new boolean[]{false,true})for(boolean hover:new boolean[]{false,true})for(boolean permission:new boolean[]{false,true}){
                engine.menu.reclaimArmed=armed;engine.menu.canConfigure=permission;invoke(engine.screen,"updateControls");
                ResponsiveScreenTestFixture.set(button,"isHovered",hover);
                assertEquals(!permission?"disabled":armed?"confirm":"danger",status.invoke(button));
            }
        }
    }

    @Test void engineFeedbackAndPrimaryActionsRetainTheirWholeMessagesInBothLanguages() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var ui=new OsqUi("star_computation_engine");
            var screen=ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
            var menu=ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);
            ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"menu",menu);
            ResponsiveScreenTestFixture.set(screen,"font",context.font);ResponsiveScreenTestFixture.set(screen,"pendingVersion",-1);
            ResponsiveScreenTestFixture.set(screen,"resultUntil",Long.MAX_VALUE);
            menu.catalog=StarComputationRecipeCatalog.EMPTY;menu.formed=true;menu.networkOnline=true;
            menu.researchNodes=customResearchNodes(12,false);
            var locale=JsonParser.parseString(Files.readString(ASSETS.resolve("lang/"+language+".json"))).getAsJsonObject();
            for(String key:locale.keySet())if(key.startsWith("gui.molecularmanipulator.star_computation.result.")){
                String state=key.substring(key.lastIndexOf('.')+1);
                menu.actionResult=StarComputationEngineMenu.ActionResult.valueOf(state.toUpperCase(java.util.Locale.ROOT));
                if(menu.actionResult==StarComputationEngineMenu.ActionResult.NONE)continue;
                for(var node:menu.nodes()){
                    menu.resultNode=node.index();var g=new InkRecorder(context);screen.drawFG(g,0,0,0,0);
                    var result=g.ink.stream().filter(i->i.left>=212&&i.center()>253&&i.center()<263).findFirst().orElseThrow();
                    String name=Component.translatable(node.index()==0?"gui.molecularmanipulator.star_computation.node_short.material":node.title()).getString();
                    assertEquals(Component.translatable("gui.molecularmanipulator.star_computation.node_result",name,Component.translatable(key)).getString(),result.text);
                    assertEquals(7.5f/9,result.scale,.001f);assertTrue(result.right<=383.5);
                }
            }
            for(var action:Map.of("refresh","refresh","prerequisites","view_prerequisites","guide","guide","overclock","overclock_off","unlock","unlocked_permanent","reclaim","reclaim","reclaim_confirm","reclaim_confirm").entrySet()){
                var box=ui.layout.findControl(action.getKey().equals("reclaim_confirm")?"reclaim":action.getKey());
                var label=Component.translatable("gui.molecularmanipulator.star_computation."+action.getValue());
                float text=OsqFonts.widthF(context.font,label.getString(),OsqFonts.id("minecraft:default"),7.5f);
                assertTrue(text+(action.getKey().equals("overclock")?41:8)<=box.w,action+" "+text);
            }
        }
    }

    @Test void englishTextBoxesDoNotMoveBindingsOrAlterTheChinesePresentation() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){for(var path:paths.toList()){
                var source=JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("draw");
                var layout=OsqLayout.forTheme(path.getFileName().toString().replace(".json",""),false);
                for(int i=0;i<source.size();i++){
                    var json=source.get(i).getAsJsonObject();var entry=layout.draw.get(i);
                    if(!entry.type.equals("text"))continue;
                    assertEquals(json.get("x").getAsFloat(),entry.x);assertEquals(json.get("baseline").getAsFloat(),entry.baseline);
                    assertEquals(json.get(language.equals("en_us")&&json.has("width_en")?"width_en":"width").getAsFloat(),entry.w);
                    var drawX=json.get(language.equals("en_us")&&json.has("draw_x_en")?"draw_x_en":"draw_x");
                    assertEquals(drawX==null?null:drawX.getAsFloat(),entry.drawX);
                    if(json.has("center_y"))assertEquals(json.get("center_y").getAsFloat(),entry.centerY);
                }
            }}
        }
    }

    @Test void engineReclaimHasItsOwnReadableActionRowInBothLanguages() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var context=new Context(false,language)){
            var engine=engine(context);var screen=engine.screen;var menu=engine.menu;
            var reclaim=engine.buttons.get("reclaim");assertNotNull(reclaim.getTooltip());
            menu.formed=false;menu.networkOnline=false;invoke(screen,"updateControls");
            assertTrue(reclaim.visible&&reclaim.active,"L0/offline diagnostics can still reclaim a damaged machine");
            menu.formed=true;menu.networkOnline=true;
            for(int level:new int[]{0,9})for(boolean armed:new boolean[]{false,true})for(String state:List.of("normal","hover","disabled")){
                menu.benefitLevel=level;menu.effectiveEntropy=menu.levelThreshold(level);
                menu.reclaimArmed=armed;menu.canConfigure=!state.equals("disabled");
                invoke(screen,"updateControls");
                var g=new InkRecorder(context);screen.drawFG(g,0,0,-1,-1);
                for(var button:engine.buttons.values())if(button.visible){
                    ResponsiveScreenTestFixture.set(button,"isHovered",state.equals("hover"));
                    var content=button.getClass().getDeclaredMethod("renderContents",net.minecraft.client.gui.GuiGraphics.class,String.class);
                    content.setAccessible(true);content.invoke(button,g,!button.active?"disabled":state);
                    var ink=g.exact(button.getMessage().getString());
                    assertEquals(7.5f/9,ink.scale,.001f,language+" "+ink);
                    assertTrue(ink.left>=button.getX()+3.5f&&ink.right<=button.getRight()-3.5f,language+" "+ink);
                    assertTrue(screen.screenContent.owns(button));
                }
                assertTrue(reclaim.visible,"Even L0/offline structures can be archived");
                assertEquals(menu.canConfigure,reclaim.active);
                assertEquals(Component.translatable("gui.molecularmanipulator.star_computation."
                        +(armed?"reclaim_confirm":"reclaim")).getString(),reclaim.getMessage().getString());
                for(int a=0;a<g.ink.size();a++)for(int b=a+1;b<g.ink.size();b++)
                    assertFalse(g.ink.get(a).overlaps(g.ink.get(b)),language+" "+g.ink.get(a)+" / "+g.ink.get(b));
                for(var ink:g.ink)assertFalse(ink.text.endsWith("…"),language+" "+ink);
                var visible=engine.buttons.values().stream().filter(b->b.visible).toList();
                for(int a=0;a<visible.size();a++){
                    var button=visible.get(a);
                    for(int b=a+1;b<visible.size();b++)assertFalse(overlaps(button,visible.get(b)),"Live controls never share a hitbox");
                    for(var slot:engine.ui.layout.draw)if("slot".equals(slot.kind))
                        assertFalse(button.getX()<slot.x+slot.w&&button.getRight()>slot.x
                                &&button.getY()<slot.y+slot.h&&button.getBottom()>slot.y,"Control cannot cover native inventory");
                }
            }
            menu.canConfigure=true;invoke(screen,"updateControls");
            for(int[] viewport:new int[][]{{320,240},{567,240}}){
                screen.width=viewport[0];screen.height=viewport[1];var bounds=screen.responsiveBounds();
                assertTrue(bounds.getX()>=3&&bounds.getY()>=3);
                assertTrue(bounds.getX()+bounds.getWidth()<=viewport[0]-3&&bounds.getY()+bounds.getHeight()<=viewport[1]-3);
                int x=screen.responsiveScreenX(reclaim.getX()+reclaim.getWidth()/2);
                int y=screen.responsiveScreenY(reclaim.getY()+reclaim.getHeight()/2);
                assertTrue(reclaim.isMouseOver(screen.logicalMouseX(x),screen.logicalMouseY(y)));
            }
        }
    }

    @Test void engineReclaimWaitsForTheServerAndResearchKeepsItsExistingControls() throws Exception {
        try(var context=new Context(false,"en_us")){
            var engine=engine(context);var menu=engine.menu;var screen=engine.screen;var reclaim=engine.buttons.get("reclaim");
            // The headless transport is server-side/no-op. Execute the real registered screen callback.
            reclaim.onPress();assertEquals(0,field(screen,"pendingVersion"));
            assertFalse(menu.reclaimArmed,"The client cannot arm itself or invent a successful package");
            assertFalse(reclaim.active);assertFalse(engine.buttons.get("refresh").active);
            reclaim.onPress();assertEquals(0,field(screen,"pendingVersion"));
            menu.reclaimArmed=true;menu.resultVersion++;screen.containerTick();
            assertTrue(reclaim.active);assertEquals(-1,field(screen,"pendingVersion"));
            var content=reclaim.getClass().getDeclaredMethod("renderContents",net.minecraft.client.gui.GuiGraphics.class,String.class);
            content.setAccessible(true);content.invoke(reclaim,new InkRecorder(context),"normal");
            assertEquals(Component.translatable("gui.molecularmanipulator.star_computation.reclaim_confirm").getString(),reclaim.getMessage().getString());
            reclaim.onPress();assertEquals(1,field(screen,"pendingVersion"));
            menu.reclaimArmed=false;menu.resultVersion++;screen.containerTick();
            menu.canConfigure=false;invoke(screen,"updateControls");assertFalse(reclaim.active);
            ResponsiveScreenTestFixture.set(screen,"recipesPage",true);invoke(screen,"updateControls");
            assertFalse(reclaim.visible);assertFalse(engine.buttons.get("refresh").visible);assertFalse(engine.buttons.get("overclock").visible);
            assertTrue(engine.buttons.get("unlock").visible);assertTrue(engine.buttons.get("prerequisites").visible);
            assertTrue(engine.buttons.get("guide").visible);
            for(int i=0;i<5;i++)assertEquals(i==0,engine.buttons.get("node."+i).visible,
                    "Only the default material is visible; archival compatibility nodes are not rows");
            assertFalse(((net.minecraft.client.gui.components.AbstractWidget)field(screen,"researchScrollbar")).visible);
        }
    }

    @Test void singularityPendingReclaimKeepsCancelReachableAndDisablesCompetingActionsWithoutACompactReclaim() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var holder=net.neoforged.neoforge.registries.DeferredHolder.class.getDeclaredField("holder");holder.setAccessible(true);
        var compact=com.atir.molecularmanipulator.registry.SingularityContent.COMPACT;var previous=holder.get(compact);
        // isSingleBlock only compares block identity; reuse a registered block after bootstrap freezes the registry.
        var compactBlock=net.minecraft.world.level.block.Blocks.GOLD_BLOCK;
        holder.set(compact,net.minecraft.core.Holder.direct(compactBlock));
        try{
            try(var context=new Context(false,"en_us")){
                for(boolean single:new boolean[]{false,true})for(String page:List.of("sing_overview","sing_collection","sing_duplication")){
                    var screen=ResponsiveScreenTestFixture.allocate(SingularityScreen.class);
                    var menu=ResponsiveScreenTestFixture.allocate(com.atir.molecularmanipulator.menu.SingularityMenu.class);
                    var machine=ResponsiveScreenTestFixture.allocate(com.atir.molecularmanipulator.blockentity.SingularityBlockEntity.class);
                    ResponsiveScreenTestFixture.set(machine,"blockState",single?compactBlock.defaultBlockState():net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                    ResponsiveScreenTestFixture.set(menu,"machine",machine);serverTransport(menu);
                    ResponsiveScreenTestFixture.set(menu,"slotsBySemantic",com.google.common.collect.ArrayListMultimap.create());
                    var ui=new OsqUi(page);screenState(screen,menu,ui,context.font);
                    var controls=ui.layout.draw.stream().filter(e->"ctl".equals(e.kind)).toList();
                    ui.hide(e->controls.stream().anyMatch(control->ui.layout.owns(control,e)));
                    boolean collection=page.endsWith("collection"),duplication=page.endsWith("duplication");
                    ResponsiveScreenTestFixture.set(screen,"collectionPage",collection);ResponsiveScreenTestFixture.set(screen,"duplicationPage",duplication);
                    for(String name:List.of("build","preview","refresh","embed","overview","collectionTab","duplicationTab","collectionToggle"))
                        ResponsiveScreenTestFixture.set(screen,name,Button.builder(Component.empty(),b->{}).bounds(0,0,40,22).build());
                    ResponsiveScreenTestFixture.set(screen,"effects",ResponsiveScreenTestFixture.allocate(MachineEffectButton.class));
                    var init=SingularityScreen.class.getDeclaredMethod("initReclaimControls",boolean.class);init.setAccessible(true);init.invoke(screen,collection||duplication);
                    menu.operation=com.atir.molecularmanipulator.blockentity.SingularityBlockEntity.Operation.IDLE;
                    menu.formed=true;menu.networkOnline=true;menu.correct=2;
                    menu.structureVersion=com.atir.molecularmanipulator.blockentity.SingularityStructure.LEGACY_VERSION;
                    var cancel=(Button)field(screen,"cancel");var reclaim=(Button)field(screen,"dismantle");
                    assertNotNull(reclaim.getTooltip());
                    if(!single&&!collection&&!duplication){
                        invoke(screen,"updateWidgets");reclaim.onPress();var armed=field(screen,"armedAt");
                        assertNotEquals(0L,armed);reclaim.onPress();assertEquals(armed,field(screen,"armedAt"));
                        ResponsiveScreenTestFixture.set(screen,"armedAt",net.minecraft.Util.getMillis()-300);
                        reclaim.onPress();assertEquals(0L,field(screen,"armedAt"));
                    }
                    for(boolean pending:new boolean[]{false,true})for(int motionMode:new int[]{0,3}){
                        menu.packageReclaimRequested=pending;menu.motionMode=motionMode;
                        ResponsiveScreenTestFixture.set(screen,"armedAt",net.minecraft.Util.getMillis());invoke(screen,"updateWidgets");
                        assertEquals(!single&&pending,cancel.visible,page+" cancel must be available while docking");
                        assertEquals(!single&&pending,cancel.active&&cancel.visible);
                        if(!single&&!collection&&!duplication)
                            assertEquals(!pending,reclaim.active,page+" docking mode "+motionMode+" can be adopted until reclaim is pending");
                        if(pending){
                            assertEquals(0L,field(screen,"armedAt"));
                            for(String name:List.of("build","preview","refresh","dismantle","embed","collectionToggle","effects"))
                                assertFalse(((Button)field(screen,name)).active,page+" "+name);
                        }
                        if(single)assertFalse(reclaim.visible,"Compact production machines are not multiblock archives");
                        if(cancel.visible){
                            assertTrue(screen.screenContent.owns(cancel));
                            assertTrue(cancel.getX()>=0&&cancel.getY()>=0&&cancel.getRight()<=ui.layout.width&&cancel.getBottom()<=ui.layout.height);
                            for(var slot:ui.layout.draw)if("slot".equals(slot.kind))assertFalse(cancel.getX()<slot.x+slot.w&&cancel.getRight()>slot.x
                                    &&cancel.getY()<slot.y+slot.h&&cancel.getBottom()>slot.y,page+" cancel cannot cover "+slot.id);
                            var body=new InkRecorder(context);ui.foreground(body,context.font);
                            for(var ink:body.ink)assertFalse(cancel.getX()<ink.right&&cancel.getRight()>ink.left
                                    &&cancel.getY()<ink.bottom&&cancel.getBottom()>ink.top,page+" cancel cannot cover "+ink);
                            if(collection)assertFalse(((Button)field(screen,"collectionToggle")).visible,"Cancel replaces the idle collection row");
                        }
                    }
                }
            }
        }finally{holder.set(compact,previous);}
    }

    private record Engine(StarComputationEngineScreen screen,StarComputationEngineMenu menu,OsqUi ui,Map<String,Button> buttons) {}

    private static Engine engine(Context context) throws Exception { return engine(context,false); }

    private static Engine engine(Context context,boolean recipes) throws Exception {
        return engine(context,recipes,StarResearchMenuNodes.FALLBACK);
    }

    /** Explicit pack-defined studies for multi-node geometry scenarios, never resurrected production defaults. */
    private static StarResearchMenuNodes customResearchNodes(int count,boolean unlocked) {
        var nodes=new ArrayList<Node>();var material=StarResearchMenuNodes.FALLBACK.node(0);
        nodes.add(new Node(0,material.id(),material.title(),material.requiredLevel(),material.informationCost(),List.of(),"",unlocked));
        for(int index=1;index<count;index++){
            var parent=nodes.getLast();var prerequisites=index>1?List.of(new Prerequisite(parent.id(),parent.title(),unlocked)):List.<Prerequisite>of();
            nodes.add(new Node(index,ResourceLocation.parse("kubejs:study_"+index),"Study "+index,
                    Math.min(9,index+1),index*16L,prerequisites,"",unlocked));
        }
        return new StarResearchMenuNodes(nodes);
    }

    private static Engine engine(Context context,boolean recipes,StarResearchMenuNodes research) throws Exception {
        var screen=ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
        var menu=ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);var ui=new OsqUi("star_computation_engine");
        screenState(screen,menu,ui,context.font);serverTransport(menu);
        ResponsiveScreenTestFixture.set(screen,"recipesPage",recipes);invoke(screen,"configureRecipePage");
        ResponsiveScreenTestFixture.set(screen,"imageHeight",ui.layout.height-(recipes?27:0));
        ResponsiveScreenTestFixture.set(screen,"nodeButtons",new ArrayList<>());ResponsiveScreenTestFixture.set(screen,"pendingVersion",-1);
        ResponsiveScreenTestFixture.set(screen,"levelButtons",new ArrayList<>());
        ui.visible=e->e.id==null||!e.id.startsWith("up_")&&!e.id.startsWith(recipes?"ov_":"rc_");ui.hide(e->"ctl".equals(e.kind)||"edge".equals(e.kind));
        menu.canConfigure=true;menu.formed=true;menu.networkOnline=true;
        menu.actionResult=StarComputationEngineMenu.ActionResult.NONE;menu.catalog=StarComputationRecipeCatalog.EMPTY;
        menu.researchNodes=research;
        menu.upgrades=com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeCatalog.EMPTY;
        menu.certifiedLevel=3;menu.upgradeStatus="idle";
        invoke(screen,"initControls");invoke(screen,"updateControls");var buttons=new HashMap<String,Button>();
        for(var child:screen.children())if(child instanceof Button button){
            String id=button instanceof OsqUi.SkinButton?((OsqLayout.Entry)field(button,"box")).id:(String)field(button,"id");
            buttons.put(id,button);
        }
        assertEquals(22,buttons.size());return new Engine(screen,menu,ui,buttons);
    }

    private static void screenState(ResponsiveContainerScreen<?> screen,Object menu,OsqUi ui,Font font) throws Exception {
        for(String name:List.of("children","renderables","narratables","screenWidgets"))ResponsiveScreenTestFixture.set(screen,name,new ArrayList<>());
        ResponsiveScreenTestFixture.set(screen,"screenContent",new ResponsiveScreenWidgets());
        ResponsiveScreenTestFixture.set(screen,"widgets",new appeng.client.gui.WidgetContainer(new appeng.client.gui.style.ScreenStyle()));
        ResponsiveScreenTestFixture.set(screen,"menu",menu);ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"font",font);
        ResponsiveScreenTestFixture.set(screen,"imageWidth",ui.layout.width);ResponsiveScreenTestFixture.set(screen,"imageHeight",ui.layout.height);
    }

    private static void serverTransport(Object menu) throws Exception {
        var player=ResponsiveScreenTestFixture.allocate(net.minecraft.server.level.ServerPlayer.class);
        ResponsiveScreenTestFixture.set(player,"level",ResponsiveScreenTestFixture.allocate(net.minecraft.server.level.ServerLevel.class));
        ResponsiveScreenTestFixture.set(menu,"playerInventory",new net.minecraft.world.entity.player.Inventory(player));
    }

    private static Object field(Object target,String name) throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{
            var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(target);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }

    private static boolean overlaps(Button a,Button b) {
        return a.getX()<b.getRight()&&a.getRight()>b.getX()&&a.getY()<b.getBottom()&&a.getBottom()>b.getY();
    }

    private record Center(MolecularCenterScreen screen,MolecularCenterMenu menu,OsqUi ui,List<Button> buttons) {}

    /** Run the same common-row binding and control registration called by the production screen's init. */
    private static Center center(Context context,String name) throws Exception {
        var screen=ResponsiveScreenTestFixture.allocate(MolecularCenterScreen.class);
        var menu=ResponsiveScreenTestFixture.allocate(MolecularCenterMenu.class);var ui=new OsqUi(name);
        for(String field:List.of("children","renderables","narratables","screenWidgets"))
            ResponsiveScreenTestFixture.set(screen,field,new ArrayList<>());
        ResponsiveScreenTestFixture.set(screen,"screenContent",new ResponsiveScreenWidgets());
        ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"menu",menu);
        ResponsiveScreenTestFixture.set(screen,"font",context.font);
        ResponsiveScreenTestFixture.set(screen,"imageWidth",ui.layout.width);ResponsiveScreenTestFixture.set(screen,"imageHeight",ui.layout.height);
        ResponsiveScreenTestFixture.set(screen,"detailTab",name.endsWith("autocraft")?MolecularCenterScreen.TAB_AUTO_CRAFT
                :name.endsWith("colors")?MolecularCenterScreen.TAB_COLORS:MolecularCenterScreen.TAB_OVERVIEW);
        menu.quantumLinkState=MolecularCenterBlockEntity.QuantumLinkState.EMPTY;menu.autoCraftSelectedSlot=-1;
        ui.visible=e->!"tip".equals(e.kind)&&!"pop".equals(e.kind)&&ui.layout.layerOf(e)==null;
        var controls=ui.layout.draw.stream().filter(e->("ctl".equals(e.kind)||"edge".equals(e.kind))
                &&!"pattern_upload".equals(e.id)&&!"migrate_patterns".equals(e.id)).toList();
        ui.hide(e->controls.stream().anyMatch(control->ui.layout.owns(control,e)));
        invoke(screen,"bindQuantumLink");invoke(screen,"initPatternTransferControls");
        var buttons=screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
        assertEquals(2,buttons.size(),"Production registers exactly the two live pattern-transfer controls");
        return new Center(screen,menu,ui,buttons);
    }

    private static Object buttonVisual(Button button) throws Exception {
        var type=OsqUi.SkinButton.class;var kindMethod=type.getDeclaredMethod("kind");var stateMethod=type.getDeclaredMethod("status");
        kindMethod.setAccessible(true);stateMethod.setAccessible(true);
        String kind=(String)kindMethod.invoke(button),state=(String)stateMethod.invoke(button);
        var visualMethod=type.getDeclaredMethod("visual",String.class,String.class,long.class);visualMethod.setAccessible(true);
        return visualMethod.invoke(button,kind,state,OsqFonts.resourceGeneration());
    }

    /** Execute production SkinButton state, resource and content code, omitting only its OpenGL frame. */
    private static void renderButtonContents(Button button,InkRecorder g) throws Exception {
        var type=OsqUi.SkinButton.class;var kindMethod=type.getDeclaredMethod("kind");var stateMethod=type.getDeclaredMethod("status");
        kindMethod.setAccessible(true);stateMethod.setAccessible(true);
        String kind=(String)kindMethod.invoke(button),state=(String)stateMethod.invoke(button);
        var visual=buttonVisual(button);
        var content=type.getDeclaredMethod("renderContents",net.minecraft.client.gui.GuiGraphics.class,
                String.class,String.class,float.class,visual.getClass());content.setAccessible(true);
        content.invoke(button,g,kind,state,OsqUi.controlCenter(kind,state,button.getHeight(),false),visual);
    }

    private static void invoke(Object target,String method) throws Exception {
        var m=target.getClass().getDeclaredMethod(method);m.setAccessible(true);m.invoke(target);
    }

    private static final class HandheldProbe extends StarChartClient.PanelScreen {
        private HandheldProbe(){super(Component.empty(),"star_chart_calibrator",true);}
        void add(PointerProbe input){addRenderableWidget(input);}
    }

    private static final class PointerProbe implements net.minecraft.client.gui.components.events.GuiEventListener,
            net.minecraft.client.gui.components.Renderable,net.minecraft.client.gui.narration.NarratableEntry {
        double x,y,dx,dy,scroll;boolean focused;
        @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){}
        @Override public boolean isMouseOver(double x,double y){return x>=12&&x<42&&y>=22&&y<52;}
        @Override public void mouseMoved(double x,double y){this.x=x;this.y=y;}
        @Override public boolean mouseClicked(double x,double y,int button){mouseMoved(x,y);return isMouseOver(x,y);}
        @Override public boolean mouseReleased(double x,double y,int button){mouseMoved(x,y);return true;}
        @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){mouseMoved(x,y);this.dx=dx;this.dy=dy;return true;}
        @Override public boolean mouseScrolled(double x,double y,double horizontal,double delta){mouseMoved(x,y);scroll=delta;return true;}
        @Override public void setFocused(boolean focused){this.focused=focused;}
        @Override public boolean isFocused(){return focused;}
        @Override public NarrationPriority narrationPriority(){return NarrationPriority.NONE;}
        @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){}
    }

    private static void invoke(Object target,String method,InkRecorder g) throws Exception {
        Method m=target.getClass().getDeclaredMethod(method,net.minecraft.client.gui.GuiGraphics.class);
        m.setAccessible(true);m.invoke(target,g);
    }

    private record Ink(String text,ResourceLocation face,float left,float top,float right,float bottom,float scale){
        float center(){return(top+bottom)/2;}
        boolean overlaps(Ink b){return left<b.right-.1f&&right>b.left+.1f&&top<b.bottom-.1f&&bottom>b.top+.1f;}
    }

    private static final class InkRecorder extends UiRenderRecorder {
        final Context context;final List<Ink> ink=new ArrayList<>();
        InkRecorder(Context context){this.context=context;}
        @Override public int drawString(Font font,FormattedCharSequence sequence,int x,int y,int color,boolean shadow){
            var value=new StringBuilder();var faces=new ArrayList<ResourceLocation>();
            sequence.accept((index,style,cp)->{
                assertEquals(VanillaFontTestFixture.DEFAULT_FONT,style.getFont(),"Live UI text must use minecraft:default");
                value.appendCodePoint(cp);faces.add(style.getFont());return true;
            });
            float cursor=x,minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
            int index=0;
            for(int cp:value.toString().codePoints().toArray()){
                var info=context.glyph(faces.get(index++),cp);
                var bounds=context.vanilla.inkBounds(info);
                if(bounds[0]<bounds[2]&&bounds[1]<bounds[3]){
                    minX=Math.min(minX,cursor+bounds[0]);minY=Math.min(minY,y+bounds[1]);
                    maxX=Math.max(maxX,cursor+bounds[2]);maxY=Math.max(maxY,y+bounds[3]);
                }
                cursor+=info.getAdvance();
            }
            if(Float.isFinite(minX)){
                var matrix=pose().last().pose();var a=matrix.transformPosition(new Vector3f(minX,minY,0));var b=matrix.transformPosition(new Vector3f(maxX,maxY,0));
                ink.add(new Ink(value.toString(),faces.getFirst(),a.x,a.y,b.x,b.y,matrix.m00()));
            }
            return (int)Math.ceil(cursor);
        }
        Ink exact(String text){return ink.stream().filter(i->i.text.equals(text)).findFirst().orElseThrow(()->new AssertionError("Missing exact "+text+" in "+ink));}
        Ink containing(String text){return ink.stream().filter(i->i.text.contains(text)).findFirst().orElseThrow(()->new AssertionError("Missing "+text+" in "+ink));}
    }

    private static final class Context implements AutoCloseable {
        final VanillaFontTestFixture vanilla;
        final ReloadableResourceManager resources;
        final Font font;
        // Retained for the neighboring UI test's reflective recorder wrapper, never as a second theme.
        Context(boolean dark,String language) throws Exception {
            assertFalse(dark,"The removed dark resource pack is not a test fixture");
            vanilla=new VanillaFontTestFixture(language);
            resources=vanilla.resources;font=vanilla.font;
        }
        com.mojang.blaze3d.font.GlyphInfo glyph(ResourceLocation face,int cp){return vanilla.glyph(face,cp);}
        @Override public void close() throws Exception {vanilla.close();}
    }
}
