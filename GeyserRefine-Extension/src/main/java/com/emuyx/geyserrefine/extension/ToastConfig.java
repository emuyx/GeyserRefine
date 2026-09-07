package com.emuyx.geyserrefine.extension;

import org.geysermc.geyser.api.extension.Extension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 加入 Toast 配置（toast.yml）。
 * <p>
 * 首次加载自动写入默认配置。加入 Toast 不再依赖逐玩家的开关，而是全局在此配置。
 */
public final class ToastConfig {

    private static boolean enabled = true;
    private static String title = "温馨提示";
    private static String content = "在服务器设置中可打开基岩版设置";

    private ToastConfig() {}

    public static void load(Extension extension) {
        try {
            Path configFile = extension.dataFolder().resolve("toast.yml");
            if (Files.exists(configFile)) {
                for (String line : Files.readAllLines(configFile)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int colon = trimmed.indexOf(':');
                    if (colon <= 0) continue;
                    String key = trimmed.substring(0, colon).trim();
                    String value = trimmed.substring(colon + 1).trim();
                    switch (key) {
                        case "enabled" -> enabled = Boolean.parseBoolean(value);
                        case "title" -> title = value;
                        case "content" -> content = value;
                        default -> {}
                    }
                }
            } else {
                String defaults = "# 加入服务器时的 Toast 提示\n"
                        + "enabled: true\n"
                        + "title: 温馨提示\n"
                        + "content: 在服务器设置中可打开基岩版设置\n";
                Files.writeString(configFile, defaults);
            }
        } catch (IOException e) {
            extension.logger().warning("Failed to load toast.yml: " + e.getMessage());
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static String getTitle() {
        return title;
    }

    public static String getContent() {
        return content;
    }
}
