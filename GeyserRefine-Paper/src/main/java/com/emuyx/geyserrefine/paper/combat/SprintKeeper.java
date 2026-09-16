package com.emuyx.geyserrefine.paper.combat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 疾跑状态追踪，用于实现"基岩版攻击不打断疾跑"。
 * <p>
 * Java 版中，冲刺状态下攻击会使玩家停止疾跑；基岩版没有这个机制。
 * Java 的攻击流程会先 {@code setSprinting(false)} 再触发伤害事件，因此伤害
 * 事件里 {@code isSprinting()} 已是 false —— 这里通过监听
 * {@link PlayerToggleSprintEvent} 记录"本 tick 刚停止疾跑"，供伤害处理判断并恢复。
 */
public class SprintKeeper implements Listener {

    private static final Map<UUID, Long> lastStopTick = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) {
            lastStopTick.put(event.getPlayer().getUniqueId(), (long) Bukkit.getCurrentTick());
        }
    }

    /** 该玩家是否在本 tick 内刚停止疾跑（很可能是攻击导致的）。 */
    public static boolean justStoppedSprinting(Player player) {
        Long tick = lastStopTick.get(player.getUniqueId());
        return tick != null && Bukkit.getCurrentTick() - tick <= 0;
    }

    public static void forget(Player player) {
        lastStopTick.remove(player.getUniqueId());
    }
}
