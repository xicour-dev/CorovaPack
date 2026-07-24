package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LightningBook extends EnchantmentBook {

    // -------------------------------------------------------------------------
    // Level scaling
    //   I   → cooldown 10 s, damage multiplier 1.2×
    //   II  → cooldown  9 s, damage multiplier 1.6×
    //   III → cooldown  8 s, damage multiplier 2.0×
    //   IV  → cooldown  7 s, damage multiplier 2.5×
    //   V   → cooldown  6 s, damage multiplier 3.0×
    // -------------------------------------------------------------------------
    private static final long[]   COOLDOWN_BY_LEVEL   = { 0, 10_000L, 9_000L, 8_000L, 7_000L, 6_000L }; // ms
    private static final double[] MULTIPLIER_BY_LEVEL = { 0, 1.2,     1.6,    2.0,    2.5,    3.0    };

    // Static maps shared across all instances (same enchant ID, different levels)
    private static final Map<UUID, Long> cooldowns       = new HashMap<>();
    private static final Map<UUID, Long> lastMessageSent = new HashMap<>();

    public LightningBook() {
        this(1);
    }

    public LightningBook(int level) {
        super(
                "Book of Lightning",
                CorovaEnchantments.LIGHTNING_ID,
                level,
                "book_lightning",
                allowedMaterials()
        );
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(
                Material.WOODEN_SWORD,
                Material.STONE_SWORD,
                Material.IRON_SWORD,
                Material.GOLDEN_SWORD,
                Material.DIAMOND_SWORD,
                Material.NETHERITE_SWORD,
                Material.WOODEN_AXE,
                Material.STONE_AXE,
                Material.IRON_AXE,
                Material.GOLDEN_AXE,
                Material.DIAMOND_AXE,
                Material.NETHERITE_AXE,
                Material.TRIDENT,
                Material.MACE,
                Material.BOW,
                Material.CROSSBOW
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static long cooldownForLevel(int level) {
        if (level >= 1 && level < COOLDOWN_BY_LEVEL.length) return COOLDOWN_BY_LEVEL[level];
        return 6_000L; // min cooldown beyond V
    }

    private static double multiplierForLevel(int level) {
        if (level >= 1 && level < MULTIPLIER_BY_LEVEL.length) return MULTIPLIER_BY_LEVEL[level];
        return MULTIPLIER_BY_LEVEL[MULTIPLIER_BY_LEVEL.length - 1] + (level - 5) * 0.5;
    }

    // -------------------------------------------------------------------------
    // triggerEffect — called by BoomerangBook
    // -------------------------------------------------------------------------
    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (BoomerangBook.IS_BOOMERANG_HIT.get()) {
            target.getWorld().strikeLightningEffect(target.getLocation());
            com.example.corovaItems.ItemMutations.MutationManager.getInstance().getSynergyHandler().handleEnchantSynergy(CorovaEnchantments.LIGHTNING_ID, damager, target, BoomerangBook.SYNERGY_ITEM.get(), level);
            return;
        }
        long now      = System.currentTimeMillis();
        long lastUsed = cooldowns.getOrDefault(damager.getUniqueId(), 0L);
        long cd       = cooldownForLevel(level);

        if (now - lastUsed > cd) {
            cooldowns.put(damager.getUniqueId(), now);
            target.getWorld().strikeLightningEffect(target.getLocation());
            double damage = getWeaponDamageStatic(
                    damager.getEquipment() != null ? damager.getEquipment().getItemInMainHand() : null,
                    damager, target) * multiplierForLevel(level);
            target.setNoDamageTicks(0);

            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(damager.getUniqueId());
            try {
                target.damage(damage, damager);
            } finally {
                com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(damager.getUniqueId());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Right-click entity (player melee / trident / mace)
    // -------------------------------------------------------------------------
    @EventHandler
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity rightClicked)) return;

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(handItem, getEnchantId())) return;

        // Only handle the level this instance represents
        if (CorovaEnchantments.getEnchantLevel(handItem, CorovaEnchantments.LIGHTNING_ID) != this.getLevel()) return;

        Material type = handItem.getType();
        if (type == Material.BOW || type == Material.CROSSBOW) return;

        if (rightClicked instanceof Player victim) {
            if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                CorovaGuard.sendSafeZoneMessage(player);
                event.setCancelled(true);
                return;
            }
            CorovaTeams teamsInstance = CorovaTeams.getInstance();
            if (teamsInstance != null) {
                CorovaTeam attackerTeam = teamsInstance.getTeamManager().getTeamByPlayer(player.getUniqueId());
                CorovaTeam victimTeam   = teamsInstance.getTeamManager().getTeamByPlayer(victim.getUniqueId());
                if (attackerTeam != null && attackerTeam.equals(victimTeam) && !attackerTeam.hasFriendlyFire()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        event.setCancelled(true);

        UUID   uuid     = player.getUniqueId();
        long   now      = System.currentTimeMillis();
        long   lastUsed = cooldowns.getOrDefault(uuid, 0L);
        long   cd       = cooldownForLevel(this.getLevel());

        if (now - lastUsed > cd) {
            cooldowns.put(uuid, now);
            player.getWorld().strikeLightningEffect(rightClicked.getLocation());
            double damage = getWeaponDamage(handItem, player, rightClicked) * multiplierForLevel(this.getLevel());
            rightClicked.setNoDamageTicks(0);

            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(uuid);
            try {
                rightClicked.damage(damage, player);
            } finally {
                com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(uuid);
            }
        } else {
            long lastMsg = lastMessageSent.getOrDefault(uuid, 0L);
            if (now - lastMsg > 500) {
                lastMessageSent.put(uuid, now);
                long remaining = (cd - (now - lastUsed)) / 1000;
                String enchantName = EnchantmentBook.applyEnchantmentGradient(getEnchantId(), CorovaEnchantments.DISPLAY_NAME.getOrDefault(getEnchantId(), "Lightning"));
                player.sendMessage(enchantName + ChatColor.RED + " is on cooldown for " + remaining + "s.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Entity damage event (mob attackers + bow/crossbow arrows)
    // -------------------------------------------------------------------------
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        LivingEntity damager;
        boolean isProjectile = false;

        if (event.getDamager() instanceof Projectile projectile) {
            if (!(projectile.getShooter() instanceof LivingEntity)) return;
            damager = (LivingEntity) projectile.getShooter();
            isProjectile = true;
            if (!(projectile instanceof Arrow)) return;
        } else if (event.getDamager() instanceof LivingEntity le) {
            damager = le;
            if (damager instanceof Player) return; // players handled via right-click
        } else {
            return;
        }

        if (target instanceof Player victim) {
            if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) return;
            CorovaTeams teamsInstance = CorovaTeams.getInstance();
            if (teamsInstance != null) {
                CorovaTeam attackerTeam = teamsInstance.getTeamManager().getTeamByPlayer(damager.getUniqueId());
                CorovaTeam victimTeam   = teamsInstance.getTeamManager().getTeamByPlayer(victim.getUniqueId());
                if (attackerTeam != null && attackerTeam.equals(victimTeam) && !attackerTeam.hasFriendlyFire()) return;
            }
        }

        ItemStack handItem = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        if (handItem == null || handItem.getType() == Material.AIR) return;
        if (!CorovaEnchantments.hasEnchant(handItem, getEnchantId())) return;

        // Only process for this instance's level
        if (CorovaEnchantments.getEnchantLevel(handItem, CorovaEnchantments.LIGHTNING_ID) != this.getLevel()) return;

        Material type = handItem.getType();
        if (isProjectile) {
            if (type != Material.BOW && type != Material.CROSSBOW) return;
        } else {
            if (type == Material.BOW || type == Material.CROSSBOW) return;
        }

        long now      = System.currentTimeMillis();
        long lastUsed = cooldowns.getOrDefault(damager.getUniqueId(), 0L);
        long cd       = cooldownForLevel(this.getLevel());

        if (now - lastUsed > cd) {
            cooldowns.put(damager.getUniqueId(), now);
            target.getWorld().strikeLightningEffect(target.getLocation());

            // Bypass combat cooldown for the enchantment portion
            if (event.isCancelled()) {
                event.setCancelled(false);
                target.setNoDamageTicks(0);
            }

            event.setDamage(event.getDamage() * multiplierForLevel(this.getLevel()));
        }
    }

    // -------------------------------------------------------------------------
    // Weapon damage helpers
    // -------------------------------------------------------------------------
    private double getWeaponDamage(ItemStack item, LivingEntity attacker, LivingEntity victim) {
        return getWeaponDamageStatic(item, attacker, victim);
    }

    private static double getWeaponDamageStatic(ItemStack item, LivingEntity attacker, LivingEntity victim) {
        return com.example.corovaItems.ItemMutations.MutationUtils.getWeaponDamage(item, attacker, victim);
    }
}