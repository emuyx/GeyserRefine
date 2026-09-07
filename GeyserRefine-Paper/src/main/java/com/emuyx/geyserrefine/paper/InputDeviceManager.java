package com.emuyx.geyserrefine.paper;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.util.InputMode;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InputDeviceManager {

    private static final Map<UUID, InputMode> inputModeCache = new ConcurrentHashMap<>();
    private static BedrockCombatConfig config;

    public static void init(BedrockCombatConfig config) {
        InputDeviceManager.config = config;
    }

    /**
     * 刷新玩家的输入模式（玩家加入时调用）
     */
    public static void updateInputMode(Player player) {
        if (!FloodgateUtil.isBedrockPlayer(player)) {
            inputModeCache.remove(player.getUniqueId());
            return;
        }
        FloodgatePlayer fPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (fPlayer != null) {
            InputMode inputMode = fPlayer.getInputMode();
            inputModeCache.put(player.getUniqueId(), inputMode);
        }
    }

    /**
     * 获取玩家对应的方块交互距离（单位：格）
     */
    public static double getBlockRange(Player player) {
        InputMode mode = inputModeCache.getOrDefault(player.getUniqueId(), InputMode.UNKNOWN);
        return switch (mode) {
            case KEYBOARD_MOUSE -> config.blockRangeKeyboardMouse;
            case CONTROLLER -> config.blockRangeController;
            case TOUCH -> config.blockRangeTouch;
            default -> config.blockRangeUnknown;  // 包括 UNKNOWN 及其他新增值
        };
    }
}