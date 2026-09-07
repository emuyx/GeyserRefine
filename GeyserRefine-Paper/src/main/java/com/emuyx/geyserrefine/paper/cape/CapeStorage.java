package com.emuyx.geyserrefine.paper.cape;

import com.google.gson.*;
import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CapeStorage {
    private static final Path STORAGE_FILE = GeyserRefinePlugin.getInstance().getDataFolder().toPath().resolve("capes.json");
    private static final Map<UUID, JsonObject> DYNAMIC_MAP = new ConcurrentHashMap<>();

    public static void load() {
        if (!Files.exists(STORAGE_FILE)) return;
        try (Reader reader = Files.newBufferedReader(STORAGE_FILE)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement elem : array) {
                JsonObject obj = elem.getAsJsonObject();
                UUID capeId = UUID.fromString(obj.get("capeId").getAsString());
                JsonObject profile = obj.getAsJsonObject("profile");
                DYNAMIC_MAP.put(capeId, profile);
            }
        } catch (Exception e) {
            GeyserRefinePlugin.getInstance().getLogger().warning("Failed to load cape storage: " + e.getMessage());
        }
    }

    public static void save() {
        try {
            JsonArray array = new JsonArray();
            for (Map.Entry<UUID, JsonObject> entry : DYNAMIC_MAP.entrySet()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("capeId", entry.getKey().toString());
                obj.add("profile", entry.getValue());
                array.add(obj);
            }
            try (Writer writer = Files.newBufferedWriter(STORAGE_FILE)) {
                writer.write(array.toString());
            }
        } catch (Exception e) {
            GeyserRefinePlugin.getInstance().getLogger().warning("Failed to save cape storage: " + e.getMessage());
        }
    }

    public static void addDynamicCape(UUID capeId, JsonObject profile) {
        DYNAMIC_MAP.put(capeId, profile);
        if (GeyserRefinePlugin.getInstance().getConfig().getBoolean("cape-workaround.save-dynamic", true)) {
            save();
        }
    }

    public static JsonObject getProfile(UUID capeId) {
        return DYNAMIC_MAP.get(capeId);
    }

    public static boolean exists(UUID capeId) {
        return DYNAMIC_MAP.containsKey(capeId);
    }

    public static int getDynamicCount() {
        return DYNAMIC_MAP.size();
    }
}