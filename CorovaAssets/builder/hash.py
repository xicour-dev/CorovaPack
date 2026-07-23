"""
builder.hash
============

Computes the SHA1 of the built pack zip -- the value Minecraft servers
use in the resource-pack "hash" field so clients know when to
re-download. Also writes it to a sidecar .sha1 file for convenience.
"""

from __future__ import annotations

import hashlib
from pathlib import Path


class PackHasher:
    def __init__(self, logger) -> None:
        self.logger = logger

    def hash_file(self, path: Path) -> str:
        sha1 = hashlib.sha1()
        with path.open("rb") as fh:
            for chunk in iter(lambda: fh.read(1024 * 1024), b""):
                sha1.update(chunk)
        digest = sha1.hexdigest()

        sidecar = path.with_suffix(path.suffix + ".sha1")
        sidecar.write_text(digest + "\n", encoding="utf-8")

        self.logger.info(f"SHA1: {digest}")
        return digest
