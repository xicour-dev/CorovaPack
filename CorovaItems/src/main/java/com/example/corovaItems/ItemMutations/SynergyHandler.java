package com.example.corovaItems.ItemMutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantBooks.*;
import com.example.corovaItems.ItemMutations.Mutation.MutationCategory;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class SynergyHandler implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey explodeKey;
    private static BiConsumer<LivingEntity, LivingEntity> arrowRainCallback = null;

    public SynergyHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.explodeKey = new NamespacedKey(plugin, "synergy_explode");
    }

    public static void registerArrowRainCallback(BiConsumer<LivingEntity, LivingEntity> callback) {
        arrowRainCallback = callback;
    }

    public static BiConsumer<LivingEntity, LivingEntity> getArrowRainCallback() {
        return arrowRainCallback;
    }


    public List<String> getMutationSynergy(MutationType type, ItemStack item) {
        List<String> syner = new java.util.ArrayList<>();
        if (item != null) {
            Mutation m = MutationManager.getInstance().getMutation(type);
            if (m != null && !m.isCompatible(item)) return syner;
        }
        switch (type) {
            case EXTRA_CUSTOM_ENCHANT_SLOT -> {
                if (item == null || MutationUtils.isBow(item)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Allows for an extra custom enchantment.");
                }
            }
            case VENOM -> {
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.POISON_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Poison adds Nausea II for 10s.");
                }
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WEB_SLING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Web Slinger shoots venomous green webs.");
                }
            }
            case NETHER_FIRE -> {
                if (item == null || (item.hasItemMeta() && item.getItemMeta().hasEnchant(Enchantment.FIRE_ASPECT))) {
                    syner.add(ChatColor.YELLOW + "Synergy: Fire Aspect duration +5s.");
                }
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.STEED_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Steed — summoned horse burns with flame.");
                }
            }
            case FROST -> {
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.FREEZE_ID) || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.SNOWSTORM_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Freeze/Snowstorm duration +2s.");
                }
            }
            case DECAY -> {
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WITHER_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Wither adds Wither III for 5s.");
                }
            }
            case STATIC_CHARGE -> {
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.LIGHTNING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Lightning adds +10% proc chance,");
                    syner.add(ChatColor.YELLOW + "double strikes & arcs to 2 enemies.");
                }
            }
            case ARROW_VELOCITY -> {
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WEB_SLING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Web Slinger shoots faster webs.");
                }
            }
            case DOUBLE_TAP, TRIPLE_TAP -> {
                // TRIPLE_TAP-specific: arrows explode
                if (type == MutationType.TRIPLE_TAP) {
                    if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.EXPLOSIVE_ROUND_ID)) {
                        syner.add(ChatColor.YELLOW + "Synergy: Triple Tap arrows explode.");
                    }
                }
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.EXPLOSIVE_ROUND_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Explosive Round applies to extra arrows.");
                }
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WEB_SLING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Web Slinger shoots extra webs.");
                }
            }
            case KINETIC_CHARGE -> {
                if (item == null || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.LIGHTNING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Lightning adds 5x damage and explosion.");
                }
            }
            case PRISMATIC_EDGE -> {
                syner.add(ChatColor.YELLOW + "Synergy: Diamond Trims increase burst damage by +10% each.");
            }
            case SKULL_CRUSH -> {
                syner.add(ChatColor.YELLOW + "Synergy: Axe critical hits deal significantly more damage.");
            }
            case HEAVY_METAL -> {
                syner.add(ChatColor.YELLOW + "Synergy: Iron armor provides immense protection.");
            }
            case WOUND_MENDING -> {
                syner.add(ChatColor.YELLOW + "Synergy: Chainmail restores health much faster after combat.");
            }
            case BRIMSTONE -> {
                syner.add(ChatColor.YELLOW + "Synergy: Netherite builds power from taken damage to unleash on hits.");
            }
            case TORTOISESHELL -> {
                syner.add(ChatColor.YELLOW + "Synergy: Turtle Shell provides elite defensive protection.");
            }
        }
        return syner;
    }

    public void appendMutationSynergyLore(MutationType type, ItemStack item, List<String> lore) {
        lore.addAll(getMutationSynergy(type, item));
    }

    public List<String> getEnchantSynergy(String enchantId, int level, ItemStack item) {
        List<String> syner = new java.util.ArrayList<>();
        boolean isBook = item == null;

        switch (enchantId) {
            case CorovaEnchantments.FREEZE_ID, CorovaEnchantments.SNOWSTORM_ID -> {
                if (isBook || MutationManager.getInstance().hasMutation(item, MutationType.FROST)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Frost mutation duration +2s.");
                }
            }
            case CorovaEnchantments.LIGHTNING_ID -> {
                if (isBook || MutationManager.getInstance().hasMutation(item, MutationType.STATIC_CHARGE)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Static Charge adds +10% proc chance,");
                    syner.add(ChatColor.YELLOW + "double strikes & arcs to 2 enemies.");
                }
                if (isBook || MutationManager.getInstance().hasMutation(item, MutationType.KINETIC_CHARGE)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Kinetic Charge adds 5x damage and explosion.");
                }
            }
            case CorovaEnchantments.POISON_ID -> {
                if (isBook || MutationManager.getInstance().hasMutation(item, MutationType.VENOM)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Venom mutation adds Nausea II for 10s.");
                }
                if (isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WEB_SLING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Web Slinger shoots venomous green webs.");
                }
            }
            case CorovaEnchantments.WITHER_ID -> {
                if (isBook || MutationManager.getInstance().hasMutation(item, MutationType.DECAY)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Decay mutation adds Wither III for 5s.");
                }
                if (isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WEB_SLING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Web Slinger shoots withered black webs.");
                }
            }
            case CorovaEnchantments.EXPLOSIVE_ROUND_ID -> {
                boolean hasDouble = isBook || MutationManager.getInstance().hasMutation(item, MutationType.DOUBLE_TAP);
                boolean hasTriple = isBook || MutationManager.getInstance().hasMutation(item, MutationType.TRIPLE_TAP);
                boolean hasRain = isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.ARROW_RAIN_ID);
                if (hasDouble || hasTriple || hasRain) {
                    syner.add(ChatColor.YELLOW + "Synergy: Double/Triple Tap & Arrow Rain arrows explode.");
                    syner.add(ChatColor.YELLOW + "Explosion radius: 2.0.");
                }
            }
            case CorovaEnchantments.ARROW_RAIN_ID -> {
                if (isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.EXPLOSIVE_ROUND_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Explosive Round makes rain arrows explode");
                    syner.add(ChatColor.YELLOW + "(2.0 radius).");
                }
                if (isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WEB_SLING_ID)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Web Slinger makes rain fire webs.");
                }
            }
            case CorovaEnchantments.BOOMERANG_ID -> {
                syner.add(ChatColor.YELLOW + "Synergy: Triggers enchantments & mutations on hit.");
                if (!isBook) {
                    Map<MutationType, Integer> mutations = MutationManager.getInstance().getMutations(item);
                    for (MutationType type : mutations.keySet()) {
                        Mutation m = MutationManager.getInstance().getMutation(type);
                        if (m != null) {
                            ChatColor mColor = ChatColor.of(m.getColor());
                            switch (type) {
                                case BLEED, FROST, SPLINTER, CLOBBER, DECAY, VENOM, SHATTER, STATIC_CHARGE, COLD_STEEL, NETHER_FIRE, LIFE_SIPHON, AMPLIFIER, DICE, FEAR, BACKSTAB, PARRY, LAST_STAND, BREAK_THROUGH, SKULL_CRUSH -> {
                                    syner.add(ChatColor.YELLOW + "Synergy: Boomerang hit triggers " + mColor + m.getName() + ChatColor.YELLOW + ".");
                                }
                            }
                        }
                    }
                }
            }
            case CorovaEnchantments.WEB_SLING_ID -> {
                boolean hasPoison = isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.POISON_ID);
                boolean hasVenom = isBook || MutationManager.getInstance().hasMutation(item, MutationType.VENOM);
                if (hasPoison || hasVenom) {
                    syner.add(ChatColor.YELLOW + "Synergy: Shoots venomous green webs.");
                }
                boolean hasWither = isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WITHER_ID);
                boolean hasDecay = isBook || MutationManager.getInstance().hasMutation(item, MutationType.DECAY);
                if (hasWither || hasDecay) {
                    syner.add(ChatColor.YELLOW + "Synergy: Shoots withered black webs.");
                }
                boolean hasDouble = isBook || MutationManager.getInstance().hasMutation(item, MutationType.DOUBLE_TAP);
                boolean hasTriple = isBook || MutationManager.getInstance().hasMutation(item, MutationType.TRIPLE_TAP);
                if (hasDouble || hasTriple) {
                    syner.add(ChatColor.YELLOW + "Synergy: Fires extra webs.");
                }
                if (isBook || MutationManager.getInstance().hasMutation(item, MutationType.ARROW_VELOCITY)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Increases web speed.");
                }
            }
            case CorovaEnchantments.STEED_ID -> {
                if (isBook || MutationManager.getInstance().hasMutation(item, MutationType.NETHER_FIRE)) {
                    syner.add(ChatColor.YELLOW + "Synergy: Nether Fire — summoned horse burns with flame.");
                }
            }
        }

        // Global Boomerang Synergy Info
        if (!enchantId.equals(CorovaEnchantments.BOOMERANG_ID)) {
            if (isBook || CorovaEnchantments.hasEnchant(item, CorovaEnchantments.BOOMERANG_ID)) {
                switch (enchantId) {
                    case CorovaEnchantments.LIGHTNING_ID, CorovaEnchantments.POISON_ID, CorovaEnchantments.WITHER_ID,
                         CorovaEnchantments.FREEZE_ID, CorovaEnchantments.BEAM_ID, CorovaEnchantments.SOUL_PROJECTION_ID,
                         CorovaEnchantments.PAYDAY_ID, CorovaEnchantments.FANG_STRIKE_ID, CorovaEnchantments.LAUNCH_ID,
                         CorovaEnchantments.TELEPORT_ID, CorovaEnchantments.MUSIC_ID, CorovaEnchantments.SNOWSTORM_ID -> {
                        syner.add(ChatColor.YELLOW + "Synergy: Boomerang hit triggers effect.");
                    }
                }
            }
        }
        return syner;
    }

    public void appendEnchantSynergyLore(String enchantId, int level, ItemStack item, List<String> lore) {
        lore.addAll(getEnchantSynergy(enchantId, level, item));
    }

    public void handleMutationSynergy(MutationType type, LivingEntity damager, LivingEntity victim, ItemStack weapon, int level) {
        double multiplier = 1.0;
        Mutation mutation = MutationManager.getInstance().getMutation(type);
        if (damager instanceof Player player && mutation != null) {
            String context = (type == MutationType.VENOM || type == MutationType.DECAY) ? "duration" : "window";
            multiplier = applyTrimAmplification(player, mutation, 1.0, context);
        }

        switch (type) {
            case VENOM -> {
                if (weapon != null) {
                    int duration = (int) (200 * multiplier);
                    if (CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.POISON_ID)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 1));
                    }
                    if (CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.WEB_SLING_ID)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 1));
                    }
                }
            }
            case NETHER_FIRE -> {
                if (weapon != null && com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.getTrueLevel(weapon, Enchantment.FIRE_ASPECT) > 0) {
                    victim.setFireTicks(victim.getFireTicks() + (int)(100 * multiplier));
                }
            }
            case DECAY -> {
                if (weapon != null) {
                    int duration = (int) (100 * multiplier);
                    if (CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.WITHER_ID)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, 2));
                    }
                    if (CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.WEB_SLING_ID)) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, duration, 2));
                    }
                }
            }
        }
    }

    public void handleEnchantSynergy(String enchantId, LivingEntity damager, LivingEntity victim, ItemStack weapon, int level) {
        double multiplier = 1.0;
        switch (enchantId) {
            case CorovaEnchantments.POISON_ID -> {
                Mutation m = MutationManager.getInstance().getMutation(MutationType.VENOM);
                if (weapon != null && MutationManager.getInstance().hasMutation(weapon, MutationType.VENOM)) {
                    if (damager instanceof Player player && m != null) multiplier = applyTrimAmplification(player, m, 1.0, "duration");
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (int)(200 * multiplier), 1));
                }
            }
            case CorovaEnchantments.WITHER_ID -> {
                Mutation m = MutationManager.getInstance().getMutation(MutationType.DECAY);
                if (weapon != null && MutationManager.getInstance().hasMutation(weapon, MutationType.DECAY)) {
                    if (damager instanceof Player player && m != null) multiplier = applyTrimAmplification(player, m, 1.0, "duration");
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, (int)(100 * multiplier), 2));
                }
            }
            case CorovaEnchantments.LIGHTNING_ID -> {
                Mutation m = MutationManager.getInstance().getMutation(MutationType.STATIC_CHARGE);
                if (weapon != null && MutationManager.getInstance().hasMutation(weapon, MutationType.STATIC_CHARGE)) {
                    if (damager instanceof Player player && m != null) multiplier = applyTrimAmplification(player, m, 1.0, "window");

                    // Schedule secondary lightning hits for the next tick.
                    // Calling victim.damage() synchronously inside an EntityDamageByEntityEvent
                    // fires a nested damage event at HIGHEST priority. With no-I-frame mode
                    // active, the victim takes the hit immediately, and any Redstone/Quartz
                    // trim handlers re-fire on the nested event — amplifying the damage a second
                    // time and causing random instant-kill spikes. Deferring by 1 tick breaks
                    // the nesting entirely; the secondary hit becomes its own clean event.
                    final double finalMultiplier = multiplier;
                    final LivingEntity finalVictim = victim;
                    final LivingEntity finalDamager = damager;

                    // Snapshot nearby entities now (inside the event) to keep targeting
                    // consistent, then apply damage on the next tick.
                    java.util.List<LivingEntity> arcTargets = new java.util.ArrayList<>();
                    int arcs = 0;
                    for (org.bukkit.entity.Entity nearby : victim.getNearbyEntities(5, 5, 5)) {
                        if (nearby instanceof LivingEntity nearbyLiving
                                && !nearby.equals(damager)
                                && !nearby.equals(victim)) {
                            arcTargets.add(nearbyLiving);
                            arcs++;
                            if (arcs >= 2) break;
                        }
                    }

                    org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // Synergy: strikes the main target a second time
                        if (finalVictim.isValid() && !finalVictim.isDead()) {
                            finalVictim.getWorld().strikeLightningEffect(finalVictim.getLocation());
                            finalVictim.damage(2.0 * finalMultiplier, finalDamager);
                        }
                        // Arc hits
                        for (LivingEntity arcTarget : arcTargets) {
                            if (arcTarget.isValid() && !arcTarget.isDead()) {
                                arcTarget.damage(2.0 * finalMultiplier, finalDamager);
                                arcTarget.getWorld().strikeLightningEffect(arcTarget.getLocation());
                            }
                        }
                    }, 1L);
                }
            }
        }
    }

    public void handleBoomerangSynergies(Player thrower, LivingEntity victim, ItemStack thrownItem) {
        // 1. Custom Enchantment Synergies
        Map<String, Integer> enchants = CorovaEnchantments.getAllCustomEnchants(thrownItem);
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String id = entry.getKey();
            int level = entry.getValue();

            switch (id) {
                case CorovaEnchantments.LIGHTNING_ID -> LightningBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.POISON_ID -> PoisonBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.WITHER_ID -> WitherBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.FREEZE_ID -> FreezeBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.BEAM_ID -> BeamBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.SOUL_PROJECTION_ID -> SoulProjection.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.PAYDAY_ID -> PaydayBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.FANG_STRIKE_ID -> FangStrike.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.LAUNCH_ID -> LaunchBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.TELEPORT_ID -> TeleportBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.MUSIC_ID -> MusicBook.triggerEffect(thrower, victim, level);
                case CorovaEnchantments.SNOWSTORM_ID -> SnowStormBook.triggerEffect(thrower, victim, level);
            }
        }

        // 2. Mutation Synergies
        MutationManager.getInstance().triggerMutations(thrower, victim, thrownItem);
    }

    public boolean isNetherFire(ItemStack item) {
        if (item == null) return false;
        return MutationManager.getInstance().hasMutation(item, MutationType.NETHER_FIRE);
    }

    public boolean isVenomous(ItemStack item) {
        if (item == null) return false;
        return CorovaEnchantments.hasEnchant(item, CorovaEnchantments.POISON_ID)
                || MutationManager.getInstance().hasMutation(item, MutationType.VENOM);
    }

    public boolean isWithered(ItemStack item) {
        if (item == null) return false;
        return CorovaEnchantments.hasEnchant(item, CorovaEnchantments.WITHER_ID)
                || MutationManager.getInstance().hasMutation(item, MutationType.DECAY);
    }

    public boolean hasExplosiveSynergy(ItemStack item) {
        if (item == null) return false;
        return CorovaEnchantments.hasEnchant(item, CorovaEnchantments.EXPLOSIVE_ROUND_ID);
    }

    public boolean isLightning(ItemStack item) {
        if (item == null) return false;
        return CorovaEnchantments.hasEnchant(item, CorovaEnchantments.LIGHTNING_ID);
    }

    public int getFreezeDurationBonus(ItemStack item) {
        if (item == null) return 0;
        return MutationManager.getInstance().hasMutation(item, MutationType.FROST) ? 40 : 0;
    }

    public double applyTrimAmplification(
            Player player,
            Mutation mutation,
            double baseDamage,
            String context) { // context = "incremental", "window", "duration", etc.

        PlayerTrimProfile profile = TrimManager.getInstance().getProfile(player);
        Set<MutationCategory> categories = mutation.getCategories();

        double result = baseDamage;

        // Apply the relevant amplification for this context
        double amplifier = TrimCalculator.getAmplification(categories, profile, context);

        // For multipliers (window, duration, accumulation, tick_damage):
        // amplifier is already a multiplier like 1.25
        // For additive bonuses (incremental, threshold, condition):
        // amplifier is an additive delta — handle this in the specific mutation, not here

        result *= amplifier;

        // Quartz intersection — check if this event qualifies
        result *= TrimCalculator.getQuartzIntersectionBonus(categories, profile);

        // Resin bonus — applies as additional multiplier on tick damage
        double resinBonus = TrimCalculator.getResinBonus(categories, profile);
        result *= (1.0 + resinBonus);

        return result;
    }
}