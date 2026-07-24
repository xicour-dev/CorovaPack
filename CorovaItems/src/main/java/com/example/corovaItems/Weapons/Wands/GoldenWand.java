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

public class GoldenWand extends CorovaItems {

    public GoldenWand() {
        super(
                "Golden Wand",
                new Material[]{Material.matchMaterial("GOLDEN_SPEAR")},
                lore(),
                Collections.emptyMap(),
                "goldenwand",
                CorovaCustomModelData.WEAPONS_GOLDEN_WAND,
                attributes()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                "§7A luxurious golden wand.",
                "§eScales with Power, Smite, and Bane of Arthropods."
        );
    }

    private static Map<Attribute, AttributeModifier> attributes() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(GoldenWand.class);
        return Map.of(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "goldwand_attack_speed"), -3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND),
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "goldwand_attack_damage"), 3.5, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
    }
}