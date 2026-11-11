package smoke;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

/** Invokes the installed, unmodified Sponge Mixin transformer, then asks Java 21 to link its output. */
public final class SmokeMain {
    private static final String SLOT = "mezz.jei.library.gui.ingredients.RecipeSlot";
    private static final String LAYOUT = "mezz.jei.library.gui.recipes.RecipeLayout";
    private static final String SCREEN = "mezz.jei.library.gui.helpers.ScreenHelper";
    private static final String RENDER = "mezz.jei.gui.events.GuiEventHandler";
    private static final String PREFIX = "molecularmanipulator$";

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Files.deleteIfExists(output.resolve("success.txt"));
        System.out.println("JEI_RECIPE_LOCK_SMOKE_RUNTIME=" + args[1]);
        System.out.println("JEI_RECIPE_LOCK_SMOKE_MIXIN=" + MixinBootstrap.class.getProtectionDomain().getCodeSource());
        System.out.println("JEI_RECIPE_LOCK_SMOKE_EXTRAS=" + MixinExtrasBootstrap.class.getProtectionDomain().getCodeSource());
        MixinBootstrap.init();
        var service = (SmokeMixinService) MixinService.getService();
        service.enterDefaultPhase();
        var environment = MixinEnvironment.getCurrentEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("jei-recipe-lock-smoke.mixins.json");
        var transformer = service.createTransformer();
        MixinExtrasBootstrap.init();
        Map<String, byte[]> transformed = new LinkedHashMap<>();
        var report = new StringBuilder("Real Mixin transformation / Java 21 class-link smoke\n");
        report.append("JEI runtime: ").append(args[1]).append('\n');
        for (String name : new String[]{LAYOUT, SLOT, SCREEN, RENDER}) {
            byte[] original;
            try (var input = service.getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (input == null) throw new AssertionError("Missing real JEI class " + name);
                original = input.readAllBytes();
            }
            var originalNode = new ClassNode();
            new ClassReader(original).accept(originalNode, 0);
            boolean optionalOldRender = name.equals(RENDER) && originalNode.methods.stream()
                    .noneMatch(m -> m.name.equals("drawForScreenForeground") || m.name.equals("drawForScreenBackground"));
            byte[] result = transformer.transformClassBytes(name, name, original);
            if (Arrays.equals(original, result) && !optionalOldRender) {
                throw new AssertionError("Class was not transformed: " + name);
            }
            var node = new ClassNode();
            new ClassReader(result).accept(node, 0);
            Path file = output.resolve("transformed").resolve(name.replace('.', '/') + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, result);
            transformed.put(name, result);
            report.append("TRANSFORMED ").append(name).append(' ')
                    .append(original.length).append(" -> ").append(result.length).append(" bytes\n");
            if (name.equals(SLOT)) verifySlot(node, report);
            else if (name.equals(LAYOUT)) verifyLayout(node, report);
            else if (name.equals(SCREEN)) {
                verifyCalls(node, "skipVanillaSlot", 1, report);
                verifyCalls(node, "skipVanillaSlotArea", 1, report);
            } else {
                verifyCalls(node, "screenForeground", optionalOldRender ? 0 : 1, report);
                verifyCalls(node, "screenBackground", optionalOldRender ? 0 : 1, report);
            }
            Files.writeString(output.resolve("transformation.txt"), report.toString());
            System.out.println("TRANSFORMED_AND_CHECKED=" + name);
        }
        var loader = new ClassLoader(SmokeMain.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null && transformed.containsKey(name)) {
                        byte[] bytes = transformed.get(name);
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (loaded == null) loaded = super.loadClass(name, false);
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        for (String name : transformed.keySet()) {
            Class<?> cls = Class.forName(name, false, loader);
            if (cls.getClassLoader() != loader) throw new AssertionError("Loaded original instead of transformed " + name);
            // Reflection forces all method/constructor descriptors to link, with -Xverify:all on the JVM.
            var methods = cls.getDeclaredMethods();
            var constructors = cls.getDeclaredConstructors();
            if (!name.equals(RENDER) && Arrays.stream(methods).noneMatch(m -> m.getName().contains(PREFIX))) {
                throw new AssertionError("No actual merged production methods in loaded class " + name);
            }
            report.append("JVM_LINKED ").append(name).append(" methods=").append(methods.length)
                    .append(" constructors=").append(constructors.length).append('\n');
        }
        report.append("PASS: four real client classes transformed and JVM-linked (old render hooks optional); no game/client/world started.\n");
        Files.writeString(output.resolve("success.txt"), report.toString());
        System.out.print(report);
        System.out.println("JEI_RECIPE_LOCK_SMOKE_SUCCESS");
    }

    private static boolean production(String name) { return name.contains(PREFIX); }
    private static boolean isLockAppender(MethodNode method) {
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invoke
                    && invoke.owner.equals("com/atir/molecularmanipulator/client/RecipeLockTooltips")
                    && invoke.name.equals("appendCurrentRecipe")) return true;
        }
        return false;
    }
    private static void verifySlot(ClassNode node, StringBuilder report) {
        var appenders = node.methods.stream().filter(m -> production(m.name) && isLockAppender(m)).toList();
        if (appenders.isEmpty()) throw new AssertionError("Slot mixin did not merge its real append handler");
        int calls = 0;
        for (var method : node.methods) {
            if (!method.name.equals("getTooltip") && !method.name.equals("addTooltip")) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invoke && invoke.owner.equals(node.name)
                        && appenders.stream().anyMatch(m -> m.name.equals(invoke.name))) {
                    calls++;
                    report.append("SLOT_INJECTED ").append(method.name).append(method.desc)
                            .append(" -> ").append(invoke.name).append('\n');
                }
            }
        }
        if (calls != 1) throw new AssertionError("Expected exactly one actual tooltip injection, got " + calls);
    }
    private static void verifyCalls(ClassNode node, String handler, int expected, StringBuilder report) {
        int calls = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invoke && invoke.owner.equals(node.name)
                        && (invoke.name.endsWith(PREFIX + handler) || invoke.name.contains(PREFIX + handler + "$"))) {
                    calls++;
                    report.append("RESPONSIVE_INJECTED ").append(method.name).append(method.desc)
                            .append(" -> ").append(invoke.name).append('\n');
                }
            }
        }
        if (calls != expected) {
            throw new AssertionError("Expected " + expected + " calls to " + handler + ", got " + calls);
        }
    }
    private static void verifyLayout(ClassNode node, StringBuilder report) {
        int contextCalls = 0, categoryCalls = 0;
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode invoke && invoke.owner.equals(node.name)
                        && production(invoke.name)) {
                    if (invoke.name.contains(PREFIX + "recipeTooltipContext")) contextCalls++;
                    if (invoke.name.contains(PREFIX + "recipeBackgroundTooltip")) categoryCalls++;
                    report.append("LAYOUT_INJECTED ").append(method.name).append(method.desc)
                            .append(" -> ").append(invoke.name).append('\n');
                }
                if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object arg : dynamic.bsmArgs) {
                        if (arg instanceof Handle handle && handle.getOwner().equals(node.name)
                                && production(handle.getName())) {
                            report.append("LAYOUT_OPERATION ").append(method.name).append(" -> ")
                                    .append(handle.getName()).append('\n');
                        }
                    }
                }
            }
        }
        if (contextCalls != 1 || categoryCalls != 1) {
            throw new AssertionError("Expected exactly one real call to each layout wrapper; context="
                    + contextCalls + ", category=" + categoryCalls);
        }
    }
}
