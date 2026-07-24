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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Set;

public class BackStab implements Mutation {

    private final MutationManager mutationManager;

    public BackStab(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.CONDITIONAL);
    }

    @Override
    public String getColor() {
        return "#8B0000";
    }

    @Override
    public String getName() {
        return "Backstab";
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
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Deals 10% bonus damage when hitting");
            desc.add(ChatColor.GRAY + "a target from behind.");
        } else {
            desc.add(ChatColor.GRAY + "Deals 20% bonus damage when hitting");
            desc.add(ChatColor.GRAY + "a target from behind.");
        }
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.BACKSTAB;
    }



    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        double conditionBonus = 0.0;
        if (damager instanceof Player player) {
            PlayerTrimProfile profile = TrimManager.getInstance().getProfile(player);
            conditionBonus = TrimCalculator.getAmplification(getCategories(), profile, "condition");
        }

        if (isBehind(damager, victim, conditionBonus)) {
            double multiplier = (level == 1) ? 1.1 : 1.2;
            if (damager instanceof Player player) {
                multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "window");
            }
            event.setDamage(event.getDamage() * multiplier);

            if (damager instanceof Player player) {
                player.sendActionBar(ChatColor.RED + "§lBackstab!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.5f);
            }
        }
    }

    private boolean isBehind(LivingEntity damager, LivingEntity victim, double conditionBonus) {
        // Vector from victim to damager
        Vector victimToDamager = damager.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (victimToDamager.lengthSquared() == 0) return false;
        victimToDamager.normalize();

        // Victim's forward direction
        Vector victimDir = victim.getLocation().getDirection().setY(0).normalize();

        // If the dot product of victimDir and victimToDamager is negative,
        // it means the damager is behind the victim.
        double threshold = -0.6 + conditionBonus;
        return victimDir.dot(victimToDamager) < threshold;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }
}
