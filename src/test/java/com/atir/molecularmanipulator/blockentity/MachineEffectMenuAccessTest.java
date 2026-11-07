package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the production action bytecode with only AE2/world/player infrastructure replaced by fixtures. */
class MachineEffectMenuAccessTest {
    private static final String ACCESS = "com/atir/molecularmanipulator/menu/MachineEffectMenuAccess";
    private static final Method APPLY = action();

    @Test void localMenusKeepTheEightBlockLimitAndOnlyPersistRealChanges() throws Exception {
        var f = new Fixture();
        f.player.distanceSquared = 64;
        assertTrue(f.apply(MachineEffectSettings.OFF));
        f.assertState(MachineEffectSettings.OFF, 1);
        assertFalse(f.apply(MachineEffectSettings.OFF));
        f.assertState(MachineEffectSettings.OFF, 1);
        assertTrue(f.apply(MachineEffectSettings.FULL));
        assertTrue(f.apply(MachineEffectSettings.INHERIT));
        f.assertState(MachineEffectSettings.INHERIT, 3);

        var distant = new Fixture();
        distant.player.distanceSquared = 64.01;
        assertFalse(distant.apply(MachineEffectSettings.REDUCED));
        distant.assertState(MachineEffectSettings.INHERIT, 0);
    }

    @Test void aValidBoundTerminalMayChangeADistantControllersEffects() throws Exception {
        var f = new Fixture();
        f.player.distanceSquared = 1_000_000;
        f.menu.locator = new Terminal(new Target(f.world.dimension, f.machine.pos));
        assertTrue(f.apply(MachineEffectSettings.REDUCED));
        f.assertState(MachineEffectSettings.REDUCED, 1);
        assertTrue(f.apply(MachineEffectSettings.INHERIT));
        f.assertState(MachineEffectSettings.INHERIT, 2);
    }

    @Test void invalidTerminalBindingsCannotFallBackToNearbyDistance() throws Exception {
        for (boolean distant : new boolean[]{false, true}) {
            for (var target : List.of(new Target("other_dimension", new BlockPos(1, 2, 3)),
                    new Target("overworld", BlockPos.ZERO), new Target("overworld", new BlockPos(1, 2, 3)))) {
                var f = new Fixture();
                f.player.distanceSquared = distant ? 1_000_000 : 0;
                var terminal = new Terminal(target);
                terminal.valid = !target.dimension().equals(f.world.dimension) || !target.pos().equals(f.machine.pos);
                f.menu.locator = terminal;
                assertFalse(f.apply(MachineEffectSettings.FULL));
                f.assertState(MachineEffectSettings.INHERIT, 0);
            }
        }
    }

    @Test void everyServerMenuAndHostGuardRejectsBeforeDirtyingTheController() throws Exception {
        List<Consumer<Fixture>> invalid = List.of(
                f -> f.menu.clientSide = true,
                f -> f.menu.player = new ParentPlayer(),
                f -> f.player.containerMenu = null,
                f -> f.menu.valid = false,
                f -> f.menu.stillValid = false,
                f -> f.player.spectator = true,
                f -> f.player.mayBuild = false,
                f -> f.machine.world = null,
                f -> f.world.clientSide = true,
                f -> f.machine.removed = true,
                f -> f.player.world = new World(),
                f -> f.world.loaded = false,
                f -> f.world.machine = new Machine(),
                f -> f.menu.machine = new Machine(),
                f -> f.world.mayInteract = false);
        for (var invalidate : invalid) {
            var f = new Fixture();
            invalidate.accept(f);
            assertFalse(f.apply(MachineEffectSettings.FULL));
            f.assertState(MachineEffectSettings.INHERIT, 0);
        }
        for (Integer invalidLevel : new Integer[]{null, Integer.MIN_VALUE, -2, 3, Integer.MAX_VALUE}) {
            var f = new Fixture();
            assertFalse(f.apply(invalidLevel));
            f.assertState(MachineEffectSettings.INHERIT, 0);
        }
        var f = new Fixture();
        assertFalse((boolean) APPLY.invoke(null, f.menu, new Machine(), MachineEffectSettings.OFF));
        f.assertState(MachineEffectSettings.INHERIT, 0);
    }

    @Test void anUnloadedTargetIsRejectedBeforeWorldLookupOrAe2MenuValidation() throws Exception {
        var f = new Fixture();
        f.world.loaded = false;
        f.menu.locator = new Terminal(new Target(f.world.dimension, f.machine.pos));
        assertFalse(f.apply(MachineEffectSettings.FULL));
        assertEquals(0, f.world.lookups);
        assertEquals(0, f.menu.validations);
        f.assertState(MachineEffectSettings.INHERIT, 0);
    }

    private static final class Fixture {
        final World world = new World();
        final EffectMachine machine = new EffectMachine();
        final Player player = new Player();
        final Menu menu = new Menu();
        Fixture() {
            machine.world = player.world = world;
            world.machine = menu.machine = machine;
            menu.player = player;
            player.containerMenu = menu;
        }
        boolean apply(Integer value) throws Exception { return (boolean) APPLY.invoke(null, menu, machine, value); }
        void assertState(int override, int updates) {
            assertEquals(override, machine.settings.getOverride());
            assertEquals(updates, machine.saves);
            assertEquals(updates, machine.watcherUpdates, "Every persisted change also reaches non-menu chunk watchers");
        }
    }

