package com.emuyx.geyserrefine.paper.fishing;

import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class FishingRodListener implements Listener {

    private static final double VANILLA_GRAVITY = 0.03; // per tick
    private final BedrockCombatConfig config;
    private final Set<FishHook> activeHooks = new HashSet<>();
    private BukkitRunnable gravityTask;

    public FishingRodListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;
        Player player = event.getPlayer();
        if (!FloodgateUtil.isBedrockPlayer(player) || PlayerSettings.getSetting(player, "java-attack")) return;

        FishHook hook = event.getHook();

        // Launch from a lower position (eye height minus offset)
        Location eye = player.getEyeLocation().add(0, config.fishingThrowHeightOffset, 0);
        float yaw = (float) Math.toRadians(eye.getYaw());
        float pitch = (float) Math.toRadians(eye.getPitch());

        double vx = -Math.sin(yaw) * Math.cos(pitch) * 0.4;
        double vy = -Math.sin(pitch) * 0.4;
        double vz = Math.cos(yaw) * Math.cos(pitch) * 0.4;

        Random random = new Random();
        vx += random.nextGaussian() * 0.0075;
        vy += random.nextGaussian() * 0.0075;
        vz += random.nextGaussian() * 0.0075;

        // Apply velocity multiplier
        Vector velocity = new Vector(vx, vy, vz).multiply(config.fishingVelocityMultiplier);
        hook.setVelocity(velocity);

        // Register for gravity + position indicator
        activeHooks.add(hook);
        ensureGravityTask();
    }

    private void ensureGravityTask() {
        if (gravityTask == null) {
            gravityTask = new BukkitRunnable() {
                @Override
                public void run() {
                    activeHooks.removeIf(hook -> !hook.isValid() || hook.isOnGround());
                    for (FishHook hook : activeHooks) {
                        // Apply scaled gravity if not in water
                        if (!hook.isInWater() && hook.getWorld().getBlockAt(hook.getLocation()).getType() != Material.WATER) {
                            Vector vel = hook.getVelocity();
                            vel.setY(vel.getY() - VANILLA_GRAVITY * config.fishingGravityMultiplier);
                            hook.setVelocity(vel);
                        }

                        // Show position indicator (critical particle) every tick to compensate for Geyser delay
                        hook.getWorld().spawnParticle(
                                Particle.CRIT,
                                hook.getLocation(),
                                1, 0, 0, 0, 0, null
                        );
                    }
                    if (activeHooks.isEmpty()) {
                        cancel();
                        gravityTask = null;
                    }
                }
            };
            gravityTask.runTaskTimer(GeyserRefinePlugin.getInstance(), 1, 1);
        }
    }
}