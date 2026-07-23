"""
builder.zip
===========

Zips the finished resource pack (everything in generated/ except the
java/ helper-class output, which is a build artifact for the plugin,
not part of the pack itself) into output/<PACK_NAME>.zip.
"""

from __future__ import annotations

import zipfile
from pathlib import Path

from .config import PACK_NAME, Paths


class PackZipper:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def build(self) -> Path:
        dest = self.paths.output / f"{PACK_NAME}.zip"
        util_ensure = self.paths.output
        util_ensure.mkdir(parents=True, exist_ok=True)

        skip_dirs = {self.paths.java_dir.resolve()}

        with zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zf:
            for path in sorted(self.paths.generated.rglob("*")):
                if not path.is_file():
                    continue
                if any(str(path.resolve()).startswith(str(skip)) for skip in skip_dirs):
                    continue
                arcname = path.relative_to(self.paths.generated)
                zf.write(path, arcname)

        self.logger.info(f"{dest.name} built ({dest.stat().st_size:,} bytes)")
        return dest
