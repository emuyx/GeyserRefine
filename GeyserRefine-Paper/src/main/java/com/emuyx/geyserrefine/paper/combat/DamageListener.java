package com.emuyx.geyserrefine.paper.combat;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import com.emuyx.geyserrefine.paper.packet.AttackBlocker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DamageListener implements Listener {

    private final BedrockCombatConfig config;
    private final Map<UUID, Vector> pendingKnockback = new HashMap<>();
    private final Map<UUID, Double> lastDamageStore = new HashMap<>();
    private final Map<UUID, Long> lastSpearAttackTime = new HashMap<>();

    private static final Map<String, Long> SPEAR_COOLDOWN = new HashMap<>();
    static {
        SPEAR_COOLDOWN.put("wooden_spear", 650L);
        SPEAR_COOLDOWN.put("stone_spear", 750L);
        SPEAR_COOLDOWN.put("copper_spear", 850L);
        SPEAR_COOLDOWN.put("iron_spear", 950L);
        SPEAR_COOLDOWN.put("golden_spear", 950L);
        SPEAR_COOLDOWN.put("diamond_spear", 1050L);
        SPEAR_COOLDOWN.put("netherite_spear", 1150L);
    }

    public DamageListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!FloodgateUtil.isBedrockPlayer(attacker)) return;
        if (PlayerSettings.getSetting(attacker, "java-attack")) return;

        // 检测横扫攻击
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            handleBedrockSweepAttack(event, attacker);
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        String matKey = weapon.getType().getKey().getKey();

        // 矛冷却检查
        if (matKey.endsWith("_spear")) {
            Long cooldownMs = SPEAR_COOLDOWN.get(matKey);
            if (cooldownMs != null) {
                long now = System.currentTimeMillis();
                Long last = lastSpearAttackTime.get(attacker.getUniqueId());
                if (last != null && (now - last) < cooldownMs) {
                    event.setCancelled(true);
                    return;
                }
                lastSpearAttackTime.put(attacker.getUniqueId(), now);
            }
        }

        // 记录攻击（用于横扫粒子屏蔽）
        AttackBlocker.getInstance().recordAttack(attacker, attacker.getLocation());

        // 基岩版普通攻击没有横扫，如果检测到横扫则取消
        if (isSweepAttack(event)) {
            event.setCancelled(true);
            return;
        }

        if (!isWeapon(weapon.getType()) && !matKey.endsWith("_spear")) return;

        // ========== 基岩版伤害计算 ==========
        double weaponDamage = getWeaponAttackDamage(weapon);
        double rawDamage = 1.0 + weaponDamage;

        // 力量效果
        int strengthLevel = getEffectLevel(attacker, PotionEffectType.STRENGTH);
        for (int i = 0; i < strengthLevel; i++) {
            rawDamage = rawDamage * 1.3 + 1;
        }

        // 虚弱效果
        int weaknessLevel = getEffectLevel(attacker, PotionEffectType.WEAKNESS);
        for (int i = 0; i < weaknessLevel; i++) {
            rawDamage = rawDamage * 0.8 - 0.5;
            if (rawDamage < 0) rawDamage = 0;
        }

        // 附魔伤害
        double enchantDamage = 0;
        int sharpness = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) {
            enchantDamage += 1.25 * sharpness;
        }
        if (isUndead(event.getEntity())) {
            int smite = weapon.getEnchantmentLevel(Enchantment.SMITE);
            if (smite > 0) enchantDamage += 2.5 * smite;
        } else if (isArthropod(event.getEntity())) {
            int bane = weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
            if (bane > 0) enchantDamage += 2.5 * bane;
        }
        enchantDamage = Math.floor(enchantDamage);
        rawDamage += enchantDamage;

        // 重锤
        if (weapon.getType() == Material.MACE) {
            double fallHeight = attacker.getFallDistance();
            if (fallHeight > 1.5 && !attacker.isGliding()) {
                double extra = 0;
                if (fallHeight <= 3) {
                    extra = 4 * fallHeight;
                } else if (fallHeight <= 8) {
                    extra = 2 * fallHeight + 6;
                } else {
                    extra = fallHeight + 14;
                }
                int densityLevel = weapon.getEnchantmentLevel(Enchantment.DENSITY);
                if (densityLevel > 0) {
                    extra += 0.5 * densityLevel * fallHeight;
                }
                rawDamage += extra;
            }
        }

        // 暴击
        if (isCriticalHitBedrock(attacker)) {
            rawDamage *= config.criticalMultiplier;
        }

        // 减伤
        double finalDamage = rawDamage;
        double reducedDamage = rawDamage; // 减免后(PHDI前)伤害，用于下次受击免疫比较
        boolean comboDecayTriggered = false;
        if (event.getEntity() instanceof LivingEntity victim) {
            // 盔甲
            var armorAttr = victim.getAttribute(Attribute.GENERIC_ARMOR);
            var toughnessAttr = victim.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS);
            if (armorAttr != null) {
                double armor = armorAttr.getValue();
                double toughness = toughnessAttr != null ? toughnessAttr.getValue() : 0;
                double effectiveArmor = armor - finalDamage / (2 + toughness / 4);
                effectiveArmor = Math.max(armor / 5.0, Math.min(20, effectiveArmor));
                double reduction = finalDamage * effectiveArmor * 0.04;
                finalDamage -= reduction;
            }

            // 保护附魔
            int totalProtection = getTotalProtectionLevel(victim);
            if (totalProtection > 0) {
                double protectionFactor = 1 - Math.min(20, totalProtection) * 0.04;
                finalDamage *= protectionFactor;
            }

            // 抗性提升
            PotionEffect resistanceEffect = victim.getPotionEffect(PotionEffectType.RESISTANCE);
            if (resistanceEffect != null) {
                int level = resistanceEffect.getAmplifier() + 1;
                double reduction = finalDamage * 0.2 * level;
                finalDamage = Math.max(0, finalDamage - reduction);
            }

            // 连击递减：基岩攻击「纯 Java 玩家」或「使用 java 攻击模式的基岩玩家」才生效
            if (config.comboDecayEnabled && victim instanceof Player victimPlayer) {
                boolean victimUsesJavaCombat = !FloodgateUtil.isBedrockPlayer(victimPlayer)
                        || PlayerSettings.getSetting(victimPlayer, "java-attack");
                if (victimUsesJavaCombat) {
                    double multiplier = ComboDecayManager.apply(attacker, config);
                    finalDamage *= multiplier;
                    comboDecayTriggered = multiplier < 1.0;
                }
            }

            // 记录减免后的伤害（PHDI 之前），供下一次受击免疫比较
            reducedDamage = finalDamage;

            // PHDI
            int noDamageTicks = victim.getNoDamageTicks();
            int maxNoDamageTicks = victim.getMaximumNoDamageTicks();
            if (noDamageTicks > maxNoDamageTicks / 2) {
                double lastDamage = victim.getLastDamage();
                if (finalDamage <= lastDamage) {
                    event.setCancelled(true);
                    return;
                }
                finalDamage = finalDamage - lastDamage;
            }
        }

        // 设置伤害
        // 只清零手动重算的减伤项（盔甲/抗性/保护附魔），
        // 保留盾牌格挡(BLOCKING)、头盔(HARD_HAT)、吸收(ABSORPTION)
        for (EntityDamageEvent.DamageModifier mod : EntityDamageEvent.DamageModifier.values()) {
            boolean recomputed = mod == EntityDamageEvent.DamageModifier.ARMOR
                    || mod == EntityDamageEvent.DamageModifier.RESISTANCE
                    || mod == EntityDamageEvent.DamageModifier.MAGIC;
            if (recomputed && event.isApplicable(mod)) {
                event.setDamage(mod, 0);
            }
        }
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, finalDamage);

        if (event.getEntity() instanceof LivingEntity victim) {
            lastDamageStore.put(victim.getUniqueId(), reducedDamage);
        }

        if (event.getEntity() instanceof LivingEntity victimForKB) {
            if (comboDecayTriggered) {
                // 连击递减触发：无击退
                ComboDecayManager.markNoKnockback(victimForKB);
            } else {
                Vector kb = computeKnockback(attacker, victimForKB);
                pendingKnockback.put(victimForKB.getUniqueId(), kb);
            }
        }
    }

    private void handleBedrockSweepAttack(EntityDamageByEntityEvent event, Player attacker) {
        // 盔甲架或船取消横扫
        if (event.getEntity() instanceof ArmorStand || event.getEntity() instanceof Boat) {
            event.setCancelled(true);
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        int sweepLevel = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
        if (sweepLevel == 0) {
            event.setCancelled(true);
            return;
        }

        // 基岩版有效等级 = 等级-1
        int effectiveLevel = Math.max(0, sweepLevel - 1);

        // 计算横扫伤害
        double currentDamage = event.getDamage();
        double baseDamage = currentDamage / (1 + 0.5 * sweepLevel); // 还原基础伤害
        double bedrockSweepDamage = baseDamage * (1 + 0.5 * effectiveLevel);

        double finalDamage = bedrockSweepDamage;
        if (event.getEntity() instanceof LivingEntity victim) {
            // 减伤
            var armorAttr = victim.getAttribute(Attribute.GENERIC_ARMOR);
            var toughnessAttr = victim.getAttribute(Attribute.GENERIC_ARMOR_TOUGHNESS);
            if (armorAttr != null) {
                double armor = armorAttr.getValue();
                double toughness = toughnessAttr != null ? toughnessAttr.getValue() : 0;
                double effectiveArmor = armor - finalDamage / (2 + toughness / 4);
                effectiveArmor = Math.max(armor / 5.0, Math.min(20, effectiveArmor));
                double reduction = finalDamage * effectiveArmor * 0.04;
                finalDamage -= reduction;
            }

            int totalProtection = getTotalProtectionLevel(victim);
            if (totalProtection > 0) {
                double protectionFactor = 1 - Math.min(20, totalProtection) * 0.04;
                finalDamage *= protectionFactor;
            }

            PotionEffect resistanceEffect = victim.getPotionEffect(PotionEffectType.RESISTANCE);
            if (resistanceEffect != null) {
                int level = resistanceEffect.getAmplifier() + 1;
                double reduction = finalDamage * 0.2 * level;
                finalDamage = Math.max(0, finalDamage - reduction);
            }

            int noDamageTicks = victim.getNoDamageTicks();
            int maxNoDamageTicks = victim.getMaximumNoDamageTicks();
            if (noDamageTicks > maxNoDamageTicks / 2) {
                double lastDamage = victim.getLastDamage();
                if (finalDamage <= lastDamage) {
                    event.setCancelled(true);
                    return;
                }
                finalDamage = finalDamage - lastDamage;
            }
        }

        // 设置横扫伤害
        // 只清零手动重算的减伤项（盔甲/抗性/保护附魔），
        // 保留盾牌格挡(BLOCKING)、头盔(HARD_HAT)、吸收(ABSORPTION)
        for (EntityDamageEvent.DamageModifier mod : EntityDamageEvent.DamageModifier.values()) {
            boolean recomputed = mod == EntityDamageEvent.DamageModifier.ARMOR
                    || mod == EntityDamageEvent.DamageModifier.RESISTANCE
                    || mod == EntityDamageEvent.DamageModifier.MAGIC;
            if (recomputed && event.isApplicable(mod)) {
                event.setDamage(mod, 0);
            }
        }
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, finalDamage);
    }

    // ========== 事件监听器（辅助） ==========

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageMonitor(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!FloodgateUtil.isBedrockPlayer(attacker)) return;
        if (PlayerSettings.getSetting(attacker, "java-attack")) return;
        if (event.getEntity() instanceof LivingEntity victim) {
            Double reduced = lastDamageStore.remove(victim.getUniqueId());
            if (reduced != null) {
                victim.setLastDamage(reduced);
            }
        }
    }

    /**
     * 基岩版命中音效：每次近战命中播放 attack.strong。
     * 通过 Paper 播放 Java 音效，Geyser 翻译为基岩版 game.player.attack.strong。
     * 只对基岩版攻击模式玩家生效（java 攻击模式有服务端原版音效）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageHitSound(EntityDamageByEntityEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!FloodgateUtil.isBedrockPlayer(attacker)) return;
        if (PlayerSettings.getSetting(attacker, "java-attack")) return;
        attacker.playSound(attacker.getLocation(),
                Sound.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerVelocity(PlayerVelocityEvent event) {
        UUID id = event.getPlayer().getUniqueId();

        // 跨类型连击递减触发期间：只取消水平击退，保留纵向击退
        if (ComboDecayManager.consumeNoKnockback(id)) {
            Vector pre = event.getPlayer().getVelocity();
            event.setVelocity(new Vector(pre.getX(), event.getVelocity().getY(), pre.getZ()));
            return;
        }

        Vector kb = pendingKnockback.remove(id);
        if (kb != null) {
            event.setVelocity(kb);
        }
    }

    /**
     * 生物击退：把 Bedrock 风格击退应用到生物（玩家由 onPlayerVelocity 处理）。
     * 排除抗击退/无击退的实体（Boss、载具、盔甲架等），它们保持原版行为。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        if (event.getCause() != EntityKnockbackEvent.Cause.ENTITY_ATTACK) return;

        UUID id = event.getEntity().getUniqueId();

        // 连击递减触发期间（PvP）：只保留纵向击退，取消水平击退
        if (ComboDecayManager.consumeNoKnockback(id)) {
            Vector kb = event.getKnockback();
            event.setKnockback(new Vector(0, kb.getY(), 0));
            return;
        }

        // 抗击退实体：丢弃待应用的击退，保持原版
        if (isKnockbackResistant(event.getEntity())) {
            pendingKnockback.remove(id);
            return;
        }

        Vector pending = pendingKnockback.remove(id);
        if (pending != null) {
            event.setKnockback(pending);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ComboDecayManager.cleanup(event.getPlayer());
    }

    // ========== 辅助方法 ==========

    /**
     * 是否属于击退小或没有击退的实体（Boss、载具、盔甲架等）。
     * 这些实体不应用 Bedrock 风格击退，保持原版行为。
     */
    private boolean isKnockbackResistant(Entity entity) {
        return entity instanceof EnderDragon
                || entity instanceof Wither
                || entity instanceof Warden
                || entity instanceof IronGolem
                || entity instanceof EnderCrystal
                || entity instanceof Boat
                || entity instanceof Minecart
                || entity instanceof ArmorStand;
    }

    private int getEffectLevel(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private double getWeaponAttackDamage(ItemStack weapon) {
        Double configDamage = getConfiguredDamage(weapon.getType());
        if (configDamage != null) {
            return configDamage;
        }
        return 0.0;
    }

    private Double getConfiguredDamage(Material material) {
        String key = "old-tool-damage.damages." + material.getKey().toString();
        if (!config.toolDamageConfig.contains(key)) return null;
        double val = config.toolDamageConfig.getDouble(key, -1);
        return val < 0 ? null : val;
    }

    private boolean isUndead(Entity entity) {
        return entity instanceof Zombie || entity instanceof Skeleton ||
                entity instanceof WitherSkeleton || entity instanceof Wither ||
                entity instanceof Phantom || entity instanceof SkeletonHorse ||
                entity instanceof ZombieHorse || entity instanceof Zoglin;
    }

    private boolean isArthropod(Entity entity) {
        return entity instanceof Spider || entity instanceof CaveSpider ||
                entity instanceof Silverfish || entity instanceof Endermite;
    }

    private boolean isWeapon(Material mat) {
        return mat.name().endsWith("_SWORD") || mat.name().endsWith("_AXE")
                || mat.name().endsWith("_PICKAXE") || mat.name().endsWith("_SHOVEL")
                || mat.name().endsWith("_HOE") || mat == Material.TRIDENT || mat == Material.MACE
                || mat.getKey().getKey().endsWith("_spear");
    }

    private boolean isCriticalHitBedrock(Player player) {
        if (player.getFallDistance() <= 0.0F) return false;
        if (player.isOnGround()) return false;
        if (player.isInWater()) return false;
        if (player.isClimbing()) return false;
        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) return false;
        if (player.isInsideVehicle()) return false;
        return true;
    }

    private boolean isSweepAttack(EntityDamageByEntityEvent event) {
        return false; // 基岩版无横扫，由服务器生成的 ENTITY_SWEEP_ATTACK 事件单独处理
    }

    private int getTotalProtectionLevel(LivingEntity entity) {
        int total = 0;
        if (entity.getEquipment() != null) {
            for (ItemStack armor : entity.getEquipment().getArmorContents()) {
                if (armor != null && armor.containsEnchantment(Enchantment.PROTECTION)) {
                    total += armor.getEnchantmentLevel(Enchantment.PROTECTION);
                }
            }
        }
        return total;
    }

    private Vector computeKnockback(Player attacker, LivingEntity victim) {
        Location aLoc = attacker.getLocation();
        Location vLoc = victim.getLocation();
        double dx = aLoc.getX() - vLoc.getX();
        double dz = aLoc.getZ() - vLoc.getZ();
        double mag = Math.sqrt(dx * dx + dz * dz);
        if (mag < 0.001) mag = 0.001;

        Vector vel = victim.getVelocity();
        double x = vel.getX() / 2 - (dx / mag) * config.knockbackHorizontal;
        double y = vel.getY() / 2 + config.knockbackVertical;
        double z = vel.getZ() / 2 - (dz / mag) * config.knockbackHorizontal;

        ItemStack weapon = attacker.getEquipment() != null ? attacker.getInventory().getItemInMainHand() : null;
        int knockbackLevel = weapon != null ? weapon.getEnchantmentLevel(Enchantment.KNOCKBACK) : 0;
        if (attacker.isSprinting()) knockbackLevel++;

        if (knockbackLevel > 0) {
            double yaw = Math.toRadians(attacker.getLocation().getYaw());
            x += -Math.sin(yaw) * knockbackLevel * config.knockbackExtraHorizontal;
            z += Math.cos(yaw) * knockbackLevel * config.knockbackExtraHorizontal;
            y += config.knockbackExtraVertical;
        }
        if (y > config.knockbackVerticalLimit) y = config.knockbackVerticalLimit;

        return new Vector(x, y, z);
    }
}