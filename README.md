# GeyserRefine

面向 Geyser / Floodgate 互通服务器，为 Java 服务器上的基岩版（Geyser）玩家补充一些额外的功能。攻击机制只是其中一部分，整体还包括客户端体验、界面与辅助设置等多方面。

功能构想与部分实现思路受 [GeyserExtras](https://github.com/GeyserExtras/GeyserExtras) 启发。

由 Paper 插件与 Geyser 扩展两部分组成：

```
基岩版客户端
    │
Geyser 代理 ── GeyserRefine-Extension  ┐
    │                                  │ TCP（127.0.0.1:25567）
Paper 服务端 ── GeyserRefine-Paper    ┘
```

两模块通过本机 TCP 通信。配对运行时为完整功能；单独运行时两端各自保留可用功能（见下文"配对"）。

## 模块

| 模块 | 类型 | 作用 |
|------|------|------|
| GeyserRefine-Paper | Paper 插件 | 服务端侧的附加能力：战斗相关机制、命中反馈、粒子/音效处理 |
| GeyserRefine-Extension | Geyser 扩展 | 客户端侧附加能力：设置表单与菜单、夜视、坐标、游玩天数、灵魂出窍、Toast |

## 功能

### GeyserRefine-Paper（服务端侧）

主要处理服务端机制，仅对基岩版玩家生效；开启 java-attack 的玩家使用原版攻击节奏。其中近战相关的部分包括：

- 移除 1.9 攻击冷却（攻速设为 40），基岩版武器伤害表，冲刺暴击，旧版生命回复
- 基岩风格击退，作用于玩家与生物；自动排除抗击退实体（末影龙、凋灵、监守者、铁傀儡、末地水晶、船、矿车、盔甲架）
- 基岩版护甲减伤公式（护甲韧性下限按护甲/5），保护附魔、抗性、受击免疫、盾牌格挡均保留
- 跨类型连击递减：基岩攻击者攻击"Java 战斗"目标（纯 Java 玩家或 java-attack 的基岩玩家）时，窗口内连击伤害递减，递减期间只保留纵向击退；打怪与基岩内战不触发
- 每次近战命中向攻击者播放 game.player.attack.strong（经 Geyser 翻译）
- 依赖 packetevents 屏蔽横扫、伤害指示粒子与相关音效
- 按输入设备（键鼠/手柄/触屏）调整方块交互距离
- 监听 /msg /tell /w /whisper，将私聊推送给扩展显示为基岩 Toast
- 命令 /geyserrefine（别名 /gr）：
  - settings：已配对时打开扩展主菜单；未配对时打开内建设置（切换 Java 攻击）
  - reload：重载配置
  - escape：维度闪切脱离卡死

### GeyserRefine-Extension（客户端侧）

面向基岩玩家的客户端功能与界面：

- 将暂停菜单的"服务器设置"入口替换为综合设置表单（Geyser 默认页不再显示）
- 综合设置表单：最前为一次性"快速重连"开关；含攻击区（仅配对时显示）与辅助区
  - 辅助项：持久夜视、显示坐标（默认开）、显示游玩天数（默认开）、减少挖掘粒子、高级提示框、自定义头颅、链接前提示
- 主菜单（经 /geyserrefine settings 打开）：快速重连、进度、统计、攻击设置（配对时）、辅助性设置、界面元素设置、灵魂出窍（可配置关闭）
- 持久夜视：直接向客户端发送 MobEffectPacket 伪造夜视，不依赖 Paper 施加热度药水；玩家身上有真实夜视时不会覆盖
- 显示游玩天数：发送 showDaysPlayed 游戏规则，并修正 Geyser 的 SetTime（游玩天数不再每 8 天归零）
- 显示坐标持久化，默认开启，覆盖 Geyser 的一次性坐标偏好
- 灵魂出窍：自定义 FREE 摄像机 + 隐藏并钉住身体，WASD/空格/潜行/疾跑控制（config.yml 可关闭）
- 加入 Toast：由 toast.yml 配置标题、内容与开关
- 可选资源包按玩家注入
- 披风透传（实验性）

## 配对

两端通过 TCP 连接状态判断是否配对。

| 场景 | 结果 |
|------|------|
| 仅 Paper | 基岩战斗、击退、命中音效可用；/geyserrefine settings 打开内建表单（仅"切换 Java 攻击"） |
| 仅 Extension | 辅助/界面/夜视/坐标/游玩天数/灵魂出窍/Toast 等客户端功能可用；攻击设置区隐藏 |
| 两端配对 | 全部功能 |

## 构建

需要 JDK 17 与 Maven。

```
mvn clean package
```

产物：
- GeyserRefine-Paper/target/GeyserRefine-Paper.jar
- GeyserRefine-Extension/target/GeyserRefine-Extension.jar

依赖由服务端在运行时提供（provided）：Paper API 1.21.1、Floodgate API、packetevents、Geyser core、Cumulus。

## 安装

1. 将 GeyserRefine-Paper.jar 放入服务器 plugins/ 目录（需 Floodgate，可选 packetevents）。
2. 将 GeyserRefine-Extension.jar 放入 Geyser 的 extensions/ 目录。
3. 两端在同一机器上时，默认自动通过 TCP 配对（端口见各端 tcp.properties）。
4. 重启服务器与 Geyser。

## 配置

| 文件 | 模块 | 内容 |
|------|------|------|
| plugins/GeyserRefine/config.yml | Paper | 战斗数值、护甲、击退、连击递减等 |
| plugins/GeyserRefine/tcp.properties | Paper | TCP 开关、端口、日志 |
| plugins/GeyserRefine/playerdata.yml | Paper | 玩家 java-attack 等设置 |
| extensions/geyserrefine/config.yml | Extension | 灵魂出窍开关等 |
| extensions/geyserrefine/toast.yml | Extension | 加入 Toast 标题、内容、开关 |
| extensions/geyserrefine/tcp.properties | Extension | TCP 目标端口等 |
| extensions/geyserrefine/player_settings.json | Extension | 玩家扩展设置 |

## 许可

本项目以 MIT License 发布，见 LICENSE。

本项目开发中使用了 AI 编程助手（Anthropic Claude / Claude Code）参与代码编写、问题排查与文档整理。AI 生成内容可能存在缺陷，请审查后使用，使用风险由使用者承担。
