package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FortyFoot extends CorovaItems implements Listener {

    private final List<Arrow> arrows = new ArrayList<>();

    public FortyFoot() {
        super(
                ChatColor.AQUA + "THE 40 FOOT CUMSHOT",
                Material.BOW,
                lore(),
                enchantments(),
                "40footcumshot"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(ChatColor.GRAY + "Poison I");
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.INFINITY, 1);
        map.put(Enchantment.POWER, 5);
        map.put(Enchantment.PUNCH, 5);
        return map;
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getProjectile() instanceof Arrow)) return;

        ItemStack bow = event.getBow();
        if (ItemManager.getInstance().isCorovaItem(bow, this)) {
            arrows.add((Arrow) event.getProjectile());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        if (arrows.contains(arrow)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 10 * 20, 0));
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            arrows.remove(event.getEntity());
        }
    }
}
