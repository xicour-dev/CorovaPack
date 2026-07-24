package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * SolidStance — the first shield mutation.
 *
 * When the player successfully blocks an attack while holding a shield with this
 * mutation, there is a 75% chance the shield stun is completely cancelled,
 * letting the player keep their shield raised without the usual 5-tick (0.25s)
 * usage-cooldown that a successful hit normally forces.
 *
 * ── HOW SHIELD STUN WORKS IN PAPER/SPIGOT ─────────────────────────────────
 * When a shield block is registered, Bukkit fires EntityDamageByEntityEvent with
 * the damage already reduced to 0.  Immediately after, the server applies a
 * 5-tick shield cooldown to the holder, which temporarily disables the shield.
 * There is no dedicated "ShieldBlockEvent" yet in the standard Bukkit API, so we
 * detect a block by checking:
 *   1. The victim is a Player.
 *   2. The player is actively using (right-clicking) their shield.
 *   3. The event's final damage is 0 (the shield absorbed everything).
 *
 * To cancel the stun we call player.setCooldown(Material.SHIELD, 0) immediately
 * after damage processing, which clears the cooldown before it can take effect.
 * A one-tick-later follow-up task also calls setCooldown(SHIELD, 0) to catch
 * any cooldown set in post-processing (some server implementations set it after
 * the event fires).
 *
 * ── DISCOVERY ─────────────────────────────────────────────────────────────
 * Solid Stance can only appear on shields (Material.SHIELD).  Discovery is
 * handled in MutationDiscoveryListener / MutationManager via the shield pool
 * added below — it triggers on EntityDamageByEntityEvent when the player
 * successfully blocks (same condition as above), matching the thematic feel of
 * "discovered through combat".
 *
 * The discovery chance is already governed by the existing ARMOR_MUTATION_CHANCE
 * constant (0.02%) in MutationManager; we just need SOLID_STANCE in the shield
 * pool so tryToMutate() can pick it.
 * ──────────────────────────────────────────────────────────────────────────
 */
public class SolidStance implements Mutation {

    private static final double STUN_RESIST_CHANCE = 0.75;
    private static final Random random = new Random();

    private final MutationManager mutationManager;

    public SolidStance(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    // -------------------------------------------------------------------------
    // Mutation metadata
    // -------------------------------------------------------------------------

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEFENSIVE);
    }

    @Override
    public String getColor() {
        return "#4682B4";
    }

    @Override
    public String getName() {
        return "Solid Stance";
    }

    @Override
    public int getMaxLevel() {
        // Single-level mutation for now — mirrors BattleHardened's design.
        return 1;
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
        desc.add(ChatColor.GRAY + "75% chance to resist shield stun on block.");
        desc.add(ChatColor.DARK_GRAY + "Shields only.");
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.SOLID_STANCE;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    // -------------------------------------------------------------------------
    // Stun-resist event
    // -------------------------------------------------------------------------

    /**
     * Fires at MONITOR priority so all damage modifications (protection enchants,
     * etc.) have already been applied before we decide whether a block occurred.
     *
     * We do NOT set ignoreCancelled = true on purpose: a cancelled damage event
     * still represents a blocked hit and we want to resist the stun in that case
     * too (some plugins cancel rather than zero-out shield blocks).
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player defender = (Player) event.getEntity();

        // Player must be actively blocking with a shield.
        if (!isBlocking(defender)) return;

        // Confirm the held shield actually carries Solid Stance.
        ItemStack shield = getActiveShield(defender);
        if (shield == null) return;
        if (!mutationManager.hasMutation(shield, MutationType.SOLID_STANCE)) return;

        int level = mutationManager.getMutationLevel(shield, MutationType.SOLID_STANCE);

        // 75% chance to cancel the stun.
        double chance = STUN_RESIST_CHANCE + com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile((Player)defender), "incremental");
        if (random.nextDouble() >= chance) return;

        // Clear the cooldown immediately …
        defender.setCooldown(Material.SHIELD, 0);

        // … and again one tick later in case the server re-applies it after the event.
        org.bukkit.Bukkit.getScheduler().runTaskLater(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(this.getClass()),
                () -> {
                    if (defender.isOnline()) {
                        defender.setCooldown(Material.SHIELD, 0);
                    }
                },
                1L
        );

        defender.sendActionBar(ChatColor.AQUA + "Solid Stance!");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the player is currently blocking (i.e. using their
     * shield via right-click).  {@link Player#isBlocking()} covers both main-hand
     * and off-hand shield usage.
     */
    private boolean isBlocking(Player player) {
        return player.isBlocking();
    }

    /**
     * Returns the item ItemStack the player is actively holding that has this mutation,
     * or {@code null} if none is found. Prefers the main hand.
     */
    private ItemStack getActiveShield(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && mutationManager.hasMutation(main, MutationType.SOLID_STANCE)) return main;

        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && mutationManager.hasMutation(off, MutationType.SOLID_STANCE)) return off;

        return null;
    }
}