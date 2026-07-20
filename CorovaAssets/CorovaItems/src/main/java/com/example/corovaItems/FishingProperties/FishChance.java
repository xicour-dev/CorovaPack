package com.example.corovaItems.FishingProperties;

import org.bukkit.inventory.ItemStack;

/**
 * Represents a single weighted entry in a fishing loot table.
 * Higher weight = more likely to be selected relative to the pool total.
 *
 * Example:
 *   new FishChance(new ItemStack(Material.COD), 40, 1, 3, "Cod")
 *   new FishChance(myCorovaItem.getItemStack(), 5, 1, 1, "Rare Relic")
 */
public class FishChance {

    /**
     * Broad category used to filter tables or show in messages.
     *
     * COROVA_ITEM — custom CorovaItems (armor, trinkets, cases, weapons).
     *               Luck of the Sea boosts these the same way it boosts TREASURE.
     */
    public enum Category {
        FISH,
        JUNK,
        TREASURE,
        CUSTOM,
        COROVA_ITEM
    }

    private final ItemStack item;
    private final int weight;
    private final int minCount;
    private final int maxCount;
    private final String displayLabel;
    private final Category category;

    // ── Convenience constructors ──────────────────────────────────────────────

    public FishChance(ItemStack item, int weight) {
        this(item, weight, 1, 1, item.getType().name(), Category.FISH);
    }

    public FishChance(ItemStack item, int weight, String displayLabel) {
        this(item, weight, 1, 1, displayLabel, Category.FISH);
    }

    public FishChance(ItemStack item, int weight, int minCount, int maxCount, String displayLabel) {
        this(item, weight, minCount, maxCount, displayLabel, Category.FISH);
    }

    public FishChance(ItemStack item, int weight, int minCount, int maxCount,
                      String displayLabel, Category category) {
        if (weight <= 0)         throw new IllegalArgumentException("FishChance weight must be > 0");
        if (minCount < 1)        throw new IllegalArgumentException("FishChance minCount must be >= 1");
        if (maxCount < minCount) throw new IllegalArgumentException("FishChance maxCount must be >= minCount");

        this.item         = item.clone();
        this.weight       = weight;
        this.minCount     = minCount;
        this.maxCount     = maxCount;
        this.displayLabel = displayLabel;
        this.category     = category;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Returns a defensive copy so callers can't mutate the template. */
    public ItemStack getItem()         { return item.clone(); }
    public int       getWeight()       { return weight; }
    public int       getMinCount()     { return minCount; }
    public int       getMaxCount()     { return maxCount; }
    public String    getDisplayLabel() { return displayLabel; }
    public Category  getCategory()     { return category; }
}