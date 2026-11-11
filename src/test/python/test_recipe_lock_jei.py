"""Standalone JEI recipe-lock source/bytecode regressions; no network, Gradle, or JDK needed.

Run: python -B src/test/python/test_recipe_lock_jei.py -v
Fixtures use these environment overrides, or exact-version NeoForge JEI Gradle cache entries
under GRADLE_USER_HOME (default: Path.home() / ".gradle"):
  RECIPE_LOCK_JEI_OLD_JAR: JEI 19.27.0.340
  RECIPE_LOCK_JEI_INSTALLED_JAR: JEI 19.56.0.441
  RECIPE_LOCK_JEI_LATEST_JAR: JEI 19.70.0.469
Missing cached jars are explicit per-version skips; overrides must exist and present jars must validate.
No fixtures are downloaded or changed. These six static tests do NOT perform Mixin transformation,
JVM linking, or tooltip behavior tests. For actual Mixin transformation and Java 21 linking, use
python -B src/test/python/run_jei_mixin_smoke.py --runtime all (requested runtimes must be available).
"""

import json
import os
from pathlib import Path
import re
import struct
import unittest
from zipfile import BadZipFile, ZipFile

from test_recipe_lock_ae_adapters import (
    ClassContract, ROOT, annotations, cached_jar, declaration_parts, descriptor, source,
)


SLOT = "mezz/jei/library/gui/ingredients/RecipeSlot"
LAYOUT = "mezz/jei/library/gui/recipes/RecipeLayout"
CATEGORY = "mezz/jei/api/recipe/category/IRecipeCategory"
VIEW = "mezz/jei/api/gui/ingredient/IRecipeSlotView"
DRAWABLE = "mezz/jei/api/gui/ingredient/IRecipeSlotDrawable"
BUILDER = "Lmezz/jei/api/gui/builder/ITooltipBuilder;"
TYPED = "Lmezz/jei/api/ingredients/ITypedIngredient;"
DRAW_DESC = "(Lnet/minecraft/client/gui/GuiGraphics;II)V"
LEGACY = ("getTooltip", "(" + BUILDER + TYPED + ")V")
MODERN = ("addTooltip", "(" + BUILDER + ")V")
CATEGORY_TOOLTIP = ("getTooltip", "(" + BUILDER + "Ljava/lang/Object;"
                    "Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;DD)V")
CATEGORY_TARGET = "L" + CATEGORY + ";" + "".join(CATEGORY_TOOLTIP)
RICH_CALLBACK = ("mezz/jei/api/gui/ingredient/IRecipeSlotRichTooltipCallback", "onRichTooltip",
                 "(L" + VIEW + ";" + BUILDER + ")V")
RUNTIMES = {
    "19.27.0.340": ("RECIPE_LOCK_JEI_OLD_JAR",
                    cached_jar("mezz.jei", "jei-1.21.1-neoforge", "19.27.0.340"), LEGACY),
    "19.56.0.441": ("RECIPE_LOCK_JEI_INSTALLED_JAR",
                    cached_jar("mezz.jei", "jei-1.21.1-neoforge", "19.56.0.441"), MODERN),
    "19.70.0.469": ("RECIPE_LOCK_JEI_LATEST_JAR",
                    cached_jar("mezz.jei", "jei-1.21.1-neoforge", "19.70.0.469"), MODERN),
}


def selector(annotation):
    return re.search(r'method\s*=\s*"([^"]+)"', annotation).group(1)


def method_key(selected):
    name, desc = selected.split("(", 1)
    return name, "(" + desc


def imports_for(text):
    return {**{path.rsplit(".", 1)[1]: path for path in re.findall(r"^import ([\w.]+);", text, re.M)},
            "R": "java.lang.Object"}  # RecipeLayout's erased recipe type.


def handler_signature(text, declaration):
    imports = imports_for(text)
    static, result, args = declaration_parts(declaration, imports)
    return static, "(" + "".join(descriptor(arg, imports) for arg in args[:-1]) + ")" + result, args[-1]


