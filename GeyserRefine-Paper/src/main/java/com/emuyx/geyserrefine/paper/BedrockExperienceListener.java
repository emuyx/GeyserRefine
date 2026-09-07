package com.emuyx.geyserrefine.paper;

import org.bukkit.Location;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Random;

public class BedrockExperienceListener implements Listener {

    private static final int DELAY_TICKS = 20;
    private final Random random = new Random();

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (!FloodgateUtil.isBedrockPlayer(killer)) return;
        // 修正：使用 "java-attack"，且仅在 false 时生效（即基岩版攻击模式）
        if (PlayerSettings.getSetting(killer, "java-attack")) return;

        int exp = event.getDroppedExp();
        if (exp <= 0) return;

        event.setDroppedExp(0);

        Location deathLoc = event.getEntity().getLocation().clone();
        // 添加微小偏移，防止多个经验球在同一位置合并时出错
        deathLoc.add(
                (random.nextDouble() - 0.5) * 0.4,
                0,
                (random.nextDouble() - 0.5) * 0.4
        );

        GeyserRefinePlugin.getInstance().getServer().getScheduler()
                .runTaskLater(GeyserRefinePlugin.getInstance(), () -> {
                    ExperienceOrb orb = deathLoc.getWorld().spawn(deathLoc, ExperienceOrb.class);
                    orb.setExperience(exp);
                }, DELAY_TICKS);
    }
}