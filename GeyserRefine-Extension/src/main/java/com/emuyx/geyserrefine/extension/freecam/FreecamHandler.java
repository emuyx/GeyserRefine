package com.emuyx.geyserrefine.extension.freecam;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.api.bedrock.camera.CameraEaseType;
import org.geysermc.geyser.api.bedrock.camera.CameraPerspective;
import org.geysermc.geyser.api.bedrock.camera.CameraPosition;
import org.geysermc.geyser.api.bedrock.camera.GuiElement;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * "灵魂出窍" (Out-of-Body / Freecam).
 *
 * <p>Follows the same pattern as {@code EntitySpectateHelper}:
 * <ol>
 *   <li>Body made <b>invisible</b> ({@link EntityFlag#INVISIBLE}).</li>
 *   <li>Spectator-style HUD elements hidden.</li>
 *   <li>Camera detached via {@link CameraPosition} with
 *       {@code FREE} preset + {@link CameraEaseType#LINEAR} easing.</li>
 *   <li>PlayerAuthInput translator returns early
 *       — no movement forwarded to Java.</li>
 *   <li>On exit: everything restored.</li>
 * </ol>
 */
public final class FreecamHandler {

    static final Map<UUID, FreecamState> STATES = new ConcurrentHashMap<>();

    private static final float BASE_SPEED      = 0.25f;
    private static final float FAST_MULTIPLIER  = 3.0f;
    private static final float EYE_HEIGHT       = 1.62f;
    private static final float EASE_SECONDS     = 0.15f;
    private static final float POS_THRESH_SQ    = 0.0001f;
    private static final float ROT_THRESH_DEG   = 1.0f;

    // ---- Spectator-style HUD elements to hide during freecam ----
    private static final GuiElement[] HIDDEN_HUD = {
        GuiElement.AIR_BUBBLES_BAR,
        GuiElement.ARMOR,
        GuiElement.HEALTH,
        GuiElement.FOOD_BAR,
        GuiElement.PROGRESS_BAR,
        GuiElement.TOOL_TIPS,
        GuiElement.PAPER_DOLL,
        GuiElement.VEHICLE_HEALTH
    };

    private FreecamHandler() {}

    // ============================================================
    //  Public API
    // ============================================================

    public static boolean isActive(GeyserSession session) {
        return STATES.containsKey(session.javaUuid());
    }

    public static void toggle(GeyserSession session) {
        if (isActive(session)) exit(session); else enter(session);
    }

    public static void cleanup(GeyserSession session) {
        FreecamState s = STATES.remove(session.javaUuid());
        if (s != null) {
            setBodyVisible(session, true);
            setHudHidden(session, false);
        }
    }

    public static void cleanupAll() {
        STATES.clear();
    }

    // ============================================================
    //  Per-tick — called from translator
    // ============================================================

    public static void onPlayerAuthInput(GeyserSession session, PlayerAuthInputPacket packet) {
        FreecamState s = STATES.get(session.javaUuid());
        if (s == null) return;

        Vector3f rot    = packet.getRotation();
        Vector2f motion = packet.getMotion();
        var       in    = packet.getInputData();

        float yaw   = rot.getY();
        float pitch = clamp(rot.getX(), -90f, 90f);

        float speed = BASE_SPEED;
        if (in.contains(PlayerAuthInputData.SPRINTING)
                || in.contains(PlayerAuthInputData.START_SPRINTING)) {
            speed *= FAST_MULTIPLIER;
        }

        double rad = Math.toRadians(yaw);
        float fX = (float) -Math.sin(rad), fZ = (float) Math.cos(rad);
        float rX = (float)  Math.cos(rad), rZ = (float) Math.sin(rad);

        float dx = (rX * motion.getX() + fX * motion.getY()) * speed;
        float dz = (rZ * motion.getX() + fZ * motion.getY()) * speed;
        float dy = 0f;
        if (in.contains(PlayerAuthInputData.JUMPING))  dy = speed;
        if (in.contains(PlayerAuthInputData.SNEAKING)
                || in.contains(PlayerAuthInputData.START_SNEAKING)) dy = -speed;

        Vector3f newPos = s.position.add(dx, dy, dz);
        boolean posChg  = newPos.distanceSquared(s.lastSentPos) >= POS_THRESH_SQ;
        boolean rotChg  = Math.abs(yaw   - s.lastSentYaw)   >= ROT_THRESH_DEG
                       || Math.abs(pitch - s.lastSentPitch) >= ROT_THRESH_DEG;

        s.position = newPos;
        s.yaw      = yaw;
        s.pitch    = pitch;

        if (posChg || rotChg) {
            s.lastSentPos   = newPos;
            s.lastSentYaw   = yaw;
            s.lastSentPitch = pitch;
            sendFreecamPosition(session, newPos, yaw, pitch);
        }

        // 每 tick 把客户端身体钉回冻结位置，防止客户端的本地移动预测
        // 在玩家同时上下移动 + 水平移动时带动身体漂移。
        // 旋转使用当前摄像机旋转，避免 MovePlayerPacket 覆盖摄像机视角
        pinBody(session, s, yaw, pitch);
    }

    // ============================================================
    //  Enter / Exit
    // ============================================================

    private static void enter(GeyserSession session) {
        SessionPlayerEntity self = session.getPlayerEntity();

        Vector3f bodyPos   = self.getPosition();   // 脚部位置
        float    bodyYaw   = self.getYaw();
        float    bodyPitch = self.getPitch();
        Vector3f camPos    = bodyPos.add(0f, EYE_HEIGHT, 0f);

        FreecamState s = new FreecamState(camPos, bodyYaw, bodyPitch,
                                          bodyPos, bodyYaw, bodyPitch);
        s.lastSentPos   = camPos;
        s.lastSentYaw   = bodyYaw;
        s.lastSentPitch = bodyPitch;
        STATES.put(session.javaUuid(), s);

        // 0) 先钉住身体再分离摄像机，避免进入瞬间身体已漂移
        pinBody(session, s, bodyYaw, bodyPitch);

        // ① 隐藏身体（INVISIBLE flag，与 EntitySpectateHelper 相同做法）
        setBodyVisible(session, false);

        // ② 隐藏旁观者风格的 HUD 元素
        setHudHidden(session, true);

        // ③ 发送 FREE 摄像机 + LINEAR 缓动
        sendFreecamPosition(session, camPos, bodyYaw, bodyPitch);

        session.sendMessage("§b☁ 已进入灵魂出窍模式 §7— 视角已脱离身体");
        session.sendMessage("§7  WASD移动 | 空格上升 | 潜行下降 | 疾跑加速");
        session.sendMessage("§7  双击背包 → 基岩版菜单 → 再次点击退出");
    }

    private static void exit(GeyserSession session) {
        FreecamState s = STATES.remove(session.javaUuid());
        if (s != null) {
            // 0) 把身体钉回原始位置和旋转，再恢复可见
            pinBody(session, s, s.bodyYaw, s.bodyPitch);
        }

        // ① 恢复身体可见
        setBodyVisible(session, true);

        // ② 恢复 HUD 元素
        setHudHidden(session, false);

        // ③ 恢复第一人称 + 延迟清除
        try {
            session.camera().forceCameraPerspective(CameraPerspective.FIRST_PERSON);
        } catch (Exception ignored) {}
        session.scheduleInEventLoop(
                () -> session.camera().clearCameraInstructions(),
                150, TimeUnit.MILLISECONDS);

        session.sendMessage("§b☁ 已退出灵魂出窍模式 §7— 视角已回归身体");
    }

    // ============================================================
    //  Helpers
    // ============================================================

    /** Toggle {@link EntityFlag#INVISIBLE} on the local player entity. */
    private static void setBodyVisible(GeyserSession session, boolean visible) {
        SessionPlayerEntity self = session.getPlayerEntity();
        self.setFlag(EntityFlag.INVISIBLE, !visible);
        self.updateBedrockMetadata();
    }

    /**
     * 把客户端身体钉回冻结位置（bedrock 坐标 = 脚部 + 眼高偏移）。
     * 基岩客户端有自己的移动预测，即使服务端不转发移动包，客户端仍会在本地
     * 模拟身体移动（尤其是同时按跳/潜行 + WASD 时）。每 tick 发一个
     * {@link MovePlayerPacket}(RESPAWN) 把身体强制拉回，配合 FREE 摄像机预设，
     * 只动身体不动相机。
     *
     * @param yaw   传给身体的 yaw（每 tick 传摄像机 yaw，避免覆盖视角；进入/退出传冻结 yaw）
     * @param pitch 传给身体的 pitch（同理）
     */
    private static void pinBody(GeyserSession session, FreecamState s, float yaw, float pitch) {
        MovePlayerPacket pkt = new MovePlayerPacket();
        pkt.setRuntimeEntityId(session.getPlayerEntity().geyserId());
        pkt.setPosition(s.bodyPos.up(EYE_HEIGHT));              // bedrock 坐标
        pkt.setRotation(Vector3f.from(pitch, yaw, yaw));        // pitch, yaw, headYaw
        pkt.setMode(MovePlayerPacket.Mode.RESPAWN);
        session.sendUpstreamPacket(pkt);
    }

    /** Hide/show spectator-style HUD elements. */
    private static void setHudHidden(GeyserSession session, boolean hidden) {
        for (GuiElement element : HIDDEN_HUD) {
            if (hidden) {
                session.camera().hideElement(element);
            } else {
                session.camera().resetElement(element);
            }
        }
    }

    /**
     * Sends a FREE-camera position update with LINEAR easing,
     * exactly like {@code EntitySpectateHelper.sendCamera()}.
     */
    private static void sendFreecamPosition(GeyserSession session,
                                            Vector3f pos, float yaw, float pitch) {
        CameraPosition cam = CameraPosition.builder()
                .position(pos)
                .rotationX((int) pitch)
                .rotationY((int) yaw)
                .easeType(CameraEaseType.LINEAR)
                .easeSeconds(EASE_SECONDS)
                .playerPositionForAudio(true)
                .renderPlayerEffects(false)
                .build();
        session.camera().sendCameraPosition(cam);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : v > max ? max : v;
    }

    // ============================================================
    //  State
    // ============================================================

    static final class FreecamState {
        Vector3f position;      // 摄像机位置（眼高）
        float yaw, pitch;       // 摄像机朝向
        Vector3f lastSentPos;
        float lastSentYaw, lastSentPitch;

        // 冻结的身体状态（脚部位置 + 朝向），用于钉回身体
        Vector3f bodyPos;
        float bodyYaw, bodyPitch;

        FreecamState(Vector3f pos, float yaw, float pitch,
                     Vector3f bodyPos, float bodyYaw, float bodyPitch) {
            this.position  = pos;
            this.yaw       = yaw;
            this.pitch     = pitch;
            this.bodyPos   = bodyPos;
            this.bodyYaw   = bodyYaw;
            this.bodyPitch = bodyPitch;
        }
    }
}
