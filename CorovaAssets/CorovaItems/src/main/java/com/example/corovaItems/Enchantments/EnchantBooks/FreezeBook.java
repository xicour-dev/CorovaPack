package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class FreezeBook extends EnchantmentBook implements Listener {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final long COOLDOWN_MS = 10_000L;

    // ── Shared state (static so all level-instances share the same sets) ──────

    private static final Set<UUID>            freezingPlayers = new HashSet<>();
    private static final Set<UUID>            frozenEntities  = new HashSet<>();
    private static final Map<UUID, Location>  frozenLocations = new HashMap<>();
    private static final Map<UUID, Integer>   chargeMap       = new HashMap<>();
    private static final Map<UUID, Long>      cooldownMap     = new HashMap<>();

    // ── Instance state ────────────────────────────────────────────────────────

    private final JavaPlugin plugin;

    // ── Constructors ──────────────────────────────────────────────────────────

    public FreezeBook() { this(1); }

    public FreezeBook(int level) {
        super(
                "Book of Freeze",
                CorovaEnchantments.FREEZE_ID,
                level,
                "book_freeze_" + level,
                Set.of(
                        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                        Material.WOODEN_AXE,   Material.STONE_AXE,   Material.IRON_AXE,
                        Material.GOLDEN_AXE,   Material.DIAMOND_AXE,  Material.NETHERITE_AXE,
                        Material.TRIDENT, Material.MACE
                )
        );
        this.plugin = JavaPlugin.getProvidingPlugin(getClass());
        ItemManager.getInstance().registerItem(this);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * External entry point (e.g. Boomerang). Performs a single-target freeze,
     * respecting safe-zones and team checks. Does NOT consume or check cooldown.
     */
    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (!isValidVictim(damager, target)) return;
        if (frozenEntities.contains(target.getUniqueId())) return;

        ItemStack weapon = resolveWeapon(damager);
        freezeSingleTarget(damager, target, level, weapon);
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /** Damage multiplier on a frozen target (levels 4-5 only). */
    // ignoreCancelled = false so this fires even when CorovaCombat cancelled the hit
    // at LOW priority (attacker cooldown). We uncancel for frozen targets explicitly.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Single-instance guard: only level-1 listener applies this; level is
        // resolved from the weapon PDC at runtime so all levels are covered.
        if (getLevel() != 1) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;

        if (!(event.getDamager() instanceof LivingEntity damager)) return;
        if (!(event.getEntity()  instanceof LivingEntity victim))  return;

        // If the victim isn't frozen, don't touch the event at all.
        if (!frozenEntities.contains(victim.getUniqueId())) return;

        // Victim IS frozen — always uncancel and clear noDamageTicks so the hit lands.
        event.setCancelled(false);
        victim.setNoDamageTicks(0);

        // Apply bonus multiplier only if the attacker is holding a Freeze weapon (levels 4-5).
        ItemStack weapon = damager.getEquipment() == null
                ? null : damager.getEquipment().getItemInMainHand();
        if (weapon == null || !CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.FREEZE_ID)) return;

        int level = CorovaEnchantments.getEnchantLevel(weapon, CorovaEnchantments.FREEZE_ID);
        double multiplier = switch (level) {
            case 4  -> 2.0;
            case 5  -> 3.0;
            default -> 1.0;
        };
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    /** Lock frozen players in place. */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (getLevel() != 1) return;
        Player player = event.getPlayer();
        if (!frozenEntities.contains(player.getUniqueId())) return;

        Location frozen = frozenLocations.get(player.getUniqueId());
        if (frozen == null) return;

        // Allow head rotation but block positional movement.
        event.setTo(new Location(frozen.getWorld(), frozen.getX(), frozen.getY(), frozen.getZ(),
                event.getTo().getYaw(), event.getTo().getPitch()));
    }

    /** Cancel fall/contact damage while frozen. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (getLevel() != 1) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FALL
                && cause != EntityDamageEvent.DamageCause.CONTACT) return;

        if (event.getEntity() instanceof LivingEntity le
                && frozenEntities.contains(le.getUniqueId())) {
            event.setDamage(0);
            event.setCancelled(true);
        }
    }

    /** Clean up all state when a player disconnects. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (getLevel() != 1) return;
        UUID id = event.getPlayer().getUniqueId();
        event.getPlayer().setWalkSpeed(0.2f);
        freezingPlayers.remove(id);
        frozenEntities.remove(id);
        frozenLocations.remove(id);
        chargeMap.remove(id);
        cooldownMap.remove(id);
    }

    /** Shift to charge, release to blast. */
    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (getLevel() != 1) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.FREEZE_ID)) return;

        int  level = CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.FREEZE_ID);
        UUID uuid  = player.getUniqueId();
        if (freezingPlayers.contains(uuid)) return;

        long now      = System.currentTimeMillis();
        long lastUsed = cooldownMap.getOrDefault(uuid, 0L);
        if (now - lastUsed < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
            player.sendActionBar(enchantName("Freeze")
                    .append(Component.text(" is on cooldown: " + remaining + "s", NamedTextColor.RED)));
            return;
        }

        freezingPlayers.add(uuid);
        chargeMap.put(uuid, 0);

        int maxRadius = switch (level) {
            case 1  ->  5;
            case 2  ->  7;
            case 3  ->  9;
            case 4  -> 11;
            case 5  -> 14;
            default ->  5;
        };
        long tickInterval = switch (level) {
            case 1, 2 -> 20L;
            case 3, 4 -> 10L;
            case 5    ->  7L;
            default   -> 20L;
        };

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !player.isSneaking() || !freezingPlayers.contains(uuid)) {
                    int charge = chargeMap.getOrDefault(uuid, 0);
                    if (charge > 0) activateAoe(player, charge, level);
                    cleanup(uuid);
                    cancel();
                    return;
                }

                int charge = chargeMap.getOrDefault(uuid, 0);
                if (charge >= maxRadius) {
                    activateAoe(player, maxRadius, level);
                    cleanup(uuid);
                    cancel();
                    return;
                }

                int next = Math.min(maxRadius, charge + 1);
                chargeMap.put(uuid, next);
                player.sendActionBar(enchantName("Freeze")
                        .append(Component.text(" Radius: " + next,
                                TextColor.color(0x00FFFF), TextDecoration.BOLD)));
            }
        }.runTaskTimer(plugin, 0L, tickInterval);
    }

    // ── AOE activation ────────────────────────────────────────────────────────

    private static void activateAoe(LivingEntity caster, int radius, int level) {
        UUID      activatorId = caster.getUniqueId();
        ItemStack weapon      = resolveWeapon(caster);

        cooldownMap.put(activatorId, System.currentTimeMillis());

        List<LivingEntity>  burstTargets = new ArrayList<>();
        List<String>        frozenNames  = new ArrayList<>();

        for (Entity entity : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (activatorId.equals(target.getUniqueId())) continue;
            if (!isValidVictim(caster, target)) continue;
            if (frozenEntities.contains(target.getUniqueId())) continue;

            frozenNames.add(target.getName());
            freezeSingleTarget(caster, target, level, weapon, false);
            if (level >= 4) burstTargets.add(target);
        }

        // Stagger burst damage so noDamageTicks don't absorb consecutive hits.
        if (level >= 4 && !burstTargets.isEmpty()) {
            double burstDamage = EnchantmentBook.getWeaponDamage(caster);
            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(activatorId);
            for (LivingEntity target : burstTargets) {
                if (!target.isValid() || target.isDead()) continue;
                int savedMax = target.getMaximumNoDamageTicks();
                target.setMaximumNoDamageTicks(0);
                target.setNoDamageTicks(0);
                target.damage(burstDamage, caster);
                target.setMaximumNoDamageTicks(savedMax);
            }
            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(activatorId);
        }

        if (caster instanceof Player player) {
            if (!frozenNames.isEmpty()) {
                Component names = buildNameList(frozenNames);
                player.sendActionBar(Component.text("You have ", NamedTextColor.GRAY)
                        .append(enchantName("frozen"))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(names));
            } else {
                player.sendActionBar(Component.text("No one in range...", NamedTextColor.RED));
            }
        }
    }

    // ── Core single-target freeze ─────────────────────────────────────────────

    private static void freezeSingleTarget(LivingEntity caster, LivingEntity target,
                                           int level, ItemStack weapon) {
        freezeSingleTarget(caster, target, level, weapon, true);
    }

    private static void freezeSingleTarget(LivingEntity caster, LivingEntity target,
                                           int level, ItemStack weapon,
                                           boolean applyBurstDamage) {
        JavaPlugin plugin  = JavaPlugin.getProvidingPlugin(FreezeBook.class);
        UUID       targetId = target.getUniqueId();

        frozenEntities.add(targetId);

        int freezeTicks = switch (level) {
            case 1  ->  2;
            case 2  ->  3;
            case 3  ->  4;
            case 4  ->  5;
            case 5  ->  6;
            default ->  2;
        } * 20;
        freezeTicks += MutationManager.getInstance().getSynergyHandler().getFreezeDurationBonus(weapon);
        final int finalFreezeTicks = freezeTicks;

        // Burst damage for levels 4/5 (external/single-target path only).
        if (applyBurstDamage && level >= 4) {
            double burstDamage = EnchantmentBook.getWeaponDamage(caster);
            int savedMax = target.getMaximumNoDamageTicks();
            target.setMaximumNoDamageTicks(0);
            target.setNoDamageTicks(0);

            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(caster.getUniqueId());
            try {
                target.damage(burstDamage, caster);
            } finally {
                com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(caster.getUniqueId());
            }
            target.setMaximumNoDamageTicks(savedMax);
        }

        if (target instanceof Player playerTarget) {
            frozenLocations.put(playerTarget.getUniqueId(), playerTarget.getLocation());
            playerTarget.setWalkSpeed(0f);
            playerTarget.sendActionBar(
                    Component.text("You have been ", NamedTextColor.AQUA)
                            .append(enchantName("frozen").decorate(TextDecoration.BOLD))
                            .append(Component.text(" by " + caster.getName() + "!", NamedTextColor.AQUA)));
        }

        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.5f);
        spawnFreezeExplosion(target);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= finalFreezeTicks || !target.isValid()
                        || !frozenEntities.contains(targetId)) {
                    frozenEntities.remove(targetId);

                    if (target instanceof Player p) {
                        frozenLocations.remove(p.getUniqueId());
                        if (p.isOnline()) {
                            p.setWalkSpeed(0.2f);
                            p.sendActionBar(Component.text("You are no longer ", NamedTextColor.GRAY)
                                    .append(enchantName("frozen")));
                            if (level == 5) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        }
                    } else if (level == 5) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                    }
                    cancel();
                    return;
                }

                target.setVelocity(new Vector(0, 0, 0));

                // NOTE: do NOT teleport frozen players every tick. The onMove handler
                // above already cancels positional movement client-side, which is
                // sufficient to lock them in place. Teleporting every tick forces Paper
                // to resync the player's server-side position each tick, which causes
                // incoming attack packets to fail Paper's reach/position validation —
                // making frozen players unhittable. Velocity zeroing handles mobs;
                // onMove handles players.

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private static void spawnFreezeExplosion(Entity entity) {
        Location loc   = entity.getLocation().add(0, 1, 0);
        World    world = entity.getWorld();
        Random   rng   = new Random();

        for (int i = 0; i < 40; i++) {
            double angle   = rng.nextDouble() * 2 * Math.PI;
            double spread  = rng.nextDouble();
            double ox = Math.cos(angle) * spread;
            double oy = rng.nextDouble() * 0.5 + 0.2;
            double oz = Math.sin(angle) * spread;
            world.spawnParticle(Particle.CLOUD,     loc, 1, ox, oy, oz, 0.3);
            world.spawnParticle(Particle.SNOWFLAKE,  loc, 1, ox, oy, oz, 0.3);
        }

        world.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 20, 0.5, 0.5, 0.5, 0,
                Material.ICE.createBlockData());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void cleanup(UUID uuid) {
        freezingPlayers.remove(uuid);
        chargeMap.remove(uuid);
    }

    private static ItemStack resolveWeapon(LivingEntity entity) {
        if (entity instanceof Player p) return p.getInventory().getItemInMainHand();
        return entity.getEquipment() != null ? entity.getEquipment().getItemInMainHand() : null;
    }

    /**
     * Returns {@code true} if {@code target} is a valid freeze victim.
     * Safe-zone and friendly-fire checks are applied for player targets.
     */
    private static boolean isValidVictim(LivingEntity attacker, LivingEntity target) {
        if (!(target instanceof Player victimPlayer)) return true;

        CorovaGuard guard = CorovaGuard.getInstance();
        if (guard != null && guard.isPlayerInSafeZone(victimPlayer)) return false;

        CorovaTeams teams = CorovaTeams.getInstance();
        if (teams != null) {
            CorovaTeam attackerTeam = teams.getTeamManager().getTeamByPlayer(attacker.getUniqueId());
            CorovaTeam victimTeam   = teams.getTeamManager().getTeamByPlayer(victimPlayer.getUniqueId());
            if (attackerTeam != null && attackerTeam.equals(victimTeam)
                    && !attackerTeam.hasFriendlyFire()) return false;
        }
        return true;
    }

    private static Component enchantName(String label) {
        return EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.FREEZE_ID, label);
    }

    private static Component buildNameList(List<String> names) {
        Component comp = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            comp = comp.append(Component.text(names.get(i), NamedTextColor.AQUA));
            if (i < names.size() - 1) comp = comp.append(Component.text(", ", NamedTextColor.GRAY));
        }
        return comp;
    }
}