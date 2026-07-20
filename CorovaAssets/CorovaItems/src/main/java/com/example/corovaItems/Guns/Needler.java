package com.example.corovaItems.Guns;

import com.example.corovaItems.CorovaItems;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Needler — Halo-inspired Crystalline Weapon
 * Fires homing needles that explode after 7 hits (Supercombine).
 */
public class Needler extends CorovaItems implements Listener {

    private static final double DAMAGE     = 2.0;
    private static final double RANGE      = 30.0;
    private static final int    MAG_SIZE   = 20;
    private static final long   RELOAD_MS  = 2000L;
    private static final long   FIRE_MS    = 100L;
    private static final int    SUPERCOMBINE_HITS = 7;
    private static final double EXPLOSION_DAMAGE  = 100.0;

    private final Set<UUID> reloading = new HashSet<>();
    private final Map<UUID, Long> lastFired = new HashMap<>();
    private final Map<UUID, Long> lastClickTick = new HashMap<>();
    private final Set<UUID> loopRunning = new HashSet<>();
    private final Map<UUID, Map<UUID, Integer>> targetHits = new HashMap<>(); // Shooter -> (Target -> Hits)

    public Needler() {
        super("§dNeedler", Material.WARPED_FUNGUS_ON_A_STICK,
                List.of("§7Guided Crystalline Projectiles",
                        "§7Damage: §c4 §7| Mag: §e20 §7| Reload: §a2.0s",
                        "§7Homing Needles §7| §dSupercombine: §f7 hits",
                        "§8Right-click: Fire (hold)  |  Shift Drop: Reload"),
                Collections.emptyMap(), "needler");
    }

    private JavaPlugin pl() { return JavaPlugin.getProvidingPlugin(Needler.class); }

