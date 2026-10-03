---
navigation:
  parent: omnisequence-index.md
  title: "视界奇点天枢"
  icon: molecularmanipulator:event_horizon_singularity_hub
  position: 1100
item_ids:
- molecularmanipulator:event_horizon_singularity_hub
- molecularmanipulator:singularity_base_casing
- molecularmanipulator:gravity_gilded_block
- molecularmanipulator:spacetime_anchor_pillar
- molecularmanipulator:singularity_ring_track
- molecularmanipulator:event_horizon_lens
- molecularmanipulator:event_horizon_flux_pillar
- molecularmanipulator:black_hole_capture_node
- molecularmanipulator:white_hole_resource_core
- molecularmanipulator:event_horizon_stabilizer
- molecularmanipulator:singularity_crystal_tower
- molecularmanipulator:singularity_base_stairs
- molecularmanipulator:singularity_base_slab
---

# 视界奇点天枢

同时采集基础资源，并用微型黑洞产生奇点序质来复制物品。两条生产路线共用天枢连接的 ME 网络。

## 建成并连接

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:singularity_ring_track" />
<ItemIcon id="molecularmanipulator:event_horizon_lens" />
<ItemIcon id="molecularmanipulator:black_hole_capture_node" />
<ItemIcon id="molecularmanipulator:white_hole_resource_core" />
<ItemIcon id="molecularmanipulator:singularity_crystal_tower" />
</ItemGrid>

1. 完成构筑井的**三阶：视界奇点**研究，制作天枢所有部件；主控需要一个微型黑洞，白洞资源核心需要一个微型白洞。
2. 在 JEI 结构页准备材料，放置控制器并开启**投影**。
3. 清理冲突，点击**一键搭建**；先取玩家背包，再取 ME 材料。
4. 接入有电、有频道的 ME 网络，确认总览中的结构与联网状态。
5. 准备物品存储用于采集产物，准备流体存储用于奇点序质。

**天枢总览**内的专用量子槽只允许放入**缠绕态奇点**。配对的另一枚放在已供电的 AE2 量子环，未成型时也能连接远端材料网络；量子链路额外需要 512 AE/t 和一个频道。

## 三个页面

| 页面 | 主要用途 |
| --- | --- |
| 首页总览 | 查看建筑与运行状态，控制投影、搭建和拆除 |
| [资源采集](singularity_resource_collection.md) | 查看全部可采集资源，开始或停止采集 |
| [物质复制](singularity_matter_duplication.md) | 放入样品与微型黑洞，查看序质库存和 FE / AE 消耗 |

资源采集启动后，建筑进入启动过程并开始运动；停止采集后环带归位。物质复制的序质生产按微型黑洞与网络条件独立工作。

天枢所有部件配方的非微型黑洞、微型白洞原料已提高到原来的 **100 倍**；洞本身的数量和批量产出数量保持不变。

## 核心部件配方

<RecipeFor id="molecularmanipulator:event_horizon_singularity_hub" />

<RecipeFor id="molecularmanipulator:white_hole_resource_core" />

## 回收与保存

**一键拆除**保留控制器，回收材料优先存 ME，再进背包；空间不足时暂停。先停止采集，待建筑停止并归位后操作。

正常拆下控制器时，量子槽、样品、微型黑洞和待回收内容随方块保存。多方块强加载设置控制建筑所需区块的加载。

## 继续阅读

<SubPages icons={true} />
