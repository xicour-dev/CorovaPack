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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TripleTap implements Mutation {

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;

    public TripleTap(MutationManager mutationManager, JavaPlugin plugin) {
        this.mutationManager = mutationManager;
        this.plugin = plugin;
    }


    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.BURST);
    }

    @Override
    public String getColor() {
        return "#FF4500";
    }

    @Override
    public String getName() {
        return "Triple Tap";
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return MutationUtils.isBow(item);
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
            desc.add(ChatColor.GRAY + "Fires two extra shots after shooting.");
        }
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public MutationType getType() {
        return MutationType.TRIPLE_TAP;
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

        if (mutationManager.hasMutation(bowInHand, MutationType.TRIPLE_TAP)) {
            boolean explode = MutationManager.getInstance().getSynergyHandler().hasExplosiveSynergy(bowInHand);
            MutationUtils.scheduleArrowShot(plugin, shooter, originalArrow, 3L, explode);
            MutationUtils.scheduleArrowShot(plugin, shooter, originalArrow, 6L, explode);

            double bonusProc = com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile((Player)shooter), "incremental");
            if (Math.random() < bonusProc) {
                MutationUtils.scheduleArrowShot(plugin, shooter, originalArrow, 9L, explode);
            }
        }
    }
}
