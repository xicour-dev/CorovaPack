package com.example.corovaItems.ArmorTrims;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum TrimMaterialType {
    IRON, COPPER, REDSTONE, GOLD, LAPIS,
    EMERALD, DIAMOND, NETHERITE, QUARTZ,
    AMETHYST, RESIN;

    public String getColorCode() {
        return switch (this) {
            case IRON, QUARTZ -> "§f";
            case COPPER, RESIN -> "§6";
            case REDSTONE     -> "§c";
            case GOLD         -> "§e";
            case LAPIS        -> "§9";
            case EMERALD      -> "§a";
            case DIAMOND      -> "§b";
            case NETHERITE    -> "§8";
            case AMETHYST     -> "§d";
        };
    }

    public String getDisplayName() {
        String name = name().toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public String getPassiveDescription() {
        return switch (this) {
            case IRON      -> "+0.5 Armor";
            case COPPER    -> "+7.5% Attack Speed";
            case REDSTONE  -> "+7.5% Speed";
            // Gold passive: armor + base absorption; synergy bonus shown separately via GA lore
            case GOLD      -> "+1.0 Armor & +2.5♥ Absorption (Regenerating)";
            case LAPIS     -> "+5 Max Mana";
            case EMERALD   -> "+2.5 Hearts";
            case DIAMOND   -> "+0.5 Armor & +0.5 Toughness";
            case NETHERITE -> "+1 Toughness & 20% Fire Immunity";
            case QUARTZ    -> "+15% Crit Damage per piece";
            case AMETHYST  -> "+12 Max Mana";
            case RESIN     -> "Replicates present materials";
            default        -> null;
        };
    }

    public String getMutationDescription() {
        return switch (this) {
            case IRON      -> "Debuff Mutations: +15% duration";
            case COPPER    -> "Burst Mutations: +12% window";
            case REDSTONE  -> "Incremental Mutations: -5.25% Hits Needed";
            // Gold mutation: synergy bonus scales with GA level (+1.5♥ at lvl 1, +2.5♥ at lvl 2)
            case GOLD      -> "Golden Aegis: +1.5♥ synergy/trim (lvl 1) or +2.5♥/trim (lvl 2)";
            case LAPIS     -> "Synergy Mutations: +10% power";
            case EMERALD   -> "Health Scale: +5% threshold & Recovery: +15% power";
            case DIAMOND   -> "Conditional Mutations: +8% window & Prismatic Edge: +10% Damage";
            case NETHERITE -> "Sustained Mutations: +12% tick damage";
            case QUARTZ    -> "Burst & Conditional: +8% damage per piece when mutation has BOTH categories";
            case AMETHYST  -> "All Enchantments: -8% cooldown";
            case RESIN     -> "Mutation/Passive additive replication";
        };
    }

    /**
     * Returns the description of this material's 4-piece full-set bonus,
     * or null if the material has no set bonus defined.
     */
    public String getSetBonusDescription() {
        return switch (this) {
            case IRON      -> "-10% incoming damage";
            case COPPER    -> "+12% Attack Speed bonus";
            case REDSTONE  -> "+50% damage dealt & -30% taken below 50% health";
            // Gold set bonus: full pure 4/4 gold trim set doubles absorption regen speed,
            // independent of any Golden Aegis mutation pieces worn.
            // Additionally, when Golden Aegis is worn: GA I gives +1.5♥/trim synergy;
            // GA II gives +2.5♥/trim synergy. Full 4× GA II + 4× gold trims reaches 30♥ (3 rows).
            case GOLD      -> "Full Set (4/4 Gold Trims): 2x Absorption Regen Speed\n"
                    + "GA I + 4× Gold Trims = +1.5♥/trim synergy (22♥ total)\n"
                    + "GA II + 4× Gold Trims = +2.5♥/trim synergy (30♥ total)";
            case LAPIS     -> "Mana Regen +50%";
            case EMERALD   -> null;
            case DIAMOND   -> "+2 Armor & +1 Toughness bonus";
            case NETHERITE -> "Complete fire immunity";
            case QUARTZ    -> null; // per-piece crit bonus only — no separate set bonus
            case AMETHYST  -> "+50 Max Mana";
            case RESIN     -> "Counts as 1 extra for every material already worn";
        };
    }

    private static final Map<String, TrimMaterialType> KEY_MAP = new HashMap<>();

    static {
        KEY_MAP.put("iron",      IRON);
        KEY_MAP.put("copper",    COPPER);
        KEY_MAP.put("redstone",  REDSTONE);
        KEY_MAP.put("gold",      GOLD);
        KEY_MAP.put("lapis",     LAPIS);
        KEY_MAP.put("emerald",   EMERALD);
        KEY_MAP.put("diamond",   DIAMOND);
        KEY_MAP.put("netherite", NETHERITE);
        KEY_MAP.put("quartz",    QUARTZ);
        KEY_MAP.put("amethyst",  AMETHYST);
        KEY_MAP.put("resin",     RESIN);
    }

    // Bukkit's TrimMaterial key is like "minecraft:iron" — strip the namespace
    public static Optional<TrimMaterialType> fromBukkitKey(String namespacedKey) {
        String path = namespacedKey.contains(":")
                ? namespacedKey.split(":")[1]
                : namespacedKey;
        return Optional.ofNullable(KEY_MAP.get(path.toLowerCase()));
    }
}