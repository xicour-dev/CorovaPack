package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpidermanBow extends CorovaItems implements Listener {

    private final List<Arrow> arrows = new ArrayList<>();

    public SpidermanBow() {
        super(
                ChatColor.AQUA + "Spider-Man Bow",
                Material.BOW,
                lore(),
                enchantments(),
                "spidermanbow"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Web Shooter I");
        lore.add(ChatColor.DARK_GRAY + "Hit an enemy with an arrow to trap them in a web!");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.POWER, 10);
        return enchantments;
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getProjectile() instanceof Arrow)) return;

        ItemStack bow = event.getBow();
        if (ItemManager.getInstance().isCorovaItem(bow, this)) {
            arrows.add((Arrow) event.getProjectile());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (arrows.remove(arrow)) {
            // NOTE: Cooldowns and PVP area checks are removed.
            // A modern cooldown system should be implemented if desired.

            final Location loc1 = victim.getLocation();
            final Location loc2 = victim.getLocation().add(0, 1, 0);

            // Temporarily store original blocks
            final Material originalType1 = loc1.getBlock().getType();
            final Material originalType2 = loc2.getBlock().getType();

            loc1.getBlock().setType(Material.COBWEB);
            loc2.getBlock().setType(Material.COBWEB);

            // Schedule a task to remove the webs after 5 seconds
            JavaPlugin plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("CorovaItems");
            if (plugin == null) {
                // Fallback if plugin is not found
                Bukkit.getLogger().severe("Could not find CorovaItems plugin to schedule a task!");
                return;
            }

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (loc1.getBlock().getType() == Material.COBWEB) {
                        loc1.getBlock().setType(originalType1);
                    }
                    if (loc2.getBlock().getType() == Material.COBWEB) {
                        loc2.getBlock().setType(originalType2);
                    }
                }
            }.runTaskLater(plugin, 100L); // 100 ticks = 5 seconds
        }
    }
}
