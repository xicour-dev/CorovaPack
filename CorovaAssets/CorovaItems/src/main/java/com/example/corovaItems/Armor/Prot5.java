package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;

/**
 * Prot5 full diamond armor set with Protection 5.
 */
public class Prot5 extends CorovaItems implements Listener {

    private static final List<String> LORE = List.of(ChatColor.DARK_GRAY + "Authentic Protection 5 Armor");
    private static final Map<Enchantment, Integer> ENCHANTMENTS = Map.of(Enchantment.PROTECTION, 5);

    private static final Material[] ARMOR_PIECES = {
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS
    };

    public Prot5() {
        super(ChatColor.AQUA + "Prot 5", ARMOR_PIECES, LORE, ENCHANTMENTS, "prot5");
        ItemManager.getInstance().registerItem(this);
    }
}