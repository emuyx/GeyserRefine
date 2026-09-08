package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.i18n.Lang;
import com.emuyx.geyserrefine.extension.network.TCPClient;
import com.emuyx.geyserrefine.extension.network.TCPMessage;
import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.CooldownUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 综合设置表单（服务器设置入口用）。多语言，最前为语言选择与一次性"快速重连"；
 * 攻击区仅在与 Paper 配对时显示。读取统一用 response.next()。
 */
public class SettingsForm {

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

        // 当前语言：下拉默认按存储的选择显示（auto 则显示"自动"）
        String savedLang = storage.getLanguage(xuid);
        String lang = Lang.resolve(session, storage);
        int langDropdownDefault = Lang.langIndex(savedLang == null ? "auto" : savedLang);

        String preset = storage.getPreset(xuid);
        boolean javaAttack = storage.getJavaAttack(xuid);
        boolean touchSwing = storage.getTouchSwing(xuid);
        boolean muteSound = storage.getMuteNodamageSound(xuid);
        boolean optionalPack = storage.getOptionalPackEnabled(xuid);

        boolean nightVision = storage.getNightVision(xuid);
        boolean showCoordinates = storage.getShowCoordinates(xuid);
        boolean showDaysPlayed = storage.getShowDaysPlayed(xuid);
        boolean reduceParticles = storage.getReduceMiningParticles(xuid);
        boolean advancedTooltips = storage.getAdvancedTooltips(xuid);
        boolean customSkulls = storage.getCustomSkulls(xuid);
        boolean promptOnLinks = storage.getPromptOnLinks(xuid);

        CooldownUtils.CooldownType cooldown = session.getPreferencesCache().getCooldownPreference();
        List<String> cooldownOptions = List.of(
                Lang.tr(lang, "cooldown.crosshair"),
                Lang.tr(lang, "cooldown.hotbar"),
                Lang.tr(lang, "cooldown.disabled"));
        int cooldownIndex = switch (cooldown) {
            case HOTBAR -> 1;
            case DISABLED -> 2;
            default -> 0;
        };

        boolean oldJavaAttack = javaAttack, oldTouchSwing = touchSwing,
                oldMuteSound = muteSound, oldOptionalPack = optionalPack;
        String oldPreset = preset;

        CustomForm.Builder builder = CustomForm.builder()
                .title("§l§6" + Lang.tr(lang, "settings.title"))
                .dropdown(Lang.tr(lang, "settings.lang"),
                        List.of(Lang.langNames()), langDropdownDefault)
                .toggle(Lang.tr(lang, "quick.reconnect")
                        + "｜" + Lang.tr(lang, "quick.reconnect.desc"), false);

        if (paired) {
            builder.label("§6━ " + Lang.tr(lang, "sec.attack") + " ━")
                    .dropdown(Lang.tr(lang, "preset") + "（" + Lang.tr(lang, "preset.desc") + "）",
                            List.of(Lang.tr(lang, "preset.bedrock"),
                                    Lang.tr(lang, "preset.java"),
                                    Lang.tr(lang, "preset.custom")), presetIndex(preset))
                    .toggle(Lang.tr(lang, "opt.javaattack") + "｜" + Lang.tr(lang, "opt.javaattack.desc"), javaAttack)
                    .toggle(Lang.tr(lang, "opt.touchswing") + "｜" + Lang.tr(lang, "opt.touchswing.desc"), touchSwing)
                    .toggle(Lang.tr(lang, "opt.mutesound") + "｜" + Lang.tr(lang, "opt.mutesound.desc"), muteSound)
                    .toggle(Lang.tr(lang, "opt.optionalpack") + "｜" + Lang.tr(lang, "opt.optionalpack.desc"), optionalPack)
                    .dropdown(Lang.tr(lang, "cooldown.indicator"), cooldownOptions, cooldownIndex);
        } else {
            builder.label("§e" + Lang.tr(lang, "attack.unpaired"));
        }

        builder.label("§b━ " + Lang.tr(lang, "sec.aux") + " ━")
                .toggle(Lang.tr(lang, "opt.nightvision") + "｜" + Lang.tr(lang, "opt.nightvision.desc"), nightVision)
                .toggle(Lang.tr(lang, "opt.coords") + "｜" + Lang.tr(lang, "opt.coords.desc"), showCoordinates)
                .toggle(Lang.tr(lang, "opt.daysplayed") + "｜" + Lang.tr(lang, "opt.daysplayed.desc"), showDaysPlayed)
                .toggle(Lang.tr(lang, "opt.mining") + "｜" + Lang.tr(lang, "opt.mining.desc"), reduceParticles)
                .toggle(Lang.tr(lang, "opt.tooltips") + "｜" + Lang.tr(lang, "opt.tooltips.desc"), advancedTooltips)
                .toggle(Lang.tr(lang, "opt.skulls") + "｜" + Lang.tr(lang, "opt.skulls.desc"), customSkulls)
                .toggle(Lang.tr(lang, "opt.links") + "｜" + Lang.tr(lang, "opt.links.desc"), promptOnLinks);

        boolean finalPaired = paired;
        return builder.validResultHandler(response -> {
            // 语言下拉（下标）
            int langChoice = ((Number) response.next()).intValue();
            String newLang = Lang.LANG_CODES[langChoice];
            boolean quickReconnect = response.next();

            // 攻击（仅配对时有）
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

            // 辅助
            boolean newNightVision = response.next();
            boolean newShowCoordinates = response.next();
            boolean newShowDaysPlayed = response.next();
            boolean newReduceParticles = response.next();
            boolean newAdvancedTooltips = response.next();
            boolean newCustomSkulls = response.next();
            boolean newPromptOnLinks = response.next();

            // 语言可能已改变 → 用新语言发消息
            String msgLang = Lang.resolve(session, storage);
            storage.setLanguage(xuid, newLang);
            msgLang = Lang.resolve(session, storage);

            storage.setNightVision(xuid, newNightVision);
            storage.setShowCoordinates(xuid, newShowCoordinates);
            storage.setShowDaysPlayed(xuid, newShowDaysPlayed);
            storage.setSetting(xuid, "reduce-mining-particles", newReduceParticles);
            storage.setAdvancedTooltips(xuid, newAdvancedTooltips);
            storage.setCustomSkulls(xuid, newCustomSkulls);
            storage.setPromptOnLinks(xuid, newPromptOnLinks);

            try {
                session.getPreferencesCache().setPrefersShowCoordinates(newShowCoordinates);
                session.getPreferencesCache().updateShowCoordinates();
                session.setAdvancedTooltips(newAdvancedTooltips);
                session.getPreferencesCache().setPrefersCustomSkulls(newCustomSkulls);
            } catch (Exception ignored) {}
            GeyserRefineExtension.setShowDaysPlayed(session, newShowDaysPlayed);
            NightVisionManager.refresh(session, storage);

            if (finalPaired) {
                String selectedPreset = switch (presetIndex) {
                    case 1 -> ToastSettingsStorage.PRESET_JAVA;
                    case 2 -> ToastSettingsStorage.PRESET_CUSTOM;
                    default -> ToastSettingsStorage.PRESET_BEDROCK;
                };
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
                session.sendMessage("§e" + Lang.tr(msgLang, "msg.reconnecting"));
                session.transfer(session.joinAddress(), session.joinPort());
                return;
            }
            session.sendMessage("§a" + Lang.tr(msgLang, "msg.saved"));
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
