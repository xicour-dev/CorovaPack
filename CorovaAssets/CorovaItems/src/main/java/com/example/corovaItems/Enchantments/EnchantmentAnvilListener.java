package com.example.corovaItems.Enchantments;

import com.example.corovaItems.Enchantments.EnchantBooks.*;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class EnchantmentAnvilListener implements Listener {
    private final JavaPlugin plugin;

    // Keys for reading enchant book PDC (stored on the book item itself)
    private final NamespacedKey keyId;
    private final NamespacedKey keyLvl;

    // Keys for reading/writing weapon enchant PDC.
    // Created here in the constructor so they are NEVER null, regardless of whether
    // CorovaEnchantments.init(plugin) has been called yet.
    private final NamespacedKey weaponEnchantId;
    private final NamespacedKey weaponEnchantLvl;
    private final NamespacedKey weaponEnchant2Id;
    private final NamespacedKey weaponEnchant2Lvl;

    private final NamespacedKey keyMergeCost;

    private final Map<Player, Inventory> openAnvils = new HashMap<>();

    public EnchantmentAnvilListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.keyId  = new NamespacedKey(plugin, "corova_enchant_book_id");
        this.keyLvl = new NamespacedKey(plugin, "corova_enchant_book_level");

        // These must exactly match the key strings used in CorovaEnchantments.init()
        this.weaponEnchantId   = new NamespacedKey(plugin, "corova_enchant_id");
        this.weaponEnchantLvl  = new NamespacedKey(plugin, "corova_enchant_level");
        this.weaponEnchant2Id  = new NamespacedKey(plugin, "corova_enchant_2_id");
        this.weaponEnchant2Lvl = new NamespacedKey(plugin, "corova_enchant_2_level");

        this.keyMergeCost      = new NamespacedKey(plugin, "corova_anvil_merge_cost");
    }

    // =========================================================================
    // Material compatibility is checked via EnchantmentBook.canEnchantApplyTo().
    //
    // That method reads from a static registry (EnchantmentBook.REGISTRY) that
    // is populated automatically when each book is constructed in registerAllBooks().
    //
    // To change what items an enchant can go on: edit the allowedMaterials set
    // in the corresponding EnchantmentBook subclass. Nothing here needs touching.
    // =========================================================================

    public void openCustomAnvil(Player player) {
        Inventory anvil = Bukkit.createInventory(null, InventoryType.ANVIL, "Custom Enchant Anvil");
        player.openInventory(anvil);
        openAnvils.put(player, anvil);
    }

    private void updateAnvilResult(Inventory anvil) {
        ItemStack left  = anvil.getItem(0);
        ItemStack right = anvil.getItem(1);
        anvil.setItem(2, null);

        if (left == null || left.getType() == Material.AIR ||
                right == null || right.getType() == Material.AIR) {
            return;
        }

        // --- Book Upgrade Logic (Book + Book = Upgraded Book) ---
        if (left.getType() == Material.ENCHANTED_BOOK && right.getType() == Material.ENCHANTED_BOOK) {
            ItemStack upgradedBook = SoulProjection.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = PoisonBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = WitherBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = NapalmBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = StormBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = FlightBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = PaydayBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = ThrustBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = Flare.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = Missile.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            // ArrowRain is single-level — no book+book upgrade path needed

            upgradedBook = SteedBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = DivinumTrabemBook.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }

            upgradedBook = CosmicRay.getUpgradedBook(left, right, keyId, keyLvl);
            if (upgradedBook != null) { anvil.setItem(2, upgradedBook); return; }
        }

        // --- Item + Item Merging Logic ---
        if (left.getType() == right.getType() && left.getType() != Material.ENCHANTED_BOOK) {
            ItemStack result = left.clone();
            boolean changed = false;
            int totalCost = 0;

            // 1. Vanilla Enchants Merging
            Map<org.bukkit.enchantments.Enchantment, Integer> leftVanilla = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.getAllTrueLevels(left);
            Map<org.bukkit.enchantments.Enchantment, Integer> rightVanilla = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.getAllTrueLevels(right);

            for (Map.Entry<org.bukkit.enchantments.Enchantment, Integer> entry : rightVanilla.entrySet()) {
                org.bukkit.enchantments.Enchantment ench = entry.getKey();
                int rightLvl = entry.getValue();
                if (leftVanilla.containsKey(ench)) {
                    int leftLvl = leftVanilla.get(ench);
                    if (leftLvl == rightLvl && leftLvl < ench.getMaxLevel()) {
                        com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.applyWithDisplay(result, ench, leftLvl + 1);
                        totalCost += (leftLvl + 1); // Vanilla-like cost
                        changed = true;
                    } else if (rightLvl > leftLvl) {
                        com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.applyWithDisplay(result, ench, rightLvl);
                        totalCost += rightLvl;
                        changed = true;
                    }
                } else {
                    com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.applyWithDisplay(result, ench, rightLvl);
                    totalCost += rightLvl;
                    changed = true;
                }
            }

            // 2. Mutation Merging (Moved before custom enchants to unlock slots)
            boolean leftHasMutations = hasAnyMutation(left);
            boolean rightHasMutations = hasAnyMutation(right);

            if (!(leftHasMutations && rightHasMutations)) {
                MutationManager.MergeResult mResult = MutationManager.getInstance().mergeMutations(result, right);
                if (mResult.changed) {
                    totalCost += mResult.cost;
                    changed = true;
                }
            }

            // 3. Custom Enchants Merging
            CorovaEnchantments.MergeResult ceResult = CorovaEnchantments.mergeEnchants(result, right);
            if (ceResult.changed) {
                totalCost += ceResult.cost;
                changed = true;
            }

            if (changed) {
                ItemMeta meta = result.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(keyMergeCost, PersistentDataType.INTEGER, Math.max(1, totalCost));
                    result.setItemMeta(meta);

                    // Standardize preview lore rebuilding in the custom anvil.
                    // We call updateLore with null player context because the viewer is unknown at this stage
                    // of the prepare-event, but it correctly re-asserts canonical order for mutations/enchants.
                    MutationManager.getInstance().updateLore(result, null);
                }
                anvil.setItem(2, result);
                return;
            }
        }

        // --- Item Enchanting/Upgrading Logic (Weapon/Tool + Book) ---
        if (right.getType() == Material.ENCHANTED_BOOK) {
            ItemMeta rightMeta = right.getItemMeta();
            if (rightMeta instanceof EnchantmentStorageMeta bookMeta) {
                PersistentDataContainer bookPdc = bookMeta.getPersistentDataContainer();
                if (bookPdc.has(keyId, PersistentDataType.STRING)) {
                    String  bookEnchantId = bookPdc.get(keyId,  PersistentDataType.STRING);
                    Integer bookLevel     = bookPdc.get(keyLvl, PersistentDataType.INTEGER);

                    if (bookEnchantId != null && bookLevel != null) {
                        // Compatibility check: each book's own allowedMaterials set is
                        // the authority. Edit the book class to change what it can apply to.
                        if (!EnchantmentBook.canEnchantApplyTo(bookEnchantId, left.getType())) {
                            return; // incompatible item — leave result slot empty
                        }

                        ItemMeta leftMeta = left.getItemMeta();
                        if (leftMeta != null) {
                            PersistentDataContainer leftPdc = leftMeta.getPersistentDataContainer();
                            String  id1  = leftPdc.get(weaponEnchantId,   PersistentDataType.STRING);
                            Integer lvl1 = leftPdc.get(weaponEnchantLvl,  PersistentDataType.INTEGER);
                            String  id2  = leftPdc.get(weaponEnchant2Id,  PersistentDataType.STRING);
                            Integer lvl2 = leftPdc.get(weaponEnchant2Lvl, PersistentDataType.INTEGER);

                            if (bookEnchantId.equals(id1)) {
                                int maxLevel = CorovaEnchantments.getMaxLevel(bookEnchantId);
                                if (bookLevel.equals(lvl1) && lvl1 < maxLevel) {
                                    ItemStack result = left.clone();
                                    CorovaEnchantments.applyEnchant(result, bookEnchantId, lvl1 + 1, 1);
                                    MutationManager.getInstance().updateLore(result, null);
                                    anvil.setItem(2, result);
                                    return;
                                } else if (bookLevel > lvl1) {
                                    ItemStack result = left.clone();
                                    CorovaEnchantments.applyEnchant(result, bookEnchantId, bookLevel, 1);
                                    MutationManager.getInstance().updateLore(result, null);
                                    anvil.setItem(2, result);
                                    return;
                                }
                                return; // same enchant, can't upgrade — no result
                            }

                            if (bookEnchantId.equals(id2)) {
                                int maxLevel = CorovaEnchantments.getMaxLevel(bookEnchantId);
                                if (bookLevel.equals(lvl2) && lvl2 < maxLevel) {
                                    ItemStack result = left.clone();
                                    CorovaEnchantments.applyEnchant(result, bookEnchantId, lvl2 + 1, 2);
                                    MutationManager.getInstance().updateLore(result, null);
                                    anvil.setItem(2, result);
                                    return;
                                } else if (bookLevel > lvl2) {
                                    ItemStack result = left.clone();
                                    CorovaEnchantments.applyEnchant(result, bookEnchantId, bookLevel, 2);
                                    MutationManager.getInstance().updateLore(result, null);
                                    anvil.setItem(2, result);
                                    return;
                                }
                                return; // same enchant, can't upgrade — no result
                            }

                            // If not the same enchant, find a slot or replace
                            if (id1 == null) {
                                ItemStack result = left.clone();
                                CorovaEnchantments.applyEnchant(result, bookEnchantId, bookLevel, 1);
                                MutationManager.getInstance().updateLore(result, null);
                                anvil.setItem(2, result);
                            } else if (MutationManager.getInstance().hasMutation(left, MutationType.EXTRA_CUSTOM_ENCHANT_SLOT)) {
                                if (id2 == null) {
                                    ItemStack result = left.clone();
                                    CorovaEnchantments.applyEnchant(result, bookEnchantId, bookLevel, 2);
                                    MutationManager.getInstance().updateLore(result, null);
                                    anvil.setItem(2, result);
                                } else {
                                    // Both slots full, replace slot 1
                                    ItemStack result = left.clone();
                                    CorovaEnchantments.applyEnchant(result, bookEnchantId, bookLevel, 1);
                                    MutationManager.getInstance().updateLore(result, null);
                                    anvil.setItem(2, result);
                                }
                            } else {
                                // No extra slot, replace slot 1
                                ItemStack result = left.clone();
                                CorovaEnchantments.applyEnchant(result, bookEnchantId, bookLevel, 1);
                                MutationManager.getInstance().updateLore(result, null);
                                anvil.setItem(2, result);
                            }

                        } else {
                            // No meta at all — treat as empty, apply to slot 1
                            ItemStack result = left.clone();
                            CorovaEnchantments.applyEnchant(result, bookEnchantId, bookLevel, 1);
                            MutationManager.getInstance().updateLore(result, null);
                            anvil.setItem(2, result);
                        }
                    }
                }
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openAnvils.containsKey(player)) return;
        if (!event.getInventory().equals(openAnvils.get(player))) return;

        Inventory anvil = openAnvils.get(player);
        int slot = event.getRawSlot();

        if (slot >= 0 && slot <= 2) {
            // ── Slot 2: result slot ───────────────────────────────────────────
            if (slot == 2) {
                event.setCancelled(true);
                ItemStack result = anvil.getItem(2);
                if (result == null || result.getType() == Material.AIR) return;

                ItemStack left  = anvil.getItem(0);
                ItemStack right = anvil.getItem(1);
                if (left == null || right == null) return;

                ItemMeta rightMeta = right.getItemMeta();
                if (rightMeta == null) return;
                EnchantmentStorageMeta bookMeta = (rightMeta instanceof EnchantmentStorageMeta) ? (EnchantmentStorageMeta) rightMeta : null;

                boolean isBookUpgrade = left.getType() == Material.ENCHANTED_BOOK &&
                        right.getType() == Material.ENCHANTED_BOOK;

                int cost;
                if (isBookUpgrade) {
                    if (bookMeta == null) return;
                    String enchantId = bookMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
                    cost = enchantId != null ? CorovaEnchantments.getUpgradeCost(enchantId) : 10;
                } else if (left.getType() == right.getType()) {
                    // Item + Item merge
                    ItemMeta resMeta = result.getItemMeta();
                    if (resMeta == null) return;
                    cost = resMeta.getPersistentDataContainer().getOrDefault(keyMergeCost, PersistentDataType.INTEGER, 1);
                    // Clean up temporary PDC cost
                    resMeta.getPersistentDataContainer().remove(keyMergeCost);
                    result.setItemMeta(resMeta);
                } else {
                    if (bookMeta == null) return;
                    String bookEnchantId = bookMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
                    cost = CorovaEnchantments.hasEnchant(left, bookEnchantId)
                            ? (bookEnchantId != null ? CorovaEnchantments.getUpgradeCost(bookEnchantId) : 10)
                            : 1;
                }

                if (player.getLevel() < cost) {
                    player.sendMessage(ChatColor.RED + "You need at least " + cost + " level(s) to do that!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                // Final lore refresh before handing to the player to ensure viewer-dependent trims are correct.
                ItemStack finalItem = result.clone();
                MutationManager.getInstance().updateLore(finalItem, player);
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(finalItem);
                if (!leftover.isEmpty()) {
                    for (ItemStack item : leftover.values())
                        player.getWorld().dropItem(player.getLocation(), item);
                }

                if (left.getAmount() > 1)  { left.setAmount(left.getAmount() - 1);   anvil.setItem(0, left); }
                else                        { anvil.setItem(0, null); }
                if (right.getAmount() > 1) { right.setAmount(right.getAmount() - 1); anvil.setItem(1, right); }
                else                        { anvil.setItem(1, null); }

                anvil.setItem(2, null);
                player.setLevel(player.getLevel() - cost);
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> updateAnvilResult(anvil), 1L);
                return;
            }

            // ── Slots 0 / 1: input slots ──────────────────────────────────────
            if (slot == 0 || slot == 1) {
                if (event.isShiftClick()) {
                    // Shift-click OUT of an anvil input slot:
                    // Cancel, return the item to the player's inventory, clear the
                    // slot, then recalculate the result.
                    event.setCancelled(true);
                    ItemStack inSlot = anvil.getItem(slot);
                    if (inSlot == null || inSlot.getType() == Material.AIR) return;

                    HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(inSlot.clone());
                    if (!leftover.isEmpty()) {
                        for (ItemStack drop : leftover.values())
                            player.getWorld().dropItem(player.getLocation(), drop);
                    }
                    anvil.setItem(slot, null);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> updateAnvilResult(anvil), 2L);
                    return;
                }
                // Normal (non-shift) click — let Bukkit handle placement naturally,
                // then recalculate the result slot.
                Bukkit.getScheduler().runTaskLater(plugin, () -> updateAnvilResult(anvil), 2L);
            }

        } else {
            // ── Player inventory: shift-click INTO the anvil ──────────────────
            if (event.isShiftClick() && event.getCurrentItem() != null) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked.getType() == Material.AIR) return;

                event.setCancelled(true);
                boolean isBook = clicked.getType() == Material.ENCHANTED_BOOK;

                ItemStack slot0 = anvil.getItem(0);
                ItemStack slot1 = anvil.getItem(1);

                if (isBook) {
                    if (slot1 == null || slot1.getType() == Material.AIR) {
                        anvil.setItem(1, clicked.clone()); event.setCurrentItem(null);
                    } else if (slot0 == null || slot0.getType() == Material.AIR) {
                        anvil.setItem(0, clicked.clone()); event.setCurrentItem(null);
                    }
                    // Both slots occupied — do nothing, item stays in inventory
                } else {
                    if (slot0 == null || slot0.getType() == Material.AIR) {
                        anvil.setItem(0, clicked.clone()); event.setCurrentItem(null);
                    } else if (slot1 == null || slot1.getType() == Material.AIR) {
                        anvil.setItem(1, clicked.clone()); event.setCurrentItem(null);
                    }
                    // Both slots occupied — do nothing
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> updateAnvilResult(anvil), 2L);
            }
        }
    }

    //  @EventHandler  (disabled — glass-block debug trigger)
    public void onGlassInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.GLASS) return;

        Player player = event.getPlayer();
        event.setCancelled(true);
        openCustomAnvil(player);
        player.sendMessage("§aOpened Custom Enchantment Anvil!");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = openAnvils.get(player);
        if (inv != null && event.getInventory().equals(inv)) {
            ItemStack left  = inv.getItem(0);
            ItemStack right = inv.getItem(1);

            if (left != null && left.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(left);
                if (!leftover.isEmpty()) player.getWorld().dropItem(player.getLocation(), left);
            }
            if (right != null && right.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(right);
                if (!leftover.isEmpty()) player.getWorld().dropItem(player.getLocation(), right);
            }
            openAnvils.remove(player);
        }
    }

    private boolean hasAnyMutation(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        // Check if item has any mutations by attempting to merge them into a fresh item of the same type
        ItemStack dummy = new ItemStack(item.getType());
        MutationManager.MergeResult res = MutationManager.getInstance().mergeMutations(dummy, item);
        return res.changed;
    }
}