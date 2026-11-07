package com.atir.molecularmanipulator.integration.jei;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.block.StarOrreryFoundationBlock;
import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.building.BuildingPackage;
import com.atir.molecularmanipulator.building.BuildingType;
import com.atir.molecularmanipulator.client.UiRenderRecorder;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.starcomputation.StarOrreryFoundation;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.mojang.blaze3d.platform.Window;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IRecipeWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

/** Real package data, AE keys and block states; only JEI's registration boundary is recorded. */
class StarOrreryStructureJeiTest {
    private static final Map<DeferredHolder<?, ?>, Object> PREVIOUS_HOLDERS = new LinkedHashMap<>();
    private static final Map<BuildingType, Object> PREVIOUS_TEMPLATES = new EnumMap<>(BuildingType.class);
    private static final List<Block> FIXTURE_BLOCKS = new ArrayList<>();
    private static Field holderField;
    private static Field templateField;
    private static Field clientField;
    private static Object previousClient;
    private static sun.misc.Unsafe unsafe;
    private static Field distField;
    private static Object previousDist;

    @BeforeAll
    static void bootstrapNativeBlueprintAndPackageFixtures() throws Exception {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        holderField = DeferredHolder.class.getDeclaredField("holder");
        holderField.setAccessible(true);
        bind(ModContent.FOLDED_BUILDING_PACKAGE, Items.BUNDLE);

        // Match the standalone block-state tests: register native block classes without a mod event bus.
        // Vanilla item identities make AE keys usable without registering a second mod item registry.
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            bindBlock("core", ModContent.STAR_COMPUTATION_ENGINE_CORE,
                    new StarComputationEngineCoreBlock(BlockBehaviour.Properties.of()),
                    ModContent.STAR_COMPUTATION_ENGINE_CORE_ITEM, Items.BEACON);
            bindBlock("casing", ModContent.STAR_COMPUTATION_ENGINE_CASING,
                    new StarComputationEngineCasingBlock(BlockBehaviour.Properties.of()),
                    ModContent.STAR_COMPUTATION_ENGINE_CASING_ITEM, Items.IRON_BLOCK);
            bindBlock("observation", ModContent.STAR_COMPUTATION_OBSERVATION_PORT,
                    new StarComputationObservationPortBlock(BlockBehaviour.Properties.of()),
                    ModContent.STAR_COMPUTATION_OBSERVATION_PORT_ITEM, Items.GLASS);
            bindBlock("engraved", ModContent.STAR_ORRERY_ENGRAVED_PLINTH,
                    new StarOrreryFoundationBlock(BlockBehaviour.Properties.of()),
                    ModContent.STAR_ORRERY_ENGRAVED_PLINTH_ITEM, Items.CHISELED_QUARTZ_BLOCK);
            bindBlock("gilded", ModContent.STAR_ORRERY_GILDED_PLINTH,
                    new StarOrreryFoundationBlock(BlockBehaviour.Properties.of()),
                    ModContent.STAR_ORRERY_GILDED_PLINTH_ITEM, Items.GOLD_BLOCK);
            bindBlock("reference", ModContent.REFERENCE_STAR_POSITION_ANCHOR,
                    new StarPositionAnchorBlock(BlockBehaviour.Properties.of()),
                    ModContent.REFERENCE_STAR_POSITION_ANCHOR_ITEM, Items.AMETHYST_BLOCK);
            bindBlock("quantum", ModContent.QUANTUM_STAR_POSITION_ANCHOR,
                    new StarPositionAnchorBlock(BlockBehaviour.Properties.of()),
                    ModContent.QUANTUM_STAR_POSITION_ANCHOR_ITEM, Items.DIAMOND_BLOCK);
            bindBlock("terminal", ModContent.TERMINAL_STAR_POSITION_ANCHOR,
                    new StarPositionAnchorBlock(BlockBehaviour.Properties.of()),
                    ModContent.TERMINAL_STAR_POSITION_ANCHOR_ITEM, Items.EMERALD_BLOCK);
        } finally {
            registry.freeze();
        }
        for (var block : FIXTURE_BLOCKS) block.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        templateField = BuildingType.class.getDeclaredField("template");
        templateField.setAccessible(true);
        for (var type : BuildingType.orderable()) if (type.isStarOrrery()) {
            PREVIOUS_TEMPLATES.put(type, templateField.get(type));
            templateField.set(type, null);
        }

