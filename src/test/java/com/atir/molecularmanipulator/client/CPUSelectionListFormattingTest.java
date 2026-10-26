package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.CpuSelectionMode;
import appeng.client.gui.widgets.CPUSelectionList;
import appeng.client.gui.widgets.InfoBar;
import appeng.menu.me.crafting.CraftingStatusMenu;
import com.atir.molecularmanipulator.mixin.CPUSelectionListMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * Directly exercises the three private mixin handlers. No client, window or OpenGL context is
 * started; the only bootstrap is the one shared with {@link UiRenderRecorder}.
 */
class CPUSelectionListFormattingTest {
    private static final String NEXUS = "gui.molecularmanipulator.nexus.cpu_name";
    private static final String NEXUS_SHORT = "gui.molecularmanipulator.nexus.cpu_name_short";
    private static final String OMNI = "gui.molecularmanipulator.omni.cpu_name";
    private static final String OMNI_SHORT = "gui.molecularmanipulator.omni.cpu_name_short";

    private static final long KIB = 1L << 10;
    private static final long MIB = 1L << 20;
    private static final long GIB = 1L << 30;
    private static final long TIB = 1L << 40;
    private static final long PIB = 1L << 50;
    private static final long EIB = 1L << 60;

    private static final int COLOR = 0xFF12AB34;
    private static final float SCALE = 0.666F;
    private static final int X = 39;
    private static final int Y = 13;

    // Also triggers UiRenderRecorder's headless Minecraft/NeoForge bootstrap.
    private static final UiRenderRecorder.MetricsFont FONT = new UiRenderRecorder.MetricsFont();

    private static final Method COMPACT_NAME = findMethod("molecularmanipulator$compactOmniCpuName",
            CPUSelectionList.class, CraftingStatusMenu.CraftingCpuListEntry.class, Operation.class);
    private static final Method COMPACT_STORAGE = findMethod("molecularmanipulator$compactStorage",
            InfoBar.class, String.class, int.class, float.class, int.class, int.class, Operation.class,
            CraftingStatusMenu.CraftingCpuListEntry.class);
    private static final Method COMPACT_PARALLELISM = findMethod("molecularmanipulator$compactParallelism",
            InfoBar.class, String.class, int.class, float.class, int.class, int.class, Operation.class,
            CraftingStatusMenu.CraftingCpuListEntry.class);

    @Test
    void bothOmniNamesUseShortKeysWithoutChangingTheFullTooltipName() throws Exception {
        for (String key : List.of(NEXUS, OMNI)) {
            String shortKey = NEXUS.equals(key) ? NEXUS_SHORT : OMNI_SHORT;
            // Long.MAX_VALUE must still get the short name; only the amount formatters defer
            // the infinity sentinel to AppliedEnhancements.
            for (long storage : new long[] {0L, 16L * TIB, Long.MAX_VALUE}) {
                var fullName = Component.translatable(key, "L7");
                var cpu = entry(fullName, storage, 65535);

                var shortened = shortenName(cpu, fullName);

                assertNotSame(fullName, shortened);
                var shortContents = assertInstanceOf(TranslatableContents.class, shortened.getContents());
                assertEquals(shortKey, shortContents.getKey());
                assertArrayEquals(new Object[] {"L7"}, shortContents.getArgs());

                var fullContents = assertInstanceOf(TranslatableContents.class, fullName.getContents());
                assertEquals(key, fullContents.getKey());
                assertArrayEquals(new Object[] {"L7"}, fullContents.getArgs());
                assertSame(fullName, cpu.name());
            }
        }
    }

    @Test
    void otherModsLiteralsAndNullNamesAreNotShortened() throws Exception {
        var otherMod = Component.translatable("gui.example.other.cpu_name", "L8");
        var literalKey = Component.literal(NEXUS);
        var literalText = Component.literal("Transfinite Compute Nexus - Lane L8");
        for (var fullName : List.of(otherMod, literalKey, literalText)) {
            var cpu = entry(fullName, 16L * TIB, 65535);
            assertSame(fullName, shortenName(cpu, fullName));
        }

        // getCpuName falls back to "Cpus #n" when the CPU has no name.
        var fallback = Component.literal("Cpus #8");
        var nullNameCpu = entry(null, 16L * TIB, 65535);
        assertSame(fallback, shortenName(nullNameCpu, fallback));

        // A translatable key that merely contains one of our keys is not an Omni CPU name.
        var lookalike = Component.translatable(NEXUS + "_suffix", "L8");
        assertSame(lookalike, shortenName(entry(lookalike, 16L * TIB, 65535), lookalike));
    }

