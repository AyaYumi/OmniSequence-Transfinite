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

    @Override public VertexConsumer addVertex(float x,float y,float z){pending=new Vec3(x,y,z);return this;}
    @Override public VertexConsumer setColor(int r,int g,int b,int a){
        quad.add(new Point(pending,r,g,b,a));
        if(quad.size()==4){clip();quad.clear();}
        return this;
    }
    private double distance(Point p){return p.position.subtract(origin).dot(normal);}
    private void clip(){
        var clipped=new ArrayList<Point>(5);
        for(int i=0;i<4;i++){
            var first=quad.get(i);var next=quad.get((i+1)%4);
            double a=distance(first),b=distance(next);
            boolean insideA=a<=1e-6,insideB=b<=1e-6;
            if(insideA)clipped.add(first);
            if(insideA!=insideB)clipped.add(first.lerp(next,Math.clamp(a/(a-b),0,1)));
        }
        if(clipped.size()<3)return;
        if(clipped.size()==4){for(var p:clipped)emit(p);return;}
        for(int i=1;i+1<clipped.size();i++){emit(clipped.getFirst());emit(clipped.get(i));emit(clipped.get(i+1));emit(clipped.get(i+1));}
    }
    private void emit(Point p){output.addVertex((float)p.position.x,(float)p.position.y,(float)p.position.z).setColor(p.r,p.g,p.b,p.a);}
    @Override public VertexConsumer setUv(float u,float v){throw new UnsupportedOperationException("POSITION_COLOR only");}
    @Override public VertexConsumer setUv1(int u,int v){throw new UnsupportedOperationException("POSITION_COLOR only");}
    @Override public VertexConsumer setUv2(int u,int v){throw new UnsupportedOperationException("POSITION_COLOR only");}
    @Override public VertexConsumer setNormal(float x,float y,float z){throw new UnsupportedOperationException("POSITION_COLOR only");}
}
