"""Standalone AE-family adapter wiring and exact dependency-bytecode contracts.

Run: python src/test/python/test_recipe_lock_ae_adapters.py -v
No Gradle build, third-party source checkout, or game startup is used; the core API is read, not changed.
Optional AdvancedAE bytecode checks use RECIPE_LOCK_ADVANCED_AE_JAR, or the inspection-only JAR at
%TEMP%/star-recipe-lock-ae-adapters/AdvancedAE-1.6.12-1.21.1.jar. Missing artifacts are explicit skips.
These checks do NOT replace Mixin transformation or real-server authorization GameTests.
"""

import hashlib
import os
from pathlib import Path
import re
import struct
import tempfile
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[3]
MIXINS = ROOT / "src/main/java/com/atir/molecularmanipulator/mixin"
CACHE = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
ADVANCED = Path(os.environ.get("RECIPE_LOCK_ADVANCED_AE_JAR", str(
    Path(tempfile.gettempdir()) / "star-recipe-lock-ae-adapters/AdvancedAE-1.6.12-1.21.1.jar")))
AE = "appeng/"
EAE = "com/glodblock/github/extendedae/"
GLODIUM = "com/glodblock/github/glodium/"
AAE = "net/pedroksl/advanced_ae/"
TARGETS = {
    "RecipeLockAEMolecularAssemblerMixin": ("ae2", [AE + "blockentity/crafting/MolecularAssemblerBlockEntity"]),
    "RecipeLockAEInscriberMixin": ("ae2", [AE + "blockentity/misc/InscriberBlockEntity"]),
    "RecipeLockAEInscriberRecipesMixin": ("ae2", [AE + "blockentity/misc/InscriberRecipes"]),
    "RecipeLockAECraftingTermMenuMixin": ("ae2", [AE + "menu/me/items/CraftingTermMenu"]),
    "RecipeLockAECraftingTermRefreshMixin": ("ae2", [AE + "menu/AEBaseMenu"]),
    "RecipeLockAECraftingTermSlotMixin": ("ae2", [AE + "menu/slot/CraftingTermSlot"]),
    "RecipeLockAEPatternProviderMixin": ("ae2", [AE + "helpers/patternprovider/PatternProviderLogic"]),
    "RecipeLockExtendedCraftingThreadMixin": ("extendedae", [EAE + "common/me/CraftingThread"]),
    "RecipeLockExtendedInscriberThreadMixin": ("extendedae", [EAE + "common/me/InscriberThread"]),
    "RecipeLockExtendedMachinePollingMixin": ("extendedae", [EAE + "common/tileentities/" + name for name in (
        "TileExInscriber", "TileCircuitCutter", "TileCrystalAssembler", "TileCrystalFixer")]),
    "RecipeLockExtendedRecipeExecutorMixin": ("extendedae", [EAE + "util/RecipeExecutor"]),
    "RecipeLockExtendedRecipeSearchMixin": ("glodium", [GLODIUM + "recipe/RecipeSearchContext"]),
    "RecipeLockAdvancedReactionChamberMixin": ("advancedae", [AAE + "common/entities/ReactionChamberEntity"]),
    "RecipeLockAdvancedReactionRecipesMixin": ("advancedae", [AAE + "recipes/ReactionChamberRecipes"]),
    "RecipeLockAdvancedQuantumCrafterMixin": ("advancedae", [AAE + "common/entities/QuantumCrafterEntity"]),
}


def source(name):
    return (MIXINS / (name + ".java")).read_text(encoding="utf-8")


def cached_jar(group, artifact, version):
    folder = CACHE / group / artifact / version
    return next(folder.glob("*/" + artifact + "-" + version + ".jar"), None)


JARS = {
    "ae2": cached_jar("org.appliedenergistics", "appliedenergistics2", "19.2.18"),
    "extendedae": cached_jar("curse.maven", "ex-pattern-provider-892005", "8025439"),
    "glodium": cached_jar("io.github.glodblock", "Glodium", "1.21-2.2-neoforge"),
    "advancedae": ADVANCED if ADVANCED.is_file() else None,
}


