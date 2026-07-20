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
 * P90 — Full-Auto PDW
 * Damage: 5 | Mag: 50 | Reload: 2.8s | ~920 RPM | Range: 30
 * Right-click: Fire (hold) | Shift Drop: Reload
 */
public class P90 extends CorovaItems implements Listener {

    private static final double DAMAGE     = 3.0;
    private static final double RANGE      = 30.0;
    private static final double HIT_RADIUS = 0.4;
    private static final double SPREAD     = 0.07;
    private static final int    MAG_SIZE   = 50;
    private static final long   RELOAD_MS  = 2800L;
    private static final long   FIRE_MS    = 65L;
    private static final long   IDLE_TICKS = 10L;

    private final Set<UUID>          reloading     = new HashSet<>();
    private final Map<UUID, Long>    lastFired     = new HashMap<>();
    private final Map<UUID, Long>    lastClickTick = new HashMap<>();
    private final Set<UUID>          loopRunning   = new HashSet<>();
    private final Random             rng           = new Random();

    public P90() {
        super("§dP90", Material.STONE_SWORD,
                List.of("§7Full-Auto PDW",
                        "§7Damage: §c6 §7| Mag: §e50 §7| Reload: §a2.8s",
                        "§7Fire Rate: §d~920 RPM §7| Range: §b30",
                        "§8Right-click: Fire (hold)  |  Shift Drop: Reload"),
                Collections.emptyMap(), "p90");
    }

    private JavaPlugin pl()               { return JavaPlugin.getProvidingPlugin(P90.class); }
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
        if (getAmmo(stack) == MAG_SIZE) { p.sendMessage("§eMagazine already full!"); return; }
        reloading.add(p.getUniqueId());
        loopRunning.remove(p.getUniqueId());
        lastClickTick.remove(p.getUniqueId());
        p.sendMessage("§eReloading... §7(2.8s)");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
        new BukkitRunnable() { @Override public void run() {
            reloading.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            ItemStack cur = p.getInventory().getItemInMainHand();
            if (isThisItem(cur)) {
                setAmmo(cur, MAG_SIZE);
                p.sendMessage("§aReloaded! §7[" + MAG_SIZE + "/" + MAG_SIZE + "]");
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.4f); bar(p, cur);
            } else {
                setAmmo(stack, MAG_SIZE);
            }}}.runTaskLater(pl(), RELOAD_MS / 50L);
    }

    private void tryFire(Player p, ItemStack stack) {
        if (isReloading(p)) { p.sendMessage("§cReloading..."); return; }
        if (onCooldown(p)) return;
        if (getAmmo(stack) <= 0) {
            p.sendMessage("§c*click* §7Empty! §e(Shift Drop to reload)");
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 2f);
            loopRunning.remove(p.getUniqueId());
            return;
        }
        doShot(p, stack);
    }

    private void doShot(Player p, ItemStack stack) {
        if (getAmmo(stack) <= 0) return;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        dir.add(new Vector((rng.nextDouble() - .5) * SPREAD, (rng.nextDouble() - .5) * SPREAD, (rng.nextDouble() - .5) * SPREAD)).normalize();
        boolean hit = false;
        for (double i = 0.5; i < RANGE; i += 0.35) {
            Location loc = eye.clone().add(dir.clone().multiply(i));
            if (loc.getBlock().getType().isSolid()) break;
            p.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0);
            for (Entity e : p.getWorld().getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                if (e instanceof LivingEntity t && e != p) {
                    t.setNoDamageTicks(0);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(p.getUniqueId());
                    t.setNoDamageTicks(0);
                    t.damage(DAMAGE, p);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(p.getUniqueId());
                    loc.getWorld().spawnParticle(Particle.CRIT, loc, 3, 0.05, 0.05, 0.05, 0.01);
                    hit = true; break;
                }
            }
            if (hit) break;
        }
        setAmmo(stack, getAmmo(stack) - 1); lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 2.0f); bar(p, stack);
        if (getAmmo(stack) == 0) { loopRunning.remove(p.getUniqueId()); reload(p, stack); }
    }

    private void startAutoIfNeeded(Player p) {
        UUID uid = p.getUniqueId();
        lastClickTick.put(uid, (long) p.getServer().getCurrentTick());

        if (loopRunning.contains(uid)) return;
        loopRunning.add(uid);

        long fireTicks = Math.max(1L, FIRE_MS / 50L);
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || !isThisItem(p.getInventory().getItemInMainHand())) {
                    loopRunning.remove(uid); cancel(); return;
                }
                if (!loopRunning.contains(uid)) { cancel(); return; }
                long ticksSinceClick = p.getServer().getCurrentTick() - lastClickTick.getOrDefault(uid, 0L);
                if (ticksSinceClick > IDLE_TICKS) {
                    loopRunning.remove(uid); cancel(); return;
                }
                tryFire(p, p.getInventory().getItemInMainHand());
            }
        }.runTaskTimer(pl(), 0L, fireTicks);
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