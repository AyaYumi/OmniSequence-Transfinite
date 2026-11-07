package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StarOrreryNebulaRendererTest {
    @Test
    void enlargedGalaxyReachesPastTheSecondBeltAndRemainsAvailableUntilWorldFogFadesIt() {
        assertTrue(StarOrreryNebulaRenderer.FIELD_RADIUS
                > com.atir.molecularmanipulator.starcomputation.StarOrreryLayout.radius(1));
        assertTrue(StarOrreryNebulaRenderer.FIELD_RADIUS
                < com.atir.molecularmanipulator.starcomputation.StarOrreryLayout.radius(2));
        assertFalse(StarOrreryNebulaRenderer.visible(0,0));
        assertFalse(StarOrreryNebulaRenderer.visible(-1,0));
        for (double distance : new double[] {160, 224, 257, 384, 512}) {
            assertTrue(StarOrreryNebulaRenderer.visible(9,distance*distance));
        }
        assertTrue(StarOrreryNebulaRenderer.visible(1,159D*159D));
    }

    @Test
    void viewRaysMapBackToFixedMachineSpaceFromEveryFacingAndCameraAngle() {
        for (int facing=0;facing<4;facing++) for (float angle:new float[] {-.8F,0,.9F,2.7F}) {
            var pose=new Matrix4f().translation(24,5,-32).rotateY((float)(facing*Math.PI/2));
            var view=new Matrix4f().rotationX(angle*.3F).rotateY(angle);
            var poseBefore=new Matrix4f(pose);var viewBefore=new Matrix4f(view);
            var inverse=StarOrreryNebulaRenderer.viewToNebula(pose,view);
            var forward=new Matrix4f(view).mul(pose);
            for (var p:new Vector3f[] {new Vector3f(),new Vector3f(2,3,-1),new Vector3f(-5,1,4)}) {
                var restored=inverse.transformPosition(forward.transformPosition(new Vector3f(p)));
                assertVector(p,restored,1E-4F);
            }
            var direction=new Vector3f(.2F,.1F,-1).normalize();
            assertVector(direction,inverse.transformDirection(forward.transformDirection(new Vector3f(direction))),1E-5F);
            assertEquals(poseBefore,pose);assertEquals(viewBefore,view);
        }
    }

    @Test
    void shaderReusesProjectLensVertexStageAndOnlySamplesTheCurrentScene() throws Exception {
        var json=JsonParser.parseString(resource("star_orrery_nebula.json")).getAsJsonObject();
        assertEquals("molecularmanipulator:cosmic_lens",json.get("vertex").getAsString());
        assertEquals("molecularmanipulator:star_orrery_nebula",json.get("fragment").getAsString());
        assertEquals(Set.of("SceneDepth"),json.getAsJsonArray("samplers").asList().stream()
                .map(e->e.getAsJsonObject().get("name").getAsString()).collect(Collectors.toSet()));
        var composite=JsonParser.parseString(resource("star_orrery_nebula_composite.json")).getAsJsonObject();
        assertEquals(Set.of("SceneColor","SceneDepth","VolumeColor"),
                composite.getAsJsonArray("samplers").asList().stream()
                        .map(e->e.getAsJsonObject().get("name").getAsString()).collect(Collectors.toSet()));
        var uniforms=json.getAsJsonArray("uniforms").asList().stream()
                .map(e->e.getAsJsonObject().get("name").getAsString()).collect(Collectors.toSet());
        assertTrue(uniforms.containsAll(Set.of("ViewToNebula","InverseProjection","RayOrigin",
                "NebulaTime","FlowTime","Detail","Dynamics","FieldRadius","MarchSize",
                "NebulaToCamera","VolumeFogStart","VolumeFogEnd","VolumeFogShape")));
    }

    @Test
    void volumeMarchClipsAgainstForegroundDepthAndUsesTheTranslatedProjectionEye() throws Exception {
        String shader=volumeShader().replaceAll("\\s+","");
        assertTrue(shader.contains("normalize(viewPoint(uv,0.5)-RayOrigin)"));
        assertTrue(shader.contains("ViewToNebula*vec4(RayOrigin,1.0)"));
        assertTrue(shader.contains("ViewToNebula*vec4(viewRay,0.0)"));
        assertTrue(shader.contains("length(viewPoint(uv,depth)-RayOrigin)"));
        assertTrue(shader.contains("min(-b+sqrt(discriminant),surfaceDistance(uv))"));
        assertTrue(shader.contains("if(far<=near)returnvec4(0,0,0,1);"));
        String composite=resource("star_orrery_nebula_composite.fsh").replaceAll("\\s+","");
        assertTrue(composite.contains("background*volume.a+volume.rgb"));
        assertTrue(composite.contains("sum/total:integrateNebula(uv)"),"Unsampled depth edges use the full-resolution ray");
        assertTrue(composite.contains("clamp(base+vec2(x,y),MarchBounds.xy,MarchBounds.zw)"));
    }

    @Test
    void shaderHasBoundedQualityLevelsAndDynamicFlowRatherThanACloudTexture() throws Exception {
        String shader=volumeShader().replaceAll("\\s+","");
        assertTrue(shader.contains("Detail>0.5?64:36"));
        assertTrue(shader.contains("for(inti=0;i<64;i++)"));
        assertTrue(shader.contains("if(i>=steps||transmittance<0.015)break;"));
        assertTrue(shader.contains("radius*31.0+FlowTime*TAU/4.0"));
        assertTrue(shader.contains("floatnoise(vec3p)"));
        assertTrue(shader.contains("floatvertical=exp("));
        assertFalse(shader.contains("sampler2DCloud"));
        assertFalse(shader.contains("GameTime"),"FlowTime comes from the real activity clock, not a global time multiplier");
    }

    @Test
    void enlargedDiskHasIndependentSpiralLayersDustLanesAndWarmCore() throws Exception {
        String shader=volumeShader().replaceAll("\\s+","");
        assertTrue(shader.contains("vec3p=galaxySpace(point/FieldRadius)"));
        assertTrue(shader.contains("5.1*log(radius+0.055)"));
        assertTrue(shader.contains("floatmainArms="));
        assertTrue(shader.contains("floatbranches="));
        assertTrue(shader.contains("floatfilaments="));
        assertTrue(shader.contains("floatdustLanes="));
        assertTrue(shader.contains("floatstellarPoints(vec2p)"));
        assertTrue(shader.contains("floatdust="));
        assertTrue(shader.contains("lightDensity/max(lightDensity+dust,0.0001)"));
        assertTrue(shader.contains("smoothstep(0.73,0.99,radius)"));
        assertTrue(shader.contains("vec3(1.0,0.76,0.54)"));
    }

    @Test
    void volumeFadesEachSampleWithTerrainFogSavedBeforeTheLateCompositionPass() throws Exception {
        String shader=volumeShader().replaceAll("\\s+","");
        assertTrue(shader.contains("fog_distance((NebulaToCamera*vec4(point,1.0)).xyz,VolumeFogShape)"));
        assertTrue(shader.contains("VolumeFogShape),VolumeFogStart,VolumeFogEnd)"));
        assertTrue(shader.contains("(1.0-exp(-field.a*stride*0.84))*visibility"));
        var json=JsonParser.parseString(resource("star_orrery_nebula.json")).getAsJsonObject();
        var uniforms=json.getAsJsonArray("uniforms").asList().stream()
                .map(e->e.getAsJsonObject().get("name").getAsString()).collect(Collectors.toSet());
        assertFalse(uniforms.contains("FogStart"),"Late default uniforms must not replace the saved terrain fog");
        String renderer=java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/atir/molecularmanipulator/client/render/StarOrreryNebulaRenderer.java"));
        String enqueue=renderer.substring(renderer.indexOf("public static void enqueue"),renderer.indexOf("static Matrix4f viewToNebula"));
        assertTrue(enqueue.contains("RenderSystem.getShaderFogStart()"));
        assertTrue(enqueue.contains("RenderSystem.getShaderFogEnd()"));
        assertTrue(enqueue.contains("RenderSystem.getShaderFogShape()"));
    }

    @Test
    void densityEnvelopeRejectsEmptySpaceBeforeComputingNoiseButKeepsTheCoreAndFeed() throws Exception {
        String shader=volumeShader().replaceAll("\\s+","");
        int reject=shader.indexOf("if(max(vertical,diffuseVertical)*edge+bulge+feedEnvelope<0.000001)");
        assertTrue(reject>shader.indexOf("floatbulge="));
        assertTrue(reject<shader.indexOf("floatturbulence="));
        assertTrue(shader.contains("returnvec4(min(radiance,vec3(1.3)),transmittance)"));
        assertFalse(shader.contains("discard"),"Every covered march texel overwrites the reused target");
    }

    @Test
    void halfResolutionRectanglesCoverEveryFilterTapAndOddWindowEdge() {
        for (int width:new int[]{1,2,127,128,1919,1920}) for (int height:new int[]{1,3,1079,1080}) {
            int lowWidth=StarOrreryNebulaRenderer.marchDimension(width);
            int lowHeight=StarOrreryNebulaRenderer.marchDimension(height);
            assertEquals((width+1)/2,lowWidth);
            assertEquals((height+1)/2,lowHeight);
            for (int x:new int[]{0,width/2,width-1}) for (int y:new int[]{0,height/2,height-1}) {
                var nativeBounds=new SingularityLensProjection.ScreenBounds(x,y,width-x,height-y);
                var march=StarOrreryNebulaRenderer.scaleBounds(nativeBounds,width,height,lowWidth,lowHeight)
                        .expand(1,lowWidth,lowHeight);
                var source=StarOrreryNebulaRenderer.scaleBounds(march,lowWidth,lowHeight,width,height)
                        .expand(1,width,height);
                assertTrue(source.x()<=nativeBounds.x());
                assertTrue(source.y()<=nativeBounds.y());
                assertEquals(width,source.x()+source.width());
                assertEquals(height,source.y()+source.height());
                for (int lx=march.x();lx<march.x()+march.width();lx++) {
                    int sample=(int)((lx+.5D)*width/lowWidth);
                    assertTrue(sample>=source.x() && sample<source.x()+source.width());
                }
            }
        }
        assertFalse(StarOrreryNebulaRenderer.reducedResolution(new SingularityLensProjection.ScreenBounds(0,0,127,128)));
        assertTrue(StarOrreryNebulaRenderer.reducedResolution(new SingularityLensProjection.ScreenBounds(0,0,128,128)));
    }

    private static String volumeShader() throws Exception {
        try(var stream=StarOrreryNebulaRendererTest.class.getResourceAsStream(
                "/assets/molecularmanipulator/shaders/include/star_orrery_volume.glsl")) {
            assertNotNull(stream);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    private static void assertVector(Vector3f expected,Vector3f actual,float tolerance) {
        assertEquals(expected.x,actual.x,tolerance);assertEquals(expected.y,actual.y,tolerance);
        assertEquals(expected.z,actual.z,tolerance);
    }

    private static String resource(String file) throws Exception {
        try(var stream=StarOrreryNebulaRendererTest.class.getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/"+file)) {
            assertNotNull(stream);return new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
