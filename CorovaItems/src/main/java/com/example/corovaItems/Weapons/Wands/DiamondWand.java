package com.example.corovaItems.Weapons.Wands;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.corova.chronicles.generated.CorovaCustomModelData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DiamondWand extends CorovaItems {

    public DiamondWand() {
        super(
                "Diamond Wand",
                new Material[]{Material.matchMaterial("DIAMOND_SPEAR")},
                lore(),
                Collections.emptyMap(),
                "diamondwand",
                CorovaCustomModelData.WEAPONS_DIAMOND_WAND,
                attributes()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                "§7A powerful diamond wand.",
                "§eScales with Power, Smite, and Bane of Arthropods."
        );
    }

    private static Map<Attribute, AttributeModifier> attributes() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(DiamondWand.class);
        return Map.of(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "diamondwand_attack_speed"), -3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND),
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "diamondwand_attack_damage"), 5, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
    }
}