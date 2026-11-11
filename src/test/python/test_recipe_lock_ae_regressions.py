"""Isolated Java compilation and executable regressions for the AE-family adapters.

Run: python src/test/python/test_recipe_lock_ae_regressions.py -v
No Gradle task, Mixin transformation, Minecraft bootstrap or GameTest is run.

The JDK 21 check compiles all actual adapter sources against the exact dependency JARs
and API stubs generated from the current RecipeLocks/PatternRecipeReloads signatures;
RecipeExecutionContext is compiled from its actual source. AdvancedAE is deliberately
absent from that classpath. Set RECIPE_LOCK_JAVA_HOME and/or RECIPE_LOCK_AE_CLASSPATH
if the local JDK/development classpath cannot be discovered. Missing inputs are skips.

The JDK 17+ executable check extracts the actual changed handlers and core raw-Recipe
identity guard/playerGrid methods. Small inventory/lookup/network stand-ins exercise
unchanged-input reloads and live terminal binding. The separate bytecode contracts tie
the modeled null-cache branch to the real JARs; these are NOT in-game integration tests.
"""

import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

from test_recipe_lock_ae_adapters import (AAE, JARS, MIXINS, ROOT, TARGETS,
                                         ClassContract, cached_jar, source)


MAIN = ROOT / "src/main/java/com/atir/molecularmanipulator"
CORE = MAIN / "recipelock/RecipeLocks.java"
CONTEXT = MAIN / "recipelock/RecipeExecutionContext.java"
RELOADS = MAIN / "crafting/PatternRecipeReloads.java"


def method(text, name, signature=None):
    """Extract a real declaration/body, keeping strings/comments out of brace counting."""
    masked = re.sub(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/',
                    lambda m: re.sub(r"[^\n]", " ", m.group()), text, flags=re.S)
    declarations = re.finditer(
        r"(?m)^ {4}(?:@\w+\s+)*(?:(?:public|private|protected|static|final|synchronized) +)*"
        r"[A-Za-z_$][\w.$<>?, \[\]]* +" + re.escape(name) + r"\s*\(", masked)
    found = []
    for declaration in declarations:
        opening = masked.index("{", declaration.end())
        if signature and signature not in text[declaration.start():opening]:
            continue
        position, depth = opening + 1, 1
        while depth:
            depth += (masked[position] == "{") - (masked[position] == "}")
            position += 1
        found.append(text[declaration.start():position])
    if len(found) != 1:
        raise AssertionError((name, signature, len(found)))
    return found[0]


