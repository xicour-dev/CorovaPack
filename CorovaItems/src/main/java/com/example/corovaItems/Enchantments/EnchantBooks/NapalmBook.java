package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class NapalmBook extends EnchantmentBook implements Listener {

    public static final String NAPALM_ID    = "napalm";
    public static final int    MAX_LEVEL    = 3;
    public static final int    UPGRADE_COST = 10;

    private static org.bukkit.NamespacedKey NAPALM_KEY;
    private static final Random RANDOM = new Random();

    // ─── Napalm Burn Effect tracker ──────────────────────────────────────────
    // Maps entity UUID → enchant level of the napalm that afflicted them.
    // While present, every fire/fire-tick damage event on that entity deals
    // bonus damage: lvl 1 = +2 HP, lvl 2 = +4 HP, lvl 3 = +6 HP.
    private static final Map<UUID, Integer> napalmBurning = new HashMap<>();

    private static final Color COLOR_ORANGE      = Color.fromRGB(255, 100,  0);
    private static final Color COLOR_DEEP_ORANGE = Color.fromRGB(220,  50,  0);
    private static final Color COLOR_RED         = Color.fromRGB(200,  15,  0);

    // ─── Server-side lava drop ────────────────────────────────────────────────

    private static class LavaDrop {
        final World        world;
        double             x, y, z;
        double             vx, vy, vz;
        int                lifeTicks;
        final JavaPlugin   plugin;
        final int          enchantLevel;
        final LivingEntity shooter; // null for mob shooters without a reference

        LavaDrop(World world, double x, double y, double z,
                 double vx, double vy, double vz,
                 JavaPlugin plugin, int enchantLevel, LivingEntity shooter) {
            this.world        = world;
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.lifeTicks    = 80;
            this.plugin       = plugin;
            this.enchantLevel = enchantLevel;
            this.shooter      = shooter;
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

    // ─── Cluster tracker (lvl 3 only) ────────────────────────────────────────
    // activeIgnitionClouds tracks cloud centre positions that already have a
    // running BukkitRunnable. Before starting a new ignition cloud we check
    // whether one already exists within CLUSTER_DIST — if so, we just add the
    // new landing position to the shared list and let the existing runnable
    // handle the extra density, rather than stacking another runnable on top.

    private static class LandingCluster {
        final Location       centre;
        final List<Location> allLandings;
        static final int    DURATION     = 100;
        static final double CLOUD_RADIUS = 1.4;
        static final double CLUSTER_DIST = 3.0;

        LandingCluster(Location centre, List<Location> allLandings) {
            this.centre      = centre;
            this.allLandings = allLandings;
        }

        int density() {
            int count = 0;
            for (Location l : allLandings) {
                if (l.getWorld() == centre.getWorld()
                        && l.distanceSquared(centre) <= CLUSTER_DIST * CLUSTER_DIST) {
                    count++;
                }
            }
            return count;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public NapalmBook() { this(1); }

    public NapalmBook(int level) {
        super(
                "Book of Napalm",
                NAPALM_ID,
                level,
                "book_napalm",
                allowedMaterials()
        );

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(NapalmBook.class);
        if (NAPALM_KEY == null) {
            NAPALM_KEY = new org.bukkit.NamespacedKey(plugin, "napalm_level");
        }
        ItemManager.getInstance().registerItem(this);
    }

    // ─── Anvil book upgrade ───────────────────────────────────────────────────

    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right,
                                            org.bukkit.NamespacedKey keyId,
                                            org.bukkit.NamespacedKey keyLvl) {
        if (left == null || right == null) return null;
        if (!(left.getItemMeta()  instanceof EnchantmentStorageMeta lMeta)) return null;
        if (!(right.getItemMeta() instanceof EnchantmentStorageMeta rMeta)) return null;

        String lId = lMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String rId = rMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        if (!NAPALM_ID.equals(lId) || !NAPALM_ID.equals(rId)) return null;

        Integer lLvl = lMeta.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        Integer rLvl = rMeta.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        if (lLvl == null || rLvl == null) return null;
        if (!lLvl.equals(rLvl))          return null;
        if (lLvl >= MAX_LEVEL)           return null;

        return new NapalmBook(lLvl + 1).toItemStack();
    }

    // ─── Shoot event ─────────────────────────────────────────────────────────

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (bow == null) return;
        if (!CorovaEnchantments.hasEnchant(bow, NAPALM_ID)) return;

        // ── Mana check (players only — mobs fire freely) ──────────────────────
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            com.example.corovaItems.MageSystem.ManaManager mana =
                    com.example.corovaItems.MageSystem.ManaManager.getInstance();
            if (mana == null || !mana.tryConsumeMana(player,
                    com.example.corovaItems.MageSystem.ManaManager.COST_BOW_SPELL)) {
                event.setCancelled(true);
                return;
            }
        }

        int          lvl        = CorovaEnchantments.getEnchantLevel(bow);

        // Randomized level logic for "The Scorched" mob: 70% Napalm I, 20% Napalm II, 10% Napalm III
        if (event.getEntity().getScoreboardTags().contains("TheScorched")) {
            double chance = RANDOM.nextDouble();
            if (chance < 0.70)      lvl = 1;
            else if (chance < 0.90) lvl = 2;
            else                    lvl = 3;
        }

        // Capped at level 1 for other mobs
        if (!(event.getEntity() instanceof org.bukkit.entity.Player) && !event.getEntity().getScoreboardTags().contains("TheScorched")) {
            lvl = 1;
        }

        final int    level      = lvl;
        Entity       projectile = event.getProjectile();
        LivingEntity shooter    = (event.getEntity() instanceof LivingEntity le) ? le : null;

        projectile.getPersistentDataContainer()
                .set(NAPALM_KEY, PersistentDataType.INTEGER, level);

        // Apply NapalmBurnEffect to the arrow itself will be done on hit;
        // tag the shooter so drops and puddles know who to exempt from fire.
        JavaPlugin     plugin           = JavaPlugin.getProvidingPlugin(NapalmBook.class);
        List<LavaDrop> activeDrops      = new ArrayList<>();
        List<Location> landingPositions = new ArrayList<>();
        // Tracks which positions already have an active ignition cloud runnable
        List<Location> activeIgnitionClouds = new ArrayList<>();

        new BukkitRunnable() {
            int tickCount = 0;

            @Override
            public void run() {
                boolean arrowAlive = projectile.isValid() && !projectile.isDead();

                if (arrowAlive) {
                    Location loc   = projectile.getLocation();
                    World    world = loc.getWorld();
                    if (world == null) { cancel(); return; }

                    world.spawnParticle(Particle.FALLING_LAVA, loc, 2,
                            0.08, 0.08, 0.08, 0.0);

                    if (level == 1) {
                        world.spawnParticle(Particle.FALLING_LAVA, loc, 2,
                                0.12, 0.12, 0.12, 0.0);
                        world.spawnParticle(Particle.SMALL_FLAME, loc, 1,
                                0.08, 0.08, 0.08, 0.01);
                    } else {
                        spawnDustTransition(world, loc,
                                Color.fromRGB(255, 80, 0),
                                Color.fromRGB(20,  10, 0),
                                1.5f, 6);
                        world.spawnParticle(Particle.SMALL_FLAME, loc, 2,
                                0.1, 0.1, 0.1, 0.01);

                        if (level >= 3) {
                            world.spawnParticle(Particle.DRIPPING_LAVA, loc, 3,
                                    0.05, 0.05, 0.05, 0.0);
                        }

                        if (tickCount % 3 == 0) {
                            int numDrops = 1 + RANDOM.nextInt(2);
                            for (int i = 0; i < numDrops; i++) {
                                double angle  = RANDOM.nextDouble() * Math.PI * 2;
                                double speedH = 0.05 + RANDOM.nextDouble() * 0.12;
                                activeDrops.add(new LavaDrop(
                                        world,
                                        loc.getX() + (RANDOM.nextDouble() - 0.5) * 0.3,
                                        loc.getY(),
                                        loc.getZ() + (RANDOM.nextDouble() - 0.5) * 0.3,
                                        Math.cos(angle) * speedH,
                                        0.0,
                                        Math.sin(angle) * speedH,
                                        plugin, level, shooter
                                ));
                            }
                        }
                    }
                }

                Iterator<LavaDrop> it = activeDrops.iterator();
                while (it.hasNext()) {
                    LavaDrop drop = it.next();
                    drop.step();
                    drop.world.spawnParticle(Particle.FALLING_LAVA,
                            drop.x, drop.y, drop.z, 1, 0, 0, 0, 0);
                    if (level >= 3) {
                        drop.world.spawnParticle(Particle.DRIPPING_LAVA,
                                drop.x, drop.y, drop.z, 1, 0, 0, 0, 0);
                    }
                    if (drop.hasLanded() || drop.lifeTicks <= 0) {
                        it.remove();
                        landDrop(drop, landingPositions, activeIgnitionClouds);
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
        if (NAPALM_KEY == null) return;
        if (!projectile.getPersistentDataContainer()
                .has(NAPALM_KEY, PersistentDataType.INTEGER)) return;

        Integer level = projectile.getPersistentDataContainer()
                .get(NAPALM_KEY, PersistentDataType.INTEGER);
        if (level == null) return;

        Location impactLoc = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().add(0.5, 0.5, 0.5)
                : (event.getHitEntity() != null
                   ? event.getHitEntity().getLocation()
                   : projectile.getLocation());

        World world = impactLoc.getWorld();
        if (world == null) return;

        JavaPlugin     plugin               = JavaPlugin.getProvidingPlugin(NapalmBook.class);
        List<Location> landingPositions     = new ArrayList<>();
        List<Location> activeIgnitionClouds = new ArrayList<>();

        // Apply NapalmBurnEffect to any entity directly hit by the arrow
        if (event.getHitEntity() instanceof LivingEntity hitEntity) {
            applyNapalmBurn(hitEntity, level, plugin);
        }

        spawnImpactFlash(world, impactLoc);

        if (level >= 2) {
            spawnBurstDrops(world, impactLoc, level, plugin,
                    landingPositions, activeIgnitionClouds);
            // Only ONE lingering cloud at the impact centre — called once here,
            // never again per-drop
            spawnLingeringCloud(world, impactLoc, level, plugin);
        }

        projectile.remove();
    }

    // ─── Impact visual flash ──────────────────────────────────────────────────

    private void spawnImpactFlash(World world, Location loc) {
        world.spawnParticle(Particle.FLAME,        loc, 20, 0.4, 0.3, 0.4, 0.12);
        world.spawnParticle(Particle.LARGE_SMOKE,  loc, 12, 0.5, 0.3, 0.5, 0.04);
        world.spawnParticle(Particle.FALLING_LAVA, loc, 10, 0.4, 0.2, 0.4, 0.0);
        spawnDustTransition(world, loc.clone().add(0, 0.1, 0),
                Color.fromRGB(255, 60, 0), Color.fromRGB(10, 5, 0), 2.0f, 20);
        world.spawnParticle(Particle.BLOCK, loc, 16,
                0.7, 0.25, 0.7, 0.25,
                Material.MAGMA_BLOCK.createBlockData());
    }

    // ─── Burst drops on impact (lvl 2+) ──────────────────────────────────────

    private void spawnBurstDrops(World world, Location loc, int level,
                                 JavaPlugin plugin,
                                 List<Location> landingPositions,
                                 List<Location> activeIgnitionClouds) {
        int            dropCount  = 18 + (level * 8);
        List<LavaDrop> burstDrops = new ArrayList<>();

        for (int i = 0; i < dropCount; i++) {
            double angle  = RANDOM.nextDouble() * Math.PI * 2;
            double speedH = 0.25 + RANDOM.nextDouble() * (0.4 + level * 0.1);
            double speedV = 0.15 + RANDOM.nextDouble() * 0.35;
            burstDrops.add(new LavaDrop(
                    world,
                    loc.getX(), loc.getY(), loc.getZ(),
                    Math.cos(angle) * speedH,
                    speedV,
                    Math.sin(angle) * speedH,
                    plugin, level, null  // burst drops post-impact, shooter no longer relevant
            ));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (burstDrops.isEmpty()) { cancel(); return; }
                Iterator<LavaDrop> it = burstDrops.iterator();
                while (it.hasNext()) {
                    LavaDrop drop = it.next();
                    drop.step();
                    world.spawnParticle(Particle.LAVA,
                            drop.x, drop.y, drop.z, 1, 0, 0, 0, 0);
                    world.spawnParticle(Particle.FALLING_LAVA,
                            drop.x, drop.y, drop.z, 1, 0.02, 0.02, 0.02, 0);
                    if (level >= 3) {
                        world.spawnParticle(Particle.DRIPPING_LAVA,
                                drop.x, drop.y, drop.z, 1, 0, 0, 0, 0);
                    }
                    if (drop.hasLanded() || drop.lifeTicks <= 0) {
                        it.remove();
                        landDrop(drop, landingPositions, activeIgnitionClouds);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    public static void spawnNapalmCloud(World world, Location loc, int level, JavaPlugin plugin) {
        int    duration = 60 + (level * 20);
        double radius   = 1.5 + (level * 0.5);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) { cancel(); return; }
                ticks += 2;

                for (int i = 0; i < 8; i++) {
                    double angle  = RANDOM.nextDouble() * Math.PI * 2;
                    double r      = RANDOM.nextDouble() * radius;
                    double height = RANDOM.nextDouble() * 1.2;
                    Location pLoc = loc.clone().add(
                            Math.cos(angle) * r, height, Math.sin(angle) * r);

                    Color dustColor = (i % 2 == 0)
                            ? Color.fromRGB(220,  30, 0)
                            : Color.fromRGB(255, 120, 0);

                    world.spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            pLoc, 1, 0, 0, 0, 0,
                            new Particle.DustTransition(dustColor,
                                    Color.fromRGB(10, 0, 0), 1.2f));
                }

                if (ticks % 4 == 0) {
                    world.spawnParticle(Particle.SMALL_FLAME, loc, 3,
                            radius * 0.4, 0.3, radius * 0.4, 0.02);
                }

                if (ticks % 10 == 0) {
                    Collection<Entity> nearby = world.getNearbyEntities(
                            loc, radius, 1.0, radius);
                    for (Entity entity : nearby) {
                        if (entity instanceof LivingEntity living) {
                            living.setFireTicks(80);
                            applyNapalmBurn(living, level, plugin);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ─── Called when any LavaDrop hits the ground ─────────────────────────────

    private void landDrop(LavaDrop drop,
                          List<Location> landingPositions,
                          List<Location> activeIgnitionClouds) {
        Location landLoc = drop.landingLocation();
        World    world   = landLoc.getWorld();
        if (world == null) return;

        if (drop.enchantLevel >= 2) {
            Block surface  = world.getBlockAt(
                    (int) Math.floor(landLoc.getX()),
                    (int) Math.floor(landLoc.getY()) - 1,
                    (int) Math.floor(landLoc.getZ()));
            Block fireSlot = surface.getRelative(BlockFace.UP);

            // ── Safety zone: do not place fire on or adjacent to the shooter ──
            boolean tooClose = false;
            if (drop.shooter != null && drop.shooter.isValid()) {
                Location shooterLoc = drop.shooter.getLocation();
                if (landLoc.getWorld().equals(shooterLoc.getWorld())
                        && landLoc.distanceSquared(shooterLoc) <= 4.0) { // within 2 blocks
                    tooClose = true;
                }
            }

            if (!tooClose && (fireSlot.getType() == Material.AIR
                    || fireSlot.getType() == Material.CAVE_AIR)) {
                fireSlot.setType(Material.FIRE);
            }
        }

        // Landing particle burst
        world.spawnParticle(Particle.LAVA,         landLoc, 4, 0.15, 0.05, 0.15, 0.0);
        world.spawnParticle(Particle.FALLING_LAVA,  landLoc, 3, 0.10, 0.05, 0.10, 0.0);
        world.spawnParticle(Particle.BLOCK, landLoc, 6,
                0.25, 0.1, 0.25, 0.05,
                Material.MAGMA_BLOCK.createBlockData());

        if (drop.enchantLevel >= 3) {
            // Always register this position so existing cloud runnables get
            // denser — but only START a new runnable if no cloud exists nearby
            landingPositions.add(landLoc.clone());

            boolean cloudNearby = false;
            for (Location existing : activeIgnitionClouds) {
                if (existing.getWorld() == landLoc.getWorld()
                        && existing.distanceSquared(landLoc)
                        <= LandingCluster.CLUSTER_DIST * LandingCluster.CLUSTER_DIST) {
                    cloudNearby = true;
                    break;
                }
            }

            if (!cloudNearby) {
                activeIgnitionClouds.add(landLoc.clone());
                spawnIgnitionCloud(world, landLoc, drop.plugin, drop.enchantLevel,
                        landingPositions, activeIgnitionClouds);
            }
        }
    }

    // ─── Lvl 3 ignition cloud ─────────────────────────────────────────────────
    // One runnable per distinct cluster zone. Density scales with how many
    // drops have landed nearby, but there is never more than one runnable
    // emitting particles per cluster zone at a time.

    private void spawnIgnitionCloud(World world, Location loc,
                                    JavaPlugin plugin, int enchantLevel,
                                    List<Location> allLandings,
                                    List<Location> activeIgnitionClouds) {
        final double         CLOUD_RADIUS   = LandingCluster.CLOUD_RADIUS;
        final int            CLOUD_DURATION = LandingCluster.DURATION;
        final LandingCluster cluster        = new LandingCluster(loc, allLandings);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= CLOUD_DURATION) {
                    // Clean up so future drops in the same area can start fresh
                    activeIgnitionClouds.remove(loc);
                    cancel();
                    return;
                }
                ticks += 2;

                int density      = cluster.density();
                int particleBase = Math.min(6 * density, 30);

                for (int i = 0; i < particleBase; i++) {
                    double angle  = RANDOM.nextDouble() * Math.PI * 2;
                    double r      = RANDOM.nextDouble() * CLOUD_RADIUS;
                    double height = RANDOM.nextDouble() * 0.9;
                    Location pLoc = loc.clone().add(
                            Math.cos(angle) * r, height, Math.sin(angle) * r);

                    Color col;
                    int   mod = i % 3;
                    if      (mod == 0) col = COLOR_ORANGE;
                    else if (mod == 1) col = COLOR_DEEP_ORANGE;
                    else               col = COLOR_RED;

                    world.spawnParticle(
                            Particle.ENTITY_EFFECT,
                            pLoc.getX(), pLoc.getY(), pLoc.getZ(),
                            0, 0, 0, 0,
                            col);
                }

                if (ticks % 6 == 0) {
                    world.spawnParticle(Particle.SMALL_FLAME, loc, 1,
                            CLOUD_RADIUS * 0.5, 0.1, CLOUD_RADIUS * 0.5, 0.01);
                }

                if (ticks % 10 == 0) {
                    Collection<Entity> nearby = world.getNearbyEntities(
                            loc, CLOUD_RADIUS, 1.0, CLOUD_RADIUS);
                    for (Entity entity : nearby) {
                        if (entity instanceof LivingEntity living) {
                            living.setFireTicks(80);
                            applyNapalmBurn(living, enchantLevel, plugin);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ─── Central lingering cloud at impact point (lvl 2+) ────────────────────
    // Called exactly ONCE per projectile hit — no per-drop duplication.

    private void spawnLingeringCloud(World world, Location loc, int level, JavaPlugin plugin) {
        int    duration = 60 + (level * 20);
        double radius   = 1.5 + (level * 0.5);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration) { cancel(); return; }
                ticks += 2;

                for (int i = 0; i < 8; i++) {
                    double angle  = RANDOM.nextDouble() * Math.PI * 2;
                    double r      = RANDOM.nextDouble() * radius;
                    double height = RANDOM.nextDouble() * 1.2;
                    Location pLoc = loc.clone().add(
                            Math.cos(angle) * r, height, Math.sin(angle) * r);

                    Color dustColor = (i % 2 == 0)
                            ? Color.fromRGB(220,  30, 0)
                            : Color.fromRGB(255, 120, 0);

                    world.spawnParticle(
                            Particle.DUST_COLOR_TRANSITION,
                            pLoc, 1, 0, 0, 0, 0,
                            new Particle.DustTransition(dustColor,
                                    Color.fromRGB(10, 0, 0), 1.2f));
                }

                if (ticks % 4 == 0) {
                    world.spawnParticle(Particle.SMALL_FLAME, loc, 3,
                            radius * 0.4, 0.3, radius * 0.4, 0.02);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void spawnDustTransition(World world, Location loc,
                                     Color from, Color to,
                                     float size, int count) {
        world.spawnParticle(
                Particle.DUST_COLOR_TRANSITION,
                loc, count, 0.3, 0.1, 0.3, 0,
                new Particle.DustTransition(from, to, size));
    }

    // ─── Napalm Burn Effect ───────────────────────────────────────────────────
    // Tags the entity for 5 seconds. While tagged, every fire/fire-tick damage
    // event on that entity deals bonus damage: lvl I=+4, lvl II=+8, lvl III=+12.

    public static void applyNapalmBurn(LivingEntity entity, int level, JavaPlugin plugin) {
        UUID id = entity.getUniqueId();
        // Keep the highest level if they are already burning at a lower level
        napalmBurning.merge(id, level, Math::max);
        entity.setFireTicks(Math.max(entity.getFireTicks(), 100)); // at least 5s of fire

        // Schedule removal after 5 seconds (100 ticks) unless refreshed
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Only remove if the level hasn't been upgraded in the meantime
            napalmBurning.remove(id, level);
        }, 100L);
    }

    // ─── EntityDamageEvent — bonus fire damage ────────────────────────────────
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FIRE
                && cause != EntityDamageEvent.DamageCause.FIRE_TICK
                && cause != EntityDamageEvent.DamageCause.LAVA) return;

        if (!(event.getEntity() instanceof LivingEntity living)) return;

        Integer napalmLevel = napalmBurning.get(living.getUniqueId());
        if (napalmLevel == null) return;

        double bonus = napalmLevel * 2.0; // lvl 1=2, lvl 2=4, lvl 3=6
        event.setDamage(event.getDamage() + bonus);
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(Material.BOW, Material.CROSSBOW);
    }
}