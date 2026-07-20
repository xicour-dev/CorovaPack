package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.Particle;

import java.util.*;

public class AngelWings extends CorovaItems implements Listener {

    private final Map<UUID, Location> locs = new HashMap<>();
    private final Set<UUID> hitPlayers = new HashSet<>();
    private final Set<UUID> flyingPlayers = new HashSet<>();

    public AngelWings() {
        super(
                ChatColor.AQUA + "Angel Wings",
                Material.DIAMOND_CHESTPLATE,
                lore(),
                enchantments(),
                "angelwings"
        );

        // Register this item with the ItemManager
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Flight I",
                ChatColor.DARK_GRAY + "Shift to fly!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.PROTECTION, 5);
        return map;
    }

    private boolean checkHeight(Player player, Location loc) {
        Location stored = locs.get(player.getUniqueId());
        return stored != null && stored.getY() + 20 > loc.getY();
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack chestplate = player.getInventory().getChestplate();

        if (!ItemManager.getInstance().isCorovaItem(chestplate, this)) return;

        if (player.getLocation().getBlock().getType() != Material.AIR && locs.containsKey(player.getUniqueId())) {
            locs.remove(player.getUniqueId());
        }

        locs.putIfAbsent(player.getUniqueId(), player.getLocation());

        if (checkHeight(player, player.getLocation()) && !hitPlayers.contains(player.getUniqueId())) {
            player.setVelocity(player.getEyeLocation().getDirection().multiply(0.5).add(new Vector(0, 0.5, 0)));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);
            player.getWorld().spawnParticle(
                    Particle.FIREWORK,      // Particle type
                    player.getLocation(),           // Location
                    30,                             // count
                    0.25, 0.25, 0.25,              // offset x, y, z
                    0.25                            // extra (speed)
            );

            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }

            flyingPlayers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        ItemStack chestplate = player.getInventory().getChestplate();

        if (ItemManager.getInstance().isCorovaItem(chestplate, this) || flyingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onArrowHitPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (!ItemManager.getInstance().isCorovaItem(chestplate, this)) return;

        if (player.getLocation().subtract(new Vector(0, 1, 0)).getBlock().getType() == Material.AIR) {
            hitPlayers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!flyingPlayers.contains(uuid)) return;

        ItemStack chestplate = player.getInventory().getChestplate();
        if (!ItemManager.getInstance().isCorovaItem(chestplate, this)) {
            player.setAllowFlight(false);
            flyingPlayers.remove(uuid);
            hitPlayers.remove(uuid);
            return;
        }

        if (player.getLocation().getBlock().getType() != Material.AIR) {
            player.setAllowFlight(false);
            flyingPlayers.remove(uuid);
            hitPlayers.remove(uuid);
        }
    }
}
