package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

public class LifeSteal implements Mutation {

    private final MutationManager mutationManager;

    public LifeSteal(MutationManager mutationManager, JavaPlugin plugin) {
        this.mutationManager = mutationManager;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEFENSIVE, MutationCategory.RECOVERY);
    }

    @Override
    public String getColor() {
        return "#8B0000";
    }

    @Override
    public String getName() {
        return "Life Steal";
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
            desc.add(ChatColor.GRAY + "Recover 5% of an enemy's max health (max 1 heart) upon kill.");
        } else {
            desc.add(ChatColor.GRAY + "Recover 10% of an enemy's max health (max 2 hearts) upon kill.");
        }
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.LIFE_STEAL;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        LivingEntity killer = victim.getKiller();

        if (killer == null) {
            if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                if (((EntityDamageByEntityEvent) victim.getLastDamageCause()).getDamager() instanceof LivingEntity) {
                    killer = (LivingEntity) ((EntityDamageByEntityEvent) victim.getLastDamageCause()).getDamager();
                }
            }
        }

        if (killer == null) return;

        ItemStack weapon = killer.getEquipment() != null ? killer.getEquipment().getItemInMainHand() : null;
        if (weapon == null || weapon.getType().isAir()) return;

        if (mutationManager.hasMutation(weapon, MutationType.LIFE_STEAL)) {
            if (killer instanceof Player && victim instanceof Player && com.example.corovateams.CorovaTeams.getInstance() != null) {
                com.example.corovateams.TeamManager tm = com.example.corovateams.CorovaTeams.getInstance().getTeamManager();
                com.example.corovateams.CorovaTeam damagerTeam = tm.getTeamByPlayer(killer.getUniqueId());
                com.example.corovateams.CorovaTeam victimTeam = tm.getTeamByPlayer(victim.getUniqueId());
                if (damagerTeam != null && damagerTeam.equals(victimTeam) && !damagerTeam.hasFriendlyFire()) {
                    return;
                }
            }

            int level = mutationManager.getMutationLevel(weapon, MutationType.LIFE_STEAL);
            double victimMaxHealth = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
            double healAmount = (level == 1) ? victimMaxHealth * 0.05 : victimMaxHealth * 0.10;

            double maxHealAmount = (level == 1) ? 2.0 : 4.0; // 1 heart for level I, 2 hearts for level II
            if (healAmount > maxHealAmount) {
                healAmount = maxHealAmount;
            }

            double currentHealth = killer.getHealth();
            double maxHealth = killer.getAttribute(Attribute.MAX_HEALTH).getValue();
            healAmount = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification((Player)killer, this, healAmount, "recovery");
            killer.setHealth(Math.min(currentHealth + healAmount, maxHealth));

            if (killer instanceof Player) {
                ((Player) killer).sendActionBar(ChatColor.RED + "You stole " + String.format("%.1f", healAmount) + " HP from " + victim.getName() + "!");
            }
        }
    }
}
