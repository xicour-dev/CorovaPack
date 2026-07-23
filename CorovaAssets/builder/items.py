"""
builder.items
=============

Generates the modern (1.21.4+) per-item definition files. These are NOT
looked up by our own pack namespace -- an item's default item_model
component points at "<the item's own namespace>:items/<its own id>",
so to override e.g. minecraft:wooden_hoe's appearance the file has to
live at assets/minecraft/items/wooden_hoe.json, regardless of what
namespace the rest of this pack uses. Writing it under assets/corova/
(our own namespace) silently does nothing -- the game never looks
there for a vanilla item's default model.

Each file's model is a "minecraft:select" on the
"minecraft:custom_model_data" component, with one case per corova item
that rides on that base item, and a fallback that renders the vanilla
item untouched.

Multiple corova items are free to share a base item (e.g. every sword
type riding on minecraft:diamond_sword) -- they're disambiguated by
their custom_model_data string, which ItemAsset derives automatically
from its category/name. No manual JSON is ever required.

MUTATION GLOW OVERLAY & LIGHTNING OVERLAY: any item that has the Lightning custom enchant
will have the animated lightning overlay. Furthermore, the lightning animation acts as its
own glow texture for mutations, so if an item has a mutation and lightning, the lightning
overlay will have the mutation color tint added to it.
"""

from __future__ import annotations

from . import util
from .config import MUTATION_TYPES, Paths
from .registry import ItemAsset, Registry, VanillaGlowAsset


def _lightning_model_id_for(item: ItemAsset, is_freeze: bool = False) -> str:
    # A custom item's lightning model ID
    return f"corova:item/{item.category}/{item.name}_lightning"


def _lightning_overlay_entry(lightning_model_id: str) -> dict:
    # Lightning flag is at index 41 (len(MUTATION_TYPES))
    return {
        "type": "minecraft:condition",
        "property": "minecraft:custom_model_data",
        "index": len(MUTATION_TYPES),
        "on_true": {
            "type": "minecraft:model",
            "model": lightning_model_id
        },
        "on_false": {"type": "minecraft:empty"}
    }


def _composite_mutation_and_lightning_entries(glow_model_id: str | None, lightning_model_id: str) -> list[dict]:
    entries = []
    # If both lightning and mutation are active, use the lightning model tinted with the mutation color.
    # Otherwise, fallback to the normal mutation glow model if it exists.
    for index, _mutation_name in enumerate(MUTATION_TYPES):
        on_true_structure = {
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": len(MUTATION_TYPES), # lightning flag
            "on_true": {
                "type": "minecraft:model",
                "model": lightning_model_id,
                "tints": [
                    {"type": "minecraft:custom_model_data", "index": index, "default": -1}
                ]
            },
            "on_false": {
                "type": "minecraft:model",
                "model": glow_model_id,
                "tints": [
                    {"type": "minecraft:custom_model_data", "index": index, "default": -1}
                ]
            } if glow_model_id else {"type": "minecraft:empty"}
        }

        entries.append({
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": index,
            "on_true": on_true_structure,
            "on_false": {"type": "minecraft:empty"},
        })
    return entries


def _model_with_overlays(base_model: dict, glow_model_id: str | None, lightning_model_id: str) -> dict:
    # Build a unified composite model with custom base, untinted lightning, and mutation-tinted overlays
    models = [base_model]
    models.append(_lightning_overlay_entry(lightning_model_id))
    models.extend(_composite_mutation_and_lightning_entries(glow_model_id, lightning_model_id))
    return {
        "type": "minecraft:composite",
        "models": models
    }


def _model_for(item: ItemAsset, registry: Registry, is_freeze: bool = False) -> dict:
    if is_freeze and item.name.endswith("scythe"):
        model_id = f"corova:item/{item.category}/{item.name}_freeze"
    else:
        model_id = item.model_id

    base_model = {
        "type": "minecraft:model",
        "model": model_id,
    }

    glow_model_id = item.glow_model_id if item.glow_texture_path is not None else None
    lightning_model_id = _lightning_model_id_for(item)

    return _model_with_overlays(base_model, glow_model_id, lightning_model_id)


def _cases_for(item: ItemAsset, registry: Registry) -> list[dict]:
    # Returns normal and optionally freeze cases
    normal_case = {
        "when": item.custom_model_data,
        "model": _model_for(item, registry, is_freeze=False)
    }
    cases = [normal_case]
    if item.name.endswith("scythe"):
        freeze_case = {
            "when": f"{item.custom_model_data}_freeze",
            "model": _model_for(item, registry, is_freeze=True)
        }
        cases.append(freeze_case)
    return cases


def _vanilla_base_model(base_item: str, is_freeze: bool = False) -> dict:
    vanilla_name = util.strip_namespace(base_item)
    if is_freeze:
        model_id = f"corova:item/vanilla/{vanilla_name}_freeze"
    else:
        model_id = f"minecraft:item/{vanilla_name}"
    return {
        "type": "minecraft:model",
        "model": model_id,
    }


