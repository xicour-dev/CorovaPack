package com.example.corovaItems.Trinkets;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TrinketUtils implements Listener {

    private static final Map<UUID, Double> lastScaling = new HashMap<>();
    private static JavaPlugin plugin;

    // -----------------------------------------------------------------------
    // Scaling cache — populated once per checkEntity() call, cleared after.
    // All checkTrinket() implementations call getCachedScaling() so the
    // full inventory scan only happens once per entity per checkEntity pass.
    // Not thread-safe by design: trinket checks always run on the main thread.
    // -----------------------------------------------------------------------
    static double  cachedScaling       = -1.0;
    static boolean scalingCacheActive  = false;

    static double getCachedScaling(LivingEntity entity, ItemStack[] items) {
        if (scalingCacheActive) return cachedScaling;
        return applyTrinketScaling(entity, items);
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    public static void registerListeners(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        TrinketUtils utils = new TrinketUtils();
        Bukkit.getPluginManager().registerEvents(utils, plugin);

        Bukkit.getPluginManager().registerEvents(new Anchor(),              plugin);
        Bukkit.getPluginManager().registerEvents(new Backpack(),            plugin);
        Bukkit.getPluginManager().registerEvents(new BlazingPower(),        plugin);
        Bukkit.getPluginManager().registerEvents(new BlockReachExtender(),  plugin);
        Bukkit.getPluginManager().registerEvents(new BloodSugar(),          plugin);
        Bukkit.getPluginManager().registerEvents(new DenseArmorPlating(),   plugin);
        Bukkit.getPluginManager().registerEvents(new EnergyFormula(),       plugin);
        Bukkit.getPluginManager().registerEvents(new MinersMight(),         plugin);
        Bukkit.getPluginManager().registerEvents(new SpiderEyeTotem(),      plugin);
        Bukkit.getPluginManager().registerEvents(new SwiftStrike(),         plugin);
        Bukkit.getPluginManager().registerEvents(new Compactor(),           plugin);
        Bukkit.getPluginManager().registerEvents(new EnchantedQuiver(),     plugin);

        // Background task: players only, once per second.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkEntity(player);
            }
        }, 20L, 20L);
    }

    // -----------------------------------------------------------------------
    // Item helpers
    // -----------------------------------------------------------------------

    public static ItemStack[] getTrinketItems(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getInventory().getContents();
        }
        if (entity.getEquipment() == null) return new ItemStack[0];
        return new ItemStack[]{
                entity.getEquipment().getItemInMainHand(),
                entity.getEquipment().getItemInOffHand()
        };
    }

    // -----------------------------------------------------------------------
    // Trinket counting — lightweight, no lore rewrite.
    //
    // This is the hot path called by countTrinkets() and the cache-fill in
    // checkEntity().  It only iterates the inventory once and checks for the
    // UUID lore tag — no particle effects, no lore mutations, no HashMap
    // writes unless the scaling value has actually changed.
    // -----------------------------------------------------------------------

    /**
     * Counts UUID-tagged trinkets and returns the corresponding scaling value.
     * Does NOT emit particles, send action-bar messages, or rewrite lore.
     * Those side-effects live in applyTrinketScaling() and are only triggered
     * when the scaling value changes.
     */
    static double computeScaling(int activeTrinkets) {
        if (activeTrinkets <= 3) return 1.0;
        if (activeTrinkets == 4) return 0.75;
        if (activeTrinkets == 5) return 0.5;
        return 0.0;
    }

    static int countActiveTrinkets(ItemStack[] trinkets) {
        int count = 0;
        for (ItemStack item : trinkets) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            List<String> lore = meta.getLore();
            if (lore == null) continue;
            for (String line : lore) {
                if (line.contains("UUID:")) { count++; break; }
            }
        }
        return count;
    }

    // -----------------------------------------------------------------------
    // Full scaling apply — only called when something has actually changed.
    //
    // FIX: The lore-rewrite block (adding/removing "Effect reduced" warnings)
    // previously ran on every single checkEntity() call regardless of whether
    // the scaling value had changed.  For 80-100 players that meant up to 100
    // full inventory scans with meta reads, lore clones, and slot-by-slot item
    // replacements every second — all wasted when scaling is stable (which it
    // is the vast majority of the time).
    //
    // The fix moves the lore-rewrite inside the `scaling != previousScaling`
    // branch so it only runs when the trinket count actually crosses a
    // threshold.  The particle/action-bar feedback was already gated; this
    // extends the same gate to the expensive lore mutation.
    // -----------------------------------------------------------------------

    public static double applyTrinketScaling(LivingEntity entity, ItemStack[] trinkets) {
        if (entity == null) return 1.0;

        int activeTrinkets = 0;
        List<ItemStack> countedTrinkets = new ArrayList<>();
        for (ItemStack item : trinkets) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            List<String> lore = meta.getLore();
            if (lore == null) continue;
            for (String line : lore) {
                if (line.contains("UUID:")) {
                    activeTrinkets++;
                    countedTrinkets.add(item);
                    break;
                }
            }
        }

        double scaling;
        String severity;
        if (activeTrinkets <= 3) {
            scaling  = 1.0;
            severity = "";
        } else if (activeTrinkets == 4) {
            scaling  = 0.75;
            severity = "mild";
        } else if (activeTrinkets == 5) {
            scaling  = 0.5;
            severity = "moderate";
        } else {
            scaling  = 0.0;
            severity = "severe";
        }

        // Creative players still return the scaling value but skip all effects.
        if (entity instanceof Player player &&
                player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return scaling;
        }

        double previousScaling = lastScaling.getOrDefault(entity.getUniqueId(), 1.0);

        // ── Everything below only runs when scaling actually changes ──────────
        // FIX: moved lore-rewrite inside this branch — it was previously
        // executing unconditionally on every call.
        if (scaling != previousScaling) {
            lastScaling.put(entity.getUniqueId(), scaling);

            // Particle feedback
            if (scaling < 1.0 && scaling > 0.0) {
                int count = severity.equals("mild") ? 5 : severity.equals("moderate") ? 10 : 0;
                if (count > 0) {
                    entity.getWorld().spawnParticle(
                            Particle.ANGRY_VILLAGER,
                            entity.getLocation().add(0, 1, 0),
                            count, 0.3, 0.3, 0.3, 0);
                }
            }
            if (scaling == 0.0) {
                entity.getWorld().spawnParticle(
                        Particle.ANGRY_VILLAGER,
                        entity.getLocation().add(0, 1, 0),
                        20, 0.5, 0.5, 0.5, 0);
            }

            // Lore rewrite — only for players, only when the threshold crossed.
            if (entity instanceof Player player) {
                int softLimit = 3;
                String warning = org.bukkit.ChatColor.RED +
                        "Effect reduced due to too many trinkets!";

                for (int i = 0; i < countedTrinkets.size(); i++) {
                    ItemStack item = countedTrinkets.get(i);
                    if (item == null) continue;
                    ItemMeta meta = item.getItemMeta();
                    if (meta == null) continue;

                    List<String> lore = meta.getLore() != null
                            ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                    boolean shouldWarn = (i >= softLimit && scaling < 1.0);
                    boolean hasWarning = lore.contains(warning);

                    // Only mutate the item when the lore actually needs to change.
                    if (shouldWarn == hasWarning) continue;

                    if (shouldWarn) {
                        lore.add(warning);
                    } else {
                        lore.removeIf(line -> line.contains("Effect reduced"));
                    }

                    meta.setLore(lore);
                    ItemStack newItem = item.clone();
                    newItem.setItemMeta(meta);

                    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                        ItemStack slotItem = player.getInventory().getItem(slot);
                        if (slotItem == item) {
                            player.getInventory().setItem(slot, newItem);
                            break;
                        }
                    }
                }
            }
        }

        // Action-bar reminder fires every call while scaling is degraded
        // (cheap — just a string send, no inventory scan).
        if (scaling < 1.0 && entity instanceof Player player) {
            player.sendActionBar(org.bukkit.ChatColor.RED +
                    "Trinket effects reduced (" +
                    (int) ((1.0 - scaling) * 100) + "%) [" + severity + "]");
        }

        return scaling;
    }

    // -----------------------------------------------------------------------
    // Central entity checker
    // -----------------------------------------------------------------------

    public static void checkEntity(LivingEntity entity) {
        ItemStack[] items = getTrinketItems(entity);

        // FIX: use the lightweight counter to fill the cache rather than the
        // full applyTrinketScaling(), which does expensive lore rewrites.
        // applyTrinketScaling() is only called when the scaling value changes.
        int    trinketCount  = countActiveTrinkets(items);
        double freshScaling  = computeScaling(trinketCount);
        double prevScaling   = lastScaling.getOrDefault(entity.getUniqueId(), 1.0);

        if (freshScaling != prevScaling) {
            // Scaling changed — run the full path (particles, lore rewrite, etc.).
            freshScaling = applyTrinketScaling(entity, items);
        }

        cachedScaling      = freshScaling;
        scalingCacheActive = true;

        try {
            Anchor.checkTrinket(entity);
            Backpack.checkTrinket(entity);
            BlazingPower.checkTrinket(entity);
            BlockReachExtender.checkTrinket(entity);
            BloodSugar.checkTrinket(entity);
            DenseArmorPlating.checkTrinket(entity);
            EnergyFormula.checkTrinket(entity);
            MinersMight.checkTrinket(entity);
            SpiderEyeTotem.checkTrinket(entity);
            SwiftStrike.checkTrinket(entity);
            Compactor.checkTrinket(entity);
            EnchantedQuiver.checkTrinket(entity);
        } finally {
            scalingCacheActive = false;
            cachedScaling      = -1.0;
        }
    }

    public static JavaPlugin getPlugin()              { return plugin; }
    public static void removeEntity(LivingEntity entity) {
        lastScaling.remove(entity.getUniqueId());
    }

    // -----------------------------------------------------------------------
    // Events
    // -----------------------------------------------------------------------

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof LivingEntity le) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> checkEntity(le), 1L);
        }
    }

    @EventHandler
    public void onPlayerAttemptPickup(PlayerAttemptPickupItemEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkEntity(event.getPlayer()), 1L);
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity le) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> checkEntity(le), 1L);
        }
    }
}