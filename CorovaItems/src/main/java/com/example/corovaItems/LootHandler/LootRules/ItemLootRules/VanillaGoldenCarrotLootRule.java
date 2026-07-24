package com.example.corovaItems.LootHandler.LootRules.ItemLootRules;

import com.example.corovaItems.LootHandler.ChanceUtil;
import com.example.corovaItems.LootHandler.DropContext;
import com.example.corovaItems.LootHandler.ItemDropEntry;
import com.example.corovaItems.LootHandler.LootRule;
import com.example.corovaItems.LootHandler.MobLootConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class VanillaGoldenCarrotLootRule implements LootRule {

    private static final int DROP_MIN = 5;
    private static final int DROP_MAX = 15;

    private final Map<String, MobLootConfig> mobConfigs = new HashMap<>();

    public VanillaGoldenCarrotLootRule() {
        //              mob identifier                    base   loot    luck    DROPLIMITER
        registerMob("corovacore_killerbunny",               1.0,  0.0,  0.0,  true);   // DROPLIMITER: ON
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private VanillaGoldenCarrotLootRule registerMob(String id,
                                                    double baseChance,
                                                    double lootingBonus,
                                                    double luckBonus,
                                                    boolean dropLimiter) {
        mobConfigs.put(id.toLowerCase(), new MobLootConfig(baseChance, lootingBonus, luckBonus, dropLimiter));
        return this;
    }

    // ── Mob resolution ────────────────────────────────────────────────────────

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

        int amount = ThreadLocalRandom.current().nextInt(DROP_MIN, DROP_MAX + 1); // 5–15 inclusive
        ItemStack goldenCarrot = new ItemStack(Material.GOLDEN_CARROT, amount);

        String group = cfg.getDropGroup();
        if (group == null) {
            drops.add(ItemDropEntry.unlimited(goldenCarrot));
        } else {
            drops.add(ItemDropEntry.limited(goldenCarrot, group));
        }
    }
}