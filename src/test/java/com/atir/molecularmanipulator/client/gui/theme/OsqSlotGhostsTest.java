package com.atir.molecularmanipulator.client.gui.theme;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqSlotGhostsTest {
    @Test void removeOpaqueTileButKeepExactNativeOutlinePixels() {
        int[] pixels=new int[256];Arrays.fill(pixels,0xFFADB0C4);
        Arrays.fill(pixels,0,16,0xFF9A9FB4);
        for(int x=2;x<6;x++)pixels[16+x]=0xFF878FA5;
        pixels[2*16+1]=0xFF878FA5;pixels[6*16+4]=0xFF878FA5;
        var result=OsqSlotGhosts.stripTile(pixels);
        assertEquals(6,Arrays.stream(result).filter(p->p!=0).count());
        assertEquals(0xFF878FA5,result[18]);assertEquals(0xFF878FA5,result[100]);
        assertEquals(0,result[0]);assertEquals(0,result[255]);
        assertEquals(0xFFADB0C4,pixels[255],"Do not mutate native source pixels");
    }
    @Test void rejectWrongTileDimensions() {
        assertThrows(IllegalArgumentException.class,()->OsqSlotGhosts.stripTile(new int[16]));
    }
}
