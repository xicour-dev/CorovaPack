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
 * RPG-7 — Rocket Launcher
 * Direct: 40 + 20 splash (3-block radius) | Mag: 1 | Reload: 5.0s
 * Self-damage possible at close range.
 * Right-click: Fire | Shift Drop: Reload
 */
public class RPG7 extends CorovaItems implements Listener {

    private static final double DAMAGE_DIRECT = 40.0;
    private static final double DAMAGE_SPLASH = 30.0;
    private static final double SPLASH_RADIUS = 3.0;
    private static final double RANGE         = 80.0;
    private static final double STEP          = 0.6;
    private static final int    MAG_SIZE      = 1;
    private static final long   RELOAD_MS     = 5000L;
    private static final long   FIRE_MS       = 100L;

    private final Set<UUID>          reloading = new HashSet<>();
    private final Map<UUID, Long>    lastFired = new HashMap<>();

    public RPG7() {
        super("§cRPG-7", Material.BLAZE_ROD,
                List.of("§7Rocket-Propelled Grenade",
                        "§7Direct hit: §c80 §7| Splash: §c60 §7(3-block radius)",
                        "§7Mag: §e1 §7| Reload: §a5.0s",
                        "§c⚠ Self-damage possible at close range!",
                        "§8Right-click: Fire  |  Shift Drop: Reload"),
                Collections.emptyMap(), "rpg7");
    }

    private JavaPlugin pl()               { return JavaPlugin.getProvidingPlugin(RPG7.class); }
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
        int a = getAmmo(stack);
        String s = (a > 0 ? "§a█" : "§8█") + "§8█████████ §f" + a + "§7/" + MAG_SIZE;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s));
    }

    private void reload(Player p, ItemStack stack) {
        if (isReloading(p)) return;
        if (getAmmo(stack) == MAG_SIZE) { p.sendMessage("§eRocket loaded!"); return; }
        reloading.add(p.getUniqueId());
        p.sendMessage("§eLoading rocket... §7(5.0s)");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 0.8f);
        new BukkitRunnable() { @Override public void run() {
            reloading.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            ItemStack cur = p.getInventory().getItemInMainHand();
            if (isThisItem(cur)) {
                setAmmo(cur, MAG_SIZE);
                p.sendMessage("§aRocket loaded!");
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 0.8f); bar(p, cur);
            } else {
                setAmmo(stack, MAG_SIZE);
            }}}.runTaskLater(pl(), RELOAD_MS / 50L);
    }

    private void tryFire(Player p, ItemStack stack) {
        if (isReloading(p)) { p.sendMessage("§cLoading..."); return; }
        if (onCooldown(p)) return;
        if (getAmmo(stack) <= 0) { p.sendMessage("§c No rocket loaded! §e(Shift Drop to reload)"); p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 2f); return; }
        doShot(p, stack);
    }

    private void doShot(Player p, ItemStack stack) {
        if (getAmmo(stack) <= 0) return;
        setAmmo(stack, MAG_SIZE); lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        bar(p, stack);

        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize().multiply(STEP);
        p.getWorld().playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 0.5f);

        new BukkitRunnable() {
            double traveled = 0;
            final Location rocketLoc = eye.clone();
            @Override public void run() {
                if (traveled >= RANGE) { explode(rocketLoc, p, false); cancel(); return; }
                rocketLoc.add(dir); traveled += STEP;
                rocketLoc.getWorld().spawnParticle(Particle.FLAME, rocketLoc, 3, 0.05, 0.05, 0.05, 0.0);
                rocketLoc.getWorld().spawnParticle(Particle.SMOKE, rocketLoc, 2, 0.1, 0.1, 0.1, 0.02);
                for (Entity e : rocketLoc.getWorld().getNearbyEntities(rocketLoc, 0.6, 0.6, 0.6)) {
                    if (e instanceof LivingEntity && e != p) { explode(rocketLoc, p, true); cancel(); return; }
                }
                if (rocketLoc.getBlock().getType().isSolid()) { explode(rocketLoc, p, false); cancel(); }
            }
        }.runTaskTimer(pl(), 0L, 1L);

        reload(p, stack);
    }

    private void explode(Location loc, Player shooter, boolean directHit) {
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.7f);
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 1, 0, 0, 0, 0);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 30, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 20, 0.4, 0.4, 0.4, 0.05);
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
        if (!e.getPlayer().isSneaking()) return;
        e.setCancelled(true); reload(e.getPlayer(), stack);
    }
}