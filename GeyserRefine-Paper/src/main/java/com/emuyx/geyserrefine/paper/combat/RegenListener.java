package com.emuyx.geyserrefine.paper.combat;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegenListener implements Listener {

    private final BedrockCombatConfig config;
    private final Map<UUID, Long> lastHealTick = new HashMap<>();
    private BukkitRunnable ticker;
    private long tickCounter;

    public RegenListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (event.getEntityType() != EntityType.PLAYER) return;
        Player player = (Player) event.getEntity();
        if (!FloodgateUtil.isBedrockPlayer(player)) return;
        if (!FloodgateUtil.isBedrockPlayer(player) || PlayerSettings.getSetting(player, "java-attack")) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;

        event.setCancelled(true);

        UUID id = player.getUniqueId();
        ensureTicker();

        long current = tickCounter;
        Long last = lastHealTick.get(id);
        if (last != null && current - last < config.regenIntervalTicks) {
            float exhaustion = player.getExhaustion();
            runNextTick(() -> player.setExhaustion(exhaustion));
            return;
        }

        double max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double now = player.getHealth();
        if (now < max) {
            player.setHealth(Math.min(now + config.regenAmount, max));
            lastHealTick.put(id, current);
        }

        float exhaustion = player.getExhaustion();
        runNextTick(() -> player.setExhaustion(exhaustion + config.regenExhaustion));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastHealTick.remove(event.getPlayer().getUniqueId());
        stopTickerIfEmpty();
    }

    private void ensureTicker() {
        if (ticker == null) {
            ticker = new BukkitRunnable() {
                @Override
                public void run() {
                    tickCounter++;
                    if (lastHealTick.isEmpty()) stopTickerIfEmpty();
                }
            };
            ticker.runTaskTimer(GeyserRefinePlugin.getInstance(), 1, 1);
        }
    }

    private void stopTickerIfEmpty() {
        if (ticker != null && lastHealTick.isEmpty()) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void runNextTick(Runnable task) {
        GeyserRefinePlugin.getInstance().getServer().getScheduler()
                .runTaskLater(GeyserRefinePlugin.getInstance(), task, 1);
    }
}