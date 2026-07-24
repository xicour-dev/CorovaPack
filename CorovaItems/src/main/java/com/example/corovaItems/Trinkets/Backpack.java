package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * Backpack — a trinket that provides 18 extra inventory slots (2 rows).
 *
 * <p><b>Behaviour:</b>
 * <ul>
 *   <li>Right-click while holding the trinket to open the backpack GUI.</li>
 *   <li>When the player's main inventory cannot fully absorb a picked-up item,
 *       any remainder automatically overflows into the backpack.</li>
 *   <li>If the main inventory is completely full, the entire pickup goes straight
 *       into the backpack.</li>
 *   <li>Shulker boxes are explicitly blocked from being placed inside the
 *       backpack to prevent serialization corruption and item loss.</li>
 *   <li>No durability — this trinket never breaks.</li>
 *   <li>Contents are stored in the trinket's PersistentDataContainer so they
 *       survive restarts and are bound to <em>that specific item</em>.</li>
 * </ul>
 */
public class Backpack extends CorovaItems implements Listener {

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Number of slots in the backpack (18 = 2 rows of 9). */
    private static final int BACKPACK_SIZE = 18;

    /** Title shown in the GUI. */
    private static final String GUI_TITLE = ChatColor.DARK_PURPLE + "Backpack";

    /**
     * Delimiter used between serialised slots.
     *
     * CRITICAL: Must be a character that NEVER appears in Base64 output.
     * Base64 uses A-Z, a-z, 0-9, +, /, and = for padding.
     * A pipe '|' is safe. The old code used ',' which CAN appear in
     * Base64 — that caused shulker boxes (and other complex items whose
     * serialised bytes happened to produce a comma) to split incorrectly,
     * corrupting or silently deleting the stored item.
     */
    private static final String SLOT_DELIMITER = "|";

    /** Regex-safe version of SLOT_DELIMITER for String#split(). */
    private static final String SLOT_DELIMITER_REGEX = "\\|";

    /**
     * PDC key that stores the serialised inventory contents on the item itself.
     * Format: pipe-separated Base64-encoded ItemStack serialisations, with
     * empty slots stored as the literal string "EMPTY".
     */
    private static final NamespacedKey CONTENTS_KEY =
            new NamespacedKey("corova", "backpack_contents");

    private static final NamespacedKey TIER_KEY =
            new NamespacedKey("corova", "backpack_tier");

    private static BiPredicate<Player, Integer> tierChecker = (p, t) -> false;

    public static void setTierChecker(BiPredicate<Player, Integer> checker) {
        tierChecker = checker;
    }

    public static int getPlayerTier(Player player) {
        for (int t = 5; t >= 1; --t) {
            if (tierChecker.test(player, t)) return t;
        }
        return 0;
    }

    /**
     * Tracks which players have a backpack GUI open, mapped to the specific
     * backpack ItemStack they opened so we can save back to the correct item.
     */
    private static final Map<UUID, ItemStack> openBackpacks = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public Backpack() {
        super(
                ChatColor.DARK_PURPLE + "Backpack",
                Material.CHEST,
                lore(),
                null,
                "backpack"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "18 extra inventory slots!",
                ChatColor.GRAY + "Right-click to open.",
                ChatColor.GRAY + "Auto-collects overflow items."
        );
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            // Pre-initialise empty contents so the key always exists.
            meta.getPersistentDataContainer().set(
                    CONTENTS_KEY, PersistentDataType.STRING, emptyContentsString());

            List<String> lore = meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "UUID: " + UUID.randomUUID());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * No-op for attribute purposes (backpack has no attribute modifiers),
     * but kept so {@link TrinketUtils#checkEntity} can call it uniformly.
     */
    public static void checkTrinket(org.bukkit.entity.LivingEntity entity) {
        // Backpack has no attribute modifiers — nothing to apply or remove.
    }

    // ── GUI helpers ───────────────────────────────────────────────────────────

    private static void openBackpackGUI(Player player, ItemStack backpackItem) {
        int tier = getTier(backpackItem);
        int size = getSizeForTier(tier);
        Inventory gui = Bukkit.createInventory(null, size, GUI_TITLE);

        ItemStack[] stored = loadContents(backpackItem);
        for (int i = 0; i < stored.length && i < size; i++) {
            gui.setItem(i, stored[i]);
        }

        openBackpacks.put(player.getUniqueId(), backpackItem);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.0f);
    }

