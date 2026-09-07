package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.geyser.session.GeyserSession;

/**
 * 辅助性设置（MenuForm → 辅助性设置 打开）。
 * 含：夜视 / 显示坐标 / 显示游玩天数 / 减少挖掘粒子 / 高级提示框 / 自定义头颅 / 链接前提示。
 * 统一用 response.next() 顺序读取（跳过 label）。
 */
public class ToastSettingsForm {

    private static final String N = "§f";
    private static final String D = "§e";

    private final ToastSettingsStorage storage;

    public ToastSettingsForm(ToastSettingsStorage storage) {
        this.storage = storage;
    }

    public void open(GeyserSession session) {
        String xuid = session.xuid();

        boolean nightVision = storage.getNightVision(xuid);
        boolean showCoordinates = storage.getShowCoordinates(xuid);
        boolean showDaysPlayed = storage.getShowDaysPlayed(xuid);
        boolean reduceParticles = storage.getReduceMiningParticles(xuid);
        boolean advancedTooltips = storage.getAdvancedTooltips(xuid);
        boolean customSkulls = storage.getCustomSkulls(xuid);
        boolean promptOnLinks = storage.getPromptOnLinks(xuid);

        CustomForm form = CustomForm.builder()
                .title("§l§b辅助性设置")
                .toggle(N + "夜视" + D + "｜开启后获得持久夜视", nightVision)
                .toggle(N + "显示坐标" + D + "｜屏幕顶部显示坐标", showCoordinates)
                .toggle(N + "显示游玩天数" + D + "｜暂停菜单显示真实游玩天数", showDaysPlayed)
                .toggle(N + "减少挖掘粒子" + D + "｜关闭破碎粒子减少卡顿", reduceParticles)
                .toggle(N + "高级提示框" + D + "｜物品/方块完整信息", advancedTooltips)
                .toggle(N + "自定义头颅" + D + "｜显示基岩版自定义头颅", customSkulls)
                .toggle(N + "链接前提示" + D + "｜点链接前先确认", promptOnLinks)
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

                    // 应用
                    try {
                        session.getPreferencesCache().setPrefersShowCoordinates(newShowCoordinates);
                        session.getPreferencesCache().updateShowCoordinates();
                        session.setAdvancedTooltips(newAdvancedTooltips);
                        session.getPreferencesCache().setPrefersCustomSkulls(newCustomSkulls);
                    } catch (Exception ignored) {}

                    GeyserRefineExtension.setShowDaysPlayed(session, newShowDaysPlayed);
                    NightVisionManager.refresh(session, storage);

                    session.sendMessage("§a辅助设置已更新。");
                })
                .closedOrInvalidResultHandler(() -> {})
                .build();
        session.sendForm(form);
    }
}
