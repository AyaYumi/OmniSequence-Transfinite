package com.atir.molecularmanipulator.research;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResearchMaterialAllocatorTest {
    @Test void singleDemandReservesWholeBeforeTakingOrderedPortion() {
        var stock = new LinkedHashMap<String, Long>();
        stock.put("ignored", 100L); stock.put("oak", 3L); stock.put("birch", 5L);
        assertEquals(Map.of("oak", 3L, "birch", 1L), ResearchMaterialAllocator.planPortion(
                List.of(8L), List.of(4L), stock, (row, key) -> !key.equals("ignored")));
        assertNull(ResearchMaterialAllocator.planPortion(
                List.of(9L), List.of(0L), stock, (row, key) -> !key.equals("ignored")));
        assertEquals(Map.of("ignored", 100L, "oak", 3L, "birch", 5L), stock);
    }

    @Test void singleDemandHandlesLongMaximumAndZeroWithoutOverflow() {
        var stock = new LinkedHashMap<String, Long>();
        stock.put("a", Long.MAX_VALUE - 2); stock.put("b", Long.MAX_VALUE);
        assertEquals(Map.of("a", Long.MAX_VALUE - 2, "b", 2L), ResearchMaterialAllocator.plan(
                List.of(Long.MAX_VALUE), stock, (row, key) -> true));
        assertEquals(Map.of(), ResearchMaterialAllocator.plan(List.of(0L), stock, (row, key) -> false));
        assertThrows(IllegalArgumentException.class, () -> ResearchMaterialAllocator.plan(
                List.of(-1L), stock, (row, key) -> true));
    }

    @Test void portionsPreserveMaterialsNeededByLaterCrafts() {
        var stock = new LinkedHashMap<String, Long>(); stock.put("oak", 2L); stock.put("birch", 2L);
        var selected = ResearchMaterialAllocator.planPortion(List.of(2L, 2L), List.of(1L, 1L), stock,
                (row, key) -> row == 0 || key.equals("oak"));
        assertEquals(Map.of("oak", 1L, "birch", 1L), selected);
        selected.forEach((key, amount) -> stock.put(key, stock.get(key) - amount));
        assertNotNull(ResearchMaterialAllocator.plan(List.of(1L, 1L), stock, (row, key) -> row == 0 || key.equals("oak")));
    }
    @Test void portionsUseLongCountersWithoutSummingIndependentTypes() {
        var stock = Map.of("a", Long.MAX_VALUE, "b", Long.MAX_VALUE);
        assertEquals(Map.of("a", 1L, "b", 1L), ResearchMaterialAllocator.planPortion(
                List.of(Long.MAX_VALUE, Long.MAX_VALUE), List.of(1L, 1L), stock, (row, key) -> row == 0 || key.equals("a")));
    }
    @Test void oversizedPortionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ResearchMaterialAllocator.planPortion(
                List.of(1L), List.of(2L), Map.of("a", 2L), (row, key) -> true));
    }
    @Test void reservesSpecificIngredientEvenWhenBroadTagComesFirst() {
        var stock = new LinkedHashMap<String,Long>(); stock.put("oak",1L); stock.put("birch",1L);
        assertEquals(stock, ResearchMaterialAllocator.plan(List.of(1L,1L),stock,(row,key)->row==0||key.equals("oak")));
    }
    @Test void sharedStockCannotPayTwoCostsAndPlanningDoesNotMutateInventory() {
        var stock=new LinkedHashMap<>(Map.of("oak",3L));
        assertNull(ResearchMaterialAllocator.plan(List.of(2L,2L),stock,(row,key)->true));
        assertEquals(Map.of("oak",3L),stock);
        assertEquals(Map.of(),ResearchMaterialAllocator.plan(List.of(0L,0L),stock,(row,key)->true));
    }
    @Test void independentMaterialsCanEachReachLongMaxWithoutSummingAndOverflowing() {
        var stock=Map.of("a",Long.MAX_VALUE,"b",Long.MAX_VALUE);
        assertEquals(stock,ResearchMaterialAllocator.plan(List.of(Long.MAX_VALUE,Long.MAX_VALUE),stock,(row,key)->true));
        assertNull(ResearchMaterialAllocator.plan(List.of(Long.MAX_VALUE,1L),Map.of("a",Long.MAX_VALUE),(row,key)->true));
    }
    @Test void allSmallTwoIngredientCasesAgreeWithExhaustiveAssignments() {
        for(int mask=0;mask<16;mask++)for(int a=0;a<4;a++)for(int b=0;b<4;b++)for(int x=0;x<4;x++)for(int y=0;y<4;y++) {
            final int m=mask; boolean possible=false;
            for(int ax=0;ax<=a;ax++)for(int ay=0;ay<=a-ax;ay++)for(int bx=0;bx<=b;bx++)for(int by=0;by<=b-bx;by++)
                if(ax+bx==x&&ay+by==y&&(ax==0||(mask&1)!=0)&&(bx==0||(mask&2)!=0)&&(ay==0||(mask&4)!=0)&&(by==0||(mask&8)!=0))possible=true;
            var plan=ResearchMaterialAllocator.plan(List.of((long)x,(long)y),Map.of(0,(long)a,1,(long)b),(row,key)->(m&(1<<(row*2+key)))!=0);
            assertEquals(possible,plan!=null,"mask="+mask+" stock="+a+","+b+" demand="+x+","+y);
        }
    }
}
