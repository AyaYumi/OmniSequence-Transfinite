package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Five volumetric science-fiction concept assemblies, also used by the real layered printing effect. */
public final class MatterProjectionShapes {
    public static final int COUNT=5;
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final int PEARL=0xECF5ED, GOLD=0xE7D2A4, LIGHT=0xFFF2C5, ICE=0xA9EBD8;
    private MatterProjectionShapes() {}
    public record Bounds(float bottom,float top,float radiusX,float radiusZ) {}
    public static Bounds bounds(int item){
        return switch(item){
            case 0 -> new Bounds(-2.88F,2.88F,2.88F,2.88F);
            case 1 -> new Bounds(-2.75F,2.75F,2.75F,2.75F);
            case 2 -> new Bounds(-3.06F,3.06F,2.85F,2.85F);
            case 3 -> new Bounds(-3.08F,3.08F,2.85F,2.85F);
            case 4 -> new Bounds(-2.70F,2.70F,2.85F,2.85F);
            default -> throw new IllegalArgumentException("Unknown concept projection: "+item);
        };
    }
    public static String conceptName(int item){return switch(item){
        case 0 -> "quantum_core";case 1 -> "lattice_memory";case 2 -> "zero_point_capacitor";
        case 3 -> "dimensional_prism";case 4 -> "matter_compiler";default -> throw new IllegalArgumentException();};}
    public static void render(PoseStack.Pose pose,VertexConsumer out,int index,int alpha,float width){
        switch(index){
            case 0 -> quantumCore(pose,out,alpha,width);
            case 1 -> latticeMemory(pose,out,alpha,width);
            case 2 -> capacitor(pose,out,alpha,width);
            case 3 -> dimensionalPrism(pose,out,alpha,width);
            case 4 -> compiler(pose,out,alpha,width);
            default -> throw new IllegalArgumentException("Unknown concept projection: "+index);
        }
    }
    private static void quantumCore(PoseStack.Pose pose,VertexConsumer out,int alpha,float width){
        octahedron(pose,out,Vec3.ZERO,0.92,1.15,0.92,ICE,alpha,width);
        cage(pose,out,2.75,0.065,PEARL,alpha,width,0,0);
        for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){
            Vec3 center=axis==0?new Vec3(sign*1.76,0,0):axis==1?new Vec3(0,sign*1.76,0):new Vec3(0,0,sign*1.76);
            holoBox(pose,out,center,axis==0?0.14:0.85,axis==1?0.14:0.85,axis==2?0.14:0.85,PEARL,alpha,width);
            var badge=center.scale(1.09);
            holoBox(pose,out,badge,axis==0?0.045:0.33,axis==1?0.045:0.33,axis==2?0.045:0.33,GOLD,alpha,width);
            rod(pose,out,center.scale(0.52),center,0.065,GOLD,alpha,width);
        }
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}){
            var point=new Vec3(x*1.56,y*1.56,z*1.56);
            octahedron(pose,out,point,0.24,0.32,0.24,GOLD,alpha,width);
            rod(pose,out,point,point.scale(1.70),0.035,ICE,alpha,width);
        }
    }
    private static void latticeMemory(PoseStack.Pose pose,VertexConsumer out,int alpha,float width){
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            var point=new Vec3(x*1.55,y*1.55,z*1.55);
            octahedron(pose,out,point,0.31,0.44,0.31,(x+y+z)%2==0?ICE:GOLD,alpha,width);
            if(x<1)rod(pose,out,point,point.add(1.55,0,0),0.032,PEARL,alpha,width);
            if(y<1)rod(pose,out,point,point.add(0,1.55,0),0.032,PEARL,alpha,width);
            if(z<1)rod(pose,out,point,point.add(0,0,1.55),0.032,PEARL,alpha,width);
        }
        cage(pose,out,2.65,0.055,PEARL,alpha,width,0,0);
        for(int y:new int[]{-1,1})for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})
            holoBox(pose,out,new Vec3(x*2.60,y*2.60,z*2.60),0.12,0.12,0.12,GOLD,alpha,width);
    }
    private static void capacitor(PoseStack.Pose pose,VertexConsumer out,int alpha,float width){
        octahedron(pose,out,Vec3.ZERO,0.55,2.62,0.55,ICE,alpha,width);
        for(int layer=-1;layer<=1;layer++)annularPlate(pose,out,layer*1.8,0.94,2.45,0.14,16,layer==0?GOLD:PEARL,alpha,width);
        for(int i=0;i<6;i++){
            double angle=i*Math.PI/3;var radial=new Vec3(Math.cos(angle),0,Math.sin(angle));var tangent=new Vec3(-radial.z,0,radial.x);
            holoBox(pose,out,radial.scale(2.44),radial.scale(0.18),new Vec3(0,0.95,0),tangent.scale(0.40),PEARL,alpha,width);
            rod(pose,out,radial.scale(1.06).add(0,-2.1,0),radial.scale(1.06).add(0,2.1,0),0.055,GOLD,alpha,width);
        }
        for(int sign:new int[]{-1,1}){
            holoBox(pose,out,new Vec3(0,sign*2.86,0),0.66,0.16,0.66,PEARL,alpha,width);
            holoBox(pose,out,new Vec3(0,sign*3.025,0),0.28,0.025,0.28,GOLD,alpha,width);
        }
    }
    private static void dimensionalPrism(PoseStack.Pose pose,VertexConsumer out,int alpha,float width){
        var top=new Vec3(0,3,0);var bottom=new Vec3(0,-3,0);
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
            var first=new Vec3(Math.cos(a)*2.7,0,Math.sin(a)*2.7);var next=new Vec3(Math.cos(b)*2.7,0,Math.sin(b)*2.7);
            rod(pose,out,top,first,0.065,PEARL,alpha,width);rod(pose,out,bottom,first,0.065,PEARL,alpha,width);
            rod(pose,out,first,next,0.055,GOLD,alpha,width);
            if(i%2==0){
                var centre=top.add(first).add(next).scale(1.0/3);
                var x=centre.lerp(top,0.64);var y=centre.lerp(first,0.64);var z=centre.lerp(next,0.64);
                holoFace(pose,out,x,y,z,z,Vec3.ZERO,ICE,alpha,width);
                centre=bottom.add(first).add(next).scale(1.0/3);
                x=centre.lerp(bottom,0.64);y=centre.lerp(first,0.64);z=centre.lerp(next,0.64);
                holoFace(pose,out,x,y,z,z,Vec3.ZERO,PEARL,alpha,width);
            }
        }
        cage(pose,out,0.97,0.055,GOLD,alpha,width,(float)Math.toRadians(45),(float)Math.toRadians(28));
        octahedron(pose,out,Vec3.ZERO,0.36,0.57,0.36,GOLD,alpha,width);
    }
    private static void compiler(PoseStack.Pose pose,VertexConsumer out,int alpha,float width){
        for(int layer=-1;layer<=1;layer++){
            double spin=(layer+1)*Math.PI/6,y=layer*1.82;
            for(int i=0;i<6;i++){
                double a=spin+i*Math.PI/3,b=spin+(i+1)*Math.PI/3;
                var p=new Vec3(Math.cos(a)*2.62,y,Math.sin(a)*2.62);var q=new Vec3(Math.cos(b)*2.62,y,Math.sin(b)*2.62);
                rod(pose,out,p,q,0.105,PEARL,alpha,width);
                if(layer<1){double nextSpin=spin+Math.PI/6;var next=new Vec3(Math.cos(nextSpin+i*Math.PI/3)*2.62,y+1.82,Math.sin(nextSpin+i*Math.PI/3)*2.62);rod(pose,out,p,next,0.042,GOLD,alpha,width);}
            }
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
                var point=new Vec3(x*1.05,y,z*1.05).yRot((float)spin);
                holoBox(pose,out,point,0.25,0.15,0.25,(x+z)%2==0?GOLD:ICE,alpha,width);
            }
        }
        octahedron(pose,out,Vec3.ZERO,0.36,2.5,0.36,ICE,alpha,width);
        for(int sign:new int[]{-1,1})holoBox(pose,out,new Vec3(0,sign*2.55,0),0.43,0.11,0.43,GOLD,alpha,width);
    }
    private static void octahedron(PoseStack.Pose pose,VertexConsumer out,Vec3 center,double rx,double ry,double rz,int rgb,int alpha,float width){
        var top=center.add(0,ry,0);var bottom=center.add(0,-ry,0);
        Vec3[] middle={center.add(rx,0,0),center.add(0,0,rz),center.add(-rx,0,0),center.add(0,0,-rz)};
        for(int i=0;i<4;i++){var a=middle[i];var b=middle[(i+1)%4];holoFace(pose,out,top,a,b,b,center,rgb,alpha,width);holoFace(pose,out,bottom,b,a,a,center,rgb,alpha,width);}
    }
    private static void cage(PoseStack.Pose pose,VertexConsumer out,double half,double thickness,int rgb,int alpha,float width,float yaw,float roll){
        Vec3[] points=new Vec3[8];
        for(int i=0;i<8;i++)points[i]=new Vec3((i&1)==0?-half:half,(i&2)==0?-half:half,(i&4)==0?-half:half).zRot(roll).yRot(yaw);
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)rod(pose,out,points[i],points[i|bit],thickness,rgb,alpha,width);
    }
    private static void rod(PoseStack.Pose pose,VertexConsumer out,Vec3 from,Vec3 to,double thickness,int rgb,int alpha,float width){
        var delta=to.subtract(from);var forward=delta.normalize();var side=UP.cross(forward);if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);else side=side.normalize();
        holoBox(pose,out,from.lerp(to,0.5),side.scale(thickness),forward.cross(side).scale(thickness),delta.scale(0.5),rgb,alpha,width);
    }
    private static void annularPlate(PoseStack.Pose pose,VertexConsumer out,double y,double inner,double outer,double halfHeight,int segments,int rgb,int alpha,float width){
        for(int i=0;i<segments;i++){
            double a=i*Math.PI*2/segments+0.025,b=(i+1)*Math.PI*2/segments-0.025;
            Vec3[] top={new Vec3(Math.cos(a)*inner,y+halfHeight,Math.sin(a)*inner),new Vec3(Math.cos(a)*outer,y+halfHeight,Math.sin(a)*outer),new Vec3(Math.cos(b)*outer,y+halfHeight,Math.sin(b)*outer),new Vec3(Math.cos(b)*inner,y+halfHeight,Math.sin(b)*inner)};
            Vec3[] bottom=new Vec3[4];for(int j=0;j<4;j++)bottom[j]=top[j].add(0,-halfHeight*2,0);
            var centre=top[0].add(top[1]).add(top[2]).add(top[3]).scale(0.25).add(0,-halfHeight,0);
            holoFace(pose,out,top[0],top[1],top[2],top[3],centre,rgb,alpha,width);holoFace(pose,out,bottom[0],bottom[1],bottom[2],bottom[3],centre,rgb,alpha,width);
            for(int j=0;j<4;j++)holoFace(pose,out,top[j],bottom[j],bottom[(j+1)%4],top[(j+1)%4],centre,rgb,alpha,width);
        }
    }
    private static void wireBox(PoseStack.Pose pose,VertexConsumer out,Vec3 center,Vec3 x,Vec3 y,Vec3 z,float stroke,int rgb,int alpha){
        Vec3[] points=new Vec3[8];for(int i=0;i<8;i++)points[i]=center.add(x.scale((i&1)==0?-1:1)).add(y.scale((i&2)==0?-1:1)).add(z.scale((i&4)==0?-1:1));
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)segment(pose,out,points[i],points[i|bit],stroke,stroke,rgb,alpha);
    }

    private static void holoBox(PoseStack.Pose pose,VertexConsumer out,Vec3 center,double x,double y,double z,int rgb,int alpha,float width) {
        holoBox(pose,out,center,new Vec3(x,0,0),new Vec3(0,y,0),new Vec3(0,0,z),rgb,alpha,width);
    }

    private static void holoBox(PoseStack.Pose pose,VertexConsumer out,Vec3 center,Vec3 x,Vec3 y,Vec3 z,int rgb,int alpha,float width) {
        if(width==0)orientedBox(pose,out,center,x,y,z,rgb,alpha);
        else wireBox(pose,out,center,x,y,z,width,LIGHT,alpha);
    }

    private static void holoFace(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 center,int rgb,int alpha,float width) {
        if(width==0)face(pose,out,a,b,c,d,center,rgb,alpha);
        else {Vec3[] points={a,b,c,d};for(int i=0;i<4;i++)segment(pose,out,points[i],points[(i+1)%4],width,width,LIGHT,alpha);}
    }
    private static void segment(PoseStack.Pose pose, VertexConsumer out, Vec3 from, Vec3 to,
            double width, double height, int rgb, int alpha) {
        var delta=to.subtract(from);double length=delta.length();if(length<1e-6)return;
        var forward=delta.scale(1/length);var sideways=UP.cross(forward);
        if(sideways.lengthSqr()<1e-6)sideways=new Vec3(1,0,0);else sideways=sideways.normalize();
        var vertical=forward.cross(sideways).normalize();
        orientedBox(pose,out,from.lerp(to,0.5),sideways.scale(width),vertical.scale(height),forward.scale(length/2),rgb,alpha);
    }

    private static void orientedBox(PoseStack.Pose pose,VertexConsumer out,Vec3 center,Vec3 x,Vec3 y,Vec3 z,int rgb,int alpha){
        Vec3[] points=new Vec3[8];
        for(int i=0;i<8;i++)points[i]=center.add(x.scale((i&1)==0?-1:1)).add(y.scale((i&2)==0?-1:1)).add(z.scale((i&4)==0?-1:1));
        for(int[] f:new int[][]{{0,1,3,2},{4,6,7,5},{0,2,6,4},{1,5,7,3},{2,3,7,6},{0,4,5,1}})
            face(pose,out,points[f[0]],points[f[1]],points[f[2]],points[f[3]],center,rgb,alpha);
    }

    private static void face(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,Vec3 center,int rgb,int alpha){
        var normal=b.subtract(a).cross(c.subtract(a));
        if(normal.dot(a.add(b).add(c).add(d).scale(0.25).subtract(center))<0){var swap=b;b=d;d=swap;normal=normal.scale(-1);}
        normal=normal.normalize();
        double shade=0.73+0.22*Math.max(0,normal.y)+0.05*Math.max(0,normal.x*0.6-normal.z*0.8);
        int color=OmniRenderGeometry.argb((int)((rgb>>16&255)*shade)<<16|(int)((rgb>>8&255)*shade)<<8|(int)((rgb&255)*shade),alpha);
        for(var point:new Vec3[]{a,b,c,d})out.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setColor(color);
    }

}
