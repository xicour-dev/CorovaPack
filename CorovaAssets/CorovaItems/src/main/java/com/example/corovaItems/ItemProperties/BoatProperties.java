package com.example.corovaItems.ItemProperties;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;

/**
 * BoatProperties — Seamless Ice Boat Block Climbing
 *
 * Probes 1.5 blocks ahead every tick. When a steppable block is detected,
 * teleports the boat (and all passengers) BEFORE the boat reaches the face —
 * eliminating the bump entirely because collision never occurs.
 */
public class BoatProperties implements Listener {

    private static final Set<Material> ICE_TYPES = Set.of(
            Material.ICE,
            Material.PACKED_ICE,
            Material.BLUE_ICE,
            Material.FROSTED_ICE
    );

    // Boat entity Y offset above its resting surface
    private static final double BOAT_Y_OFFSET = 0.375;

    // How far ahead to probe — large enough to act before contact (1.5 blocks)
    private static final double PROBE_DISTANCE = 1.5;

    // Minimum horizontal speed to bother checking
    private static final double MIN_SPEED_SQ = 0.01 * 0.01;

    // Cooldown in ticks after a step-up — prevents repeated triggers
    private static final int STEP_COOLDOWN_TICKS = 8;

    // UUID → cooldown ticks remaining
    private final java.util.Map<UUID, Integer> cooldowns = new java.util.HashMap<>();

    private final Plugin plugin;

    public BoatProperties(Plugin plugin) {
        this.plugin = plugin;
        startTickLoop();
    }

    private void startTickLoop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Tick down cooldowns
                cooldowns.replaceAll((id, ticks) -> ticks - 1);
                cooldowns.entrySet().removeIf(e -> e.getValue() <= 0);

                for (org.bukkit.World world : plugin.getServer().getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (!(entity instanceof Boat boat)) continue;
                        if (boat.getPassengers().isEmpty()) continue;
                        if (cooldowns.containsKey(boat.getUniqueId())) continue;
                        tryStepUp(boat);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void tryStepUp(Boat boat) {
        Vector vel = boat.getVelocity();
        double vx = vel.getX();
        double vz = vel.getZ();

        // ── 1. Must be moving horizontally ───────────────────────────────────
        double horizSq = vx * vx + vz * vz;
        if (horizSq < MIN_SPEED_SQ) return;

        // ── 2. Must be on ice ─────────────────────────────────────────────────
        Location loc = boat.getLocation();
        Block surface = loc.clone().subtract(0, 0.5, 0).getBlock();
        if (!ICE_TYPES.contains(surface.getType())) return;

        // ── 3. Must not be mid-air from a previous step ───────────────────────
        if (vel.getY() > 0.05) return;

        // ── 4. Probe 1.5 blocks ahead — well before the block face ───────────
        double speed = Math.sqrt(horizSq);
        double nx = vx / speed;
        double nz = vz / speed;

        Block ahead = null;
        // Scan from 0.7 → 1.5 so we catch blocks at various speeds
        for (double probe = 0.7; probe <= PROBE_DISTANCE; probe += 0.1) {
            Block candidate = loc.clone().add(nx * probe, 0, nz * probe).getBlock();
            if (candidate.getType().isSolid()) {
                ahead = candidate;
                break;
            }
        }
        if (ahead == null) return;

        // ── 5. Obstacle must be exactly 1 block taller than current surface ───
        // ahead.getY() should equal floor(loc.getY()) for a 1-block step
        int boatFloorY = (int) Math.floor(loc.getY() - BOAT_Y_OFFSET);
        if (ahead.getY() != boatFloorY) return; // not a 1-block step, skip

        // ── 6. Space above obstacle must be clear ────────────────────────────
        if (ahead.getRelative(0, 1, 0).getType().isSolid()) return;

        // ── 7. Space above boat must be clear ────────────────────────────────
        if (loc.clone().add(0, 1.5, 0).getBlock().getType().isSolid()) return;

        // ── 8. Destination: top of block + boat offset, already past the face ─
        // Place the boat 0.6 blocks past the block's centre in travel direction
        // so it lands comfortably on top, never touching the side face
        double destX = ahead.getX() + 0.5 + nx * 0.6;
        double destY = ahead.getY() + 1.0 + BOAT_Y_OFFSET;
        double destZ = ahead.getZ() + 0.5 + nz * 0.6;

        Location dest = loc.clone();
        dest.setX(destX);
        dest.setY(destY);
        dest.setZ(destZ);

        // Capture velocity to restore after teleport
        final double capVx = vx;
        final double capVz = vz;
        final double capSpeed = speed;
        final double captureNx = nx;
        final double captureNz = nz;

        // Add cooldown immediately to prevent re-trigger during this step
        cooldowns.put(boat.getUniqueId(), STEP_COOLDOWN_TICKS);

        // Teleport boat
        boat.teleport(dest);

        // Teleport each passenger to the same spot so the client accepts it
        for (Entity passenger : boat.getPassengers()) {
            passenger.teleport(dest);
        }

        // Re-add passengers (teleport ejects them on some server versions)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!boat.isValid()) return;
            for (Entity passenger : new java.util.ArrayList<>(boat.getPassengers())) {
                boat.removePassenger(passenger);
            }
            for (Entity passenger : boat.getPassengers()) {
                if (!boat.getPassengers().contains(passenger)) {
                    boat.addPassenger(passenger);
                }
            }

            // Restore momentum — carry full pre-step speed plus a tiny forward nudge
            double scale = Math.min(capSpeed * 20.0, 0.45);
            boat.setVelocity(new Vector(
                    captureNx * scale,
                    0.0,
                    captureNz * scale
            ));
        }, 1L);
    }
}