package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling;
import com.example.corovaItems.WeaponProperties.CorovaCombat;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class StormBook extends EnchantmentBook implements Listener {

    public static final String STORM_ID     = "storm";
    public static final int    MAX_LEVEL    = 2;
    public static final int    UPGRADE_COST = 10;

    private static NamespacedKey STORM_KEY;
    private static NamespacedKey SHOOTER_KEY;
    private static NamespacedKey POWER_KEY;
    private static NamespacedKey SHARPNESS_KEY;
    private static NamespacedKey SMITE_KEY;
    private static NamespacedKey BANE_KEY;

    private static final Random RANDOM = new Random();

    // Blue colours for the lingering puddle particles (ENTITY_EFFECT + Color)
    private static final Color COLOR_BLUE_BRIGHT = Color.fromRGB(100, 160, 255);
    private static final Color COLOR_BLUE_MID    = Color.fromRGB(80,  120, 240);
    private static final Color COLOR_BLUE_DARK   = Color.fromRGB(60,  100, 220);

    // How many ticks between spawning a new cloud along the arrow trail
    // (20 ticks = 1 second — clouds appear roughly every second of flight)
    private static final int TRAIL_CLOUD_INTERVAL = 20;

    // ─── Server-side water drop ───────────────────────────────────────────────
    // fromCloud = true  → secondary drop emitted by a cloud, lands → puddle only

    private static class WaterDrop {
        final World      world;
        double           x, y, z;
        double           vx, vy, vz;
        int              lifeTicks;
        final JavaPlugin plugin;
        final int        enchantLevel;
        final boolean    fromCloud;
        final UUID       shooterUuid;
        final int        pwr, shp, smt, bne;

        WaterDrop(World world, double x, double y, double z,
                  double vx, double vy, double vz,
                  JavaPlugin plugin, int enchantLevel, boolean fromCloud,
                  UUID shooterUuid, int pwr, int shp, int smt, int bne) {
            this.world        = world;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.lifeTicks    = 60;
            this.plugin       = plugin;
            this.enchantLevel = enchantLevel;
            this.fromCloud    = fromCloud;
            this.shooterUuid  = shooterUuid;
            this.pwr = pwr; this.shp = shp; this.smt = smt; this.bne = bne;
        }

        void step() {
            vy        -= 0.04;
            vx        *= 0.98;
            vz        *= 0.98;
            x         += vx;
            y         += vy;
            z         += vz;
            lifeTicks -= 2;
        }

        boolean hasLanded() {
            Block foot  = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y),     (int) Math.floor(z));
            Block below = world.getBlockAt((int) Math.floor(x), (int) Math.floor(y) - 1, (int) Math.floor(z));
            return (!foot.getType().isAir()  && foot.getType().isSolid())
                    || (!below.getType().isAir() && below.getType().isSolid() && vy < 0);
        }

        Location landingLocation() {
            for (int dy = 1; dy >= -3; dy--) {
                Block b = world.getBlockAt(
                        (int) Math.floor(x),
                        (int) Math.floor(y) + dy,
                        (int) Math.floor(z));
                if (!b.getType().isAir() && b.getType().isSolid()) {
                    return b.getLocation().add(0.5, 1.0, 0.5);
                }
            }
            return new Location(world, x, y, z);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public StormBook() { this(1); }

    public StormBook(int level) {
        super(
                "Book of Storm",
                STORM_ID,
                level,
                "book_storm",
                allowedMaterials()
        );

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(StormBook.class);
        if (STORM_KEY == null) {
            STORM_KEY     = new NamespacedKey(plugin, "storm_level");
            SHOOTER_KEY   = new NamespacedKey(plugin, "storm_shooter");
            POWER_KEY     = new NamespacedKey(plugin, "storm_power");
            SHARPNESS_KEY = new NamespacedKey(plugin, "storm_sharpness");
            SMITE_KEY     = new NamespacedKey(plugin, "storm_smite");
            BANE_KEY      = new NamespacedKey(plugin, "storm_bane");
        }
        ItemManager.getInstance().registerItem(this);
    }

    // ─── Anvil book upgrade ───────────────────────────────────────────────────

    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right,
                                            NamespacedKey keyId,
                                            NamespacedKey keyLvl) {
        if (left == null || right == null) return null;
        if (!(left.getItemMeta()  instanceof EnchantmentStorageMeta lMeta)) return null;
        if (!(right.getItemMeta() instanceof EnchantmentStorageMeta rMeta)) return null;

        String lId = lMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String rId = rMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        if (!STORM_ID.equals(lId) || !STORM_ID.equals(rId)) return null;

        Integer lLvl = lMeta.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        Integer rLvl = rMeta.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        if (lLvl == null || rLvl == null) return null;
        if (!lLvl.equals(rLvl))          return null;
        if (lLvl >= MAX_LEVEL)           return null;

        return new StormBook(lLvl + 1).toItemStack();
    }

    // ─── Shoot event ─────────────────────────────────────────────────────────

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (bow == null) return;
        if (!CorovaEnchantments.hasEnchant(bow, STORM_ID)) return;

        // ── Mana check (players only — mobs fire freely) ──────────────────────
        if (event.getEntity() instanceof Player player) {
            com.example.corovaItems.MageSystem.ManaManager mana =
                    com.example.corovaItems.MageSystem.ManaManager.getInstance();
            if (mana == null || !mana.tryConsumeMana(player,
                    com.example.corovaItems.MageSystem.ManaManager.COST_BOW_SPELL)) {
                event.setCancelled(true);
                return;
            }
        }

        int    level      = CorovaEnchantments.getEnchantLevel(bow, STORM_ID);
        Entity projectile = event.getProjectile();

        projectile.getPersistentDataContainer().set(STORM_KEY, PersistentDataType.INTEGER, level);
        projectile.getPersistentDataContainer().set(SHOOTER_KEY, PersistentDataType.STRING, event.getEntity().getUniqueId().toString());

        int pwr = bow.getEnchantmentLevel(Enchantment.POWER);
        int shp = bow.getEnchantmentLevel(Enchantment.SHARPNESS);
        int smt = bow.getEnchantmentLevel(Enchantment.SMITE);
        int bne = bow.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);

        projectile.getPersistentDataContainer().set(POWER_KEY,     PersistentDataType.INTEGER, pwr);
        projectile.getPersistentDataContainer().set(SHARPNESS_KEY, PersistentDataType.INTEGER, shp);
        projectile.getPersistentDataContainer().set(SMITE_KEY,     PersistentDataType.INTEGER, smt);
        projectile.getPersistentDataContainer().set(BANE_KEY,      PersistentDataType.INTEGER, bne);

        JavaPlugin      plugin      = JavaPlugin.getProvidingPlugin(StormBook.class);
        List<WaterDrop> activeDrops = new ArrayList<>();
        UUID            shooterUuid = event.getEntity().getUniqueId();

        new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                boolean arrowAlive = projectile.isValid() && !projectile.isDead();

                if (arrowAlive) {
                    Location loc   = projectile.getLocation();
                    World    world = loc.getWorld();
                    if (world == null) { cancel(); return; }

                    // Arrow trail particles
                    world.spawnParticle(Particle.FALLING_WATER,  loc, 3, 0.08, 0.08, 0.08, 0.0);
                    world.spawnParticle(Particle.DRIPPING_WATER, loc, 2, 0.06, 0.06, 0.06, 0.0);

                    // ── Periodically spawn a cloud along the trail ────────────
                    if (tickCount > 0 && tickCount % TRAIL_CLOUD_INTERVAL == 0) {
                        Location cloudLoc = loc.clone().add(0, 10.0, 0);
                        spawnSingleCloud(world, cloudLoc, level, plugin, shooterUuid, pwr, shp, smt, bne);
                    }

                    // Spawn server-side drip drops every 3 ticks
                    if (tickCount % 3 == 0) {
                        int numDrops = 1 + RANDOM.nextInt(level + 1);
                        for (int i = 0; i < numDrops; i++) {
                            double angle  = RANDOM.nextDouble() * Math.PI * 2;
                            double speedH = 0.04 + RANDOM.nextDouble() * 0.10;
                            activeDrops.add(new WaterDrop(
                                    world,
                                    loc.getX() + (RANDOM.nextDouble() - 0.5) * 0.3,
                                    loc.getY(),
                                    loc.getZ() + (RANDOM.nextDouble() - 0.5) * 0.3,
                                    Math.cos(angle) * speedH,
                                    0.0,
                                    Math.sin(angle) * speedH,
                                    plugin, level, false, shooterUuid, pwr, shp, smt, bne
                            ));
                        }
                    }
                }

                // Tick trail drops
                Iterator<WaterDrop> it = activeDrops.iterator();
                while (it.hasNext()) {
                    WaterDrop drop = it.next();
                    drop.step();
                    drop.world.spawnParticle(Particle.FALLING_WATER,
                            drop.x, drop.y, drop.z, 1, 0, 0, 0, 0);
                    drop.world.spawnParticle(Particle.DRIPPING_WATER,
                            drop.x, drop.y, drop.z, 1, 0, 0, 0, 0);
                    if (drop.hasLanded() || drop.lifeTicks <= 0) {
                        it.remove();
                        if (drop.hasLanded()) landDrop(drop, plugin);
                    }
                }

                if (!arrowAlive && activeDrops.isEmpty()) { cancel(); return; }
                tickCount++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ─── Impact event ─────────────────────────────────────────────────────────

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        if (STORM_KEY == null) return;
        if (!projectile.getPersistentDataContainer().has(STORM_KEY, PersistentDataType.INTEGER)) return;

        int level = projectile.getPersistentDataContainer().get(STORM_KEY, PersistentDataType.INTEGER);
        String sUuidStr = projectile.getPersistentDataContainer().get(SHOOTER_KEY, PersistentDataType.STRING);
        UUID shooterUuid = sUuidStr != null ? UUID.fromString(sUuidStr) : null;

        int pwr = projectile.getPersistentDataContainer().getOrDefault(POWER_KEY,     PersistentDataType.INTEGER, 0);
        int shp = projectile.getPersistentDataContainer().getOrDefault(SHARPNESS_KEY, PersistentDataType.INTEGER, 0);
        int smt = projectile.getPersistentDataContainer().getOrDefault(SMITE_KEY,     PersistentDataType.INTEGER, 0);
        int bne = projectile.getPersistentDataContainer().getOrDefault(BANE_KEY,      PersistentDataType.INTEGER, 0);

        Location impactLoc = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
                : (event.getHitEntity() != null
                   ? event.getHitEntity().getLocation()
                   : projectile.getLocation());

        World world = impactLoc.getWorld();
        if (world == null) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(StormBook.class);

        spawnImpactBurst(world, impactLoc);
        spawnStormClouds(world, impactLoc, level, plugin, shooterUuid, pwr, shp, smt, bne);

        projectile.remove();
    }

    // ─── Impact water burst ───────────────────────────────────────────────────

    private void spawnImpactBurst(World world, Location loc) {
        world.spawnParticle(Particle.SPLASH,         loc, 30, 0.5, 0.2, 0.5, 0.3);
        world.spawnParticle(Particle.FALLING_WATER,  loc, 20, 0.4, 0.2, 0.4, 0.0);
        world.spawnParticle(Particle.DRIPPING_WATER, loc, 15, 0.3, 0.2, 0.3, 0.0);
        world.spawnParticle(Particle.CLOUD,          loc, 10, 0.4, 0.2, 0.4, 0.02);
    }

    // ─── Impact cloud cluster ─────────────────────────────────────────────────

    private void spawnStormClouds(World world, Location impactLoc, int level, JavaPlugin plugin,
                                  UUID shooterUuid, int pwr, int shp, int smt, int bne) {
        int cloudCount = (level >= 2) ? 3 : 1;

        for (int c = 0; c < cloudCount; c++) {
            double offsetAngle = (cloudCount == 1) ? 0 : (c * (Math.PI * 2.0 / cloudCount));
            double offsetDist  = (cloudCount == 1) ? 0 : 2.5;
            Location cloudBase = impactLoc.clone().add(
                    Math.cos(offsetAngle) * offsetDist,
                    10.0,
                    Math.sin(offsetAngle) * offsetDist
            );
            spawnSingleCloud(world, cloudBase, level, plugin, shooterUuid, pwr, shp, smt, bne);
        }
    }

    // ─── Single rain cloud ────────────────────────────────────────────────────

    private void spawnSingleCloud(World world, Location cloudBase, int level, JavaPlugin plugin,
                                  UUID shooterUuid, int pwr, int shp, int smt, int bne) {
        int cloudDuration     = 120 + (level * 40);
        int lightningInterval = 25; // ticks between bolts

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= cloudDuration) { cancel(); return; }
                ticks += 2;

                // ── Cloud disc ────────────────────────────────────────────────
                int cloudParticles = 35 + (level * 10);
                for (int i = 0; i < cloudParticles; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2;
                    double r     = RANDOM.nextDouble() * 3.5;
                    double yOff  = (RANDOM.nextDouble() - 0.5) * 1.2;
                    Location pLoc = cloudBase.clone().add(
                            Math.cos(angle) * r, yOff, Math.sin(angle) * r);
                    if (i % 3 != 2) {
                        world.spawnParticle(Particle.CLOUD, pLoc, 1,
                                0.1, 0.05, 0.1, 0.0);
                    } else {
                        world.spawnParticle(Particle.WHITE_ASH, pLoc, 1,
                                0.15, 0.05, 0.15, 0.01);
                    }
                }

                // ── Drip drops downward every 10 ticks ───────────────────────
                if (ticks % 10 == 0) {
                    int dropCount = 2 + level;
                    for (int i = 0; i < dropCount; i++) {
                        double angle      = RANDOM.nextDouble() * Math.PI * 2;
                        double r          = RANDOM.nextDouble() * 1.8;
                        Location dropSpawn = cloudBase.clone().add(
                                Math.cos(angle) * r, -0.3, Math.sin(angle) * r);

                        WaterDrop drop = new WaterDrop(
                                world,
                                dropSpawn.getX(), dropSpawn.getY(), dropSpawn.getZ(),
                                (RANDOM.nextDouble() - 0.5) * 0.04,
                                -0.1,
                                (RANDOM.nextDouble() - 0.5) * 0.04,
                                plugin, level, true, shooterUuid, pwr, shp, smt, bne
                        );

                        new BukkitRunnable() {
                            final WaterDrop d = drop;
                            @Override
                            public void run() {
                                d.step();
                                world.spawnParticle(Particle.FALLING_WATER,
                                        d.x, d.y, d.z, 1, 0, 0, 0, 0);
                                world.spawnParticle(Particle.DRIPPING_WATER,
                                        d.x, d.y, d.z, 1, 0, 0, 0, 0);
                                if (d.hasLanded() || d.lifeTicks <= 0) {
                                    if (d.hasLanded()) landDrop(d, plugin);
                                    cancel();
                                }
                            }
                        }.runTaskTimer(plugin, 0L, 1L);
                    }
                }

                // ── Lvl 2: lightning bolt ─────────────────────────────────────
                if (level >= 2 && ticks % lightningInterval == 0) {
                    spawnVisualLightning(world, cloudBase, plugin, shooterUuid, pwr, shp, smt, bne);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ─── Visual lightning bolt ────────────────────────────────────────────────

    private void spawnVisualLightning(World world, Location cloudBase, JavaPlugin plugin,
                                      UUID shooterUuid, int pwr, int shp, int smt, int bne) {
        LivingEntity strikeTarget = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity e : world.getNearbyEntities(cloudBase, 8.0, cloudBase.getY(), 8.0)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le.getLocation().getY() >= cloudBase.getY()) continue;
            double dSq = le.getLocation().distanceSquared(
                    new Location(world, cloudBase.getX(), le.getLocation().getY(), cloudBase.getZ()));
            if (dSq < bestDistSq) { bestDistSq = dSq; strikeTarget = le; }
        }

        Location groundLoc = cloudBase.clone();
        if (strikeTarget != null) {
            groundLoc = strikeTarget.getLocation().clone().add(0, 1.0, 0);
        } else {
            for (int dy = 0; dy >= -30; dy--) {
                Block b = world.getBlockAt(
                        cloudBase.getBlockX(),
                        cloudBase.getBlockY() + dy,
                        cloudBase.getBlockZ());
                if (!b.getType().isAir() && b.getType().isSolid()) {
                    groundLoc = b.getLocation().add(0.5, 1.0, 0.5);
                    break;
                }
            }
        }

        final Location     finalGround = groundLoc.clone();
        final LivingEntity finalTarget = strikeTarget;
        final double       startY      = cloudBase.getY();
        final double       endY        = finalGround.getY();
        final double       totalDist   = startY - endY;
        if (totalDist <= 0) return;

        List<Location> mainBolt = buildBoltPath(world,
                cloudBase.getX(), startY, cloudBase.getZ(),
                finalGround.getX(), endY, finalGround.getZ(),
                totalDist);

        int      b1Idx   = mainBolt.size() / 3;
        int      b2Idx   = mainBolt.size() * 2 / 3;
        Location b1Start = mainBolt.get(b1Idx);
        Location b2Start = mainBolt.get(b2Idx);

        List<Location> branch1 = buildBoltPath(world,
                b1Start.getX(), b1Start.getY(), b1Start.getZ(),
                b1Start.getX() + (RANDOM.nextDouble() - 0.5) * 6, endY + 1,
                b1Start.getZ() + (RANDOM.nextDouble() - 0.5) * 6,
                b1Start.getY() - (endY + 1));

        List<Location> branch2 = buildBoltPath(world,
                b2Start.getX(), b2Start.getY(), b2Start.getZ(),
                b2Start.getX() + (RANDOM.nextDouble() - 0.5) * 5, endY + 1,
                b2Start.getZ() + (RANDOM.nextDouble() - 0.5) * 5,
                b2Start.getY() - (endY + 1));

        Particle.DustOptions coreYellow  = new Particle.DustOptions(Color.fromRGB(255, 255, 200), 0.5f);
        Particle.DustOptions glowYellow  = new Particle.DustOptions(Color.fromRGB(255, 230,  20), 1.2f);
        Particle.DustOptions branchColor = new Particle.DustOptions(Color.fromRGB(200, 200, 255), 0.8f);

        if (finalTarget != null) {
            Location warnLoc = finalTarget.getLocation().clone().add(0, 0.1, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, warnLoc, 20, 0.4, 0.1, 0.4, 0.05);
            world.spawnParticle(Particle.DUST, warnLoc, 12, 0.3, 0.1, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(200, 240, 255), 1.0f));
        }

        int segPerStage = Math.max(1, mainBolt.size() / 3);
        for (int stage = 0; stage < 3; stage++) {
            final int s = stage;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                int from = s * segPerStage;
                int to   = (s == 2) ? mainBolt.size() : Math.min(from + segPerStage, mainBolt.size());
                for (int i = from; i < to; i++) {
                    Location pt = mainBolt.get(i);
                    world.spawnParticle(Particle.DUST, pt, 10, 0.05, 0.05, 0.05, 0, glowYellow);
                    world.spawnParticle(Particle.DUST, pt, 6, 0.02, 0.02, 0.02, 0, coreYellow);
                }
                if (s == 1) {
                    for (Location pt : branch1)
                        world.spawnParticle(Particle.DUST, pt, 4, 0.02, 0.02, 0.02, 0, branchColor);
                    for (Location pt : branch2)
                        world.spawnParticle(Particle.DUST, pt, 4, 0.02, 0.02, 0.02, 0, branchColor);
                }
            }, (long) stage);
        }

        world.playSound(cloudBase, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 2.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                world.playSound(finalGround, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 1.0f), 2L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            world.spawnParticle(Particle.FLASH,          finalGround, 1,  0,   0,   0,   0);
            world.spawnParticle(Particle.DUST,           finalGround, 25, 0.6, 0.1, 0.6, 0, glowYellow);
            world.spawnParticle(Particle.LARGE_SMOKE,    finalGround, 8,  0.3, 0.1, 0.3, 0.05);
            world.spawnParticle(Particle.ELECTRIC_SPARK, finalGround, 30, 0.5, 0.2, 0.5, 0.1);
        }, 2L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Location pt : mainBolt)
                world.spawnParticle(Particle.DUST, pt, 2, 0.02, 0.02, 0.02, 0, glowYellow);
        }, 5L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity shooter = shooterUuid != null ? Bukkit.getEntity(shooterUuid) : null;
            double pwrMult = BowDamageScaling.getPowerMultiplier(pwr);

            if (finalTarget != null && finalTarget.isValid() && !finalTarget.isDead()) {
                double base = 7.0;
                double bonus = BowDamageScaling.getEnchantmentDamageBonus(shp, smt, bne, finalTarget);
                double finalDmg = (base * pwrMult) + (bonus * 0.7);
                applyStormDamage(finalTarget, finalDmg, shooter);
                finalTarget.setFireTicks(100);
            }
            for (Entity nearby : world.getNearbyEntities(finalGround, 2.5, 2.5, 2.5)) {
                if (!(nearby instanceof LivingEntity living)) continue;
                if (living.equals(finalTarget)) continue;
                double base = 3.5;
                double bonus = BowDamageScaling.getEnchantmentDamageBonus(shp, smt, bne, living);
                double finalDmg = (base * pwrMult) + (bonus * 0.7);
                applyStormDamage(living, finalDmg, shooter);
                living.setFireTicks(60);
            }
        }, 2L);
    }

    // ─── Bolt path builder ────────────────────────────────────────────────────

    private List<Location> buildBoltPath(World world,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2,
                                         double totalDist) {
        List<Location> points = new ArrayList<>();
        // Create 4-6 dramatic zigzag nodes
        int nodeCount = 5 + RANDOM.nextInt(3);
        List<Location> nodes = new ArrayList<>();
        nodes.add(new Location(world, x1, y1, z1));

        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) { points.add(new Location(world, x1, y1, z1)); return points; }
        double ux = dx / len, uy = dy / len, uz = dz / len;

        double p1x, p1y, p1z;
        if (Math.abs(uy) < 0.99) {
            p1x = uz; p1y = 0.0; p1z = -ux;
        } else {
            p1x = 0.0; p1y = uz; p1z = -uy;
        }
        double p1len = Math.sqrt(p1x*p1x + p1y*p1y + p1z*p1z);
        p1x /= p1len; p1y /= p1len; p1z /= p1len;

        double p2x = uy * p1z - uz * p1y;
        double p2y = uz * p1x - ux * p1z;
        double p2z = ux * p1y - uy * p1x;

        for (int i = 1; i < nodeCount; i++) {
            double t = (double) i / nodeCount;
            double bx = x1 + dx * t;
            double by = y1 + dy * t;
            double bz = z1 + dz * t;

            double mag = 1.2 + RANDOM.nextDouble() * 1.5;
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double a1 = Math.cos(angle) * mag;
            double a2 = Math.sin(angle) * mag;

            nodes.add(new Location(world,
                    bx + a1 * p1x + a2 * p2x,
                    by + a1 * p1y + a2 * p2y,
                    bz + a1 * p1z + a2 * p2z));
        }
        nodes.add(new Location(world, x2, y2, z2));

        // Interpolate between nodes with high particle density
        for (int i = 0; i < nodes.size() - 1; i++) {
            Location start = nodes.get(i);
            Location end = nodes.get(i + 1);
            double dist = start.distance(end);
            int steps = Math.max(1, (int) (dist * 20));
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                points.add(new Location(world,
                        start.getX() + (end.getX() - start.getX()) * t,
                        start.getY() + (end.getY() - start.getY()) * t,
                        start.getZ() + (end.getZ() - start.getZ()) * t));
            }
        }

        return points;
    }

    private void landDrop(WaterDrop drop, JavaPlugin plugin) {
        Location landLoc = drop.landingLocation();
        World    world   = landLoc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.SPLASH,        landLoc, 5, 0.15, 0.05, 0.15, 0.1);
        world.spawnParticle(Particle.FALLING_WATER, landLoc, 3, 0.10, 0.05, 0.10, 0.0);
        spawnWaterPuddle(world, landLoc, drop.enchantLevel, plugin, drop.shooterUuid, drop.pwr, drop.shp, drop.smt, drop.bne);
    }

    // ─── Blue lingering puddle ────────────────────────────────────────────────

    private void spawnWaterPuddle(World world, Location loc, int level, JavaPlugin plugin, UUID shooterUuid, int pwr, int shp, int smt, int bne) {
        final double PUDDLE_RADIUS = 0.9 + (level * 0.2);
        final int    DURATION      = 60 + (level * 20);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= DURATION) { cancel(); return; }
                ticks += 2;

                int count = 4 + (level * 2);
                for (int i = 0; i < count; i++) {
                    double angle  = RANDOM.nextDouble() * Math.PI * 2;
                    double r      = RANDOM.nextDouble() * PUDDLE_RADIUS;
                    double height = RANDOM.nextDouble() * 0.5;
                    Location pLoc = loc.clone().add(
                            Math.cos(angle) * r, height, Math.sin(angle) * r);

                    Color col;
                    int   mod = i % 3;
                    if      (mod == 0) col = COLOR_BLUE_BRIGHT;
                    else if (mod == 1) col = COLOR_BLUE_MID;
                    else               col = COLOR_BLUE_DARK;

                    world.spawnParticle(
                            Particle.ENTITY_EFFECT,
                            pLoc.getX(), pLoc.getY(), pLoc.getZ(),
                            0, 0, 0, 0,
                            col);
                }

                if (ticks % 10 == 0) {
                    Entity shooter = shooterUuid != null ? Bukkit.getEntity(shooterUuid) : null;
                    double pwrMult = BowDamageScaling.getPowerMultiplier(pwr);

                    for (Entity e : world.getNearbyEntities(loc, PUDDLE_RADIUS, 1.0, PUDDLE_RADIUS)) {
                        if (e instanceof LivingEntity living) {
                            double bonus = BowDamageScaling.getEnchantmentDamageBonus(shp, smt, bne, living);
                            double damage = (1.0 * pwrMult) + (bonus / 5.0); // Puddle bonus is reduced
                            applyStormDamage(living, damage, shooter);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void applyStormDamage(LivingEntity target, double damage, Entity shooter) {
        target.setNoDamageTicks(0);
        UUID shooterUuid = (shooter instanceof LivingEntity) ? shooter.getUniqueId() : null;
        if (shooterUuid != null) CorovaCombat.abilityBypass.add(shooterUuid);
        try {
            if (shooter != null) target.damage(damage, shooter);
            else target.damage(damage);
        } finally {
            if (shooterUuid != null) CorovaCombat.abilityBypass.remove(shooterUuid);
        }
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(Material.BOW, Material.CROSSBOW);
    }
}
