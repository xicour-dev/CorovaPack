package com.example.corovaItems.ItemMutations;

import com.example.corovaItems.WeaponProperties.DualWielding;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

public class MutationDiscoveryListener implements Listener {
    private final MutationManager mutationManager;
    private final java.util.Map<java.util.UUID, Long> lastActiveMutationTick = new java.util.HashMap<>();
    private final java.util.Random random = new java.util.Random();

    public MutationDiscoveryListener(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Mutation restrictions (Parry, Fear, Dice) are enforced in MutationManager.tryToMutate
        if (event.getDamager() instanceof Player player) {

            // Bug fix #2: During an off-hand attack, DualWielding temporarily swaps the
            // two hand slots so that player.attack() reads attributes from the main-hand
            // slot. While swapped, getItemInMainHand() returns the off-hand sword and
            // getItemInOffHand() returns the real main-hand weapon. If we checked the
            // off-hand slot here as usual, any mutation would be applied to the main-hand
            // weapon and the player would see it land on the wrong item.
            //
            // Fix: when an off-hand attack is in progress, only check the main-hand slot
            // (which holds the temporarily-swapped off-hand sword). DualWielding's refresh
            // step already reads the updated item back from the main-hand slot and returns
            // it to the off-hand after the attack completes, so the mutation ends up on
            // the correct item.
            boolean isOffHandAttack = DualWielding.offHandAttackInProgress.contains(player.getUniqueId());

            long currentTick = player.getWorld().getFullTime();
            if (lastActiveMutationTick.getOrDefault(player.getUniqueId(), -1L) != currentTick) {
                lastActiveMutationTick.put(player.getUniqueId(), currentTick);

                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (mainHand != null && mainHand.getType() != Material.AIR) {
                    if (this.handleMutationAttempt(player, mainHand)) {
                        player.getInventory().setItemInMainHand(mainHand);
                    }
                }

                // Skip the off-hand slot check while an off-hand attack is in progress.
                // The off-hand slot temporarily holds the real main-hand weapon during the
                // swap, so checking it would give that weapon an unintended mutation chance.
                if (!isOffHandAttack) {
                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    if (offHand != null && offHand.getType() != Material.AIR) {
                        if (this.handleMutationAttempt(player, offHand)) {
                            player.getInventory().setItemInOffHand(offHand);
                        }
                    }
                }
            }
        }

        if (event.getEntity() instanceof Player victim) {
            ItemStack[] armor = victim.getInventory().getArmorContents();
            int slotToCheck = random.nextInt(4);
            ItemStack piece = armor[slotToCheck];
            if (piece != null && piece.getType() != Material.AIR) {
                if (this.handleMutationAttempt(victim, piece)) {
                    victim.getInventory().setArmorContents(armor);
                }
            }

            // Check hands for shields (SolidStance mutation)
            ItemStack mainHand = victim.getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.getType() == Material.SHIELD) {
                if (this.handleMutationAttempt(victim, mainHand)) {
                    victim.getInventory().setItemInMainHand(mainHand);
                }
            }
            ItemStack offHand = victim.getInventory().getItemInOffHand();
            if (offHand != null && offHand.getType() == Material.SHIELD) {
                if (this.handleMutationAttempt(victim, offHand)) {
                    victim.getInventory().setItemInOffHand(offHand);
                }
            }
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            long currentTick = player.getWorld().getFullTime();
            if (lastActiveMutationTick.getOrDefault(player.getUniqueId(), -1L) == currentTick) return;
            lastActiveMutationTick.put(player.getUniqueId(), currentTick);

            ItemStack bow = event.getBow();
            if (bow == null || bow.getType() == Material.AIR) return;

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            if (mainHand != null && mainHand.isSimilar(bow)) {
                if (this.handleMutationAttempt(player, mainHand)) {
                    player.getInventory().setItemInMainHand(mainHand);
                }
                return;
            }

            ItemStack offHand = player.getInventory().getItemInOffHand();
            if (offHand != null && offHand.isSimilar(bow)) {
                if (this.handleMutationAttempt(player, offHand)) {
                    player.getInventory().setItemInOffHand(offHand);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        long currentTick = player.getWorld().getFullTime();
        if (lastActiveMutationTick.getOrDefault(player.getUniqueId(), -1L) == currentTick) return;
        lastActiveMutationTick.put(player.getUniqueId(), currentTick);

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && (item.getType().name().endsWith("_PICKAXE") || item.getType().name().endsWith("_HOE"))) {
            if (this.handleMutationAttempt(player, item)) {
                player.getInventory().setItemInMainHand(item);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastActiveMutationTick.remove(event.getPlayer().getUniqueId());
    }

    private boolean handleMutationAttempt(Player player, ItemStack item) {
        boolean result = this.mutationManager.tryToMutate(item, player);
        if (result) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.4F, 0.8F);
            String itemName = (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();

            java.util.Map<MutationType, Integer> mutations = this.mutationManager.getMutations(item);
            if (!mutations.isEmpty()) {
                MutationType lastType = null;
                for (MutationType type : mutations.keySet()) {
                    lastType = type;
                }
                Mutation m = this.mutationManager.getMutation(lastType);
                if (m != null) {
                    ChatColor mColor = ChatColor.of(m.getColor());
                    player.sendActionBar(ChatColor.GREEN + itemName + ChatColor.GRAY + " has mutated into " + mColor + m.getName() + ChatColor.GRAY + "!");
                    return true;
                }
            }

            player.sendActionBar("§a" + itemName + " §7has mutated!");
            return true;
        }
        return false;
    }
}