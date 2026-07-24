"""
builder.util
============

Small, dependency-free helpers shared across the pipeline stages:
filesystem prep, JSON I/O, string/identifier conversion, and a tiny
build logger. Kept deliberately boring -- nothing here should ever need
to know what a "scythe" is.
"""

from __future__ import annotations

import json
import re
import shutil
from pathlib import Path
from typing import Any


# ---------------------------------------------------------------------------
# Filesystem
# ---------------------------------------------------------------------------

def clean_dir(path: Path) -> None:
    """Delete a directory tree if it exists, leaving nothing behind."""
    if path.exists():
        shutil.rmtree(path)


def ensure_dir(path: Path) -> Path:
    """Create a directory (and parents) if missing. Returns the path."""
    path.mkdir(parents=True, exist_ok=True)
    return path


def copy_file(src: Path, dest: Path) -> None:
    ensure_dir(dest.parent)
    shutil.copy2(src, dest)


# ---------------------------------------------------------------------------
# JSON
# ---------------------------------------------------------------------------

def write_json(path: Path, data: Any, *, sort_keys: bool = False) -> None:
    ensure_dir(path.parent)
    with path.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, sort_keys=sort_keys)
        fh.write("\n")


def read_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


# ---------------------------------------------------------------------------
# Identifiers
# ---------------------------------------------------------------------------

_NON_ALNUM = re.compile(r"[^a-z0-9_]+")


def to_snake_case(name: str) -> str:
    """Normalize an arbitrary folder/file name into a safe snake_case id."""
    lowered = name.strip().lower().replace("-", "_").replace(" ", "_")
    return _NON_ALNUM.sub("_", lowered).strip("_")


def to_screaming_snake_case(name: str) -> str:
    return to_snake_case(name).upper()


def to_title_case(name: str) -> str:
    """Turn 'blood_scythe' into 'Blood Scythe' for default lang entries."""
    return " ".join(part.capitalize() for part in to_snake_case(name).split("_") if part)


def java_class_name(stem: str) -> str:
    return "".join(part.capitalize() for part in to_snake_case(stem).split("_"))


def strip_namespace(identifier: str) -> str:
    """'minecraft:diamond_sword' -> 'diamond_sword'."""
    return identifier.split(":", 1)[-1]


# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

class BuildLogger:
    """Minimal, dependency-free step logger for a clean console build log."""

    def __init__(self) -> None:
        self._step_no = 0

    def step(self, message: str) -> None:
        self._step_no += 1
        print(f"[{self._step_no:2}] {message}")

    def info(self, message: str) -> None:
        print(f"     - {message}")

    def warn(self, message: str) -> None:
        print(f"     ! WARNING: {message}")

    def section(self, title: str) -> None:
        print()
        print(f"== {title} ==")
