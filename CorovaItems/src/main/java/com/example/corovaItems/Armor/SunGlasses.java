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

public class SunGlasses extends CorovaItems implements Listener {

    private final Map<UUID, Boolean> wearingGlasses = new HashMap<>();

    public SunGlasses() {
        super(
                ChatColor.AQUA + "Sun Glasses",
                Material.DIAMOND_HELMET,
                lore(),
                enchantments(),
                "sunglasses"
        );
        ItemManager.getInstance().registerItem(this);
        startTimeTask();
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Tint I",
                ChatColor.DARK_GRAY + "LOLPVP limited edition sunglasses! (Original Edition)"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.PROTECTION, 5);
        return map;
    }

    private void startTimeTask() {
        Bukkit.getScheduler().runTaskTimer(Bukkit.getPluginManager().getPlugin("CorovaCore"), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                ItemStack helmet = player.getInventory().getHelmet();
                boolean isWearing = ItemManager.getInstance().isCorovaItem(helmet, this);

                if (isWearing) {
                    if (player.getWorld().getTime() < 13000) {
                        player.setPlayerTime(13000, true);
                    }
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100000, 5));
                    wearingGlasses.put(playerId, true);
                } else if (wearingGlasses.getOrDefault(playerId, false)) {
                    player.resetPlayerTime();
                    player.removePotionEffect(PotionEffectType.NIGHT_VISION);
                    wearingGlasses.remove(playerId);
                }
            }
        }, 0L, 20L);
    }
}
