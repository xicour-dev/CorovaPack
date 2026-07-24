package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FireWorkBow extends CorovaItems implements Listener {

    private final List<Arrow> arrows = new ArrayList<>();
    private final Random random = new Random();

    public FireWorkBow() {
        super(
                ChatColor.AQUA + "Firework Bow",
                Material.BOW,
                lore(),
                enchantments(),
                "fireworkbow"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Firework I",
                ChatColor.DARK_GRAY + "Shoot fireworks!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.POWER, 5);
        return map;
    }

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getProjectile() instanceof Arrow)) return;

        ItemStack bow = event.getBow();
        if (ItemManager.getInstance().isCorovaItem(bow, this)) {
            arrows.add((Arrow) event.getProjectile());
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow) {
            if (arrows.remove(event.getEntity())) {
                spawnRandomFirework(event.getEntity().getLocation());
                event.getEntity().remove(); // Remove the arrow
            }
        }
    }

    private void spawnRandomFirework(Location loc) {
        Firework fw = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();

        FireworkEffect.Type type = FireworkEffect.Type.values()[random.nextInt(FireworkEffect.Type.values().length)];
        Color c1 = Color.fromBGR(random.nextInt(256), random.nextInt(256), random.nextInt(256));
        Color c2 = Color.fromBGR(random.nextInt(256), random.nextInt(256), random.nextInt(256));

        FireworkEffect effect = FireworkEffect.builder()
                .flicker(random.nextBoolean())
                .withColor(c1)
                .withFade(c2)
                .with(type)
                .trail(random.nextBoolean())
                .build();

        fwm.addEffect(effect);
        fwm.setPower(random.nextInt(2) + 1);
        fw.setFireworkMeta(fwm);
    }
}
