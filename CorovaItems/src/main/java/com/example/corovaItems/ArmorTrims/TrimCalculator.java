package com.example.corovaItems.ArmorTrims;

import com.example.corovaItems.ItemMutations.Mutation.MutationCategory;
import com.example.corovaItems.MageSystem.ManaManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TrimCalculator {

    private TrimCalculator() {}

    // ── Set Bonus Spectrum API ────────────────────────────────────────────────

    /**
     * Returns true only when the player has all 4 armor slots filled with ANY trim.
     * This is the prerequisite for ANY set bonus to activate.
     */
    public static boolean hasFullTrimmedSet(PlayerTrimProfile profile) {
        return profile.getTotalTrimCount() >= 4;
    }

    /**
     * Returns a 0.0–1.0 scalar representing how much of a material's set bonus
     * is active. Requires a full 4-piece trimmed set (any mix) to be equipped first —
     * if fewer than 4 trimmed pieces are worn, returns 0.0 regardless of material count.
     * When the full set requirement is met, scales by how many pieces of this material
     * the player is wearing: 1 piece = 25%, 2 = 50%, 3 = 75%, 4 = 100%.
     */
    public static double getSetBonusStrength(TrimMaterialType type, PlayerTrimProfile profile) {
        if (!hasFullTrimmedSet(profile)) return 0.0;
        return Math.min(getEffectivePieceCount(type, profile), 4.0) / 4.0;
    }

    /** True only when the player has all 4 pieces of the same material (including resin replication). */
    public static boolean hasPureSet(TrimMaterialType type, PlayerTrimProfile profile) {
        return getEffectivePieceCount(type, profile) >= 4;
    }

    /** True when the player is wearing at least one trimmed armor piece. */
    public static boolean hasAnyTrim(PlayerTrimProfile profile) {
        return profile.getTotalTrimCount() > 0;
    }

    // Kept for backwards compat — now correctly checks for a full 4-piece trimmed set.
    public static boolean hasFullSetEquipped(PlayerTrimProfile profile) {
        return hasFullTrimmedSet(profile);
    }

    public static double getEffectivePieceCount(TrimMaterialType type, PlayerTrimProfile profile) {
        int count = profile.getCount(type);
        if (type == TrimMaterialType.RESIN) return count;
        // Resin replication logic: if you have at least one piece of a material,
        // all resin pieces also count as that material.
        return count > 0 ? (count + profile.resinCount) : 0;
    }

    // ── Custom Synergy Hook ───────────────────────────────────────────────────

    public static boolean applyCustomSynergies(Player player, PlayerTrimProfile profile) {
        return false;
    }

    public static List<String> describeCustomSynergies(PlayerTrimProfile profile) {
        return new ArrayList<>();
    }

    // ── Mutation Amplification ────────────────────────────────────────────────

    public static double getAmplification(
            Set<MutationCategory> categories,
            PlayerTrimProfile profile,
            String amplificationType) {

        return switch (amplificationType) {
            case "duration" -> categories.contains(MutationCategory.DEBUFF)
                    ? 1.0 + (getEffectivePieceCount(TrimMaterialType.IRON, profile) * 0.15)     : 1.0;
            case "window"          -> categories.contains(MutationCategory.BURST)
                    ? 1.0 + (getEffectivePieceCount(TrimMaterialType.COPPER, profile) * 0.12)    : 1.0;
            case "incremental"     -> categories.contains(MutationCategory.INCREMENTAL)
                    ? getEffectivePieceCount(TrimMaterialType.REDSTONE, profile) * 0.0525        : 0.0;
            case "accumulation"    -> categories.contains(MutationCategory.ACCUMULATION)
                    ? 1.0 + (getEffectivePieceCount(TrimMaterialType.GOLD, profile) * 0.10)      : 1.0;
            case "partial_synergy" -> categories.contains(MutationCategory.ENCHANT_SYNERGY)
                    ? getEffectivePieceCount(TrimMaterialType.LAPIS, profile) * 0.10             : 0.0;
            case "threshold"       -> categories.contains(MutationCategory.HEALTH_SCALE)
                    ? getEffectivePieceCount(TrimMaterialType.EMERALD, profile) * 0.05           : 0.0;
            case "condition"       -> categories.contains(MutationCategory.CONDITIONAL)
                    ? getEffectivePieceCount(TrimMaterialType.DIAMOND, profile) * 0.08           : 0.0;
            case "tick_damage"     -> categories.contains(MutationCategory.SUSTAINED)
                    ? 1.0 + (getEffectivePieceCount(TrimMaterialType.NETHERITE, profile) * 0.12) : 1.0;
            case "cooldown"        -> 1.0 - (getEffectivePieceCount(TrimMaterialType.AMETHYST, profile) * 0.08);
            case "recovery"        -> categories.contains(MutationCategory.RECOVERY)
                    ? 1.0 + (getEffectivePieceCount(TrimMaterialType.EMERALD, profile) * 0.15)   : 1.0;
            // Resin multiplier: a standalone case callers can query to get the full resin bonus
            // multiplier for any SUSTAINED/DEBUFF mutation (returns 1.0 + resin bonus as a factor).
            case "resin_multiplier" -> {
                double bonus = getResinBonus(categories, profile);
                yield bonus > 0 ? 1.0 + bonus : 1.0;
            }
            default                -> 1.0;
        };
    }

    public static double getQuartzIntersectionBonus(Set<MutationCategory> categories, PlayerTrimProfile profile) {
        double quartzPieces = getEffectivePieceCount(TrimMaterialType.QUARTZ, profile);
        if (quartzPieces < 1) return 1.0;
        boolean isBurst       = categories.contains(MutationCategory.BURST);
        boolean isConditional = categories.contains(MutationCategory.CONDITIONAL);
        return (isBurst && isConditional) ? 1.0 + (quartzPieces * 0.08) : 1.0;
    }

    /**
     * Returns an additive bonus applied to the raw (pre-crit) base damage on critical hits.
     * Scales per-piece: 1 piece = 15%, 2 = 30%, 3 = 45%, 4 = 60%.
     * getSetBonusStrength divides piece count by 4, so multiplying by 0.60 gives 15% per piece.
     * Caller must add the result to the final post-armor damage value (not BASE).
     */
    public static double getQuartzCritBonus(PlayerTrimProfile profile) {
        return getSetBonusStrength(TrimMaterialType.QUARTZ, profile) * 0.60;
    }

    public static double getResinBonus(Set<MutationCategory> categories, PlayerTrimProfile profile) {
        return 0.06 * profile.resinCount * countDomains(categories);
    }

    private static int countDomains(Set<MutationCategory> categories) {
        int count = 0;
        if (categories.contains(MutationCategory.SUSTAINED)) count++;
        if (categories.contains(MutationCategory.DEBUFF))    count++;
        return count;
    }

    // ── Runtime Amplification Helpers (call these from mutation onProc) ────────

    /**
     * Convenience overload — resolves the player's trim profile automatically.
     * Call this from a mutation's onProc/onNoProc when the damager is a Player.
     *
     * Usage in a SUSTAINED mutation's onProc:
     *   int ticks = (int)(baseTicks * TrimCalculator.getAmplification(getCategories(), damager, "tick_damage"));
     *
     * Usage in a DEBUFF mutation's onProc:
     *   int duration = (int)(baseDuration * TrimCalculator.getAmplification(getCategories(), damager, "duration"));
     */
    public static double getAmplification(
            Set<MutationCategory> categories,
            org.bukkit.entity.LivingEntity entity,
            String amplificationType) {
        PlayerTrimProfile profile;
        if (entity instanceof Player player) {
            profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
        } else {
            profile = PlayerTrimProfile.fromEntity(entity);
        }
        return getAmplification(categories, profile, amplificationType);
    }

    // ── General Passives ──────────────────────────────────────────────────────

    public static void applyGeneralPassives(Player player, PlayerTrimProfile profile) {
        // Resin replication logic: Resin adds to the count of any other present material.
        // Capped at 4 for attribute passives to maintain row-of-hearts consistency.
        double ironCount      = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.IRON,      profile));
        double copperCount    = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.COPPER,    profile));
        double redstoneCount  = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.REDSTONE,  profile));
        double goldCount      = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.GOLD,      profile));
        double emeraldCount   = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.EMERALD,   profile));
        double diamondCount   = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.DIAMOND,   profile));
        double netheriteCount = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.NETHERITE, profile));
        double amethystCount  = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.AMETHYST,  profile));
        double lapisCount     = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.LAPIS,     profile));

        applyAttributeModifier(player, Attribute.ARMOR, "trim_iron_armor",
                ironCount * 0.5, AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.ATTACK_SPEED, "trim_copper_attack_speed",
                copperCount * 0.075, AttributeModifier.Operation.ADD_SCALAR);

        applyAttributeModifier(player, Attribute.MOVEMENT_SPEED, "trim_redstone_speed",
                redstoneCount * 0.075, AttributeModifier.Operation.ADD_SCALAR);

        applyAttributeModifier(player, Attribute.ARMOR, "trim_gold_resistance",
                goldCount * 1.0, AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.MAX_HEALTH, "trim_emerald_health",
                emeraldCount * 5.0, AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.ARMOR, "trim_diamond_armor",
                diamondCount * 0.5, AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.ARMOR_TOUGHNESS, "trim_diamond_toughness",
                diamondCount * 0.5, AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.ARMOR_TOUGHNESS, "trim_netherite_toughness",
                netheriteCount * 1.0, AttributeModifier.Operation.ADD_NUMBER);

        // Zero-value call ensures any previously applied modifier is cleared on refresh.
        applyAttributeModifier(player, Attribute.ATTACK_DAMAGE, "trim_quartz_damage",
                0, AttributeModifier.Operation.ADD_SCALAR);

        // Mana: per-piece passive amplified by resin replication.
        double trimMana = ((amethystCount * 12.0) + (lapisCount * 5.0))
                + (hasPureSet(TrimMaterialType.AMETHYST, profile) ? 50.0 : 0.0);
        ManaManager.getInstance().setTrimManaBonus(player, trimMana);

        applySetBonuses(player, profile);
    }

    public static void applySetBonuses(Player player, PlayerTrimProfile profile) {
        applyAttributeModifier(player, Attribute.ARMOR, "trim_set_iron_defense",
                getSetBonusStrength(TrimMaterialType.IRON, profile) * 2.0,
                AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.ATTACK_SPEED, "trim_set_copper_attack_speed",
                getSetBonusStrength(TrimMaterialType.COPPER, profile) * 0.12,
                AttributeModifier.Operation.ADD_SCALAR);

        // Emerald has no set bonus (getSetBonusDescription returns null).
        // All emerald health scaling comes from the per-piece passive in applyGeneralPassives.
        // Clearing any stale modifier from older versions.
        applyAttributeModifier(player, Attribute.MAX_HEALTH, "trim_set_emerald_health",
                0, AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.ARMOR, "trim_set_diamond_armor",
                getSetBonusStrength(TrimMaterialType.DIAMOND, profile) * 2.0,
                AttributeModifier.Operation.ADD_NUMBER);

        applyAttributeModifier(player, Attribute.ARMOR_TOUGHNESS, "trim_set_diamond_toughness",
                getSetBonusStrength(TrimMaterialType.DIAMOND, profile) * 1.0,
                AttributeModifier.Operation.ADD_NUMBER);

        applyCustomSynergies(player, profile);
    }

    // ── Lore / Description ────────────────────────────────────────────────────

    public static List<String> describeAmplifications(
            Set<MutationCategory> categories, PlayerTrimProfile profile) {

        List<String> lines = new ArrayList<>();

        if (categories.contains(MutationCategory.DEBUFF)) {
            double effective = getEffectivePieceCount(TrimMaterialType.IRON, profile);
            if (effective > 0) {
                String resinSuffix = profile.resinCount > 0 && profile.ironCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Iron ×" + (int)effective + "] Debuff Mutations: +" + (int)(effective * 15) + "% duration" + resinSuffix);
            }
        }

        if (categories.contains(MutationCategory.BURST)) {
            double effective = getEffectivePieceCount(TrimMaterialType.COPPER, profile);
            if (effective > 0) {
                String resinSuffix = profile.resinCount > 0 && profile.copperCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Copper ×" + (int)effective + "] Burst Mutations: +" + (int)(effective * 12) + "% window" + resinSuffix);
            }
        }

        if (categories.contains(MutationCategory.INCREMENTAL)) {
            double effective = getEffectivePieceCount(TrimMaterialType.REDSTONE, profile);
            if (effective > 0) {
                String resinSuffix = profile.resinCount > 0 && profile.redstoneCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Redstone ×" + (int)effective + "] Incremental Mutations: -" + String.format("%.2f", effective * 5.25) + "% Hits Needed" + resinSuffix);
            }
        }

        if (categories.contains(MutationCategory.ENCHANT_SYNERGY)) {
            double effective = getEffectivePieceCount(TrimMaterialType.LAPIS, profile);
            if (effective > 0) {
                String resinSuffix = profile.resinCount > 0 && profile.lapisCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Lapis ×" + (int)effective + "] Synergy Mutations: +" + (int)(effective * 10) + "% power" + resinSuffix);
            }
        }

        if (profile.getCount(TrimMaterialType.EMERALD) > 0 || (profile.resinCount > 0 && profile.emeraldCount > 0)) {
            double effective = getEffectivePieceCount(TrimMaterialType.EMERALD, profile);
            if (categories.contains(MutationCategory.HEALTH_SCALE)) {
                String resinSuffix = profile.resinCount > 0 && profile.emeraldCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Emerald ×" + (int)effective + "] Health Scale Mutations: +" + (int)(effective * 5) + "% threshold" + resinSuffix);
            }
            if (categories.contains(MutationCategory.RECOVERY)) {
                String resinSuffix = profile.resinCount > 0 && profile.emeraldCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Emerald ×" + (int)effective + "] Recovery Mutations: +" + (int)(effective * 15) + "% power" + resinSuffix);
            }
        }

        if (categories.contains(MutationCategory.CONDITIONAL)) {
            double effective = getEffectivePieceCount(TrimMaterialType.DIAMOND, profile);
            if (effective > 0) {
                String resinSuffix = profile.resinCount > 0 && profile.diamondCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Diamond ×" + (int)effective + "] Conditional Mutations: +" + (int)(effective * 8) + "% window" + resinSuffix);
            }
        }

        if (categories.contains(MutationCategory.SUSTAINED)) {
            double effective = getEffectivePieceCount(TrimMaterialType.NETHERITE, profile);
            if (effective > 0) {
                String resinSuffix = profile.resinCount > 0 && profile.netheriteCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Netherite ×" + (int)effective + "] Sustained Mutations: +" + (int)(effective * 12) + "% tick damage" + resinSuffix);
            }
        }

        if (profile.getCount(TrimMaterialType.QUARTZ) > 0 || (profile.resinCount > 0 && profile.quartzCount > 0)) {
            double effective = getEffectivePieceCount(TrimMaterialType.QUARTZ, profile);
            boolean isBurst       = categories.contains(MutationCategory.BURST);
            boolean isConditional = categories.contains(MutationCategory.CONDITIONAL);
            if (isBurst && isConditional) {
                String resinSuffix = profile.resinCount > 0 && profile.quartzCount > 0 ? " §8(+§6Resin Replicated§8)" : "";
                lines.add("§b[Quartz ×" + (int)effective + "] Intersection: +" + (int)(effective * 8) + "% damage (BURST + CONDITIONAL)" + resinSuffix);
            }
            else if (isBurst || isConditional)
                lines.add("§8[Quartz ×" + (int)effective + "] Locked — needs both BURST + CONDITIONAL");
        }

        // Resin hybrid bonus is now reflected inline in the Iron (duration) and Netherite (tick_damage) lore lines above.

        return lines;
    }

    /**
     * Builds the adaptive set bonus lore block.
     *
     * Returns an EMPTY list unless the player has all 4 armor slots filled with
     * trimmed armor (any mix of materials). This matches the runtime requirement
     * in getSetBonusStrength — no full trimmed set = no set bonuses, no lore.
     *
     * When the full set requirement is met:
     *   Mixed pieces → §e◆ partial bonus lines per material (25%/50%/75%)
     *   Pure 4/4     → §6✦ pure set line
     */
    public static List<String> describeSetBonuses(PlayerTrimProfile profile) {
        List<String> lines = new ArrayList<>();

        // Fewer than 4 trimmed pieces → no set bonuses active, show nothing.
        if (profile == null || !hasFullTrimmedSet(profile)) return lines;

        lines.add("§6Adaptable Set Bonus:");

        for (TrimMaterialType type : TrimMaterialType.values()) {
            if (type == TrimMaterialType.RESIN) continue;
            double effective = getEffectivePieceCount(type, profile);
            if (effective <= 0) continue;

            String color = type.getColorCode();
            String desc  = type.getSetBonusDescription();
            if (desc == null) continue;

            if (hasPureSet(type, profile)) {
                // 4/4 — full pure set
                lines.add("§6✦ " + color + "[" + type.getDisplayName() + " Pure Set] §r§7" + desc);
            } else {
                // Partial — show piece count and percentage
                int pct = (int) Math.round((Math.min(effective, 4.0) / 4.0) * 100);
                lines.add("§e◆ " + color + "[" + type.getDisplayName() + " ×" + (int)Math.min(effective, 4.0) + "/4 — " + pct + "%] §r§7" + desc);
            }
        }

        lines.addAll(describeCustomSynergies(profile));
        return lines;
    }

    public static List<String> describeGeneralPassives(PlayerTrimProfile profile) {
        List<String> lines = new ArrayList<>();

        if (profile.ironCount > 0) {
            double effective = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.IRON, profile));
            double passive  = effective * 0.5;
            double setBonus = getSetBonusStrength(TrimMaterialType.IRON, profile) * 2.0;
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §f+" + String.format("%.2f", passive + setBonus) + " Armor §8(Iron)" + tag);
        }
        if (profile.copperCount > 0) {
            double effective = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.COPPER, profile));
            double passive  = effective * 7.5;
            double setBonus = getSetBonusStrength(TrimMaterialType.COPPER, profile) * 12;
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §6+" + String.format("%.1f", passive + setBonus) + "% Attack Speed §8(Copper)" + tag);
        }
        if (profile.redstoneCount > 0) {
            double effective = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.REDSTONE, profile));
            double passive = effective * 7.5;
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §c+" + String.format("%.1f", passive) + "% Speed §8(Redstone)" + tag);
        }
        if (profile.goldCount > 0) {
            double effective = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.GOLD, profile));
            double armorPassive = effective * 1.0;
            double absorptionPassive = effective * 2.5;
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §e+" + String.format("%.2f", armorPassive) + " Armor & +"
                    + String.format("%.1f", absorptionPassive) + "♥ Absorption (Regenerating) §8(Gold)" + tag);
        }
        if (profile.emeraldCount > 0) {
            double effective = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.EMERALD, profile));
            double passive  = effective * 2.5;
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §a+" + String.format("%.2f", passive) + " Hearts §8(Emerald)" + tag);
        }
        if (profile.diamondCount > 0) {
            double effective = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.DIAMOND, profile));
            double strength  = getSetBonusStrength(TrimMaterialType.DIAMOND, profile);
            double armor     = (effective * 0.5) + (strength * 2.0);
            double toughness = (effective * 0.5) + (strength * 1.0);
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §b+" + String.format("%.2f", armor) + " Armor & +"
                    + String.format("%.2f", toughness) + " Toughness §8(Diamond)" + tag);
        }
        if (profile.netheriteCount > 0) {
            double effective = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.NETHERITE, profile));
            double passive = effective * 1.0;
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §8+" + String.format("%.2f", passive) + " Toughness §8(Netherite)" + tag);
        }

        double amethystCount  = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.AMETHYST,  profile));
        double lapisCount     = Math.min(4.0, getEffectivePieceCount(TrimMaterialType.LAPIS,     profile));
        double trimMana = ((amethystCount * 12.0) + (lapisCount * 5.0))
                + (hasPureSet(TrimMaterialType.AMETHYST, profile) ? 50.0 : 0.0);
        if (trimMana > 0) {
            String tag = (profile.resinCount > 0) ? " §6[Resin Replicated]§r" : "";
            lines.add(" §7- §9+" + String.format("%.0f", trimMana) + " Max Mana §8(Lapis/Amethyst)" + tag);
        }

        if (profile.resinCount > 0)
            lines.add(" §7- §6Resin x" + profile.resinCount + ": §r§7Replicates other equipped trims.");

        return lines;
    }

    /**
     * Per-piece trim lore: trim name, passive description, mutation description.
     *
     * The static "Full Set:" line has been removed. The adaptive set bonus
     * block is generated separately in MutationManager.updateLore() using the
     * viewer's live profile so it always reflects what they currently wear.
     */
    public static List<String> getTrimPieceLore(ItemStack item) {
        List<String> lines = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return lines;

        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof ArmorMeta armorMeta)) return lines;

        ArmorTrim trim = armorMeta.getTrim();
        if (trim == null) return lines;

        org.bukkit.NamespacedKey key = org.bukkit.Registry.TRIM_MATERIAL.getKey(trim.getMaterial());
        String keyStr = key != null ? key.toString() : "";

        java.util.Optional<TrimMaterialType> typeOpt = TrimMaterialType.fromBukkitKey(keyStr);
        if (typeOpt.isEmpty()) return lines;

        TrimMaterialType type = typeOpt.get();
        String color = type.getColorCode();
        String name  = type.getDisplayName();

        lines.add(color + "[" + name + " Trim]§r");

        String passive = type.getPassiveDescription();
        if (passive != null)
            lines.add(" §7Passive: " + color + passive + "§r");

        String mutation = type.getMutationDescription();
        if (mutation != null)
            lines.add(" §7Mutation: " + color + mutation + "§r");

        // No static "Full Set:" line — the adaptive block handles this.
        return lines;
    }

    /** Called from MutationManager.updateLore() when a viewer is available. */
    public static List<String> getTrimSetBonusLoreForViewer(ItemStack item, PlayerTrimProfile profile) {
        return describeSetBonuses(profile);
    }

    // ── Attribute Helper ──────────────────────────────────────────────────────

    public static void applyAttributeModifier(
            Player player, Attribute attribute, String keyName,
            double value, AttributeModifier.Operation operation) {

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        NamespacedKey key = new NamespacedKey("corovaitems", keyName);

        // OPTIMIZATION: only update if the modifier doesn't already exist with the same value/operation.
        // This avoids redundant attribute refreshes (and UI flicker) during frequent profile updates.
        for (AttributeModifier existing : instance.getModifiers()) {
            if (existing.getKey().equals(key)) {
                if (existing.getAmount() == value && existing.getOperation() == operation) {
                    return;
                }
                break;
            }
        }

        instance.removeModifier(key);

        if (value > 0) {
            instance.addModifier(new AttributeModifier(key, value, operation, EquipmentSlotGroup.ANY));
        }
    }
}