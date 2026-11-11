package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.IManagedGridNode;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu.ActionResult;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;

/** Runs the production request/handler bytecode; substitutes only AE2 transport, permissions and the core payment boundary. */
class StarResearchUnlockActionTest {
    private static final Class<?> ACTIONS = actions();
    private static final ResourceLocation SELECTED = ResourceLocation.parse("pack:z_selected");
    private static final ResourceLocation INSERTED = ResourceLocation.parse("pack:a_inserted");

    @BeforeAll
    static void bootstrap() { StarResearchNodeRecipeTest.bootstrap(); }

    @Test
    void integerConvenienceRequestSendsStableStringAndSameManagerReloadCannotPurchaseTheNewSlotOccupant() throws Exception {
        var fixture = new Fixture();
        fixture.replace(SELECTED);
        fixture.menu.clientSide = true;
        fixture.menu.researchNodes = StarResearchMenuNodes.from(StarResearchNodes.catalog(fixture.level), Set.of());
        int oldIndex = fixture.menu.researchNodes.indexOf(SELECTED);
        invoke(fixture.menu, "requestUnlock", int.class, oldIndex);
        assertEquals("entropy_unlock", fixture.menu.action);
        assertEquals(SELECTED.toString(), fixture.menu.payload);
        assertInstanceOf(String.class, fixture.menu.payload);
        fixture.replace(INSERTED, SELECTED);
        var reloaded = StarResearchMenuNodes.from(StarResearchNodes.catalog(fixture.level), Set.of());
        assertEquals(INSERTED, reloaded.node(oldIndex).id(), "A raw old index really would address a different definition");
        assertNotEquals(oldIndex, reloaded.indexOf(SELECTED));
        fixture.menu.clientSide = false;
        fixture.deliver();
        assertEquals(List.of(SELECTED), fixture.core.purchases);
        assertEquals(Set.of(SELECTED), fixture.core.permanent);
        assertEquals(ActionResult.UNLOCKED, fixture.menu.result);
        assertEquals(SELECTED, fixture.menu.resultId, "Feedback follows the paid ID, not the old position");
        assertFalse(fixture.core.permanent.contains(INSERTED));
        assertFalse(fixture.menu.researchNodes.node(oldIndex).unlocked(), "The stale GuiSync snapshot was not trusted or mutated");
    }

    @Test
    void directIdRequestRemovedByReloadFailsClosedInsteadOfPurchasingAnotherNodeAtItsOldIndex() throws Exception {
        var fixture = new Fixture();
        fixture.replace(SELECTED);
        fixture.menu.clientSide = true;
        fixture.menu.researchNodes = StarResearchMenuNodes.from(StarResearchNodes.catalog(fixture.level), Set.of());
        invoke(fixture.menu, "requestUnlock", ResourceLocation.class, SELECTED);
        assertEquals(SELECTED.toString(), fixture.menu.payload);
        fixture.replace(INSERTED);
        fixture.menu.clientSide = false;
        fixture.deliver();
        assertEquals(ActionResult.INVALID_NODE, fixture.menu.result);
        assertEquals(-1, fixture.menu.resultIndex);
        assertTrue(fixture.core.purchases.isEmpty());
        assertTrue(fixture.core.permanent.isEmpty());
    }

