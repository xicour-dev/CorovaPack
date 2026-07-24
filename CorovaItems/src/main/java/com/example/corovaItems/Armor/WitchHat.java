package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WitchHat extends CorovaItems implements Listener {

    public WitchHat() {
        super(
                ChatColor.DARK_PURPLE + "Witch's Hat",
                Material.DIAMOND_HELMET,
                lore(),
                enchantments(),
                "witchhat"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.DARK_GRAY + "A special Halloween item that prevents all fall damage!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.AQUA_AFFINITY, 5);
        map.put(Enchantment.RESPIRATION, 5);
        map.put(Enchantment.PROTECTION, 5);
        map.put(Enchantment.BLAST_PROTECTION, 5);
        map.put(Enchantment.FIRE_PROTECTION, 5);
        map.put(Enchantment.PROJECTILE_PROTECTION, 5);
        return map;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                ItemStack helmet = player.getInventory().getHelmet();
                if (ItemManager.getInstance().isCorovaItem(helmet, this)) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