        // Allocate a native, headless client/window like UiRenderRecorder, never starting Minecraft or GL.
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        // Native tooltip hooks consult the runtime distribution even without a running game.
        distField = net.neoforged.fml.loading.FMLEnvironment.class.getDeclaredField("dist");
        previousDist = unsafe.getObject(unsafe.staticFieldBase(distField), unsafe.staticFieldOffset(distField));
        unsafe.putObject(unsafe.staticFieldBase(distField), unsafe.staticFieldOffset(distField),
                net.neoforged.api.distmarker.Dist.DEDICATED_SERVER);
        var client = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        var window = (Window) unsafe.allocateInstance(Window.class);
        setField(Window.class, window, "guiScaledHeight", 360);
        setField(Minecraft.class, client, "window", window);
        clientField = Minecraft.class.getDeclaredField("instance");
        clientField.setAccessible(true);
        previousClient = clientField.get(null);
        clientField.set(null, client);
        setField(Minecraft.class, client, "font", new UiRenderRecorder.MetricsFont());
    }

    @AfterAll
    static void restoreStandaloneIdentitiesAndCaches() throws Exception {
        for (var entry : PREVIOUS_TEMPLATES.entrySet()) templateField.set(entry.getKey(), entry.getValue());
        for (var entry : PREVIOUS_HOLDERS.entrySet()) holderField.set(entry.getKey(), entry.getValue());
        FIXTURE_BLOCKS.forEach(Item.BY_BLOCK::remove);
        if (clientField != null) clientField.set(null, previousClient);
        if (distField != null) unsafe.putObject(unsafe.staticFieldBase(distField), unsafe.staticFieldOffset(distField), previousDist);
    }

    @Test
    void categoryHasExactlyTheFirstOneTwoAndThreeRingPackagesAndNoLegacyRecipe() {
        var recipes = StarOrreryStructureJeiRecipe.recipes();
        assertEquals(List.of(BuildingType.STAR_ORRERY_L1, BuildingType.STAR_ORRERY_L2, BuildingType.STAR_ORRERY_L3),
                recipes.stream().map(StarOrreryStructureJeiRecipe::type).toList());
        assertEquals(List.of(1, 2, 3), recipes.stream().map(recipe -> recipe.type().orreryLevel()).toList());
        assertThrows(IllegalArgumentException.class, () -> new StarOrreryStructureJeiRecipe(BuildingType.FABRICATION_WELL));
        assertThrows(IllegalArgumentException.class, () -> new StarOrreryStructureJeiRecipe(BuildingType.STAR_COMPUTATION_ENGINE));
        for (var recipe : recipes) {
            var output = recipe.output();
            assertTrue(output.is(ModContent.FOLDED_BUILDING_PACKAGE.get()));
            assertEquals(1, output.getCount());
            assertSame(recipe.type(), BuildingPackage.type(output));
            assertNull(BuildingPackage.archive(output));
            assertNull(BuildingPackage.reservation(output));
            assertEquals(Set.of("type", "template"), BuildingPackage.data(output).getAllKeys());
            BuildingPackage.reserve(output, UUID.randomUUID());
            assertNull(BuildingPackage.reservation(recipe.output()), "JEI must not retain mutable deployment receipts");
        }
    }

    @Test
    void materialsAndPreviewIncludeTheCentralBodyAnd928FoundationBlocksButNoLegacyColumns() {
        for (var recipe : StarOrreryStructureJeiRecipe.recipes()) {
            var type = recipe.type();
            var materials = amounts(recipe.materials());
            assertEquals(type.materials(), materials);
            assertEquals(520L, materials.get(AEItemKey.of(Items.CHISELED_QUARTZ_BLOCK)).longValue());
            assertEquals(408L, materials.get(AEItemKey.of(Items.GOLD_BLOCK)).longValue());
            assertEquals(1L, materials.get(AEItemKey.of(Items.BEACON)).longValue());
            assertEquals(22L, materials.get(AEItemKey.of(Items.IRON_BLOCK)).longValue());
            assertEquals(4L, materials.get(AEItemKey.of(Items.GLASS)).longValue());
            int anchors = 0;
            for (int ring = 0; ring < type.orreryLevel(); ring++) anchors += StarOrreryLayout.slotCount(ring);
            assertEquals((long) anchors, materials.get(AEItemKey.of(Items.AMETHYST_BLOCK)).longValue());
            assertFalse(materials.containsKey(AEItemKey.of(Items.DIAMOND_BLOCK)));
            assertFalse(materials.containsKey(AEItemKey.of(Items.EMERALD_BLOCK)));
            assertEquals(27L + 928 + anchors, materials.values().stream().mapToLong(Long::longValue).sum());

            var expected = new HashMap<BlockPos, BlockState>();
            type.template().stream().filter(entry -> !entry.state().isAir())
                    .forEach(entry -> assertNull(expected.put(entry.offset(), entry.state())));
            var actual = new HashMap<BlockPos, BlockState>();
            for (var part : StarOrreryStructurePreviewWidget.renderParts(type)) {
                assertNull(actual.put(part.position(), part.state()));
                assertNotEquals(RenderShape.INVISIBLE, part.state().getRenderShape());
                if (part.state().hasProperty(StarComputationEngineCoreBlock.FORMED)) {
                    assertFalse(part.state().getValue(StarComputationEngineCoreBlock.FORMED));
                }
            }
            assertEquals(expected, actual, "Overview and layers must not replace the package blueprint");
            assertEquals(27 + 928 + anchors, actual.size(), "The central body is included, but retired columns are not");
            assertEquals(1L, actual.values().stream().filter(state -> state.is(ModContent.STAR_COMPUTATION_ENGINE_CORE.get())).count());
            assertEquals(22L, actual.values().stream().filter(state -> state.is(ModContent.STAR_COMPUTATION_ENGINE_CASING.get())).count());
            assertEquals(4L, actual.values().stream().filter(state -> state.is(ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get())).count());
            for (var pos : BlockPos.betweenClosed(new BlockPos(-1,-1,-1), new BlockPos(1,1,1))) {
                var state = actual.get(pos);
                assertNotNull(state, type + " central " + pos);
                Block expectedBlock = pos.equals(BlockPos.ZERO) ? ModContent.STAR_COMPUTATION_ENGINE_CORE.get()
                        : pos.getY() == 0 && Math.abs(pos.getX()) + Math.abs(pos.getZ()) == 1
                        ? ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get() : ModContent.STAR_COMPUTATION_ENGINE_CASING.get();
                assertSame(expectedBlock, state.getBlock(), type + " central " + pos);
                assertEquals(Direction.NORTH, state.getValue(HorizontalDirectionalBlock.FACING));
                assertFalse(state.getValue(StarComputationEngineCoreBlock.FORMED));
                if (pos.equals(BlockPos.ZERO)) {
                    assertTrue(state.getValue(StarComputationEngineCoreBlock.STAR_ORRERY));
                } else {
                    assertFalse(state.getValue(StarComputationEngineCasingBlock.INVISIBLE));
                }
            }
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (int distance = 2; distance <= 5; distance++) for (int index = 0; index < 9; index++) {
                    var pos = BlockPos.ZERO.relative(facing, distance)
                            .relative(facing.getCounterClockWise(), index % 3 - 1).above(1 - index / 3);
                    assertFalse(actual.containsKey(pos), "Retired anchor columns stay out of the package: " + pos);
                }
            }
            assertEquals(928, StarOrreryFoundation.members().size());
            for (var member : StarOrreryFoundation.members()) {
                assertInstanceOf(StarOrreryFoundationBlock.class, actual.get(member.offset()).getBlock());
            }
            for (int ring = 0; ring < StarOrreryLayout.RINGS; ring++) {
                for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                    var pos = StarOrreryLayout.offset(Direction.NORTH, ring, slot);
                    assertEquals(ring < type.orreryLevel(), actual.containsKey(pos), type + " ring=" + ring);
                }
            }
            var preview = new StarOrreryStructurePreviewWidget(type, 168);
            int minY = actual.keySet().stream().mapToInt(BlockPos::getY).min().orElseThrow();
            int maxY = actual.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow();
            assertTrue(minY < 0, "The foundation is below the core, not a flattened floor");
            preview.mouseClicked(10, 10, 0);
            preview.mouseReleased(10, 10, 0);
            assertEquals(maxY - minY, preview.layer, "Layer controls must include the negative-Y foundation");
        }
    }

    @Test
    void nativeCategorySlotsKeepFullQuantitiesAndOutputTheCorrespondingPackage() {
        var category = category();
        assertSame(StarOrreryStructureJeiCategory.TYPE, category.getRecipeType());
        assertEquals(InteractiveStructurePreviewWidget.WIDTH, category.getWidth());
        assertEquals(StructureJeiLayout.height(), category.getHeight());
        assertFalse(category.needsRecipeBorder());
        for (var recipe : StarOrreryStructureJeiRecipe.recipes()) {
            var slots = new ArrayList<Slot>();
            var builder = proxy(IRecipeLayoutBuilder.class, (self, method, args) -> {
                boolean output = method.getName().equals("addOutputSlot");
                assertTrue(output || method.getName().equals("addInputSlot"));
                var slot = new Slot(output, (int) args[0], (int) args[1]);
                slots.add(slot);
                return slot.builder();
            });
            category.setRecipe(builder, recipe, null);
            var inputs = slots.stream().filter(slot -> !slot.output).toList();
            assertEquals(recipe.type().materials(), amounts(inputs.stream().map(slot -> slot.stack).toList()));
            for (int i = 0; i < inputs.size(); i++) {
                assertSame(StructureMaterialRenderer.INSTANCE, inputs.get(i).renderer);
                assertEquals(StructureJeiLayout.materialX(i), inputs.get(i).x);
                assertEquals(StructureJeiLayout.materialY(i), inputs.get(i).y);
            }
            assertEquals(1, slots.stream().filter(slot -> slot.output).count());
            var output = slots.stream().filter(slot -> slot.output).findFirst().orElseThrow();
            assertSame(recipe.type(), BuildingPackage.type(output.stack));
            assertNull(BuildingPackage.archive(output.stack));
            assertEquals(219, output.x);
        }
    }

    @Test
    void categoryRegistersTheSamePerRecipeWidgetAndEventListenerWithWorkingCameraAndLayers() {
        var category = category();
        var previews = new ArrayList<StarOrreryStructurePreviewWidget>();
        for (var recipe : StarOrreryStructureJeiRecipe.recipes()) {
            var widgets = new ArrayList<IRecipeWidget>();
            var listeners = new ArrayList<IJeiGuiEventListener>();
            var extras = proxy(IRecipeExtrasBuilder.class, (self, method, args) -> {
                switch (method.getName()) {
                    case "addWidget" -> widgets.add((IRecipeWidget) args[0]);
                    case "addGuiEventListener" -> listeners.add((IJeiGuiEventListener) args[0]);
                    default -> fail("Unexpected extras method: " + method.getName());
                }
                return null;
            });
            category.createRecipeExtras(extras, recipe, null);
            assertEquals(1, widgets.size());
            assertEquals(1, listeners.size());
            assertSame(widgets.getFirst(), listeners.getFirst());
            var preview = assertInstanceOf(StarOrreryStructurePreviewWidget.class, widgets.getFirst());
            assertTrue(previews.stream().noneMatch(previous -> previous == preview));
            previews.add(preview);
            assertTrue(preview.mouseClicked(80, 60, 0));
            assertTrue(preview.mouseDragged(90, 70, 0, 10, 10));
            assertTrue(preview.mouseReleased(90, 70, 0));
            assertNotEquals(45, preview.camera.yaw);
            assertTrue(preview.mouseScrolled(90, 70, 0, 1));
            assertTrue(preview.camera.zoom > 1);
            assertTrue(preview.keyPressed(90, 70, GLFW.GLFW_KEY_PAGE_DOWN, 0, 0));
            assertEquals(0, preview.layer);
            assertTrue(preview.keyPressed(90, 70, GLFW.GLFW_KEY_HOME, 0, 0));
            assertEquals(45, preview.camera.yaw);
            assertEquals(1, preview.camera.zoom);
            assertFalse(preview.mouseClicked(12, StructureJeiLayout.previewHeight() + 20, 0));
        }
    }

    @Test
    void materialRendererWrites520And408OnceWithoutInventoryStackClamping() {
        var graphics = new UiRenderRecorder();
        for (var stack : List.of(new ItemStack(Items.CHISELED_QUARTZ_BLOCK, 520), new ItemStack(Items.GOLD_BLOCK, 408))) {
            StructureMaterialRenderer.INSTANCE.render(graphics, stack);
            var lines = StructureMaterialRenderer.INSTANCE.getTooltip(stack, TooltipFlag.NORMAL);
            var count = assertInstanceOf(TranslatableContents.class, lines.getLast().getContents());
            assertEquals("gui.molecularmanipulator.jei_required_count", count.getKey());
            assertEquals(stack.getCount(), count.getArgs()[0]);
        }
        assertEquals(List.of("520", "408"), graphics.text.stream().map(UiRenderRecorder.Text::value).toList());
        assertEquals(List.of(520, 408), graphics.items.stream().map(item -> item.stack().getCount()).toList());
        assertTrue(graphics.text.stream().noneMatch(UiRenderRecorder.Text::shadow));
    }

    @Test
    void packageTitleOverloadKeepsTheExistingMaterialPanelGeometry() {
        var original = new UiRenderRecorder();
        var packages = new UiRenderRecorder();
        StructureJeiLayout.drawMaterials(original);
        StructureJeiLayout.drawMaterials(packages, Component.literal("Package"));
        assertEquals(original.rects, packages.rects);
        assertTrue(packages.text.stream().anyMatch(text -> text.value().equals("Package")));
    }

    @Test
    void pluginRegistersModernTypeOnlySubtypesIgnoringArchivesReceiptsAndSummaries() {
        var interpreters = new ArrayList<ISubtypeInterpreter<ItemStack>>();
        var registration = proxy(ISubtypeRegistration.class, (self, method, args) -> {
            assertEquals("registerSubtypeInterpreter", method.getName());
            assertEquals(3, args.length);
            assertSame(VanillaTypes.ITEM_STACK, args[0]);
            assertSame(ModContent.FOLDED_BUILDING_PACKAGE.get(), args[1]);
            @SuppressWarnings("unchecked") var interpreter = (ISubtypeInterpreter<ItemStack>) args[2];
            interpreters.add(interpreter);
            return null;
        });
        new MolecularCenterJeiPlugin().registerItemSubtypes(registration);
        assertEquals(1, interpreters.size());
        var interpreter = interpreters.getFirst();
        var identities = new HashSet<>();
        for (var type : BuildingType.orderable()) {
            var fresh = BuildingPackage.fresh(type);
            var sealed = BuildingPackage.sealed(type, UUID.randomUUID(), Integer.MAX_VALUE);
            BuildingPackage.reserve(sealed, UUID.randomUUID());
            CustomData.update(DataComponents.CUSTOM_DATA, sealed,
                    tag -> tag.getCompound("folded_building").putInt("template", 999));
            sealed.set(DataComponents.CUSTOM_NAME, Component.literal("An individual archived machine"));
            for (var context : UidContext.values()) {
                assertEquals(type.id, interpreter.getSubtypeData(fresh, context));
                assertEquals(interpreter.getSubtypeData(fresh, context), interpreter.getSubtypeData(sealed, context));
                assertEquals(type.id, interpreter.getLegacyStringSubtypeInfo(sealed, context));
            }
            identities.add(interpreter.getSubtypeData(fresh, UidContext.Recipe));
        }
        assertEquals(BuildingType.orderable().size(), identities.size());
        var invalid = new ItemStack(ModContent.FOLDED_BUILDING_PACKAGE.get());
        CustomData.update(DataComponents.CUSTOM_DATA, invalid, tag -> {
            var building = new net.minecraft.nbt.CompoundTag();
            building.putString("type", "unknown_building");
            tag.put("folded_building", building);
        });
        for (var stack : List.of(invalid, new ItemStack(Items.APPLE), new ItemStack(ModContent.FOLDED_BUILDING_PACKAGE.get()))) {
            assertNull(interpreter.getSubtypeData(stack, UidContext.Recipe));
            assertEquals("", interpreter.getLegacyStringSubtypeInfo(stack, UidContext.Ingredient));
        }
    }

    private static Map<AEItemKey, Long> amounts(List<ItemStack> stacks) {
        var result = new LinkedHashMap<AEItemKey, Long>();
        for (var stack : stacks) result.merge(AEItemKey.of(stack), (long) stack.getCount(), Math::addExact);
        return result;
    }

    private static StarOrreryStructureJeiCategory category() {
        var icon = proxy(IDrawable.class, (self, method, args) -> null);
        return new StarOrreryStructureJeiCategory(proxy(IGuiHelper.class, (self, method, args) -> {
            assertEquals("createDrawableItemStack", method.getName());
            return icon;
        }));
    }

    private static void bind(DeferredHolder<?, ?> deferred, Object value) throws IllegalAccessException {
        PREVIOUS_HOLDERS.put(deferred, holderField.get(deferred));
        holderField.set(deferred, Holder.direct(value));
    }

    private static void bindBlock(String name, DeferredHolder<?, ?> deferred, Block block,
            DeferredHolder<?, ?> itemDeferred, Item item) throws IllegalAccessException {
        Registry.register(BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath("star_orrery_jei_test", name), block);
        FIXTURE_BLOCKS.add(block);
        Item.BY_BLOCK.put(block, item);
        bind(deferred, block);
        bind(itemDeferred, item);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(StarOrreryStructureJeiTest.class.getClassLoader(), new Class<?>[] {type}, handler));
    }

    private static final class Slot {
        final boolean output;
        final int x, y;
        ItemStack stack;
        Object renderer;

        Slot(boolean output, int x, int y) {
            this.output = output;
            this.x = x;
            this.y = y;
        }

        IRecipeSlotBuilder builder() {
            return proxy(IRecipeSlotBuilder.class, (self, method, args) -> {
                switch (method.getName()) {
                    case "addItemStack" -> stack = (ItemStack) args[0];
                    case "setCustomRenderer" -> {
                        assertSame(VanillaTypes.ITEM_STACK, args[0]);
                        renderer = args[1];
                    }
                    case "setStandardSlotBackground", "setOutputSlotBackground" -> { }
                    default -> fail("Unexpected slot method: " + method.getName());
                }
                return self;
            });
        }
    }
}
