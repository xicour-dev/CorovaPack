package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class AbominableSnowman extends CorovaItems implements Listener {

    private final Set<UUID> chargingPlayers = new HashSet<>();
    private final Set<UUID> blindedPlayers = new HashSet<>();
    private final Map<UUID, Integer> chargeMap = new HashMap<>();
    private final Map<UUID, Long> cooldownMap = new HashMap<>();
    private final Map<UUID, BukkitRunnable> activeBlindTasks = new HashMap<>();
    private static final long COOLDOWN_MS = 5000; // 5 seconds

    /** Parameterless constructor compatible with /c i */
    public AbominableSnowman() {
        super(
                ChatColor.AQUA + "Abominable Snowman",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "snowman"
        );

        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AbominableSnowman.class));
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemManager.getInstance().isCorovaItem(hand, this)) return;
        UUID uuid = player.getUniqueId();
        if (chargingPlayers.contains(uuid)) return;

        long lastUsed = cooldownMap.getOrDefault(uuid, 0L);
        long now = System.currentTimeMillis();
        if (now - lastUsed < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
            player.sendActionBar(Component.text(ChatColor.RED + "Abominable Snowman is on cooldown: " + remaining + "s"));
            return;
        }

        chargingPlayers.add(uuid);
        chargeMap.put(uuid, 0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isSneaking() || !chargingPlayers.contains(uuid)) {
                    int charge = chargeMap.getOrDefault(uuid, 0);
                    if (charge > 0) activate(player, charge);
                    chargingPlayers.remove(uuid);
                    chargeMap.remove(uuid);
                    cancel();
                    return;
                }

                int charge = chargeMap.getOrDefault(uuid, 0);
                if (charge >= 10) { // max charge
                    activate(player, charge);
                    chargingPlayers.remove(uuid);
                    chargeMap.remove(uuid);
                    cancel();
                    return;
                }

                chargeMap.put(uuid, charge + 1);
                player.sendActionBar(Component.text(ChatColor.AQUA + "Blind Radius: " + charge));

                // Spawn charging particles around player
                Location loc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 5, 0.5, 0.5, 0.5, 0.1);
                player.getWorld().spawnParticle(Particle.CLOUD, loc, 5, 0.5, 0.5, 0.5, 0.05);
            }
        }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AbominableSnowman.class), 0L, 20L);
    }

    private void activate(Player player, int radius) {
        UUID uuid = player.getUniqueId();
        chargingPlayers.remove(uuid);
        chargeMap.remove(uuid);
        cooldownMap.put(uuid, System.currentTimeMillis());

        List<Player> affectedPlayers = new ArrayList<>();
        List<String> affectedNames = new ArrayList<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player target)) continue;
            if (target.getUniqueId().equals(uuid)) continue;
            if (blindedPlayers.contains(target.getUniqueId())) continue;

            blindedPlayers.add(target.getUniqueId());
            affectedPlayers.add(target);
            affectedNames.add(target.getName());

            // Apply blindness effect
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, radius * 20, 0));

            // Action bar notification
            target.sendActionBar(Component.text(ChatColor.AQUA + "You have been blinded by " + player.getName() + "!"));

            // Spawn freeze particles around the target
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks > 20) { // 1 second of particles
                        cancel();
                        return;
                    }
                    Location loc = target.getLocation().add(0, 1, 0);
                    target.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 3, 0.3, 0.5, 0.3, 0.1);
                    target.getWorld().spawnParticle(Particle.CLOUD, loc, 3, 0.3, 0.5, 0.3, 0.05);
                    ticks++;
                }
            }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AbominableSnowman.class), 0L, 1L);
        }

        // e) Add BallerItemTask-style STEP_SOUND particle effect for each affected player
        for (Player p : affectedPlayers) {
            new BukkitRunnable() {
                int count = 0;
                @Override
                public void run() {
                    if (count >= 20) { // 20 particle plays
                        cancel();
                        return;
                    }
                    Location loc = p.getLocation().add(0, 1, 0);
                    p.getWorld().playEffect(loc, Effect.STEP_SOUND, Material.SNOW_BLOCK);
                    p.getWorld().playEffect(p.getLocation(), Effect.STEP_SOUND, Material.SNOW_BLOCK);
                    count++;
                }
            }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AbominableSnowman.class), 0L, 1L);
        }

        // Schedule task to remove blindness
        for (Player target : affectedPlayers) {
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    blindedPlayers.remove(target.getUniqueId());
                    target.sendActionBar(Component.text(ChatColor.GRAY + "You are no longer blinded!"));
                    activeBlindTasks.remove(target.getUniqueId());
                }
            };
            task.runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(AbominableSnowman.class), radius * 20L);
            activeBlindTasks.put(target.getUniqueId(), task);
        }

        if (!affectedNames.isEmpty()) {
            player.sendActionBar(Component.text(ChatColor.GRAY + "You have blinded: " + String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, affectedNames)));
        } else {
            player.sendActionBar(Component.text(ChatColor.RED + "You did not affect anyone."));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chargingPlayers.remove(uuid);
        chargeMap.remove(uuid);
        cooldownMap.remove(uuid);
        if (activeBlindTasks.containsKey(uuid)) {
            activeBlindTasks.get(uuid).cancel();
            activeBlindTasks.remove(uuid);
        }
        blindedPlayers.remove(uuid);
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        enchants.put(Enchantment.SHARPNESS, 10);
        enchants.put(Enchantment.SMITE, 10);
        enchants.put(Enchantment.LOOTING, 5);
        return enchants;
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Snowstorm I");
        lore.add(ChatColor.DARK_GRAY + "This sword was found buried deep in a cavern in Antarctica. Shift to charge");
        return lore;
    }
}
