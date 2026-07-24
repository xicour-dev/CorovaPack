package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

public class LastStand implements Mutation {

    private final MutationManager mutationManager;

    // Base chance at 50% health
    private static final double BASE_CHANCE = 0.05;

    // Maximum bonus chance added at near-zero health, per level
    private static final double MAX_BONUS_LVL1 = 0.10;
    private static final double MAX_BONUS_LVL2 = 0.15;

    public LastStand(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.CONDITIONAL, MutationCategory.HEALTH_SCALE);
    }

    @Override
    public String getColor() {
        return "#006400";
    }

    @Override
    public String getName() {
        return "Last Stand";
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
        double minChance = BASE_CHANCE;
        double maxChance = BASE_CHANCE + (level == 1 ? MAX_BONUS_LVL1 : MAX_BONUS_LVL2);

        int minPct = (int) Math.round(minChance * 100);
        int maxPct = (int) Math.round(maxChance * 100);

        String damageMultMin = (level == 1) ? "1.0x" : "1.0x";
        String damageMultMax = (level == 1) ? "1.2x" : "1.3x";

        List<String> desc = new java.util.ArrayList<>();
        desc.add(ChatColor.GRAY + "Proc chance scales with missing health.");
        desc.add(ChatColor.GRAY + "Chance: " + ChatColor.YELLOW + minPct + "%" + ChatColor.GRAY + " (50% HP) → " + ChatColor.YELLOW + maxPct + "%" + ChatColor.GRAY + " (near death)");
        desc.add(ChatColor.GRAY + "Bonus damage: " + ChatColor.YELLOW + damageMultMin + ChatColor.GRAY + " → " + ChatColor.YELLOW + damageMultMax + ChatColor.GRAY + " (scales with missing HP)");
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.LAST_STAND;
    }


    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        double maxHealth = damager.getAttribute(Attribute.MAX_HEALTH).getValue();
        double currentHealth = damager.getHealth();
        double missingHealthPercent = Math.max(0.0, 1.0 - (currentHealth / (maxHealth / 2.0)));

        // Bonus damage scales with missing health too
        double multiplier = 1.0 + (missingHealthPercent * (level == 1 ? 0.2 : 0.3));
        if (damager instanceof Player player) {
            multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "window");
        }
        event.setDamage(event.getDamage() * multiplier);

        if (damager instanceof Player player) {
            // Show current proc chance so the player knows how close to the edge they are
            double currentChance = 0.0;
            int currentPct = (int) Math.round(currentChance * 100);

            player.sendActionBar(ChatColor.GREEN + "§lLast Stand! " + ChatColor.GRAY + "(Proc chance: " + ChatColor.YELLOW + currentPct + "%" + ChatColor.GRAY + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.6f, 1.2f);
        }

        victim.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, victim.getLocation().add(0, 1.5, 0), 5);
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }
}