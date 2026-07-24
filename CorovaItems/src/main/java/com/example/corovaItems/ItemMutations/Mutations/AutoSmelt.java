package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class AutoSmelt implements Mutation, Listener {

    private final MutationManager mutationManager;
    private final NamespacedKey toggleKey;
    private final Random random = new Random();

    /**
     * STATIC debounce map — survives across any number of AutoSmelt instances.
     * Paper fires PlayerInteractEvent twice per click. Both fires land in the
     * same server tick (same ms), so a per-instance map can be empty for both
     * fires if somehow two instances exist. Making it static guarantees the
     * second fire always sees the timestamp written by the first.
     */
    private static final Map<UUID, Long> lastToggleTime = new HashMap<>();
    private static final long DEBOUNCE_MS = 250;

    private static final Map<Material, Material> SMELT_MAP = new HashMap<>();

    private static final Set<Material> INTERACTABLE_BLOCKS = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.SHULKER_BOX,
            Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX, Material.LIME_SHULKER_BOX,
            Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX, Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
            Material.ENDER_CHEST, Material.HOPPER,
            Material.DISPENSER, Material.DROPPER,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.CRAFTING_TABLE, Material.ANVIL, Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL, Material.ENCHANTING_TABLE,
            Material.GRINDSTONE, Material.SMITHING_TABLE, Material.STONECUTTER,
            Material.LOOM, Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE,
            Material.BREWING_STAND, Material.BEACON,
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR,
            Material.JUNGLE_DOOR, Material.ACACIA_DOOR, Material.DARK_OAK_DOOR,
            Material.MANGROVE_DOOR, Material.CHERRY_DOOR, Material.BAMBOO_DOOR,
            Material.CRIMSON_DOOR, Material.WARPED_DOOR, Material.IRON_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR,
            Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR,
            Material.ACACIA_TRAPDOOR, Material.DARK_OAK_TRAPDOOR,
            Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR,
            Material.BAMBOO_TRAPDOOR, Material.CRIMSON_TRAPDOOR,
            Material.WARPED_TRAPDOOR, Material.IRON_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE,
            Material.BIRCH_FENCE_GATE, Material.JUNGLE_FENCE_GATE,
            Material.ACACIA_FENCE_GATE, Material.DARK_OAK_FENCE_GATE,
            Material.MANGROVE_FENCE_GATE, Material.CHERRY_FENCE_GATE,
            Material.BAMBOO_FENCE_GATE, Material.CRIMSON_FENCE_GATE,
            Material.WARPED_FENCE_GATE,
            Material.OAK_BUTTON, Material.SPRUCE_BUTTON, Material.BIRCH_BUTTON,
            Material.JUNGLE_BUTTON, Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON, Material.MANGROVE_BUTTON,
            Material.CHERRY_BUTTON, Material.BAMBOO_BUTTON,
            Material.CRIMSON_BUTTON, Material.WARPED_BUTTON,
            Material.STONE_BUTTON, Material.POLISHED_BLACKSTONE_BUTTON,
            Material.LEVER,
            Material.JUKEBOX, Material.NOTE_BLOCK, Material.CAKE,
            Material.CAULDRON, Material.WATER_CAULDRON, Material.LAVA_CAULDRON,
            Material.POWDER_SNOW_CAULDRON, Material.COMPOSTER,
            Material.BELL, Material.RESPAWN_ANCHOR, Material.LODESTONE,
            Material.DAYLIGHT_DETECTOR, Material.CHISELED_BOOKSHELF
    );

    public AutoSmelt(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        this.toggleKey = new NamespacedKey(plugin, "autosmelt_enabled");
    }

    // -------------------------------------------------------------------------
    // Mutation metadata
    // -------------------------------------------------------------------------

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() { return "#FF8C00"; }

    @Override
    public String getName() { return "Auto Smelt"; }

    @Override
    public int getMaxLevel() { return 1; }

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
        desc.add(ChatColor.GRAY + "Blocks mined drop their smelted result.");
        desc.add(ChatColor.GRAY + "Silk Touch: sneak while mining to smelt instead.");
        desc.add(ChatColor.GRAY + "Right-click to toggle ON/OFF.");
        desc.add(ChatColor.DARK_GRAY + "Pickaxes only.");
        return desc;
    }

    @Override
    public MutationType getType() { return MutationType.AUTO_SMELT; }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    // -------------------------------------------------------------------------
    // Toggle helpers
    // -------------------------------------------------------------------------

    private boolean isEnabled(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Byte value = pdc.get(toggleKey, PersistentDataType.BYTE);
        return value == null || value != 0;
    }

    private boolean toggle(Player player) {
        boolean newState = !isEnabled(player);
        player.getPersistentDataContainer().set(
                toggleKey, PersistentDataType.BYTE, (byte) (newState ? 1 : 0));
        return newState;
    }

    // -------------------------------------------------------------------------
    // Right-click toggle
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isPickaxe(item)) return;
        if (!this.mutationManager.hasMutation(item, MutationType.AUTO_SMELT)) return;

        // Skip interactable blocks so chests/furnaces/doors still work normally.
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (INTERACTABLE_BLOCKS.contains(event.getClickedBlock().getType())) return;
        }

        // Debounce: Paper fires PlayerInteractEvent twice per physical click.
        // Both fires land within the same millisecond. The map is static so
        // it is shared across any number of AutoSmelt instances — the second
        // fire always sees the timestamp the first fire wrote.
        long now = System.currentTimeMillis();
        UUID uid = player.getUniqueId();
        Long last = lastToggleTime.get(uid);
        if (last != null && (now - last) < DEBOUNCE_MS) return;
        lastToggleTime.put(uid, now);

        boolean nowEnabled = toggle(player);

        player.sendActionBar(nowEnabled
                ? ChatColor.GREEN + "Auto Smelt: ON"
                : ChatColor.RED   + "Auto Smelt: OFF");

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);

        if (action == Action.RIGHT_CLICK_AIR) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Block-break smelting
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack pickaxe = player.getInventory().getItemInMainHand();

        if (!isPickaxe(pickaxe)) return;
        if (!this.mutationManager.hasMutation(pickaxe, MutationType.AUTO_SMELT)) return;
        if (!isEnabled(player)) return;

        boolean hasSilkTouch = pickaxe.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        if (hasSilkTouch && !player.isSneaking()) return;

        ItemStack toolForDrops = hasSilkTouch ? stripSilkTouch(pickaxe) : pickaxe;

        List<ItemStack> rawDrops = new ArrayList<>(event.getBlock().getDrops(toolForDrops, player));
        if (rawDrops.isEmpty()) return;

        List<ItemStack> finalDrops = new ArrayList<>(rawDrops.size());
        boolean anyReplaced = false;

        for (ItemStack drop : rawDrops) {
            Material smelted = SMELT_MAP.get(drop.getType());
            if (smelted != null) {
                finalDrops.add(new ItemStack(smelted, drop.getAmount()));
                anyReplaced = true;
            } else {
                finalDrops.add(drop);
            }
        }

        if (anyReplaced) {
            event.setDropItems(false);
            double bonusProc = com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player), "incremental");
            for (ItemStack drop : finalDrops) {
                ItemStack spawnDrop = drop;
                if (random.nextDouble() < bonusProc) {
                    spawnDrop = drop.clone();
                    spawnDrop.setAmount(spawnDrop.getAmount() * 2);
                }
                event.getBlock().getWorld().dropItemNaturally(
                        event.getBlock().getLocation(), spawnDrop);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ItemStack stripSilkTouch(ItemStack original) {
        ItemStack copy = original.clone();
        copy.removeEnchantment(Enchantment.SILK_TOUCH);
        return copy;
    }

    private boolean isPickaxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_PICKAXE");
    }

    // -------------------------------------------------------------------------
    // Smelt map
    // -------------------------------------------------------------------------

    static {
        SMELT_MAP.put(Material.RAW_IRON,             Material.IRON_INGOT);
        SMELT_MAP.put(Material.RAW_GOLD,             Material.GOLD_INGOT);
        SMELT_MAP.put(Material.RAW_COPPER,           Material.COPPER_INGOT);
        SMELT_MAP.put(Material.ANCIENT_DEBRIS,       Material.NETHERITE_SCRAP);
        SMELT_MAP.put(Material.IRON_ORE,             Material.IRON_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_IRON_ORE,   Material.IRON_INGOT);
        SMELT_MAP.put(Material.GOLD_ORE,             Material.GOLD_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_GOLD_ORE,   Material.GOLD_INGOT);
        SMELT_MAP.put(Material.NETHER_GOLD_ORE,      Material.GOLD_NUGGET);
        SMELT_MAP.put(Material.COPPER_ORE,           Material.COPPER_INGOT);
        SMELT_MAP.put(Material.DEEPSLATE_COPPER_ORE, Material.COPPER_INGOT);
        SMELT_MAP.put(Material.SAND,                 Material.GLASS);
        SMELT_MAP.put(Material.COBBLESTONE,          Material.STONE);
        SMELT_MAP.put(Material.BLACKSTONE,           Material.POLISHED_BLACKSTONE);
        SMELT_MAP.put(Material.BASALT,               Material.SMOOTH_BASALT);
        SMELT_MAP.put(Material.COBBLED_DEEPSLATE,    Material.DEEPSLATE);
        SMELT_MAP.put(Material.CLAY_BALL,            Material.BRICK);
        SMELT_MAP.put(Material.CLAY,                 Material.TERRACOTTA);
        SMELT_MAP.put(Material.NETHERRACK,           Material.NETHER_BRICK);
        SMELT_MAP.put(Material.STONE,                Material.SMOOTH_STONE);
        SMELT_MAP.put(Material.SANDSTONE,            Material.SMOOTH_SANDSTONE);
        SMELT_MAP.put(Material.RED_SANDSTONE,        Material.SMOOTH_RED_SANDSTONE);
        SMELT_MAP.put(Material.QUARTZ_BLOCK,         Material.SMOOTH_QUARTZ);
    }
}