package com.emuyx.geyserrefine.paper.fishing;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

public class FishingKnockbackListener implements Listener {

    private final BedrockCombatConfig config;

    public FishingKnockbackListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(hook.getShooter() instanceof Player rodder)) return;
        if (!FloodgateUtil.isBedrockPlayer(rodder) || PlayerSettings.getSetting(rodder, "java-attack")) return;

        if (event.getHitEntity() == null) return;
        if (!(event.getHitEntity() instanceof LivingEntity victim)) return;
        if (victim.equals(rodder)) return;
        if (victim.getNoDamageTicks() > victim.getMaximumNoDamageTicks() / 2f) return;

        Location loc1 = hook.getLocation();
        Location loc2 = victim.getLocation();

        // 基岩版鱼竿伤害 0.001，击退正常
        victim.damage(config.fishingDamage, rodder);

        Vector vel = victim.getVelocity();
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        double mag = Math.sqrt(dx * dx + dz * dz);
        if (mag < 0.001) mag = 0.001;

        Vector newVel = new Vector(
                vel.getX() / 2 - (dx / mag) * config.fishingKnockbackHorizontal,
                vel.getY() / 2 + config.fishingKnockbackVertical,
                vel.getZ() / 2 - (dz / mag) * config.fishingKnockbackHorizontal
        );
        if (newVel.getY() > 0.4) newVel.setY(0.4);
        victim.setVelocity(newVel);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReelIn(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        Player player = event.getPlayer();
        if (!FloodgateUtil.isBedrockPlayer(player)) return;

        if (config.fishingCancelDrag) {
            event.getHook().remove();
            event.setCancelled(true);
        } else {
            Entity caught = event.getCaught();
            if (caught instanceof LivingEntity) {
                Vector direction = player.getLocation().toVector().subtract(caught.getLocation().toVector()).normalize();
                caught.setVelocity(caught.getVelocity().add(direction.multiply(config.fishingDragStrength)));
            }
        }
    }
}