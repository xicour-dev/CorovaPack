package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Splinter implements Mutation, Mutation.BuildUpMutation {

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private static final String METADATA_HITS = "SplinterHits";
    private static final String METADATA_LEVEL = "SplinterLevel";
    public Splinter(MutationManager mutationManager, JavaPlugin plugin) {
        this.mutationManager = mutationManager;
        this.plugin = plugin;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEBUFF, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#DEB887";
    }

    @Override
    public String getName() {
        return "Splinter";
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public List<String> getLore(int level) {
        List<String> lore = new java.util.ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new java.util.ArrayList<>();
        int hits = getRequiredHits(level, null, null, 0.0);
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Make targets vulnerable after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Next 3 hits deal 1.3x weapon damage.");
            desc.add(ChatColor.GRAY + "Adds +751 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Wooden weapons only.");
        } else {
            desc.add(ChatColor.GRAY + "Make targets vulnerable after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Next 3 hits deal 1.6x weapon damage.");
            desc.add(ChatColor.GRAY + "Adds +1502 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Wooden weapons only.");
        }
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public int getDurabilityBonus(int level) {
        if (level == 1) return 751;
        if (level == 2) return 1502;
        return 0;
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 1.0 + level;
    }

    @Override
    public MutationType getType() {
        return MutationType.SPLINTER;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 15 : 10;
        return Math.max(1, (int) Math.round(baseThreshold * (1.0 - thresholdReduction)));
    }

    @Override
    public boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc, double thresholdReduction) {
        if (MutationUtils.isFriendly(user, victim)) return false;
        int required = getRequiredHits(level, item, user, thresholdReduction);
        int count = hitCounter.getOrDefault(damagerId, 0) + 1;
        if (count >= required) {
            if (canProc) {
                hitCounter.put(damagerId, 0);
                return true;
            }
            hitCounter.put(damagerId, required);
            return false;
        }
        hitCounter.put(damagerId, count);
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        victim.setMetadata(METADATA_HITS, new FixedMetadataValue(plugin, 3));
        victim.setMetadata(METADATA_LEVEL, new FixedMetadataValue(plugin, level));
        if (damager instanceof Player player) {
            player.sendActionBar(ChatColor.YELLOW + "You splintered " + victim.getName() + "'s defense! (3 hits of bonus damage)");
        }
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Handle Vulnerability check
        if (event.getEntity().hasMetadata(METADATA_HITS)) {
            int level = event.getEntity().getMetadata(METADATA_LEVEL).get(0).asInt();
            int remaining = event.getEntity().getMetadata(METADATA_HITS).get(0).asInt();

            // event.getDamage() already accounts for all enchantments (Sharpness, Smite, Bane, Power, etc.)
            // Multiplies the total current damage.
            double multiplier = (level == 1) ? 1.3 : 1.6;

            if (event.getDamager() instanceof Player player) {
                multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "duration");
            }

            event.setDamage(event.getDamage() * multiplier);

            remaining--;
            event.getEntity().getWorld().playSound(event.getEntity().getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.5f);

            if (remaining <= 0) {
                event.getEntity().removeMetadata(METADATA_HITS, plugin);
                event.getEntity().removeMetadata(METADATA_LEVEL, plugin);
            } else {
                event.getEntity().setMetadata(METADATA_HITS, new FixedMetadataValue(plugin, remaining));
            }
        }
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Synergy: Single hit of vulnerability (1.2x damage on next hit)
        victim.setMetadata(METADATA_HITS, new FixedMetadataValue(plugin, 1));
        victim.setMetadata(METADATA_LEVEL, new FixedMetadataValue(plugin, level));
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.0f);
    }
}
