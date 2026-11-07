package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.gui.theme.OsqPalette;
import org.junit.jupiter.api.Test;

class OsqFluidGaugeTest {
    @Test void liquidRisesFromEmptyToFullAtTheActualCapacity() {
        assertEquals(0, OsqFluidGauge.fillHeight(0, 1000, 30));
        assertEquals(6, OsqFluidGauge.fillHeight(200, 1000, 30));
        assertEquals(15, OsqFluidGauge.fillHeight(500, 1000, 30));
        assertEquals(24, OsqFluidGauge.fillHeight(800, 1000, 30));
        assertEquals(30, OsqFluidGauge.fillHeight(1000, 1000, 30));
        assertEquals(30, OsqFluidGauge.fillHeight(2000, 1000, 30));
    }

    @Test void aSmallAmountInTheIntMaxTankIsVisibleButNotShownAsFull() {
        assertEquals(1, OsqFluidGauge.fillHeight(1, Integer.MAX_VALUE, 30));
        assertEquals(1, OsqFluidGauge.fillHeight(1000, Integer.MAX_VALUE, 30));
        assertEquals(15, OsqFluidGauge.fillHeight(Integer.MAX_VALUE / 2, Integer.MAX_VALUE, 30));
        assertEquals(30, OsqFluidGauge.fillHeight(Integer.MAX_VALUE, Integer.MAX_VALUE, 30));
        assertEquals(30, OsqFluidGauge.fillHeight(Long.MAX_VALUE, Long.MAX_VALUE, 30));
        assertEquals(0, OsqFluidGauge.fillHeight(-1, 1000, 30));
        assertEquals(0, OsqFluidGauge.fillHeight(1000, 0, 30));
    }

    @Test void readoutKeepsOnlyThreeSignificantDigitsWhileTheTooltipCanStayExact() {
        assertEquals("0", OsqFluidGauge.readout(0));
        assertEquals("999", OsqFluidGauge.readout(999));
        assertEquals("1K", OsqFluidGauge.readout(1000));
        assertEquals("537M", OsqFluidGauge.readout(536_870_911));
        assertEquals("1.07G", OsqFluidGauge.readout(1_073_741_823));
        assertEquals("2.15G", OsqFluidGauge.readout(Integer.MAX_VALUE));
        assertEquals("1M", OsqFluidGauge.readout(999_950));
        assertEquals("2,147,483,647", DisplayNumbers.exact(Integer.MAX_VALUE));
    }

    @Test void emptyTanksKeepTheFourTicksInsideTheirOriginalGlassFrame() {
        for (var palette : new OsqPalette[]{OsqPalette.FAB, OsqPalette.FAB_DARK}) {
            var graphics = new UiRenderRecorder();
            OsqFluidGauge.draw(graphics, null, 0, Integer.MAX_VALUE, 14, 46, 34, 34, palette);
            assertEquals(4, graphics.rects.size());
            for (int i = 0; i < 4; i++) {
                var tick = graphics.rects.get(i);
                assertEquals(38, tick.left());assertEquals(46, tick.right());
                assertEquals(54 + i * 6, tick.top());assertEquals(tick.top() + 1, tick.bottom());
                assertEquals(palette.ink3, tick.color());
            }
            assertTrue(graphics.items.isEmpty(), "A gauge must not fall back to an inventory item icon");
        }
    }
}
