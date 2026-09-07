package com.emuyx.geyserrefine.paper;

import com.emuyx.geyserrefine.paper.cape.CapeLoader;
import com.emuyx.geyserrefine.paper.cape.MannequinCapeWorkaround;
import com.emuyx.geyserrefine.paper.combat.*;
import com.emuyx.geyserrefine.paper.fishing.FishingKnockbackListener;
import com.emuyx.geyserrefine.paper.fishing.FishingRodListener;
import com.emuyx.geyserrefine.paper.move.BedrockFailMoveListener;
import com.emuyx.geyserrefine.paper.network.TCPMessageHandler;
import com.emuyx.geyserrefine.paper.network.TCPServer;
import com.emuyx.geyserrefine.paper.network.TCPConfig;
import com.emuyx.geyserrefine.paper.network.TellToast;
import com.emuyx.geyserrefine.paper.packet.AttackBlocker;
import com.emuyx.geyserrefine.paper.packet.BedrockPacketListener;
import com.emuyx.geyserrefine.paper.projectile.ProjectileKnockbackListener;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * GeyserRefine-Paper 主插件。
 * <p>
 * 只对基岩版玩家生效：施加基岩风格战斗（无冷却 / 武器伤害 / 击退 / 护甲公式 /
 * 受击免疫 / 跨类型连击递减 / 命中音效），并处理粒子/音效过滤与输入设备距离。
 * <p>
 * 与 GeyserRefine-Extension 通过本机 TCP 配对（本类持有 TCPServer）。未配对时
 * 战斗仍可用，但 `/geyserrefine settings` 退回内建表单（见 BCTCommand）。
 */
public final class GeyserRefinePlugin extends JavaPlugin implements Listener {

    private static GeyserRefinePlugin instance;
    private BedrockCombatConfig config;
    private PacketEventsAPI<?> packetEvents;
    private TCPServer tcpServer;
    private MannequinCapeWorkaround capeWorkaround;

    @Override
    public void onLoad() {
        instance = this;
        packetEvents = SpigotPacketEventsBuilder.build(this);
        PacketEvents.setAPI(packetEvents);
        packetEvents.load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new BedrockCombatConfig(this);
        config.reload();

        new AttackBlocker();
        InputDeviceManager.init(config);
        Objects.requireNonNull(getCommand("geyserrefine")).setExecutor(new BCTCommand(this));
        Objects.requireNonNull(getCommand("geyserrefine")).setTabCompleter(new BCTCommand(this));

        packetEvents.init();
        PlayerSettings.init(config, getDataFolder());

        // 加载披风功能
        if (getConfig().getBoolean("cape-workaround.enabled", false)) {
            CapeLoader.init();
            capeWorkaround = new MannequinCapeWorkaround();
            getLogger().info("Cape workaround enabled.");
        } else {
            getLogger().info("Cape workaround disabled.");
        }

        // 启动 TCP
        TCPConfig tcpConfig = new TCPConfig(this);
        if (tcpConfig.enabled) {
            tcpServer = new TCPServer(tcpConfig.port, tcpConfig.logRetries);
            tcpServer.start();
            getLogger().info("TCP Server started.");
        } else {
            getLogger().info("TCP disabled.");
        }

        packetEvents.getEventManager().registerListener(new BedrockPacketListener());
        AttackSpeedListener attackSpeedListener = new AttackSpeedListener(config);
        attackSpeedListener.runTaskTimer(this, 0L, 1L);
        getServer().getPluginManager().registerEvents(new DamageListener(config), this);
        getServer().getPluginManager().registerEvents(new RegenListener(config), this);
        getServer().getPluginManager().registerEvents(new ProjectileKnockbackListener(config), this);
        getServer().getPluginManager().registerEvents(new FishingRodListener(config), this);
        getServer().getPluginManager().registerEvents(new FishingKnockbackListener(config), this);
        getServer().getPluginManager().registerEvents(new BedrockCombatSoundListener(config), this);
        getServer().getPluginManager().registerEvents(new BedrockFailMoveListener(config), this);
        getServer().getPluginManager().registerEvents(new BedrockSwingListener(config), this);
        getServer().getPluginManager().registerEvents(new BedrockBlockRangeListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerSettings(), this);
        //getServer().getPluginManager().registerEvents(new BedrockExperienceListener(), this);
        getServer().getPluginManager().registerEvents(new TellToast(), this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        PlayerSettings.saveAll();
        packetEvents.terminate();
        if (tcpServer != null) tcpServer.stop();
        if (capeWorkaround != null) capeWorkaround.disable();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (FloodgateUtil.isBedrockPlayer(player)) {
            TCPMessageHandler.broadcastSettings(player);
            // 玩家加入时检查是否有待应用的披风
            String capeId = TCPMessageHandler.getCapeIdByName(player.getName());
            if (capeId != null && capeWorkaround != null) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    capeWorkaround.refreshCapeForPlayer(player.getUniqueId());
                    getLogger().info("[Cape] Applied cape on join for " + player.getName());
                }, 10L);
            }
        }
    }

    public void reloadCapeWorkaround() {
        if (capeWorkaround != null) {
            capeWorkaround.disable();
            capeWorkaround = null;
        }
        CapeLoader.reloadMappings();
        capeWorkaround = new MannequinCapeWorkaround();
        getLogger().info("[Cape] Cape workaround reloaded.");
    }

    public static GeyserRefinePlugin getInstance() {
        return instance;
    }

    public BedrockCombatConfig getBedrockCombatConfig() {
        return config;
    }

    public TCPServer getTCPServer() {
        return tcpServer;
    }

    public MannequinCapeWorkaround getCapeWorkaround() {
        return capeWorkaround;
    }
}