package com.emuyx.geyserrefine.paper.network;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class TCPConfig {
    public boolean enabled;
    public int port;
    public boolean logRetries;

    public TCPConfig(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        File configFile = new File(dataFolder, "tcp.properties");

        if (!configFile.exists()) {
            try (InputStream in = plugin.getResource("tcp.properties")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Created tcp.properties from resources.");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create tcp.properties, using defaults.");
            }
        }

        Properties props = new Properties();
        try (FileReader reader = new FileReader(configFile)) {
            props.load(reader);
            this.enabled = Boolean.parseBoolean(props.getProperty("tcp.enabled", "true"));
            this.port = Integer.parseInt(props.getProperty("tcp.port", "25567"));
            this.logRetries = Boolean.parseBoolean(props.getProperty("tcp.log-retries", "false"));
            plugin.getLogger().info("TCP config loaded: enabled=" + enabled +
                    ", port=" + port + ", logRetries=" + logRetries);
        } catch (Exception e) {
            this.enabled = true;
            this.port = 25567;
            this.logRetries = false;
            plugin.getLogger().warning("Failed to parse tcp.properties, using defaults.");
        }
    }
}