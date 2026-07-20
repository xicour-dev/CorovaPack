package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class KineticCharge implements Mutation {

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;
    private final NamespacedKey CHARGED_KEY;

    public KineticCharge(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        this.CHARGED_KEY = new NamespacedKey(plugin, "kinetic_charged");

        startHealingTask();
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.ACCUMULATION, MutationCategory.BURST, MutationCategory.ENCHANT_SYNERGY);
    }

    @Override
    public String getColor() {
        return "#B87333";
    }

    @Override
    public String getName() {
        return "Kinetic Charge";
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
        desc.add(ChatColor.GRAY + "Taking damage has a 8% chance to charge armor.");
        desc.add(ChatColor.GRAY + "Charged armor gives +0.5 absorption hearts.");
        desc.add(ChatColor.GRAY + "Right-click to strike lightning and discharge.");
        desc.add(ChatColor.GRAY + "Healing lightning strikes you every 15s (4-10% chance).");
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Adds +75 Durability.");
        } else if (level == 2) {
            desc.add(ChatColor.GRAY + "Adds +125 Durability.");
        }
        desc.add(ChatColor.DARK_GRAY + "Copper armor only.");
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public MutationType getType() {
        return MutationType.KINETIC_CHARGE;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @Override
    public int getDurabilityBonus(int level) {
        if (level == 1) return 75;
        if (level == 2) return 125;
        return 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        boolean updated = false;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && mutationManager.hasMutation(armor, MutationType.KINETIC_CHARGE)) {
                double chance = 0.08;
                com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
                chance += com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), profile, "incremental");
                if (Math.random() < chance) {
                    if (setCharged(armor, true)) {
                        updated = true;
                    }
                }
            }
        }

        if (updated) {
            updateAbsorption(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
            player.sendActionBar(ChatColor.AQUA + "Your armor is kinetically charging!");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity victim)) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) return;

        int chargedCount = getChargedCount(player);
        if (chargedCount <= 0) return;

        // Discharge
        player.getWorld().strikeLightningEffect(victim.getLocation());

        double baseDamage = getBaseWeaponDamage(hand, player);
        double multiplier = 1.5;
        multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "accumulation");
        boolean hasSynergy = MutationManager.getInstance().getSynergyHandler().isLightning(hand);

        if (hasSynergy) {
            multiplier = 2.5;
            victim.getWorld().createExplosion(victim.getLocation(), 2.0f, false, false);
        }

        victim.damage(baseDamage * multiplier, player);

        // Consume charges
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            setCharged(armor, false);
        }
        updateAbsorption(player);
        player.sendActionBar(ChatColor.YELLOW + "Kinetically discharged!");
    }

    private void startHealingTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                int mutationCount = 0;
                boolean anyCharged = false;
                for (ItemStack armor : player.getInventory().getArmorContents()) {
                    if (armor != null && mutationManager.hasMutation(armor, MutationType.KINETIC_CHARGE)) {
                        mutationCount++;
                        if (isCharged(armor)) {
                            anyCharged = true;
                        }
                    }
                }

                if (mutationCount > 0 && anyCharged) {
                    double chance = 0.04 + (mutationCount - 1) * 0.02;
                    com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
                    chance += com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), profile, "incremental");
                    if (Math.random() < chance) {
                        player.getWorld().strikeLightningEffect(player.getLocation());
                        double currentHealth = player.getHealth();
                        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                        double healAmount = 6.0;
                        healAmount = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, KineticCharge.this, healAmount, "recovery");
                        player.setHealth(Math.min(maxHealth, currentHealth + healAmount));
                        player.sendActionBar(ChatColor.GREEN + "Healing lightning restores your energy!");
                    }
                }
            }
        }, 300L, 300L); // 15 seconds
    }

    private boolean isCopperArmor(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type == Material.matchMaterial("COPPER_HELMET") ||
                type == Material.matchMaterial("COPPER_CHESTPLATE") ||
                type == Material.matchMaterial("COPPER_LEGGINGS") ||
                type == Material.matchMaterial("COPPER_BOOTS");
    }

    private boolean setCharged(ItemStack item, boolean charged) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean wasCharged = pdc.has(CHARGED_KEY, PersistentDataType.BYTE);

        if (charged) {
            if (wasCharged) return false;
            pdc.set(CHARGED_KEY, PersistentDataType.BYTE, (byte) 1);
        } else {
            if (!wasCharged) return false;
            pdc.remove(CHARGED_KEY);
        }

        item.setItemMeta(meta);
        return true;
    }

    private boolean isCharged(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(CHARGED_KEY, PersistentDataType.BYTE);
    }

    private int getChargedCount(Player player) {
        int count = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (isCharged(armor)) count++;
        }
        return count;
    }

    private void updateAbsorption(Player player) {
        int chargedCount = getChargedCount(player);
        double amount = chargedCount * 1.0; // 0.5 heart per charged piece
        amount = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, amount, "accumulation");
        player.setAbsorptionAmount(amount);
    }

    private double getBaseWeaponDamage(ItemStack item, Player player) {
        double damage = 1.0;
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attr != null) {
            damage = attr.getValue();
        }

        if (item != null && item.hasItemMeta()) {
            // Sharpness
            int sharpness = item.getEnchantmentLevel(Enchantment.SHARPNESS);
            if (sharpness > 0) {
                damage += (0.5 * sharpness + 0.5);
            }

            // Power (for bows)
            int power = item.getEnchantmentLevel(Enchantment.POWER);
            if (power > 0) {
                damage += (0.5 * power + 0.5);
            }
        }

        return damage;
    }
}
