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

public class NetheriteWand extends CorovaItems {

    public NetheriteWand() {
        super(
                "Netherite Wand",
                new Material[]{Material.matchMaterial("NETHERITE_SPEAR")},
                lore(),
                Collections.emptyMap(),
                "netheritewand",
                CorovaCustomModelData.WEAPONS_NETHERITE_WAND,
                attributes()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                "§7A legendary netherite wand.",
                "§eScales with Power, Smite, and Bane of Arthropods."
        );
    }

    private static Map<Attribute, AttributeModifier> attributes() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(NetheriteWand.class);
        return Map.of(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "netheritewand_attack_speed"), -3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND),
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "netheritewand_attack_damage"), 6, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
    }
}