package com.example.corovaItems.CustomCrafting;

import com.example.corovaItems.CorovaItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class WandCrafting {

    public static void registerRecipes(JavaPlugin plugin) {
        registerWandRecipe(plugin, "woodenwand", new RecipeChoice.MaterialChoice(Tag.PLANKS));
        registerWandRecipe(plugin, "stonewand", new RecipeChoice.MaterialChoice(Material.COBBLESTONE));
        registerWandRecipe(plugin, "goldwand", new RecipeChoice.MaterialChoice(Material.GOLD_INGOT));
        registerWandRecipe(plugin, "copperwand", new RecipeChoice.MaterialChoice(Material.COPPER_INGOT));
        registerWandRecipe(plugin, "ironwand", new RecipeChoice.MaterialChoice(Material.IRON_INGOT));
        registerWandRecipe(plugin, "diamondwand", new RecipeChoice.MaterialChoice(Material.DIAMOND));
    }

    private static void registerWandRecipe(JavaPlugin plugin, String key, RecipeChoice material) {
        CorovaItems item = CorovaItems.getItemByName(key);
        if (item == null) {
            plugin.getLogger().warning("WandCrafting: skipping recipe for '" + key + "' - item not found in registry.");
            return;
        }
        if (item.getMaterials()[0] == null) {
            plugin.getLogger().warning("WandCrafting: skipping recipe for '" + key + "' - material is null (unsupported on this server version).");
            return;
        }

        // XMX
        // SSS
        // XSX
        // No mirrored recipe needed — the pattern is already symmetrical.
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, key), item.getItemStack());
        recipe.shape("XMX", "SSS", "XSX");
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('M', material);
        Bukkit.addRecipe(recipe);
    }
}