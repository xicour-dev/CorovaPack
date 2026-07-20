package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

public class CriticalBook extends EnchantmentBook implements Listener {

    // Sentinel level used for the single registered listener instance
    private static final int LISTENER_LEVEL = 1;

    public CriticalBook() {
        this(LISTENER_LEVEL);
    }

    public CriticalBook(int level) {
        super(
                "Book of Critical",
                CorovaEnchantments.CRITICAL_ID,
                level,
                "book_critical_" + level,
                allowedMaterialsStatic()
        );
    }

    private static Set<Material> allowedMaterialsStatic() {
        return Set.of(
                Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE,
                Material.TRIDENT, Material.MACE
        );
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.CRITICAL_ID)) return;

        if (event.isCritical()) {
            // Level is read from the weapon at runtime — not from this instance.
            int level = CorovaEnchantments.getEnchantLevel(weapon, CorovaEnchantments.CRITICAL_ID);

            // BASE at NORMAL priority already has vanilla's 1.5× crit applied.
            // Multiplying that value would compound the vanilla bonus, making each
            // level far stronger than intended. Instead, recover the raw weapon base
            // and add a flat bonus — the same way Sharpness works (ADD_NUMBER, not scaling).
            // Per-level bonus: +3% of weapon base per level, crit-only.
            // L1=+3%, L2=+6%, L3=+9%, L4=+12%, L5=+15%, L6=+18%
            double weaponBase = event.getDamage(DamageModifier.BASE) / 1.5;
            double flatBonus  = level * (weaponBase * 0.03); // +3% of weapon base per level
            event.setDamage(DamageModifier.BASE, event.getDamage(DamageModifier.BASE) + flatBonus);
        }
    }
}