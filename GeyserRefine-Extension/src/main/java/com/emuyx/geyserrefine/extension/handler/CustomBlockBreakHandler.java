package com.emuyx.geyserrefine.extension.handler;

import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.physics.Direction;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.BlockBreakHandler;

public class CustomBlockBreakHandler extends BlockBreakHandler {
    private final ToastSettingsStorage storage;

    public CustomBlockBreakHandler(GeyserSession session, ToastSettingsStorage storage) {
        super(session);
        this.storage = storage;
    }

    @Override
    protected void handleContinueDestroy(Vector3i position, BlockState state, Direction blockFace,
                                         boolean bedrockDestroyed, boolean sendParticles, long tick) {
        boolean reduce = storage.getReduceMiningParticles(session.xuid());
        boolean finalSendParticles = reduce ? false : true;
        super.handleContinueDestroy(position, state, blockFace, bedrockDestroyed, finalSendParticles, tick);
    }
}