def _bow_glow_model_for(vanilla_glow: VanillaGlowAsset, suffix: str | None) -> str:
    if suffix and suffix in vanilla_glow.extra_glows:
        return vanilla_glow.extra_glow_model_id(suffix)
    return vanilla_glow.glow_model_id


def _bow_composite(vanilla_glow: VanillaGlowAsset, suffix: str | None) -> dict:
    base_model_name = f"minecraft:item/bow_{suffix}" if suffix else "minecraft:item/bow"
    base_model = {
        "type": "minecraft:model",
        "model": base_model_name
    }
    glow_model_id = _bow_glow_model_for(vanilla_glow, suffix)
    lightning_model_id = f"corova:item/vanilla/{vanilla_glow.name}_lightning"
    if suffix:
        lightning_model_id += f"_{suffix}"

    return _model_with_overlays(base_model, glow_model_id, lightning_model_id)


def _bow_fallback_model(vanilla_glow: VanillaGlowAsset) -> dict:
    return {
        "type": "minecraft:condition",
        "property": "minecraft:using_item",
        "on_true": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:use_duration",
            "scale": 0.05,
            "entries": [
                {
                    "threshold": 0.65,
                    "model": _bow_composite(vanilla_glow, "pulling_1")
                },
                {
                    "threshold": 0.9,
                    "model": _bow_composite(vanilla_glow, "pulling_2")
                }
            ],
            "fallback": _bow_composite(vanilla_glow, "pulling_0")
        },
        "on_false": _bow_composite(vanilla_glow, None)
    }


def _crossbow_glow_model_for(vanilla_glow: VanillaGlowAsset, suffix: str | None) -> str:
    if suffix and suffix in vanilla_glow.extra_glows:
        return vanilla_glow.extra_glow_model_id(suffix)
    return vanilla_glow.glow_model_id


def _crossbow_composite(vanilla_glow: VanillaGlowAsset, suffix: str | None) -> dict:
    base_model_name = f"minecraft:item/crossbow_{suffix}" if suffix else "minecraft:item/crossbow"
    base_model = {
        "type": "minecraft:model",
        "model": base_model_name
    }
    glow_model_id = _crossbow_glow_model_for(vanilla_glow, suffix)
    lightning_model_id = f"corova:item/vanilla/{vanilla_glow.name}_lightning"
    if suffix:
        lightning_model_id += f"_{suffix}"

    return _model_with_overlays(base_model, glow_model_id, lightning_model_id)


def _crossbow_fallback_model(vanilla_glow: VanillaGlowAsset) -> dict:
    return {
        "type": "minecraft:select",
        "property": "minecraft:charge_type",
        "cases": [
            {
                "when": "arrow",
                "model": _crossbow_composite(vanilla_glow, "arrow")
            },
            {
                "when": "rocket",
                "model": _crossbow_composite(vanilla_glow, "firework")
            }
        ],
        "fallback": {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_true": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:crossbow/pull",
                "entries": [
                    {
                        "threshold": 0.58,
                        "model": _crossbow_composite(vanilla_glow, "pulling_1")
                    },
                    {
                        "threshold": 1.0,
                        "model": _crossbow_composite(vanilla_glow, "pulling_2")
                    }
                ],
                "fallback": _crossbow_composite(vanilla_glow, "pulling_0")
            },
            "on_false": _crossbow_composite(vanilla_glow, None)
        }
    }


def _trident_fallback_model(vanilla_glow: VanillaGlowAsset) -> dict:
    # Trident model uses a flat 2D composite model for inventory, ground, and fixed.
    # We apply the overlays to the flat model.
    flat_model = {
        "type": "minecraft:model",
        "model": "minecraft:item/trident"
    }
    lightning_model_id = f"corova:item/vanilla/{vanilla_glow.name}_lightning"
    flat_composite = _model_with_overlays(flat_model, vanilla_glow.glow_model_id, lightning_model_id)

    return {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [
            {
                "when": [ "gui", "ground", "fixed" ],
                "model": flat_composite
            }
        ],
        "fallback": {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_false": {
                "type": "minecraft:special",
                "base": "minecraft:item/trident_in_hand",
                "model": {
                    "type": "minecraft:trident"
                }
            },
            "on_true": {
                "type": "minecraft:special",
                "base": "minecraft:item/trident_throwing",
                "model": {
                    "type": "minecraft:trident"
                }
            }
        }
    }


