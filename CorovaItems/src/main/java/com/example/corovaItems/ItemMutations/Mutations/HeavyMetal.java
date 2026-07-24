package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeavyMetal implements Mutation {

    private final MutationManager mutationManager;

    public HeavyMetal(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEFENSIVE);
    }

    @Override
    public String getColor() {
        return "#808080";
    }

    public String getName() {
        return "Heavy Metal";
    }

    public int getMaxLevel() {
        return 2;
    }

    public List<String> getLore(int level) {
        List<String> lore = new ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new ArrayList<>();
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Adds +1.0 Armor and +0.5 Armor Toughness.");
        } else {
            desc.add(ChatColor.GRAY + "Adds +1.5 Armor and +1.0 Armor Toughness.");
        }
        return desc;
    }

    public MutationType getType() {
        return MutationType.HEAVY_METAL;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }

    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        if (level >= 1) {
            ensureBaseAttributes(item, meta);

            double armorBonus = level == 1 ? 1.0 : 1.5;
            double toughnessBonus = level == 1 ? 0.5 : 1.0;

            AttributeModifier armorMod = new AttributeModifier(getArmorKey(item), armorBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR);
            AttributeModifier toughnessMod = new AttributeModifier(getToughnessKey(item), toughnessBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR);

            meta.addAttributeModifier(Attribute.ARMOR, armorMod);
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, toughnessMod);
        }
    }

    private void ensureBaseAttributes(ItemStack item, ItemMeta meta) {
        if (meta.hasAttributeModifiers()) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (Map.Entry<Attribute, AttributeModifier> entry : item.getType().getDefaultAttributeModifiers(slot).entries()) {
                meta.addAttributeModifier(entry.getKey(), entry.getValue());
            }
        }
    }

    public void removeAttributes(ItemStack item, ItemMeta meta) {
        removeAttributeByKey(meta, Attribute.ARMOR, getArmorKey(item));
        removeAttributeByKey(meta, Attribute.ARMOR_TOUGHNESS, getToughnessKey(item));
    }

    private NamespacedKey getArmorKey(ItemStack item) {
        return new NamespacedKey("corovaitems", "heavy_metal_" + item.getType().name().toLowerCase() + "_armor");
    }

    private NamespacedKey getToughnessKey(ItemStack item) {
        return new NamespacedKey("corovaitems", "heavy_metal_" + item.getType().name().toLowerCase() + "_toughness");
    }

    private void removeAttributeByKey(ItemMeta meta, Attribute attribute, NamespacedKey key) {
        if (meta.hasAttributeModifiers()) {
            java.util.Collection<AttributeModifier> modifiers = meta.getAttributeModifiers(attribute);
            if (modifiers != null) {
                for (AttributeModifier modifier : new java.util.ArrayList<>(modifiers)) {
                    if (modifier.getKey().equals(key)) {
                        meta.removeAttributeModifier(attribute, modifier);
                    }
                }
            }
        }
    }

    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.HEAVY_METAL_WEIGHT;
    }
}
