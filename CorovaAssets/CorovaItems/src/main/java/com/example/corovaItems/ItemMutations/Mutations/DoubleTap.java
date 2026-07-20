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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DoubleTap implements Mutation {

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> shotCounter = new HashMap<>();

    public DoubleTap(MutationManager mutationManager, JavaPlugin plugin) {
        this.mutationManager = mutationManager;
        this.plugin = plugin;
    }


    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.BURST);
    }

    @Override
    public String getColor() {
        return "#FF8C00";
    }

    @Override
    public String getName() {
        return "Double Tap";
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
            desc.add(ChatColor.GRAY + "Fires another shot after every other shot.");
        } else if (level == 2) {
            desc.add(ChatColor.GRAY + "Fires another shot after every shot.");
        }
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.DOUBLE_TAP;
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

        if (mutationManager.hasMutation(bowInHand, MutationType.DOUBLE_TAP)) {
            int level = mutationManager.getMutationLevel(bowInHand, MutationType.DOUBLE_TAP);
            UUID entityUUID = shooter.getUniqueId();
            int shots = shotCounter.getOrDefault(entityUUID, 0) + 1;

            boolean explode = MutationManager.getInstance().getSynergyHandler().hasExplosiveSynergy(bowInHand);

            double bonusProc = com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile((Player)shooter), "incremental");
            boolean procExtra = Math.random() < bonusProc;

            if ((level == 1 && shots % 2 == 0) || level == 2 || procExtra) {
                MutationUtils.scheduleArrowShot(plugin, shooter, originalArrow, 3L, explode);
                if (level == 1 && shots % 2 == 0) shotCounter.put(entityUUID, 0);
                else if (level != 2 && !procExtra) shotCounter.put(entityUUID, shots);
            } else {
                shotCounter.put(entityUUID, shots);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        shotCounter.remove(event.getPlayer().getUniqueId());
    }
}
