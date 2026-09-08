package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.i18n.Lang;
import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.geyser.api.bedrock.camera.GuiElement;
import org.geysermc.geyser.session.GeyserSession;

/** 界面元素设置。多语言，读取用 response.next()。 */
public class InterfaceSettingsForm {

    private final ToastSettingsStorage storage;

    public InterfaceSettingsForm(ToastSettingsStorage storage) {
        this.storage = storage;
    }

    private static final GuiElement[] ELEMENTS = {
            GuiElement.PAPER_DOLL, GuiElement.AIR_BUBBLES_BAR, GuiElement.ARMOR,
            GuiElement.CROSSHAIR, GuiElement.EFFECTS_BAR, GuiElement.PROGRESS_BAR,
            GuiElement.FOOD_BAR, GuiElement.HEALTH, GuiElement.HOTBAR,
            GuiElement.ITEM_TEXT_POPUP, GuiElement.VEHICLE_HEALTH, GuiElement.TOOL_TIPS,
            GuiElement.TOUCH_CONTROLS
    };

    private static final String[] KEYS = {
            "ui.paperdoll", "ui.air", "ui.armor", "ui.crosshair", "ui.effects",
            "ui.progress", "ui.food", "ui.health", "ui.hotbar", "ui.itemtext",
            "ui.vehiclehealth", "ui.tooltips", "ui.touch"
    };

    public void open(GeyserSession session) {
        String lang = Lang.resolve(session, storage);

        CustomForm.Builder builder = CustomForm.builder()
                .title("§l§b" + Lang.tr(lang, "menu.interface"));

        boolean[] show = new boolean[ELEMENTS.length];
        for (int i = 0; i < ELEMENTS.length; i++) {
            show[i] = !session.camera().isHudElementHidden(ELEMENTS[i]);
            builder.toggle(Lang.tr(lang, KEYS[i]), show[i]);
        }

        String savedMsg = Lang.tr(lang, "msg.uiSaved");
        CustomForm form = builder
                .validResultHandler(response -> {
                    for (int i = 0; i < ELEMENTS.length; i++) {
                        boolean v = response.next();
                        if (v) {
                            session.camera().resetElement(ELEMENTS[i]);
                        } else {
                            session.camera().hideElement(ELEMENTS[i]);
                        }
                    }
                    session.sendMessage("§a" + savedMsg);
                })
                .closedOrInvalidResultHandler(() -> {})
                .build();
        session.sendForm(form);
    }
}
