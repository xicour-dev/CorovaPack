package com.example.corovaItems.CustomCrafting;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Recipe registrar for all custom crafting recipes.
 */
public class CustomCrafting {
    private final JavaPlugin plugin;
    private final List<NamespacedKey> registeredRecipes = new ArrayList<>();

    public CustomCrafting(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize and register all custom crafting recipes.
     */
    public void initialize() {
        plugin.getLogger().log(Level.INFO, "Registering custom crafting recipes...");

        // Register individual recipe classes
        registerRecipe(AutoTurretMinecart.getRecipe(plugin));

        plugin.getLogger().log(Level.INFO, "Registered " + registeredRecipes.size() + " custom recipe(s).");
    }

    /**
     * Register a recipe and track its key for cleanup.
     */
    private void registerRecipe(Recipe recipe) {
        if (recipe == null) {
            plugin.getLogger().log(Level.WARNING, "Attempted to register null recipe!");
            return;
        }

        Bukkit.addRecipe(recipe);

        // Track the key for cleanup
        if (recipe instanceof org.bukkit.inventory.ShapedRecipe) {
            registeredRecipes.add(((org.bukkit.inventory.ShapedRecipe) recipe).getKey());
        } else if (recipe instanceof org.bukkit.inventory.ShapelessRecipe) {
            registeredRecipes.add(((org.bukkit.inventory.ShapelessRecipe) recipe).getKey());
        }
    }

    /**
     * Unregister all custom recipes when the plugin is disabled.
     */
    public void unregisterAll() {
        for (NamespacedKey key : registeredRecipes) {
            Bukkit.removeRecipe(key);
        }
        registeredRecipes.clear();
        plugin.getLogger().log(Level.INFO, "Unregistered all custom recipes.");
    }

    /**
     * Get the number of registered custom recipes.
     */
    public int getRecipeCount() {
        return registeredRecipes.size();
    }
}