package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GoldTurkey extends CorovaItems implements Listener {

    public GoldTurkey() {
        super(
                ChatColor.AQUA + "Gold Turkey",
                Material.GOLDEN_SWORD,
                lore(),
                enchantments(),
                "goldturkey"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Launcher I",
                ChatColor.DARK_GRAY + "A super special Thanksgiving sword!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 10);
        map.put(Enchantment.UNBREAKING, 10);
        return map;
    }

    @EventHandler
    public void onInteractItem(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (event.getRightClicked() instanceof LivingEntity entity && ItemManager.getInstance().isCorovaItem(handItem, this)) {
            entity.setVelocity(new Vector(0, 1.3, 0));
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1F, 1F);
            event.setCancelled(true);
        }
    }
}
