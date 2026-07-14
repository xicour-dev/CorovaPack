"""
builder.models
==============

Generates one item model JSON per discovered ItemAsset. Every model
currently uses the standard flat "item/generated" parent with a single
texture layer -- that's the right default for 2D icon-style items and
is exactly what lets this stay 100% automatic.

Swapping specific items to a 3D/handheld parent, or compiling real
Blockbench models, is a drop-in extension point (see the `parent_for`
hook) and won't require touching any other stage.

In-hand/GUI/ground positioning is controlled via each item's optional
item.json "display" field (see ItemAsset.display_overrides). When
present it's merged straight into the model's "display" block --
still zero hand-written model JSON, just an optional sidecar tweak.
"""

from __future__ import annotations

from . import util
from .config import Paths
from .registry import ItemAsset, Registry, VanillaGlowAsset

# Categories that look right as in-hand tools/weapons use the handheld
# parent (slight offset/rotation baked in by vanilla); everything else
# falls back to the flat 2D "generated" parent. This is presentation
# only -- it is not gameplay.
_HANDHELD_CATEGORIES = {"weapons", "tools"}


def parent_for(item: ItemAsset) -> str:
    if item.category in _HANDHELD_CATEGORIES:
        return "minecraft:item/handheld"
    return "minecraft:item/generated"


def vanilla_parent_for(vanilla_glow: VanillaGlowAsset) -> str:
    """Decides the parent model for a vanilla item with a mutation glow overlay.
    For tools/weapons, it should match handheld (or handheld_rod, if a rod),
    while default items should be generated.
    """
    name = vanilla_glow.name.lower()

    if name == "mace":
        return "minecraft:item/mace"

    # Handheld rods
    if name in {"fishing_rod", "carrot_on_a_stick", "warped_fungus_on_a_stick"}:
        return "minecraft:item/handheld_rod"

    # Swords, tools, and weapons (e.g. mace, trident, bow, spear, etc. are typically handheld or generated depending on vanilla specs, but let's cover all standard tools/weapons)
    tool_suffixes = (
        "_sword", "_pickaxe", "_axe", "_shovel", "_hoe",
        "mace", "trident", "bow", "crossbow", "spear", "brush", "shears"
    )
    if any(name.endswith(suffix) or suffix in name for suffix in tool_suffixes):
        return "minecraft:item/handheld"

    return "minecraft:item/generated"


class ModelGenerator:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def generate(self, registry: Registry) -> int:
        count = 0
        for item in registry.items:
            model_json = {
                "parent": parent_for(item),
                "textures": {
                    "layer0": item.texture_id,
                },
            }
            if item.display_overrides:
                model_json["display"] = item.display_overrides

            dest = self.paths.models_dir / item.category / f"{item.name}.json"
            util.write_json(dest, model_json)
            count += 1

            # Per-item mutation glow overlay: same parent/display transform
            # as the base item (so it lines up in-hand pixel-for-pixel),
            # just pointed at the glow texture instead of the item texture.
            if item.glow_texture_path is not None:
                glow_model_json = {
                    "parent": parent_for(item),
                    "textures": {
                        "layer0": item.glow_texture_id,
                    },
                }
                if item.display_overrides:
                    glow_model_json["display"] = item.display_overrides

                glow_dest = self.paths.models_dir / item.category / f"{item.name}_glow.json"
                util.write_json(glow_dest, glow_model_json)
                count += 1

        # Vanilla-item mutation glow overlays (items with no corova reskin
        # at all -- see registry.VanillaGlowAsset). These have no ItemAsset,
        # no base texture, and no display_overrides of their own; just a
        # flat 2D overlay model pointed at the copied glow texture.
        for vanilla_glow in registry.vanilla_glow.values():
            name = vanilla_glow.name.lower()
            if name == "bow":
                stages = ["", "_pulling_0", "_pulling_1", "_pulling_2"]
                for stage in stages:
                    glow_model_json = {
                        "parent": f"minecraft:item/bow{stage}",
                        "textures": {
                            "layer0": vanilla_glow.glow_texture_id,
                        },
                    }
                    dest = self.paths.models_dir / "vanilla" / f"bow{stage}_glow.json"
                    util.write_json(dest, glow_model_json)
                    count += 1
            elif name == "crossbow":
                stages = ["", "_pulling_0", "_pulling_1", "_pulling_2", "_arrow", "_firework"]
                for stage in stages:
                    glow_model_json = {
                        "parent": f"minecraft:item/crossbow{stage}",
                        "textures": {
                            "layer0": vanilla_glow.glow_texture_id,
                        },
                    }
                    dest = self.paths.models_dir / "vanilla" / f"crossbow{stage}_glow.json"
                    util.write_json(dest, glow_model_json)
                    count += 1
            elif name == "trident":
                # For trident, we generate trident_glow (flat 2D), trident_in_hand_glow (3D), trident_throwing_glow (3D)
                glow_model_json_2d = {
                    "parent": "minecraft:item/trident",
                    "textures": {
                        "layer0": vanilla_glow.glow_texture_id,
                    },
                }
                util.write_json(self.paths.models_dir / "vanilla" / "trident_glow.json", glow_model_json_2d)
                count += 1

                glow_model_json_in_hand = {
                    "parent": "minecraft:item/trident_in_hand",
                    "textures": {
                        "layer0": vanilla_glow.glow_texture_id,
                    },
                }
                util.write_json(self.paths.models_dir / "vanilla" / "trident_in_hand_glow.json", glow_model_json_in_hand)
                count += 1

                glow_model_json_throwing = {
                    "parent": "minecraft:item/trident_throwing",
                    "textures": {
                        "layer0": vanilla_glow.glow_texture_id,
                    },
                }
                util.write_json(self.paths.models_dir / "vanilla" / "trident_throwing_glow.json", glow_model_json_throwing)
                count += 1
            else:
                glow_model_json = {
                    "parent": vanilla_parent_for(vanilla_glow),
                    "textures": {
                        "layer0": vanilla_glow.glow_texture_id,
                    },
                }
                dest = self.paths.models_dir / "vanilla" / f"{vanilla_glow.name}_glow.json"
                util.write_json(dest, glow_model_json)
                count += 1

        self.logger.info(f"{count} model JSON file(s) generated")
        return count
