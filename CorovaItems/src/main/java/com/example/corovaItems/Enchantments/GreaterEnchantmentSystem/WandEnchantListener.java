package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility helpers shared by all wand projectile handlers.
 *
 * All enchantment level reads use {@link VanillaEnchantDisplay#getTrueLevel} so
 * that levels above 10 — stored in PDC because the live enchant is clamped for
 * display purposes — are correctly reflected in damage scaling.
 */
public class WandEnchantListener {

    public static final Set<Material> WAND_MATERIALS = Stream.of(
            Material.matchMaterial("WOODEN_SPEAR"),
            Material.matchMaterial("STONE_SPEAR"),
            Material.matchMaterial("IRON_SPEAR"),
            Material.matchMaterial("GOLDEN_SPEAR"),
            Material.matchMaterial("COPPER_SPEAR"),
            Material.matchMaterial("DIAMOND_SPEAR"),
            Material.matchMaterial("NETHERITE_SPEAR")
    ).filter(Objects::nonNull).collect(Collectors.toSet());

    public static boolean isWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!WAND_MATERIALS.contains(item.getType())) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Wand");
    }

    public static boolean isWand(Material material) {
        return WAND_MATERIALS.contains(material);
    }

    /**
     * Calculates scaled damage for wands based on Power, Smite, and Bane of Arthropods.
     * Logic refinements for high-level enchantments (up to lvl 10) are handled
     * via the standard scaling formulas below.
     *
     * Scaling formula:
     * - Power: +10% base damage per level
     * - Smite: +2.5 flat damage per level (against Undead)
     * - Bane of Arthropods: +2.5 flat damage per level (against Arthropods)
     *
     * Uses {@link VanillaEnchantDisplay#getTrueLevel} for every enchantment read so
     * over-cap PDC-stored levels are used in the math instead of the clamped live value.
     */
    public static double getScaledDamage(double baseDamage, ItemStack weapon, Player shooter, Entity target) {
        if (!isWand(weapon)) return baseDamage;
        double finalDamage = baseDamage;

        // Power scaling (+10% per level)
        int powerLevel = VanillaEnchantDisplay.getTrueLevel(weapon, Enchantment.POWER);
        if (powerLevel > 0) {
            finalDamage += baseDamage * (powerLevel * 0.10);
        }

        // Smite scaling (+2.5 per level, with diminishing returns beyond lvl 5)
        int smiteLevel = VanillaEnchantDisplay.getTrueLevel(weapon, Enchantment.SMITE);
        if (smiteLevel > 0 && BowDamageScaling.isUndead(target)) {
            if (smiteLevel <= 5) {
                finalDamage += 2.5 * smiteLevel;
            } else {
                finalDamage += 12.5 + (1.5 * (smiteLevel - 5));
            }
        }

        // Bane of Arthropods scaling (+2.5 per level, with diminishing returns beyond lvl 5)
        int baneLevel = VanillaEnchantDisplay.getTrueLevel(weapon, Enchantment.BANE_OF_ARTHROPODS);
        if (baneLevel > 0 && BowDamageScaling.isArthropod(target)) {
            if (baneLevel <= 5) {
                finalDamage += 2.5 * baneLevel;
            } else {
                finalDamage += 12.5 + (1.5 * (baneLevel - 5));
            }
        }

        return finalDamage;
    }

    /**
     * Returns an arbitrary unit vector perpendicular to {@code dir}.
     */
    public static Vector perpendicularVector(Vector dir) {
        Vector ref = Math.abs(dir.getX()) < 0.9
                ? new Vector(1, 0, 0)
                : new Vector(0, 1, 0);
        return dir.getCrossProduct(ref).normalize();
    }

    /**
     * Standard target validation for wand projectiles.
     */
    public static boolean isValidTarget(LivingEntity le, Player shooter) {
        if (le.isDead() || !le.isValid()) return false;
        if (le.equals(shooter)) return false;
        if (le instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE
                    || p.getGameMode() == GameMode.SPECTATOR) return false;
            CorovaGuard guard = CorovaGuard.getInstance();
            if (guard != null && guard.isPlayerInSafeZone(p)) return false;
            CorovaTeams teams = CorovaTeams.getInstance();
            if (teams != null) {
                CorovaTeam attackerTeam = teams.getTeamManager().getTeamByPlayer(shooter.getUniqueId());
                CorovaTeam victimTeam   = teams.getTeamManager().getTeamByPlayer(p.getUniqueId());
                if (attackerTeam != null && attackerTeam.equals(victimTeam)
                        && !attackerTeam.hasFriendlyFire()) return false;
            }
        }
        return true;
    }
}