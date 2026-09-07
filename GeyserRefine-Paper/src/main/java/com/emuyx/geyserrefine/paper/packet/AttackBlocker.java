package com.emuyx.geyserrefine.paper.packet;

import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AttackBlocker {

    private static final long EXPIRY_TICKS = 40;
    private static final double RANGE = 20.0;

    private static AttackBlocker instance;

    private final ConcurrentHashMap<UUID, Location> attackLocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, BukkitTask> removalTasks = new ConcurrentHashMap<>();

    public AttackBlocker() {
        instance = this;
    }

    public static AttackBlocker getInstance() {
        return instance;
    }

    public void recordAttack(Player attacker, Location attackLocation) {
        UUID uuid = attacker.getUniqueId();
        attackLocations.put(uuid, attackLocation.clone());

        BukkitTask oldTask = removalTasks.get(uuid);
        if (oldTask != null) {
            oldTask.cancel();
        }

        BukkitTask newTask = GeyserRefinePlugin.getInstance().getServer()
                .getScheduler().runTaskLater(GeyserRefinePlugin.getInstance(), () -> {
                    attackLocations.remove(uuid);
                    removalTasks.remove(uuid);
                }, EXPIRY_TICKS);

        removalTasks.put(uuid, newTask);
    }

    public Player getAttackerNearLocation(Location particleLocation, double maxDistance) {
        UUID closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Map.Entry<UUID, Location> entry : attackLocations.entrySet()) {
            if (!entry.getValue().getWorld().equals(particleLocation.getWorld())) continue;
            double dist = entry.getValue().distanceSquared(particleLocation);
            if (dist < closestDist) {
                closestDist = dist;
                closest = entry.getKey();
            }
        }
        if (closest != null && closestDist <= maxDistance * maxDistance) {
            return GeyserRefinePlugin.getInstance().getServer().getPlayer(closest);
        }
        return null;
    }
}