    private static void saveAndClose(Player player, Inventory gui) {
        ItemStack backpackItem = openBackpacks.remove(player.getUniqueId());
        if (backpackItem == null) return;

        // Find the exact backpack that was opened by matching its UUID lore tag.
        // We must NOT use findBackpackItem() here — that returns the first
        // backpack in inventory order, which is wrong when the player has
        // multiple backpacks.  The UUID lore line is unique per-item and was
        // set at creation time, so it reliably identifies the correct one.
        String openedUuid = getBackpackUuid(backpackItem);
        ItemStack canonical = null;

        if (openedUuid != null) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                if (openedUuid.equals(getBackpackUuid(item))) {
                    canonical = item;
                    break;
                }
            }
        }

        // Fallback: if UUID match failed (shouldn't happen), write to the
        // reference we already have rather than silently discarding changes.
        if (canonical == null) canonical = backpackItem;

        saveContents(canonical, gui.getContents());
    }

    /**
     * Extracts the UUID string from the backpack item's lore tag.
     * Returns null if not found.
     */
    private static String getBackpackUuid(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        List<String> lore = meta.getLore();
        if (lore == null) return null;
        for (String line : lore) {
            if (line.contains("UUID:")) {
                int idx = line.indexOf("UUID:") + 5;
                return line.substring(idx).trim();
            }
        }
        return null;
    }

    // ── Overflow auto-collect ─────────────────────────────────────────────────

    /**
     * Attempts to add {@code item} to the backpack owned by {@code player}.
     *
     * Tries to stack with existing identical items first, then fills empty
     * slots. Mutates {@code item}'s amount to reflect however many were NOT
     * absorbed (0 means fully absorbed).
     *
     * Shulker boxes are rejected here as an extra safety net — they must not
     * be auto-collected into the backpack because Bukkit cannot reliably
     * round-trip shulker-box-inside-shulker-box serialisation.
     *
     * @return {@code true} if the item was fully absorbed.
     */
    private static boolean tryAddToBackpack(Player player, ItemStack item) {
        if (isShulkerBox(item)) return false;

        // Find the backpack with the most available space for this item type
        // so overflow distributes sensibly when multiple backpacks are present.
        ItemStack bestBackpack = null;
        int bestSpace = -1;

        for (ItemStack candidate : player.getInventory().getContents()) {
            if (candidate == null || !candidate.hasItemMeta()) continue;
            ItemMeta meta = candidate.getItemMeta();
            if (meta == null) continue;
            if (!meta.getPersistentDataContainer()
                    .has(CONTENTS_KEY, PersistentDataType.STRING)) continue;

            int space = computeSpaceForItem(candidate, item);
            if (space > bestSpace) {
                bestSpace = space;
                bestBackpack = candidate;
            }
        }

        if (bestBackpack == null || bestSpace <= 0) return false;

        ItemStack[] contents = loadContents(bestBackpack);
        boolean mutated = false;

        // Pass 1: stack onto existing partial stacks.
        for (int i = 0; i < contents.length; i++) {
            if (item.getAmount() <= 0) break;
            ItemStack slot = contents[i];
            if (slot == null || slot.getType() == Material.AIR) continue;
            if (!slot.isSimilar(item)) continue;
            if (slot.getAmount() >= slot.getMaxStackSize()) continue;

            int space = slot.getMaxStackSize() - slot.getAmount();
            int take  = Math.min(space, item.getAmount());
            slot.setAmount(slot.getAmount() + take);
            item.setAmount(item.getAmount() - take);
            contents[i] = slot;
            mutated = true;
        }

        // Pass 2: fill empty slots.
        for (int i = 0; i < contents.length; i++) {
            if (item.getAmount() <= 0) break;
            if (contents[i] != null && contents[i].getType() != Material.AIR) continue;

            int take = Math.min(item.getMaxStackSize(), item.getAmount());
            ItemStack placed = item.clone();
            placed.setAmount(take);
            contents[i] = placed;
            item.setAmount(item.getAmount() - take);
            mutated = true;
        }

        if (mutated) {
            saveContents(bestBackpack, contents);
        }

        return item.getAmount() <= 0;
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    /**
     * Serialises and saves an array of {@link ItemStack}s into the backpack
     * item's PersistentDataContainer.
     *
     * Uses '|' as a slot delimiter because it is not a valid Base64 character
     * and therefore cannot appear inside any serialised item payload, unlike
     * ',' which could cause split() to produce extra tokens and corrupt data.
     */
    @SuppressWarnings("deprecation")
    private static void saveContents(ItemStack backpackItem, ItemStack[] contents) {
        if (backpackItem == null) return;
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contents.length; i++) {
            if (i > 0) sb.append(SLOT_DELIMITER);
            ItemStack slot = contents[i];
            if (slot == null || slot.getType() == Material.AIR) {
                sb.append("EMPTY");
            } else {
                // Extra safety: refuse to serialise shulker boxes.
                // If one somehow ends up in the contents array, store EMPTY
                // instead of silently corrupting adjacent slots on load.
                if (isShulkerBox(slot)) {
                    sb.append("EMPTY");
                    Bukkit.getLogger().warning("[Backpack] Refused to serialise shulker box in slot "
                            + i + " — dropping to EMPTY to prevent data corruption.");
                    continue;
                }
                try {
                    java.io.ByteArrayOutputStream byteOut = new java.io.ByteArrayOutputStream();
                    org.bukkit.util.io.BukkitObjectOutputStream out =
                            new org.bukkit.util.io.BukkitObjectOutputStream(byteOut);
                    out.writeObject(slot);
                    out.close();
                    sb.append(Base64.getEncoder().encodeToString(byteOut.toByteArray()));
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Backpack] Failed to serialise slot " + i
                            + " (" + slot.getType() + "): " + e.getMessage());
                    sb.append("EMPTY");
                }
            }
        }

        meta.getPersistentDataContainer().set(CONTENTS_KEY, PersistentDataType.STRING, sb.toString());
        backpackItem.setItemMeta(meta);
    }

    /**
     * Deserialises and returns the stored {@link ItemStack}s from the backpack item.
     *
     * Splits on '|' which is guaranteed to never appear in a Base64 payload,
     * so complex items (enchanted books, written books, shulker boxes, etc.)
     * are parsed correctly regardless of their binary content.
     */
    @SuppressWarnings("deprecation")
    private static ItemStack[] loadContents(ItemStack backpackItem) {
        int tier = getTier(backpackItem);
        int size = getSizeForTier(tier);
        ItemStack[] result = new ItemStack[size];
        if (backpackItem == null) return result;

        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null) return result;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(CONTENTS_KEY, PersistentDataType.STRING)) return result;

        String raw = pdc.get(CONTENTS_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isEmpty()) return result;

        // Support both old comma-delimited saves and new pipe-delimited saves.
        // If the string contains no '|' at all but does contain ',' it is
        // almost certainly a legacy save — migrate it on the fly.
        String[] parts;
        if (!raw.contains(SLOT_DELIMITER) && raw.contains(",")) {
            // Legacy format: try comma split.  This will still corrupt any slot
            // whose Base64 payload contained a comma, but it is no worse than
            // what the old code did and gives us a migration path.
            Bukkit.getLogger().warning("[Backpack] Detected legacy comma-delimited save for "
                    + backpackItem.getType() + " — migrating to pipe-delimited format.");
            parts = raw.split(",", -1);
        } else {
            parts = raw.split(SLOT_DELIMITER_REGEX, -1);
        }

        for (int i = 0; i < Math.min(parts.length, size); i++) {
            String part = parts[i];
            if (part == null || part.equals("EMPTY") || part.isEmpty()) {
                result[i] = null;
                continue;
            }
            try {
                byte[] bytes = Base64.getDecoder().decode(part);
                org.bukkit.util.io.BukkitObjectInputStream in =
                        new org.bukkit.util.io.BukkitObjectInputStream(
                                new java.io.ByteArrayInputStream(bytes));
                ItemStack loaded = (ItemStack) in.readObject();
                in.close();

                // Post-load safety: if a shulker box somehow slipped in via a
                // legacy save, drop it rather than risk further corruption.
                if (loaded != null && isShulkerBox(loaded)) {
                    Bukkit.getLogger().warning("[Backpack] Dropped shulker box found in slot "
                            + i + " during load — was stored in a legacy save.");
                    result[i] = null;
                } else {
                    result[i] = loaded;
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Backpack] Failed to deserialise slot " + i
                        + ": " + e.getMessage());
                result[i] = null;
            }
        }
        return result;
    }

    /** Returns a string representing an entirely empty backpack. */
    private static String emptyContentsString() {
        return emptyContentsString(BACKPACK_SIZE);
    }

    private static String emptyContentsString(int size) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) sb.append(SLOT_DELIMITER);
            sb.append("EMPTY");
        }
        return sb.toString();
    }

    private static int getTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer()
                .getOrDefault(TIER_KEY, PersistentDataType.INTEGER, 0);
    }

    private static int getSizeForTier(int tier) {
        if (tier >= 5) return 36;
        if (tier >= 3) return 27;
        return 18;
    }

    private static String getCapacityText(int tier) {
        int slots = getSizeForTier(tier);
        return ChatColor.GRAY.toString() + slots + " extra inventory slots!";
    }

    // ── Item finder ───────────────────────────────────────────────────────────

    /**
     * Finds the backpack by checking for its PDC storage key directly.
     * Does NOT use ItemManager — works regardless of registration state.
     */
    private static ItemStack findBackpackItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            if (meta.getPersistentDataContainer()
                    .has(CONTENTS_KEY, PersistentDataType.STRING)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Returns the total number of items of the given type that the backpack
     * can still absorb — used to pick the best backpack for overflow.
     */
    private static int computeSpaceForItem(ItemStack backpackItem, ItemStack incoming) {
        ItemStack[] contents = loadContents(backpackItem);
        int space = 0;
        for (ItemStack slot : contents) {
            if (slot == null || slot.getType() == Material.AIR) {
                space += incoming.getMaxStackSize();
            } else if (slot.isSimilar(incoming) && slot.getAmount() < slot.getMaxStackSize()) {
                space += slot.getMaxStackSize() - slot.getAmount();
            }
        }
        return space;
    }

    /**
     * absorb — either into partial stacks of the same type or empty slots.
     */
    private static int capacityForItem(Player player, ItemStack incoming) {
        int capacity = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                capacity += incoming.getMaxStackSize();
            } else if (slot.isSimilar(incoming) && slot.getAmount() < slot.getMaxStackSize()) {
                capacity += slot.getMaxStackSize() - slot.getAmount();
            }
            if (capacity >= incoming.getAmount()) return capacity;
        }
        return capacity;
    }

    /**
     * Returns true if the given ItemStack is a shulker box of any colour.
     *
     * Shulker boxes must never be stored inside the backpack: Bukkit's
     * BukkitObjectOutputStream can serialise them but the round-trip is
     * fragile, their BlockStateMeta contains a nested inventory that bloats
     * the PDC string enormously, and the pipe/Base64 scheme gives us no way
     * to safely nest variable-length payloads.  Block them at every entry
     * point (GUI click, auto-collect, and save) so there is no way for a
     * shulker box to reach the serialiser.
     */
    private static boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        return switch (item.getType()) {
            case SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX,
                 MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX,
                 PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX,
                 GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }

    // ── Events ────────────────────────────────────────────────────────────────

    /**
     * Right-click with the backpack in hand → open GUI.
     * Cancelled so the item is not consumed or placed.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!ItemManager.getInstance().isCorovaItem(
                hand, ItemManager.getInstance().getItemById("backpack"))) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (!player.isSneaking() && isInteractiveBlock(type)) return;
        }

        event.setCancelled(true);
        openBackpackGUI(player, hand);
    }

    /** Persist contents when the player closes the backpack GUI. */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!openBackpacks.containsKey(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        saveAndClose(player, event.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
    }

    /**
     * Guard: prevent placing unsafe items INTO the backpack GUI.
     *
     * Blocks:
     *  1. The backpack item itself (cursor dragged into a GUI slot).
     *  2. Shulker boxes of any colour (serialisation corruption / data loss).
     *  3. Shift-clicking either of the above from the player's bottom inventory
     *     up into the backpack GUI.
     *
     * Critically, clicks that originate FROM the backpack GUI slots (moving
     * items OUT to the player inventory) are never blocked — even if the item
     * being moved is a backpack or shulker box that somehow ended up inside via
     * a legacy save.  Blocking outbound moves would permanently trap items with
     * no recovery path.
     *
     * Decision table:
     *
     *   clickedInventory == GUI  + shift-click  → item moving OUT  → allow
     *   clickedInventory == GUI  + normal click  → cursor going IN  → check cursor
     *   clickedInventory == player inv + shift   → item moving IN   → check currentItem
     *   clickedInventory == player inv + normal  → cursor going IN  → check cursor
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openBackpacks.containsKey(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        Inventory gui         = event.getView().getTopInventory();
        Inventory clicked     = event.getClickedInventory();
        boolean   clickInGui  = gui.equals(clicked);

        // ── Shift-click originating from the GUI → moving OUT → always allow ──
        if (clickInGui && event.isShiftClick()) return;

        // ── Only check items that are actually entering the GUI ───────────────
        //
        // Two cases where an item enters the GUI:
        //   1. Normal click on a GUI slot — cursor is being placed into that slot.
        //   2. Shift-click from the player's bottom inventory — item jumps into GUI.
        //
        // Everything else (moving items around the player's own inventory while
        // the GUI is open, picking items up off a GUI slot onto the cursor, etc.)
        // must never be blocked.

        ItemStack incoming = null;

        if (clickInGui && !event.isShiftClick()) {
            // Cursor being dropped onto a GUI slot.
            incoming = event.getCursor();
        } else if (!clickInGui && event.isShiftClick()) {
            // Shift-click from player inventory → would land in GUI.
            incoming = event.getCurrentItem();
        }
        // All other cases (click in player inv without shift, click in GUI with
        // shift to move OUT, number-key swaps from player inv, etc.) are not
        // placing anything into the GUI — fall through and allow them.

        if (incoming == null || incoming.getType() == Material.AIR) return;

        // Block the backpack being placed inside itself.
        if (ItemManager.getInstance().isCorovaItem(
                incoming, ItemManager.getInstance().getItemById("backpack"))) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot put a backpack inside a backpack!");
            return;
        }

        // Block shulker boxes entering the backpack.
        if (isShulkerBox(incoming)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot put a shulker box inside a backpack!");
        }
    }

    /**
     * Assigns a tier to a backpack item when the player picks it up off the ground.
     *
     * This handler only fires for the backpack item itself (the trinket), NOT
     * for general item pickups — the auto-collect logic lives in onItemPickup.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBackpackPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem().getItemStack();

        // Only handle the backpack trinket item itself.
        if (!ItemManager.getInstance().isCorovaItem(
                item, ItemManager.getInstance().getItemById("backpack"))) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Only assign tier if none has been assigned yet.
        if (pdc.has(TIER_KEY, PersistentDataType.INTEGER)) return;

        int tier = getPlayerTier(player);
        pdc.set(TIER_KEY, PersistentDataType.INTEGER, tier);

        int size = getSizeForTier(tier);
        if (size > BACKPACK_SIZE) {
            String contents = pdc.get(CONTENTS_KEY, PersistentDataType.STRING);
            if (contents != null) {
                StringBuilder sb = new StringBuilder(contents);
                for (int i = BACKPACK_SIZE; i < size; i++) {
                    sb.append(SLOT_DELIMITER).append("EMPTY");
                }
                pdc.set(CONTENTS_KEY, PersistentDataType.STRING, sb.toString());
            }
        }

        List<String> lore = meta.getLore();
        if (lore != null) {
            List<String> newLore = new ArrayList<>();
            String oldCapacityText = lore().get(1);
            String newCapacityText = getCapacityText(tier);
            for (String line : lore) {
                newLore.add(line.equals(oldCapacityText) ? newCapacityText : line);
            }
            meta.setLore(newLore);
        }

        String name = ChatColor.GOLD + "[T" + tier + "] " + ChatColor.DARK_PURPLE + "Backpack";
        meta.setDisplayName(name);

        item.setItemMeta(meta);
        event.getItem().setItemStack(item);
    }

    /**
     * Auto-collect items into the backpack when the main inventory is full or
     * cannot fit the entire pickup.
     *
     * Cancels the vanilla pickup entirely and distributes the item manually.
     *
     *   Case A) Main inventory fits everything        → return, let vanilla handle it
     *   Case B) Main inventory fits nothing (full)    → entire item goes to backpack
     *   Case C) Main inventory fits some but not all  → split: main + backpack
     *
     * If the backpack is also full in B/C, the true leftover is restored to
     * the ground item so nothing is silently destroyed.
     *
     * Shulker boxes are never intercepted — they fall through to vanilla so
     * the player must manually move them to their inventory, where the GUI
     * click guard will prevent them from entering the backpack.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(PlayerAttemptPickupItemEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack backpackItem = findBackpackItem(player);
        if (backpackItem == null) return;

        Item itemEntity = event.getItem();
        ItemStack pickup = itemEntity.getItemStack();

        // Never intercept the backpack item itself.
        if (pickup.hasItemMeta()) {
            ItemMeta pm = pickup.getItemMeta();
            if (pm != null && pm.getPersistentDataContainer()
                    .has(CONTENTS_KEY, PersistentDataType.STRING)) return;
        }

        // Never intercept shulker boxes — they cannot safely be stored.
        if (isShulkerBox(pickup)) return;

        int capacity = capacityForItem(player, pickup);
        int needed   = pickup.getAmount();

        // Case A: fits in main inventory — let vanilla do it.
        if (capacity >= needed) return;

        int toMain     = capacity;
        int toBackpack = needed - capacity;

        // Pre-check: if the backpack has no space for the overflow portion,
        // and the main inventory also can't fit anything, let vanilla handle it
        // (or do nothing). This prevents cancelling the event and setting
        // flyAtPlayer(true) on an item that can't actually be stored — which
        // would cause the item to magnetically loop toward the player forever.
        ItemStack backpackItem2 = findBackpackItem(player);
        int backpackSpace = (backpackItem2 != null) ? computeSpaceForItem(backpackItem2, pickup) : 0;

        if (toMain <= 0 && backpackSpace <= 0) {
            // Both inventories are completely full — let the event proceed normally
            // (vanilla will cancel it / item stays on ground without looping).
            return;
        }

        // Cases B + C: cancel vanilla, distribute manually.
        event.setCancelled(true);

        if (toMain > 0) {
            ItemStack forMain = pickup.clone();
            forMain.setAmount(toMain);
            player.getInventory().addItem(forMain);
        }

        ItemStack forBackpack = pickup.clone();
        forBackpack.setAmount(toBackpack);
        tryAddToBackpack(player, forBackpack);

        int leftover = forBackpack.getAmount();

        if (leftover <= 0) {
            itemEntity.remove();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.2f);
            if (toMain > 0) {
                player.sendActionBar(ChatColor.DARK_PURPLE + "\u00bb Overflow collected into Backpack");
            } else {
                player.sendActionBar(ChatColor.DARK_PURPLE + "\u00bb Item collected into Backpack");
            }
        } else {
            // Backpack partially full — some items were stored, leftover stays on ground.
            // Do NOT call setFlyAtPlayer(true) here: that would magnetically attract the
            // item entity but never pick it up (event is cancelled), causing an infinite loop.
            pickup.setAmount(leftover);
            itemEntity.setItemStack(pickup);
            if (toMain > 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.2f);
            }
        }
    }

    /** Save contents if the player quits with the GUI open. */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        if (!openBackpacks.containsKey(id)) return;

        Inventory top = player.getOpenInventory().getTopInventory();
        if (top != null && player.getOpenInventory().getTitle().equals(GUI_TITLE)) {
            saveAndClose(player, top);
        }
        openBackpacks.remove(id);
        TrinketUtils.removeEntity(player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true for blocks that open their own GUI on right-click. */
    private static boolean isInteractiveBlock(Material type) {
        return switch (type) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL,
                 SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX,
                 MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX,
                 PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX,
                 GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX,
                 FURNACE, BLAST_FURNACE, SMOKER, CRAFTING_TABLE,
                 ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 ENCHANTING_TABLE, GRINDSTONE, SMITHING_TABLE,
                 LOOM, CARTOGRAPHY_TABLE, STONECUTTER,
                 HOPPER, DROPPER, DISPENSER -> true;
            default -> false;
        };
    }
}