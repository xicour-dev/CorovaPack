package com.example.corovaItems.Food;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class LuckyMushroom extends CorovaItems implements Listener {

    private static final int EAT_TICKS = 32;
    private static final long MAX_HOLD_GAP_NS = 300_000_000L;

    private final Map<UUID, Integer> eatingPlayers = new HashMap<>();
    private final Map<UUID, Long> lastInteractNs = new HashMap<>();

    public LuckyMushroom() {
        super(
                ChatColor.RED + "Lucky Mushroom",
                Material.RED_MUSHROOM,
                lore(),
                Collections.singletonMap(Enchantment.LUCK_OF_THE_SEA, 1),
                "luckymushroom"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A rare mushroom said to bring good fortune.");
        lore.add(ChatColor.DARK_GRAY + "Grants Luck III when eaten.");
        return lore;
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setMaxStackSize(1); // Prevent stacking
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onRightClickEat(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;

        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (!ItemManager.getInstance().isCorovaItem(inHand, this)) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        long now = System.nanoTime();
        lastInteractNs.put(uuid, now);

        if (!eatingPlayers.containsKey(uuid)) {
            eatingPlayers.put(uuid, EAT_TICKS);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);

            // ------------------ EATING SOUND LOOP ------------------
            new BukkitRunnable() {
                int soundTicks = 0;

                @Override
                public void run() {
                    if (!player.isOnline() || !eatingPlayers.containsKey(uuid)) {
                        cancel();
                        return;
                    }
                    soundTicks += 4;
                    if (soundTicks >= EAT_TICKS) {
                        cancel();
                        return;
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.9f, 1.0f);
                }
            }.runTaskTimer(Bukkit.getPluginManager().getPlugin("corovacore"), 4L, 4L);

            // ------------------ MAIN EATING CHECKER ------------------
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        eatingPlayers.remove(uuid);
                        lastInteractNs.remove(uuid);
                        cancel();
                        return;
                    }

                    int remaining = eatingPlayers.getOrDefault(uuid, 0) - 16;
                    if (remaining <= 0) remaining = 0;

                    Long last = lastInteractNs.get(uuid);
                    if (last == null || System.nanoTime() - last > MAX_HOLD_GAP_NS) {
                        eatingPlayers.remove(uuid);
                        lastInteractNs.remove(uuid);
                        cancel();
                        return;
                    }

                    if (remaining <= 0) {
                        eatingPlayers.remove(uuid);
                        lastInteractNs.remove(uuid);

                        ItemStack current = player.getInventory().getItemInMainHand();
                        if (ItemManager.getInstance().isCorovaItem(current, LuckyMushroom.this)) {
                            // Give Luck 2 for 8 minutes
                            player.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 8 * 60 * 20, 1));

                            int amount = current.getAmount();
                            if (amount > 1) {
                                current.setAmount(amount - 1);
                            } else {
                                player.getInventory().setItemInMainHand(null);
                            }

                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
                        }

                        cancel();
                        return;
                    }

                    eatingPlayers.put(uuid, remaining);
                }
            }.runTaskTimer(Bukkit.getPluginManager().getPlugin("corovacore"), 16L, 16L);
        }
    }
}