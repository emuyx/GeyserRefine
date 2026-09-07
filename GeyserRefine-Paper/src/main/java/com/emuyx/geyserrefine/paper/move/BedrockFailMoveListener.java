package com.emuyx.geyserrefine.paper.move;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import io.papermc.paper.event.player.PlayerFailMoveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class BedrockFailMoveListener implements Listener {

    private final BedrockCombatConfig config;

    public BedrockFailMoveListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFailMove(PlayerFailMoveEvent event) {
        if (!config.blockFailMove) return;
        if (!FloodgateUtil.isBedrockPlayer(event.getPlayer())) return;
        if (PlayerSettings.getSetting(event.getPlayer(), "java-attack")) return;

        // 针对所有常见的基岩版运动异常原因，直接放行。
        // Paper 的事件允许调用 setAllowed(true) 来恢复这次移动，
        // 同时可以将日志输出关掉以免刷屏。
        event.setAllowed(true);
        event.setLogWarning(false);
    }
}