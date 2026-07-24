"""
builder.config
==============

Single source of truth for pipeline-wide settings: paths, the resource
pack namespace, the item categories that get automatic model/item
generation, and the fallback vanilla items used to host custom models.

Nothing in here is gameplay data. It only describes *where things live*
and *how identifiers are built* -- not what anything does in-game.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path


# ---------------------------------------------------------------------------
# Namespace / identity
# ---------------------------------------------------------------------------

NAMESPACE = "corova"
PACK_NAME = "CorovaPack"

# Resource pack format written into pack.mcmeta.
#
# As of Minecraft 25w31a, packs no longer declare a single "pack_format"
# field -- they declare "min_format" and "max_format" instead.
#
# IMPORTANT: get PACK_FORMAT_MIN directly from your own client, not from
# a wiki table -- for very recent releases (like 26.x at time of writing)
# public docs are frequently unconfirmed/out of date. In-game: F3+V, or
# run /version. Use the number it reports.
#
# MAX is set high on purpose (rather than matching MIN exactly) so the
# pack keeps loading without a compatibility warning across the server's
# future point-release updates, without needing a rebuild every patch.
# Tighten it if you ever need to hard-block newer clients.
PACK_FORMAT_MIN = 88  # TODO: replace with the value F3+V reports on your client
PACK_FORMAT_MAX = 999
PACK_DESCRIPTION = "Corova Chronicles"

# ---------------------------------------------------------------------------
# Item categories
# ---------------------------------------------------------------------------
# Every folder listed here is scanned under source/<category>/<item name>/
# for a texture.png. Each one found automatically gets:
#   - a copied texture
#   - a generated item model json
#   - a generated (or merged) item definition json
#   - a Java constant
#
# No model JSON is ever hand-written for these categories.
ITEM_CATEGORIES: tuple[str, ...] = (
    "weapons",
    "tools",
    "armor",
    "materials",
    "blocks",  # item-form only for now; blockstates are a future feature
    "mobs",
)

# Categories that are copied as raw textures with no model/item generation.
# Key = source subfolder name, value = destination folder under
# assets/<namespace>/textures/
RAW_TEXTURE_CATEGORIES: dict[str, str] = {
    "entities": "entity",
    "gui": "gui",
    "particles": "particle",
}

# Fonts are copied verbatim into assets/<namespace>/font/
FONT_SOURCE_DIR = "fonts"

# Sounds live under source/sounds/** and are copied into
# assets/<namespace>/sounds/** with the same relative layout.
SOUND_SOURCE_DIR = "sounds"

# Hand-authored / overridable language fragments. Anything here wins over
# auto-generated display-name keys.
LANG_SOURCE_DIR = "lang"

# ---------------------------------------------------------------------------
# Base vanilla items used to host custom models via custom_model_data
# selection (the modern, 1.21.4+ item-model "minecraft:select" pattern).
# Every corova item needs a real vanilla item to attach to; this is the
# default per category, overridable per-asset via an optional item.json
# sidecar (see builder.registry).
# ---------------------------------------------------------------------------
DEFAULT_BASE_ITEMS: dict[str, str] = {
    "weapons": "minecraft:diamond_sword",
    "tools": "minecraft:diamond_pickaxe",
    "armor": "minecraft:leather_chestplate",
    "materials": "minecraft:paper",
    "blocks": "minecraft:stone",
    "mobs": "minecraft:paper",
}

# ---------------------------------------------------------------------------
# Mutation glow overlay
# ---------------------------------------------------------------------------
# Every item can optionally ship its own "glow" overlay texture -- a
# glow.png sidecar dropped right next to that item's texture.png
# (source/<category>/<name>/glow.png; see registry.GLOW_TEXTURE_FILENAME).
# When present, that item's model is wrapped in a minecraft:composite: the
# item's normal model, plus one conditional overlay entry per MutationType,
# each toggled by a flags[] bit and tinted by a colors[] entry on the item's
# custom_model_data component (see items.py / models.py).
#
# There is no shared/category-wide glow asset anymore -- every item shapes
# its own overlay to its own silhouette, and every category (weapons,
# tools, armor, materials, blocks) supports this the same way. An item with
# no glow.png simply never shows a mutation tint.
#
# CRITICAL: this list's order must exactly mirror MutationType.java's enum
# declaration order in the plugin, entry for entry. The Java side sets
# flags/colors by MutationType.ordinal() (see MutationVisuals.syncVisuals);
# this Python list has no way to read that enum directly (it lives in a
# separate repo), so it's a manually-mirrored copy. If you add, remove, or
# reorder a MutationType in Java, update this list to match in the same
# commit -- a mismatch here means a mutation's glow silently renders at the
# wrong color/on the wrong slot, with no error from either side.
MUTATION_TYPES: tuple[str, ...] = (
    "LIFE_SIPHON",
    "EXTRACTOR_O_ENCHANTING",
    "BLEED",
    "ARROW_VELOCITY",
    "DOUBLE_TAP",
    "TRIPLE_TAP",
    "BATTLE_HARDENED",
    "NETHER_FIRE",
    "SPLINTER",
    "FROST",
    "VENOM",
    "DECAY",
    "CLOBBER",
    "COLD_STEEL",
    "SHATTER",
    "STATIC_CHARGE",
    "LIFE_STEAL",
    "EXTRA_CUSTOM_ENCHANT_SLOT",
    "EXTRA_MUTATION_SLOT",
    "MANA_CONSERVATION",
    "MYSTIC_ARCANUM",
    "SOUL_SIPHON",
    "AUTO_SMELT",
    "EXCAVATION",
    "SOLID_STANCE",
    "KINETIC_CHARGE",
    "AMPLIFIER",
    "DICE",
    "FEAR",
    "BACKSTAB",
    "PARRY",
    "LAST_STAND",
    "BREAK_THROUGH",
    "HEAVY_METAL",
    "WOUND_MENDING",
    "BRIMSTONE",
    "HUNTERS_INSTINCT",
    "GOLDEN_AEGIS",
    "PRISMATIC_EDGE",
    "TORTOISESHELL",
    "SKULL_CRUSH",
)


@dataclass(frozen=True)
class Paths:
    """Resolved filesystem locations for a single build run."""

    root: Path
    source: Path
    generated: Path
    output: Path

    # Sub-locations inside generated/, mirroring a real resource pack.
    assets_root: Path = field(init=False)
    namespace_root: Path = field(init=False)
    models_dir: Path = field(init=False)
    items_dir: Path = field(init=False)
    textures_dir: Path = field(init=False)
    sounds_dir: Path = field(init=False)
    font_dir: Path = field(init=False)
    lang_dir: Path = field(init=False)
    java_dir: Path = field(init=False)

    def __post_init__(self) -> None:
        assets_root = self.generated / "assets"
        namespace_root = assets_root / NAMESPACE
        object.__setattr__(self, "assets_root", assets_root)
        object.__setattr__(self, "namespace_root", namespace_root)
        object.__setattr__(self, "models_dir", namespace_root / "models" / "item")
        object.__setattr__(self, "items_dir", namespace_root / "items")
        object.__setattr__(self, "textures_dir", namespace_root / "textures")
        object.__setattr__(self, "sounds_dir", namespace_root / "sounds")
        object.__setattr__(self, "font_dir", namespace_root / "font")
        object.__setattr__(self, "lang_dir", namespace_root / "lang")
        object.__setattr__(self, "java_dir", self.generated / "java")

    @classmethod
    def from_root(cls, root: Path) -> "Paths":
        return cls(
            root=root,
            source=root / "source",
            generated=root / "generated",
            output=root / "output",
        )