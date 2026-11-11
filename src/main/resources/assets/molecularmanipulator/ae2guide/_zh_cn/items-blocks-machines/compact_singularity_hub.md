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

这是**不用盖大型建筑的奇点天枢**。接上 ME 后，可以采集资源，也可以生产奇点序质、复制样品物品；产量与能耗规则和[多方块天枢](event_horizon_singularity_hub.md)相同。

<Row>
<BlockImage id="molecularmanipulator:compact_singularity_hub" scale="4" />

<ItemImage id="ae2:fluix_glass_cable" scale="4" />
</Row>

**天枢 + 有电的 ME 网络**就是起点，不需要另搭外壳、轨道或资源核心。

## 准备什么

- **设备本体**：默认没有生存合成配方，从创造模式物品栏取得；有命令权限时也可使用 `/give @s molecularmanipulator:compact_singularity_hub`。
- **ME 网络**：供电、至少 1 个可用频道，以及能接收产物的物品存储。
- 若要生产序质与复制物品，再准备 **ME 流体存储、微型黑洞和能源**，详见[物质复制](singularity_matter_duplication.md)。

## 第一次使用

1. 放下天枢，从任意一面接上 ME 线缆，保持所在区块加载。
2. 右键打开界面，确认网络在线。
3. 进入**资源采集**页，查看资源列表，点击**开始采集**。默认每秒约产出列表中每种资源 **1000 个**。
4. 在 ME 终端确认物品数量增加。需要复制时，再切换到**物质复制**页，按该页指南放入黑洞和样品。

| 想做什么 | 打开哪一页 |
| --- | --- |
| 同时获得列表中的粗矿、原木等资源 | [资源采集](singularity_resource_collection.md)；不需要样品或黑洞 |
| 消耗能源生产奇点序质流体 | [物质复制](singularity_matter_duplication.md)；放入微型黑洞 |
| 用序质复制指定物品 | [物质复制](singularity_matter_duplication.md)；放入样品，原件保留 |

**资源采集与另外两项分别运行。** 停止采集不会停止产液或复制；取出样品停止复制，取出黑洞停止产液。

## 怎样算成功

界面显示网络在线，ME 中出现采集产物；使用复制功能时，ME 中出现复制品，样品仍在槽内。未放样品时，生产的序质应在 ME 流体存储中增加。

## 搬走时怎么做

先停止采集，正常拆下方块并**收好掉落物**。方块内保存的物品（包括样品和黑洞）及待交付内容会随方块保留； 重新放置、接网后即可继续。它不使用多方块的“回收包裹”操作。

## 不工作时先看这里

| 现象 | 怎么处理 |
| --- | --- |
| 网络离线 | 检查 ME 电源、线缆和频道。 |
| 采集没有产物 | 点击开始采集，并给列表中所有资源留出物品存储空间。 |
| 有物品空间，却没有序质 | 还需要流体存储；检查黑洞与能源，见[物质复制](singularity_matter_duplication.md)。 |
| 离开后停产 | 单方块天枢不强加载区块，保持它所在区块加载。 |

采集在暂时离线时暂停，联网后继续；不会补发暂停期间的产量。