def java_tools(minimum):
    candidates = []
    for env in ("RECIPE_LOCK_JAVA_HOME", "JAVA_HOME"):
        if os.environ.get(env):
            candidates.append(Path(os.environ[env]) / "bin/javac")
    if shutil.which("javac"):
        candidates.append(Path(shutil.which("javac")))
    if os.name == "nt":
        candidates.append(Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Java/jdk-21/bin/javac.exe")
    for candidate in candidates:
        executable = shutil.which(str(candidate))
        if executable:
            version = subprocess.run([executable, "-version"], capture_output=True, text=True, timeout=15)
            major = re.search(r"javac (\d+)", version.stdout + version.stderr)
            if major and int(major.group(1)) >= minimum:
                java = Path(executable).with_name("java.exe" if os.name == "nt" else "java")
                if java.is_file():
                    return executable, str(java)
    return None


class ActualAdapterCompilationTests(unittest.TestCase):
    def test_compile_all_adapters_with_real_ae_eae_glodium_but_without_optional_advancedae(self):
        tools = java_tools(21)
        if tools is None:
            self.skipTest("JDK 21 required for actual Minecraft/AE dependency bytecode")
        missing = [key for key in ("ae2", "extendedae", "glodium") if JARS[key] is None]
        if missing:
            self.skipTest("Missing exact dependency artifacts: " + ", ".join(missing))
        explicit_cp = os.environ.get("RECIPE_LOCK_AE_CLASSPATH")
        legacy = ROOT / "build/moddev/clientLegacyClasspath.txt"
        artifact = ROOT / "build/moddev/artifacts/neoforge-21.1.220.jar"
        extras = cached_jar("io.github.llamalad7", "mixinextras-neoforge", "0.5.0")
        annotations = cached_jar("org.jetbrains", "annotations", "24.1.0")
        if explicit_cp:
            entries = explicit_cp.split(os.pathsep)
        else:
            if not legacy.is_file() or not artifact.is_file() or not extras or not annotations:
                self.skipTest("Development artifact/runtime classpath/MixinExtras/annotations unavailable")
            entries = [str(artifact), str(extras), str(annotations)] + legacy.read_text().splitlines()
        # No project build/classes dependency: a concurrent main build cannot change these API checks.
        entries = [entry for entry in entries if entry and Path(entry).is_file()]
        entries += [str(JARS[key]) for key in ("ae2", "extendedae", "glodium")]
        for entry in entries:
            self.assertNotIn("advancedae", Path(entry).name.lower())
        core_text = CORE.read_text(encoding="utf-8")
        signatures = re.findall(r"(?m)^    public static [^{;]+\{", core_text)
        self.assertGreaterEqual(len(signatures), 9)
        records = re.findall(r"(?m)^    public record [^{]+\{", core_text)
        api = """package com.atir.molecularmanipulator.recipelock;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
public final class RecipeLocks {
""" + "\n".join(header + " }" for header in records) + "\n" + "\n".join(
            header + " throw new AssertionError(); }" for header in signatures) + "\n}\n"
        generation = method(RELOADS.read_text(encoding="utf-8"), "generation").split("{", 1)[0]
        reload_api = ("package com.atir.molecularmanipulator.crafting;\npublic final class PatternRecipeReloads {\n"
                      + generation + "{ return 0L; }\n}\n")
        with tempfile.TemporaryDirectory(prefix="recipe-lock-actual-ae-javac-") as directory:
            base = Path(directory)
            stub = base / "api/com/atir/molecularmanipulator/recipelock/RecipeLocks.java"
            stub.parent.mkdir(parents=True)
            stub.write_text(api, encoding="utf-8")
            reload_stub = base / "api/com/atir/molecularmanipulator/crafting/PatternRecipeReloads.java"
            reload_stub.parent.mkdir(parents=True)
            reload_stub.write_text(reload_api, encoding="utf-8")
            classes = base / "classes"
            args = ["--release", "21", "-encoding", "UTF-8", "-proc:none", "-sourcepath", "",
                    "-classpath", os.pathsep.join(entries), "-d", str(classes), str(stub), str(reload_stub), str(CONTEXT)]
            args += [str(MIXINS / (name + ".java")) for name in TARGETS]
            argfile = base / "compile.args"
            argfile.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in args), encoding="utf-8")
            compiled = subprocess.run([tools[0], "@" + str(argfile)], capture_output=True, text=True, timeout=90)
            self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
            output = classes / "com/atir/molecularmanipulator/mixin"
            self.assertEqual(set(TARGETS), {file.stem for file in output.glob("*.class")})
            for name in TARGETS:
                if name.startswith("RecipeLockAdvanced"):
                    contract = ClassContract((output / (name + ".class")).read_bytes())
                    referenced_classes = [contract.utf(value) for entry in contract.cp[1:] if entry
                                          for tag, value in [entry] if tag == 7]
                    self.assertFalse(any(value.startswith(AAE) for value in referenced_classes), name)
                    descriptors = [desc for _, desc in (*contract.fields, *contract.methods)]
                    self.assertFalse(any(AAE in desc for desc in descriptors), name)


