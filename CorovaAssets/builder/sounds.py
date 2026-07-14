"""
builder.sounds
===============

Copies every .ogg found under source/sounds/** into the generated pack
and emits sounds.json entries for them automatically, keyed off their
relative path. Nothing here assigns sounds to gameplay events -- that
mapping (which sound plays for which ability) belongs to the plugin,
which can reference these by the identifiers on SoundAsset / the
generated CorovaSounds.java constants.
"""

from __future__ import annotations

from . import util
from .config import NAMESPACE, Paths
from .registry import Registry


class SoundCompiler:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def compile(self, registry: Registry) -> int:
        if not registry.sounds:
            self.logger.info("no sound files found -- skipping sounds.json")
            return 0

        sounds_json: dict[str, dict] = {}

        for sound in registry.sounds:
            dest = self.paths.sounds_dir / sound.relative_path
            util.copy_file(sound.source_path, dest)

            sounds_json[sound.event_name] = {
                "sounds": [{"name": f"{NAMESPACE}:{sound.sound_id}"}]
            }

        util.write_json(self.paths.namespace_root / "sounds.json", sounds_json, sort_keys=True)

        self.logger.info(f"{len(registry.sounds)} sound file(s) copied, sounds.json generated")
        return len(registry.sounds)
