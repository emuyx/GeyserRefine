package com.emuyx.geyserrefine.extension.loader;

import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OptionalPackLoader {
    private final Map<String, ResourcePack> packs = new HashMap<>();
    private final Map<String, ResourcePackManifest> packInfo = new HashMap<>();

    public OptionalPackLoader(Path packsPath) {
        loadPacks(packsPath);
    }

    private void loadPacks(Path path) {
        if (!path.toFile().exists()) {
            path.toFile().mkdirs();
            return;
        }
        for (File file : Objects.requireNonNull(path.toFile().listFiles())) {
            if (file.isFile() && file.getName().endsWith(".mcpack")) {
                try {
                    ResourcePack pack = ResourcePack.create(PackCodec.path(file.toPath()));
                    String uuid = pack.manifest().header().uuid().toString();
                    packs.put(uuid, pack);
                    packInfo.put(uuid, pack.manifest());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public Map<String, ResourcePack> getPacks() {
        return packs;
    }
}