class ClassContract:
    """Minimal classfile reader: members, Code bytes, and resolved method/field instructions."""

    def __init__(self, data):
        self.data, self.pos = data, 8
        self.cp = [None] * self.u2()
        index = 1
        while index < len(self.cp):
            tag = self.u1()
            if tag == 1:
                length = self.u2()
                value = data[self.pos:self.pos + length].decode("utf-8", errors="replace")
                self.pos += length
            elif tag in (7, 8, 16, 19, 20):
                value = self.u2()
            elif tag in (9, 10, 11, 12, 17, 18):
                value = (self.u2(), self.u2())
            else:
                size = {3: 4, 4: 4, 5: 8, 6: 8, 15: 3}[tag]
                value = data[self.pos:self.pos + size]
                self.pos += size
            self.cp[index] = (tag, value)
            index += 2 if tag in (5, 6) else 1
        self.access = self.u2()
        self.name = self.utf(self.cp[self.u2()][1])
        super_index = self.u2()
        self.super_name = self.utf(self.cp[super_index][1]) if super_index else None
        self.interfaces = [self.utf(self.cp[self.u2()][1]) for _ in range(self.u2())]
        self.fields = self.members()
        self.methods = self.members()

    def u1(self):
        value = self.data[self.pos]
        self.pos += 1
        return value

    def u2(self):
        value = struct.unpack_from(">H", self.data, self.pos)[0]
        self.pos += 2
        return value

    def u4(self):
        value = struct.unpack_from(">I", self.data, self.pos)[0]
        self.pos += 4
        return value

    def utf(self, index):
        return self.cp[index][1]

    def members(self):
        result = {}
        for _ in range(self.u2()):
            access, name, desc = self.u2(), self.u2(), self.u2()
            code = b""
            for _ in range(self.u2()):
                attribute, length = self.utf(self.u2()), self.u4()
                start = self.pos
                if attribute == "Code":
                    code_length = struct.unpack_from(">I", self.data, start + 4)[0]
                    code = self.data[start + 8:start + 8 + code_length]
                self.pos = start + length
            result[(self.utf(name), self.utf(desc))] = (access, code)
        return result

    def ref(self, index):
        tag, value = self.cp[index]
        if tag not in (9, 10, 11):
            return None
        owner, name_type = value
        name, desc = self.cp[name_type][1]
        return self.utf(self.cp[owner][1]), self.utf(name), self.utf(desc)

    def method(self, name):
        found = [(key, value) for key, value in self.methods.items() if key[0] == name]
        if len(found) != 1:
            raise AssertionError((name, [key for key, _ in found]))
        return found[0]

    def instructions(self, name):
        code = self.method(name)[1][1]
        pos = 0
        while pos < len(code):
            op = code[pos]
            ref = self.ref(struct.unpack_from(">H", code, pos + 1)[0]) if 178 <= op <= 185 else None
            yield pos, op, ref
            length = 1
            if op in (16, 18, 169, 188) or 21 <= op <= 25 or 54 <= op <= 58:
                length = 2
            elif op in (17, 19, 20, 132, 187, 189, 192, 193, 198, 199) or 153 <= op <= 168 or 178 <= op <= 184:
                length = 3
            elif op in (185, 186, 200, 201):
                length = 5
            elif op == 197:
                length = 4
            elif op == 196:
                length = 6 if code[pos + 1] == 132 else 4
            elif op in (170, 171):
                aligned = (pos + 4) & ~3
                if op == 170:
                    low, high = struct.unpack_from(">ii", code, aligned + 4)
                    length = aligned - pos + 12 + 4 * (high - low + 1)
                else:
                    pairs = struct.unpack_from(">i", code, aligned + 4)[0]
                    length = aligned - pos + 8 + 8 * pairs
            pos += length
        if pos != len(code):
            raise AssertionError((name, "invalid instruction boundary", pos, len(code)))

    def references(self, name):
        return [ref for _, _, ref in self.instructions(name) if ref is not None]


def annotations(text, kind):
    for match in re.finditer("@" + kind + r"\(", text):
        depth, quoted, escaped = 1, False, False
        pos = match.end()
        while depth:
            char = text[pos]
            if quoted:
                if char == '"' and not escaped:
                    quoted = False
                escaped = char == "\\" and not escaped
            elif char == '"':
                quoted = True
            else:
                depth += (char == "(") - (char == ")")
            pos += 1
        yield text[match.end():pos - 1], text[pos:text.index("{", pos)].strip()


def split_types(text):
    parts, start, depth = [], 0, 0
    for index, char in enumerate(text):
        depth += (char == "<") - (char == ">")
        if char == "," and depth == 0:
            parts.append(text[start:index].strip())
            start = index + 1
    if text[start:].strip():
        parts.append(text[start:].strip())
    return parts


def descriptor(java_type, imports):
    java_type = re.sub(r"@\w+\s*", "", java_type).strip()
    if java_type.endswith("[]"):
        return "[" + descriptor(java_type[:-2], imports)
    erased = java_type.split("<", 1)[0].strip()
    primitives = {"void": "V", "int": "I", "boolean": "Z", "double": "D", "long": "J"}
    if "." in erased and erased.split(".", 1)[0] in imports:
        outer, nested = erased.split(".", 1)
        return "L" + imports[outer].replace(".", "/") + "$" + nested.replace(".", "$") + ";"
    return primitives.get(erased) or "L" + imports.get(erased, "java.lang." + erased).replace(".", "/") + ";"


def declaration_parts(declaration, imports):
    head, arguments = declaration.split("(", 1)
    return_type = head.split()[-2]
    arguments = arguments.rsplit(")", 1)[0]
    args = [arg.rsplit(" ", 1)[0] for arg in split_types(arguments)]
    return "static" in head.split(), descriptor(return_type, imports), args


