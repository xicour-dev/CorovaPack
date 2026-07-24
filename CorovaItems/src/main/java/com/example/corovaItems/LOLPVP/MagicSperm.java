package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MagicSperm extends CorovaItems implements Listener {

    private final Map<UUID, BossBar> invisibilityBars = new HashMap<>();
    private final JavaPlugin plugin;

    public MagicSperm() {
        super(
                ChatColor.AQUA + "Magic Sperm",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "magicsperm"
        );
        // It's better to get the plugin instance once.
        this.plugin = JavaPlugin.getProvidingPlugin(MagicSperm.class);
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            if (isThisItem(attacker.getInventory().getItemInMainHand())) {
                if (!invisibilityBars.containsKey(attacker.getUniqueId())) {
                    // Hide the attacker from the victim
                    victim.hidePlayer(plugin, attacker);

                    BossBar bossBar = Bukkit.createBossBar(
                            ChatColor.AQUA + "" + ChatColor.BOLD + "You are invisible.",
                            BarColor.WHITE,
                            BarStyle.SOLID
                    );
                    bossBar.addPlayer(attacker);
                    invisibilityBars.put(attacker.getUniqueId(), bossBar);

                    new BukkitRunnable() {
                        int ticks = 40; // 2 seconds

                        @Override
                        public void run() {
                            if (ticks <= 0 || !invisibilityBars.containsKey(attacker.getUniqueId())) {
                                victim.showPlayer(plugin, attacker);
                                BossBar bar = invisibilityBars.remove(attacker.getUniqueId());
                                if (bar != null) {
                                    bar.removeAll();
                                }
                                cancel();
                                return;
                            }

                            bossBar.setProgress((double) ticks / 40.0);
                            ticks--;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                }
            }
        }
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Invisibility I",
                ChatColor.DARK_GRAY + "Hit enemies to become invisible!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.SHARPNESS, 10);
        enchantments.put(Enchantment.SMITE, 10);
        enchantments.put(Enchantment.UNBREAKING, 10);
        return enchantments;
    }
}
