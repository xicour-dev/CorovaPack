package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NoobsBlade extends CorovaItems {

    public NoobsBlade() {
        super(
                ChatColor.AQUA + "Noob's Blade",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "noobsblade"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Made for noobs - by Thad");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.SHARPNESS, 1);
        enchantments.put(Enchantment.KNOCKBACK, 1);
        enchantments.put(Enchantment.LOOTING, 1);
        enchantments.put(Enchantment.FIRE_ASPECT, 1);
        return enchantments;
    }
}
