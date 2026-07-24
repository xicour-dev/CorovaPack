package com.example.corovaItems.Guns;

import com.example.corovaItems.CorovaItems;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Fuel Rod Cannon — Covenant Heavy Weapon (Halo)
 * Launches arcing green plasma rods that explode on impact.
 * Direct: 30 + 20 splash (3.5-block radius) | Mag: 5 | Reload: 4.0s
 */
public class FuelRodCannon extends CorovaItems implements Listener {

    private static final double DAMAGE_DIRECT = 30.0;
    private static final double DAMAGE_SPLASH = 20.0;
    private static final double SPLASH_RADIUS = 3.5;
    private static final double RANGE         = 80.0;
    private static final double STEP          = 0.7;
    private static final double GRAVITY       = 0.035;
    private static final int    MAG_SIZE      = 5;
    private static final long   RELOAD_MS     = 4000L;
    private static final long   FIRE_MS       = 600L;

    private final Set<UUID>          reloading = new HashSet<>();
    private final Map<UUID, Long>    lastFired = new HashMap<>();

    public FuelRodCannon() {
        super("§aFuel Rod Cannon", Material.GOLDEN_HOE,
                List.of("§7Covenant Heavy Launcher",
                        "§7Direct hit: §c60 §7| Splash: §c40 §7(3.5-block radius)",
                        "§7Mag: §e5 §7| Reload: §a4.0s",
                        "§8Right-click: Fire  |  Shift Drop: Reload"),
                Collections.emptyMap(), "fuel_rod_cannon");
    }

    private JavaPlugin pl()               { return JavaPlugin.getProvidingPlugin(FuelRodCannon.class); }
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
    private boolean isReloading(Player p) { return reloading.contains(p.getUniqueId()); }
    private boolean onCooldown(Player p)  { return System.currentTimeMillis() - lastFired.getOrDefault(p.getUniqueId(), 0L) < FIRE_MS - 10; }

    private void bar(Player p, ItemStack stack) {
        int a = getAmmo(stack), filled = (int) Math.round((double) a / MAG_SIZE * 10);
        String s = "§a" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled) + " §f" + a + "§7/" + MAG_SIZE;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s));
    }

    private void reload(Player p, ItemStack stack) {
        if (isReloading(p)) return;
        if (getAmmo(stack) == MAG_SIZE) { p.sendMessage("§eMagazine full!"); return; }
        reloading.add(p.getUniqueId());
        p.sendMessage("§eReloading rods... §7(4.0s)");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1.0f, 0.5f);
        new BukkitRunnable() { @Override public void run() {
            reloading.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            setAmmo(stack, MAG_SIZE);
            ItemStack cur = p.getInventory().getItemInMainHand();
            if (isThisItem(cur)) {
                p.sendMessage("§aRods loaded!");
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.2f); bar(p, cur);
            }}}.runTaskLater(pl(), RELOAD_MS / 50L);
    }

    private void tryFire(Player p, ItemStack stack) {
        if (isReloading(p)) { p.sendMessage("§cReloading..."); return; }
        if (onCooldown(p)) return;
        if (getAmmo(stack) <= 0) { p.sendMessage("§c No rods left! §e(Shift Drop to reload)"); p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 2f); return; }
        doShot(p, stack);
    }

    private void doShot(Player p, ItemStack stack) {
        if (getAmmo(stack) <= 0) return;
        setAmmo(stack, getAmmo(stack) - 1);
        lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        bar(p, stack);

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize().multiply(STEP);
        p.getWorld().playSound(eye, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.5f, 1.8f);

        new BukkitRunnable() {
            double traveled = 0;
            final Location rodLoc = eye.clone();
            final Vector currentVelocity = dir.clone();
            @Override public void run() {
                if (traveled >= RANGE) { explode(rodLoc, p, false); cancel(); return; }

                rodLoc.add(currentVelocity);
                currentVelocity.setY(currentVelocity.getY() - GRAVITY);
                traveled += STEP;

                rodLoc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, rodLoc, 4, 0.05, 0.05, 0.05, 0.01);
                rodLoc.getWorld().spawnParticle(Particle.COMPOSTER, rodLoc, 1, 0, 0, 0, 0);

                for (Entity e : rodLoc.getWorld().getNearbyEntities(rodLoc, 0.7, 0.7, 0.7)) {
                    if (e instanceof LivingEntity && e != p) { explode(rodLoc, p, true); cancel(); return; }
                }
                if (rodLoc.getBlock().getType().isSolid()) { explode(rodLoc, p, false); cancel(); }
            }
        }.runTaskTimer(pl(), 0L, 1L);

        if (getAmmo(stack) == 0) reload(p, stack);
    }

    private void explode(Location loc, Player shooter, boolean directHit) {
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2f, 0.6f);
        loc.getWorld().spawnParticle(Particle.COMPOSTER, loc, 40, 0.6, 0.6, 0.6, 0.1);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 30, 0.5, 0.5, 0.5, 0.2);

        for (Entity e : loc.getWorld().getNearbyEntities(loc, SPLASH_RADIUS, SPLASH_RADIUS, SPLASH_RADIUS)) {
            if (e instanceof LivingEntity target) {
                double dist = e.getLocation().distance(loc);
                target.setNoDamageTicks(0);
                if (directHit && e != shooter) {
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(shooter.getUniqueId());
                    target.setNoDamageTicks(0);
                    target.damage(DAMAGE_DIRECT + DAMAGE_SPLASH, shooter);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(shooter.getUniqueId());
                } else {
                    double falloff = Math.max(0, 1.0 - (dist / SPLASH_RADIUS));
                    double dmg = DAMAGE_SPLASH * falloff;
                    if (e == shooter) dmg *= 0.5;
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(shooter.getUniqueId());
                    target.setNoDamageTicks(0);
                    if (dmg > 0) target.damage(dmg, shooter);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(shooter.getUniqueId());
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack stack = p.getInventory().getItemInMainHand();
        if (!isThisItem(stack)) return;
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (right) { e.setCancelled(true); tryFire(p, p.getInventory().getItemInMainHand()); }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        if (!isThisItem(stack)) return;
        e.setCancelled(true); reload(e.getPlayer(), stack);
    }
}
