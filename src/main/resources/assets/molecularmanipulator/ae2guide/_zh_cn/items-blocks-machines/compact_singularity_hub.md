---
navigation:
  parent: omnisequence-index.md
  title: "单方块奇点天枢"
  icon: molecularmanipulator:compact_singularity_hub
  position: 1110
item_ids:
- molecularmanipulator:compact_singularity_hub
---

# 单方块奇点天枢

单方块奇点天枢提供资源采集、奇点序质生产与物品复制功能，无需搭建多方块结构。它使用与[视界奇点天枢](event_horizon_singularity_hub.md)相同的生产和能耗设置。

<Row>
<BlockImage id="molecularmanipulator:compact_singularity_hub" scale="4" />
<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

## 获取与连接

此设备没有默认合成配方，可从创造模式物品栏获取，或使用 `/give @s molecularmanipulator:compact_singularity_hub`。

放置后可从六个面连接 ME 线缆。为网络供电并提供 **1 个频道**，右键打开界面。采集与复制需要 ME 物品存储，奇点序质需要 ME 流体存储。

总览页的量子槽可放入**缠绕态奇点**，与已供电的 AE2 量子环配对。量子链路额外需要 **512 AE/t**。

## 采集与复制

| 页面 | 操作 |
| --- | --- |
| [资源采集](singularity_resource_collection.md) | 查看可生产资源，点击开始或停止采集 |
| [物质复制](singularity_matter_duplication.md) | 放入微型黑洞生产序质，放入样品开始复制 |

默认每 **20 tick** 生产列表中每种资源 **1000 个**。网络离线时暂停，恢复后继续生产。

微型黑洞槽最多容纳 **64 个**。默认每个黑洞每 20 tick 生产 **20 mB** 序质，每 mB 消耗 **1000 FE 或 256 AE**。每复制一件物品消耗 **1000 mB** 序质，样品保留。序质生产与复制无需启动资源采集。

## 搬迁

正常拆下方块会保留量子槽、样品、微型黑洞与缓存内容，重新放置并接入 ME 后可继续使用。

此设备不会强加载区块，持续生产需要保持所在区块加载。
