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

public class AttackSettingsForm {
    private final ToastSettingsStorage storage;

    public AttackSettingsForm(ToastSettingsStorage storage) {
        this.storage = storage;
    }

    public void open(GeyserSession session) {
        session.sendForm(buildForm(session));
    }

    /** 构建攻击设置表单（CustomForm，不发送）。由 open() 或服务器设置入口使用。 */
    public CustomForm buildForm(GeyserSession session) {
        String xuid = session.xuid();
        UUID playerUUID = session.javaUuid();

        String preset = storage.getPreset(xuid);
        boolean javaAttack = storage.getJavaAttack(xuid);
        boolean touchSwing = storage.getTouchSwing(xuid);
        boolean muteSound = storage.getMuteNodamageSound(xuid);
        boolean optionalPack = storage.getOptionalPackEnabled(xuid);

        // 攻击指示器位置
        CooldownUtils.CooldownType currentCooldown = session.getPreferencesCache().getCooldownPreference();
        List<String> cooldownOptions = List.of("准星", "动作栏", "禁用");
        int cooldownIndex = switch (currentCooldown) {
            case HOTBAR -> 1;
            case DISABLED -> 2;
            default -> 0;
        };

        return CustomForm.builder()
                .title("攻击设置")
                .dropdown("预设", List.of("基岩版", "Java版", "自定义"), getPresetIndex(preset))
                .toggle("蓄力攻击（Java攻击）", javaAttack)
                .toggle("触屏挥手反馈", touchSwing)
                .toggle("屏蔽击空音效", muteSound)
                .toggle("启用可选材质", optionalPack)
                .dropdown("攻击指示器位置", cooldownOptions, cooldownIndex)
                .validResultHandler(response -> {
                    int presetIndex = response.asDropdown(0);
                    String selectedPreset = switch (presetIndex) {
                        case 1 -> ToastSettingsStorage.PRESET_JAVA;
                        case 2 -> ToastSettingsStorage.PRESET_CUSTOM;
                        default -> ToastSettingsStorage.PRESET_BEDROCK;
                    };

                    boolean newJavaAttack = response.asToggle(1);
                    boolean newTouchSwing = response.asToggle(2);
                    boolean newMuteSound = response.asToggle(3);
                    boolean newOptionalPack = response.asToggle(4);
                    int cooldownIndexNew = response.asDropdown(5);

                    // ===== 攻击模式切换逻辑 =====
                    // 仅改了预设（详细开关未变）→ 应用预设规定的详细值
                    boolean detailsChanged = newJavaAttack != javaAttack
                            || newTouchSwing != touchSwing
                            || newMuteSound != muteSound
                            || newOptionalPack != optionalPack;
                    boolean presetChanged = !selectedPreset.equals(preset);

                    if (presetChanged && !detailsChanged
                            && !selectedPreset.equals(ToastSettingsStorage.PRESET_CUSTOM)) {
                        boolean presetJava = selectedPreset.equals(ToastSettingsStorage.PRESET_JAVA);
                        newJavaAttack = presetJava;
                        newTouchSwing = presetJava;
                        newMuteSound = presetJava;
                        newOptionalPack = presetJava;
                    }
                    // 依据最终详细值计算预设：均开=Java，均关=基岩，混合=自定义
                    boolean allOn = newJavaAttack && newTouchSwing && newMuteSound && newOptionalPack;
                    boolean allOff = !newJavaAttack && !newTouchSwing && !newMuteSound && !newOptionalPack;
                    String calculatedPreset = allOn ? ToastSettingsStorage.PRESET_JAVA :
                            allOff ? ToastSettingsStorage.PRESET_BEDROCK :
                                    ToastSettingsStorage.PRESET_CUSTOM;

                    // 存储开关值
                    storage.setJavaAttack(xuid, newJavaAttack);
                    storage.setTouchSwing(xuid, newTouchSwing);
                    storage.setMuteNodamageSound(xuid, newMuteSound);
                    storage.setOptionalPackEnabled(xuid, newOptionalPack);
                    storage.setPreset(xuid, calculatedPreset);

                    // 更新攻击指示器
                    CooldownUtils.CooldownType newCooldown = switch (cooldownIndexNew) {
                        case 1 -> CooldownUtils.CooldownType.HOTBAR;
                        case 2 -> CooldownUtils.CooldownType.DISABLED;
                        default -> CooldownUtils.CooldownType.CROSSHAIR;
                    };
                    session.getPreferencesCache().setCooldownPreference(newCooldown);

                    boolean optionalChanged = (newOptionalPack != optionalPack);
                    boolean javaAttackChanged = (newJavaAttack != javaAttack);

                    // 同步 javaAttack 到 Paper
                    if (javaAttackChanged) {
                        syncToPaper(playerUUID, session.javaUsername(), newJavaAttack, storage.getNightVision(xuid));
                    }

                    if (optionalChanged) {
                        openReconnectConfirmForm(session);
                    } else {
                        session.sendMessage("§a攻击设置已更新。");
                    }
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

    private void syncToPaper(UUID uuid, String playerName, boolean javaAttack, boolean nightVision) {
        TCPClient client = GeyserRefineExtension.getTCPClient();
        if (client == null) return;
        TCPMessage msg = new TCPMessage();
        msg.type = TCPMessage.Type.SYNC_SETTINGS;
        msg.playerUUID = uuid;
        msg.playerName = playerName;
        // 夜视已改为 extension 端伪造，不再同步到 Paper
        msg.settings = Map.of("java-attack", javaAttack);
        client.sendMessage(msg);
    }

    private void openReconnectConfirmForm(GeyserSession session) {
        ModalForm form = ModalForm.builder()
                .title("设置已更改")
                .content("你有一项设置需要重连应用，是否现在重连？")
                .button1("确定")
                .button2("取消")
                .validResultHandler((modalForm, response) -> {
                    if (response.clickedButtonId() == 0) {
                        session.sendMessage("§e正在重连...");
                        session.transfer(session.joinAddress(), session.joinPort());
                    } else {
                        session.sendMessage("§a设置已保存，但需要重连才能应用可选材质。");
                    }
                })
                .closedOrInvalidResultHandler(() -> {
                    session.sendMessage("§a设置已保存，但需要重连才能应用可选材质。");
                })
                .build();
        session.sendForm(form);
    }
}