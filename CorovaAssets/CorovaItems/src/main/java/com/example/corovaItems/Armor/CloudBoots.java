package com.example.corovaItems.Armor;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class CloudBoots extends CorovaItems implements Listener {

    // Tracks players whose flight was granted specifically by CloudBoots
    private final Set<UUID> doubleJumpers = new HashSet<>();

    public CloudBoots() {
        super(
                ChatColor.AQUA + "Cloud Boots",
                Material.DIAMOND_BOOTS,
                lore(),
                enchantments(),
                "cloudboots"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Double Jump I",
                ChatColor.DARK_GRAY + "Special boots that allow you to double jump!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.PROTECTION, 5);
        map.put(Enchantment.FIRE_PROTECTION, 5);
        map.put(Enchantment.BLAST_PROTECTION, 5);
        map.put(Enchantment.PROJECTILE_PROTECTION, 5);
        map.put(Enchantment.FEATHER_FALLING, 5);
        return map;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ItemStack boots = player.getInventory().getBoots();

        boolean isSurvivalMode = player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR;

        if (!isSurvivalMode) return;

        boolean wearingBoots = boots != null && ItemManager.getInstance().isCorovaItem(boots, this);

        if (wearingBoots) {
            // Grant flight (for double jump) when the player is on the ground
            if (player.getLocation().subtract(0, 1, 0).getBlock().getType() != Material.AIR) {
                player.setAllowFlight(true);
                doubleJumpers.add(uuid);
            }
        } else if (doubleJumpers.contains(uuid)) {
            // Only revoke flight if CloudBoots was the one that granted it
            player.setAllowFlight(false);
            doubleJumpers.remove(uuid);
        }
        // If the player doesn't have CloudBoots and was never tracked by doubleJumpers,
        // we do NOT touch their flight state — this preserves /fly granted by ops/permissions.
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ItemStack boots = player.getInventory().getBoots();

        if (player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR
                && boots != null
                && ItemManager.getInstance().isCorovaItem(boots, this)) {
            // Intercept the flight toggle to perform the double jump instead
            event.setCancelled(true);
            player.setFlying(false);
            player.setAllowFlight(false);
            doubleJumpers.remove(uuid); // Clean up so it doesn't linger
            player.setVelocity(player.getLocation().getDirection().multiply(1.5).setY(1));
            player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1, 1);
        }
    }
}