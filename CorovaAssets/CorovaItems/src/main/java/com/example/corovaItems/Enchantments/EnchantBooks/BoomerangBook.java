package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BoomerangBook extends EnchantmentBook implements Listener {

    public static final ThreadLocal<Boolean>   IS_BOOMERANG_HIT = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<ItemStack> SYNERGY_ITEM     = new ThreadLocal<>();

    private final JavaPlugin plugin;

    // ── PDC keys ──────────────────────────────────────────────────────────────
    private static final NamespacedKey BOUNCES_KEY  =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(BoomerangBook.class), "boomerang_bounces");
    private static final NamespacedKey RETURNING_KEY =
            new NamespacedKey(JavaPlugin.getProvidingPlugin(BoomerangBook.class), "boomerang_returning");

    // ── State ─────────────────────────────────────────────────────────────────
    private static final Map<UUID, Long> cooldowns    = new HashMap<>();
    private static final Set<UUID>       thrownWeapons = new HashSet<>();
    private static final Map<UUID, UUID>  itemOwners   = new HashMap<>(); // item UUID → owner UUID

    // ── Tuning ────────────────────────────────────────────────────────────────
    private static final long COOLDOWN_MS   = 10_000L;
    private static final int  TIMEOUT_TICKS = 7 * 20;
    private static final double RETURN_SPEED = 1.8;
    private static final double THROW_SPEED  = 1.5;
    private static final double SNAP_DIST_SQ = 2.25; // 1.5 blocks²

    public BoomerangBook() {
        super(
                "Book of Boomerang",
                CorovaEnchantments.BOOMERANG_ID,
                1,
                "book_boomerang",
                allowedMaterialsStatic()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(BoomerangBook.class);
        ItemManager.getInstance().registerItem(this);
    }

    // ── Allowed materials — swords & axes only ────────────────────────────────
    private static Set<Material> allowedMaterialsStatic() {
        return Set.of(
                Material.WOODEN_SWORD,  Material.STONE_SWORD,  Material.IRON_SWORD,
                Material.GOLDEN_SWORD,  Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
                Material.WOODEN_AXE,   Material.STONE_AXE,    Material.IRON_AXE,
                Material.GOLDEN_AXE,   Material.DIAMOND_AXE,  Material.NETHERITE_AXE
        );
    }

    // ── Right-click to throw ──────────────────────────────────────────────────
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) return;

        Player    player = event.getPlayer();
        ItemStack held   = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) return;
        if (!CorovaEnchantments.hasEnchant(held, CorovaEnchantments.BOOMERANG_ID)) return;

        event.setCancelled(true);

        UUID playerId = player.getUniqueId();

        if (thrownWeapons.contains(playerId)) {
            player.sendActionBar(Component.text("You must wait for your weapon to return!", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - cooldowns.getOrDefault(playerId, 0L);
        if (elapsed < COOLDOWN_MS) {
            double remaining = (COOLDOWN_MS - elapsed) / 1000.0;
            Component name = EnchantmentBook.applyEnchantmentGradientComponent(
                    getEnchantId(), CorovaEnchantments.DISPLAY_NAME.getOrDefault(getEnchantId(), "Boomerang"));
            player.sendActionBar(name.append(Component.text(
                    " is on cooldown for " + String.format("%.1f", remaining) + "s", NamedTextColor.RED)));
            return;
        }

        thrownWeapons.add(playerId);

        ItemStack thrownItem = held.clone();
        player.getInventory().setItemInMainHand(null);

        Item dropped = player.getWorld().dropItem(player.getEyeLocation(), thrownItem);
        dropped.setVelocity(player.getEyeLocation().getDirection().multiply(THROW_SPEED));
        dropped.setOwner(playerId);
        dropped.setCanMobPickup(false);

        PersistentDataContainer pdc = dropped.getPersistentDataContainer();
        pdc.set(BOUNCES_KEY,   PersistentDataType.INTEGER, 0);
        pdc.set(RETURNING_KEY, PersistentDataType.BYTE,    (byte) 0);

        itemOwners.put(dropped.getUniqueId(), playerId);

        launchTracker(dropped, thrownItem, playerId);
    }

    private void launchTracker(Item dropped, ItemStack thrownItem, UUID playerId) {
        new BukkitRunnable() {
            private int ticksLived = 0;

            @Override
            public void run() {
                ticksLived++;

                if (dropped.isDead()) {
                    itemOwners.remove(dropped.getUniqueId());
                    cancel();
                    return;
                }

                Player owner = Bukkit.getPlayer(playerId);
                if (owner == null || !owner.isOnline() || ticksLived > TIMEOUT_TICKS) {
                    returnItemToOwner(owner, dropped, thrownItem, playerId);
                    cancel();
                    return;
                }

                PersistentDataContainer pdc = dropped.getPersistentDataContainer();
                boolean isReturning = pdc.getOrDefault(RETURNING_KEY, PersistentDataType.BYTE, (byte) 0) == 1;

                if (isReturning) {
                    tickReturn(owner, dropped);
                } else {
                    tickFlight(owner, dropped, thrownItem, playerId, pdc);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Moves the item back towards the owner each tick. */
    private void tickReturn(Player owner, Item dropped) {
        if (owner.getLocation().distanceSquared(dropped.getLocation()) < SNAP_DIST_SQ) {
            dropped.setVelocity(new Vector());
        } else {
            Vector dir = owner.getEyeLocation().toVector()
                    .subtract(dropped.getLocation().toVector()).normalize();
            dropped.setVelocity(dir.multiply(RETURN_SPEED));
        }
    }

    /** Checks for entity hits and ground bounces during outbound flight. */
    private void tickFlight(Player owner, Item dropped, ItemStack thrownItem, UUID playerId,
                            PersistentDataContainer pdc) {
        // Entity collision
        for (LivingEntity entity : dropped.getWorld().getNearbyLivingEntities(dropped.getLocation(), 1.2)) {
            if (!entity.getUniqueId().equals(playerId)) {
                onHit(owner, entity, dropped, thrownItem);
                return;
            }
        }

        // Ground bounce
        if (dropped.isOnGround()) {
            int bounces = pdc.getOrDefault(BOUNCES_KEY, PersistentDataType.INTEGER, 0);
            if (bounces >= 5) {
                pdc.set(RETURNING_KEY, PersistentDataType.BYTE, (byte) 1);
            } else {
                pdc.set(BOUNCES_KEY, PersistentDataType.INTEGER, bounces + 1);
                Vector v = dropped.getVelocity();
                v.setY(Math.abs(v.getY()) * 0.7);
                dropped.setVelocity(v);
                dropped.getWorld().playSound(dropped.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
            }
        }
    }

    private void returnItemToOwner(Player owner, Item dropped, ItemStack thrownItem, UUID playerId) {
        if (owner != null && owner.isOnline()) {
            owner.getInventory().addItem(thrownItem.clone());
            owner.sendMessage(Component.text("Your weapon has been returned.", NamedTextColor.GREEN));
            cooldowns.put(playerId, System.currentTimeMillis());
        }
        dropped.remove();
        thrownWeapons.remove(playerId);
        itemOwners.remove(dropped.getUniqueId());
    }

    // ── Pickup: only the owner may collect the in-flight weapon ──────────────
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!item.getPersistentDataContainer().has(BOUNCES_KEY, PersistentDataType.INTEGER)) return;

        if (event.getEntity() instanceof Player player
                && player.getUniqueId().equals(item.getOwner())) {
            thrownWeapons.remove(player.getUniqueId());
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            itemOwners.remove(item.getUniqueId());
        } else {
            event.setCancelled(true);
        }
    }

    // ── Hit a living entity ───────────────────────────────────────────────────
    private void onHit(Player thrower, LivingEntity victim, Item dropped, ItemStack thrownItem) {
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.0f);

        if (victim instanceof Player p) damageArmor(p);

        double weaponDamage       = com.example.corovaItems.ItemMutations.MutationUtils
                .getWeaponDamage(thrownItem, thrower, victim);
        double combinedMultiplier = com.example.corovaItems.ItemMutations.MutationManager
                .getInstance().getCombinedSynergyMultiplier(thrownItem);

        for (Map.Entry<String, Integer> e : CorovaEnchantments.getAllCustomEnchants(thrownItem).entrySet()) {
            combinedMultiplier += CorovaEnchantments.getSynergyMultiplier(e.getKey(), e.getValue());
        }

        victim.setNoDamageTicks(0);

        IS_BOOMERANG_HIT.set(true);
        SYNERGY_ITEM.set(thrownItem);
        try {
            victim.damage(weaponDamage * (2.0 + combinedMultiplier), thrower);
            triggerSynergies(thrower, victim, thrownItem);
        } finally {
            IS_BOOMERANG_HIT.set(false);
            SYNERGY_ITEM.remove();
        }

        dropped.getPersistentDataContainer().set(RETURNING_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    private void triggerSynergies(Player thrower, LivingEntity victim, ItemStack thrownItem) {
        com.example.corovaItems.ItemMutations.MutationManager.getInstance()
                .getSynergyHandler().handleBoomerangSynergies(thrower, victim, thrownItem);
    }

    /** Deals random 50–75% durability damage to each armor piece; breaks it if it exceeds max. */
    private void damageArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece == null || piece.getType() == Material.AIR) continue;
            int damage = (int) (piece.getType().getMaxDurability()
                    * ThreadLocalRandom.current().nextDouble(0.5, 0.75));
            piece.setDurability((short) (piece.getDurability() + damage));
            if (piece.getDurability() >= piece.getType().getMaxDurability()) {
                piece.setAmount(0);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
        player.getInventory().setArmorContents(armor);
    }
}