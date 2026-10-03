---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "天枢资源采集"
  icon: minecraft:raw_iron
  position: 0
---

# 天枢资源采集

生产配置物品标签内的全部资源，支持任意物品标签，不限于粗矿和原木。每轮同时产出，不需要切换目标。

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

以上是常见示例；**资源采集页面的完整列表**会随整合包标签和黑名单更新。

## 开始采集

1. 建成[视界奇点天枢](event_horizon_singularity_hub.md)，接入在线 ME 网络。
2. 打开**资源采集**页，检查实际可生产的全部资源。
3. 为列表中的所有物品准备 ME 存储空间，点击**开始采集**。
4. 等待建筑启动，产物自动写入该 ME 网络。

| 默认生产设置 | 数值 |
| --- | --- |
| 每种物品的每轮产量 | 1000 个 |
| 生产间隔 | 20 tick，正常 TPS 下约 1 秒 |
| 生产方式 | 全列表同时生产，相同物品去重 |
| 存储不够 | 等待空间，不把溢出资源丢在地上 |

## 让整合包决定资源

服务器配置的 **视界奇点天枢 → 资源采集** 分组可调整标签、产量、间隔和物品黑名单。

| 设置 | 默认或填写方式 |
| --- | --- |
| 物品标签 | #c:raw_ores、#c:raw_materials、#minecraft:raw_ores、#minecraft:logs、#c:logs |
| 物品黑名单 | 精确物品 ID，例如 minecraft:raw_iron |

可添加其他类别的物品标签，例如 #minecraft:planks 生产木板。原有粗矿与原木列表会自动合并，保留你已有的配置。

只有当前整合包实际定义的标签会提供成员。命中任意白名单标签的物品合并后再排除黑名单，**黑名单优先**，被排除的物品也不会出现在采集列表中。

## 停止与等待

点击**停止采集**停止生产并让建筑归位。网络离线、结构不满足或存储无法接收时，查看总览与采集页状态，恢复对应条件后继续。