class AdapterWiringTests(unittest.TestCase):
    def test_only_promised_api_no_permission_or_progress_cache(self):
        for name in TARGETS:
            with self.subTest(adapter=name):
                text = source(name)
                for forbidden in ("@Overwrite", "@Redirect", "ThreadLocal", "SavedData", "getTeam", "setTeam", "Class.forName"):
                    self.assertNotIn(forbidden, text)
                self.assertIn("remap = false", text)
                self.assertLessEqual(set(re.findall(r"RecipeLocks\.(\w+)\(", text)), {
                    "hasRules", "allowed", "allowedPattern", "allowedForPlayer", "allowedInContext", "identifyNamePress"})
                self.assertEqual(text.count("RecipeExecutionContext.open("),
                                 text.count("try (var scope = RecipeExecutionContext.open("))
                self.assertNotIn("catch (", text, "Never turn an authorization error into a successful craft")

    def test_assembler_live_admission_is_per_successful_job_not_per_tick_or_cached_plan(self):
        for name, admission, execution in (
                ("RecipeLockAEMolecularAssemblerMixin", "pushPattern", "tickingRequest"),
                ("RecipeLockExtendedCraftingThreadMixin", "acceptJob", "tick")):
            text = source(name)
            self.assertIn('@WrapMethod(method = "' + admission + '")', text)
            self.assertIn('@WrapMethod(method = "' + execution + '")', text)
            self.assertEqual(2, text.count("RecipeLocks.allowedPattern("))
            accept = text[text.index("private boolean recipeLock$beforeAccept"):text.index('@WrapMethod(method = "' + execution)]
            self.assertLess(accept.index("!RecipeLocks.allowedPattern"), accept.index("original.call(pattern, inputs, side)"))
            self.assertIn("if (accepted) {\n                this.recipeLock$admittedPlan = this.myPlan;", accept)
            execute = text[text.index('@WrapMethod(method = "' + execution):text.index("@WrapOperation")]
            self.assertIn("this.recipeLock$admittedPlan != this.myPlan", execute)
            self.assertIn("if (!this.gridInv.getStackInSlot(9).isEmpty())", execute)
            self.assertIn("this.isAwake && this.recipeLock$admittedPlan == null", execute)
            self.assertLess(execute.index("!RecipeLocks.allowedPattern"), execute.index("return original.call("))
            self.assertIn("return TickRateModulation.SLOWER", execute)
            self.assertNotIn("this.myPlan = null", text)
            self.assertNotIn(".setItemDirect(", text)

    def test_assembler_tokens_burn_only_at_actual_tick_assembly_including_cached_matrix_output(self):
        for name, tick, target in (
                ("RecipeLockAEMolecularAssemblerMixin", "tickingRequest", "IMolecularAssemblerSupportedPattern;assemble("),
                ("RecipeLockExtendedCraftingThreadMixin", "tick", "CraftingThread;assemblePattern(")):
            text = source(name)
            operations = list(annotations(text, "WrapOperation"))
            self.assertEqual(1, len(operations))
            self.assertIn('method = "' + tick + '"', operations[0][0])
            self.assertIn(target, operations[0][0])
            self.assertNotIn('method = "hasMats"', text)
            start = text.index("private ItemStack recipeLock$consumeAdmission")
            consumed = text[start:text.index("@WrapMethod", start)]
            self.assertIn("finally {", consumed)
            self.assertIn("this.recipeLock$admittedPlan = null;", consumed)
            self.assertIn("this.recipeLock$restoringAdmission = ItemStack.EMPTY;", consumed)
            self.assertIn(".saveChanges();", consumed)

    def test_assembler_persistence_is_explicit_one_shot_pattern_bound_and_keeps_native_io(self):
        for name, save, load in (
                ("RecipeLockAEMolecularAssemblerMixin", "saveAdditional", "loadTag"),
                ("RecipeLockExtendedCraftingThreadMixin", "writeNBT", "readNBT")):
            text = source(name)
            self.assertIn('@WrapMethod(method = "' + save + '")', text)
            self.assertIn('@WrapMethod(method = "' + load + '")', text)
            self.assertIn('@WrapMethod(method = "recalculatePlan")', text)
            self.assertIn('"molecularmanipulator:admitted_craft"', text)
            self.assertIn("@Unique private IMolecularAssemblerSupportedPattern recipeLock$admittedPlan;", text)
            self.assertIn("@Unique private ItemStack recipeLock$restoringAdmission = ItemStack.EMPTY;", text)
            self.assertIn("data.put(recipeLock$ADMITTED_CRAFT, pattern.save(registries))", text)
            self.assertIn("ItemStack.parseOptional(registries, data.getCompound(recipeLock$ADMITTED_CRAFT))", text)
            self.assertIn("ItemStack.matches(this.recipeLock$restoringAdmission, this.myPlan.getDefinition().toStack())", text)
            self.assertNotIn("PatternRecipeReloads.generation()", text, "Reloads must not renew a one-craft token")
            self.assertNotIn('data.put("myPlan"', text, "Keep native plans, installed patterns and inputs authoritative")

    def test_inscriber_query_and_cached_returns_recheck_under_machine_scopes(self):
        for name in ("RecipeLockAEInscriberMixin", "RecipeLockExtendedInscriberThreadMixin"):
            text = source(name)
            self.assertIn('@WrapMethod(method = "getTask")', text)
            self.assertIn("var recipe = original.call();", text)
            self.assertIn("RecipeLocks.allowed(", text)
            self.assertIn("? recipe : null;", text)
            refresh = text[text.index("long generation = PatternRecipeReloads.generation();"):
                           text.index("var recipe = original.call();")]
            self.assertIn("this.recipeLock$recipeGeneration != generation", refresh)
            self.assertIn("this.cachedTask = null;", refresh)
            self.assertEqual(1, text.count("this.cachedTask = null;"))
            self.assertNotIn("setProcessingTime(", text)
            self.assertNotIn("setSmash(", text)
            self.assertIn("rate == TickRateModulation.SLEEP", text)

    def test_raw_recipe_generation_markers_are_per_instance_and_not_permission_snapshots(self):
        for name in ("RecipeLockAEInscriberMixin", "RecipeLockExtendedInscriberThreadMixin",
                     "RecipeLockAdvancedReactionChamberMixin"):
            text = source(name)
            self.assertIn("@Unique private long recipeLock$recipeGeneration = Long.MIN_VALUE;", text)
            self.assertIn("PatternRecipeReloads.generation()", text)
            self.assertNotIn("static long recipeLock$recipeGeneration", text)
            self.assertNotIn("extractItem(", text)
            self.assertNotIn("insertItem(", text)
            self.assertNotIn("setItemDirect(", text)

    def test_inscriber_and_reaction_candidate_filters_are_execution_only(self):
        for name in ("RecipeLockAEInscriberRecipesMixin", "RecipeLockAdvancedReactionRecipesMixin"):
            text = source(name)
            self.assertIn('@WrapOperation(method = "findRecipe"', text)
            self.assertIn("!RecipeExecutionContext.active()", text)
            self.assertIn("RecipeLocks.allowedInContext(level, recipe)", text)
            self.assertNotIn('@WrapMethod(method = "getRecipes")', text)
            self.assertNotIn('@WrapMethod(method = "isValidIngredient")', text)

    def test_terminal_wraps_each_actual_craft_and_rejects_cached_recipe_before_extraction(self):
        text = source("RecipeLockAECraftingTermSlotMixin")
        self.assertIn('@WrapMethod(method = "craftItem")', text)
        self.assertIn("RecipeExecutionContext.open(player)", text)
        self.assertIn("if (player.level().isClientSide)", text)
        self.assertIn('@WrapMethod(method = "findRecipe")', text)
        self.assertIn("RecipeLocks.allowedInContext(level, recipe)", text)
        self.assertIn("candidate.value().matches(input, level)", text)
        self.assertIn("RecipeLocks.allowedInContext(level, candidate)", text)
        self.assertIn("setDisplayedCraftingOutput(ItemStack.EMPTY)", text)
        self.assertNotIn("extract(", text)
        self.assertNotIn("makeItem(", text)

    def test_terminal_alternative_keeps_exact_remainder_identity_until_craft_finishes(self):
        text = source("RecipeLockAECraftingTermSlotMixin")
        self.assertIn("this.recipeLock$executingRecipe = recipe;", text)
        self.assertIn('@WrapMethod(method = "getRemainingItems")', text)
        self.assertIn("this.recipeLock$executingRecipe.value().getRemainingItems(input)", text)
        self.assertIn("finally {\n            this.recipeLock$executingRecipe = previous;", text)
        self.assertLess(text.index("this.recipeLock$executingRecipe = null;"),
                        text.index("original.call(player, inventory, available)"))

    def test_terminal_preview_invalidates_input_equality_cache_but_is_not_the_only_guard(self):
        text = source("RecipeLockAECraftingTermMenuMixin")
        self.assertIn("forceUpdate || RecipeLocks.hasRules(player.level())", text)
        self.assertIn("RecipeLocks.allowedForPlayer(player, this.currentRecipe)", text)
        self.assertIn("this.currentRecipe = null;", text)
        self.assertIn("this.outputSlot.set(ItemStack.EMPTY)", text)

    def test_terminal_refreshes_only_when_bound_on_server_before_synchronization(self):
        text = source("RecipeLockAECraftingTermRefreshMixin")
        self.assertIn("this instanceof CraftingTermMenu menu", text)
        self.assertIn("!player.level().isClientSide", text)
        self.assertIn("player.containerMenu == menu", text)
        self.assertIn("RecipeLocks.hasRules(player.level())", text)
        self.assertLess(text.index("menu.slotsChanged("), text.index("original.call();"))

    def test_provider_is_only_early_dispatch_rejection(self):
        text = source("RecipeLockAEPatternProviderMixin")
        self.assertIn('@WrapMethod(method = "pushPattern")', text)
        self.assertIn("RecipeLocks.allowedPattern(machine, pattern)", text)
        self.assertNotIn("getAvailablePatterns", re.sub(r"/\*.*?\*/", "", text, flags=re.S))
        self.assertNotIn("decodePattern(", text)

    def test_extended_executor_validates_current_cache_before_any_progress(self):
        text = source("RecipeLockExtendedRecipeExecutorMixin")
        check = text[text.index("try (var scope = RecipeExecutionContext.open("):]
        self.assertLess(check.index("!RecipeLocks.allowed(blockEntity, context.currentRecipe)"),
                        check.index("original.call(speed, usePower)"))
        self.assertIn("context.currentRecipe = null;", check)
        self.assertIn("context.lastRecipe = null;", check)
        self.assertIn("this.machine.setProgress(0)", check)
        self.assertIn("context.stuck = false;", check)
        self.assertNotIn("runRecipe(", text)
        self.assertNotIn("insertItem(", text)
        search = source("RecipeLockExtendedRecipeSearchMixin")
        self.assertIn('method = {"findRecipe", "searchRecipe"}', search)
        self.assertIn("RecipeExecutionContext.active()", search)
        self.assertIn("!RecipeLocks.allowedInContext(level, recipe)", search)

    def test_sleeping_lookup_caches_can_resume_without_an_inventory_change(self):
        for name in ("RecipeLockAEInscriberMixin", "RecipeLockExtendedMachinePollingMixin",
                     "RecipeLockAdvancedReactionChamberMixin"):
            text = source(name)
            self.assertIn("request.isSleeping()", text)
            self.assertIn("request.minTickRate(), request.maxTickRate(), false, request.initialTickRate()", text)

    def test_optional_mixins_have_no_optional_class_descriptors_and_are_pseudo(self):
        for name in TARGETS:
            if not name.startswith("RecipeLockAdvanced"):
                continue
            text = source(name)
            self.assertIn("@Pseudo", text)
            self.assertIn('@Mixin(targets = "net.pedroksl.advanced_ae.', text)
            self.assertNotRegex(text, r"import net\.pedroksl\.")
            self.assertIn("1.6.12-1.21.1", text)

    def test_reaction_null_task_cannot_fall_through_working_flag_to_energy_dereference(self):
        text = source("RecipeLockAdvancedReactionChamberMixin")
        self.assertIn('@Inject(method = "getTask", at = @At("RETURN"), cancellable = true)', text)
        self.assertIn("CallbackInfoReturnable<Recipe<?>>", text)
        self.assertIn("recipe == null || RecipeLocks.hasRules(level) && !RecipeLocks.allowed(machine, recipe)", text)
        self.assertLess(text.index("this.setWorking(false);"), text.index("cir.setReturnValue(null);"))

    def test_quantum_crafter_blocks_actual_extraction_not_just_availability(self):
        text = source("RecipeLockAdvancedQuantumCrafterMixin")
        self.assertIn('method = {"maximumCraftableAmount", "performCraft"}', text)
        self.assertIn("opcode = Opcodes.GETFIELD, ordinal = 0", text)
        self.assertIn("@Coerce Object job", text)
        self.assertIn("RecipeLocks.allowedPattern(machine, pattern)", text)
        self.assertNotIn(".pattern =", text)
        self.assertNotIn("StorageHelper.poweredExtraction(", text)


