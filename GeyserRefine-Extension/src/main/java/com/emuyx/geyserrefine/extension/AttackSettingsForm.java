package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.i18n.Lang;
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

/** 攻击设置（MenuForm → 攻击设置，仅在配对时可达）。多语言，按固定索引读取（无 label）。 */
public class AttackSettingsForm {

    private final ToastSettingsStorage storage;

    public AttackSettingsForm(ToastSettingsStorage storage) {
        this.storage = storage;
    }

    public void open(GeyserSession session) {
        session.sendForm(buildForm(session));
    }

    public CustomForm buildForm(GeyserSession session) {
        String xuid = session.xuid();
        UUID playerUUID = session.javaUuid();
        String lang = Lang.resolve(session, storage);

        String preset = storage.getPreset(xuid);
        boolean javaAttack = storage.getJavaAttack(xuid);
        boolean touchSwing = storage.getTouchSwing(xuid);
        boolean muteSound = storage.getMuteNodamageSound(xuid);
        boolean optionalPack = storage.getOptionalPackEnabled(xuid);

        CooldownUtils.CooldownType currentCooldown = session.getPreferencesCache().getCooldownPreference();
        List<String> cooldownOptions = List.of(
                Lang.tr(lang, "cooldown.crosshair"),
                Lang.tr(lang, "cooldown.hotbar"),
                Lang.tr(lang, "cooldown.disabled"));
        int cooldownIndex = switch (currentCooldown) {
            case HOTBAR -> 1;
            case DISABLED -> 2;
            default -> 0;
        };

        return CustomForm.builder()
                .title("§l§6" + Lang.tr(lang, "menu.attack"))
                .dropdown(Lang.tr(lang, "preset") + "（" + Lang.tr(lang, "preset.desc") + "）",
                        List.of(Lang.tr(lang, "preset.bedrock"),
                                Lang.tr(lang, "preset.java"),
                                Lang.tr(lang, "preset.custom")), presetIndex(preset))
                .toggle(item(lang, "opt.javaattack"), javaAttack)
                .toggle(item(lang, "opt.touchswing"), touchSwing)
                .toggle(item(lang, "opt.mutesound"), muteSound)
                .toggle(item(lang, "opt.optionalpack"), optionalPack)
                .dropdown(Lang.tr(lang, "cooldown.indicator"), cooldownOptions, cooldownIndex)
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

                    // 仅改预设（详细未变）→ 应用预设值
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
                    boolean allOn = newJavaAttack && newTouchSwing && newMuteSound && newOptionalPack;
                    boolean allOff = !newJavaAttack && !newTouchSwing && !newMuteSound && !newOptionalPack;
                    String calculatedPreset = allOn ? ToastSettingsStorage.PRESET_JAVA :
                            allOff ? ToastSettingsStorage.PRESET_BEDROCK :
                                    ToastSettingsStorage.PRESET_CUSTOM;

                    storage.setJavaAttack(xuid, newJavaAttack);
                    storage.setTouchSwing(xuid, newTouchSwing);
                    storage.setMuteNodamageSound(xuid, newMuteSound);
                    storage.setOptionalPackEnabled(xuid, newOptionalPack);
                    storage.setPreset(xuid, calculatedPreset);

                    try {
                        session.getPreferencesCache().setCooldownPreference(switch (cooldownIndexNew) {
                            case 1 -> CooldownUtils.CooldownType.HOTBAR;
                            case 2 -> CooldownUtils.CooldownType.DISABLED;
                            default -> CooldownUtils.CooldownType.CROSSHAIR;
                        });
                    } catch (Exception ignored) {}

                    boolean optionalChanged = (newOptionalPack != optionalPack);
                    boolean javaAttackChanged = (newJavaAttack != javaAttack);
                    if (javaAttackChanged) {
                        syncToPaper(playerUUID, session.javaUsername(), newJavaAttack);
                    }

                    String lang2 = Lang.resolve(session, storage);
                    if (optionalChanged) {
                        openReconnectConfirmForm(session, lang2);
                    } else {
                        session.sendMessage("§a" + Lang.tr(lang2, "msg.attackSaved"));
                    }
                })
                .closedOrInvalidResultHandler(() -> {})
                .build();
    }

    private static int presetIndex(String preset) {
        return switch (preset) {
            case ToastSettingsStorage.PRESET_JAVA -> 1;
            case ToastSettingsStorage.PRESET_CUSTOM -> 2;
            default -> 0;
        };
    }

    private static String item(String lang, String nameKey) {
        return Lang.tr(lang, nameKey) + "｜" + Lang.tr(lang, nameKey + ".desc");
    }

    private void syncToPaper(UUID uuid, String playerName, boolean javaAttack) {
        TCPClient client = GeyserRefineExtension.getTCPClient();
        if (client == null) return;
        TCPMessage msg = new TCPMessage();
        msg.type = TCPMessage.Type.SYNC_SETTINGS;
        msg.playerUUID = uuid;
        msg.playerName = playerName;
        msg.settings = Map.of("java-attack", javaAttack);
        client.sendMessage(msg);
    }

    private void openReconnectConfirmForm(GeyserSession session, String lang) {
        String yes = Lang.tr(lang, "cfm.yes");
        String no = Lang.tr(lang, "cfm.no");
        String needReconnect = Lang.tr(lang, "msg.packNeedReconnect");
        ModalForm form = ModalForm.builder()
                .title(Lang.tr(lang, "cfm.title"))
                .content(Lang.tr(lang, "cfm.content"))
                .button1(yes)
                .button2(no)
                .validResultHandler((modalForm, response) -> {
                    if (response.clickedButtonId() == 0) {
                        session.sendMessage("§e" + Lang.tr(lang, "msg.reconnecting"));
                        session.transfer(session.joinAddress(), session.joinPort());
                    } else {
                        session.sendMessage("§a" + needReconnect);
                    }
                })
                .closedOrInvalidResultHandler(() -> {
                    session.sendMessage("§a" + needReconnect);
                })
                .build();
        session.sendForm(form);
    }
}
