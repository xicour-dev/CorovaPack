package com.example.corovaItems.LootHandler;

import com.example.corovaItems.LootHandler.LootRules.ItemLootRules.*;
import com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments.*;
import com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets.*;
import com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Weapons.*;
import com.example.corovaItems.LootHandler.LootRules.ItemLootRules.XP.*;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Collects candidate drops from every rule, then arbitrates:
 *
 *   Unlimited entries (group == null) → all drop, no cap.
 *   Limited entries   (group != null) → one random winner per named group.
 */
public class LootRuleManager {

    public static final class ApplyResult {
        public final List<ItemStack> drops;
        public final boolean         clearVanilla;
        public final Integer         customXP;

        ApplyResult(List<ItemStack> drops, boolean clearVanilla, Integer customXP) {
            this.drops        = drops;
            this.clearVanilla = clearVanilla;
            this.customXP     = customXP;
        }
    }

    private final List<LootRule> rules = new ArrayList<>();

    public LootRuleManager() {
        registerAllItemLootRules();
    }

    private void registerAllItemLootRules() {
        // Trinkets
        register(new BlazingPowerLootRule());
        register(new BloodSugarLootRule());
        register(new DenseArmorPlatingLootRule());
        register(new EnchantedGlisteringMelonLootRule());
        register(new EnergyFormulaLootRule());
        register(new MinersMightLootRule());
        register(new SpiderEyeTotemLootRule());
        register(new SwiftStrikeLootRule());
        register(new AnchorLootRule());
        register(new TotemOfDyingLootRule());
        register(new EnchantedQuiverLootRule());
        register(new BackpackLootRule());
        register(new BlockReachExtenderLootRule());
        register(new ManaTrinketLootRule());
        register(new CompactorLootRule());

        // Enchantment books
        register(new BeamBookLootRule());
        register(new FreezeBookLootRule());
        register(new PoisonBookLootRule());
        register(new LightningBookLootRule());
        register(new TeleportBookLootRule());
        register(new LaunchBookLootRule());
        register(new FlightBookLootRule());
        register(new SoulProjectionLootRule());
        register(new ExplosiveRoundBookLootRule());
        register(new EnderMissileBookLootRule());
        register(new WitherBookLootRule());
        register(new FangStrikeLootRule());
        register(new SnowStormBookLootRule());
        register(new WaterBallBookLootRule());
        register(new DoubleJumpBookLootRule());
        register(new SteedBookLootRule());
        register(new HasteBookLootRule());
        register(new VeinMinerBookLootRule());
        register(new StormBookLootRule());
        register(new MusicBookLootRule());
        register(new PaydayBookLootRule());
        register(new ArrowRainBookLootRule());
        register(new SoulExtractionBookLootRule());
        register(new NapalmBookLootRule());
        register(new BoomerangBookLootRule());
        register(new VanillaSoulSpeedLootRule());

        // New enchantment books
        register(new CriticalBookLootRule());
        register(new SoulFireAspectBookLootRule());
        register(new StealthStepBookLootRule());
        register(new ThrustBookLootRule());
        register(new FlareBookLootRule());
        register(new HookBookLootRule());
        register(new DivinumTrabemBookLootRule());
        register(new WebSlingBookLootRule());
        register(new MissileBookLootRule());
        register(new NightVisionBookLootRule());
        register(new CosmicRayBookLootRule());
        register(new KnockbackProtectionBookLootRule());

        // Weapons
        register(new BurstRodLootRule());
        register(new CopperMjolnirLootRule());
        register(new EggsplodingEggLootRule());
        register(new RodOfArrowCastingLootRule());
        register(new GolemSmashLootRule());
        register(new VanillaTridentLootRule());

        // XP
        register(new TenXP());
        register(new TwentyXP());
        register(new ThirtyXP());
        register(new FourtyXP());
        register(new FiftyXP());
        register(new SixtyXP());
        register(new SeventyXP());
        register(new EightyXP());
        register(new NinetyXP());
        register(new OneHundredXP());

        // Misc
        register(new EnchantedEnderPearlLootRule());
        register(new EnchantedGoldenCarrotLootRule());
        register(new VanillaGoldenCarrotLootRule());
        register(new LuckyMushroomLootRule());
    }

    public void register(LootRule rule) {
        rules.add(rule);
        rules.sort(Comparator.comparingInt(LootRule::getPriority).reversed());
    }

    public List<LootRule> getAllRules() {
        return new ArrayList<>(rules);
    }

    /**
     * Ask every rule to collect its candidate drops into a single flat list,
     * then arbitrate that list in one pass.
     */
    public ApplyResult applyRules(DropContext context) {
        boolean clearVanilla = false;
        Integer customXP     = null;

        List<ItemDropEntry> candidates = new ArrayList<>();

        for (LootRule rule : rules) {
            int sizeBefore = candidates.size();
            rule.collectDrops(context, candidates);
            boolean ruleAddedDrops = candidates.size() > sizeBefore;

            if (ruleAddedDrops && rule.overridesVanillaDrops()) {
                clearVanilla = true;
            }

            if (customXP == null) {
                customXP = rule.getExperience(context);
            }
        }

        // Arbitrate: split candidates into unlimited and per-group buckets.
        List<ItemStack>              unlimited = new ArrayList<>();
        Map<String, List<ItemStack>> byGroup   = new HashMap<>();

        for (ItemDropEntry entry : candidates) {
            if (!entry.isLimited()) {
                unlimited.add(entry.getItem());
            } else {
                byGroup.computeIfAbsent(entry.getGroup(), k -> new ArrayList<>())
                        .add(entry.getItem());
            }
        }

        // Build final drop list: all unlimited + one random winner per group.
        List<ItemStack> drops = new ArrayList<>(unlimited);
        for (List<ItemStack> groupCandidates : byGroup.values()) {
            if (!groupCandidates.isEmpty()) {
                drops.add(groupCandidates.get(ThreadLocalRandom.current().nextInt(groupCandidates.size())));
            }
        }

        return new ApplyResult(drops, clearVanilla, customXP);
    }
}