package com.emuyx.geyserrefine.extension.network;

import com.emuyx.geyserrefine.extension.GeyserRefineExtension;
import com.emuyx.geyserrefine.extension.MenuForm;
import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Locale;

public class MessageHandler {
    private static ToastSettingsStorage storage;
    private static ExtensionLogger logger;

    public static void init(ToastSettingsStorage s, ExtensionLogger log) {
        storage = s;
        logger = log;
    }

    public static void handle(TCPMessage msg) {
        if (storage == null) return;

        switch (msg.type) {
            case SYNC_SETTINGS -> handleSyncSettings(msg);
            case TOAST -> handleToast(msg);
            case OPEN_MENU -> handleOpenMenu(msg);
            default -> {}
        }
    }

    /** Paper 请求打开目标基岩玩家的扩展主菜单（/geyserrefine settings）。 */
    private static void handleOpenMenu(TCPMessage msg) {
        GeyserSession target = findSession(msg);
        if (target == null) return;
        MenuForm.open(target);
    }

    /** 按 UUID（优先）或玩家名找到目标 GeyserSession。 */
    private static GeyserSession findSession(TCPMessage msg) {
        if (msg.playerUUID != null) {
            for (GeyserSession s : GeyserImpl.getInstance().getSessionManager().getAllSessions()) {
                if (msg.playerUUID.equals(s.javaUuid())) return s;
            }
        }
        if (msg.playerName != null) {
            String name = msg.playerName.toLowerCase(Locale.ROOT);
            for (GeyserSession s : GeyserImpl.getInstance().getSessionManager().getAllSessions()) {
                String uname = s.javaUsername();
                if (uname != null && uname.toLowerCase(Locale.ROOT).equals(name)) return s;
            }
        }
        return null;
    }

    private static void handleSyncSettings(TCPMessage msg) {
        if (msg.settings == null || msg.playerUUID == null) return;

        String xuid = GeyserRefineExtension.getXuidFromUUID(msg.playerUUID);
        if (xuid == null) return;

        Boolean javaAttack = msg.settings.get("java-attack");
        if (javaAttack != null) {
            storage.syncFromPaper(xuid, javaAttack);
            if (logger != null) {
                logger.debug("Synced settings from Paper for " + msg.playerName);
            }
        }
    }

    /** 私聊 Toast：找到目标基岩玩家会话并发送 ToastRequestPacket。 */
    private static void handleToast(TCPMessage msg) {
        if (msg.sender == null || msg.content == null) return;
        if (msg.playerUUID == null && msg.playerName == null) return;

        GeyserSession target = null;

        // ① 优先按 UUID 匹配（Paper 端已解析 Floodgate 前缀问题）
        if (msg.playerUUID != null) {
            for (GeyserSession session : GeyserImpl.getInstance().getSessionManager().getAllSessions()) {
                if (msg.playerUUID.equals(session.javaUuid())) {
                    target = session;
                    break;
                }
            }
        }

        // ② 回退：按名字（不区分大小写）匹配
        if (target == null && msg.playerName != null) {
            String targetName = msg.playerName.toLowerCase(Locale.ROOT);
            for (GeyserSession session : GeyserImpl.getInstance().getSessionManager().getAllSessions()) {
                String name = session.javaUsername();
                if (name != null && name.toLowerCase(Locale.ROOT).equals(targetName)) {
                    target = session;
                    break;
                }
            }
        }

        if (target == null) {
            if (logger != null) {
                logger.debug("Toast recipient not online: " + msg.playerName);
            }
            return;
        }

        try {
            ToastRequestPacket toast = new ToastRequestPacket();
            toast.setTitle(msg.sender + " 私聊");
            toast.setContent(msg.content);
            target.sendUpstreamPacket(toast);
            if (logger != null) {
                logger.debug("Toast to " + msg.playerName + " from " + msg.sender);
            }
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("Failed to send toast to " + msg.playerName + ": " + e.getMessage());
            }
        }
    }
}
