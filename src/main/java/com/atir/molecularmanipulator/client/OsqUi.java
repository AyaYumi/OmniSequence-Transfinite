package com.atir.molecularmanipulator.client;

import appeng.client.gui.Icon;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.atir.molecularmanipulator.client.gui.theme.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Runtime adapter: supplied geometry and typography, actual menu data and native input. */
final class OsqUi {
    final OsqLayout layout;
    final OsqPalette palette;
    private final Map<OsqLayout.Entry, Supplier<Component>> values = new IdentityHashMap<>();
    private final Set<OsqLayout.Entry> hidden = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<OsqLayout.Entry, Supplier<String>> sprites = new IdentityHashMap<>();
    private final Map<OsqLayout.Entry, DoubleSupplier> progress = new IdentityHashMap<>();
    private final Map<OsqLayout.Entry, Float> progressWidths = new IdentityHashMap<>();
    private final Map<OsqLayout.Entry, IntSupplier> colors = new IdentityHashMap<>();
    private final Map<String, BooleanSupplier> selectedTabs = new LinkedHashMap<>();
    Predicate<OsqLayout.Entry> visible = e -> !"tip".equals(e.kind);
    OsqUi(String name) { this(OsqLayout.forTheme(name, false)); }
    OsqUi(OsqLayout layout) {
        this.layout = layout;
        var window = layout.find("window");
        palette = OsqPalette.of(layout.family, window.pad[3] > 1);
        // Explicit centers from the approved layouts take precedence. Only fill
        // missing status alignment, within the same source/floating layer.
        for (var dot : layout.draw) if (dot.sprite != null && dot.sprite.contains("/dot_")) {
            float center = dot.y + dot.h / 2;
            for (var text : layout.draw) if ("text".equals(text.type) && text.centerY == null
                    && layout.layerOf(text) == layout.layerOf(dot)
                    && (text.x >= dot.x + dot.w && text.x - dot.x - dot.w <= 8
                        || text.x + text.w <= dot.x && dot.x - text.x - text.w <= 8)
                    && Math.abs(text.baseline - center) <= 4.5f) text.centerY = center;
        }
        for (var entry : layout.draw) {
            var layer = layout.layerOf(entry);
            if ("tip".equals(entry.kind) || layer != null && "tip".equals(layer.kind)) hidden.add(entry);
        }
    }
    static Component tr(String key, Object... args) { return Component.translatable(key, args); }
    void text(String lang, Supplier<Component> value) {
        layout.draw.stream().filter(e -> "text".equals(e.type) && lang.equals(e.lang))
                .forEach(e -> values.put(e, value));
    }
    void at(float x, float baseline, Supplier<Component> value) {
        layout.draw.stream().filter(e -> "text".equals(e.type)
                && Math.abs(e.x - x) < .8f && Math.abs(e.baseline - baseline) < .8f)
                .forEach(e -> values.put(e, value));
    }
    void omit(String lang) { text(lang, Component::empty); }
    void width(float x,float baseline,float width) {
        layout.draw.stream().filter(e->"text".equals(e.type)&&Math.abs(e.x-x)<.8f&&Math.abs(e.baseline-baseline)<.8f)
                .findFirst().ifPresent(e->e.w=width);
    }
    void number(float x, float y, Supplier<?> value) { at(x, y, () -> Component.literal(String.valueOf(value.get()))); }
    void sprite(float x, float y, Supplier<String> value) {
        layout.draw.stream().filter(e -> e.sprite != null && Math.abs(e.x-x)<.8f && Math.abs(e.y-y)<.8f)
                .findFirst().ifPresent(e -> sprites.put(e, value));
    }
    String sprite(String name) {
        return OsqSprites.resolve(palette,name).toString().replace("osq_dark/","osq_light/");
    }
    void progress(float x, float y, DoubleSupplier ratio) {
        layout.draw.stream().filter(e -> e.sprite != null && e.sprite.contains("/bar_fill_")
                && Math.abs(e.x-x)<.8f && Math.abs(e.y-y)<.8f).findFirst().ifPresent(e -> {
                    progress.put(e, ratio);
                    float width=layout.draw.stream().filter(track->track.sprite!=null&&track.sprite.contains("/bar_track")
                            &&Math.abs(track.x-x)<.8f&&Math.abs(track.y-y)<.8f).findFirst().map(track->track.w).orElse(e.w);
                    progressWidths.put(e, width);
                });
    }
    void color(String lang, IntSupplier value) {
        layout.draw.stream().filter(e -> "text".equals(e.type) && lang.equals(e.lang))
                .forEach(e -> colors.put(e, value));
    }
    List<OsqLayout.Entry> entries(String id) {
        return layout.draw.stream().filter(e -> id.equals(e.id)).toList();
    }
    void slots(List<? extends Slot> slots, String id) {
        var boxes = entries(id).stream().filter(e -> "slot".equals(e.kind)).toList();
        for (int i = 0; i < Math.min(slots.size(), boxes.size()); i++) {
            slots.get(i).x = Math.round(boxes.get(i).x) + 1;
            slots.get(i).y = Math.round(boxes.get(i).y) + 1;
            // Retain the AE2 slot role; ResponsiveContainerScreen renders only its native placeholder outline.
        }
    }
    void inventory(AEBaseMenu menu) {
        var list = new ArrayList<Slot>(menu.getSlots(SlotSemantics.PLAYER_INVENTORY));
        list.addAll(menu.getSlots(SlotSemantics.PLAYER_HOTBAR));
        slots(list, "inv");
    }
    void slot(Slot slot, String id) { if (slot != null) slots(List.of(slot), id); }
    void hide(Predicate<OsqLayout.Entry> filter) { layout.draw.stream().filter(filter).forEach(hidden::add); }
    void slotBackground(GuiGraphics g, float x, float y) {
        var e = new OsqLayout.Entry(); e.sprite = sprite("slot"); e.x = x; e.y = y; e.w = e.h = 18;
        OsqLayout.drawSprite(g, e, 0, 0);
    }
    private void own(OsqLayout.Entry rect) {
        layout.draw.stream().filter(e -> layout.owns(rect, e)).forEach(hidden::add);
    }

