//


//

package com.example.corovaEvents.WorldEvents;

import com.example.corovaEvents.CorovaEvents;
import com.example.corovaEvents.DiscordBridge;
import com.example.corovaEvents.SpawnModifierManager;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TheSunWontSaveYouToday implements Listener {
    private static final Map<World, TheSunWontSaveYouToday> worldInstances = new HashMap();
    private static final double CHANCE_PER_DAY = 0.05;
    private final JavaPlugin plugin;
    private final World world;
    private final Random random = new Random();
    private boolean active = false;
    private boolean chosen = false;
    private boolean hasRolledThisDay = false;
    private boolean announcedThisDay = false;
    private BukkitRunnable dayCheckTask = null;

    public static TheSunWontSaveYouToday getForWorld(World world) {
        return (TheSunWontSaveYouToday)worldInstances.get(world);
    }

    public TheSunWontSaveYouToday(JavaPlugin plugin, World world) {
        TheSunWontSaveYouToday existing = (TheSunWontSaveYouToday)worldInstances.get(world);
        if (existing != null) {
            existing.cleanup();
        }

        this.plugin = plugin;
        this.world = world;
        worldInstances.put(world, this);
        this.startDayCheck();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("§e[TheSunWontSaveYouToday] Initialized for world: " + world.getName());
    }

    public boolean isActive() {
        return this.active || this.chosen;
    }

    public boolean isChosen() {
        return this.chosen;
    }

    private void startDayCheck() {
        this.dayCheckTask = new BukkitRunnable() {
            public void run() {
                long time = TheSunWontSaveYouToday.this.world.getTime() % 24000L;

                // The event should only be active/chosen during the day.
                if (time >= 12000L && (TheSunWontSaveYouToday.this.active || TheSunWontSaveYouToday.this.chosen)) {
                    TheSunWontSaveYouToday.this.end();
                }

                if (time >= 0L && time < 12000L) {
                    if (!TheSunWontSaveYouToday.this.hasRolledThisDay) {
                        TheSunWontSaveYouToday.this.hasRolledThisDay = true;
                        if (com.example.corovaEvents.CorovaEvents.getInstance().isWorldEventEnabled("sunwontsaveyou") && TheSunWontSaveYouToday.this.random.nextDouble() < 0.05) {
                            TheSunWontSaveYouToday.this.chosen = true;
                            TheSunWontSaveYouToday.this.plugin.getLogger().info("§c[TheSunWontSaveYouToday] Chosen for today in " + TheSunWontSaveYouToday.this.world.getName());
                        } else {
                            TheSunWontSaveYouToday.this.plugin.getLogger().info("§7[TheSunWontSaveYouToday] Not triggered today.");
                        }
                    }

                    if (TheSunWontSaveYouToday.this.chosen && !TheSunWontSaveYouToday.this.announcedThisDay && time >= 1000L) {
                        TheSunWontSaveYouToday.this.announcedThisDay = true;
                        TheSunWontSaveYouToday.this.activate();
                    }
                }

                if (time >= 12000L && time < 12100L) {
                    if (TheSunWontSaveYouToday.this.active || TheSunWontSaveYouToday.this.chosen) {
                        TheSunWontSaveYouToday.this.end();
                    }

                    TheSunWontSaveYouToday.this.hasRolledThisDay = false;
                    TheSunWontSaveYouToday.this.announcedThisDay = false;
                }

            }
        };
        this.dayCheckTask.runTaskTimer(this.plugin, 0L, 20L);
    }

    public void trigger() {
        this.plugin.getLogger().info("§c§l[TheSunWontSaveYouToday] ===== EVENT TRIGGERED BY COMMAND =====");
        long time = this.world.getTime() % 24000L;
        if (time >= 12000L) {
            this.world.setTime(this.world.getFullTime() + 24000L - time + 500L);
            this.plugin.getLogger().info("§e[TheSunWontSaveYouToday] Time pushed to morning for event.");
        }

        this.chosen = true;
        this.hasRolledThisDay = true;
        this.announcedThisDay = true;
        this.activate();
    }

    private void activate() {
        if (!this.active) {
            this.active = true;
            String message = "§cThe sun wont protect you today...";

            for(Player p : this.world.getPlayers()) {
                p.sendMessage(message);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.5F, 0.5F);
                this.applyBloodSunVisuals(p);
            }

            DiscordBridge.sendToDiscord(message);
            this.plugin.getLogger().info("§c[TheSunWontSaveYouToday] ACTIVATED in " + this.world.getName());
            // Signal activity to SpawnModifierManager for anti-light-spawn logic
            SpawnModifierManager.getInstance().setSunWontSaveActive(this.world, true);
            double mult = CorovaEvents.getInstance().getEventsConfig().getDouble("sun_wont_save_you.spawn_multiplier", (double)1.5F) * 2.0;
            SpawnModifierManager.getInstance().setGlobalSpawnRateMultiplier(this.world, mult);

            for(String mob : CorovaEvents.getInstance().getEventsConfig().getStringList("sun_wont_save_you.forced_mobs")) {
                SpawnModifierManager.getInstance().addForcedMob(this.world, mob);
            }

        }
    }

    private void end() {
        if (this.active || this.chosen) {
            if (this.active) {
                SpawnModifierManager.getInstance().reset(this.world);
                for (Player p : this.world.getPlayers()) {
                    this.removeBloodSunVisuals(p);
                }
                this.plugin.getLogger().info("§a[TheSunWontSaveYouToday] Event ended in " + this.world.getName());
            }

            this.active = false;
            this.chosen = false;
        }
    }

    public void forceReset() {
        this.end();
        this.hasRolledThisDay = false;
        this.announcedThisDay = false;
    }

    public void cleanup() {
        this.forceReset();
        if (this.dayCheckTask != null) {
            this.dayCheckTask.cancel();
            this.dayCheckTask = null;
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
        worldInstances.remove(this.world);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (this.active && p.getWorld().equals(this.world)) {
            this.applyBloodSunVisuals(p);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();
        if (p.getWorld().equals(this.world)) {
            if (this.active) {
                this.applyBloodSunVisuals(p);
            }
        } else if (event.getFrom().equals(this.world)) {
            this.removeBloodSunVisuals(p);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        if (this.active && event.getRespawnLocation().getWorld().equals(this.world)) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (p.isOnline() && p.getWorld().equals(TheSunWontSaveYouToday.this.world)) {
                        TheSunWontSaveYouToday.this.applyBloodSunVisuals(p);
                    }
                }
            }.runTaskLater(this.plugin, 2L);
        }
    }

    private void applyBloodSunVisuals(Player p) {
        this.plugin.getServer().dispatchCommand(
            this.plugin.getServer().getConsoleSender(),
            "posteffect add " + p.getName() + " corova:blood_sun"
        );
    }

    private void removeBloodSunVisuals(Player p) {
        this.plugin.getServer().dispatchCommand(
            this.plugin.getServer().getConsoleSender(),
            "posteffect clear " + p.getName()
        );
    }
}
