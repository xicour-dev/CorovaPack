package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EMP extends CorovaItems implements Listener {

    private final NamespacedKey empKey;
    private final JavaPlugin plugin;

    public EMP() {
        super(
                "EMP",
                Material.MILK_BUCKET,
                lore(),
                enchantments(),
                "emp"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(EMP.class);
        this.empKey = new NamespacedKey(plugin, "emp_projectile");
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isThisItem(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);

            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            Item emp = player.getWorld().dropItem(player.getEyeLocation(), getItemStack());
            emp.setVelocity(player.getEyeLocation().getDirection().multiply(1.2));
            emp.setOwner(player.getUniqueId());
            emp.setCanMobPickup(false);
            emp.getPersistentDataContainer().set(empKey, PersistentDataType.BYTE, (byte) 1);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (emp.isDead()) {
                        cancel();
                        return;
                    }

                    // Check for nearby entities
                    for (LivingEntity entity : emp.getWorld().getNearbyLivingEntities(emp.getLocation(), 1)) {
                        if (!entity.getUniqueId().equals(player.getUniqueId())) {
                            explode(emp);
                            cancel();
                            return;
                        }
                    }

                    if (emp.isOnGround()) {
                        explode(emp);
                        cancel();
                        return;
                    }

                    emp.getWorld().spawnParticle(Particle.FIREWORK, emp.getLocation(), 1, 0, 0, 0, 0);
                    emp.getWorld().playSound(emp.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0F, 2.0F);
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (item.getPersistentDataContainer().has(empKey, PersistentDataType.BYTE)) {
            if (event.getEntity() instanceof Player player && player.getUniqueId().equals(item.getOwner())) {
                // Allow the owner to pick it up
            } else {
                // Prevent others from picking it up
                event.setCancelled(true);
            }
        }
    }

    private void explode(Item emp) {
        emp.getWorld().spawnParticle(Particle.EFFECT, emp.getLocation(), 100, 1, 1, 1, 0.1);
        emp.getWorld().playSound(emp.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.0F);

        for (LivingEntity entity : emp.getWorld().getNearbyLivingEntities(emp.getLocation(), 3)) {
            UUID ownerUUID = emp.getOwner();
            if (ownerUUID != null && entity.getUniqueId().equals(ownerUUID)) continue;

            for (PotionEffect effect : entity.getActivePotionEffects()) {
                entity.removePotionEffect(effect.getType());
            }
        }
        emp.remove();
    }


    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.ITALIC + "" + ChatColor.DARK_PURPLE + "A throwable bomb that disables your enemies' potion effects!",
                ChatColor.DARK_GRAY + "Right click to launch."
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>();
    }
}
