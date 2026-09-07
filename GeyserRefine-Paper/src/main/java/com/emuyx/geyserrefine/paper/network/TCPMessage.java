package com.emuyx.geyserrefine.paper.network;

import java.util.Map;
import java.util.UUID;

public class TCPMessage {
    public enum Type {
        OPEN_SETTINGS,
        ESCAPE,
        SYNC_SETTINGS,
        XUID_UPDATE,
        CAPE_UPDATE,
        TOAST,         // 私聊Toast：sender → content 推送给 playerName
        OPEN_MENU      // Paper 请求打开扩展的主菜单（/geyserrefine settings）
    }

    public Type type;
    public UUID playerUUID;
    public String playerName;
    public String xuid;
    public String capeId;
    public String sender;     // Toast 发送者
    public String content;    // Toast 内容
    public Map<String, Boolean> settings;
}