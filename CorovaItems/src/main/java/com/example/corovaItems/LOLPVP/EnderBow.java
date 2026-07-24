package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

/**
 * Modernized EnderBow with teleport toggle + phantom enchant effect.
 */
public class EnderBow extends CorovaItems implements Listener {

    private final List<Arrow> trackedArrows = new ArrayList<>();
    private final Set<UUID> teleportEnabled = new HashSet<>();

    public EnderBow() {
        super(
                ChatColor.AQUA + "Ender Bow",
                Material.BOW,
                lore(),
                enchantments(),
                "enderbow"
        );
    }

    // Must be called by plugin onEnable to register listeners.
    public void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Toggle teleport mode on left-click */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR &&
                event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!this.isThisItem(item)) return;

        event.setCancelled(true);

        boolean enabled = !teleportEnabled.contains(player.getUniqueId());

        if (enabled) {
            teleportEnabled.add(player.getUniqueId());
            player.sendActionBar(ChatColor.GREEN + "Teleportation: ON");
        } else {
            teleportEnabled.remove(player.getUniqueId());
            player.sendActionBar(ChatColor.RED + "Teleportation: OFF");
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        ItemStack bow = event.getBow();
        if (bow == null) return;

        if (this.isThisItem(bow)) {
            ProjectileSource shooter = arrow.getShooter();
            if (shooter instanceof Player player) {
                if (teleportEnabled.contains(player.getUniqueId())) {
                    trackedArrows.add(arrow);
                }
            }
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;

        ProjectileSource shooter = arrow.getShooter();
        if (!(shooter instanceof Player player)) return;

        if (!trackedArrows.contains(arrow)) return;

        Location loc = new Location(
                arrow.getWorld(),
                arrow.getLocation().getX(),
                arrow.getLocation().getY(),
                arrow.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );

        player.teleport(loc);
        arrow.getWorld().playEffect(arrow.getLocation(), Effect.ENDER_SIGNAL, 4);
        arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);

        cleanupArrow(arrow);
    }

    private void cleanupArrow(final Arrow arrow) {
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugins()[0],
                () -> trackedArrows.remove(arrow),
                3L
        );
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Teleport I",
                ChatColor.DARK_GRAY + "Shoot to teleport!",
                ChatColor.DARK_GRAY + "Left click to toggle teleportation!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        // No actual enchantments, glint handled separately
        return Collections.emptyMap();
    }
}
