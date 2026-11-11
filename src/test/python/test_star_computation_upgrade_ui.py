"""Upgrade-only UI asset/layout checks. Run directly without compiling the core/menu work."""

import json
from pathlib import Path
import re
import unittest

from PIL import Image


ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src/main/resources/assets/molecularmanipulator"
JAVA = ROOT / "src/main/java/com/atir/molecularmanipulator"
PREFIX = "gui.molecularmanipulator.star_computation.upgrade."


def rect(entry):
    return tuple(entry["rect"])


def sprite_image(sprite):
    namespace, path = sprite.split(":", 1)
    return Image.open(ROOT / f"src/main/resources/assets/{namespace}/textures/gui/sprites/{path}.png").convert("RGBA")


def luminance(rgb):
    values = [value / 255 for value in rgb[:3]]
    values = [value / 12.92 if value <= 0.04045 else ((value + 0.055) / 1.055) ** 2.4 for value in values]
    return sum(value * weight for value, weight in zip(values, (0.2126, 0.7152, 0.0722)))


class StarComputationUpgradeUiAssetsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.layout = json.loads((ASSETS / "osq_light/layout/star_computation_engine.json").read_text(encoding="utf-8"))
        cls.draw = cls.layout["draw"]
        cls.panels = {entry["id"]: entry for entry in cls.draw if entry.get("k") == "card"}
        cls.controls = {entry["id"]: entry for entry in cls.draw if entry.get("k") == "ctl"}
        cls.languages = {
            language: json.loads((ASSETS / f"lang/{language}.json").read_text(encoding="utf-8"))
            for language in ("zh_cn", "en_us")
        }

    def test_frame_inventory_and_nine_level_controls_keep_the_existing_geometry(self):
        self.assertEqual([400, 344], self.layout["size"])
        self.assertEqual((8, 32, 190, 210), rect(self.panels["armillary"]))
        self.assertEqual((8, 248, 190, 88), rect(self.panels["inv"]))
        slots = [entry for entry in self.draw if entry.get("k") == "slot" and entry["type"] == "sprite"]
        expected = [(22 + col * 18, y, 18, 18) for y in (254, 272, 290, 312) for col in range(9)]
        self.assertEqual(expected, [rect(entry) for entry in slots])
        for level in range(1, 10):
            self.assertEqual((17 + (level - 1) * 19, 56, 18, 22), rect(self.controls[f"up_level.{level}"]))
        self.assertEqual((204, 312, 92, 22), rect(self.controls["upgrade_start"]))
        self.assertEqual((300, 312, 92, 22), rect(self.controls["upgrade_pause"]))

    def test_three_tabs_are_parallel_and_clear_of_the_full_title(self):
        previous_right = 214  # Screen title: x=12, reserved width=202.
        for name in ("overview", "upgrade", "recipes"):
            x, y, width, height = rect(self.controls[name])
            self.assertGreaterEqual(x - previous_right, 4, name)
            self.assertEqual(10, y)
            self.assertEqual(22, height)
            self.assertLessEqual(x + width, 392)
            previous_right = x + width
        self.assertEqual("up_target", self.controls["upgrade"]["tab_panel"])

    def test_requirement_viewport_and_calibration_do_not_cover_each_other_or_inventory(self):
        target = rect(self.panels["up_target"])
        conditions = rect(self.panels["up_conditions"])
        calibration = rect(self.panels["up_calibration"])
        self.assertEqual((204, 32, 188, 60), target)
        self.assertEqual((204, 98, 188, 142), conditions)
        self.assertEqual((204, 246, 188, 60), calibration)
        self.assertGreaterEqual(conditions[1] - target[1] - target[3], 6)
        self.assertGreaterEqual(calibration[1] - conditions[1] - conditions[3], 6)
        x, y, width, height = rect(self.controls["up_requirements"])
        self.assertEqual((213, 117, 170, 106), (x, y, width, height))
        self.assertLessEqual(y + height + 7, 233)  # Leave room for the hover guidance below the list.
        self.assertGreaterEqual(x - conditions[0], 8)
        self.assertGreaterEqual(conditions[0] + conditions[2] - x - width, 8)
        self.assertGreater(y, 108 + 4)  # Heading has its own line above the viewport.
        self.assertLessEqual(y + height, conditions[1] + conditions[3] - 7)
        self.assertGreaterEqual(calibration[0] - (8 + 190), 6)
        self.assertLessEqual(calibration[1] + calibration[3] + 6, self.controls["upgrade_start"]["rect"][1])

    def test_new_panel_assets_reuse_osq_four_x_material_and_closed_tab_joins(self):
        for name in ("up_target", "up_conditions", "up_calibration"):
            entry = self.panels[name]
            with sprite_image(entry["sprite"]) as image:
                pad = entry["pad"]
                self.assertEqual(((entry["rect"][2] + pad[0] + pad[2]) * 4,
                                  (entry["rect"][3] + pad[1] + pad[3]) * 4), image.size)
                self.assertEqual(0, image.getpixel((0, 0))[3])
                self.assertGreaterEqual(image.getpixel((image.width // 2, image.height // 2))[3], 230)
        for name in ("overview", "upgrade", "recipes"):
            control = self.controls[name]
            surface = control["tab_surface"]
            with sprite_image(surface["sprite"]) as image:
                self.assertEqual((surface["rect"][2] * 4, surface["rect"][3] * 4), image.size)
                center_x = round((control["rect"][0] + control["rect"][2] / 2 - surface["rect"][0]) * 4)
                for logical_y in range(control["rect"][1] + 1, surface["rect"][1] + surface["rect"][3] - 4):
                    y = (logical_y - surface["rect"][1]) * 4
                    self.assertGreaterEqual(image.getpixel((center_x, y))[3], 230, (name, logical_y))

    def test_anchor_stat_material_still_touches_the_left_and_bottom_frame(self):
        bounds = {}
        for name in ("armillary", "ov_anchors"):
            entry = self.panels[name]
            with sprite_image(entry["sprite"]) as image:
                alpha = image.getchannel("A").point(lambda value: 255 if value >= 230 else 0)
                left, top, right, bottom = alpha.getbbox()
                x, y, width, height = rect(entry)
                pad = entry["pad"]
                sx = (width + pad[0] + pad[2]) / image.width
                sy = (height + pad[1] + pad[3]) / image.height
                bounds[name] = (x - pad[0] + left * sx, y - pad[1] + top * sy,
                                x - pad[0] + right * sx, y - pad[1] + bottom * sy)
        frame, anchors = bounds["armillary"], bounds["ov_anchors"]
        self.assertAlmostEqual(frame[0], anchors[0], places=2)
        self.assertAlmostEqual(frame[3], anchors[3], places=2)
        self.assertAlmostEqual(191, anchors[1], places=2)
        self.assertAlmostEqual(93, anchors[2], places=2)

    def test_upgrade_copy_has_complete_rule_dynamic_key_coverage_and_matching_arguments(self):
        keys = {language: {key for key in values if key.startswith(PREFIX)} for language, values in self.languages.items()}
        self.assertEqual(keys["zh_cn"], keys["en_us"])
        expected = set()
        for relative in ("starcomputation/StarComputationUpgrades.java", "starcomputation/StarComputationUpgradeCatalog.java"):
            source = (JAVA / relative).read_text(encoding="utf-8")
            expected.update(key for key in re.findall(r'\btext\("([a-z_.]+)"', source) if not key.endswith("."))
            expected.update(re.findall(r'unavailable\([^\n]*?,\s*"([a-z_]+)"', source))
        expected.update("group." + suffix for suffix in ("all", "any", "at_least"))
        expected.update("match." + suffix for suffix in ("all", "any", "at_least"))
        for kind in ("condition.", "row.", "detail."):
            expected.update(kind + suffix for suffix in ("ftb_quests", "machines", "research", "nodes"))
        node_source = (JAVA / "starcomputation/StarComputationRecipes.java").read_text(encoding="utf-8")
        expected.update("node." + suffix for suffix in re.findall(r'node\(\d+, "([a-z_]+)"', node_source))
        expected.update(("unconfigured", "conflict", "research_same", "research_cross", "machines_formed", "machines_active"))
        screen = (JAVA / "client/StarComputationEngineScreen.java").read_text(encoding="utf-8")
        expected.update(key for key in re.findall(r'\bup\("([a-z_.]+)"', screen) if not key.endswith("."))
        expected.update("status." + suffix for suffix in (
            "idle", "running", "paused", "structure", "offline", "conditions", "rule_missing", "energy",
            "restoring", "complete", "migration", "automatic", "certified", "repair", "preview", "another_task", "permission"))
        for language, values in self.languages.items():
            for suffix in expected:
                self.assertIn(PREFIX + suffix, values, (language, suffix))
            for key in keys[language]:
                self.assertTrue(values[key].strip(), (language, key))
                self.assertNotIn("entropy", values[key].lower(), key)
        for key in keys["en_us"]:
            placeholders = lambda value: sorted(re.findall(r'%(?:\d+\$)?s', value))
            self.assertEqual(placeholders(self.languages["en_us"][key]), placeholders(self.languages["zh_cn"][key]), key)
        for language in self.languages:
            self.assertNotRegex(self.languages[language][PREFIX + "detail.ftb_unavailable"], r"%(?:\d+\$)?s")

    def test_upgrade_accent_body_text_has_readable_contrast_on_existing_material(self):
        palette = {entry["id"]: entry["color"] for entry in self.draw if entry.get("id", "").startswith("color.")}
        with sprite_image(self.panels["up_conditions"]["sprite"]) as image:
            body = image.crop((20, 20, image.width - 20, image.height - 20))
            darkest = min(luminance(pixel) for pixel in body.getdata())
        for tier in ("bronze", "violet", "gold"):
            color = palette["color.upgrade_accent." + tier][-6:]
            ink = luminance(bytes.fromhex(color))
            self.assertGreaterEqual((darkest + 0.05) / (ink + 0.05), 4.5, tier)


if __name__ == "__main__":
    unittest.main()
