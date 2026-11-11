"""Independent machine-admission regressions; no Gradle or Minecraft startup.

Run: python src/test/python/test_recipe_lock_machine_admission.py -v
Static checks cover production call sites, bridge identity and accepted-work boundaries.
The executable check compiles the *actual Java admission methods extracted from source*
against tiny policy/context stubs in a temporary directory (JDK 17+ suffices). It tests
our adapter semantics, NOT the real RecipeLocks policy, AE grid discovery or Mixin runtime.
"""

from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
MAIN = ROOT / "src/main/java/com/atir/molecularmanipulator"


def source(relative):
    return (MAIN / relative).read_text(encoding="utf-8")


def masked(text):
    # Keep offsets/newlines, hiding braces and method-like text in comments/literals.
    return re.sub(r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*.*?\*/',
                  lambda match: re.sub(r"[^\n]", " ", match.group()), text, flags=re.S)


def methods(text, name):
    code = masked(text)
    declaration = re.compile(
        r"(?m)^ {4}(?:(?:public|private|protected|static|final) +)*"
        r"[A-Za-z_$][\w.$<>?, \[\]]* +" + re.escape(name) + r"\s*\(")
    result = []
    for match in declaration.finditer(code):
        opening = code.index("{", match.end())
        position, depth = opening + 1, 1
        while depth:
            depth += (code[position] == "{") - (code[position] == "}")
            position += 1
        result.append(text[match.start():position])
    return result


def method(relative, name, signature=None):
    result = methods(source(relative), name)
    if signature:
        result = [value for value in result if signature in value.split("{", 1)[0]]
    if len(result) != 1:
        raise AssertionError((relative, name, signature, len(result)))
    return result[0]


BATCHER = "blockentity/MolecularCraftingBatcher.java"
API = "research/MatterResearchApi.java"
WELL = "blockentity/MatterFabricationBlockEntity.java"
BATCH = "blockentity/MatterFabricationBatch.java"
BUFFER = "blockentity/MatterPatternBuffer.java"
AUTO = "blockentity/MolecularAutoCrafter.java"
BRIDGE = "crafting/MatterRecipeBridge.java"
RECIPE = "crafting/MatterFabricationRecipe.java"


