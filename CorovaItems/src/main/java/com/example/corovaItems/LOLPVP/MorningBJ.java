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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MorningBJ extends CorovaItems implements Listener {

    public MorningBJ() {
        super(
                ChatColor.AQUA + "Morning BJ",
                Material.WOODEN_SWORD,
                lore(),
                enchantments(),
                "morningbj"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "The second wood tier weapon!");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.SHARPNESS, 10);
        enchantments.put(Enchantment.FIRE_ASPECT, 10);
        return enchantments;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player)) return;

        if (ItemManager.getInstance().isCorovaItem(damager.getInventory().getItemInMainHand(), this)) {
            event.setDamage(event.getDamage() * 1.1);
        }
    }
}
