package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Clips actual POSITION_COLOR polygons to the printing plane, including partially intersected faces. */
public final class MatterLayerClipper implements VertexConsumer {
    private final VertexConsumer output;
    private final Vec3 origin, normal;
    private final List<Point> quad = new ArrayList<>(4);
    private Vec3 pending;
    private int red=255, green=255, blue=255, alpha=255;
    private record Point(Vec3 position, int r, int g, int b, int a) {
        Point lerp(Point other, double fraction) {
            return new Point(position.lerp(other.position, fraction), mix(r,other.r,fraction),
                    mix(g,other.g,fraction),mix(b,other.b,fraction),mix(a,other.a,fraction));
        }
        private static int mix(int a,int b,double f){return (int)Math.round(a+(b-a)*f);}
    }

    public MatterLayerClipper(VertexConsumer output, PoseStack.Pose pose, float height) {
        this.output = output;
        var point = pose.pose().transformPosition(new Vector3f(0, height, 0));
        var direction = pose.normal().transform(new Vector3f(0, 1, 0)).normalize();
        origin = new Vec3(point.x,point.y,point.z); normal = new Vec3(direction.x,direction.y,direction.z);
    }

    @Override public VertexConsumer vertex(double x,double y,double z){pending=new Vec3(x,y,z);return this;}
    @Override public VertexConsumer color(int r,int g,int b,int a){
        red=r;green=g;blue=b;alpha=a;
        return this;
    }
    @Override public void endVertex() {
        quad.add(new Point(pending,red,green,blue,alpha));
        if(quad.size()==4){clip();quad.clear();}
    }
    @Override public void defaultColor(int r,int g,int b,int a){color(r,g,b,a);}
    @Override public void unsetDefaultColor(){color(255,255,255,255);}
    private double distance(Point p){return p.position.subtract(origin).dot(normal);}
    private void clip(){
        var clipped=new ArrayList<Point>(5);
        for(int i=0;i<4;i++){
            var first=quad.get(i);var next=quad.get((i+1)%4);
            double a=distance(first),b=distance(next);
            boolean insideA=a<=1e-6,insideB=b<=1e-6;
            if(insideA)clipped.add(first);
            if(insideA!=insideB)clipped.add(first.lerp(next,com.atir.molecularmanipulator.util.MathCompat.clamp(a/(a-b),0,1)));
        }
        if(clipped.size()<3)return;
        if(clipped.size()==4){for(var p:clipped)emit(p);return;}
        for(int i=1;i+1<clipped.size();i++){emit(clipped.get(0));emit(clipped.get(i));emit(clipped.get(i+1));emit(clipped.get(i+1));}
    }
    private void emit(Point p){output.vertex((float)p.position.x,(float)p.position.y,(float)p.position.z).color(p.r,p.g,p.b,p.a).endVertex();}
    @Override public VertexConsumer uv(float u,float v){throw new UnsupportedOperationException("POSITION_COLOR only");}
    @Override public VertexConsumer overlayCoords(int u,int v){throw new UnsupportedOperationException("POSITION_COLOR only");}
    @Override public VertexConsumer uv2(int u,int v){throw new UnsupportedOperationException("POSITION_COLOR only");}
    @Override public VertexConsumer normal(float x,float y,float z){throw new UnsupportedOperationException("POSITION_COLOR only");}
}
