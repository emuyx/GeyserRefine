package com.emuyx.geyserrefine.extension;

import org.geysermc.geyser.api.extension.Extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 扩展配置（config.yml）。
 * <p>
 * 首次加载时若文件不存在则写入默认配置。
 * 当前仅支持简单的 "key: value" 行格式。
 */
public final class GeyserRefineConfig {

    /** 灵魂出窍是否启用。关闭后菜单中不显示"灵魂出窍"按钮。 */
    private static boolean freecamEnabled = true;

    private GeyserRefineConfig() {}

    public static void load(Extension extension) {
        try {
            Path configFile = extension.dataFolder().resolve("config.yml");
            if (Files.exists(configFile)) {
                for (String line : Files.readAllLines(configFile)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int colon = trimmed.indexOf(':');
                    if (colon <= 0) continue;
                    String key = trimmed.substring(0, colon).trim();
                    String value = trimmed.substring(colon + 1).trim();
                    switch (key) {
                        case "freecam-enabled" -> freecamEnabled = Boolean.parseBoolean(value);
                        default -> {}
                    }
                }
            } else {
                // 写入默认配置
                String defaults = "# GeyserRefine 扩展配置\n"
                        + "# 灵魂出窍是否启用（关闭后菜单不显示该按钮）\n"
                        + "freecam-enabled: true\n";
                Files.writeString(configFile, defaults);
            }
        } catch (IOException e) {
            extension.logger().warning("Failed to load config.yml: " + e.getMessage());
        }
    }

    public static boolean isFreecamEnabled() {
        return freecamEnabled;
    }
}
