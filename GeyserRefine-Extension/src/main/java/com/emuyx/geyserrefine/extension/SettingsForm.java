package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.network.TCPClient;
import com.emuyx.geyserrefine.extension.network.TCPMessage;
import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.CooldownUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 综合设置表单（服务器设置入口用）。
 * <p>
 * 上：攻击设置（仅在与 Paper 配对时显示，因需同步 java-attack）；下：辅助设置。
 * 最前为一次性"快速重连"开关。读取统一用 response.next()（跳过 label）。
 */
public class SettingsForm {

    private static final String H_ATK = "§l§6";
    private static final String H_AUX = "§l§b";
    private static final String N = "§f";
    private static final String D = "§e";

    private final ToastSettingsStorage storage;

    public SettingsForm(ToastSettingsStorage storage) {
        this.storage = storage;
    }

    public void open(GeyserSession session) {
        session.sendForm(buildForm(session));
    }

    public CustomForm buildForm(GeyserSession session) {
        String xuid = session.xuid();
        UUID playerUUID = session.javaUuid();
        boolean paired = GeyserRefineExtension.isPairedToPaper();

        // 攻击（仅配对时用）
        String preset = storage.getPreset(xuid);
        boolean javaAttack = storage.getJavaAttack(xuid);
        boolean touchSwing = storage.getTouchSwing(xuid);
        boolean muteSound = storage.getMuteNodamageSound(xuid);
        boolean optionalPack = storage.getOptionalPackEnabled(xuid);
        // 辅助
        boolean nightVision = storage.getNightVision(xuid);
        boolean showCoordinates = storage.getShowCoordinates(xuid);
        boolean showDaysPlayed = storage.getShowDaysPlayed(xuid);
        boolean reduceParticles = storage.getReduceMiningParticles(xuid);
        boolean advancedTooltips = storage.getAdvancedTooltips(xuid);
        boolean customSkulls = storage.getCustomSkulls(xuid);
        boolean promptOnLinks = storage.getPromptOnLinks(xuid);

        CooldownUtils.CooldownType cooldown = session.getPreferencesCache().getCooldownPreference();
        List<String> cooldownOptions = List.of("准星", "动作栏", "禁用");
        int cooldownIndex = switch (cooldown) {
            case HOTBAR -> 1;
            case DISABLED -> 2;
            default -> 0;
        };

        // 旧的攻击值，用于"仅改预设"判断
        boolean oldJavaAttack = javaAttack, oldTouchSwing = touchSwing,
                oldMuteSound = muteSound, oldOptionalPack = optionalPack;
        String oldPreset = preset;

        CustomForm.Builder builder = CustomForm.builder()
                .title("§l§6基岩版综合设置")
                .toggle(N + "快速重连" + D + "｜开启后关闭本设置即重连（一次性）", false);

        if (paired) {
            builder.label(H_ATK + "━ 攻击设置 ━")
                    .dropdown(N + "预设" + D + "（快速切换基岩/Java 风格）",
                            List.of("基岩版", "Java版", "自定义"), getPresetIndex(preset))
                    .toggle(N + "蓄力攻击（Java攻击）" + D + "｜开=Java节奏，关=无冷却连打", javaAttack)
                    .toggle(N + "触屏挥手反馈" + D + "｜触屏攻击时挥手动画", touchSwing)
                    .toggle(N + "屏蔽击空音效" + D + "｜关闭后挥空有空挥声", muteSound)
                    .toggle(N + "启用可选材质" + D + "｜需重连生效", optionalPack)
                    .dropdown(N + "攻击指示器位置", cooldownOptions, cooldownIndex);
        } else {
            builder.label("§e（未连接 Paper 插件，攻击设置已隐藏）");
        }

        builder.label(H_AUX + "━ 辅助设置 ━")
                .toggle(N + "夜视" + D + "｜开启后获得持久夜视", nightVision)
                .toggle(N + "显示坐标" + D + "｜屏幕顶部显示坐标", showCoordinates)
                .toggle(N + "显示游玩天数" + D + "｜暂停菜单显示真实游玩天数", showDaysPlayed)
                .toggle(N + "减少挖掘粒子" + D + "｜关闭破碎粒子减少卡顿", reduceParticles)
                .toggle(N + "高级提示框" + D + "｜物品/方块完整信息", advancedTooltips)
                .toggle(N + "自定义头颅" + D + "｜显示基岩版自定义头颅", customSkulls)
                .toggle(N + "链接前提示" + D + "｜点链接前先确认", promptOnLinks);

        boolean finalPaired = paired;
        return builder.validResultHandler(response -> {
            // 一次性快速重连
            boolean quickReconnect = response.next();

            // ---- 攻击（仅配对时有这些元素）----
            int presetIndex = 0, cooldownIndexNew = 0;
            boolean newJavaAttack = false, newTouchSwing = false,
                    newMuteSound = false, newOptionalPack = false;
            if (finalPaired) {
                presetIndex = ((Number) response.next()).intValue();
                newJavaAttack = response.next();
                newTouchSwing = response.next();
                newMuteSound = response.next();
                newOptionalPack = response.next();
                cooldownIndexNew = ((Number) response.next()).intValue();
            }

            // ---- 辅助 ----
            boolean newNightVision = response.next();
            boolean newShowCoordinates = response.next();
            boolean newShowDaysPlayed = response.next();
            boolean newReduceParticles = response.next();
            boolean newAdvancedTooltips = response.next();
            boolean newCustomSkulls = response.next();
            boolean newPromptOnLinks = response.next();

            // 存储辅助
            storage.setNightVision(xuid, newNightVision);
            storage.setShowCoordinates(xuid, newShowCoordinates);
            storage.setShowDaysPlayed(xuid, newShowDaysPlayed);
            storage.setSetting(xuid, "reduce-mining-particles", newReduceParticles);
            storage.setAdvancedTooltips(xuid, newAdvancedTooltips);
            storage.setCustomSkulls(xuid, newCustomSkulls);
            storage.setPromptOnLinks(xuid, newPromptOnLinks);

            // 应用辅助
            try {
                session.getPreferencesCache().setPrefersShowCoordinates(newShowCoordinates);
                session.getPreferencesCache().updateShowCoordinates();
                session.setAdvancedTooltips(newAdvancedTooltips);
                session.getPreferencesCache().setPrefersCustomSkulls(newCustomSkulls);
            } catch (Exception ignored) {}
            GeyserRefineExtension.setShowDaysPlayed(session, newShowDaysPlayed);
            NightVisionManager.refresh(session, storage);

            // 攻击：仅配对时保存并同步
            if (finalPaired) {
                String selectedPreset = switch (presetIndex) {
                    case 1 -> ToastSettingsStorage.PRESET_JAVA;
                    case 2 -> ToastSettingsStorage.PRESET_CUSTOM;
                    default -> ToastSettingsStorage.PRESET_BEDROCK;
                };
                // 仅改预设（详细未变）→ 应用预设值
                boolean detailsChanged = newJavaAttack != oldJavaAttack
                        || newTouchSwing != oldTouchSwing
                        || newMuteSound != oldMuteSound
                        || newOptionalPack != oldOptionalPack;
                boolean presetChanged = !selectedPreset.equals(oldPreset);
                if (presetChanged && !detailsChanged
                        && !selectedPreset.equals(ToastSettingsStorage.PRESET_CUSTOM)) {
                    boolean presetJava = selectedPreset.equals(ToastSettingsStorage.PRESET_JAVA);
                    newJavaAttack = presetJava;
                    newTouchSwing = presetJava;
                    newMuteSound = presetJava;
                    newOptionalPack = presetJava;
                }
                boolean allOn = newJavaAttack && newTouchSwing && newMuteSound && newOptionalPack;
                boolean allOff = !newJavaAttack && !newTouchSwing && !newMuteSound && !newOptionalPack;
                String finalPreset = allOn ? ToastSettingsStorage.PRESET_JAVA :
                        allOff ? ToastSettingsStorage.PRESET_BEDROCK :
                                ToastSettingsStorage.PRESET_CUSTOM;

                storage.setPreset(xuid, finalPreset);
                storage.setJavaAttack(xuid, newJavaAttack);
                storage.setTouchSwing(xuid, newTouchSwing);
                storage.setMuteNodamageSound(xuid, newMuteSound);
                storage.setOptionalPackEnabled(xuid, newOptionalPack);

                try {
                    session.getPreferencesCache().setCooldownPreference(switch (cooldownIndexNew) {
                        case 1 -> CooldownUtils.CooldownType.HOTBAR;
                        case 2 -> CooldownUtils.CooldownType.DISABLED;
                        default -> CooldownUtils.CooldownType.CROSSHAIR;
                    });
                } catch (Exception ignored) {}

                if (newJavaAttack != oldJavaAttack) {
                    syncJavaAttackToPaper(playerUUID, session.javaUsername(), newJavaAttack);
                }
            }

            if (quickReconnect) {
                session.sendMessage("§e正在快速重连...");
                session.transfer(session.joinAddress(), session.joinPort());
                return;
            }
            session.sendMessage("§a综合设置已保存。");
        })
        .closedOrInvalidResultHandler(() -> {})
        .build();
    }

    private int getPresetIndex(String preset) {
        return switch (preset) {
            case ToastSettingsStorage.PRESET_JAVA -> 1;
            case ToastSettingsStorage.PRESET_CUSTOM -> 2;
            default -> 0;
        };
    }

    private void syncJavaAttackToPaper(UUID uuid, String playerName, boolean javaAttack) {
        TCPClient client = GeyserRefineExtension.getTCPClient();
        if (client == null) return;
        TCPMessage msg = new TCPMessage();
        msg.type = TCPMessage.Type.SYNC_SETTINGS;
        msg.playerUUID = uuid;
        msg.playerName = playerName;
        msg.settings = Map.of("java-attack", javaAttack);
        client.sendMessage(msg);
    }
}
