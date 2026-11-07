package com.atir.molecularmanipulator.blockentity;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MachineEffectSettingsTest {
    @Test void oldMachinesFollowTheCurrentClientDefaultWithoutMaterializingAnOverride() {
        var settings = new MachineEffectSettings();
        assertEquals(-1, settings.getOverride());
        for (int global = 0; global <= 2; global++) {
            assertEquals(global, MachineEffectSettings.resolve(settings.getOverride(), global));
        }
        var tag = new CompoundTag();
        settings.save(tag);
        assertFalse(tag.contains(MachineEffectSettings.NBT_KEY));
    }

    @Test void explicitLevelsAreExactlyZeroOneTwoAndNeverLimitedByTheGlobalDefault() {
        assertEquals(0, MachineEffectSettings.OFF);
        assertEquals(1, MachineEffectSettings.REDUCED);
        assertEquals(2, MachineEffectSettings.FULL);
        for (int level = 0; level <= 2; level++) {
            var settings = new MachineEffectSettings();
            assertTrue(settings.setOverride(level));
            for (int global = 0; global <= 2; global++) {
                assertEquals(level, MachineEffectSettings.resolve(settings.getOverride(), global));
            }
        }
    }

    @Test void inheritedDefaultsAreClampedWithJava17CompatibleIntegerMath() {
        assertEquals(0, MachineEffectSettings.resolve(-1, Integer.MIN_VALUE));
        assertEquals(2, MachineEffectSettings.resolve(-1, Integer.MAX_VALUE));
        assertEquals(1, MachineEffectSettings.resolve(3, 1));
        assertEquals(2, MachineEffectSettings.resolve(2, Integer.MIN_VALUE));
    }

    @Test void distinctMachinesAndReloadedCopiesDoNotShareState() {
        var first = new MachineEffectSettings();
        var second = new MachineEffectSettings();
        first.setOverride(0);
        second.setOverride(2);
        var firstTag = new CompoundTag();
        var secondTag = new CompoundTag();
        first.save(firstTag);
        second.save(secondTag);
        var reloadedFirst = new MachineEffectSettings();
        var reloadedSecond = new MachineEffectSettings();
        reloadedFirst.load(firstTag);
        reloadedSecond.load(secondTag);
        assertEquals(0, reloadedFirst.getOverride());
        assertEquals(2, reloadedSecond.getOverride());
        first.setOverride(1);
        assertEquals(0, reloadedFirst.getOverride());
        assertEquals(2, second.getOverride());
    }

    @Test void allStatesSurviveNbtWithoutChangingOtherMachineData() {
        for (int level = -1; level <= 2; level++) {
            var tag = new CompoundTag();
            tag.putString("existing_quantum_data", "preserved");
            var source = new MachineEffectSettings();
            source.setOverride(level);
            source.save(tag);
            var restored = new MachineEffectSettings();
            restored.setOverride(2);
            restored.load(tag);
            assertEquals(level, restored.getOverride());
            assertEquals("preserved", tag.getString("existing_quantum_data"));
        }
    }

    @Test void resettingRemovesOnlyTheOverrideAndReenablesLiveClientDefaults() {
        var settings = new MachineEffectSettings();
        var tag = new CompoundTag();
        tag.putInt("other_setting", 42);
        settings.setOverride(0);
        settings.save(tag);
        assertTrue(tag.contains(MachineEffectSettings.NBT_KEY));
        assertTrue(settings.setOverride(MachineEffectSettings.INHERIT));
        settings.save(tag);
        assertFalse(tag.contains(MachineEffectSettings.NBT_KEY));
        assertEquals(42, tag.getInt("other_setting"));
        assertEquals(1, MachineEffectSettings.resolve(settings.getOverride(), 1));
        assertEquals(2, MachineEffectSettings.resolve(settings.getOverride(), 2));
    }

    @Test void invalidAndRepeatedRequestsAreNotChanges() {
        var settings = new MachineEffectSettings();
        settings.setOverride(1);
        for (int invalid : new int[]{Integer.MIN_VALUE, -2, 3, 100, Integer.MAX_VALUE}) {
            assertFalse(MachineEffectSettings.isValidOverride(invalid));
            assertFalse(settings.setOverride(invalid));
            assertEquals(1, settings.getOverride());
        }
        assertFalse(settings.setOverride(1));
    }

    @Test void absentWronglyTypedAndCorruptSavedValuesResetToInheritance() {
        var settings = new MachineEffectSettings();
        var tag = new CompoundTag();
        settings.setOverride(0);
        settings.load(tag);
        assertEquals(-1, settings.getOverride());
        tag.putString(MachineEffectSettings.NBT_KEY, "0");
        settings.load(tag);
        assertEquals(-1, settings.getOverride());
        for (int invalid : new int[]{-2, 3, Integer.MAX_VALUE}) {
            settings.setOverride(2);
            tag.putInt(MachineEffectSettings.NBT_KEY, invalid);
            settings.load(tag);
            assertEquals(-1, settings.getOverride());
        }
    }

    @Test void wireRoundTripsPreserveInheritanceAndReportChangesWithoutShortCircuitingReads() {
        var target = new MachineEffectSettings();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (int level : new int[]{2, 0, 1, -1, -1, 2}) {
                var source = new MachineEffectSettings();
                source.setOverride(level);
                source.write(buffer);
                buffer.writeInt(123456); // The next stream field must never be consumed or left behind.
                boolean expectedChange = target.getOverride() != level;
                assertEquals(expectedChange, target.read(buffer));
                assertEquals(level, target.getOverride());
                assertEquals(123456, buffer.readInt());
                assertEquals(0, buffer.readableBytes());
                buffer.clear();
            }
            buffer.writeByte(127);
            assertTrue(target.read(buffer));
            assertEquals(-1, target.getOverride());
        } finally {
            buffer.release();
        }
    }

    @Test void cyclingContainsOnlyThreeExplicitTiersAndStartsFromTheEffectiveDefault() {
        for (int global = 0; global <= 2; global++) {
            assertEquals((global + 1) % 3, MachineEffectSettings.nextLevel(-1, global));
            int next = global;
            for (int step = 0; step < 3; step++) {
                next = MachineEffectSettings.nextLevel(next, global);
                assertTrue(next >= 0 && next <= 2);
            }
            assertEquals(global, next);
        }
    }
}
