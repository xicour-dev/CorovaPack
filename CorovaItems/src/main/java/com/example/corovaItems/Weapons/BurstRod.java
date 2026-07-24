package com.example.corovaItems.Weapons;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.MageSystem.ManaManager;
import net.corova.chronicles.generated.CorovaCustomModelData;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Burst Rod — holds right-click to fire one fireball per tick from the
 * centre of the player's view.
 *
 * Mana integration: each server tick of firing costs
 * {@link ManaManager#COST_BURST_ROD_TICK} mana. Firing stops automatically
 * the moment the player runs out of mana. The mana BossBar is managed by
 * {@link ManaManager} and is shown whenever the player holds the rod.
 */
public class BurstRod extends CorovaItems implements Listener {

    private static BurstRod instance;

    // ── Per-player state ──────────────────────────────────────────────────────
    /** Active firing runnables, one per player. */
    private static final Map<UUID, BukkitTask> firingTasks    = new HashMap<>();
    /** Last System.currentTimeMillis() at which the player right-clicked. */
    private static final Map<UUID, Long>        lastInteraction = new HashMap<>();

    /** Stop firing if the player hasn't right-clicked within this window. */
    private static final long INTERACTION_TIMEOUT_MS = 500L;
    private static final int MAX_DURABILITY = 1000;

    private static final NamespacedKey BURST_ROD_FIREBALL_KEY =
            new NamespacedKey("corovaitems", "burst_rod_fireball");
    private static final NamespacedKey UNIQUE_ID_KEY =
            new NamespacedKey("corovaitems", "unique_id");

    private static final NamespacedKey SPAWN_X_KEY =
            new NamespacedKey("corovaitems", "spawn_x");
    private static final NamespacedKey SPAWN_Y_KEY =
            new NamespacedKey("corovaitems", "spawn_y");
    private static final NamespacedKey SPAWN_Z_KEY =
            new NamespacedKey("corovaitems", "spawn_z");
    private static final NamespacedKey SPAWN_WORLD_KEY =
            new NamespacedKey("corovaitems", "spawn_world");

    private static final double MAX_DISTANCE_SQUARED = 100.0 * 100.0;

    private final JavaPlugin plugin;
    private final Random     random = new Random();

    // ── Constructor ───────────────────────────────────────────────────────────
    public BurstRod() {
        super(
                ChatColor.GOLD + "Burst Rod",
                new Material[]{Material.matchMaterial("COPPER_SPEAR")},
                lore(),
                enchantments(),
                "burstrod",
                CorovaCustomModelData.WEAPONS_BURST_ROD,
                Collections.emptyMap()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(getClass());
        ItemManager.getInstance().registerItem(this);
        instance = this;
    }

    private static List<String> lore() {
        return Collections.singletonList(ChatColor.RED + "Fire Burst I");
    }

    private static Map<org.bukkit.enchantments.Enchantment, Integer> enchantments() {
        return new HashMap<>();
    }

    // ── Right-click → start firing ────────────────────────────────────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack item   = player.getInventory().getItemInMainHand();
        if (!isThisItem(item)) return;

        event.setCancelled(true);

        // Stamp the unique-ID once so stacked rods get individual identities.
        ItemMeta meta = item.getItemMeta();
        if (meta != null
                && !meta.getPersistentDataContainer()
                .has(UNIQUE_ID_KEY, PersistentDataType.STRING)) {
            meta.getPersistentDataContainer()
                    .set(UNIQUE_ID_KEY, PersistentDataType.STRING,
                            UUID.randomUUID().toString());

            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(MAX_DURABILITY);
            }

            item.setItemMeta(meta);
        }

        lastInteraction.put(player.getUniqueId(), System.currentTimeMillis());
        startFiring(player);
    }

    // ── Firing task ───────────────────────────────────────────────────────────
    private void startFiring(Player player) {
        UUID id = player.getUniqueId();

        // Already firing — just refresh the interaction timestamp and return.
        if (firingTasks.containsKey(id)) {
            lastInteraction.put(id, System.currentTimeMillis());
            return;
        }

        ManaManager mana = ManaManager.getInstance();
        if (mana == null) return;

        // Require at least one tick's worth of mana before starting.
        if (mana.getMana(player) < ManaManager.COST_BURST_ROD_TICK) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Not enough mana!");
            return;
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { stopFiring(player); return; }

                if (!isThisItem(player.getInventory().getItemInMainHand())) {
                    stopFiring(player);
                    return;
                }

                // Stop if the player has released right-click (timeout).
                long lastTime = lastInteraction.getOrDefault(id, 0L);
                if (System.currentTimeMillis() - lastTime > INTERACTION_TIMEOUT_MS) {
                    stopFiring(player);
                    return;
                }

                // Stop if mana is exhausted.
                if (!mana.tryConsumeMana(player, ManaManager.COST_BURST_ROD_TICK)) {
                    stopFiring(player);
                    return;
                }

                shootFireball(player);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        firingTasks.put(id, task);
    }

    private void stopFiring(Player player) {
        BukkitTask task = firingTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
    }

    // ── Fireball helpers ──────────────────────────────────────────────────────
    private void shootFireball(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getItemMeta() instanceof Damageable damageable) {
            damageable.setMaxDamage(MAX_DURABILITY);
            damageable.setDamage(damageable.getDamage() + 1);

            if (damageable.getDamage() >= MAX_DURABILITY) {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                item.setAmount(item.getAmount() - 1);
                stopFiring(player);
            } else {
                item.setItemMeta(damageable);
            }
        }

        Location eyeLocation = player.getEyeLocation();
        Vector   forward     = eyeLocation.getDirection().normalize();

        // Spawn a single fireball 1.2 blocks ahead of the player's eye,
        // centred on their aim direction.
        Location spawnLoc = eyeLocation.clone().add(forward.clone().multiply(1.2));

        double cone = 0.1;
        spawnFireball(spawnLoc, randomizeDirection(forward, cone), player);

        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.0f);
    }

    private Vector randomizeDirection(Vector direction, double cone) {
        double x = direction.getX() + (random.nextDouble() - 0.5) * cone;
        double y = direction.getY() + (random.nextDouble() - 0.5) * cone;
        double z = direction.getZ() + (random.nextDouble() - 0.5) * cone;
        return new Vector(x, y, z).normalize();
    }

    private void startDistanceTracker(SmallFireball fireball, Location spawnLoc) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!fireball.isValid() || fireball.isDead()) {
                    this.cancel();
                    return;
                }

                Location current = fireball.getLocation();
                if (current.getWorld() == null || !current.getWorld().equals(spawnLoc.getWorld())
                        || current.distanceSquared(spawnLoc) > MAX_DISTANCE_SQUARED) {
                    fireball.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void spawnFireball(Location loc, Vector dir, Player shooter) {
        SmallFireball ball = shooter.getWorld().spawn(loc, SmallFireball.class, f -> {
            f.setShooter(shooter);
            f.setIsIncendiary(false);
            f.setYield(0f);
            PersistentDataContainer pdc = f.getPersistentDataContainer();
            pdc.set(BURST_ROD_FIREBALL_KEY, PersistentDataType.BYTE, (byte) 1);
            pdc.set(SPAWN_X_KEY, PersistentDataType.DOUBLE, loc.getX());
            pdc.set(SPAWN_Y_KEY, PersistentDataType.DOUBLE, loc.getY());
            pdc.set(SPAWN_Z_KEY, PersistentDataType.DOUBLE, loc.getZ());
            if (loc.getWorld() != null) {
                pdc.set(SPAWN_WORLD_KEY, PersistentDataType.STRING, loc.getWorld().getName());
            }
        });
        ball.setVelocity(dir.multiply(1.5));
        startDistanceTracker(ball, loc);
    }

    // ── Damage events ─────────────────────────────────────────────────────────
    @EventHandler
    public void onDirectFireballHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof SmallFireball fireball)) return;
        if (!fireball.getPersistentDataContainer()
                .has(BURST_ROD_FIREBALL_KEY, PersistentDataType.BYTE)) return;

        if (event.getEntity() instanceof Player victim) {
            if (fireball.getShooter() instanceof Player shooter) {
                if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                    CorovaGuard.sendSafeZoneMessage(shooter);
                    event.setCancelled(true);
                    return;
                }
                CorovaTeams teamsInstance = CorovaTeams.getInstance();
                if (teamsInstance != null) {
                    CorovaTeam shooterTeam = teamsInstance.getTeamManager()
                            .getTeamByPlayer(shooter.getUniqueId());
                    CorovaTeam victimTeam  = teamsInstance.getTeamManager()
                            .getTeamByPlayer(victim.getUniqueId());
                    if (shooterTeam != null && shooterTeam.equals(victimTeam)
                            && !shooterTeam.hasFriendlyFire()) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof SmallFireball ball)) return;
        if (!ball.getPersistentDataContainer()
                .has(BURST_ROD_FIREBALL_KEY, PersistentDataType.BYTE)) return;
        if (!(ball.getShooter() instanceof Player shooter)) {
            ball.remove();
            return;
        }

        if (ball.getLocation().getWorld() != null) {
            boolean sentMessage = false;
            for (Entity hit : ball.getNearbyEntities(1.5, 1.5, 1.5)) {
                if (!(hit instanceof LivingEntity le) || le.equals(shooter)) continue;

                if (le instanceof Player victim) {
                    if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                        if (!sentMessage) {
                            CorovaGuard.sendSafeZoneMessage(shooter);
                            sentMessage = true;
                        }
                        continue;
                    }
                    CorovaTeams teamsInstance = CorovaTeams.getInstance();
                    if (teamsInstance != null) {
                        CorovaTeam shooterTeam = teamsInstance.getTeamManager()
                                .getTeamByPlayer(shooter.getUniqueId());
                        CorovaTeam victimTeam  = teamsInstance.getTeamManager()
                                .getTeamByPlayer(victim.getUniqueId());
                        if (shooterTeam != null && shooterTeam.equals(victimTeam)
                                && !shooterTeam.hasFriendlyFire()) continue;
                    }
                }
                le.damage(45, shooter);
            }
        }
        ball.remove();
    }

    // ── Lifecycle events ──────────────────────────────────────────────────────
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof SmallFireball fireball)) continue;
            PersistentDataContainer pdc = fireball.getPersistentDataContainer();
            if (!pdc.has(BURST_ROD_FIREBALL_KEY, PersistentDataType.BYTE)) continue;

            // Fix the bug where reloaded fireballs start fires
            fireball.setIsIncendiary(false);
            fireball.setYield(0f);

            if (pdc.has(SPAWN_X_KEY, PersistentDataType.DOUBLE) &&
                    pdc.has(SPAWN_Y_KEY, PersistentDataType.DOUBLE) &&
                    pdc.has(SPAWN_Z_KEY, PersistentDataType.DOUBLE) &&
                    pdc.has(SPAWN_WORLD_KEY, PersistentDataType.STRING)) {

                double x = pdc.get(SPAWN_X_KEY, PersistentDataType.DOUBLE);
                double y = pdc.get(SPAWN_Y_KEY, PersistentDataType.DOUBLE);
                double z = pdc.get(SPAWN_Z_KEY, PersistentDataType.DOUBLE);
                String worldName = pdc.get(SPAWN_WORLD_KEY, PersistentDataType.STRING);
                World world = Bukkit.getWorld(worldName);

                if (world != null) {
                    Location spawnLoc = new Location(world, x, y, z);
                    if (fireball.getLocation().getWorld().equals(world) &&
                            fireball.getLocation().distanceSquared(spawnLoc) <= MAX_DISTANCE_SQUARED) {
                        startDistanceTracker(fireball, spawnLoc);
                        continue;
                    }
                }
            }
            fireball.remove();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopFiring(event.getPlayer());
        lastInteraction.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Stop firing when switching away from the rod.
        ItemStack prev = player.getInventory().getItem(event.getPreviousSlot());
        if (prev != null && isThisItem(prev)) {
            stopFiring(player);
        }
    }

    @EventHandler
    public void onPlayerDrop(PlayerDropItemEvent event) {
        if (isThisItem(event.getItemDrop().getItemStack())) {
            stopFiring(event.getPlayer());
            lastInteraction.remove(event.getPlayer().getUniqueId());
        }
    }
}