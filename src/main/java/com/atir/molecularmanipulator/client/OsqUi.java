package com.atir.molecularmanipulator.client;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import com.atir.molecularmanipulator.client.gui.theme.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
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
    Predicate<OsqLayout.Entry> visible = e -> !"tip".equals(e.kind);
    OsqUi(String name) {
        layout = OsqLayout.forTheme(name, false);
        var window = layout.find("window");
        palette = OsqPalette.of(layout.family, window.pad[3] > 1);
        // A status row shares one visual center, even when its labels use different fonts/sizes.
        for (var dot : layout.draw) if (dot.sprite != null && dot.sprite.contains("/dot_")) {
            float center = dot.y + dot.h / 2;
            for (var text : layout.draw) if ("text".equals(text.type)
                    && text.x >= dot.x - 52 && text.x <= dot.x + dot.w + 8
                    && Math.abs(text.baseline - center) <= 4.5f) text.centerY = center;
        }
        // Only hide text belonging to a tooltip layer AFTER its background.
        // A tooltip may cover ordinary telemetry that must remain visible when it closes.
        for(int i=0;i<layout.draw.size();i++)if("tip".equals(layout.draw.get(i).kind)) {
            var tip=layout.draw.get(i);hidden.add(tip);
            for(int j=i+1;j<layout.draw.size();j++) {
                var entry=layout.draw.get(j);
                if("sprite".equals(entry.type)&&(entry.sprite==null||!entry.sprite.contains("/icons/")))break;
                if("text".equals(entry.type)&&entry.x>=tip.x&&entry.x<tip.x+tip.w
                        &&entry.baseline>=tip.y&&entry.baseline<=tip.y+tip.h)hidden.add(entry);
            }
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
        for (var e : layout.draw) {
            if (e == rect || OsqLayout.sameRect(e, rect) || ("sprite".equals(e.type) && e.sprite != null && e.sprite.contains("/icons/")
                    && e.x >= rect.x && e.y >= rect.y && e.x + e.w <= rect.x + rect.w + 1
                    && e.y + e.h <= rect.y + rect.h + 1)) hidden.add(e);
            if ("text".equals(e.type) && e.x >= rect.x && e.x < rect.x + rect.w
                    && e.baseline >= rect.y && e.baseline <= rect.y + rect.h) hidden.add(e);
        }
    }
    Button button(String id, int left, int top, Supplier<Component> label, Runnable action) {
        var box = layout.findControl(id);
        own(box);
        return new SkinButton(box, left, top, label, action);
    }
    Button rect(float x, float y, float w, float h, int left, int top,
            Supplier<Component> label, Runnable action) {
        var e = new OsqLayout.Entry(); e.x=x; e.y=y; e.w=w; e.h=h;
        e.sprite=sprite(w<=22?"key13_normal":"button_normal");
        own(e);
        return new SkinButton(e, left, top, label, action);
    }
    Button tab(float x,float y,float w,float h,int left,int top,Supplier<Component> label,Runnable action) {
        var e=layout.draw.stream().filter(entry->entry.sprite!=null&&entry.sprite.contains("/tab_")
                &&Math.abs(entry.x-x)<.8f&&Math.abs(entry.y-y)<.8f).findFirst().orElseGet(()->{
                    var entry=new OsqLayout.Entry();entry.x=x;entry.y=y;entry.w=w;entry.h=h;
                    entry.sprite=sprite("tab_normal");return entry;
                });
        own(e);return new SkinButton(e,left,top,label,action);
    }
    void state(Button button, Supplier<String> state) { ((SkinButton) button).state = state; }
    void icon(Button button, Supplier<String> name) { ((SkinButton)button).iconName=name; }
    void itemIcon(Button button, Supplier<ItemStack> stack) { ((SkinButton)button).itemIcon=stack; }
    void outline(Button button) { ((SkinButton)button).outlined=true; }
    void centerLabel(Button button) { ((SkinButton)button).centerLabel=true; }
    EditBox field(String id, int left, int top, Font font, Component hint) {
        var box = layout.findControl(id); own(box);
        return new SkinField(box, left, top, font, hint);
    }
    void background(GuiGraphics g, Font font, int left, int top) {
        sprites.forEach((entry, value) -> entry.sprite = value.get());
        progress.forEach((entry, ratio) -> entry.w = progressWidths.get(entry)
                * (float)Math.clamp(ratio.getAsDouble(), 0, 1));
        layout.render(g, font, left, top,
                e -> e.w > 0 && !"tip".equals(e.kind) && visible.test(e) && !hidden.contains(e) && !"text".equals(e.type) && !"item".equals(e.type));
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
        var e = new OsqLayout.Entry(); e.x=x; e.baseline=y; e.w=maxWidth; e.size=6;
        e.fontId="molecularmanipulator:osq_nsc_600_6"; e.color=String.format("#%08X", color);
        layout.renderSingleLine(g, Minecraft.getInstance().font, e, value, 0, 0);
    }
    void centeredText(GuiGraphics g, Component value, float x, float centerY, float maxWidth, int color) {
        var e = new OsqLayout.Entry(); e.x=x; e.centerY=centerY; e.w=maxWidth; e.size=6;
        e.fontId="molecularmanipulator:osq_nsc_600_6"; e.color=String.format("#%08X", color);
        layout.renderSingleLine(g, Minecraft.getInstance().font, e, value, 0, 0);
    }
    static String compact(long number) { return DisplayNumbers.compact(number); }
    private static boolean spriteExists(String sprite) {
        var id = ResourceLocation.parse(sprite);
        return Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(), "textures/gui/sprites/" + id.getPath() + ".png")).isPresent();
    }
    private record ButtonKey(String kind, String status, boolean enabled, String sourceSprite) {}
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
    private class SkinButton extends Button {
        private final OsqLayout.Entry box;
        private final Supplier<Component> label;
        private final OsqLayout.Entry labelTemplate;
        private final List<OsqLayout.Entry> icons;
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
        private boolean outlined;
        private boolean centerLabel;
        SkinButton(OsqLayout.Entry box, int left, int top, Supplier<Component> label, Runnable action) {
            super(left+Math.round(box.x), top+Math.round(box.y), Math.round(box.w), Math.round(box.h),
                    label == null ? Component.empty() : label.get(), b -> action.run(), DEFAULT_NARRATION);
            this.box=box; this.label=label;
            labelTemplate=layout.draw.stream().filter(e -> "text".equals(e.type)
                    && e.x>=box.x && e.x<box.x+box.w && e.baseline>=box.y && e.baseline<=box.y+box.h)
                    .findFirst().orElse(null);
            icons=layout.draw.stream().filter(e -> "sprite".equals(e.type) && e.sprite!=null
                    && e.sprite.contains("/icons/") && e.x>=box.x && e.y>=box.y
                    && e.x+e.w<=box.x+box.w+1 && e.y+e.h<=box.y+box.h+1).toList();
            fallbackLabel.size=6; fallbackLabel.fontId="molecularmanipulator:osq_nsc_600_6";
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
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            if (label != null) setMessage(label.get());
            boolean small=getWidth()<=22;
            String kind = box.sprite != null && box.sprite.contains("edge_key") ? "edge_key"
                    : box.sprite != null && box.sprite.contains("tab_") ? "tab"
                    : box.sprite != null && box.sprite.contains("row_") ? "row" : small ? "key13" : "button";
            // Resolve the live status every draw; only its resource decisions are cached.
            String status=!active ? "disabled" : state != null ? state.get()
                    : isHoveredOrFocused() ? "hover"
                    : box.sprite != null && box.sprite.endsWith("button_danger") ? "danger" : "normal";
            long generation = OsqFonts.resourceGeneration();
            var visual = visual(kind, status, generation);
            background.sprite=visual.sprite; background.x=getX(); background.y=getY();
            background.w=getWidth(); background.h=getHeight(); background.pad=visual.pad;
            OsqLayout.drawSprite(g,background,0,0);
            if (outlined || itemIcon != null) {
                int color = isHoveredOrFocused() || "active".equals(status) ? palette.acc : palette.ink4;
                g.renderOutline(getX(),getY(),getWidth(),getHeight(),color);
            }
            if (itemIcon != null) {
                ItemStack stack=itemIcon.get();
                if (stack != null && !stack.isEmpty())
                    g.renderItem(stack,getX()+(getWidth()-16)/2,getY()+(getHeight()-16)/2);
                return;
            }
            for (var icon : icons) {
                String original=icon.sprite;
                String name=iconName==null?null:String.valueOf(iconName.get());
                String tint=!active?"dis":status.equals("active")||status.equals("danger")||status.equals("confirm")
                        ?palette.dark?"inv":"white":"ink";
                icon.sprite=iconSprites.resolve(generation,original,name,tint,OsqUi::spriteExists);
                float originalX=icon.x,originalY=icon.y;
                if(small&&icons.size()==1)icon.x=box.x+(getWidth()-icon.w)/2;
                icon.y=box.y+(getHeight()-icon.h)/2;
                OsqLayout.drawSprite(g, icon,getX()-Math.round(box.x), getY()-Math.round(box.y));
                icon.sprite=original;icon.x=originalX;icon.y=originalY;
            }
            if (labelTemplate != null) {
                // Design text extents describe example labels, not the available live label area.
                float originalWidth = labelTemplate.w;
                float originalX = labelTemplate.x;
                String originalAlignment = labelTemplate.alignment;
                labelTemplate.w = Math.max(1, box.x + getWidth() - labelTemplate.x - 4);
                if ((small || centerLabel) && icons.isEmpty()) {
                    labelTemplate.x = box.x;
                    labelTemplate.w = getWidth() - (small ? 0 : 6);
                    if (!small) labelTemplate.x += 3;
                    labelTemplate.alignment = "center";
                }
                String originalColor = labelTemplate.color;
                Float originalCenter = labelTemplate.centerY;
                labelTemplate.centerY = box.y + getHeight() / 2f;
                labelTemplate.color = visual.textColor(palette,kind,status,active);
                layout.renderSingleLine(g,Minecraft.getInstance().font,labelTemplate,getMessage(),
                        getX()-Math.round(box.x), getY()-Math.round(box.y));
                labelTemplate.w = originalWidth;
                labelTemplate.x = originalX;
                labelTemplate.alignment = originalAlignment;
                labelTemplate.color = originalColor;
                labelTemplate.centerY = originalCenter;
                return;
            }
            if (!icons.isEmpty()) return;
            fallbackLabel.x=getX()+3; fallbackLabel.baseline=getY()+getHeight()/2f+2;fallbackLabel.centerY=getY()+getHeight()/2f;
            fallbackLabel.w=getWidth()-6; fallbackLabel.color=visual.textColor(palette,kind,status,active);
            layout.renderSingleLine(g,Minecraft.getInstance().font,fallbackLabel,getMessage(),0,0);
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
        private final ResourceLocation face = ResourceLocation.parse("molecularmanipulator:osq_nsc_600_6");
        private final Component hint;
        private final OsqLayout.Entry background = new OsqLayout.Entry();
        private final OsqFieldMeasurements measurements = new OsqFieldMeasurements();
        private final OsqFieldMeasurements.Measure measure = text -> OsqFonts.widthF(Minecraft.getInstance().font,text,face);
        private String shownValue = "";
        private Component shownText = Component.literal("");
        private int display, highlight, color;
        SkinField(OsqLayout.Entry box,int left,int top,Font font,Component hint) {
            super(font,left+Math.round(box.x)+4,top+Math.round(box.y)+3,
                    Math.round(box.w)-8,Math.round(box.h)-4,hint);
            this.box=box;this.hint=hint;setBordered(false);setMaxLength(64);setTextColor(palette.ink);
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
            background.sprite=sprite(isFocused()?"field_focus":"field_normal");background.x=getX()-4;background.y=getY()-3;
            background.w=box.w;background.h=box.h;background.pad=box.pad;OsqLayout.drawSprite(g,background,0,0);
            var font=Minecraft.getInstance().font;String value=getValue();int cursor=getCursorPosition();
            var splitter=font.getSplitter();
            measurements.layout(value,cursor,display,getWidth(),font,splitter,
                    OsqFonts.metricsGeneration(font),fieldScaleKey(splitter),measure);
            display=measurements.display();int end=measurements.end();
            String shown=measurements.visibleText();
            if(!shownValue.equals(shown)) {shownValue=shown;shownText=Component.literal(shown);}
            // Keep the actual hint Component live: translations/arguments may change without editing the field.
            Component displayedText=value.isEmpty()?hint:shownText;
            float baseline=getY()-3+box.h/2+OsqFonts.centeredBaselineOffset(displayedText,face,6);
            if(value.isEmpty()&&!isFocused())OsqFonts.draw(g,font,hint,face,6,getX(),baseline,palette.ink3,0);
            else OsqFonts.draw(g,font,shownText,face,6,getX(),baseline,active?color:palette.disT,0);
            float cx=getX()+measurements.cursorWidth();
            if(isFocused()&&net.minecraft.Util.getMillis()/300%2==0)g.fill(Math.round(cx),getY(),Math.round(cx)+1,getY()+7,active?color:palette.disT);
            int selected=Math.clamp(highlight,display,end);
            if(selected!=cursor&&!getHighlighted().isEmpty()) {
                int hx=Math.round(getX()+measurements.widthTo(selected,measure));
                g.fill(net.minecraft.client.renderer.RenderType.guiTextHighlight(),Math.min(hx,Math.round(cx)),getY(),Math.max(hx,Math.round(cx)),getY()+7,0xFF6688FF);
            }
        }
    }
}
