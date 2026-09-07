package com.emuyx.geyserrefine.paper.network;

import com.emuyx.geyserrefine.paper.BCTCommand;
import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TCPMessageHandler {

    private static final Map<String, UUID> xuidToUuid = new ConcurrentHashMap<>();
    private static final Map<UUID, String> uuidToXuid = new ConcurrentHashMap<>();
    private static final Map<UUID, String> uuidToCapeId = new ConcurrentHashMap<>();
    private static final Map<String, String> xuidToCapeId = new ConcurrentHashMap<>();
    private static final Map<String, String> nameToCapeId = new ConcurrentHashMap<>();

    public static String getXuid(UUID uuid) {
        return uuidToXuid.get(uuid);
    }

    public static String getCapeId(UUID uuid) {
        String capeId = uuidToCapeId.get(uuid);
        if (capeId != null) return capeId;
        String xuid = uuidToXuid.get(uuid);
        if (xuid != null) {
            capeId = xuidToCapeId.get(xuid);
        }
        return capeId;
    }

    public static String getCapeIdByName(String playerName) {
        return nameToCapeId.get(playerName);
    }

    public static void handle(TCPMessage msg, Socket client) {
        switch (msg.type) {
            case XUID_UPDATE -> {
                if (msg.xuid != null && msg.playerUUID != null) {
                    xuidToUuid.put(msg.xuid, msg.playerUUID);
                    uuidToXuid.put(msg.playerUUID, msg.xuid);
                }
                return;
            }
            case CAPE_UPDATE -> {
                if (msg.capeId != null && msg.playerName != null) {
                    nameToCapeId.put(msg.playerName, msg.capeId);
                    if (msg.playerUUID != null) {
                        uuidToCapeId.put(msg.playerUUID, msg.capeId);
                    }
                    String xuid = uuidToXuid.get(msg.playerUUID);
                    if (xuid != null) {
                        xuidToCapeId.put(xuid, msg.capeId);
                    }
                    // 延迟刷新披风
                    Bukkit.getScheduler().runTaskLater(GeyserRefinePlugin.getInstance(), () -> {
                        refreshCapeByName(msg.playerName);
                    }, 20L);
                }
                return;
            }
            case OPEN_SETTINGS -> {
                Player player = findPlayer(msg);
                if (player == null) return;
                final Player finalPlayer = player;
                Bukkit.getScheduler().runTask(GeyserRefinePlugin.getInstance(), () -> {
                    Bukkit.dispatchCommand(finalPlayer, "geyserrefine settings");
                });
            }
            case ESCAPE -> {
                Player player = findPlayer(msg);
                if (player == null) return;
                final Player finalPlayer = player;
                Bukkit.getScheduler().runTask(GeyserRefinePlugin.getInstance(), () -> {
                    new BCTCommand(GeyserRefinePlugin.getInstance()).escapePlayer(finalPlayer);
                });
            }
            case SYNC_SETTINGS -> {
                Player player = findPlayer(msg);
                if (player == null) return;
                if (msg.settings != null) {
                    Boolean javaAttack = msg.settings.get("java-attack");
                    if (javaAttack != null) {
                        PlayerSettings.setSetting(player, "java-attack", javaAttack);
                    }
                    // 夜视已由 extension 端伪造，不再在 Paper 端设置
                }
            }
            default -> {
                GeyserRefinePlugin.getInstance().getLogger().warning(
                        "[TCP] Unknown message type: " + msg.type + " from " + client.getRemoteSocketAddress()
                );
            }
        }
    }

    private static Player findPlayer(TCPMessage msg) {
        Player player = null;
        if (msg.playerName != null && !msg.playerName.isEmpty()) {
            player = Bukkit.getPlayerExact(msg.playerName);
        }
        if (player == null && msg.playerUUID != null) {
            player = Bukkit.getPlayer(msg.playerUUID);
        }
        if (player == null) {
            GeyserRefinePlugin.getInstance().getLogger().warning(
                    "[TCP] Player not found for: " + (msg.playerName != null ? msg.playerName : msg.playerUUID)
            );
        }
        return player;
    }

    private static void refreshCapeByName(String playerName) {
        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            Bukkit.getScheduler().runTaskLater(GeyserRefinePlugin.getInstance(), () -> {
                Player p = Bukkit.getPlayerExact(playerName);
                if (p != null && GeyserRefinePlugin.getInstance().getCapeWorkaround() != null) {
                    GeyserRefinePlugin.getInstance().getCapeWorkaround().refreshCapeForPlayer(p.getUniqueId());
                }
            }, 40L);
            return;
        }
        if (GeyserRefinePlugin.getInstance().getCapeWorkaround() != null) {
            GeyserRefinePlugin.getInstance().getCapeWorkaround().refreshCapeForPlayer(player.getUniqueId());
        }
    }

    public static void broadcastSettings(Player player) {
        TCPMessage msg = new TCPMessage();
        msg.type = TCPMessage.Type.SYNC_SETTINGS;
        msg.playerUUID = player.getUniqueId();
        msg.playerName = player.getName();
        msg.settings = Map.of(
                "java-attack", PlayerSettings.getSetting(player, "java-attack")
        );
        GeyserRefinePlugin.getInstance().getTCPServer().broadcast(msg);
    }
}