    public static class Menu {
        public ParentPlayer player;
        public Machine machine;
        public Object locator;
        public boolean clientSide;
        public boolean valid = true;
        public boolean stillValid = true;
        public int validations;
        public boolean isClientSide() { return clientSide; }
        public ParentPlayer getPlayer() { return player; }
        public boolean isValidMenu() { return valid; }
        public boolean stillValid(ParentPlayer player) { validations++; return stillValid; }
        public Machine getBlockEntity() { return machine; }
        public Object getLocator() { return locator; }
    }

    public static class Machine {
        public World world;
        public BlockPos pos = new BlockPos(1, 2, 3);
        public boolean removed;
        public int saves;
        public int watcherUpdates;
        public World getLevel() { return world; }
        public BlockPos getBlockPos() { return pos; }
        public boolean isRemoved() { return removed; }
        public void saveChanges() { saves++; }
        public void markForClientUpdate() { watcherUpdates++; }
    }

    public static class EffectMachine extends Machine implements MachineEffectHost {
        final MachineEffectSettings settings = new MachineEffectSettings();
        @Override public MachineEffectSettings getMachineEffects() { return settings; }
    }

    public static class ParentPlayer {
        public Menu containerMenu;
        public World world;
        public boolean spectator;
        public boolean mayBuild = true;
        public double distanceSquared;
        public boolean isSpectator() { return spectator; }
        public boolean mayBuild() { return mayBuild; }
        public World level() { return world; }
        public double distanceToSqr(Vec3 center) { return distanceSquared; }
    }
    public static class Player extends ParentPlayer {}

    public static class World {
        public String dimension = "overworld";
        public Machine machine;
        public boolean loaded = true;
        public boolean mayInteract = true;
        public boolean clientSide;
        public int lookups;
        public boolean isClientSide() { return clientSide; }
        public boolean hasChunkAt(BlockPos pos) { return loaded; }
        public Machine getBlockEntity(BlockPos pos) { lookups++; return machine; }
        public boolean mayInteract(ParentPlayer player, BlockPos pos) { return mayInteract; }
        public String dimension() { return dimension; }
    }

    public record Target(String dimension, BlockPos pos) {}
    public static class Terminal {
        public final Target target;
        public boolean valid = true;
        public Terminal(Target target) { this.target = target; }
        public Target target() { return target; }
        public boolean isValid(ParentPlayer player) { return valid; }
    }

    private static Method action() {
        try (var input = MachineEffectMenuAccessTest.class.getClassLoader().getResourceAsStream(ACCESS + ".class")) {
            assertNotNull(input);
            String fixture = MachineEffectMenuAccessTest.class.getName().replace('.', '/');
            var names = Map.ofEntries(
                    Map.entry(ACCESS, fixture + "$Action"),
                    Map.entry("appeng/menu/AEBaseMenu", Menu.class.getName().replace('.', '/')),
                    Map.entry("net/minecraft/world/inventory/AbstractContainerMenu", Menu.class.getName().replace('.', '/')),
                    Map.entry("appeng/blockentity/AEBaseBlockEntity", Machine.class.getName().replace('.', '/')),
                    Map.entry("net/minecraft/world/level/block/entity/BlockEntity", Machine.class.getName().replace('.', '/')),
                    Map.entry("net/minecraft/world/entity/player/Player", ParentPlayer.class.getName().replace('.', '/')),
                    Map.entry("net/minecraft/server/level/ServerPlayer", Player.class.getName().replace('.', '/')),
                    Map.entry("net/minecraft/world/level/Level", World.class.getName().replace('.', '/')),
                    Map.entry("appeng/menu/locator/MenuHostLocator", "java/lang/Object"),
                    Map.entry("com/atir/molecularmanipulator/terminal/BuildingTerminalLocator", Terminal.class.getName().replace('.', '/')),
                    Map.entry("net/minecraft/core/GlobalPos", Target.class.getName().replace('.', '/')),
                    Map.entry("net/minecraft/resources/ResourceKey", "java/lang/String"));
            var type = new ClassNode();
            new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG);
            type.name = names.get(ACCESS);
            for (var method : type.methods) {
                method.desc = descriptor(method.desc, names);
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        call.owner = names.getOrDefault(call.owner, call.owner);
                        call.desc = descriptor(call.desc, names);
                    } else if (instruction instanceof FieldInsnNode field) {
                        field.owner = names.getOrDefault(field.owner, field.owner);
                        field.desc = descriptor(field.desc, names);
                    } else if (instruction instanceof TypeInsnNode cast) {
                        cast.desc = names.getOrDefault(cast.desc, cast.desc);
                    } else if (instruction instanceof FrameNode frame) {
                        remapFrame(frame.local, names);
                        remapFrame(frame.stack, names);
                    }
                }
            }
            var writer = new ClassWriter(0);
            type.accept(writer);
            return MethodHandles.lookup().defineClass(writer.toByteArray()).getMethod("apply", Menu.class, Machine.class, Integer.class);
        } catch (Exception failure) {
            throw new AssertionError("Cannot execute production effect-menu validation", failure);
        }
    }

    private static String descriptor(String descriptor, Map<String, String> names) {
        for (var name : names.entrySet()) descriptor = descriptor.replace("L" + name.getKey() + ";", "L" + name.getValue() + ";");
        return descriptor;
    }

    private static void remapFrame(List<Object> frame, Map<String, String> names) {
        if (frame == null) return;
        for (int i = 0; i < frame.size(); i++) {
            if (frame.get(i) instanceof String name) frame.set(i, names.getOrDefault(name, name));
        }
    }
}
