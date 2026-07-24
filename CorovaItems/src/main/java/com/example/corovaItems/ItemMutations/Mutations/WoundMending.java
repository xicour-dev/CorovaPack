package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class WoundMending implements Mutation {

    private final MutationManager mutationManager;
    private final Map<UUID, Long> lastDamageTime = new HashMap<>();
    private final Map<UUID, Integer> playerPieceCount = new HashMap<>();
    private final Map<UUID, Double> playerTotalDelayReduction = new HashMap<>();
    private final Map<UUID, Double> playerTotalRegenBonus = new HashMap<>();

    public WoundMending(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
        startRegenTask();
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.RECOVERY);
    }

    @Override
    public String getColor() {
        return "#FFC0CB";
    }

    public String getName() {
        return "Wound Mending";
    }

    public int getMaxLevel() {
        return 2;
    }

    public List<String> getLore(int level) {
        List<String> lore = new ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new ArrayList<>();
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Reduces natural regeneration delay by 0.375s,");
            desc.add(ChatColor.GRAY + "increases regen rate by 5%, and adds +400 Durability.");
        } else {
            desc.add(ChatColor.GRAY + "Reduces natural regeneration delay by 0.75s,");
            desc.add(ChatColor.GRAY + "increases regen rate by 10%, and adds +800 Durability.");
        }
        desc.add(ChatColor.DARK_GRAY + "Chainmail armor only.");
        return desc;
    }

    public MutationType getType() {
        return MutationType.WOUND_MENDING;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamageTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED
                && event.getRegainReason() != EntityRegainHealthEvent.RegainReason.REGEN) return;

        UUID uuid = player.getUniqueId();
        updatePlayerState(player);
        int count = playerPieceCount.getOrDefault(uuid, 0);
        if (count <= 0) return;

        long lastHit = lastDamageTime.getOrDefault(uuid, 0L);
        double totalReduction = playerTotalDelayReduction.getOrDefault(uuid, 0.0);
        double delaySeconds = 4.0 - totalReduction;
        long now = System.currentTimeMillis();

        if (now - lastHit < (long) (delaySeconds * 1000)) {
            event.setCancelled(true);
        } else {
            double bonus = playerTotalRegenBonus.getOrDefault(uuid, 0.0);
            event.setAmount(event.getAmount() * (1.0 + bonus));
            com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
            double amp = com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), profile, "recovery");
            event.setAmount(event.getAmount() * amp);
        }
    }

    private void startRegenTask() {
        new BukkitRunnable() {
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerEarlyRegen(player);
                }
            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(getClass()), 20L, 20L);
    }

    private void checkPlayerEarlyRegen(Player player) {
        UUID uuid = player.getUniqueId();
        updatePlayerState(player);
        int count = playerPieceCount.getOrDefault(uuid, 0);
        if (count <= 0) return;

        long lastHit = lastDamageTime.getOrDefault(uuid, 0L);
        double totalReduction = playerTotalDelayReduction.getOrDefault(uuid, 0.0);
        double delaySeconds = 4.0 - totalReduction;
        long now = System.currentTimeMillis();

        // If we are between our reduced delay and vanilla's 4s delay, we heal manually
        if (now - lastHit >= (long) (delaySeconds * 1000) && now - lastHit < 4000) {
            double currentHealth = player.getHealth();
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();

            if (currentHealth < maxHealth && player.getFoodLevel() >= 18) {
                double healAmount = 1.0;
                double bonus = playerTotalRegenBonus.getOrDefault(uuid, 0.0);
                healAmount *= (1.0 + bonus);
                com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
                double amp = com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), profile, "recovery");
                healAmount *= amp;

                player.setHealth(Math.min(maxHealth, currentHealth + healAmount));
                player.setExhaustion(player.getExhaustion() + 1.0f);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastDamageTime.remove(uuid);
        playerPieceCount.remove(uuid);
        playerTotalDelayReduction.remove(uuid);
        playerTotalRegenBonus.remove(uuid);
    }

    private void updatePlayerState(Player player) {
        int count = 0;
        double totalReduction = 0;
        double totalBonus = 0;
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        for (ItemStack armor : armorContents) {
            if (armor != null && mutationManager.hasMutation(armor, MutationType.WOUND_MENDING)) {
                count++;
                int lvl = mutationManager.getMutationLevel(armor, MutationType.WOUND_MENDING);
                if (lvl == 1) {
                    totalReduction += 0.375;
                    totalBonus += 0.05;
                } else if (lvl >= 2) {
                    totalReduction += 0.75;
                    totalBonus += 0.10;
                }
            }
        }
        playerPieceCount.put(player.getUniqueId(), count);
        playerTotalDelayReduction.put(player.getUniqueId(), totalReduction);
        playerTotalRegenBonus.put(player.getUniqueId(), totalBonus);
    }

    private void scheduleCheck(Player player) {
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(getClass()), () -> updatePlayerState(player), 1L);
    }

    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.WOUND_MENDING_WEIGHT;
    }

    @Override
    public int getDurabilityBonus(int level) {
        return level * 400;
    }
}