    @Test
    void serverRevalidatesPermanentCustomPrerequisitesAndActualLevelAndBalanceRatherThanStaleGuiSync() throws Exception {
        var fixture = new Fixture();
        var parent = ResourceLocation.parse("pack:parent");
        fixture.manager.holders = List.of(node(parent), new RecipeHolder<>(SELECTED,
                new StarResearchNodeRecipe("Paid child", 4, 11, List.of(parent))));
        fixture.menu.researchNodes = StarResearchMenuNodes.from(StarResearchNodes.catalog(fixture.level), Set.of(parent));
        fixture.core.actualLevel = 9;
        fixture.core.balance = 100;
        fixture.receive(SELECTED.toString());
        assertEquals(ActionResult.MISSING_PREREQUISITES, fixture.menu.result);
        assertTrue(fixture.core.purchases.isEmpty(), "Borrowed/stale display prerequisites cannot become permanent grants");
        fixture.core.permanent.add(parent);
        fixture.core.actualLevel = 3;
        fixture.receive(SELECTED.toString());
        assertEquals(ActionResult.INSUFFICIENT_ENTROPY, fixture.menu.result);
        fixture.core.actualLevel = 4;
        fixture.core.balance = 10;
        fixture.receive(SELECTED.toString());
        assertEquals(ActionResult.INSUFFICIENT_INFORMATION, fixture.menu.result);
        fixture.core.balance = 11;
        fixture.receive(SELECTED.toString());
        assertEquals(ActionResult.UNLOCKED, fixture.menu.result);
        assertEquals(List.of(SELECTED), fixture.core.purchases);
        fixture.receive(SELECTED.toString());
        assertEquals(ActionResult.ALREADY_UNLOCKED, fixture.menu.result);
        assertEquals(1, fixture.core.purchases.size(), "A repeated request never reaches payment again");
    }

    @Test
    void defaultMaterialAtIndexZeroPurchasesWithNoFoundationProgressOrInformationAtActualL3() throws Exception {
        var fixture = new Fixture();
        var material = ResourceLocation.parse("molecularmanipulator:material");
        assertEquals(material, fixture.menu.researchNodes.node(0).id());
        fixture.core.actualLevel = 3;
        fixture.core.balance = 0;
        fixture.receive(material.toString());
        assertEquals(ActionResult.UNLOCKED, fixture.menu.result);
        assertEquals(List.of(material), fixture.core.purchases);
        assertEquals(Set.of(material), fixture.core.permanent);
    }

    @Test
    void invalidStringsWrongSideAndUnauthorizedRequestsNeverReachCorePaymentAndBadClientIndicesSendNothing() throws Exception {
        var fixture = new Fixture();
        fixture.replace(SELECTED);
        fixture.menu.clientSide = true;
        for (int index : new int[] {-1, 1, Integer.MAX_VALUE}) invoke(fixture.menu, "requestUnlock", int.class, index);
        invoke(fixture.menu, "requestUnlock", ResourceLocation.class, null);
        assertEquals(0, fixture.menu.sent);
        fixture.receive(SELECTED.toString());
        assertNull(fixture.menu.result, "A server handler invoked on the client is inert");
        fixture.menu.clientSide = false;
        invoke(fixture.menu, "requestUnlock", ResourceLocation.class, SELECTED);
        invoke(fixture.menu, "requestUnlock", int.class, 0);
        assertEquals(0, fixture.menu.sent, "Server-side callers cannot send client actions");
        for (String invalid : new String[] {null, "BAD ID", "", "pack:removed"}) {
            fixture.receive(invalid);
            assertEquals(ActionResult.INVALID_NODE, fixture.menu.result);
        }
        fixture.menu.permitted = false;
        fixture.receive(SELECTED.toString());
        assertEquals(ActionResult.NOT_PERMITTED, fixture.menu.result);
        assertTrue(fixture.core.purchases.isEmpty());
    }

