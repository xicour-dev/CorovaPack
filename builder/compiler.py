"""
builder.compiler
=================

Orchestrates the full build: wires the registry scanner and every
generation stage together in order, and prints the final summary.
This is the only module that knows the *order* things happen in --
every individual stage stays ignorant of the others.
"""

from __future__ import annotations

import time
from pathlib import Path

from . import util
from .config import PACK_NAME, Paths
from .hash import PackHasher
from .items import ItemDefinitionGenerator
from .java import JavaGenerator
from .language import LanguageGenerator
from .models import ModelGenerator
from .pack import PackAssembler
from .registry import Registry, RegistryScanner
from .sounds import SoundCompiler
from .textures import TextureCompiler
from .zip import PackZipper


class BuildResult:
    def __init__(self) -> None:
        self.registry: Registry | None = None
        self.zip_path: Path | None = None
        self.sha1: str | None = None
        self.elapsed_seconds: float = 0.0


class PipelineCompiler:
    """Runs the full 17-step build described in the project spec."""

    def __init__(self, root: Path) -> None:
        self.paths = Paths.from_root(root)
        self.logger = util.BuildLogger()

    def run(self) -> BuildResult:
        start = time.perf_counter()
        result = BuildResult()

        self.logger.section(f"Building {PACK_NAME}")

        # 1-2. Clean and recreate output locations.
        self.logger.step("Cleaning previous build output")
        util.clean_dir(self.paths.generated)
        util.clean_dir(self.paths.output)

        self.logger.step("Recreating build directories")
        util.ensure_dir(self.paths.generated)
        util.ensure_dir(self.paths.output)
        util.ensure_dir(self.paths.namespace_root)

        # 3. Scan source/ into a Registry.
        self.logger.step("Scanning source assets")
        registry = RegistryScanner(self.paths.source, self.logger).scan()
        result.registry = registry
        self.logger.info(
            f"{len(registry.items)} item asset(s), "
            f"{sum(len(v) for v in registry.raw_textures.values())} raw texture(s), "
            f"{len(registry.sounds)} sound(s), "
            f"{len(registry.fonts)} font asset(s)"
        )

        # 4. Copy textures (item + raw categories).
        self.logger.step("Copying textures")
        TextureCompiler(self.paths, self.logger).compile(registry)

        # 5. Generate item model JSON.
        self.logger.step("Generating item model JSON files")
        ModelGenerator(self.paths, self.logger).generate(registry)

        # 6. Generate item definition JSON.
        self.logger.step("Generating item definition files")
        ItemDefinitionGenerator(self.paths, self.logger).generate(registry)

        # 7-8. Copy sounds + sounds.json.
        self.logger.step("Copying sounds and generating sounds.json")
        SoundCompiler(self.paths, self.logger).compile(registry)

        # 9. GUI assets were already copied as part of step 4 (raw textures);
        #    called out separately here to match the spec's step list.
        self.logger.step("GUI assets copied")
        self.logger.info(f"{len(registry.raw_textures.get('gui', []))} GUI asset file(s)")

        # 10. Copy fonts.
        self.logger.step("Copying fonts")
        pack_assembler = PackAssembler(self.paths, self.logger)
        pack_assembler.copy_fonts(registry)

        # 11. Generate language files.
        self.logger.step("Generating language files")
        LanguageGenerator(self.paths, self.logger).generate(registry)

        # 12. Generate Java helper classes.
        self.logger.step("Generating Java helper classes")
        JavaGenerator(self.paths, self.logger).generate(registry)

        # Copy manual overrides from source/assets/ to generated/assets/ if exists
        source_assets_dir = self.paths.source / "assets"
        if source_assets_dir.is_dir():
            self.logger.step("Copying manual assets overrides")
            import shutil
            shutil.copytree(source_assets_dir, self.paths.assets_root, dirs_exist_ok=True)

        # 13-14. pack.mcmeta + pack.png.
        self.logger.step("Writing pack.mcmeta and pack.png")
        pack_assembler.write_mcmeta()
        pack_assembler.copy_pack_png(registry)

        # 15. Build the zip.
        self.logger.step(f"Building {PACK_NAME}.zip")
        result.zip_path = PackZipper(self.paths, self.logger).build()

        # 16. Hash it.
        self.logger.step("Calculating SHA1")
        result.sha1 = PackHasher(self.logger).hash_file(result.zip_path)

        result.elapsed_seconds = time.perf_counter() - start

        # 17. Summary.
        self._print_summary(result)
        return result

    def _print_summary(self, result: BuildResult) -> None:
        registry = result.registry
        assert registry is not None

        self.logger.section("Build Summary")
        print(f"  Items generated   : {len(registry.items)}")
        for category in sorted({i.category for i in registry.items}):
            count = len(registry.items_by_category(category))
            print(f"    - {category:<10} {count}")
        print(f"  Sounds copied     : {len(registry.sounds)}")
        print(f"  Font assets       : {len(registry.fonts)}")
        print(f"  Raw textures      : {sum(len(v) for v in registry.raw_textures.values())}")
        print(f"  Output zip        : {result.zip_path}")
        print(f"  SHA1              : {result.sha1}")
        print(f"  Time              : {result.elapsed_seconds:.2f}s")
        print()
        print("Build complete.")
