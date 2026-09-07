package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.freecam.FreecamHandler;
import com.emuyx.geyserrefine.extension.handler.CustomBlockBreakHandler;
import com.emuyx.geyserrefine.extension.loader.OptionalPackLoader;
import com.emuyx.geyserrefine.extension.network.MessageHandler;
import com.emuyx.geyserrefine.extension.network.TCPClient;
import com.emuyx.geyserrefine.extension.network.TCPConfig;
import com.emuyx.geyserrefine.extension.network.TCPMessage;
import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import com.emuyx.geyserrefine.extension.translator.GeyserRefineBedrockPlayerAuthInputTranslator;
import com.emuyx.geyserrefine.extension.translator.GeyserRefineServerSettingsTranslator;
import com.emuyx.geyserrefine.extension.translator.GeyserRefineSetTimeTranslator;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerSettingsRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.cloudburstmc.protocol.bedrock.packet.ToastRequestPacket;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.bedrock.SessionSkinApplyEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetTimePacket;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class GeyserRefineExtension implements Extension, EventRegistrar {
    private static TCPClient tcpClient;
    private static ToastSettingsStorage toastStorage;
    private static ToastSettingsForm toastSettingsForm;
    private static AttackSettingsForm attackSettingsForm;
    private static InterfaceSettingsForm interfaceSettingsForm;
    private static SettingsForm settingsForm;
    private static OptionalPackLoader optionalPackLoader;
    private static final ConcurrentHashMap<UUID, Boolean> toastSent = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> uuidToXuid = new ConcurrentHashMap<>();
    private static ExtensionLogger staticLogger;

    // 全局缓存：XUID -> Cape 数据（用于替换其他玩家的披风）
    private static final ConcurrentHashMap<String, Cape> xuidCapeCache = new ConcurrentHashMap<>();

    // ---------- 静态日志访问器 ----------
    public static ExtensionLogger getLogger() {
        return staticLogger;
    }

    @Subscribe
    public void onEnable(GeyserPostInitializeEvent event) {
        staticLogger = this.logger();

        GeyserRefineConfig.load(this);
        ToastConfig.load(this);

        Registries.BEDROCK_PACKET_TRANSLATORS.register(
                PlayerAuthInputPacket.class,
                new GeyserRefineBedrockPlayerAuthInputTranslator()
        );
        // 替换 Geyser 默认"服务器设置"为我们的攻击设置表单（仅 CustomForm 可在此入口显示）
        Registries.BEDROCK_PACKET_TRANSLATORS.register(
                ServerSettingsRequestPacket.class,
                new GeyserRefineServerSettingsTranslator()
        );
        // 双击背包打开菜单功能已移除，不再注册 BedrockInteractInjector

        // 注册下行包翻译器，用于修改其他玩家的披风显示
        Registries.BEDROCK_PACKET_TRANSLATORS.register(
                PlayerListPacket.class,
                new PlayerListTranslator()
        );
        Registries.BEDROCK_PACKET_TRANSLATORS.register(
                PlayerSkinPacket.class,
                new PlayerSkinTranslator()
        );

        // 修复游玩天数每 8 天重置：包裹 vanilla 的 SetTime 翻译器
        PacketTranslator<?> timeOriginal = Registries.JAVA_PACKET_TRANSLATORS.get(ClientboundSetTimePacket.class);
        if (timeOriginal != null) {
            @SuppressWarnings("unchecked")
            PacketTranslator<ClientboundSetTimePacket> timeCast =
                    (PacketTranslator<ClientboundSetTimePacket>) timeOriginal;
            Registries.JAVA_PACKET_TRANSLATORS.register(
                    ClientboundSetTimePacket.class,
                    new GeyserRefineSetTimeTranslator(timeCast));
        } else {
            logger().warning("Could not find the vanilla SetTime translator.");
        }

        toastStorage = new ToastSettingsStorage(this);
        toastSettingsForm = new ToastSettingsForm(toastStorage);
        attackSettingsForm = new AttackSettingsForm(toastStorage);
        interfaceSettingsForm = new InterfaceSettingsForm(toastStorage);
        settingsForm = new SettingsForm(toastStorage);
        GeyserRefineBedrockPlayerAuthInputTranslator.setStorage(toastStorage);
        MessageHandler.init(toastStorage, staticLogger);

        optionalPackLoader = new OptionalPackLoader(this.dataFolder().resolve("packs"));

        TCPConfig config = new TCPConfig(this);
        if (config.enabled) {
            tcpClient = new TCPClient("127.0.0.1", config.port, config.logRetries);
            tcpClient.startWithRetry(this);
        } else {
            logger().info("TCP disabled, extension will run without Paper communication.");
        }

        GeyserImpl.getInstance().eventBus().register(this, this);
        logger().info("GeyserRefineExtension enabled.");
    }

    @Subscribe
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        if (event.connection() instanceof GeyserSession session) {
            toastSent.remove(session.javaUuid());
            uuidToXuid.remove(session.javaUuid());
            FreecamHandler.cleanup(session);
            NightVisionManager.cleanup(session);
        }
    }

    @Subscribe
    public void onShutdown(GeyserShutdownEvent event) {
        if (tcpClient != null) tcpClient.disconnect();
        toastSent.clear();
        uuidToXuid.clear();
        xuidCapeCache.clear();
        FreecamHandler.cleanupAll();
    }

    @Subscribe
    public void onSessionJoin(SessionJoinEvent event) {
        GeyserConnection connection = event.connection();
        if (!(connection instanceof GeyserSession session)) return;

        replaceBlockBreakHandler(session);
        String xuid = session.xuid();
        UUID javaUuid = session.javaUuid();
        String playerName = session.javaUsername();
        if (xuid == null || javaUuid == null) {
            logger().warning("Skipping onSessionJoin — name=" + playerName + ", xuid=" + xuid + ", javaUuid=" + javaUuid);
            return;
        }
        uuidToXuid.put(javaUuid, xuid);

        // 应用持久化显示设置：游玩天数 / 显示坐标 / 夜视
        applySessionDisplaySettings(session, xuid);

        // 缓存该玩家的披风数据
        var clientData = session.getClientData();
        if (clientData != null) {
            byte[] capeBytes = clientData.getCapeData();
            String capeId = clientData.getCapeId();
            if (capeBytes != null && capeBytes.length > 0 && capeId != null && !capeId.isEmpty()) {
                Cape cape = new Cape(capeId, capeId, capeBytes);
                xuidCapeCache.put(xuid, cape);
                logger().debug("Cached cape for xuid " + xuid + " id=" + capeId + " size=" + capeBytes.length);
            }
            // 无披风时不再 put null — ConcurrentHashMap 不允许 null 值
        }

        if (tcpClient != null) {
            TCPMessage xuidMsg = new TCPMessage();
            xuidMsg.type = TCPMessage.Type.XUID_UPDATE;
            xuidMsg.playerUUID = javaUuid;
            xuidMsg.playerName = playerName;
            xuidMsg.xuid = xuid;
            tcpClient.sendMessage(xuidMsg);

            String capeId = clientData != null ? clientData.getCapeId() : null;
            if (capeId != null && !capeId.isEmpty()) {
                TCPMessage capeMsg = new TCPMessage();
                capeMsg.type = TCPMessage.Type.CAPE_UPDATE;
                capeMsg.playerUUID = javaUuid;
                capeMsg.playerName = playerName;
                capeMsg.capeId = capeId;
                tcpClient.sendMessage(capeMsg);
            }
        }

        // Toast 逻辑（由 toast.yml 全局配置，不再逐玩家）
        UUID sessionId = javaUuid;
        if (toastSent.putIfAbsent(sessionId, true) != null) {
            return;
        }
        if (!ToastConfig.isEnabled()) {
            return;
        }
        String toastTitle = ToastConfig.getTitle();
        String toastContent = ToastConfig.getContent();
        session.scheduleInEventLoop(() -> {
            if (!toastSent.containsKey(sessionId)) return;
            ToastRequestPacket packet = new ToastRequestPacket();
            packet.setTitle(toastTitle);
            packet.setContent(toastContent);
            session.sendUpstreamPacket(packet);
            logger().info("Sent Toast to " + playerName);
        }, 2000, TimeUnit.MILLISECONDS);
    }

    @Subscribe
    public void onResourcePackLoad(SessionLoadResourcePacksEvent event) {
        GeyserConnection connection = event.connection();
        if (!(connection instanceof GeyserSession session)) return;
        String xuid = session.xuid();
        if (xuid == null) return;
        boolean enabled = toastStorage.getOptionalPackEnabled(xuid);
        if (enabled) {
            for (ResourcePack pack : optionalPackLoader.getPacks().values()) {
                boolean alreadyRegistered = event.resourcePacks().stream()
                        .anyMatch(rp -> rp.manifest().header().uuid().equals(pack.manifest().header().uuid()));
                if (!alreadyRegistered) {
                    event.register(pack);
                }
            }
            logger().debug("Registered optional packs for " + session.javaUsername());
        }
    }

    // ---------- 下行包翻译器（静态内部类） ----------

    @Translator(packet = PlayerListPacket.class)
    public static class PlayerListTranslator extends PacketTranslator<PlayerListPacket> {
        @Override
        public void translate(GeyserSession session, PlayerListPacket packet) {
            String myXuid = session.xuid();

            for (PlayerListPacket.Entry entry : packet.getEntries()) {
                UUID targetUuid = entry.getUuid();
                String targetXuid = uuidToXuid.get(targetUuid);
                if (targetXuid == null || targetXuid.equals(myXuid)) {
                    continue;
                }

                Cape cachedCape = xuidCapeCache.get(targetXuid);
                if (cachedCape != null && !cachedCape.failed()) {
                    SerializedSkin oldSkin = entry.getSkin();
                    SerializedSkin newSkin = SerializedSkin.builder()
                            .skinId(oldSkin.getSkinId())
                            .skinResourcePatch(oldSkin.getSkinResourcePatch())
                            .skinData(oldSkin.getSkinData())
                            .capeData(ImageData.of(cachedCape.capeData()))
                            .geometryData(oldSkin.getGeometryData())
                            .premium(oldSkin.isPremium())
                            .capeId(cachedCape.capeId())
                            .fullSkinId(oldSkin.getFullSkinId())
                            .geometryDataEngineVersion(oldSkin.getGeometryDataEngineVersion())
                            .overridingPlayerAppearance(oldSkin.isOverridingPlayerAppearance())
                            .build();
                    entry.setSkin(newSkin);
                    if (staticLogger != null) {
                        staticLogger.debug("Modified PlayerListPacket for " + targetUuid + " capeId=" + cachedCape.capeId());
                    }
                }
            }
        }
    }

    @Translator(packet = PlayerSkinPacket.class)
    public static class PlayerSkinTranslator extends PacketTranslator<PlayerSkinPacket> {
        @Override
        public void translate(GeyserSession session, PlayerSkinPacket packet) {
            UUID targetUuid = packet.getUuid();
            String myXuid = session.xuid();
            String targetXuid = uuidToXuid.get(targetUuid);
            if (targetXuid == null || targetXuid.equals(myXuid)) {
                return;
            }

            Cape cachedCape = xuidCapeCache.get(targetXuid);
            if (cachedCape != null && !cachedCape.failed()) {
                SerializedSkin oldSkin = packet.getSkin();
                SerializedSkin newSkin = SerializedSkin.builder()
                        .skinId(oldSkin.getSkinId())
                        .skinResourcePatch(oldSkin.getSkinResourcePatch())
                        .skinData(oldSkin.getSkinData())
                        .capeData(ImageData.of(cachedCape.capeData()))
                        .geometryData(oldSkin.getGeometryData())
                        .premium(oldSkin.isPremium())
                        .capeId(cachedCape.capeId())
                        .fullSkinId(oldSkin.getFullSkinId())
                        .geometryDataEngineVersion(oldSkin.getGeometryDataEngineVersion())
                        .overridingPlayerAppearance(oldSkin.isOverridingPlayerAppearance())
                        .build();
                packet.setSkin(newSkin);
                if (staticLogger != null) {
                    staticLogger.debug("Modified PlayerSkinPacket for " + targetUuid + " capeId=" + cachedCape.capeId());
                }
            }
        }
    }

    /**
     * 应用加入时的持久化显示设置：显示游玩天数（默认开）、显示坐标（默认开）、夜视。
     */
    private void applySessionDisplaySettings(GeyserSession session, String xuid) {
        // 显示游玩天数（原版基岩行为，per-player 持久，默认开）
        boolean showDays = toastStorage.getShowDaysPlayed(xuid);
        setShowDaysPlayed(session, showDays);

        // 显示坐标（持久化，默认开）——覆盖 Geyser 的一次性默认
        boolean showCoords = toastStorage.getShowCoordinates(xuid);
        try {
            session.getPreferencesCache().setPrefersShowCoordinates(showCoords);
            session.getPreferencesCache().updateShowCoordinates();
        } catch (Exception e) {
            logger().debug("Failed to apply coordinates for " + session.javaUsername() + ": " + e.getMessage());
        }

        // 夜视：extension 端伪造，定时保活
        scheduleNightVisionLoop(session);
    }

    /** 基岩 1.19.30+：showDaysPlayed 游戏规则让暂停菜单显示真实"游玩天数"。 */
    public static void setShowDaysPlayed(GeyserSession session, boolean show) {
        try {
            GameRulesChangedPacket packet = new GameRulesChangedPacket();
            packet.getGameRules().add(new GameRuleData<>("showDaysPlayed", show));
            session.sendUpstreamPacket(packet);
        } catch (Exception e) {
            // 忽略
        }
    }

    /** 每 30 秒刷新一次夜视伪造，使持久夜视持续生效。 */
    private void scheduleNightVisionLoop(GeyserSession session) {
        session.scheduleInEventLoop(new Runnable() {
            @Override
            public void run() {
                if (session.isClosed()) return;
                NightVisionManager.refresh(session, toastStorage);
                session.scheduleInEventLoop(this, 30, TimeUnit.SECONDS);
            }
        }, 3, TimeUnit.SECONDS);
    }

    private void replaceBlockBreakHandler(GeyserSession session) {
        try {
            Field field = GeyserSession.class.getDeclaredField("blockBreakHandler");
            field.setAccessible(true);
            Object current = field.get(session);
            if (current instanceof CustomBlockBreakHandler) {
                return;
            }
            CustomBlockBreakHandler customHandler = new CustomBlockBreakHandler(session, toastStorage);
            field.set(session, customHandler);
            logger().debug("Replaced BlockBreakHandler for " + session.javaUsername());
        } catch (Exception e) {
            logger().warning("Failed to replace BlockBreakHandler: " + e.getMessage());
        }
    }

    @Subscribe
    public void onSkinApply(SessionSkinApplyEvent event) {
        // 不再需要此事件，但保留以防后续扩展
    }

    public static ToastSettingsForm getToastSettingsForm() {
        return toastSettingsForm;
    }

    public static AttackSettingsForm getAttackSettingsForm() {
        return attackSettingsForm;
    }

    public static InterfaceSettingsForm getInterfaceSettingsForm() {
        return interfaceSettingsForm;
    }

    public static SettingsForm getSettingsForm() {
        return settingsForm;
    }

    public static TCPClient getTCPClient() {
        return tcpClient;
    }

    /** 是否与 GeyserRefine-Paper 配对（TCP 已连接）。配对才提供完整功能。 */
    public static boolean isPairedToPaper() {
        return tcpClient != null && tcpClient.isConnected();
    }

    public static ToastSettingsStorage getToastStorage() {
        return toastStorage;
    }

    public static String getXuidFromUUID(UUID uuid) {
        return uuidToXuid.get(uuid);
    }
}