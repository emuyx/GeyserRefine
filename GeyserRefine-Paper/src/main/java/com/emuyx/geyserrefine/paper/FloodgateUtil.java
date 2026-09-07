package com.emuyx.geyserrefine.paper;

import org.geysermc.floodgate.api.FloodgateApi;
import org.bukkit.entity.Player;

/**
 * Floodgate 工具：判断某玩家是否来自基岩版。
 * <p>
 * 未安装 Floodgate（{@code NoClassDefFoundError}）或 Floodgate 未初始化
 * （{@code NullPointerException}）时一律按非基岩玩家处理，保证插件可回退运行。
 */
public class FloodgateUtil {
    /** @return true 表示该玩家为经 Floodgate 接入的基岩版玩家。 */
    public static boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (NoClassDefFoundError | NullPointerException e) {
            return false; // Floodgate 未安装/未初始化
        }
    }
}