package com.emuyx.geyserrefine.paper;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public class BedrockBlockRangeListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handlePlayer(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        handlePlayer(event.getPlayer());
    }

    private void handlePlayer(Player player) {
        if (!FloodgateUtil.isBedrockPlayer(player)) return;
        if (PlayerSettings.getSetting(player, "java-attack")) {
            // 恢复原版
            AttributeInstance attr = player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE);
            if (attr != null) attr.setBaseValue(4.5);
            return;
        }

        InputDeviceManager.updateInputMode(player);
        double range = InputDeviceManager.getBlockRange(player);
        AttributeInstance attribute = player.getAttribute(Attribute.PLAYER_BLOCK_INTERACTION_RANGE);
        if (attribute != null) {
            attribute.setBaseValue(range);
        }
    }
}