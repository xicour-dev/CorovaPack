package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZeusBow extends CorovaItems implements Listener {

    private final ArrayList<Entity> arrows = new ArrayList<>();

    public ZeusBow() {
        super(
                ChatColor.AQUA + "Zeus Bow",
                Material.BOW,
                lore(),
                enchantments(),
                "zeusbow"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Lightning I");
        lore.add(ChatColor.DARK_GRAY + "Shoot enemies to strike them with lightning!");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>(); // No enchantments
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getProjectile() instanceof Arrow)) return;

        ItemStack bow = event.getBow();
        if (ItemManager.getInstance().isCorovaItem(bow, this)) {
            this.arrows.add(event.getProjectile());
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;

        Entity arrow = event.getEntity();
        if (this.arrows.contains(arrow)) {
            Location location = arrow.getLocation();
            player.getWorld().strikeLightning(location);
            this.arrows.remove(arrow);
        }
    }
}
