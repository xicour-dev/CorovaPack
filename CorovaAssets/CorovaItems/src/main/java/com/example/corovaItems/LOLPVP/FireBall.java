package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FireBall extends CorovaItems implements Listener {

    public FireBall() {
        super(
                ChatColor.AQUA + "Fire Ball",
                Material.FIRE_CHARGE,
                lore(),
                enchantments(),
                "fireball"
        );
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(FireBall.class));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isThisItem(item)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);

                // This is a conceptual adaptation. The original 'IceBlade.froze' is not accessible.
                // A better implementation would involve checking for a specific potion effect or metadata.
                // For now, we'll just apply the fire effect.
                player.setFireTicks(5 * 20); // 5 seconds of fire
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 5 * 20, 0));

                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }
            }
        }
    }

    private static List<String> lore() {
        return Collections.singletonList(ChatColor.DARK_GRAY + "Right Click to heat yourself up!");
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.FIRE_PROTECTION, 1); // Example enchantment
        return enchantments;
    }
}
