package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 持久夜视（extension 端伪造）。
 * <p>
 * 不再通过 TCP 让 Paper 施加真实夜视药水效果，而是向基岩客户端直接发送
 * {@link MobEffectPacket}（NIGHT_VISION=16）伪造夜视视觉。
 * <p>
 * 为避免与真实夜视冲突：仅当玩家的 {@code effectCache} 中<b>没有</b>真实的
 * NIGHT_VISION 时才伪造。真实夜视由 Java 服务器施加并进入 Geyser 的 effectCache；
 * 伪造效果不进 cache，因此两者互不干扰。
 */
public final class NightVisionManager {

    private static final int NIGHT_VISION_EFFECT_ID = 16; // Bedrock NIGHT_VISION
    private static final int FAKE_DURATION_TICKS = 20 * 60; // 60 秒，周期性重发保活
    private static final long REFRESH_MS = 30_000;          // 每 30 秒刷新一次

    private static final Set<UUID> fakeApplied = ConcurrentHashMap.newKeySet();

    private NightVisionManager() {}

    /** 若需要伪造夜视（开关开且玩家无真实夜视），发送伪造效果；否则必要时移除。 */
    public static void refresh(GeyserSession session, ToastSettingsStorage storage) {
        String xuid = session.xuid();
        if (xuid == null) return;
        boolean settingOn = storage != null && storage.getNightVision(xuid);

        if (settingOn && !hasRealNightVision(session)) {
            if (!fakeApplied.contains(session.javaUuid())) {
                sendFake(session, MobEffectPacket.Event.ADD);
                fakeApplied.add(session.javaUuid());
            } else {
                // 保活：重置持续时间
                sendFake(session, MobEffectPacket.Event.MODIFY);
            }
        } else {
            // 关闭，或玩家有了真实夜视 → 移除伪造（不打扰真实效果）
            if (fakeApplied.remove(session.javaUuid())) {
                sendFake(session, MobEffectPacket.Event.REMOVE);
            }
        }
    }

    /** 玩家断开时清理。 */
    public static void cleanup(GeyserSession session) {
        UUID uuid = session.javaUuid();
        if (fakeApplied.remove(uuid)) {
            sendFake(session, MobEffectPacket.Event.REMOVE);
        }
    }

    private static boolean hasRealNightVision(GeyserSession session) {
        try {
            return session.getEffectCache().getEntityEffects().stream()
                    .anyMatch(e -> e.name().equals("NIGHT_VISION"));
        } catch (Exception e) {
            return false;
        }
    }

    private static void sendFake(GeyserSession session, MobEffectPacket.Event event) {
        try {
            MobEffectPacket packet = new MobEffectPacket();
            packet.setRuntimeEntityId(session.getPlayerEntity().geyserId());
            packet.setEvent(event);
            packet.setEffectId(NIGHT_VISION_EFFECT_ID);
            packet.setAmplifier(0);
            packet.setDuration(event == MobEffectPacket.Event.REMOVE ? 0 : FAKE_DURATION_TICKS);
            packet.setParticles(false);
            packet.setAmbient(false);
            session.sendUpstreamPacket(packet);
        } catch (Exception ignored) {
        }
    }
}
