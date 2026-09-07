package com.emuyx.geyserrefine.extension;

import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.geyser.api.bedrock.camera.GuiElement;
import org.geysermc.geyser.session.GeyserSession;

public class InterfaceSettingsForm {
    private final ToastSettingsStorage storage;

    public InterfaceSettingsForm(ToastSettingsStorage storage) {
        this.storage = storage;
    }

    public void open(GeyserSession session) {
        String xuid = session.xuid();

        // HUD 元素
        boolean showPaperDoll = !session.camera().isHudElementHidden(GuiElement.PAPER_DOLL);
        boolean showAirBubbles = !session.camera().isHudElementHidden(GuiElement.AIR_BUBBLES_BAR);
        boolean showArmor = !session.camera().isHudElementHidden(GuiElement.ARMOR);
        boolean showCrosshair = !session.camera().isHudElementHidden(GuiElement.CROSSHAIR);
        boolean showEffects = !session.camera().isHudElementHidden(GuiElement.EFFECTS_BAR);
        boolean showProgress = !session.camera().isHudElementHidden(GuiElement.PROGRESS_BAR);
        boolean showFood = !session.camera().isHudElementHidden(GuiElement.FOOD_BAR);
        boolean showHealth = !session.camera().isHudElementHidden(GuiElement.HEALTH);
        boolean showHotbar = !session.camera().isHudElementHidden(GuiElement.HOTBAR);
        boolean showItemText = !session.camera().isHudElementHidden(GuiElement.ITEM_TEXT_POPUP);
        boolean showVehicleHealth = !session.camera().isHudElementHidden(GuiElement.VEHICLE_HEALTH);
        boolean showToolTips = !session.camera().isHudElementHidden(GuiElement.TOOL_TIPS);
        boolean showTouchControls = !session.camera().isHudElementHidden(GuiElement.TOUCH_CONTROLS);

        CustomForm form = CustomForm.builder()
                .title("界面元素设置")
                .toggle("纸娃娃", showPaperDoll)
                .toggle("气泡条", showAirBubbles)
                .toggle("护甲值", showArmor)
                .toggle("准星", showCrosshair)
                .toggle("状态效果", showEffects)
                .toggle("进度条", showProgress)
                .toggle("食物条", showFood)
                .toggle("生命值", showHealth)
                .toggle("快捷栏", showHotbar)
                .toggle("物品文本", showItemText)
                .toggle("载具生命值", showVehicleHealth)
                .toggle("工具提示", showToolTips)
                .toggle("触摸控制", showTouchControls)
                .validResultHandler(response -> {
                    boolean newPaperDoll = response.next();
                    boolean newAirBubbles = response.next();
                    boolean newArmor = response.next();
                    boolean newCrosshair = response.next();
                    boolean newEffects = response.next();
                    boolean newProgress = response.next();
                    boolean newFood = response.next();
                    boolean newHealth = response.next();
                    boolean newHotbar = response.next();
                    boolean newItemText = response.next();
                    boolean newVehicleHealth = response.next();
                    boolean newToolTips = response.next();
                    boolean newTouchControls = response.next();

                    // 应用 HUD 元素
                    setHudElement(session, GuiElement.PAPER_DOLL, newPaperDoll);
                    setHudElement(session, GuiElement.AIR_BUBBLES_BAR, newAirBubbles);
                    setHudElement(session, GuiElement.ARMOR, newArmor);
                    setHudElement(session, GuiElement.CROSSHAIR, newCrosshair);
                    setHudElement(session, GuiElement.EFFECTS_BAR, newEffects);
                    setHudElement(session, GuiElement.PROGRESS_BAR, newProgress);
                    setHudElement(session, GuiElement.FOOD_BAR, newFood);
                    setHudElement(session, GuiElement.HEALTH, newHealth);
                    setHudElement(session, GuiElement.HOTBAR, newHotbar);
                    setHudElement(session, GuiElement.ITEM_TEXT_POPUP, newItemText);
                    setHudElement(session, GuiElement.VEHICLE_HEALTH, newVehicleHealth);
                    setHudElement(session, GuiElement.TOOL_TIPS, newToolTips);
                    setHudElement(session, GuiElement.TOUCH_CONTROLS, newTouchControls);

                    session.sendMessage("§a界面元素设置已更新。");
                })
                .closedOrInvalidResultHandler(() -> {})
                .build();
        session.sendForm(form);
    }

    private void setHudElement(GeyserSession session, GuiElement element, boolean show) {
        if (show) {
            session.camera().resetElement(element);
        } else {
            session.camera().hideElement(element);
        }
    }
}