"""
Corova Chronicles asset pipeline.

This package compiles raw source assets (textures, sounds, fonts, GUI art)
into a finished Minecraft: Java Edition resource pack, and emits Java
constant classes so the companion Paper plugin never has to hardcode
resource identifiers.

This package does NOT contain, and must never contain, gameplay logic
(damage values, abilities, crafting recipes, mutations, AI, progression).
All of that lives in the Paper plugin. This is an asset compiler only.
"""

__version__ = "1.0.0"