    @Test
    void nonOmniNamesKeepAe2NumericTextAndDrawParameters() throws Exception {
        var otherMod = Component.translatable("gui.example.other.cpu_name", "L9");
        var literalKey = Component.literal(NEXUS);
        for (var name : List.of(otherMod, literalKey)) {
            var cpu = entry(name, 16L * TIB, 65535);
            assertEquals("16.0T", invokeStorage(cpu, "16.0T").text());
            assertEquals("65.54k", invokeParallelism(cpu, "65.54k").text());
        }

        var nullNameCpu = entry(null, 16L * TIB, 65535);
        assertEquals("16.0T", invokeStorage(nullNameCpu, "16.0T").text());
        assertEquals("65.54k", invokeParallelism(nullNameCpu, "65.54k").text());
    }

    @Test
    void storageUsesBinaryUnitsAtEveryCapacityAndFiniteLongsDoNotOverflow() throws Exception {
        var name = Component.translatable(NEXUS, "L10");
        record Case(long bytes, String label) { }
        var cases = List.of(
                new Case(0L, "0"),
                new Case(1L, "1"),
                new Case(512L, "512"),
                new Case(999L, "999"),
                new Case(1023L, "1023"),
                new Case(KIB, "1k"),
                new Case(MIB, "1M"),
                new Case(GIB, "1G"),
                new Case(TIB, "1T"),
                new Case(PIB, "1P"),
                new Case(EIB, "1E"),
                new Case(16L * TIB, "16T"),
                new Case(64L * TIB, "64T"),
                new Case(256L * GIB, "256G"),
                new Case((long) Integer.MAX_VALUE + 1L, "2G"),
                new Case(Long.MAX_VALUE / 2, "3.9E"),
                new Case(Long.MAX_VALUE - 1L, "7.9E"));

        for (var c : cases) {
            String text = invokeStorage(entry(name, c.bytes(), 0), "ae2-storage").text();
            assertEquals(c.label(), text, "storage " + c.bytes());
            assertTrue(text.length() <= 4, "common storage label exceeds four characters: " + text);
        }
    }

    @Test
    void binaryThresholdsAndDecimalTruncationAreExact() throws Exception {
        var name = Component.translatable(OMNI, "L11");
        record Case(long bytes, String label) { }
        var cases = List.of(
                new Case(1023L, "1023"),
                new Case(1024L, "1k"),
                new Case(1536L, "1.5k"),
                new Case(999999L, "976k"),
                // Promote just below the next binary unit instead of overflowing the column.
                new Case(1023L * KIB, ".99M"),
                new Case(MIB - 1, ".99M"),
                new Case(MIB, "1M"),
                new Case(1023L * MIB, ".99G"),
                new Case(GIB - 1, ".99G"),
                new Case(GIB, "1G"),
                new Case(TIB - 1, ".99T"),
                new Case(TIB, "1T"),
                new Case(PIB - 1, ".99P"),
                new Case(PIB, "1P"),
                new Case(EIB - 1, ".99E"),
                new Case(EIB, "1E"));

        for (var c : cases) {
            var label = invokeStorage(entry(name, c.bytes(), 0), "ae2-storage").text();
            assertEquals(c.label(), label, "storage " + c.bytes());
            assertTrue(label.length() <= 4, "storage label overflows its column: " + label);
        }
    }

    @Test
    void processorLabelsUseIntegerKmgQuotientsAndFitTheThirteenPixelColumn() throws Exception {
        var name = Component.translatable(OMNI, "L12");
        record Case(int processors, String label) { }
        var cases = List.of(
                new Case(0, "0"),
                new Case(1, "1"),
                new Case(999, "999"),
                new Case(1000, "1K"),
                new Case(1023, "1K"),
                new Case(1024, "1K"),
                new Case(9999, "9K"),
                new Case(10000, "10K"),
                new Case(65535, "65K"),
                new Case(65536, "65K"),
                new Case(99999, "99K"),
                new Case(100000, "100K"),
                new Case(131071, "131K"),
                new Case(131072, "131K"),
                new Case(131100, "131K"),
                new Case(999999, "999K"),
                new Case(1000000, "1M"),
                new Case(1999999, "1M"),
                new Case(999999999, "999M"),
                new Case(1000000000, "1G"),
                new Case(Integer.MAX_VALUE - 1, "2G"));

        for (var c : cases) {
            float expectedScale = expectedParallelismScale(c.label());
            var operation = invokeParallelism(
                    entry(name, 16L * TIB, c.processors()), "ae2-processors", expectedScale);
            String text = operation.text();
            assertEquals(c.label(), text, "processor " + c.processors());
            assertTrue(text.length() <= 4, "processor label exceeds four characters: " + text);
            // Four-character finite labels shrink to 0.5 so they still fit the 13px column;
            // shorter labels keep the widget's original 2/3 scale.
            assertTrue(FONT.width(text) * operation.scale() <= 13.0F,
                    "processor label overlaps the 13px storage-icon column: " + text);
        }
    }

