"""
builder.registry
=================

Scans source/ once per build and produces a plain, in-memory description
of every asset that exists. Every other stage (models, items, textures,
java, sounds, language) reads from this Registry instead of touching the
filesystem directly -- that's what keeps the stages decoupled and makes
new asset types easy to bolt on later.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

from . import util
from .config import (
    DEFAULT_BASE_ITEMS,
    FONT_SOURCE_DIR,
    ITEM_CATEGORIES,
    LANG_SOURCE_DIR,
    NAMESPACE,
    RAW_TEXTURE_CATEGORIES,
    SOUND_SOURCE_DIR,
)


@dataclass(frozen=True)
class ItemAsset:
    """A single generated item: one folder under source/<category>/<name>/."""

    category: str
    name: str  # snake_case id, derived from the folder name
    display_name: str
    texture_path: Path
    icon_path: Path | None
    base_item: str  # e.g. "minecraft:diamond_sword"
    # Optional per-item mutation glow overlay texture (see GLOW_TEXTURE_FILENAME
    # below). When present, this item gets its OWN glow model generated --
    # sized/shaped to match its own silhouette -- instead of riding on a
    # category-wide shared glow asset. See builder.items for how this is
    # composited in.
    glow_texture_path: Path | None
    # Optional per-item display transforms (translation/rotation/scale for
    # thirdperson_righthand, firstperson_righthand, gui, ground, etc).
    # Passed through verbatim into the generated model JSON's "display"
    # block when present -- see item.json's optional "display" field.
    display_overrides: dict | None = None

    @property
    def model_id(self) -> str:
        """Resource-location of the generated item model."""
        return f"{NAMESPACE}:item/{self.category}/{self.name}"

    @property
    def texture_id(self) -> str:
        return f"{NAMESPACE}:item/{self.category}/{self.name}"

    @property
    def glow_model_id(self) -> str:
        """Resource-location of this item's OWN generated glow overlay
        model. Only meaningful when glow_texture_path is not None -- see
        builder.items._glow_model_id, which is what actually decides
        whether to use this."""
        return f"{NAMESPACE}:item/{self.category}/{self.name}_glow"

    @property
    def glow_texture_id(self) -> str:
        return f"{NAMESPACE}:item/{self.category}/{self.name}_glow"

    @property
    def custom_model_data(self) -> str:
        """The string key used to select this model via custom_model_data."""
        return f"{NAMESPACE}:{self.category}/{self.name}"

    @property
    def java_constant(self) -> str:
        return util.to_screaming_snake_case(f"{self.category}_{self.name}")

    @property
    def lang_key(self) -> str:
        return f"item.{NAMESPACE}.{self.category}_{self.name}"



@dataclass(frozen=True)
class VanillaGlowAsset:
    """A mutation glow overlay for a vanilla item that has no corova reskin
    (see source/vanilla_glow/<vanilla item id>/glow.png). Only a glow.png is
    needed here -- there's no accompanying texture.png/model, since the
    vanilla game already renders the item itself; this only supplies the
    per-MutationType overlay composited on top of it in items._fallback_for.
    """

    name: str  # snake_case vanilla item id, e.g. "diamond_sword"
    base_item: str  # e.g. "minecraft:diamond_sword"
    glow_texture_path: Path
    extra_glows: dict[str, Path] = field(default_factory=dict)  # e.g. {"pulling_0": Path}

    @property
    def glow_model_id(self) -> str:
        return f"{NAMESPACE}:item/vanilla/{self.name}_glow"

    @property
    def glow_texture_id(self) -> str:
        return f"{NAMESPACE}:item/vanilla/{self.name}_glow"

    def extra_glow_model_id(self, suffix: str) -> str:
        return f"{NAMESPACE}:item/vanilla/{self.name}_glow_{suffix}"

    def extra_glow_texture_id(self, suffix: str) -> str:
        return f"{NAMESPACE}:item/vanilla/{self.name}_glow_{suffix}"


@dataclass(frozen=True)
class RawAsset:
    """A raw file copied verbatim (GUI art, entity textures, fonts, ...)."""

    relative_path: Path  # relative to its source category folder
    source_path: Path


@dataclass(frozen=True)
class SoundAsset:
    relative_path: Path  # relative to source/sounds/
    source_path: Path

    @property
    def event_name(self) -> str:
        """corova:sounds/foo/bar (dotted for sounds.json readability)."""
        parts = list(self.relative_path.with_suffix("").parts)
        return ".".join(util.to_snake_case(p) for p in parts)

    @property
    def sound_id(self) -> str:
        parts = list(self.relative_path.with_suffix("").parts)
        return "/".join(util.to_snake_case(p) for p in parts)


@dataclass
class Registry:
    """Aggregated view of every discovered asset for this build."""

    items: list[ItemAsset] = field(default_factory=list)
    # Keyed by base_item (e.g. "minecraft:diamond_sword") -- at most one
    # vanilla_glow overlay per base item, same as how corova items are
    # grouped by base_item elsewhere in the registry.
    vanilla_glow: dict[str, VanillaGlowAsset] = field(default_factory=dict)
    raw_textures: dict[str, list[RawAsset]] = field(default_factory=dict)  # dest folder -> files
    fonts: list[RawAsset] = field(default_factory=list)
    sounds: list[SoundAsset] = field(default_factory=list)
    lang_files: list[Path] = field(default_factory=list)
    pack_png: Path | None = None

    def items_by_category(self, category: str) -> list["ItemAsset"]:
        return [i for i in self.items if i.category == category]

    def items_by_base_item(self) -> dict[str, list["ItemAsset"]]:
        grouped: dict[str, list[ItemAsset]] = {}
        for item in self.items:
            grouped.setdefault(item.base_item, []).append(item)
        return grouped


TEXTURE_FILENAME = "texture.png"
ICON_FILENAME = "icon.png"
GLOW_TEXTURE_FILENAME = "glow.png"
ITEM_METADATA_FILENAME = "item.json"
VANILLA_GLOW_SOURCE_DIR = "vanilla_glow"


class RegistryScanner:
    """Walks source/ and builds a Registry. Pure discovery, no writing."""

    def __init__(self, source_dir: Path, logger) -> None:
        self.source_dir = source_dir
        self.logger = logger

    def scan(self) -> Registry:
        registry = Registry()

        for category in ITEM_CATEGORIES:
            registry.items.extend(self._scan_item_category(category))

        registry.vanilla_glow = self._scan_vanilla_glow()

        for src_folder, dest_folder in RAW_TEXTURE_CATEGORIES.items():
            registry.raw_textures[dest_folder] = self._scan_raw_folder(src_folder)

        registry.fonts = self._scan_raw_folder(FONT_SOURCE_DIR)
        registry.sounds = self._scan_sounds()
        registry.lang_files = self._scan_lang_files()

        pack_png = self.source_dir / "pack.png"
        registry.pack_png = pack_png if pack_png.is_file() else None

        return registry

    # -- item categories -----------------------------------------------

    def _scan_item_category(self, category: str) -> list[ItemAsset]:
        category_dir = self.source_dir / category
        if not category_dir.is_dir():
            return []

        assets: list[ItemAsset] = []
        for entry in sorted(category_dir.iterdir()):
            if not entry.is_dir():
                continue

            texture_path = entry / TEXTURE_FILENAME
            if not texture_path.is_file():
                self.logger.warn(
                    f"{category}/{entry.name} has no {TEXTURE_FILENAME} -- skipped"
                )
                continue

            name = util.to_snake_case(entry.name)
            icon_path = entry / ICON_FILENAME
            glow_path = entry / GLOW_TEXTURE_FILENAME
            metadata = self._read_item_metadata(entry / ITEM_METADATA_FILENAME)

            base_item = metadata.get("base_item", DEFAULT_BASE_ITEMS.get(category, "minecraft:stick"))
            display_name = metadata.get("display_name", util.to_title_case(name))
            display_overrides = metadata.get("display")  # optional in-hand/GUI/ground transforms

            assets.append(
                ItemAsset(
                    category=category,
                    name=name,
                    display_name=display_name,
                    texture_path=texture_path,
                    icon_path=icon_path if icon_path.is_file() else None,
                    base_item=base_item,
                    glow_texture_path=glow_path if glow_path.is_file() else None,
                    display_overrides=display_overrides,
                )
            )
        return assets

    def _scan_vanilla_glow(self) -> dict[str, VanillaGlowAsset]:
        """Scans source/vanilla_glow/<vanilla item id>/glow.png -- lets a
        bare, never-reskinned vanilla item (e.g. minecraft:diamond_sword)
        still show mutation glow overlays. Folder name IS the vanilla item
        id (snake_case, no "minecraft:" prefix needed)."""
        folder = self.source_dir / VANILLA_GLOW_SOURCE_DIR
        if not folder.is_dir():
            return {}

        assets: dict[str, VanillaGlowAsset] = {}
        for entry in sorted(folder.iterdir()):
            if not entry.is_dir():
                continue

            glow_path = entry / GLOW_TEXTURE_FILENAME
            if not glow_path.is_file():
                self.logger.warn(
                    f"{VANILLA_GLOW_SOURCE_DIR}/{entry.name} has no {GLOW_TEXTURE_FILENAME} -- skipped"
                )
                continue

            name = util.to_snake_case(entry.name)
            base_item = f"minecraft:{name}"
            if base_item in assets:
                self.logger.warn(f"duplicate vanilla_glow entry for {base_item} -- keeping first")
                continue

            extra_glows = {}
            for sub_entry in entry.iterdir():
                if sub_entry.is_file() and sub_entry.name.startswith("glow_") and sub_entry.suffix.lower() == ".png":
                    suffix = sub_entry.stem[5:]  # strip 'glow_'
                    extra_glows[suffix] = sub_entry

            assets[base_item] = VanillaGlowAsset(
                name=name,
                base_item=base_item,
                glow_texture_path=glow_path,
                extra_glows=extra_glows,
            )
        return assets

    def _read_item_metadata(self, path: Path) -> dict:
        """Optional sidecar for per-item overrides. Never required."""
        if not path.is_file():
            return {}
        try:
            return util.read_json(path)
        except Exception as exc:  # noqa: BLE001 - surfaced as a build warning
            self.logger.warn(f"could not parse {path}: {exc}")
            return {}

    # -- raw copy categories ---------------------------------------------

    def _scan_raw_folder(self, folder_name: str) -> list[RawAsset]:
        folder = self.source_dir / folder_name
        if not folder.is_dir():
            return []

        assets: list[RawAsset] = []
        for path in sorted(folder.rglob("*")):
            if path.is_file():
                assets.append(RawAsset(relative_path=path.relative_to(folder), source_path=path))
        return assets

    # -- sounds ------------------------------------------------------------

    def _scan_sounds(self) -> list[SoundAsset]:
        folder = self.source_dir / SOUND_SOURCE_DIR
        if not folder.is_dir():
            return []

        sounds: list[SoundAsset] = []
        for path in sorted(folder.rglob("*")):
            if path.is_file() and path.suffix.lower() == ".ogg":
                sounds.append(SoundAsset(relative_path=path.relative_to(folder), source_path=path))
        return sounds

    # -- language ------------------------------------------------------------

    def _scan_lang_files(self) -> list[Path]:
        folder = self.source_dir / LANG_SOURCE_DIR
        if not folder.is_dir():
            return []
        return sorted(p for p in folder.glob("*.json") if p.is_file())