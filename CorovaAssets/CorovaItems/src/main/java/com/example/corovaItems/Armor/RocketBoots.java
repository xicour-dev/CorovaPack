package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public class RocketBoots extends CorovaItems implements Listener {

    private final Set<UUID> inRocketJump = new HashSet<>();
    private final Map<UUID, Integer> sneakTaps = new HashMap<>();

    public RocketBoots() {
        super(
                ChatColor.AQUA + "Rocket Boots",
                Material.DIAMOND_BOOTS,
                lore(),
                enchantments(),
                "rocketboots"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Hover Jump I",
                ChatColor.DARK_GRAY + "Double tap shift to hover jump!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.PROTECTION, 5);
        map.put(Enchantment.BLAST_PROTECTION, 5);
        map.put(Enchantment.FEATHER_FALLING, 5);
        map.put(Enchantment.FIRE_PROTECTION, 5);
        map.put(Enchantment.PROJECTILE_PROTECTION, 5);
        return map;
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        ItemStack boots = player.getInventory().getBoots();

        if (event.isSneaking() && ItemManager.getInstance().isCorovaItem(boots, this)) {
            sneakTaps.put(playerId, sneakTaps.getOrDefault(playerId, 0) + 1);

            if (sneakTaps.get(playerId) >= 2) {
                sneakTaps.remove(playerId);
                player.setVelocity(new Vector(player.getVelocity().getX(), 2.2, player.getVelocity().getZ()));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0F, 1.0F);
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 20, 0.2, 0.2, 0.2, 0.1);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 20, 0.2, 0.2, 0.2, 0.1);
                inRocketJump.add(playerId);
            }

            Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("CorovaCore"), () -> sneakTaps.remove(playerId), 10L);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            UUID playerId = player.getUniqueId();

            if (inRocketJump.contains(playerId) && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                inRocketJump.remove(playerId);
            }
        }
    }
}