    static float controlCenter(String kind, String status, float height, boolean pagination) {
        if ("tab".equals(kind)) return OsqTabs.textCenter(height);
        if (pagination) return height / 2;
        boolean sunk = Set.of("active", "pressed", "disabled").contains(status);
        if (kind.equals("key13") || kind.equals("edge_key")) return (sunk ? 2 : 1) + (height - 4) / 2;
        return 1 + (sunk ? 2 : status.equals("hover") ? 1 : 0) + (height - 5) / 2;
    }

    private float fieldInset(OsqLayout.Entry box) {
        return layout.draw.stream().filter(e -> e.sprite != null && e.sprite.contains("/icons/") && layout.owns(box, e))
                .map(e -> e.x + e.w - box.x + 4).max(Float::compare).orElse(4f);
    }
    Button button(String id, int left, int top, Supplier<Component> label, Runnable action) {
        var box = layout.findControl(id);
        own(box);
        return new SkinButton(box, left, top, label, action);
    }
    Button rect(float x, float y, float w, float h, int left, int top,
            Supplier<Component> label, Runnable action) {
        return new SkinButton(buttonBox(x,y,w,h), left, top, label, action);
    }
    OsqLayout.Entry buttonBox(float x, float y, float w, float h) {
        var e = new OsqLayout.Entry(); e.x=x; e.y=y; e.w=w; e.h=h;
        e.sprite=sprite(w<=22?"key13_normal":"button_normal");
        own(e);
        return e;
    }
    Button tab(String id, int left, int top, Supplier<Component> label,
            BooleanSupplier selected, Runnable action) {
        var box=layout.findControl(id);own(box);
        var tab=new PageTab(box,left,top,label,selected,action);
        tabSelection(id,()->tab.visible&&selected.getAsBoolean());
        return tab;
    }
    /** The research layer supplies the parent while its screen owns the actual tab widgets. */
    void tabSelection(String id, BooleanSupplier selected) { selectedTabs.put(id, selected); }
    Map<OsqLayout.Entry, OsqLayout.Entry> tabSurfaces() {
        var surfaces=new IdentityHashMap<OsqLayout.Entry, OsqLayout.Entry>();
        selectedTabs.forEach((id,selected)->{
            var box=layout.findControl(id);
            if(selected.getAsBoolean()&&box.tabSurface!=null) {
                var parent=layout.find(box.tabPanel);
                if(parent!=null&&visible.test(parent)&&!hidden.contains(parent)) {
                    box.tabSurface.joinedTab=box;
                    surfaces.put(parent,box.tabSurface);
                }
            }
        });
        return surfaces;
    }
    void state(Button button, Supplier<String> state) { ((SkinButton) button).state = state; }
    void icon(Button button, Supplier<String> name) { ((SkinButton)button).iconName=name; }
    void itemIcon(Button button, Supplier<ItemStack> stack) { ((SkinButton)button).itemIcon=stack; }
    EditBox field(String id, int left, int top, Font font, Component hint) {
        var box = layout.findControl(id); own(box);
        return new SkinField(box, left, top, font, hint);
    }
    void background(GuiGraphics g, Font font, int left, int top) {
        sprites.forEach((entry, value) -> entry.sprite = value.get());
        progress.forEach((entry, ratio) -> entry.w = progressWidths.get(entry)
                * (float)Math.clamp(ratio.getAsDouble(), 0, 1));
        layout.render(g, font, left, top,
                e -> e.w > 0 && !"tip".equals(e.kind) && visible.test(e) && !hidden.contains(e) && !"text".equals(e.type) && !"item".equals(e.type), tabSurfaces());
    }
    void foreground(GuiGraphics g, Font font) {
        for (var e : layout.draw) {
            if (!"text".equals(e.type) || hidden.contains(e) || !visible.test(e)) continue;
            var supplied = values.get(e);
            var color = colors.get(e);
            if (color != null) e.color = String.format("#%08X", color.getAsInt());
            if (supplied != null) layout.renderSingleLine(g, font, e, supplied.get(), 0, 0);
            else if (!e.dynamic && e.langArgs == null)
                layout.renderText(g, font, e, e.staticText(), 0, 0);
        }
    }
    void smallText(GuiGraphics g, Component value, float x, float y, float maxWidth) {
        smallText(g, value, x, y, maxWidth, palette.ink);
    }
    void smallText(GuiGraphics g, Component value, float x, float y, float maxWidth, int color) {
        var e = new OsqLayout.Entry(); e.x=x; e.baseline=y; e.w=maxWidth; e.size=7.5f;
        e.fontId="minecraft:default"; e.color=String.format("#%08X", color);
        layout.renderSingleLine(g, Minecraft.getInstance().font, e, value, 0, 0);
    }
    void centeredText(GuiGraphics g, Component value, float x, float centerY, float maxWidth, int color) {
        var e = new OsqLayout.Entry(); e.x=x; e.centerY=centerY; e.w=maxWidth; e.size=7.5f;
        e.fontId="minecraft:default"; e.color=String.format("#%08X", color);
        layout.renderSingleLine(g, Minecraft.getInstance().font, e, value, 0, 0);
    }
    static String compact(long number) { return DisplayNumbers.compact(number); }
    private static boolean spriteExists(String sprite) {
        var id = ResourceLocation.parse(sprite);
        return Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), "textures/gui/sprites/" + id.getPath() + ".png")).isPresent();
    }
    private final class PageTab extends Button {
        private final OsqLayout.Entry box;
        private final Supplier<Component> label;
        private final BooleanSupplier selected;
        private final OsqLayout.Entry text = new OsqLayout.Entry();
        PageTab(OsqLayout.Entry box,int left,int top,Supplier<Component> label,
                BooleanSupplier selected,Runnable action) {
            super(left+Math.round(box.x),top+Math.round(box.y),Math.round(box.w),Math.round(box.h),
                    label.get(),button->action.run(),DEFAULT_NARRATION);
            this.box=box;this.label=label;this.selected=selected;
            var template=layout.draw.stream().filter(e->"text".equals(e.type)&&layout.owns(box,e)).findFirst().orElse(null);
            text.size=7.5f;text.fontId=template==null?"minecraft:default":template.fontId;
            text.alignment="center";
        }
        @Override protected void renderWidget(GuiGraphics graphics,int mx,int my,float pt) {
            setMessage(label.get());
            boolean chosen=selected.getAsBoolean();
            String state=!active?"disabled":chosen?"active":isHoveredOrFocused()?"hover":"normal";
            OsqTabs.draw(graphics,getX(),getY(),getWidth(),getHeight(),palette,state,chosen,box.tabSurface!=null);
            text.x=getX()+OsqTabs.TEXT_INSET;text.w=getWidth()-2*OsqTabs.TEXT_INSET;
            text.centerY=getY()+OsqTabs.textCenter(getHeight());
            text.color=chosen&&active&&box.tabTextColor!=null?box.tabTextColor
                    :String.format("#%08X",OsqTabs.textColor(palette,state));
            layout.renderSingleLine(graphics,Minecraft.getInstance().font,text,getMessage(),0,0);
        }
    }
    private record ButtonKey(String kind, String status, boolean enabled, String sourceSprite) {}
    private record ButtonIcon(OsqLayout.Entry entry, String sprite, OsqIconInk.Center ink, float width, float height) {
        float visibleWidth() { return width * ink.width(); }
    }
    private static final class ButtonVisual {
        final String sprite;
        final int[] pad;
        private String textColor;
        private int textArgb;
        ButtonVisual(String sprite, int[] pad) { this.sprite = sprite; this.pad = pad; }
        String textColor(OsqPalette palette, String kind, String status, boolean enabled) {
            int color = palette.controlText(kind, status, enabled);
            if (textColor == null || textArgb != color) {
                textArgb = color;
                textColor = String.format("#%08X", color);
            }
            return textColor;
        }
    }
    class SkinButton extends Button {
        private final OsqLayout.Entry box;
        private final Supplier<Component> label;
        private final OsqLayout.Entry labelTemplate;
        private final List<OsqLayout.Entry> icons;
        private final Icon nativeIcon;
        private final OsqLayout.Entry background = new OsqLayout.Entry();
        private final OsqLayout.Entry fallbackLabel = new OsqLayout.Entry();
        private final OsqButtonIconCache iconSprites = new OsqButtonIconCache();
        private final Map<ButtonKey, ButtonVisual> visuals = new LinkedHashMap<>(8, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<ButtonKey, ButtonVisual> entry) { return size() > 32; }
        };
        private long resourceGeneration = Long.MIN_VALUE;
        private ButtonKey lastKey;
        private ButtonVisual lastVisual;
        private Supplier<String> state;
        private Supplier<String> iconName;
        private Supplier<ItemStack> itemIcon;
        private Tooltip overflowTooltip;
        private Component overflowMessage;
        SkinButton(OsqLayout.Entry box, int left, int top, Supplier<Component> label, Runnable action) {
            super(left+Math.round(box.x), top+Math.round(box.y), Math.round(box.w), Math.round(box.h),
                    label == null ? Component.empty() : label.get(), b -> action.run(), DEFAULT_NARRATION);
            this.box=box; this.label=label;
            nativeIcon=box.aeIcon==null?null:Icon.valueOf(box.aeIcon);
            labelTemplate=layout.draw.stream().filter(e -> "text".equals(e.type) && layout.owns(box, e))
                    .findFirst().orElse(null);
            icons=layout.draw.stream().filter(e -> "sprite".equals(e.type) && e.sprite!=null
                    && e.sprite.contains("/icons/") && layout.owns(box, e)).toList();
            boolean direction=Arrays.asList(OsqNeighborDirections.IDS).contains(box.id);
            fallbackLabel.size=direction?8:7.5f;
            fallbackLabel.fontId="minecraft:default";
            fallbackLabel.alignment="center";
        }
        private ButtonVisual visual(String kind, String status, long generation) {
            if (resourceGeneration != generation) {
                resourceGeneration = generation;
                visuals.clear();
                lastKey = null;
            }
            if (lastKey != null && lastKey.kind.equals(kind) && Objects.equals(lastKey.status, status)
                    && lastKey.enabled == active && Objects.equals(lastKey.sourceSprite, box.sprite)) return lastVisual;
            var key = new ButtonKey(kind, status, active, box.sprite);
            var visual = visuals.get(key);
            if (visual == null) {
                String sprite = kind.equals("edge_key") ? status.equals("active") ? "edge_key_active" : "edge_key" : kind+"_"+status;
                var id = OsqSprites.resolve(palette, sprite);
                id = ResourceLocation.fromNamespaceAndPath(OsqSprites.NS, id.getPath().replace("osq_dark/", "osq_light/"));
                if (!spriteExists(id.toString())) id = ResourceLocation.parse(box.sprite);
                int[] pad = OsqSprites.pad((palette.dark ? "osq_dark/" : "osq_light/")
                        + id.getPath().substring("osq_light/".length()));
                visual = new ButtonVisual(id.toString(), pad);
                visuals.put(key, visual);
            }
            lastKey = key;
            return lastVisual = visual;
        }
        private String kind() {
            return box.sprite != null && box.sprite.contains("edge_key") ? "edge_key"
                    : box.sprite != null && box.sprite.contains("tab_") ? "tab"
                    : box.sprite != null && box.sprite.contains("row_") ? "row" : getWidth()<=22 ? "key13" : "button";
        }
        private String status() {
            // Resolve the live status every draw; only its resource decisions are cached.
            String status=!active ? "disabled" : state != null ? state.get()
                    : box.sprite != null && box.sprite.endsWith("button_danger") ? "danger" : "normal";
            return active && "normal".equals(status) && isHoveredOrFocused() ? "hover" : status;
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            String kind=kind(), status=status();
            float center=controlCenter(kind,status,getHeight(),Set.of("prev","next").contains(box.id == null ? "" : box.id));
            var visual = visual(kind, status, OsqFonts.resourceGeneration());
            background.sprite=visual.sprite; background.x=getX(); background.y=getY();
            background.w=getWidth(); background.h=getHeight(); background.pad=visual.pad;
            OsqLayout.drawSprite(g,background,0,0);
            renderContents(g,kind,status,center,visual);
        }
        /** Keep live content/typography separate from the GPU-backed frame. */
        private void renderContents(GuiGraphics g, String kind, String status, float center, ButtonVisual visual) {
            if (label != null) setMessage(label.get());
            boolean iconOnly=labelTemplate==null&&(nativeIcon!=null||itemIcon!=null||!icons.isEmpty());
            if(iconOnly)updateAutomaticTooltip(!getMessage().getString().isBlank());
            long generation=OsqFonts.resourceGeneration();
            if (itemIcon != null) {
                int color = isHoveredOrFocused() || "active".equals(status) ? palette.acc : palette.ink4;
                g.renderOutline(getX(),getY(),getWidth(),getHeight(),color);
            }
            if (itemIcon != null) {
                ItemStack stack=itemIcon.get();
                if (stack != null && !stack.isEmpty()) {
                    g.renderItem(stack,getX()+(getWidth()-16)/2,Math.round(getY()+center-8));
                    return;
                }
                // Empty neighbors still show their localized direction; occupied
                // neighbors retain the live item icon and full tooltip.
            }
            if (nativeIcon != null) {
                var icon=!active&&nativeIcon==Icon.COG?Icon.COG_DISABLED:nativeIcon;
                float size=Math.min(16,Math.min(getWidth(),getHeight())-6);
                var ink=OsqIconInk.center(icon);
                g.pose().pushPose();
                g.pose().translate(getX()+getWidth()/2f-ink.x()*size,getY()+center-ink.y()*size,0);
                g.pose().scale(size/icon.width,size/icon.height,1);
                icon.getBlitter().dest(0,0).opacity(active||nativeIcon==Icon.COG?1:.5f).blit(g);
                g.pose().popPose();
                return;
            }
            var renderedIcons=new ArrayList<ButtonIcon>(icons.size());
            float iconSpan=0;
            for(var icon:icons){
                String name=iconName==null?null:String.valueOf(iconName.get());
                String tint=!active?"dis":!palette.dark ? status.equals("hover")?"ink":"white"
                        :status.equals("danger")||status.equals("confirm")?"white":status.equals("active")?"inv":"ink";
                String sprite=iconSprites.resolve(generation,icon.sprite,name,tint,OsqUi::spriteExists);
                var ink=OsqIconInk.center(sprite);
                var rendered=new ButtonIcon(icon,sprite,ink,ink.fitWidth(icon.w,icon.h),ink.fitHeight(icon.w,icon.h));
                renderedIcons.add(rendered);iconSpan+=rendered.visibleWidth();
            }
            iconSpan+=Math.max(0,icons.size()-1)*3;
            // Compact actions need less chrome, not a smaller or abbreviated label.
            float inset=labelTemplate==null?3:getWidth()<=64?4:7;
            float gap=icons.isEmpty()?0:getWidth()<=64?3:6;
            float available=Math.max(1,getWidth()-2*inset-iconSpan-gap);
            String plain=getMessage().getString();
            var textStyle=labelTemplate==null?fallbackLabel:labelTemplate;
            float tracking=Math.max(0,plain.codePointCount(0,plain.length())-1)*textStyle.letterSpacing;
            float fullTextWidth=Math.max(0,OsqFonts.widthF(Minecraft.getInstance().font,
                    plain,OsqFonts.id(textStyle.fontId),textStyle.size)+tracking);
            float textWidth=Math.min(available,fullTextWidth);
            if(!iconOnly)updateAutomaticTooltip(fullTextWidth>available);
            float contentLeft=getX()+(getWidth()-iconSpan-(labelTemplate==null?0:gap+textWidth))/2f;
            float cursor=contentLeft;
            for (var rendered : renderedIcons) {
                var icon=rendered.entry();String original=icon.sprite;
                float originalX=icon.x,originalY=icon.y,originalWidth=icon.w,originalHeight=icon.h;
                Float originalDrawX=icon.drawX,originalDrawY=icon.drawY;
                icon.drawX=icon.drawY=null;
                icon.sprite=rendered.sprite();icon.w=rendered.width();icon.h=rendered.height();
                icon.x=cursor+rendered.visibleWidth()/2-rendered.ink().x()*icon.w;
                icon.y=getY()+center-rendered.ink().y()*icon.h;
                cursor+=rendered.visibleWidth()+3;
                // Widget bounds are rounded by vanilla. Do not reintroduce the
                // source rectangle's half-pixel offset into only its contents.
                OsqLayout.drawSprite(g,icon,0,0);
                icon.sprite=original;icon.x=originalX;icon.y=originalY;icon.w=originalWidth;icon.h=originalHeight;
                icon.drawX=originalDrawX;icon.drawY=originalDrawY;
            }
            if (labelTemplate != null) {
                // Design text extents describe example labels, not the available live label area.
                float originalWidth = labelTemplate.w;
                float originalX = labelTemplate.x;
                String originalAlignment = labelTemplate.alignment;
                Float originalDrawX = labelTemplate.drawX;
                labelTemplate.drawX = null;
                labelTemplate.x = icons.isEmpty()?getX()+inset:contentLeft+iconSpan+gap;
                labelTemplate.w = icons.isEmpty()?getWidth()-2*inset:Math.max(1,textWidth);
                labelTemplate.alignment = "center";
                String originalColor = labelTemplate.color;
                Float originalCenter = labelTemplate.centerY;
                labelTemplate.centerY = getY() + center;
                labelTemplate.color = visual.textColor(palette,kind,status,active);
                layout.renderSingleLine(g,Minecraft.getInstance().font,labelTemplate,getMessage(),0,0);
                labelTemplate.w = originalWidth;
                labelTemplate.x = originalX;
                labelTemplate.alignment = originalAlignment;
                labelTemplate.drawX = originalDrawX;
                labelTemplate.color = originalColor;
                labelTemplate.centerY = originalCenter;
                return;
            }
            if (!icons.isEmpty()) return;
            fallbackLabel.x=getX()+3; fallbackLabel.baseline=getY()+center+2;fallbackLabel.centerY=getY()+center;
            fallbackLabel.w=getWidth()-6; fallbackLabel.color=visual.textColor(palette,kind,status,active);
            layout.renderSingleLine(g,Minecraft.getInstance().font,fallbackLabel,getMessage(),0,0);
        }
        private void updateAutomaticTooltip(boolean needed) {
            if(getTooltip()!=null&&getTooltip()!=overflowTooltip)return;
            if(needed){
                if(!getMessage().equals(overflowMessage)){
                    overflowMessage=getMessage();overflowTooltip=Tooltip.create(overflowMessage);
                }
                setTooltip(overflowTooltip);
            }else setTooltip(null);
        }
    }
    private static int fieldScaleKey(Object splitter) {
        return splitter.getClass().getName().equals("icyllis.modernui.mc.text.ModernStringSplitter")
                ? ModernFieldMetrics.baseSizeKey() : 0;
    }
    /** Same optional, live base-size input as OsqFonts; resource generation alone cannot track it. */
    private static final class ModernFieldMetrics {
        private static final java.lang.reflect.Field SIZE = sizeField();
        private static java.lang.reflect.Field sizeField() {
            try {
                return Class.forName("icyllis.modernui.mc.text.TextLayoutProcessor", false,
                        OsqUi.class.getClassLoader()).getField("sBaseFontSize");
            } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
        }
        static int baseSizeKey() {
            try { return Float.floatToIntBits(SIZE == null ? 8 : SIZE.getFloat(null)); }
            catch (IllegalAccessException ignored) { return Float.floatToIntBits(8); }
        }
    }
    private class SkinField extends EditBox {
        private final OsqLayout.Entry box;
        private final float inset;
        private final List<OsqLayout.Entry> icons;
        private final ResourceLocation face = net.minecraft.network.chat.Style.DEFAULT_FONT;
        private final Component hint;
        private final OsqLayout.Entry background = new OsqLayout.Entry();
        private final OsqFieldMeasurements measurements = new OsqFieldMeasurements();
        private final OsqLayout.Entry hintStyle = new OsqLayout.Entry();
        private final OsqFieldMeasurements.Measure measure = text -> OsqFonts.widthF(Minecraft.getInstance().font,text,face);
        private String shownValue = "";
        private Component shownText = Component.literal("");
        private int display, highlight, color;
        SkinField(OsqLayout.Entry box,int left,int top,Font font,Component hint) {
            super(font,left+Math.round(box.x+fieldInset(box)),top+Math.round(box.y)+3,
                    Math.round(box.w-fieldInset(box)-4),Math.round(box.h)-4,hint);
            this.box=box;this.hint=hint;this.inset=fieldInset(box);
            hintStyle.size=7.5f;hintStyle.fontId=face.toString();hintStyle.color=String.format("#%08X",palette.ink3);
            icons=layout.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/")&&layout.owns(box,e)).toList();
            setBordered(false);setMaxLength(64);setTextColor(palette.ink);
            setTextColorUneditable(palette.disT);setTextShadow(false);
            setFormatter((text,position)->OsqFonts.styled(Component.literal(text),face).getVisualOrderText());
        }
        @Override public void setTextColor(int color) { super.setTextColor(color); this.color=color; }
        @Override public void setHighlightPos(int position) { super.setHighlightPos(position); highlight=position; }
        @Override public void onClick(double mouseX, double mouseY) {
            var font=Minecraft.getInstance().font; var splitter=font.getSplitter();
            int position=measurements.positionAt(getValue(),display,mouseX-getX(),font,splitter,
                    OsqFonts.metricsGeneration(font),fieldScaleKey(splitter),measure);
            moveCursorTo(position,net.minecraft.client.gui.screens.Screen.hasShiftDown());
        }
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float pt) {
            if (!visible) return;
            background.sprite=sprite(isFocused()?"field_focus":"field_normal");background.x=getX()-inset;background.y=getY()-3;
            background.w=box.w;background.h=box.h;background.pad=box.pad;OsqLayout.drawSprite(g,background,0,0);
            for(var icon:icons){
                float x=icon.x,y=icon.y;
                Float drawX=icon.drawX,drawY=icon.drawY;icon.drawX=icon.drawY=null;
                var ink=OsqIconInk.center(icon.sprite);
                icon.x=background.x+icon.x-box.x;
                icon.y=background.y+box.h/2-ink.y()*icon.h;
                OsqLayout.drawSprite(g,icon,0,0);
                icon.x=x;icon.y=y;icon.drawX=drawX;icon.drawY=drawY;
            }
            renderContents(g);
        }
        private void renderContents(GuiGraphics g) {
            var font=Minecraft.getInstance().font;String value=getValue();int cursor=getCursorPosition();
            var splitter=font.getSplitter();
            measurements.layout(value,cursor,display,getWidth(),font,splitter,
                    OsqFonts.metricsGeneration(font),fieldScaleKey(splitter),measure);
            display=measurements.display();int end=measurements.end();
            String shown=measurements.visibleText();
            if(!shownValue.equals(shown)) {shownValue=shown;shownText=Component.literal(shown);}
            // Keep the actual hint Component live: translations/arguments may change without editing the field.
            Component displayedText=value.isEmpty()?hint:shownText;
            float center=getY()-3+box.h/2;
            float baseline=center+OsqFonts.centeredBaselineOffset(displayedText,face,7.5f);
            if(value.isEmpty()&&!isFocused()){
                hintStyle.x=getX();hintStyle.centerY=center;hintStyle.w=getWidth();
                layout.renderSingleLine(g,font,hintStyle,hint,0,0);
            }else OsqFonts.draw(g,font,shownText,face,7.5f,getX(),baseline,active?color:palette.disT,0);
            int textTop=Math.round(center-4),textBottom=textTop+8;
            float cx=getX()+measurements.cursorWidth();
            if(isFocused()&&net.minecraft.Util.getMillis()/300%2==0)g.fill(Math.round(cx),textTop,Math.round(cx)+1,textBottom,active?color:palette.disT);
            int selected=Math.clamp(highlight,display,end);
            if(selected!=cursor&&!getHighlighted().isEmpty()) {
                int hx=Math.round(getX()+measurements.widthTo(selected,measure));
                g.fill(net.minecraft.client.renderer.RenderType.guiTextHighlight(),Math.min(hx,Math.round(cx)),textTop,Math.max(hx,Math.round(cx)),textBottom,0xFF6688FF);
            }
        }
    }
}
