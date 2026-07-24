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

public class DateRapist extends CorovaItems {

    public DateRapist() {
        super(
                ChatColor.AQUA + "Date Rapist",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "daterapist"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return Collections.singletonList(ChatColor.DARK_GRAY + "The original top tier sword!");
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 10);
        map.put(Enchantment.SMITE, 10);
        map.put(Enchantment.BANE_OF_ARTHROPODS, 5);
        map.put(Enchantment.LOOTING, 5);
        map.put(Enchantment.KNOCKBACK, 10);
        map.put(Enchantment.FIRE_ASPECT, 7);
        return map;
    }
}
