package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Brimstone implements Mutation {

    private final MutationManager mutationManager;
    private final Map<UUID, Integer> slotCount = new HashMap<>();

    public Brimstone(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.ACCUMULATION, MutationCategory.BURST);
    }

    @Override
    public String getColor() {
        return "#800000";
    }

    public String getName() {
        return "Brimstone";
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
        desc.add(ChatColor.GRAY + "Taking damage fills slots (Max " + (level == 2 ? "6" : "3") + ").");
        desc.add(ChatColor.GRAY + "Dealing damage consumes a slot for bonus damage.");
        desc.add(ChatColor.GRAY + "Bonus per slot: 2.5% - 10% (scales with pieces).");
        desc.add(ChatColor.DARK_GRAY + "Netherite armor only.");
        return desc;
    }

    public MutationType getType() {
        return MutationType.BRIMSTONE;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        int count = 0;
        int maxSlots = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && mutationManager.hasMutation(armor, MutationType.BRIMSTONE)) {
                count++;
                int lvl = mutationManager.getMutationLevel(armor, MutationType.BRIMSTONE);
                maxSlots = Math.max(maxSlots, lvl == 2 ? 6 : 3);
            }
        }

        if (count > 0) {
            UUID uuid = player.getUniqueId();
            int current = slotCount.getOrDefault(uuid, 0);
            if (current < maxSlots) {
                slotCount.put(uuid, current + 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        UUID uuid = player.getUniqueId();
        int currentSlots = slotCount.getOrDefault(uuid, 0);
        if (currentSlots <= 0) return;

        int pieceCount = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && mutationManager.hasMutation(armor, MutationType.BRIMSTONE)) {
                pieceCount++;
            }
        }

        if (pieceCount > 0) {
            // 1 piece = 1.25%, 4 pieces = 5%
            double bonusPerSlot = 0.0125 + (0.0375 * (pieceCount - 1) / 3.0);

            // Apply Gold trim accumulation amplification
            com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
            bonusPerSlot = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, bonusPerSlot, "accumulation");

            double multiplier = 1.0 + (currentSlots * bonusPerSlot);
            event.setDamage(event.getDamage() * multiplier);

            slotCount.put(uuid, currentSlots - 1);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        slotCount.remove(event.getPlayer().getUniqueId());
    }

    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.BRIMSTONE_WEIGHT;
    }
}
