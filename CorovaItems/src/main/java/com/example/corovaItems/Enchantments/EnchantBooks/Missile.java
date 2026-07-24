package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

public class Missile extends EnchantmentBook implements Listener {

    private static final Map<UUID, Arrow> activeMissiles = new HashMap<>();
    private static final Map<UUID, Long> missileTimers = new HashMap<>();
    private static final Map<UUID, LivingEntity> missileTargets = new HashMap<>();
    private static final Map<UUID, Long> lockTimes = new HashMap<>();
    private static final Map<UUID, List<UUID>> playerMissiles = new HashMap<>();
    private static final Map<UUID, UUID> arrowToPlayer = new HashMap<>();

    private static NamespacedKey SHOOTER_KEY;
    private static NamespacedKey POWER_KEY;
    private static NamespacedKey SHARPNESS_KEY;
    private static NamespacedKey SMITE_KEY;
    private static NamespacedKey BANE_KEY;

    private static int ticks = 0;

    public Missile() {
        this(1);
    }

    public Missile(int level) {
        super(
                "Book of Missile",
                CorovaEnchantments.MISSILE_ID,
                level,
                "book_missile",
                Set.of(Material.BOW)
        );
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(Missile.class);
        if (SHOOTER_KEY == null) {
            SHOOTER_KEY   = new NamespacedKey(plugin, "missile_shooter");
            POWER_KEY     = new NamespacedKey(plugin, "missile_power");
            SHARPNESS_KEY = new NamespacedKey(plugin, "missile_sharpness");
            SMITE_KEY     = new NamespacedKey(plugin, "missile_smite");
            BANE_KEY      = new NamespacedKey(plugin, "missile_bane");
        }
        ItemManager.getInstance().registerItem(this);
    }

