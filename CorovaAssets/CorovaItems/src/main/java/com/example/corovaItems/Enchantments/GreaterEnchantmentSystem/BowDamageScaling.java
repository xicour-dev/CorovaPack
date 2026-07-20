package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles damage scaling for SHARPNESS, SMITE, and BANE_OF_ARTHROPODS enchantments on bows/crossbows.
 *
 * Vanilla Minecraft only applies these damage-boosting enchantments when attacking with the weapon
 * directly in hand. Arrows fired from a bow carry no such logic. This listener intercepts
 * arrow damage events, checks whether the shooting bow has these enchantments, and adds
 * the correct bonus damage.
 *
 * To ensure balance, these bonuses are scaled by the bow's charge level (derived from arrow velocity).
 * This prevents players from dealing full enchantment bonus damage with rapid, uncharged shots,
 * mirroring how melee damage scales with attack strength.
 *
 *   SHARPNESS:           +0.3 * level + 0.2     (against all mobs)
 *   SMITE:               +1.5 damage per level  (against Undead mobs)
 *   BANE_OF_ARTHROPODS:  +1.5 damage per level  (against Arthropod mobs)
 *
 * These enchantments can coexist with POWER on bows in this system.
 *
 * All enchantment level reads use {@link VanillaEnchantDisplay#getTrueLevel} so that
 * levels above 10 (stored in PDC because the live enchant is clamped) are correctly
 * reflected in damage calculations.
 */
public class BowDamageScaling implements Listener {

    /**
     * Intercept arrow impacts to apply damage enchantment bonuses from the bow.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent event) {
        // We only care about projectile damage
        Entity damager = event.getDamager();
        if (!(damager instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) damager;

        // Only process arrows shot by a player
        if (!(arrow.getShooter() instanceof Player)) {
            return;
        }

        Player shooter = (Player) arrow.getShooter();

        // Retrieve the bow/crossbow that was used to fire the arrow.
        // Bukkit stores this on the arrow itself for player-fired arrows.
        ItemStack bow = getBowFromArrow(arrow, shooter);
        if (bow == null) {
            return;
        }

        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity)) {
            return;
        }

        double bonusDamage = getEnchantmentDamageBonus(bow, target);

        if (bonusDamage > 0.0) {
            // Scale enchantment damage by bow charge (0.0 to 1.0).
            // A fully charged bow shot has a velocity of ~3.0.
            double velocity = arrow.getVelocity().length();
            double chargeMultiplier = Math.min(1.0, velocity / 3.0);

            event.setDamage(event.getDamage() + (bonusDamage * chargeMultiplier));
        }
    }

    /**
     * Returns the vanilla POWER enchantment damage multiplier.
     * Formula: 1.0 + 0.25 * (level + 1)
     *
     * Uses {@link VanillaEnchantDisplay#getTrueLevel} to get the real level.
     */
    public static double getPowerMultiplier(ItemStack bow) {
        if (bow == null || !bow.hasItemMeta()) return 1.0;
        int level = VanillaEnchantDisplay.getTrueLevel(bow, Enchantment.POWER);
        return getPowerMultiplier(level);
    }

    /**
     * Returns the vanilla POWER enchantment damage multiplier for a given level.
     */
    public static double getPowerMultiplier(int level) {
        if (level <= 0) return 1.0;
        return 1.0 + 0.25 * (level + 1);
    }

    /**
     * Calculates the charge multiplier based on the projectile's velocity.
     * Fully charged shot: ~3.0 velocity -> 1.0 multiplier.
     */
    public static double getChargeMultiplier(Entity projectile) {
        if (projectile == null) return 1.0;
        double velocity = projectile.getVelocity().length();
        return Math.min(1.0, velocity / 3.0);
    }

    /**
     * Calculates explosive damage based on distance, level, and bow enchantments.
     * This nerfed formula provides better balance for Explosive Round.
     */
    public static double calculateExplosiveDamage(int level, int power, double distance, double radius, float force, double bonusDamage) {
        if (distance > radius) return 0.0;
        double baseDamage = 2.0 * level + (power * 0.5);
        return (baseDamage + bonusDamage) * (1 - (distance / radius)) * force;
    }

    /**
     * Calculates the bonus damage from Sharpness, Smite, and Bane of Arthropods on a bow.
     *
     * Uses {@link VanillaEnchantDisplay#getTrueLevel} for each enchantment so that
     * over-cap levels stored in PDC are correctly included in damage math.
     */
    public static double getEnchantmentDamageBonus(ItemStack bow, Entity target) {
        if (bow == null || !bow.hasItemMeta()) return 0.0;
        int sharpness = VanillaEnchantDisplay.getTrueLevel(bow, Enchantment.SHARPNESS);
        int smite     = VanillaEnchantDisplay.getTrueLevel(bow, Enchantment.SMITE);
        int bane      = VanillaEnchantDisplay.getTrueLevel(bow, Enchantment.BANE_OF_ARTHROPODS);
        return getEnchantmentDamageBonus(sharpness, smite, bane, target);
    }

    /**
     * Calculates the bonus damage from Sharpness, Smite, and Bane of Arthropods levels.
     */
    public static double getEnchantmentDamageBonus(int sharpness, int smite, int bane, Entity target) {
        double bonusDamage = 0.0;

        // SHARPNESS: +0.3 * level + 0.2 (diminishing returns beyond lvl 5)
        if (sharpness > 0) {
            if (sharpness <= 5) {
                bonusDamage += 0.3 * sharpness + 0.2;
            } else {
                bonusDamage += 1.7 + (0.15 * (sharpness - 5));
            }
        }

        // SMITE: +1.5 per level against Undead (diminishing returns beyond lvl 5)
        if (smite > 0 && isUndead(target)) {
            if (smite <= 5) {
                bonusDamage += 1.5 * smite;
            } else {
                bonusDamage += 7.5 + (1.0 * (smite - 5));
            }
        }

        // BANE_OF_ARTHROPODS: +1.5 per level against Arthropods (diminishing returns beyond lvl 5)
        if (bane > 0 && isArthropod(target)) {
            if (bane <= 5) {
                bonusDamage += 1.5 * bane;
            } else {
                bonusDamage += 7.5 + (1.0 * (bane - 5));
            }
        }

        return bonusDamage;
    }

    /**
     * Attempt to retrieve the bow/crossbow ItemStack that fired this arrow.
     *
     * Bukkit's AbstractArrow#getWeapon() (added in 1.20.4) is the cleanest way to get
     * the firing weapon. We fall back to the shooter's main-hand item for older server
     * versions that may not have the API yet.
     */
    public static ItemStack getBowFromArrow(AbstractArrow arrow, Player shooter) {
        // Primary: use getWeapon() if available (Paper/Bukkit 1.20.4+)
        if (arrow != null) {
            try {
                ItemStack weapon = arrow.getWeapon();
                if (weapon != null && isBow(weapon)) {
                    return weapon;
                }
            } catch (NoSuchMethodError ignored) {
                // API not present on this server version — fall through to fallback
            }
        }

        // Fallback: check the player's current main-hand item.
        // This is accurate when the player is still holding the bow (common case).
        ItemStack mainHand = shooter.getInventory().getItemInMainHand();
        if (isBow(mainHand)) {
            return mainHand;
        }

        ItemStack offHand = shooter.getInventory().getItemInOffHand();
        if (isBow(offHand)) {
            return offHand;
        }

        return null;
    }

    private static boolean isBow(ItemStack item) {
        if (item == null) return false;
        return item.getType() == org.bukkit.Material.BOW
                || item.getType() == org.bukkit.Material.CROSSBOW;
    }

    /**
     * Returns true if the entity is sensitive to SMITE, using the vanilla entity tag.
     * This replaces the removed Undead marker interface (removed after 1.20.4).
     */
    public static boolean isUndead(Entity entity) {
        return Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(entity.getType());
    }

    /**
     * Returns true if the entity is sensitive to BANE_OF_ARTHROPODS, using the vanilla entity tag.
     * This replaces the removed Arthropod marker interface (removed after 1.20.4).
     */
    public static boolean isArthropod(Entity entity) {
        return Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(entity.getType());
    }
}