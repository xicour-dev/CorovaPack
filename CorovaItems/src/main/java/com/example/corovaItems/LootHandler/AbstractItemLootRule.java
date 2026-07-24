package com.example.corovaItems.LootHandler;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Base class for all item-based loot rules.
 *
 * Subclasses register mobs in their constructor and that is all they do.
 * This class handles all resolution, rolling, and drop routing internally
 * through the single collectDrops() method required by LootRule.
 */
public abstract class AbstractItemLootRule implements LootRule {

    private static final Logger LOGGER = Logger.getLogger("corovaItems");

    protected final String itemId;
    protected final double defaultBaseChance;
    protected final double defaultLootingBonus;
    protected final double defaultLuckBonus;
    protected final int    defaultMinLevel;
    protected final int    defaultMaxLevel;

    protected final Map<String, MobLootConfig> mobConfigs = new HashMap<>();

    protected AbstractItemLootRule(String itemId,
                                   double defaultBaseChance,
                                   double defaultLootingBonus,
                                   double defaultLuckBonus) {
        this(itemId, defaultBaseChance, defaultLootingBonus, defaultLuckBonus, 1, 1);
    }

    protected AbstractItemLootRule(String itemId,
                                   double defaultBaseChance,
                                   double defaultLootingBonus,
                                   double defaultLuckBonus,
                                   int defaultMinLevel,
                                   int defaultMaxLevel) {
        this.itemId              = itemId;
        this.defaultBaseChance   = defaultBaseChance;
        this.defaultLootingBonus = defaultLootingBonus;
        this.defaultLuckBonus    = defaultLuckBonus;
        this.defaultMinLevel     = defaultMinLevel;
        this.defaultMaxLevel     = defaultMaxLevel;
    }

    // ── registerMob overloads ─────────────────────────────────────────────────

    /** Register mob with all defaults; DROPLIMITER ON (default group). */
    protected AbstractItemLootRule registerMob(String id) {
        return registerMob(id, defaultBaseChance, defaultLootingBonus, defaultLuckBonus, MobLootConfig.DEFAULT_GROUP);
    }

    /** Register mob with custom base chance; DROPLIMITER ON (default group). */
    protected AbstractItemLootRule registerMob(String id, double baseChance) {
        return registerMob(id, baseChance, defaultLootingBonus, defaultLuckBonus, MobLootConfig.DEFAULT_GROUP);
    }

    /** Register mob with custom chances; DROPLIMITER ON (default group). */
    protected AbstractItemLootRule registerMob(String id,
                                               double baseChance,
                                               double lootingBonus,
                                               double luckBonus) {
        return registerMob(id, baseChance, lootingBonus, luckBonus, MobLootConfig.DEFAULT_GROUP);
    }

    /**
     * Legacy boolean overload.
     *   true  → DROPLIMITER ON  (default group)
     *   false → DROPLIMITER OFF (unlimited, always drops on success)
     */
    protected AbstractItemLootRule registerMob(String id,
                                               double baseChance,
                                               double lootingBonus,
                                               double luckBonus,
                                               boolean dropLimiter) {
        return registerMob(id, baseChance, lootingBonus, luckBonus,
                dropLimiter ? MobLootConfig.DEFAULT_GROUP : null);
    }

    /**
     * Full control overload.
     *   null      → DROPLIMITER OFF (unlimited)
     *   "default" → DROPLIMITER ON, shared default pool
     *   "trinket" → DROPLIMITER ON, named "trinket" pool
     *   etc.
     */
    protected AbstractItemLootRule registerMob(String id,
                                               double baseChance,
                                               double lootingBonus,
                                               double luckBonus,
                                               String dropGroup) {
        mobConfigs.put(id.toLowerCase(), new MobLootConfig(baseChance, lootingBonus, luckBonus, dropGroup, defaultMinLevel, defaultMaxLevel));
        return this;
    }

    /**
     * Full control overload with level ranges.
     */
    protected AbstractItemLootRule registerMob(String id,
                                               double baseChance,
                                               double lootingBonus,
                                               double luckBonus,
                                               String dropGroup,
                                               int minLevel,
                                               int maxLevel) {
        mobConfigs.put(id.toLowerCase(), new MobLootConfig(baseChance, lootingBonus, luckBonus, dropGroup, minLevel, maxLevel));
        return this;
    }

