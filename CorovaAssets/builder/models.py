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
    By inheriting directly from the vanilla item model (e.g., minecraft:item/mace),
    the glow model inherits the exact transformations, rotations, and positions of the base item.
    """
    return f"minecraft:item/{vanilla_glow.name}"


class ModelGenerator:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def generate(self, registry: Registry) -> int:
        count = 0
        for item in registry.items:
            # 1. Base model
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

            # 2. Per-item mutation glow overlay
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

            # 3. Lightning overlay model (uses the animated lightning texture)
            lightning_model_json = {
                "parent": parent_for(item),
                "textures": {
                    "layer0": "corova:item/weapons/_shared/lightning/lightning",
                },
            }
            if item.display_overrides:
                lightning_model_json["display"] = item.display_overrides

            lightning_dest = self.paths.models_dir / item.category / f"{item.name}_lightning.json"
            util.write_json(lightning_dest, lightning_model_json)
            count += 1

            # 4. Custom item freeze model (scythes only)
            if item.name.endswith("scythe"):
                freeze_model_json = {
                    "parent": parent_for(item),
                    "textures": {
                        "layer0": "corova:item/weapons/_shared/_scythe/freeze",
                    },
                }
                if item.display_overrides:
                    freeze_model_json["display"] = item.display_overrides

                freeze_dest = self.paths.models_dir / item.category / f"{item.name}_freeze.json"
                util.write_json(freeze_dest, freeze_model_json)
                count += 1

        # Vanilla-item models (mutation glow, lightning overlays, freeze)
        for vanilla_glow in registry.vanilla_glow.values():
            # Vanilla mutation glow model
            glow_model_json = {
                "parent": vanilla_parent_for(vanilla_glow),
                "textures": {
                    "layer0": vanilla_glow.glow_texture_id,
                },
            }
            dest = self.paths.models_dir / "vanilla" / f"{vanilla_glow.name}_glow.json"
            util.write_json(dest, glow_model_json)
            count += 1

            # Vanilla lightning overlay model
            lightning_model_json = {
                "parent": vanilla_parent_for(vanilla_glow),
                "textures": {
                    "layer0": "corova:item/weapons/_shared/lightning/lightning",
                },
            }
            lightning_dest = self.paths.models_dir / "vanilla" / f"{vanilla_glow.name}_lightning.json"
            util.write_json(lightning_dest, lightning_model_json)
            count += 1

            # Vanilla freeze model (swords only)
            if vanilla_glow.name.endswith("_sword") and vanilla_glow.freeze_texture_path is not None:
                freeze_model_json = {
                    "parent": vanilla_parent_for(vanilla_glow),
                    "textures": {
                        "layer0": f"corova:item/vanilla/{vanilla_glow.name}_freeze",
                    },
                }
                freeze_dest = self.paths.models_dir / "vanilla" / f"{vanilla_glow.name}_freeze.json"
                util.write_json(freeze_dest, freeze_model_json)
                count += 1

            # Suffix models for bow pulling state/crossbow arrow/spear in hand, etc.
            for suffix in vanilla_glow.extra_glows:
                extra_glow_model_json = {
                    "parent": f"minecraft:item/{vanilla_glow.name}_{suffix}",
                    "textures": {
                        "layer0": vanilla_glow.extra_glow_texture_id(suffix),
                    },
                }
                extra_dest = self.paths.models_dir / "vanilla" / f"{vanilla_glow.name}_glow_{suffix}.json"
                util.write_json(extra_dest, extra_glow_model_json)
                count += 1

                extra_lightning_model_json = {
                    "parent": f"minecraft:item/{vanilla_glow.name}_{suffix}",
                    "textures": {
                        "layer0": "corova:item/weapons/_shared/lightning/lightning",
                    },
                }
                extra_lightning_dest = self.paths.models_dir / "vanilla" / f"{vanilla_glow.name}_lightning_{suffix}.json"
                util.write_json(extra_lightning_dest, extra_lightning_model_json)
                count += 1

        self.logger.info(f"{count} model JSON file(s) generated")
        return count
