package com.atir.molecularmanipulator.menu;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity.Operation;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu.ActionResult;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Executes the production menu actions, replacing only AE2/player/world/archive infrastructure. */
class PackageReclaimMenuTest {
    private static final String ROOT = "com/atir/molecularmanipulator/";
    private static final Class<?> STAR = action("StarComputationEngineMenu", "StarAction",
            List.of("reclaimPackage", "permitted", "stillValid"));
    private static final Class<?> SINGULARITY = action("SingularityMenu", "SingularityAction",
            List.of("reclaimPackage", "withPlayer", "withAvailablePlayer"));

    @Test void starRequiresTwoServerRequestsFiveToSixtyTicksApartAndAllowsSealToCloseTheMenu() throws Exception {
        var f = new Fixture(STAR);
        f.star();
        assertEquals(0, f.machine.seals);
        assertEquals(100, f.menu.reclaimArmedAt);
        f.world.tick = 104; f.star();
        assertEquals(0, f.machine.seals, "A rapid double click is not confirmation");
        f.world.tick = 105; f.star();
        assertEquals(1, f.machine.seals);
        assertSame(f.player, f.machine.sealedBy);
        assertEquals(Long.MIN_VALUE, f.menu.reclaimArmedAt);
        assertNull(f.player.containerMenu, "The archive operation owns closing this menu");
        assertEquals(2, f.menu.results, "Do not broadcast or revalidate against the removed core after sealing");
    }

    @Test void starTimeoutAndFailedSealBothRequireFreshConfirmation() throws Exception {
        var f = new Fixture(STAR);
        f.star(); f.world.tick = 161; f.star();
        assertEquals(0, f.machine.seals);
        assertEquals(161, f.menu.reclaimArmedAt);
        f.machine.sealSucceeds = false;
        f.world.tick = 166; f.star();
        assertEquals(1, f.machine.seals);
        assertEquals(ActionResult.REJECTED, f.menu.lastResult);
        assertEquals(Long.MIN_VALUE, f.menu.reclaimArmedAt);
        f.world.tick = 167; f.star();
        assertEquals(1, f.machine.seals, "A failed seal does not leave a destructive action armed");
        f.world.tick = 172; f.star();
        assertEquals(2, f.machine.seals);
    }

    @Test void starRevalidatesEveryOwnershipPermissionAndHostGuardOnBothRequests() throws Exception {
        List<Consumer<Fixture>> invalid = List.of(
                f -> f.menu.clientSide = true,
                f -> f.menu.player = new ParentPlayer(),
                f -> f.player.containerMenu = null,
                f -> f.menu.valid = false,
                f -> f.player.mayBuild = false,
                f -> f.player.spectator = true,
                f -> f.machine.removed = true,
                f -> f.player.world = new World(),
                f -> f.machine.world = null,
                f -> f.world.loaded = false,
                f -> f.world.machine = new Machine(),
                f -> f.menu.locatorValid = false,
                f -> f.machine.canInteract = false,
                f -> f.world.mayInteract = false);
        for (var invalidate : invalid) for (boolean second : new boolean[]{false, true}) {
            var f = new Fixture(STAR);
            if (second) { f.star(); f.world.tick += 5; }
            invalidate.accept(f); f.star();
            assertEquals(0, f.machine.seals);
            if (!f.menu.clientSide) assertEquals(Long.MIN_VALUE, f.menu.reclaimArmedAt);
        }
        var unloaded = new Fixture(STAR);
        unloaded.world.loaded = false; unloaded.star();
        assertEquals(0, unloaded.menu.validations, "Reject unloaded hosts before AE2 validation can look them up");
        var remote = new Fixture(STAR);
        remote.menu.locatorValid = false; remote.machine.canInteract = true; remote.star();
        assertEquals(0, remote.machine.seals, "Nearby access or another terminal cannot bypass this menu's locator");
    }

    @Test void singularityKeepsItsDoubleConfirmationAndRejectsCompactOrCompetingNativeOperations() throws Exception {
        var f = new Fixture(SINGULARITY);
        f.singularity(); assertEquals(0, f.machine.reclaims);
        f.world.tick = 104; f.singularity(); assertEquals(0, f.machine.reclaims);
        f.world.tick = 109; f.singularity(); assertEquals(1, f.machine.reclaims);
        assertEquals(Long.MIN_VALUE, f.menu.dismantleArmedAt);
        assertSame(f.player, f.machine.reclaimedBy);
        for (var busy : List.<Consumer<Fixture>>of(
                fixture -> fixture.machine.singleBlock = true,
                fixture -> fixture.machine.operation = Operation.BUILD,
                fixture -> fixture.machine.operation = Operation.DISMANTLE,
                fixture -> fixture.machine.embedRequested = true,
                fixture -> fixture.machine.collectionActive = true)) {
            var blocked = new Fixture(SINGULARITY); busy.accept(blocked);
            var operation = blocked.machine.operation;
            blocked.singularity(); blocked.world.tick += 5; blocked.singularity();
            assertEquals(0, blocked.machine.reclaims);
            assertEquals(operation, blocked.machine.operation, "Historical native operations remain untouched");
        }
        var expired = new Fixture(SINGULARITY);
        expired.singularity(); expired.world.tick += 61; expired.singularity();
        assertEquals(0, expired.machine.reclaims);
    }

