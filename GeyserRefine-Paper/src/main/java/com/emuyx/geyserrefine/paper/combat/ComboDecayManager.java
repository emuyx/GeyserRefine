package com.emuyx.geyserrefine.paper.combat;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨类型 PvP 连击伤害递减（软递减）。
 *
 * <p>只用于基岩玩家 ↔ Java 玩家之间的战斗：连续攻击会递减伤害，
 * 停止攻击超过窗口时间后重置为满伤害。攻击同类型玩家或生物不受影响。
 *
 * <p>递减公式：{@code multiplier = max(min-multiplier, 1 - (combo-1) * decay-per-hit)}
 *
 * <p>递减触发期间（multiplier &lt; 1.0），受击者不受击退。
 */
public final class ComboDecayManager {

    private static final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> comboCount = new ConcurrentHashMap<>();

    // 被标记"本次无击退"的受击者（跨类型连击递减触发时）
    private static final Set<UUID> noKnockback = ConcurrentHashMap.newKeySet();

    private ComboDecayManager() {}

    /**
     * 计算并记录一次攻击的连击递减倍率。
     * 该攻击若发生在窗口时间内则连击数 +1，否则重置为 1。
     *
     * @param attacker 攻击者
     * @param config   战斗配置（提供窗口/递减参数）
     * @return 伤害倍率（1.0 表示满伤害）
     */
    public static double apply(Player attacker, BedrockCombatConfig config) {
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        long windowMs = config.comboDecayWindowTicks * 50L;

        int count = comboCount.getOrDefault(uuid, 0);
        Long last = lastAttackTime.get(uuid);
        if (last != null && (now - last) <= windowMs) {
            count++;
        } else {
            count = 1;
        }

        lastAttackTime.put(uuid, now);
        comboCount.put(uuid, count);

        double multiplier = 1.0 - (count - 1) * config.comboDecayPerHit;
        return Math.max(config.comboDecayMinMultiplier, multiplier);
    }

    /** 标记受击者本次攻击无击退。 */
    public static void markNoKnockback(LivingEntity victim) {
        noKnockback.add(victim.getUniqueId());
    }

    /** 查询并清除受击者的无击退标记（在 PlayerVelocityEvent 中调用）。 */
    public static boolean consumeNoKnockback(UUID uuid) {
        return noKnockback.remove(uuid);
    }

    /** 玩家退出时清理连击状态。 */
    public static void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        lastAttackTime.remove(uuid);
        comboCount.remove(uuid);
        noKnockback.remove(uuid);
    }
}
