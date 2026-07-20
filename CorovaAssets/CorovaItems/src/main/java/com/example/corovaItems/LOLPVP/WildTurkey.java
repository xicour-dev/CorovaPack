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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WildTurkey extends CorovaItems implements Listener {

    public WildTurkey() {
        super(
                ChatColor.AQUA + "Wild Turkey",
                Material.WOODEN_SWORD,
                lore(),
                enchantments(),
                "wildturkey"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Launcher I");
        lore.add(ChatColor.DARK_GRAY + "A rare Thanksgiving sword!");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.SHARPNESS, 10);
        enchantments.put(Enchantment.SMITE, 10);
        enchantments.put(Enchantment.BANE_OF_ARTHROPODS, 5);
        enchantments.put(Enchantment.LOOTING, 5);
        return enchantments;
    }

    @EventHandler
    public void onInteractItem(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (ItemManager.getInstance().isCorovaItem(handItem, this)) {
            entity.setVelocity(new Vector(0, 1.3, 0));
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1F, 1F);
        }
    }
}