HARNESS_PREFIX = r"""
import java.util.*;
import java.util.function.Supplier;
public class AECacheHarness {
    @interface Nullable {}
    @interface Coerce {}
    interface Operation<T> { T call(Object... arguments); }
    interface Recipe<T> {}
    static class InscriberRecipe implements Recipe<Object> {}
    record ResourceLocation(String id) {}
    static class MatterFabricationRecipe implements Recipe<Object> {
        ResourceLocation sourceRecipeId() { return null; }
    }
    static class Level {
        boolean isClientSide, rules = true, grant = true, throwLookup;
        int lookups;
        Recipe<?> nextRecipe;
        final Map<Recipe<?>, ResourceLocation> ids = new IdentityHashMap<>();
        Recipe<?> lookup() {
            lookups++;
            if (throwLookup) throw new IllegalStateException("query failure");
            return nextRecipe;
        }
    }
    static class BlockEntity {
        Level level = new Level();
        int inputs = 4, outputs = 2, progress = 113, energy = 700;
        boolean smash = true;
        Level getLevel() { return level; }
    }
    static class InscriberBlockEntity extends BlockEntity {}
    static class TileExInscriber extends BlockEntity {}
    static class Player {}
    static class ServerPlayer extends Player { Object containerMenu; }
    static class IGrid {}
    static class IGridNode {
        boolean active;
        IGrid grid;
        boolean isActive() { return active; }
        IGrid getGrid() { return grid; }
    }
    static class ActionHost {
        IGridNode node;
        IGridNode getActionableNode() { return node; }
    }
    static class ActionSource {
        ActionHost host;
        Optional<ActionHost> machine() { return Optional.ofNullable(host); }
    }
    static class CraftingTermMenu {
        final ActionSource source = new ActionSource();
        ActionSource getActionSource() { return source; }
    }
    static class WirelessCraftingTermMenu extends CraftingTermMenu {}
    static class PatternRecipeReloads {
        static long current;
        static long generation() { return current; }
    }
    static class CallbackInfoReturnable<T> {
        T value;
        CallbackInfoReturnable(T value) { this.value = value; }
        T getReturnValue() { return value; }
        void setReturnValue(T value) { this.value = value; }
    }
    static class RecipeLockIndex {
        final Level level;
        RecipeLockIndex(Level level) { this.level = level; }
        ResourceLocation idOf(Recipe<?> recipe) { return level.ids.get(recipe); }
    }
    static class RecipeLocks {
        // The extracted core guard also recognizes generated name-press tasks. This cache harness
        // never creates one, but keeps those API fields available while exercising ordinary recipes.
        static final ResourceLocation NAME_PRESS_RECIPE = new ResourceLocation("ae2:inscriber/name_press");
        static final Set<Recipe<?>> NAME_PRESS_TASKS = Collections.newSetFromMap(new IdentityHashMap<>());
        static RecipeLockIndex index(Level level) { return new RecipeLockIndex(level); }
        static boolean hasRules(Level level) { return !level.isClientSide && level.rules; }
        static boolean allowed(BlockEntity machine, ResourceLocation id) { return !machine.level.rules || machine.level.grant; }
        static boolean sourceAllowed(BlockEntity machine, Recipe<?> recipe) { return true; }
"""

