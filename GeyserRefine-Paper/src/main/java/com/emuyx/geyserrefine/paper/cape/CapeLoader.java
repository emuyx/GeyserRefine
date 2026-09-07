package com.emuyx.geyserrefine.paper.cape;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemProfile;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.emuyx.geyserrefine.paper.network.TCPMessageHandler;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CapeLoader {
    private static final Map<UUID, JsonObject> UUID_TO_JAVA_PROFILE = new ConcurrentHashMap<>();
    private static final Map<String, UUID> CAPE_NAME_TO_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> BEDROCK_TO_JAVA_UUID = new ConcurrentHashMap<>();
    private static final Path MAPPINGS_FILE = GeyserRefinePlugin.getInstance().getDataFolder().toPath().resolve("mappings.json");
    private static final Path CAPES_DIR = GeyserRefinePlugin.getInstance().getDataFolder().toPath().resolve("capes");

    private static final String[] BUILTIN_CAPES = {
            "15thanniversary.json", "builder.json", "cherry.json", "common.json", "copper.json",
            "eyeblossom.json", "followers.json", "founders.json", "home.json",
            "mcc15thyear.json", "mcexperience.json", "menace.json", "pan.json",
            "purpleheart.json", "vanilla.json", "yearn.json",
            "minecon2011.json", "minecon2012.json", "minecon2013.json",
            "minecon2015.json", "minecon2016.json"
    };

    public static void init() {
        copyDefaultCapes();
        loadMappings();
        loadCapesFromFolder();
        CapeStorage.load();
    }

    private static void copyDefaultCapes() {
        if (!Files.exists(CAPES_DIR)) {
            try {
                Files.createDirectories(CAPES_DIR);
            } catch (IOException e) {
                GeyserRefinePlugin.getInstance().getLogger().warning("[Cape] Failed to create capes directory: " + e.getMessage());
                return;
            }
        }

        for (String fileName : BUILTIN_CAPES) {
            Path target = CAPES_DIR.resolve(fileName);
            if (Files.exists(target)) continue;

            try (InputStream in = CapeLoader.class.getClassLoader().getResourceAsStream("capes/" + fileName)) {
                if (in == null) continue;
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }
    }

    private static void loadMappings() {
        if (!Files.exists(MAPPINGS_FILE)) {
            try (InputStream in = CapeLoader.class.getClassLoader().getResourceAsStream("mappings.json")) {
                if (in == null) {
                    Files.createDirectories(MAPPINGS_FILE.getParent());
                    Files.write(MAPPINGS_FILE, "{}".getBytes(StandardCharsets.UTF_8));
                } else {
                    Files.copy(in, MAPPINGS_FILE, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                return;
            }
        }

        try (Reader reader = Files.newBufferedReader(MAPPINGS_FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (String bedrockCapeId : root.keySet()) {
                String javaUuid = root.get(bedrockCapeId).getAsString();
                try {
                    UUID bedrockId = parseUUID(bedrockCapeId);
                    UUID javaId = parseUUID(javaUuid);
                    BEDROCK_TO_JAVA_UUID.put(bedrockId, javaId);
                } catch (IllegalArgumentException e) {
                    GeyserRefinePlugin.getInstance().getLogger().warning("[Cape] Invalid UUID in mappings.json: " + bedrockCapeId + " -> " + javaUuid);
                }
            }
        } catch (IOException ignored) {}
    }

    private static void loadCapesFromFolder() {
        if (!Files.exists(CAPES_DIR)) return;
        try {
            Files.list(CAPES_DIR).filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                try (Reader reader = Files.newBufferedReader(path)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    UUID javaUuid = parseUUID(obj.get("id").getAsString());
                    UUID_TO_JAVA_PROFILE.put(javaUuid, obj);
                    String name = path.getFileName().toString().replace(".json", "");
                    CAPE_NAME_TO_UUID.put(name, javaUuid);
                } catch (Exception e) {
                    GeyserRefinePlugin.getInstance().getLogger().warning("[Cape] Failed to load cape: " + path.getFileName() + " - " + e.getMessage());
                }
            });
        } catch (IOException ignored) {}
    }

    private static UUID parseUUID(String uuidStr) {
        if (uuidStr == null) throw new IllegalArgumentException("UUID string is null");
        if (uuidStr.length() == 32) {
            StringBuffer sb = new StringBuffer(uuidStr);
            sb.insert(20, '-');
            sb.insert(16, '-');
            sb.insert(12, '-');
            sb.insert(8, '-');
            uuidStr = sb.toString();
        }
        return UUID.fromString(uuidStr);
    }

    public static ItemProfile getAsItemProfile(UUID capeUUID) {
        JsonObject object = getProfile(capeUUID);
        if (object == null) return null;
        try {
            String name = object.get("name").getAsString();
            String id = object.get("id").getAsString();
            String textureValue = object.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            String signature = object.getAsJsonArray("properties").get(0).getAsJsonObject().get("signature").getAsString();

            return new ItemProfile(
                    name,
                    parseUUID(id),
                    List.of(new ItemProfile.Property("textures", textureValue, signature)),
                    new ItemProfile.SkinPatch(ResourceLocation.minecraft("gui/tab_header_background"), null, null, null)
            );
        } catch (Exception e) {
            GeyserRefinePlugin.getInstance().getLogger().warning("[Cape] Failed to create ItemProfile for " + capeUUID + ": " + e.getMessage());
            return null;
        }
    }

    public static JsonObject getProfile(UUID uuid) {
        JsonObject profile = CapeStorage.getProfile(uuid);
        if (profile != null) return profile;
        return UUID_TO_JAVA_PROFILE.get(uuid);
    }

    public static boolean exists(UUID uuid) {
        UUID javaUuid = BEDROCK_TO_JAVA_UUID.getOrDefault(uuid, uuid);
        return CapeStorage.exists(javaUuid) || UUID_TO_JAVA_PROFILE.containsKey(javaUuid);
    }

    public static UUID getCapeUUID(Player player) {
        String capeId = TCPMessageHandler.getCapeId(player.getUniqueId());
        if (capeId == null || capeId.isEmpty()) {
            capeId = TCPMessageHandler.getCapeIdByName(player.getName());
        }
        if (capeId == null || capeId.isEmpty()) return null;

        UUID bedrockCapeId;
        try {
            bedrockCapeId = parseUUID(capeId);
        } catch (IllegalArgumentException e) {
            return CAPE_NAME_TO_UUID.get(capeId);
        }

        UUID javaUuid = BEDROCK_TO_JAVA_UUID.get(bedrockCapeId);
        if (javaUuid != null) return javaUuid;
        if (UUID_TO_JAVA_PROFILE.containsKey(bedrockCapeId) || CapeStorage.exists(bedrockCapeId)) {
            return bedrockCapeId;
        }
        return null;
    }

    public static void reloadMappings() {
        BEDROCK_TO_JAVA_UUID.clear();
        loadMappings();
    }
}