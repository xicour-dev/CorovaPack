package com.example.corovaItems.LOLPVP;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class IceBlade extends CorovaItems implements Listener {

    private final Set<UUID> freezingPlayers = new HashSet<>();
    private final Set<UUID> frozenEntities = new HashSet<>();
    private final Map<UUID, Integer> chargeMap = new HashMap<>();
    private final Map<UUID, Long> cooldownMap = new HashMap<>();
    private final Map<UUID, Location> frozenLocations = new HashMap<>();
    private static final long COOLDOWN_MS = 8000; // 8 seconds

    /** Parameterless constructor compatible with /c i */
    public IceBlade() {
        super(
                "§bIceblade",
                Material.DIAMOND_SWORD,
                Arrays.asList("§7Freeze I", "§8Hold shift to freeze enemies!"),
                Map.of(
                        Enchantment.SHARPNESS, 10,
                        Enchantment.SMITE, 10,
                        Enchantment.BANE_OF_ARTHROPODS, 5,
                        Enchantment.LOOTING, 5
                ),
                "iceblade"
        );

        // Register item in ItemManager (makes /c i work)
        ItemManager.getInstance().registerItem(this);

        // Register events so abilities work
        Bukkit.getPluginManager().registerEvents(this, org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(IceBlade.class));
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (frozenEntities.contains(player.getUniqueId())) {
            Location loc = frozenLocations.get(player.getUniqueId());
            if (loc != null) {
                event.setTo(new Location(
                        loc.getWorld(),
                        loc.getX(), loc.getY(), loc.getZ(),
                        event.getTo().getYaw(),
                        event.getTo().getPitch()
                ));
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity entity) {
            UUID id = entity instanceof Player ? ((Player) entity).getUniqueId() : entity.getUniqueId();
            if (frozenEntities.contains(id) &&
                    (event.getCause() == EntityDamageEvent.DamageCause.FALL ||
                            event.getCause() == EntityDamageEvent.DamageCause.CONTACT)) {
                event.setDamage(0);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        player.setWalkSpeed(0.2f);
        freezingPlayers.remove(player.getUniqueId());
        frozenEntities.remove(player.getUniqueId());
        chargeMap.remove(player.getUniqueId());
        frozenLocations.remove(player.getUniqueId());
        cooldownMap.remove(player.getUniqueId());
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ItemManager.getInstance().isCorovaItem(hand, this)) return;

        UUID uuid = player.getUniqueId();
        if (freezingPlayers.contains(uuid)) return;

        long lastUsed = cooldownMap.getOrDefault(uuid, 0L);
        long now = System.currentTimeMillis();
        if (now - lastUsed < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
            player.sendActionBar(Component.text("§cFreeze is on cooldown: " + remaining + "s"));
            return;
        }

        freezingPlayers.add(uuid);
        chargeMap.put(uuid, 0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isSneaking() || !freezingPlayers.contains(uuid)) {
                    int charge = chargeMap.getOrDefault(uuid, 0);
                    if (charge > 0) activate(player, charge);
                    freezingPlayers.remove(uuid);
                    chargeMap.remove(uuid);
                    cancel();
                    return;
                }

                int charge = chargeMap.getOrDefault(uuid, 0);
                if (charge >= 10) {
                    activate(player, charge);
                    freezingPlayers.remove(uuid);
                    chargeMap.remove(uuid);
                    cancel();
                    return;
                }

                chargeMap.put(uuid, charge + 1);
                player.sendActionBar(Component.text("§b§lFreeze Radius: " + charge));
            }
        }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(IceBlade.class), 0L, 20L);
    }

    private void activate(Player player, int radius) {
        UUID activator = player.getUniqueId();
        freezingPlayers.remove(activator);
        chargeMap.remove(activator);
        cooldownMap.put(activator, System.currentTimeMillis());

        List<String> frozenNames = new ArrayList<>();

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target instanceof Player && ((Player) target).getUniqueId().equals(activator)) continue;

            if (target instanceof Player && CorovaGuard.getInstance().isPlayerInSafeZone((Player) target)) {
                continue;
            }

            UUID targetId = target instanceof Player ? ((Player) target).getUniqueId() : target.getUniqueId();
            if (frozenEntities.contains(targetId)) continue;

            frozenEntities.add(targetId);
            if (target instanceof Player playerTarget) {
                frozenLocations.put(playerTarget.getUniqueId(), playerTarget.getLocation());
                playerTarget.setWalkSpeed(0f);
                playerTarget.sendActionBar(Component.text("§b§lYou have been frozen by " + player.getName() + "!"));
            }

            frozenNames.add(target.getName());

            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.5f);
            spawnFreezeExplosion(target);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 60) {
                        frozenEntities.remove(targetId);
                        if (target instanceof Player playerTarget) {
                            frozenLocations.remove(playerTarget.getUniqueId());
                            playerTarget.setWalkSpeed(0.2f);
                            playerTarget.sendActionBar(Component.text("§7You are no longer frozen!"));
                        }
                        cancel();
                        return;
                    }

                    target.setVelocity(new Vector(0, 0, 0));
                    if (target instanceof Player playerTarget && frozenLocations.containsKey(playerTarget.getUniqueId())) {
                        Location loc = frozenLocations.get(playerTarget.getUniqueId());
                        playerTarget.teleport(new Location(
                                loc.getWorld(),
                                loc.getX(), loc.getY(), loc.getZ(),
                                playerTarget.getLocation().getYaw(),
                                playerTarget.getLocation().getPitch()
                        ));
                    }

                    ticks++;
                }
            }.runTaskTimer(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(IceBlade.class), 0L, 1L);
        }

        // Send frozen list as a single action bar message
        if (!frozenNames.isEmpty()) {
            String names = String.join("§7, §b", frozenNames);
            player.sendActionBar(Component.text("§7You have frozen: §b" + names));
        } else {
            player.sendActionBar(Component.text("§cNo one in range..."));
        }
    }
    // Event listener for preventing damage in safezones
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack handItem = damager.getInventory().getItemInMainHand();
        if (!ItemManager.getInstance().isCorovaItem(handItem, this)) return;

        if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
            damager.sendMessage(ChatColor.RED + "You cannot damage players in a safezone.");
            event.setCancelled(true);
        }
    }
    private void spawnFreezeExplosion(Entity entity) {
        Location loc = entity.getLocation().add(0, 1, 0);
        for (int i = 0; i < 40; i++) {
            double angle = Math.random() * 2 * Math.PI;
            double spread = Math.random();
            double offsetX = Math.cos(angle) * spread;
            double offsetY = Math.random() * 0.5 + 0.2;
            double offsetZ = Math.sin(angle) * spread;

            entity.getWorld().spawnParticle(Particle.CLOUD, loc, 1, offsetX, offsetY, offsetZ, 0.3);
            entity.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 1, offsetX, offsetY, offsetZ, 0.3);
        }

        entity.getWorld().spawnParticle(
                Particle.BLOCK_CRUMBLE,
                loc,
                20,
                0.5, 0.5, 0.5,
                0,
                Material.ICE.createBlockData()
        );
    }
}