class MachineAdmissionWiringTests(unittest.TestCase):
    def test_bridge_recipe_has_exact_nullable_identity_without_json_or_stream_fields(self):
        recipe = source(RECIPE)
        self.assertRegex(recipe, r"@Nullable ResourceLocation sourceRecipeId\) implements Recipe")
        copy = method(RECIPE, "withSourceRecipeId")
        for field in ("ingredients", "catalysts", "results", "fluidInput", "fluidResult",
                      "aeInputs", "aeOutputs", "processingTime", "aePerTick", "requiresResearch"):
            self.assertIn(field, copy)
        self.assertIn("requireNonNull(sourceRecipeId)", copy)
        self.assertNotIn("sourceRecipeId", recipe.split("class Serializer", 1)[1])
        self.assertIn("processingTime, aePerTick, requiresResearch, null)", recipe)

    def test_all_import_kinds_carry_the_real_source_holder(self):
        bridge = source(BRIDGE)
        self.assertIn("convert(level, holder, output, encoded, type, machine)", bridge)
        self.assertIn("record Branch(RecipeHolder<?> source", bridge)
        self.assertIn("new Branch(source, output", bridge)
        self.assertIn(".withSourceRecipeId(branch.source().id())", bridge)
        self.assertEqual(1, bridge.count("new MatterFabricationRecipe("),
                         "All built-in and declarative import paths must attach identity at the common conversion")
        key = re.search(r"var key = machine.key[^;]+;", bridge).group()
        self.assertIn("holder.id()", key, "Equivalent outputs/materials must not coalesce different source IDs")
        self.assertIn("thenComparing(entry -> entry.getValue().getFirst().source().id().toString())", bridge)

    def test_well_checks_both_recipe_ids_then_preserves_local_research(self):
        permission = method(API, "hasPermission")
        self.assertIn("RecipeLocks.allowed(machine, id)", permission)
        self.assertIn("sourceRecipeId != null && !RecipeLocks.allowed(machine, sourceRecipeId)", permission)
        self.assertLess(permission.index("RecipeLocks.allowed"), permission.index("MachineResearchGate.met"))
        self.assertIn("index.researchFor(id)", permission)
        self.assertIn("return !locked", permission)
        self.assertIn("recipe.value().sourceRecipeId()", method(API, "canUseRecipe"))
        self.assertIn("canUseRecipe(machine, recipe)", method(API, "isRecipeUnlocked"))
        for obsolete in ("requiredMask", "unlockMask", "StarComputationRecipes"):
            self.assertNotIn(obsolete, source(API))

    def test_every_molecular_runtime_caller_uses_machine_guarded_overloads(self):
        calls = []
        for path in MAIN.rglob("*.java"):
            if path.name == "MolecularCraftingBatcher.java":
                continue
            text = path.read_text(encoding="utf-8")
            for match in re.finditer(r"(?:craftingBatcher|batcher)\.(prepare(?:Selected|Reusable)?)\(\s*([^,]+),", text):
                calls.append((path.name, match.group(1)))
                self.assertIn(match.group(2).strip(), ("this", "machine"), str(path))
        self.assertEqual(12, len(calls))
        self.assertEqual({"MolecularManipulatorBlockEntity.java", "MolecularCenterBlockEntity.java",
                          "AssemblerMatrixMolecularCoreBlockEntity.java", "MolecularAutoCrafter.java"},
                         {file for file, _ in calls})

    def test_warm_normal_selected_and_reusable_preparations_all_recheck(self):
        for name in ("prepare", "prepareSelected", "prepareReusable"):
            with self.subTest(entry=name):
                guarded = method(BATCHER, name, "BlockEntity machine")
                check = guarded.index("RecipeLocks.allowedPattern(machine, patternDetails)")
                delegate = guarded.index("return " + name + "(patternDetails")
                self.assertLess(check, delegate)
                self.assertIn("level.isClientSide()", guarded)
                self.assertIn("try (var scope = RecipeExecutionContext.open(machine))", guarded)
        normal = method(BATCHER, "prepare", "BlockEntity machine")
        self.assertIn("planRecipeView != recipes", normal)
        self.assertIn("planCache.clear()", normal)
        self.assertIn("planCache.get(patternDetails)", method(BATCHER, "prepare", "IPatternDetails patternDetails, KeyCounter[] inputs, Level"))

    def test_passive_crafter_rechecks_decoded_patterns_before_first_extraction(self):
        run = method(AUTO, "runPattern")
        self.assertIn("host instanceof BlockEntity machine", run)
        self.assertLess(run.index("RecipeLocks.allowedPattern(machine, details)"),
                        run.index("CraftingCpuHelper.extractPatternInputs"))
        self.assertIn("RecipeExecutionContext.open(machine)", run)
        denied = run.split("!RecipeLocks.allowedPattern(machine, details)", 1)[1].split("var outputs", 1)[0]
        self.assertNotIn("config.enabled = false", denied, "Sharing recovery must be retried without re-enabling a slot")

    def test_processing_patterns_check_a_real_material_matched_holder(self):
        match = method(BATCH, "match", "List<RecipeHolder<MatterFabricationRecipe>> candidates")
        self.assertLess(match.index("validInputs(holder.value(), supplied, count)"),
                        match.index("MatterResearchApi.canUseRecipe(host, holder)"))
        accept = method(BATCH, "accept", "RecipeHolder<MatterFabricationRecipe> holder")
        self.assertLess(accept.index("MatterResearchApi.canUseRecipe"), accept.index("acceptAdmitted"))
        enqueue = method(BUFFER, "enqueue", "UUID craftingId")
        self.assertLess(enqueue.index("MatterFabricationBatch.match"), enqueue.index("queued.add"))

    def test_cached_counted_batch_commit_still_enqueues_through_live_admission(self):
        logic = source("blockentity/MatterFabricationPatternLogic.java")
        self.assertIn("&& assembly.getBuffer().enqueue(pattern, totalInputs, Math.multiplyExact(crafts, multiplier), craftingId)", logic)
        self.assertIn("return commit(", logic.split("public boolean commitReserved", 1)[1])
        self.assertIn("return commit(", logic.split("public boolean commitPrototype", 1)[1])

    def test_queue_slices_finish_previously_owned_inputs_without_reauthorization(self):
        process = method(BUFFER, "process")
        self.assertIn("MatterFabricationBatch.validInputs(recipe.value(), work.inputs, work.crafts)", process)
        self.assertIn("active.acceptAdmitted(", process)
        self.assertNotIn("MatterFabricationBatch.match(", process)
        self.assertNotIn("canUseRecipe", process)
        admitted = method(BATCH, "acceptAdmitted")
        self.assertIn("validInputs(holder.value(), supplied, count)", admitted)
        self.assertNotIn("canUseRecipe", admitted)
        external_calls = [(path.relative_to(MAIN).as_posix(), path.read_text(encoding="utf-8").count(".acceptAdmitted("))
                          for path in MAIN.rglob("*.java") if ".acceptAdmitted(" in path.read_text(encoding="utf-8")]
        self.assertEqual([("blockentity/MatterPatternBuffer.java", 1)], external_calls)
        for tick in methods(source(BATCH), "tick"):
            self.assertNotIn("canUseRecipe", tick)
            self.assertNotIn("RecipeLocks", tick)

    def test_admitted_reusable_jobs_do_not_reauthorize_during_settlement(self):
        for host in ("MolecularManipulatorBlockEntity", "MolecularCenterBlockEntity", "AssemblerMatrixMolecularCoreBlockEntity"):
            with self.subTest(host=host):
                process = method("blockentity/" + host + ".java", "processReusableBatch")
                self.assertIn("job.advance(step)", process)
                self.assertNotIn("RecipeLocks", process)
                self.assertNotIn("prepareReusable", process)

    def test_manual_match_cache_never_stores_authorization(self):
        find = method(WELL, "findRecipe")
        self.assertIn("cachedSelections = List.copyOf(matches)", find)
        self.assertEqual(2, find.count("return selectPermittedRecipe()"))
        self.assertNotIn("canUseRecipe", find)
        select = method(WELL, "selectPermittedRecipe")
        self.assertIn("if (manualAdmitted && activeRecipeId != null)", select)
        self.assertIn("selection.holder().id().equals(activeRecipeId)", select)
        self.assertIn("MatterResearchApi.canUseRecipe(this, selection.holder())", select)
        self.assertNotIn("selectionEntropyUnlockMask", source(WELL))

    def test_manual_admission_survives_zero_rounded_progress_but_not_completion(self):
        process = method(WELL, "processRecipe")
        self.assertIn("if (!manualAdmitted) manualCrafts = manualBatchLimit", process)
        self.assertLess(process.index("consumePower(currentAePerTick)"), process.index("manualAdmitted = true"))
        self.assertIn("manualAdmitted = false", method(WELL, "completeRecipe"))
        self.assertIn("manualAdmitted = false", method(WELL, "resetProgress"))
        self.assertIn('putBoolean("fabrication_manual_admitted", manualAdmitted)', method(WELL, "saveAdditional"))
        self.assertIn('tag.getBoolean("fabrication_manual_admitted") : progress > 0', method(WELL, "loadTag"))
        self.assertIn("manualAdmitted", method(WELL, "canAdmitBatch"))
        self.assertIn("manualAdmitted", method(WELL, "processPatternBuffers"))


