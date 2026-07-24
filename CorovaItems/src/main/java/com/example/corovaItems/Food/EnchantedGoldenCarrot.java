package com.example.corovaItems.Food;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class EnchantedGoldenCarrot extends CorovaItems implements Listener {

    public EnchantedGoldenCarrot() {
        super(
                ChatColor.GOLD + "Enchanted Golden Carrot", // Display Name
                Material.GOLDEN_CARROT,                     // Material
                lore(),                                     // Lore
                null,                                       // No real enchantments
                "enchantedgoldencarrot"                     // Internal ID
        );

        // Register automatically
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A mystical carrot full of magical energy!");
        lore.add(ChatColor.DARK_GRAY + "Grants powerful buffs when eaten.");
        return lore;
    }

    /** Apply glint without actual enchantment and make it always edible */
    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Add glint
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Get or create FoodComponent
            FoodComponent food = meta.getFood();
            food.setNutrition(6);
            food.setSaturation(1.2f);
            food.setCanAlwaysEat(true);  // allow eating when full

            // Apply back to item meta
            meta.setFood(food);
            item.setItemMeta(meta);
        }

        return item;
    }


    /** Handle consumption and apply potion effects */
    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!ItemManager.getInstance().isCorovaItem(item, this)) return;

        // Apply potion effects
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 4 * 60 * 20, 1));       // Haste II for 4 minutes
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 4 * 60 * 20, 1)); // Jump Boost II for 4 minutes
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 25 * 20, 2));       // Strength III for 25 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 4 * 60 * 20, 1));      // Speed II for 4 minutes
        player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 30 * 20, 0));    // Saturation for 30 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 4 * 60 * 20, 1));       // Luck II for 4 minutes
    }
}
