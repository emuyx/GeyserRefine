package com.emuyx.geyserrefine.paper.projectile;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

public class ProjectileKnockbackListener implements Listener {

    private final BedrockCombatConfig config;

    public ProjectileKnockbackListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) return;
        if (!(projectile.getShooter() instanceof Player shooter)) return;
        if (!FloodgateUtil.isBedrockPlayer(shooter)) return;
        if (PlayerSettings.getSetting(shooter, "java-attack")) return;
        EntityType type = projectile.getType();
        if (type != EntityType.SNOWBALL && type != EntityType.EGG) return;

        // 基岩版投掷物伤害 0.001，附赠击退
        event.setDamage(config.projectileDamage);
        if (event.getEntity() instanceof LivingEntity victim) {
            Vector direction = victim.getLocation().toVector()
                    .subtract(shooter.getLocation().toVector()).normalize();
            Vector knockback = direction.multiply(config.projectileKnockbackStrength);
            victim.setVelocity(victim.getVelocity().add(knockback));
        }
    }
}