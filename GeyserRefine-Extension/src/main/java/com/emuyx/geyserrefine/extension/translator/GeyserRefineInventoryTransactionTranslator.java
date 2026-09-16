package com.emuyx.geyserrefine.extension.translator;

import com.emuyx.geyserrefine.extension.spear.SpearSounds;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockInventoryTransactionTranslator;

/**
 * 包裹 Geyser 的 {@link BedrockInventoryTransactionTranslator}，用于矛的戳刺冷却与未击中音。
 * <p>
 * 矛的戳刺走 {@code ITEM_USE} + {@code actionType == 3}（STAB）。Geyser 原版在此
 * <b>不检查任何冷却</b>，每次点击都会发送 {@code ServerboundPlayerActionPacket(STAB)}
 * 与 {@code ServerboundSwingPacket}，导致他人按点击频率看到戳刺。
 * <p>
 * 该包只在客户端手持"刺穿类武器（矛）"时才会发出，因此这里<b>直接以包类型判定</b>，
 * 不依赖 Geyser 的手持物品状态（换手后该状态可能滞后，曾导致换掉矛后仍播矛音）。
 * 手持 id 仅用于"木矛/其它矛"的音效变体选择，缺失时回退为通用矛音。
 */
@Translator(packet = InventoryTransactionPacket.class)
public class GeyserRefineInventoryTransactionTranslator extends BedrockInventoryTransactionTranslator {

    @Override
    public void translate(GeyserSession session, InventoryTransactionPacket packet) {
        if (packet.getTransactionType() == InventoryTransactionType.ITEM_USE
                && packet.getActionType() == 3) {
            // 冷却内：不转发 STAB/Swing（同时阻止服务端执行该次攻击）
            if (!SpearSounds.tryBeginThrust(session, SpearSounds.heldSpearId(session))) {
                return;
            }
            // 冷却外：播放未击中音（若随即命中，则由命中音覆盖）
            SpearSounds.playMiss(session, SpearSounds.heldSpearId(session));
        }
        super.translate(session, packet);
    }
}
