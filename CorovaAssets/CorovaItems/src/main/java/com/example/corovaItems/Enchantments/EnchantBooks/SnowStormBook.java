package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.ItemMutations.MutationManager;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SnowStormBook extends EnchantmentBook implements Listener {

    private final JavaPlugin plugin;

    private static final Set<UUID> chargingPlayers      = new HashSet<>();
    private static final Set<UUID> blindedPlayers        = new HashSet<>();
    private static final Set<UUID> snowstormHitPlayers   = new HashSet<>(); // tracks entities hit by snowstorm blast for damage multiplier

    /**
     * Guards against onEntityDamage applying the snowstorm multiplier during
     * the initial blast damage. When activateDirectStatic calls target.damage(),
     * it fires EntityDamageByEntityEvent which would normally trigger
     * onEntityDamage and multiply the burst damage.
     */
    private static final Set<UUID> aoeBurstActive = new HashSet<>();
    private static final Map<UUID, Integer> chargeMap    = new HashMap<>();
    private static final Map<UUID, Long>    cooldownMap  = new HashMap<>();
    private static final Map<UUID, BukkitRunnable> activeBlindTasks = new HashMap<>();

    // Cooldown is 15 seconds for all levels
    private static final long COOLDOWN_MS = 15_000L;

    public SnowStormBook() {
        this(1);
    }

    public SnowStormBook(int level) {
        super(
                "Book of Snowstorm",
                CorovaEnchantments.SNOWSTORM_ID,
                level,
                "book_snowstorm",
                allowedMaterialsStatic()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterialsStatic() {
        Set<Material> s = new HashSet<>();
        s.add(Material.WOODEN_SWORD);
        s.add(Material.STONE_SWORD);
        s.add(Material.IRON_SWORD);
        s.add(Material.GOLDEN_SWORD);
        s.add(Material.DIAMOND_SWORD);
        s.add(Material.NETHERITE_SWORD);
        s.add(Material.WOODEN_AXE);
        s.add(Material.STONE_AXE);
        s.add(Material.IRON_AXE);
        s.add(Material.GOLDEN_AXE);
        s.add(Material.DIAMOND_AXE);
        s.add(Material.NETHERITE_AXE);
        s.add(Material.TRIDENT);
        return s;
    }

    // ── Damage multiplier on hit ──────────────────────────────────────────────
    //
    // FIX 1: Guard with getLevel() != 1 so only one listener instance handles
    //         this event (same pattern as FreezeBook). Without this, all 5
    //         registered instances fire and stack-multiply the damage.
    //
    // FIX 2: The multiplier is applied on EVERY hit against a snowstorm-blasted
    //         target (one who is currently blinded/slowed from the AOE). The
    //         original code only applied the multiplier if the victim was already
    //         in snowstormHitPlayers, but that set is only populated during
    //         activateDirectStatic — so normal melee hits never saw it. The fix
    //         reads the level from the weapon PDC at runtime (same as MusicBook)
    //         and applies the multiplier directly whenever the victim is in the set.

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Single-instance guard: only the level-1 listener handles this event.
        // The level is read from the weapon at runtime so all levels are covered.
        // HIGHEST priority ensures Sharpness and all other enchantment bonuses are
        // already factored into event.getDamage() before we apply the multiplier.
        if (getLevel() != 1) return;

        if (!(event.getDamager() instanceof LivingEntity damager)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;

        ItemStack weapon = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        if (weapon == null || !CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.SNOWSTORM_ID)) return;

        int level = CorovaEnchantments.getEnchantLevel(weapon, CorovaEnchantments.SNOWSTORM_ID);

        // Apply damage multiplier when hitting a target currently under the snowstorm effect.
        if (aoeBurstActive.contains(victim.getUniqueId())) {
            // Bypass combat cooldown for the enchantment portion
            if (event.isCancelled()) {
                event.setCancelled(false);
                victim.setNoDamageTicks(0);
            }

            double multiplier = switch (level) {
                case 1 -> 2.0;
                case 2 -> 3.0;
                case 3 -> 4.0;
                case 4 -> 5.0;
                case 5 -> 6.0;
                default -> 2.0;
            };
            event.setDamage(event.getDamage() * multiplier);
        } else if (snowstormHitPlayers.contains(victim.getUniqueId())) {
            double multiplier = switch (level) {
                case 1 -> 2.0;
                case 2 -> 2.5;
                case 3 -> 3.0;
                case 4 -> 3.5;
                case 5 -> 4.0;
                default -> 1.0;
            };
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    // ── Shift to charge, release to blast ────────────────────────────────────
    //
    // FIX 3: Guard with getLevel() != 1 so only one listener handles sneaking.
    //         Without this, all 5 registered instances each start their own
    //         charge runnable when the player shifts.

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        // Single-instance guard: level is read from the weapon PDC at runtime.
        if (getLevel() != 1) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.SNOWSTORM_ID)) return;

        int level = CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.SNOWSTORM_ID);
        UUID uuid = player.getUniqueId();
        if (chargingPlayers.contains(uuid)) return;

        long lastUsed = cooldownMap.getOrDefault(uuid, 0L);
        long now = System.currentTimeMillis();
        if (now - lastUsed < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
            Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(
                    getEnchantId(), CorovaEnchantments.DISPLAY_NAME.getOrDefault(getEnchantId(), "Snowstorm"));
            player.sendActionBar(enchantName.append(Component.text(
                    " is on cooldown: " + remaining + "s", net.kyori.adventure.text.format.NamedTextColor.RED)));
            return;
        }

        chargingPlayers.add(uuid);
        chargeMap.put(uuid, 0);

        // Charge rate: tick interval between each +1 charge.
        // Level 1/2 = 20 ticks (1 charge/sec)
        // Level 3/4 = 10 ticks  (2x faster)
        // Level 5   =  7 ticks  (3x faster)
        long tickInterval = switch (level) {
            case 1, 2 -> 20L;
            case 3, 4 -> 10L;
            case 5    ->  7L;
            default   -> 20L;
        };

        boolean useBlockBreaking = level >= 3;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isSneaking() || !chargingPlayers.contains(uuid)) {
                    int charge = chargeMap.getOrDefault(uuid, 0);
                    if (charge > 0) activate(player, charge, level);
                    chargingPlayers.remove(uuid);
                    chargeMap.remove(uuid);
                    cancel();
                    return;
                }

                int charge = chargeMap.getOrDefault(uuid, 0);
                if (charge >= 10) {
                    activate(player, charge, level);
                    chargingPlayers.remove(uuid);
                    chargeMap.remove(uuid);
                    cancel();
                    return;
                }

                int nextCharge = charge + 1;
                chargeMap.put(uuid, nextCharge);
                player.sendActionBar(Component.text(ChatColor.AQUA + "Blind Radius: " + nextCharge));

                Location loc = player.getLocation().add(0, 1, 0);
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, loc, 1, 0.3, 0.5, 0.3, 0.1);
                player.getWorld().spawnParticle(Particle.CLOUD,     loc, 1, 0.3, 0.5, 0.3, 0.05);

                if (useBlockBreaking) {
                    player.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, loc, 1, 0.3, 0.3, 0.3, 0.0,
                            Material.SNOW_BLOCK.createBlockData());
                }
            }
        }.runTaskTimer(plugin, 0L, tickInterval);
    }

    // ── External trigger (e.g. on-hit from other systems) ────────────────────

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        activateDirectStatic(damager, 5, level,
                target != null ? target.getLocation() : damager.getLocation());
    }

    // ── Internal activate ─────────────────────────────────────────────────────

    private void activate(Player player, int radius, int level) {
        UUID uuid = player.getUniqueId();
        chargingPlayers.remove(uuid);
        chargeMap.remove(uuid);
        cooldownMap.put(uuid, System.currentTimeMillis());
        activateDirectStatic(player, radius, level, player.getLocation());
    }

    // ── Core effect logic ─────────────────────────────────────────────────────

    private static void activateDirectStatic(LivingEntity caster, int radius, int level, Location center) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(SnowStormBook.class);
        UUID uuid = caster.getUniqueId();

        // ── Per-level effect parameters ───────────────────────────────────────

        // Blindness: all levels = 7 seconds (140 ticks)
        int blindnessTicks = 140;

        // Slowness:
        //   level 1/2/3 = Slowness II (amp 1)
        //   level 4/5   = Slowness III (amp 2)
        // Duration:
        //   level 1     = 5 seconds (100 ticks)
        //   level 2-5   = 7 seconds (140 ticks)
        int slownessAmplifier = switch (level) {
            case 1, 2, 3 -> 1; // Slowness II
            case 4, 5    -> 2; // Slowness III
            default      -> 1;
        };
        int slownessTicks = switch (level) {
            case 1          -> 100; // 5 seconds
            case 2, 3, 4, 5 -> 140; // 7 seconds
            default         -> 100;
        };

        // Weakness: only levels 3-5
        //   level 3    = Weakness I  (amp 0), 7s
        //   levels 4-5 = Weakness II (amp 1), 7s
        boolean applyWeakness = level >= 3;
        int weaknessAmplifier = (level == 3) ? 0 : 1;
        int weaknessTicks = 140; // 7 seconds

        // Frostbite (powder-snow freeze via setFreezeTicks): only levels 4-5
        boolean applyFrostbite = level >= 4;
        int frostbiteTicks = switch (level) {
            case 4 -> 100; // 5 seconds
            case 5 -> 140; // 7 seconds
            default -> 0;
        };

        // Elytra sound on blast: levels 4-5
        boolean playElytraSound = level >= 4;

        List<Player> affectedPlayers    = new ArrayList<>();
        List<String> affectedNames      = new ArrayList<>();
        List<LivingEntity> affectedMobs = new ArrayList<>();

        List<Entity> nearbyEntities = new ArrayList<>(center.getWorld().getNearbyEntities(center, radius, radius, radius));
        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(uuid);
        for (int idx = 0; idx < nearbyEntities.size(); idx++) {
            Entity entity = nearbyEntities.get(idx);
            if (!(entity instanceof LivingEntity target)) continue;
            if (target.getUniqueId().equals(uuid)) continue;

            target.setNoDamageTicks(0);

            // Apply synergy bonus to effect durations
            ItemStack weapon = (caster.getEquipment() != null) ? caster.getEquipment().getItemInMainHand() : null;
            int synergyBonus = MutationManager.getInstance().getSynergyHandler().getFreezeDurationBonus(weapon);

            // Base burst damage matches the weapon's attribute damage.
            // Multipliers (2.0x-6.0x) are applied in onEntityDamage to ensure
            // enchants like Sharpness are included in the final calculation.
            double burstDamage = EnchantmentBook.getWeaponDamage(caster);

            // Damage the target immediately in the same tick.
            if (target.isValid() && !target.isDead()) {
                aoeBurstActive.add(target.getUniqueId());
                int savedMax = target.getMaximumNoDamageTicks();
                target.setMaximumNoDamageTicks(0);
                target.setNoDamageTicks(0);
                target.damage(burstDamage, caster);
                target.setMaximumNoDamageTicks(savedMax);
                aoeBurstActive.remove(target.getUniqueId());
            }

            if (target instanceof Player targetPlayer) {
                if (blindedPlayers.contains(targetPlayer.getUniqueId())) continue;

                blindedPlayers.add(targetPlayer.getUniqueId());
                // FIX: add to snowstormHitPlayers so the damage multiplier in
                //      onEntityDamage will fire for subsequent hits on this target.
                snowstormHitPlayers.add(targetPlayer.getUniqueId());
                affectedPlayers.add(targetPlayer);
                affectedNames.add(targetPlayer.getName());

                int finalBlindTicks = blindnessTicks + synergyBonus;
                int finalSlowTicks  = slownessTicks  + synergyBonus;

                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, finalBlindTicks, 0));
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,  finalSlowTicks,  slownessAmplifier));

                if (applyWeakness) {
                    targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks + synergyBonus, weaknessAmplifier));
                }

                if (applyFrostbite) {
                    // setFreezeTicks gives the powdered-snow freeze effect.
                    // The max freeze ticks for a player is 140 (7s); we clamp to
                    // avoid exceeding the vanilla cap unintentionally.
                    targetPlayer.setFreezeTicks(frostbiteTicks + synergyBonus);
                }

                targetPlayer.sendActionBar(Component.text(
                        ChatColor.AQUA + "You have been blinded by " + caster.getName() + "!"));

                // Schedule cleanup: remove from tracking sets when blindness expires
                BukkitRunnable task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        blindedPlayers.remove(targetPlayer.getUniqueId());
                        snowstormHitPlayers.remove(targetPlayer.getUniqueId());
                        targetPlayer.sendActionBar(Component.text(ChatColor.GRAY + "You are no longer blinded!"));
                        activeBlindTasks.remove(targetPlayer.getUniqueId());
                    }
                };
                task.runTaskLater(plugin, (long) finalBlindTicks);
                activeBlindTasks.put(targetPlayer.getUniqueId(), task);

                spawnSnowEffect(plugin, target, finalBlindTicks, level);

            } else {
                // Mob targets
                affectedMobs.add(target);

                int finalSlowTicks = slownessTicks + synergyBonus;
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, finalSlowTicks, slownessAmplifier));

                if (applyWeakness) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, weaknessTicks + synergyBonus, weaknessAmplifier));
                }

                // Track mobs for damage multiplier too
                snowstormHitPlayers.add(target.getUniqueId());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        snowstormHitPlayers.remove(target.getUniqueId());
                    }
                }.runTaskLater(plugin, (long) finalSlowTicks);

                spawnSnowEffect(plugin, target, finalSlowTicks, level);
            }
        }
        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(uuid);

        // ── One-shot impact burst ──────────────────────────────────────────────
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)) continue;
            if (target.getUniqueId().equals(uuid)) continue;
            spawnSnowBurst(target, level);
        }

        // ── Elytra sound for levels 4-5 ───────────────────────────────────────
        if (playElytraSound) {
            center.getWorld().playSound(center, org.bukkit.Sound.ITEM_ELYTRA_FLYING, 1.0f, 2.0f);
        }

        if (caster instanceof Player player) {
            if (!affectedNames.isEmpty() || !affectedMobs.isEmpty()) {
                String msg = ChatColor.GRAY + "You have blinded: "
                        + (!affectedNames.isEmpty()
                        ? ChatColor.AQUA + String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, affectedNames)
                        : "")
                        + (!affectedMobs.isEmpty()
                        ? (affectedNames.isEmpty() ? "" : ChatColor.GRAY + ", ")
                          + ChatColor.AQUA + affectedMobs.size() + " mob(s)"
                        : "");
                player.sendActionBar(Component.text(msg));
            } else {
                player.sendActionBar(Component.text(ChatColor.RED + "You did not affect anyone."));
            }
        }
    }

    // ── Snow ring sound — 4 cardinal hitbox-edge points at multiple heights ──

    private static void playSnowRing(LivingEntity target, int count) {
        Location base = target.getLocation();
        double[] heights = {0.0, 0.5, 1.0, 1.7};
        double[] dx = { 0.3, -0.3,  0.0,  0.0};
        double[] dz = { 0.0,  0.0,  0.3, -0.3};

        for (double h : heights) {
            for (int i = 0; i < 4; i++) {
                Location loc = base.clone().add(dx[i], h, dz[i]);
                for (int j = 0; j < count; j++) {
                    target.getWorld().playEffect(loc, Effect.STEP_SOUND, Material.SNOW_BLOCK);
                }
            }
        }
    }

    // ── Spawns BLOCK_CRUMBLE at the 4 cardinal edge midpoints of the player hitbox

    private static void spawnHitboxCrumble(LivingEntity target, double yOffset, int countPerPoint) {
        Location base = target.getLocation().add(0, yOffset, 0);
        org.bukkit.block.data.BlockData snow = Material.SNOW_BLOCK.createBlockData();
        double[] dx = { 0.3, -0.3,  0.0,  0.0};
        double[] dz = { 0.0,  0.0,  0.3, -0.3};
        for (int i = 0; i < 4; i++) {
            Location pt = base.clone().add(dx[i], 0, dz[i]);
            target.getWorld().spawnParticle(Particle.BLOCK_CRUMBLE, pt, countPerPoint, 0, 0, 0, 0, snow);
        }
    }

    // ── Ongoing snow effect — fires every 5 ticks for exactly durationTicks ──

    private static void spawnSnowEffect(JavaPlugin plugin, LivingEntity target, int durationTicks, int level) {
        final int maxFires = Math.max(1, durationTicks / 5);
        boolean useBlockBreaking = level >= 3;

        new BukkitRunnable() {
            int fires = 0;
            @Override
            public void run() {
                if (fires >= maxFires || target.isDead()) { cancel(); return; }

                playSnowRing(target, 1);

                Location locHead = target.getLocation().add(0, 1.7, 0);
                target.getWorld().spawnParticle(Particle.SNOWFLAKE, locHead, 1, 0.3, 0.5, 0.3, 0.05);
                target.getWorld().spawnParticle(Particle.CLOUD,     locHead, 1, 0.3, 0.5, 0.3, 0.02);

                if (useBlockBreaking) {
                    spawnHitboxCrumble(target, 0.1, 1);
                    spawnHitboxCrumble(target, 0.6, 1);
                    spawnHitboxCrumble(target, 1.2, 1);
                    spawnHitboxCrumble(target, 1.7, 1);
                }

                fires++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    // ── One-shot burst on impact ──────────────────────────────────────────────

    private static void spawnSnowBurst(LivingEntity target, int level) {
        playSnowRing(target, 2);

        Location locHead = target.getLocation().add(0, 1.7, 0);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE, locHead, 4, 0.3, 0.5, 0.3, 0.1);
        target.getWorld().spawnParticle(Particle.CLOUD,     locHead, 2, 0.3, 0.5, 0.3, 0.05);

        if (level >= 3) {
            spawnHitboxCrumble(target, 0.1, 1);
            spawnHitboxCrumble(target, 0.6, 1);
            spawnHitboxCrumble(target, 1.2, 1);
            spawnHitboxCrumble(target, 1.7, 1);
        }
    }

    // ── Cleanup on disconnect ─────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chargingPlayers.remove(uuid);
        chargeMap.remove(uuid);
        cooldownMap.remove(uuid);
        blindedPlayers.remove(uuid);
        snowstormHitPlayers.remove(uuid);
        BukkitRunnable task = activeBlindTasks.remove(uuid);
        if (task != null) task.cancel();
    }
}