    private static final class Fixture {
        final ReloadableRecipes manager = new ReloadableRecipes();
        final StarResearchTestLevel level;
        final Core core = new Core();
        final Menu menu;
        Fixture() throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            menu = (Menu) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ACTIONS);
            menu.core = core;
            menu.player = new Player();
            menu.permitted = true;
            menu.researchNodes = StarResearchMenuNodes.FALLBACK;
            level = StarResearchTestLevel.create(manager, RegistryAccess.EMPTY);
            core.level = level;
        }
        void replace(ResourceLocation... ids) { manager.holders = java.util.Arrays.stream(ids).<RecipeHolder<?>>map(StarResearchUnlockActionTest::node).toList(); }
        void deliver() throws Exception { receive((String) menu.payload); }
        void receive(String name) throws Exception { invoke(menu, "unlockRecipe", String.class, name); }
    }

    public static class Menu {
        public Core core;
        public Player player;
        public StarResearchMenuNodes researchNodes;
        public int unlockMask, sent, resultIndex;
        public boolean clientSide, permitted;
        public String action;
        public Object payload;
        public ActionResult result;
        public ResourceLocation resultId;
        public boolean isClientSide() { return clientSide; }
        public boolean isServerSide() { return !clientSide; }
        public boolean permitted() { return permitted; }
        public Player getPlayer() { return player; }
        public void sendClientAction(String action, Object payload) { this.action = action; this.payload = payload; sent++; }
        public void finish(ActionResult result, int index) { this.result = result; resultIndex = index; resultId = null; }
        public void finish(ActionResult result, ResourceLocation id) { this.result = result; resultId = id; }
    }

    public static final class Player {}

    public static final class Core {
        public Level level;
        public int actualLevel = 9;
        public long balance = Long.MAX_VALUE;
        public final Set<ResourceLocation> permanent = new HashSet<>();
        public final List<ResourceLocation> purchases = new ArrayList<>();
        public Level getLevel() { return level; }
        public Set<ResourceLocation> getUnlockedResearchIds() { return Set.copyOf(permanent); }
        public boolean isFormed() { return true; }
        public IManagedGridNode getMainNode() {
            return (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(), new Class<?>[] {IManagedGridNode.class},
                    (object, method, args) -> {
                        assertEquals("isActive", method.getName());
                        return true;
                    });
        }
        public StarComputationBenefits getBenefits() { return new StarComputationBenefits(actualLevel, false); }
        public long getInfoBalance() { return balance; }
        public boolean tryUnlockResearch(Player player, ResourceLocation id) { purchases.add(id); return permanent.add(id); }
    }

    private static final class ReloadableRecipes extends RecipeManager {
        List<RecipeHolder<?>> holders = List.of();
        ReloadableRecipes() { super(RegistryAccess.EMPTY); }
        @Override public Collection<RecipeHolder<?>> getRecipes() { return holders; }
    }

    private static RecipeHolder<StarResearchNodeRecipe> node(ResourceLocation id) {
        return new RecipeHolder<>(id, new StarResearchNodeRecipe("Node", 1, 0, List.of()));
    }

    private static void invoke(Menu target, String name, Class<?> parameter, Object argument) throws Exception {
        var method = ACTIONS.getDeclaredMethod(name, parameter);
        method.setAccessible(true);
        method.invoke(target, argument);
    }

    private static Class<?> actions() {
        String source = "com/atir/molecularmanipulator/menu/StarComputationEngineMenu";
        String target = Type.getInternalName(StarResearchUnlockActionTest.class) + "$Actions";
        try (var input = StarResearchUnlockActionTest.class.getClassLoader().getResourceAsStream(source + ".class")) {
            assertNotNull(input);
            var type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG);
            type.superName = Type.getInternalName(Menu.class);
            type.nestHostClass = null;
            type.nestMembers = null;
            type.innerClasses.clear();
            type.fields.clear();
            type.methods.removeIf(method -> !Set.of("requestUnlock", "unlockRecipe", "nodes", "node").contains(method.name)
                    && !(method.name.equals("unlockStatus") && (method.access & Opcodes.ACC_STATIC) != 0));
            var remapping = Map.of(source, target,
                    "com/atir/molecularmanipulator/blockentity/StarComputationEngineCoreBlockEntity", Type.getInternalName(Core.class),
                    "net/minecraft/server/level/ServerPlayer", Type.getInternalName(Player.class),
                    "net/minecraft/world/entity/player/Player", Type.getInternalName(Player.class));
            var output = new ClassWriter(0);
            type.accept(new ClassRemapper(output, new SimpleRemapper(remapping)));
            return MethodHandles.lookup().defineClass(output.toByteArray());
        } catch (Exception error) { throw new AssertionError("Could not isolate production unlock actions", error); }
    }
}
