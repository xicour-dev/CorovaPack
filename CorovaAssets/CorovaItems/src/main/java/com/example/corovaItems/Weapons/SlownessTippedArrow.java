package com.example.corovaItems.Weapons;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collections;

/**
 * CorovaItem representing a Slowness II tipped arrow.
 * Arrow shows fake duration in lore, applies Slowness II for 5 seconds on hit.
 */
public class SlownessTippedArrow extends CorovaItems implements Listener {

    private static Plugin plugin;
    private static NamespacedKey key;

    /**
     * Public no-arg constructor for CorovaItems reflection-based registration.
     */
    public SlownessTippedArrow() {
        super(
                "§5Slowness Arrow II",
                Material.TIPPED_ARROW,
                Collections.singletonList("§7Slowness II (0:05)"), // fake lore
                Collections.emptyMap(),
                "slowness_arrow_ii"
        );

        // Register item
        ItemManager.getInstance().registerItem(this);

        // Register listener if plugin is set
        if (plugin != null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    /**
     * Must be called once from main plugin class to provide plugin reference.
     * IMPORTANT: This must run BEFORE this item is registered/constructed,
     * otherwise `key` will still be null when the event listeners fire.
     */
    public static void initialize(Plugin pluginInstance) {
        plugin = pluginInstance;
        key = new NamespacedKey(plugin, "slowness_arrow_ii");
    }

    // -------------------- ITEMSTACK CREATION --------------------
    @Override
    public ItemStack toItemStack() {
        ItemStack arrow = super.toItemStack();
        if (arrow.getItemMeta() instanceof PotionMeta meta) {
            // Cosmetic arrow: no actual potion effect
            meta.clearCustomEffects();

            // Set arrow color
            meta.setColor(Color.fromRGB(85, 85, 255));

            // Fake lore
            meta.setLore(Arrays.asList("§7Slowness II (0:05)"));

            // Mark ItemStack for identification
            if (key != null) {
                meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            }

            arrow.setItemMeta(meta);
        }
        return arrow;
    }

    // -------------------- TAG ARROW ON LAUNCH --------------------
    @EventHandler
    public void onArrowLaunch(ProjectileLaunchEvent event) {
        // Guard: if initialize() hasn't run yet (or somehow failed), key will be
        // null and PersistentDataContainer#has(null, ...) throws IllegalArgumentException.
        // Bail out safely instead of crashing on every projectile launch.
        if (key == null) return;

        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) return;

        EntityEquipment equipment = shooter.getEquipment();
        if (equipment == null) return;

        ItemStack stack = equipment.getItemInMainHand();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;

        if (meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            arrow.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        }
    }

    // -------------------- APPLY EFFECT ON HIT --------------------
    @EventHandler
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (key == null) return;
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        if (arrow.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            // Apply Slowness II for 5 seconds (100 ticks)
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }
    }
}