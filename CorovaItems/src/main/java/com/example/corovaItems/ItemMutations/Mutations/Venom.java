package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class Venom implements Mutation, Mutation.BuildUpMutation, Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private static final String METADATA_KEY = "VenomLevel";

    public Venom(MutationManager manager, JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEBUFF, MutationCategory.INCREMENTAL, MutationCategory.SUSTAINED);
    }

    @Override
    public String getColor() {
        return "#2E8B57";
    }

    @Override
    public String getName() {
        return "Venom";
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
            desc.add(ChatColor.GRAY + "Poison targets with lethal venom after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Deals heavy damage over time.");
        } else {
            desc.add(ChatColor.GRAY + "Poison targets with lethal venom after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Deals extreme damage over time.");
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
        return MutationType.VENOM;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 8 : 6;
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

        // Applying POISON instead of WITHER
        // Duration: 10s for Level I, 20s for Level II (matching Nether Fire)
        int duration = (level == 1) ? 200 : 400;
        if (damager instanceof Player player) {
            duration = (int) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, duration, "duration");
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0));

        // Tag the entity with the mutation level
        victim.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, level));
        victim.setMetadata("VenomDamager", new FixedMetadataValue(plugin, damager.getUniqueId().toString()));

        ItemStack weapon = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        MutationManager.getInstance().getSynergyHandler().handleMutationSynergy(getType(), damager, victim, weapon, level);

        if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.DARK_GREEN + "Your weapon's venom seeps into " + victim.getName() + "!");
        }
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        if (MutationUtils.isFriendly(damager, victim)) return;

        // Reduced duration for synergy: 5s for Level I, 10s for Level II
        int duration = (level == 1) ? 100 : 200;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, duration, 0));
        victim.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, level));
        victim.setMetadata("VenomDamager", new FixedMetadataValue(plugin, damager.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPoisonTick(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.POISON) return;

        if (event.getEntity().hasMetadata(METADATA_KEY)) {
            // Poison normally doesn't kill, but we want it to be lethal
            event.setCancelled(false);

            List<MetadataValue> values = event.getEntity().getMetadata(METADATA_KEY);
            if (values.isEmpty()) return;

            int level = values.get(0).asInt();
            // Matching Nether Fire damage: 0.75 hearts (1.5) and 1.25 hearts (2.5)
            double baseDamage = (level == 1) ? 1.5 : 2.5;
            double finalDamage = baseDamage;
            if (event.getEntity().hasMetadata("VenomDamager")) {
                try {
                    java.util.UUID damagerUUID = java.util.UUID.fromString(event.getEntity().getMetadata("VenomDamager").get(0).asString());
                    org.bukkit.entity.Player damager = org.bukkit.Bukkit.getPlayer(damagerUUID);
                    if (damager != null) {
                        finalDamage = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(damager, this, finalDamage, "tick_damage");
                    }
                } catch (Exception ignored) {}
            }

            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity victim = (LivingEntity) event.getEntity();

                // Armor Protection
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

            event.setDamage(finalDamage);

            // If the entity no longer has poison, remove the metadata
            if (event.getEntity() instanceof LivingEntity) {
                LivingEntity victim = (LivingEntity) event.getEntity();
                if (!victim.hasPotionEffect(PotionEffectType.POISON)) {
                    event.getEntity().removeMetadata(METADATA_KEY, plugin);
                }
            }
        }
    }
}
