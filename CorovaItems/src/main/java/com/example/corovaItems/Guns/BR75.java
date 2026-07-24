package com.example.corovaItems.Guns;

import com.example.corovaItems.CorovaItems;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * BR75 — 3-Round Burst Rifle
 * High precision with scoping capability.
 */
public class BR75 extends CorovaItems implements Listener {

    private static final double DAMAGE     = 5.5;
    private static final double RANGE      = 80.0;
    private static final double HIT_RADIUS = 0.35;
    private static final int    MAG_SIZE   = 36;
    private static final long   RELOAD_MS  = 2500L;
    private static final long   FIRE_MS    = 400L;

    private final Set<UUID>          reloading = new HashSet<>();
    private final Map<UUID, Long>    lastFired = new HashMap<>();
    private final Map<UUID, Long>    lastInteract = new HashMap<>();
    private final Set<UUID>          scoped    = new HashSet<>();

    public BR75() {
        super("§bBR75", Material.DIAMOND_HOE,
                List.of("§7Precision Burst Rifle",
                        "§73-round burst | Damage: §c11",
                        "§7Effective Range: §b~80 blocks",
                        "§8Left-click: Scope  |  Right-click: Fire  |  Shift Drop: Reload"),
                Collections.emptyMap(), "br75");
    }

    private JavaPlugin pl()               { return JavaPlugin.getProvidingPlugin(BR75.class); }
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
        String s = "§b" + "█".repeat(filled) + "§8" + "█".repeat(10 - filled) + " §f" + a + "§7/" + MAG_SIZE;
        p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s));
    }

    private void applyScope(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,    999999, 2,   false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,999999, 0,   false, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 1.2f);
    }

    private void removeScope(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 1f, 1.2f);
    }

    private void reload(Player p, ItemStack stack) {
        if (isReloading(p)) return;
        if (getAmmo(stack) == MAG_SIZE) { p.sendMessage("§bMagazine already full!"); return; }
        reloading.add(p.getUniqueId());
        p.sendMessage("§bReloading... §7(2.5s)");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.5f);
        new BukkitRunnable() { @Override public void run() {
            reloading.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            ItemStack cur = p.getInventory().getItemInMainHand();
            if (isThisItem(cur)) {
                setAmmo(cur, MAG_SIZE);
                p.sendMessage("§aReloaded! §7[" + MAG_SIZE + "/" + MAG_SIZE + "]");
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.7f); bar(p, cur);
            } else {
                setAmmo(stack, MAG_SIZE);
            }}}.runTaskLater(pl(), RELOAD_MS / 50L);
    }

    private void tryFire(Player p, ItemStack stack) {
        if (isReloading(p)) { p.sendMessage("§cReloading..."); return; }
        if (onCooldown(p)) return;
        if (getAmmo(stack) < 3) { p.sendMessage("§c*click* §7Low Ammo! §b(Shift Drop to reload)"); p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 2f); return; }
        doBurst(p, stack);
    }

    private void doBurst(Player p, ItemStack stack) {
        lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        new BukkitRunnable() {
            int shots = 0;
            @Override public void run() {
                if (!p.isOnline() || !isThisItem(p.getInventory().getItemInMainHand()) || shots >= 3) { cancel(); return; }
                fireBullet(p, stack);
                shots++;
            }
        }.runTaskTimer(pl(), 0L, 2L);
    }

    private void fireBullet(Player p, ItemStack stack) {
        if (getAmmo(stack) <= 0) return;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        p.getWorld().playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.8f);

        boolean hit = false;
        for (double i = 0.5; i < RANGE; i += 0.4) {
            Location loc = eye.clone().add(dir.clone().multiply(i));
            if (loc.getBlock().getType().isSolid()) break;
            if (i % 2.0 < 0.4) p.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0);
            for (Entity e : p.getWorld().getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                if (e instanceof LivingEntity t && e != p) {
                    double falloff = 1.0 - (i / RANGE) * 0.4;
                    double damage = DAMAGE * falloff;
                    if (Math.abs(loc.getY() - t.getEyeLocation().getY()) < 0.45) damage *= 1.8;

                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(p.getUniqueId());
                    t.setNoDamageTicks(0);
                    t.damage(damage, p);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(p.getUniqueId());

                    BlockData blood = Material.REDSTONE_BLOCK.createBlockData();
                    loc.getWorld().spawnParticle(Particle.BLOCK, loc, 10, 0.1, 0.1, 0.1, blood);
                    hit = true; break;
                }
            }
            if (hit) break;
        }
        setAmmo(stack, getAmmo(stack) - 1); bar(p, stack);
        if (getAmmo(stack) == 0) reload(p, stack);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack stack = p.getInventory().getItemInMainHand();
        if (!isThisItem(stack)) return;
        Action a = e.getAction();
        boolean left  = a == Action.LEFT_CLICK_AIR  || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (left || right) e.setCancelled(true);
        if (right) {
            long now = p.getServer().getCurrentTick();
            long last = lastInteract.getOrDefault(p.getUniqueId(), 0L);
            lastInteract.put(p.getUniqueId(), now);
            if (now - last <= 3) return; // Block hold-to-fire (Minecraft repeat is 4 ticks)
            tryFire(p, p.getInventory().getItemInMainHand());
        }
        if (left) {
            UUID uid = p.getUniqueId();
            if (scoped.contains(uid)) { scoped.remove(uid); removeScope(p); }
            else { scoped.add(uid); applyScope(p); }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack stack = e.getItemDrop().getItemStack();
        if (!isThisItem(stack)) return;
        if (!e.getPlayer().isSneaking()) return;
        e.setCancelled(true); reload(e.getPlayer(), stack);
    }
}