    @Test
    void infiniteStorageAndProcessorSentinelsAreIndependentAndUntouched() throws Exception {
        var name = Component.translatable(OMNI, "L13");

        var infiniteStorage = entry(name, Long.MAX_VALUE, 65535);
        assertEquals("ae2-infinite-storage", invokeStorage(infiniteStorage, "ae2-infinite-storage").text());
        assertEquals("65K", invokeParallelism(infiniteStorage, "ae2-processors").text());

        var infiniteProcessors = entry(name, 16L * TIB, Integer.MAX_VALUE);
        assertEquals("16T", invokeStorage(infiniteProcessors, "ae2-storage").text());
        assertEquals("ae2-infinite-processors",
                invokeParallelism(infiniteProcessors, "ae2-infinite-processors").text());

        var bothInfinite = entry(name, Long.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals("ae2-infinite-storage", invokeStorage(bothInfinite, "ae2-infinite-storage").text());
        assertEquals("ae2-infinite-processors",
                invokeParallelism(bothInfinite, "ae2-infinite-processors").text());
    }

    @Test
    void negativeStorageAndProcessorValuesAreForwardedUntouched() throws Exception {
        var name = Component.translatable(NEXUS, "L14");
        for (long storage : new long[] {-1L, Long.MIN_VALUE}) {
            var cpu = entry(name, storage, 65535);
            assertEquals("ae2-negative-storage", invokeStorage(cpu, "ae2-negative-storage").text());
            assertEquals("65K", invokeParallelism(cpu, "ae2-processors").text());
        }
        for (int processors : new int[] {-1, Integer.MIN_VALUE}) {
            var cpu = entry(name, 16L * TIB, processors);
            assertEquals("16T", invokeStorage(cpu, "ae2-storage").text());
            assertEquals("ae2-negative-processors",
                    invokeParallelism(cpu, "ae2-negative-processors").text());
        }
    }

    @Test
    void compactHandlersOnlyReplaceTextAndForwardEveryOtherDrawParameter() throws Exception {
        var name = Component.translatable(NEXUS, "L15");
        var cpu = entry(name, 16L * TIB, 65535);
        var bar = new InfoBar();
        int color = 0xC03F1A7B;
        float scale = 0.666F;
        int x = 39, y = 13;

        var storageOperation = new VoidOperation();
        COMPACT_STORAGE.invoke(new CPUSelectionListMixin() {}, bar, "ae2-storage", color, scale,
                x, y, storageOperation, cpu);
        assertEquals(1, storageOperation.calls);
        assertSame(bar, storageOperation.bar());
        assertEquals("16T", storageOperation.text());
        assertEquals(color, storageOperation.color());
        assertEquals(scale, storageOperation.scale(), 0.0F);
        assertEquals(x, storageOperation.x());
        assertEquals(y, storageOperation.y());

        var processorOperation = new VoidOperation();
        COMPACT_PARALLELISM.invoke(new CPUSelectionListMixin() {}, bar, "ae2-processors", color, scale,
                x, y, processorOperation, cpu);
        assertEquals(1, processorOperation.calls);
        assertSame(bar, processorOperation.bar());
        assertEquals("65K", processorOperation.text());
        assertEquals(color, processorOperation.color());
        assertEquals(scale, processorOperation.scale(), 0.0F);
        assertEquals(x, processorOperation.x());
        assertEquals(y, processorOperation.y());

        // Four-character finite labels shrink to at most 0.5; a smaller incoming scale is kept.
        var wideCpu = entry(name, 16L * TIB, 131100);
        var wideOperation = new VoidOperation();
        COMPACT_PARALLELISM.invoke(new CPUSelectionListMixin() {}, bar, "ae2-processors", color, scale,
                x, y, wideOperation, wideCpu);
        assertEquals("131K", wideOperation.text());
        assertEquals(0.5F, wideOperation.scale(), 0.0F);
        assertEquals(color, wideOperation.color());
        assertEquals(x, wideOperation.x());
        assertEquals(y, wideOperation.y());

        var alreadySmallOperation = new VoidOperation();
        COMPACT_PARALLELISM.invoke(new CPUSelectionListMixin() {}, bar, "ae2-processors", color, 0.25F,
                x, y, alreadySmallOperation, wideCpu);
        assertEquals("131K", alreadySmallOperation.text());
        assertEquals(0.25F, alreadySmallOperation.scale(), 0.0F);

        // Formatting must not alter the real CPU row data or its capabilities.
        assertEquals(16L * TIB, cpu.storage());
        assertEquals(65535, cpu.coProcessors());
        assertSame(name, cpu.name());
    }

    @Test
    void ae2TooltipLookupStillUsesTheFullOmniName() throws Exception {
        var list = cpuSelectionListWithoutConstructor();
        var getCpuName = CPUSelectionList.class.getDeclaredMethod("getCpuName",
                CraftingStatusMenu.CraftingCpuListEntry.class);
        getCpuName.setAccessible(true);

        for (String key : List.of(NEXUS, OMNI)) {
            var fullName = Component.translatable(key, "L16");
            var cpu = entry(fullName, 16L * TIB, 65535);
            assertNotSame(fullName, shortenName(cpu, fullName));

            var tooltipName = (Component) getCpuName.invoke(list, cpu);
            assertSame(fullName, tooltipName);
            var contents = assertInstanceOf(TranslatableContents.class, tooltipName.getContents());
            assertEquals(key, contents.getKey());
            assertArrayEquals(new Object[] {"L16"}, contents.getArgs());
        }
    }

    private static CraftingStatusMenu.CraftingCpuListEntry entry(Component name, long storage,
            int coProcessors) {
        return new CraftingStatusMenu.CraftingCpuListEntry(1, storage, coProcessors, name,
                CpuSelectionMode.ANY, null, 0.0F, 0L);
    }

    private static Component shortenName(CraftingStatusMenu.CraftingCpuListEntry cpu,
            Component fullName) throws Exception {
        var operation = new ComponentOperation(fullName);
        var shortened = (Component) COMPACT_NAME.invoke(new CPUSelectionListMixin() {}, null, cpu, operation);
        assertEquals(1, operation.calls, "the name handler must call AE2's getCpuName exactly once");
        assertNull(operation.parameters[0], "the mixin receives the CPUSelectionList instance");
        assertSame(cpu, operation.parameters[1], "the handler must forward the same CPU entry");
        return shortened;
    }

    private static VoidOperation invokeStorage(CraftingStatusMenu.CraftingCpuListEntry cpu,
            String originalText) throws Exception {
        return invokeVoid(COMPACT_STORAGE, cpu, originalText, SCALE);
    }

    private static VoidOperation invokeParallelism(CraftingStatusMenu.CraftingCpuListEntry cpu,
            String originalText) throws Exception {
        return invokeVoid(COMPACT_PARALLELISM, cpu, originalText, SCALE);
    }

    private static VoidOperation invokeParallelism(CraftingStatusMenu.CraftingCpuListEntry cpu,
            String originalText, float expectedScale) throws Exception {
        return invokeVoid(COMPACT_PARALLELISM, cpu, originalText, expectedScale);
    }

    private static VoidOperation invokeVoid(Method handler, CraftingStatusMenu.CraftingCpuListEntry cpu,
            String originalText, float expectedScale) throws Exception {
        var bar = new InfoBar();
        var operation = new VoidOperation();
        handler.invoke(new CPUSelectionListMixin() {}, bar, originalText, COLOR, SCALE, X, Y,
                operation, cpu);
        assertEquals(1, operation.calls, "compact handlers must forward exactly one draw call");
        assertSame(bar, operation.bar(), "the original InfoBar must be forwarded");
        assertEquals(COLOR, operation.color(), "text color must not change");
        assertEquals(expectedScale, operation.scale(), 0.0F, "text scale changed unexpectedly");
        assertEquals(X, operation.x(), "text x must not change");
        assertEquals(Y, operation.y(), "text y must not change");
        return operation;
    }

    private static float expectedParallelismScale(String label) {
        return label.length() == 4 ? 0.5F : SCALE;
    }

    private static Method findMethod(String name, Class<?>... parameterTypes) {
        try {
            var method = CPUSelectionListMixin.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing mixin handler " + name, exception);
        }
    }

    private static CPUSelectionList cpuSelectionListWithoutConstructor() throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        return (CPUSelectionList) unsafe.allocateInstance(CPUSelectionList.class);
    }

    private static final class ComponentOperation implements Operation<Component> {
        private final Component fullName;
        private int calls;
        private Object[] parameters;

        private ComponentOperation(Component fullName) {
            this.fullName = fullName;
        }

        @Override
        public Component call(Object... args) {
            calls++;
            parameters = args;
            return fullName;
        }
    }

    private static final class VoidOperation implements Operation<Void> {
        private int calls;
        private Object[] parameters;

        @Override
        public Void call(Object... args) {
            calls++;
            parameters = args;
            return null;
        }

        private InfoBar bar() {
            return (InfoBar) parameters[0];
        }

        private String text() {
            return (String) parameters[1];
        }

        private int color() {
            return (Integer) parameters[2];
        }

        private float scale() {
            return (Float) parameters[3];
        }

        private int x() {
            return (Integer) parameters[4];
        }

        private int y() {
            return (Integer) parameters[5];
        }
    }
}
