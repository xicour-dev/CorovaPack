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
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WaterBallBook extends EnchantmentBook implements Listener {

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final double BALL_DIRECT_DAMAGE = 20.0;
    private static final double BEAM_DAMAGE        = 12.0;
    private static final double BALL_SPEED         = 0.4;
    private static final double BALL_RADIUS        = 0.65;
    private static final int    BALL_MAX_TICKS     = 100;
    private static final double BEAM_SPEED         = 0.7;
    private static final double BEAM_MAX_DIST      = 28.0;
    private static final int    BEAM_FIRE_INTERVAL = 20;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Random    random         = new Random();
    private final Set<UUID> firingThisTick = new HashSet<>();
    private final Map<UUID, List<LivingEntity[]>> activeTargetHolders = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────
    public WaterBallBook() {
        this(1);
    }

    public WaterBallBook(int level) {
        super(
                "Book of Water Ball",
                CorovaEnchantments.WATER_BALL_ID,
                level,
                "book_waterball",
                allowedMaterialsStatic()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterialsStatic() {
        return Stream.of(
                Material.matchMaterial("WOODEN_SPEAR"),
                Material.matchMaterial("STONE_SPEAR"),
                Material.matchMaterial("IRON_SPEAR"),
                Material.matchMaterial("GOLDEN_SPEAR"),
                Material.matchMaterial("COPPER_SPEAR"),
                Material.matchMaterial("DIAMOND_SPEAR"),
                Material.matchMaterial("NETHERITE_SPEAR")
        ).filter(Objects::nonNull).collect(Collectors.toSet());
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

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.WATER_BALL_ID)) return;

        // ── Left-click: Set target for active water balls ─────────────────────
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            LivingEntity target = resolveAimedTarget(player, 30);
            if (target != null) {
                event.setCancelled(true);
                List<LivingEntity[]> holders = activeTargetHolders.get(player.getUniqueId());
                if (holders != null && !holders.isEmpty()) {
                    for (LivingEntity[] holder : holders) {
                        holder[0] = target;
                    }
                    String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.WATER_BALL_ID, "Target locked");
                    player.sendMessage(enchantName + org.bukkit.ChatColor.GRAY + ": " + org.bukkit.ChatColor.WHITE + target.getName());
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                }
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        // ── Dedup: ignore the second event Bukkit fires on the same tick ─────
        UUID uuid = player.getUniqueId();
        if (firingThisTick.contains(uuid)) return;
        firingThisTick.add(uuid);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        plugin.getServer().getScheduler().runTask(plugin, () -> firingThisTick.remove(uuid));

        // ── Mana check ────────────────────────────────────────────────────────
        ManaManager mana = ManaManager.getInstance();
        if (mana == null) return;
        if (!mana.tryConsumeMana(player, ManaManager.COST_WATER_BALL)) return;

        fireBall(player, null);
    }

    // ── Target resolution ─────────────────────────────────────────────────────
    // FIX: Use World#rayTraceEntities instead of Player#getTargetEntity so that
    //      players are properly detected. getTargetEntity uses block-based look
    //      logic that skips entities in many Bukkit versions.
    private LivingEntity resolveAimedTarget(Player shooter, int range) {
        Location eyeLoc = shooter.getEyeLocation();
        Vector   dir    = eyeLoc.getDirection();

        RayTraceResult result = shooter.getWorld().rayTraceEntities(
                eyeLoc,
                dir,
                range,
                0.5,   // entity bounding-box inflation for easier hitting
                entity -> entity instanceof LivingEntity le && isValidTarget(le, shooter)
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity le) {
            return le;
        }
        return null;
    }

    private LivingEntity getNearestValidTarget(Location origin, double range, Player shooter) {
        LivingEntity best   = null;
        double       bestSq = range * range;
        for (Entity e : origin.getNearbyEntities(range, range, range)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!isValidTarget(le, shooter)) continue;
            double dSq = le.getLocation().distanceSquared(origin);
            if (dSq < bestSq) { bestSq = dSq; best = le; }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity le, Player shooter) {
        if (le.isDead() || !le.isValid()) return false;
        if (le.equals(shooter)) return false;
        if (le instanceof Player p) {
            if (p.getGameMode() == GameMode.CREATIVE
                    || p.getGameMode() == GameMode.SPECTATOR) return false;
            CorovaGuard guard = CorovaGuard.getInstance();
            if (guard != null && guard.isPlayerInSafeZone(p)) return false;
            CorovaTeams teams = CorovaTeams.getInstance();
            if (teams != null) {
                CorovaTeam attackerTeam = teams.getTeamManager().getTeamByPlayer(shooter.getUniqueId());
                CorovaTeam victimTeam   = teams.getTeamManager().getTeamByPlayer(p.getUniqueId());
                if (attackerTeam != null && attackerTeam.equals(victimTeam)
                        && !attackerTeam.hasFriendlyFire()) return false;
            }
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true if the block at this location should stop the ball/beam.
     * Water and waterlogged blocks are intentionally treated as passable so
     * the spell works correctly underwater.
     */
    private boolean isSolidObstacle(Block block) {
        if (!block.isPassable()) {
            Material type = block.getType();
            // Allow travel through water variants
            if (type == Material.WATER
                    || type == Material.BUBBLE_COLUMN
                    || type == Material.KELP
                    || type == Material.KELP_PLANT
                    || type == Material.SEAGRASS
                    || type == Material.TALL_SEAGRASS) {
                return false;
            }
            // Also allow waterlogged blocks (stairs, slabs, etc. filled with water)
            if (block.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) {
                return false;
            }
            return true; // genuine solid obstacle
        }
        return false;
    }

    // ── Ball logic ────────────────────────────────────────────────────────────
    private void fireBall(Player shooter, LivingEntity lockedTarget) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        Location   start  = shooter.getEyeLocation();
        Vector     dir    = start.getDirection().normalize();

        shooter.getWorld().playSound(start, Sound.BLOCK_WATER_AMBIENT, 1.0f, 0.8f);
        shooter.getWorld().spawnParticle(Particle.SPLASH, start, 20, 0.2, 0.2, 0.2, 0.15);

        final LivingEntity[] targetHolder = { lockedTarget };
        final UUID shooterUUID = shooter.getUniqueId();
        activeTargetHolders.computeIfAbsent(shooterUUID, k -> new ArrayList<>()).add(targetHolder);

        new BukkitRunnable() {
            final Location ballLoc = start.clone();
            int ticks = 0;

            private void cleanUp() {
                List<LivingEntity[]> holders = activeTargetHolders.get(shooterUUID);
                if (holders != null) {
                    holders.remove(targetHolder);
                    if (holders.isEmpty()) activeTargetHolders.remove(shooterUUID);
                }
            }

            @Override
            public void run() {
                if (ticks > BALL_MAX_TICKS || !shooter.isValid()) {
                    cleanUp();
                    cancel();
                    return;
                }

                ballLoc.add(dir.clone().multiply(BALL_SPEED));
                World w = ballLoc.getWorld();

                for (int i = 0; i < 14; i++) {
                    double theta  = random.nextDouble() * 2 * Math.PI;
                    double phi    = Math.acos(1.0 - 2.0 * random.nextDouble());
                    Vector offset = new Vector(
                            BALL_RADIUS * Math.sin(phi) * Math.cos(theta),
                            BALL_RADIUS * Math.sin(phi) * Math.sin(theta),
                            BALL_RADIUS * Math.cos(phi));
                    w.spawnParticle(Particle.DUST, ballLoc.clone().add(offset),
                            1, new Particle.DustOptions(Color.BLUE, 1.2f));
                    w.spawnParticle(Particle.DUST, ballLoc.clone().add(offset.clone().multiply(0.5)),
                            1, new Particle.DustOptions(Color.AQUA, 0.9f));
                }
                w.spawnParticle(Particle.DRIPPING_WATER, ballLoc, 3, 0.25, 0.25, 0.25, 0);

                // FIX: use isSolidObstacle() so water blocks don't stop the ball
                if (isSolidObstacle(ballLoc.getBlock())) {
                    w.spawnParticle(Particle.SPLASH, ballLoc, 40, 0.4, 0.4, 0.4, 0.3);
                    w.playSound(ballLoc, Sound.ENTITY_PLAYER_SPLASH, 1.0f, 0.9f);
                    cleanUp();
                    cancel();
                    return;
                }

                for (Entity e : w.getNearbyEntities(ballLoc, BALL_RADIUS, BALL_RADIUS, BALL_RADIUS)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (!isValidTarget(le, shooter)) continue;
                    double scaledDamage = WandEnchantListener.getScaledDamage(
                            BALL_DIRECT_DAMAGE, shooter.getInventory().getItemInMainHand(), shooter, le);
                    le.damage(scaledDamage, shooter);
                    w.spawnParticle(Particle.SPLASH, ballLoc, 50, 0.4, 0.4, 0.4, 0.4);
                    w.spawnParticle(Particle.DUST, ballLoc, 20,
                            0.4, 0.4, 0.4, 0, new Particle.DustOptions(Color.AQUA, 1.8f));
                    w.playSound(ballLoc, Sound.ENTITY_PLAYER_SPLASH, 1.2f, 0.8f);
                    cleanUp();
                    cancel();
                    return;
                }

                if (ticks % BEAM_FIRE_INTERVAL == 0) {
                    LivingEntity beamTarget = targetHolder[0];
                    if (beamTarget != null && beamTarget.isValid() && !beamTarget.isDead()) {
                        fireTravelingBeam(ballLoc.clone(), beamTarget, shooter, plugin);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Beam logic ────────────────────────────────────────────────────────────
    private void fireTravelingBeam(Location origin, LivingEntity target,
                                   Player shooter, JavaPlugin plugin) {
        Vector beamDir = target.getEyeLocation().toVector()
                .subtract(origin.toVector()).normalize();

        new BukkitRunnable() {
            final Location current  = origin.clone();
            double         distance = 0;

            @Override
            public void run() {
                if (distance > BEAM_MAX_DIST || !shooter.isValid()) { cancel(); return; }

                for (int sub = 0; sub < 3; sub++) {
                    current.add(beamDir.clone().multiply(BEAM_SPEED / 3.0));
                    distance += BEAM_SPEED / 3.0;
                    World w = current.getWorld();

                    w.spawnParticle(Particle.BUBBLE, current, 2, 0.04, 0.04, 0.04, 0);
                    w.spawnParticle(Particle.DUST, current, 1,
                            new Particle.DustOptions(Color.AQUA, 0.9f));

                    // FIX: use isSolidObstacle() so beam also passes through water
                    if (isSolidObstacle(current.getBlock())) {
                        w.spawnParticle(Particle.SPLASH, current, 20, 0.2, 0.2, 0.2, 0.3);
                        cancel();
                        return;
                    }

                    for (Entity e : w.getNearbyEntities(current, 0.4, 0.4, 0.4)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (!isValidTarget(le, shooter)) continue;
                        double scaledDamage = WandEnchantListener.getScaledDamage(
                                BEAM_DAMAGE, shooter.getInventory().getItemInMainHand(), shooter, le);
                        le.damage(scaledDamage, shooter);
                        w.spawnParticle(Particle.SPLASH, current, 25, 0.2, 0.2, 0.2, 0.4);
                        w.playSound(current, Sound.ENTITY_PLAYER_SPLASH, 0.8f, 1.2f);
                        cancel();
                        return;
                    }

                    if (distance > BEAM_MAX_DIST) { cancel(); return; }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}