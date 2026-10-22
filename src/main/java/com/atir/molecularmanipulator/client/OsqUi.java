package com.atir.molecularmanipulator.client;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
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
                layout.renderText(g, font, e, e.lang == null ? Component.literal(e.text) : tr(e.lang), 0, 0);
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
    private class SkinButton extends Button {
        private final OsqLayout.Entry box;
        private final Supplier<Component> label;
        private final OsqLayout.Entry labelTemplate;
        private final List<OsqLayout.Entry> icons;
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
        }
        @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            if (label != null) setMessage(label.get());
            boolean small=getWidth()<=22;
            String kind = box.sprite != null && box.sprite.contains("edge_key") ? "edge_key"
                    : box.sprite != null && box.sprite.contains("tab_") ? "tab"
                    : box.sprite != null && box.sprite.contains("row_") ? "row" : small ? "key13" : "button";
            String status=!active ? "disabled" : state != null ? state.get()
                    : isHoveredOrFocused() ? "hover"
                    : box.sprite != null && box.sprite.endsWith("button_danger") ? "danger" : "normal";
            String sprite=kind.equals("edge_key") ? status.equals("active")?"edge_key_active":"edge_key" : kind+"_"+status;
            var rm=Minecraft.getInstance().getResourceManager();
            ResourceLocation id=OsqSprites.resolve(palette,sprite);
            id=ResourceLocation.fromNamespaceAndPath(OsqSprites.NS,id.getPath().replace("osq_dark/","osq_light/"));
            if (!rm.getResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
                    "textures/gui/sprites/"+id.getPath()+".png")).isPresent()) {
                id=ResourceLocation.parse(box.sprite);
            }
            int[] pad=OsqSprites.pad((palette.dark ? "osq_dark/" : "osq_light/")
                    +id.getPath().substring("osq_light/".length()));
            var draw=new OsqLayout.Entry(); draw.sprite=id.toString(); draw.x=getX(); draw.y=getY();
            draw.w=getWidth(); draw.h=getHeight(); draw.pad=pad;
            OsqLayout.drawSprite(g,draw,0,0);
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
                if(iconName!=null)icon.sprite=original.replaceAll("/icons/[^/]+_(ink2|ink|mute|white|acc|dis|inv)_(\\d+)$",
                        "/icons/"+iconName.get()+"_$1_$2");
                String tint=!active?"dis":status.equals("active")||status.equals("danger")||status.equals("confirm")
                        ?palette.dark?"inv":"white":"ink";
                String candidate=icon.sprite.replaceAll("_(ink2|ink|mute|white|acc|dis|inv)_(\\d+)$","_"+tint+"_$2");
                var candidateId=ResourceLocation.parse(candidate);
                if(rm.getResource(ResourceLocation.fromNamespaceAndPath(candidateId.getNamespace(),
                        "textures/gui/sprites/"+candidateId.getPath()+".png")).isPresent())icon.sprite=candidate;
                else {
                    String fallback=icon.sprite.replaceAll("_(ink2|ink|mute|white|acc|dis|inv)_\\d+$","_"+tint+"_8");
                    var fallbackId=ResourceLocation.parse(fallback);
                    if(rm.getResource(ResourceLocation.fromNamespaceAndPath(fallbackId.getNamespace(),"textures/gui/sprites/"+fallbackId.getPath()+".png")).isPresent())icon.sprite=fallback;
                }
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
                labelTemplate.color = String.format("#%08X", palette.controlText(kind,status,active));
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
            var text=new OsqLayout.Entry();text.x=getX()+3; text.baseline=getY()+getHeight()/2f+2;text.centerY=getY()+getHeight()/2f;
            text.w=getWidth()-6; text.size=6; text.fontId="molecularmanipulator:osq_nsc_600_6";
            text.alignment="center";
            text.color=String.format("#%08X",palette.controlText(kind,status,active));
            layout.renderSingleLine(g,Minecraft.getInstance().font,text,getMessage(),0,0);
        }
    }
    private class SkinField extends EditBox {
        private final OsqLayout.Entry box;
        private final ResourceLocation face = ResourceLocation.parse("molecularmanipulator:osq_nsc_600_6");
        private final Component hint;
        private int display, highlight, color;
        SkinField(OsqLayout.Entry box,int left,int top,Font font,Component hint) {
            super(font,left+Math.round(box.x)+4,top+Math.round(box.y)+3,
                    Math.round(box.w)-8,Math.round(box.h)-4,hint);
            this.box=box;this.hint=hint;setBordered(false);setMaxLength(64);setTextColor(palette.ink);
            setTextColorUneditable(palette.disT);setTextShadow(false);
            setFormatter((text,position)->OsqFonts.styled(Component.literal(text),
                    ResourceLocation.parse("molecularmanipulator:osq_nsc_600_6")).getVisualOrderText());
        }
        @Override public void setTextColor(int color) { super.setTextColor(color); this.color=color; }
        @Override public void setHighlightPos(int position) { super.setHighlightPos(position); highlight=position; }
        @Override public void onClick(double mouseX, double mouseY) {
            String value=getValue(); int position=Math.min(display,value.length());
            while(position<value.length()) {
                int next=value.offsetByCodePoints(position,1);
                float width=OsqFonts.widthF(Minecraft.getInstance().font,value.substring(display,next),face);
                if(width>mouseX-getX())break;position=next;
            }
            moveCursorTo(position,net.minecraft.client.gui.screens.Screen.hasShiftDown());
        }
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float pt) {
            if (!visible) return;
            var e=new OsqLayout.Entry();e.sprite=sprite(isFocused()?"field_focus":"field_normal");e.x=getX()-4;e.y=getY()-3;
            e.w=box.w;e.h=box.h;e.pad=box.pad;OsqLayout.drawSprite(g,e,0,0);
            var font=Minecraft.getInstance().font;String value=getValue();int cursor=getCursorPosition();
            display=Math.clamp(display,0,Math.min(cursor,value.length()));
            while(display<cursor&&OsqFonts.widthF(font,value.substring(display,cursor),face)>getWidth()-2)
                display=value.offsetByCodePoints(display,1);
            int end=display;
            while(end<value.length()) {int next=value.offsetByCodePoints(end,1);
                if(OsqFonts.widthF(font,value.substring(display,next),face)>getWidth())break;end=next;}
            Component displayedText=value.isEmpty()?hint:Component.literal(value.substring(display,end));
            float baseline=getY()-3+box.h/2+OsqFonts.centeredBaselineOffset(displayedText,face,6);
            if(value.isEmpty()&&!isFocused())OsqFonts.draw(g,font,hint,face,6,getX(),baseline,palette.ink3,0);
            else OsqFonts.draw(g,font,Component.literal(value.substring(display,end)),face,6,getX(),baseline,active?color:palette.disT,0);
            float cx=getX()+OsqFonts.widthF(font,value.substring(display,Math.min(cursor,end)),face);
            if(isFocused()&&net.minecraft.Util.getMillis()/300%2==0)g.fill(Math.round(cx),getY(),Math.round(cx)+1,getY()+7,active?color:palette.disT);
            int selected=Math.clamp(highlight,display,end);
            if(selected!=cursor&&!getHighlighted().isEmpty()) {
                int hx=Math.round(getX()+OsqFonts.widthF(font,value.substring(display,selected),face));
                g.fill(net.minecraft.client.renderer.RenderType.guiTextHighlight(),Math.min(hx,Math.round(cx)),getY(),Math.max(hx,Math.round(cx)),getY()+7,0xFF6688FF);
            }
        }
    }
}