def body(text, declaration):
    start = text.index("{", text.index(declaration) + len(declaration))
    depth, end = 1, start + 1
    while depth:
        depth += (text[end] == "{") - (text[end] == "}")
        end += 1
    return " ".join(text[start + 1:end - 1].split())


class JeiContract(ClassContract):
    """Reuse the class reader, adding only exact overloads and lambda bootstrap edges."""

    def __init__(self, data):
        super().__init__(data)
        self.bootstraps = []
        for _ in range(self.u2()):
            attribute, length = self.utf(self.u2()), self.u4()
            end = self.pos + length
            if attribute == "BootstrapMethods":
                for _ in range(self.u2()):
                    handle = self.u2()
                    self.bootstraps.append((handle, [self.u2() for _ in range(self.u2())]))
            self.pos = end

    def method(self, key):
        return (key, self.methods[key]) if isinstance(key, tuple) else super().method(key)

    def calls(self, key):
        code = self.methods[key][1]
        for pos, op, ref in self.instructions(key):
            if 182 <= op <= 185:
                yield pos, ref
            elif op == 186:
                dynamic = self.cp[struct.unpack_from(">H", code, pos + 1)[0]][1]
                bootstrap, args = self.bootstraps[dynamic[0]]
                bootstrap_ref = self.ref(struct.unpack_from(">H", self.cp[bootstrap][1], 1)[0])
                if bootstrap_ref[0] != "java/lang/invoke/LambdaMetafactory":
                    continue
                for argument in args:
                    tag, value = self.cp[argument]
                    if tag == 15:  # The actual lambda implementation handle, not a name-based guess.
                        yield pos, self.ref(struct.unpack_from(">H", value, 1)[0])

    def path_to(self, start, target):
        pending, seen = [(start, [start])], set()
        while pending:
            key, path = pending.pop()
            if key == target:
                return path
            if key in seen:
                continue
            seen.add(key)
            for _, (owner, name, desc) in self.calls(key):
                called = (name, desc)
                if owner == self.name and called in self.methods:
                    pending.append((called, path + [called]))
        return []


