package com.example.corovaItems.Guns;

import com.example.corovaItems.CorovaItems;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
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
 * Peacemaker — Heavy Minigun
 * Damage: 3.0 | Mag: 100 | Reload: 5.0s | Range: 45
 * Feature: 1.0s Wind-up before firing.
 */
public class PeaceMaker extends CorovaItems implements Listener {

    private static final double DAMAGE     = 1.5; // Scaled to 3.0 via headshots
    private static final double RANGE      = 45.0;
    private static final double HIT_RADIUS = 0.4;
    private static final double SPREAD     = 0.22;
    private static final int    MAG_SIZE   = 100;
    private static final long   RELOAD_MS  = 5000L;
    private static final long   FIRE_MS    = 45L;  // 1 shot per tick
    private static final long   WINDUP_TICKS = 20L; // 1 second
    private static final long   IDLE_TICKS = 10L;

    private final Set<UUID>          reloading     = new HashSet<>();
    private final Map<UUID, Long>    lastFired     = new HashMap<>();
    private final Map<UUID, Long>    lastClickTick = new HashMap<>();
    private final Map<UUID, Long>    windupStart   = new HashMap<>();
    private final Set<UUID>          loopRunning   = new HashSet<>();
    private final Random             rng           = new Random();

    public PeaceMaker() {
        super("§7Peacemaker", Material.IRON_SWORD,
                List.of("§7Heavy Multi-barrel Minigun",
                        "§7Damage: §c3.0 §7| Mag: §e100 §7| Reload: §a5.0s",
                        "§7Extreme ROF §7| §6Wind-up: 1.0s",
                        "§8Right-click: Fire (hold)  |  Shift Drop: Reload"),
                Collections.emptyMap(), "peacemaker");
    }

    private JavaPlugin pl()               { return JavaPlugin.getProvidingPlugin(PeaceMaker.class); }
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

    private void bar(Player p, ItemStack stack, boolean woundUp) {
        int a = getAmmo(stack), filled = (int) Math.round((double) a / MAG_SIZE * 10);
        String color = woundUp ? "§c" : "§7";
        String s = color + "█".repeat(filled) + "§8" + "█".repeat(10 - filled) + " §f" + a + "§7/" + MAG_SIZE;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s));
    }

    private void reload(Player p, ItemStack stack) {
        if (isReloading(p)) return;
        if (getAmmo(stack) == MAG_SIZE) return;
        reloading.add(p.getUniqueId());
        loopRunning.remove(p.getUniqueId());
        windupStart.remove(p.getUniqueId());
        p.sendMessage("§eReloading... §7(5.0s)");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 0.5f);
        new BukkitRunnable() { @Override public void run() {
            reloading.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            ItemStack cur = p.getInventory().getItemInMainHand();
            if (isThisItem(cur)) {
                setAmmo(cur, MAG_SIZE);
                p.sendMessage("§aMinigun refilled!");
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 0.5f); bar(p, cur, false);
            } else {
                setAmmo(stack, MAG_SIZE);
            }}}.runTaskLater(pl(), RELOAD_MS / 50L);
    }

    private void tryFire(Player p, ItemStack stack) {
        if (isReloading(p)) return;
        UUID uid = p.getUniqueId();
        long now = p.getServer().getCurrentTick();

        if (!windupStart.containsKey(uid)) windupStart.put(uid, now);

        long windupElapsed = now - windupStart.get(uid);
        if (windupElapsed < WINDUP_TICKS) {
            if (windupElapsed % 5 == 0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 0.5f + (windupElapsed / 20.0f));
            bar(p, stack, false);
            return;
        }

        if (onCooldown(p)) return;
        if (getAmmo(stack) <= 0) {
            p.sendMessage("§c*click* §7Empty! §e(Shift Drop to reload)");
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 2f);
            loopRunning.remove(uid);
            windupStart.remove(uid);
            return;
        }
        doShot(p, stack);
    }

    private void doShot(Player p, ItemStack stack) {
        if (getAmmo(stack) <= 0) return;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        dir.add(new Vector((rng.nextDouble() - .5) * SPREAD, (rng.nextDouble() - .5) * SPREAD, (rng.nextDouble() - .5) * SPREAD)).normalize();

        for (double i = 0.5; i < RANGE; i += 0.5) {
            Location loc = eye.clone().add(dir.clone().multiply(i));
            if (loc.getBlock().getType().isSolid()) break;
            if (i % 4.0 < 0.5) p.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0.01);

            for (Entity e : p.getWorld().getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                if (e instanceof LivingEntity t && e != p) {
                    double falloff = 1.0 - (i / RANGE) * 0.4;
                    double damage = DAMAGE * falloff;
                    if (Math.abs(loc.getY() - t.getEyeLocation().getY()) < 0.45) damage *= 2.0;

                    t.setNoDamageTicks(0);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(p.getUniqueId());
                    t.setNoDamageTicks(0);
                    t.damage(damage, p);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(p.getUniqueId());
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.1, 0.1, 0.1, 0.05);
                    setAmmo(stack, getAmmo(stack) - 1); lastFired.put(p.getUniqueId(), System.currentTimeMillis());
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 0.8f); bar(p, stack, true);
                    if (getAmmo(stack) == 0) { loopRunning.remove(p.getUniqueId()); windupStart.remove(p.getUniqueId()); reload(p, stack); } return;
                }
            }
        }
        setAmmo(stack, getAmmo(stack) - 1); lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 0.8f); bar(p, stack, true);
        if (getAmmo(stack) == 0) { loopRunning.remove(p.getUniqueId()); windupStart.remove(p.getUniqueId()); reload(p, stack); }
    }

    private void startAutoIfNeeded(Player p) {
        UUID uid = p.getUniqueId();
        lastClickTick.put(uid, (long) p.getServer().getCurrentTick());

        if (loopRunning.contains(uid)) return;
        loopRunning.add(uid);

        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || !isThisItem(p.getInventory().getItemInMainHand())) {
                    loopRunning.remove(uid); windupStart.remove(uid); cancel(); return;
                }
                if (!loopRunning.contains(uid)) { windupStart.remove(uid); cancel(); return; }
                long ticksSinceClick = p.getServer().getCurrentTick() - lastClickTick.getOrDefault(uid, 0L);
                if (ticksSinceClick > IDLE_TICKS) {
                    loopRunning.remove(uid); windupStart.remove(uid); cancel(); return;
                }
                tryFire(p, p.getInventory().getItemInMainHand());
            }
        }.runTaskTimer(pl(), 0L, 1L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack stack = p.getInventory().getItemInMainHand();
        if (!isThisItem(stack)) return;
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (right) { e.setCancelled(true); startAutoIfNeeded(p); }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        if (!isThisItem(stack)) return;
        if (!e.getPlayer().isSneaking()) return;
        e.setCancelled(true); reload(e.getPlayer(), stack);
    }
}
