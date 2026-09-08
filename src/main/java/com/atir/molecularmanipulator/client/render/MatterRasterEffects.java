package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.blockentity.MatterPearlGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

/** A progress-driven light raster grows the workpiece while material motes converge on its current layer. */
public final class MatterRasterEffects {
    public static final int DISPLAY_TICKS = 120;
    private static final int MILK = 0xF1FFF3, GOLD = 0xFFE4A8, ICE = 0xBFEEDD;
    private MatterRasterEffects() {}

    public record Frame(int item, float opacity, float progress, boolean running, boolean constructing,
            float completion, float rotation) {
        public float builtFraction(){return completion>0?1:constructing?progress:1;}
        public float scanHeight(){var bounds=MatterProjectionShapes.bounds(item);return bounds.bottom()-0.03F+(bounds.top()-bounds.bottom()+0.06F)*builtFraction();}
    }

    /** Keep one prototype throughout a task or a paused task; idle prototypes rotate and change every six seconds. */
    public static final class Animation {
        private boolean held;
        private int item;
        private float rotation;
        private double previousTime = Double.NaN;
        public Frame sample(float time,boolean formed,boolean running,float progress,float completion){
            if(!formed){held=false;previousTime=Double.NaN;return new Frame(0,0,0,false,false,0,0);}
            double elapsed=Double.isNaN(previousTime)?0:Math.max(0,time-previousTime);
            if(Double.isNaN(previousTime))rotation=(time*0.28F)%360;
            previousTime=time;
            progress=com.atir.molecularmanipulator.util.MathCompat.clamp(progress,0,1);completion=com.atir.molecularmanipulator.util.MathCompat.clamp(completion,0,1);
            boolean constructing=running||progress>0&&progress<1;
            boolean nextHeld=constructing||completion>0;
            if(!held)item=idleItem(time);
            if(!nextHeld){item=idleItem(time);rotation=(rotation+(float)elapsed*0.28F)%360;}
            held=nextHeld;
            return new Frame(item,held?1:idleOpacity(time),progress,running,constructing,completion,rotation);
        }
    }

    public static int idleItem(float time){return Math.floorMod((long)Math.floor((time+40.0)/DISPLAY_TICKS),MatterProjectionShapes.COUNT);}
    public static float idleOpacity(float time){
        double tick=fraction((time+40.0)/DISPLAY_TICKS)*DISPLAY_TICKS;
        double x=com.atir.molecularmanipulator.util.MathCompat.clamp(Math.min(tick,DISPLAY_TICKS-tick)/9,0,1);return (float)(x*x*(3-2*x));
    }
    private static double bob(float time,Frame frame){return frame.constructing()||frame.completion()>0?0:Math.sin(time*0.021)*0.16;}

    public static void renderObject(PoseStack stack,VertexConsumer output,float time,Frame frame,boolean glow){
        if(frame.opacity()<0.001)return;
        stack.pushPose();stack.translate(0,bob(time,frame),0);stack.mulPose(Axis.YP.rotationDegrees(frame.rotation()));
        int lineAlpha=Math.round(frame.opacity()*(glow?30:235));
        float width=glow?0.065F:0.024F;
        if(frame.constructing()&&frame.completion()==0){
            MatterProjectionShapes.render(stack.last(),output,frame.item(),glow?3:14,glow?0.025F:0.009F);
            if(frame.progress()>0){
                var clipped=new MatterLayerClipper(output,stack.last(),frame.scanHeight());
                if(!glow)MatterProjectionShapes.render(stack.last(),clipped,frame.item(),185,0);
                MatterProjectionShapes.render(stack.last(),clipped,frame.item(),lineAlpha,width);
            }
        }else{
            if(!glow)MatterProjectionShapes.render(stack.last(),output,frame.item(),Math.round(frame.opacity()*165),0);
            MatterProjectionShapes.render(stack.last(),output,frame.item(),lineAlpha,width);
        }
        stack.popPose();
    }

    public static void renderField(PoseStack stack,VertexConsumer output,float time,Frame frame,boolean detailed,boolean glow){
        var pose=stack.last();float stroke=glow?0.060F:0.022F;
        ambientField(stack,output,time,detailed,glow);
        if(frame.running()&&frame.completion()==0){
            float scan=frame.scanHeight();
            for(int i=-6;i<=6;i++){
                double offset=i*0.70;
                line(pose,output,new Vec3(-4.2,scan,offset),new Vec3(4.2,scan,offset),stroke*0.38F,MILK,glow?9:70);
                line(pose,output,new Vec3(offset,scan,-4.2),new Vec3(offset,scan,4.2),stroke*0.38F,MILK,glow?9:70);
            }
            for(int sx:new int[]{-1,1})for(int sz:new int[]{-1,1}){
                var corner=new Vec3(sx*4.32,scan,sz*4.32);
                line(pose,output,corner,corner.add(-sx*0.56,0,0),stroke,MILK,glow?20:220);
                line(pose,output,corner,corner.add(0,0,-sz*0.56),stroke,MILK,glow?20:220);
            }
            double sweep=-4.2+fraction(time*0.023)*8.4;
            line(pose,output,new Vec3(-4.2,scan+0.018,sweep),new Vec3(4.2,scan+0.018,sweep),stroke*0.65F,GOLD,glow?22:205);
            for(int stream=0;stream<4;stream++) {
                int count=detailed?18:8;
                for(int particle=0;particle<count;particle++){
                    double travel=fraction(time*0.011+particle/(double)count+stream*0.17);
                    var point=mote(frame,stream,travel);
                    double size=0.045+0.035*(0.5+0.5*Math.sin(particle*2.7));
                    OmniRenderGeometry.orientedBox(pose,output,point,new Vec3(size,0,0),new Vec3(0,size,0),new Vec3(0,0,size),OmniRenderGeometry.argb(GOLD,glow?24:210));
                    if(detailed)line(pose,output,mote(frame,stream,Math.max(0,travel-0.023)),point,stroke*0.45F,MILK,glow?8:75);
                }
            }
        }
        if(frame.completion()>0){
            float age=1-frame.completion();
            OmniRenderGeometry.ring(stack,output,0,-1.8F,0,1.65F+age*2.9F,glow?0.07F:0.026F,detailed?80:40,0,0,0,
                    OmniRenderGeometry.argb(GOLD,Math.round(frame.completion()*(glow?26:155))));
        }
    }

