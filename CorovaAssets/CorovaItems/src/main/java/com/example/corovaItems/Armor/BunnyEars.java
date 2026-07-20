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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class BunnyEars extends CorovaItems implements Listener {

    private final Map<UUID, Boolean> wearingEars = new HashMap<>();

    public BunnyEars() {
        super(
                ChatColor.AQUA + "Bunny Ears",
                Material.PLAYER_HEAD,
                lore(),
                enchantments(),
                "bunnyears"
        );
        ItemManager.getInstance().registerItem(this);
        startEffectTask();
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Jump Boost III",
                ChatColor.DARK_GRAY + "Hop like a bunny!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>();
    }

    @Override
    public ItemStack getItemStack() {
        ItemStack item = super.getItemStack();
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer("LOLPVPDonations"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void startEffectTask() {
        Bukkit.getScheduler().runTaskTimer(Bukkit.getPluginManager().getPlugin("CorovaCore"), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                ItemStack helmet = player.getInventory().getHelmet();
                boolean isWearing = ItemManager.getInstance().isCorovaItem(helmet, this);

                if (isWearing) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 2, true, false));
                    wearingEars.put(playerId, true);
                } else if (wearingEars.getOrDefault(playerId, false)) {
                    player.removePotionEffect(PotionEffectType.JUMP_BOOST);
                    wearingEars.remove(playerId);
                }
            }
        }, 0L, 20L);
    }
}
