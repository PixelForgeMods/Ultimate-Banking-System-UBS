"""Export the grouped ATM source as one full-height block model and its item."""
import base64
import copy
import json
import re
from pathlib import Path


ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/ultimatebankingsystem"


def java_model(project):
    if any(any(group.get("rotation", [])) for group in project["groups"]):
        raise ValueError("Apply group rotations before exporting the Java model")
    elements = []
    width = project["resolution"]["width"]
    height = project["resolution"]["height"]
    for source in project["elements"]:
        if source.get("export", True) is False:
            continue
        element = {key: copy.deepcopy(source[key]) for key in ("name", "from", "to")}
        rotation = source.get("rotation", [0, 0, 0])
        axes = [axis for axis, angle in enumerate(rotation) if angle]
        if axes:
            if len(axes) != 1 or rotation[axes[0]] not in (-45, -22.5, 22.5, 45):
                raise ValueError("Unsupported Java cube rotation")
            element["rotation"] = {"angle": rotation[axes[0]], "axis": "xyz"[axes[0]],
                                   "origin": source["origin"].copy()}
        element["faces"] = {}
        for direction, face in source["faces"].items():
            if face.get("texture") is None:
                continue
            if face["texture"] != 0:
                raise ValueError("ATM source must use its single embedded texture")
            uv = [value * 16 / (width if index % 2 == 0 else height)
                  for index, value in enumerate(face["uv"])]
            element["faces"][direction] = {"uv": uv, "texture": "#atm"}
            if face.get("rotation"):
                element["faces"][direction]["rotation"] = face["rotation"]
            name = source["name"]
            light = 0
            if name == "LCD welcome screen" and direction == "north":
                light = 14
                element["faces"][direction]["neoforge_data"] = {"color": "ffb0b0b0"}
            elif name.startswith("Green anti skimming collar"):
                light = 15
                # Tint the lamp's white atlas swatch: the painted green swatch caps LED luminance.
                element["faces"][direction]["uv"] = [1599.5 * 16 / width, 63.5 * 16 / height,
                                                       1600.5 * 16 / width, 64.5 * 16 / height]
                element["faces"][direction]["neoforge_data"] = {"color": "ff55ff77"}
            elif name in ("Card lower guide", "Insert card text"):
                light = 15
            elif name == "Keypad lamp lens":
                light = 15
            elif name.startswith("PIN ") and direction == "up":
                light = 13 - int(name.split(" row ", 1)[1].split()[0]) // 2
            elif name == "EPP keyboard recessed border" and direction == "up":
                light = 11
            elif name == "Sloped metal keyboard deck" and direction == "up":
                light = 9
            elif name == "Controls silver fascia" and direction == "north":
                light = 8
            elif name.startswith("FDK ") and " round button segment " in name:
                light = 13 if direction == "north" else 10
            if light:
                element["faces"][direction]["neoforge_data"] = {
                    **element["faces"][direction].get("neoforge_data", {}),
                    "block_light": light, "sky_light": 15 if light == 15 else 0,
                    "ambient_occlusion": False}
                element["shade"] = False
        elements.append(element)
    return {"ambientocclusion": False, "texture_size": [width, height],
            "textures": {"atm": "ultimatebankingsystem:block/atm_machine", "particle": "#atm"},
            "elements": elements}


