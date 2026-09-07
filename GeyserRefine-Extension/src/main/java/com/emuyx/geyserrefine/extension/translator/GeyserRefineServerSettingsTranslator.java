package com.emuyx.geyserrefine.extension.translator;

import com.emuyx.geyserrefine.extension.GeyserRefineExtension;
import com.emuyx.geyserrefine.extension.SettingsForm;
import org.cloudburstmc.protocol.bedrock.packet.ServerSettingsRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerSettingsResponsePacket;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;

import java.util.concurrent.TimeUnit;

/**
 * 替换 Geyser 默认的"服务器设置"表单为我们的综合设置表单（SettingsForm，CustomForm）。
 * <p>
 * 完全镜像 Geyser 原版 {@code BedrockServerSettingsRequestTranslator}：注册表单进缓存，
 * 序列化后通过 {@link ServerSettingsResponsePacket} 发回。基岩版此入口只支持 CustomForm。
 */
@Translator(packet = ServerSettingsRequestPacket.class)
public class GeyserRefineServerSettingsTranslator extends PacketTranslator<ServerSettingsRequestPacket> {

    private final FormDefinitions formDefinitions = FormDefinitions.instance();

    @Override
    public void translate(GeyserSession session, ServerSettingsRequestPacket packet) {
        if (!session.isLoggedIn()) {
            return;
        }

        // 关闭当前打开的表单（与 Geyser 原版一致）
        try {
            session.getFormCache().closeForms();
        } catch (Exception ignored) {
        }

        SettingsForm settingsForm = GeyserRefineExtension.getSettingsForm();
        if (settingsForm == null) {
            return;
        }

        // 构建我们的 CustomForm（失败则静默返回，不打扰玩家）
        CustomForm form;
        try {
            form = settingsForm.buildForm(session);
        } catch (Exception e) {
            session.getGeyser().getLogger().debug(
                    "[ServerSettings] Failed to build form: " + e.getMessage());
            return;
        }

        int formId = session.getFormCache().addForm(form);
        String jsonData;
        try {
            jsonData = formDefinitions.codecFor(form).jsonData(form);
        } catch (Exception e) {
            session.getGeyser().getLogger().debug(
                    "[ServerSettings] Failed to serialize form: " + e.getMessage());
            session.getFormCache().showForm(form);
            return;
        }

        // 延迟 1 秒发送，规避 MCPE-94012（Geyser 原版同样的处理）
        final String data = jsonData;
        session.scheduleInEventLoop(() -> {
            ServerSettingsResponsePacket resp = new ServerSettingsResponsePacket();
            resp.setFormId(formId);
            resp.setFormData(data);
            session.sendUpstreamPacket(resp);
        }, 1, TimeUnit.SECONDS);
    }
}
