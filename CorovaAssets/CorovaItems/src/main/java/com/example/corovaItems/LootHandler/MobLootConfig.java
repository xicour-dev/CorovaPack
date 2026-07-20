package com.example.corovaItems.LootHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds per-mob drop configuration for a single item loot rule.
 *
 * DROP GROUP behaviour:
 *   null (DROPLIMITER OFF) — this item is UNLIMITED.
 *                            It always drops independently when its roll succeeds,
 *                            regardless of any other drops on the same death.
 *                            Multiple unlimited items can all drop in the same death.
 *
 *   non-null string        — this item is LIMITED to its named group.
 *                            All items whose dropGroup equals the same string compete:
 *                            if multiple succeed in the same death, only ONE from that
 *                            group is kept (chosen at random).
 *                            Using different group names creates independent pools,
 *                            so you can have e.g. one "trinket" winner AND one "book"
 *                            winner from the same mob death.
 *
 *   The legacy boolean constructor maps  true  → group "default"
 *                                         false → group null (unlimited)
 *   for full backwards compatibility.
 */
public class MobLootConfig {

    /** Sentinel used by the legacy boolean API to mean "competes in one shared pool". */
    public static final String DEFAULT_GROUP = "default";

    private final double baseChance;
    private final double lootingBonus;
    private final double luckBonus;
    private final int minLevel;
    private final int maxLevel;

    /**
     * null  = DROPLIMITER OFF (unlimited, always drops on success).
     * any string = DROPLIMITER ON for that named group.
     */
    private final String dropGroup;

    // ── Full constructor ──────────────────────────────────────────────────────

    /**
     * @param baseChance    Base drop chance (0.0–1.0)
     * @param lootingBonus  Bonus per Looting level (0.0–1.0)
     * @param luckBonus     Bonus per Luck level (0.0–1.0)
     * @param dropGroup     null = unlimited; any string = limited to that named group
     * @param minLevel      Minimum level of the item
     * @param maxLevel      Maximum level of the item
     */
    public MobLootConfig(double baseChance, double lootingBonus, double luckBonus, String dropGroup, int minLevel, int maxLevel) {
        this.baseChance    = baseChance;
        this.lootingBonus  = lootingBonus;
        this.luckBonus     = luckBonus;
        this.dropGroup     = dropGroup;
        this.minLevel      = minLevel;
        this.maxLevel      = maxLevel;
    }

    /**
     * @param baseChance    Base drop chance (0.0–1.0)
     * @param lootingBonus  Bonus per Looting level (0.0–1.0)
     * @param luckBonus     Bonus per Luck level (0.0–1.0)
     * @param dropGroup     null = unlimited; any string = limited to that named group
     */
    public MobLootConfig(double baseChance, double lootingBonus, double luckBonus, String dropGroup) {
        this(baseChance, lootingBonus, luckBonus, dropGroup, 1, 1);
    }

    // ── Legacy boolean constructor (full backwards compatibility) ─────────────

    /**
     * Legacy constructor.
     * {@code dropLimiterEnabled = true}  maps to group {@value #DEFAULT_GROUP}.
     * {@code dropLimiterEnabled = false} maps to group {@code null} (unlimited).
     */
    public MobLootConfig(double baseChance, double lootingBonus, double luckBonus, boolean dropLimiterEnabled) {
        this(baseChance, lootingBonus, luckBonus, dropLimiterEnabled ? DEFAULT_GROUP : null, 1, 1);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public double  getBaseChance()   { return baseChance;   }
    public double  getLootingBonus() { return lootingBonus; }
    public double  getLuckBonus()    { return luckBonus;    }

    /**
     * Returns the drop group this item competes in, or {@code null} if it is unlimited.
     */
    public String  getDropGroup()    { return dropGroup;    }

    /**
     * Convenience: {@code true} when this item is limited (part of any named group).
     */
    public boolean isDropLimiterEnabled() { return dropGroup != null; }

    public int getMinLevel() { return minLevel; }
    public int getMaxLevel() { return maxLevel; }

    /**
     * Returns a random level between minLevel and maxLevel (inclusive).
     */
    public int getRandomLevel() {
        if (minLevel >= maxLevel) return minLevel;
        return ThreadLocalRandom.current().nextInt(minLevel, maxLevel + 1);
    }
}