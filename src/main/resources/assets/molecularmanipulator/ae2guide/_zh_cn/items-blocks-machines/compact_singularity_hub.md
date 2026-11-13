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

单方块奇点天枢能采集资源、生产奇点序质和复制样品物品，产量与能耗规则和[多方块天枢](event_horizon_singularity_hub.md)相同。

<Row>
<BlockImage id="molecularmanipulator:compact_singularity_hub" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

将天枢接入有电的 ME 网络即可，不需要外壳、轨道或单独的资源核心。

## 准备什么

- 从创造模式物品栏取得设备，或在有命令权限时使用 `/give @s molecularmanipulator:compact_singularity_hub`。默认没有生存合成配方。
- 准备 ME 供电、至少 1 个可用频道，以及能接收产物的物品存储。
- 产序质和复制另需 ME 流体存储、微型黑洞和能源，详见[物质复制](singularity_matter_duplication.md)。

## 第一次使用

1. 放下天枢并保持区块加载。从任意一面接 ME 电缆，或在右侧栏用“远程连接”绑定无线枢纽，操作见[构序无线枢纽设置](sequence_array_controller.md)。
2. 右键打开界面，确认网络在线。
3. 进入**资源采集**页，查看资源列表，点击**开始采集**。默认每秒约产出列表中每种资源 **1000 个**。
4. 在 ME 终端确认物品数量增加。需要复制时，再切换到**物质复制**页，按该页指南放入黑洞和样品。

| 想做什么 | 打开哪一页 |
| --- | --- |
| 同时获得列表中的粗矿、原木等资源 | [资源采集](singularity_resource_collection.md)；不需要样品或黑洞 |
| 消耗能源生产奇点序质流体 | [物质复制](singularity_matter_duplication.md)；放入微型黑洞 |
| 用序质复制指定物品 | [物质复制](singularity_matter_duplication.md)；放入样品，原件保留 |

资源采集与产液、复制独立运行。取出样品可停止复制，取出黑洞可停止产液。

## 怎样算成功

界面显示网络在线，ME 中出现采集产物；使用复制功能时，ME 中出现复制品，样品仍在槽内。未放样品时，生产的序质应在 ME 流体存储中增加。

## 搬走时怎么做

先停止采集，正常拆下方块并收好掉落物。样品、黑洞等已存物品、待交付内容和无线绑定都会随方块保留。重新放置后恢复 ME 连接；如果无线枢纽被移动或替换，需要重新绑定。单方块天枢不使用多方块的“回收包裹”操作。

## 不工作时先看这里

| 现象 | 怎么处理 |
| --- | --- |
| 网络离线 | 检查 ME 供电、频道，以及所选的电缆或无线连接。 |
| 采集没有产物 | 点击开始采集，并给列表中所有资源留出物品存储空间。 |
| 有物品空间，却没有序质 | 还需要流体存储；检查黑洞与能源，见[物质复制](singularity_matter_duplication.md)。 |
| 离开后停产 | 单方块天枢不强加载区块，保持它所在区块加载。 |

采集在暂时离线时暂停，联网后继续；不会补发暂停期间的产量。
