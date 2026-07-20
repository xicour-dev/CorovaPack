package com.example.corovaItems.CustomCrafting;

import com.example.corovaItems.CorovaItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class WandUpgradeListener implements Listener {

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent e) {
        SmithingInventory inventory = e.getInventory();
        ItemStack template = inventory.getItem(0);
        ItemStack base = inventory.getItem(1);
        ItemStack addition = inventory.getItem(2);

        // Ensure all three ingredient slots are filled for our custom recipe
        if (template == null || base == null || addition == null) {
            return;
        }

        // Check for the correct ingredients for the Netherite Wand upgrade
        boolean isCorrectTemplate = template.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE;
        CorovaItems diamondWand = CorovaItems.getItemByName("diamondwand");
        boolean isDiamondWand = diamondWand != null && diamondWand.isThisItem(base);
        boolean isNetheriteIngot = addition.getType() == Material.NETHERITE_INGOT;

        if (isCorrectTemplate && isDiamondWand && isNetheriteIngot) {
            CorovaItems netheriteWand = CorovaItems.getItemByName("netheritewand");
            if (netheriteWand == null) {
                return;
            }
            ItemStack result = netheriteWand.getItemStack();

            // Transfer enchantments and durability from the base item to the result
            ItemMeta baseMeta = base.getItemMeta();
            if (baseMeta != null) {
                ItemMeta resultMeta = result.getItemMeta();
                if (resultMeta != null) {
                    if (baseMeta.hasEnchants()) {
                        baseMeta.getEnchants().forEach((ench, level) -> resultMeta.addEnchant(ench, level, true));
                    }
                    if (baseMeta instanceof Damageable && resultMeta instanceof Damageable) {
                        ((Damageable) resultMeta).setDamage(((Damageable) baseMeta).getDamage());
                    }
                    result.setItemMeta(resultMeta);
                }
            }
            e.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        // We only care about clicks inside a smithing inventory
        if (!(e.getInventory() instanceof SmithingInventory)) {
            return;
        }

        // Check if the click was on the result slot (slot 3 in modern smithing tables)
        if (e.getSlot() != 3) {
            return;
        }

        // Check if the player is actually trying to take an item from the result slot
        if (e.getCurrentItem() == null) {
            return;
        }

        CorovaItems netheriteWand = CorovaItems.getItemByName("netheritewand");
        if (netheriteWand == null || !netheriteWand.isThisItem(e.getCurrentItem())) {
            return;
        }

        // At this point, we know the player is trying to craft our wand.
        // We must override the default behavior completely.
        e.setCancelled(true); // Cancel the vanilla event

        SmithingInventory inventory = (SmithingInventory) e.getInventory();
        Player player = (Player) e.getWhoClicked();
        ItemStack resultItem = e.getCurrentItem().clone(); // Clone the item before it disappears

        // Consume the ingredients from the correct slots
        ItemStack template = inventory.getItem(0);
        ItemStack base = inventory.getItem(1);
        ItemStack addition = inventory.getItem(2);

        // This check is important to prevent duping if the player lags or swaps items
        if (template == null || base == null || addition == null) {
            return;
        }
        CorovaItems diamondWand = CorovaItems.getItemByName("diamondwand");
        if (template.getType() != Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE ||
                diamondWand == null || !diamondWand.isThisItem(base) ||
                addition.getType() != Material.NETHERITE_INGOT) {
            return; // Items were swapped out last-second, so we do nothing.
        }

        // Consume one from each stack
        template.setAmount(template.getAmount() - 1);
        base.setAmount(base.getAmount() - 1);
        addition.setAmount(addition.getAmount() - 1);

        // Update the inventory to show the consumed items
        inventory.setItem(0, template);
        inventory.setItem(1, base);
        inventory.setItem(2, addition);
        inventory.setItem(3, null); // Clear the result slot

        // Give the resulting item to the player on their cursor
        player.setItemOnCursor(resultItem);
    }
}