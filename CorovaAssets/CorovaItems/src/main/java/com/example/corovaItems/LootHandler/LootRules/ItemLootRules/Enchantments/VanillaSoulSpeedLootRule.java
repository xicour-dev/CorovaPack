package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.ChanceUtil;
import com.example.corovaItems.LootHandler.DropContext;
import com.example.corovaItems.LootHandler.ItemDropEntry;
import com.example.corovaItems.LootHandler.LootRule;
import com.example.corovaItems.LootHandler.MobLootConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla loot rule that drops a Soul Speed enchanted book (level I, II, or III)
 * chosen at random when the roll succeeds.
 *
 * Because this produces a plain vanilla enchanted book (not an ItemManager item),
 * it does NOT extend AbstractItemLootRule. Instead it replicates the same
 * per-mob config pattern manually so it integrates cleanly with LootRuleManager.
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class VanillaSoulSpeedLootRule implements LootRule {

    private final Map<String, MobLootConfig> mobConfigs = new HashMap<>();

    public VanillaSoulSpeedLootRule() {
        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("soul_hound_mob",   0.01,  0.005,  0.005,  true);
        registerMob("soul_blaze",       0.01,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_4",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_5",          0.01,  0.005,  0.005,  true);
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private VanillaSoulSpeedLootRule registerMob(String id,
                                                 double baseChance,
                                                 double lootingBonus,
                                                 double luckBonus,
                                                 boolean dropLimiter) {
        mobConfigs.put(id.toLowerCase(), new MobLootConfig(baseChance, lootingBonus, luckBonus, dropLimiter));
        return this;
    }

    // ── Mob resolution ────────────────────────────────────────────────────────

    /**
     * Resolve the MobLootConfig for the mob that died, or null if not applicable.
     *
     * Resolution order (mirrors AbstractItemLootRule):
     *   1. Scoreboard tags on the entity
     *   2. PDC entries with a "namespace:key" formatted id
     *   3. PDC key-name match (no namespace in registered id)
     *   4. Entity type name (e.g. "zombie", "skeleton")
     */
    public MobLootConfig resolveConfig(DropContext context) {
        if (context.getMob() == null) return null;
        LivingEntity mob = context.getMob();

        // 1. Scoreboard tags
        for (String tag : mob.getScoreboardTags()) {
            MobLootConfig cfg = mobConfigs.get(tag.toLowerCase());
            if (cfg != null) return cfg;
        }

        var pdc = mob.getPersistentDataContainer();

        // 2. PDC entries with "namespace:key" formatted id
        for (Map.Entry<String, MobLootConfig> entry : mobConfigs.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            if (parts.length != 2) continue;
            try {
                NamespacedKey key = new NamespacedKey(parts[0], parts[1]);
                if (pdc.has(key, PersistentDataType.BYTE)) {
                    return entry.getValue();
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // 3. PDC key-name match (registered id has no ":")
        for (NamespacedKey pdcKey : pdc.getKeys()) {
            MobLootConfig cfg = mobConfigs.get(pdcKey.getKey().toLowerCase());
            if (cfg != null) return cfg;
        }

        // 4. Entity type name fallback
        return mobConfigs.get(mob.getType().name().toLowerCase());
    }

    // ── LootRule implementation ───────────────────────────────────────────────

    @Override
    public void collectDrops(DropContext context, List<ItemDropEntry> drops) {
        MobLootConfig cfg = resolveConfig(context);
        if (cfg == null) return;

        double chance = ChanceUtil.applyLootingAndLuck(
                cfg.getBaseChance(), context, cfg.getLootingBonus(), cfg.getLuckBonus());

        if (!context.roll(chance)) return;

        int soulSpeedLevel = ThreadLocalRandom.current().nextInt(3) + 1;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.SOUL_SPEED, soulSpeedLevel, false);
        book.setItemMeta(meta);

        String group = cfg.getDropGroup();
        if (group == null) {
            drops.add(ItemDropEntry.unlimited(book));
        } else {
            drops.add(ItemDropEntry.limited(book, group));
        }
    }
}