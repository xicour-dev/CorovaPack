package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;

public class BreakThrough implements Mutation, Mutation.BuildUpMutation {

    private final MutationManager mutationManager;
    private final Plugin plugin;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    // Debuff strength per level
    private static final double ARMOR_REDUCTION_LVL1     = 0.20;
    private static final double ARMOR_REDUCTION_LVL2     = 0.30;
    private static final double TOUGHNESS_REDUCTION_LVL1 = 0.20;
    private static final double TOUGHNESS_REDUCTION_LVL2 = 0.30;

    // Duration per level in ticks (20 ticks = 1 second)
    private static final long DURATION_TICKS_LVL1 = 100L; // 5 seconds
    private static final long DURATION_TICKS_LVL2 = 200L; // 10 seconds

    // Proc chances per level
    private static final double INCREMENTAL_LVL1 = 0.04;
    private static final double INCREMENTAL_LVL2 = 0.08;

    // The four armor slots in order — used for iteration and random fallback.
    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    );

    // NamespacedKeys for each armor slot's armor and toughness modifier.
    // One fixed key per slot means modifiers can never stack on the same piece.
    private final Map<EquipmentSlot, NamespacedKey> armorKeys    = new EnumMap<>(EquipmentSlot.class);
    private final Map<EquipmentSlot, NamespacedKey> toughnessKeys = new EnumMap<>(EquipmentSlot.class);

    // Tracks which armor slots are currently weakened per victim.
    // Key = victim UUID, Value = set of EquipmentSlots currently debuffed.
    private final Map<UUID, Set<EquipmentSlot>> weakenedSlots = new ConcurrentHashMap<>();

    private static final Random RANDOM = new Random();

    public BreakThrough(MutationManager mutationManager, Plugin plugin) {
        this.mutationManager = mutationManager;
        this.plugin = plugin;

        // Build one NamespacedKey per slot per attribute up front
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            String slotName = slot.name().toLowerCase();
            armorKeys.put(slot,     new NamespacedKey(plugin, "breakthrough_armor_"     + slotName));
            toughnessKeys.put(slot, new NamespacedKey(plugin, "breakthrough_toughness_" + slotName));
        }
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEBUFF);
    }

    @Override
    public String getColor() {
        return "#4682B4";
    }

    @Override
    public String getName() {
        return "Break Through";
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
        int hits = getRequiredHits(level, null, null, 0.0);
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Deal 1.4x damage and temporarily weaken");
            desc.add(ChatColor.GRAY + "target's armor by 20% for 5s after " + hits + " hits.");
        } else {
            desc.add(ChatColor.GRAY + "Deal 1.4x damage and temporarily weaken");
            desc.add(ChatColor.GRAY + "target's armor by 30% for 10s after " + hits + " hits.");
        }
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.BREAK_THROUGH;
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
            if (canProc) {
                hitCounter.put(damagerId, 0);
                return true;
            }
            hitCounter.put(damagerId, required);
            return false;
        }
        hitCounter.put(damagerId, count);
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        // --- 1. Always apply the 1.4x damage multiplier ---
        double multiplier = 1.4;
        if (damager instanceof Player player) {
            multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "window");
        }
        event.setDamage(event.getDamage() * multiplier);

        if (damager instanceof Player player) {
            player.sendActionBar(ChatColor.DARK_AQUA + "§lBreak Through! " + ChatColor.GRAY + "§e(1.4x Damage)");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.2f);
        }

        // --- 2. Attempt armor weakening if target has armor ---
        EntityEquipment equipment = victim.getEquipment();
        if (equipment == null) return;

        List<EquipmentSlot> armoredSlots = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack piece = getArmorPiece(equipment, slot);
            if (piece != null && !piece.getType().isAir()) {
                armoredSlots.add(slot);
            }
        }

        if (armoredSlots.isEmpty()) return; // No armor — 2x damage is all we do.

        Set<EquipmentSlot> alreadyWeakened = weakenedSlots.computeIfAbsent(
                victim.getUniqueId(), id -> ConcurrentHashMap.newKeySet()
        );

        // --- 3. Resolve which slot to break through ---
        EquipmentSlot targetSlot = resolveTargetSlot(damager, victim, armoredSlots, alreadyWeakened);

        if (targetSlot == null) {
            // All armored slots are already weakened — nothing more to do.
            return;
        }

        // --- 4. Apply the weakening ---
        applyArmorWeakening(damager, victim, targetSlot, level, alreadyWeakened);
    }

    // -------------------------------------------------------------------------
    // Slot resolution
    // -------------------------------------------------------------------------

    private EquipmentSlot resolveTargetSlot(
            LivingEntity damager,
            LivingEntity victim,
            List<EquipmentSlot> armoredSlots,
            Set<EquipmentSlot> alreadyWeakened
    ) {
        EquipmentSlot preferred = guessHitSlot(damager, victim);

        if (preferred != null
                && armoredSlots.contains(preferred)
                && !alreadyWeakened.contains(preferred)) {
            return preferred;
        }

        // Preferred slot unavailable — pick any un-weakened armored slot at random
        List<EquipmentSlot> available = new ArrayList<>(armoredSlots);
        available.removeAll(alreadyWeakened);

        if (available.isEmpty()) return null;
        return available.get(RANDOM.nextInt(available.size()));
    }

    /**
     * Guesses the hit slot from the attacker's eye height relative to the victim's
     * bounding box. 0.0 = victim's feet, 1.0 = victim's head.
     */
    private EquipmentSlot guessHitSlot(LivingEntity damager, LivingEntity victim) {
        double damagerEyeY = damager.getEyeLocation().getY();
        double victimFeetY = victim.getLocation().getY();
        double victimHeight = victim.getHeight();

        double relativeHit = (damagerEyeY - victimFeetY) / victimHeight;
        relativeHit = Math.max(0.0, Math.min(1.0, relativeHit));

        if (relativeHit >= 0.80) return EquipmentSlot.HEAD;
        if (relativeHit >= 0.55) return EquipmentSlot.CHEST;
        if (relativeHit >= 0.30) return EquipmentSlot.LEGS;
        return EquipmentSlot.FEET;
    }

    // -------------------------------------------------------------------------
    // Armor weakening
    // -------------------------------------------------------------------------

    private void applyArmorWeakening(
            LivingEntity damager,
            LivingEntity victim,
            EquipmentSlot slot,
            int level,
            Set<EquipmentSlot> alreadyWeakened
    ) {
        double armorReduction     = (level == 1) ? ARMOR_REDUCTION_LVL1     : ARMOR_REDUCTION_LVL2;
        double toughnessReduction = (level == 1) ? TOUGHNESS_REDUCTION_LVL1 : TOUGHNESS_REDUCTION_LVL2;
        long   durationTicks      = (level == 1) ? DURATION_TICKS_LVL1      : DURATION_TICKS_LVL2;

        if (damager instanceof Player player) {
            durationTicks = (long) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, (double) durationTicks, "duration");
        }

        NamespacedKey armorKey     = armorKeys.get(slot);
        NamespacedKey toughnessKey = toughnessKeys.get(slot);

        // Apply armor debuff — remove first so we never double-apply
        // Check for trim amplification on duration
        // We don't have the damager here, but applyArmorWeakening is called from onProc which has it.
        // Wait, applyArmorWeakening signature doesn't include the player.
        // Let's modify applyArmorWeakening to accept the amplifier or the player.
        // Or just apply it before calling.

        AttributeInstance armorAttr = victim.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.removeModifier(armorKey);
            armorAttr.addModifier(new AttributeModifier(armorKey, -armorReduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }

        // Apply toughness debuff
        AttributeInstance toughnessAttr = victim.getAttribute(Attribute.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) {
            toughnessAttr.removeModifier(toughnessKey);
            toughnessAttr.addModifier(new AttributeModifier(toughnessKey, -toughnessReduction, AttributeModifier.Operation.MULTIPLY_SCALAR_1, org.bukkit.inventory.EquipmentSlotGroup.ANY));
        }

        alreadyWeakened.add(slot);

        // Armor crack sound at the victim — lower pitch for a heavy, chunky feel
        victim.getWorld().playSound(
                victim.getLocation(),
                Sound.ENTITY_ITEM_BREAK,
                0.8f,
                0.5f
        );

        if (victim instanceof Player victimPlayer) {
            victimPlayer.sendActionBar(ChatColor.DARK_AQUA + "Your " + slotDisplayName(slot)
                    + " was broken through! " + ChatColor.GRAY + "(-"
                    + (int)(armorReduction * 100) + "% armor for "
                    + (durationTicks / 20) + "s)");
        }

        // Schedule debuff removal
        UUID victimId = victim.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                AttributeInstance a = victim.getAttribute(Attribute.ARMOR);
                if (a != null) a.removeModifier(armorKey);

                AttributeInstance t = victim.getAttribute(Attribute.ARMOR_TOUGHNESS);
                if (t != null) t.removeModifier(toughnessKey);

                Set<EquipmentSlot> slots = weakenedSlots.get(victimId);
                if (slots != null) {
                    slots.remove(slot);
                    if (slots.isEmpty()) weakenedSlots.remove(victimId);
                }
            }
        }.runTaskLater(plugin, durationTicks);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ItemStack getArmorPiece(EntityEquipment equipment, EquipmentSlot slot) {
        return switch (slot) {
            case HEAD  -> equipment.getHelmet();
            case CHEST -> equipment.getChestplate();
            case LEGS  -> equipment.getLeggings();
            case FEET  -> equipment.getBoots();
            default    -> null;
        };
    }

    private String slotDisplayName(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD  -> "helmet";
            case CHEST -> "chestplate";
            case LEGS  -> "leggings";
            case FEET  -> "boots";
            default    -> "armor";
        };
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }
}