---
navigation:
  parent: omnisequence-index.md
  title: "万物演算核心"
  icon: molecularmanipulator:omni_computation_controller
  position: 1020
item_ids:
- molecularmanipulator:omni_computation_controller
- molecularmanipulator:omni_computation_casing
- molecularmanipulator:omni_computation_glass
- molecularmanipulator:infinite_parallel_matrix
- molecularmanipulator:infinite_crafting_storage
- molecularmanipulator:universal_pattern_matrix
- molecularmanipulator:computation_data_entangler
- molecularmanipulator:computation_energy_stabilizer
- molecularmanipulator:computation_output_node
- molecularmanipulator:computation_crystal_pylon
---

# 万物演算核心

万物演算核心是一座大型 ME 合成 CPU，可以同时运行多个独立合成请求。它负责管理合成任务，配方加工仍由网络中的样板供应器和机器完成。

<ItemGrid>
<ItemIcon id="molecularmanipulator:omni_computation_controller" />
<ItemIcon id="molecularmanipulator:omni_computation_casing" />
<ItemIcon id="molecularmanipulator:omni_computation_glass" />
<ItemIcon id="molecularmanipulator:infinite_parallel_matrix" />
<ItemIcon id="molecularmanipulator:infinite_crafting_storage" />
<ItemIcon id="molecularmanipulator:universal_pattern_matrix" />
<ItemIcon id="molecularmanipulator:computation_data_entangler" />
<ItemIcon id="molecularmanipulator:computation_energy_stabilizer" />
<ItemIcon id="molecularmanipulator:computation_output_node" />
<ItemIcon id="molecularmanipulator:computation_crystal_pylon" />
</ItemGrid>

## 搭建

先在构筑井完成**二阶：万物演算**研究，再制作控制器与结构部件。

| 空间 | 范围 |
| --- | --- |
| 建筑外形 | 65 × 65，高 35 格 |
| 相对控制器 | 上下各 17 格，左右各 32 格 |
| 前后空间 | 前方 22 格，后方 42 格 |

1. 放置控制器，在 JEI 查看结构清单，并开启**投影**。
2. 清理冲突、备齐材料后点击**一键搭建**。材料先取背包，再取 ME。
3. 接入有电、有频道的 ME 网络，确认结构成型。
4. 从 ME 终端下单，在 AE2 合成状态界面查看任务。

## 合成请求

核心为多个请求提供独立的合成空间，可持续接收新的订单。大量订单仍需要足够的原料、能源、样板供应器、加工机器与输出空间。

循环配方需要初始种子材料。任务停滞时，检查合成状态中的缺料，以及对应加工机器是否能够接收材料和返还产物。

## 量子连接

量子槽放入一枚**缠绕态奇点**，远端已供电的 AE2 量子环放入配对的另一枚。链路额外需要 **512 AE/t 和 1 个频道**。

## 回收与结构更新

结构损坏或区块卸载时，任务、材料和进度保留，恢复工作条件后继续。正常拆下控制器时，任务与量子槽内容随方块保存。

**一键拆除**保留控制器，回收材料优先送回 ME，再放入背包。空间不足时暂停。服务器启用多方块强加载后，成型、施工与结构更新期间保持所需区块加载；建筑占用区块阻止自然刷怪。

旧版 **1.3.9** 径向结构可以使用**更新结构**。先检查新投影和所需材料，点击一次进入确认，再于 5 秒内点击执行。更新会迁移控制器与任务。

需要紧凑 CPU 时，可使用[超限算枢](transfinite_compute_nexus.md)。

## 配方

<RecipeFor id="molecularmanipulator:omni_computation_controller" fallbackText="当前整合包未提供可用配方，请查看 JEI 和研究条件。" />
