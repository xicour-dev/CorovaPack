package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BlindingRod extends CorovaItems implements Listener {

    public BlindingRod() {
        super(
                ChatColor.AQUA + "Blinding Rod",
                Material.SHEARS,
                lore(),
                enchantments(),
                "blindingrod"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Blindness I",
                ChatColor.DARK_GRAY + "Hit enemies to blind them"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>();
    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (ItemManager.getInstance().isCorovaItem(damager.getInventory().getItemInMainHand(), this)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 4 * 20, 0));
        }
    }
}
