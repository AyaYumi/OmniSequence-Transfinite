package com.atir.molecularmanipulator.client.gui.theme;

import appeng.client.gui.Icon;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Exact native slot outlines, sampled from installed AE2; no item artwork or AE2 tile background. */
public final class OsqSlotGhosts {
    private record Run(int x,int y,int width,int color) {}
    private static final Map<Integer,List<Run>> CACHE=new HashMap<>();
    private OsqSlotGhosts() {}
    public static boolean supports(Icon icon) {
        return icon==Icon.BACKGROUND_BLANK_PATTERN || icon==Icon.BACKGROUND_ENCODED_PATTERN
                || icon==Icon.BACKGROUND_SINGULARITY;
    }
    public static void clear() { CACHE.clear(); }
    public static void draw(GuiGraphics graphics,Icon icon,int x,int y) {
        int sourceY=icon==Icon.BACKGROUND_SINGULARITY?160:icon==Icon.BACKGROUND_ENCODED_PATTERN?112:128;
        var runs = CACHE.computeIfAbsent(sourceY,OsqSlotGhosts::load);
        if (runs.isEmpty()) return;
        // GuiGraphics.fill flushes each scanline separately outside drawManaged.
        // Pattern pages contain dozens of empty slots: submit one buffer per icon instead.
        var buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        var matrix = graphics.pose().last().pose();
        for (var run : runs) {
            float left=x+run.x, top=y+run.y, right=left+run.width, bottom=top+1;
            buffer.addVertex(matrix,left,top,0).setColor(run.color);
            buffer.addVertex(matrix,left,bottom,0).setColor(run.color);
            buffer.addVertex(matrix,right,bottom,0).setColor(run.color);
            buffer.addVertex(matrix,right,top,0).setColor(run.color);
        }
        graphics.flush();
    }
    private static List<Run> load(int sourceY) {
        var resource=ResourceLocation.fromNamespaceAndPath("ae2","textures/guis/states.png");
        try(var stream=Minecraft.getInstance().getResourceManager().open(resource);
                var image=NativeImage.read(stream)) {
            int[] pixels=new int[256];
            for(int y=0;y<16;y++)for(int x=0;x<16;x++) {
                int abgr=image.getPixelRGBA(240+x,sourceY+y);
                pixels[y*16+x]=(abgr&0xFF00FF00)|((abgr&0xFF)<<16)|((abgr>>>16)&0xFF);
            }
            int[] outline=stripTile(pixels);
            var runs=new ArrayList<Run>();
            for(int y=0;y<16;y++)for(int x=0;x<16;) {
                int color=outline[y*16+x],end=x+1;
                while(end<16&&outline[y*16+end]==color)end++;
                if((color>>>24)!=0)runs.add(new Run(x,y,end-x,color));
                x=end;
            }
            return List.copyOf(runs);
        } catch(Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot read native AE2 slot outline",ex);
            return List.of();
        }
    }
    /** Remove only the tile's uniform fill and top-edge shading, retaining exact outline pixels. */
    static int[] stripTile(int[] pixels) {
        if(pixels.length!=256)throw new IllegalArgumentException("Expected a 16 x 16 AE2 icon");
        Map<Integer,Integer> frequency=new HashMap<>();
        for(int y=1;y<16;y++)for(int x=0;x<16;x++)frequency.merge(pixels[y*16+x],1,Integer::sum);
        int background=frequency.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        int topEdge=pixels[0];int[] result=pixels.clone();
        for(int i=0;i<result.length;i++)if(i<16||result[i]==background||result[i]==topEdge)result[i]=0;
        return result;
    }
}
