package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.WandEnchantListener;
import com.example.corovaItems.MageSystem.ManaManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EnderMissileBook extends EnchantmentBook {

    private static final long    MOB_COOLDOWN_MS = 5_000L;
    private static final double  BASE_DAMAGE     = 40.0;
    private static final double  AOE_RADIUS      = 2.5;
    private static final String  PDC_KEY_NAME    = "ender_missile_projectile";

    // Lazy-initialised once per class-load; safe because NamespacedKey is immutable.
    private static NamespacedKey PROJECTILE_KEY;

    private final Map<UUID, Long> mobCooldowns   = new HashMap<>();
    private final Set<UUID>       firingThisTick = new HashSet<>();

    // ── Constructors ──────────────────────────────────────────────────────────

    public EnderMissileBook() { this(1); }

    public EnderMissileBook(int level) {
        super(
                "Book of Ender Missile",
                CorovaEnchantments.ENDER_MISSILE_ID,
                level,
                "book_endermissile",
                buildAllowedMaterials()
        );
        ensureProjectileKey();
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

    private static Set<Material> buildAllowedMaterials() {
        return Stream.of(
                "WOODEN_SPEAR", "STONE_SPEAR", "IRON_SPEAR",
                "GOLDEN_SPEAR", "COPPER_SPEAR", "DIAMOND_SPEAR", "NETHERITE_SPEAR"
        ).map(Material::matchMaterial).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private void ensureProjectileKey() {
        if (PROJECTILE_KEY == null) {
            PROJECTILE_KEY = new NamespacedKey(
                    JavaPlugin.getProvidingPlugin(EnderMissileBook.class), PDC_KEY_NAME);
        }
    }

    // ── Wand-only restriction ─────────────────────────────────────────────────

    @Override
    public boolean canApplyTo(ItemStack stack) {
        if (stack == null) return false;
        if (!super.canApplyTo(stack)) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Wand");
    }

    // ── Player right-click ────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack item   = event.getItem();
        if (item == null || !CorovaEnchantments.hasEnchant(item, CorovaEnchantments.ENDER_MISSILE_ID)) return;

        event.setCancelled(true);

        // Dedup: Bukkit fires two events per click (main + off-hand slot).
        UUID uuid = player.getUniqueId();
        if (!firingThisTick.add(uuid)) return;
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        plugin.getServer().getScheduler().runTask(plugin, () -> firingThisTick.remove(uuid));

        ManaManager mana = ManaManager.getInstance();
        if (mana == null || !mana.tryConsumeMana(player, ManaManager.COST_ENDER_MISSILE)) return;

        shootMissile(player);
    }

    // ── Mob AI path ───────────────────────────────────────────────────────────

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager) || damager instanceof Player) return;

        ItemStack handItem = damager.getEquipment() == null
                ? null : damager.getEquipment().getItemInMainHand();
        if (handItem == null || handItem.getType() == Material.AIR) return;
        if (!CorovaEnchantments.hasEnchant(handItem, getEnchantId())) return;

        long now  = System.currentTimeMillis();
        Long last = mobCooldowns.get(damager.getUniqueId());
        if (last != null && now - last < MOB_COOLDOWN_MS) return;

        shootMissile(damager);
        mobCooldowns.put(damager.getUniqueId(), now);
    }

    // ── Projectile spawn ──────────────────────────────────────────────────────

    private void shootMissile(LivingEntity caster) {
        Location eyeLoc   = caster.getEyeLocation();
        Vector   dir      = eyeLoc.getDirection();
        World    world    = caster.getWorld();

        world.spawn(eyeLoc.clone().add(dir.clone().multiply(1.0)), DragonFireball.class, f -> {
            f.setShooter(caster);
            f.setVelocity(dir.clone().multiply(1.5));
            f.getPersistentDataContainer().set(PROJECTILE_KEY, PersistentDataType.BYTE, (byte) 1);
        });

        world.playSound(caster.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.5f, 1.2f);
        world.spawnParticle(Particle.PORTAL, caster.getLocation(), 30, 0.3, 0.3, 0.3, 0.1);
    }

    // ── Projectile hit ────────────────────────────────────────────────────────

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof DragonFireball fireball)) return;
        if (!fireball.getPersistentDataContainer().has(PROJECTILE_KEY, PersistentDataType.BYTE)) return;

        Location hitLoc      = fireball.getLocation();
        World    world       = hitLoc.getWorld();
        Entity   shooterEnt  = (Entity) fireball.getShooter();
        Player   playerCaster = fireball.getShooter() instanceof Player p ? p : null;
        ItemStack weapon      = playerCaster == null
                ? null : playerCaster.getInventory().getItemInMainHand();

        // Direct entity hit takes priority; fall back to area damage otherwise.
        if (event.getHitEntity() instanceof LivingEntity directVictim) {
            applyDamage(directVictim, shooterEnt, weapon, playerCaster);
        } else {
            world.getNearbyEntities(hitLoc, AOE_RADIUS, AOE_RADIUS, AOE_RADIUS).stream()
                    .filter(e -> e instanceof LivingEntity && e != fireball.getShooter())
                    .forEach(e -> applyDamage((LivingEntity) e, shooterEnt, weapon, playerCaster));
        }

        // Linger cloud
        world.spawn(hitLoc, AreaEffectCloud.class, aec -> {
            aec.setRadius(1.5f);
            aec.setDuration(100);
            aec.setRadiusPerTick(0.0f);
            aec.setParticle(Particle.DRAGON_BREATH);
            if (fireball.getShooter() instanceof LivingEntity le) aec.setSource(le);
        });

        world.playSound(hitLoc, Sound.ENTITY_ENDER_DRAGON_HURT, 1.0f, 1.2f);
        world.spawnParticle(Particle.DRAGON_BREATH, hitLoc, 50, 0.7, 0.3, 0.7, 0.05);
        world.spawnParticle(Particle.PORTAL,        hitLoc, 30, 0.5, 0.3, 0.5, 0.2);

        fireball.remove();
    }

    // ── Shared damage helper ──────────────────────────────────────────────────

    private static void applyDamage(LivingEntity victim, Entity source, ItemStack weapon, Player playerCaster) {
        double damage = (playerCaster != null && weapon != null)
                ? WandEnchantListener.getScaledDamage(BASE_DAMAGE, weapon, playerCaster, victim)
                : BASE_DAMAGE;
        victim.damage(damage, source);
    }
}