package com.example.corovaItems.Guns;

import com.example.corovaItems.CorovaItems;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Spartan Laser — Heavy Directed Energy Weapon
 * 2.5s charge time, penetrating beam, extreme damage.
 */
public class SpartanLaser extends CorovaItems implements Listener {

    private static final double DAMAGE     = 500.0;
    private static final double RANGE      = 100.0;
    private static final double HIT_RADIUS = 0.6;
    private static final long   CHARGE_TICKS = 50L; // 2.5s
    private static final long   IDLE_TICKS   = 10L;
    private static final long   COOLDOWN_MS  = 2000L;

    private final Map<UUID, Long> lastClickTick = new HashMap<>();
    private final Map<UUID, Long> lastFireTime  = new HashMap<>();
    private final Set<UUID>       charging      = new HashSet<>();

    public SpartanLaser() {
        super("§6Spartan Laser", Material.NETHERITE_HOE,
                List.of("§7Heavy Energy Weapon",
                        "§7Charge-up: §e2.5s §7| Damage: §c1000",
                        "§7Penetrating Beam | Range: §b~100",
                        "§8Hold Right-click: Charge & Fire"),
                Collections.emptyMap(), "spartan_laser");
    }

    private JavaPlugin pl() { return JavaPlugin.getProvidingPlugin(SpartanLaser.class); }

    private void bar(Player p, double progress) {
        int filled = (int) Math.round(progress * 10);
        String s = "§6CHARGING: " + "█".repeat(filled) + "§8" + "█".repeat(10 - filled) + " §f" + (int)(progress * 100) + "%";
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s));
    }

    private void doFire(Player p) {
        lastFireTime.put(p.getUniqueId(), System.currentTimeMillis());
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        p.getWorld().playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        p.getWorld().playSound(eye, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 2f, 2f);

        Set<UUID> hitEntities = new HashSet<>();
        Particle.DustOptions fireDust = new Particle.DustOptions(Color.RED, 2.0f);

        for (double i = 1.0; i < RANGE; i += 0.5) {
            Location loc = eye.clone().add(dir.clone().multiply(i));
            if (loc.getBlock().getType().isSolid()) break;
            p.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.1, 0.1, 0.1, 0.02, fireDust);
            p.getWorld().spawnParticle(Particle.FLAME, loc, 2, 0.05, 0.05, 0.05, 0.02);

            for (Entity e : p.getWorld().getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                if (e instanceof LivingEntity t && e != p && !hitEntities.contains(e.getUniqueId())) {
                    t.setNoDamageTicks(0);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(p.getUniqueId());
                    t.setNoDamageTicks(0);
                    t.damage(DAMAGE, p);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(p.getUniqueId());
                    hitEntities.add(e.getUniqueId());
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 10, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    private void startCharging(Player p) {
        UUID uid = p.getUniqueId();
        if (System.currentTimeMillis() - lastFireTime.getOrDefault(uid, 0L) < COOLDOWN_MS) {
            p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§cCooling down..."));
            return;
        }
        lastClickTick.put(uid, (long) p.getServer().getCurrentTick());
        if (charging.contains(uid)) return;
        charging.add(uid);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                long currentTick = p.getServer().getCurrentTick();
                long sinceLastClick = currentTick - lastClickTick.getOrDefault(uid, 0L);

                if (!p.isOnline() || !isThisItem(p.getInventory().getItemInMainHand()) || sinceLastClick > IDLE_TICKS) {
                    charging.remove(uid);
                    p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize("§cCharge Interrupted"));
                    cancel();
                    return;
                }

                ticks++;
                double progress = (double) ticks / CHARGE_TICKS;
                bar(p, Math.min(1.0, progress));

                // Visual charge laser line
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                Particle.DustOptions chargeDust = new Particle.DustOptions(Color.RED, 0.6f);
                for (double d = 1.0; d < 30.0; d += 1.0) {
                    Location loc = eye.clone().add(dir.clone().multiply(d));
                    if (loc.getBlock().getType().isSolid()) break;
                    p.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, chargeDust);
                }

                if (ticks % 5 == 0) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 0.5f + (float)progress * 1.5f);
                }

                if (ticks >= CHARGE_TICKS) {
                    charging.remove(uid);
                    cancel();
                    doFire(p);
                }
            }
        }.runTaskTimer(pl(), 0L, 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!isThisItem(p.getInventory().getItemInMainHand())) return;

        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            startCharging(p);
        }
    }
}
