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

public class Riptide extends CorovaItems {

    public Riptide() {
        super(
                ChatColor.AQUA + "Riptide",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "riptide"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return new ArrayList<>();
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.SHARPNESS, 10);
        enchantments.put(Enchantment.SMITE, 10);
        enchantments.put(Enchantment.BANE_OF_ARTHROPODS, 5);
        enchantments.put(Enchantment.LOOTING, 5);
        enchantments.put(Enchantment.KNOCKBACK, 10);
        enchantments.put(Enchantment.FIRE_ASPECT, 7);
        return enchantments;
    }
}
