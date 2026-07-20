package com.example.corovaItems.ItemMutations;

import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.CustomEnchantMutationFormatting;
import com.example.corovaItems.ItemMutations.Mutations.*;
import com.example.corovaItems.WeaponProperties.DualWielding;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class MutationManager implements Listener {
    private static MutationManager instance;
    private static final ThreadLocal<ItemStack> currentLoreItem = new ThreadLocal<>();
    private final JavaPlugin plugin;
    private final Map<MutationType, Mutation> mutations = new HashMap();
    private final SynergyHandler synergyHandler;
    private static final Random random = new Random();
    public static final NamespacedKey MUTATION_1_TYPE_KEY = new NamespacedKey("corovaitems", "mutation_1_type");
    public static final NamespacedKey MUTATION_1_LEVEL_KEY = new NamespacedKey("corovaitems", "mutation_1_level");
    public static final NamespacedKey MUTATION_2_TYPE_KEY = new NamespacedKey("corovaitems", "mutation_2_type");
    public static final NamespacedKey MUTATION_2_LEVEL_KEY = new NamespacedKey("corovaitems", "mutation_2_level");
    public static final NamespacedKey MUTATION_3_TYPE_KEY = new NamespacedKey("corovaitems", "mutation_3_type");
    public static final NamespacedKey MUTATION_3_LEVEL_KEY = new NamespacedKey("corovaitems", "mutation_3_level");
    public static final NamespacedKey MUTATION_4_TYPE_KEY = new NamespacedKey("corovaitems", "mutation_4_type");
    public static final NamespacedKey MUTATION_4_LEVEL_KEY = new NamespacedKey("corovaitems", "mutation_4_level");
    public static final NamespacedKey ITEM_UUID_KEY = new NamespacedKey("corovaitems", "item_uuid");

    // NOTE: a UUID-keyed mutation cache used to live here. It was removed:
    // ItemStacks are cloned constantly by Bukkit (inventory reads, anvils,
    // pickups, internal copies, etc.), and every clone carries the same
    // ITEM_UUID_KEY value. Caching by that UUID meant a stale clone could
    // return another clone's already-overwritten mutation map, even though
    // the on-item PDC had the correct data. That silently broke proc
    // checks, lore updates, and discovery (tryToMutate looked at items as
    // if they had no/old mutations). Reading the PDC directly is cheap
    // enough that the cache wasn't worth the correctness risk.

    private static final double MUTATION_CHANCE = 0.001;
    private static final double ARMOR_MUTATION_CHANCE = 2.0E-4;

    public MutationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        this.synergyHandler = new SynergyHandler(plugin);
        this.registerMutations();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(new com.example.corovaItems.ArmorTrims.TrimEventListener(), plugin);
    }

    public static ItemStack getCurrentLoreItem() {
        return currentLoreItem.get();
    }

    public static void setCurrentLoreItem(ItemStack item) {
        currentLoreItem.set(item);
    }

    public static MutationManager getInstance() {
        return instance;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    private void registerMutations() {
        this.registerMutation(new LifeSiphon(this));
        this.registerMutation(new ExtractorOEnchanting(this));
        this.registerMutation(new Bleed(this));
        this.registerMutation(new ArrowVelocity(this));
        this.registerMutation(new DoubleTap(this, this.plugin));
        this.registerMutation(new TripleTap(this, this.plugin));
        this.registerMutation(new BattleHardened(this));
        this.registerMutation(new NetherFire(this, this.plugin));
        this.registerMutation(new Splinter(this, this.plugin));
        this.registerMutation(new Frost(this));
        this.registerMutation(new Venom(this, this.plugin));
        this.registerMutation(new Decay(this, this.plugin));
        this.registerMutation(new Clobber(this));
        this.registerMutation(new ColdSteel(this));
        this.registerMutation(new Shatter(this));
        this.registerMutation(new StaticCharge(this));
        this.registerMutation(new LifeSteal(this, this.plugin));
        this.registerMutation(new ExtraCustomEnchant());
        this.registerMutation(new ExtraMutationSlot());
        this.registerMutation(new ManaConservation(this));
        this.registerMutation(new SoulSiphon(this));
        this.registerMutation(new MysticArcanum(this));
        this.registerMutation(new AutoSmelt(this));
        this.registerMutation(new Excavation(this));
        this.registerMutation(new SolidStance(this)); // Shield mutation
        this.registerMutation(new KineticCharge(this));
        this.registerMutation(new HuntersInstinct(this));
        this.registerMutation(new GoldenAegis(this));
        this.registerMutation(new Dice(this.plugin));
        this.registerMutation(new Amplifier(this));
        this.registerMutation(new Fear(this));
        this.registerMutation(new BackStab(this));
        this.registerMutation(new Parry(this));
        this.registerMutation(new LastStand(this));
        this.registerMutation(new BreakThrough(this, this.plugin));
        this.registerMutation(new PrismaticEdge(this));
        this.registerMutation(new SkullCrush(this));
        this.registerMutation(new HeavyMetal(this));
        this.registerMutation(new WoundMending(this));
        this.registerMutation(new Brimstone(this));
        this.registerMutation(new Tortoiseshell(this));
        // NOTE: MutationDiscoveryListener is registered once in the constructor
        // via ItemRegistry / CorovaItemsRegistrar — do NOT register it again here.
    }

    private void registerMutation(Mutation mutation) {
        this.mutations.put(mutation.getType(), mutation);
        this.plugin.getServer().getPluginManager().registerEvents(mutation, this.plugin);
    }

    public Mutation getMutation(MutationType type) {
        return (Mutation) this.mutations.get(type);
    }

    public SynergyHandler getSynergyHandler() {
        return synergyHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProcCheck(EntityDamageByEntityEvent event) {
        if (com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) return;

        LivingEntity damager;
        boolean isProjectile = false;
        if (event.getDamager() instanceof LivingEntity le) {
            damager = le;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof LivingEntity le) {
            damager = le;
            isProjectile = true;
        } else {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (damager.getEquipment() == null) return;

        // NOTE: attack-cooldown / full-strength-swing gating used to live here, guarding
        // the old percentage-chance branch below. Build-up mutations are deliberately
        // cooldown-agnostic (spam-clicking still fills the counter), so now that every
        // mutation is a BuildUpMutation there's nothing left that gating protected.
        boolean isPlayer = damager instanceof Player;
        Player player = isPlayer ? (Player) damager : null;

        boolean isOffHandAttack = !isProjectile && isPlayer && DualWielding.offHandAttackInProgress.contains(player.getUniqueId());

        if (isOffHandAttack) {
            // During an off-hand attack DualWielding swaps the slots temporarily —
            // the off-hand sword is now in the main-hand slot. Fire procs against
            // that slot only so the mutation lands on the correct weapon.
            ItemStack swappedWeapon = damager.getEquipment().getItemInMainHand();
            this.handleWeaponProcs(damager, victim, swappedWeapon, event);
        } else if (isProjectile) {
            ItemStack bow = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling
                    .getBowFromArrow((AbstractArrow) event.getDamager(), damager instanceof Player ? (Player) damager : null);
            if (bow != null && !bow.getType().isAir()) {
                this.handleWeaponProcs(damager, victim, bow, event);
            }
        } else {
            // Normal main-hand attack (or non-player damager).
            ItemStack mainHand = damager.getEquipment().getItemInMainHand();
            this.handleWeaponProcs(damager, victim, mainHand, event);

            // Non-player entities can dual-wield (e.g. Splinter mob).
            if (!isPlayer) {
                ItemStack offHand = damager.getEquipment().getItemInOffHand();
                if (offHand != null && !offHand.getType().isAir()) {
                    this.handleWeaponProcs(damager, victim, offHand, event);
                }
            }
        }
    }

    private void handleWeaponProcs(LivingEntity damager, LivingEntity victim, ItemStack weapon, EntityDamageByEntityEvent event) {
        if (weapon == null || weapon.getType() == Material.AIR) return;

        ItemMeta meta = weapon.getItemMeta();

        Map<MutationType, Integer> itemMutations = this.getMutations(weapon, meta);
        if (itemMutations.isEmpty()) return;

        // Resolve trim profile once per hit for INCREMENTAL threshold-reduction amplification.
        // Redstone trim reduces the hits needed to proc for all INCREMENTAL mutations.
        com.example.corovaItems.ArmorTrims.PlayerTrimProfile trimProfile = null;
        if (damager instanceof Player player) {
            trimProfile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
        }

        for (Map.Entry<MutationType, Integer> entry : itemMutations.entrySet()) {
            MutationType type = entry.getKey();
            int level = entry.getValue();
            Mutation m = this.mutations.get(type);
            if (m == null) continue;

            if (!(m instanceof Mutation.BuildUpMutation buildUp)) continue;

            // BuildUp mutations are cooldown-agnostic: every hit (including spam clicks)
            // increments the counter and is eligible to proc once the threshold is reached.
            // canProc is always true so that the effect fires on the exact hit that fills
            // the bar rather than being held until the next full-strength swing.
            //
            // Redstone trim's old "+1.5% proc chance" bonus is now expressed as a
            // threshold reduction: INCREMENTAL-category mutations need 5.25% fewer
            // hits to proc instead of getting a chance-roll bonus.
            double thresholdReduction = 0.0;
            if (trimProfile != null && m.getCategories().contains(Mutation.MutationCategory.INCREMENTAL)) {
                thresholdReduction = com.example.corovaItems.ArmorTrims.TrimCalculator
                        .getAmplification(m.getCategories(), trimProfile, "incremental");
            }

            boolean procced = buildUp.incrementAndCheck(
                    damager.getUniqueId(), victim, level, weapon, damager, true, thresholdReduction);
            if (procced) {
                m.onProc(damager, victim, level, event);
            } else {
                m.onNoProc(damager, victim, level, event);
            }
        }
    }

    public boolean tryToMutate(ItemStack item) {
        return tryToMutate(item, null);
    }

    public boolean tryToMutate(ItemStack item, Player player) {
        if (item != null && item.getType() != Material.AIR) {
            double chance = this.isArmor(item) ? 2.0E-4 : 0.001;
            if (random.nextDouble() >= chance) {
                return false;
            } else {
                Map<MutationType, Integer> currentMutations = this.getMutations(item);
                if (!currentMutations.isEmpty() && random.nextDouble() < (double) 0.5F) {
                    List<MutationType> upgradable = new ArrayList();
                    for (Map.Entry<MutationType, Integer> entry : currentMutations.entrySet()) {
                        Mutation m = (Mutation) this.mutations.get(entry.getKey());
                        if (m != null && (Integer) entry.getValue() < m.getMaxLevel() && m.isCompatible(item)) {
                            upgradable.add((MutationType) entry.getKey());
                        }
                    }
                    if (!upgradable.isEmpty()) {
                        return this.upgradeMutation(item,
                                (MutationType) upgradable.get(random.nextInt(upgradable.size())), player);
                    }
                }

                boolean hasExtraSlot = currentMutations.containsKey(MutationType.EXTRA_MUTATION_SLOT);
                int normalCount = 0;
                for (MutationType t : currentMutations.keySet()) {
                    if (t != MutationType.EXTRA_MUTATION_SLOT) ++normalCount;
                }

                int maxSlots = hasExtraSlot ? 3 : 2;
                if (normalCount < maxSlots || !hasExtraSlot) {
                    List<MutationType> pool = new ArrayList();
                    boolean isScythe = MutationUtils.isScythe(item);
                    boolean isArmor = this.isArmor(item);
                    boolean isHandWeapon = MutationUtils.isSword(item)
                            || item.getType().name().endsWith("_AXE")
                            || item.getType().name().endsWith("_HOE")
                            || isScythe;
                    boolean isWandOrRod = isWandOrRod(item);

                    if (isArmor) {
                        pool.add(MutationType.BATTLE_HARDENED);
                        pool.add(MutationType.EXTRA_MUTATION_SLOT);
                        pool.add(MutationType.MYSTIC_ARCANUM);
                        if (this.isCopper(item.getType()))  pool.add(MutationType.KINETIC_CHARGE);
                        if (this.isIronArmor(item.getType())) pool.add(MutationType.HEAVY_METAL);
                        if (this.isChainmailArmor(item.getType())) pool.add(MutationType.WOUND_MENDING);
                        if (this.isNetheriteArmor(item.getType())) pool.add(MutationType.BRIMSTONE);
                        if (this.isLeather(item.getType())) pool.add(MutationType.HUNTERS_INSTINCT);
                        if (this.isGold(item.getType()))    pool.add(MutationType.GOLDEN_AEGIS);
                        if (this.isDiamondArmor(item.getType())) pool.add(MutationType.PRISMATIC_EDGE);
                        if (this.isTurtleShell(item.getType()))  pool.add(MutationType.TORTOISESHELL);
                    } else if (item.getType() == Material.SHIELD) {
                        pool.add(MutationType.SOLID_STANCE);
                    } else if (this.isPickaxe(item.getType())) {
                        pool.add(MutationType.AUTO_SMELT);
                        pool.add(MutationType.EXCAVATION);
                        pool.add(MutationType.EXTRACTOR_O_ENCHANTING);
                    } else if (MutationUtils.isBow(item)) {
                        pool.add(MutationType.DOUBLE_TAP);
                        pool.add(MutationType.TRIPLE_TAP);
                        pool.add(MutationType.ARROW_VELOCITY);
                        pool.add(MutationType.EXTRA_CUSTOM_ENCHANT_SLOT);
                        pool.add(MutationType.EXTRA_MUTATION_SLOT);
                        pool.add(MutationType.LIFE_SIPHON);
                        pool.add(MutationType.EXTRACTOR_O_ENCHANTING);
                    } else {
                        pool.add(MutationType.NETHER_FIRE);
                        pool.add(MutationType.FROST);
                        pool.add(MutationType.BLEED);
                        pool.add(MutationType.AMPLIFIER);
                        pool.add(MutationType.VENOM);
                        pool.add(MutationType.DECAY);
                        pool.add(MutationType.LIFE_STEAL);
                        pool.add(MutationType.LIFE_SIPHON);
                        pool.add(MutationType.EXTRACTOR_O_ENCHANTING);
                        pool.add(MutationType.EXTRA_MUTATION_SLOT);
                        if (isHandWeapon) {
                            pool.add(MutationType.EXTRA_CUSTOM_ENCHANT_SLOT);
                            pool.add(MutationType.BACKSTAB);
                            if (MutationUtils.isSword(item)) {
                                pool.add(MutationType.PARRY);
                            }
                            pool.add(MutationType.LAST_STAND);
                        }
                        // Axes only
                        if (item.getType().name().endsWith("_AXE")) {
                            pool.add(MutationType.BREAK_THROUGH);
                            pool.add(MutationType.SKULL_CRUSH);
                        }
                        if (isScythe) {
                            pool.add(MutationType.DICE);
                            pool.add(MutationType.FEAR);
                        }
                        if (this.isWooden(item.getType())) pool.add(MutationType.SPLINTER);
                        if (this.isStone(item.getType())) pool.add(MutationType.CLOBBER);
                        if (this.isIron(item.getType())) pool.add(MutationType.COLD_STEEL);
                        if (this.isDiamond(item.getType())) pool.add(MutationType.SHATTER);
                        if (this.isCopper(item.getType())) pool.add(MutationType.STATIC_CHARGE);
                        if (isWandOrRod) {
                            pool.add(MutationType.MANA_CONSERVATION);
                            pool.add(MutationType.SOUL_SIPHON);
                        }
                    }

                    pool.removeAll(currentMutations.keySet());
                    if (!pool.isEmpty()) {
                        MutationType selected = MutationUtils.getWeightedRandom(pool, this.mutations, random);
                        if (selected != null) {
                            // discovery should NOT replace existing mutations automatically
                            return this.addMutation(item, selected, 1, player, false);
                        }
                    }
                }
                return false;
            }
        } else {
            return false;
        }
    }

    private static boolean isWandOrRod(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String plain = ChatColor.stripColor(meta.getDisplayName());
            return plain.contains("Wand") || plain.contains("Rod");
        }
        return false;
    }

    private boolean isWooden(Material type) {
        String n = type.name();
        return n.startsWith("WOODEN_") && (n.endsWith("_SWORD") || n.endsWith("_AXE")
                || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE"));
    }

    private boolean isStone(Material type) {
        String n = type.name();
        return (n.contains("STONE_") || n.contains("COBBLESTONE_"))
                && (n.endsWith("_SWORD") || n.endsWith("_AXE")
                || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE"));
    }

    private boolean isIron(Material type) {
        String n = type.name();
        return n.startsWith("IRON_") && (n.endsWith("_SWORD") || n.endsWith("_AXE")
                || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE"));
    }

    private boolean isDiamond(Material type) {
        String n = type.name();
        return n.startsWith("DIAMOND_") && (n.endsWith("_SWORD") || n.endsWith("_AXE")
                || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE"));
    }

    private boolean isCopper(Material type) {
        return type.name().startsWith("COPPER_");
    }

    private boolean isLeather(Material type) {
        return type == Material.LEATHER_HELMET
                || type == Material.LEATHER_CHESTPLATE
                || type == Material.LEATHER_LEGGINGS
                || type == Material.LEATHER_BOOTS;
    }

    private boolean isGold(Material type) {
        return type == Material.GOLDEN_HELMET
                || type == Material.GOLDEN_CHESTPLATE
                || type == Material.GOLDEN_LEGGINGS
                || type == Material.GOLDEN_BOOTS;
    }

    private boolean isPickaxe(Material type) {
        return type.name().endsWith("_PICKAXE");
    }

    private boolean isIronArmor(Material type) {
        return type == Material.IRON_HELMET
                || type == Material.IRON_CHESTPLATE
                || type == Material.IRON_LEGGINGS
                || type == Material.IRON_BOOTS;
    }

    private boolean isChainmailArmor(Material type) {
        return type == Material.CHAINMAIL_HELMET
                || type == Material.CHAINMAIL_CHESTPLATE
                || type == Material.CHAINMAIL_LEGGINGS
                || type == Material.CHAINMAIL_BOOTS;
    }

    private boolean isNetheriteArmor(Material type) {
        return type == Material.NETHERITE_HELMET
                || type == Material.NETHERITE_CHESTPLATE
                || type == Material.NETHERITE_LEGGINGS
                || type == Material.NETHERITE_BOOTS;
    }

    private boolean isDiamondArmor(Material type) {
        return type == Material.DIAMOND_HELMET
                || type == Material.DIAMOND_CHESTPLATE
                || type == Material.DIAMOND_LEGGINGS
                || type == Material.DIAMOND_BOOTS;
    }

    private boolean isTurtleShell(Material type) {
        return type == Material.TURTLE_HELMET;
    }

    private boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String n = item.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || item.getType() == Material.TURTLE_HELMET
                || item.getType() == Material.ELYTRA;
    }

    public boolean addMutation(ItemStack item, MutationType type, int level) {
        return addMutation(item, type, level, null, true);
    }

    public boolean addMutation(ItemStack item, MutationType type, int level, Player viewer) {
        return addMutation(item, type, level, viewer, true);
    }

    public boolean addMutation(ItemStack item, MutationType type, int level, Player viewer, boolean allowReplace) {
        if (item != null && item.getType() != Material.AIR) {
            // Check if item is already at max mutations
            Map<MutationType, Integer> currentMutations = this.getMutations(item);
            if (!currentMutations.containsKey(type)) {
                boolean hasExtraSlot = currentMutations.containsKey(MutationType.EXTRA_MUTATION_SLOT);
                int normalCount = 0;
                for (MutationType t : currentMutations.keySet()) {
                    if (t != MutationType.EXTRA_MUTATION_SLOT) ++normalCount;
                }
                int maxSlots = hasExtraSlot ? 3 : 2;
                if (normalCount >= maxSlots && type != MutationType.EXTRA_MUTATION_SLOT && !allowReplace) {
                    return false;
                }
            }

            Mutation m = this.getMutation(type);
            if (m != null && !m.isCompatible(item)) return false;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            int existingSlot = this.getMutationSlot(pdc, type);
            if (existingSlot != -1) {
                pdc.set(this.getLevelKey(existingSlot), PersistentDataType.INTEGER, level);
                pdc.set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
                item.setItemMeta(meta);
                setCurrentLoreItem(null);
                this.updateLore(item, viewer);
                return true;
            }

            boolean hasExtra = currentMutations.containsKey(MutationType.EXTRA_MUTATION_SLOT);
            int normalCount = 0;
            for (MutationType t : currentMutations.keySet()) {
                if (t != MutationType.EXTRA_MUTATION_SLOT) ++normalCount;
            }
            int maxSlots = hasExtra ? 3 : 2;

            int slotToUse = -1;
            for (int i = 1; i <= 4; ++i) {
                if (!pdc.has(this.getTypeKey(i), PersistentDataType.STRING)) {
                    slotToUse = i;
                    break;
                }
            }

            if (type != MutationType.EXTRA_MUTATION_SLOT && normalCount >= maxSlots) {
                if (!allowReplace) return false;
                // Find a non-extra-slot mutation to replace.
                for (int i = 1; i <= 4; i++) {
                    String typeStr = pdc.get(this.getTypeKey(i), PersistentDataType.STRING);
                    if (typeStr != null && !typeStr.equals(MutationType.EXTRA_MUTATION_SLOT.name())) {
                        slotToUse = i;
                        break;
                    }
                }
            }

            if (slotToUse == -1) return false;

            pdc.set(this.getTypeKey(slotToUse), PersistentDataType.STRING, type.name());
            pdc.set(this.getLevelKey(slotToUse), PersistentDataType.INTEGER, level);
            pdc.set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            item.setItemMeta(meta);
            setCurrentLoreItem(null);
            this.updateLore(item, viewer);
            return true;
        }
        return false;
    }

    public boolean upgradeMutation(ItemStack item, MutationType type) {
        return upgradeMutation(item, type, null);
    }

    public boolean upgradeMutation(ItemStack item, MutationType type, Player viewer) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (!this.hasMutation(item, type, meta)) return false;
        Mutation m = (Mutation) this.mutations.get(type);
        if (m != null && !m.isCompatible(item)) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int slot = this.getMutationSlot(pdc, type);
        if (slot == -1) return false;
        NamespacedKey levelKey = this.getLevelKey(slot);
        int current = (Integer) pdc.getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
        if (m != null && current < m.getMaxLevel()) {
            pdc.set(levelKey, PersistentDataType.INTEGER, current + 1);
            pdc.set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            item.setItemMeta(meta);
            setCurrentLoreItem(null);
            this.updateLore(item, viewer);
            return true;
        }
        return false;
    }

    public boolean removeMutation(ItemStack item, MutationType type) {
        return removeMutation(item, type, null);
    }

    public boolean removeMutation(ItemStack item, MutationType type, Player viewer) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        if (!this.hasMutation(item, type, meta)) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int slot = this.getMutationSlot(pdc, type);
        if (slot != -1) {
            pdc.remove(this.getTypeKey(slot));
            pdc.remove(this.getLevelKey(slot));
            pdc.set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            item.setItemMeta(meta);
            setCurrentLoreItem(null);
            this.updateLore(item, viewer);
            return true;
        }
        return false;
    }

    public Map<MutationType, Integer> getMutations(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return Collections.emptyMap();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Collections.emptyMap();
        return getMutations(item, meta);
    }

    private Map<MutationType, Integer> getMutations(ItemStack item, ItemMeta meta) {
        // Always read straight from the PersistentDataContainer. Do NOT cache
        // this by ITEM_UUID_KEY — that key value is copied onto every clone of
        // this ItemStack, and Bukkit clones ItemStacks constantly (inventory
        // reads, anvils, pickups, internal copies). A UUID-keyed cache means a
        // stale clone can return another clone's already-overwritten mutation
        // map even though the PDC on this exact item is correct, which is why
        // mutations could silently appear to "not work" (procs skipped, lore
        // not updating, discovery re-rolling as if no mutation was present).
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Map<MutationType, Integer> result = new HashMap<>();
        for (int i = 1; i <= 4; ++i) {
            NamespacedKey typeKey = this.getTypeKey(i);
            if (pdc.has(typeKey, PersistentDataType.STRING)) {
                try {
                    MutationType type = MutationType.valueOf(
                            (String) pdc.get(typeKey, PersistentDataType.STRING));
                    int lvl = (Integer) pdc.getOrDefault(
                            this.getLevelKey(i), PersistentDataType.INTEGER, 0);
                    result.put(type, lvl);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // ITEM_UUID_KEY is kept (other systems / merge-history tracking may
        // rely on it existing), but it's no longer used as a cache key here.
        if (!pdc.has(ITEM_UUID_KEY, PersistentDataType.STRING)) {
            pdc.set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            item.setItemMeta(meta);
        }

        return Collections.unmodifiableMap(result);
    }

    private NamespacedKey getTypeKey(int slot) {
        return switch (slot) {
            case 1 -> MUTATION_1_TYPE_KEY;
            case 2 -> MUTATION_2_TYPE_KEY;
            case 3 -> MUTATION_3_TYPE_KEY;
            case 4 -> MUTATION_4_TYPE_KEY;
            default -> null;
        };
    }

    private NamespacedKey getLevelKey(int slot) {
        return switch (slot) {
            case 1 -> MUTATION_1_LEVEL_KEY;
            case 2 -> MUTATION_2_LEVEL_KEY;
            case 3 -> MUTATION_3_LEVEL_KEY;
            case 4 -> MUTATION_4_LEVEL_KEY;
            default -> null;
        };
    }

    public boolean hasMutation(ItemStack item, MutationType type) {
        return this.getMutations(item).containsKey(type);
    }

    public boolean hasMutation(ItemStack item, MutationType type, ItemMeta meta) {
        return this.getMutations(item, meta).containsKey(type);
    }

    public int getMutationLevel(ItemStack item, MutationType type) {
        return (Integer) this.getMutations(item).getOrDefault(type, 0);
    }

    public double getCombinedSynergyMultiplier(ItemStack item) {
        double total = 0.0;
        Map<MutationType, Integer> itemMutations = this.getMutations(item);
        for (Map.Entry<MutationType, Integer> entry : itemMutations.entrySet()) {
            Mutation m = this.mutations.get(entry.getKey());
            if (m != null) {
                total += m.getSynergyMultiplier(entry.getValue());
            }
        }
        return total;
    }

    public void triggerMutations(LivingEntity damager, LivingEntity victim, ItemStack weapon) {
        if (weapon == null || weapon.getType().isAir()) return;

        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;
        Map<MutationType, Integer> itemMutations = this.getMutations(weapon, meta);
        if (itemMutations.isEmpty()) return;

        for (Map.Entry<MutationType, Integer> entry : itemMutations.entrySet()) {
            Mutation m = this.mutations.get(entry.getKey());
            if (m == null) continue;

            if (!(m instanceof Mutation.BuildUpMutation buildUp)) {
                // Every mutation is a BuildUpMutation now — nothing left to roll RNG for.
                continue;
            }

            boolean procced = buildUp.incrementAndCheck(
                    damager.getUniqueId(), victim, entry.getValue(), weapon, damager, true);
            if (procced) {
                m.onProcSynergy(damager, victim, entry.getValue());
            } else {
                m.onNoProcSynergy(damager, victim, entry.getValue());
            }
        }
    }

    private int getMutationSlot(PersistentDataContainer pdc, MutationType type) {
        for (int i = 1; i <= 4; ++i) {
            String s = (String) pdc.get(this.getTypeKey(i), PersistentDataType.STRING);
            if (s != null && s.equals(type.name())) return i;
        }
        return -1;
    }

    public void transferMutations(ItemStack source, ItemStack target) {
        transferMutations(source, target, null);
    }

    public void transferMutations(ItemStack source, ItemStack target, Player viewer) {
        if (source != null && target != null && source.getType() != Material.AIR && target.getType() != Material.AIR) {
            ItemMeta srcMeta = source.getItemMeta();
            ItemMeta tgtMeta = target.getItemMeta();
            if (srcMeta == null || tgtMeta == null) return;
            PersistentDataContainer src = srcMeta.getPersistentDataContainer();
            PersistentDataContainer tgt = tgtMeta.getPersistentDataContainer();
            boolean had = false;
            for (int i = 1; i <= 4; ++i) {
                NamespacedKey tk = this.getTypeKey(i);
                NamespacedKey lk = this.getLevelKey(i);
                if (src.has(tk, PersistentDataType.STRING)) {
                    tgt.set(tk, PersistentDataType.STRING, (String) src.get(tk, PersistentDataType.STRING));
                    tgt.set(lk, PersistentDataType.INTEGER, (Integer) src.get(lk, PersistentDataType.INTEGER));
                    had = true;
                }
            }
            if (had) {
                tgt.set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
                target.setItemMeta(tgtMeta);
                setCurrentLoreItem(null);
                this.updateLore(target, viewer);
            }
        }
    }

    /**
     * Rebuilds the item's lore in canonical order:
     * base lore → custom enchants → mutations
     * <p>
     * This replaces the old "append mutations at the bottom" approach.
     * Delegating to {@link CustomEnchantMutationFormatting#rebuildLore} ensures
     * that no matter which system (enchant or mutation) writes last, the order
     * is always enforced.
     */
    public static class MergeResult {
        public final int cost;
        public final boolean changed;

        public MergeResult(int cost, boolean changed) {
            this.cost = cost;
            this.changed = changed;
        }
    }

    public MergeResult mergeMutations(ItemStack left, ItemStack right) {
        return mergeMutations(left, right, null);
    }

    public MergeResult mergeMutations(ItemStack left, ItemStack right, Player viewer) {
        int totalCost = 0;
        boolean changed = false;
        Map<MutationType, Integer> rightMutations = getMutations(right);

        // Handle EXTRA_MUTATION_SLOT first to ensure slot availability
        if (rightMutations.containsKey(MutationType.EXTRA_MUTATION_SLOT)) {
            MutationType type = MutationType.EXTRA_MUTATION_SLOT;
            int rightLvl = rightMutations.get(type);
            if (hasMutation(left, type)) {
                int leftLvl = getMutationLevel(left, type);
                Mutation m = mutations.get(type);
                if (rightLvl == leftLvl && leftLvl < (m != null ? m.getMaxLevel() : 1)) {
                    if (upgradeMutation(left, type, viewer)) {
                        totalCost += 10;
                        changed = true;
                    }
                } else if (rightLvl > leftLvl) {
                    removeMutation(left, type, viewer);
                    addMutation(left, type, rightLvl, viewer, true);
                    totalCost += 1;
                    changed = true;
                }
            } else {
                if (addMutation(left, type, rightLvl, viewer, true)) {
                    totalCost += 1;
                    changed = true;
                }
            }
        }

        for (Map.Entry<MutationType, Integer> entry : rightMutations.entrySet()) {
            MutationType type = entry.getKey();
            if (type == MutationType.EXTRA_MUTATION_SLOT) continue;
            int rightLvl = entry.getValue();

            if (hasMutation(left, type)) {
                int leftLvl = getMutationLevel(left, type);
                Mutation m = mutations.get(type);
                if (rightLvl == leftLvl && leftLvl < (m != null ? m.getMaxLevel() : 1)) {
                    if (upgradeMutation(left, type, viewer)) {
                        totalCost += 10;
                        changed = true;
                    }
                } else if (rightLvl > leftLvl) {
                    removeMutation(left, type, viewer);
                    addMutation(left, type, rightLvl, viewer, true);
                    totalCost += 1;
                    changed = true;
                }
            } else {
                if (addMutation(left, type, rightLvl, viewer, true)) {
                    totalCost += 1;
                    changed = true;
                }
            }
        }
        return new MergeResult(totalCost, changed);
    }

    public void removeAllMutations(ItemStack item) {
        removeAllMutations(item, null);
    }

    public void removeAllMutations(ItemStack item, Player viewer) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (int i = 1; i <= 4; i++) {
            pdc.remove(this.getTypeKey(i));
            pdc.remove(this.getLevelKey(i));
        }
        pdc.set(ITEM_UUID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
        item.setItemMeta(meta);
        this.updateLore(item, viewer);
    }

    public void updateLore(ItemStack item, Player viewer) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        updateLore(item, meta, viewer);
        item.setItemMeta(meta);
        // Re-sync after setItemMeta: syncVisuals() (called inside updateLore(item, meta, viewer)
        // above) writes CUSTOM_MODEL_DATA directly onto `item` via item.setData(...), but that
        // happens *before* this setItemMeta call. Since `meta` was snapshotted before syncVisuals
        // ran, setItemMeta(meta) overwrites item's components with the stale snapshot and silently
        // reverts the flags/colors syncVisuals just set. Re-running it here, after the meta write,
        // makes it the last word so the glow actually sticks.
        MutationVisuals.syncVisuals(item, this.getMutations(item, meta), this::getMutation);
    }

    public void updateLore(ItemStack item, ItemMeta meta, Player viewer) {
        if (item == null || meta == null) return;

        setCurrentLoreItem(item);

        // Lore rebuilding is now handled incrementally by CustomEnchantMutationFormatting
        // to preserve base lore while ensuring canonical order. Wiping lore here
        // would destroy user-set lore or item descriptions.

        for (Mutation m : this.mutations.values()) {
            m.removeAttributes(item, meta);
        }
        Map<MutationType, Integer> current = this.getMutations(item, meta);
        MutationVisuals.syncVisuals(item, current, this::getMutation);
        int totalDurabilityBonus = 0;
        for (Map.Entry<MutationType, Integer> entry : current.entrySet()) {
            Mutation m = this.mutations.get(entry.getKey());
            if (m != null) {
                m.applyAttributes(item, meta, entry.getValue());
                totalDurabilityBonus += m.getDurabilityBonus(entry.getValue());
            }
        }

        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            if (totalDurabilityBonus > 0) {
                damageable.setMaxDamage(item.getType().getMaxDurability() + totalDurabilityBonus);
            } else {
                damageable.setMaxDamage((int) item.getType().getMaxDurability()); // Resets to default
            }
        }

        CustomEnchantMutationFormatting.rebuildLore(item, meta);

        // Re-inject vanilla enchant lore lines
        // (This also strips any existing preview lore)
        com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.refreshDisplay(item, meta);

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        // MutationUtils.appendSynergyLore(item, lore); // Moved to /item info

        // Basic per-piece trim lore is now handled by rebuildLore above to ensure
        // correct canonical ordering (at the bottom of managed blocks).

        // Viewer-dependent lines (amplifications, passives summary, adaptive set bonus).
        List<String> trimLines = new ArrayList<>();

        if (viewer != null) {
            com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile =
                    com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(viewer);

            // Mutation category amplification lines
            for (Map.Entry<MutationType, Integer> entry : current.entrySet()) {
                Mutation m = mutations.get(entry.getKey());
                if (m != null && !m.getCategories().isEmpty()) {
                    trimLines.addAll(
                            com.example.corovaItems.ArmorTrims.TrimCalculator
                                    .describeAmplifications(m.getCategories(), profile));
                }
            }

            // Total passives summary (only on armor pieces)
            if (isArmor(item)) {
                List<String> passives =
                        com.example.corovaItems.ArmorTrims.TrimCalculator.describeGeneralPassives(profile);
                if (!passives.isEmpty()) {
                    trimLines.add("§6Total Trim Buffs:");
                    trimLines.addAll(passives);
                }
            }

            // Adaptive set bonus block — scales from 1 piece, empty when nothing worn.
            // describeSetBonuses returns an empty list when the profile has 0 trims,
            // which is exactly what clears the lore when armor is removed.
            trimLines.addAll(
                    com.example.corovaItems.ArmorTrims.TrimCalculator
                            .getTrimSetBonusLoreForViewer(item, profile));
        }
        // viewer == null → trimLines stays empty → set-bonus block is not appended,
        // effectively stripping it from pieces that were just unequipped.

        if (!trimLines.isEmpty()) {
            if (!lore.isEmpty() && !lore.get(lore.size() - 1).trim().isEmpty()) {
                lore.add(""); // Spacer before viewer-dependent trim section
            }
            // Use ArrayList — NOT LinkedHashSet — to preserve all lines including
            // those with identical text (e.g. two materials with the same desc).
            lore.addAll(trimLines);
        }

        meta.setLore(lore);
        setCurrentLoreItem(null);
    }
}