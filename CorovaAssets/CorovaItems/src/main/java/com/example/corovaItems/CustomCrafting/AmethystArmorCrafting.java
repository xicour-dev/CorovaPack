package com.example.corovaItems.CustomCrafting;

import com.example.corovaItems.CorovaItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers crafting recipes for the full amethyst armor set.
 *
 * All four pieces use the standard vanilla armor grid patterns with
 * AMETHYST_SHARD as the material. Grid key: A = shard, _ = empty.
 *
 * Helmet     (5 shards):  A_A / AAA
 * Chestplate (8 shards):  A_A / AAA / AAA
 * Leggings   (7 shards):  AAA / A_A / A_A
 * Boots      (4 shards):  A_A / A_A
 *
 * Total for a full set: 24 amethyst shards.
 */
public class AmethystArmorCrafting {

    public static void registerRecipes(JavaPlugin plugin) {
        registerHelmet(plugin);
        registerChestplate(plugin);
        registerLeggings(plugin);
        registerBoots(plugin);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helmet  —  vanilla pattern:
    //
    //  [A][ ][A]       row 1
    //  [A][A][A]       row 2
    //
    // ─────────────────────────────────────────────────────────────────────────
    private static void registerHelmet(JavaPlugin plugin) {
        CorovaItems item = CorovaItems.getItemByName("amethysthelmet");
        if (item == null) {
            plugin.getLogger().warning("AmethystArmorCrafting: skipping helmet — item not registered.");
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(
                new NamespacedKey(plugin, "amethysthelmet"),
                item.toItemStack());
        recipe.shape(
                "AAA",
                "A A"
        );
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("AmethystArmorCrafting: registered helmet recipe.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chestplate  —  vanilla pattern:
    //
    //  [A][ ][A]       row 1
    //  [A][A][A]       row 2
    //  [A][A][A]       row 3
    //
    // ─────────────────────────────────────────────────────────────────────────
    private static void registerChestplate(JavaPlugin plugin) {
        CorovaItems item = CorovaItems.getItemByName("amethystchestplate");
        if (item == null) {
            plugin.getLogger().warning("AmethystArmorCrafting: skipping chestplate — item not registered.");
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(
                new NamespacedKey(plugin, "amethystchestplate"),
                item.toItemStack());
        recipe.shape(
                "A A",
                "AAA",
                "AAA"
        );
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("AmethystArmorCrafting: registered chestplate recipe.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Leggings  —  vanilla pattern:
    //
    //  [A][A][A]       row 1
    //  [A][ ][A]       row 2
    //  [A][ ][A]       row 3
    //
    // ─────────────────────────────────────────────────────────────────────────
    private static void registerLeggings(JavaPlugin plugin) {
        CorovaItems item = CorovaItems.getItemByName("amethystleggings");
        if (item == null) {
            plugin.getLogger().warning("AmethystArmorCrafting: skipping leggings — item not registered.");
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(
                new NamespacedKey(plugin, "amethystleggings"),
                item.toItemStack());
        recipe.shape(
                "AAA",
                "A A",
                "A A"
        );
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("AmethystArmorCrafting: registered leggings recipe.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Boots  —  vanilla pattern:
    //
    //  [A][ ][A]       row 1
    //  [A][ ][A]       row 2
    //
    // ─────────────────────────────────────────────────────────────────────────
    private static void registerBoots(JavaPlugin plugin) {
        CorovaItems item = CorovaItems.getItemByName("amethystboots");
        if (item == null) {
            plugin.getLogger().warning("AmethystArmorCrafting: skipping boots — item not registered.");
            return;
        }
        ShapedRecipe recipe = new ShapedRecipe(
                new NamespacedKey(plugin, "amethystboots"),
                item.toItemStack());
        recipe.shape(
                "A A",
                "A A"
        );
        recipe.setIngredient('A', Material.AMETHYST_SHARD);
        Bukkit.addRecipe(recipe);
        plugin.getLogger().info("AmethystArmorCrafting: registered boots recipe.");
    }
}