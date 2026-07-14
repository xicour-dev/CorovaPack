#!/usr/bin/env python3
"""
CorovaAssets build entry point.

    python build.py

Compiles source/ into the finished CorovaPack.zip resource pack plus
generated Java helper classes for the plugin. This script contains no
gameplay logic -- see builder/compiler.py for the full pipeline, and
the module docstrings under builder/ for what each stage does.
"""

from __future__ import annotations

import sys
from pathlib import Path

from builder.compiler import PipelineCompiler


def main() -> int:
    root = Path(__file__).resolve().parent
    compiler = PipelineCompiler(root)
    try:
        compiler.run()
    except Exception as exc:  # noqa: BLE001
        print(f"\nBUILD FAILED: {exc}", file=sys.stderr)
        raise
    return 0


if __name__ == "__main__":
    sys.exit(main())
