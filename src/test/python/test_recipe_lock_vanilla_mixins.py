"""Standalone wiring/signature regressions; no RecipeLocks implementation or Gradle build needed.

Run: python src/test/python/test_recipe_lock_vanilla_mixins.py -v
These are source/bytecode contract checks, not a substitute for transformed-server GameTests.
"""

from pathlib import Path
import re
import struct
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[3]
MIXINS = ROOT / "src/main/java/com/atir/molecularmanipulator/mixin"
ARTIFACT = ROOT / "build/moddev/artifacts/neoforge-21.1.220.jar"
TARGETS = {
    "RecipeLockRecipeManagerMixin": ("net/minecraft/world/item/crafting/RecipeManager",),
    "RecipeLockCraftingMenuMixin": ("net/minecraft/world/inventory/CraftingMenu",),
    "RecipeLockQuickMoveMixin": tuple("net/minecraft/world/inventory/" + name for name in (
        "CraftingMenu", "InventoryMenu", "StonecutterMenu", "ItemCombinerMenu")),
    "RecipeLockSlotMixin": ("net/minecraft/world/inventory/Slot",),
    "RecipeLockResultSlotMixin": ("net/minecraft/world/inventory/ResultSlot",),
    "RecipeLockServerPlaceRecipeMixin": ("net/minecraft/recipebook/ServerPlaceRecipe",),
    "RecipeLockFurnaceMixin": ("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity",),
    "RecipeLockStonecutterMenuMixin": ("net/minecraft/world/inventory/StonecutterMenu",),
    "RecipeLockSmithingMenuMixin": ("net/minecraft/world/inventory/SmithingMenu",),
    "RecipeLockCampfireMixin": ("net/minecraft/world/level/block/entity/CampfireBlockEntity",),
    "RecipeLockCrafterMixin": ("net/minecraft/world/level/block/CrafterBlock",),
}


def source(name):
    return (MIXINS / (name + ".java")).read_text(encoding="utf-8")


def split_types(text):
    parts, start, depth = [], 0, 0
    for index, char in enumerate(text):
        depth += (char == "<") - (char == ">")
        if char == "," and depth == 0:
            parts.append(text[start:index].strip())
            start = index + 1
    parts.append(text[start:].strip())
    return parts


def java_descriptor(type_name, imports):
    erased = type_name.split("<", 1)[0].strip()
    primitives = {"void": "V", "boolean": "Z", "int": "I"}
    if erased in primitives:
        return primitives[erased]
    if erased == "I":  # Generic input parameter of RecipeManager.getRecipeFor.
        erased = "RecipeInput"
    return "L" + imports[erased].replace(".", "/") + ";"


class ClassMembers:
    """Read just class member descriptors, without starting Minecraft or requiring a JDK."""

    def __init__(self, data):
        self.data, self.offset = data, 8  # magic, minor, major
        constant_count = self.u2()
        constants = [None] * constant_count
        index = 1
        sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
                 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
        while index < constant_count:
            tag = self.u1()
            if tag == 1:
                length = self.u2()
                constants[index] = data[self.offset:self.offset + length].decode("utf-8", errors="replace")
                self.offset += length
            else:
                self.offset += sizes[tag]
            index += 2 if tag in (5, 6) else 1
        self.offset += 6  # access, this_class, super_class
        interface_count = self.u2()
        self.offset += 2 * interface_count
        self.fields = self.members(constants)
        self.methods = self.members(constants)

    def u1(self):
        value = self.data[self.offset]
        self.offset += 1
        return value

    def u2(self):
        value = struct.unpack_from(">H", self.data, self.offset)[0]
        self.offset += 2
        return value

    def u4(self):
        value = struct.unpack_from(">I", self.data, self.offset)[0]
        self.offset += 4
        return value

    def members(self, constants):
        result = {}
        for _ in range(self.u2()):
            access, name, descriptor = self.u2(), self.u2(), self.u2()
            result[(constants[name], constants[descriptor])] = access
            for _ in range(self.u2()):
                self.u2()  # attribute name
                length = self.u4()
                self.offset += length
        return result


