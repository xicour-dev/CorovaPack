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

MUTATION GLOW OVERLAY: any item that ships an optional glow.png sidecar
(see registry.GLOW_TEXTURE_FILENAME) gets its own dedicated overlay
model generated (see builder.models), sized/shaped to match its own
silhouette. That item's model here is wrapped in a minecraft:composite
-- the item's own model plus one conditional overlay entry per
MutationType, each independently toggled by a flags[] bit and tinted by
a colors[] entry on the item's custom_model_data component. Items with
no glow.png simply render without any overlay -- they aren't visually
"eligible" for mutation tints until one is added. See
MutationVisuals.syncVisuals on the Java side for what actually sets
those flags/colors; it's fully generic per-item and needed no changes
for this.
"""

from __future__ import annotations

from . import util
from .config import MUTATION_TYPES, Paths
from .registry import ItemAsset, Registry


def _base_model_for(item: ItemAsset) -> dict:
    return {
        "type": "minecraft:model",
        "model": item.model_id,
    }


def _mutation_overlay_entries(glow_model_id: str) -> list[dict]:
    entries = []
    for index, _mutation_name in enumerate(MUTATION_TYPES):
        entries.append({
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": index,
            "on_true": {
                "type": "minecraft:model",
                "model": glow_model_id,
                "tints": [
                    {"type": "minecraft:custom_model_data", "index": index, "default": -1}
                ],
            },
            "on_false": {"type": "minecraft:empty"},
        })
    return entries


def _mutation_special_overlay_entries(glow_base_model_id: str, special_type: str) -> list[dict]:
    entries = []
    for index, _mutation_name in enumerate(MUTATION_TYPES):
        entries.append({
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": index,
            "on_true": {
                "type": "minecraft:special",
                "base": glow_base_model_id,
                "model": {
                    "type": special_type
                },
                "tints": [
                    {"type": "minecraft:custom_model_data", "index": index, "default": -1}
                ],
            },
            "on_false": {"type": "minecraft:empty"},
        })
    return entries


def _composite_of(base_model: dict, glow_model_id: str) -> dict:
    return {
        "type": "minecraft:composite",
        "models": [
            base_model,
            *_mutation_overlay_entries(glow_model_id)
        ]
    }


def _composite_special_of(base_model: dict, glow_base_model_id: str, special_type: str) -> dict:
    return {
        "type": "minecraft:composite",
        "models": [
            base_model,
            *_mutation_special_overlay_entries(glow_base_model_id, special_type)
        ]
    }


def _model_for(item: ItemAsset, registry: Registry) -> dict:
    base_model = _base_model_for(item)

    if item.glow_texture_path is None:
        return base_model

    return {
        "type": "minecraft:composite",
        "models": [base_model, *_mutation_overlay_entries(item.glow_model_id)],
    }


def _case_for(item: ItemAsset, registry: Registry) -> dict:
    return {
        "when": item.custom_model_data,
        "model": _model_for(item, registry),
    }


def _vanilla_base_model(base_item: str) -> dict:
    return {
        "type": "minecraft:model",
        "model": f"minecraft:item/{util.strip_namespace(base_item)}",
    }


def _fallback_for(base_item: str, registry: Registry) -> dict:
    """Model used when custom_model_data doesn't match any corova item on
    this base_item -- i.e. a plain, unmodified vanilla item, OR a vanilla
    item that was never reskinned by any corova ItemAsset in the first
    place (see the standalone loop in generate() below for that second
    case -- both paths end up calling this same helper).

    If a vanilla_glow overlay was authored for this base_item (see
    registry.VanillaGlowAsset / source/vanilla_glow/), the fallback itself
    becomes a composite: the untouched vanilla model plus the same
    per-MutationType conditional overlay stack used for corova's own
    custom items. This is what lets a bare minecraft:diamond_sword that
    was never reskinned still show mutation glow. Items with no
    vanilla_glow overlay authored just render as plain vanilla, same as
    before.
    """
    vanilla_glow = registry.vanilla_glow.get(base_item)
    if vanilla_glow is None:
        return _vanilla_base_model(base_item)

    if base_item == "minecraft:bow":
        bow_model = {"type": "minecraft:model", "model": "minecraft:item/bow"}
        bow_pulling_0 = {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_0"}
        bow_pulling_1 = {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_1"}
        bow_pulling_2 = {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_2"}

        bow_comp = _composite_of(bow_model, "corova:item/vanilla/bow_glow")
        pull0_comp = _composite_of(bow_pulling_0, "corova:item/vanilla/bow_pulling_0_glow")
        pull1_comp = _composite_of(bow_pulling_1, "corova:item/vanilla/bow_pulling_1_glow")
        pull2_comp = _composite_of(bow_pulling_2, "corova:item/vanilla/bow_pulling_2_glow")

        return {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_false": bow_comp,
            "on_true": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:use_duration",
                "scale": 0.05,
                "fallback": pull0_comp,
                "entries": [
                    {
                        "threshold": 0.65,
                        "model": pull1_comp
                    },
                    {
                        "threshold": 0.9,
                        "model": pull2_comp
                    }
                ]
            }
        }

    elif base_item == "minecraft:crossbow":
        crossbow_model = {"type": "minecraft:model", "model": "minecraft:item/crossbow"}
        crossbow_pull0 = {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_0"}
        crossbow_pull1 = {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_1"}
        crossbow_pull2 = {"type": "minecraft:model", "model": "minecraft:item/crossbow_pulling_2"}
        crossbow_arrow = {"type": "minecraft:model", "model": "minecraft:item/crossbow_arrow"}
        crossbow_firework = {"type": "minecraft:model", "model": "minecraft:item/crossbow_firework"}

        crossbow_comp = _composite_of(crossbow_model, "corova:item/vanilla/crossbow_glow")
        pull0_comp = _composite_of(crossbow_pull0, "corova:item/vanilla/crossbow_pulling_0_glow")
        pull1_comp = _composite_of(crossbow_pull1, "corova:item/vanilla/crossbow_pulling_1_glow")
        pull2_comp = _composite_of(crossbow_pull2, "corova:item/vanilla/crossbow_pulling_2_glow")
        arrow_comp = _composite_of(crossbow_arrow, "corova:item/vanilla/crossbow_arrow_glow")
        firework_comp = _composite_of(crossbow_firework, "corova:item/vanilla/crossbow_firework_glow")

        pulling_dispatch = {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_false": crossbow_comp,
            "on_true": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:crossbow/pull",
                "fallback": pull0_comp,
                "entries": [
                    {
                        "threshold": 0.58,
                        "model": pull1_comp
                    },
                    {
                        "threshold": 1.0,
                        "model": pull2_comp
                    }
                ]
            }
        }

        return {
            "type": "minecraft:select",
            "property": "minecraft:charge_type",
            "fallback": pulling_dispatch,
            "cases": [
                {
                    "when": "arrow",
                    "model": arrow_comp
                },
                {
                    "when": "rocket",
                    "model": firework_comp
                }
            ]
        }

    elif base_item == "minecraft:trident":
        trident_2d = {"type": "minecraft:model", "model": "minecraft:item/trident"}
        trident_in_hand = {
            "type": "minecraft:special",
            "base": "minecraft:item/trident_in_hand",
            "model": {"type": "minecraft:trident"}
        }
        trident_throwing = {
            "type": "minecraft:special",
            "base": "minecraft:item/trident_throwing",
            "model": {"type": "minecraft:trident"}
        }

        trident_2d_comp = _composite_of(trident_2d, "corova:item/vanilla/trident_glow")
        trident_in_hand_comp = _composite_special_of(trident_in_hand, "corova:item/vanilla/trident_in_hand_glow", "minecraft:trident")
        trident_throwing_comp = _composite_special_of(trident_throwing, "corova:item/vanilla/trident_throwing_glow", "minecraft:trident")

        in_hand_using = {
            "type": "minecraft:condition",
            "property": "minecraft:using_item",
            "on_false": trident_in_hand_comp,
            "on_true": trident_throwing_comp
        }

        return {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "fallback": in_hand_using,
            "cases": [
                {
                    "when": ["gui", "ground", "fixed", "head"],
                    "model": trident_2d_comp
                }
            ]
        }

    else:
        vanilla_model = _vanilla_base_model(base_item)
        return {
            "type": "minecraft:composite",
            "models": [vanilla_model, *_mutation_overlay_entries(vanilla_glow.glow_model_id)],
        }


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
        # Base items that only exist because of a vanilla_glow overlay --
        # nobody made a corova reskin of them, but we still need to emit a
        # definition file so a plain vanilla instance can pick up the
        # mutation glow composite from _fallback_for(). Skip any base_item
        # already covered above; that file's fallback already folds the
        # vanilla_glow overlay in.
        vanilla_only_bases = [b for b in registry.vanilla_glow if b not in grouped]
        count = 0

        for base_item, items in grouped.items():
            items_sorted = sorted(items, key=lambda i: (i.category, i.name))
            definition = {
                "model": {
                    "type": "minecraft:select",
                    "property": "minecraft:custom_model_data",
                    "index": 0,
                    "cases": [_case_for(item, registry) for item in items_sorted],
                    "fallback": _fallback_for(base_item, registry),
                }
            }
            # IMPORTANT: goes under the base item's OWN namespace
            # (assets/minecraft/items/... for vanilla items), not our
            # pack's assets/corova/items/ -- see module docstring.
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
            # No corova cases at all -- every instance of this vanilla item
            # goes straight to the glow-composite fallback, driven purely
            # by whatever custom_model_data flags/colors MutationVisuals
            # has set on that specific item stack.
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