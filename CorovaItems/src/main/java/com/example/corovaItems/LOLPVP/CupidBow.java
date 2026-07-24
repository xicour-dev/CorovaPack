package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CupidBow extends CorovaItems implements Listener {

    private final NamespacedKey cupidArrowKey;
    private final JavaPlugin plugin;

    public CupidBow() {
        super(
                ChatColor.AQUA + "Cupid's Bow",
                Material.BOW,
                lore(),
                enchantments(),
                "cupidsbow"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(CupidBow.class);
        this.cupidArrowKey = new NamespacedKey(plugin, "cupid_arrow");
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Arrow arrow) {
            ItemStack bow = event.getBow();
            if (isThisItem(bow)) {
                arrow.getPersistentDataContainer().set(cupidArrowKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && event.getEntity() instanceof Player player) {
            if (arrow.getPersistentDataContainer().has(cupidArrowKey, PersistentDataType.BYTE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 300, 30));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 30, 1, 1, 1);
            }
        }
    }

    private static List<String> lore() {
        return Collections.singletonList("A rare Valentines Day bow!");
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.INFINITY, 1);
        return enchantments;
    }
}
