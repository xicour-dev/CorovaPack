package com.example.corovaItems.CustomCrafting;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Auto Turret Minecart crafting recipe.
 *
 * Recipe Layout:
 * D D D
 * D F D
 * D M D
 *
 * Where:
 * D = Diamond (7 total)
 * F = Fire Charge
 * M = Minecart
 */
public class AutoTurretMinecart {

    /**
     * Get the crafting recipe for Auto Turret Minecart.
     *
     * @param plugin The JavaPlugin instance for creating the NamespacedKey
     * @return The shaped recipe
     */
    public static Recipe getRecipe(JavaPlugin plugin) {
        NamespacedKey key = new NamespacedKey(plugin, "auto_turret_minecart");

        // Create the result item using the AutoTurretMinecart class from corovaCarts
        ItemStack result = com.example.corovaCarts.Turrets.AutoTurretMinecart.getItem();

        // Create the shaped recipe
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        // Set the pattern
        recipe.shape(
                "DDD",
                "DFD",
                "DMD"
        );

        // Map the ingredients
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('F', Material.FIRE_CHARGE);
        recipe.setIngredient('M', Material.MINECART);

        return recipe;
    }
}