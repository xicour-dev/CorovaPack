package com.example.corovaItems.Weapons.Scythes;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.corova.chronicles.generated.CorovaCustomModelData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class StoneScythe extends CorovaItems {

    public StoneScythe() {
        super(
                "Stone Scythe",
                new Material[]{Material.STONE_SWORD},
                lore(),
                Collections.emptyMap(),
                "stonescythe",
                CorovaCustomModelData.WEAPONS_STONE_SCYTHE,
                attributes()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return Collections.singletonList("A heavy blade designed for reaping.");
    }

    private static Map<Attribute, AttributeModifier> attributes() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(StoneScythe.class);
        // Design rule: Attack Speed is fixed at double a sword's base (1.6 x 2 = 3.2)
        // Attack Damage target (matching the old hoe version): 4.5
        // IMPORTANT: custom AttributeModifiers on the item REPLACE the sword's built-in
        // vanilla modifiers - they do NOT stack with them. Only the player's flat generic
        // base remains underneath (Attack Speed base 4.0, Attack Damage base 1.0).
        // Modifier = target total - generic base.
        return Map.of(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "stonescythe_attack_speed"), -0.8, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND),
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "stonescythe_attack_damage"), 3.5, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND)
        );
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            NamespacedKey key = new NamespacedKey(JavaPlugin.getProvidingPlugin(StoneScythe.class), "is_scythe");
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }
}