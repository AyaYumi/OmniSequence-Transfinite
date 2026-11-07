package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StarOrreryCoreEffectsTest {
    @Test
    void inactiveOrDisabledCoresEmitNoRainButDistanceOnlyReducesDetail() {
        for (boolean glow : new boolean[] {false,true}) {
            for (int quality : new int[] {0,1,2}) assertTrue(draw(0,quality,0,6,glow).vertices().isEmpty());
            assertTrue(draw(9,0,0,6,glow).vertices().isEmpty());
            for (double distance : new double[] {160, 224, 257, 384, 512}) {
                assertFalse(draw(9,2,distance*distance,6,glow).vertices().isEmpty());
            }
            assertTrue(draw(-1,2,0,6,glow).vertices().isEmpty());
            assertFalse(draw(9,1,159D*159D,6,glow).vertices().isEmpty());
        }
    }

    @Test
    void actualOperatingStateRatherThanRingTierControlsAccretionSpeed() {
        assertEquals(.35F,StarOrreryCoreEffects.accretionSpeed(0));
        assertEquals(1F,StarOrreryCoreEffects.accretionSpeed(1));
        assertEquals(2F,StarOrreryCoreEffects.accretionSpeed(2));
        assertEquals(.35F,StarOrreryCoreEffects.accretionSpeed(-1));
        assertEquals(2F,StarOrreryCoreEffects.accretionSpeed(99));
    }

    @Test
    void changingLoadDoesNotJumpShaderFlowAndDisabledTimeDoesNotAccumulate() {
        var clock = new StarOrreryCoreEffects.AccretionClock();
        assertEquals(0,clock.sample(10,1,true));
        assertEquals(1,clock.sample(11,1,true));
        assertEquals(1,clock.sample(11,2,true));
        assertEquals(3,clock.sample(12,2,true));
        assertEquals(3,clock.sample(12,2,false));
        assertEquals(3,clock.sample(112,1,true));
        assertEquals(4,clock.sample(113,1,true));
        assertEquals(4,clock.sample(110,1,true));
    }

    @Test
    void reducedQualityAndDistanceReduceRainWork() {
        assertTrue(draw(9,2,0,3,false).vertices().size()>draw(9,1,0,3,false).vertices().size()*2);
        assertEquals(draw(9,1,0,3,false).vertices(),draw(9,2,113D*113D,3,false).vertices());
        assertEquals(draw(3,2,0,3,false).vertices(),draw(9,2,0,3,false).vertices());
    }

    @Test
    void rainUsesBlueVioletAndIceRatherThanRecoloringTheWholeCoreGold() {
        var vertices=draw(9,2,0,3,false).vertices();
        assertTrue(vertices.stream().anyMatch(v->v.rgb()==0xBF91FF));
        assertTrue(vertices.stream().anyMatch(v->v.rgb()==0x71BFFF));
        assertTrue(vertices.stream().anyMatch(v->v.rgb()==0xDBF6FF));
        assertTrue(vertices.stream().allMatch(v->(v.rgb()&255)>=(v.rgb()>>16&255)));
    }

    @Test
    void rainGeometryIsDeterministicAndRepeatsWithTheFlowClock() {
        for (boolean glow:new boolean[] {false,true}) {
            assertNotEquals(draw(9,2,0,1,glow).vertices(),draw(9,2,0,6,glow).vertices());
            assertEquals(draw(9,2,0,3,glow).vertices(),draw(9,2,0,3,glow).vertices());
            var a=draw(9,2,0,3,glow).vertices(); var b=draw(9,2,0,12003,glow).vertices();
            assertEquals(a.size(),b.size());
            for (int i=0;i<a.size();i++) {
                assertEquals(a.get(i).x(),b.get(i).x(),1E-5);
                assertEquals(a.get(i).y(),b.get(i).y(),1E-4);
                assertEquals(a.get(i).z(),b.get(i).z(),1E-5);
                assertEquals(a.get(i).argb(),b.get(i).argb());
            }
        }
    }

    @Test
    void dataRainTravelsDownTheFixedCentralAxisAndStaysAboveTheWell() {
        assertTrue(StarOrreryCoreEffects.rainY(0,0,1)>StarOrreryCoreEffects.rainY(0,0,2));
        for (boolean glow:new boolean[] {false,true}) for (float time:new float[] {0,1,4,12,11999}) {
            var vertices=draw(9,2,0,time,glow).vertices();
            assertFalse(vertices.isEmpty()); assertEquals(0,vertices.size()%4);
            assertTrue(vertices.stream().allMatch(v->Math.hypot(v.x(),v.z())<3.6));
            assertTrue(vertices.stream().allMatch(v->v.y()<=StarOrreryCoreEffects.RAIN_TOP
                    && v.y()>=StarOrreryCoreEffects.RAIN_BOTTOM-.55));
            assertTrue(vertices.stream().anyMatch(v->v.y()<-10));
            assertTrue(vertices.stream().allMatch(v->v.alpha()>0 && v.alpha()<=(glow?32:240)));
        }
    }

    @Test
    void glyphsContainRealDistinctHexDigitsAndRunesInsteadOfUniformDots() {
        var masks=new java.util.HashSet<Integer>();
        for (int glyph=0;glyph<20;glyph++) {
            int mask=StarOrreryCoreEffects.glyphMask(glyph);
            assertTrue(mask>0 && mask<1<<15); assertTrue(Integer.bitCount(mask)>=5);
            assertTrue(masks.add(mask));
        }
        assertEquals(StarOrreryCoreEffects.glyphMask(0),StarOrreryCoreEffects.glyphMask(20));
    }

    @Test
    void glitchIsIntermittentLocalAndStaggeredInsteadOfGlobalFlashing() {
        int visible=0;
        for (int sample=0;sample<400;sample++) {
            float value=StarOrreryCoreEffects.glitch(1,sample/100F);
            assertTrue(value>=0 && value<=1);
            if (value>0) visible++;
        }
        assertTrue(visible>=30 && visible<45);
        float atPeak=(float)((.825-.271)*4);
        assertTrue(StarOrreryCoreEffects.glitch(1,atPeak)>.99F);
        assertEquals(0,StarOrreryCoreEffects.glitch(2,atPeak));
    }

    @Test
    void rainAppliesAndRestoresTheCallerPose() {
        for (int angle:new int[] {0,90,180,270}) for (boolean glow:new boolean[] {false,true}) {
            var poses=new PoseStack();poses.translate(3,12,-9);poses.mulPose(Axis.YP.rotationDegrees(angle));
            var before=new Matrix4f(poses.last().pose());
            var local=draw(9,2,0,3.5F,glow);var transformed=new RecordingColorConsumer();
            StarOrreryCoreEffects.renderRain(poses,transformed,9,2,0,3.5F,glow);
            assertEquals(before,poses.last().pose());assertEquals(local.vertices().size(),transformed.vertices().size());
            for (int i=0;i<local.vertices().size();i++) {
                var source=local.vertices().get(i);var actual=transformed.vertices().get(i);
                var expected=before.transformPosition(new Vector3f(source.x(),source.y(),source.z()));
                assertEquals(expected.x,actual.x(),1E-5);assertEquals(expected.y,actual.y(),1E-5);
                assertEquals(expected.z,actual.z(),1E-5);assertEquals(source.argb(),actual.argb());
            }
        }
    }

    @Test
    void rainAndRingGeometryStayBoundedWithoutTheRemovedParticleCloud() {
        for (float time:new float[] {0,.5F,2,3.5F,5,6.5F,7.5F}) {
            int full=0,reduced=0,rails=0;
            for (boolean glow:new boolean[] {false,true}) {
                full+=draw(9,2,0,time,glow).vertices().size();
                reduced+=draw(9,1,0,time,glow).vertices().size();
                var out=new RecordingColorConsumer();
                StarOrreryEffects.render(new PoseStack(),out,9,time,true,glow);rails+=out.vertices().size();
            }
            assertTrue(full<8500,"Axial code geometry: "+full);
            assertTrue(reduced*2<full);
            assertTrue(rails+full<80000,"Ring + code geometry: "+(rails+full));
        }
    }

    private static RecordingColorConsumer draw(int lit,int quality,double distance,float time,boolean glow) {
        var out=new RecordingColorConsumer();
        StarOrreryCoreEffects.renderRain(new PoseStack(),out,lit,quality,distance,time,glow);
        return out;
    }
}
