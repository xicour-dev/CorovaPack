package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class WitchsMagic extends CorovaItems implements Listener {

    // Set to track thrown potions from this item
    private final Set<ThrownPotion> thrownPotions = new HashSet<>();

    public WitchsMagic() {
        super(
                ChatColor.AQUA + "Witch's Magic",
                Material.SPLASH_POTION,
                lore(),
                enchantments(),
                "witchsmagic"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.RED + "Blindness (0:10)");
        lore.add(ChatColor.RED + "Slowness (0:10)");
        lore.add(ChatColor.RED + "Confusion (0:10)");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>(); // No enchantments
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (ItemManager.getInstance().isCorovaItem(item, this) &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {

            event.setCancelled(true);

            // Decrement item amount if not in creative mode
            if (player.getGameMode() != GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }

            // Launch a potion and track it
            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.0F, 1.0F);
            ThrownPotion potion = player.launchProjectile(ThrownPotion.class);
            thrownPotions.add(potion);
        }
    }

    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();

        // Check if this potion was thrown by our item
        if (thrownPotions.contains(potion)) {
            // Apply custom effects to all affected entities
            for (LivingEntity entity : event.getAffectedEntities()) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 10 * 20, 0));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 10 * 20, 0));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10 * 20, 0));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 33 * 20, 0));
            }

            // Clean up the tracked potion
            thrownPotions.remove(potion);
        }
    }
}
