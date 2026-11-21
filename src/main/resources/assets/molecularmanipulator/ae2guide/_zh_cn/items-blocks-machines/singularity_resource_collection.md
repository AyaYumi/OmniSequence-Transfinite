---
navigation:
  parent: items-blocks-machines/event_horizon_singularity_hub.md
  title: "天枢资源采集"
  icon: minecraft:raw_iron
  position: 0
---

# 天枢资源采集

**点一次开始，天枢就同时生产列表中的全部资源，并自动存入 ME。** 默认以粗矿、原木等资源为主，不需要选择单一目标，也不需要放样品或微型黑洞。

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

## 准备什么

- 已成型的[视界奇点天枢](event_horizon_singularity_hub.md)，或已接网的[单方块天枢](compact_singularity_hub.md)。
- 有电、有频道的 ME 网络，以及能接收**所有列表资源**的物品存储。
- 多方块天枢没有正在进行的施工、改建或回收。

这项功能不消耗奇点序质，也不需要流体存储。想复制指定样品或生产序质，请看[物质复制](singularity_matter_duplication.md)，不要用“开始采集”代替那一页的操作。

## 一步步开始采集

1. 右键天枢，打开**资源采集**页，确认网络在线。
2. 浏览当前资源列表，检查 ME 是否能收下每一种物品。只为粗铁预留空间、却挡住其他产物，会使整轮采集停住。
3. 点击**开始采集**。多方块天枢在 **40 tick** 后开始采集，运动未启动、暂停或归位时也能采集。
4. 打开 ME 终端，确认列表中的资源一起增加。产物直接进入 ME 存储。

默认每 **20 tick** 产出**每种资源各 1000 个**，正常速度下约为 **1 秒**。同种资源只计一次。以游戏内显示的资源清单、数量和间隔为准，这些都由服务器设置。

## 停止与再次开工

- 点击**停止采集**即可停止供货，运动状态不变。
- 要停止黑洞产液或样品复制，去物质复制页分别停止。
- 存储满时先腾出空间，恢复后从下一轮开始采集。

## 不工作时先看这里

| 现象 | 怎么处理 |
| --- | --- |
| 资源列表为空或没有想要的物品 | 清单由服务器选择；确认整合包包含该资源，并请服主查看采集范围和排除名单。 |
| 显示采集中，却没有产物 | 等待启动完成，再检查 ME 物品容量与存储过滤，所有产物都要能存。 |
| 多方块天枢无法启动运动或归位 | 清除运动路径上的障碍，并加载整个运动范围的区块。 |
| 重启、损坏或施工后停止采集 | 保持该区域加载，等待 ME 重新连接。补齐部件并结束施工。如果仍显示停止，点击“开始采集”。 |

在机器的资源采集页点击**指南**，可随时回到本页。
