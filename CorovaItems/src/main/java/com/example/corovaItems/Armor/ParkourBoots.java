package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParkourBoots extends CorovaItems implements Listener {

    public ParkourBoots() {
        super(
                ChatColor.AQUA + "Parkour Boots",
                Material.LEATHER_BOOTS,
                lore(),
                enchantments(),
                "parkourboots"
        );
        ItemManager.getInstance().registerItem(this);
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(Color.WHITE);
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> lore() {
        return Collections.singletonList(ChatColor.DARK_GRAY + "Free Runner boots.");
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.FEATHER_FALLING, 10);
        return map;
    }

    @EventHandler
    public void onPlayerDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            ItemStack boots = player.getInventory().getBoots();
            if (boots != null && ItemManager.getInstance().isCorovaItem(boots, this)) {
                event.setCancelled(true);
            }
        }
    }
}
