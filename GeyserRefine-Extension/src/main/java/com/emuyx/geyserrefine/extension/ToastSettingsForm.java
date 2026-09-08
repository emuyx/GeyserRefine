package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.i18n.Lang;
import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.geyser.session.GeyserSession;

/** 辅助性设置（MenuForm → 辅助性设置）。多语言，读取用 response.next()。 */
public class ToastSettingsForm {

    private final ToastSettingsStorage storage;

    public ToastSettingsForm(ToastSettingsStorage storage) {
        this.storage = storage;
    }

    public void open(GeyserSession session) {
        String xuid = session.xuid();
        String lang = Lang.resolve(session, storage);

        boolean nightVision = storage.getNightVision(xuid);
        boolean showCoordinates = storage.getShowCoordinates(xuid);
        boolean showDaysPlayed = storage.getShowDaysPlayed(xuid);
        boolean reduceParticles = storage.getReduceMiningParticles(xuid);
        boolean advancedTooltips = storage.getAdvancedTooltips(xuid);
        boolean customSkulls = storage.getCustomSkulls(xuid);
        boolean promptOnLinks = storage.getPromptOnLinks(xuid);

        String savedMsg = Lang.tr(lang, "msg.auxSaved");
        CustomForm form = CustomForm.builder()
                .title("§l§b" + Lang.tr(lang, "menu.aux"))
                .toggle(item(lang, "opt.nightvision"), nightVision)
                .toggle(item(lang, "opt.coords"), showCoordinates)
                .toggle(item(lang, "opt.daysplayed"), showDaysPlayed)
                .toggle(item(lang, "opt.mining"), reduceParticles)
                .toggle(item(lang, "opt.tooltips"), advancedTooltips)
                .toggle(item(lang, "opt.skulls"), customSkulls)
                .toggle(item(lang, "opt.links"), promptOnLinks)
                .validResultHandler(response -> {
                    boolean newNightVision = response.next();
                    boolean newShowCoordinates = response.next();
                    boolean newShowDaysPlayed = response.next();
                    boolean newReduceParticles = response.next();
                    boolean newAdvancedTooltips = response.next();
                    boolean newCustomSkulls = response.next();
                    boolean newPromptOnLinks = response.next();

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
                    session.sendMessage("§a" + savedMsg);
                })
                .closedOrInvalidResultHandler(() -> {})
                .build();
        session.sendForm(form);
    }

    /** 名称 + 说明。 */
    private static String item(String lang, String nameKey) {
        return Lang.tr(lang, nameKey) + "｜" + Lang.tr(lang, nameKey + ".desc");
    }
}
