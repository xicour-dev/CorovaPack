package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class KnockbackProtectionBook extends EnchantmentBook implements Listener {

    public KnockbackProtectionBook() {
        this(1);
    }

    public KnockbackProtectionBook(int level) {
        super(
                "Book of Knockback Protection",
                CorovaEnchantments.KNOCKBACK_PROTECTION_ID,
                level,
                "book_knockback_protection_" + level,
                allowedMaterialsStatic()
        );
    }

    private static Set<Material> allowedMaterialsStatic() {
        return Set.of(
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
                Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
                Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
                Material.TURTLE_HELMET, Material.ELYTRA
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        // Only one instance should handle this to avoid multiple reductions
        if (getLevel() != 1) return;

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        int totalLevel = 0;
        if (victim.getEquipment() != null) {
            for (ItemStack armor : victim.getEquipment().getArmorContents()) {
                if (armor != null && CorovaEnchantments.hasEnchant(armor, CorovaEnchantments.KNOCKBACK_PROTECTION_ID)) {
                    totalLevel += CorovaEnchantments.getEnchantLevel(armor, CorovaEnchantments.KNOCKBACK_PROTECTION_ID);
                }
            }
        }

        if (totalLevel > 0) {
            // Max level is 7 * 4 = 28.
            // Percentage reduction: (totalLevel / 28) * 0.6
            double reduction = Math.min(0.6, (totalLevel / 28.0) * 0.6);
            double multiplier = 1.0 - reduction;

            event.setKnockback(event.getKnockback().multiply(multiplier));
        }
    }
}
