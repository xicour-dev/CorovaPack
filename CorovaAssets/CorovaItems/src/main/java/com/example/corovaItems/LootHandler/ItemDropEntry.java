package com.example.corovaItems.LootHandler;

import org.bukkit.inventory.ItemStack;

/**
 * A candidate drop produced by a LootRule.
 *
 * group == null  → UNLIMITED: this item always drops if it was added.
 * group != null  → LIMITED:   this item competes with others in the same named group.
 *                              Only one item per group survives to the final drop list.
 */
public final class ItemDropEntry {

    private final ItemStack item;
    private final String    group;

    /**
     * Create an unlimited drop (always drops).
     */
    public static ItemDropEntry unlimited(ItemStack item) {
        return new ItemDropEntry(item, null);
    }

    /**
     * Create a limited drop that competes within the named group.
     * Use {@link MobLootConfig#DEFAULT_GROUP} for the legacy shared pool.
     */
    public static ItemDropEntry limited(ItemStack item, String group) {
        if (group == null) throw new IllegalArgumentException("group must not be null for a limited drop; use unlimited() instead");
        return new ItemDropEntry(item, group);
    }

    private ItemDropEntry(ItemStack item, String group) {
        if (item == null) throw new IllegalArgumentException("ItemDropEntry item must not be null");
        this.item  = item;
        this.group = group;
    }

    public ItemStack getItem()  { return item;  }
    public String    getGroup() { return group; }
    public boolean   isLimited() { return group != null; }
}