# Deliberately small server-independent stand-ins. The methods inserted below are
# the current production source, not a separately maintained Python/Java model.
HARNESS_PREFIX = r"""
import java.util.*;
import java.util.function.ToIntFunction;
public class MachineAdmissionHarness {
    @interface Nullable {}
    record ResourceLocation(String value) {}
    record RecipeHolder<T>(ResourceLocation id, T value) {}
    record MatterFabricationRecipe(boolean requiresResearch, ResourceLocation sourceRecipeId) {}
    record MatterResearchRecipe() {}
    record RecipeSelection(RecipeHolder<MatterFabricationRecipe> holder) {}
    record IPatternDetails(ResourceLocation recipeId) {}
    static class KeyCounter {}
    static class MolecularReusableBatchPlan {}
    static class MolecularReusableBatchJob {}
    static class RecipeManager {
        Object recipes = new Object();
        Object getRecipes() { return recipes; }
    }
    static class Level {
        boolean client;
        final RecipeManager manager = new RecipeManager();
        final MatterRecipeIndex index = new MatterRecipeIndex();
        boolean isClientSide() { return client; }
        RecipeManager getRecipeManager() { return manager; }
    }
    static class Grid { final Set<ResourceLocation> online = new HashSet<>(); }
    static class Research {
        final Map<ResourceLocation, Integer> counts = new HashMap<>();
        int completionCount(ResourceLocation id) { return counts.getOrDefault(id, 0); }
    }
    static class BlockEntity {
        Level level;
        Grid grid;
        final Set<ResourceLocation> team = new HashSet<>();
        BlockEntity(Level level) { this.level = level; }
        Level getLevel() { return level; }
    }
    static class MatterRecipeIndex {
        final Map<ResourceLocation, List<RecipeHolder<MatterResearchRecipe>>> owners = new HashMap<>();
        final Set<ResourceLocation> gated = new HashSet<>();
        static MatterRecipeIndex get(Level level) { return level.index; }
        boolean requiresMachineResearchGate(ResourceLocation id) { return gated.contains(id); }
        List<RecipeHolder<MatterResearchRecipe>> researchFor(ResourceLocation id) { return owners.getOrDefault(id, List.of()); }
        List<RecipeHolder<MatterResearchRecipe>> research() { return List.of(); }
    }
    static final ResourceLocation GATE = new ResourceLocation("well:machine_gate");
    static class MachineResearchGate {
        static boolean met(List<RecipeHolder<MatterResearchRecipe>> definitions, ToIntFunction<ResourceLocation> completions) {
            return completions.applyAsInt(GATE) > 0;
        }
    }
    static class RecipeLocks {
        static final Set<ResourceLocation> rules = new HashSet<>();
        static boolean allowed(BlockEntity machine, ResourceLocation id) {
            return !rules.contains(id) || machine.team.contains(id)
                    || machine.grid != null && machine.grid.online.contains(id);
        }
        static boolean allowedPattern(BlockEntity machine, IPatternDetails pattern) {
            return allowed(machine, pattern.recipeId());
        }
    }
    static class RecipeExecutionContext {
        static int active;
        static Scope open(BlockEntity machine) { active++; return new Scope(); }
        static class Scope implements AutoCloseable { public void close() { active--; } }
    }
"""

