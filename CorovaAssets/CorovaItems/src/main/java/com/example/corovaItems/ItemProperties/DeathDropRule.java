package com.example.corovaItems.ItemProperties;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Enforces PVP-only item/XP drops.
 *
 * - PVP death  (killed by another Player) → normal vanilla behaviour, items and XP drop.
 * - Non-PVP death (mob, fall, fire, void, etc.) → keep inventory + keep XP, nothing drops.
 */
public class DeathDropRule implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        // Check whether the killer is a player (direct hit or player-owned projectile)
        boolean killedByPlayer = victim.getKiller() != null;

        if (!killedByPlayer) {
            // Non-PVP death — simulate keep-inventory behaviour
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            event.getDrops().clear();
        }
        // PVP death — leave the event untouched so vanilla drop rules apply
    }
}