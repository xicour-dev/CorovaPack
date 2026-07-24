package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EggsplodingEgg extends CorovaItems implements Listener {

    private final NamespacedKey eggsplodingEggKey;
    private final JavaPlugin plugin;

    public EggsplodingEgg() {
        super(
                ChatColor.AQUA + "Eggsploding Egg",
                Material.EGG,
                lore(),
                enchantments(),
                "eggsplodingegg"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(EggsplodingEgg.class);
        this.eggsplodingEggKey = new NamespacedKey(plugin, "eggsploding_egg");
        ItemManager.getInstance().registerItem(this);
        // Event registration handled by ItemRegistry
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (isThisItem(item)) {
                event.setCancelled(true);

                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItemInMainHand(null);
                }

                Egg egg = player.launchProjectile(Egg.class);
                egg.getPersistentDataContainer().set(eggsplodingEggKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    @EventHandler
    public void onLand(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Egg egg) {
            if (egg.getPersistentDataContainer().has(eggsplodingEggKey, PersistentDataType.BYTE)) {
                spawnFirework(egg.getLocation());
                egg.getWorld().createExplosion(egg.getLocation(), 2.0f, false, false);
            }
        }
    }

    private void spawnFirework(org.bukkit.Location location) {
        Firework fw = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.setPower(1);
        fwm.addEffect(FireworkEffect.builder()
                .withColor(Color.FUCHSIA)
                .withFade(Color.AQUA)
                .with(FireworkEffect.Type.BALL_LARGE)
                .build());
        fw.setFireworkMeta(fwm);
        fw.detonate();
    }


    private static List<String> lore() {
        return Collections.singletonList(ChatColor.GRAY + "Right click to throw.");
    }

    private static Map<Enchantment, Integer> enchantments() {
        return new HashMap<>();
    }
}