def export():
    project = json.loads((ASSETS / "blockbench/atm_machine.bbmodel").read_text(encoding="utf-8"))
    model = java_model(project)
    model["render_type"] = "minecraft:solid"
    buttons = []
    moving = set()
    parts = {}
    groups = {}
    for element in model["elements"]:
        name = element["name"]
        if name.startswith("PIN "):
            row, column = map(int, re.search(r"row (\d+) column (\d+)", name).groups())
            index = row * 4 + column
        elif name.startswith("FDK ") and (" round button segment " in name or " dark center " in name):
            side, row = re.search(r"FDK ([LR])(\d)", name).groups()
            index = 16 + (0 if side == "L" else 4) + int(row) - 1
        else:
            continue
        groups.setdefault(index, []).append(element)
        moving.add(name)
    if set(groups) != set(range(24)):
        raise ValueError("ATM must have sixteen keypad keys and eight LCD buttons")
    for index, elements in sorted(groups.items()):
        keypad = index < 16
        rotation = elements[0].get("rotation", {})
        buttons.append({"index": index, "name": elements[0]["name"], "keypad": keypad,
                        "from": [min(e["from"][a] for e in elements) for a in range(3)],
                        "to": [max(e["to"][a] for e in elements) for a in range(3)],
                        "origin": rotation.get("origin", [0, 0, 0]), "angle": rotation.get("angle", 0)})
        parts[f"block/atm/buttons/{index}"] = {**model, "elements": elements}
    guides = [e for e in model["elements"] if e["name"].startswith("FDK ") and e["name"].endswith(" screen guide")]
    if len(guides) != 8:
        raise ValueError("ATM must have one screen guide for each LCD button")
    for guide in guides:
        side, row = re.search(r"FDK ([LR])(\d)", guide["name"]).groups()
        index = 16 + (0 if side == "L" else 4) + int(row) - 1
        moving.add(guide["name"])
        parts[f"block/atm/indicators/{index}_off"] = {**model, "elements": [guide]}
        lit = copy.deepcopy(guide)
        lit["shade"] = False
        for face in lit["faces"].values():
            face["uv"] = [1599.5 * 16 / model["texture_size"][0], 63.5 * 16 / model["texture_size"][1],
                          1600.5 * 16 / model["texture_size"][0], 64.5 * 16 / model["texture_size"][1]]
            face["neoforge_data"] = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
        parts[f"block/atm/indicators/{index}_on"] = {**model, "elements": [lit]}
    shutter = [e for e in model["elements"] if e["name"] == "Silver cash shutter" or e["name"].startswith("Cash shutter rib ")]
    if len(shutter) != 12:
        raise ValueError("ATM cash shutter requires its panel and eleven ribs")
    moving.update(e["name"] for e in shutter)
    parts["block/atm/cash_shutter"] = {**model, "elements": shutter}
    cavity = next(e for e in model["elements"] if e["name"] == "Cash cavity")
    receipt = next(e for e in model["elements"] if e["name"] == "Receipt recessed slot")
    hardware = {"cash": [(cavity["from"][a] + cavity["to"][a]) / 2 if a != 2 else cavity["from"][a] for a in range(3)],
                "receipt": [(receipt["from"][a] + receipt["to"][a]) / 2 if a != 2 else receipt["from"][a] for a in range(3)]}
    (ASSETS / "atm_dispenser.json").write_text(json.dumps(hardware, indent=2) + "\n", encoding="utf-8", newline="\n")
    collar = copy.deepcopy([e for e in model["elements"] if e["name"].startswith("Green anti skimming collar")])
    if len(collar) != 21:
        raise ValueError("ATM reader ring requires twenty-one collar segments")
    moving.update(e["name"] for e in collar)
    for element in collar:
        for face in element["faces"].values():
            face["tintindex"] = 0
            face["neoforge_data"].pop("color", None)
    parts["block/atm/card_reader_led"] = {**model, "elements": collar}
    guide = copy.deepcopy(next(e for e in model["elements"] if e["name"] == "Card lower guide"))
    moving.add(guide["name"])
    white_uv = next(iter(collar[0]["faces"].values()))["uv"]
    for face in guide["faces"].values():
        face["uv"] = white_uv.copy()
        face["tintindex"] = 0
        face["neoforge_data"].pop("color", None)
    parts["block/atm/card_reader_guide"] = {**model, "elements": [guide]}
    static = {**model, "elements": copy.deepcopy([e for e in model["elements"] if e["name"] not in moving])}
    lcd = next(e for e in static["elements"] if e["name"] == "LCD welcome screen")
    lcd["faces"].pop("north")
    (ASSETS / "atm_lcd.json").write_text(json.dumps({"from": lcd["from"], "to": lcd["to"]}, indent=2) + "\n",
                                        encoding="utf-8", newline="\n")
    models = {"block/atm_machine_body": model, "block/atm_machine_static": static,
              "block/atm_machine": {
                  "parent": "ultimatebankingsystem:block/atm_machine_static"},
              "item/atm_machine": {"parent": "ultimatebankingsystem:block/atm_machine_body",
                                   "display": project["display"]}, **parts}
    for name, asset in models.items():
        target = ASSETS / "models" / f"{name}.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(asset, indent=2) + "\n", encoding="utf-8", newline="\n")
    (ASSETS / "atm_controls.json").write_text(json.dumps(buttons, indent=2) + "\n", encoding="utf-8", newline="\n")
    reader = [e for e in model["elements"] if e["name"].startswith(("Green anti skimming collar", "Card reader"))
              or e["name"] in ("Card insertion slit", "Card lower guide")]
    slit = next(e for e in model["elements"] if e["name"] == "Card insertion slit")
    reader_data = {"from": [min(e["from"][a] for e in reader) for a in range(3)],
                   "to": [max(e["to"][a] for e in reader) for a in range(3)],
                   "slot": [(slit["from"][a] + slit["to"][a]) / 2 if a != 2 else slit["from"][a]
                            for a in range(3)]}
    (ASSETS / "atm_reader.json").write_text(json.dumps(reader_data, indent=2) + "\n", encoding="utf-8", newline="\n")
    texture = project["textures"][0]["source"].split(",", 1)[1]
    (ASSETS / "textures/block/atm_machine.png").write_bytes(base64.b64decode(texture))
    (ASSETS / "textures/block/atm_glow.png").unlink(missing_ok=True)
    print(f"Exported single-block ATM: {len(model['elements'])} cubes, {model['texture_size']} texture")


if __name__ == "__main__":
    export()
