package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class LOLSword extends CorovaItems implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 5000; // 5 seconds
    private final List<PotionEffect> effects = Arrays.asList(
            new PotionEffect(PotionEffectType.BLINDNESS, 100, 0),
            new PotionEffect(PotionEffectType.NAUSEA, 100, 0),
            new PotionEffect(PotionEffectType.POISON, 100, 0),
            new PotionEffect(PotionEffectType.SLOWNESS, 100, 0),
            new PotionEffect(PotionEffectType.WITHER, 100, 0),
            new PotionEffect(PotionEffectType.WEAKNESS, 100, 0)
    );
    private final NamespacedKey lolswordTakenKey;
    private final JavaPlugin plugin;

    public LOLSword() {
        super(
                ChatColor.AQUA + "lolitssword",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "lolsword"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(LOLSword.class);
        this.lolswordTakenKey = new NamespacedKey(plugin, "lolsword_taken");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getName().toLowerCase().startsWith("lolits")) {
            PersistentDataContainer data = player.getPersistentDataContainer();
            if (!data.has(lolswordTakenKey, PersistentDataType.BYTE)) {
                player.getInventory().addItem(getItemStack());
                player.sendMessage(Component.text("You received a LOL Sword!", NamedTextColor.AQUA));
                data.set(lolswordTakenKey, PersistentDataType.BYTE, (byte) 1);
            }
        }
    }

    @EventHandler
    public void onPlayerInteractPlayer(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Player clicked) {
            Player player = event.getPlayer();

            if (isThisItem(player.getInventory().getItemInMainHand())) {
                if (!player.getName().toLowerCase().startsWith("lolits")) {
                    player.sendMessage(Component.text(ChatColor.RED + "You cannot use this sword! You need to have 'lolits' in your name!"));
                    return;
                }

                UUID playerId = player.getUniqueId();
                long lastUsed = cooldowns.getOrDefault(playerId, 0L);
                long now = System.currentTimeMillis();

                if (now - lastUsed < COOLDOWN_MS) {
                    long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
                    player.sendActionBar(Component.text(ChatColor.RED + "LOLSword is on cooldown for " + remaining + "s"));
                    return;
                }

                cooldowns.put(playerId, now);
                Random r = new Random();
                PotionEffect randomEffect = effects.get(r.nextInt(effects.size()));
                clicked.addPotionEffect(randomEffect);
                player.sendActionBar(Component.text(ChatColor.GREEN + "You have afflicted " + clicked.getName() + " with a random effect!"));
                event.setCancelled(true);
            }
        }
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Random Effect I",
                ChatColor.DARK_GRAY + "A special sword given only to those who bear the lolits name."
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return Collections.singletonMap(Enchantment.SHARPNESS, 8);
    }
}