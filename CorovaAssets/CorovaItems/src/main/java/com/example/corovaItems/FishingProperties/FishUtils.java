package com.example.corovaItems.FishingProperties;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Utility helpers for the custom fishing system.
 */
public class FishUtils {

    private static final Random RANDOM = new Random();

    private FishUtils() {} // static utility class

    // ── Selection ─────────────────────────────────────────────────────────────

    /**
     * Picks a random {@link FishChance} from the pool using weighted probability.
     * Respects the player's Luck of the Sea level:
     *   - Each level reduces JUNK weight by 8% and FISH weight by 2%.
     *   - TREASURE and COROVA_ITEM entries get a slight boost per level.
     *
     * @param pool   The full loot table.
     * @param player The fishing player (used to read Luck of the Sea level).
     * @return       The chosen entry, or {@code null} if the pool is empty.
     */
    public static FishChance pickWeightedRandom(List<FishChance> pool, Player player) {
        if (pool == null || pool.isEmpty()) return null;

        int luckLevel = player.getInventory().getItemInMainHand()
                .getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);

        List<Integer> effectiveWeights = pool.stream()
                .map(e -> adjustWeight(e, luckLevel))
                .collect(Collectors.toList());

        int totalWeight = effectiveWeights.stream().mapToInt(Integer::intValue).sum();
        if (totalWeight <= 0) return pool.get(pool.size() - 1);

        int roll = RANDOM.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < pool.size(); i++) {
            cumulative += effectiveWeights.get(i);
            if (roll < cumulative) return pool.get(i);
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * Simpler overload — no luck adjustment.
     */
    public static FishChance pickWeightedRandom(List<FishChance> pool) {
        if (pool == null || pool.isEmpty()) return null;
        int totalWeight = pool.stream().mapToInt(FishChance::getWeight).sum();
        int roll = RANDOM.nextInt(totalWeight);
        int cumulative = 0;
        for (FishChance entry : pool) {
            cumulative += entry.getWeight();
            if (roll < cumulative) return entry;
        }
        return pool.get(pool.size() - 1);
    }

    // ── Item building ─────────────────────────────────────────────────────────

    /**
     * Clones the entry's item template, then randomises the stack count
     * within [minCount, maxCount].
     */
    public static ItemStack resolveItem(FishChance entry) {
        ItemStack item = entry.getItem(); // already a clone
        int range = entry.getMaxCount() - entry.getMinCount();
        int count = (range == 0)
                ? entry.getMinCount()
                : entry.getMinCount() + RANDOM.nextInt(range + 1);
        item.setAmount(count);
        return item;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the effective weight for a pool entry after applying Luck of the Sea.
     *
     * JUNK     — reduced 8% per level (becomes much less likely).
     * FISH     — reduced 2% per level (slightly less likely).
     * TREASURE — boosted 1% per level (slightly more likely relatively).
     * COROVA_ITEM — boosted 2% per level (Luck of the Sea rewards CorovaItem hunters).
     * CUSTOM   — unchanged (neutral category).
     */
    private static int adjustWeight(FishChance entry, int luckLevel) {
        if (luckLevel == 0) return entry.getWeight();
        double multiplier = switch (entry.getCategory()) {
            case JUNK        -> Math.max(0.0, 1.0 - luckLevel * 0.08);
            case FISH        -> Math.max(0.0, 1.0 - luckLevel * 0.02);
            case TREASURE    -> 1.0 + luckLevel * 0.01;
            case COROVA_ITEM -> 1.0 + luckLevel * 0.02; // luck noticeably helps CorovaItem odds
            default          -> 1.0;
        };
        return (int) Math.max(1, Math.round(entry.getWeight() * multiplier));
    }

    /**
     * Calculates the total weight of a pool — useful for displaying odds.
     */
    public static int totalWeight(List<FishChance> pool) {
        return pool.stream().mapToInt(FishChance::getWeight).sum();
    }

    /**
     * Returns the percentage chance (0–100) for a single entry in the pool.
     */
    public static double chancePercent(FishChance entry, List<FishChance> pool) {
        int total = totalWeight(pool);
        return total == 0 ? 0 : (entry.getWeight() * 100.0) / total;
    }
}