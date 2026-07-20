package com.example.corovaItems.LootHandler;

import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Convenience base class for mob-specific rules (KillerBunnyLootRule, etc.).
 *
 * These rules use a simpler pattern: they only need to implement:
 *   - appliesTo(context)  — return true if this rule governs this mob
 *   - collectItems(context, drops) — add ItemStacks directly to the list
 *
 * Routing: all items added via collectItems() are treated as UNLIMITED
 * (they always drop when added). If a mob-specific rule needs limited/group
 * behaviour, it should extend AbstractItemLootRule or implement LootRule directly.
 */
public abstract class MobLootRule implements LootRule {

    /**
     * Return true if this rule governs the dying mob in the given context.
     */
    protected abstract boolean appliesTo(DropContext context);

    /**
     * Add any items that should drop to the list.
     * Called only when appliesTo() returned true.
     */
    protected abstract void collectItems(DropContext context, List<ItemStack> drops);

    @Override
    public void collectDrops(DropContext context, List<ItemDropEntry> drops) {
        if (!appliesTo(context)) return;

        List<ItemStack> items = new ArrayList<>();
        collectItems(context, items);

        // All mob-specific rule items are unlimited — they drop independently.
        for (ItemStack item : items) {
            if (item != null) {
                drops.add(ItemDropEntry.unlimited(item));
            }
        }
    }
}