class DependencyBytecodeTests(unittest.TestCase):
    def load(self, dependency, target):
        jar = JARS[dependency]
        if jar is None:
            self.skipTest(dependency + " exact inspection artifact not available")
        with ZipFile(jar) as archive:
            return ClassContract(archive.read(target + ".class"))

    def test_all_wrapmethod_handlers_match_real_method_descriptors_and_static_flags(self):
        checked = 0
        for adapter, (dependency, targets) in TARGETS.items():
            if JARS[dependency] is None:
                continue
            text = source(adapter)
            imports = {path.rsplit(".", 1)[1]: path for path in re.findall(r"^import ([\w.]+);", text, re.M)}
            for annotation, declaration in annotations(text, "WrapMethod"):
                method = re.search(r'method = "([^"]+)"', annotation).group(1)
                is_static, result, args = declaration_parts(declaration, imports)
                self.assertTrue(args[-1].startswith("Operation<"), adapter)
                desc = "(" + "".join(descriptor(arg, imports) for arg in args[:-1]) + ")" + result
                for target in targets:
                    members = self.load(dependency, target)
                    with self.subTest(adapter=adapter, target=target, method=method):
                        self.assertEqual((method, desc), members.method(method)[0],
                                         "Name-only selectors must remain unambiguous in this exact version")
                        self.assertEqual(is_static, bool(members.methods[(method, desc)][0] & 0x0008))
                    checked += 1
        self.assertGreater(checked, 0, "No dependency bytecode was checked")

    def test_shadow_field_types_visibility_final_static_flags_and_methods_are_exact(self):
        for adapter, (dependency, targets) in TARGETS.items():
            if JARS[dependency] is None:
                continue
            text = source(adapter)
            imports = {path.rsplit(".", 1)[1]: path for path in re.findall(r"^import ([\w.]+);", text, re.M)}
            for final, visibility, field_type, field_name in re.findall(
                    r"@Shadow\s+(@Final\s+)?(private|protected|public)\s+([^;()]+?)\s+(\w+);", text):
                for target in targets:
                    with self.subTest(adapter=adapter, field=field_name):
                        fields = self.load(dependency, target).fields
                        key = (field_name, descriptor(field_type, imports))
                        self.assertIn(key, fields)
                        access = fields[key][0]
                        self.assertEqual({"public": 1, "private": 2, "protected": 4}[visibility], access & 7)
                        self.assertEqual(bool(final), bool(access & 0x0010))
                        self.assertFalse(access & 0x0008, "These shadows are instance fields")
            if "@Shadow public abstract void setWorking(boolean working);" in text:
                access = self.load(dependency, targets[0]).methods[("setWorking", "(Z)V")][0]
                self.assertEqual(1, access & 7)
                self.assertFalse(access & 0x0008)

    def test_every_wrapoperation_targets_a_real_instruction_in_each_selected_method(self):
        for adapter, (dependency, targets) in TARGETS.items():
            if JARS[dependency] is None:
                continue
            text = source(adapter)
            imports = {path.rsplit(".", 1)[1]: path for path in re.findall(r"^import ([\w.]+);", text, re.M)}
            for annotation, declaration in annotations(text, "WrapOperation"):
                selected = re.search(r'method = ("[^"]+"|\{[^}]+\})', annotation).group(1)
                methods = re.findall(r'"([^"]+)"', selected)
                owner, member = re.search(r'target = "L([^;]+);([^"]+)"', annotation).groups()
                if ":" in member:
                    field, desc = member.split(":", 1)
                    reference = (owner, field, desc)
                    expected_result, expected_args = desc, ["L" + owner + ";"]
                else:
                    name, desc = member.split("(", 1)
                    reference = (owner, name, "(" + desc)
                    expected_result = desc.split(")", 1)[1]
                    expected_args = None
                _, handler_result, handler_args = declaration_parts(declaration, imports)
                if expected_result != handler_result:
                    self.assertIn("@Coerce", declaration.split("(", 1)[0], adapter)
                    self.assertEqual("Ljava/lang/Object;", handler_result, adapter)
                    self.assertTrue(expected_result.startswith("L"), adapter)
                self.assertTrue(handler_args[-1].startswith("Operation<"), adapter)
                for target in targets:
                    contract = self.load(dependency, target)
                    for method in methods:
                        with self.subTest(adapter=adapter, target=target, method=method):
                            matches = [(pos, op) for pos, op, ref in contract.instructions(method) if ref == reference]
                            if "opcode = Opcodes.GETFIELD" in annotation:
                                matches = [(pos, op) for pos, op in matches if op == 180]
                            ordinal = re.search(r"ordinal = (\d+)", annotation)
                            if ordinal:
                                selected_index = int(ordinal.group(1))
                                self.assertGreater(len(matches), selected_index, reference)
                                matches = [matches[selected_index]]
                            self.assertTrue(matches, reference)
                            if expected_args is None:
                                params = re.findall(r"\[*L[^;]+;|\[*[ZBCSIJFD]", desc.split(")", 1)[0])
                                args = params if matches[0][1] == 184 else ["L" + owner + ";"] + params
                            else:
                                self.assertTrue(all(op == 180 for _, op in matches))
                                args = expected_args
                            actual = [descriptor(arg, imports) for arg in handler_args[:-1]]
                            self.assertEqual(len(args), len(actual))
                            for declared, expected in zip(handler_args[:-1], args):
                                if "@Coerce Object" not in declared:
                                    self.assertEqual(expected, descriptor(declared, imports))

    def test_exact_jar_toml_mod_ids_versions_and_advancedae_hash(self):
        for dependency, mod_id, version in (
                ("ae2", "ae2", "19.2.18"),
                ("extendedae", "extendedae", "1.21-2.2.32-neoforge"),
                ("glodium", "glodium", "1.21-2.2-neoforge"),
                ("advancedae", "advanced_ae", "1.6.12-1.21.1")):
            with self.subTest(dependency=dependency):
                if JARS[dependency] is None:
                    self.skipTest(dependency + " exact inspection artifact not available")
                with ZipFile(JARS[dependency]) as jar:
                    text = jar.read("META-INF/neoforge.mods.toml").decode()
                    block = re.search(r"\[\[mods\]\](.*?)(?=\[\[|\Z)", text, re.S).group(1)
                    self.assertRegex(block, r'modId\s*=\s*"' + re.escape(mod_id) + '"')
                    self.assertRegex(block, r'version\s*=\s*"' + re.escape(version) + '"')
        if JARS["advancedae"]:
            self.assertEqual("9358ccfa5477c7ab1c5ffab6c831e105fe46ecc3",
                             hashlib.sha1(JARS["advancedae"].read_bytes()).hexdigest())

    def test_ae_terminal_checks_findrecipe_before_network_extraction_and_makeitem(self):
        contract = self.load("ae2", AE + "menu/slot/CraftingTermSlot")
        calls = [ref[1] for ref in contract.references("craftItem")]
        self.assertLess(calls.index("findRecipe"), calls.index("extractItemsByRecipe"))
        self.assertLess(calls.index("findRecipe"), calls.index("makeItem"))
        self.assertLess(calls.index("findRecipe"), calls.index("postCraft"))
        self.assertIn("getCurrentRecipe", [ref[1] for ref in contract.references("findRecipe")])
        self.assertIn("getCurrentRecipe", [ref[1] for ref in contract.references("getRemainingItems")])

    def test_inscribers_both_execute_from_gettask_and_have_real_cached_fields(self):
        for dependency, target, tick in (
                ("ae2", AE + "blockentity/misc/InscriberBlockEntity", "tickingRequest"),
                ("extendedae", EAE + "common/me/InscriberThread", "tick")):
            contract = self.load(dependency, target)
            calls = [ref[1] for ref in contract.references(tick)]
            self.assertGreaterEqual(calls.count("getTask"), 2)
            self.assertLess(calls.index("getTask"), calls.index("extractItem"))
            self.assertIn(("cachedTask", "Lappeng/recipes/handlers/InscriberRecipe;"), contract.fields)
            work = [ref[1] for ref in contract.references("hasCraftWork")]
            self.assertLess(work.index("getTask"), work.index("insertItem"))

    def test_assembler_actual_assembly_is_single_call_after_power_before_output_and_not_has_mats(self):
        for dependency, target, admission, tick, assembly in (
                ("ae2", AE + "blockentity/crafting/MolecularAssemblerBlockEntity", "pushPattern", "tickingRequest", "assemble"),
                ("extendedae", EAE + "common/me/CraftingThread", "acceptJob", "tick", "assemblePattern")):
            with self.subTest(dependency=dependency):
                contract = self.load(dependency, target)
                instructions = list(contract.instructions(tick))
                calls = [(i, ref[1]) for i, (_, _, ref) in enumerate(instructions) if ref]
                assemble_at = [i for i, name in calls if name == assembly]
                self.assertEqual(1, len(assemble_at), "Burn exactly once at the actual, virtual assembly call")
                power_at = [i for i, name in calls if name == "userPower"]
                self.assertTrue(all(i < assemble_at[0] for i in power_at))
                outputs = [i for i, name in calls if name == "pushOut"]
                self.assertEqual(2, len(outputs), "One early ejection branch and one successful-assembly output")
                self.assertLess(outputs[0], power_at[0])
                self.assertTrue(any(op == 176 for _, op, _ in instructions[outputs[0]:power_at[0]]),
                                "Ejection must return before power or another craft")
                remainder_at = next(i for i, name in calls if name == "getRemainingItems")
                self.assertLess(assemble_at[0], remainder_at)
                self.assertLess(remainder_at, outputs[1])
                self.assertIn("assemble", [ref[1] for ref in contract.references("hasMats")],
                              "Trial assembly is outside the wrapped actual tick instruction")
                accepted = list(contract.instructions(admission))
                success = [i for i, (_, op, _) in enumerate(accepted)
                           if op == 172 and i and accepted[i - 1][1] == 4]
                self.assertEqual(1, len(success))
                for name in ("fillGrid", "updateSleepiness", "saveChanges"):
                    self.assertLess(next(i for i, (_, _, ref) in enumerate(accepted) if ref and ref[1] == name), success[0])

    def test_assembler_native_plan_and_input_io_stays_in_same_host_save_load(self):
        ae = self.load("ae2", AE + "blockentity/crafting/MolecularAssemblerBlockEntity")
        self.assertIn("forcePlan", [ref[1] for ref in ae.references("saveAdditional")])
        self.assertIn("recalculatePlan", [ref[1] for ref in ae.references("loadTag")])
        inv = self.load("ae2", AE + "blockentity/AEBaseInvBlockEntity")
        self.assertIn("getInternalInventory", [ref[1] for ref in inv.references("saveAdditional")])
        self.assertIn("saveOptional", [ref[1] for ref in inv.references("saveAdditional")])
        self.assertIn("parseOptional", [ref[1] for ref in inv.references("loadTag")])
        self.assertIn("setItemDirect", [ref[1] for ref in inv.references("loadTag")])
        for host in ("TileExMolecularAssembler", "matrix/TileAssemblerMatrixCrafter"):
            contract = self.load("extendedae", EAE + "common/tileentities/" + host)
            self.assertIn("writeNBT", [ref[1] for ref in contract.references("saveAdditional")])
            reads = [ref[1] for ref in contract.references("loadTag")]
            self.assertIn("readNBT", reads)
            if host.startswith("matrix/"):
                self.assertLess(reads.index("readNBT"), reads.index("setItemDirect"),
                                "A restored matrix thread must not require its inputs before the host loads them")
        thread = self.load("extendedae", EAE + "common/me/CraftingThread")
        self.assertIn("getDefinition", [ref[1] for ref in thread.references("writeNBT")])
        self.assertIn("recalculatePlan", [ref[1] for ref in thread.references("readNBT")])

    def test_extended_matrix_cached_output_still_uses_guarded_base_tick(self):
        matrix = self.load("extendedae", EAE + "common/me/CraftingMatrixThread")
        self.assertIn((EAE + "common/me/CraftingThread", "tick", "(II)Lappeng/api/networking/ticking/TickRateModulation;"),
                      matrix.references("tick"))
        self.assertIn("getCraftRecipeResult", [ref[1] for ref in matrix.references("assemblePattern")])
        base = self.load("extendedae", EAE + "common/me/CraftingThread")
        calls = [ref[1] for ref in base.references("tick")]
        self.assertLess(calls.index("userPower"), calls.index("assemblePattern"))
        for target in ("TileExMolecularAssembler", "matrix/TileAssemblerMatrixCrafter"):
            host = self.load("extendedae", EAE + "common/tileentities/" + target)
            self.assertIn("tick", [ref[1] for ref in host.references("tickingRequest")])

    def test_extended_executor_uses_cached_holder_before_progress_and_output(self):
        executor = self.load("extendedae", EAE + "util/RecipeExecutor")
        calls = [ref[1] for ref in executor.references("execute")]
        self.assertLess(calls.index("currentRecipe"), calls.index("setWorking"))
        self.assertLess(calls.index("currentRecipe"), calls.index("addProgress"))
        self.assertLess(calls.index("currentRecipe"), calls.index("runRecipe"))

    def test_reaction_injection_has_exact_optional_recipe_return_and_precedes_outputs(self):
        chamber = self.load("advancedae", AAE + "common/entities/ReactionChamberEntity")
        self.assertIn(("getTask", "()Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;"), chamber.methods)
        calls = [ref[1] for ref in chamber.references("tickingRequest")]
        self.assertIn("getTask", calls)
        self.assertLess(calls.index("getTask"), calls.index("insertItem"))
        self.assertLess(calls.index("getTask"), calls.index("getValidInputs"))
        work = [ref[1] for ref in chamber.references("hasCraftWork")]
        self.assertIn("isWorking", work)

    def test_gettask_raw_caches_only_query_when_null_and_new_reaction_hook_hits_that_guard(self):
        for dependency, target, desc in (
                ("ae2", AE + "blockentity/misc/InscriberBlockEntity", "Lappeng/recipes/handlers/InscriberRecipe;"),
                ("extendedae", EAE + "common/me/InscriberThread", "Lappeng/recipes/handlers/InscriberRecipe;"),
                ("advancedae", AAE + "common/entities/ReactionChamberEntity",
                 "Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;")):
            contract = self.load(dependency, target)
            instructions = list(contract.instructions("getTask"))
            reads = [i for i, (_, op, ref) in enumerate(instructions)
                     if op == 180 and ref == (target, "cachedTask", desc)]
            self.assertEqual(2, len(reads))
            self.assertEqual(199, instructions[reads[0] + 1][1], "First cachedTask read must be IFNONNULL")
            writes = [i for i, (_, op, ref) in enumerate(instructions)
                      if op == 181 and ref == (target, "cachedTask", desc)]
            queries = [i for i, (_, _, ref) in enumerate(instructions) if ref and ref[1] == "findRecipe"]
            self.assertEqual(1, len(writes))
            self.assertEqual(1, len(queries))
            self.assertLess(reads[0], queries[0])
            self.assertLess(queries[0], writes[0])
            self.assertLess(writes[0], reads[1])
            self.assertEqual(2, contract.fields[("cachedTask", desc)][0] & 7)
        text = source("RecipeLockAdvancedReactionChamberMixin")
        hook = next(ann for ann, _ in annotations(text, "WrapOperation") if 'method = "getTask"' in ann)
        self.assertIn("opcode = Opcodes.GETFIELD, ordinal = 0", hook)
        self.assertIn("cachedTask:Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;", hook)

    def test_reaction_return_callback_uses_erased_callback_only_not_a_mismatched_recipe_descriptor(self):
        text = source("RecipeLockAdvancedReactionChamberMixin")
        annotations_and_handlers = list(annotations(text, "Inject"))
        self.assertEqual(1, len(annotations_and_handlers))
        annotation, declaration = annotations_and_handlers[0]
        self.assertIn('method = "getTask"', annotation)
        self.assertIn('@At("RETURN")', annotation)
        self.assertIn("cancellable = true", annotation)
        self.assertIn("void recipeLock$checkQueryAndCachedTask(CallbackInfoReturnable<Recipe<?>> cir)", declaration)
        target = self.load("advancedae", AAE + "common/entities/ReactionChamberEntity")
        self.assertEqual(("getTask", "()Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;"),
                         target.method("getTask")[0])
        recipe = self.load("advancedae", AAE + "recipes/ReactionChamberRecipe")
        self.assertIn("net/minecraft/world/item/crafting/Recipe", recipe.interfaces)

    def test_real_terminal_menu_exposes_action_source_and_wireless_inherits_guarded_menu(self):
        base = self.load("ae2", AE + "menu/AEBaseMenu")
        self.assertEqual(1, base.methods[("getActionSource", "()Lappeng/api/networking/security/IActionSource;")][0] & 7)
        wireless = self.load("ae2", AE + "menu/me/items/WirelessCraftingTermMenu")
        self.assertEqual(AE + "menu/me/items/CraftingTermMenu", wireless.super_name)
        core = (ROOT / "src/main/java/com/atir/molecularmanipulator/recipelock/RecipeLocks.java").read_text(encoding="utf-8")
        player_grid = core[core.index("IGrid playerGrid("):core.index("public static boolean allowed(BlockEntity")]
        self.assertIn("player.containerMenu instanceof CraftingTermMenu terminal", player_grid)
        self.assertIn("terminal.getActionSource().machine().orElse(null)", player_grid)
        self.assertIn("activeGrid(host.getActionableNode())", player_grid)
        self.assertNotIn("getBlockPos", player_grid, "A real open terminal, not a nearby cable, supplies the grid")

    def test_quantum_first_pattern_reads_are_null_guards_before_any_powered_extraction(self):
        quantum = self.load("advancedae", AAE + "common/entities/QuantumCrafterEntity")
        field = (AAE + "common/entities/QuantumCrafterEntity$CraftingJob", "pattern", "Lappeng/crafting/pattern/AECraftingPattern;")
        for method in ("maximumCraftableAmount", "performCraft"):
            instructions = list(quantum.instructions(method))
            index = next(i for i, (_, op, ref) in enumerate(instructions) if op == 180 and ref == field)
            self.assertIn(instructions[index + 1][1], (198, 199), "The wrapped read must be a null check")
            if method == "performCraft":
                extraction = next(i for i, (_, _, ref) in enumerate(instructions)
                                  if ref and ref[1] == "poweredExtraction")
                self.assertLess(index, extraction)


if __name__ == "__main__":
    unittest.main()
