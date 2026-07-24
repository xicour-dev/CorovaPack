package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class BoneSword extends CorovaItems implements Listener {

    private final List<UUID> horseList = new ArrayList<>();
    private final List<Item> bones = new ArrayList<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public BoneSword() {
        super(ChatColor.AQUA + "Bone Sword",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "bonesword"
        );

        // Register this item
        ItemManager.getInstance().registerItem(this);
    }

    /** Drop bones when hitting an entity */
    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (!ItemManager.getInstance().isCorovaItem(player.getInventory().getItemInMainHand(), this)) return;

        World world = entity.getWorld();

        // Spawn "falling bones" effect
        for (int i = 0; i < 10; i++) {
            ItemStack boneStack = getBone();
            // Drop the item
            Item bone = world.dropItem(entity.getLocation().add(
                    (Math.random() - 0.5) * 1.2,  // small x offset
                    0.5 + Math.random() * 0.5,   // small y offset
                    (Math.random() - 0.5) * 1.2  // small z offset
            ), boneStack);
            bones.add(bone);

            // Make it impossible to pick up
            bone.setPickupDelay(Integer.MAX_VALUE);
            bone.setInvulnerable(true);
            bone.setGravity(true);

            // Give it a slight random velocity for "falling" effect
            bone.setVelocity(new Vector(
                    (Math.random() - 0.5) * 0.2,
                    Math.random() * 0.2 + 0.1,
                    (Math.random() - 0.5) * 0.2
            ));

            // Add particle effect at the bone location (block crumble)
            world.spawnParticle(Particle.BLOCK_CRUMBLE, bone.getLocation(), 4, 0.2, 0.2, 0.2, Material.BONE_BLOCK.createBlockData());

            // Schedule removal very quickly
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!bone.isDead()) {
                        bone.remove();
                    }
                    bones.remove(bone);
                }
            }.runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(BoneSword.class), 10L); // 0.5 seconds
        }

        // Play hurt sound
        world.playSound(entity.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
    }


    /** Prevent picking up temporary bones */
    @EventHandler
    public void onPickup(PlayerPickupItemEvent event) {
        if (bones.contains(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Right-click to summon skeleton horse */
    @EventHandler
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!ItemManager.getInstance().isCorovaItem(player.getInventory().getItemInMainHand(), this)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid) && now - cooldowns.get(uuid) < 15_000) {
            long remaining = (15_000 - (now - cooldowns.get(uuid))) / 1000;
            player.sendMessage(ChatColor.RED + "Bone Sword is on cooldown for " + remaining + " seconds.");
            return;
        }
        cooldowns.put(uuid, now);

        // Spawn SkeletonHorse
        SkeletonHorse horse = (SkeletonHorse) player.getWorld().spawn(player.getLocation(), SkeletonHorse.class);
        horse.setTamed(true);
        horse.setOwner(player);
        horse.setCustomName(ChatColor.WHITE + player.getName());
        horse.setCustomNameVisible(true);
        horse.setMaxHealth(300.0);
        horse.setHealth(300.0);
        horse.setRemoveWhenFarAway(false);

// Add saddle
        horse.getInventory().setSaddle(new ItemStack(Material.SADDLE));

// Apply Speed II in attribute form
// Default horse movement speed is ~0.2, so Speed II is roughly +40% (0.2 * (1 + 0.4) = 0.28)
        horse.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED)
                .setBaseValue(0.4); // Adjust this to match desired speed

        horseList.add(horse.getUniqueId());


        new BukkitRunnable() {
            @Override
            public void run() {
                horse.remove();
                horseList.remove(horse.getUniqueId());
                player.sendMessage(ChatColor.RED + "Your horse has despawned.");
            }
        }.runTaskLater(org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(BoneSword.class), 180 * 20L);
    }


    /** Prevent drops from summoned horses */
    @EventHandler
    public void onHorseDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof SkeletonHorse horse)) return;
        if (!horseList.contains(horse.getUniqueId())) return;

        event.getDrops().clear();
        horseList.remove(horse.getUniqueId());
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

    /** Enchantments */
    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        enchants.put(Enchantment.SHARPNESS, 10);
        enchants.put(Enchantment.SMITE, 10);
        enchants.put(Enchantment.LOOTING, 5);
        enchants.put(Enchantment.BANE_OF_ARTHROPODS, 5);
        return enchants;
    }

    /** Lore */
    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Summon Horse I");
        lore.add(ChatColor.DARK_GRAY + "The sword of the dead!");
        return lore;
    }
}
