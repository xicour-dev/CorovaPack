package com.example.corovaItems.CustomCrafting;

import com.example.corovaItems.CorovaItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class ScytheCrafting {

    public static void registerRecipes(JavaPlugin plugin) {
        registerScytheRecipe(plugin, "woodenscythe", new RecipeChoice.MaterialChoice(Tag.PLANKS));
        registerScytheRecipe(plugin, "stonescythe", new RecipeChoice.MaterialChoice(Material.COBBLESTONE));
        registerScytheRecipe(plugin, "copperscythe", new RecipeChoice.MaterialChoice(Material.COPPER_INGOT));
        registerScytheRecipe(plugin, "ironscythe", new RecipeChoice.MaterialChoice(Material.IRON_INGOT));
        registerScytheRecipe(plugin, "diamondscythe", new RecipeChoice.MaterialChoice(Material.DIAMOND));
    }

    private static void registerScytheRecipe(JavaPlugin plugin, String key, RecipeChoice material) {
        // Standard Recipe
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, key), CorovaItems.getItemByName(key).getItemStack());
        recipe.shape(" MM", "M S", "  S");
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('M', material);
        Bukkit.addRecipe(recipe);

        // Mirrored Recipe
        ShapedRecipe mirroredRecipe = new ShapedRecipe(new NamespacedKey(plugin, key + "_mirrored"), CorovaItems.getItemByName(key).getItemStack());
        mirroredRecipe.shape("MM ", "S M", "S  ");
        mirroredRecipe.setIngredient('S', Material.STICK);
        mirroredRecipe.setIngredient('M', material);
        Bukkit.addRecipe(mirroredRecipe);
    }
}
