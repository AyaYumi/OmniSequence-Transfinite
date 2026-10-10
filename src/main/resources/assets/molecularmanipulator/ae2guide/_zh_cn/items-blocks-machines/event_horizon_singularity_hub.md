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

视界奇点天枢用于持续采集资源，并以奇点序质复制物品。资源采集与物质复制共用天枢连接的 ME 网络，可以分别运行。

<ItemGrid>
<ItemIcon id="molecularmanipulator:event_horizon_singularity_hub" />
<ItemIcon id="molecularmanipulator:singularity_ring_track" />
<ItemIcon id="molecularmanipulator:event_horizon_lens" />
<ItemIcon id="molecularmanipulator:black_hole_capture_node" />
<ItemIcon id="molecularmanipulator:white_hole_resource_core" />
<ItemIcon id="molecularmanipulator:singularity_crystal_tower" />
</ItemGrid>

## 搭建与连接

1. 在构筑井完成**三阶：视界奇点**研究，制作天枢部件。主控与白洞资源核心各需要一枚[微型超新星](black_and_white_holes.md)。
2. 按 JEI 结构清单备料，放置控制器并开启**投影**。
3. 清理冲突后点击**一键搭建**。材料优先取玩家背包，再取 ME 库存。
4. 接通 ME 电源与频道，在总览中确认结构和联网状态。
5. 为采集与复制产物准备 ME 物品存储，为奇点序质准备 ME 流体存储。

天枢基壳楼梯和台阶在工作台制作：6 个基壳按楼梯形排列得到 4 个楼梯，3 个基壳横排得到 6 个台阶。

## 控制器界面

| 页面 | 功能 |
| --- | --- |
| 总览 | 查看运行状态，控制投影、搭建和拆除 |
| [资源采集](singularity_resource_collection.md) | 查看资源列表，开始或停止采集 |
| [物质复制](singularity_matter_duplication.md) | 放入样品与微型黑洞，查看序质库存和能耗 |

点击开始采集后，等待天枢启动，产物会送入 ME。停止后建筑归位。微型黑洞生产序质和样品复制无需启动资源采集。

## 量子连接

总览页的量子槽只接受**缠绕态奇点**。将另一枚配对奇点放入已供电的 AE2 量子环，即可连接远端网络；未成型时也可获取施工材料。链路额外需要 **512 AE/t 和 1 个频道**。

## 拆除与搬迁

先停止资源采集，等待建筑归位，再点击**一键拆除**。控制器保留，结构材料先送 ME，再送背包；空间不足时暂停。

正常拆下控制器会保留量子槽、样品、微型黑洞和待回收内容。持续生产需要区块保持加载，可通过服务器的多方块强加载设置维持建筑所需区块。

## 配方

<RecipeFor id="molecularmanipulator:event_horizon_singularity_hub" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<RecipeFor id="molecularmanipulator:white_hole_resource_core" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />

<SubPages icons={true} />
