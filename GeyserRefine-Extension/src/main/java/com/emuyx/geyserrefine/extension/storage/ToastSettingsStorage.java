package com.emuyx.geyserrefine.extension.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.geysermc.geyser.api.extension.Extension;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class ToastSettingsStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path storageFile;

    public static final String PRESET_BEDROCK = "bedrock";
    public static final String PRESET_JAVA = "java";
    public static final String PRESET_CUSTOM = "custom";

    public ToastSettingsStorage(Extension extension) {
        this.storageFile = extension.dataFolder().resolve("player_settings.json");
        try {
            Files.createDirectories(extension.dataFolder());
            if (!Files.exists(storageFile)) {
                Files.createFile(storageFile);
                try (Writer writer = Files.newBufferedWriter(storageFile)) {
                    writer.write("{}");
                }
            }
        } catch (IOException e) {
            extension.logger().warning("Failed to create player_settings.json: " + e.getMessage());
        }
    }

    private JsonObject loadRoot() {
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            JsonObject obj = GSON.fromJson(reader, JsonObject.class);
            return obj != null ? obj : new JsonObject();
        } catch (IOException e) {
            return new JsonObject();
        }
    }

    private void saveRoot(JsonObject root) {
        try (Writer writer = Files.newBufferedWriter(storageFile)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Boolean
    public boolean getSetting(String playerXuid, String key, boolean defaultValue) {
        JsonObject root = loadRoot();
        if (root != null && root.has(playerXuid)) {
            JsonObject playerData = root.getAsJsonObject(playerXuid);
            if (playerData != null && playerData.has(key)) {
                return playerData.get(key).getAsBoolean();
            }
        }
        return defaultValue;
    }

    public void setSetting(String playerXuid, String key, boolean value) {
        JsonObject root = loadRoot();
        if (root == null) root = new JsonObject();
        JsonObject playerData = root.has(playerXuid) ? root.getAsJsonObject(playerXuid) : new JsonObject();
        playerData.addProperty(key, value);
        root.add(playerXuid, playerData);
        saveRoot(root);
    }

    // String
    public String getSetting(String playerXuid, String key, String defaultValue) {
        JsonObject root = loadRoot();
        if (root != null && root.has(playerXuid)) {
            JsonObject playerData = root.getAsJsonObject(playerXuid);
            if (playerData != null && playerData.has(key)) {
                return playerData.get(key).getAsString();
            }
        }
        return defaultValue;
    }

    public void setSetting(String playerXuid, String key, String value) {
        JsonObject root = loadRoot();
        if (root == null) root = new JsonObject();
        JsonObject playerData = root.has(playerXuid) ? root.getAsJsonObject(playerXuid) : new JsonObject();
        playerData.addProperty(key, value);
        root.add(playerXuid, playerData);
        saveRoot(root);
    }

    // Int
    public int getSettingInt(String playerXuid, String key, int defaultValue) {
        JsonObject root = loadRoot();
        if (root != null && root.has(playerXuid)) {
            JsonObject playerData = root.getAsJsonObject(playerXuid);
            if (playerData != null && playerData.has(key)) {
                return playerData.get(key).getAsInt();
            }
        }
        return defaultValue;
    }

    public void setSettingInt(String playerXuid, String key, int value) {
        JsonObject root = loadRoot();
        if (root == null) root = new JsonObject();
        JsonObject playerData = root.has(playerXuid) ? root.getAsJsonObject(playerXuid) : new JsonObject();
        playerData.addProperty(key, value);
        root.add(playerXuid, playerData);
        saveRoot(root);
    }

    // ----- 预设 -----
    public String getPreset(String xuid) {
        String preset = getSetting(xuid, "preset", (String) null);
        if (preset == null) {
            boolean javaAttack = getSetting(xuid, "java-attack", false);
            boolean touchSwing = getSetting(xuid, "touch-swing", false);
            if (javaAttack) {
                preset = PRESET_JAVA;
            } else if (!touchSwing) {
                preset = PRESET_BEDROCK;
            } else {
                preset = PRESET_CUSTOM;
            }
            setPreset(xuid, preset);
            removeKey(xuid, "java-attack");
        }
        return preset;
    }

    public void setPreset(String xuid, String preset) {
        setSetting(xuid, "preset", preset);
    }

    // ----- 攻击设置 -----
    public boolean getJavaAttack(String xuid) {
        return getSetting(xuid, "java-attack", false);
    }

    public void setJavaAttack(String xuid, boolean enabled) {
        setSetting(xuid, "java-attack", enabled);
    }

    public boolean getTouchSwing(String xuid) {
        return getSetting(xuid, "touch-swing", false);
    }

    public void setTouchSwing(String xuid, boolean enabled) {
        setSetting(xuid, "touch-swing", enabled);
    }

    public boolean getMuteNodamageSound(String xuid) {
        return getSetting(xuid, "mute-nodamage-sound", false);
    }

    public void setMuteNodamageSound(String xuid, boolean enabled) {
        setSetting(xuid, "mute-nodamage-sound", enabled);
    }

    public boolean getOptionalPackEnabled(String xuid) {
        return getSetting(xuid, "optional-pack", false);
    }

    public void setOptionalPackEnabled(String xuid, boolean enabled) {
        setSetting(xuid, "optional-pack", enabled);
    }

    // ----- 辅助设置 -----
    public boolean getShowToast(String xuid) {
        return getSetting(xuid, "show-toast", true);
    }

    public boolean getReduceMiningParticles(String xuid) {
        return getSetting(xuid, "reduce-mining-particles", false);
    }

    public boolean getNightVision(String xuid) {
        return getSetting(xuid, "night-vision", false);
    }

    public void setNightVision(String xuid, boolean enabled) {
        setSetting(xuid, "night-vision", enabled);
    }

    // 显示坐标（持久化，默认开）
    public boolean getShowCoordinates(String xuid) {
        return getSetting(xuid, "show-coordinates", true);
    }

    public void setShowCoordinates(String xuid, boolean enabled) {
        setSetting(xuid, "show-coordinates", enabled);
    }

    // 显示游玩天数（默认开）
    public boolean getShowDaysPlayed(String xuid) {
        return getSetting(xuid, "show-days-played", true);
    }

    public void setShowDaysPlayed(String xuid, boolean enabled) {
        setSetting(xuid, "show-days-played", enabled);
    }

    // 新增：从高级设置移过来的
    public boolean getAdvancedTooltips(String xuid) {
        return getSetting(xuid, "advanced-tooltips", false);
    }

    public void setAdvancedTooltips(String xuid, boolean enabled) {
        setSetting(xuid, "advanced-tooltips", enabled);
    }

    public boolean getCustomSkulls(String xuid) {
        return getSetting(xuid, "custom-skulls", true);
    }

    public void setCustomSkulls(String xuid, boolean enabled) {
        setSetting(xuid, "custom-skulls", enabled);
    }

    public int getDoubleClickMs(String xuid) {
        return getSettingInt(xuid, "double-click-ms", 200);
    }

    public void setDoubleClickMs(String xuid, int ms) {
        setSettingInt(xuid, "double-click-ms", ms);
    }

    public boolean getPromptOnLinks(String xuid) {
        return getSetting(xuid, "prompt-on-links", true);
    }

    public void setPromptOnLinks(String xuid, boolean enabled) {
        setSetting(xuid, "prompt-on-links", enabled);
    }

    public boolean getForceSprint(String xuid) {
        return getSetting(xuid, "force-sprint", false);
    }

    public void setForceSprint(String xuid, boolean enabled) {
        setSetting(xuid, "force-sprint", enabled);
    }

    // 界面语言（"auto" 或语言代码，如 zh_CN/en_US/zh_TW/ja_JP）
    public String getLanguage(String xuid) {
        return getSetting(xuid, "language", "auto");
    }

    public void setLanguage(String xuid, String language) {
        setSetting(xuid, "language", language);
    }

    // 从Paper同步（夜视已改为 extension 端处理，Paper 只同步 java-attack）
    public void syncFromPaper(String xuid, boolean javaAttack) {
        setJavaAttack(xuid, javaAttack);
    }

    private void removeKey(String xuid, String key) {
        JsonObject root = loadRoot();
        if (root != null && root.has(xuid)) {
            JsonObject playerData = root.getAsJsonObject(xuid);
            if (playerData != null && playerData.has(key)) {
                playerData.remove(key);
                saveRoot(root);
            }
        }
    }
}