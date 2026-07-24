package com.example.corovaItems.Enchantments.EnchantBooks;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import org.bukkit.Bukkit;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StealthStepBook extends EnchantmentBook implements Listener {

    public StealthStepBook() {
        this(1);
    }

    public StealthStepBook(int level) {
        super(
                "Book of Stealth Step",
                CorovaEnchantments.STEALTH_STEP_ID,
                level,
                "book_stealth_step_" + level,
                allowedMaterialsStatic()
        );
    }

    private static Set<Material> allowedMaterialsStatic() {
        return Set.of(
                Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS,
                Material.GOLDEN_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
        );
    }

    public void initProtocolLib(JavaPlugin plugin) {
        List<PacketType> types = new ArrayList<>();
        // Broad dynamic discovery of all sound/effect related server packets
        String[] keywords = {"SOUND", "NAMED", "LEVEL_EVENT", "WORLD_EVENT", "WORLD_PARTICLES"};
        for (java.lang.reflect.Field field : PacketType.Play.Server.class.getFields()) {
            try {
                if (field.getType() == PacketType.class) {
                    String name = field.getName();
                    for (String keyword : keywords) {
                        if (name.contains(keyword) && !name.contains("STOP")) {
                            PacketType type = (PacketType) field.get(null);
                            if (type != null && !types.contains(type)) types.add(type);
                            break;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (types.isEmpty()) return;

        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin, ListenerPriority.HIGHEST, types) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                Player recipient = event.getPlayer();

                // 1. Identification: Determine if this is a movement/impact sound
                String soundName = "";
                boolean isMovementEffect = false;

                // Handle Effect/LevelEvent packets (block steps/breaks)
                String typeName = event.getPacketType().name();
                if (typeName.contains("LEVEL_EVENT") || typeName.contains("WORLD_EVENT")) {
                    if (packet.getIntegers().size() > 0) {
                        int id = packet.getIntegers().read(0);
                        if (id == 2001) isMovementEffect = true; // Block step/break particles+sound
                    }
                }

                if (!isMovementEffect) {
                    try {
                        if (packet.getSoundEffects().size() > 0) {
                            Object obj = packet.getSoundEffects().read(0);
                            if (obj != null) {
                                try { soundName = String.valueOf(obj.getClass().getMethod("key").invoke(obj)).toLowerCase(); } catch (Throwable t1) {
                                    try { soundName = String.valueOf(obj.getClass().getMethod("getKey").invoke(obj)).toLowerCase(); } catch (Throwable t2) {
                                        soundName = obj.toString().toLowerCase();
                                    }
                                }
                            }
                        }
                        if (soundName.isEmpty() && packet.getMinecraftKeys().size() > 0) {
                            soundName = packet.getMinecraftKeys().read(0).toString().toLowerCase();
                        }
                        if (soundName.isEmpty() && packet.getStrings().size() > 0) {
                            soundName = packet.getStrings().read(0).toLowerCase();
                        }
                    } catch (Throwable ignored) {}

                    isMovementEffect = soundName.contains("step") || soundName.contains("foot") || soundName.contains("walk") ||
                            soundName.contains("climb") || soundName.contains("swim") || soundName.contains("jump") ||
                            soundName.contains("fall") || soundName.contains("land") || soundName.contains("hit_ground") ||
                            soundName.contains("impact") || soundName.contains("thud") || soundName.contains("crash") ||
                            soundName.contains("plop") || soundName.contains("movement");
                }

                if (!isMovementEffect) return;

                // 2. Attribution: Identify which entity produced the sound
                LivingEntity source = null;
                boolean isFall = soundName.contains("fall") || soundName.contains("land") || soundName.contains("hit") || soundName.contains("impact") || soundName.contains("thud");

                // Case A: Entity ID present
                if (typeName.contains("ENTITY") && packet.getIntegers().size() > 0) {
                    try {
                        int entityId = packet.getIntegers().read(0);
                        Entity e = ProtocolLibrary.getProtocolManager().getEntityFromID(recipient.getWorld(), entityId);
                        if (e instanceof LivingEntity le) source = le;
                    } catch (Throwable ignored) {}
                }

                // Case B: Spatial Attribution
                if (source == null) {
                    try {
                        double x = 0, y = 0, z = 0;
                        boolean hasCoords = false;
                        if (packet.getDoubles().size() >= 3) {
                            x = packet.getDoubles().read(0); y = packet.getDoubles().read(1); z = packet.getDoubles().read(2);
                            hasCoords = true;
                        } else if (packet.getIntegers().size() >= 3) {
                            x = packet.getIntegers().read(0) / 8.0; y = packet.getIntegers().read(1) / 8.0; z = packet.getIntegers().read(2) / 8.0;
                            hasCoords = true;
                        }

                        if (!hasCoords) {
                            source = recipient; // Assume self
                        } else {
                            Location soundLoc = new Location(recipient.getWorld(), x, recipient.getLocation().getY(), z);
                            if (soundLoc.distanceSquared(recipient.getLocation()) < 4.0) {
                                source = recipient;
                            } else {
                                for (Entity e : soundLoc.getWorld().getNearbyEntities(soundLoc, 2.0, 3.0, 2.0)) {
                                    if (e instanceof LivingEntity le) {
                                        source = le;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) { source = recipient; }
                }

                // 3. Conditionally cancel packet
                if (source != null && CorovaEnchantments.shouldSilenceStealthStep(source, isFall)) {
                    event.setCancelled(true);
                }
            }
        });

        // Suppressor task (Reinforce setSilent flag at 20Hz)
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    handleSilence(p);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) { handleSilence(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSprint(PlayerToggleSprintEvent event) {
        handleSilence(event.getPlayer());
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(this.getClass()), () -> handleSilence(event.getPlayer()), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSneak(PlayerToggleSneakEvent event) { handleSilence(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) { handleSilence(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof LivingEntity le) {
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(this.getClass()), () -> handleSilence(le), 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { handleSilence(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().isSilent()) event.getPlayer().setSilent(false);
    }

    private void handleSilence(LivingEntity entity) {
        if (entity == null || entity.getEquipment() == null) return;
        ItemStack boots = entity.getEquipment().getBoots();

        if (boots == null || !CorovaEnchantments.hasEnchant(boots, CorovaEnchantments.STEALTH_STEP_ID)) {
            if (entity.isSilent()) entity.setSilent(false);
            return;
        }

        boolean shouldBeSilent = CorovaEnchantments.shouldSilenceStealthStep(entity, false);
        if (entity.isSilent() != shouldBeSilent) {
            entity.setSilent(shouldBeSilent);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGameEvent(GenericGameEvent event) {
        GameEvent ge = event.getEvent();
        if (ge != GameEvent.STEP && ge != GameEvent.HIT_GROUND) return;
        if (event.getEntity() instanceof LivingEntity le && CorovaEnchantments.shouldSilenceStealthStep(le, ge == GameEvent.HIT_GROUND)) {
            event.setCancelled(true);
        }
    }
}
