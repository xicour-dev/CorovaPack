package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ExtraCustomEnchant implements Mutation {

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.ENCHANT_SYNERGY);
    }

    @Override
    public String getColor() {
        return "#DA70D6";
    }

    @Override
    public String getName() {
        return "Extra Custom Enchant Slot";
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return MutationUtils.isSword(item)
                || item.getType().name().endsWith("_AXE")
                || item.getType().name().endsWith("_HOE")
                || MutationUtils.isScythe(item)
                || MutationUtils.isBow(item);
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
        desc.add(ChatColor.GRAY + "Allows for an extra custom enchantment slot on this item.");
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.EXTRA_CUSTOM_ENCHANT_SLOT;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.EXTREMELY_RARE_WEIGHT;
    }
}