HARNESS_MAIN = r"""
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        var level = new Level();
        var first = new MatterFabricationBlockEntity(level);
        var second = new MatterFabricationBlockEntity(level);
        var internal = new ResourceLocation("well:imported_recipe");
        var source = new ResourceLocation("addon:exact_source_recipe");
        var owner = new ResourceLocation("well:research_owner");
        var ordinary = new RecipeHolder<>(new ResourceLocation("other:unlisted"), new MatterFabricationRecipe(false, null));
        var imported = new RecipeHolder<>(internal, new MatterFabricationRecipe(true, source));
        var selection = new RecipeSelection(imported);
        first.cachedSelections = List.of(new RecipeSelection(ordinary));
        check(first.selectPermittedRecipe() != null, "Unlisted ordinary recipe must not be locked by default");
        level.index.owners.put(internal, List.of(new RecipeHolder<>(owner, new MatterResearchRecipe())));
        first.research.counts.put(owner, 1);
        first.cachedSelections = List.of(selection);
        second.cachedSelections = List.of(selection);
        RecipeLocks.rules.add(source);
        check(first.selectPermittedRecipe() == null, "Local research alone cannot bypass a source rule");
        var grid = new Grid(); first.grid = grid; second.grid = grid;
        grid.online.add(source);
        check(first.selectPermittedRecipe() == selection, "Online grid may supply source authorization");
        check(second.selectPermittedRecipe() == null, "Shared star authorization cannot replace another well's research");
        second.research.counts.put(owner, 1);
        check(second.selectPermittedRecipe() == selection, "Second well can share the currently online source grant");
        grid.online.clear();
        check(first.selectPermittedRecipe() == null && second.selectPermittedRecipe() == null,
                "Same cached matches must reject a new batch immediately after sharing expires");
        first.team.add(source);
        check(first.selectPermittedRecipe() == selection && second.selectPermittedRecipe() == null,
                "Permanent team research must remain independent");
        first.grid = null; first.team.clear();
        check(first.selectPermittedRecipe() == null, "A former grid attachment must not leave a copied unlock");
        first.team.add(source); RecipeLocks.rules.add(internal);
        check(first.selectPermittedRecipe() == null, "Source authorization must not replace an internal-ID rule");
        first.team.add(internal);
        first.research.counts.clear();
        check(first.selectPermittedRecipe() == null, "Both global IDs still cannot replace local research");
        first.research.counts.put(owner, 1); level.index.gated.add(internal);
        check(first.selectPermittedRecipe() == null, "An alternative owner cannot bypass the well's machine gate");
        first.research.counts.put(GATE, 1);
        check(first.selectPermittedRecipe() == selection, "All local and global requirements grant admission");
        first.team.clear(); first.manualAdmitted = true; first.activeRecipeId = internal;
        check(first.selectPermittedRecipe() == selection, "An admitted manual batch may finish after revocation");
        first.cachedSelections = List.of(new RecipeSelection(ordinary));
        check(first.selectPermittedRecipe() == null, "Admission belongs to one exact branch, not a replacement recipe");
        first.cachedSelections = List.of(selection); first.manualAdmitted = false;
        check(first.selectPermittedRecipe() == null, "Completion must not grandfather a second batch");

        var batcher = new MolecularCraftingBatcher();
        var pattern = new IPatternDetails(source);
        var inputs = new KeyCounter[0];
        check(!batcher.prepare(first, pattern, inputs, 20), "Normal batch must reject before touching its plan cache");
        check(batcher.delegateCalls == 0, "Denied preparation must not delegate");
        first.grid = grid; grid.online.add(source);
        check(batcher.prepare(first, pattern, inputs, 20), "Authorized batch can populate the structural cache");
        check(!batcher.planCache.isEmpty() && batcher.delegateCalls == 1, "Warm-cache setup");
        grid.online.clear();
        check(!batcher.prepare(first, pattern, inputs, 20) && batcher.delegateCalls == 1,
                "A warm cached plan cannot bypass a revoked grid grant");
        check(!batcher.prepareSelected(first, pattern, inputs, 20, inputs, 20), "Selected batch also rechecks");
        check(batcher.prepareReusable(first, pattern, inputs, UUID.randomUUID(), new MolecularReusableBatchPlan()) == null,
                "Reusable batch also rechecks");
        grid.online.add(source);
        check(batcher.prepareSelected(first, pattern, inputs, 20, inputs, 20), "Authorized selected admission");
        check(batcher.prepareReusable(first, pattern, inputs, UUID.randomUUID(), new MolecularReusableBatchPlan()) != null,
                "Authorized reusable admission");
        check(RecipeExecutionContext.active == 0, "All execution scopes must close");
        level.client = true;
        check(!batcher.prepare(first, pattern, inputs, 1), "Clients cannot use server admission");
        first.level = null;
        check(!batcher.prepare(first, pattern, inputs, 1), "Unattached machines cannot use server admission");
        System.out.println("machine admission checks passed");
    }
}
"""


class ExtractedAdmissionBehaviorTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac") and shutil.which("java"), "JDK required for isolated Java adapter checks")
    def test_live_authorization_local_research_cached_plans_and_admitted_work(self):
        java = HARNESS_PREFIX
        java += "    static class MatterResearchApi {\n" + method(API, "canUseRecipe") + "\n" + method(API, "hasPermission") + "\n    }\n"
        java += """    static class MatterFabricationBlockEntity extends BlockEntity {
        final Research research = new Research();
        List<RecipeSelection> cachedSelections = List.of();
        boolean manualAdmitted;
        ResourceLocation activeRecipeId;
        MatterFabricationBlockEntity(Level level) { super(level); }
        Research getResearch() { return research; }
""" + method(WELL, "selectPermittedRecipe") + "\n    }\n"
        java += """    static class MolecularCraftingBatcher {
        final Map<IPatternDetails, Object> planCache = new HashMap<>();
        Object planRecipeView;
        int delegateCalls;
        boolean prepare(IPatternDetails pattern, KeyCounter[] inputs, Level level, long count) {
            delegateCalls++; planCache.put(pattern, new Object()); return true;
        }
        boolean prepareSelected(IPatternDetails pattern, KeyCounter[] inputs, Level level, long count, KeyCounter[] first, long selected) {
            delegateCalls++; return true;
        }
        MolecularReusableBatchJob prepareReusable(IPatternDetails pattern, KeyCounter[] inputs, Level level, UUID id, MolecularReusableBatchPlan plan) {
            delegateCalls++; return new MolecularReusableBatchJob();
        }
"""
        for name in ("prepare", "prepareSelected", "prepareReusable"):
            java += method(BATCHER, name, "BlockEntity machine") + "\n"
        java += "    }\n" + HARNESS_MAIN
        with tempfile.TemporaryDirectory(prefix="machine-recipe-admission-") as directory:
            file = Path(directory) / "MachineAdmissionHarness.java"
            file.write_text(java, encoding="utf-8")
            compiled = subprocess.run([shutil.which("javac"), "-encoding", "UTF-8", "-d", directory, str(file)],
                                      capture_output=True, text=True, timeout=45)
            self.assertEqual(0, compiled.returncode, compiled.stdout + compiled.stderr)
            executed = subprocess.run([shutil.which("java"), "-cp", directory, "MachineAdmissionHarness"],
                                      capture_output=True, text=True, timeout=20)
            self.assertEqual(0, executed.returncode, executed.stdout + executed.stderr)
            self.assertIn("machine admission checks passed", executed.stdout)


if __name__ == "__main__":
    unittest.main()
