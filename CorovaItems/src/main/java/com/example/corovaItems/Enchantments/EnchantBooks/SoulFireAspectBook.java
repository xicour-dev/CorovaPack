package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SoulFireAspectBook extends EnchantmentBook implements Listener {

    private static final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();

    public SoulFireAspectBook() {
        this(1);
    }

    public SoulFireAspectBook(int level) {
        super(
                "Book of Soul Fire Aspect",
                CorovaEnchantments.SOUL_FIRE_ASPECT_ID,
                level,
                "book_soul_fire_aspect_" + level,
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (getLevel() != 1) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = attacker.getEquipment() != null ? attacker.getEquipment().getItemInMainHand() : null;
        if (weapon == null || !CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.SOUL_FIRE_ASPECT_ID)) return;

        int level = CorovaEnchantments.getEnchantLevel(weapon, CorovaEnchantments.SOUL_FIRE_ASPECT_ID);
        applySoulFire(victim, level);
    }

    private void applySoulFire(LivingEntity victim, int level) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        double damagePerSecond = switch (level) {
            case 1 -> 2.0;
            case 2 -> 3.0;
            case 3 -> 4.0;
            default -> 2.0;
        };
        int durationTicks = 80 * level; // 4s for lvl 1, 8s for lvl 2, 12s for lvl 3

        // Cancel any existing soul fire task on this victim (like real fire — no stacking)
        UUID victimId = victim.getUniqueId();
        BukkitRunnable existing = activeTasks.get(victimId);
        if (existing != null) {
            existing.cancel();
        }

        BukkitRunnable task = new BukkitRunnable() {
            int elapsed = 0;
            @Override
            public void run() {
                if (elapsed >= durationTicks || !victim.isValid()) {
                    activeTasks.remove(victimId);
                    cancel();
                    return;
                }

                elapsed += 2; // Increment first so elapsed=0 is never checked (no instant hit)

                // Apply damage every second (20 ticks)
                if (elapsed % 20 == 0) {
                    victim.damage(damagePerSecond);
                }

                // Spawn particles
                Location loc = victim.getLocation().add(0, 1, 0);
                World world = victim.getWorld();
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 3, 0.3, 0.5, 0.3, 0.02);
                world.spawnParticle(Particle.SOUL, loc, 1, 0.2, 0.5, 0.2, 0.02);
                world.spawnParticle(Particle.ENTITY_EFFECT, loc, 2, 0.3, 0.5, 0.3, 0.0, Color.BLUE.asRGB());
            }
        };

        activeTasks.put(victimId, task);
        task.runTaskTimer(plugin, 0L, 2L);
    }
}