package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SkeletonHorse;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class SteedBook extends EnchantmentBook implements Listener {

    public static final int MAX_LEVEL    = 3;
    public static final int UPGRADE_COST = 10;

    // Horse movement speed per level.
    // Level 2 preserves the original 0.4 speed. Level 1 is slower, Level 3 is faster.
    private static final double[] SPEED_BY_LEVEL = {
            0.30, // Level I   — noticeably slower
            0.40, // Level II  — original speed (unchanged)
            0.52, // Level III — meaningfully faster
    };

    private final JavaPlugin plugin;

    private final List<UUID> horseList = new ArrayList<>();
    private final List<Item> bones     = new ArrayList<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private static final long COOLDOWN_MS   = 15_000; // 15 s
    private static final long DESPAWN_TICKS = 180 * 20L; // 3 min

    public SteedBook() {
        this(1);
    }

    public SteedBook(int level) {
        super(
                "Book of Steed",
                CorovaEnchantments.STEED_ID,
                level,
                "book_steed",
                allowedMaterialsStatic()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        ItemManager.getInstance().registerItem(this);
    }

    /**
     * Called by EnchantmentAnvilListener when two Steed books of the same level
     * are combined. Returns the next-level book, or null if already at max.
     */
    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right,
                                            NamespacedKey keyId, NamespacedKey keyLvl) {
        if (left == null || right == null) return null;
        var leftMeta  = left.getItemMeta();
        var rightMeta = right.getItemMeta();
        if (leftMeta == null || rightMeta == null) return null;

        String leftId  = leftMeta.getPersistentDataContainer().get(keyId,  org.bukkit.persistence.PersistentDataType.STRING);
        String rightId = rightMeta.getPersistentDataContainer().get(keyId, org.bukkit.persistence.PersistentDataType.STRING);
        if (!CorovaEnchantments.STEED_ID.equals(leftId) || !CorovaEnchantments.STEED_ID.equals(rightId)) return null;

        Integer leftLvl  = leftMeta.getPersistentDataContainer().get(keyLvl,  org.bukkit.persistence.PersistentDataType.INTEGER);
        Integer rightLvl = rightMeta.getPersistentDataContainer().get(keyLvl, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (leftLvl == null || rightLvl == null) return null;
        if (!leftLvl.equals(rightLvl)) return null; // must be same level to combine
        if (leftLvl >= MAX_LEVEL) return null;       // already at max

        return new SteedBook(leftLvl + 1).toItemStack();
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

    // ── Right-click to summon skeleton horse ──────────────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();

        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.STEED_ID)) return;

        // FIX: Only the instance matching the weapon's enchant level processes this.
        if (CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.STEED_ID) != this.getLevel()) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        long now  = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(uuid, 0L);

        if (now - last < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - last)) / 1000;
            Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.STEED_ID, "Steed");
            player.sendActionBar(enchantName.append(Component.text(" is on cooldown: " + remaining + "s", net.kyori.adventure.text.format.NamedTextColor.RED)));
            return;
        }
        cooldowns.put(uuid, now);

        // Resolve speed for this book's level (clamp defensively)
        int idx = Math.max(0, Math.min(this.getLevel() - 1, SPEED_BY_LEVEL.length - 1));
        double speed = SPEED_BY_LEVEL[idx];

        // ── Spawn the skeleton horse ──────────────────────────────────────────
        SkeletonHorse horse = (SkeletonHorse) player.getWorld()
                .spawn(player.getLocation(), SkeletonHorse.class);

        horse.setTamed(true);
        horse.setOwner(player);
        horse.setCustomName(ChatColor.WHITE + player.getName());
        horse.setCustomNameVisible(true);
        horse.setMaxHealth(300.0);
        horse.setHealth(300.0);
        horse.setRemoveWhenFarAway(false);
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));
        horse.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)
                .setBaseValue(speed);

        horseList.add(horse.getUniqueId());

        // ── Nether Fire synergy ───────────────────────────────────────────────
        // If the weapon has the Nether Fire mutation, the summoned horse radiates
        // flame and smoke particles for its full lifetime.
        boolean netherFireSynergy = com.example.corovaItems.ItemMutations.MutationManager
                .getInstance().getSynergyHandler().isNetherFire(hand);

        if (netherFireSynergy) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (horse.isDead() || !horse.isValid()) {
                        cancel();
                        return;
                    }
                    org.bukkit.Location l = horse.getLocation().add(0, 0.5, 0);
                    horse.getWorld().spawnParticle(Particle.FLAME,      l, 6, 0.4, 0.5, 0.4, 0.02);
                    horse.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 3, 0.3, 0.4, 0.3, 0.01);
                }
            }.runTaskTimer(plugin, 0L, 4L); // fires every 4 ticks (5× per second)
        }

        Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.STEED_ID, "steed");
        player.sendActionBar(Component.text("Your ", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .append(enchantName)
                .append(Component.text(" has arrived!", net.kyori.adventure.text.format.NamedTextColor.GREEN)));

        // ── Auto-despawn after 3 minutes ──────────────────────────────────────
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!horse.isDead()) horse.remove();
                horseList.remove(horse.getUniqueId());
                Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.STEED_ID, "steed");
                player.sendActionBar(Component.text("Your ", net.kyori.adventure.text.format.NamedTextColor.RED)
                        .append(enchantName)
                        .append(Component.text(" has despawned.", net.kyori.adventure.text.format.NamedTextColor.RED)));
            }
        }.runTaskLater(plugin, DESPAWN_TICKS);
    }

    // ── Clear drops when a summoned horse dies ────────────────────────────────

    @EventHandler
    public void onHorseDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof SkeletonHorse horse)) return;
        if (!horseList.contains(horse.getUniqueId())) return;

        event.getDrops().clear();
        horseList.remove(horse.getUniqueId());
    }

    /** Drop bones when hitting an entity */
    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.STEED_ID)) return;

        // FIX: same level guard — prevents N bone volleys for N registered instances
        if (CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.STEED_ID) != this.getLevel()) return;

        World world = entity.getWorld();

        // Spawn "falling bones" effect
        for (int i = 0; i < 10; i++) {
            ItemStack boneStack = getBone();
            Item bone = world.dropItem(entity.getLocation().add(
                    (Math.random() - 0.5) * 1.2,
                    0.5 + Math.random() * 0.5,
                    (Math.random() - 0.5) * 1.2
            ), boneStack);
            bones.add(bone);

            bone.setPickupDelay(Integer.MAX_VALUE);
            bone.setInvulnerable(true);
            bone.setGravity(true);

            bone.setVelocity(new Vector(
                    (Math.random() - 0.5) * 0.2,
                    Math.random() * 0.2 + 0.1,
                    (Math.random() - 0.5) * 0.2
            ));

            world.spawnParticle(Particle.BLOCK_CRUMBLE, bone.getLocation(), 4, 0.2, 0.2, 0.2, Material.BONE_BLOCK.createBlockData());

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!bone.isDead()) bone.remove();
                    bones.remove(bone);
                }
            }.runTaskLater(plugin, 10L);
        }

        world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
    }

    /** Prevent picking up temporary bones */
    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (bones.contains(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Generate a single bone item */
    private ItemStack getBone() {
        ItemStack bone = new ItemStack(Material.BONE);
        ItemMeta meta = bone.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + "Bones");
            bone.setItemMeta(meta);
        }
        return bone;
    }
}