    private int getAmmo(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) return MAG_SIZE;
        return stack.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(pl(), "ammo"), PersistentDataType.INTEGER, MAG_SIZE);
    }

    private void setAmmo(ItemStack stack, int v) {
        if (stack == null || stack.getItemMeta() == null) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(pl(), "ammo"), PersistentDataType.INTEGER, Math.max(0, Math.min(MAG_SIZE, v)));
        stack.setItemMeta(meta);
    }

    private void bar(Player p, ItemStack stack) {
        int a = getAmmo(stack), filled = (int) Math.round((double) a / MAG_SIZE * 10);
        String s = "§d" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled) + " §f" + a + "§7/" + MAG_SIZE;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s));
    }

    private void reload(Player p, ItemStack stack) {
        if (reloading.contains(p.getUniqueId())) return;
        if (getAmmo(stack) == MAG_SIZE) return;
        reloading.add(p.getUniqueId());
        p.sendMessage("§dReloading...");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.5f);
        new BukkitRunnable() {
            @Override public void run() {
                reloading.remove(p.getUniqueId());
                if (!p.isOnline()) return;
                ItemStack cur = p.getInventory().getItemInMainHand();
                if (isThisItem(cur)) {
                    setAmmo(cur, MAG_SIZE);
                    p.sendMessage("§aNeedles replenished!");
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.8f); bar(p, cur);
                } else {
                    setAmmo(stack, MAG_SIZE);
                }
            }}.runTaskLater(pl(), RELOAD_MS / 50L);
    }

    private void tryFire(Player p, ItemStack stack) {
        if (reloading.contains(p.getUniqueId())) return;
        if (System.currentTimeMillis() - lastFired.getOrDefault(p.getUniqueId(), 0L) < FIRE_MS - 10) return;
        if (getAmmo(stack) <= 0) { reload(p, stack); return; }

        doShot(p, stack);
    }

    private void doShot(Player p, ItemStack stack) {
        setAmmo(stack, getAmmo(stack) - 1);
        lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1f, 1.5f);
        bar(p, stack);

        new BukkitRunnable() {
            Location loc = p.getEyeLocation();
            Vector dir = loc.getDirection().normalize().multiply(0.8);
            int ticks = 0;
            LivingEntity target = findTarget(p, loc);

            @Override public void run() {
                if (ticks++ > 40 || loc.getBlock().getType().isSolid()) { cancel(); return; }

                if (target != null && target.isValid() && !target.isDead()) {
                    Vector toTarget = target.getEyeLocation().toVector().subtract(loc.toVector()).normalize().multiply(0.8);
                    dir.add(toTarget.multiply(0.2)).normalize().multiply(0.8);
                }

                loc.add(dir);
                loc.getWorld().spawnParticle(Particle.DUST, loc, 1, new Particle.DustOptions(Color.fromRGB(255, 0, 255), 0.6f));

                for (Entity e : loc.getWorld().getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
                    if (e instanceof LivingEntity t && e != p) {
                        applyHit(p, t);
                        cancel(); return;
                    }
                }
            }
        }.runTaskTimer(pl(), 0L, 1L);
    }

    private LivingEntity findTarget(Player p, Location loc) {
        double closest = 15.0;
        LivingEntity best = null;
        for (Entity e : p.getWorld().getNearbyEntities(loc, 15, 15, 15)) {
            if (e instanceof LivingEntity t && e != p) {
                double dist = t.getLocation().distance(loc);
                if (dist < closest) {
                    Vector to = t.getLocation().toVector().subtract(loc.toVector()).normalize();
                    if (loc.getDirection().angle(to) < Math.toRadians(45)) {
                        closest = dist; best = t;
                    }
                }
            }
        }
        return best;
    }

    private void applyHit(Player shooter, LivingEntity target) {
        UUID sId = shooter.getUniqueId();
        com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(sId);
        target.setNoDamageTicks(0);
        target.damage(DAMAGE, shooter);
        com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(sId);

        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.5f, 2f);

        UUID tId = target.getUniqueId();
        targetHits.putIfAbsent(sId, new HashMap<>());
        Map<UUID, Integer> hits = targetHits.get(sId);
        int count = hits.getOrDefault(tId, 0) + 1;

        if (count >= SUPERCOMBINE_HITS) {
            hits.remove(tId);
            triggerSupercombine(shooter, target);
        } else {
            hits.put(tId, count);
            new BukkitRunnable() { @Override public void run() {
                if (targetHits.containsKey(sId)) {
                    Map<UUID, Integer> m = targetHits.get(sId);
                    if (m.getOrDefault(tId, 0) == count) m.remove(tId);
                }
            }}.runTaskLater(pl(), 60L); // Reset hits after 3s
        }
    }

    private void triggerSupercombine(Player shooter, LivingEntity target) {
        Location loc = target.getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f);

        UUID sId = shooter.getUniqueId();
        com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(sId);
        target.setNoDamageTicks(0);
        target.damage(EXPLOSION_DAMAGE, shooter);
        com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(sId);
    }

    private void startAutoIfNeeded(Player p) {
        UUID uid = p.getUniqueId();
        lastClickTick.put(uid, (long) p.getServer().getCurrentTick());
        if (loopRunning.contains(uid)) return;
        loopRunning.add(uid);
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || !isThisItem(p.getInventory().getItemInMainHand())) { loopRunning.remove(uid); cancel(); return; }
                if (p.getServer().getCurrentTick() - lastClickTick.getOrDefault(uid, 0L) > 10L) { loopRunning.remove(uid); cancel(); return; }
                tryFire(p, p.getInventory().getItemInMainHand());
            }
        }.runTaskTimer(pl(), 0L, 2L);
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (!isThisItem(e.getItem())) return;
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true); startAutoIfNeeded(e.getPlayer());
        }
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        if (isThisItem(stack)) {
            if (!e.getPlayer().isSneaking()) return;
            e.setCancelled(true); reload(e.getPlayer(), stack);
        }
    }
}
