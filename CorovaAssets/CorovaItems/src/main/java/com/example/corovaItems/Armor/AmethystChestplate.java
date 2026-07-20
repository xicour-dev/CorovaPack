package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Amethyst Chestplate
 *
 * Armor:      7    (Iron: 6   | Diamond: 8)
 * Toughness:  1    (Iron: 0   | Diamond: 2)
 * Durability: 400  (Iron: 240 | Diamond: 1561)
 * Mana:       25
 *
 * Leather chestplate dyed amethyst purple, RIB trim (amethyst material)
 * — structured vertical lines that fill the leather chest panel.
 */
public class AmethystChestplate extends CorovaItems {

    private static final Color AMETHYST_COLOR = Color.fromRGB(148, 84, 181);
    private static final int DURABILITY = 400;

    public AmethystChestplate() {
        super(
                "Amethyst Chestplate",
                new Material[]{Material.LEATHER_CHESTPLATE},
                List.of(
                        "§5Crystallized from the depths of the earth",
                        "",
                        "§7Mana Boost: §b+25 Max Mana"
                ),
                Collections.<Enchantment, Integer>emptyMap(),
                "amethystchestplate",
                null,
                buildAttributes()
        );
    }

    private static Map<Attribute, AttributeModifier> buildAttributes() {
        Map<Attribute, AttributeModifier> attrs = new HashMap<>();
        attrs.put(Attribute.ARMOR,
                new AttributeModifier(
                        new NamespacedKey("corova", "amethystchestplate_armor"),
                        7.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.CHEST));
        attrs.put(Attribute.ARMOR_TOUGHNESS,
                new AttributeModifier(
                        new NamespacedKey("corova", "amethystchestplate_toughness"),
                        1.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.CHEST));
        return attrs;
    }

    @Override
    protected void postProcess(ItemStack item) {
        if (!(item.getItemMeta() instanceof LeatherArmorMeta leatherMeta)) return;
        leatherMeta.setColor(AMETHYST_COLOR);
        if (leatherMeta instanceof Damageable damageable) {
            damageable.setMaxDamage(DURABILITY);
        }
        if (leatherMeta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(TrimMaterial.AMETHYST, TrimPattern.RIB));
        }
        item.setItemMeta(leatherMeta);
    }
}