    // ── Config resolution ─────────────────────────────────────────────────────

    /**
     * Find the MobLootConfig for the dying mob, or null if this rule doesn't apply.
     *
     * Resolution order:
     *   1. Scoreboard tags      (custom mob identification)
     *   2. PDC keys             ("namespace:key" format — registered id contains ":")
     *   3. PDC key-name match   (registered id has no ":" — matched against the key
     *                            portion of every PDC entry on the mob, any namespace)
     *   4. Entity type name     (vanilla mobs)
     */
    public MobLootConfig resolveConfig(DropContext context) {
        if (context.getMob() == null) return null;
        LivingEntity mob = context.getMob();

        // 1. Scoreboard tags
        for (String tag : mob.getScoreboardTags()) {
            MobLootConfig cfg = mobConfigs.get(tag.toLowerCase());
            if (cfg != null) return cfg;
        }

        PersistentDataContainer pdc = mob.getPersistentDataContainer();

        // 2. PDC keys — registered id is "namespace:key"
        for (Map.Entry<String, MobLootConfig> entry : mobConfigs.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) continue;
            try {
                NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
                if (pdc.has(key, PersistentDataType.BYTE)) {
                    return entry.getValue();
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid namespace/key characters — skip silently
            }
        }

        // 3. PDC key-name match — registered id has no ":", match against the key
        //    portion of every PDC entry on the mob regardless of namespace.
        //    This lets you registerMob("deadthor") and have it match a mob whose
        //    PDC contains e.g. corovamobs:deadthor or anyns:deadthor.
        for (NamespacedKey pdcKey : pdc.getKeys()) {
            MobLootConfig cfg = mobConfigs.get(pdcKey.getKey().toLowerCase());
            if (cfg != null) return cfg;
        }

        // 4. Entity type name
        return mobConfigs.get(mob.getType().name().toLowerCase());
    }

    // ── LootRule implementation ───────────────────────────────────────────────

    /**
     * The single, definitive code path for this rule.
     *
     *   1. Resolve config  — null means this rule doesn't apply, return.
     *   2. Calculate chance and roll — miss means return.
     *   3. Build item      — null means item lookup failed, return.
     *   4. Add to drops as unlimited or limited based on dropGroup.
     *
     * No separate applies() gate. No dual code path. No shared mutable state.
     * Every case terminates cleanly with an early return.
     */
    @Override
    public void collectDrops(DropContext context, List<ItemDropEntry> drops) {
        MobLootConfig cfg = resolveConfig(context);
        if (cfg == null) return;

        double chance = ChanceUtil.applyLootingAndLuck(
                cfg.getBaseChance(), context, cfg.getLootingBonus(), cfg.getLuckBonus());

        if (!context.roll(chance)) return;

        ItemStack item = buildItemStack(cfg.getRandomLevel());
        if (item == null) return;

        String group = cfg.getDropGroup();
        if (group == null) {
            drops.add(ItemDropEntry.unlimited(item));
        } else {
            drops.add(ItemDropEntry.limited(item, group));
        }
    }

    // ── Item construction ─────────────────────────────────────────────────────

    public ItemStack buildItemStack() {
        return buildItemStack(1);
    }

    public ItemStack buildItemStack(int level) {
        if (itemId == null) return null;
        try {
            CorovaItems entry = CorovaItems.getItemByName(itemId);
            if (entry == null) {
                LOGGER.warning("[corovaItems] LootRule " + getClass().getSimpleName()
                        + ": CorovaItems.getItemByName('" + itemId + "') returned null.");
                return null;
            }

            if (entry instanceof EnchantmentBook book) {
                return book.createBookStack(level);
            }

            return entry.getItemStack();
        } catch (Exception e) {
            LOGGER.severe("[corovaItems] LootRule " + getClass().getSimpleName()
                    + ": exception building item '" + itemId + "': " + e.getMessage());
            return null;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String      getItemId()           { return itemId;             }
    public double      getDefaultBaseChance() { return defaultBaseChance; }
    public Set<String> getApplicableMobs()    { return mobConfigs.keySet(); }
}