    @Test void singularityPendingBlocksCompetingActionsButStillAuthorizesCancelThroughTheOwnedMenu() throws Exception {
        var f = new Fixture(SINGULARITY);
        f.machine.pending = true;
        int[] calls = {0};
        f.with("withAvailablePlayer", player -> calls[0]++);
        assertEquals(0, calls[0]);
        f.with("withPlayer", player -> calls[0]++);
        assertEquals(1, calls[0], "Cancel remains available while docking, under the same ownership gate");
        for (var invalid : List.<Consumer<Fixture>>of(
                fixture -> fixture.menu.clientSide = true,
                fixture -> fixture.menu.player = new ParentPlayer(),
                fixture -> fixture.player.containerMenu = null,
                fixture -> fixture.menu.valid = false,
                fixture -> fixture.menu.locatorValid = false,
                fixture -> fixture.machine.canManage = false)) {
            var blocked = new Fixture(SINGULARITY); invalid.accept(blocked);
            blocked.with("withPlayer", player -> fail("An invalid menu cannot cancel or request reclaim"));
            blocked.singularity(); blocked.world.tick += 5; blocked.singularity();
            assertEquals(0, blocked.machine.reclaims);
        }
    }

    private static final class Fixture {
        final World world = new World();
        final Machine machine = new Machine();
        final Player player = new Player();
        final Menu menu;
        Fixture(Class<?> action) throws Exception {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            menu = (Menu) ((sun.misc.Unsafe) field.get(null)).allocateInstance(action);
            menu.core = menu.machine = machine;
            menu.player = player; player.containerMenu = menu;
            player.world = machine.world = world; world.machine = machine;
            menu.valid = menu.locatorValid = true;
            menu.reclaimArmedAt = menu.dismantleArmedAt = Long.MIN_VALUE;
        }
        void star() throws Exception { method(menu, "reclaimPackage").invoke(menu); }
        void singularity() throws Exception {
            with("withAvailablePlayer", player -> {
                try { method(menu, "reclaimPackage", Player.class).invoke(menu, player); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
        }
        void with(String gate, Consumer<Player> action) throws Exception {
            method(menu, gate, Consumer.class).invoke(menu, action);
        }
    }

    public static class Menu {
        public Machine core, machine;
        public ParentPlayer player;
        public boolean clientSide, valid, locatorValid, reclaimArmed;
        public long reclaimArmedAt, dismantleArmedAt;
        public int results, validations;
        public ActionResult lastResult;
        public boolean isServerSide() { return !clientSide; }
        public boolean isValidMenu() { return valid; }
        public ParentPlayer getPlayer() { return player; }
        public boolean stillValid(ParentPlayer player) { validations++; return locatorValid; }
        public void finish(ActionResult result, int node) { results++; lastResult = result; }
    }
    public static class ParentPlayer {
        public Menu containerMenu;
        public World world;
        public boolean mayBuild = true, spectator;
        public World level() { return world; }
        public boolean mayBuild() { return mayBuild; }
        public boolean isSpectator() { return spectator; }
    }
    public static class Player extends ParentPlayer {}
    public static class Machine {
        public World world;
        public boolean removed, pending, singleBlock, embedRequested, collectionActive;
        public boolean canInteract = true, canManage = true, sealSucceeds = true;
        public Operation operation = Operation.IDLE;
        public int seals, reclaims;
        public Player sealedBy, reclaimedBy;
        public World getLevel() { return world; }
        public BlockPos getBlockPos() { return BlockPos.ZERO; }
        public boolean isRemoved() { return removed; }
        public boolean canInteract(ParentPlayer player) { return canInteract; }
        public boolean canManage(Player player) { return canManage; }
        public boolean packageReclaimRequested() { return pending; }
        public boolean isSingleBlock() { return singleBlock; }
        public boolean embedRequested() { return embedRequested; }
        public boolean collectionActive() { return collectionActive; }
        public Operation operation() { return operation; }
        public void startDismantle(Player player) { reclaims++; reclaimedBy = player; }
    }
    public static class World {
        public long tick = 100;
        public Machine machine;
        public boolean loaded = true, mayInteract = true;
        public long getGameTime() { return tick; }
        public boolean hasChunkAt(BlockPos pos) { return loaded; }
        public Machine getBlockEntity(BlockPos pos) { return machine; }
        public boolean mayInteract(ParentPlayer player, BlockPos pos) { return mayInteract; }
    }
    public static class Transfer {
        public static boolean seal(Player player, Machine core) {
            core.seals++; core.sealedBy = player;
            if (core.sealSucceeds) { player.containerMenu = null; core.removed = true; }
            return core.sealSucceeds;
        }
    }

    private static Method method(Object target, String name, Class<?>... parameters) throws NoSuchMethodException {
        var method = target.getClass().getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }

    private static Class<?> action(String menu, String suffix, List<String> methods) {
        String source = ROOT + "menu/" + menu;
        try (var input = PackageReclaimMenuTest.class.getClassLoader().getResourceAsStream(source + ".class")) {
            assertNotNull(input);
            String target = PackageReclaimMenuTest.class.getName().replace('.', '/') + "$" + suffix;
            var names = Map.ofEntries(
                    Map.entry(source, target),
                    Map.entry(ROOT + "menu/BuildingMenu", name(Menu.class)),
                    Map.entry("appeng/menu/AEBaseMenu", name(Menu.class)),
                    Map.entry("net/minecraft/world/inventory/AbstractContainerMenu", name(Menu.class)),
                    Map.entry(ROOT + "blockentity/StarComputationEngineCoreBlockEntity", name(Machine.class)),
                    Map.entry(ROOT + "blockentity/SingularityBlockEntity", name(Machine.class)),
                    Map.entry("net/minecraft/world/level/block/entity/BlockEntity", name(Machine.class)),
                    Map.entry("net/minecraft/world/entity/player/Player", name(ParentPlayer.class)),
                    Map.entry("net/minecraft/server/level/ServerPlayer", name(Player.class)),
                    Map.entry("net/minecraft/world/level/Level", name(World.class)),
                    Map.entry(ROOT + "building/BuildingTransfer", name(Transfer.class)));
            var type = new ClassNode(); new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG);
            type.name = target; type.superName = name(Menu.class); type.signature = null;
            type.nestHostClass = null; type.nestMembers = null; type.innerClasses.clear(); type.fields.clear();
            type.methods.removeIf(method -> !methods.contains(method.name) && !method.name.startsWith("lambda$withAvailablePlayer$"));
            for (var method : type.methods) {
                method.desc = descriptor(method.desc, names); method.signature = null;
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        call.owner = names.getOrDefault(call.owner, call.owner); call.desc = descriptor(call.desc, names);
                    } else if (instruction instanceof FieldInsnNode field) {
                        field.owner = names.getOrDefault(field.owner, field.owner); field.desc = descriptor(field.desc, names);
                    } else if (instruction instanceof TypeInsnNode cast) cast.desc = names.getOrDefault(cast.desc, cast.desc);
                    else if (instruction instanceof FrameNode frame) { frame(frame.local, names); frame(frame.stack, names); }
                    else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                        dynamic.desc = descriptor(dynamic.desc, names);
                        for (int i = 0; i < dynamic.bsmArgs.length; i++) {
                            if (dynamic.bsmArgs[i] instanceof Type value) dynamic.bsmArgs[i] = Type.getType(descriptor(value.getDescriptor(), names));
                            else if (dynamic.bsmArgs[i] instanceof Handle handle) dynamic.bsmArgs[i] = new Handle(handle.getTag(),
                                    names.getOrDefault(handle.getOwner(), handle.getOwner()), handle.getName(), descriptor(handle.getDesc(), names), handle.isInterface());
                        }
                    }
                }
            }
            var writer = new ClassWriter(0); type.accept(writer);
            return MethodHandles.lookup().defineClass(writer.toByteArray());
        } catch (Exception failure) { throw new AssertionError("Cannot execute production package-reclaim actions", failure); }
    }
    private static String name(Class<?> type) { return type.getName().replace('.', '/'); }
    private static String descriptor(String value, Map<String, String> names) {
        for (var name : names.entrySet()) value = value.replace("L" + name.getKey() + ";", "L" + name.getValue() + ";");
        return value;
    }
    private static void frame(List<Object> values, Map<String, String> names) {
        if (values != null) for (int i = 0; i < values.size(); i++)
            if (values.get(i) instanceof String value) values.set(i, names.getOrDefault(value, value));
    }
}
