package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.client.gui.theme.OsqFonts;
import com.atir.molecularmanipulator.client.gui.theme.OsqLayout;
import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphProvider;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Real live drawing with Minecraft's FreeType providers; no window or approximate glyph advances. */
class OsqRuntimeReadabilityTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/molecularmanipulator");

    @Test void quantumFrequencyMigrationUploadAndLiveStatusHaveSeparateRowsInEveryCenterPage() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
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
                    String sample=(dark?"dark":"light")+" "+language+" "+name+" "+linkState+" auto="+automatic+" "+state;
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
                    for(var ink:List.of(title,status,caption,frequency,upkeep))assertEquals(1,ink.scale,.001f,sample);
                    for(int index=0;index<2;index++){
                        var button=center.buttons.get(index);
                        var box=ui.layout.findControl(index==0?"pattern_upload":"migrate_patterns");
                        String key=index==0?"gui.molecularmanipulator.pattern_upload."+(automatic?"on":"off")
                                :"gui.molecularmanipulator.pattern_migration.button";
                        String message=Component.translatable(key).getString();var ink=g.exact(message);
                        assertEquals(message,ink.text,sample);assertEquals(1,ink.scale,.001f,sample);
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
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"en_us")){
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

    @Test void engineBenefitsUseLiveLevelsAndNoDetailTouchesItsTitleNextRowOrFootnote() throws Exception {
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"zh_cn")){
            var screen=ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
            var menu=ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);
            ResponsiveScreenTestFixture.set(screen,"ui",new OsqUi("star_computation_engine"));
            ResponsiveScreenTestFixture.set(screen,"menu",menu);
            ResponsiveScreenTestFixture.set(screen,"font",context.font);
            menu.formed=true;menu.anchorCount=246;menu.effectiveEntropy=2147483647;
            menu.anchorsReference=81;menu.anchorsQuantum=82;menu.anchorsTerminal=83;
            menu.catalog=StarComputationRecipeCatalog.EMPTY;
            for(int level:new int[]{0,2,5,8,9})for(boolean overclock:new boolean[]{false,true}){
                menu.benefitLevel=level;menu.benefitsOverclocked=overclock;
                var g=new InkRecorder(context);invoke(screen,"drawOverview",g);
                var rows=g.ink.stream().filter(i->i.left>=231&&i.top>=138).toList();
                for(int a=0;a<rows.size();a++)for(int b=a+1;b<rows.size();b++)
                    assertFalse(rows.get(a).overlaps(rows.get(b)),"L"+level+" "+rows.get(a)+" / "+rows.get(b));
                for(var row:rows)assertTrue(row.scale>=7.5f/9-.001f,row.toString());
                assertTrue(g.containing("深度研究").bottom<242);
            }
        }
    }

    @Test void longRuntimeTextRetainsReadableBodySizeAndWholeCodepoints() throws Exception {
        try(var context=new Context(false,"zh_cn")){
            var ui=new OsqUi("fab_port_item");var g=new InkRecorder(context);
            ui.centeredText(g,Component.literal("一段很长的实际物品名称😀用于验证省略号"),10,20,60,ui.palette.ink);
            assertEquals(1,g.ink.size());var text=g.ink.getFirst();
            assertTrue(text.text.endsWith("…"));assertEquals(1,text.scale,.001f);
            assertTrue(text.right<=70.5);assertTrue(text.center()>19.5&&text.center()<20.5);
        }
    }

    @Test void actualReturnLabelFitsBesideItsVisibleIconInAllStatesAndThemes() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
            var ui=new OsqUi("fab_port_item");var box=ui.layout.findControl("return");
            var label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&ui.layout.owns(box,e)).findFirst().orElseThrow();
            var original=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/")&&ui.layout.owns(box,e)).findFirst().orElseThrow();
            var cache=new OsqButtonIconCache();
            for(String state:List.of("normal","hover","disabled")){
                String tint=state.equals("disabled")?"dis":!dark?(state.equals("hover")?"ink":"white"):"ink";
                String sprite=cache.resolve(OsqFonts.resourceGeneration(),original.sprite,null,tint,
                        id->context.resources.getResource(ResourceLocation.parse(id).withPrefix("textures/gui/sprites/").withSuffix(".png")).isPresent());
                var icon=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(sprite);
                float iw=icon.fitWidth(original.w,original.h)*icon.width();
                float width=OsqFonts.widthF(context.font,Component.translatable(
                        "gui.molecularmanipulator.fabrication.port.return_ae").getString(),OsqFonts.id(label.fontId));
                assertTrue(iw+6+width+14<=box.w,"Full 全部退回 AE label fits with real "+state+" pixels");
                float cy=OsqUi.controlCenter("button",state,box.h,false);
                label.drawX=box.x+(box.w-iw-6-width)/2+iw+6;label.w=width;label.centerY=box.y+cy;
                var g=new InkRecorder(context);
                ui.layout.renderSingleLine(g,context.font,label,Component.translatable(
                        "gui.molecularmanipulator.fabrication.port.return_ae"),0,0);
                var ink=g.ink.getFirst();assertEquals(box.y+cy,ink.center(),.8);
                assertEquals(1,ink.scale,.001);assertEquals(Component.translatable(
                        "gui.molecularmanipulator.fabrication.port.return_ae").getString(),ink.text);
            }
        }
    }

    @Test void everyPageSwitchUsesConnectedAeTextTabsWithFullChineseAndEnglishLabels() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
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
                                assertEquals(1,text.scale,.001f);assertEquals(tab.getY()+10,text.center(),.8f);
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
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"zh_cn")){
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
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"zh_cn")){
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
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
            for(String page:List.of("fab_assembly_patterns","fab_assembly_input","fab_assembly_catalyst")){
                var ui=new OsqUi(page);var box=ui.layout.findControl("save");
                var label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&ui.layout.owns(box,e)).findFirst().orElseThrow();
                var icon=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/")&&ui.layout.owns(box,e)).findFirst().orElseThrow();
                float width=OsqFonts.widthF(context.font,label.staticText().getString(),OsqFonts.id(label.fontId));
                assertTrue(width+14+6+icon.w<=box.w,page+" "+language+" save has room for its text and icon");
                ui.number(160.42f,22,()->"36 / 36");ui.at(170.19f,22,Component::empty);
                var g=new InkRecorder(context);ui.foreground(g,context.font);
                var count=g.containing("36 / 36");assertEquals("36 / 36",count.text);assertEquals(1,count.scale,.001f);
                assertTrue(count.right<=182.5);assertTrue(count.left>=142);
            }
        }
    }

    @Test void researchOrderStatusUsesOneLiveLabelAndTheDotsVisibleCenterInEveryState() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
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
                var text=g.containing(message);assertEquals(message,text.text);assertEquals(1,text.scale,.001f);
                assertEquals(1,g.ink.stream().filter(i->i.text.equals(message)).count(),"The old manual status must not remain");
                var dot=ui.layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/dot_")&&e.x==17&&e.y==212).findFirst().orElseThrow();
                var ink=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(ui.sprite("dot_"+(state.equals("idle")?"mute":state.equals("submitted")?"ok":"warn")));
                assertEquals(dot.drawY+dot.h*ink.y(),text.center(),.8f,state+" "+language);
            }
        }
    }

    @Test void portsReplaceTheirWholeSourceButtonWithoutDrawingAnExtraOutline() throws Exception {
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"zh_cn")){
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

    @Test void handheldControlsAndSealParagraphUseReadableUnclippedTextInBothThemesAndLanguages() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
            for(String name:List.of("star_chart_calibrator","star_chart_seal","star_chart_details")){
                var ui=new OsqUi(name);
                for(var control:ui.layout.draw.stream().filter(e->"ctl".equals(e.kind)).toList()){
                    assertTrue(control.h>=22,name+" "+control.id);
                    var label=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&control.id.equals(e.ownerId)).findFirst().orElseThrow();
                    float width=OsqFonts.widthF(context.font,label.staticText().getString(),OsqFonts.id(label.fontId));
                    assertTrue(width<=control.w-8+.5f,language+" "+name+" "+label.staticText().getString()+" width="+width);
                }
                var body=ui.layout.findText("star_chart.molecularmanipulator.seal_contents");
                if(body!=null){
                    var g=new InkRecorder(context);ui.layout.renderText(g,context.font,body,body.staticText(),0,0);
                    for(var line:g.ink){assertTrue(line.bottom<228);assertEquals(1,line.scale,.001f);}
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
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
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
                var ink=g.ink.get(a);assertEquals(1,ink.scale,.001f);assertTrue(ink.bottom<344);
                for(int b=a+1;b<g.ink.size();b++)assertFalse(ink.overlaps(g.ink.get(b)),ink+" / "+g.ink.get(b));
            }
        }
    }

    @Test void terminalRowsKeepLongNamesStatusDotsAndRemoveButtonsSeparate() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
            var screen=ResponsiveScreenTestFixture.allocate(BuildingTerminalScreen.class);
            var ui=new OsqUi("building_access_terminal");
            ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"font",context.font);
            var rows=new ArrayList<>();
            var rowType=Class.forName(BuildingTerminalScreen.class.getName()+"$RowView");
            var constructor=rowType.getDeclaredConstructors()[0];constructor.setAccessible(true);
            for(var status:com.atir.molecularmanipulator.menu.BuildingTerminalMenu.Status.values()){
                var entry=new com.atir.molecularmanipulator.menu.BuildingTerminalMenu.Entry(
                        net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD,new net.minecraft.core.BlockPos(-30000000,320,30000000)),
                        ResourceLocation.withDefaultNamespace("stone"),status);
                rows.add(constructor.newInstance(entry,net.minecraft.world.item.ItemStack.EMPTY,
                        Component.literal("很长的建筑名称 Long building controller"),Component.literal("Overworld (-30000000, 320, 30000000)")));
            }
            ResponsiveScreenTestFixture.set(screen,"rows",rows);
            var g=new InkRecorder(context);invoke(screen,"drawRows",g);
            for(int row=0;row<5;row++){
                var box=ui.layout.find("card."+row);float y=box.y;
                var texts=g.ink.stream().filter(i->i.top>=y&&i.bottom<y+box.h&&i.left>box.x+30).toList();
                for(int a=0;a<texts.size();a++)for(int b=a+1;b<texts.size();b++)assertFalse(texts.get(a).overlaps(texts.get(b)));
                var status=texts.getLast();assertEquals(y+10,status.center(),.8);
                var remove=ui.layout.findControl("remove."+row);assertTrue(status.bottom+3<remove.y);
            }
            var help=new InkRecorder(context);invoke(screen,"drawHelp",help);
            for(int a=0;a<help.ink.size();a++){
                assertTrue(help.ink.get(a).bottom<=308,language+" "+help.ink.get(a));
                assertEquals(1,help.ink.get(a).scale,.001f);
                for(int b=a+1;b<help.ink.size();b++)assertFalse(help.ink.get(a).overlaps(help.ink.get(b)));
            }
        }
    }

    @Test void liveAssemblyMetadataMovesItsDrawingOverrideAndCentersTheVisibleIcon() throws Exception {
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"zh_cn")){
            var screen=ResponsiveScreenTestFixture.allocate(MatterFabricationPatternAssemblyScreen.class);
            var ui=new OsqUi("fab_assembly_patterns");
            ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"font",context.font);
            var value=Component.translatable("gui.molecularmanipulator.fabrication.buffer.queued",9223372036854775807L);
            var method=screen.getClass().getDeclaredMethod("centerMetadata",String.class,String.class,Component.class);method.setAccessible(true);
            method.invoke(screen,"assembly_queue","assembly_queue_icon",value);
            var label=ui.layout.find("assembly_queue");var icon=ui.layout.find("assembly_queue_icon");
            var ink=com.atir.molecularmanipulator.client.gui.theme.OsqIconInk.center(icon.sprite);
            assertEquals(label.centerY,icon.drawY+ink.y()*icon.h,.001);
            assertNotNull(label.drawX);assertTrue(label.drawX>icon.drawX+icon.w*ink.x());
            var action=ui.layout.findControl("return");assertTrue(label.centerY+label.size/2+3<=action.y);
        }
    }

    @Test void everyPageWithRealFontsHasNoCollisionsOrClippedEnglishLabels() throws Exception {
        int pages = 0;
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
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
                        ui.at(64.5f,102,Component::empty);ui.at(125.8f,102,Component::empty);
                    }
                    if(name.equals("sing_duplication"))ui.at(199.53f,181,Component::empty);
                    var g=new InkRecorder(context);ui.foreground(g,context.font);
                    String sample=(dark?"dark":"light")+" "+language+" "+name;
                    for(int a=0;a<g.ink.size();a++)for(int b=a+1;b<g.ink.size();b++)
                        assertFalse(g.ink.get(a).overlaps(g.ink.get(b)),sample+" "+g.ink.get(a)+" / "+g.ink.get(b));
                    if(language.equals("en_us"))for(var ink:g.ink)
                        assertFalse(ink.text.endsWith("…"),sample+" "+ink);
                    pages++;
                }
            }
        }
        assertEquals(100,pages);
    }

    @Test void nativeFieldHintsStayInsideTheFieldWithoutChangingTheEditableDraft() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
            for(var sample:Map.of("seq_center_overview","search","seq_autocrafter_config","limit","fab_assembly_input","name").entrySet()){
                var ui=new OsqUi(sample.getKey());
                var hint=Component.translatable("gui.molecularmanipulator.auto_craft_amount_range");
                var field=ui.field(sample.getValue(),13,17,context.font,hint);
                var render=field.getClass().getDeclaredMethod("renderContents",net.minecraft.client.gui.GuiGraphics.class);render.setAccessible(true);
                var g=new InkRecorder(context);render.invoke(field,g);
                assertEquals(1,g.ink.size());var text=g.ink.getFirst();assertTrue(text.text.endsWith("…"));
                assertTrue(text.left>=field.getX()-.5f&&text.right<=field.getRight()+.5f,text.toString());
                assertEquals(1,text.scale,.001f);assertEquals("",field.getValue());
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
                assertEquals(1,g.ink.size());assertEquals(1,g.ink.getFirst().scale,.001f);
                assertTrue(g.ink.getFirst().text.endsWith("…"));
            }
            entry.wrap=true;entry.w=80;var g=new InkRecorder(context);
            ui.layout.renderText(g,context.font,entry,Component.literal("This paragraph has a deliberately reserved multiline area."),0,0);
            assertTrue(g.ink.size()>1,"Explicit paragraphs still use word wrapping");
        }
    }

    @Test void englishLiveStatusesAndPageCountersFitTheirActualRows() throws Exception {
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"en_us")){
            var locale=JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
            for(String page:List.of("fab_overview","seq_center_overview","seq_center_autocraft","seq_center_colors","omni_core","omni_core_legacy","sing_overview","sing_collection")){
                var ui=new OsqUi(page);
                var status=ui.layout.draw.stream().filter(e->"text".equals(e.type)&&e.lang!=null
                        &&(e.lang.contains("quantum_state.")||e.lang.equals("gui.molecularmanipulator.fab.s026")||e.lang.equals("gui.molecularmanipulator.sing.s001"))).findFirst().orElseThrow();
                for(String key:locale.keySet())if(key.startsWith("gui.molecularmanipulator.quantum_state.")){
                    var g=new InkRecorder(context);var message=Component.translatable(key);
                    ui.layout.renderSingleLine(g,context.font,status,message,0,0);
                    assertEquals(message.getString(),g.ink.getFirst().text,page+" "+key);assertEquals(1,g.ink.size());
                    assertEquals(1,g.ink.getFirst().scale,.001f);
                }
            }
            for(String page:List.of("fab_assembly_input","seq_autocrafter_config","seq_center_overview","seq_center_autocraft","seq_center_colors","seq_manipulator","sing_collection")){
                var ui=new OsqUi(page);var entry=ui.layout.findText("gui.molecularmanipulator.page");
                var message=Component.translatable("gui.molecularmanipulator.page",113,200);var g=new InkRecorder(context);
                ui.layout.renderSingleLine(g,context.font,entry,message,0,0);
                assertEquals(message.getString(),g.ink.getFirst().text,page+" full page counter");assertEquals(1,g.ink.getFirst().scale,.001f);
            }
            var ui=new OsqUi("seq_center_autocraft");
            for(String key:locale.keySet())if(key.startsWith("gui.molecularmanipulator.auto_craft_state.")){
                var g=new InkRecorder(context);var message=Component.literal("#9 · ").append(Component.translatable(key));
                ui.centeredText(g,message,230,153,82,ui.palette.ink);
                assertEquals(message.getString(),g.ink.getFirst().text,key);assertEquals(1,g.ink.getFirst().scale,.001f);
            }
        }
    }

    @Test void englishEngineLivePagesAndAllFiveNodeStatesHaveNoCollisionsOrClippedLabels() throws Exception {
        var issues=new ArrayList<String>();
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"en_us")){
            var screen=ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
            var menu=ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);
            ResponsiveScreenTestFixture.set(screen,"ui",new OsqUi("star_computation_engine"));
            ResponsiveScreenTestFixture.set(screen,"menu",menu);ResponsiveScreenTestFixture.set(screen,"font",context.font);
            ResponsiveScreenTestFixture.set(screen,"pendingVersion",-1);
            menu.formed=true;menu.networkOnline=true;menu.canConfigure=true;menu.infoBalance=1000000000;
            menu.anchorCount=246;menu.anchorsReference=81;menu.anchorsQuantum=82;menu.anchorsTerminal=83;
            menu.actionResult=StarComputationEngineMenu.ActionResult.NONE;menu.catalog=StarComputationRecipeCatalog.EMPTY;
            for(boolean recipes:new boolean[]{false,true})for(int level:new int[]{0,2,5,8,9})for(boolean boosted:new boolean[]{false,true}){
                menu.benefitLevel=level;menu.effectiveEntropy=menu.levelThreshold(level);menu.benefitsOverclocked=boosted;
                ResponsiveScreenTestFixture.set(screen,"recipesPage",recipes);
                for(int node=0;node<(recipes?5:1);node++)for(boolean unlocked:new boolean[]{false,true}){
                    menu.unlockMask=unlocked?StarComputationEngineMenu.ALL_UNLOCKS:0;
                    ResponsiveScreenTestFixture.set(screen,"selectedNode",node);
                    var g=new InkRecorder(context);screen.drawFG(g,0,0,0,0);
                    if(recipes){
                        var type=Class.forName(StarComputationEngineScreen.class.getName()+"$EngineButton");
                        var constructor=type.getDeclaredConstructor(StarComputationEngineScreen.class,String.class,java.util.function.Supplier.class,Runnable.class);
                        constructor.setAccessible(true);
                        var render=type.getDeclaredMethod("renderWidget",net.minecraft.client.gui.GuiGraphics.class,int.class,int.class,float.class);render.setAccessible(true);
                        for(var recipe:StarComputationEngineMenu.NODES){
                            var button=constructor.newInstance(screen,"node."+recipe.index(),
                                    (java.util.function.Supplier<Component>)()->Component.translatable(recipe.translationKey()),(Runnable)()->{});
                            render.invoke(button,g,0,0,0f);
                        }
                    }
                    String state=(dark?"dark":"light")+" "+(recipes?"recipes":"overview")+" L"+level+" boost="+boosted+" node="+node+" unlocked="+unlocked;
                    for(var ink:g.ink)if(ink.text.endsWith("…")&&!ink.text.equals(Component.translatable(
                            "gui.molecularmanipulator.star_computation.catalog_waiting").getString()))issues.add(state+" clipped "+ink);
                    for(int a=0;a<g.ink.size();a++)for(int b=a+1;b<g.ink.size();b++)if(g.ink.get(a).overlaps(g.ink.get(b)))
                        issues.add(state+" overlap "+g.ink.get(a)+" / "+g.ink.get(b));
                }
            }
        }
        assertTrue(issues.isEmpty(),String.join("\n",issues.stream().limit(20).toList()));
    }

    @Test void englishEngineFeedbackAndPrimaryActionsRetainTheirWholeMessages() throws Exception {
        for(boolean dark:new boolean[]{false,true})try(var context=new Context(dark,"en_us")){
            var ui=new OsqUi("star_computation_engine");
            var screen=ResponsiveScreenTestFixture.allocate(StarComputationEngineScreen.class);
            var menu=ResponsiveScreenTestFixture.allocate(StarComputationEngineMenu.class);
            ResponsiveScreenTestFixture.set(screen,"ui",ui);ResponsiveScreenTestFixture.set(screen,"menu",menu);
            ResponsiveScreenTestFixture.set(screen,"font",context.font);ResponsiveScreenTestFixture.set(screen,"pendingVersion",-1);
            ResponsiveScreenTestFixture.set(screen,"resultUntil",Long.MAX_VALUE);
            menu.catalog=StarComputationRecipeCatalog.EMPTY;menu.formed=true;menu.networkOnline=true;
            var locale=JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
            for(String key:locale.keySet())if(key.startsWith("gui.molecularmanipulator.star_computation.result.")){
                String state=key.substring(key.lastIndexOf('.')+1);
                menu.actionResult=StarComputationEngineMenu.ActionResult.valueOf(state.toUpperCase(java.util.Locale.ROOT));
                if(menu.actionResult==StarComputationEngineMenu.ActionResult.NONE)continue;
                for(var node:StarComputationEngineMenu.NODES){
                    menu.resultNode=node.index();var g=new InkRecorder(context);screen.drawFG(g,0,0,0,0);
                    var result=g.ink.stream().filter(i->i.left>=212&&i.center()>253&&i.center()<263).findFirst().orElseThrow();
                    String name=Component.translatable("gui.molecularmanipulator.star_computation.node_short."
                            +node.translationKey().substring(node.translationKey().lastIndexOf('.')+1)).getString();
                    assertEquals(Component.translatable("gui.molecularmanipulator.star_computation.node_result",name,Component.translatable(key)).getString(),result.text);
                    assertEquals(1,result.scale,.001f);assertTrue(result.right<=383.5);
                }
            }
            for(var action:Map.of("refresh","refresh","prerequisites","view_prerequisites","guide","guide","overclock","overclock_off","unlock","unlocked_permanent").entrySet()){
                var box=ui.layout.findControl(action.getKey());
                var label=Component.translatable("gui.molecularmanipulator.star_computation."+action.getValue());
                float text=OsqFonts.widthF(context.font,label.getString(),OsqFonts.id("osq_entropy_600_7_5"));
                assertTrue(text+(action.getKey().equals("overclock")?41:8)<=box.w,action+" "+text);
            }
        }
    }

    @Test void englishTextBoxesDoNotMoveBindingsOrAlterTheChinesePresentation() throws Exception {
        for(boolean dark:new boolean[]{false,true})for(String language:List.of("zh_cn","en_us"))try(var context=new Context(dark,language)){
            Path assets=dark?Path.of("resourcepacks/OmniSequence Dark/assets/molecularmanipulator"):ASSETS;
            try(var paths=Files.list(assets.resolve("osq_light/layout"))){for(var path:paths.toList()){
                var source=JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("draw");
                var layout=OsqLayout.forTheme(path.getFileName().toString().replace(".json",""),dark);
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

    /** Execute production SkinButton state, resource and content code, omitting only its OpenGL frame. */
    private static void renderButtonContents(Button button,InkRecorder g) throws Exception {
        var type=button.getClass();var kindMethod=type.getDeclaredMethod("kind");var stateMethod=type.getDeclaredMethod("status");
        kindMethod.setAccessible(true);stateMethod.setAccessible(true);
        String kind=(String)kindMethod.invoke(button),state=(String)stateMethod.invoke(button);
        var visualMethod=type.getDeclaredMethod("visual",String.class,String.class,long.class);visualMethod.setAccessible(true);
        var visual=visualMethod.invoke(button,kind,state,OsqFonts.resourceGeneration());
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
            sequence.accept((index,style,cp)->{value.appendCodePoint(cp);faces.add(style.getFont());return true;});
            float cursor=x,minX=Float.POSITIVE_INFINITY,minY=Float.POSITIVE_INFINITY,maxX=Float.NEGATIVE_INFINITY,maxY=Float.NEGATIVE_INFINITY;
            int index=0;
            for(int cp:value.toString().codePoints().toArray()){
                var info=context.glyph(faces.get(index++),cp);
                final float gx=cursor;final float[] bounds={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                info.bake(sheet->{bounds[0]=gx+sheet.getLeft();bounds[1]=y+sheet.getTop();bounds[2]=gx+sheet.getRight();bounds[3]=y+sheet.getBottom();return null;});
                minX=Math.min(minX,bounds[0]);minY=Math.min(minY,bounds[1]);maxX=Math.max(maxX,bounds[2]);maxY=Math.max(maxY,bounds[3]);
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
        final ReloadableResourceManager resources=new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
        final Map<ResourceLocation,List<GlyphProvider>> providers=new HashMap<>();
        final Object oldResources,oldFont,oldOptions;final Language oldLanguage;final Font font;
        Context(boolean dark,String language) throws Exception {
            Class.forName(UiRenderRecorder.class.getName());
            var packs=new ArrayList<net.minecraft.server.packs.PackResources>();
            packs.add(pack("base",Path.of("src/main/resources")));
            if(dark)packs.add(pack("dark",Path.of("resourcepacks/OmniSequence Dark")));
            var field=ReloadableResourceManager.class.getDeclaredField("resources");field.setAccessible(true);
            field.set(resources,new MultiPackResourceManager(PackType.CLIENT_RESOURCES,packs));
            var client=Minecraft.getInstance();var rf=Minecraft.class.getDeclaredField("resourceManager");rf.setAccessible(true);oldResources=rf.get(client);rf.set(client,resources);
            oldOptions=client.options;
            var options=ResponsiveScreenTestFixture.allocate(net.minecraft.client.Options.class);options.languageCode=language;
            ResponsiveScreenTestFixture.set(client,"options",options);
            oldLanguage=Language.getInstance();Language.inject(ClientLanguage.loadFrom(resources,List.of(language),false));
            var splitter=new StringSplitter((cp,style)->glyph(style.getFont().equals(net.minecraft.network.chat.Style.DEFAULT_FONT)
                    ?OsqFonts.id("osq_nsc_600_7_5"):style.getFont(),cp).getAdvance());
            font=new Font(id->null,false){@Override public StringSplitter getSplitter(){return splitter;}};
            // Native EditBox also calls Font's substring helpers, which read this field directly.
            ResponsiveScreenTestFixture.set(font,"splitter",splitter);
            oldFont=client.font;ResponsiveScreenTestFixture.set(client,"font",font);OsqFonts.clearInkMetrics();
        }
        private static PathPackResources pack(String id,Path path){return new PathPackResources(new PackLocationInfo(id,Component.literal(id),PackSource.DEFAULT,Optional.empty()),path);}
        com.mojang.blaze3d.font.GlyphInfo glyph(ResourceLocation face,int cp){
            try{
                var list=providers.get(face);
                if(list==null){
                    list=new ArrayList<>();
                    var json=JsonParser.parseReader(resources.openAsReader(face.withPrefix("font/").withSuffix(".json"))).getAsJsonObject();
                    for(var item:json.getAsJsonArray("providers")){
                        var p=item.getAsJsonObject();if(!p.get("type").getAsString().equals("ttf"))continue;
                        var skip=p.get("skip");String skipped=skip.isJsonArray()?String.join("",skip.getAsJsonArray().asList().stream().map(v->v.getAsString()).toList()):skip.getAsString();
                        var shift=p.getAsJsonArray("shift");
                        var definition=new TrueTypeGlyphProviderDefinition(ResourceLocation.parse(p.get("file").getAsString()),p.get("size").getAsFloat(),p.get("oversample").getAsFloat(),
                                new TrueTypeGlyphProviderDefinition.Shift(shift.get(0).getAsFloat(),shift.get(1).getAsFloat()),skipped);
                        list.add(definition.unpack().left().orElseThrow().load(resources));
                    }
                    providers.put(face,list);
                }
                for(var provider:list){var glyph=provider.getGlyph(cp);if(glyph!=null)return glyph;}
                return (com.mojang.blaze3d.font.GlyphInfo.SpaceGlyphInfo)()->4;
            }catch(Exception e){throw new AssertionError(face+" "+Integer.toHexString(cp),e);}
        }
        @Override public void close() throws Exception {
            providers.values().forEach(list->list.forEach(GlyphProvider::close));
            var f=Minecraft.class.getDeclaredField("resourceManager");f.setAccessible(true);f.set(Minecraft.getInstance(),oldResources);
            ResponsiveScreenTestFixture.set(Minecraft.getInstance(),"font",oldFont);
            ResponsiveScreenTestFixture.set(Minecraft.getInstance(),"options",oldOptions);
            Language.inject(oldLanguage);OsqFonts.clearInkMetrics();resources.close();
        }
    }
}
