package com.example.corovaItems.LOLPVP;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WifeBeater extends CorovaItems implements Listener, CommandExecutor {

    // Constructor for CorovaItems registration
    public WifeBeater() {
        super(
                ChatColor.AQUA + "Wife Beater", // Display name
                Material.DIAMOND_SWORD,         // Material
                lore(),                         // Lore
                enchantments(),                 // Enchantments
                "wifebeater"                    // internalId
        );

        // Automatically registers this item with ItemManager
        ItemManager.getInstance().registerItem(this);
    }

    // Helper method for lore
    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Launcher I");
        lore.add(ChatColor.DARK_GRAY + "The launcher sword!");
        return lore;
    }

    // Helper method for enchantments
    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 10);
        map.put(Enchantment.SMITE, 10);
        map.put(Enchantment.BANE_OF_ARTHROPODS, 5);
        map.put(Enchantment.LOOTING, 5);
        return map;
    }

    // Event listener for right-clicking entities
    @EventHandler
    public void onInteractItem(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity entity)) return;

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!ItemManager.getInstance().isCorovaItem(handItem, this)) return;

        // Prevent use on players in safezones
        if (entity instanceof Player && CorovaGuard.getInstance().isPlayerInSafeZone((Player) entity)) {
            CorovaGuard.sendSafeZoneMessage(player);
            event.setCancelled(true);
            return;
        }

        // Launch the entity straight up
        entity.setVelocity(new Vector(0, 1.3, 0));

        // Play sound
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);

        // Cancel the default interaction
        event.setCancelled(true);
    }

    // Event listener for preventing damage in safezones
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack handItem = damager.getInventory().getItemInMainHand();
        if (!ItemManager.getInstance().isCorovaItem(handItem, this)) return;

        if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
            CorovaGuard.sendSafeZoneMessage(damager);
            event.setCancelled(true);
        }
    }


    // Command to give the item
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command!");
            return true;
        }

        player.getInventory().addItem(this.toItemStack());
        player.sendMessage(ChatColor.GREEN + "You received the Wife Beater!");
        return true;
    }
}
