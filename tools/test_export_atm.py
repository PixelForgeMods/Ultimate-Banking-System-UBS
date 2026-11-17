import copy
import json
import tempfile
from pathlib import Path
from unittest.mock import patch
import unittest

from export_atm import ASSETS, export, java_model


class AtmExportTest(unittest.TestCase):
    def setUp(self):
        self.project = {"groups": [], "resolution": {"width": 2048, "height": 2048}, "elements": [{
            "name": "LCD welcome screen", "from": [5, 22.12, 1.955], "to": [11, 26.32, 2.02],
            "rotation": [-22.5, 0, 0], "origin": [8, 18.15, -.1], "faces": {
                "north": {"texture": 0, "uv": [0, 256, 1024, 1024]},
                "up": {"texture": 0, "uv": [0, 0, 152, 152], "rotation": 180},
                "south": {"texture": None, "uv": [0, 0, 0, 0]}}}]}

    def test_exports_full_height_geometry_lcd_uv_and_rotated_key_labels(self):
        original = copy.deepcopy(self.project)
        model = java_model(self.project)
        element = model["elements"][0]
        self.assertEqual(original, self.project)
        self.assertEqual([5, 22.12, 1.955], element["from"])
        self.assertEqual([11, 26.32, 2.02], element["to"])
        self.assertEqual([8, 18.15, -.1], element["rotation"]["origin"])
        self.assertEqual([0, 2, 8, 8], element["faces"]["north"]["uv"])
        self.assertEqual(180, element["faces"]["up"]["rotation"])
        self.assertNotIn("south", element["faces"])
        self.assertEqual("#atm", element["faces"]["north"]["texture"])

    def test_rejects_group_or_multi_axis_rotations_instead_of_changing_the_model(self):
        self.project["groups"] = [{"rotation": [0, 90, 0]}]
        with self.assertRaises(ValueError):
            java_model(self.project)
        self.project["groups"] = []
        self.project["elements"][0]["rotation"] = [22.5, 22.5, 0]
        with self.assertRaises(ValueError):
            java_model(self.project)

    def test_lighting_targets_screen_reader_lamp_keys_and_button_rings(self):
        template = self.project["elements"][0]
        self.project["elements"] = []
        for name in ("LCD welcome screen", "Rear armored shell", "Green anti skimming collar smooth segment 0",
                     "PIN 1 row 0 column 0", "Keypad lamp lens", "FDK L1 round button segment 0",
                     "FDK L1 dark center 0", "FDK L1 socket segment 0",
                     "Sloped metal keyboard deck", "Controls silver fascia"):
            element = copy.deepcopy(template)
            element["name"] = name
            self.project["elements"].append(element)
        screen, cabinet, reader, key, lamp, ring, center, socket, deck, fascia = java_model(self.project)["elements"]
        self.assertEqual(14, screen["faces"]["north"]["neoforge_data"]["block_light"])
        self.assertEqual("ffb0b0b0", screen["faces"]["north"]["neoforge_data"]["color"])
        self.assertEqual(0, screen["faces"]["north"]["neoforge_data"]["sky_light"])
        self.assertNotIn("neoforge_data", screen["faces"]["up"])
        self.assertTrue(all("neoforge_data" not in f for f in cabinet["faces"].values()))
        self.assertTrue(all(f["neoforge_data"]["block_light"] == 15 for f in reader["faces"].values()))
        self.assertTrue(all(f["neoforge_data"]["sky_light"] == 15 for f in reader["faces"].values()))
        self.assertTrue(all(f["neoforge_data"]["color"] == "ff55ff77" for f in reader["faces"].values()))
        self.assertNotEqual(screen["faces"]["north"]["uv"], reader["faces"]["north"]["uv"])
        self.assertTrue(all(f["neoforge_data"]["block_light"] == 15 for f in lamp["faces"].values()))
        self.assertEqual(13, key["faces"]["up"]["neoforge_data"]["block_light"])
        self.assertNotIn("neoforge_data", key["faces"]["north"])
        self.assertEqual(13, ring["faces"]["north"]["neoforge_data"]["block_light"])
        self.assertEqual(10, ring["faces"]["up"]["neoforge_data"]["block_light"])
        for element in (center, socket):
            self.assertTrue(all("neoforge_data" not in f for f in element["faces"].values()))
        self.assertEqual(9, deck["faces"]["up"]["neoforge_data"]["block_light"])
        self.assertNotIn("neoforge_data", deck["faces"]["north"])
        self.assertEqual(8, fascia["faces"]["north"]["neoforge_data"]["block_light"])
        self.assertNotIn("neoforge_data", fascia["faces"]["up"])

    def test_export_uses_only_real_surfaces_without_translucent_glow_sheets(self):
        source = (ASSETS / "blockbench/atm_machine.bbmodel").read_bytes()
        with tempfile.TemporaryDirectory() as directory:
            assets = Path(directory)
            for folder in ("blockbench", "models/block", "models/item", "textures/block"):
                (assets / folder).mkdir(parents=True)
            (assets / "blockbench/atm_machine.bbmodel").write_bytes(source)
            old_glow = assets / "textures/block/atm_glow.png"
            old_glow.write_bytes(b"obsolete glow sheet")
            with patch("export_atm.ASSETS", assets):
                export()
            root = json.loads((assets / "models/block/atm_machine.json").read_text())
            body = json.loads((assets / "models/block/atm_machine_body.json").read_text())
            self.assertEqual("ultimatebankingsystem:block/atm_machine_static", root["parent"])
            self.assertNotIn("children", root)
            self.assertEqual("minecraft:solid", body["render_type"])
            self.assertFalse(old_glow.exists())
            original = json.loads(source)
            self.assertEqual([e["name"] for e in original["elements"]],
                             [e["name"] for e in body["elements"]])
            static = json.loads((assets / "models/block/atm_machine_static.json").read_text())
            lcd = next(e for e in body["elements"] if e["name"] == "LCD welcome screen")
            live_lcd = next(e for e in static["elements"] if e["name"] == "LCD welcome screen")
            self.assertIn("north", lcd["faces"])
            self.assertNotIn("north", live_lcd["faces"])
            self.assertEqual({"from": lcd["from"], "to": lcd["to"]},
                             json.loads((assets / "atm_lcd.json").read_text()))
            controls = json.loads((assets / "atm_controls.json").read_text())
            reader = json.loads((assets / "atm_reader.json").read_text())
            slit = next(e for e in body["elements"] if e["name"] == "Card insertion slit")
            self.assertEqual([(slit["from"][a] + slit["to"][a]) / 2 if a != 2 else slit["from"][a]
                              for a in range(3)], reader["slot"])
            self.assertTrue(all(reader["from"][a] <= reader["slot"][a] <= reader["to"][a] for a in range(3)))
            self.assertEqual(list(range(24)), [b["index"] for b in controls])
            self.assertEqual(16, sum(b["keypad"] for b in controls))
            names = [e["name"] for e in static["elements"]]
            shutter = json.loads((assets / "models/block/atm/cash_shutter.json").read_text())
            self.assertEqual(12, len(shutter["elements"]))
            names.extend(e["name"] for e in shutter["elements"])
            reader_led = json.loads((assets / "models/block/atm/card_reader_led.json").read_text())
            self.assertEqual(21, len(reader_led["elements"]))
            self.assertTrue(all(f["tintindex"] == 0 for e in reader_led["elements"] for f in e["faces"].values()))
            self.assertTrue(all("color" not in f["neoforge_data"] for e in reader_led["elements"] for f in e["faces"].values()),
                            "Dynamic green comes from explicit render RGB, without a second baked tint")
            self.assertFalse(any(e["name"].startswith("Green anti skimming collar") for e in static["elements"]))
            names.extend(e["name"] for e in reader_led["elements"])
            guide = json.loads((assets / "models/block/atm/card_reader_guide.json").read_text())
            self.assertEqual(["Card lower guide"], [e["name"] for e in guide["elements"]])
            self.assertNotIn("Card lower guide", names)
            self.assertTrue(all(f["tintindex"] == 0 for f in guide["elements"][0]["faces"].values()))
            names.append("Card lower guide")
            self.assertFalse(any("shutter" in e["name"].lower() for e in static["elements"]))
            hardware = json.loads((assets / "atm_dispenser.json").read_text())
            self.assertEqual([8.0, 15.6, -0.53], hardware["cash"])
            for button in controls:
                part = json.loads((assets / f"models/block/atm/buttons/{button['index']}.json").read_text())
                self.assertEqual(1 if button["keypad"] else 18, len(part["elements"]))
                names.extend(e["name"] for e in part["elements"])
                if not button["keypad"]:
                    prefix = assets / f"models/block/atm/indicators/{button['index']}"
                    off = json.loads(Path(str(prefix) + "_off.json").read_text())["elements"][0]
                    on = json.loads(Path(str(prefix) + "_on.json").read_text())["elements"][0]
                    self.assertEqual(off["from"], on["from"])
                    self.assertEqual(off["to"], on["to"])
                    self.assertTrue(off["name"].startswith(button["name"][:6]))
                    self.assertNotIn("neoforge_data", off["faces"]["north"])
                    self.assertTrue(all(f["neoforge_data"]["block_light"] == 15 for f in on["faces"].values()))
                    self.assertNotEqual(off["faces"]["north"]["uv"], on["faces"]["north"]["uv"])
                    names.append(off["name"])
            self.assertCountEqual([e["name"] for e in body["elements"]], names)
            self.assertTrue(all(f["texture"] == "#atm" for e in body["elements"] for f in e["faces"].values()))
            collars = [e for e in body["elements"] if e["name"].startswith("Green anti skimming collar")]
            self.assertEqual(21, len(collars))
            for collar in collars:
                for face in collar["faces"].values():
                    self.assertEqual("ff55ff77", face["neoforge_data"]["color"])
                    self.assertGreater(face["uv"][0], 12)
            keys = [e for e in body["elements"] if e["name"].startswith("PIN ")]
            self.assertEqual(16, len(keys))
            for key in keys:
                row = int(key["name"].split(" row ", 1)[1].split()[0])
                self.assertEqual(13 - row // 2, key["faces"]["up"]["neoforge_data"]["block_light"])
                self.assertNotIn("neoforge_data", key["faces"]["north"])


if __name__ == "__main__":
    unittest.main()
