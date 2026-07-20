package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Prot6 full diamond armor set with Protection 6.
 * Uses normal Minecraft mechanics for damage reduction.
 */
public class Prot6 extends CorovaItems {

    private static final List<String> ARMOR_LORE = Arrays.asList(ChatColor.DARK_GRAY + "Authentic Protection 6 Armor");
    private static final Map<Enchantment, Integer> ENCHANTMENTS = new HashMap<>() {{
        put(Enchantment.PROTECTION, 6); // Vanilla Protection 6
    }};
    private static final Material[] ARMOR_MATERIALS = {
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS
    };

    public Prot6() {
        super(ChatColor.AQUA + "Prot 6", ARMOR_MATERIALS, ARMOR_LORE, ENCHANTMENTS, "prot6");
    }

    @Override
    public ItemStack toItemStack() {
        return getFullSet()[0]; // ItemManager will handle the full set automatically
    }
}