HARNESS_ADAPTERS = r"""
    static class AEAdapter extends InscriberBlockEntity {
        private InscriberRecipe cachedTask;
        private long recipeLock$recipeGeneration = Long.MIN_VALUE;
        InscriberRecipe task() {
            return recipeLock$checkQueryAndCache(args -> {
                if (cachedTask == null && getLevel() != null) cachedTask = (InscriberRecipe) level.lookup();
                return cachedTask;
            });
        }
        // ACTUAL_AE_HANDLER
    }
    static class ExtendedAdapter {
        private final TileExInscriber host = new TileExInscriber();
        private InscriberRecipe cachedTask;
        private long recipeLock$recipeGeneration = Long.MIN_VALUE;
        InscriberRecipe task() {
            return recipeLock$checkQueryAndCache(args -> {
                if (cachedTask == null && host.getLevel() != null) cachedTask = (InscriberRecipe) host.level.lookup();
                return cachedTask;
            });
        }
        // ACTUAL_EXTENDED_HANDLER
    }
    static class ReactionAdapter extends BlockEntity {
        private Recipe<?> cachedTask;
        private long recipeLock$recipeGeneration = Long.MIN_VALUE;
        boolean working = true;
        void setWorking(boolean working) { this.working = working; }
        Recipe<?> task() {
            // Mirrors the exact first-read null-guard/lookup/write/final-read sequence asserted
            // against ReactionChamberEntity.getTask bytecode, without starting Minecraft.
            if (recipeLock$refreshStaleQuery(this, args -> cachedTask) == null && level != null) {
                cachedTask = level.lookup();
            }
            var callback = new CallbackInfoReturnable<Recipe<?>>(cachedTask);
            recipeLock$checkQueryAndCachedTask(callback);
            return callback.getReturnValue();
        }
        // ACTUAL_REACTION_REFRESH
        // ACTUAL_REACTION_RETURN
    }
"""

HARNESS_MAIN = r"""
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void exercise(Supplier<Recipe<?>> task, BlockEntity machine) {
        var level = machine.level;
        var id = new ResourceLocation("test:inscribing_or_reaction");
        var old = new InscriberRecipe();
        level.nextRecipe = old; level.ids.put(old, id);
        check(task.get() == old && level.lookups == 1, "Initial lookup must be accepted");
        check(task.get() == old && level.lookups == 1, "Unchanged generation must retain the structural cache");
        level.grant = false;
        check(task.get() == null && level.lookups == 1, "Cached return must still check live permission");
        level.grant = true;
        check(task.get() == old && level.lookups == 1, "An unlock needs no inventory change or replacement query");

        var replacement = new InscriberRecipe();
        level.ids.clear(); level.ids.put(replacement, id); level.nextRecipe = replacement;
        PatternRecipeReloads.current++;
        check(!RecipeLocks.allowed(machine, old), "The actual core guard rejects stale/unknown recipe identity");
        check(task.get() == replacement && level.lookups == 2,
                "Reload with identical inputs must query a current-manager object instead of getting stuck");
        check(task.get() == replacement && level.lookups == 2, "Only one cache invalidation per generation");

        var afterFailure = new InscriberRecipe();
        level.ids.clear(); level.ids.put(afterFailure, id); level.nextRecipe = afterFailure;
        PatternRecipeReloads.current++; level.throwLookup = true;
        try { task.get(); throw new AssertionError("Lookup errors must propagate"); }
        catch (IllegalStateException expected) {}
        check(!RecipeExecutionContext.active(), "Exception must close the execution scope");
        level.throwLookup = false;
        check(task.get() == afterFailure, "A failed refresh must retry instead of retaining an old raw recipe forever");

        level.rules = false; level.grant = false;
        var unlocked = new InscriberRecipe(); level.nextRecipe = unlocked; level.ids.clear();
        PatternRecipeReloads.current++;
        check(task.get() == unlocked, "Reload invalidation must also work when the reload removes all rules");
        level.rules = true; level.nextRecipe = null; PatternRecipeReloads.current++;
        check(task.get() == null, "A removed recipe must not execute its old cached object");
        check(machine.inputs == 4 && machine.outputs == 2 && machine.progress == 113
                && machine.energy == 700 && machine.smash,
                "Query-cache migration must not mutate inputs, completed output, progress, power or smash state");
        check(!RecipeExecutionContext.active(), "No query scope may escape");
    }
    static void terminalBinding() {
        var player = new ServerPlayer();
        var grid = new IGrid();
        var node = new IGridNode(); node.active = true; node.grid = grid;
        var host = new ActionHost(); host.node = node;
        var terminal = new WirelessCraftingTermMenu(); terminal.source.host = host;
        check(RecipeLocks.playerGrid(player) == null, "Constructor preview cannot borrow an unbound terminal");
        player.containerMenu = terminal;
        check(RecipeLocks.playerGrid(player) == grid, "Bound wired/wireless crafting menu uses its actual action host");
        node.active = false;
        check(RecipeLocks.playerGrid(player) == null, "Lost channel/power cannot retain a grid grant");
        node.active = true;
        var replacement = new IGrid(); node.grid = replacement;
        check(RecipeLocks.playerGrid(player) == replacement, "A split/rejoined network must be read live");
        terminal.source.host = null;
        check(RecipeLocks.playerGrid(player) == null, "Missing host must not reuse the former grid");
        terminal.source.host = host; player.containerMenu = new Object();
        check(RecipeLocks.playerGrid(player) == null, "Vanilla workbenches cannot borrow a nearby or previous ME grid");
    }
    public static void main(String[] args) {
        var ae = new AEAdapter(); exercise(ae::task, ae);
        var extended = new ExtendedAdapter(); exercise(extended::task, extended.host);
        var reaction = new ReactionAdapter(); exercise(reaction::task, reaction);
        check(!reaction.working, "Null/denied reaction task must clear working before the energy lambda can run");
        reaction.level.rules = false; reaction.working = true; PatternRecipeReloads.current++;
        check(reaction.task() == null && !reaction.working,
                "Removing the last rule and recipe during work must not leave a null energy task with working=true");
        terminalBinding();
        System.out.println("AE raw-cache reload, failure retry, no-consumption and bound-terminal regressions passed");
    }
}
"""


