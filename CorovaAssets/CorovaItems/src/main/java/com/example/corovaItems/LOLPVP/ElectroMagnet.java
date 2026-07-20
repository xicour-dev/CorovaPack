package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ElectroMagnet extends CorovaItems implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BossBar> chargingBars = new HashMap<>();
    private static final long COOLDOWN_MS = 5000; // 5 seconds
    private final JavaPlugin plugin;

    public ElectroMagnet() {
        super(
                ChatColor.AQUA + "Electromagnet",
                Material.FLINT,
                lore(),
                enchantments(),
                "electromagnet"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(ElectroMagnet.class);
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // Prevent double firing

        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (isThisItem(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);
                UUID playerId = player.getUniqueId();

                if (chargingBars.containsKey(playerId)) {
                    return;
                }

                long lastUsed = cooldowns.getOrDefault(playerId, 0L);
                long now = System.currentTimeMillis();

                if (now - lastUsed < COOLDOWN_MS) {
                    long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
                    player.sendActionBar(Component.text(ChatColor.RED + "Electromagnet is on cooldown for " + remaining + "s"));
                    return;
                }

                cooldowns.put(playerId, now);
                BossBar bossBar = Bukkit.createBossBar(ChatColor.RED + "Charging Magnet...", BarColor.RED, BarStyle.SOLID);
                bossBar.addPlayer(player);
                chargingBars.put(playerId, bossBar);

                new BukkitRunnable() {
                    int ticks = 20; // 1 second charge

                    @Override
                    public void run() {
                        if (ticks <= 0 || !player.isOnline()) {
                            bossBar.removeAll();
                            chargingBars.remove(playerId);
                            if(player.isOnline()){
                                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                                for (Entity e : player.getNearbyEntities(15, 15, 15)) {
                                    if (e instanceof LivingEntity && !e.equals(player)) {
                                        pullEntityToLocation(e, player.getLocation());
                                    }
                                }
                            }
                            cancel();
                            return;
                        }

                        bossBar.setProgress((double) ticks / 20.0);
                        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 30, 1, 1, 1);
                        ticks--;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (chargingBars.containsKey(playerId)) {
            chargingBars.remove(playerId).removeAll();
        }
    }

    private void pullEntityToLocation(Entity e, Location loc) {
        Vector direction = loc.toVector().subtract(e.getLocation().toVector()).normalize();
        e.setVelocity(direction.multiply(1.5));
    }

    private static List<String> lore() {
        return Collections.singletonList(ChatColor.DARK_GRAY + "Star Lord's Electromagnet.");
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>();
    }
}
