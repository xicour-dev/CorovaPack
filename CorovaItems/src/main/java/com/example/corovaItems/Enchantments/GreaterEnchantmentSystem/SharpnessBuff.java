package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Applies a bonus damage multiplier when a weapon carries Sharpness VI or higher.
 *
 * Tier table (matches CorovaCombat's no-i-frame / melee-cooldown system):
 *
 *  Tier | Sharpness | Extra damage multiplier
 *  -----|-----------|------------------------
 *   1   |    VI     |  +2%
 *   2   |    VII    |  +4%
 *   3   |    VIII   |  +7%
 *   4   |    IX     |  +9%
 *   5   |    X      |  +11%
 *   6   |    XI     |  +14%
 *   7   |    XII    |  +16%
 *   8   |    XIII   |  +19%
 *
 * Uses {@link VanillaEnchantDisplay#getTrueLevel} to read the real enchantment
 * level because the live enchant on the item is clamped to 10 for display purposes.
 */
public class SharpnessBuff implements Listener {

    // Maps enchantment level → bonus multiplier (e.g. 0.02 = +2 %)
    private static final double[] BONUS_BY_LEVEL = {
            // index 0-5 unused (levels 1-5 are vanilla, no extra buff)
            0.00, // 1
            0.00, // 2
            0.00, // 3
            0.00, // 4
            0.00, // 5
            0.05, // 6  – Tier 1
            0.12, // 7  – Tier 2
            0.20, // 8  – Tier 3
            0.30, // 9  – Tier 4
            0.42, // 10 – Tier 5
            0.55, // 11 – Tier 6
            0.70, // 12 – Tier 7
            0.90, // 13 – Tier 8
    };

    /**
     * Runs at HIGH priority so it fires after CorovaCombat (NORMAL) has already
     * set the base damage, but before any protection calculations at HIGHEST.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        // Only care about players attacking living entities
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || !weapon.hasItemMeta()) return;

        // Use getTrueLevel so we read the PDC-stored value for levels > 10,
        // rather than the clamped live enchant level.
        int level = VanillaEnchantDisplay.getTrueLevel(weapon, Enchantment.SHARPNESS);
        if (level < 1) return;

        double bonus = getBonusMultiplier(level);
        if (bonus <= 0.0) return; // levels 1-5 receive no extra buff

        double newDamage = event.getDamage() * (1.0 + bonus);
        event.setDamage(newDamage);
    }

    /**
     * Convenience helper — returns the bonus multiplier for a given sharpness
     * level, or 0.0 if the level is out of range / below Tier 1.
     */
    public static double getBonusMultiplier(int sharpnessLevel) {
        if (sharpnessLevel < 1) return 0.0;
        if (sharpnessLevel >= BONUS_BY_LEVEL.length) {
            return BONUS_BY_LEVEL[BONUS_BY_LEVEL.length - 1];
        }
        return BONUS_BY_LEVEL[sharpnessLevel];
    }
}