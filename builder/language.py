"""
builder.language
=================

Builds assets/<namespace>/lang/*.json files. For every source lang file
(e.g. source/lang/en_us.json), auto-generates a display-name key for
every discovered item (item.corova.<category>_<name>) and then merges
in whatever the source file already contains, with the hand-authored
values always winning. If no source lang files exist at all, a single
en_us.json is still produced from the automatic keys so the pack is
never missing item names.
"""

from __future__ import annotations

from . import util
from .config import Paths
from .registry import Registry

_DEFAULT_LOCALE = "en_us"


class LanguageGenerator:
    def __init__(self, paths: Paths, logger) -> None:
        self.paths = paths
        self.logger = logger

    def generate(self, registry: Registry) -> int:
        auto_keys = {item.lang_key: item.display_name for item in registry.items}

        locales_written = 0

        if registry.lang_files:
            for lang_file in registry.lang_files:
                locale = lang_file.stem
                manual = util.read_json(lang_file)
                merged = {**auto_keys, **manual} if locale == _DEFAULT_LOCALE else dict(manual)
                util.write_json(self.paths.lang_dir / f"{locale}.json", merged, sort_keys=True)
                locales_written += 1

            if not any(f.stem == _DEFAULT_LOCALE for f in registry.lang_files):
                util.write_json(self.paths.lang_dir / f"{_DEFAULT_LOCALE}.json", auto_keys, sort_keys=True)
                locales_written += 1
        else:
            util.write_json(self.paths.lang_dir / f"{_DEFAULT_LOCALE}.json", auto_keys, sort_keys=True)
            locales_written = 1

        self.logger.info(f"{locales_written} language file(s) generated ({len(auto_keys)} auto key(s))")
        return locales_written
