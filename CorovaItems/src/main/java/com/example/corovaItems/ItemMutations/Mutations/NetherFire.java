package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class NetherFire implements Mutation, Mutation.BuildUpMutation {

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private static final String METADATA_KEY = "NetherFireLevel";

    public NetherFire(MutationManager mutationManager, JavaPlugin plugin) {
        this.mutationManager = mutationManager;
        this.plugin = plugin;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.SUSTAINED, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#FF4500";
    }

    @Override
    public String getName() {
        return "Nether Fire";
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
            desc.add(ChatColor.GRAY + "Ignite targets with Nether Fire after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "10s of fire, 0.75 hearts base damage.");
        } else {
            desc.add(ChatColor.GRAY + "Ignite targets with Nether Fire after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "20s of fire, 1.25 hearts base damage.");
        }
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 0.5 * level;
    }

    @Override
    public MutationType getType() {
        return MutationType.NETHER_FIRE;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 3 : 2;
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

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {

        // Set on fire: Level I = 10s (200 ticks), Level II = 20s (400 ticks)
        int fireTicks = level == 1 ? 200 : 400;
        if (damager instanceof Player player) {
            fireTicks = (int) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, fireTicks, "duration");
        }
        victim.setFireTicks(fireTicks);

        ItemStack weapon = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        MutationManager.getInstance().getSynergyHandler().handleMutationSynergy(getType(), damager, victim, weapon, level);

        // Tag the entity with the mutation level
        victim.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, level));
        victim.setMetadata("NetherFireDamager", new FixedMetadataValue(plugin, damager.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onFireTick(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) return;

        if (event.getEntity().hasMetadata(METADATA_KEY)) {
            event.setCancelled(false);

            List<MetadataValue> values = event.getEntity().getMetadata(METADATA_KEY);
            if (values.isEmpty()) return;

            int level = values.get(0).asInt();
            double baseDamage = (level == 1) ? 1.5 : 2.5;

            // This fires on tick, should probably use SUSTAINED amplification
            // But we don't have the damager here easily.
            // We could store the damager UUID in metadata too if needed.
            // For now let's assume we can use the amplification if we find a player

            double finalDamage = baseDamage;
            if (event.getEntity().hasMetadata("NetherFireDamager")) {
                try {
                    java.util.UUID damagerUUID = java.util.UUID.fromString(event.getEntity().getMetadata("NetherFireDamager").get(0).asString());
                    org.bukkit.entity.Player damager = org.bukkit.Bukkit.getPlayer(damagerUUID);
                    if (damager != null) {
                        finalDamage = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(damager, this, finalDamage, "tick_damage");
                    }
                } catch (Exception ignored) {}
            }

            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity victim = (LivingEntity) event.getEntity();

                // 1. Fire Resistance reduction: -1 heart (-2 damage)
                if (victim.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                    finalDamage -= 2.0;
                }

                // 2. Fire Protection IV reduction: -1 heart (-2 damage)
                if (hasFireProtection4(victim)) {
                    finalDamage -= 2.0;
                }

                // 3. Armor Protection
                double armor = 0;
                AttributeInstance armorAttr = victim.getAttribute(Attribute.ARMOR);
                if (armorAttr != null) {
                    armor = armorAttr.getValue();
                }

                double toughness = 0;
                AttributeInstance toughnessAttr = victim.getAttribute(Attribute.ARMOR_TOUGHNESS);
                if (toughnessAttr != null) {
                    toughness = toughnessAttr.getValue();
                }

                // Formula from Minecraft Wiki: damage * (1 - min(20, max(armor / 5, armor - (4 * damage) / (toughness + 8))) / 25)
                finalDamage = finalDamage * (1.0 - Math.min(20.0, Math.max(armor / 5.0, armor - (4.0 * finalDamage) / (toughness + 8.0))) / 25.0);
            }

            finalDamage = Math.max(1.0, finalDamage);

            event.setDamage(finalDamage);

            // If the entity is no longer on fire, remove the metadata
            if (event.getEntity().getFireTicks() <= 1) {
                event.getEntity().removeMetadata(METADATA_KEY, plugin);
            }
        }
    }

    private boolean hasFireProtection4(LivingEntity entity) {
        if (entity.getEquipment() == null) return false;
        ItemStack[] armor = entity.getEquipment().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece != null && piece.hasItemMeta()) {
                if (piece.getEnchantmentLevel(Enchantment.FIRE_PROTECTION) >= 4) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Synergy checks friendly inside handleMutationSynergy or caller, but let's be safe
        if (MutationUtils.isFriendly(damager, victim)) return;

        // Reduced synergy: 5s fire for both levels
        victim.setFireTicks(100);
        victim.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, level));
        victim.setMetadata("NetherFireDamager", new FixedMetadataValue(plugin, damager.getUniqueId().toString()));
    }
}