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
 * Kar98k — Classic Bolt-Action Sniper
 * Damage: 55 base / 90 vs targets below 40% HP | Mag: 5 | Reload: 5.0s | Range: 130
 * Left-click: toggle scope | Right-click: Fire | Shift Drop: Reload
 */
public class Kar98k extends CorovaItems implements Listener {

    private static final double DAMAGE_BASE   = 65.0;
    private static final double DAMAGE_FINISH = 100.0;
    private static final double FINISH_THRESH = 0.40;
    private static final double RANGE         = 130.0;
    private static final double HIT_RADIUS    = 0.32;
    private static final int    MAG_SIZE      = 5;
    private static final long   RELOAD_MS     = 5000L;
    private static final long   FIRE_MS       = 2000L;

    private final Set<UUID>          reloading = new HashSet<>();
    private final Map<UUID, Long>    lastFired = new HashMap<>();
    private final Set<UUID>          scoped    = new HashSet<>();

    public Kar98k() {
        super("§7Kar98k", Material.CROSSBOW,
                List.of("§7Classic Bolt-Action Sniper",
                        "§7Damage: §c130 §7(§4200 §7vs low-HP) §7| Mag: §e5 §7| Reload: §a5.0s",
                        "§7Bolt cycle: §d2s §7| Range: §b130",
                        "§8Left-click: Scope  |  Right-click: Fire  |  Shift Drop: Reload"),
                Collections.emptyMap(), "kar98k");
    }

    private JavaPlugin pl()               { return JavaPlugin.getProvidingPlugin(Kar98k.class); }
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

    private void applyScope(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,    999999, 4,   false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,999999, 255, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,999999, 0,   false, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 0.9f);
    }

    private void removeScope(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.SLOW_FALLING);
        p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 1f, 0.9f);
    }

    private void reload(Player p, ItemStack stack) {
        if (isReloading(p)) return;
        if (getAmmo(stack) == MAG_SIZE) { p.sendMessage("§eMagazine already full!"); return; }
        reloading.add(p.getUniqueId());
        p.sendMessage("§eReloading... §7(5.0s)");
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
        if (getAmmo(stack) <= 0) { p.sendMessage("§c*click* §7Empty! §e(Shift Drop to reload)"); p.getWorld().playSound(p.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 2f); return; }
        doShot(p, stack);
    }

    private void doShot(Player p, ItemStack stack) {
        if (getAmmo(stack) <= 0) return;
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        p.getWorld().playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.55f);
        boolean hit = false;
        for (double i = 0; i < RANGE; i += 0.4) {
            Location loc = eye.clone().add(dir.clone().multiply(i));
            if (loc.getBlock().getType().isSolid()) break;
            if (i % 4.0 < 0.4) p.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0);
            for (Entity e : p.getWorld().getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                if (e instanceof LivingEntity t && e != p) {
                    double ratio = t.getHealth() / t.getMaxHealth();
                    double dmg = (ratio <= FINISH_THRESH) ? DAMAGE_FINISH : DAMAGE_BASE;
                    t.setNoDamageTicks(0);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(p.getUniqueId());
                    t.setNoDamageTicks(0);
                    t.damage(dmg, p);
                    com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(p.getUniqueId());
                    if (ratio <= FINISH_THRESH) {
                        p.sendMessage("§6★ Finish shot! §f(" + (int) dmg + " dmg)");
                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                    }
                    BlockData blood = Material.REDSTONE_BLOCK.createBlockData();
                    Objects.requireNonNull(loc.getWorld()).spawnParticle(Particle.BLOCK, loc, 20, 0.2, 0.2, 0.2, blood);
                    hit = true; break;
                }
            }
            if (hit) break;
        }
        setAmmo(stack, getAmmo(stack) - 1); lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        bar(p, stack);
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
        if (right) tryFire(p, p.getInventory().getItemInMainHand());
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