package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cleans up VanillaEnchantDisplay PDC keys and lore lines when an item passes
 * through a grindstone.
 *
 * Vanilla strips the live enchant map but leaves PDC and lore untouched, which
 * causes getTrueLevel() to return stale values and leaves the gray lore lines
 * still visible on the item after grinding.
 *
 * Strategy: intercept any click that would cause the player to receive the
 * grindstone result — either clicking the result slot directly (slot 2) or
 * shift-clicking an input slot (slots 0/1) while a result exists — clean the
 * output item, then give it to the player manually.
 *
 * Bugs fixed in this implementation:
 *
 *  1. The result item's PDC is NOT inherited from the input items in Bukkit.
 *     We must read which enchantments to strip from the INPUT items' PDC, then
 *     strip those same keys from the RESULT item. The original code did this
 *     correctly in concept but the result meta was then never given back to the
 *     player (see bug 3).
 *
 *  2. Shift-clicking an INPUT slot (rawSlot 0 or 1) also triggers a result
 *     collection. The original guard `if (rawSlot != 2) return` missed these,
 *     leaving lore un-cleaned on shift-click pickups.
 *
 *  3. Calling `event.getInventory().setItem(2, cleaned)` during a click event
 *     does NOT change what the player receives — Bukkit has already snapshotted
 *     the result item before our handler fires. We must cancel the event and
 *     give the item ourselves so the player actually gets the cleaned version.
 *
 *  4. CustomEnchantMutationFormatting's marker-bracketed blocks (custom
 *     enchant / mutation lore) were not stripped from the result item, leaving
 *     stale bracketed content on ground items. Now delegates to
 *     {@link CustomEnchantMutationFormatting#stripManagedBlocks(List)} instead
 *     of maintaining a second, independent copy of that logic. The previous
 *     local copy matched sentinels by exact line equality; once
 *     CustomEnchantMutationFormatting switched to gluing its markers onto the
 *     front/back of real content lines (fixing the blank-lore-row bug), an
 *     exact-equality check here would never match anything and every
 *     grindstoned item would keep its full custom-enchant/mutation lore.
 *     Delegating to the shared method means the two can never drift out of
 *     sync like that again.
 */
public class GrindstoneEnchantCleaner implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGrindstoneClick(InventoryClickEvent event) {
        // Only care about grindstones
        if (event.getInventory().getType() != InventoryType.GRINDSTONE) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();

        /*
         * Two cases where the player collects the result:
         *   a) Normal/double-click on result slot (rawSlot == 2, top inventory)
         *   b) Shift-click on an input slot (rawSlot 0 or 1, top inventory)
         *      — vanilla moves the result to the player's inventory on these too.
         */
        boolean isResultClick = (rawSlot == 2);
        boolean isInputShiftClick = (rawSlot == 0 || rawSlot == 1)
                && (event.getClick() == ClickType.SHIFT_LEFT
                || event.getClick() == ClickType.SHIFT_RIGHT);

        if (!isResultClick && !isInputShiftClick) return;

        // There must be a result present
        ItemStack result = event.getInventory().getItem(2);
        if (result == null || result.getType().isAir()) return;

        // Read the true-level PDC keys from the INPUT items before vanilla clears them.
        // The result item's own PDC is a fresh copy that does NOT carry over the
        // VanillaEnchantDisplay keys from the inputs — so we identify what to strip
        // from the inputs, then remove those same keys from the result.
        ItemStack upper = event.getInventory().getItem(0);
        ItemStack lower = event.getInventory().getItem(1);

        ItemStack cleaned = result.clone();
        ItemMeta meta = cleaned.getItemMeta();
        if (meta == null) return;

        // Strip PDC true-level entries for every enchantment found on either input.
        for (ItemStack input : new ItemStack[]{upper, lower}) {
            if (input == null || input.getType().isAir()) continue;
            Map<Enchantment, Integer> trueLevels = VanillaEnchantDisplay.getAllTrueLevels(input);
            for (Enchantment ench : trueLevels.keySet()) {
                VanillaEnchantDisplay.removeTrueLevelFromMeta(meta, ench);
            }
        }

        // Also clear custom enchantments from PDC.
        meta.getPersistentDataContainer().remove(com.example.corovaItems.Enchantments.CorovaEnchantments.KEY_ENCHANT_ID);
        meta.getPersistentDataContainer().remove(com.example.corovaItems.Enchantments.CorovaEnchantments.KEY_ENCHANT_LVL);
        meta.getPersistentDataContainer().remove(com.example.corovaItems.Enchantments.CorovaEnchantments.KEY_ENCHANT_2_ID);
        meta.getPersistentDataContainer().remove(com.example.corovaItems.Enchantments.CorovaEnchantments.KEY_ENCHANT_2_LVL);

        // Remove enchantment hide flags so the ground item looks vanilla.
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.removeItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);

        // Strip all VanillaEnchantDisplay-managed lore lines (signature + legacy).
        // Also strip CustomEnchantMutationFormatting-managed blocks (custom
        // enchant / mutation lore) and any content they bracket, so the
        // grindstoned item doesn't carry stale managed-block content.
        if (meta.hasLore()) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(VanillaEnchantDisplay::isOverLevelLoreLine);
            lore = CustomEnchantMutationFormatting.stripManagedBlocks(lore);
            // Also strip trim/synergy components on grindstone cleanup.
            com.example.corovaItems.ItemMutations.MutationUtils.stripLoreComponents(lore);
            // Clean up trailing empty lines
            while (!lore.isEmpty() && lore.get(lore.size() - 1).trim().isEmpty()) {
                lore.remove(lore.size() - 1);
            }
            meta.setLore(lore);
        }

        cleaned.setItemMeta(meta);

        /*
         * Cancel the vanilla click so Bukkit doesn't give the player the
         * un-cleaned original. Then manually add the cleaned item to the
         * player's inventory (dropping any overflow on the ground), clear the
         * result slot, and consume the input slots exactly as vanilla would.
         */
        event.setCancelled(true);

        // Give the cleaned item; drop overflow at the player's feet
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(cleaned);
        for (ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }

        // Clear all three grindstone slots to match vanilla behaviour
        event.getInventory().setItem(0, null);
        event.getInventory().setItem(1, null);
        event.getInventory().setItem(2, null);

        // Force an inventory update so the client sees the cleared slots
        player.updateInventory();
    }
}