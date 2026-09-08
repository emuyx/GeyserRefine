package com.emuyx.geyserrefine.extension.i18n;

import com.emuyx.geyserrefine.extension.storage.ToastSettingsStorage;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Locale;
import java.util.Map;

/**
 * 轻量多语言支持。
 * <p>
 * 支持：zh_CN（默认/回退）、zh_TW、en_US、ja_JP。
 * 玩家语言存储于 ToastSettingsStorage，取值可为 "auto"（默认，跟随客户端语言）或某语言代码。
 * 未知语言一律回退 zh_CN。
 */
public final class Lang {

    /** 支持的语言与语言名（用于下拉），顺序即下拉顺序。 */
    public static final String[] LANG_CODES = {"auto", "zh_CN", "zh_TW", "en_US", "ja_JP"};

    private Lang() {}

    /** 解析玩家当前语言代码。auto 时取客户端语言；未知回退 zh_CN。 */
    public static String resolve(GeyserSession session, ToastSettingsStorage storage) {
        String xuid = session.xuid();
        if (xuid != null && storage != null) {
            String saved = storage.getLanguage(xuid);
            if (saved != null && !saved.equals("auto")) {
                return normalize(saved);
            }
        }
        try {
            return normalize(session.languageCode());
        } catch (Exception e) {
            return "zh_CN";
        }
    }

    private static String normalize(String code) {
        if (code == null) return "zh_CN";
        String c = code.replace('-', '_').toLowerCase(Locale.ROOT);
        switch (c) {
            case "zh_tw": case "zh_hk": case "zh_mo": return "zh_TW";
            // 英式及各国英语统一用美式英语
            case "en_us": case "en_gb": case "en_ca": case "en_au": case "en_nz":
            case "en": return "en_US";
            case "ja_jp": case "ja": return "ja_JP";
            case "zh_cn": case "zh": return "zh_CN";
            default: return "zh_CN";
        }
    }

    /** 取文案；目标语言缺 key 时回退 zh_CN。 */
    public static String tr(String lang, String key) {
        String out = TEXTS.get(lang).get(key);
        if (out == null) {
            out = TEXTS.get("zh_CN").get(key);
        }
        return out != null ? out : key;
    }

    // ---------- 语言下拉项：各语言用其自身语言显示，不随界面语言变化 ----------
    public static String[] langNames() {
        return new String[]{"Auto", "简体中文", "繁體中文", "English", "日本語"};
    }

    /** 语言代码在 LANG_CODES 中的下标。 */
    public static int langIndex(String langCode) {
        for (int i = 0; i < LANG_CODES.length; i++) {
            if (LANG_CODES[i].equals(langCode)) return i;
        }
        return 0;
    }

