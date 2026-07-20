package com.example.corovaItems.LootHandler;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * Interface for all loot rules.
 *
 * The contract is simple:
 *   - collectDrops() is the ONE method rules implement.
 *   - It receives a single flat list and adds ItemDropEntry objects to it.
 *   - Each ItemDropEntry carries the item AND whether it is limited (and to what group).
 *   - LootRuleManager handles all arbitration after collecting from every rule.
 *
 * Rules never need to know about other rules, buckets, or arbitration logic.
 */
public interface LootRule {

    /**
     * Collect all candidate drops for this rule into the provided list.
     * Roll your chances here and add an ItemDropEntry for each item that wins its roll.
     *
     * @param context The drop context.
     * @param drops   Add your candidate drops here.
     */
    void collectDrops(DropContext context, List<ItemDropEntry> drops);

    /**
     * Return the amount of XP to drop, or null to use vanilla default.
     */
    default Integer getExperience(DropContext context) {
        return null;
    }

    /**
     * Return true to clear vanilla drops for this mob.
     */
    default boolean overridesVanillaDrops() {
        return false;
    }

    /**
     * Higher priority rules run first. Default 0.
     */
    default int getPriority() {
        return 0;
    }
}