class ExtractedAdapterBehaviorTests(unittest.TestCase):
    def test_real_cache_handlers_identity_refusal_no_inventory_mutation_and_bound_terminal(self):
        tools = java_tools(17)
        if tools is None:
            self.skipTest("JDK 17+ required for isolated executable adapter checks")
        core = CORE.read_text(encoding="utf-8")
        java = HARNESS_PREFIX
        java += method(core, "allowed", "BlockEntity machine, Recipe<?> recipe") + "\n"
        java += method(core, "playerGrid") + "\n" + method(core, "activeGrid") + "\n    }\n"
        context = CONTEXT.read_text(encoding="utf-8")
        context = context[context.index("public final class RecipeExecutionContext"):]
        java += context.replace("public final class RecipeExecutionContext", "static final class RecipeExecutionContext", 1) + "\n"
        adapters = HARNESS_ADAPTERS
        for marker, adapter, handler in (
                ("ACTUAL_AE_HANDLER", "RecipeLockAEInscriberMixin", "recipeLock$checkQueryAndCache"),
                ("ACTUAL_EXTENDED_HANDLER", "RecipeLockExtendedInscriberThreadMixin", "recipeLock$checkQueryAndCache"),
                ("ACTUAL_REACTION_REFRESH", "RecipeLockAdvancedReactionChamberMixin", "recipeLock$refreshStaleQuery"),
                ("ACTUAL_REACTION_RETURN", "RecipeLockAdvancedReactionChamberMixin", "recipeLock$checkQueryAndCachedTask")):
            adapters = adapters.replace("// " + marker, method(source(adapter), handler))
        java += adapters + HARNESS_MAIN
        with tempfile.TemporaryDirectory(prefix="recipe-lock-ae-cache-regression-") as directory:
            path = Path(directory) / "AECacheHarness.java"
            path.write_text(java, encoding="utf-8")
            compiled = subprocess.run([tools[0], "-encoding", "UTF-8", "-proc:none", "-d", directory, str(path)],
                                      capture_output=True, text=True, timeout=45)
            self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
            executed = subprocess.run([tools[1], "-cp", directory, "AECacheHarness"],
                                      capture_output=True, text=True, timeout=20)
            self.assertEqual(0, executed.returncode, executed.stdout + executed.stderr)
            self.assertIn("regressions passed", executed.stdout)


if __name__ == "__main__":
    unittest.main()
