package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Prot7 full diamond armor set with Protection 7.
 * Uses normal Minecraft mechanics for damage reduction.
 */
public class Prot7 extends CorovaItems {

    private static final List<String> ARMOR_LORE = Arrays.asList(ChatColor.DARK_GRAY + "Authentic Protection 7 Armor");
    private static final Map<Enchantment, Integer> ENCHANTMENTS = new HashMap<>() {{
        put(Enchantment.PROTECTION, 7); // Vanilla Protection 7
    }};
    private static final Material[] ARMOR_MATERIALS = {
            Material.DIAMOND_HELMET,
            Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS,
            Material.DIAMOND_BOOTS
    };

    public Prot7() {
        super(ChatColor.AQUA + "Prot 7", ARMOR_MATERIALS, ARMOR_LORE, ENCHANTMENTS, "prot7");
    }

    @Override
    public ItemStack toItemStack() {
        return getFullSet()[0]; // ItemManager will handle the full set automatically
    }
}