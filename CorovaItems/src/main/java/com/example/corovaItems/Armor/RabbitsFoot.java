package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class RabbitsFoot extends CorovaItems implements Listener {

    private final Map<UUID, Boolean> wearingBoots = new HashMap<>();

    public RabbitsFoot() {
        super(
                ChatColor.AQUA + "Rabbits Foot",
                Material.DIAMOND_BOOTS,
                lore(),
                enchantments(),
                "rabbitsfoot"
        );
        ItemManager.getInstance().registerItem(this);
        startEffectTask();
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Jump Boost III",
                ChatColor.DARK_GRAY + "A special Easter item!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.PROTECTION, 5);
        map.put(Enchantment.BLAST_PROTECTION, 5);
        map.put(Enchantment.FEATHER_FALLING, 5);
        map.put(Enchantment.FIRE_PROTECTION, 5);
        map.put(Enchantment.PROJECTILE_PROTECTION, 5);
        return map;
    }

    private void startEffectTask() {
        Bukkit.getScheduler().runTaskTimer(Bukkit.getPluginManager().getPlugin("CorovaCore"), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                ItemStack boots = player.getInventory().getBoots();
                boolean isWearing = ItemManager.getInstance().isCorovaItem(boots, this);

                if (isWearing) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 2, true, false));
                    wearingBoots.put(playerId, true);
                } else if (wearingBoots.getOrDefault(playerId, false)) {
                    player.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    wearingBoots.remove(playerId);
                }
            }
        }, 0L, 20L);
    }
}
