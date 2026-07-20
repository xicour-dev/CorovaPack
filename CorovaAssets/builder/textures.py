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
from PIL import Image

from . import util
from .config import Paths, ITEM_CATEGORIES
from .registry import Registry


class TextureCompiler:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def compile(self, registry: Registry) -> int:
        count = 0

        # Copy standard item assets
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

            if vanilla_glow.freeze_texture_path is not None:
                freeze_dest = self.paths.textures_dir / "item" / "vanilla" / f"{vanilla_glow.name}_freeze.png"
                util.copy_file(vanilla_glow.freeze_texture_path, freeze_dest)
                count += 1

            for suffix, path in vanilla_glow.extra_glows.items():
                extra_dest = self.paths.textures_dir / "item" / "vanilla" / f"{vanilla_glow.name}_glow_{suffix}.png"
                util.copy_file(path, extra_dest)
                count += 1

        # Copy raw textures (entities, gui, particles)
        for dest_folder, raw_assets in registry.raw_textures.items():
            for asset in raw_assets:
                dest = self.paths.textures_dir / dest_folder / asset.relative_path
                util.copy_file(asset.source_path, dest)
                count += 1

        # Copy _shared folders and handle animated textures
        for category in ITEM_CATEGORIES:
            shared_dir = self.paths.source / category / "_shared"
            if shared_dir.is_dir():
                for path in shared_dir.rglob("*"):
                    if path.is_file():
                        rel = path.relative_to(shared_dir)
                        dest = self.paths.textures_dir / "item" / category / "_shared" / rel
                        util.copy_file(path, dest)
                        count += 1

                # Stitch lightning animation if present under _shared/lightning
                lightning_dir = shared_dir / "lightning"
                if lightning_dir.is_dir():
                    self._stitch_lightning(lightning_dir, category)

        self.logger.info(f"{count} texture file(s) copied")
        return count

    def _stitch_lightning(self, frames_dir: Path, category: str) -> None:
        """Stitches the 20 lightning animation frames vertically into a single animated PNG with a .mcmeta file."""
        frames = []
        for i in range(1, 21):
            path1 = frames_dir / f"lightning_{i}.png"
            path2 = frames_dir / f"Layer 1_lightning_{i:02d}.png"
            if path1.is_file():
                frames.append(path1)
            elif path2.is_file():
                frames.append(path2)
            else:
                # Find any ending with lightning_{i}.png or lightning_{i:02d}.png
                found = False
                for f in frames_dir.iterdir():
                    if f.is_file() and (f.name == f"lightning_{i}.png" or f.name.endswith(f"lightning_{i:02d}.png") or f.name.endswith(f"lightning_{i}.png")):
                        frames.append(f)
                        found = True
                        break
                if not found:
                    self.logger.warn(f"Lightning animation frame {i} not found in {frames_dir}")

        if not frames:
            return

        # Open frames and paste them vertically
        images = [Image.open(f) for f in frames]
        width = images[0].width
        height = images[0].height

        stitched = Image.new("RGBA", (width, height * len(images)))
        for idx, img in enumerate(images):
            stitched.paste(img, (0, idx * height))

        dest_png = self.paths.textures_dir / "item" / category / "_shared" / "lightning" / "lightning.png"
        util.ensure_dir(dest_png.parent)
        stitched.save(dest_png)

        # Create the .png.mcmeta file
        mcmeta_dest = dest_png.with_name("lightning.png.mcmeta")
        mcmeta_data = {
            "animation": {
                "frametime": 1
            }
        }
        util.write_json(mcmeta_dest, mcmeta_data)
        self.logger.info(f"Stitched {len(frames)} lightning frames into {dest_png} at 20 FPS")
