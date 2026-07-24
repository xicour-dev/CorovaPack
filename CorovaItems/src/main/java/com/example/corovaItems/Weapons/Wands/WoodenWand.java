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

public class WoodenWand extends CorovaItems {

    public WoodenWand() {
        super(
                "Wooden Wand",
                new Material[]{Material.matchMaterial("WOODEN_SPEAR")},
                lore(),
                Collections.emptyMap(),
                "woodenwand",
                CorovaCustomModelData.WEAPONS_WOODEN_WAND,
                attributes()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                "§7A basic wand for beginners.",
                "§eScales with Power, Smite, and Bane of Arthropods."
        );
    }

    private static Map<Attribute, AttributeModifier> attributes() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(WoodenWand.class);
        return Map.of(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "woodenwand_attack_speed"), -3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND),
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "woodenwand_attack_damage"), 3, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
    }
}