class JeiWiringTests(unittest.TestCase):
    def test_slot_hooks_keep_exact_selectors_fail_loud_group_and_output_only_real_append(self):
        text = source("JeiRecipeLockSlotMixin")
        hooks, groups = list(annotations(text, "Inject")), list(annotations(text, "Group"))
        self.assertEqual(2, len(hooks))
        self.assertEqual(2, len(groups))
        self.assertCountEqual([LEGACY, MODERN], [method_key(selector(ann)) for ann, _ in hooks])
        for (ann, declaration), (group, grouped_declaration) in zip(hooks, groups):
            with self.subTest(hook=selector(ann)):
                self.assertTrue(grouped_declaration.endswith(declaration), "Group must bind to this injector")
                self.assertEqual('name = "molecularmanipulator$researchTooltip", min = 1, max = 1',
                                 " ".join(group.split()))
                self.assertRegex(ann, r'at\s*=\s*@At\("TAIL"\)')
                self.assertRegex(ann, r"\brequire\s*=\s*0\b")
                self.assertEqual((False, method_key(selector(ann))[1], "CallbackInfo"),
                                 handler_signature(text, declaration))
                self.assertEqual(
                    "if (((IRecipeSlotView) this).getRole() == RecipeIngredientRole.OUTPUT) { "
                    "RecipeLockTooltips.appendCurrentRecipe(tooltip::add); }", body(text, declaration))
        self.assertEqual(VIEW.replace("/", "."), imports_for(text)["IRecipeSlotView"])
        self.assertEqual("mezz.jei.api.recipe.RecipeIngredientRole", imports_for(text)["RecipeIngredientRole"])

    def test_layout_keeps_native_scoped_draw_and_appends_after_category_tooltip(self):
        text = source("JeiRecipeLockLayoutMixin")
        wraps, operations = list(annotations(text, "WrapMethod")), list(annotations(text, "WrapOperation"))
        self.assertEqual(1, len(wraps))
        self.assertEqual(1, len(operations))
        ann, declaration = wraps[0]
        self.assertEqual("drawOverlays", selector(ann))
        self.assertEqual((False, DRAW_DESC, "Operation<Void>"), handler_signature(text, declaration))
        self.assertIn("var recipe = getRecipe();", body(text, declaration))
        self.assertIn("try (var ignored = RecipeLockTooltips.recipeContext("
                      "getRecipeCategory().getRegistryName(recipe), recipe)) { "
                      "original.call(graphics, mouseX, mouseY); }", body(text, declaration))
        ann, declaration = operations[0]
        self.assertEqual("drawOverlays", selector(ann))
        self.assertIn('value = "INVOKE"', ann)
        self.assertEqual(CATEGORY_TARGET, re.search(r'target\s*=\s*"([^"]+)"', ann).group(1))
        self.assertEqual((False, "(L" + CATEGORY + ";" + CATEGORY_TOOLTIP[1][1:], "Operation<Void>"),
                         handler_signature(text, declaration))
        self.assertEqual("original.call(category, tooltip, recipe, slots, mouseX, mouseY); "
                         "RecipeLockTooltips.appendCurrentRecipe(tooltip::add);", body(text, declaration))

    def test_mixins_are_client_only_jei_gated_and_use_the_real_tooltip_helper(self):
        config = json.loads((ROOT / "src/main/resources/molecularmanipulator.mixins.json").read_text(encoding="utf-8"))
        self.assertTrue(config["required"])
        self.assertEqual(1, config["injectors"]["defaultRequire"])
        plugin = (ROOT / "src/main/java" / (config["plugin"].replace(".", "/") + ".java")).read_text(encoding="utf-8")
        for name, target in (("JeiRecipeLockSlotMixin", SLOT), ("JeiRecipeLockLayoutMixin", LAYOUT)):
            with self.subTest(mixin=name):
                self.assertEqual(1, config["client"].count(name))
                self.assertNotIn(name, config["mixins"] + config.get("server", []))
                text = source(name)
                mixin = list(annotations(text, "Mixin"))
                self.assertEqual(1, len(mixin))
                self.assertIn('targets = "' + target.replace("/", ".") + '"', mixin[0][0])
                self.assertIn("remap = false", mixin[0][0])
                self.assertEqual("com.atir.molecularmanipulator.client.RecipeLockTooltips",
                                 imports_for(text)["RecipeLockTooltips"])
                gates = [(condition, result) for condition, result in re.findall(
                    r"if\s*\(([^{}]+)\)\s*\{([^{}]+)\}", plugin)
                         if 'mixinClassName.endsWith(".' + name + '")' in condition]
                self.assertEqual(1, len(gates))
                self.assertEqual('return loadingModList != null && '
                                 'loadingModList.getModFileById("jei") != null;', " ".join(gates[0][1].split()))


