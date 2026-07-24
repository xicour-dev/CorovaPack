//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class VeinMinerBook extends EnchantmentBook implements Listener {
    private static final int MAX_VEIN_SIZE = 64;
    private static final Random RANDOM = new Random();
    private static final Set<Material> ORE_MATERIALS;

    public VeinMinerBook() {
        this(1);
    }

    public VeinMinerBook(int level) {
        super("Book of Vein Miner", "veinminer", level, "book_veinminer", allowedMaterialsStatic());
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterialsStatic() {
        return Set.of(Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (CorovaEnchantments.hasEnchant(tool, "veinminer")) {
                Block origin = event.getBlock();
                Material oreType = origin.getType();
                if (ORE_MATERIALS.contains(oreType)) {
                    List<Block> vein = this.findVein(origin, oreType);
                    int unbreakingLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);

                    for(Block block : vein) {
                        if (!block.equals(origin)) {
                            block.breakNaturally(tool);
                            this.damageTool(player, tool, unbreakingLevel);
                        }
                    }

                }
            }
        }
    }

    private void damageTool(Player player, ItemStack tool, int unbreakingLevel) {
        if (unbreakingLevel <= 0 || RANDOM.nextInt(unbreakingLevel + 1) == 0) {
            ItemMeta meta = tool.getItemMeta();
            if (meta instanceof Damageable) {
                Damageable damageable = (Damageable)meta;
                int newDamage = damageable.getDamage() + 1;
                short maxDurability = tool.getType().getMaxDurability();
                if (newDamage >= maxDurability) {
                    tool.setAmount(0);
                    String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.VEIN_MINER_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.VEIN_MINER_ID, "Vein Miner"));
                    player.sendMessage(org.bukkit.ChatColor.RED + "Your pickaxe broke from " + enchantName + org.bukkit.ChatColor.RED + "!");
                } else {
                    damageable.setDamage(newDamage);
                    tool.setItemMeta(meta);
                }

            }
        }
    }

    private List<Block> findVein(Block origin, Material type) {
        List<Block> vein = new ArrayList();
        Set<String> visited = new HashSet();
        Queue<Block> queue = new LinkedList();
        queue.add(origin);
        visited.add(this.blockKey(origin));

        while(!queue.isEmpty() && vein.size() < 64) {
            Block current = (Block)queue.poll();
            vein.add(current);

            for(Block neighbor : this.getAdjacentBlocks(current)) {
                String key = this.blockKey(neighbor);
                if (!visited.contains(key) && neighbor.getType() == type) {
                    visited.add(key);
                    queue.add(neighbor);
                }
            }
        }

        return vein;
    }

    private List<Block> getAdjacentBlocks(Block block) {
        List<Block> neighbors = new ArrayList(26);
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();

        for(int dx = -1; dx <= 1; ++dx) {
            for(int dy = -1; dy <= 1; ++dy) {
                for(int dz = -1; dz <= 1; ++dz) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        neighbors.add(block.getWorld().getBlockAt(bx + dx, by + dy, bz + dz));
                    }
                }
            }
        }

        return neighbors;
    }

    private String blockKey(Block block) {
        int var10000 = block.getX();
        return var10000 + "," + block.getY() + "," + block.getZ();
    }

    static {
        ORE_MATERIALS = Set.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.NETHER_GOLD_ORE, Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS, Material.RAW_IRON_BLOCK, Material.RAW_COPPER_BLOCK, Material.RAW_GOLD_BLOCK);
    }
}
