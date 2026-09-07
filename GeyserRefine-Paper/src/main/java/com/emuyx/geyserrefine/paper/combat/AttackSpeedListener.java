package com.emuyx.geyserrefine.paper.combat;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class AttackSpeedListener extends BukkitRunnable {

    private final BedrockCombatConfig config;

    public AttackSpeedListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @Override
    public void run() {
        for (Player player : GeyserRefinePlugin.getInstance().getServer().getOnlinePlayers()) {
            // 非基岩版或启用 Java 攻击 → 恢复原版冷却
            if (!FloodgateUtil.isBedrockPlayer(player) || PlayerSettings.getSetting(player, "java-attack")) {
                setAttackSpeed(player, 4.0);
                continue;
            }
            // 基岩版攻击模式 → 无冷却（使用配置的高速值）
            setAttackSpeed(player, config.genericAttackSpeed);
        }
    }

    private void setAttackSpeed(Player player, double speed) {
        var attr = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
        if (attr != null && attr.getBaseValue() != speed) {
            attr.setBaseValue(speed);
        }
    }
}