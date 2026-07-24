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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * SPAS-12 — Semi-Auto Shotgun
 * Buffed for high lethality and fast semi-auto fire.
 */
public class SPAS12 extends CorovaItems implements Listener {

    private static final int    PELLETS    = 10;
    private static final double DAMAGE     = 5.0;
    private static final double RANGE      = 22.0;
    private static final double SPREAD     = 0.24;
    private static final double HIT_RADIUS = 0.45;
    private static final int    MAG_SIZE   = 8;
    private static final long   RELOAD_MS  = 4000L;
    private static final long   FIRE_MS    = 450L;

    private final Set<UUID>          reloading = new HashSet<>();
    private final Map<UUID, Long>    lastFired = new HashMap<>();
    private final Random             rng       = new Random();

    public SPAS12() {
        super("§cSPAS-12", Material.WOODEN_HOE,
                List.of("§7Semi-Auto Shotgun",
                        "§710 pellets | Damage: §c10 §7| Mag: §e8",
                        "§7Fire Rate: §dfaster than pump §7| Range: §b~22",
                        "§8Right-click: Fire  |  Shift Drop: Reload"),
                Collections.emptyMap(), "spas12");
    }

    private JavaPlugin pl()               { return JavaPlugin.getProvidingPlugin(SPAS12.class); }
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
        if (getAmmo(stack) == MAG_SIZE) { p.sendMessage("§eTube already full!"); return; }
        reloading.add(p.getUniqueId());
        p.sendMessage("§eReloading... §7(4.0s)");
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
        Map<UUID, Double> aggregatedDamage = new HashMap<>();

        for (int pellet = 0; pellet < PELLETS; pellet++) {
            Vector dir = eye.getDirection().clone().normalize();
            dir.add(new Vector((rng.nextDouble() - .5) * SPREAD, (rng.nextDouble() - .5) * SPREAD, (rng.nextDouble() - .5) * SPREAD)).normalize();
            boolean pelletHit = false;
            for (double i = 0.5; i < RANGE; i += 0.3) {
                Location loc = eye.clone().add(dir.clone().multiply(i));
                if (loc.getBlock().getType().isSolid()) break;
                if (i < 12) p.getWorld().spawnParticle(Particle.SMOKE, loc, 1, 0, 0, 0, 0);
                for (Entity e : p.getWorld().getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                    if (e instanceof LivingEntity t && e != p) {
                        double falloff = 1.0 - (i / RANGE) * 0.6;
                        double d = DAMAGE * falloff;
                        if (Math.abs(loc.getY() - t.getEyeLocation().getY()) < 0.5) d *= 2.5;

                        aggregatedDamage.put(e.getUniqueId(), aggregatedDamage.getOrDefault(e.getUniqueId(), 0.0) + d);

                        BlockData blood = Material.REDSTONE_BLOCK.createBlockData();
                        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 4, 0.1, 0.1, 0.1, blood);
                        pelletHit = true; break;
                    }
                }
                if (pelletHit) break;
            }
        }

        aggregatedDamage.forEach((uuid, dmg) -> {
            Entity e = p.getServer().getEntity(uuid);
            if (e instanceof LivingEntity t) {
                t.setNoDamageTicks(0);
                com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.add(p.getUniqueId());
                t.setNoDamageTicks(0);
                t.damage(dmg, p);
                com.example.corovaItems.WeaponProperties.GunCombat.firingGuns.remove(p.getUniqueId());
            }
        });

        eye.getWorld().spawnParticle(Particle.FLAME, eye.clone().add(eye.getDirection().multiply(1.5)), 5, 0.05, 0.05, 0.05, 0.02);

        setAmmo(stack, getAmmo(stack) - 1); lastFired.put(p.getUniqueId(), System.currentTimeMillis());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f); bar(p, stack);
        if (getAmmo(stack) == 0) reload(p, stack);
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