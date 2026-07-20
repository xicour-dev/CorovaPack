package com.example.corovaItems.Weapons;

import com.example.corovaItems.CorovaItems;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class Sniper extends CorovaItems implements Listener {

    private final Set<UUID> scopedPlayers = new HashSet<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    private static final double DAMAGE = 360.0;
    private static final long COOLDOWN_MS = 30_000L; // 30 seconds in milliseconds

    public Sniper() {
        super("§8Sniper Rifle",
                Material.CROSSBOW,
                List.of("§7Scoped Rifle", "§fRight-click to scope"),
                Collections.emptyMap(),
                "sniper");
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isThisItem(item)) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK) {

            event.setCancelled(true);
            toggleScope(player);
            return;
        }

        if (event.getAction() == Action.LEFT_CLICK_AIR
                || event.getAction() == Action.LEFT_CLICK_BLOCK) {

            event.setCancelled(true);

            UUID uid = player.getUniqueId();
            long now = System.currentTimeMillis();

            if (cooldowns.containsKey(uid)) {
                long elapsed = now - cooldowns.get(uid);
                if (elapsed < COOLDOWN_MS) {
                    long remaining = (COOLDOWN_MS - elapsed) / 1000L + 1;
                    player.sendMessage("§cSniper is on cooldown! §f(" + remaining + "s remaining)");
                    return;
                }
            }

            shoot(player);
            cooldowns.put(uid, now);
        }
    }

    private void toggleScope(Player player) {
        if (scopedPlayers.contains(player.getUniqueId())) {
            scopedPlayers.remove(player.getUniqueId());
            removeScope(player);
        } else {
            scopedPlayers.add(player.getUniqueId());
            applyScope(player);
        }
    }

    private void applyScope(Player player) {
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, 999999, 255, false, false, false));

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING, 999999, 255, false, false, false));

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NIGHT_VISION, 999999, 0, false, false, false));

        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 1f);
    }

    private void removeScope(Player player) {
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);

        player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 1f, 1f);
    }

    private void shoot(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double range = 120;

        player.getWorld().playSound(
                eye,
                Sound.ENTITY_GENERIC_EXPLODE,
                1f,
                0.7f
        );

        for (double i = 0; i < range; i += 0.4) {
            Location loc = eye.clone().add(dir.clone().multiply(i));

            player.getWorld().spawnParticle(
                    Particle.SMOKE,
                    loc,
                    1,
                    0, 0, 0, 0
            );

            for (Entity e : player.getWorld().getNearbyEntities(loc, 0.35, 0.35, 0.35)) {
                if (e instanceof LivingEntity target && e != player) {
                    target.damage(DAMAGE, player);

                    BlockData blood = Material.REDSTONE_BLOCK.createBlockData();
                    Objects.requireNonNull(loc.getWorld()).spawnParticle(
                            Particle.BLOCK,
                            loc,
                            30,
                            0.2, 0.2, 0.2,
                            blood
                    );
                    return;
                }
            }
        }
    }
}