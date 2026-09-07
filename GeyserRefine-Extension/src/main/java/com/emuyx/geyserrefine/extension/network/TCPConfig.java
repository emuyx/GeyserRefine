package com.emuyx.geyserrefine.extension.network;

import org.geysermc.geyser.api.extension.Extension;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class TCPConfig {
    public boolean enabled;
    public int port;
    public boolean logRetries;

    public TCPConfig(Extension extension) {
        File dataFolder = extension.dataFolder().toFile();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        File configFile = new File(dataFolder, "tcp.properties");

        // 如果文件不存在，从 resources 复制
        if (!configFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("tcp.properties")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    extension.logger().info("Created tcp.properties from resources.");
                }
            } catch (IOException e) {
                extension.logger().warning("Failed to create tcp.properties, using defaults.");
            }
        }

        // 读取配置
        Properties props = new Properties();
        try (FileReader reader = new FileReader(configFile)) {
            props.load(reader);
            this.enabled = Boolean.parseBoolean(props.getProperty("tcp.enabled", "true"));
            this.port = Integer.parseInt(props.getProperty("tcp.port", "25567"));
            this.logRetries = Boolean.parseBoolean(props.getProperty("tcp.log-retries", "false"));
            extension.logger().info("TCP config loaded: enabled=" + enabled +
                    ", port=" + port + ", logRetries=" + logRetries);
        } catch (Exception e) {
            this.enabled = true;
            this.port = 25567;
            this.logRetries = false;
            extension.logger().warning("Failed to parse tcp.properties, using defaults.");
        }
    }
}