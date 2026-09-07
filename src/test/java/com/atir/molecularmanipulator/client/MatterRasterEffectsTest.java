package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;
import com.atir.molecularmanipulator.blockentity.MatterPearlGeometry;
import com.atir.molecularmanipulator.client.render.MatterLayerClipper;
import com.atir.molecularmanipulator.client.render.MatterProjectionShapes;
import com.atir.molecularmanipulator.client.render.MatterRasterEffects;
import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class MatterRasterEffectsTest {
    @Test void allConceptsHaveSubstantialThreeDimensionalSilhouettes(){
        var names=new HashSet<String>();
        for(int item=0;item<5;item++){
            names.add(MatterProjectionShapes.conceptName(item));
            var result=new RecordingColorConsumer();MatterProjectionShapes.render(new PoseStack().last(),result,item,165,0);
            double width=result.vertices().stream().mapToDouble(v->v.x()).max().orElseThrow()-result.vertices().stream().mapToDouble(v->v.x()).min().orElseThrow();
            double height=result.vertices().stream().mapToDouble(v->v.y()).max().orElseThrow()-result.vertices().stream().mapToDouble(v->v.y()).min().orElseThrow();
            double depth=result.vertices().stream().mapToDouble(v->v.z()).max().orElseThrow()-result.vertices().stream().mapToDouble(v->v.z()).min().orElseThrow();
            assertTrue(width>4.5&&height>4.5&&depth>4.5,"A concept must not collapse to a small flat inventory icon");
        }
        assertEquals(5,names.size());
    }

    @Test void reducedDetailStillRetainsALargeBrightIdleDisplayField(){
        var frame=new MatterRasterEffects.Frame(0,1,0,false,false,0,0);
        var output=new RecordingColorConsumer();MatterRasterEffects.renderField(new PoseStack(),output,1200,frame,false,false);
        assertTrue(output.vertices().stream().filter(v->v.alpha()>=100).count()>2500);
        assertTrue(output.vertices().stream().mapToDouble(v->Math.abs(v.x())).max().orElseThrow()>4.3);
        assertTrue(output.vertices().stream().mapToDouble(v->v.y()).min().orElseThrow()<-4);
        assertTrue(output.vertices().stream().mapToDouble(v->v.y()).max().orElseThrow()>4.4);
        assertTrue(output.vertices().stream().allMatch(v->v.y()>-4.3&&v.y()<4.6),"Ambient streams must not extend toward the emitters or scanner");
        assertTrue(output.vertices().stream().allMatch(v->Math.abs(v.x())<5&&Math.abs(v.z())<5));
    }

    @Test void idleCyclesFiveModelsButAWorkpieceRemainsFixedDuringConstructionAndPause(){
        var types=new HashSet<Integer>();
        for(int i=0;i<5;i++){assertEquals(i,MatterRasterEffects.idleItem(i*120));types.add(MatterRasterEffects.idleItem(i*120));}
        assertEquals(5,types.size());assertEquals(0,MatterRasterEffects.idleOpacity(80));
        var animation=new MatterRasterEffects.Animation();
        var first=animation.sample(0,true,true,0.1F,0);
        var later=animation.sample(350,true,true,0.6F,0);
        var paused=animation.sample(380,true,false,0.6F,0);
        assertEquals(first.item(),later.item());assertEquals(first.rotation(),later.rotation());
        assertEquals(later.item(),paused.item());assertTrue(paused.constructing());assertFalse(paused.running());
        assertEquals(0.6F,paused.builtFraction());
        assertEquals(1,animation.sample(381,true,false,0,1).builtFraction());
        assertEquals(MatterRasterEffects.idleItem(420),animation.sample(420,true,false,0,0).item());
    }

    @Test void allFiveActualMeshesGrowMonotonicallyBelowTheScanPlane(){
        for(int item=0;item<5;item++){
            var bounds=MatterProjectionShapes.bounds(item);double previous=0;
            var full=new RecordingColorConsumer();MatterProjectionShapes.render(new PoseStack().last(),full,item,255,0);
            for(float progress:new float[]{0,0.25F,0.5F,0.75F,1}){
                float height=bounds.bottom()-0.03F+(bounds.top()-bounds.bottom()+0.06F)*progress;
                var pose=new PoseStack();var output=new RecordingColorConsumer();
                MatterProjectionShapes.render(pose.last(),new MatterLayerClipper(output,pose.last(),height),item,255,0);
                assertTrue(output.vertices().stream().allMatch(v->v.y()<=height+1e-5));
                double area=area(output.vertices());assertTrue(area+1e-5>=previous);previous=area;
                if(progress==0)assertTrue(output.vertices().isEmpty());
                if(progress==1)assertEquals(area(full.vertices()),area,1e-5);
            }
        }
    }

    @Test void clipperHandlesIntersectingFacesAndTransformedCoordinateSpaces(){
        var pose=new PoseStack();pose.translate(3,4,5);pose.mulPose(Axis.ZP.rotationDegrees(37));pose.scale(0.7F,0.7F,0.7F);
        var result=new RecordingColorConsumer();var clip=new MatterLayerClipper(result,pose.last(),0);
        for(var p:List.of(new Vec3(-1,-1,0),new Vec3(1,-1,0),new Vec3(1,1,0),new Vec3(-1,1,0)))
            clip.addVertex(pose.last(),(float)p.x,(float)p.y,(float)p.z).setColor(255,220,180,200);
        assertEquals(0.98,area(result.vertices()),1e-5);
        var inverse=new org.joml.Matrix4f(pose.last().pose()).invert();
        for(var v:result.vertices())assertTrue(inverse.transformPosition(new org.joml.Vector3f(v.x(),v.y(),v.z())).y<=1e-5);
    }

    @Test void noOpaqueOrBrightUnbuiltSurfacesAppearAboveTheCurrentLayer(){
        var frame=new MatterRasterEffects.Frame(2,1,0.4F,true,true,0,35);
        var result=new RecordingColorConsumer();MatterRasterEffects.renderObject(new PoseStack(),result,100,frame,false);
        assertTrue(result.vertices().stream().anyMatch(v->v.alpha()>100));
        assertTrue(result.vertices().stream().filter(v->v.alpha()>40).allMatch(v->v.y()<=frame.scanHeight()+1e-5));
    }

    @Test void materialPathsStartOnRealEmittersAndEndAtTheRotatedConstructionLayer(){
        var frame=new MatterRasterEffects.Frame(0,1,0.5F,true,true,0,45);
        var bounds=MatterProjectionShapes.bounds(0);
        for(int stream=0;stream<4;stream++){
            var block=MatterPearlGeometry.fieldEmitters().get(stream);
            var origin=MatterRasterEffects.mote(frame,stream,0);
            assertEquals(new Vec3(block.getX(),block.getY()-MatterPearlGeometry.CENTER_Y+0.56,block.getZ()),origin);
            int sx=block.getX()<0?-1:1,sz=block.getZ()<0?-1:1;
            var expected=new org.joml.Vector3f((float)(sx*bounds.radiusX()*0.68),frame.scanHeight(),(float)(sz*bounds.radiusZ()*0.68));
            new org.joml.Matrix4f().rotateY((float)Math.toRadians(45)).transformPosition(expected);
            var end=MatterRasterEffects.mote(frame,stream,1);
            assertEquals(expected.x,end.x,1e-5);assertEquals(expected.y,end.y,1e-5);assertEquals(expected.z,end.z,1e-5);
        }
    }

    @Test void completionAndWorkBeamsAreIndependentAndUnformedMachinesEmitNothing(){
        var idle=new MatterRasterEffects.Frame(0,1,0,false,false,0,0);
        var working=new MatterRasterEffects.Frame(0,1,0.5F,true,true,0,0);
        var completed=new MatterRasterEffects.Frame(0,1,0,false,false,1,0);
        var a=new RecordingColorConsumer();var b=new RecordingColorConsumer();var c=new RecordingColorConsumer();
        MatterRasterEffects.renderField(new PoseStack(),a,100,idle,true,false);
        MatterRasterEffects.renderField(new PoseStack(),b,100,working,true,false);
        MatterRasterEffects.renderField(new PoseStack(),c,100,completed,true,false);
        assertTrue(b.vertices().size()>a.vertices().size());assertTrue(c.vertices().size()>a.vertices().size());
        for(var pass:MatterFabricationRenderer.FoundryPass.values()){
            var none=new RecordingColorConsumer();MatterFabricationRenderer.renderFabricationPass(new PoseStack(),none,100,true,false,Direction.NORTH,pass,working);
            assertTrue(none.vertices().isEmpty());
        }
    }

    @Test void bothRasterAndClippedObjectsFollowAllFourControllerFacings(){
        var frame=new MatterRasterEffects.Frame(0,1,0.5F,true,true,0,20);
        for(var pass:MatterFabricationRenderer.FoundryPass.values()){
            var north=new RecordingColorConsumer();MatterFabricationRenderer.renderFabricationPass(new PoseStack(),north,1200,true,true,Direction.NORTH,pass,frame);
            for(var facing:new Direction[]{Direction.EAST,Direction.SOUTH,Direction.WEST}){
                var rotated=new RecordingColorConsumer();MatterFabricationRenderer.renderFabricationPass(new PoseStack(),rotated,1200,true,true,facing,pass,frame);
                assertEquals(north.vertices().size(),rotated.vertices().size());
                double a=Math.toRadians(180-facing.toYRot()),c=Math.cos(a),s=Math.sin(a);
                for(int i=0;i<north.vertices().size();i++){
                    var n=north.vertices().get(i);var r=rotated.vertices().get(i);
                    assertEquals(n.x()*c+n.z()*s,r.x(),1e-4);assertEquals(n.y(),r.y(),1e-4);assertEquals(-n.x()*s+n.z()*c,r.z(),1e-4);
                }
            }
        }
    }
    private static double area(List<RecordingColorConsumer.Vertex> vertices){
        double sum=0;for(int i=0;i<vertices.size();i+=4){var a=point(vertices.get(i));var b=point(vertices.get(i+1));var c=point(vertices.get(i+2));var d=point(vertices.get(i+3));sum+=b.subtract(a).cross(c.subtract(a)).length()/2+c.subtract(a).cross(d.subtract(a)).length()/2;}return sum;
    }
    private static Vec3 point(RecordingColorConsumer.Vertex v){return new Vec3(v.x(),v.y(),v.z());}
}
