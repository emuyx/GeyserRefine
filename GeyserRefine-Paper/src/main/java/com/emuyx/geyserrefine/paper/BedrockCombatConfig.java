package com.emuyx.geyserrefine.paper;

import com.emuyx.geyserrefine.paper.network.TCPConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class BedrockCombatConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public TCPConfig tcpConfig;

    // Attack cooldown
    public double genericAttackSpeed;
    public Set<String> weaponBlacklist;

    // Tool damage
    public FileConfiguration toolDamageConfig;

    // Critical hits
    public double criticalMultiplier;
    public boolean allowSprintingCrit;

    // Regen
    public int regenIntervalTicks;
    public int regenAmount;
    public float regenExhaustion;

    // Knockback
    public double knockbackHorizontal;
    public double knockbackVertical;
    public double knockbackVerticalLimit;
    public double knockbackExtraHorizontal;
    public double knockbackExtraVertical;

    // Projectile knockback
    public double projectileDamage;
    public double projectileKnockbackStrength;

    // Fishing rod
    public double fishingVelocityMultiplier;
    public double fishingGravityExtra;
    public double fishingDamage;
    public double fishingKnockbackHorizontal;
    public double fishingKnockbackVertical;
    public boolean fishingCancelDrag;
    public double fishingThrowHeightOffset;
    public double fishingGravityMultiplier;
    public double fishingDragStrength;

    // Packets (particles / sounds)
    public boolean removeAttackIndicator;
    public boolean removeSweepParticle;
    public boolean removeSweepSound;

    // 战斗音效
    public boolean soundOnKill;
    public boolean soundOnCritical;
    public boolean swingSounds;

    // 移动保护
    public boolean blockFailMove;

    public double blockRangeKeyboardMouse;
    public double blockRangeController;
    public double blockRangeTouch;
    public double blockRangeUnknown;

    // 玩家默认个性化设置
    public boolean defaultJavaAttack;

    // 跨类型 PvP 连击递减
    public boolean comboDecayEnabled;
    public int comboDecayWindowTicks;
    public double comboDecayPerHit;
    public double comboDecayMinMultiplier;

    public BedrockCombatConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();

        tcpConfig = new TCPConfig(plugin);

        genericAttackSpeed = config.getDouble("attack-cooldown.generic-attack-speed", 1024.0);
        weaponBlacklist = new HashSet<>(config.getStringList("attack-cooldown.weapon-blacklist"));

        toolDamageConfig = config.getConfigurationSection("old-tool-damage.damages") == null
                ? null
                : plugin.getConfig();

        criticalMultiplier = config.getDouble("critical-hits.multiplier", 1.5);
        allowSprintingCrit = config.getBoolean("critical-hits.allow-sprinting", true);

        regenIntervalTicks = config.getInt("player-regen.interval-ticks", 80);
        regenAmount = config.getInt("player-regen.amount", 1);
        regenExhaustion = (float) config.getDouble("player-regen.exhaustion", 3.0);

        knockbackHorizontal = config.getDouble("knockback.horizontal", 0.4);
        knockbackVertical = config.getDouble("knockback.vertical", 0.4);
        knockbackVerticalLimit = config.getDouble("knockback.vertical-limit", 0.4);
        knockbackExtraHorizontal = config.getDouble("knockback.extra-horizontal", 0.5);
        knockbackExtraVertical = config.getDouble("knockback.extra-vertical", 0.1);

        projectileDamage = config.getDouble("projectile-knockback.damage", 0.0);
        projectileKnockbackStrength = config.getDouble("projectile-knockback.knockback-strength", 0.4);

        fishingVelocityMultiplier = config.getDouble("fishing-rod.velocity-multiplier", 1.5);
        fishingGravityExtra = config.getDouble("fishing-rod.gravity-extra", -0.02);
        fishingDamage = config.getDouble("fishing-rod.knockback-damage", 0.0);
        fishingKnockbackHorizontal = config.getDouble("fishing-rod.knockback-horizontal", 0.4);
        fishingKnockbackVertical = config.getDouble("fishing-rod.knockback-vertical", 0.4);
        fishingCancelDrag = config.getBoolean("fishing-rod.cancel-drag", true);
        fishingThrowHeightOffset = config.getDouble("fishing-rod.throw-height-offset", -0.5);
        fishingGravityMultiplier = config.getDouble("fishing-rod.gravity-multiplier", 0.8);
        fishingDragStrength = config.getDouble("fishing-rod.drag-strength", 0.8);

        removeAttackIndicator = config.getBoolean("particles.remove-attack-indicator", true);
        removeSweepParticle = config.getBoolean("particles.remove-sweep", true);
        removeSweepSound = config.getBoolean("sounds.remove-sweep-sound", true);
        soundOnKill = config.getBoolean("combat-sounds.on-kill", true);
        soundOnCritical = config.getBoolean("combat-sounds.on-critical", true);
        swingSounds = config.getBoolean("combat-sounds.swing-sounds", true);
        blockFailMove = config.getBoolean("movement.block-fail-move", true);

        blockRangeKeyboardMouse = config.getDouble("block-interaction-range.keyboard_mouse", 5.6);
        blockRangeController = config.getDouble("block-interaction-range.controller", 5.7);
        blockRangeTouch = config.getDouble("block-interaction-range.touch", 6.7);
        blockRangeUnknown = config.getDouble("block-interaction-range.unknown", 6.7);

        // 确保 player-defaults 节存在并读取（若不存在则写入默认值）
        ConfigurationSection defaults = config.getConfigurationSection("player-defaults");
        if (defaults == null) {
            defaults = config.createSection("player-defaults");
        }
        defaultJavaAttack = getOrSetBoolean(defaults, "java-attack", false);

        comboDecayEnabled = config.getBoolean("combo-decay.enabled", true);
        comboDecayWindowTicks = config.getInt("combo-decay.window-ticks", 15);
        comboDecayPerHit = config.getDouble("combo-decay.decay-per-hit", 0.2);
        comboDecayMinMultiplier = config.getDouble("combo-decay.min-multiplier", 0.4);
    }

    private boolean getOrSetBoolean(ConfigurationSection section, String key, boolean defaultValue) {
        if (!section.contains(key)) {
            section.set(key, defaultValue);
        }
        return section.getBoolean(key, defaultValue);
    }
}