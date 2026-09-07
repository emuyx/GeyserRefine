package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.freecam.FreecamHandler;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;

import java.util.ArrayList;
import java.util.List;

public class MenuForm {

    private static final int ACT_RECONNECT = 0;
    private static final int ACT_PROGRESS = 1;
    private static final int ACT_STATS = 2;
    private static final int ACT_ATTACK = 3;
    private static final int ACT_AUX = 4;
    private static final int ACT_INTERFACE = 5;
    private static final int ACT_FREECAM = 6;

    public static void open(GeyserSession session) {
        // 是否与 Paper 配对：攻击设置需要 Paper 才能生效，未配对时隐藏
        boolean paired = GeyserRefineExtension.isPairedToPaper();
        boolean freecamOn = GeyserRefineConfig.isFreecamEnabled();

        SimpleForm.Builder builder = SimpleForm.builder().title("§l§6基岩版菜单");
        List<Integer> actions = new ArrayList<>();

        // 功能按钮
        add(builder, actions, ACT_RECONNECT, "快速重连", "textures/ui/refresh_hover.png");
        add(builder, actions, ACT_PROGRESS, "进度", "textures/ui/achievements.png");
        add(builder, actions, ACT_STATS, "统计", "textures/ui/world_glyph_color_2x_black_outline.png");
        // 设置按钮（攻击设置仅在配对时显示）
        if (paired) {
            add(builder, actions, ACT_ATTACK, "攻击设置", "textures/ui/settings_glyph_color_2x.png");
        }
        add(builder, actions, ACT_AUX, "辅助性设置", "textures/ui/settings_glyph_color_2x.png");
        add(builder, actions, ACT_INTERFACE, "界面元素设置", "textures/ui/settings_glyph_color_2x.png");
        if (freecamOn) {
            add(builder, actions, ACT_FREECAM, "灵魂出窍", "textures/ui/icon_import.png");
        }

        SimpleForm form = builder
                .validResultHandler(response -> {
                    int clicked = response.clickedButtonId();
                    if (clicked < 0 || clicked >= actions.size()) return;
                    switch (actions.get(clicked)) {
                        case ACT_RECONNECT -> { // 快速重连：直接重连
                            session.sendMessage("§e正在快速重连...");
                            session.transfer(session.joinAddress(), session.joinPort());
                        }
                        case ACT_PROGRESS ->
                                session.getAdvancementsCache().buildAndShowMenuForm();
                        case ACT_STATS -> {
                            session.setWaitingForStatistics(true);
                            session.sendDownstreamGamePacket(
                                    new ServerboundClientCommandPacket(ClientCommand.REQUEST_STATS)
                            );
                        }
                        case ACT_ATTACK ->
                                GeyserRefineExtension.getAttackSettingsForm().open(session);
                        case ACT_AUX ->
                                GeyserRefineExtension.getToastSettingsForm().open(session);
                        case ACT_INTERFACE ->
                                GeyserRefineExtension.getInterfaceSettingsForm().open(session);
                        case ACT_FREECAM -> FreecamHandler.toggle(session);
                        default -> {}
                    }
                })
                .closedOrInvalidResultHandler(() -> {})
                .build();
        session.sendForm(form);
    }

    private static void add(SimpleForm.Builder builder, List<Integer> actions, int action, String text, String icon) {
        actions.add(action);
        builder.button(text, FormImage.Type.PATH, icon);
    }
}
