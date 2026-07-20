package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.CurrencyHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public class Payday extends CorovaItems implements Listener {

    private final List<Item> moneyDrops = new ArrayList<>();
    private final JavaPlugin plugin;

    /** Prevents duplicate death processing */
    private final Set<UUID> processedDeaths = new HashSet<>();

    public Payday() {
        super(
                ChatColor.AQUA + "Pay Day",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "payday"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(Payday.class);
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            if (isThisItem(attacker.getInventory().getItemInMainHand())) {
                spawnFirework(victim.getLocation());

                List<Item> currentDrops = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    Item paper = victim.getWorld().dropItemNaturally(
                            victim.getLocation().add(0, 1, 0),
                            new ItemStack(Material.PAPER)
                    );
                    moneyDrops.add(paper);
                    currentDrops.add(paper);
                }

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        for (Item item : currentDrops) item.remove();
                        moneyDrops.removeAll(currentDrops);
                    }
                }.runTaskLater(plugin, 20L);
            }
        }
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (moneyDrops.contains(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) return;
        if (!isThisItem(killer.getInventory().getItemInMainHand())) return;

        UUID victimId = victim.getUniqueId();
        if (processedDeaths.contains(victimId)) return;
        processedDeaths.add(victimId);

        // Steal 10% of the victim's deposited balance
        long victimBalance = CurrencyHook.Registry.getBalance(victim);
        long stolen = (long) Math.floor(victimBalance * 0.10);

        if (stolen > 0) {
            CurrencyHook.Registry.removeBalance(victim, stolen);
            CurrencyHook.Registry.addBalance(killer, stolen);

            killer.sendMessage(ChatColor.GREEN + "Payday! You stole "
                    + CurrencyHook.Registry.format(stolen) + ChatColor.GREEN + " from " + victim.getName() + "!");
            victim.sendMessage(ChatColor.RED + killer.getName() + " stole "
                    + CurrencyHook.Registry.format(stolen) + ChatColor.RED + " from your balance!");
        }

        Bukkit.broadcast(Component.text("It's payday bitch!", NamedTextColor.GREEN));

        // Give ONE kill certificate
        ItemStack trophy = new ItemStack(Material.PAPER);
        ItemMeta meta = trophy.getItemMeta();
        meta.displayName(Component.text("Kill: " + victim.getName(), NamedTextColor.WHITE));

        String date = new SimpleDateFormat("MM-dd-yyyy").format(new Date());
        meta.lore(Collections.singletonList(
                Component.text(
                        "Trophy for killing " + victim.getName() + " on " + date,
                        NamedTextColor.DARK_GRAY
                )
        ));

        trophy.setItemMeta(meta);
        killer.getInventory().addItem(trophy);

        new BukkitRunnable() {
            @Override
            public void run() { processedDeaths.remove(victimId); }
        }.runTaskLater(plugin, 5L);
    }

    private void spawnFirework(org.bukkit.Location location) {
        Firework fw = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.setPower(0);
        fwm.addEffect(FireworkEffect.builder()
                .withColor(Color.GREEN)
                .withFade(Color.YELLOW)
                .with(FireworkEffect.Type.BURST)
                .build());
        fw.setFireworkMeta(fwm);

        new BukkitRunnable() {
            @Override
            public void run() { fw.detonate(); }
        }.runTaskLater(plugin, 2L);
    }

    public static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.SHARPNESS, 10);
        enchantments.put(Enchantment.SMITE, 10);
        enchantments.put(Enchantment.LOOTING, 10);
        return enchantments;
    }

    public static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Pay Day I",
                ChatColor.DARK_GRAY + "It's pay day! Steal 10% of your enemy's balance when you kill them!"
        );
    }
}