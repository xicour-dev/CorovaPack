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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BattleHardened implements Mutation {

    private final MutationManager mutationManager;

    public BattleHardened(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEFENSIVE);
    }

    @Override
    public String getColor() {
        return "#808080";
    }

    @Override
    public String getName() {
        return "Battle Hardened";
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
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
            desc.add(ChatColor.GRAY + "Adds +0.5 Armor, +0.5 Toughness, and +350 Durability.");
        } else if (level == 2) {
            desc.add(ChatColor.GRAY + "Adds +1 Armor, +1 Toughness, and +700 Durability.");
        }
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.BATTLE_HARDENED;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @Override
    public int getDurabilityBonus(int level) {
        return level * 350;
    }

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        if (level >= 1) {
            ensureBaseAttributes(item, meta);

            double bonus = level * 0.5;
            // Note: Since this is applied to the item meta as a static attribute,
            // we cannot easily apply player-specific trim amplification here
            // because the item might be viewed by someone without trims.
            // Phase 1 instructions say "If the damager is a Player, fetch their profile and apply amplification".
            // For attribute-based mutations, we don't have a 'damager' at the time of application.

            AttributeModifier armorMod = new AttributeModifier(getArmorKey(item), bonus, AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ARMOR);
            AttributeModifier toughnessMod = new AttributeModifier(getToughnessKey(item), bonus, AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ARMOR);

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

    @Override
    public void removeAttributes(ItemStack item, ItemMeta meta) {
        removeAttributeByKey(meta, Attribute.ARMOR, getArmorKey(item));
        removeAttributeByKey(meta, Attribute.ARMOR_TOUGHNESS, getToughnessKey(item));
    }

    private NamespacedKey getArmorKey(ItemStack item) {
        return new NamespacedKey("corovaitems", "battle_hardened_" + item.getType().name().toLowerCase() + "_armor");
    }

    private NamespacedKey getToughnessKey(ItemStack item) {
        return new NamespacedKey("corovaitems", "battle_hardened_" + item.getType().name().toLowerCase() + "_toughness");
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
}
