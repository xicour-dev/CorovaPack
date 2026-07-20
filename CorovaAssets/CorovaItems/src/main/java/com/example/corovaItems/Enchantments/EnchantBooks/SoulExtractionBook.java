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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Soul Extraction — fires a soul ball identical to the SoulBlaze mob attack.
 *
 * Cost:    30 mana (reduced by Mana Conservation if present).
 * On hit:  restores 60 mana to the caster. Only triggers on non-player entities
 *          so it cannot be exploited on other players.
 *
 * Wand-only: can only be applied to items with "Wand" in their display name.
 */
public class SoulExtractionBook extends EnchantmentBook implements Listener {

    // ── Tuning — mirrors SoulBlazeMob constants ───────────────────────────────
    private static final double BALL_SPEED     = 0.75;
    private static final double BALL_RADIUS    = 0.55;
    private static final double HIT_RADIUS     = 0.65;
    private static final int    BALL_MAX_TICKS = 100;
    private static final double BALL_DAMAGE    = 15.0;
    private static final double MANA_RESTORE   = 60.0;

    // ── Windup — mirrors SoulBlazeMob ─────────────────────────────────────────
    private static final int WINDUP_TICKS = 20;

    private static final Random    random         = new Random();
    private static final Set<UUID> firingThisTick = new HashSet<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public SoulExtractionBook() {
        this(1);
    }

