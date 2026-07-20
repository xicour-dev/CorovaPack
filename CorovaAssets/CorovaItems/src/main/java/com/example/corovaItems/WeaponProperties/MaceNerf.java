package com.example.corovaItems.WeaponProperties;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * MaceNerf — only nerfs the fall-height damage bonus.
 *
 * Vanilla maces deal +1 damage per block fallen (before Density).
 * This caps the fall distance counted toward that bonus so extreme
 * drop combos can't one-shot, while leaving base mace damage and
 * all enchant levels completely untouched.
 */
public class MaceNerf implements Listener {

    /**
     * Hard cap on how many blocks of fall distance are counted toward
     * the mace's height damage bonus. Adjust to taste.
     *   ~6  = aggressive nerf  (short hops still rewarded)
     *   ~10 = moderate nerf    (recommended starting point)
     *   ~15 = light nerf       (only stops extreme sky-drop oneshots)
     */
    private static final double MAX_COUNTED_FALL_BLOCKS = 10.0;

    /** Vanilla mace: +1 damage per block fallen (before Density scaling). */
    private static final double DAMAGE_PER_BLOCK = 1.0;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMaceHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack hand = attacker.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.MACE) return;

        float fall = attacker.getFallDistance();
        if (fall <= MAX_COUNTED_FALL_BLOCKS) return; // no excess to remove

        int densityLevel = hand.getEnchantmentLevel(Enchantment.DENSITY);

        // Density multiplies the per-block bonus: total bonus = fall * (1 + 0.5 * density)
        double bonusPerBlock = DAMAGE_PER_BLOCK * (1.0 + 0.5 * densityLevel);

        // Extra blocks beyond our cap that Bukkit already counted
        double excessBlocks = fall - MAX_COUNTED_FALL_BLOCKS;
        double excess = excessBlocks * bonusPerBlock;

        double adjusted = Math.max(event.getDamage() - excess, 1.0);
        event.setDamage(adjusted);
    }
}