def _spear_fallback_model(base_item: str, vanilla_glow: VanillaGlowAsset | None) -> dict:
    vanilla_name = util.strip_namespace(base_item)

    flat_model = {
        "type": "minecraft:model",
        "model": f"minecraft:item/{vanilla_name}"
    }
    in_hand_model = {
        "type": "minecraft:model",
        "model": f"minecraft:item/{vanilla_name}_in_hand"
    }

    glow_model_id = vanilla_glow.glow_model_id if vanilla_glow else None
    in_hand_glow_model_id = vanilla_glow.extra_glow_model_id("in_hand") if (vanilla_glow and "in_hand" in vanilla_glow.extra_glows) else None

    lightning_model_id = f"corova:item/vanilla/{vanilla_name}_lightning"
    in_hand_lightning_model_id = f"corova:item/vanilla/{vanilla_name}_lightning_in_hand" if (vanilla_glow and "in_hand" in vanilla_glow.extra_glows) else lightning_model_id

    flat_composite = _model_with_overlays(flat_model, glow_model_id, lightning_model_id)
    in_hand_composite = _model_with_overlays(in_hand_model, in_hand_glow_model_id, in_hand_lightning_model_id)

    return {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [
            {
                "when": [ "gui", "ground", "fixed" ],
                "model": flat_composite
            }
        ],
        "fallback": in_hand_composite
    }


def _vanilla_model_with_overlays(base_item: str, registry: Registry, suffix: str | None = None, is_freeze: bool = False) -> dict:
    vanilla_glow = registry.vanilla_glow.get(base_item)
    base_model = _vanilla_base_model(base_item, is_freeze)

    glow_model_id = None
    if vanilla_glow is not None:
        glow_model_id = _bow_glow_model_for(vanilla_glow, suffix)

    vanilla_name = util.strip_namespace(base_item)
    lightning_model_id = f"corova:item/vanilla/{vanilla_name}_lightning"
    if suffix:
        lightning_model_id += f"_{suffix}"

    return _model_with_overlays(base_model, glow_model_id, lightning_model_id)


def _fallback_for(base_item: str, registry: Registry) -> dict:
    vanilla_glow = registry.vanilla_glow.get(base_item)
    if base_item.endswith("_spear"):
        return _spear_fallback_model(base_item, vanilla_glow)

    if vanilla_glow is not None:
        if base_item == "minecraft:bow":
            return _bow_fallback_model(vanilla_glow)
        elif base_item == "minecraft:crossbow":
            return _crossbow_fallback_model(vanilla_glow)
        elif base_item == "minecraft:trident":
            return _trident_fallback_model(vanilla_glow)

    # Wrap the fallback (unmodified vanilla item) in a minecraft:select matching case if it is a sword and has freeze.png
    vanilla_name = util.strip_namespace(base_item)
    normal_fallback = _vanilla_model_with_overlays(base_item, registry, suffix=None, is_freeze=False)

    if base_item.endswith("_sword") and vanilla_glow is not None and vanilla_glow.freeze_texture_path is not None:
        freeze_fallback = _vanilla_model_with_overlays(base_item, registry, suffix=None, is_freeze=True)
        return {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": [
                {
                    "when": f"corova:vanilla/{vanilla_name}_freeze",
                    "model": freeze_fallback
                }
            ],
            "fallback": normal_fallback
        }

    return normal_fallback


def _base_item_namespace(base_item: str) -> str:
    """'minecraft:wooden_hoe' -> 'minecraft'. Defaults to 'minecraft' if
    a base_item override was given without an explicit namespace."""
    return base_item.split(":", 1)[0] if ":" in base_item else "minecraft"


class ItemDefinitionGenerator:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def generate(self, registry: Registry) -> int:
        grouped = registry.items_by_base_item()
        vanilla_only_bases = [b for b in registry.vanilla_glow if b not in grouped]
        count = 0

        for base_item, items in grouped.items():
            items_sorted = sorted(items, key=lambda i: (i.category, i.name))

            # Flatten cases for all ItemAssets
            cases = []
            for item in items_sorted:
                cases.extend(_cases_for(item, registry))

            definition = {
                "model": {
                    "type": "minecraft:select",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "cases": cases,
                    "fallback": _fallback_for(base_item, registry),
                }
            }
            target_namespace = _base_item_namespace(base_item)
            dest = (
                self.paths.assets_root
                / target_namespace
                / "items"
                / f"{util.strip_namespace(base_item)}.json"
            )
            util.write_json(dest, definition)
            count += 1

        for base_item in vanilla_only_bases:
            definition = {"model": _fallback_for(base_item, registry)}
            target_namespace = _base_item_namespace(base_item)
            dest = (
                self.paths.assets_root
                / target_namespace
                / "items"
                / f"{util.strip_namespace(base_item)}.json"
            )
            util.write_json(dest, definition)
            count += 1

        self.logger.info(
            f"{count} item definition file(s) generated "
            f"({sum(len(v) for v in grouped.values())} custom item(s) total, "
            f"{len(vanilla_only_bases)} vanilla-only glow-overlay item(s)) "
            f"under assets/<base item namespace>/items/"
        )
        return count
