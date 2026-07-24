package com.example.corovaItems.ArmorTrims;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import java.util.EnumMap;
import java.util.Map;

public class PlayerTrimProfile {

    public final int ironCount, copperCount, redstoneCount, goldCount;
    public final int lapisCount, emeraldCount, diamondCount, netheriteCount;
    public final int quartzCount, amethystCount, resinCount;

    // Private constructor — always build through from()
    private PlayerTrimProfile(Map<TrimMaterialType, Integer> counts) {
        this.ironCount      = counts.getOrDefault(TrimMaterialType.IRON,      0);
        this.copperCount    = counts.getOrDefault(TrimMaterialType.COPPER,    0);
        this.redstoneCount  = counts.getOrDefault(TrimMaterialType.REDSTONE,  0);
        this.goldCount      = counts.getOrDefault(TrimMaterialType.GOLD,      0);
        this.lapisCount     = counts.getOrDefault(TrimMaterialType.LAPIS,     0);
        this.emeraldCount   = counts.getOrDefault(TrimMaterialType.EMERALD,   0);
        this.diamondCount   = counts.getOrDefault(TrimMaterialType.DIAMOND,   0);
        this.netheriteCount = counts.getOrDefault(TrimMaterialType.NETHERITE, 0);
        this.quartzCount    = counts.getOrDefault(TrimMaterialType.QUARTZ,    0);
        this.amethystCount  = counts.getOrDefault(TrimMaterialType.AMETHYST,  0);
        this.resinCount     = counts.getOrDefault(TrimMaterialType.RESIN,     0);
    }

    public static PlayerTrimProfile from(Player player) {
        return fromEntity(player);
    }

    public static PlayerTrimProfile fromEntity(org.bukkit.entity.LivingEntity entity) {
        Map<TrimMaterialType, Integer> counts = new EnumMap<>(TrimMaterialType.class);

        if (entity.getEquipment() == null) return empty();
        ItemStack[] armor = entity.getEquipment().getArmorContents();

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType() == Material.AIR) continue;
            if (!piece.hasItemMeta()) continue;

            ItemMeta meta = piece.getItemMeta();
            if (!(meta instanceof ArmorMeta armorMeta)) continue;

            ArmorTrim trim = armorMeta.getTrim();
            if (trim == null) continue;

            // TrimMaterial key comes back as NamespacedKey, e.g. "minecraft:iron"
            org.bukkit.NamespacedKey namespacedKey = org.bukkit.Registry.TRIM_MATERIAL.getKey(trim.getMaterial());
            String key = namespacedKey != null ? namespacedKey.toString() : "";
            TrimMaterialType.fromBukkitKey(key).ifPresent(type ->
                    counts.merge(type, 1, Integer::sum)
            );
        }

        return new PlayerTrimProfile(counts);
    }

    // Convenience: get count by material type, useful in TrimCalculator
    public int getCount(TrimMaterialType type) {
        return switch (type) {
            case IRON      -> ironCount;
            case COPPER    -> copperCount;
            case REDSTONE  -> redstoneCount;
            case GOLD      -> goldCount;
            case LAPIS     -> lapisCount;
            case EMERALD   -> emeraldCount;
            case DIAMOND   -> diamondCount;
            case NETHERITE -> netheriteCount;
            case QUARTZ    -> quartzCount;
            case AMETHYST  -> amethystCount;
            case RESIN     -> resinCount;
        };
    }

    public int getTotalTrimCount() {
        return ironCount + copperCount + redstoneCount + goldCount + lapisCount
                + emeraldCount + diamondCount + netheriteCount + quartzCount
                + amethystCount + resinCount;
    }

    // Empty profile for players with no trims (avoids null checks everywhere)
    public static PlayerTrimProfile empty() {
        return new PlayerTrimProfile(new EnumMap<>(TrimMaterialType.class));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerTrimProfile that = (PlayerTrimProfile) o;
        return ironCount == that.ironCount && copperCount == that.copperCount && redstoneCount == that.redstoneCount && goldCount == that.goldCount && lapisCount == that.lapisCount && emeraldCount == that.emeraldCount && diamondCount == that.diamondCount && netheriteCount == that.netheriteCount && quartzCount == that.quartzCount && amethystCount == that.amethystCount && resinCount == that.resinCount;
    }

    @Override
    public int hashCode() {
        java.util.Objects.hash(ironCount, copperCount, redstoneCount, goldCount, lapisCount, emeraldCount, diamondCount, netheriteCount, quartzCount, amethystCount, resinCount);
        return java.util.Objects.hash(ironCount, copperCount, redstoneCount, goldCount, lapisCount, emeraldCount, diamondCount, netheriteCount, quartzCount, amethystCount, resinCount);
    }
}