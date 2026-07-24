package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BabyMaker extends CorovaItems implements Listener {

    public BabyMaker() {
        super(
                ChatColor.AQUA + "Baby Maker",
                Material.GOLDEN_SWORD,
                lore(),
                enchantments(),
                "babymaker"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Resistance I",
                ChatColor.DARK_GRAY + "Hit to take less damage from enemies!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 10);
        map.put(Enchantment.SMITE, 10);
        map.put(Enchantment.BANE_OF_ARTHROPODS, 5);
        map.put(Enchantment.LOOTING, 5);
        map.put(Enchantment.UNBREAKING, 10);
        return map;
    }

    @EventHandler
    public void onInteract(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player)) return;

        if (ItemManager.getInstance().isCorovaItem(damager.getInventory().getItemInMainHand(), this)) {
            damager.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 5 * 20, 1));
        }
    }
}
