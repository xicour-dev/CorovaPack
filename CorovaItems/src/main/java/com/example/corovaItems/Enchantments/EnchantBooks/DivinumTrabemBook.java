package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.WandEnchantListener;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.MageSystem.ManaManager;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DivinumTrabemBook extends EnchantmentBook implements Listener {

    // ── Tuning constants ──────────────────────────────────────────────────────

    private static final double BEAM_BASE_DAMAGE      = 20.0;
    /** Speed in blocks per tick (~3 b/t matches a fully charged arrow). */
    private static final double BEAM_SPEED            = 3.0;
    /** Max travel distance in blocks before the beam dissipates. */
    private static final double BEAM_MAX_DIST         = 60.0;
    /** Hitbox half-size around the beam tip for entity detection. */
    private static final double BEAM_HIT_RADIUS       = 0.35;
    /**
     * Sub-steps per tick. At BEAM_SPEED=3 and SUB_STEPS=12 we place a sample
     * every 0.25 blocks — tight enough to prevent gaps at any angle.
     */
    private static final int    SUB_STEPS             = 12;
    private static final int    CORE_DENSITY          = 4;
    private static final double CORE_SCATTER          = 0.06;
    private static final double RING_INTERVAL_BLOCKS  = 2.0;
    private static final int    RING_EXPAND_TICKS     = 8;
    private static final double RING_RADIUS_MIN       = 0.5;
    private static final double RING_RADIUS_MAX       = 1.75;
    private static final int    RING_POINTS           = 28;

    // ── Prismatic colour palette ──────────────────────────────────────────────

    private static final Color[] PRISM_COLORS = {
            Color.fromRGB(255, 200, 220), // rose
            Color.fromRGB(255, 220, 180), // peach
            Color.fromRGB(255, 255, 190), // pale yellow
            Color.fromRGB(200, 255, 210), // mint
            Color.fromRGB(190, 230, 255), // sky blue
            Color.fromRGB(200, 190, 255), // lavender
            Color.fromRGB(240, 200, 255), // lilac
    };

    // ── Per-level multipliers (computed once in constructor) ──────────────────

    private final int    level;
    private final double speedMultiplier;
    private final double damageMultiplier;
    private final double ringInterval;

    // ── State ─────────────────────────────────────────────────────────────────

    private final Random    random         = new Random();
    private final Set<UUID> firingThisTick = new HashSet<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    public DivinumTrabemBook() { this(1); }

    public DivinumTrabemBook(int level) {
        super(
                "Book of Divinum Trabem",
                CorovaEnchantments.DIVINUM_TRABEM_ID,
                level,
                "book_divinum_trabem_" + level,
                buildAllowedMaterials()
        );
        this.level = level;

        // Cache per-level multipliers so they are not recomputed on every fire.
        this.speedMultiplier  = level == 1 ? 0.5 : (level == 2 ? 0.75 : 1.0);
        this.damageMultiplier = speedMultiplier; // identical scaling
        this.ringInterval     = level == 2 ? RING_INTERVAL_BLOCKS * 2.0 : RING_INTERVAL_BLOCKS;

        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> buildAllowedMaterials() {
        return Stream.of(
                "WOODEN_SPEAR", "STONE_SPEAR", "IRON_SPEAR",
                "GOLDEN_SPEAR", "COPPER_SPEAR", "DIAMOND_SPEAR", "NETHERITE_SPEAR"
        ).map(Material::matchMaterial).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    // ── Wand-only restriction ─────────────────────────────────────────────────

    @Override
    public boolean canApplyTo(ItemStack stack) {
        if (stack == null) return false;
        if (!super.canApplyTo(stack)) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Wand");
    }

    // ── Interaction handler ───────────────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.DIVINUM_TRABEM_ID)) return;
        if (CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.DIVINUM_TRABEM_ID) != this.level) return;

        event.setCancelled(true);

        // Dedup: Bukkit fires two events per click (main + off-hand slot).
        UUID uuid = player.getUniqueId();
        if (!firingThisTick.add(uuid)) return;
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        plugin.getServer().getScheduler().runTask(plugin, () -> firingThisTick.remove(uuid));

        ManaManager mana = ManaManager.getInstance();
        if (mana == null || !mana.tryConsumeMana(player, ManaManager.COST_DIVINUM_TRABEM)) return;

        fireBeam(player, plugin);
    }

    // ── Public mob-facing entry point ─────────────────────────────────────────

    public void fireBeamForMob(LivingEntity shooter, JavaPlugin plugin) {
        Location eyeLoc = shooter.getEyeLocation();
        Vector   dir    = eyeLoc.getDirection().normalize();
        Vector   basisU = perpendicularVector(dir);
        Vector   basisV = dir.getCrossProduct(basisU).normalize();

        playCastSounds(shooter.getWorld(), eyeLoc);
        launchBeam(shooter, plugin, eyeLoc, dir, basisU, basisV, false);
    }

    // ── Private player-facing entry point ─────────────────────────────────────

    private void fireBeam(Player shooter, JavaPlugin plugin) {
        Location eyeLoc = shooter.getEyeLocation();
        Vector   dir    = eyeLoc.getDirection().normalize();
        Vector   basisU = perpendicularVector(dir);
        Vector   basisV = dir.getCrossProduct(basisU).normalize();

        World world = shooter.getWorld();
        playCastSounds(world, eyeLoc);

        // Muzzle burst
        for (Color c : PRISM_COLORS) {
            world.spawnParticle(Particle.DUST, eyeLoc, 4, 0.08, 0.08, 0.08, 0,
                    new Particle.DustOptions(c, 1.2f));
        }

        launchBeam(shooter, plugin, eyeLoc, dir, basisU, basisV, true);
    }

    // ── Shared beam logic ─────────────────────────────────────────────────────

    /**
     * Fires the raycasting beam runnable.
     *
     * @param isPlayer  {@code true} when the shooter is a {@link Player}; enables
     *                  scaled damage via {@link WandEnchantListener} and ring
     *                  effects for level > 1.
     */
    private void launchBeam(LivingEntity shooter, JavaPlugin plugin,
                            Location eyeLoc, Vector dir,
                            Vector basisU, Vector basisV,
                            boolean isPlayer) {
        new BukkitRunnable() {
            final Location tip        = eyeLoc.clone();
            double traveled           = 0.0;
            double sinceLastRing      = 0.0;
            final double stepDist     = (BEAM_SPEED * speedMultiplier) / SUB_STEPS;

            @Override
            public void run() {
                if (!shooter.isValid() || traveled >= BEAM_MAX_DIST) { cancel(); return; }

                for (int s = 0; s < SUB_STEPS; s++) {
                    tip.add(dir.clone().multiply(stepDist));
                    traveled      += stepDist;
                    sinceLastRing += stepDist;

                    World w = tip.getWorld();
                    if (w == null) { cancel(); return; }

                    // ── Solid beam core ───────────────────────────────────────
                    Color coreColor = prismColor(traveled);
                    for (int d = 0; d < CORE_DENSITY; d++) {
                        double ox = (random.nextDouble() - 0.5) * 2 * CORE_SCATTER;
                        double oy = (random.nextDouble() - 0.5) * 2 * CORE_SCATTER;
                        double oz = (random.nextDouble() - 0.5) * 2 * CORE_SCATTER;
                        w.spawnParticle(Particle.DUST, tip.clone().add(ox, oy, oz),
                                1, 0, 0, 0, 0, new Particle.DustOptions(coreColor, 0.7f));
                    }

                    // ── Ring spawning (player, level > 1 only) ────────────────
                    if (isPlayer && level > 1 && sinceLastRing >= ringInterval) {
                        sinceLastRing = 0.0;
                        double targetRadius = RING_RADIUS_MIN
                                + random.nextDouble() * (RING_RADIUS_MAX - RING_RADIUS_MIN);
                        Color ringColor = PRISM_COLORS[random.nextInt(PRISM_COLORS.length)];
                        spawnRing(plugin, w, tip.clone(), basisU, basisV,
                                targetRadius, ringColor, random.nextDouble() < 0.4);
                    }

                    // ── Block collision ───────────────────────────────────────
                    if (!tip.getBlock().isPassable()) {
                        for (Color c : PRISM_COLORS) {
                            w.spawnParticle(Particle.DUST, tip, 6, 0.15, 0.15, 0.15, 0,
                                    new Particle.DustOptions(c, 1.5f));
                        }
                        w.playSound(tip, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.8f, 1.4f);
                        cancel();
                        return;
                    }

                    // ── Entity collision ──────────────────────────────────────
                    for (Entity e : w.getNearbyEntities(tip, BEAM_HIT_RADIUS, BEAM_HIT_RADIUS, BEAM_HIT_RADIUS)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (!isValidTarget(le, shooter)) continue;

                        double damage;
                        if (isPlayer && shooter instanceof Player p) {
                            damage = WandEnchantListener.getScaledDamage(
                                    BEAM_BASE_DAMAGE * damageMultiplier,
                                    p.getInventory().getItemInMainHand(), p, le);
                        } else {
                            damage = BEAM_BASE_DAMAGE * damageMultiplier;
                        }

                        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(shooter.getUniqueId());
                        try {
                            le.damage(damage, shooter);
                        } finally {
                            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(shooter.getUniqueId());
                        }

                        for (Color c : PRISM_COLORS) {
                            w.spawnParticle(Particle.DUST, tip, 8, 0.2, 0.2, 0.2, 0,
                                    new Particle.DustOptions(c, 1.8f));
                        }
                        w.spawnParticle(Particle.FLASH, tip, 1, 0, 0, 0, 0);
                        w.playSound(tip, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.9f, 2.0f);
                        w.playSound(tip, Sound.BLOCK_AMETHYST_BLOCK_CHIME,   0.8f, 1.2f);
                        cancel();
                        return;
                    }

                    if (traveled >= BEAM_MAX_DIST) { cancel(); return; }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Ring logic ────────────────────────────────────────────────────────────

    private void spawnRing(JavaPlugin plugin, World world, Location center,
                           Vector basisU, Vector basisV,
                           double targetRadius, Color color, boolean expands) {
        float particleSize = (float) (0.6 + (targetRadius / RING_RADIUS_MAX) * 1.0);

        if (!expands) {
            drawRingFrame(world, center, basisU, basisV, targetRadius, color, particleSize);
            return;
        }

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= RING_EXPAND_TICKS) { cancel(); return; }
                double progress = (double) tick / (RING_EXPAND_TICKS - 1);
                double radius   = targetRadius * progress;
                float  size     = Math.max(0.3f, particleSize * (float) (1.0 - progress * 0.35));
                drawRingFrame(world, center, basisU, basisV, radius, color, size);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void drawRingFrame(World world, Location center,
                                      Vector basisU, Vector basisV,
                                      double radius, Color color, float size) {
        if (radius < 0.01) return;
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = 2 * Math.PI * i / RING_POINTS;
            Location pt  = center.clone()
                    .add(basisU.clone().multiply(Math.cos(angle) * radius))
                    .add(basisV.clone().multiply(Math.sin(angle) * radius));
            world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, size));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void playCastSounds(World world, Location loc) {
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME,  1.0f, 1.6f);
        world.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.8f);
    }

    /** Maps beam travel distance to a smoothly cycling prismatic colour. */
    private static Color prismColor(double dist) {
        double t    = (dist % 14.0) / 14.0;
        int    idx  = (int) (t * PRISM_COLORS.length) % PRISM_COLORS.length;
        int    next = (idx + 1) % PRISM_COLORS.length;
        double frac = (t * PRISM_COLORS.length) - idx;

        Color a = PRISM_COLORS[idx];
        Color b = PRISM_COLORS[next];
        return Color.fromRGB(
                lerp(a.getRed(),   b.getRed(),   frac),
                lerp(a.getGreen(), b.getGreen(), frac),
                lerp(a.getBlue(),  b.getBlue(),  frac));
    }

    private static int lerp(int a, int b, double t) {
        return Math.max(0, Math.min(255, (int) (a + t * (b - a))));
    }

    /** Returns an arbitrary unit vector perpendicular to {@code dir}. */
    private static Vector perpendicularVector(Vector dir) {
        Vector ref = Math.abs(dir.getX()) < 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        return dir.getCrossProduct(ref).normalize();
    }

    private boolean isValidTarget(LivingEntity le, LivingEntity shooter) {
        if (le.isDead() || !le.isValid()) return false;
        if (le.equals(shooter)) return false;
        if (!(le instanceof Player p)) return true;

        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return false;

        CorovaGuard guard = CorovaGuard.getInstance();
        if (guard != null && guard.isPlayerInSafeZone(p)) return false;

        if (shooter instanceof Player attackerPlayer) {
            CorovaTeams teams = CorovaTeams.getInstance();
            if (teams != null) {
                CorovaTeam attackerTeam = teams.getTeamManager().getTeamByPlayer(attackerPlayer.getUniqueId());
                CorovaTeam victimTeam   = teams.getTeamManager().getTeamByPlayer(p.getUniqueId());
                if (attackerTeam != null && attackerTeam.equals(victimTeam)
                        && !attackerTeam.hasFriendlyFire()) return false;
            }
        }
        return true;
    }

    // ── Anvil upgrade ─────────────────────────────────────────────────────────

    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right,
                                            NamespacedKey keyId, NamespacedKey keyLvl) {
        if (left == null || right == null) return null;

        ItemMeta leftMeta  = left.getItemMeta();
        ItemMeta rightMeta = right.getItemMeta();
        if (!(leftMeta  instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta lbm)) return null;
        if (!(rightMeta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta rbm)) return null;

        String idL = lbm.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String idR = rbm.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        if (!CorovaEnchantments.DIVINUM_TRABEM_ID.equals(idL)
                || !CorovaEnchantments.DIVINUM_TRABEM_ID.equals(idR)) return null;

        int lvlL = lbm.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);
        int lvlR = rbm.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);

        return (lvlL == lvlR && lvlL < 3) ? new DivinumTrabemBook(lvlL + 1).getItemStack() : null;
    }
}