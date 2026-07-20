package com.example.corovaItems.Weapons;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.Weapons.Scythes.IronScythe;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.NamespacedKey;

import java.util.UUID;
import java.util.*;

public class ReapersScythe extends CorovaItems implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ReapersScythe() {
        super(
                ChatColor.DARK_RED + "Reaper's Scythe",
                new Material[]{Material.IRON_HOE},
                lore(),
                enchantments(),
                "reapersscythe",
                null,
                attributes()
        );

        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Soul Projection I");
        lore.add(ChatColor.DARK_GRAY + "Shoots a vex on right click.");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 10);
        return map;
    }

    private static Map<Attribute, AttributeModifier> attributes() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(IronScythe.class);
        // Attack Speed 4 = Base 4 + 0 Modifier
        // Attack Damage 3 = Base 1 + 2 Modifier
        return Map.of(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "ironscythe_attack_speed"), 0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND),
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "ironscythe_attack_damage"), 3.5, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND)
        );
    }


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (!ItemManager.getInstance().isCorovaItem(handItem, this)) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            long cooldownTime = 5000;
            if (cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = System.currentTimeMillis() - cooldowns.get(player.getUniqueId());
                if (timeLeft < cooldownTime) {
                    player.sendMessage(ChatColor.RED + "Ability is on cooldown!");
                    return;
                }
            }
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());

            Vex vex = player.getWorld().spawn(player.getEyeLocation(), Vex.class);
            vex.setCustomName("ReapersScytheSoul:" + player.getUniqueId());
            vex.setCustomNameVisible(false);
            vex.setSilent(true);
            vex.setGravity(false);
            vex.setAware(false);
            vex.setInvulnerable(true);

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 1f);

            Vector velocity = player.getEyeLocation().getDirection().multiply(1.5);
            vex.setVelocity(velocity);

            final UUID vexUUID = vex.getUniqueId();
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    Entity entity = Bukkit.getEntity(vexUUID);

                    if (entity == null || !entity.isValid() || entity.isDead() || !(entity instanceof Vex)) {
                        this.cancel();
                        return;
                    }

                    Vex vex = (Vex) entity;

                    if (vex.getLocation().getBlock().getType().isSolid()) {
                        vex.remove();
                        this.cancel();
                        return;
                    }
                    ticks++;

                    if (ticks >= 30) {
                        vex.remove();
                        this.cancel();
                        return;
                    }

                    vex.setVelocity(velocity);

                    for (Entity nearbyEntity : vex.getNearbyEntities(0.5, 0.5, 0.5)) {
                        if (!(nearbyEntity instanceof LivingEntity)) continue;
                        if (nearbyEntity.equals(player)) continue;
                        if (nearbyEntity instanceof Vex) continue;

                        LivingEntity target = (LivingEntity) nearbyEntity;

                        if (target instanceof Player victim) {
                            if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                                CorovaGuard.sendSafeZoneMessage(player);
                                vex.remove();
                                this.cancel();
                                return;
                            }
                        }

                        target.damage(60.0, player);
                        vex.remove();
                        this.cancel();
                        return;
                    }
                }
            }.runTaskTimer(JavaPlugin.getProvidingPlugin(ReapersScythe.class), 0L, 1L);
        }
    }
}