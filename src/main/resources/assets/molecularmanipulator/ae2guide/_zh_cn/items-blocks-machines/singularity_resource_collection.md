---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "天枢资源采集"
  icon: minecraft:raw_iron
  position: 0
---

# 天枢资源采集

天枢可以同时生产资源列表中的全部物品。默认资源包括粗矿与原木，整合包也可以配置其他资源。

<ItemGrid>
<ItemIcon id="minecraft:raw_iron" />
<ItemIcon id="minecraft:raw_copper" />
<ItemIcon id="minecraft:raw_gold" />
<ItemIcon id="minecraft:oak_log" />
<ItemIcon id="minecraft:spruce_log" />
<ItemIcon id="minecraft:birch_log" />
<ItemIcon id="minecraft:crimson_stem" />
<ItemIcon id="minecraft:warped_stem" />
</ItemGrid>

## 开始采集

1. 建成[视界奇点天枢](event_horizon_singularity_hub.md)，或连接[单方块奇点天枢](compact_singularity_hub.md)。
2. 打开**资源采集**页，查看当前完整资源列表。
3. 为列表中的物品提供 ME 存储空间，点击**开始采集**。
4. 多方块天枢完成启动后开始生产，产物自动进入连接的 ME 网络。

默认每 **20 tick**（正常 TPS 下约 1 秒）生产每种资源 **1000 个**。同一物品即使命中多个标签，也只在列表中出现一次。无需切换采集目标。

## 资源列表

服务器配置的**视界奇点天枢 → 资源采集**分组可调整资源标签、产量、间隔与黑名单。

| 设置 | 默认值或示例 |
| --- | --- |
| 物品标签 | #c:raw_ores、#c:raw_materials、#minecraft:raw_ores、#minecraft:logs、#c:logs |
| 额外标签示例 | #minecraft:planks，用于生产木板 |
| 黑名单 | 物品 ID，例如 minecraft:raw_iron |

只有整合包中实际存在的标签成员会进入列表。黑名单中的物品不会显示或生产。页面中的列表是当前可采集资源的依据，上方图标仅为常见示例。

## 停止与等待

点击**停止采集**即可停止生产，多方块天枢会归位。网络离线、结构不完整或存储不足时，设备等待运行条件恢复。产物不会因网络放不下而散落到地面。