class RecipeLockVanillaWiringTest(unittest.TestCase):
    def test_all_mixins_use_only_the_promised_permission_api_and_no_overwrites(self):
        self.assertLessEqual(set(TARGETS), {path.stem for path in MIXINS.glob("RecipeLock*Mixin.java")})
        for name in TARGETS:
            text = source(name)
            self.assertNotIn("@Overwrite", text, name)
            self.assertNotIn("@Redirect", text, name)
            self.assertNotIn("ThreadLocal", text, name)
            calls = set(re.findall(r"RecipeLocks\.(\w+)\(", text))
            self.assertLessEqual(calls, {"hasRules", "allowed", "allowedForPlayer", "allowedInContext"}, name)
            if "RecipeExecutionContext.open(" in text:
                self.assertEqual(text.count("RecipeExecutionContext.open("),
                                 text.count("try (var scope = RecipeExecutionContext.open("), name)

    def test_manager_filters_only_execution_lookup_and_tries_later_candidates(self):
        text = source("RecipeLockRecipeManagerMixin")
        selectors = re.findall(r'@WrapMethod\(method = "([^"]+)"', text)
        self.assertEqual(1, len(selectors))
        self.assertTrue(selectors[0].startswith("getRecipeFor("))
        self.assertIn("RecipeHolder;)", selectors[0])
        for gate in ("level.isClientSide", "!RecipeExecutionContext.active()", "!RecipeLocks.hasRules(level)"):
            self.assertIn(gate, text)
        tail = text[text.index(".getAllRecipesFor(type)"):]
        self.assertIn("candidate.value().matches(input, level)", tail)
        self.assertIn("RecipeLocks.allowedInContext(level, candidate)", tail)
        self.assertIn(".findFirst()", tail)
        self.assertNotIn("byKey(", text)
        self.assertNotIn("getRecipes()", text)

    def test_inventory_and_table_share_scoped_preview_and_guard_shift_transfer(self):
        self.assertIn('@WrapMethod(method = "slotChangedCraftingGrid")', source("RecipeLockCraftingMenuMixin"))
        text = source("RecipeLockQuickMoveMixin")
        for target in ("CraftingMenu.class", "InventoryMenu.class", "StonecutterMenu.class", "ItemCombinerMenu.class"):
            self.assertIn(target, text)
        self.assertIn("index == resultIndex", text)
        self.assertIn("recipe == null || !RecipeLocks.allowedForPlayer", text)
        self.assertLess(text.index("!RecipeLocks.allowedForPlayer"), text.rindex("return original.call(player, index)"))
        self.assertIn("menu instanceof SmithingMenu", text)

    def test_normal_pickup_checks_stored_recipe_without_locking_furnace_outputs(self):
        text = source("RecipeLockSlotMixin")
        self.assertIn("this.container instanceof ResultContainer result", text)
        self.assertIn("RecipeLocks.allowedForPlayer(player, recipe)", text)
        self.assertLess(text.index("return false;"), text.index("return original.call(player)"))
        self.assertNotIn("AbstractFurnaceBlockEntity", text)

    def test_result_slot_keeps_identity_after_vanilla_award_clears_recipe_used(self):
        text = source("RecipeLockResultSlotMixin")
        for selector in ("remove", "checkTakeAchievements", "onTake"):
            self.assertIn(f'@WrapMethod(method = "{selector}")', text)
        self.assertIn("!slot.mayPickup(this.player)", text)
        remember = text[text.index("private void recipeLock$rememberBeforeAward"):text.index('@WrapMethod(method = "onTake")')]
        self.assertLess(remember.index("this.recipeLock$takingRecipe = recipe;"), remember.index("original.call(stack)"))
        take = text[text.index("private void recipeLock$checkBeforeConsume"):]
        self.assertLess(take.index("!RecipeLocks.allowedForPlayer"), take.index("original.call(player, stack)"))
        self.assertIn("finally {\n            this.recipeLock$takingRecipe = null;", take)

    def test_recipe_book_denial_happens_before_grid_or_inventory_changes(self):
        text = source("RecipeLockServerPlaceRecipeMixin")
        self.assertLess(text.index("!RecipeLocks.allowedForPlayer"), text.index("original.call(player, recipe, placeAll)"))

    def test_furnace_checks_ignition_and_direct_burn_in_shared_base(self):
        text = source("RecipeLockFurnaceMixin")
        for selector in ("serverTick", "getTotalCookTime", "canBurn"):
            self.assertIn(f'@WrapMethod(method = "{selector}")', text)
        check = text[text.index("private static boolean recipeLock$checkBeforeFuelOrInput"):]
        self.assertLess(check.index("!RecipeLocks.allowed(furnace, recipe)"), check.index("return original.call("))

    def test_stonecutter_preserves_recipe_list_indices_but_blocks_selected_output(self):
        text = source("RecipeLockStonecutterMenuMixin")
        self.assertIn("inventory.player", text)
        self.assertIn("this.recipes.get(index)", text)
        self.assertIn("this.resultSlot.set(ItemStack.EMPTY)", text)
        self.assertNotIn(".filter(", text)
        self.assertNotIn("this.recipes =", text)

    def test_smithing_filters_all_local_candidates_and_rechecks_take(self):
        text = source("RecipeLockSmithingMenuMixin")
        self.assertIn('@WrapOperation(method = "createResult"', text)
        self.assertIn("candidates.stream().filter(recipe -> RecipeLocks.allowedForPlayer(this.player, recipe)).toList()", text)
        for selector in ("createResult", "mayPickup", "onTake"):
            self.assertIn(f'@WrapMethod(method = "{selector}")', text)
        self.assertIn("this.selectedRecipe = null", text)
        self.assertIn("this.resultSlots.setRecipeUsed(null)", text)

    def test_campfire_checks_placement_and_skips_locked_slots_before_progress(self):
        text = source("RecipeLockCampfireMixin")
        for selector in ("getCookableRecipe", "placeFood", "cookTick"):
            self.assertIn(f'@WrapMethod(method = "{selector}")', text)
        self.assertIn("return selected.isPresent() && original.call(entity, food,", text)
        self.assertIn("RecipeLocks.allowedForPlayer(player, recipe)", text)
        self.assertIn("target = \"Lnet/minecraft/world/item/ItemStack;isEmpty()Z\"", text)
        self.assertIn(".filter(recipe -> RecipeLocks.allowed(campfire, recipe))\n                .isEmpty()", text)
        self.assertNotIn(".orElse(stack)", text)

    def test_crafter_bypasses_cache_only_at_execution_not_shared_previews(self):
        text = source("RecipeLockCrafterMixin")
        self.assertIn('@WrapMethod(method = "dispenseFrom")', text)
        self.assertIn('@WrapOperation(method = "dispenseFrom"', text)
        self.assertNotIn('@WrapMethod(method = "getPotentialResults")', text)
        self.assertIn("getRecipeFor(RecipeType.CRAFTING, input, level)", text)
        self.assertIn(".filter(recipe -> RecipeLocks.allowed(crafter, recipe))", text)
        self.assertNotIn("RECIPE_CACHE.get(", text)
        self.assertNotIn(".insert(", text)

    @unittest.skipUnless(ARTIFACT.exists(), "NeoForge development artifact is not present")
    def test_wrapmethod_handler_signatures_and_static_flags_match_actual_neoforge_bytecode(self):
        with ZipFile(ARTIFACT) as jar:
            for name, targets in TARGETS.items():
                text = source(name)
                imports = {path.rsplit(".", 1)[1]: path for path in re.findall(r"^import ([\w.]+);", text, re.M)}
                for annotation in re.finditer(r'@WrapMethod\(method = "([^"]+)"\)', text):
                    selector = annotation.group(1)
                    declaration = text[annotation.end():text.index("{", annotation.end())].strip()
                    head, args = declaration.split("(", 1)
                    args = args.rsplit(")", 1)[0]
                    head = re.sub(r"^(?:private|protected|public)\s+", "", head)
                    is_static = head.startswith("static ")
                    head = head.removeprefix("static ")
                    if head.startswith("<"):
                        depth, end = 0, 0
                        for end, char in enumerate(head):
                            depth += (char == "<") - (char == ">")
                            if depth == 0:
                                break
                        head = head[end + 1:].strip()
                    return_type = head.rsplit(" ", 1)[0]
                    parameters = [parameter.rsplit(" ", 1)[0] for parameter in split_types(args)]
                    self.assertTrue(parameters[-1].startswith("Operation<"), (name, selector))
                    descriptor = "(" + "".join(java_descriptor(t, imports) for t in parameters[:-1]) + ")"
                    descriptor += java_descriptor(return_type, imports)
                    method_name = selector.split("(", 1)[0]
                    if "(" in selector:
                        self.assertEqual(selector[len(method_name):], descriptor, name)
                    for target in targets:
                        members = ClassMembers(jar.read(target + ".class"))
                        self.assertIn((method_name, descriptor), members.methods, (name, target, descriptor))
                        self.assertEqual(is_static, bool(members.methods[(method_name, descriptor)] & 0x0008), (name, selector))

    @unittest.skipUnless(ARTIFACT.exists(), "NeoForge development artifact is not present")
    def test_wrapoperation_invocation_descriptors_and_constructor_target_exist(self):
        with ZipFile(ARTIFACT) as jar:
            for name in TARGETS:
                for owner, method, descriptor in re.findall(r'target = "L([^;]+);([^("]+)(\([^\"]+)', source(name)):
                    descriptor = descriptor.split('"', 1)[0]
                    self.assertIn((method, descriptor), ClassMembers(jar.read(owner + ".class")).methods, name)
            constructor = re.search(r'@Inject\(method = "<init>([^\"]+)"', source("RecipeLockStonecutterMenuMixin")).group(1)
            members = ClassMembers(jar.read("net/minecraft/world/inventory/StonecutterMenu.class"))
            self.assertIn(("<init>", constructor), members.methods)


if __name__ == "__main__":
    unittest.main()
