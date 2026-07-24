package com.example.corovaItems.ItemMutations;

public enum MutationType {
    LIFE_SIPHON,
    EXTRACTOR_O_ENCHANTING,
    BLEED,
    ARROW_VELOCITY,
    DOUBLE_TAP,
    TRIPLE_TAP,
    BATTLE_HARDENED,
    NETHER_FIRE,
    SPLINTER,
    FROST,
    VENOM,
    DECAY,
    CLOBBER,
    COLD_STEEL,
    SHATTER,
    STATIC_CHARGE,
    LIFE_STEAL,
    EXTRA_CUSTOM_ENCHANT_SLOT,
    EXTRA_MUTATION_SLOT,
    MANA_CONSERVATION,
    MYSTIC_ARCANUM,
    SOUL_SIPHON,
    AUTO_SMELT,
    EXCAVATION,
    SOLID_STANCE,
    KINETIC_CHARGE,
    AMPLIFIER,
    DICE,
    FEAR,
    BACKSTAB,
    PARRY,
    LAST_STAND,
    BREAK_THROUGH,

    // ── Material-Specific Armor Mutations ─────────────────────────────────────
    // Iron
    HEAVY_METAL,
    // Chainmail
    WOUND_MENDING,
    // Netherite
    BRIMSTONE,
    // Leather
    HUNTERS_INSTINCT,
    // Gold
    GOLDEN_AEGIS,
    // Diamond
    PRISMATIC_EDGE,
    // Turtle Shell
    TORTOISESHELL,

    // ── Weapon Mutations ──────────────────────────────────────────────────────
    SKULL_CRUSH;

    // Restrictions summary (for reference — enforced in MutationManager.tryToMutate):
    // KINETIC_CHARGE   → Copper armor only
    // HEAVY_METAL      → Iron armor only
    // WOUND_MENDING    → Chainmail armor only
    // BRIMSTONE        → Netherite armor only
    // HUNTERS_INSTINCT → Leather armor only  (isCompatible check in class)
    // GOLDEN_AEGIS     → Gold armor only      (isCompatible check in class)
    // PRISMATIC_EDGE   → Diamond armor only   (isCompatible check in class)
    // TORTOISESHELL    → Turtle Shell only    (isCompatible check in class)
    // SKULL_CRUSH      → Axe only
    // PARRY            → Sword only
    // FEAR             → Scythe only
    // DICE             → Scythe only
}