    public SoulExtractionBook(int level) {
        super(
                "Book of Soul Extraction",
                CorovaEnchantments.SOUL_EXTRACTION_ID,
                level,
                "book_soulextraction",
                allowedMaterials()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterials() {
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

    /** Wand-only restriction. */
    @Override
    public boolean canApplyTo(ItemStack stack) {
        if (stack == null) return false;
        if (!super.canApplyTo(stack)) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.getDisplayName().contains("Wand");
    }

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (!(damager instanceof Player player)) return;

        ManaManager mana = ManaManager.getInstance();
        if (mana == null) return;
        if (!mana.tryConsumeMana(player, ManaManager.COST_SOUL_EXTRACTION)) return;

        // Play windup then launch ball — exactly as SoulBlazeMob does for shot 1.
        playWindupStatic(player, () -> fireSoulBallStatic(player));
    }

    // ── Right-click handler ───────────────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack held   = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(held, CorovaEnchantments.SOUL_EXTRACTION_ID)) return;

        event.setCancelled(true);

        // ── Dedup: ignore the second event Bukkit fires on the same tick ─────
        UUID uuid = player.getUniqueId();
        if (firingThisTick.contains(uuid)) return;
        firingThisTick.add(uuid);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        plugin.getServer().getScheduler().runTask(plugin, () -> firingThisTick.remove(uuid));

        triggerEffect(player, null, CorovaEnchantments.getEnchantLevel(held));
    }

    public static void fireSoulBallForMob(LivingEntity shooter, JavaPlugin plugin) {
        Location origin = shooter.getEyeLocation();
        Vector direction = origin.getDirection().normalize().multiply(BALL_SPEED);
        shooter.getWorld().playSound(origin, Sound.BLOCK_SOUL_SAND_PLACE, 0.8f, 1.6f);
        new BukkitRunnable() {
            final Location pos = origin.clone();
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= BALL_MAX_TICKS || !shooter.isValid()) { cancel(); return; }
                for (int sub = 0; sub < 3; sub++) {
                    pos.add(direction.clone().multiply(1.0 / 3.0));
                    spawnBallParticles(pos);
                    if (!pos.getBlock().getType().isAir()) {
                        cancel();
                        return;
                    }
                    for (Entity nearby : pos.getWorld().getNearbyEntities(pos, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                        if (!(nearby instanceof LivingEntity le)) continue;
                        if (le.equals(shooter)) continue;
                        le.damage(BALL_DAMAGE, shooter);
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Windup — identical logic to SoulBlazeMob.playWindup ──────────────────

    private void playWindup(Player caster, Runnable onComplete) {
        playWindupStatic(caster, onComplete);
    }

    private static void playWindupStatic(Player caster, Runnable onComplete) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(SoulExtractionBook.class);

        caster.getWorld().playSound(
                caster.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 1.8f);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                if (!caster.isOnline()) { cancel(); return; }
                if (tick >= WINDUP_TICKS) { cancel(); onComplete.run(); return; }
                spawnWindupParticlesStatic(caster, tick);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Soul-ball travel — identical logic to SoulBlazeMob.fireSoulBall ──────

    private void fireSoulBall(Player caster) {
        fireSoulBallStatic(caster);
    }

    private static void fireSoulBallStatic(Player caster) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(SoulExtractionBook.class);
        Location   origin = caster.getEyeLocation();

        // Aim at whatever the player is looking at (target entity or eye direction).
        LivingEntity aimed = getAimedTarget(caster, 30);
        Vector direction;
        if (aimed != null) {
            direction = aimed.getEyeLocation().toVector()
                    .subtract(origin.toVector())
                    .normalize();
        } else {
            direction = origin.getDirection().normalize();
        }

        // Tiny random spread matching SoulBlazeMob
        direction.add(new Vector(
                (random.nextDouble() - 0.5) * 0.08,
                (random.nextDouble() - 0.5) * 0.04,
                (random.nextDouble() - 0.5) * 0.08
        )).normalize().multiply(BALL_SPEED);

        caster.getWorld().playSound(origin, Sound.BLOCK_SOUL_SAND_PLACE, 0.8f, 1.6f);

        new BukkitRunnable() {
            final Location pos = origin.clone();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= BALL_MAX_TICKS || !caster.isOnline()) { cancel(); return; }

                // Three sub-steps per tick, same as SoulBlazeMob
                for (int sub = 0; sub < 3; sub++) {
                    pos.add(direction.clone().multiply(1.0 / 3.0));
                    spawnBallParticles(pos);

                    // Block collision
                    if (!pos.getBlock().getType().isAir()) {
                        spawnImpactParticles(pos);
                        pos.getWorld().playSound(pos, Sound.BLOCK_SOUL_SAND_HIT, 1.0f, 1.3f);
                        cancel();
                        return;
                    }

                    // Entity collision
                    for (Entity nearby : pos.getWorld().getNearbyEntities(
                            pos, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                        if (!(nearby instanceof LivingEntity le)) continue;
                        if (le.equals(caster)) continue;

                        // Team / safe-zone checks for player targets
                        if (le instanceof Player victim) {
                            if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                                CorovaGuard.sendSafeZoneMessage(caster);
                                cancel();
                                return;
                            }
                            CorovaTeams teams = CorovaTeams.getInstance();
                            if (teams != null) {
                                CorovaTeam at = teams.getTeamManager().getTeamByPlayer(caster.getUniqueId());
                                CorovaTeam vt = teams.getTeamManager().getTeamByPlayer(victim.getUniqueId());
                                if (at != null && at.equals(vt) && !at.hasFriendlyFire()) {
                                    cancel();
                                    return;
                                }
                            }
                            // Hitting a player: damage but NO mana restore
                            if (!com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
                                double scaledDamage = WandEnchantListener.getScaledDamage(
                                        BALL_DAMAGE, caster.getInventory().getItemInMainHand(), caster, le);
                                le.damage(scaledDamage, caster);
                            }
                        } else {
                            // Mob hit: damage + mana restore
                            if (!com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
                                double scaledDamage = WandEnchantListener.getScaledDamage(
                                        BALL_DAMAGE, caster.getInventory().getItemInMainHand(), caster, le);
                                le.damage(scaledDamage, caster);
                            }

                            ManaManager mana = ManaManager.getInstance();
                            if (mana != null) {
                                mana.restoreMana(caster, MANA_RESTORE);
                                caster.sendActionBar(ChatColor.DARK_PURPLE + "✦ Soul Extracted! +"
                                        + (int) MANA_RESTORE + " mana");
                                caster.playSound(caster.getLocation(),
                                        Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                            }
                        }

                        spawnImpactParticles(pos);
                        pos.getWorld().playSound(pos, Sound.BLOCK_SOUL_SAND_HIT, 1.0f, 1.0f);
                        cancel();
                        return;
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Target helper ─────────────────────────────────────────────────────────

    private static LivingEntity getAimedTarget(Player caster, int range) {
        Entity entity = caster.getTargetEntity(range);
        if (entity instanceof LivingEntity le
                && !le.isDead()
                && le.isValid()
                && !le.equals(caster)) {
            return le;
        }
        return null;
    }

    // ── Particle helpers — faithful copies from SoulBlazeMob ─────────────────

    private void spawnWindupParticles(Player caster, int ticksElapsed) {
        spawnWindupParticlesStatic(caster, ticksElapsed);
    }

    private static void spawnWindupParticlesStatic(Player caster, int ticksElapsed) {
        World    w       = caster.getWorld();
        Location centre  = caster.getLocation().clone().add(0, 1.0, 0);
        double progress  = ticksElapsed / (double) WINDUP_TICKS;
        double radius    = 2.0 - progress * 1.6;
        double baseAngle = ticksElapsed * 0.35;

        for (int i = 0; i < 12; i++) {
            double angle = baseAngle + (2 * Math.PI / 12) * i;
            Location pt = centre.clone().add(
                    Math.cos(angle) * radius,
                    Math.sin(i * 0.5) * 0.3,
                    Math.sin(angle) * radius
            );
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0, 0, 0, 0.0);
        }

        if (progress > 0.6)
            w.spawnParticle(Particle.SOUL, centre, 3, 0.20, 0.30, 0.20, 0.02);

        if (ticksElapsed % 6 == 0) {
            float pitch = 0.8f + (float) progress * 1.2f;
            w.playSound(centre, Sound.BLOCK_SOUL_SAND_PLACE, 0.4f, pitch);
        }
    }

    private static void spawnBallParticles(Location pos) {
        World w = pos.getWorld();

        w.spawnParticle(Particle.SOUL_FIRE_FLAME, pos, 3,
                BALL_RADIUS * 0.3, BALL_RADIUS * 0.3, BALL_RADIUS * 0.3, 0.01);

        for (int i = 0; i < 6; i++) {
            double theta  = random.nextDouble() * 2 * Math.PI;
            double phi    = Math.acos(1.0 - 2.0 * random.nextDouble());
            Vector offset = new Vector(
                    BALL_RADIUS * Math.sin(phi) * Math.cos(theta),
                    BALL_RADIUS * Math.sin(phi) * Math.sin(theta),
                    BALL_RADIUS * Math.cos(phi)
            );
            w.spawnParticle(Particle.SOUL, pos.clone().add(offset), 1, 0, 0, 0, 0);
        }

        w.spawnParticle(Particle.ASH, pos, 1, 0.05, 0.05, 0.05, 0.01);
    }

    private static void spawnImpactParticles(Location pos) {
        World w = pos.getWorld();
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, pos, 18, 0.35, 0.35, 0.35, 0.12);
        w.spawnParticle(Particle.SOUL,            pos, 12, 0.40, 0.40, 0.40, 0.08);
        w.spawnParticle(Particle.ASH,             pos, 10, 0.30, 0.30, 0.30, 0.05);
        w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, pos, 4, 0.20, 0.10, 0.20, 0.02);
    }
}