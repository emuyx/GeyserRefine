package com.emuyx.geyserrefine.extension.translator;

import com.emuyx.geyserrefine.extension.spear.SpearSounds;
import org.cloudburstmc.protocol.bedrock.packet.InteractPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.protocol.bedrock.entity.player.BedrockInteractTranslator;

/**
 * 包裹 Geyser 的 {@link BedrockInteractTranslator}：持矛击中实体时播放矛命中音。
 * Geyser 不翻译矛的命中音效，因此在此补上；其余交互仍交由原翻译器处理。
 */
@Translator(packet = InteractPacket.class)
public class GeyserRefineInteractTranslator extends BedrockInteractTranslator {

    @Override
    public void translate(GeyserSession session, InteractPacket packet) {
        if (packet.getAction() == InteractPacket.Action.DAMAGE) {
            // 用"最近是否戳刺过"判定，而非手持物品（换手后手持状态可能滞后）
            if (SpearSounds.wasRecentThrust(session)) {
                SpearSounds.playHit(session, SpearSounds.heldSpearId(session));
            }
        }
        super.translate(session, packet);
    }
}
