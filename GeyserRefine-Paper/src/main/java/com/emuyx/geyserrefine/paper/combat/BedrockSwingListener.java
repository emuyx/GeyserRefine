package com.emuyx.geyserrefine.paper.combat;

import com.emuyx.geyserrefine.paper.BedrockCombatConfig;
import com.emuyx.geyserrefine.paper.FloodgateUtil;
import com.emuyx.geyserrefine.paper.PlayerSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 矛的挥动限流（服务端侧）。
 * <p>
 * 基岩版玩家快速点击持矛时，客户端会把每次点击都当作挥手广播给其他人，
 * 导致他人看到的挥手频率 = 点击频率（而攻击者自己听到的音效是有冷却的）。
 * 这里在矛的冷却时间内取消 {@link PlayerAnimationEvent}，使其他人看到的
 * 挥动频率与矛的冷却一致。
 * <p>
 * 注意：矛的专属音效已移至 Extension 端实现，本类不再播放任何音效。
 */
public class BedrockSwingListener implements Listener {

    private final BedrockCombatConfig config;
    private final Map<UUID, Long> lastSpearSwingTick = new HashMap<>();

    public BedrockSwingListener(BedrockCombatConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        if (!FloodgateUtil.isBedrockPlayer(player)) return;
        if (PlayerSettings.getSetting(player, "java-attack")) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        String matKey = weapon.getType().getKey().toString();
        if (!isSpear(matKey)) return;

        // 冷却内取消挥手 → 其他人不会看到超频挥动
        long currentTick = player.getServer().getCurrentTick();
        Long last = lastSpearSwingTick.get(player.getUniqueId());
        int cooldownTicks = spearCooldownTicks(matKey);
        if (last != null && currentTick - last < cooldownTicks) {
            event.setCancelled(true);
            return;
        }
        lastSpearSwingTick.put(player.getUniqueId(), currentTick);
    }

    private static boolean isSpear(String matKey) {
        return matKey.startsWith("minecraft:") && matKey.endsWith("_spear");
    }

    /** 矛冷却（tick），数值按 Wiki 攻击速度换算（1/攻速秒 × 20）。 */
    private static int spearCooldownTicks(String matKey) {
        return switch (matKey) {
            case "minecraft:wooden_spear" -> 13;   // 0.65s
            case "minecraft:stone_spear" -> 15;    // 0.75s
            case "minecraft:copper_spear" -> 17;   // 0.85s
            case "minecraft:iron_spear", "minecraft:golden_spear" -> 19; // 0.95s
            case "minecraft:diamond_spear" -> 21;  // 1.05s
            case "minecraft:netherite_spear" -> 23; // 1.15s
            default -> 18;
        };
    }
}
