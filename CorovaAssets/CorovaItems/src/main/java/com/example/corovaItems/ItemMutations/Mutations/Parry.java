package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;

public class Parry implements Mutation, Mutation.BuildUpMutation {

    private final MutationManager mutationManager;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    // Window duration in ticks (20 ticks = 1 second)
    private static final long WINDOW_TICKS_LVL1 = 60L;  // 3 seconds
    private static final long WINDOW_TICKS_LVL2 = 100L; // 5 seconds

    // How long the attack speed debuff lasts after a successful parry
    private static final long DEBUFF_DURATION_TICKS = 60L; // 3 seconds

    // Tracks which players currently have an active parry window open
    // Key = Player UUID, Value = mutation level that opened the window
    private final Map<UUID, Integer> activeParryWindows = new HashMap<>();
    private final NamespacedKey debuffKeyLvl1;
    private final NamespacedKey debuffKeyLvl2;

    public Parry(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
        this.debuffKeyLvl1 = new NamespacedKey(mutationManager.getPlugin(), "parry_debuff_lvl1");
        this.debuffKeyLvl2 = new NamespacedKey(mutationManager.getPlugin(), "parry_debuff_lvl2");
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEFENSIVE, MutationCategory.CONDITIONAL);
    }

    @Override
    public String getColor() {
        return "#708090";
    }

    public String getName() {
        return "Parry";
    }

    public int getMaxLevel() {
        return 2;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }

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
            desc.add(ChatColor.GRAY + "Open a 3s parry window after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Block while the window is active to parry,");
            desc.add(ChatColor.GRAY + "reducing incoming damage by 25% and");
            desc.add(ChatColor.GRAY + "the attacker's speed by 15% for 3s.");
        } else {
            desc.add(ChatColor.GRAY + "Open a 5s parry window after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Block while the window is active to parry,");
            desc.add(ChatColor.GRAY + "reducing incoming damage by 25% and");
            desc.add(ChatColor.GRAY + "the attacker's speed by 30% for 3s.");
        }
        return desc;
    }

    public MutationType getType() {
        return MutationType.PARRY;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 10 : 6;
        return Math.max(1, (int) Math.round(baseThreshold * (1.0 - thresholdReduction)));
    }

    @Override
    public boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc, double thresholdReduction) {
        if (MutationUtils.isFriendly(user, victim)) return false;
        int required = getRequiredHits(level, item, user, thresholdReduction);
        int count = hitCounter.getOrDefault(damagerId, 0) + 1;
        if (count >= required) {
            if (canProc && !activeParryWindows.containsKey(damagerId)) {
                hitCounter.put(damagerId, 0);
                return true;
            }
            hitCounter.put(damagerId, required);
            return false;
        }
        hitCounter.put(damagerId, count);
        return false;
    }

    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        if (damager instanceof Player player) {
            openParryWindow(player, level, mutationManager.getPlugin());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIncomingDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        LivingEntity attacker = (event.getDamager() instanceof LivingEntity) ? (LivingEntity) event.getDamager() : null;

        // Try to execute parry first if window is open
        if (attacker != null) {
            if (tryExecuteParry(victim, attacker, mutationManager.getPlugin())) {
                event.setDamage(event.getDamage() * 0.75); // Reduce damage by 25% on successful parry
                return;
            }
        }

        // If not parried, check if we should open a new window when taking damage
        ItemStack item = victim.getInventory().getItemInMainHand();
        if (MutationManager.getInstance().hasMutation(item, MutationType.PARRY)) {
            int level = MutationManager.getInstance().getMutationLevel(item, MutationType.PARRY);
            // Buccaneer signature: damagerId, victim, level, item, user
            // When taking damage, the 'user' is the one being hit (victim).
            // We use the attacker as the 'victim' for team checks.
            if (incrementAndCheck(victim.getUniqueId(), attacker, level, item, victim, true)) {
                openParryWindow(victim, level, mutationManager.getPlugin());
            }
        }
    }

    /**
     * Call this from your event handler whenever the player either deals or receives damage
     * and the random roll passes getProcChance(). This opens the parry window.
     *
     * @param player The player who owns the Parry mutation.
     * @param level  The mutation level (1 or 2).
     * @param plugin Your plugin instance, needed to schedule the window expiry task.
     */
    public void openParryWindow(Player player, int level, Plugin plugin) {
        UUID playerId = player.getUniqueId();

        // Don't reset an already-active window — let the current one play out
        if (activeParryWindows.containsKey(playerId)) return;

        activeParryWindows.put(playerId, level);

        player.sendActionBar(ChatColor.GOLD + "✦ Parry window open! " + ChatColor.GRAY + "Block to parry the next hit.");

        long windowTicks = (level == 1) ? WINDOW_TICKS_LVL1 : WINDOW_TICKS_LVL2;
        windowTicks = (long) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, (double) windowTicks, "window");

        new BukkitRunnable() {
            public void run() {
                // Only send expiry message if the window wasn't already consumed by a successful parry
                if (activeParryWindows.remove(playerId) != null) {
                    player.sendActionBar(ChatColor.GRAY + "Parry window expired.");
                }
            }
        }.runTaskLater(plugin, windowTicks);
    }

    /**
     * Call this from your incoming damage event handler (EntityDamageByEntityEvent)
     * BEFORE normal damage is applied. Checks whether this hit should be parried.
     *
     * Returns true if the parry executed successfully — you should cancel or
     * reduce the damage event as desired when this returns true.
     *
     * @param player   The defending player who may have an active parry window.
     * @param attacker The entity that landed the hit.
     * @param plugin   Your plugin instance, needed to schedule the debuff removal task.
     * @return true if parry was successfully executed, false otherwise.
     */
    public boolean tryExecuteParry(Player player, LivingEntity attacker, Plugin plugin) {
        UUID playerId = player.getUniqueId();

        // No active window — nothing to parry
        if (!activeParryWindows.containsKey(playerId)) return false;

        // Player must currently be blocking with their sword
        if (!player.isBlocking()) return false;

        int level = activeParryWindows.remove(playerId);

        applySpeedDebuff(player, attacker, level, plugin);

        player.sendActionBar(ChatColor.GOLD + "§l⚔ Parried!");

        return true;
    }

    /**
     * Returns true if the given player currently has an active parry window.
     * Useful if you want to show a visual indicator (e.g. action bar) elsewhere.
     */
    public boolean hasActiveWindow(Player player) {
        return activeParryWindows.containsKey(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void applySpeedDebuff(Player defender, LivingEntity attacker, int level, Plugin plugin) {
        var attackSpeedAttr = attacker.getAttribute(Attribute.ATTACK_SPEED);
        if (attackSpeedAttr == null) return;

        double reductionAmount = (level == 1) ? -0.15 : -0.30;

        // Note: applyTrimAmplification multiplies. For a negative reduction like -0.25 (which means 75% speed),
        // multiplying it by 1.15 (iron trim) would make it -0.2875, which is more reduction.
        // Actually, if it's a DEBUFF, "duration" amplification might be more appropriate for the duration of the debuff.
        // But the instructions say "identify the single number that most represents the mutation's core behavior".
        // Here it's the reduction amount or the duration.
        // Let's amplify the reduction amount.
        reductionAmount = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(defender, this, reductionAmount, "duration");

        NamespacedKey key = (level == 1) ? debuffKeyLvl1 : debuffKeyLvl2;

        // Remove any existing debuff first to reset the duration
        attackSpeedAttr.removeModifier(key);

        AttributeModifier debuff = new AttributeModifier(
                key,
                reductionAmount,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                EquipmentSlotGroup.ANY
        );

        attackSpeedAttr.addModifier(debuff);

        new BukkitRunnable() {
            public void run() {
                var attr = attacker.getAttribute(Attribute.ATTACK_SPEED);
                if (attr != null) {
                    attr.removeModifier(key);
                }
            }
        }.runTaskLater(plugin, DEBUFF_DURATION_TICKS);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        hitCounter.remove(uuid);
        activeParryWindows.remove(uuid);
    }

    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }
}