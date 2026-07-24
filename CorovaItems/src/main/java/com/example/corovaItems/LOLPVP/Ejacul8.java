package com.example.corovaItems.LOLPVP;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ejacul8 – The Poison Sword
 * Integrated with the CorovaItems system.
 * Modernized from legacy LOLPVP implementation.
 */
public class Ejacul8 extends CorovaItems implements Listener {

    public Ejacul8() {
        super(
                ChatColor.AQUA + "Ejacul8",           // Display name
                Material.DIAMOND_SWORD,               // Material
                lore(),                               // Lore
                enchantments(),                       // Enchantments
                "ejacul8"                             // Internal ID
        );

        // Automatically register item with the ItemManager
        ItemManager.getInstance().registerItem(this);
    }

    /** Handles applying poison on hit */
    // inside your event:
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // The damager can be any LivingEntity (player or mob)
        if (!(event.getDamager() instanceof LivingEntity damager)) return;

        ItemStack weapon = null;

        // Try to get main-hand weapon for mobs or players
        if (damager instanceof Player player) {
            weapon = player.getInventory().getItemInMainHand();
            if (target instanceof Player && CorovaGuard.getInstance().isPlayerInSafeZone((Player) target)) {
                CorovaGuard.sendSafeZoneMessage(player);
                return;
            }
        } else {
            try {
                weapon = ((LivingEntity) damager).getEquipment().getItemInMainHand();
            } catch (Exception ignored) {}
        }

        if (target instanceof Player && CorovaGuard.getInstance().isPlayerInSafeZone((Player) target)) {
            return;
        }


        if (weapon == null) return;
        if (!ItemManager.getInstance().isCorovaItem(weapon, this)) return;

        // Apply poison
        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 8 * 20, 6));

        // Particle effect
        Particle.DustOptions greenDust = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.5f);
        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().add(0, 1, 0),
                40,
                0.4, 0.6, 0.4,
                0,
                greenDust
        );

        // Play splash sound
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_SPLASH_POTION_BREAK,
                1f,
                1f
        );
    }


    /** Lore setup */
    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Poison VIII");
        lore.add(ChatColor.DARK_GRAY + "The poison sword!");
        return lore;
    }

    /** Enchantments setup */
    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        enchants.put(Enchantment.SHARPNESS, 8);
        enchants.put(Enchantment.BANE_OF_ARTHROPODS, 8);
        enchants.put(Enchantment.SMITE, 8);
        enchants.put(Enchantment.FIRE_ASPECT, 8);
        enchants.put(Enchantment.LOOTING, 8);
        enchants.put(Enchantment.UNBREAKING, 8);
        return enchants;
    }
}
