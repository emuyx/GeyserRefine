package com.emuyx.geyserrefine.paper.combat;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class BedrockCombatSoundListener implements Listener {

    private final BedrockCombatConfig config;

    public BedrockCombatSoundListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // 只处理基岩版玩家造成的攻击
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!FloodgateUtil.isBedrockPlayer(damager)) return;
        if (PlayerSettings.getSetting(damager, "java-attack")) return;

        // 致命一击：被攻击者死了（最终伤害 >= 剩余血量）
        if (config.soundOnKill && event.getEntity() instanceof org.bukkit.entity.LivingEntity victim) {
            double finalDamage = event.getFinalDamage();
            double victimHealth = victim.getHealth();
            if (finalDamage >= victimHealth) {
                playAttackStrong(damager);
                return; // 暴击就不用再放一次了
            }
        }

        // 暴击
        if (config.soundOnCritical && event.isCritical()) {
            playAttackStrong(damager);
        }
    }

    private void playAttackStrong(Player player) {
        player.playSound(
                player.getLocation(),
                "entity.player.attack.strong",
                SoundCategory.PLAYERS,
                5.0F,
                1.0F
        );
    }
}