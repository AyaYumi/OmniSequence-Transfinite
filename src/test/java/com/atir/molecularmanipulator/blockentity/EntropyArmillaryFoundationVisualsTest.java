package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryFoundation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class EntropyArmillaryFoundationVisualsTest {
    private static CompoundTag owner(BlockPos core, boolean hidden, long... pending) {
        var entry = new CompoundTag();
        entry.putLong("core",core.asLong());
        entry.putBoolean("hidden",hidden);
        entry.putLongArray("pending",pending);
        return entry;
    }

    private static EntropyArmillaryFoundationVisuals data(CompoundTag... entries) {
        var list = new ListTag();
        for (var entry : entries) list.add(entry);
        var tag = new CompoundTag();tag.put("owners",list);
        return EntropyArmillaryFoundationVisuals.load(tag,null);
    }

    @Test void activeVisualOwnersSurviveReloadAndHideOnlyRequiredPositions() {
        var core = new BlockPos(-17,100,31);
        var original = data(owner(core,true));
        var saved = original.save(new CompoundTag(),null);
        var restored = EntropyArmillaryFoundationVisuals.load(saved,null);
        for (var member : EntropyArmillaryFoundation.members()) assertTrue(restored.hiddenAt(core.offset(member.offset())));
        assertFalse(restored.hiddenAt(core));
        assertFalse(restored.hiddenAt(core.offset(0,-33,0)),"The open well is not a foundation member");
        assertFalse(restored.hiddenAt(core.offset(30,-38,0)),"Retired terraces must not remain invisible after an upgrade");
        assertFalse(restored.hiddenAt(core.offset(10,-37,10)),"The ring frame gaps are not hidden blocks");
        assertFalse(restored.hiddenAt(core.offset(31,-38,0)));
        assertEquals(saved,restored.save(new CompoundTag(),null));
    }

    @Test void pendingRestorationNeverHidesBlocksAndRejectsUnrelatedChunks() {
        var core = new BlockPos(10,100,20);
        var pending = new ChunkPos(core.offset(0,-38,0));
        var original = data(owner(core,false,pending.toLong(),new ChunkPos(500,500).toLong()));
        assertFalse(original.hiddenAt(core.offset(0,-38,0)));
        var entries = original.save(new CompoundTag(),null).getList("owners",Tag.TAG_COMPOUND);
        assertEquals(1,entries.size());
        assertArrayEquals(new long[]{pending.toLong()},entries.getCompound(0).getLongArray("pending"));
        assertTrue(data(owner(core,false)).save(new CompoundTag(),null).getList("owners",Tag.TAG_COMPOUND).isEmpty());
    }

    @Test void retiredOuterChunksStayQueuedUntilTheirOldHiddenBlocksCanBeRestored() {
        var core = new BlockPos(8,100,8);
        var retired = new ChunkPos(core.offset(0,-38,-30));
        assertFalse(EntropyArmillaryFoundation.chunksFor(core).contains(retired));
        var original = data(owner(core,false,retired.toLong()));
        var saved = original.save(new CompoundTag(),null);
        var entries = saved.getList("owners",Tag.TAG_COMPOUND);
        assertEquals(1,entries.size());
        assertArrayEquals(new long[]{retired.toLong()},entries.getCompound(0).getLongArray("pending"));
        assertEquals(saved,EntropyArmillaryFoundationVisuals.load(saved,null).save(new CompoundTag(),null));
    }

    @Test void releasingOneOverlappingOwnerDoesNotExposeAnotherFormedPedestal() {
        var first = new BlockPos(0,100,0);
        var second = new BlockPos(10,100,0);
        var shared = first.offset(16,-37,0);
        var data = data(owner(first,false,new ChunkPos(shared).toLong()),owner(second,true));
        assertTrue(data.hiddenAt(shared));
        assertFalse(data.hiddenAt(first.offset(-18,-37,0)),"An inactive owner must not hide its private area");
    }
}
