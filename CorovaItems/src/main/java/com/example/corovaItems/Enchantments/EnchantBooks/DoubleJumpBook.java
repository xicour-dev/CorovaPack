package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DoubleJumpBook extends EnchantmentBook implements Listener {

    // Tracks players mid-air after a double-jump (allowFlight revoked until landing)
    private final Set<UUID> inAir = new HashSet<>();

    public DoubleJumpBook() {
        this(1);
    }

    public DoubleJumpBook(int level) {
        super(
                "Book of Double Jump",
                CorovaEnchantments.DOUBLE_JUMP_ID,
                level,
                "book_doublejump",
                allowedMaterialsStatic()
        );
        // Required so the anvil listener's getItemById("book_doublejump") doesn't
        // return null and silently abort before ever writing the PDC enchant data.
        ItemManager.getInstance().registerItem(this);
    }

    // ── Only applies to boots ─────────────────────────────────────────────────

    private static Set<Material> allowedMaterialsStatic() {
        Set<Material> s = new HashSet<>();
        s.add(Material.LEATHER_BOOTS);
        s.add(Material.CHAINMAIL_BOOTS);
        s.add(Material.IRON_BOOTS);
        s.add(Material.GOLDEN_BOOTS);
        s.add(Material.DIAMOND_BOOTS);
        s.add(Material.NETHERITE_BOOTS);
        for (Material mat : Material.values()) {
            String name = mat.name();
            if (name.endsWith("_BOOTS") && name.contains("COPPER")) s.add(mat);
        }
        return s;
    }

    // ── Restore allowFlight on join (same pattern as FlightBook) ─────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isCreativeOrSpectator(player)) return;
        if (hasDoubleJumpBoots(player.getInventory().getBoots())) {
            player.setAllowFlight(true);
        }
    }

    // ── Grant allowFlight when the player lands ───────────────────────────────
    // Run at HIGHEST so we fire AFTER CloudBoots.onPlayerMove (default priority)
    // has already called setAllowFlight(false) on us.  We simply re-grant it.

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (isCreativeOrSpectator(player)) return;

        ItemStack boots = player.getInventory().getBoots();
        if (!hasDoubleJumpBoots(boots)) return;

        // Only re-grant flight once the player has actually landed.
        // Don't touch allowFlight while mid-air — we deliberately revoked it
        // in onPlayerToggleFlight to prevent the client entering fly mode.
        if (inAir.contains(player.getUniqueId())) {
            boolean onGround = player.getLocation()
                    .subtract(0, 1, 0)
                    .getBlock()
                    .getType() != Material.AIR;
            if (onGround) {
                player.setAllowFlight(true);
                inAir.remove(player.getUniqueId());
            }
        } else {
            // Player is on the ground (or never jumped) — keep allowFlight on
            // so the game registers the next space-bar press as a flight toggle.
            // This also overwrites CloudBoots' rogue setAllowFlight(false) call.
            player.setAllowFlight(true);
        }
    }

    // ── Consume the flight toggle as a double-jump ────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (isCreativeOrSpectator(player)) return;

        ItemStack boots = player.getInventory().getBoots();
        if (!hasDoubleJumpBoots(boots)) return;

        event.setCancelled(true);           // prevent actual flight mode
        player.setFlying(false);
        player.setAllowFlight(false);       // re-granted on landing (onPlayerMove)
        player.setVelocity(
                player.getLocation().getDirection().multiply(1.5).setY(1.0));
        player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
        inAir.add(player.getUniqueId());
    }

    // ── Cleanup on disconnect ─────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        inAir.remove(player.getUniqueId());
        if (!isCreativeOrSpectator(player)) {
            player.setAllowFlight(false);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasDoubleJumpBoots(ItemStack boots) {
        return boots != null
                && boots.getType() != Material.AIR
                && CorovaEnchantments.hasEnchant(boots, CorovaEnchantments.DOUBLE_JUMP_ID);
    }

    private static boolean isCreativeOrSpectator(Player player) {
        return player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR;
    }
}