package com.emuyx.geyserrefine.paper.combat;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.GeyserRefinePlugin;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BedrockSwingListener implements Listener {

    private final BedrockCombatConfig config;
    private final Map<UUID, Long> lastSpearSoundTick = new HashMap<>();
    private static final int SPEAR_COOLDOWN_TICKS = 18;

    public BedrockSwingListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (!FloodgateUtil.isBedrockPlayer(player)) return;
        if (!config.swingSounds) return;
        if (PlayerSettings.getSetting(player, "java-attack")) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        String matKey = weapon.getType().getKey().toString();

        // 矛类：直接播放戳刺音效（带冷却）
        if (isSpear(matKey)) {
            playSpearSound(player, matKey);
        }
    }

    private boolean isSpear(String matKey) {
        return matKey.startsWith("minecraft:") && matKey.endsWith("_spear");
    }

    private int getSpearCooldown(String matKey) {
        return switch (matKey) {
            case "minecraft:wooden_spear" -> 13; // 0.65s
            case "minecraft:stone_spear" -> 15; // 0.75s
            case "minecraft:copper_spear" -> 17; // 0.85s
            case "minecraft:iron_spear", "minecraft:golden_spear" -> 19; // 0.95s
            case "minecraft:diamond_spear" -> 21; // 1.05s
            case "minecraft:netherite_spear" -> 23; // 1.15s
            default -> 18; // 默认 0.9s
        };
    }

    private void playSpearSound(Player player, String matKey) {
        int cooldownTicks = getSpearCooldown(matKey);
        long currentTick = GeyserRefinePlugin.getInstance().getServer().getCurrentTick();
        Long lastTick = lastSpearSoundTick.get(player.getUniqueId());
        if (lastTick != null && currentTick - lastTick < cooldownTicks) return;
        lastSpearSoundTick.put(player.getUniqueId(), currentTick);
        String sound = matKey.equals("minecraft:wooden_spear")
                ? "item.spear_wood.attack"
                : "item.spear.attack";
        player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, 100.0F, 1.0F);
    }
}