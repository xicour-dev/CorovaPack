package com.example.corovaItems.Food;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class EnchantedGlisteringMelon extends CorovaItems implements Listener {

    private static final int EAT_TICKS = 32; // total eating time
    private static final long MAX_HOLD_GAP_NS = 300_000_000L; // 300ms = stop threshold

    private static final int HEALTH_BOOST_DURATION_TICKS = 5 * 60 * 20; // 5 minutes

    // Fixed key for the extra-heart attribute modifier so re-applying it never stacks
    private static final NamespacedKey EXTRA_HEART_MODIFIER_KEY =
            new NamespacedKey("corovacore", "enchanted_melon_extra_heart");
    private static final double EXTRA_HEART_AMOUNT = 2.0; // 1 heart = 2 health points

    private final Map<UUID, Integer> eatingPlayers = new HashMap<>();
    private final Map<UUID, Long> lastInteractNs = new HashMap<>();

    // Tracks the pending "remove extra heart" task per player so it can be
    // cancelled/rescheduled on re-eat instead of stacking or firing early.
    private final Map<UUID, BukkitTask> heartRemovalTasks = new HashMap<>();

    public EnchantedGlisteringMelon() {
        super(
                ChatColor.AQUA + "Enchanted Glistering Melon",
                Material.GLISTERING_MELON_SLICE,
                lore(),
                Collections.singletonMap(Enchantment.LUCK_OF_THE_SEA, 1),
                "enchantedglisteringmelon"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A mystical melon slice!");
        lore.add(ChatColor.DARK_GRAY + "Grants 5 hearts when eaten.");
        return lore;
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("corovacore");
    }

    /**
     * Applies the Health Boost effect (amplifier 1 = +4 hearts) plus a single,
     * non-stacking +1 heart attribute modifier, for a total of +5 hearts.
     * Safe to call repeatedly — re-eating just refreshes the duration instead of stacking,
     * and the extra heart is automatically removed once Health Boost expires.
     */
    private void applyMelonBonus(Player player) {
        // Health Boost amplifier 1 (Health Boost II) = +4 hearts
        player.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, HEALTH_BOOST_DURATION_TICKS, 1));

        addExtraHeart(player);
        scheduleHeartRemoval(player);
    }

    private void addExtraHeart(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        // Remove any previous instance of our modifier first so it never stacks
        maxHealth.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(EXTRA_HEART_MODIFIER_KEY))
                .forEach(maxHealth::removeModifier);

        AttributeModifier extraHeart = new AttributeModifier(
                EXTRA_HEART_MODIFIER_KEY,
                EXTRA_HEART_AMOUNT,
                AttributeModifier.Operation.ADD_NUMBER
        );
        maxHealth.addModifier(extraHeart);
    }

    private void removeExtraHeart(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        boolean removed = false;
        for (AttributeModifier mod : new ArrayList<>(maxHealth.getModifiers())) {
            if (mod.getKey().equals(EXTRA_HEART_MODIFIER_KEY)) {
                maxHealth.removeModifier(mod);
                removed = true;
            }
        }

        // Clamp current health down if it now exceeds the reduced max health
        if (removed && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }
    }

    /**
     * (Re)schedules removal of the extra heart to line up with when the
     * Health Boost potion effect naturally expires. Cancels any previous
     * pending removal so re-eating the melon just refreshes the timer.
     */
    private void scheduleHeartRemoval(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask existing = heartRemovalTasks.remove(uuid);
        if (existing != null) {
            existing.cancel();
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                heartRemovalTasks.remove(uuid);
                if (player.isOnline()) {
                    removeExtraHeart(player);
                }
            }
        }.runTaskLater(plugin(), HEALTH_BOOST_DURATION_TICKS);

        heartRemovalTasks.put(uuid, task);
    }

    /**
     * Catches Health Boost being removed by any means other than us re-applying it
     * (e.g. milk bucket, /effect clear, another plugin, or natural expiry that
     * somehow races our scheduled task) and cleans up the extra heart immediately.
     */
    @EventHandler
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (event.getModifiedType() != PotionEffectType.HEALTH_BOOST) return;
        if (!(event.getEntity() instanceof Player)) return;

        // If the new effect is null, Health Boost is being removed entirely
        // (expired, cleared, cured, etc.) rather than refreshed/changed.
        if (event.getNewEffect() != null) return;

        Player player = (Player) event.getEntity();
        UUID uuid = player.getUniqueId();

        BukkitTask task = heartRemovalTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        removeExtraHeart(player);
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
            }.runTaskTimer(plugin(), 4L, 4L);

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
                        // Player stopped holding — cancel after this 16-tick interval
                        eatingPlayers.remove(uuid);
                        lastInteractNs.remove(uuid);
                        cancel();
                        return;
                    }

                    if (remaining <= 0) {
                        eatingPlayers.remove(uuid);
                        lastInteractNs.remove(uuid);

                        ItemStack current = player.getInventory().getItemInMainHand();
                        if (ItemManager.getInstance().isCorovaItem(current, EnchantedGlisteringMelon.this)) {
                            applyMelonBonus(player);

                            int amount = current.getAmount();
                            if (amount > 1) current.setAmount(amount - 1);
                            else player.getInventory().setItemInMainHand(null);

                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0f, 1.0f);
                        }

                        cancel();
                        return;
                    }

                    eatingPlayers.put(uuid, remaining);
                }
            }.runTaskTimer(plugin(), 16L, 16L);
        }
    }
}