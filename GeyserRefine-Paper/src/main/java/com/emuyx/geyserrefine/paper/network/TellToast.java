package com.emuyx.geyserrefine.paper.network;

import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.UUID;

/**
 * 私聊 Toast：当玩家使用 /msg /tell /w /whisper 时，把消息通过已有的 TCP
 * 通道推送给 Geyser 扩展，让基岩版收件人看到 toast（发送者 + 完整消息）。
 * <p>
 * 收件人优先通过 Bukkit 解析成 UUID（兼容 Floodgate 的 Java 名前缀），
 * 扩展端按 UUID 匹配会话，比按名字匹配更可靠。
 */
public class TellToast implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("/msg ") && !lower.startsWith("/tell ")
                && !lower.startsWith("/w ") && !lower.startsWith("/whisper ")) {
            return;
        }
        String[] parts = message.split("\\s+", 3);
        if (parts.length < 3) return;

        TCPMessage toast = new TCPMessage();
        toast.type = TCPMessage.Type.TOAST;
        toast.playerName = parts[1];
        toast.sender = event.getPlayer().getName();
        toast.content = parts[2];

        // 解析收件人 UUID（在线匹配，兼容大小写）
        Player recipient = findRecipient(parts[1]);
        if (recipient != null) {
            toast.playerUUID = recipient.getUniqueId();
        }

        GeyserRefinePlugin.getInstance().getTCPServer().broadcast(toast);
    }

    private static Player findRecipient(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        // Floodgate 前缀：去掉下划线再试一次（"OrangeX2204" → "_OrangeX2204"）
        String stripped = name.startsWith("_") ? name.substring(1) : "_" + name;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(stripped)) {
                return player;
            }
        }
        return null;
    }
}