    public static void startTask(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Arrow> entry : new HashMap<>(activeMissiles).entrySet()) {
                UUID arrowUuid = entry.getKey();
                Arrow arrow = entry.getValue();

                if (!arrow.isValid() || arrow.isDead()) {
                    cleanupMissile(arrowUuid);
                    continue;
                }

                long elapsed = System.currentTimeMillis() - missileTimers.getOrDefault(arrowUuid, 0L);
                if (elapsed > 35000) {
                    arrow.setGravity(true);
                    arrow.removeMetadata("corova_missile", plugin);
                    cleanupMissile(arrowUuid);
                    continue;
                }

                Location loc = arrow.getLocation();
                loc.getWorld().spawnParticle(Particle.FLAME, loc, 5, 0.1, 0.1, 0.1, 0.05);
                loc.getWorld().spawnParticle(Particle.SMOKE, loc, 5, 0.1, 0.1, 0.1, 0.05);

                LivingEntity target = missileTargets.get(arrowUuid);
                if (target != null && target.isValid()) {
                    long lockElapsed = System.currentTimeMillis() - lockTimes.getOrDefault(arrowUuid, 0L);
                    if (lockElapsed < 1500) {
                        // Warning phase
                        if (target instanceof Player targetPlayer) {
                            targetPlayer.playSound(targetPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
                            String missileText = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.MISSILE_ID, "MISSILE");
                            targetPlayer.sendTitle("", ChatColor.RED + "⚠ " + missileText + ChatColor.RED + " LOCK ⚠", 0, 5, 0);
                        }
                        Vector dir = target.getEyeLocation().toVector().subtract(loc.toVector()).normalize();
                        arrow.setVelocity(dir.multiply(0.3));
                    } else {
                        // Strike phase
                        if (ticks % 5 == 0 && target instanceof Player targetPlayer) {
                            targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        }
                        Vector dir = target.getEyeLocation().toVector().subtract(loc.toVector()).normalize();
                        arrow.setVelocity(dir.multiply(2.5));
                    }
                } else if (elapsed < 3000) {
                    // Refined ascent animation: starts slow and accelerates
                    double t = elapsed / 3000.0;
                    double speed = 0.1 + (0.9 * t * t); // Accelerating upwards
                    arrow.setVelocity(new Vector(0, speed, 0));
                } else {
                    // Loiter: larger circle
                    double angle = ticks / 8.0;
                    double radius = 0.6;
                    arrow.setVelocity(new Vector(Math.sin(angle) * radius, 0.05, Math.cos(angle) * radius));
                }
            }
            ticks++;
        }, 1L, 1L);
    }

    private static void cleanupMissile(UUID arrowUuid) {
        activeMissiles.remove(arrowUuid);
        missileTimers.remove(arrowUuid);
        missileTargets.remove(arrowUuid);
        lockTimes.remove(arrowUuid);
        UUID pUuid = arrowToPlayer.remove(arrowUuid);
        if (pUuid != null) {
            List<UUID> list = playerMissiles.get(pUuid);
            if (list != null) {
                list.remove(arrowUuid);
                if (list.isEmpty()) playerMissiles.remove(pUuid);
            }
        }
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getForce() < 1.0f) return;

        ItemStack bow = event.getBow();
        if (bow == null || !CorovaEnchantments.hasEnchant(bow, CorovaEnchantments.MISSILE_ID)) return;

        if (!(event.getProjectile() instanceof Arrow arrow)) return;

        int level = CorovaEnchantments.getEnchantLevel(bow, CorovaEnchantments.MISSILE_ID);
        int maxMissiles = (level == 2 || level == 3) ? 1 : level;
        List<UUID> missiles = playerMissiles.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());

        while (missiles.size() >= maxMissiles) {
            UUID oldest = missiles.get(0);
            Arrow oldArrow = activeMissiles.get(oldest);
            if (oldArrow != null) {
                oldArrow.setGravity(true);
                oldArrow.removeMetadata("corova_missile", JavaPlugin.getProvidingPlugin(Missile.class));
            }
            cleanupMissile(oldest);
        }

        UUID arrowUuid = arrow.getUniqueId();
        missiles.add(arrowUuid);
        arrowToPlayer.put(arrowUuid, player.getUniqueId());
        activeMissiles.put(arrowUuid, arrow);
        missileTimers.put(arrowUuid, System.currentTimeMillis());

        arrow.getPersistentDataContainer().set(SHOOTER_KEY,   PersistentDataType.STRING,  player.getUniqueId().toString());
        arrow.getPersistentDataContainer().set(POWER_KEY,     PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.POWER));
        arrow.getPersistentDataContainer().set(SHARPNESS_KEY, PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.SHARPNESS));
        arrow.getPersistentDataContainer().set(SMITE_KEY,     PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.SMITE));
        arrow.getPersistentDataContainer().set(BANE_KEY,      PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS));

        arrow.setMetadata("corova_missile", new FixedMetadataValue(JavaPlugin.getProvidingPlugin(Missile.class), true));
        arrow.setGravity(false);
        arrow.setVelocity(new Vector(0, 0.1, 0)); // Start slow
        String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.MISSILE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.MISSILE_ID, "Missile"));
        player.sendMessage(enchantName + org.bukkit.ChatColor.YELLOW + " launched! (" + missiles.size() + "/" + maxMissiles + ") Left-click to designate a target.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != Material.BOW) return;

        List<UUID> missiles = playerMissiles.get(player.getUniqueId());
        if (missiles == null || missiles.isEmpty()) return;

        // Target designated
        Entity target = getTarget(player);
        if (target instanceof LivingEntity living) {
            for (UUID arrowUuid : missiles) {
                missileTargets.put(arrowUuid, living);
                lockTimes.put(arrowUuid, System.currentTimeMillis());
            }
            String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.MISSILE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.MISSILE_ID, "Missile"));
            player.sendMessage(enchantName + org.bukkit.ChatColor.RED + " target locked for all active missiles!");
            if (living instanceof Player targetPlayer) {
                targetPlayer.sendMessage(org.bukkit.ChatColor.DARK_RED + "WARNING: " + enchantName + org.bukkit.ChatColor.DARK_RED + " lock detected!");
                targetPlayer.playSound(targetPlayer.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            }
        }
    }

    private Entity getTarget(Player player) {
        double maxDist = 100.0;
        Entity target = null;
        double closest = Double.MAX_VALUE;
        for (Entity entity : player.getNearbyEntities(maxDist, maxDist, maxDist)) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                Vector toEntity = entity.getLocation().toVector().subtract(player.getEyeLocation().toVector());
                double dot = toEntity.normalize().dot(player.getEyeLocation().getDirection());
                if (dot > 0.98) {
                    double dist = player.getLocation().distance(entity.getLocation());
                    if (dist < closest) {
                        closest = dist;
                        target = entity;
                    }
                }
            }
        }
        return target;
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        if (!arrow.hasMetadata("corova_missile")) return;

        Location loc = arrow.getLocation();
        World world = loc.getWorld();
        if (world != null) {
            world.createExplosion(loc, 6.0f, false, false);

            int pwr = arrow.getPersistentDataContainer().getOrDefault(POWER_KEY,     PersistentDataType.INTEGER, 0);
            int shp = arrow.getPersistentDataContainer().getOrDefault(SHARPNESS_KEY, PersistentDataType.INTEGER, 0);
            int smt = arrow.getPersistentDataContainer().getOrDefault(SMITE_KEY,     PersistentDataType.INTEGER, 0);
            int bne = arrow.getPersistentDataContainer().getOrDefault(BANE_KEY,      PersistentDataType.INTEGER, 0);
            String sUuidStr = arrow.getPersistentDataContainer().get(SHOOTER_KEY, PersistentDataType.STRING);
            UUID shooterUuid = sUuidStr != null ? UUID.fromString(sUuidStr) : null;
            Entity shooter = shooterUuid != null ? Bukkit.getEntity(shooterUuid) : null;

            double pwrMult = BowDamageScaling.getPowerMultiplier(pwr);
            double radius = 10.0;
            double maxBaseDamage = 24.0;

            for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
                if (entity instanceof LivingEntity living) {
                    if (shooter != null && living.equals(shooter)) continue;

                    double distance = living.getLocation().distance(loc);
                    if (distance <= radius) {
                        double bonus = BowDamageScaling.getEnchantmentDamageBonus(shp, smt, bne, living);
                        double damage = (maxBaseDamage * (1 - (distance / radius)) * pwrMult) + bonus;

                        if (shooter instanceof LivingEntity shooterLiving) {
                            living.damage(damage, shooterLiving);
                        } else if (shooter != null) {
                            living.damage(damage, shooter);
                        } else {
                            living.damage(damage);
                        }
                    }
                }
            }
        }

        cleanupMissile(arrow.getUniqueId());
        arrow.remove();
    }

    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right, NamespacedKey keyId, NamespacedKey keyLvl) {
        if (left == null || right == null) return null;
        ItemMeta leftMeta = left.getItemMeta();
        ItemMeta rightMeta = right.getItemMeta();
        if (!(leftMeta instanceof EnchantmentStorageMeta lMeta && rightMeta instanceof EnchantmentStorageMeta rMeta)) return null;

        String id1 = lMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String id2 = rMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);

        if (id1 == null || !id1.equals(CorovaEnchantments.MISSILE_ID)) return null;
        if (id2 == null || !id2.equals(CorovaEnchantments.MISSILE_ID)) return null;

        int lvl1 = lMeta.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);
        int lvl2 = rMeta.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);

        if (lvl1 == lvl2 && lvl1 < CorovaEnchantments.getMaxLevel(CorovaEnchantments.MISSILE_ID)) {
            return new Missile(lvl1 + 1).getItemStack();
        }
        return null;
    }
}