class JeiRuntimeContractTests(unittest.TestCase):
    def fixture(self, version):
        env, fallback, _ = RUNTIMES[version]
        configured = os.environ.get(env)
        if configured is not None and not configured.strip():
            self.fail(f"{env} is empty; set it to the JEI {version} JAR or unset it to use the cache")
        path = Path(configured).expanduser() if configured is not None else fallback
        if path is None or not path.is_file():
            message = f"JEI {version} fixture absent: {path}; set {env} to an existing JAR"
            if configured is not None:
                self.fail(message)
            self.skipTest(message)
        try:
            with ZipFile(path) as jar:
                self.assertIsNone(jar.testzip(), f"JEI {version} fixture failed its ZIP CRC check: {path}")
                metadata = jar.read("META-INF/neoforge.mods.toml").decode("utf-8")
                mods = [block for block in re.findall(r"\[\[mods\]\](.*?)(?=\[\[|\Z)", metadata, re.S)
                        if re.search(r'modId\s*=\s*"jei"', block)]
                self.assertEqual(1, len(mods), f"Not a NeoForge JEI fixture: {path}")
                self.assertRegex(mods[0], r'version\s*=\s*"' + re.escape(version) + '"')
                return {name: JeiContract(jar.read(name + ".class"))
                        for name in (SLOT, LAYOUT, CATEGORY, VIEW, DRAWABLE)}
        except (BadZipFile, OSError) as error:
            self.fail(f"JEI {version} fixture incomplete/unreadable ZIP: {path}; {error}; set {env}")

    def test_exactly_one_slot_hook_matches_and_native_draw_reaches_it_including_real_lambdas(self):
        hooks = [method_key(selector(ann)) for ann, _ in annotations(source("JeiRecipeLockSlotMixin"), "Inject")]
        for version, (_, _, expected) in RUNTIMES.items():
            with self.subTest(version=version):
                slot = self.fixture(version)[SLOT]
                matches = [hook for hook in hooks if hook in slot.methods]
                self.assertEqual([expected], matches, "Group must match one hook, never zero or two")
                self.assertEqual(2, slot.methods[expected][0] & 15, "Expected a private instance tooltip method")
                self.assertEqual(("drawTooltip", DRAW_DESC), slot.method("drawTooltip")[0])
                path = slot.path_to(("drawTooltip", DRAW_DESC), expected)
                self.assertTrue(path, "Existing target alone is insufficient: native hover must reach it")
                if expected == LEGACY:
                    self.assertTrue(any(key[0].startswith("lambda$drawTooltip$") for key in path))
                else:
                    self.assertEqual([("drawTooltip", DRAW_DESC), expected], path)
                self.assertIn(VIEW, slot.interfaces)
                self.assertIn(("getRole", "()Lmezz/jei/api/recipe/RecipeIngredientRole;"), slot.methods)

    def test_chosen_tail_is_after_native_rich_tooltip_callbacks_not_the_new_ingredient_only_helper(self):
        for version, (_, _, selected) in RUNTIMES.items():
            with self.subTest(version=version):
                slot = self.fixture(version)[SLOT]
                instructions = list(slot.instructions(selected))
                callbacks = [pos for pos, op, ref in instructions if op == 185 and ref == RICH_CALLBACK]
                returns = [pos for pos, op, _ in instructions if op == 177]
                self.assertEqual(1, len(callbacks))
                self.assertEqual([instructions[-1][0]], returns, "Single final TAIL after the callback loop")
                self.assertLess(callbacks[0], returns[0])
                if selected == MODERN:
                    ingredient_only = ("addIngredientTooltip", "(" + BUILDER + TYPED + ")V")
                    self.assertTrue(slot.path_to(selected, ingredient_only))
                    self.assertNotIn(RICH_CALLBACK, slot.references(ingredient_only))

    def test_layout_descriptors_shadows_and_category_invoke_are_valid_in_every_runtime(self):
        text = source("JeiRecipeLockLayoutMixin")
        shadows = {name: "()" + descriptor(kind, imports_for(text)) for kind, name in re.findall(
            r"@Shadow\s+public abstract (\S+) (\w+)\(\);", text)}
        self.assertEqual({"getRecipeCategory": "()L" + CATEGORY + ";", "getRecipe": "()Ljava/lang/Object;"}, shadows)
        for version in RUNTIMES:
            with self.subTest(version=version):
                contracts = self.fixture(version)
                layout, category = contracts[LAYOUT], contracts[CATEGORY]
                self.assertEqual(("drawOverlays", DRAW_DESC), layout.method("drawOverlays")[0])
                for name, desc in shadows.items():
                    self.assertEqual((name, desc), layout.method(name)[0])
                    self.assertEqual(1, layout.methods[(name, desc)][0] & 15, "Public instance shadow")
                invokes = [ref for _, op, ref in layout.instructions("drawOverlays") if op == 185]
                self.assertEqual(1, invokes.count((CATEGORY,) + CATEGORY_TOOLTIP))
                self.assertIn((DRAWABLE, "drawTooltip", DRAW_DESC), invokes)
                self.assertEqual(1, category.methods[CATEGORY_TOOLTIP][0] & 15)
                registry_name = ("getRegistryName", "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;")
                self.assertEqual(1, category.methods[registry_name][0] & 15)
                self.assertIn(("drawTooltip", DRAW_DESC), contracts[DRAWABLE].methods)
                self.assertIn(("getRole", "()Lmezz/jei/api/recipe/RecipeIngredientRole;"), contracts[VIEW].methods)


if __name__ == "__main__":
    unittest.main()
