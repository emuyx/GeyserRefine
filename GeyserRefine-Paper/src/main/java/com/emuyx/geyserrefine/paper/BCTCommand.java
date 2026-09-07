package com.emuyx.geyserrefine.paper;

import com.emuyx.geyserrefine.paper.network.TCPMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BCTCommand implements CommandExecutor, TabCompleter {

    private final GeyserRefinePlugin plugin;

    public BCTCommand(GeyserRefinePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("geyserrefine.reload") && !sender.isOp()) {
                    sender.sendMessage(ChatColor.RED + "你没有权限执行该命令。");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getBedrockCombatConfig().reload();
                // 重载披风功能
                if (plugin.getConfig().getBoolean("cape-workaround.enabled", false)) {
                    plugin.reloadCapeWorkaround();
                }
                sender.sendMessage(ChatColor.GREEN + "GeyserRefine 配置已重载。");
            }
            case "settings" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "该命令只能由玩家执行。");
                    return true;
                }
                if (!FloodgateUtil.isBedrockPlayer(player)) {
                    player.sendMessage(ChatColor.RED + "只有基岩版玩家可以使用此命令。");
                    return true;
                }
                if (plugin.getTCPServer() != null && plugin.getTCPServer().isConnected()) {
                    // 已配对：通过 TCP 让扩展打开其完整主菜单
                    TCPMessage msg = new TCPMessage();
                    msg.type = TCPMessage.Type.OPEN_MENU;
                    msg.playerUUID = player.getUniqueId();
                    msg.playerName = player.getName();
                    plugin.getTCPServer().broadcast(msg);
                } else {
                    // 未配对：内建设置菜单（仅"开关 Java 攻击"一项）
                    openBuiltinSettings(player);
                }
                return true;
            }
            case "escape" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "该命令只能由玩家执行。");
                    return true;
                }
                if (!FloodgateUtil.isBedrockPlayer(player)) {
                    player.sendMessage(ChatColor.RED + "只有基岩版玩家可以使用此命令。");
                    return true;
                }
                escapePlayer(player);
                player.sendMessage(ChatColor.GREEN + "正在脱离卡死...");
                return true;
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== GeyserRefine 命令 ===");
        sender.sendMessage(ChatColor.YELLOW + "/geyserrefine reload " + ChatColor.WHITE + "- 重载配置");
        sender.sendMessage(ChatColor.YELLOW + "/geyserrefine settings " + ChatColor.WHITE + "- 打开基岩版菜单");
        sender.sendMessage(ChatColor.YELLOW + "/geyserrefine escape " + ChatColor.WHITE + "- 脱离卡死");
    }

    // ---------- 脱离卡死 ----------
    public void escapePlayer(Player player) {
        Location originalLoc = player.getLocation().clone();
        World originalWorld = player.getWorld();

        World targetWorld = null;
        for (World world : Bukkit.getWorlds()) {
            if (!world.equals(originalWorld)) {
                targetWorld = world;
                break;
            }
        }
        if (targetWorld == null) {
            player.sendMessage(ChatColor.RED + "没有可用的其他维度！");
            return;
        }

        Location targetLoc = new Location(targetWorld, 0, 100, 0);
        player.teleportAsync(targetLoc).thenRun(() -> {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.teleportAsync(originalLoc);
            }, 1L);
        });
    }

    // ---------- 内建设置（未配对时用）：仅"开关 Java 攻击" ----------
    private void openBuiltinSettings(Player player) {
        var fPlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
        if (fPlayer == null) {
            player.sendMessage(ChatColor.RED + "无法获取 Floodgate 信息。");
            return;
        }

        boolean javaAttack = PlayerSettings.getSetting(player, "java-attack");
        CustomForm.Builder builder = CustomForm.builder()
                .title("§l§6基岩版设置")
                .toggle("§fJava 版攻击§e｜开=原版节奏，关=无冷却基岩风格",
                        javaAttack)
                .validResultHandler(response -> {
                    boolean newJavaAttack = response.next();
                    PlayerSettings.setSetting(player, "java-attack", newJavaAttack);
                    // 夜视由扩展处理，此处不涉及；java-attack 由 AttackSpeedListener 每 tick 应用
                    player.sendMessage(ChatColor.GREEN + (newJavaAttack
                            ? "已切换为 Java 版攻击。"
                            : "已切换为基岩版（无冷却）攻击。"));
                })
                .closedOrInvalidResultHandler(() -> {});
        fPlayer.sendForm(builder);
    }

    // ---------- Tab补全 ----------
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission("geyserrefine.reload") || sender.isOp()) suggestions.add("reload");
            suggestions.add("settings");
            suggestions.add("escape");
            return suggestions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
