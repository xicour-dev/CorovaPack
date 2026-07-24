package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LaunchBook extends EnchantmentBook implements Listener {

    // -------------------------------------------------------------------------
    // Level scaling
    //   I   → velocityY = 0.65  (same as BunnyBeater "Launcher 0.5")
    //   II  → velocityY = 1.30  (original Launcher I)
    //   III → velocityY = 2.60  (twice level II)
    // -------------------------------------------------------------------------
    private static final double[] VELOCITY_Y = { 0.0, 0.65, 1.30, 2.60 }; // index = level

    private static final long COOLDOWN_TIME_MS = 7000;
    private static final int  MAX_LAUNCHES     = 2;

    private static final Map<UUID, Integer> launchCounts = new HashMap<>();
    private static final Map<UUID, Long>    cooldowns    = new HashMap<>();

    public LaunchBook() {
        this(1);
    }

    public LaunchBook(int level) {
        super(
                "Book of Launcher",
                CorovaEnchantments.LAUNCH_ID,
                level,
                "book_launch",
                allowedMaterialsStatic()
        );
    }

    private static Set<Material> allowedMaterialsStatic() {
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
                Material.NETHERITE_AXE
        );
    }

    // -------------------------------------------------------------------------
    // Velocity helper
    // -------------------------------------------------------------------------
    private static double velocityForLevel(int level) {
        if (level >= 1 && level < VELOCITY_Y.length) return VELOCITY_Y[level];
        // Beyond level III: scale linearly from III onwards
        return VELOCITY_Y[3] * (1.0 + (level - 3) * 0.5);
    }

    // -------------------------------------------------------------------------
    // triggerEffect — called by BoomerangBook and the melee handler below.
    // -------------------------------------------------------------------------
    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (target instanceof Player victimPlayer) {
            if (CorovaGuard.getInstance().isPlayerInSafeZone(victimPlayer)) return;

            CorovaTeams teamsInstance = CorovaTeams.getInstance();
            if (teamsInstance != null) {
                CorovaTeam attackerTeam = teamsInstance.getTeamManager().getTeamByPlayer(damager.getUniqueId());
                CorovaTeam victimTeam   = teamsInstance.getTeamManager().getTeamByPlayer(victimPlayer.getUniqueId());
                if (attackerTeam != null && attackerTeam.equals(victimTeam) && !attackerTeam.hasFriendlyFire()) return;
            }
        }

        // Small bonus per hit (as before for mob triggering)
        target.setVelocity(target.getVelocity().add(new Vector(0, 0.5 * level, 0)));

        if (checkCooldownForMobStatic(damager)) {
            double velocityY = velocityForLevel(level);
            target.setVelocity(new Vector(0, velocityY, 0));
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);
            handleLaunchStatic(damager);
        }
    }

    // -------------------------------------------------------------------------
    // Melee damage event (mobs AND players)
    // -------------------------------------------------------------------------
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager)) return;

        if (event.getEntity() instanceof LivingEntity victim) {
            ItemStack weapon = (damager.getEquipment() != null)
                    ? damager.getEquipment().getItemInMainHand() : null;
            if (weapon == null || !CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.LAUNCH_ID)) return;

            // Only handle non-player damagers here; players use right-click interact
            if (damager instanceof Player) return;

            int level = CorovaEnchantments.getEnchantLevel(weapon, CorovaEnchantments.LAUNCH_ID);

            // Single listener pattern: only the registered instance handles all levels.
            triggerEffect(damager, victim, level);
        }
    }

    // -------------------------------------------------------------------------
    // Right-click entity event (player-initiated launch)
    // -------------------------------------------------------------------------
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // Prevent double trigger from offhand
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;
        Player player = event.getPlayer();

        if (entity instanceof Player victim) {
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

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.LAUNCH_ID)) return;

        // Single listener pattern: only the registered instance handles all levels.
        if (!checkCooldown(player)) {
            event.setCancelled(true);
            return;
        }

        int level = CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.LAUNCH_ID);
        double velocityY = velocityForLevel(level);

        entity.setVelocity(new Vector(0, velocityY, 0));
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);

        event.setCancelled(true);
        handleLaunch(player);
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        launchCounts.remove(uuid);
        cooldowns.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Cooldown helpers
    // -------------------------------------------------------------------------
    private boolean checkCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        Long expiration = cooldowns.get(uuid);
        long now = System.currentTimeMillis();
        if (expiration != null && expiration > now) {
            long remaining = (expiration - now) / 1000 + 1;
            String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.LAUNCH_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.LAUNCH_ID, "Launch"));
            player.sendMessage(enchantName + ChatColor.RED + " is on cooldown for " + remaining + " more seconds!");
            return false;
        }
        return true;
    }

    private static boolean checkCooldownForMobStatic(LivingEntity entity) {
        UUID uuid = entity.getUniqueId();
        Long expiration = cooldowns.get(uuid);
        long now = System.currentTimeMillis();
        return expiration == null || expiration <= now;
    }

    private void handleLaunch(LivingEntity entity) {
        handleLaunchStatic(entity);
    }

    private static void handleLaunchStatic(LivingEntity entity) {
        UUID uuid  = entity.getUniqueId();
        int  count = launchCounts.getOrDefault(uuid, 0) + 1;
        if (count >= MAX_LAUNCHES) {
            cooldowns.put(uuid, System.currentTimeMillis() + COOLDOWN_TIME_MS);
            launchCounts.put(uuid, 0);
        } else {
            launchCounts.put(uuid, count);
        }
    }
}