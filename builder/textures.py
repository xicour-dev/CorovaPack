"""
builder.textures
=================

Copies every discovered texture into the generated resource pack tree.
Item textures go to textures/item/<category>/<name>.png, matching the
identifiers handed out by ItemAsset. An item's optional glow.png sidecar
(its per-item mutation overlay mask) is copied alongside it as
textures/item/<category>/<name>_glow.png, matching ItemAsset.glow_texture_id.
Raw categories (entities/gui/particles) are mirrored 1:1 under
textures/<dest>/.
"""

from __future__ import annotations

from pathlib import Path

from . import util
from .config import Paths
from .registry import Registry


class TextureCompiler:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def compile(self, registry: Registry) -> int:
        count = 0

        for item in registry.items:
            dest = self.paths.textures_dir / "item" / item.category / f"{item.name}.png"
            util.copy_file(item.texture_path, dest)
            count += 1

            if item.icon_path is not None:
                icon_dest = self.paths.textures_dir / "item" / item.category / f"{item.name}_icon.png"
                util.copy_file(item.icon_path, icon_dest)
                count += 1

            if item.glow_texture_path is not None:
                glow_dest = self.paths.textures_dir / "item" / item.category / f"{item.name}_glow.png"
                util.copy_file(item.glow_texture_path, glow_dest)
                count += 1

        for vanilla_glow in registry.vanilla_glow.values():
            dest = self.paths.textures_dir / "item" / "vanilla" / f"{vanilla_glow.name}_glow.png"
            util.copy_file(vanilla_glow.glow_texture_path, dest)
            count += 1

            for suffix, path in vanilla_glow.extra_glows.items():
                extra_dest = self.paths.textures_dir / "item" / "vanilla" / f"{vanilla_glow.name}_glow_{suffix}.png"
                util.copy_file(path, extra_dest)
                count += 1

        for dest_folder, raw_assets in registry.raw_textures.items():
            for asset in raw_assets:
                dest = self.paths.textures_dir / dest_folder / asset.relative_path
                util.copy_file(asset.source_path, dest)
                count += 1

        self.logger.info(f"{count} texture file(s) copied")
        return count