    // ---------- 文案表 ----------
    private static final Map<String, Map<String, String>> TEXTS = Map.of(
            "zh_CN", Map.ofEntries(
                    Map.entry("settings.title", "基岩版综合设置"),
                    Map.entry("settings.lang", "界面语言"),
                    Map.entry("quick.reconnect", "快速重连"),
                    Map.entry("quick.reconnect.desc", "开启后关闭本设置即重连（一次性）"),
                    Map.entry("sec.attack", "攻击设置"),
                    Map.entry("attack.unpaired", "未连接服务端插件，攻击设置不可用"),
                    Map.entry("preset", "预设"),
                    Map.entry("preset.desc", "快速切换基岩/Java 风格"),
                    Map.entry("preset.bedrock", "基岩版"),
                    Map.entry("preset.java", "Java版"),
                    Map.entry("preset.custom", "自定义"),
                    Map.entry("opt.javaattack", "蓄力攻击（Java攻击）"),
                    Map.entry("opt.javaattack.desc", "开=Java节奏，关=无冷却连打"),
                    Map.entry("opt.touchswing", "触屏挥手反馈"),
                    Map.entry("opt.touchswing.desc", "触屏攻击时挥手动画"),
                    Map.entry("opt.mutesound", "屏蔽击空音效"),
                    Map.entry("opt.mutesound.desc", "关闭后挥空有空挥声"),
                    Map.entry("opt.optionalpack", "启用可选材质"),
                    Map.entry("opt.optionalpack.desc", "需重连生效"),
                    Map.entry("cooldown.indicator", "攻击指示器位置"),
                    Map.entry("cooldown.crosshair", "准星"),
                    Map.entry("cooldown.hotbar", "动作栏"),
                    Map.entry("cooldown.disabled", "禁用"),
                    Map.entry("sec.aux", "辅助设置"),
                    Map.entry("opt.nightvision", "夜视"),
                    Map.entry("opt.nightvision.desc", "开启后获得持久夜视"),
                    Map.entry("opt.coords", "显示坐标"),
                    Map.entry("opt.coords.desc", "屏幕顶部显示坐标"),
                    Map.entry("opt.daysplayed", "显示游玩天数"),
                    Map.entry("opt.daysplayed.desc", "暂停菜单显示真实游玩天数"),
                    Map.entry("opt.mining", "减少挖掘粒子"),
                    Map.entry("opt.mining.desc", "关闭破碎粒子减少卡顿"),
                    Map.entry("opt.tooltips", "高级提示框"),
                    Map.entry("opt.tooltips.desc", "物品/方块完整信息"),
                    Map.entry("opt.skulls", "自定义头颅"),
                    Map.entry("opt.skulls.desc", "显示基岩版自定义头颅"),
                    Map.entry("opt.links", "链接前提示"),
                    Map.entry("opt.links.desc", "点链接前先确认"),
                    Map.entry("msg.saved", "设置已保存。"),
                    Map.entry("msg.reconnecting", "正在快速重连..."),
                    Map.entry("menu.title", "基岩版菜单"),
                    Map.entry("menu.reconnect", "快速重连"),
                    Map.entry("menu.progress", "进度"),
                    Map.entry("menu.stats", "统计"),
                    Map.entry("menu.attack", "攻击设置"),
                    Map.entry("menu.aux", "辅助性设置"),
                    Map.entry("menu.interface", "界面元素设置"),
                    Map.entry("menu.freecam", "灵魂出窍"),
                    Map.entry("msg.auxSaved", "辅助设置已更新。"),
                    Map.entry("msg.attackSaved", "攻击设置已更新。"),
                    Map.entry("cfm.title", "设置已更改"),
                    Map.entry("cfm.content", "你有一项设置需要重连应用，是否现在重连？"),
                    Map.entry("cfm.yes", "确定"),
                    Map.entry("cfm.no", "取消"),
                    Map.entry("msg.packNeedReconnect", "设置已保存，但需要重连才能应用可选材质。"),
                    Map.entry("ui.paperdoll", "纸娃娃"),
                    Map.entry("ui.air", "气泡条"),
                    Map.entry("ui.armor", "护甲值"),
                    Map.entry("ui.crosshair", "准星"),
                    Map.entry("ui.effects", "状态效果"),
                    Map.entry("ui.progress", "进度条"),
                    Map.entry("ui.food", "食物条"),
                    Map.entry("ui.health", "生命值"),
                    Map.entry("ui.hotbar", "快捷栏"),
                    Map.entry("ui.itemtext", "物品文本"),
                    Map.entry("ui.vehiclehealth", "载具生命值"),
                    Map.entry("ui.tooltips", "工具提示"),
                    Map.entry("ui.touch", "触摸控制"),
                    Map.entry("msg.uiSaved", "界面设置已更新。")
            ),
            "zh_TW", Map.ofEntries(
                    Map.entry("settings.title", "基岩版綜合設定"),
                    Map.entry("settings.lang", "介面語言"),
                    Map.entry("quick.reconnect", "快速重連"),
                    Map.entry("quick.reconnect.desc", "開啟後關閉本設定即重連（一次性）"),
                    Map.entry("sec.attack", "攻擊設定"),
                    Map.entry("attack.unpaired", "未連接伺服器插件，攻擊設定不可用"),
                    Map.entry("preset", "預設"),
                    Map.entry("preset.desc", "快速切換基岩/Java 風格"),
                    Map.entry("preset.bedrock", "基岩版"),
                    Map.entry("preset.java", "Java版"),
                    Map.entry("preset.custom", "自訂"),
                    Map.entry("opt.javaattack", "蓄力攻擊（Java攻擊）"),
                    Map.entry("opt.javaattack.desc", "開=Java節奏，關=無冷卻連打"),
                    Map.entry("opt.touchswing", "觸屏揮手回饋"),
                    Map.entry("opt.touchswing.desc", "觸屏攻擊時揮手動畫"),
                    Map.entry("opt.mutesound", "屏蔽擊空音效"),
                    Map.entry("opt.mutesound.desc", "關閉後揮空有空揮聲"),
                    Map.entry("opt.optionalpack", "啟用可選材質"),
                    Map.entry("opt.optionalpack.desc", "需重連生效"),
                    Map.entry("cooldown.indicator", "攻擊指示器位置"),
                    Map.entry("cooldown.crosshair", "準星"),
                    Map.entry("cooldown.hotbar", "動作欄"),
                    Map.entry("cooldown.disabled", "停用"),
                    Map.entry("sec.aux", "輔助設定"),
                    Map.entry("opt.nightvision", "夜視"),
                    Map.entry("opt.nightvision.desc", "開啟後獲得持久夜視"),
                    Map.entry("opt.coords", "顯示座標"),
                    Map.entry("opt.coords.desc", "螢幕頂部顯示座標"),
                    Map.entry("opt.daysplayed", "顯示遊玩天數"),
                    Map.entry("opt.daysplayed.desc", "暫停選單顯示真實遊玩天數"),
                    Map.entry("opt.mining", "減少挖掘粒子"),
                    Map.entry("opt.mining.desc", "關閉破碎粒子減少卡頓"),
                    Map.entry("opt.tooltips", "進階提示框"),
                    Map.entry("opt.tooltips.desc", "物品/方塊完整資訊"),
                    Map.entry("opt.skulls", "自訂頭顱"),
                    Map.entry("opt.skulls.desc", "顯示基岩版自訂頭顱"),
                    Map.entry("opt.links", "連結前提示"),
                    Map.entry("opt.links.desc", "點連結前先確認"),
                    Map.entry("msg.saved", "設定已儲存。"),
                    Map.entry("msg.reconnecting", "正在快速重連..."),
                    Map.entry("menu.title", "基岩版選單"),
                    Map.entry("menu.reconnect", "快速重連"),
                    Map.entry("menu.progress", "進度"),
                    Map.entry("menu.stats", "統計"),
                    Map.entry("menu.attack", "攻擊設定"),
                    Map.entry("menu.aux", "輔助性設定"),
                    Map.entry("menu.interface", "介面元素設定"),
                    Map.entry("menu.freecam", "靈魂出竅"),
                    Map.entry("msg.auxSaved", "輔助設定已更新。"),
                    Map.entry("msg.attackSaved", "攻擊設定已更新。"),
                    Map.entry("cfm.title", "設定已變更"),
                    Map.entry("cfm.content", "有一項設定需要重連套用，是否現在重連？"),
                    Map.entry("cfm.yes", "確定"),
                    Map.entry("cfm.no", "取消"),
                    Map.entry("msg.packNeedReconnect", "設定已儲存，但需要重連才能套用可選材質。"),
                    Map.entry("ui.paperdoll", "紙娃娃"),
                    Map.entry("ui.air", "氣泡條"),
                    Map.entry("ui.armor", "護甲值"),
                    Map.entry("ui.crosshair", "準星"),
                    Map.entry("ui.effects", "狀態效果"),
                    Map.entry("ui.progress", "進度條"),
                    Map.entry("ui.food", "食物條"),
                    Map.entry("ui.health", "生命值"),
                    Map.entry("ui.hotbar", "快捷欄"),
                    Map.entry("ui.itemtext", "物品文字"),
                    Map.entry("ui.vehiclehealth", "載具生命值"),
                    Map.entry("ui.tooltips", "工具提示"),
                    Map.entry("ui.touch", "觸控操作"),
                    Map.entry("msg.uiSaved", "介面設定已更新。")
            ),
            "en_US", Map.ofEntries(
                    Map.entry("settings.title", "Bedrock Settings"),
                    Map.entry("settings.lang", "Language"),
                    Map.entry("quick.reconnect", "Quick Reconnect"),
                    Map.entry("quick.reconnect.desc", "Reconnect when closing this settings"),
                    Map.entry("sec.attack", "Attack"),
                    Map.entry("attack.unpaired", "Server addon not connected; attack settings unavailable"),
                    Map.entry("preset", "Preset"),
                    Map.entry("preset.desc", "Switch Bedrock/Java style"),
                    Map.entry("preset.bedrock", "Bedrock"),
                    Map.entry("preset.java", "Java"),
                    Map.entry("preset.custom", "Custom"),
                    Map.entry("opt.javaattack", "Charged Attack (Java)"),
                    Map.entry("opt.javaattack.desc", "On=Java timing, Off=no cooldown"),
                    Map.entry("opt.touchswing", "Touch Swing Feedback"),
                    Map.entry("opt.touchswing.desc", "Swing animation on touch attack"),
                    Map.entry("opt.mutesound", "Mute Miss Sound"),
                    Map.entry("opt.mutesound.desc", "Whiff sound on empty swing"),
                    Map.entry("opt.optionalpack", "Optional Pack"),
                    Map.entry("opt.optionalpack.desc", "Applies after reconnect"),
                    Map.entry("cooldown.indicator", "Attack Indicator"),
                    Map.entry("cooldown.crosshair", "Crosshair"),
                    Map.entry("cooldown.hotbar", "Hotbar"),
                    Map.entry("cooldown.disabled", "Disabled"),
                    Map.entry("sec.aux", "Assist"),
                    Map.entry("opt.nightvision", "Night Vision"),
                    Map.entry("opt.nightvision.desc", "Persistent night vision when on"),
                    Map.entry("opt.coords", "Show Coordinates"),
                    Map.entry("opt.coords.desc", "Show coordinates on screen"),
                    Map.entry("opt.daysplayed", "Show Days Played"),
                    Map.entry("opt.daysplayed.desc", "Real days played in pause menu"),
                    Map.entry("opt.mining", "Reduce Mining Particles"),
                    Map.entry("opt.mining.desc", "Hide break particles to reduce lag"),
                    Map.entry("opt.tooltips", "Advanced Tooltips"),
                    Map.entry("opt.tooltips.desc", "Full item/block info"),
                    Map.entry("opt.skulls", "Custom Skulls"),
                    Map.entry("opt.skulls.desc", "Show Bedrock custom skulls"),
                    Map.entry("opt.links", "Prompt Before Links"),
                    Map.entry("opt.links.desc", "Confirm before opening links"),
                    Map.entry("msg.saved", "Settings saved."),
                    Map.entry("msg.reconnecting", "Reconnecting..."),
                    Map.entry("menu.title", "Bedrock Menu"),
                    Map.entry("menu.reconnect", "Quick Reconnect"),
                    Map.entry("menu.progress", "Progress"),
                    Map.entry("menu.stats", "Stats"),
                    Map.entry("menu.attack", "Attack Settings"),
                    Map.entry("menu.aux", "Assist Settings"),
                    Map.entry("menu.interface", "UI Settings"),
                    Map.entry("menu.freecam", "Freecam"),
                    Map.entry("msg.auxSaved", "Assist settings updated."),
                    Map.entry("msg.attackSaved", "Attack settings updated."),
                    Map.entry("cfm.title", "Settings changed"),
                    Map.entry("cfm.content", "One setting requires a reconnect to apply. Reconnect now?"),
                    Map.entry("cfm.yes", "Yes"),
                    Map.entry("cfm.no", "Cancel"),
                    Map.entry("msg.packNeedReconnect", "Settings saved, but a reconnect is required to apply the optional pack."),
                    Map.entry("ui.paperdoll", "Paper Doll"),
                    Map.entry("ui.air", "Air Bubbles"),
                    Map.entry("ui.armor", "Armor"),
                    Map.entry("ui.crosshair", "Crosshair"),
                    Map.entry("ui.effects", "Effects"),
                    Map.entry("ui.progress", "Progress"),
                    Map.entry("ui.food", "Food"),
                    Map.entry("ui.health", "Health"),
                    Map.entry("ui.hotbar", "Hotbar"),
                    Map.entry("ui.itemtext", "Item Text"),
                    Map.entry("ui.vehiclehealth", "Vehicle Health"),
                    Map.entry("ui.tooltips", "Tooltips"),
                    Map.entry("ui.touch", "Touch Controls"),
                    Map.entry("msg.uiSaved", "UI settings updated.")
            ),
            "ja_JP", Map.ofEntries(
                    Map.entry("settings.title", "Bedrock 設定"),
                    Map.entry("settings.lang", "表示言語"),
                    Map.entry("quick.reconnect", "クイック再接続"),
                    Map.entry("quick.reconnect.desc", "設定を閉じると再接続します"),
                    Map.entry("sec.attack", "攻撃"),
                    Map.entry("attack.unpaired", "サーバープラグイン未接続のため攻撃設定は利用できません"),
                    Map.entry("preset", "プリセット"),
                    Map.entry("preset.desc", "Bedrock/Java スタイルを切替"),
                    Map.entry("preset.bedrock", "Bedrock"),
                    Map.entry("preset.java", "Java"),
                    Map.entry("preset.custom", "カスタム"),
                    Map.entry("opt.javaattack", "チャージ攻撃（Java）"),
                    Map.entry("opt.javaattack.desc", "ON=Java 間隔、OFF=クールダウン無し"),
                    Map.entry("opt.touchswing", "タッチスイング"),
                    Map.entry("opt.touchswing.desc", "タッチ攻撃時のスイング演出"),
                    Map.entry("opt.mutesound", "空振り音を消す"),
                    Map.entry("opt.mutesound.desc", "空振り時のヒュッという音"),
                    Map.entry("opt.optionalpack", "オプションパック"),
                    Map.entry("opt.optionalpack.desc", "再接続後に有効"),
                    Map.entry("cooldown.indicator", "攻撃インジケーター"),
                    Map.entry("cooldown.crosshair", "クロスヘア"),
                    Map.entry("cooldown.hotbar", "ホットバー"),
                    Map.entry("cooldown.disabled", "無効"),
                    Map.entry("sec.aux", "支援"),
                    Map.entry("opt.nightvision", "暗視"),
                    Map.entry("opt.nightvision.desc", "ON で常時暗視効果"),
                    Map.entry("opt.coords", "座標を表示"),
                    Map.entry("opt.coords.desc", "画面上部に座標を表示"),
                    Map.entry("opt.daysplayed", "プレイ日数を表示"),
                    Map.entry("opt.daysplayed.desc", "一時停止メニューに実際のプレイ日数"),
                    Map.entry("opt.mining", "採掘パーティクルを減らす"),
                    Map.entry("opt.mining.desc", "破壊パーティクルを非表示にし軽量化"),
                    Map.entry("opt.tooltips", "詳細ツールチップ"),
                    Map.entry("opt.tooltips.desc", "アイテム/ブロックの詳細情報"),
                    Map.entry("opt.skulls", "カスタムスカル"),
                    Map.entry("opt.skulls.desc", "Bedrock のカスタムスカルを表示"),
                    Map.entry("opt.links", "リンク前に確認"),
                    Map.entry("opt.links.desc", "リンクを開く前に確認する"),
                    Map.entry("msg.saved", "設定を保存しました。"),
                    Map.entry("msg.reconnecting", "再接続しています..."),
                    Map.entry("menu.title", "Bedrock メニュー"),
                    Map.entry("menu.reconnect", "クイック再接続"),
                    Map.entry("menu.progress", "進捗"),
                    Map.entry("menu.stats", "統計"),
                    Map.entry("menu.attack", "攻撃設定"),
                    Map.entry("menu.aux", "支援設定"),
                    Map.entry("menu.interface", "UI 設定"),
                    Map.entry("menu.freecam", "幽体離れ"),
                    Map.entry("msg.auxSaved", "支援設定を更新しました。"),
                    Map.entry("msg.attackSaved", "攻撃設定を更新しました。"),
                    Map.entry("cfm.title", "設定を変更しました"),
                    Map.entry("cfm.content", "適用には再接続が必要な設定があります。今すぐ再接続しますか？"),
                    Map.entry("cfm.yes", "はい"),
                    Map.entry("cfm.no", "キャンセル"),
                    Map.entry("msg.packNeedReconnect", "設定は保存しました。オプションパックの適用には再接続が必要です。"),
                    Map.entry("ui.paperdoll", "ペーパードール"),
                    Map.entry("ui.air", "空気バブル"),
                    Map.entry("ui.armor", "防具"),
                    Map.entry("ui.crosshair", "クロスヘア"),
                    Map.entry("ui.effects", "状態効果"),
                    Map.entry("ui.progress", "進行バー"),
                    Map.entry("ui.food", "満腹度"),
                    Map.entry("ui.health", "体力"),
                    Map.entry("ui.hotbar", "ホットバー"),
                    Map.entry("ui.itemtext", "アイテム名"),
                    Map.entry("ui.vehiclehealth", "乗り物の体力"),
                    Map.entry("ui.tooltips", "ツールチップ"),
                    Map.entry("ui.touch", "タッチ操作"),
                    Map.entry("msg.uiSaved", "UI 設定を更新しました。")
            )
    );
}
