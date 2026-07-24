package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.XP;

import com.example.corovaItems.LootHandler.DropContext;
import com.example.corovaItems.LootHandler.ItemDropEntry;
import com.example.corovaItems.LootHandler.LootRule;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for XP-only loot rules.
 *
 * Drops no items. When the dying mob matches any registered mob ID,
 * getExperience() returns raw XP points equivalent to the given level,
 * unconditionally (guaranteed drop).
 *
 * Mob resolution mirrors AbstractItemLootRule:
 *   1. Scoreboard tags
 *   2. PDC "namespace:key" match
 *   3. PDC key-name match (any namespace)
 *   4. Entity type name
 */
public abstract class AbstractXPLootRule implements LootRule {

    private final int rawXP;
    protected final Map<String, Boolean> mobIds = new HashMap<>();

    /**
     * @param level The XP level whose total raw-point equivalent will be dropped.
     *              e.g. 10 → 160 raw XP points, 50 → 5345, 100 → 30970.
     */
    protected AbstractXPLootRule(int level) {
        this.rawXP = levelToRawXP(level);
    }

    /**
     * Converts an XP level to the total raw XP points needed to reach it from zero.
     * Uses Minecraft's exact piecewise formula.
     *
     *   Level  0–16:  N² + 6N
     *   Level 17–31:  2.5N² − 40.5N + 360
     *   Level 32+:    4.5N² − 162.5N + 2220
     */
    private static int levelToRawXP(int level) {
        if (level <= 0)  return 0;
        if (level <= 16) return (level * level) + (6 * level);
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return             (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    // ── Registration ──────────────────────────────────────────────────────────

    protected AbstractXPLootRule registerMob(String id) {
        mobIds.put(id.toLowerCase(), true);
        return this;
    }

    // ── LootRule ──────────────────────────────────────────────────────────────

    @Override
    public void collectDrops(DropContext context, List<ItemDropEntry> drops) { }

    @Override
    public Integer getExperience(DropContext context) {
        if (context.getMob() == null) return null;
        return matches(context.getMob()) ? rawXP : null;
    }

    @Override
    public boolean overridesVanillaDrops() { return false; }

    @Override
    public int getPriority() { return 0; }

    // ── Mob resolution ────────────────────────────────────────────────────────

    private boolean matches(LivingEntity mob) {
        for (String tag : mob.getScoreboardTags()) {
            if (mobIds.containsKey(tag.toLowerCase())) return true;
        }

        PersistentDataContainer pdc = mob.getPersistentDataContainer();

        for (String registeredId : mobIds.keySet()) {
            String[] parts = registeredId.split(":", 2);
            if (parts.length != 2) continue;
            try {
                NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
                if (pdc.has(key, PersistentDataType.BYTE)) return true;
            } catch (IllegalArgumentException ignored) { }
        }

        for (NamespacedKey pdcKey : pdc.getKeys()) {
            if (mobIds.containsKey(pdcKey.getKey().toLowerCase())) return true;
        }

        return mobIds.containsKey(mob.getType().name().toLowerCase());
    }
}