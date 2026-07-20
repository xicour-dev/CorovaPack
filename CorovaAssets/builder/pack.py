"""
builder.pack
============

Final pack-level assembly: pack.mcmeta, pack.png, and font assets.
Fonts get their own stage (rather than living in textures.py) because
they're a pack-root concept in the resource pack spec, not a texture
category.
"""

from __future__ import annotations

from . import util
from .config import PACK_DESCRIPTION, PACK_FORMAT_MAX, PACK_FORMAT_MIN, Paths
from .registry import Registry


class PackAssembler:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def write_mcmeta(self) -> None:
        # Modern (25w31a+) format: min_format/max_format instead of the
        # legacy single pack_format integer. See config.PACK_FORMAT_MIN.
        mcmeta = {
            "pack": {
                "min_format": PACK_FORMAT_MIN,
                "max_format": PACK_FORMAT_MAX,
                "description": PACK_DESCRIPTION,
            }
        }
        util.write_json(self.paths.generated / "pack.mcmeta", mcmeta)
        self.logger.info(
            f"pack.mcmeta written (min_format {PACK_FORMAT_MIN}, max_format {PACK_FORMAT_MAX})"
        )

    def copy_pack_png(self, registry: Registry) -> None:
        if registry.pack_png is None:
            self.logger.warn("no source/pack.png found -- pack will ship without an icon")
            return
        util.copy_file(registry.pack_png, self.paths.generated / "pack.png")
        self.logger.info("pack.png copied")

    def copy_fonts(self, registry: Registry) -> int:
        if not registry.fonts:
            self.logger.info("no font assets found -- skipping")
            return 0
        for asset in registry.fonts:
            dest = self.paths.font_dir / asset.relative_path
            util.copy_file(asset.source_path, dest)
        self.logger.info(f"{len(registry.fonts)} font asset file(s) copied")
        return len(registry.fonts)