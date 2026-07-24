package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ArrowVelocity implements Mutation {

    private final MutationManager mutationManager;

    public ArrowVelocity(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.ENCHANT_SYNERGY, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#ADD8E6";
    }

    @Override
    public String getName() {
        return "Arrow Velocity";
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
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
            desc.add(ChatColor.GRAY + "Increases arrow speed by 40%.");
        } else if (level == 2) {
            desc.add(ChatColor.GRAY + "Increases arrow speed by 80%.");
        }
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.ARROW_VELOCITY;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) || !(event.getProjectile() instanceof Arrow)) {
            return;
        }

        LivingEntity shooter = (LivingEntity) event.getEntity();
        Arrow originalArrow = (Arrow) event.getProjectile();
        ItemStack bow = event.getBow();

        if (bow == null || bow.getType() == Material.AIR) {
            return;
        }

        ItemStack bowInHand = MutationUtils.getBowInHand(shooter, bow);
        if (bowInHand == null) {
            return;
        }

        if (mutationManager.hasMutation(bowInHand, MutationType.ARROW_VELOCITY)) {
            int level = mutationManager.getMutationLevel(bowInHand, MutationType.ARROW_VELOCITY);
            Vector velocity = originalArrow.getVelocity();
            double multiplier = (level == 1) ? 1.4 : 1.8;

            if (shooter instanceof Player player) {
                multiplier += com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile((Player)shooter), "incremental");
            }

            originalArrow.setVelocity(velocity.multiply(multiplier));
        }
    }
}
