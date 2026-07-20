# CorovaAssets

A standalone Python asset compiler for **Corova Chronicles**. It turns raw
source art/sound/font files into a finished Minecraft: Java Edition resource
pack (`CorovaPack.zip`) and a set of Java constant classes for the companion
Paper plugin to consume.

**This project contains no gameplay logic.** Damage, abilities, mutations,
crafting, progression, and AI all live in the Paper plugin. This repo only
compiles assets and hands the plugin clean identifiers to reference.

## Quick start

```bash
python build.py
```

That's it. Every run:

1. Wipes `generated/` and `output/`
2. Rescans everything under `source/`
3. Copies textures, sounds, GUI art, and fonts
4. Auto-generates item model JSON + modern item definition JSON for every
   item it finds (no hand-written model JSON, ever)
5. Auto-generates language keys
6. Auto-generates Java helper classes (`generated/java/*.java`)
7. Builds `output/CorovaPack.zip` and its SHA1

A demo set of placeholder textures ships in `source/` so you can run the
build immediately and see real output. Swap them for real art whenever
you're ready — nothing about the pipeline changes.

## Adding a new item

Drop a texture in the right category folder and rebuild. That's the entire
workflow:

```
source/weapons/blood_scythe/texture.png
```

```bash
python build.py
```

You now have:

- `assets/corova/textures/item/weapons/blood_scythe.png`
- `assets/corova/models/item/weapons/blood_scythe.json`
- an entry in `assets/corova/items/diamond_sword.json` (the default base
  item for the `weapons` category)
- `CorovaModels.WEAPONS_BLOOD_SCYTHE` / `CorovaCustomModelData.WEAPONS_BLOOD_SCYTHE`
  / `CorovaBaseItems.WEAPONS_BLOOD_SCYTHE` in the generated Java classes
- an auto display name key, `item.corova.weapons_blood_scythe`, in
  `en_us.json` (override it any time in `source/lang/en_us.json`)

No manual JSON editing, ever.

### Optional per-item overrides

Every item folder can optionally include an `item.json` sidecar — never
required, only for when you want to deviate from the category default:

```json
{
  "base_item": "minecraft:iron_axe",
  "display_name": "Corova Warhammer"
}
```

`source/weapons/warhammer/item.json` in this repo demonstrates this.

## How items attach to vanilla Minecraft

Modern (1.21.4+) Java Edition resource packs don't support arbitrary custom
item IDs — every item still needs to be a real vanilla item under the hood.
This pipeline uses the current standard approach: each custom item is
selected via the `minecraft:custom_model_data` component using a
`minecraft:select` item model, layered on top of a configurable vanilla
"base item" (see `DEFAULT_BASE_ITEMS` in `builder/config.py`). Multiple
custom items can safely share the same base item; they're disambiguated by
their `custom_model_data` string, which is derived automatically from the
item's category and name.

## Project layout

```
CorovaAssets/
├── build.py                 entry point — python build.py
├── builder/
│   ├── config.py             namespace, categories, paths, base-item defaults
│   ├── util.py                filesystem / JSON / naming / logging helpers
│   ├── registry.py            scans source/ into an in-memory asset registry
│   ├── textures.py            copies textures
│   ├── models.py              generates item model JSON
│   ├── items.py                generates modern item definition JSON
│   ├── sounds.py               copies sounds + generates sounds.json
│   ├── language.py             generates/merges lang files
│   ├── java.py                  generates Java constant classes
│   ├── pack.py                  pack.mcmeta, pack.png, fonts
│   ├── zip.py                    builds CorovaPack.zip
│   └── hash.py                    SHA1 of the built zip
├── source/                  the only directory developers edit
│   ├── pack.png
│   ├── weapons/ tools/ armor/ materials/ blocks/   (item categories)
│   ├── entities/ gui/ particles/                    (raw texture categories)
│   ├── sounds/                                       (*.ogg, any subfolders)
│   ├── fonts/                                        (raw font assets)
│   └── lang/                                         (optional lang overrides)
├── generated/                recreated every build — the working resource pack tree
│   └── java/                  generated *.java files for the plugin
└── output/                   recreated every build — CorovaPack.zip + .sha1
```

## Design notes / extension points

- **Registry-first architecture.** `RegistryScanner` walks `source/` exactly
  once per build and produces a plain-data `Registry`. Every other stage
  reads from that registry instead of touching the filesystem itself, so
  stages stay decoupled and easy to reorder, disable, or replace.
- **`ItemAsset` is the single source of identifiers.** Model IDs, texture
  IDs, `custom_model_data` strings, Java constant names, and lang keys are
  all derived from one dataclass, so there's exactly one place that defines
  how a folder name becomes every downstream identifier.
- **Model parents are pluggable.** `models.parent_for()` is the one hook to
  touch when specific items need a different model parent (e.g. moving from
  flat 2D icons to real 3D/Blockbench-compiled models later).
- **Base items are configurable, per category or per item**, without ever
  requiring a hand-written model/item JSON — see `DEFAULT_BASE_ITEMS` and the
  optional `item.json` sidecar.

### Planned future work (architecture already allows for these)

- Blockbench (`.bbmodel`) compilation into full 3D models
- Animated models
- Automatic model registration with the plugin at startup
- Automatic upload/deploy to a web server or CDN
- GitHub Actions CI build + release
- Asset validation (missing textures, duplicate names, orphaned files)
- Automatic PNG optimization
- Richer automatic sound + language generation
- GUI layout generation
- Boss models, furniture, and display-entity assets

None of these require restructuring the existing stages — they're new
modules that consume the same `Registry`.
