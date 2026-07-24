package com.example.corovaItems.Weapons;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.MageSystem.ManaManager;
import net.corova.chronicles.generated.CorovaCustomModelData;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Rod of Arrow Casting.
 *
 * Mana integration:
 * <ul>
 *   <li>Left-click  — place a portal (costs {@link ManaManager#COST_ARROW_PORTAL} mana).</li>
 *   <li>Right-click — fire from the OLDEST portal only (costs {@link ManaManager#COST_ARROW_FIRE} mana).
 *       Each right-click consumes one portal in the order they were placed. </li>
 * </ul>
 * Portals are still capped at 3 simultaneously and expire after 10 s.
 */
public class RodOfArrowCasting extends CorovaItems implements Listener {

    // ── Portal types ──────────────────────────────────────────────────────────
    private enum PortalType {
        NORMAL(Color.AQUA),
        FAN(Color.YELLOW),
        POISON(Color.GREEN),
        SLOWNESS(Color.GRAY),
        KNOCKBACK(Color.WHITE);

        private final Color color;
        PortalType(Color color) { this.color = color; }
        public Color getColor() { return color; }
    }

    private static class Portal {
        Location   location;
        PortalType type;
        BukkitTask expirationTask;
        BukkitTask particleTask;

        Portal(Location location, PortalType type) {
            this.location = location;
            this.type     = type;
        }
    }

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static RodOfArrowCasting instance;

    // ── State ─────────────────────────────────────────────────────────────────
    /** Portals stored as a LinkedList so we can poll from the front (FIFO). */
    private static final Map<UUID, LinkedList<Portal>> playerPortals = new HashMap<>();
    private static final Map<UUID, Long>               lastLeftClick = new HashMap<>();

    private static final int    MAX_PORTALS     = 3;
    private static final double KNOCKBACK_HORIZ = 0.4;
    private static final double KNOCKBACK_VERT  = 0.4;
    private static final Random RANDOM          = new Random();
    private static final int    MAX_DURABILITY  = 250;
    private static final NamespacedKey UNIQUE_ID_KEY =
            new NamespacedKey("corovaitems", "unique_id_arrow_casting");

    private final JavaPlugin plugin;

    // ── Constructor ───────────────────────────────────────────────────────────
    public RodOfArrowCasting() {
        super(
                ChatColor.AQUA + "Rod of Arrow Casting",
                new Material[]{Material.DIAMOND_SPEAR},
                lore(),
                enchantments(),
                "rodofarrowcasting",
                CorovaCustomModelData.WEAPONS_ROD_OF_ARROW_CASTING,
                Collections.emptyMap()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(getClass());
        ItemManager.getInstance().registerItem(this);
        instance = this;
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY      + "Arrow Casting I",
                ChatColor.GRAY      + "A rod that channels the Stray Mage's power.",
                ChatColor.DARK_AQUA + "Left-Click  — place a portal (costs "
                        + (int) ManaManager.COST_ARROW_PORTAL + " mana).",
                ChatColor.DARK_AQUA + "Right-Click — fire from the next portal (costs "
                        + (int) ManaManager.COST_ARROW_FIRE + " mana)."
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>();
    }

    // ── Interact ──────────────────────────────────────────────────────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player    player = event.getPlayer();
        ItemStack item   = player.getInventory().getItemInMainHand();
        if (!isThisItem(item)) return;

        // Initialize durability and unique ID if missing
        ItemMeta meta = item.getItemMeta();
        if (meta != null && !meta.getPersistentDataContainer().has(UNIQUE_ID_KEY, PersistentDataType.STRING)) {
            meta.getPersistentDataContainer().set(UNIQUE_ID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(MAX_DURABILITY);
            }
            item.setItemMeta(meta);
        }

        Action action = event.getAction();
        UUID   id     = player.getUniqueId();

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (now - lastLeftClick.getOrDefault(id, 0L) < 300) return;
            lastLeftClick.put(id, now);
            openPortal(player);

        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            fireFromNextPortal(player);
        }
    }

    // ── Portal placement ──────────────────────────────────────────────────────
    private void openPortal(Player player) {
        UUID               id      = player.getUniqueId();
        LinkedList<Portal> portals = playerPortals.computeIfAbsent(id, k -> new LinkedList<>());

        if (portals.size() >= MAX_PORTALS) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            player.sendMessage(ChatColor.RED + "Maximum portals placed (" + MAX_PORTALS + ")!");
            return;
        }

        ManaManager mana = ManaManager.getInstance();
        if (mana == null) return;
        if (!mana.tryConsumeMana(player, ManaManager.COST_ARROW_PORTAL)) return;

        Location targetLoc = player.getTargetBlock(null, 20)
                .getLocation().add(0.5, 1.5, 0.5);
        if (targetLoc.getBlock().getType() != Material.AIR) {
            targetLoc = targetLoc.add(0, 1, 0);
        }

        PortalType type   = PortalType.values()[RANDOM.nextInt(PortalType.values().length)];
        Portal     portal = new Portal(targetLoc, type);

        // Add to the BACK of the queue — oldest portal is always at the front.
        portals.addLast(portal);

        BukkitTask expirationTask = new BukkitRunnable() {
            @Override
            public void run() {
                LinkedList<Portal> current = playerPortals.get(id);
                if (current != null && current.remove(portal)) {
                    if (portal.particleTask != null) portal.particleTask.cancel();
                    player.getWorld().playSound(portal.location,
                            Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f);
                    sendPortalStatus(player, current.size());
                }
            }
        }.runTaskLater(plugin, 200L);

        portal.expirationTask = expirationTask;
        portal.particleTask   = spawnPortalParticles(portal, 200);

        player.getWorld().playSound(targetLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1.8f);
        sendPortalStatus(player, portals.size());
        handleDurability(player);
    }

    // ── Fire from the next (oldest) portal ───────────────────────────────────
    private void fireFromNextPortal(Player player) {
        UUID               id      = player.getUniqueId();
        LinkedList<Portal> portals = playerPortals.get(id);

        if (portals == null || portals.isEmpty()) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
            return;
        }

        ManaManager mana = ManaManager.getInstance();
        if (mana == null) return;
        if (!mana.tryConsumeMana(player, ManaManager.COST_ARROW_FIRE)) return;

        // Poll the OLDEST portal from the front of the queue.
        Portal portal = portals.pollFirst();
        if (portal == null) return;

        // Cancel its expiry timer — we're consuming it now.
        if (portal.expirationTask != null) portal.expirationTask.cancel();
        if (portal.particleTask   != null) portal.particleTask.cancel();

        Location              target    = getTargetLocation(player);
        ItemStack             item      = player.getInventory().getItemInMainHand();
        Map<MutationType, Integer> mutations = MutationManager.getInstance().getMutations(item);

        switch (portal.type) {
            case FAN       -> fireFanArrows(player, portal.location, target, mutations);
            case POISON    -> firePotionArrow(player, portal.location, target,
                    PotionEffectType.POISON, mutations);
            case SLOWNESS  -> firePotionArrow(player, portal.location, target,
                    PotionEffectType.SLOWNESS, mutations);
            case KNOCKBACK -> fireKnockbackArrow(player, portal.location, target, mutations);
            default        -> fireNormalArrows(player, portal.location, target, mutations);
        }

        sendPortalStatus(player, portals.size());
        handleDurability(player);
    }

    private void handleDurability(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isThisItem(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(MAX_DURABILITY);
            damageable.setDamage(damageable.getDamage() + 1);

            if (damageable.getDamage() >= MAX_DURABILITY) {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                item.setAmount(item.getAmount() - 1);
                cleanupPlayer(player);
            } else {
                item.setItemMeta(damageable);
            }
        }
    }

    // ── Action-bar status helper ──────────────────────────────────────────────
    private static void sendPortalStatus(Player player, int remaining) {
        if (remaining == 0) {
            player.sendActionBar(ChatColor.GRAY + "No portals active");
        } else {
            player.sendActionBar(ChatColor.AQUA + "Portals: " + remaining + "/" + MAX_PORTALS);
        }
    }

    // ── Target resolution ─────────────────────────────────────────────────────
    private Location getTargetLocation(Player player) {
        final double maxRange = 60.0;
        Location     eyeLoc   = player.getEyeLocation();
        Vector       dir      = eyeLoc.getDirection();
        World        world    = player.getWorld();

        var result = world.rayTraceEntities(eyeLoc, dir, maxRange, 0.5,
                entity -> entity instanceof org.bukkit.entity.LivingEntity
                        && !entity.equals(player));

        if (result != null
                && result.getHitEntity() instanceof org.bukkit.entity.LivingEntity target) {
            Vector   vel    = target.getVelocity();
            double   dist   = player.getLocation().distance(target.getLocation());
            double   tth    = dist / 3.8;
            Location center = target.getBoundingBox().getCenter().toLocation(target.getWorld());
            return center.add(vel.multiply(tth));
        }
        return eyeLoc.add(dir.multiply(maxRange));
    }

    // ── Arrow type helpers ────────────────────────────────────────────────────
    private void fireNormalArrows(Player player, Location portalLoc, Location target,
                                  Map<MutationType, Integer> mutations) {
        int   velLvl  = mutations.getOrDefault(MutationType.ARROW_VELOCITY, 0);
        float velMult = 1.0f + (0.5f * velLvl);
        int   dblTap  = mutations.getOrDefault(MutationType.DOUBLE_TAP, 0);
        int   triTap  = mutations.getOrDefault(MutationType.TRIPLE_TAP, 0);

        new BukkitRunnable() {
            int burst = 0;
            @Override public void run() {
                if (burst >= 3) { cancel(); return; }
                int arrowsToFire = resolveArrowCount(dblTap, triTap, burst);
                new BukkitRunnable() {
                    int fired = 0;
                    @Override public void run() {
                        if (fired >= arrowsToFire) { cancel(); return; }
                        Vector dir   = target.clone().subtract(portalLoc).toVector().normalize();
                        Arrow  arrow = player.getWorld().spawnArrow(
                                portalLoc.clone().add(0, 0.3, 0), dir, 3.8f * velMult, 0f);
                        arrow.setShooter(player);
                        arrow.setDamage(6.0);
                        arrow.setMetadata("wand_arrow", new FixedMetadataValue(plugin, true));
                        addColoredArrowTrail(arrow, Color.AQUA);
                        player.getWorld().playSound(portalLoc, Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
                        fired++;
                    }
                }.runTaskTimer(plugin, 0L, 3L);
                burst++;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void fireFanArrows(Player player, Location portalLoc, Location target,
                               Map<MutationType, Integer> mutations) {
        int   velLvl  = mutations.getOrDefault(MutationType.ARROW_VELOCITY, 0);
        float velMult = 1.0f + (0.5f * velLvl);
        int   dblTap  = mutations.getOrDefault(MutationType.DOUBLE_TAP, 0);
        int   triTap  = mutations.getOrDefault(MutationType.TRIPLE_TAP, 0);

        new BukkitRunnable() {
            int burst = 0;
            @Override public void run() {
                if (burst >= 2) { cancel(); return; }
                int arrowsToFire = resolveArrowCount(dblTap, triTap, burst);
                new BukkitRunnable() {
                    int fired = 0;
                    @Override public void run() {
                        if (fired >= arrowsToFire) { cancel(); return; }
                        Vector dir = target.clone().subtract(portalLoc).toVector().normalize();
                        for (int i = -1; i <= 1; i++) {
                            Vector spread = dir.clone().rotateAroundY(i * 0.12);
                            Arrow  arrow  = player.getWorld().spawnArrow(
                                    portalLoc.clone().add(0, 0.3, 0),
                                    spread, 4.2f * velMult, 0f);
                            arrow.setShooter(player);
                            arrow.setDamage(25.0);
                            arrow.setMetadata("wand_arrow", new FixedMetadataValue(plugin, true));
                            addColoredArrowTrail(arrow, Color.YELLOW);
                        }
                        player.getWorld().playSound(portalLoc, Sound.ENTITY_ARROW_SHOOT, 1f, 1.5f);
                        fired++;
                    }
                }.runTaskTimer(plugin, 0L, 3L);
                burst++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void firePotionArrow(Player player, Location portalLoc, Location target,
                                 PotionEffectType effect, Map<MutationType, Integer> mutations) {
        int   velLvl       = mutations.getOrDefault(MutationType.ARROW_VELOCITY, 0);
        float velMult      = 1.0f + (0.5f * velLvl);
        int   arrowsToFire = resolveArrowCount(
                mutations.getOrDefault(MutationType.DOUBLE_TAP, 0),
                mutations.getOrDefault(MutationType.TRIPLE_TAP, 0),
                0);

        new BukkitRunnable() {
            int fired = 0;
            @Override public void run() {
                if (fired >= arrowsToFire) { cancel(); return; }
                Vector dir   = target.clone().subtract(portalLoc).toVector().normalize();
                Arrow  arrow = player.getWorld().spawnArrow(
                        portalLoc.clone().add(0, 0.3, 0), dir, 3.8f * velMult, 0f);
                arrow.setShooter(player);
                arrow.setDamage(24.0);
                arrow.addCustomEffect(new org.bukkit.potion.PotionEffect(effect, 100, 0), true);
                arrow.setMetadata("wand_arrow", new FixedMetadataValue(plugin, true));
                addColoredArrowTrail(arrow, effect.getColor());
                player.getWorld().playSound(portalLoc, Sound.ENTITY_ARROW_SHOOT, 1f, 1.0f);
                fired++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void fireKnockbackArrow(Player player, Location portalLoc, Location target,
                                    Map<MutationType, Integer> mutations) {
        int    velLvl  = mutations.getOrDefault(MutationType.ARROW_VELOCITY, 0);
        float  velMult = 1.0f + (0.5f * velLvl);
        Vector dir     = target.clone().subtract(portalLoc).toVector().normalize();
        Arrow  arrow   = player.getWorld().spawnArrow(
                portalLoc.clone().add(0, 0.3, 0), dir, 4.5f * velMult, 0f);
        arrow.setShooter(player);
        arrow.setDamage(22.0);
        arrow.setMetadata("wand_arrow",      new FixedMetadataValue(plugin, true));
        arrow.setMetadata("knockback_arrow", new FixedMetadataValue(plugin, 3));
        addColoredArrowTrail(arrow, Color.WHITE);
        player.getWorld().playSound(portalLoc, Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────
    private static int resolveArrowCount(int dblTap, int triTap, int burst) {
        if (triTap > 0) return 3;
        if (dblTap == 2) return 2;
        if (dblTap == 1) return (burst % 2 == 0) ? 2 : 1;
        return 1;
    }

    private BukkitTask spawnPortalParticles(Portal portal, int durationTicks) {
        Particle.DustOptions dust = new Particle.DustOptions(portal.type.getColor(), 1.5f);
        return new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick++ > durationTicks) { cancel(); return; }
                double radius = 0.5;
                for (int i = 0; i < 25; i++) {
                    double theta = Math.random() * Math.PI;
                    double phi   = Math.random() * 2 * Math.PI;
                    double x     = radius * Math.sin(theta) * Math.cos(phi);
                    double y     = radius * Math.sin(theta) * Math.sin(phi);
                    double z     = radius * Math.cos(theta);
                    portal.location.getWorld().spawnParticle(Particle.DUST,
                            portal.location.clone().add(x, y, z),
                            1, 0, 0, 0, 1, dust, true);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void addColoredArrowTrail(Arrow arrow, Color color) {
        new BukkitRunnable() {
            @Override public void run() {
                if (arrow.isDead() || !arrow.isValid()) { cancel(); return; }
                arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(),
                        1, 0, 0, 0, 0, new Particle.DustOptions(color, 1f));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void cleanupPlayer(Player player) {
        UUID               id      = player.getUniqueId();
        LinkedList<Portal> portals = playerPortals.remove(id);
        if (portals != null) {
            for (Portal portal : portals) {
                if (portal.expirationTask != null) portal.expirationTask.cancel();
                if (portal.particleTask   != null) portal.particleTask.cancel();
            }
        }
        lastLeftClick.remove(id);
    }

    // ── Damage events ─────────────────────────────────────────────────────────
    @EventHandler
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!arrow.hasMetadata("wand_arrow")) return;

        if (arrow.getShooter() instanceof Player shooter
                && event.getEntity() instanceof org.bukkit.entity.LivingEntity victim) {
            if (victim instanceof Player vp
                    && CorovaGuard.getInstance().isPlayerInSafeZone(vp)) {
                CorovaGuard.sendSafeZoneMessage(shooter);
                event.setCancelled(true);
                return;
            }
            CorovaTeams teams = CorovaTeams.getInstance();
            if (teams != null) {
                CorovaTeam st = teams.getTeamManager().getTeamByPlayer(shooter.getUniqueId());
                CorovaTeam vt = teams.getTeamManager().getTeamByPlayer(
                        ((Player) victim).getUniqueId());
                if (st != null && st.equals(vt) && !st.hasFriendlyFire()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (arrow.hasMetadata("knockback_arrow") && !event.isCancelled()
                && event.getEntity() instanceof org.bukkit.entity.LivingEntity victim) {
            List<org.bukkit.metadata.MetadataValue> vals = arrow.getMetadata("knockback_arrow");
            if (!vals.isEmpty()) {
                int    kbPower = vals.get(0).asInt();
                Vector kb      = arrow.getVelocity().normalize()
                        .multiply(kbPower * KNOCKBACK_HORIZ);
                kb.setY(KNOCKBACK_VERT);
                victim.setVelocity(victim.getVelocity().add(kb));
            }
        }
    }

    // ── Lifecycle events ──────────────────────────────────────────────────────
    @EventHandler public void onQuit(PlayerQuitEvent event) { cleanupPlayer(event.getPlayer()); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) {
        if (isThisItem(event.getItemDrop().getItemStack())) cleanupPlayer(event.getPlayer());
    }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent event) { /* portals persist off-hand */ }
}