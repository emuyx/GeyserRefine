package com.emuyx.geyserrefine.extension.spear;

import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 矛的戳刺冷却与音效（Extension 端，基岩版命名空间）。
 * <p>
 * 音效 ID（按 Wiki 基岩版命名空间）：
 * <ul>
 *   <li>木矛：未击中 {@code item.wooden_spear.attack_miss}，击中 {@code item.wooden_spear.attack_hit}</li>
 *   <li>其它矛：未击中 {@code item.spear.attack_miss}，击中 {@code item.spear.attack_hit}</li>
 * </ul>
 * 命中与未击中互斥：未击中音延迟 1 tick 播放，若期间发生命中则跳过，避免同一次戳刺听到两个音。
 * 冲锋攻击（{@code item.spear.use}/{@code .hit}）不在此实现，沿用 Java 机制。
 */
public final class SpearSounds {

    private static final Map<UUID, Long> lastThrustTick = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> lastHitTick = new ConcurrentHashMap<>();

    private SpearSounds() {}

    /**
     * 玩家当前手持的矛 id（如 "minecraft:wooden_spear"），非矛返回 null。
     * <p>
     * 用 Geyser 的 {@link Items} 常量做 Java 物品 ID 比对（{@code GeyserItemStack#is}），
     * 比读取基岩标识符更可靠——矛在基岩侧是 Geyser 自定义物品，标识符不可靠。
     */
    public static String heldSpearId(GeyserSession session) {
        try {
            GeyserItemStack held = session.getPlayerInventory().getItemInHand();
            if (held.is(Items.WOODEN_SPEAR)) return "minecraft:wooden_spear";
            if (held.is(Items.STONE_SPEAR)) return "minecraft:stone_spear";
            if (held.is(Items.COPPER_SPEAR)) return "minecraft:copper_spear";
            if (held.is(Items.IRON_SPEAR)) return "minecraft:iron_spear";
            if (held.is(Items.GOLDEN_SPEAR)) return "minecraft:golden_spear";
            if (held.is(Items.DIAMOND_SPEAR)) return "minecraft:diamond_spear";
            if (held.is(Items.NETHERITE_SPEAR)) return "minecraft:netherite_spear";
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 尝试开始一次戳刺：冷却内返回 false（调用方应拦截），冷却外记录并返回 true。
     * <p>
     * 同一次戳刺会产生多个包（{@code ITEM_USE/STAB} 的 STAB+Swing、Animate 挥手等），
     * 它们通常在同一 tick 到达；因此"同一 tick"视为同一次戳刺一并放行，
     * 避免把有效戳刺的后续包误拦。
     */
    public static boolean tryBeginThrust(GeyserSession session, String spearId) {
        UUID uuid = session.javaUuid();
        long now = session.getTicks();
        Long last = lastThrustTick.get(uuid);
        boolean sameThrust = last != null && now == last;
        if (!sameThrust && last != null && now - last < cooldownTicks(spearId)) {
            return false; // 冷却内
        }
        if (!sameThrust) {
            lastThrustTick.put(uuid, now);
        }
        return true;
    }

    /** 最近是否发生过一次戳刺（tick 内）。用于命中判定，避免依赖可能滞后的手持物品状态。 */
    public static boolean wasRecentThrust(GeyserSession session) {
        Long last = lastThrustTick.get(session.javaUuid());
        return last != null && session.getTicks() - last <= 3;
    }

    /**
     * 播放戳刺"未击中"音（延迟 1 tick；若期间命中则由命中音覆盖）。
     * <p>冷却判定由调用方通过 {@link #tryBeginThrust} 完成，本方法不做限流。
     */
    public static void playMiss(GeyserSession session, String spearId) {
        UUID uuid = session.javaUuid();
        String missSound = isWooden(spearId)
                ? "item.wooden_spear.attack_miss" : "item.spear.attack_miss";
        session.scheduleInEventLoop(() -> {
            Long hit = lastHitTick.get(uuid);
            if (hit != null && session.getTicks() - hit <= 1) return; // 已命中 → 由命中音覆盖
            play(session, missSound);
        }, 50, TimeUnit.MILLISECONDS);
    }

    /** 戳刺（击中）。 */
    public static void playHit(GeyserSession session, String spearId) {
        lastHitTick.put(session.javaUuid(), (long) session.getTicks());
        play(session, isWooden(spearId)
                ? "item.wooden_spear.attack_hit" : "item.spear.attack_hit");
    }

    public static void cleanup(GeyserSession session) {
        UUID uuid = session.javaUuid();
        lastThrustTick.remove(uuid);
        lastHitTick.remove(uuid);
    }

    private static boolean isWooden(String spearId) {
        return spearId != null && spearId.contains("wooden_spear");
    }

    private static void play(GeyserSession session, String sound) {
        try {
            PlaySoundPacket packet = new PlaySoundPacket();
            packet.setSound(sound);
            packet.setPosition(session.getPlayerEntity().getPosition().add(0, 1.62f, 0));
            packet.setVolume(1.0f);
            packet.setPitch(1.0f);
            session.sendUpstreamPacket(packet);
        } catch (Exception ignored) {
        }
    }

    /** 矛戳刺冷却（tick），按 Wiki 攻击速度换算。 */
    private static int cooldownTicks(String spearId) {
        if (spearId == null) return 18;
        return switch (spearId) {
            case "minecraft:wooden_spear" -> 13;
            case "minecraft:stone_spear" -> 15;
            case "minecraft:copper_spear" -> 17;
            case "minecraft:iron_spear", "minecraft:golden_spear" -> 19;
            case "minecraft:diamond_spear" -> 21;
            case "minecraft:netherite_spear" -> 23;
            default -> 18;
        };
    }
}
