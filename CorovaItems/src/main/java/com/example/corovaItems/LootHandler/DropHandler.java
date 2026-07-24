package com.example.corovaItems.LootHandler;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.function.Function;

/**
 * Listens for entity death events and applies all registered loot rules.
 */
public class DropHandler implements Listener {

    private static Function<Player, Integer> tierProvider = (player) -> 0;

    public static void setTierProvider(Function<Player, Integer> provider) {
        tierProvider = provider;
    }

    private final LootRuleManager lootRuleManager = new LootRuleManager();

    public void registerRule(LootRule rule) {
        lootRuleManager.register(rule);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity mob    = event.getEntity();
        Player        killer = mob.getKiller();

        ItemStack weapon  = null;
        int       looting = 0;
        int       luck    = 0;
        int       tier    = 0;

        if (killer != null) {
            weapon = killer.getInventory().getItemInMainHand();
            if (weapon != null && weapon.getType().isAir()) {
                weapon = null;
            }
            looting = weapon != null ? weapon.getEnchantmentLevel(Enchantment.LOOTING) : 0;
            luck = killer.getPotionEffect(PotionEffectType.LUCK) != null
                    ? killer.getPotionEffect(PotionEffectType.LUCK).getAmplifier() + 1
                    : 0;
            tier = tierProvider.apply(killer);
        }

        DropContext context = new DropContext(mob, killer, weapon, looting, luck, tier);

        LootRuleManager.ApplyResult result = lootRuleManager.applyRules(context);

        if (result.clearVanilla) {
            event.getDrops().clear();
        }

        event.getDrops().addAll(result.drops);

        if (result.customXP != null) {
            event.setDroppedExp(result.customXP);
        }
    }
}