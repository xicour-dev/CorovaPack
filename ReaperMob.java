package com.example.corovamobs.mobs;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.Weapons.ReapersScythe;
import com.example.corovamobs.spawnsystems.GeneralSpawnSystem;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ReaperMob extends AbstractCustomMob {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long ABILITY_COOLDOWN = 5000; // 5 seconds
    private static final Random random = new Random();

    // Melee reach — 7.5 squared so the scythe connects reliably
    private static final double ATTACK_RANGE_SQ = 7.5;
    private static final long MELEE_COOLDOWN_MS = 1000L;
    private static final double MELEE_DAMAGE = 10.0;

    public ReaperMob(GeneralSpawnSystem spawnSystem) {
        super("reaper", spawnSystem);
    }

    @Override
    public LivingEntity spawn(World world, double x, double y, double z) {
        Location spawnLoc = new Location(world, x + 0.5, y, z + 0.5);

        WitherSkeleton reaper = (WitherSkeleton) world.spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);

        NamespacedKey reaperKey = new NamespacedKey("corovamobs", "reaper");
        reaper.getPersistentDataContainer().set(reaperKey, PersistentDataType.BYTE, (byte) 1);
        reaper.addScoreboardTag("reaper");

        applyAttributes(reaper);

        try {
            spawnSystem.registerEntity(reaper, this.getId());
        } catch (Throwable ignored) {}

        startVexShootingTask(reaper);
        startAttackTask(reaper);

        return reaper;
    }

    @Override
    public void applyAttributes(LivingEntity entity) {
        if (!(entity instanceof WitherSkeleton reaper)) return;

        reaper.setCustomName("§cReaper");
        reaper.setCustomNameVisible(false);
        reaper.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150.0);
        reaper.setHealth(150.0);

        ItemManager itemManager = ItemManager.getInstance();
        if (itemManager.getItemById("reapersscythe") == null) {
            new ReapersScythe();
        }
        ItemStack scythe = itemManager.getItemById("reapersscythe").getItemStack();
        reaper.getEquipment().setItemInMainHand(scythe);

        List<TrimMaterial> materials = new ArrayList<>();
        Registry.TRIM_MATERIAL.forEach(materials::add);
        TrimMaterial trimMaterial = materials.get(random.nextInt(materials.size()));

        List<TrimPattern> patterns = new ArrayList<>();
        Registry.TRIM_PATTERN.forEach(patterns::add);
        TrimPattern trimPattern = patterns.get(random.nextInt(patterns.size()));

        ArmorTrim trim = new ArmorTrim(trimMaterial, trimPattern);

        reaper.getEquipment().setHelmet(createLeatherArmor(Material.LEATHER_HELMET, trim));
        reaper.getEquipment().setChestplate(createLeatherArmor(Material.LEATHER_CHESTPLATE, trim));
        reaper.getEquipment().setLeggings(createLeatherArmor(Material.LEATHER_LEGGINGS, trim));
        reaper.getEquipment().setBoots(createLeatherArmor(Material.LEATHER_BOOTS, trim));
    }

    private ItemStack createLeatherArmor(Material material, ArmorTrim trim) {
        ItemStack armor = new ItemStack(material);
        LeatherArmorMeta leatherMeta = (LeatherArmorMeta) armor.getItemMeta();
        leatherMeta.setColor(Color.BLACK);
        armor.setItemMeta(leatherMeta);

        ArmorMeta armorMeta = (ArmorMeta) armor.getItemMeta();
        armorMeta.setTrim(trim);
        armor.setItemMeta(armorMeta);

        return armor;
    }

    /**
     * Manual melee loop — gives the Reaper a reliable 7.5-squared reach so
     * it lands scythe hits even when vanilla sweep misses.
     */
    private void startAttackTask(WitherSkeleton reaper) {
        final long[] lastMelee = {0L};

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!reaper.isValid() || reaper.isDead()) {
                    cancel();
                    return;
                }
                LivingEntity target = reaper.getTarget();
                if (target == null || !target.isValid() || target.isDead()) return;

                double dist2 = reaper.getLocation().distanceSquared(target.getLocation());
                if (dist2 <= ATTACK_RANGE_SQ) {
                    long now = System.currentTimeMillis();
                    if (now - lastMelee[0] >= MELEE_COOLDOWN_MS) {
                        lastMelee[0] = now;
                        target.damage(MELEE_DAMAGE, reaper);
                    }
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(ReapersScythe.class), 0L, 2L);
    }

    private void startVexShootingTask(WitherSkeleton reaper) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (reaper == null || !reaper.isValid() || reaper.isDead()) {
                    cooldowns.remove(reaper.getUniqueId());
                    this.cancel();
                    return;
                }

                long lastTime = cooldowns.getOrDefault(reaper.getUniqueId(), 0L);
                if (System.currentTimeMillis() - lastTime < ABILITY_COOLDOWN) {
                    return;
                }

                LivingEntity target = reaper.getTarget();
                if (target != null && target.isValid() && !target.isDead()) {
                    cooldowns.put(reaper.getUniqueId(), System.currentTimeMillis());
                    startWarnThenShoot(reaper, target);
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(ReapersScythe.class), 20L, 20L);
    }

    /**
     * 1.5-second visual/audio warning before firing the vex soul.
     * Red dust particles swirl around the reaper; a rising-pitch note block hat
     * ramps up each tick to signal the shot is incoming.
     */
    private void startWarnThenShoot(WitherSkeleton reaper, LivingEntity originalTarget) {
        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (reaper == null || !reaper.isValid() || reaper.isDead()) {
                    this.cancel();
                    return;
                }

                // Swirling red dust around reaper body
                double angle = tick * 0.42;
                double orbitRadius = 0.9;
                double px = reaper.getLocation().getX() + orbitRadius * Math.cos(angle);
                double py = reaper.getLocation().getY() + 1.3;
                double pz = reaper.getLocation().getZ() + orbitRadius * Math.sin(angle);
                Location particleLoc = new Location(reaper.getWorld(), px, py, pz);

                Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.3f);
                reaper.getWorld().spawnParticle(Particle.DUST, particleLoc, 4, 0.04, 0.04, 0.04, 0, redDust);

                // Rising-pitch click to telegraph the attack
                float pitch = 0.5f + (tick / 30f) * 1.5f;
                reaper.getWorld().playSound(reaper.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, pitch);

                tick++;
                if (tick >= 30) { // 30 ticks = 1.5 seconds
                    this.cancel();
                    // Fire at current target, fall back to original
                    LivingEntity currentTarget = reaper.getTarget();
                    LivingEntity fireTarget = (currentTarget != null && currentTarget.isValid() && !currentTarget.isDead())
                            ? currentTarget : originalTarget;
                    if (fireTarget.isValid() && !fireTarget.isDead()) {
                        shootVexAt(reaper, fireTarget);
                    }
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(ReapersScythe.class), 0L, 1L);
    }

    private void shootVexAt(WitherSkeleton reaper, LivingEntity target) {
        Vex vex = reaper.getWorld().spawn(reaper.getEyeLocation(), Vex.class);
        vex.setCustomName("ReapersScytheSoul:" + reaper.getUniqueId());
        vex.setCustomNameVisible(false);
        vex.setSilent(true);
        vex.setGravity(false);
        vex.setAware(false);
        vex.setInvulnerable(true);

        reaper.getWorld().playSound(reaper.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 1f);

        Vector direction = target.getEyeLocation().toVector().subtract(reaper.getEyeLocation().toVector()).normalize();
        Vector velocity = direction.multiply(1.5);
        vex.setVelocity(velocity);

        final UUID vexUUID = vex.getUniqueId();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Entity entity = Bukkit.getEntity(vexUUID);

                if (entity == null || !entity.isValid() || entity.isDead() || !(entity instanceof Vex)) {
                    this.cancel();
                    return;
                }

                Vex vex = (Vex) entity;

                if (vex.getLocation().getBlock().getType().isSolid()) {
                    vex.remove();
                    this.cancel();
                    return;
                }
                ticks++;

                if (ticks >= 30) {
                    vex.remove();
                    this.cancel();
                    return;
                }

                vex.setVelocity(velocity);

                for (Entity nearbyEntity : vex.getNearbyEntities(0.5, 0.5, 0.5)) {
                    if (!(nearbyEntity instanceof LivingEntity)) continue;
                    if (nearbyEntity.equals(reaper)) continue;
                    if (nearbyEntity instanceof Vex) continue;

                    LivingEntity hitTarget = (LivingEntity) nearbyEntity;

                    if (hitTarget instanceof Player victim) {
                        if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                            vex.remove();
                            this.cancel();
                            return;
                        }
                    }

                    hitTarget.damage(16.0, reaper);
                    vex.remove();
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(ReapersScythe.class), 0L, 1L);
    }
}