package com.emuyx.geyserrefine.extension.translator;

import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;

import java.lang.reflect.Field;

/**
 * 修复基岩版暂停菜单"游玩天数"每 8 天重置为 0-7 的 bug。
 * <p>
 * 原版 Geyser 的 SetTime 翻译器发送的是 {@code time % 24000}（8 天的世界时间取模），
 * 基岩客户端据此计算"游玩天数"导致每 8 天归零。本翻译器在 vanilla 逻辑之后
 * 立即补发完整的世界时间，由于发生在同一 tick 且晚于 vanilla 发送，客户端
 * 最终收到的一定是完整时间。
 */
@Translator(packet = ClientboundSetTimePacket.class)
public class GeyserRefineSetTimeTranslator extends PacketTranslator<ClientboundSetTimePacket> {

    private static final Field DAY_TIME_TICKS;

    static {
        try {
            DAY_TIME_TICKS = GeyserSession.class.getDeclaredField("dayTimeTicks");
            DAY_TIME_TICKS.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("GeyserSession.dayTimeTicks not found", e);
        }
    }

    private final PacketTranslator<ClientboundSetTimePacket> delegate;

    public GeyserRefineSetTimeTranslator(PacketTranslator<ClientboundSetTimePacket> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void translate(GeyserSession session, ClientboundSetTimePacket packet) {
        // Vanilla: 更新时钟并发送取模后的 SetTimePacket
        delegate.translate(session, packet);

        // 补发完整时间，确保它是最新收到的
        try {
            SetTimePacket fixed = new SetTimePacket();
            fixed.setTime((int) Math.abs(DAY_TIME_TICKS.getLong(session)));
            session.sendUpstreamPacket(fixed);
        } catch (Exception ignored) {
        }
    }
}
