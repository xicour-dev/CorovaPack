package com.example.corovaItems;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/**
 * Central manager for CorovaItems.
 */
public class ItemManager {

    private static ItemManager instance;

    private final Map<String, CorovaItems> itemsById = new HashMap<>();

    private ItemManager() { instance = this; }

    public static ItemManager getInstance() {
        if (instance == null) instance = new ItemManager();
        return instance;
    }

    public void registerItem(CorovaItems item) {
        itemsById.put(item.getInternalId().toLowerCase(), item);
    }

    public List<CorovaItems> getAllItems() {
        return new ArrayList<>(itemsById.values());
    }

    public CorovaItems getItemById(String internalId) {
        if (internalId == null) return null;
        CorovaItems item = itemsById.get(internalId.toLowerCase());
        if (item != null) return item;
        return CorovaItems.getItemByName(internalId);
    }

    public boolean isCorovaItem(ItemStack item) {
        if (item == null) return false;
        for (CorovaItems ci : itemsById.values()) if (ci.isThisItem(item)) return true;
        return false;
    }

    public boolean isCorovaItem(ItemStack item, CorovaItems customItem) {
        if (item == null || customItem == null) return false;
        return customItem.isThisItem(item);
    }

    public boolean giveItem(Player player, String internalId) {
        CorovaItems ci = getItemById(internalId);
        if (ci == null) return false;

        if (ci.getMaterials().length > 1) {
            for (ItemStack piece : ci.getFullSet()) player.getInventory().addItem(piece);
            player.sendMessage("§aYou received the full " + ci.getName() + " armor set!");
        } else {
            ItemStack item = ci.getItemStack();
            player.getInventory().addItem(item);
            if (item.hasItemMeta()) {
                player.sendMessage("§aYou received: " + item.getItemMeta().getDisplayName());
            }
        }
        return true;
    }

    public ItemStack getItemStack(String internalId) {
        CorovaItems ci = getItemById(internalId);
        if (ci == null) return null;
        return ci.getItemStack();
    }
}