    /** Always-present holographic display infrastructure; low quality keeps its major layers visible. */
    private static void ambientField(PoseStack stack,VertexConsumer out,float time,boolean detailed,boolean glow){
        var pose=stack.last();float width=glow?0.065F:0.025F;int segments=detailed?72:40;
        OmniRenderGeometry.ring(stack,out,0,-4.1F,0,3.75F,glow?0.23F:0.11F,segments,0,0,time*0.18F,OmniRenderGeometry.argb(MILK,glow?20:170));
        OmniRenderGeometry.ring(stack,out,0,4.4F,0,3.15F,glow?0.18F:0.085F,segments,0,0,-time*0.15F,OmniRenderGeometry.argb(GOLD,glow?19:170));
        for(int tier=0;tier<2;tier++){
            double radius=tier==0?4.45:4.05,y=tier==0?-4.1:4.4;
            for(int i=0;i<segments;i++){
                if(i%(segments/4)>=segments/4-3)continue;
                double a=time*(tier==0?0.003:-0.004)+i*Math.PI*2/segments;
                line(pose,out,polar(radius,a,y),polar(radius,a+Math.PI*2/segments,y),width*0.72F,tier==0?GOLD:MILK,glow?20:175);
                if(i%(segments/8)==0){var point=polar(radius,a,y);line(pose,out,point,polar(radius+0.18,a,y),width,GOLD,glow?22:200);}
            }
        }
        for(int orbit=0;orbit<3;orbit++){
            double start=time*0.005+orbit*Math.PI*2/3,y=Math.sin(time*0.009+orbit*2.1)*1.25;
            int count=detailed?14:8;
            for(int i=0;i<count;i++){
                double a=start+i*0.94/count;
                line(pose,out,polar(4.52,a,y),polar(4.52,a+0.94/count,y),width*0.75F,ICE,glow?15:145);
            }
        }
        for(int tile=0;tile<6;tile++){
            double angle=time*0.0045+tile*Math.PI/3;
            var radial=new Vec3(Math.cos(angle),0,Math.sin(angle));var tangent=new Vec3(-radial.z,0,radial.x);
            var point=radial.scale(4.37).add(0,Math.sin(angle*2+0.8)*2.0,0);
            if(!glow)OmniRenderGeometry.orientedBox(pose,out,point,tangent.scale(0.26),new Vec3(0,0.36,0),radial.scale(0.025),OmniRenderGeometry.argb(ICE,75));
            for(int row=-1;row<=1;row++){
                var middle=point.add(radial.scale(0.034)).add(0,row*0.18,0);
                line(pose,out,middle.subtract(tangent.scale(0.20)),middle.add(tangent.scale(0.20)),width*0.6F,GOLD,glow?23:205);
            }
        }
    }

    public static Vec3 mote(Frame frame,int stream,double travel){
        var emitter=MatterPearlGeometry.fieldEmitters().get(stream);
        var start=new Vec3(emitter.getX(),emitter.getY()-MatterPearlGeometry.CENTER_Y+0.56,emitter.getZ());
        int sx=emitter.getX()<0?-1:1,sz=emitter.getZ()<0?-1:1;
        var bounds=MatterProjectionShapes.bounds(frame.item());
        double radiusX=bounds.radiusX()*0.68,radiusZ=bounds.radiusZ()*0.68;
        var end=new Vec3(sx*radiusX,frame.scanHeight(),sz*radiusZ).yRot((float)Math.toRadians(frame.rotation()));
        var control=new Vec3(sx*5.1,-1.8,sz*4.3);
        double t=com.atir.molecularmanipulator.util.MathCompat.clamp(travel,0,1);return start.scale((1-t)*(1-t)).add(control.scale(2*t*(1-t))).add(end.scale(t*t));
    }
    private static Vec3 polar(double radius,double angle,double y){return new Vec3(Math.cos(angle)*radius,y,Math.sin(angle)*radius);}
    private static void line(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,float width,int rgb,int alpha){OmniRenderGeometry.beam(pose,out,a,b,width,width,OmniRenderGeometry.argb(rgb,alpha));}
    private static double fraction(double value){return value-Math.floor(value);}
}
