package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PoisonBow extends CorovaItems implements Listener {

    private final NamespacedKey poisonArrowKey;
    private final JavaPlugin plugin;

    public PoisonBow() {
        super(
                ChatColor.AQUA + "Poison Bow",
                Material.BOW,
                lore(),
                enchantments(),
                "poisonbow"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(PoisonBow.class);
        this.poisonArrowKey = new NamespacedKey(plugin, "poison_arrow");
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getProjectile() instanceof Arrow arrow) {
            ItemStack bow = event.getBow();
            if (isThisItem(bow)) {
                arrow.getPersistentDataContainer().set(poisonArrowKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Arrow arrow && event.getEntity() instanceof Player player) {
            if (arrow.getPersistentDataContainer().has(poisonArrowKey, PersistentDataType.BYTE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 10 * 20, 0));
            }
        }
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Poison I",
                ChatColor.DARK_GRAY + "Shoot enemies to poison them!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.POWER, 3);
        return enchantments;
    }
}
