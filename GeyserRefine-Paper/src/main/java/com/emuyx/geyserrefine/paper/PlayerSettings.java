package com.emuyx.geyserrefine.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSettings implements Listener {

    private static final Map<UUID, Map<String, Boolean>> playerSettings = new ConcurrentHashMap<>();
    private static BedrockCombatConfig config;
    private static File storageFile;
    private static YamlConfiguration storage;

    public static void init(BedrockCombatConfig config, File dataFolder) {
        PlayerSettings.config = config;
        storageFile = new File(dataFolder, "playerdata.yml");
        if (!storageFile.exists()) {
            try {
                storageFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        storage = YamlConfiguration.loadConfiguration(storageFile);
    }

    public static boolean getSetting(Player player, String key) {
        Map<String, Boolean> settings = playerSettings.get(player.getUniqueId());
        if (settings != null && settings.containsKey(key)) {
            return settings.get(key);
        }
        return getDefault(key);
    }

    public static void setSetting(Player player, String key, boolean value) {
        playerSettings.compute(player.getUniqueId(), (uuid, map) -> {
            if (map == null) map = new ConcurrentHashMap<>();
            map.put(key, value);
            return map;
        });
        savePlayer(player);
    }

    private static boolean getDefault(String key) {
        return switch (key) {
            case "java-attack" -> config.defaultJavaAttack;
            default -> true;
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayer(event.getPlayer());
        playerSettings.remove(event.getPlayer().getUniqueId());
    }

    private static void loadPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        if (storage.contains(uuid.toString())) {
            ConfigurationSection section = storage.getConfigurationSection(uuid.toString());
            if (section != null) {
                Map<String, Boolean> settings = new ConcurrentHashMap<>();
                for (String key : section.getKeys(false)) {
                    if (key.equals("bedrock-attack")) {
                        boolean oldValue = section.getBoolean(key);
                        settings.put("java-attack", !oldValue);
                    } else {
                        settings.put(key, section.getBoolean(key));
                    }
                }
                playerSettings.put(uuid, settings);
            }
        }
    }

    private static void savePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Map<String, Boolean> settings = playerSettings.get(uuid);
        if (settings != null) {
            storage.set(uuid.toString(), null);
            for (Map.Entry<String, Boolean> entry : settings.entrySet()) {
                storage.set(uuid + "." + entry.getKey(), entry.getValue());
            }
        }
        saveStorage();
    }

    private static void saveStorage() {
        try {
            storage.save(storageFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveAll() {
        for (UUID uuid : playerSettings.keySet()) {
            Map<String, Boolean> settings = playerSettings.get(uuid);
            if (settings != null) {
                storage.set(uuid.toString(), null);
                for (Map.Entry<String, Boolean> entry : settings.entrySet()) {
                    storage.set(uuid + "." + entry.getKey(), entry.getValue());
                }
            }
        }
        saveStorage();
    }
}