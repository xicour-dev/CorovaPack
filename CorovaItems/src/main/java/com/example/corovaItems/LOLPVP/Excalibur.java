package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Excalibur extends CorovaItems {

    public Excalibur() {
        super(
                ChatColor.AQUA + "Excalibur",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "excalibur"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return Collections.singletonList(ChatColor.DARK_GRAY + "The essential PVP sword");
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 5);
        map.put(Enchantment.SMITE, 5);
        map.put(Enchantment.KNOCKBACK, 2);
        map.put(Enchantment.LOOTING, 3);
        map.put(Enchantment.FIRE_ASPECT, 2);
        return map;
    }
}
