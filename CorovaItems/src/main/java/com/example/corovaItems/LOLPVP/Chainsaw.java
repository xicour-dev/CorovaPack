package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Chainsaw extends CorovaItems implements Listener {

    public Chainsaw() {
        super(
                ChatColor.AQUA + "Leatherface's Chainsaw",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "leatherfaceschainsaw"
        );
        ItemManager.getInstance().registerItem(this);
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity hit) {
            if (isThisItem(player.getInventory().getItemInMainHand())) {
                hit.getWorld().spawnParticle(
                        Particle.BLOCK,
                        hit.getLocation().add(0, 1, 0),
                        30, 0.3, 0.3, 0.3,
                        Material.REDSTONE_BLOCK.createBlockData()
                );
            }
        }
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "The chainsaw used by Leatherface during the");
        lore.add(ChatColor.DARK_GRAY + "Texas Chain Saw Massacre. Now you can use it");
        lore.add(ChatColor.DARK_GRAY + "to slay your enemies in PVP and spew");
        lore.add(ChatColor.DARK_GRAY + "their blood everywhere!");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.LOOTING, 10);
        enchantments.put(Enchantment.SMITE, 10);
        enchantments.put(Enchantment.SHARPNESS, 10);
        return enchantments;
    }
}
