package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import java.util.Random;
import java.util.function.BiPredicate;

public class EnchantmentTierManager {

    /**
     * Default is FALSE — no tier unless explicitly granted via permission or
     * a registered checker.  The old default of (p,t)->true caused every player
     * to be treated as tier 5 before CorovaCore called setTierChecker(), which
     * was the root cause of Sharpness 9 and cost-49 appearing at tier 2.
     */
    private static BiPredicate<Player, Integer> tierChecker = (p, t) -> false;

    public static void setTierChecker(BiPredicate<Player, Integer> checker) {
        tierChecker = checker;
        com.example.corovaItems.Trinkets.Backpack.setTierChecker(checker);
    }

    /**
     * Returns the enchantment-system tier (0–8) for a player.
     * Checks permission nodes first, then the registered tier checker.
     * Clamped to 8 so tier 9+ players still use the tier-8 ceiling.
     */
    public static int getPlayerTier(Player player) {
        for (int t = 8; t >= 1; --t) {
            if (tierChecker.test(player, t)) return t;
        }
        return 0;
    }

    /**
     * Maximum lapis/XP cost a player can see on the enchant table.
     *
     *   Tier 0 → 30   (vanilla ceiling)
     *   Tier 1 → 35
     *   Tier 2 → 40
     *   Tier 3 → 45
     *   Tier 4 → 50
     *   Tier 5 → 55
     *   Tier 6 → 60
     *   Tier 7 → 65
     *   Tier 8 → 70
     */
    public static int getMaxTableLevel(Player player) {
        return 30 + (getPlayerTier(player) * 5);
    }

    /**
     * Returns the highest enchantment level the player may receive for
     * {@code ench}, based on vanilla max + tier bonus, with per-enchant caps.
     *
     *   FORTUNE / LOOTING  → max 5
     *   UNBREAKING         → max 10
     *   EFFICIENCY         → max 10
     *   Armor enchants     → vanillaMax+0 at tier 0, +1 at tier 1–2, +2 at tier 3–4, +3 at tier 5-6, +4 at tier 7-8
     *   All others         → vanillaMax + tier (uncapped)
     */
    public static int getMaxAllowedLevel(Enchantment ench, Player player) {
        int tier = getPlayerTier(player);
        int calculated = ench.getMaxLevel() + tier;

        if (ench.equals(Enchantment.FORTUNE) || ench.equals(Enchantment.LOOTING)) {
            return Math.min(calculated, 5);
        }
        if (ench.equals(Enchantment.UNBREAKING)) {
            return Math.min(calculated, 10);
        }
        if (ench.equals(Enchantment.EFFICIENCY)) {
            return Math.min(calculated, 10);
        }
        if (isArmorEnchant(ench)) {
            // All armor enchants upgrade every other tier, same as protection:
            // Tier 0 → vanilla max, Tier 1–2 → +1, Tier 3–4 → +2, Tier 5-6 → +3, Tier 7-8 → +4
            int armorCap = ench.getMaxLevel() + (tier >= 7 ? 4 : tier >= 5 ? 3 : tier >= 3 ? 2 : tier >= 1 ? 1 : 0);
            return Math.min(calculated, armorCap);
        }
        return calculated;
    }

    /**
     * Maps the slot's lapis/XP cost to an enchantment level, then clamps to the
     * player's tier-adjusted ceiling for that enchantment.
     *
     * Features a more gradual mapping (baseLevel = cost/5 + 1) and "lean toward max"
     * randomization to prevent always giving the ceiling.
     */
    public static int getEnchantLevelForCost(int cost, Enchantment enchantment, Player player) {
        int maxAllowed = getMaxAllowedLevel(enchantment, player);
        int baseLevel = (cost / 5) + 1;

        // "Lean toward max" randomization:
        // 70% chance for baseLevel, 20% for baseLevel-1, 10% for baseLevel-2.
        Random random = new Random();
        double roll = random.nextDouble();
        int finalLevel = baseLevel;

        if (roll < 0.10) {
            finalLevel -= 2;
        } else if (roll < 0.30) {
            finalLevel -= 1;
        }

        return Math.max(1, Math.min(finalLevel, maxAllowed));
    }

    /**
     * Determines how many bonus enchantments should be added to a slot.
     * Includes a 20% chance to reduce the count for better variety.
     */
    public static int getBonusEnchantCount(int slot, int cost) {
        int count = 0;
        if (slot == 1) {
            if      (cost >= 35) count = 3;
            else if (cost >= 25) count = 2;
            else if (cost >= 10) count = 1;
        } else if (slot == 2) {
            if      (cost >= 45) count = 5;
            else if (cost >= 35) count = 4;
            else if (cost >= 25) count = 3;
            else if (cost >= 15) count = 2;
            else if (cost >= 1)  count = 1;
        }

        if (count > 0 && new Random().nextDouble() < 0.20) {
            count--;
        }

        return Math.max(0, count);
    }

    /**
     * Core tier-bonus formula (kept public for external callers that pass
     * vanillaMax and isWeapon directly).
     */
    public static int calculateMaxAllowedLevel(int vanillaMax, boolean isWeapon, int tier) {
        return vanillaMax + tier;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Classification helpers
    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isWeaponEnchantment(Enchantment ench) {
        if (ench == null) return false;
        return ench.equals(Enchantment.SHARPNESS)
                || ench.equals(Enchantment.SMITE)
                || ench.equals(Enchantment.BANE_OF_ARTHROPODS)
                || ench.equals(Enchantment.KNOCKBACK)
                || ench.equals(Enchantment.FIRE_ASPECT)
                || ench.equals(Enchantment.LOOTING)
                || ench.equals(Enchantment.SWEEPING_EDGE)
                || ench.equals(Enchantment.UNBREAKING)
                || ench.equals(Enchantment.EFFICIENCY)
                || ench.equals(Enchantment.FORTUNE)
                || ench.equals(Enchantment.POWER)
                || ench.equals(Enchantment.PUNCH)
                || ench.equals(Enchantment.FLAME)
                || ench.equals(Enchantment.INFINITY)
                || ench.equals(Enchantment.QUICK_CHARGE)
                || ench.equals(Enchantment.MULTISHOT)
                || ench.equals(Enchantment.PIERCING)
                || ench.equals(Enchantment.IMPALING)
                || ench.equals(Enchantment.LOYALTY)
                || ench.equals(Enchantment.RIPTIDE)
                || ench.equals(Enchantment.CHANNELING);
    }

    static boolean isProtectionEnchant(Enchantment ench) {
        return ench.equals(Enchantment.PROTECTION)
                || ench.equals(Enchantment.FIRE_PROTECTION)
                || ench.equals(Enchantment.BLAST_PROTECTION)
                || ench.equals(Enchantment.PROJECTILE_PROTECTION);
    }

    static boolean isArmorEnchant(Enchantment ench) {
        return ench.equals(Enchantment.PROTECTION)
                || ench.equals(Enchantment.FIRE_PROTECTION)
                || ench.equals(Enchantment.BLAST_PROTECTION)
                || ench.equals(Enchantment.PROJECTILE_PROTECTION)
                || ench.equals(Enchantment.FEATHER_FALLING)
                || ench.equals(Enchantment.RESPIRATION)
                || ench.equals(Enchantment.DEPTH_STRIDER)
                || ench.equals(Enchantment.THORNS)
                || ench.equals(Enchantment.AQUA_AFFINITY);
    }
}