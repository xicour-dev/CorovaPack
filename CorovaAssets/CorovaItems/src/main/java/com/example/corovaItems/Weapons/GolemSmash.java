package com.example.corovaItems.Weapons;

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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GolemSmash extends CorovaItems implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 5000; // 5 seconds

    public GolemSmash() {
        super(
                ChatColor.LIGHT_PURPLE + "Golem Smash",
                Material.PURPLE_DYE,
                lore(),
                enchantments(),
                "golemsmash"
        );
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(GolemSmash.class));
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (isThisItem(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);

                UUID playerId = player.getUniqueId();
                long lastUsed = cooldowns.getOrDefault(playerId, 0L);
                long now = System.currentTimeMillis();

                if (now - lastUsed < COOLDOWN_MS) {
                    long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
                    player.sendActionBar(Component.text(ChatColor.RED + "Golem Smash is on cooldown for " + remaining + "s"));
                    return;
                }

                cooldowns.put(playerId, now);
                performGolemSlam(player);
            }
        }
    }

    /**
     * Replicates the Resentful Enchanted Golem's ground slam attack:
     *  - Wind-up: IRON_GOLEM_HURT sound + initial purple dust burst
     *  - 8-tick delay, then the slam: explosion + dense purple dust ring
     *  - Nearby living entities are launched upward with directional knockback
     */
    private void performGolemSlam(Player player) {
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Wind-up sound + initial purple particle burst (mirrors performGroundSlam wind-up)
        world.playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.5f, 0.5f);
        world.spawnParticle(
                Particle.DUST,
                loc.clone().add(0, 1, 0),
                40, 1.2, 1.2, 1.2,
                new Particle.DustOptions(Color.fromRGB(128, 0, 200), 2.5f)
        );

        // Delayed slam (8 ticks, same as the golem's BukkitRunnable delay)
        new BukkitRunnable() {
            @Override
            public void run() {
                Location slamLoc = player.getLocation();

                // Slam sounds
                world.playSound(slamLoc, Sound.ENTITY_IRON_GOLEM_ATTACK, 2.0f, 0.4f);

                // Explosion puff + dense purple dust ring (mirrors performGroundSlam exactly)
                world.spawnParticle(Particle.EXPLOSION, slamLoc, 6, 1.5, 0.1, 1.5, 0.0);
                world.spawnParticle(
                        Particle.DUST,
                        slamLoc,
                        60, 2.5, 0.2, 2.5,
                        new Particle.DustOptions(Color.fromRGB(80, 0, 160), 3.0f)
                );

                // Launch nearby entities with directional knockback (mirrors golem slam victims)
                for (Entity nearby : player.getNearbyEntities(5, 5, 5)) {
                    if (!(nearby instanceof LivingEntity victim)) continue;
                    if (victim.equals(player)) continue;
                    if (victim instanceof Player p && p.getGameMode() == GameMode.CREATIVE) continue;

                    Vector knockback = victim.getLocation().toVector()
                            .subtract(slamLoc.toVector())
                            .normalize();
                    knockback.setY(1.2);
                    knockback.multiply(1.8);
                    victim.setVelocity(knockback);
                }
            }
        }.runTaskLater(JavaPlugin.getProvidingPlugin(GolemSmash.class), 8L);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.DARK_GRAY + "Right click to smash!",
                ChatColor.DARK_GRAY + "Channeling the power of the Resentful Enchanted Golem."
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return Collections.singletonMap(Enchantment.UNBREAKING, 1);
    }
}